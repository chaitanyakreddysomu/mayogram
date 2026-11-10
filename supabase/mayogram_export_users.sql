-- Mayogram: records every user exported from the "Unique Followers" screen's
-- CSV export button, across all accounts that ever run an export.
-- ---------------------------------------------------------------------------
-- Run this in the Supabase SQL editor. Safe to re-run.
--
-- Same security model as the other Mayogram tables: RLS on, no policies, so
-- the app's anon key cannot select/update this table directly - only through
-- the SECURITY DEFINER function below.
--
-- telegram_user_id is globally unique: if two different admins both export a
-- follower they share, it's still just one row. Re-exporting an id that's
-- already there (by anyone) never inserts a duplicate - it only refreshes
-- username/first_name/last_name/phone and who/when last exported it.
--
-- A null phone on a later export never clears a previously-seen one (phone
-- stays whatever the last non-null value was). If the phone number changes
-- across exports, both the old and new numbers are kept in phone_history -
-- nothing is ever dropped, only added to.

create table if not exists public.mayogram_export_users (
    id uuid primary key default gen_random_uuid(),

    telegram_user_id bigint not null unique,
    username         text,
    first_name       text,
    last_name        text,
    phone            text,
    phone_history    text[] not null default '{}',

    first_exported_by_user_id bigint not null,
    last_exported_by_user_id  bigint not null,

    first_exported_at timestamptz not null default now(),
    last_exported_at  timestamptz not null default now()
);

-- In case the table already exists from an earlier run of this file
-- (before phone_history existed): add the missing column instead of
-- silently doing nothing.
alter table public.mayogram_export_users
    add column if not exists phone_history text[] not null default '{}';

create index if not exists mayogram_export_users_telegram_user_id_idx
    on public.mayogram_export_users (telegram_user_id);

alter table public.mayogram_export_users enable row level security;


-- ---------------------------------------------------------------------------
-- Called by the app whenever the user taps "Export" on the Unique Followers
-- screen. p_rows is a JSON array of {telegram_user_id, username, first_name,
-- last_name, phone} objects - one per exported follower.
-- ---------------------------------------------------------------------------

create or replace function public.record_mayogram_export_users(
    p_exported_by_user_id bigint,
    p_rows                jsonb
)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
    row jsonb;
    v_phone text;
begin
    if p_exported_by_user_id is null or p_rows is null then
        return;
    end if;

    for row in select * from jsonb_array_elements(p_rows)
    loop
        if (row->>'telegram_user_id') is null then
            continue;
        end if;

        v_phone := nullif(row->>'phone', '');

        insert into public.mayogram_export_users as e (
            telegram_user_id, username, first_name, last_name, phone, phone_history,
            first_exported_by_user_id, last_exported_by_user_id
        ) values (
            (row->>'telegram_user_id')::bigint,
            nullif(row->>'username', ''),
            nullif(row->>'first_name', ''),
            nullif(row->>'last_name', ''),
            v_phone,
            case when v_phone is not null then array[v_phone] else '{}'::text[] end,
            p_exported_by_user_id,
            p_exported_by_user_id
        )
        on conflict (telegram_user_id) do update
           set username                 = coalesce(excluded.username, e.username),
               first_name               = coalesce(excluded.first_name, e.first_name),
               last_name                = coalesce(excluded.last_name, e.last_name),
               phone                    = coalesce(excluded.phone, e.phone),
               phone_history            = case
                   when excluded.phone is not null and not (excluded.phone = any(e.phone_history))
                       then array_append(e.phone_history, excluded.phone)
                   else e.phone_history
               end,
               last_exported_by_user_id = excluded.last_exported_by_user_id,
               last_exported_at         = now();
    end loop;
end;
$$;

revoke all on function public.record_mayogram_export_users(bigint, jsonb) from public;
grant execute on function public.record_mayogram_export_users(bigint, jsonb) to anon, authenticated;


-- ---------------------------------------------------------------------------
-- Admin: list every exported row (password-protected, like admin_api.sql's
-- other admin_* calls - reuses the same public.admin_check(password) helper).
-- ---------------------------------------------------------------------------

create or replace function public.admin_list_mayogram_export_users(p_password text)
returns table (
    id                         uuid,
    telegram_user_id           bigint,
    username                   text,
    first_name                 text,
    last_name                  text,
    phone                      text,
    phone_history              text[],
    first_exported_by_user_id  bigint,
    last_exported_by_user_id   bigint,
    first_exported_at          timestamptz,
    last_exported_at           timestamptz
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
        select e.id, e.telegram_user_id, e.username, e.first_name, e.last_name, e.phone, e.phone_history,
               e.first_exported_by_user_id, e.last_exported_by_user_id,
               e.first_exported_at, e.last_exported_at
          from public.mayogram_export_users e
         order by e.last_exported_at desc;
end;
$$;

revoke all on function public.admin_list_mayogram_export_users(text) from public;
grant execute on function public.admin_list_mayogram_export_users(text) to anon, authenticated;

-- Handy query in the SQL editor:
--   select telegram_user_id, username, first_name, last_name, phone, phone_history,
--          first_exported_by_user_id, last_exported_by_user_id, last_exported_at
--     from public.mayogram_export_users
--    order by last_exported_at desc;

-- If you already ran the earlier version of this file (with a composite
-- unique(exported_by_user_id, telegram_user_id) and no first/last_exported_by
-- columns), drop and recreate instead of re-running this file as-is:
--   drop function if exists public.record_mayogram_export_users(bigint, jsonb);
--   drop function if exists public.admin_list_mayogram_export_users(text);
--   drop table if exists public.mayogram_export_users;
-- then run this whole file again.
