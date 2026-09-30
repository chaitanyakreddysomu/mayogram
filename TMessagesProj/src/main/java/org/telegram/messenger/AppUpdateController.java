/*
 * Mayogram in-app updater.
 *
 * Reads a small version.json from a static host (GitHub raw), and if a newer
 * version is listed, downloads the APK and hands it to the system installer.
 *
 * version.json:
 * {
 *   "latestVersion": "1.1",
 *   "downloadUrl": "https://github.com/<user>/<repo>/releases/download/v1.1/app.apk",
 *   "changelog": "- Fixed bugs\n- New features"
 * }
 */

package org.telegram.messenger;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.core.content.FileProvider;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class AppUpdateController {

    public static final String VERSION_URL = "https://raw.githubusercontent.com/chaitanyakreddysomu/mayogram/main/version.json";

    public static class UpdateInfo {
        public String version;
        public String downloadUrl;
        public String changelog;
    }

    public interface CheckCallback {
        // info == null && !error -> already up to date
        void onResult(UpdateInfo info, boolean error);
    }

    public interface DownloadCallback {
        void onProgress(int percent);
        void onDone(File file);
        void onError();
    }

    private AppUpdateController() {
    }

    public static void check(CheckCallback callback) {
        Utilities.globalQueue.postRunnable(() -> {
            UpdateInfo info = null;
            boolean error = false;
            try {
                // cache-busting: raw.githubusercontent.com caches for a few minutes
                HttpURLConnection c = (HttpURLConnection) new URL(VERSION_URL + "?t=" + System.currentTimeMillis()).openConnection();
                c.setConnectTimeout(10000);
                c.setReadTimeout(10000);
                c.setUseCaches(false);
                if (c.getResponseCode() != 200) {
                    throw new Exception("http " + c.getResponseCode());
                }
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                try (InputStream in = c.getInputStream()) {
                    byte[] buf = new byte[4096];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n);
                    }
                }
                JSONObject json = new JSONObject(new String(out.toByteArray(), StandardCharsets.UTF_8));
                String latest = json.getString("latestVersion");
                if (compareVersions(latest, BuildVars.BUILD_VERSION_STRING) > 0) {
                    info = new UpdateInfo();
                    info.version = latest;
                    info.downloadUrl = json.getString("downloadUrl");
                    info.changelog = json.optString("changelog", "");
                }
            } catch (Throwable e) {
                FileLog.e(e);
                error = true;
            }
            final UpdateInfo resultInfo = info;
            final boolean resultError = error;
            AndroidUtilities.runOnUIThread(() -> callback.onResult(resultInfo, resultError));
        });
    }

    /** Numeric, dot-separated compare ("1.10" > "1.9"). */
    public static int compareVersions(String a, String b) {
        String[] pa = a.trim().split("\.");
        String[] pb = b.trim().split("\.");
        for (int i = 0; i < Math.max(pa.length, pb.length); i++) {
            int va = i < pa.length ? parse(pa[i]) : 0;
            int vb = i < pb.length ? parse(pb[i]) : 0;
            if (va != vb) {
                return Integer.compare(va, vb);
            }
        }
        return 0;
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return 0;
        }
    }

    public static void download(UpdateInfo info, DownloadCallback callback) {
        Utilities.globalQueue.postRunnable(() -> {
            File dir = new File(ApplicationLoader.applicationContext.getFilesDir(), "cache");
            dir.mkdirs();
            File file = new File(dir, "mayogram_update.apk");
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(info.downloadUrl).openConnection();
                c.setConnectTimeout(15000);
                c.setReadTimeout(15000);
                c.setInstanceFollowRedirects(true);
                if (c.getResponseCode() != 200) {
                    throw new Exception("http " + c.getResponseCode());
                }
                long total = c.getContentLengthLong();
                long read = 0;
                int lastPercent = -1;
                try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(file)) {
                    byte[] buf = new byte[32 * 1024];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n);
                        read += n;
                        if (total > 0) {
                            int percent = (int) (read * 100 / total);
                            if (percent != lastPercent) {
                                lastPercent = percent;
                                AndroidUtilities.runOnUIThread(() -> callback.onProgress(percent));
                            }
                        }
                    }
                }
                AndroidUtilities.runOnUIThread(() -> callback.onDone(file));
            } catch (Throwable e) {
                FileLog.e(e);
                file.delete();
                AndroidUtilities.runOnUIThread(callback::onError);
            }
        });
    }

    /** Returns false if the user first has to allow installs from this app (settings screen is opened). */
    public static boolean install(Activity activity, File file) {
        if (Build.VERSION.SDK_INT >= 26 && !activity.getPackageManager().canRequestPackageInstalls()) {
            Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(settings);
            return false;
        }
        Intent intent = new Intent(Intent.ACTION_VIEW);
        Uri uri = FileProvider.getUriForFile(activity, ApplicationLoader.getApplicationId() + ".provider", file);
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        activity.startActivity(intent);
        return true;
    }
}
