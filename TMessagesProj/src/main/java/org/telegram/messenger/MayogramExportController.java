/*
 * Mayogram: "Export" button on the Unique Followers screen. Writes a CSV
 * (telegram_userid, username, first_name, last_name, phone_number) that the
 * user can open in Excel or share, and best-effort mirrors the same rows
 * into Supabase's mayogram_export_users table so exports are tracked server
 * side too. Phone numbers will be blank for almost everyone - Telegram's
 * server only ever returns a user's phone to accounts that already have
 * them as a mutual contact; this is not something the client can change.
 */
package org.telegram.messenger;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MayogramExportController {

    private MayogramExportController() {
    }

    public interface ExportCallback {
        void onDone(File csvFile);
        void onError();
    }

    /**
     * Call on the UI thread. First makes sure every follower id is loaded
     * from local storage into the in-memory user cache (ids cached by a
     * previous app run are otherwise invisible until the next full rescan -
     * see MayogramFollowersController.ensureUsersCached), then builds the
     * CSV off the UI thread, fires a best-effort Supabase upload, and calls
     * back on the UI thread. A follower id that's still unresolvable (never
     * actually cached) is still included in the CSV, just with blank
     * username/name/phone columns, so no id is silently dropped.
     */
    public static void exportFollowersToCsv(int account, Collection<Long> followerIds, ExportCallback callback) {
        ArrayList<Long> ids = new ArrayList<>(followerIds);
        MayogramFollowersController.ensureUsersCached(account, ids, () -> Utilities.globalQueue.postRunnable(() -> {
            List<TLRPC.User> users = new ArrayList<>();
            MessagesController controller = MessagesController.getInstance(account);
            for (Long id : ids) {
                TLRPC.User user = controller.getUser(id);
                if (user == null) {
                    user = new TLRPC.TL_user();
                    user.id = id;
                }
                users.add(user);
            }

            File file;
            try {
                file = writeCsv(users);
            } catch (Exception e) {
                FileLog.e(e);
                AndroidUtilities.runOnUIThread(callback::onError);
                return;
            }

            long ownerId = UserConfig.getInstance(account).getClientUserId();
            uploadToSupabase(ownerId, users);

            AndroidUtilities.runOnUIThread(() -> callback.onDone(file));
        }));
    }

    private static File writeCsv(List<TLRPC.User> users) throws Exception {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US).format(new Date());
        File file = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), "mayogram_followers_" + timestamp + ".csv");
        OutputStream out = new FileOutputStream(file);
        try {
            out.write("telegram_userid,username,first_name,last_name,phone_number\n".getBytes(StandardCharsets.UTF_8));
            for (TLRPC.User user : users) {
                out.write(csvRow(
                        String.valueOf(user.id),
                        UserObject.getPublicUsername(user),
                        user.first_name,
                        user.last_name,
                        user.phone
                ).getBytes(StandardCharsets.UTF_8));
            }
        } finally {
            out.close();
        }
        return file;
    }

    private static String csvRow(String... values) {
        StringBuilder row = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                row.append(',');
            }
            row.append(csvEscape(values[i]));
        }
        row.append('\n');
        return row.toString();
    }

    private static String csvEscape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private static void uploadToSupabase(long ownerId, List<TLRPC.User> users) {
        if (android.text.TextUtils.isEmpty(BuildConfig.SUPABASE_URL) || android.text.TextUtils.isEmpty(BuildConfig.SUPABASE_ANON_KEY) || users.isEmpty()) {
            return;
        }
        try {
            JSONArray rows = new JSONArray();
            for (TLRPC.User user : users) {
                JSONObject row = new JSONObject();
                row.put("telegram_user_id", user.id);
                row.put("username", UserObject.getPublicUsername(user) != null ? UserObject.getPublicUsername(user) : "");
                row.put("first_name", user.first_name != null ? user.first_name : "");
                row.put("last_name", user.last_name != null ? user.last_name : "");
                row.put("phone", user.phone != null ? user.phone : "");
                rows.put(row);
            }

            // Supabase RPC calls have a practical payload size limit, so send
            // in batches rather than one huge request for accounts with many
            // thousands of followers.
            int batchSize = 500;
            for (int start = 0; start < rows.length(); start += batchSize) {
                JSONArray batch = new JSONArray();
                for (int i = start; i < Math.min(start + batchSize, rows.length()); i++) {
                    batch.put(rows.get(i));
                }
                postExportBatch(ownerId, batch);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private static void postExportBatch(long ownerId, JSONArray batch) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(BuildConfig.SUPABASE_URL + "/rest/v1/rpc/record_mayogram_export_users");
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY);
            connection.setRequestProperty("Authorization", "Bearer " + BuildConfig.SUPABASE_ANON_KEY);

            JSONObject body = new JSONObject();
            body.put("p_exported_by_user_id", ownerId);
            body.put("p_rows", batch);

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
    }
}
