-- Mayogram admin API
-- ---------------------------------------------------------------------------
-- Lets a browser-based admin page generate and list access codes WITHOUT ever
-- holding a service-role key.
--
-- How it stays safe:
--   * RLS remains on with no policies, so the anon key still cannot touch
--     telegram_requests directly.
--   * Every admin function is SECURITY DEFINER but verifies an admin password
--     (bcrypt-hashed, via pgcrypto) before doing anything.
--   * A leaked anon key alone is useless; the password is required too.
--
-- Run this AFTER schema.sql.

create extension if not exists pgcrypto;

create table if not exists public.app_admin (
    id            int primary key default 1,
    password_hash text not null,
    constraint app_admin_single_row check (id = 1)
);

alter table public.app_admin enable row level security;


-- ---------------------------------------------------------------------------
-- STEP 1 - set your admin password. CHANGE 'change-me-now' FIRST.
-- ---------------------------------------------------------------------------
insert into public.app_admin (id, password_hash)
values (1, crypt('change-me-now', gen_salt('bf')))
on conflict (id) do update set password_hash = excluded.password_hash;


-- ---------------------------------------------------------------------------
-- Password check shared by every admin function.
-- ---------------------------------------------------------------------------
create or replace function public.admin_check(p_password text)
returns boolean
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
    v_hash text;
begin
    select password_hash into v_hash from public.app_admin where id = 1;
    if v_hash is null or p_password is null then
        return false;
    end if;
    return v_hash = crypt(p_password, v_hash);
end;
$$;


-- ---------------------------------------------------------------------------
-- Generate one or more access codes.
-- ---------------------------------------------------------------------------
create or replace function public.admin_generate_tokens(
    p_password text,
    p_count    int default 1
)
returns table (
    access_token text,
    status       text,
    created_at   timestamptz
)
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
    -- 0/O and 1/I omitted so codes can be read aloud without ambiguity.
    alphabet text := 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
    tok text;
    i int;
    j int;
begin
    if not public.admin_check(p_password) then
        raise exception 'unauthorized';
    end if;
    if p_count is null or p_count < 1 or p_count > 100 then
        p_count := 1;
    end if;

    for i in 1..p_count loop
        loop
            tok := '';
            for j in 1..12 loop
                tok := tok || substr(alphabet, 1 + floor(random() * length(alphabet))::int, 1);
                if j % 4 = 0 and j < 12 then
                    tok := tok || '-';
                end if;
            end loop;
            exit when not exists (
                select 1 from public.telegram_requests t where t.access_token = tok
            );
        end loop;

        insert into public.telegram_requests (access_token)
        values (tok);

        return query
            select t.access_token, t.status, t.created_at
              from public.telegram_requests t
             where t.access_token = tok;
    end loop;
end;
$$;


-- ---------------------------------------------------------------------------
-- List every code with its device binding.
-- ---------------------------------------------------------------------------
create or replace function public.admin_list_tokens(p_password text)
returns table (
    id               uuid,
    access_token     text,
    status           text,
    device_id        text,
    installation_id  text,
    telegram_user_id bigint,
    telegram_username text,
    created_at       timestamptz,
    used_at          timestamptz,
    last_seen_at     timestamptz
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
        select t.id, t.access_token, t.status, t.device_id, t.installation_id,
               t.telegram_user_id, t.telegram_username,
               t.created_at, t.used_at, t.last_seen_at
          from public.telegram_requests t
         order by t.created_at desc;
end;
$$;


-- ---------------------------------------------------------------------------
-- Revoke an unused code.
-- ---------------------------------------------------------------------------
create or replace function public.admin_revoke_token(
    p_password text,
    p_token    text
)
returns text
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
    v_updated int;
begin
    if not public.admin_check(p_password) then
        raise exception 'unauthorized';
    end if;

    update public.telegram_requests
       set status = 'revoked'
     where access_token = p_token
       and status = 'active';

    get diagnostics v_updated = row_count;
    if v_updated = 1 then
        return 'revoked';
    end if;
    return 'not_active';
end;
$$;


-- ---------------------------------------------------------------------------
-- Permissions: the anon key may call these, but each still demands the
-- password, and direct table access stays blocked by RLS.
-- ---------------------------------------------------------------------------
revoke all on function public.admin_check(text) from public;
revoke all on function public.admin_generate_tokens(text, int) from public;
revoke all on function public.admin_list_tokens(text) from public;
revoke all on function public.admin_revoke_token(text, text) from public;

grant execute on function public.admin_generate_tokens(text, int) to anon, authenticated;
grant execute on function public.admin_list_tokens(text) to anon, authenticated;
grant execute on function public.admin_revoke_token(text, text) to anon, authenticated;
