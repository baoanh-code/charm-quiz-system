begin;

-- Never hide or repair existing duplicate answers. Abort before changing the
-- schema so an operator can investigate the affected sessions explicitly.
do $$
begin
    if exists (
        select 1
        from public.quiz_answer
        group by session_id, question_id
        having count(*) > 1
    ) then
        raise exception
            'BE-QUIZ-02 cannot add uniqueness: duplicate quiz answers exist for a session/question';
    end if;
end
$$;

do $$
begin
    if not exists (
        select 1
        from pg_constraint
        where conname = 'quiz_answer_session_question_key'
          and conrelid = 'public.quiz_answer'::regclass
    ) then
        alter table public.quiz_answer
            add constraint quiz_answer_session_question_key
            unique (session_id, question_id);
    end if;
end
$$;

create or replace function public.submit_quiz_answer(
    p_session_id uuid,
    p_question_id uuid,
    p_selected_option_id uuid
)
returns table (
    outcome text,
    is_correct boolean,
    correct_option_id uuid,
    explanation text,
    answered_count integer,
    score integer,
    reward_eligible boolean,
    completed_at timestamptz
)
language plpgsql
security definer
set search_path to 'public'
as $$
declare
    v_quiz_id uuid;
    v_completed_at timestamptz;
    v_explanation text;
    v_selected_is_correct boolean;
    v_correct_option_id uuid;
    v_correct_option_count integer;
    v_answered_count integer;
    v_score integer;
    v_reward_eligible boolean;
    v_result_completed_at timestamptz;
begin
    select session.quiz_id, session.completed_at
    into v_quiz_id, v_completed_at
    from public.quiz_session session
    where session.id = p_session_id
    for update;

    if not found then
        return query select
            'SESSION_NOT_FOUND'::text, null::boolean, null::uuid, null::text,
            null::integer, null::integer, null::boolean, null::timestamptz;
        return;
    end if;

    if v_completed_at is not null then
        return query select
            'SESSION_COMPLETED'::text, null::boolean, null::uuid, null::text,
            null::integer, null::integer, null::boolean, v_completed_at;
        return;
    end if;

    select question.explanation
    into v_explanation
    from public.quiz_question question
    where question.id = p_question_id
      and question.quiz_id = v_quiz_id
      and upper(trim(question.status)) = 'ACTIVE';

    if not found then
        return query select
            'QUESTION_NOT_FOUND'::text, null::boolean, null::uuid, null::text,
            null::integer, null::integer, null::boolean, null::timestamptz;
        return;
    end if;

    select option.is_correct
    into v_selected_is_correct
    from public.quiz_option option
    where option.id = p_selected_option_id
      and option.question_id = p_question_id;

    if not found then
        return query select
            'OPTION_NOT_FOUND'::text, null::boolean, null::uuid, null::text,
            null::integer, null::integer, null::boolean, null::timestamptz;
        return;
    end if;

    if exists (
        select 1
        from public.quiz_answer answer
        where answer.session_id = p_session_id
          and answer.question_id = p_question_id
    ) then
        return query select
            'ANSWER_ALREADY_SUBMITTED'::text, null::boolean, null::uuid, null::text,
            null::integer, null::integer, null::boolean, null::timestamptz;
        return;
    end if;

    select
        count(*)::integer,
        min(option.id::text)::uuid
    into v_correct_option_count, v_correct_option_id
    from public.quiz_option option
    where option.question_id = p_question_id
      and option.is_correct = true;

    if v_correct_option_count <> 1
       or v_selected_is_correct is null then
        raise exception 'Invalid correctness model for quiz question %', p_question_id;
    end if;

    begin
        insert into public.quiz_answer (
            session_id,
            question_id,
            selected_option_id,
            is_correct
        ) values (
            p_session_id,
            p_question_id,
            p_selected_option_id,
            v_selected_is_correct
        );
    exception
        when unique_violation then
            return query select
                'ANSWER_ALREADY_SUBMITTED'::text, null::boolean, null::uuid, null::text,
                null::integer, null::integer, null::boolean, null::timestamptz;
            return;
    end;

    select
        count(distinct answer.question_id)::integer,
        count(*) filter (where answer.is_correct)::integer
    into v_answered_count, v_score
    from public.quiz_answer answer
    where answer.session_id = p_session_id;

    if v_answered_count > 10 then
        raise exception 'Quiz session % contains more than 10 distinct answers', p_session_id;
    end if;

    update public.quiz_session session
    set score = v_score,
        reward_eligible = case
            when v_answered_count = 10 then v_score >= 6
            else false
        end,
        completed_at = case
            when v_answered_count = 10 then clock_timestamp()
            else null
        end
    where session.id = p_session_id
      and session.completed_at is null
    returning session.reward_eligible, session.completed_at
    into v_reward_eligible, v_result_completed_at;

    if not found then
        raise exception 'Quiz session % changed while its answer was being submitted', p_session_id;
    end if;

    return query select
        'OK'::text,
        v_selected_is_correct,
        v_correct_option_id,
        v_explanation,
        v_answered_count,
        v_score,
        v_reward_eligible,
        v_result_completed_at;
end;
$$;

revoke all on function public.submit_quiz_answer(uuid, uuid, uuid) from public;
revoke execute on function public.submit_quiz_answer(uuid, uuid, uuid) from anon;
revoke execute on function public.submit_quiz_answer(uuid, uuid, uuid) from authenticated;
grant execute on function public.submit_quiz_answer(uuid, uuid, uuid) to service_role;

commit;
