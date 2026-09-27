-- Mayogram access-code administration (run in the Supabase SQL editor).
-- These run as the SQL editor's privileged role, so RLS does not block them.

-- 1. Generate a batch of codes in ABCD-EFGH-JKLM format.
--    Ambiguous characters (0/O, 1/I) are excluded so codes can be read aloud.
create or replace function public.generate_access_codes(p_count int default 10)
returns table (access_token text)
language plpgsql
security definer
set search_path = public
as $$
declare
    alphabet text := 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
    tok text;
    i int;
    j int;
begin
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

        insert into public.telegram_requests (access_token) values (tok);
        access_token := tok;
        return next;
    end loop;
end;
$$;

-- Usage:
--   select * from public.generate_access_codes(25);

-- 2. Active (unused) codes
--   select access_token, created_at
--     from public.telegram_requests
--    where status = 'active'
--    order by created_at desc;

-- 3. Used codes with their device binding
--   select access_token, device_id, installation_id, used_at
--     from public.telegram_requests
--    where status = 'used'
--    order by used_at desc;

-- 4. Revoke a code that has not been used yet
--   update public.telegram_requests
--      set status = 'revoked'
--    where access_token = 'ABCD-EFGH-JKLM'
--      and status = 'active';

-- 5. Summary
--   select status, count(*) from public.telegram_requests group by status;
