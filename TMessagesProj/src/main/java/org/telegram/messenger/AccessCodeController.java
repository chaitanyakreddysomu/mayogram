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

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class AccessCodeController {

    private AccessCodeController() {
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
