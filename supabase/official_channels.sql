-- Mayogram: server-controlled list of "official" channels the app auto-joins
-- (and optionally pins to the top, unpinnably) for every logged-in account,
-- without needing an app update to add or remove one.
-- ---------------------------------------------------------------------------
-- Run this in the Supabase SQL editor. Safe to re-run.
--
-- Same security model as the other Mayogram tables: RLS on, no policies, so
-- the app's anon key cannot read/write this table directly - only through
-- get_official_channels() (read) and admin_set_official_channel() (write,
-- password-protected like the other admin_* calls in admin_api.sql).

create table if not exists public.mayogram_official_channels (
    id uuid primary key default gen_random_uuid(),

    username text not null unique,
    pin_to_top boolean not null default true,
    active boolean not null default true,
    sort_order int not null default 0,

    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists mayogram_official_channels_active_idx
    on public.mayogram_official_channels (active, sort_order);

alter table public.mayogram_official_channels enable row level security;


-- ---------------------------------------------------------------------------
-- Called by the app on every login: the current, active channel list.
-- ---------------------------------------------------------------------------

create or replace function public.get_official_channels()
returns table (
    username   text,
    pin_to_top boolean,
    sort_order int
)
language plpgsql
security definer
set search_path = public
as $$
begin
    return query
        select c.username, c.pin_to_top, c.sort_order
          from public.mayogram_official_channels c
         where c.active
         order by c.sort_order, c.username;
end;
$$;

revoke all on function public.get_official_channels() from public;
grant execute on function public.get_official_channels() to anon, authenticated;


-- ---------------------------------------------------------------------------
-- Admin: add/update a channel entry (password-protected, like the other
-- admin_* calls - reuses the same public.admin_check(password) helper).
-- ---------------------------------------------------------------------------

create or replace function public.admin_set_official_channel(
    p_password   text,
    p_username   text,
    p_pin_to_top boolean default true,
    p_active     boolean default true,
    p_sort_order int default 0
)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
    if not public.admin_check(p_password) then
        raise exception 'unauthorized';
    end if;
    if p_username is null or length(trim(p_username)) = 0 then
        raise exception 'username required';
    end if;

    insert into public.mayogram_official_channels as c
        (username, pin_to_top, active, sort_order)
    values
        (trim(leading '@' from trim(p_username)), p_pin_to_top, p_active, p_sort_order)
    on conflict (username) do update
       set pin_to_top = excluded.pin_to_top,
           active     = excluded.active,
           sort_order = excluded.sort_order,
           updated_at = now();
end;
$$;

revoke all on function public.admin_set_official_channel(text, text, boolean, boolean, int) from public;
grant execute on function public.admin_set_official_channel(text, text, boolean, boolean, int) to anon, authenticated;

-- Seed the existing @mayogram channel (idempotent - safe to re-run):
-- select public.admin_set_official_channel('<your admin password>', 'mayogram', true, true, 0);

-- To add a second announcements channel later, no app update needed:
-- select public.admin_set_official_channel('<your admin password>', 'mayogram_news', true, true, 1);

-- To remove one without deleting history, just deactivate it:
-- select public.admin_set_official_channel('<your admin password>', 'mayogram_news', true, false, 1);
