// Supabase connection for the admin page.
//
// The anon key is safe to keep here: RLS is enabled with no policies, so it
// cannot read or write telegram_requests on its own. Every admin function also
// requires the admin password, which is never stored in this file.
//
// Never put the service-role key in this file.
window.MAYOGRAM_CONFIG = {
  SUPABASE_URL: "https://gnjvhgtdbvytfinffvdc.supabase.co",
  SUPABASE_ANON_KEY: "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImduanZoZ3RkYnZ5dGZpbmZmdmRjIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzY5NjE2OTMsImV4cCI6MjA5MjUzNzY5M30.gaG03kAWCnpXy7vBY5b0lEpXLXjWF4E4IP_HxfsbEwQ"
};
