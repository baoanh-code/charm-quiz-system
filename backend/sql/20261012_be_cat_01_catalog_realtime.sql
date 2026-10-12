begin;

-- Customer catalog access only requires reading ACTIVE rows through the
-- existing "Anyone can view active charms" RLS policy.
revoke insert, update, delete, truncate, references, trigger
on table public.charm
from anon;

grant select
on table public.charm
to anon;

-- Preserve all existing RLS policies and authenticated Manager privileges.
-- Realtime changes are used by the frontend only as an invalidation signal;
-- the frontend refetches GET /api/charms for the authoritative catalog view.
do $$
begin
    if not exists (
        select 1
        from pg_publication_tables
        where pubname = 'supabase_realtime'
          and schemaname = 'public'
          and tablename = 'charm'
    ) then
        execute 'alter publication supabase_realtime add table public.charm';
    end if;
end
$$;

commit;
