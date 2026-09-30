-- Mayogram: every Telegram account that logs in through an installation
-- ---------------------------------------------------------------------------
-- Run this AFTER schema.sql and admin_api.sql. Safe to re-run.
--
-- One row per (installation, Telegram account). Logging into a second, third
-- ... n-th account on the same install adds a row each; logging into the same
-- account again refreshes its details instead of duplicating it.
--
-- Same security model as telegram_requests: RLS on, no policies. The app only
-- writes through link_telegram_account(); the admin page only reads through
-- admin_list_accounts(), which demands the admin password.

create table if not exists public.telegram_accounts (
    id uuid primary key default gen_random_uuid(),

    request_id uuid references public.telegram_requests (id) on delete set null,
    installation_id text not null,

    telegram_user_id bigint not null,
    username text,
    first_name text,
    last_name text,
    phone text,

    first_login_at timestamptz not null default now(),
    last_login_at timestamptz not null default now(),

    unique (installation_id, telegram_user_id)
);

create index if not exists telegram_accounts_request_id_idx
    on public.telegram_accounts (request_id);

create index if not exists telegram_accounts_telegram_user_id_idx
    on public.telegram_accounts (telegram_user_id);

alter table public.telegram_accounts enable row level security;


-- ---------------------------------------------------------------------------
-- Called by the app right after each successful Telegram login.
-- ---------------------------------------------------------------------------
-- The name/phone parameters default to null so an older APK that still sends
-- only (installation_id, user_id, username) keeps working. The old 3-argument
-- overload must go, otherwise PostgREST cannot choose between the two.

drop function if exists public.link_telegram_account(text, bigint, text);

create or replace function public.link_telegram_account(
    p_installation_id   text,
    p_telegram_user_id  bigint,
    p_telegram_username text,
    p_first_name        text default null,
    p_last_name         text default null,
    p_phone             text default null
)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
    v_request_id uuid;
begin
    if p_installation_id is null or p_telegram_user_id is null then
        return;
    end if;

    select id into v_request_id
      from public.telegram_requests
     where installation_id = p_installation_id
     order by used_at desc nulls last
     limit 1;

    insert into public.telegram_accounts as a (
        request_id, installation_id, telegram_user_id,
        username, first_name, last_name, phone
    ) values (
        v_request_id, p_installation_id, p_telegram_user_id,
        nullif(p_telegram_username, ''), nullif(p_first_name, ''),
        nullif(p_last_name, ''), nullif(p_phone, '')
    )
    on conflict (installation_id, telegram_user_id) do update
       set request_id    = coalesce(excluded.request_id, a.request_id),
           username      = excluded.username,
           first_name    = coalesce(excluded.first_name, a.first_name),
           last_name     = coalesce(excluded.last_name, a.last_name),
           phone         = coalesce(excluded.phone, a.phone),
           last_login_at = now();

    -- Keep the token row pointing at the most recent account, as before.
    if v_request_id is not null then
        update public.telegram_requests
           set telegram_user_id  = p_telegram_user_id,
               telegram_username = nullif(p_telegram_username, ''),
               last_seen_at      = now()
         where id = v_request_id;
    end if;
end;
$$;

revoke all on function public.link_telegram_account(text, bigint, text, text, text, text) from public;
grant execute on function public.link_telegram_account(text, bigint, text, text, text, text) to anon, authenticated;


-- ---------------------------------------------------------------------------
-- Admin: list every account (password-protected, like the other admin_* calls).
-- ---------------------------------------------------------------------------
create or replace function public.admin_list_accounts(p_password text)
returns table (
    id               uuid,
    request_id       uuid,
    installation_id  text,
    telegram_user_id bigint,
    username         text,
    first_name       text,
    last_name        text,
    phone            text,
    first_login_at   timestamptz,
    last_login_at    timestamptz
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
        select a.id, a.request_id, a.installation_id, a.telegram_user_id,
               a.username, a.first_name, a.last_name, a.phone,
               a.first_login_at, a.last_login_at
          from public.telegram_accounts a
         order by a.last_login_at desc;
end;
$$;

revoke all on function public.admin_list_accounts(text) from public;
grant execute on function public.admin_list_accounts(text) to anon, authenticated;

-- Handy query in the SQL editor:
--   select r.access_token, a.telegram_user_id, a.username,
--          a.first_name, a.last_name, a.phone, a.last_login_at
--     from public.telegram_accounts a
--     left join public.telegram_requests r on r.id = a.request_id
--    order by a.last_login_at desc;
