/*
 * Mayogram access-code gate.
 *
 * Sits in front of Telegram's own login: the client must redeem a one-time
 * access code before the phone-number screen is reachable. This is entirely
 * separate from the Telegram account session - it authorises use of this app,
 * not the Telegram account.
 */

package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.text.TextUtils;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.tgnet.TLRPC;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class AccessCodeController {

    public static final int RESULT_OK = 0;
    public static final int RESULT_INVALID = 1;
    public static final int RESULT_USED = 2;
    public static final int RESULT_REVOKED = 3;
    public static final int RESULT_NETWORK_ERROR = 4;
    public static final int RESULT_EMPTY = 5;

    public interface RedeemCallback {
        void onResult(int result);
    }

    private static final String PREFS = "mayogram_access";
    private static final String KEY_AUTHORIZED = "authorized";
    private static final String KEY_INSTALLATION_ID = "installation_id";
    private static final String KEYSTORE_ALIAS = "mayogram_access_key";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int IV_LENGTH = 12;

    private static Boolean authorizedCache;

    private AccessCodeController() {
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /**
     * False when no backend is configured. The gate is then skipped, so builds
     * made without Supabase credentials still run.
     */
    public static boolean isGateConfigured() {
        return !TextUtils.isEmpty(BuildConfig.SUPABASE_URL)
                && !TextUtils.isEmpty(BuildConfig.SUPABASE_ANON_KEY);
    }

    public static boolean isAuthorized() {
        if (!isGateConfigured()) {
            return true;
        }
        if (authorizedCache != null) {
            return authorizedCache;
        }
        String stored = prefs().getString(KEY_AUTHORIZED, null);
        boolean ok = false;
        if (stored != null) {
            // The payload is encrypted with a Keystore key bound to this
            // install. A copied prefs file, or a restored backup, decrypts to
            // nothing because the key does not travel with it.
            String plain = decrypt(stored);
            ok = getInstallationId().equals(plain);
        }
        authorizedCache = ok;
        return ok;
    }

    private static void setAuthorized() {
        String payload = encrypt(getInstallationId());
        if (payload != null) {
            prefs().edit().putString(KEY_AUTHORIZED, payload).apply();
            authorizedCache = true;
        }
    }

    /**
     * Stable per-installation identifier. A reinstall produces a new one, which
     * is what forces a fresh access code.
     */
    public static String getInstallationId() {
        SharedPreferences preferences = prefs();
        String id = preferences.getString(KEY_INSTALLATION_ID, null);
        if (id == null) {
            id = UUID.randomUUID().toString();
            preferences.edit().putString(KEY_INSTALLATION_ID, id).apply();
        }
        return id;
    }

    /**
     * Redeems a code against Supabase. Networking runs off the UI thread; the
     * callback is delivered back on it.
     */
    public static void redeem(final String code, final RedeemCallback callback) {
        if (code == null || code.trim().length() == 0) {
            callback.onResult(RESULT_EMPTY);
            return;
        }
        Utilities.globalQueue.postRunnable(() -> {
            final int result = redeemBlocking(code.trim());
            AndroidUtilities.runOnUIThread(() -> {
                if (result == RESULT_OK) {
                    setAuthorized();
                }
                callback.onResult(result);
            });
        });
    }

    private static int redeemBlocking(String code) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(BuildConfig.SUPABASE_URL + "/rest/v1/rpc/redeem_access_code");
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY);
            connection.setRequestProperty("Authorization", "Bearer " + BuildConfig.SUPABASE_ANON_KEY);

            JSONObject body = new JSONObject();
            body.put("p_access_token", code);
            body.put("p_device_id", getDeviceDescription());
            body.put("p_installation_id", getInstallationId());

            OutputStream out = connection.getOutputStream();
            out.write(body.toString().getBytes(StandardCharsets.UTF_8));
            out.flush();
            out.close();

            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                return RESULT_NETWORK_ERROR;
            }
            // The function returns a table, so PostgREST replies with an array.
            JSONArray array = new JSONArray(readAll(connection.getInputStream()));
            if (array.length() == 0) {
                return RESULT_NETWORK_ERROR;
            }
            String result = array.getJSONObject(0).optString("result");
            if ("ok".equals(result)) {
                return RESULT_OK;
            } else if ("used".equals(result)) {
                return RESULT_USED;
            } else if ("revoked".equals(result)) {
                return RESULT_REVOKED;
            } else if ("invalid".equals(result)) {
                return RESULT_INVALID;
            }
            return RESULT_NETWORK_ERROR;
        } catch (Exception e) {
            FileLog.e(e);
            return RESULT_NETWORK_ERROR;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Best-effort: records the now-known Telegram account against this
     * installation, one row per account, so the admin page shows every account
     * logged in with each code. Fire-and-forget - login must never be blocked
     * or failed by this.
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
                URL url = new URL(BuildConfig.SUPABASE_URL + "/rest/v1/rpc/link_telegram_account");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY);
                connection.setRequestProperty("Authorization", "Bearer " + BuildConfig.SUPABASE_ANON_KEY);

                JSONObject body = new JSONObject();
                body.put("p_installation_id", getInstallationId());
                body.put("p_telegram_user_id", telegramUserId);
                body.put("p_telegram_username", telegramUsername != null ? telegramUsername : "");
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

    private static String getDeviceDescription() {
        return android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL
                + " (SDK " + android.os.Build.VERSION.SDK_INT + ")";
    }

    private static String readAll(InputStream stream) throws Exception {
        StringBuilder builder = new StringBuilder();
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8));
        String line;
        while ((line = reader.readLine()) != null) {
            builder.append(line);
        }
        reader.close();
        return builder.toString();
    }

    // --- Keystore-backed encryption of the local authorization ---------------

    private static SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        KeyStore.Entry entry = keyStore.getEntry(KEYSTORE_ALIAS, null);
        if (entry instanceof KeyStore.SecretKeyEntry) {
            return ((KeyStore.SecretKeyEntry) entry).getSecretKey();
        }
        KeyGenerator generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build());
        return generator.generateKey();
    }

    private static String encrypt(String value) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
            byte[] iv = cipher.getIV();
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return Base64.encodeToString(combined, Base64.NO_WRAP);
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    private static String decrypt(String value) {
        try {
            byte[] combined = Base64.decode(value, Base64.NO_WRAP);
            if (combined.length <= IV_LENGTH) {
                return null;
            }
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(),
                    new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] decrypted = cipher.doFinal(
                    combined, IV_LENGTH, combined.length - IV_LENGTH);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }
}
