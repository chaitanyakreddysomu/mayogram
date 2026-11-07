-- Mayogram: private Storage bucket for session-file (tgnet.dat) backups.
-- ---------------------------------------------------------------------------
-- Run this in the Supabase SQL editor, in the same project already used for
-- official_channels.sql / accounts.sql / mayogram_users.sql. Safe to re-run.
--
-- Every install of the app shares the one embedded anon key (there is no
-- per-user Supabase Auth here), so unlike the RPC-gated tables elsewhere in
-- this project, a Storage bucket can't distinguish "my own file" from
-- "someone else's file" for that key. The policy below only grants INSERT:
-- the app can push new backups, but the anon key can never list, read back,
-- overwrite, or delete ANY object in this bucket - including its own.
-- Restoring a backup is a manual step in the Supabase dashboard
-- (Storage -> mayogram-auth-backups), not something the app does.

insert into storage.buckets (id, name, public)
values ('mayogram-auth-backups', 'mayogram-auth-backups', false)
on conflict (id) do nothing;

drop policy if exists "mayogram_auth_backups_anon_insert" on storage.objects;

create policy "mayogram_auth_backups_anon_insert"
on storage.objects
for insert
to anon, authenticated
with check (bucket_id = 'mayogram-auth-backups');

-- Deliberately no select/update/delete policy for anon/authenticated:
-- RLS defaults to deny, so without one, those operations are refused for
-- this key on this bucket. Only the Supabase project owner, using the
-- dashboard or the service_role key (never shipped in the app), can list
-- or download what's in here.
