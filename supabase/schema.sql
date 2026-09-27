-- Mayogram access-code system
-- ---------------------------------------------------------------------------
-- Run this in the Supabase SQL editor.
--
-- Security model:
--   * RLS is enabled and NO policies are created, so the anon key used by the
--     app cannot select, insert or update this table directly. A leaked anon
--     key therefore cannot enumerate or burn access tokens.
--   * Redemption goes through redeem_access_code(), a SECURITY DEFINER
--     function that performs exactly one controlled operation.
--   * The service-role key is never needed by the client and must never be
--     placed in the APK.

create table if not exists public.telegram_requests (
    id uuid primary key default gen_random_uuid(),

    access_token text not null unique,

    status text not null default 'active'
        check (status in ('active', 'used', 'revoked')),

    device_id text,
    installation_id text,

    telegram_user_id bigint,
    telegram_username text,

    created_at timestamptz not null default now(),
    used_at timestamptz,
    last_seen_at timestamptz
);

create index if not exists telegram_requests_access_token_idx
    on public.telegram_requests (access_token);

create index if not exists telegram_requests_device_id_idx
    on public.telegram_requests (device_id);

create index if not exists telegram_requests_telegram_user_id_idx
    on public.telegram_requests (telegram_user_id);

alter table public.telegram_requests enable row level security;


-- ---------------------------------------------------------------------------
-- Atomic redemption
-- ---------------------------------------------------------------------------
-- The UPDATE ... WHERE status = 'active' is what makes this safe: PostgreSQL
-- locks the matching row, so if two devices submit the same token at the same
-- moment exactly one UPDATE can match 'active'. The loser sees 0 rows and is
-- reported as already used. No advisory locks or transactions needed.
--
-- Returns: (result, message)
--   'ok'        redeemed, caller may store local authorization
--   'invalid'   no such token
--   'used'      token already redeemed by another device
--   'revoked'   token disabled by an admin

create or replace function public.redeem_access_code(
    p_access_token   text,
    p_device_id      text,
    p_installation_id text
)
returns table (result text, message text)
language plpgsql
security definer
set search_path = public
as $$
declare
    v_updated int;
    v_status  text;
begin
    if p_access_token is null or length(trim(p_access_token)) = 0 then
        return query select 'invalid'::text, 'Invalid access code'::text;
        return;
    end if;

    update public.telegram_requests
       set status          = 'used',
           device_id       = p_device_id,
           installation_id = p_installation_id,
           used_at         = now(),
           last_seen_at    = now()
     where access_token = trim(p_access_token)
       and status       = 'active';

    get diagnostics v_updated = row_count;

    if v_updated = 1 then
        return query select 'ok'::text, 'Access granted'::text;
        return;
    end if;

    -- Nothing updated: distinguish "wrong code" from "already used".
    select status into v_status
      from public.telegram_requests
     where access_token = trim(p_access_token);

    if v_status is null then
        return query select 'invalid'::text, 'Invalid access code'::text;
    elsif v_status = 'used' then
        return query select 'used'::text, 'This access code has already been used'::text;
    else
        return query select 'revoked'::text, 'This access code is no longer valid'::text;
    end if;
end;
$$;

-- The app (anon key) may call the function, but still cannot read the table.
revoke all on function public.redeem_access_code(text, text, text) from public;
grant execute on function public.redeem_access_code(text, text, text) to anon, authenticated;
