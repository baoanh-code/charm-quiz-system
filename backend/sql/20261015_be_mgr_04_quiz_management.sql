begin;

alter table public.quiz_option
    add column if not exists option_order integer;

-- The legacy table has no authored-order metadata. Establish a deterministic
-- canonical order once, using the only stable value available: the option UUID.
with ranked_options as (
    select
        option.id,
        row_number() over (
            partition by option.question_id
            order by option.id asc
        ) - 1 as position
    from public.quiz_option option
)
update public.quiz_option option
set option_order = ranked.position
from ranked_options ranked
where option.id = ranked.id
  and option.option_order is null;

do $$
begin
    if exists (
        select 1
        from public.quiz_option option
        where option.option_order not between 0 and 3
    ) then
        raise exception
            'BE-MGR-04 cannot constrain option_order: a question has more than four options or invalid order data';
    end if;

    if exists (
        select 1
        from public.quiz_option option
        group by option.question_id, option.option_order
        having count(*) > 1
    ) then
        raise exception
            'BE-MGR-04 cannot constrain option_order: duplicate option positions exist';
    end if;
end
$$;

alter table public.quiz_option
    alter column option_order set not null;

do $$
begin
    if not exists (
        select 1
        from pg_constraint
        where conname = 'quiz_option_order_range_check'
          and conrelid = 'public.quiz_option'::regclass
    ) then
        alter table public.quiz_option
            add constraint quiz_option_order_range_check
            check (option_order between 0 and 3);
    end if;

    if not exists (
        select 1
        from pg_constraint
        where conname = 'quiz_option_question_order_key'
          and conrelid = 'public.quiz_option'::regclass
    ) then
        alter table public.quiz_option
            add constraint quiz_option_question_order_key
            unique (question_id, option_order);
    end if;
end
$$;

create or replace function public.import_manager_quiz(
    p_title text,
    p_description text,
    p_questions jsonb
)
returns table (
    outcome text,
    quiz_id uuid
)
language plpgsql
security definer
set search_path to 'public'
as $$
declare
    v_quiz_id uuid;
    v_question_id uuid;
    v_question jsonb;
    v_question_number bigint;
    v_option jsonb;
    v_option_number bigint;
    v_correct_index integer;
begin
    if p_title is null
       or trim(p_title) = ''
       or p_questions is null
       or jsonb_typeof(p_questions) <> 'array'
       or jsonb_array_length(p_questions) <> 10 then
        return query select 'INVALID_REQUEST'::text, null::uuid;
        return;
    end if;

    -- Validate the complete payload before the first write so invalid data can
    -- return a normal outcome without leaving partial rows.
    for v_question, v_question_number in
        select item.value, item.ordinality
        from jsonb_array_elements(p_questions) with ordinality item(value, ordinality)
    loop
        if jsonb_typeof(v_question) <> 'object'
           or coalesce(trim(v_question ->> 'prompt'), '') = ''
           or jsonb_typeof(v_question -> 'options') <> 'array'
           or jsonb_array_length(v_question -> 'options') <> 4
           or coalesce(v_question ->> 'correctIndex', '') !~ '^[0-3]$'
           or (
                v_question ? 'explanation'
                and v_question -> 'explanation' <> 'null'::jsonb
                and jsonb_typeof(v_question -> 'explanation') <> 'string'
           ) then
            return query select 'INVALID_REQUEST'::text, null::uuid;
            return;
        end if;

        for v_option, v_option_number in
            select item.value, item.ordinality
            from jsonb_array_elements(v_question -> 'options')
                 with ordinality item(value, ordinality)
        loop
            if jsonb_typeof(v_option) <> 'string'
               or trim(v_option #>> '{}') = '' then
                return query select 'INVALID_REQUEST'::text, null::uuid;
                return;
            end if;
        end loop;
    end loop;

    insert into public.quiz (title, description, status)
    values (
        trim(p_title),
        nullif(trim(p_description), ''),
        'INACTIVE'
    )
    returning id into v_quiz_id;

    for v_question, v_question_number in
        select item.value, item.ordinality
        from jsonb_array_elements(p_questions) with ordinality item(value, ordinality)
    loop
        v_correct_index := (v_question ->> 'correctIndex')::integer;

        insert into public.quiz_question (
            quiz_id,
            question_text,
            explanation,
            status,
            created_at
        ) values (
            v_quiz_id,
            trim(v_question ->> 'prompt'),
            nullif(trim(v_question ->> 'explanation'), ''),
            'ACTIVE',
            clock_timestamp() + ((v_question_number - 1) * interval '1 microsecond')
        )
        returning id into v_question_id;

        for v_option, v_option_number in
            select item.value, item.ordinality
            from jsonb_array_elements(v_question -> 'options')
                 with ordinality item(value, ordinality)
        loop
            insert into public.quiz_option (
                question_id,
                option_text,
                is_correct,
                option_order
            ) values (
                v_question_id,
                trim(v_option #>> '{}'),
                (v_option_number - 1) = v_correct_index,
                (v_option_number - 1)::integer
            );
        end loop;
    end loop;

    return query select 'OK'::text, v_quiz_id;
end;
$$;

create or replace function public.set_manager_quiz_status(
    p_quiz_id uuid,
    p_status text
)
returns table (
    outcome text,
    quiz_id uuid
)
language plpgsql
security definer
set search_path to 'public'
as $$
declare
    v_status text := upper(trim(p_status));
begin
    if v_status is null or v_status not in ('ACTIVE', 'INACTIVE') then
        return query select 'INVALID_REQUEST'::text, null::uuid;
        return;
    end if;

    -- Serialize status transitions so two concurrent activations cannot leave
    -- more than one ACTIVE quiz.
    lock table public.quiz in share row exclusive mode;

    perform 1
    from public.quiz quiz
    where quiz.id = p_quiz_id;

    if not found then
        return query select 'QUIZ_NOT_FOUND'::text, null::uuid;
        return;
    end if;

    if v_status = 'ACTIVE' then
        update public.quiz quiz
        set status = 'INACTIVE'
        where quiz.id <> p_quiz_id
          and upper(trim(quiz.status)) = 'ACTIVE';
    end if;

    update public.quiz quiz
    set status = v_status
    where quiz.id = p_quiz_id;

    return query select 'OK'::text, p_quiz_id;
end;
$$;

revoke all on function public.import_manager_quiz(text, text, jsonb) from public;
revoke execute on function public.import_manager_quiz(text, text, jsonb) from anon;
revoke execute on function public.import_manager_quiz(text, text, jsonb) from authenticated;
grant execute on function public.import_manager_quiz(text, text, jsonb) to service_role;

revoke all on function public.set_manager_quiz_status(uuid, text) from public;
revoke execute on function public.set_manager_quiz_status(uuid, text) from anon;
revoke execute on function public.set_manager_quiz_status(uuid, text) from authenticated;
grant execute on function public.set_manager_quiz_status(uuid, text) to service_role;

commit;
