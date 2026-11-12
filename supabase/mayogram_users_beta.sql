-- Mayogram: logged-in account registry for the BETA build/channel only.
-- ---------------------------------------------------------------------------
-- Run this in the Supabase SQL editor. Safe to re-run.
--
-- Exact mirror of mayogram_users.sql, kept as a separate table so beta
-- testers never mix into the real mayogram_users data (and vice versa). The
-- app picks which table to write to based on BuildConfig.IS_BETA - the beta
-- build type calls record_mayogram_user_beta() below instead of
-- record_mayogram_user().
--
-- "version" is the app's own versionName (e.g. "1.0.0-beta.124"), read
-- on-device from the installed package and sent on every login.
--
-- Same security model as the other Mayogram tables: RLS on, no policies, so
-- the app's anon key cannot select/update this table directly - only through
-- the SECURITY DEFINER functions below.

create table if not exists public.mayogram_users_beta (
    id uuid primary key default gen_random_uuid(),

    telegram_user_id bigint not null unique,
    username text,
    first_name text,
    last_name text,
    phone text,
    version text,

    first_login_at timestamptz not null default now(),
    last_login_at timestamptz not null default now()
);

-- In case the table already exists from before "version" was added.
alter table public.mayogram_users_beta add column if not exists version text;

create index if not exists mayogram_users_beta_telegram_user_id_idx
    on public.mayogram_users_beta (telegram_user_id);

alter table public.mayogram_users_beta enable row level security;


-- ---------------------------------------------------------------------------
-- Called by the beta app right after each successful Telegram login. The
-- parameter list changed shape (added p_version), so the old 5-argument
-- overload is dropped first instead of left dangling alongside this one.
-- ---------------------------------------------------------------------------

drop function if exists public.record_mayogram_user_beta(bigint, text, text, text, text);

create or replace function public.record_mayogram_user_beta(
    p_telegram_user_id bigint,
    p_username          text default null,
    p_first_name        text default null,
    p_last_name         text default null,
    p_phone             text default null,
    p_version           text default null
)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
    if p_telegram_user_id is null then
        return;
    end if;

    insert into public.mayogram_users_beta as u (
        telegram_user_id, username, first_name, last_name, phone, version
    ) values (
        p_telegram_user_id, nullif(p_username, ''), nullif(p_first_name, ''),
        nullif(p_last_name, ''), nullif(p_phone, ''), nullif(p_version, '')
    )
    on conflict (telegram_user_id) do update
       set username      = coalesce(excluded.username, u.username),
           first_name    = coalesce(excluded.first_name, u.first_name),
           last_name     = coalesce(excluded.last_name, u.last_name),
           phone         = coalesce(excluded.phone, u.phone),
           version       = coalesce(excluded.version, u.version),
           last_login_at = now();
end;
$$;

revoke all on function public.record_mayogram_user_beta(bigint, text, text, text, text, text) from public;
grant execute on function public.record_mayogram_user_beta(bigint, text, text, text, text, text) to anon, authenticated;


-- ---------------------------------------------------------------------------
-- Admin: list every recorded beta account (password-protected, like
-- admin_api.sql's other admin_* calls - reuses the same
-- public.admin_check(password) helper). Return shape changed (added
-- version), so the old function is dropped first.
-- ---------------------------------------------------------------------------

drop function if exists public.admin_list_mayogram_users_beta(text);

create or replace function public.admin_list_mayogram_users_beta(p_password text)
returns table (
    id                uuid,
    telegram_user_id  bigint,
    username          text,
    first_name        text,
    last_name         text,
    phone             text,
    version           text,
    first_login_at    timestamptz,
    last_login_at     timestamptz
)
language plpgsql
security definer
set search_path = public, extensions
as $$
begin
    if not public.admin_check(p_password) then
        raise exception 'unauthorized';
    end if;

    return query
        select u.id, u.telegram_user_id, u.username, u.first_name, u.last_name,
               u.phone, u.version, u.first_login_at, u.last_login_at
          from public.mayogram_users_beta u
         order by u.last_login_at desc;
end;
$$;

revoke all on function public.admin_list_mayogram_users_beta(text) from public;
grant execute on function public.admin_list_mayogram_users_beta(text) to anon, authenticated;

-- Handy query in the SQL editor:
--   select telegram_user_id, username, first_name, last_name, phone, version, last_login_at
--     from public.mayogram_users_beta
--    order by last_login_at desc;
