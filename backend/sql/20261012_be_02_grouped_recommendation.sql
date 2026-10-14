begin;

alter table public.charm
    add column if not exists stock_status varchar;

update public.charm
set stock_status = 'IN_STOCK'
where stock_status is null;

alter table public.charm
    alter column stock_status set default 'IN_STOCK';

alter table public.charm
    alter column stock_status set not null;

do $$
begin
    if not exists (
        select 1
        from pg_constraint
        where conname = 'charm_stock_status_check'
          and conrelid = 'public.charm'::regclass
    ) then
        alter table public.charm
            add constraint charm_stock_status_check
            check (stock_status in ('IN_STOCK', 'OUT_OF_STOCK'));
    end if;
end
$$;

create index if not exists charm_recommendation_availability_idx
    on public.charm (status, stock_status);

-- Keep legacy rows unchanged so get_rule_recommendation remains compatible.
-- Clone each distinct legacy logical rule for the new grouped engine.
with legacy_rules as (
    select distinct on (charm_id, operator, value, priority, status)
        charm_id,
        operator,
        value,
        priority,
        status,
        created_at
    from public.recommendation_rule
    where lower(trim(field)) in ('favorite_music', 'favorite_sport', 'interests')
    order by charm_id, operator, value, priority, status, created_at, id
)
insert into public.recommendation_rule (
    id,
    charm_id,
    field,
    operator,
    value,
    priority,
    status,
    created_at
)
select
    gen_random_uuid(),
    legacy.charm_id,
    'INTEREST',
    legacy.operator,
    legacy.value,
    legacy.priority,
    legacy.status,
    legacy.created_at
from legacy_rules legacy
where not exists (
    select 1
    from public.recommendation_rule existing
    where existing.field = 'INTEREST'
      and existing.charm_id = legacy.charm_id
      and existing.operator is not distinct from legacy.operator
      and existing.value is not distinct from legacy.value
      and existing.priority is not distinct from legacy.priority
      and existing.status is not distinct from legacy.status
);

create or replace function public.get_grouped_rule_recommendations(
    p_name_initial text,
    p_zodiac text,
    p_life_path text,
    p_interests text[],
    p_favorite_animal text,
    p_favorite_color text
)
returns table (
    section text,
    matched_field text,
    matched_value text,
    priority integer,
    charm_id uuid
)
language sql
stable
security definer
set search_path to 'public'
as $$
    with profile_values(field, input_value, section) as (
        select 'NAME_INITIAL', p_name_initial, 'NAME'
        union all
        select 'ZODIAC', p_zodiac, 'BIRTH_DATE'
        union all
        select 'LIFE_PATH', p_life_path, 'BIRTH_DATE'
        union all
        select 'INTEREST', interest, 'INTERESTS'
        from unnest(coalesce(p_interests, array[]::text[])) interest
        union all
        select 'FAVORITE_ANIMAL', p_favorite_animal, 'FAVORITE_ANIMAL'
        union all
        select 'FAVORITE_COLOR', p_favorite_color, 'FAVORITE_COLOR'
    ),
    matching_rules as (
        select
            profile.section,
            rule.field as matched_field,
            profile.input_value as matched_value,
            rule.priority,
            rule.charm_id,
            row_number() over (
                partition by profile.section, rule.charm_id
                order by
                    rule.priority desc nulls last,
                    rule.field asc,
                    profile.input_value asc,
                    rule.id asc
            ) as charm_row
        from profile_values profile
        join public.recommendation_rule rule
          on upper(trim(rule.field)) = profile.field
         and upper(trim(rule.status)) = 'ACTIVE'
         and nullif(trim(rule.value), '') is not null
         and (
             (
                 upper(trim(rule.operator)) = 'EQUALS'
                 and lower(trim(profile.input_value)) = lower(trim(rule.value))
             )
             or
             (
                 upper(trim(rule.operator)) = 'CONTAINS'
                 and position(lower(trim(rule.value)) in lower(trim(profile.input_value))) > 0
             )
         )
        join public.charm charm
          on charm.id = rule.charm_id
         and upper(trim(charm.status)) = 'ACTIVE'
         and upper(trim(charm.stock_status)) = 'IN_STOCK'
        where nullif(trim(profile.input_value), '') is not null
    )
    select
        matching_rules.section,
        matching_rules.matched_field,
        matching_rules.matched_value,
        matching_rules.priority,
        matching_rules.charm_id
    from matching_rules
    where matching_rules.charm_row = 1
    order by
        case matching_rules.section
            when 'NAME' then 1
            when 'BIRTH_DATE' then 2
            when 'INTERESTS' then 3
            when 'FAVORITE_ANIMAL' then 4
            when 'FAVORITE_COLOR' then 5
        end,
        matching_rules.priority desc nulls last,
        matching_rules.charm_id asc;
$$;

grant execute on function public.get_grouped_rule_recommendations(
    text,
    text,
    text,
    text[],
    text,
    text
) to anon, authenticated, service_role;

commit;
