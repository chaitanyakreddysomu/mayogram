/*
 * Mayogram: records every Telegram account that logs into this app in
 * Supabase (table mayogram_users). This is purely informational - it never
 * blocks or gates login. There used to be a pre-login access-code gate here;
 * it has been removed, so the app now behaves like stock Telegram's login
 * flow. One row is written per Telegram account, so logging into a second,
 * third, etc. account on the same install (Telegram's own "Add Account")
 * produces one row each.
 */

package org.telegram.messenger;

import android.text.TextUtils;

import org.json.JSONObject;
import org.telegram.tgnet.TLRPC;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

public class AccessCodeController {

    private AccessCodeController() {
    }

    // Mayogram: full_access lets an account forward from/save content in
    // forward-restricted chats (see mayogram_users.sql's full_access
    // column). Re-fetched fresh every app open (LaunchActivity calls
    // refreshFullAccess for every logged-in account on cold start) - never
    // persisted to disk, so a revoke in Supabase takes effect the next time
    // the app is opened without needing a re-login. Keyed by
    // telegram_user_id since one install can have multiple logged-in
    // accounts, each with its own full_access value.
    private static final ConcurrentHashMap<Long, Boolean> fullAccessCache = new ConcurrentHashMap<>();

    /** False until refreshFullAccess() for this account has completed at least once this app run. */
    public static boolean hasFullAccess(long telegramUserId) {
        return Boolean.TRUE.equals(fullAccessCache.get(telegramUserId));
    }

    public interface FullAccessCallback {
        void onResult(boolean fullAccess);
    }

    /**
     * Fire-and-forget: refreshes the cached full_access flag for this
     * account from Supabase. Call on every app open for every logged-in
     * account (not just at login), since this is meant to be re-checked on
     * every cold start, not cached across app restarts.
     */
    public static void refreshFullAccess(final long telegramUserId) {
        refreshFullAccess(telegramUserId, null);
    }

    public static void refreshFullAccess(final long telegramUserId, final FullAccessCallback callback) {
        if (!isGateConfigured() || telegramUserId == 0) {
            fullAccessCache.put(telegramUserId, false);
            if (callback != null) {
                AndroidUtilities.runOnUIThread(() -> callback.onResult(false));
            }
            return;
        }
        Utilities.globalQueue.postRunnable(() -> {
            boolean result = false;
            HttpURLConnection connection = null;
            try {
                URL url = new URL(BuildConfig.SUPABASE_URL + "/rest/v1/rpc/get_mayogram_full_access");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY);
                connection.setRequestProperty("Authorization", "Bearer " + BuildConfig.SUPABASE_ANON_KEY);

                JSONObject body = new JSONObject();
                body.put("p_telegram_user_id", telegramUserId);

                OutputStream out = connection.getOutputStream();
                out.write(body.toString().getBytes(StandardCharsets.UTF_8));
                out.flush();
                out.close();

                if (connection.getResponseCode() == 200) {
                    ByteArrayOutputStream bout = new ByteArrayOutputStream();
                    try (InputStream in = connection.getInputStream()) {
                        byte[] buf = new byte[256];
                        int n;
                        while ((n = in.read(buf)) > 0) {
                            bout.write(buf, 0, n);
                        }
                    }
                    result = Boolean.parseBoolean(bout.toString(StandardCharsets.UTF_8.name()).trim());
                }
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
            final boolean finalResult = result;
            fullAccessCache.put(telegramUserId, finalResult);
            if (callback != null) {
                AndroidUtilities.runOnUIThread(() -> callback.onResult(finalResult));
            }
        });
    }

    /**
     * False when no backend is configured; recording is then skipped, so
     * builds made without Supabase credentials still run normally.
     */
    public static boolean isGateConfigured() {
        return !TextUtils.isEmpty(BuildConfig.SUPABASE_URL)
                && !TextUtils.isEmpty(BuildConfig.SUPABASE_ANON_KEY);
    }

    /**
     * Fire-and-forget: records the now-known Telegram account in Supabase.
     * Networking runs off the UI thread. Login must never be blocked or
     * failed by this.
     */
    public static void linkTelegramAccount(final TLRPC.User user) {
        if (!isGateConfigured() || user == null) {
            return;
        }
        final long telegramUserId = user.id;
        final String telegramUsername = UserObject.getPublicUsername(user);
        final String firstName = user.first_name;
        final String lastName = user.last_name;
        final String phone = user.phone;
        Utilities.globalQueue.postRunnable(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(BuildConfig.SUPABASE_URL + "/rest/v1/rpc/record_mayogram_user");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY);
                connection.setRequestProperty("Authorization", "Bearer " + BuildConfig.SUPABASE_ANON_KEY);

                JSONObject body = new JSONObject();
                body.put("p_telegram_user_id", telegramUserId);
                body.put("p_username", telegramUsername != null ? telegramUsername : "");
                body.put("p_first_name", firstName != null ? firstName : "");
                body.put("p_last_name", lastName != null ? lastName : "");
                body.put("p_phone", phone != null ? phone : "");

                OutputStream out = connection.getOutputStream();
                out.write(body.toString().getBytes(StandardCharsets.UTF_8));
                out.flush();
                out.close();

                connection.getResponseCode();
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }
}
