-- Mayogram: logged-in account registry (replaces the access-code system)
-- ---------------------------------------------------------------------------
-- Run this in the Supabase SQL editor. Safe to re-run.
--
-- The app no longer gates login behind an access code (see schema.sql's
-- telegram_requests/redeem_access_code and accounts.sql's telegram_accounts/
-- link_telegram_account - both superseded for new installs by this file).
-- Those older tables/functions are left untouched in the database; the app
-- simply stops writing to them. This table is the only thing the client
-- writes to on login going forward.
--
-- One row per Telegram account (not per installation): using one APK with
-- multiple logged-in accounts (Telegram's own "Add Account") produces one
-- row per account, each refreshed on every subsequent login.
--
-- Same security model as the older tables: RLS on, no policies, so the app's
-- anon key cannot select/update this table directly - only through the
-- SECURITY DEFINER function below.

create table if not exists public.mayogram_users (
    id uuid primary key default gen_random_uuid(),

    telegram_user_id bigint not null unique,
    username text,
    first_name text,
    last_name text,
    phone text,

    -- When true, this account can forward from/save content in
    -- forward-restricted ("Content Protection") chats. Every new row starts
    -- false (the default) - the app never sets this itself, only
    -- admin_set_full_access below does.
    full_access boolean not null default false,

    first_login_at timestamptz not null default now(),
    last_login_at timestamptz not null default now()
);

-- Safe to re-run against a table created before this column existed.
alter table public.mayogram_users add column if not exists full_access boolean not null default false;

create index if not exists mayogram_users_telegram_user_id_idx
    on public.mayogram_users (telegram_user_id);

alter table public.mayogram_users enable row level security;


-- ---------------------------------------------------------------------------
-- Called by the app right after each successful Telegram login.
-- ---------------------------------------------------------------------------

create or replace function public.record_mayogram_user(
    p_telegram_user_id bigint,
    p_username          text default null,
    p_first_name        text default null,
    p_last_name         text default null,
    p_phone             text default null
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

    insert into public.mayogram_users as u (
        telegram_user_id, username, first_name, last_name, phone
    ) values (
        p_telegram_user_id, nullif(p_username, ''), nullif(p_first_name, ''),
        nullif(p_last_name, ''), nullif(p_phone, '')
    )
    on conflict (telegram_user_id) do update
       set username      = coalesce(excluded.username, u.username),
           first_name    = coalesce(excluded.first_name, u.first_name),
           last_name     = coalesce(excluded.last_name, u.last_name),
           phone         = coalesce(excluded.phone, u.phone),
           last_login_at = now();
end;
$$;

revoke all on function public.record_mayogram_user(bigint, text, text, text, text) from public;
grant execute on function public.record_mayogram_user(bigint, text, text, text, text) to anon, authenticated;


-- ---------------------------------------------------------------------------
-- Called by the app once per logged-in account on every app open (not
-- cached across launches - a revoke here should take effect next time the
-- app is opened, not require a re-login). Returns false, never an error,
-- for an unrecorded telegram_user_id.
-- ---------------------------------------------------------------------------
create or replace function public.get_mayogram_full_access(p_telegram_user_id bigint)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
declare
    v_full_access boolean;
begin
    if p_telegram_user_id is null then
        return false;
    end if;

    select full_access into v_full_access
      from public.mayogram_users
     where telegram_user_id = p_telegram_user_id;

    return coalesce(v_full_access, false);
end;
$$;

revoke all on function public.get_mayogram_full_access(bigint) from public;
grant execute on function public.get_mayogram_full_access(bigint) to anon, authenticated;


-- ---------------------------------------------------------------------------
-- Admin: list every recorded account (password-protected, like admin_api.sql's
-- other admin_* calls - reuses the same public.admin_check(password) helper).
-- ---------------------------------------------------------------------------
create or replace function public.admin_list_mayogram_users(p_password text)
returns table (
    id                uuid,
    telegram_user_id  bigint,
    username          text,
    first_name        text,
    last_name         text,
    phone             text,
    full_access       boolean,
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
               u.phone, u.full_access, u.first_login_at, u.last_login_at
          from public.mayogram_users u
         order by u.last_login_at desc;
end;
$$;

revoke all on function public.admin_list_mayogram_users(text) from public;
grant execute on function public.admin_list_mayogram_users(text) to anon, authenticated;


-- ---------------------------------------------------------------------------
-- Admin: grant/revoke full_access for one account (password-protected).
-- ---------------------------------------------------------------------------
create or replace function public.admin_set_full_access(
    p_password          text,
    p_telegram_user_id   bigint,
    p_full_access        boolean
)
returns void
language plpgsql
security definer
set search_path = public, extensions
as $$
begin
    if not public.admin_check(p_password) then
        raise exception 'unauthorized';
    end if;

    update public.mayogram_users
       set full_access = p_full_access
     where telegram_user_id = p_telegram_user_id;
end;
$$;

revoke all on function public.admin_set_full_access(text, bigint, boolean) from public;
grant execute on function public.admin_set_full_access(text, bigint, boolean) to anon, authenticated;

-- Handy query in the SQL editor:
--   select telegram_user_id, username, first_name, last_name, phone, last_login_at
--     from public.mayogram_users
--    order by last_login_at desc;
