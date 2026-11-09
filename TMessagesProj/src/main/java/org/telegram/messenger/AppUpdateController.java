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
 *
 * The beta build type (BuildConfig.IS_BETA) checks a separate file/channel
 * (version-beta.json on the beta branch) instead, so pushing beta builds
 * never notifies release users and vice versa.
 */

package org.telegram.messenger;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class AppUpdateController {

    public static final String VERSION_URL = BuildConfig.IS_BETA
            ? "https://raw.githubusercontent.com/chaitanyakreddysomu/mayogram/beta/version-beta.json"
            : "https://raw.githubusercontent.com/chaitanyakreddysomu/mayogram/main/version.json";

    private static final String PREF_DOWNLOADED_VERSION = "mayo_update_downloaded_version";
    private static final int DOWNLOAD_ATTEMPTS = 3;

    public static class UpdateInfo {
        public String version;
        public String downloadUrl;
        public String changelog;
        public java.util.List<String> features;
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

    // Background download state, independent of any UI. A download started
    // from one AppUpdateSheet keeps running (on Utilities.globalQueue) after
    // that sheet is dismissed; reopening the sheet attaches as a listener
    // instead of starting a second download, and reports the real progress
    // instead of resetting to 0%.
    private static volatile boolean downloading;
    private static volatile int downloadPercent;
    private static volatile String downloadingVersion;
    private static final List<DownloadCallback> listeners = new CopyOnWriteArrayList<>();

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
                    org.json.JSONArray features = json.optJSONArray("features");
                    if (features != null && features.length() > 0) {
                        info.features = new java.util.ArrayList<>();
                        for (int i = 0; i < features.length(); i++) {
                            String f = features.optString(i);
                            if (!f.trim().isEmpty()) {
                                info.features.add(f.trim());
                            }
                        }
                    }
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
        String[] pa = a.trim().split("\\.");
        String[] pb = b.trim().split("\\.");
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

    private static SharedPreferences prefs() {
        return MessagesController.getGlobalMainSettings();
    }

    /** Per-version file name, so a leftover/partial file never gets mistaken for a different release. */
    private static File apkFile(String version) {
        File dir = new File(ApplicationLoader.applicationContext.getFilesDir(), "cache");
        dir.mkdirs();
        String safe = version == null ? "unknown" : version.replaceAll("[^0-9A-Za-z._-]", "_");
        return new File(dir, "mayogram_update_" + safe + ".apk");
    }

    /** True once that version's APK has been fully downloaded and verified complete. */
    public static boolean isDownloaded(String version) {
        if (version == null) {
            return false;
        }
        File file = apkFile(version);
        return version.equals(prefs().getString(PREF_DOWNLOADED_VERSION, null)) && file.exists() && file.length() > 0;
    }

    public static File getDownloadedFile(String version) {
        return apkFile(version);
    }

    public static boolean isDownloading(String version) {
        return downloading && version != null && version.equals(downloadingVersion);
    }

    public static int getDownloadPercent() {
        return downloadPercent;
    }

    /**
     * Starts (or attaches to an already-running) download for this version.
     * - Already fully downloaded -> onDone immediately, no network request.
     * - Already downloading (e.g. a previous sheet instance kicked it off) ->
     *   registers as a listener and reports the current percent, the running
     *   download is left alone so closing/reopening the sheet never restarts it.
     * - Otherwise starts a new background download.
     */
    public static void download(UpdateInfo info, DownloadCallback callback) {
        if (isDownloaded(info.version)) {
            callback.onDone(apkFile(info.version));
            return;
        }
        listeners.add(callback);
        if (downloading && info.version.equals(downloadingVersion)) {
            callback.onProgress(downloadPercent);
            return;
        }
        downloading = true;
        downloadingVersion = info.version;
        downloadPercent = 0;
        Utilities.globalQueue.postRunnable(() -> runDownload(info));
    }

    /** Detaches a UI callback (e.g. on sheet dismiss) without touching the background download itself. */
    public static void removeListener(DownloadCallback callback) {
        listeners.remove(callback);
    }

    private static void notifyProgress(int percent) {
        downloadPercent = percent;
        AndroidUtilities.runOnUIThread(() -> {
            for (DownloadCallback l : listeners) {
                l.onProgress(percent);
            }
        });
    }

    private static void notifyDone(File file, String version) {
        downloading = false;
        downloadingVersion = null;
        downloadPercent = 100;
        prefs().edit().putString(PREF_DOWNLOADED_VERSION, version).commit();
        AndroidUtilities.runOnUIThread(() -> {
            for (DownloadCallback l : listeners) {
                l.onDone(file);
            }
            listeners.clear();
        });
    }

    private static void notifyError() {
        downloading = false;
        downloadingVersion = null;
        AndroidUtilities.runOnUIThread(() -> {
            for (DownloadCallback l : listeners) {
                l.onError();
            }
            listeners.clear();
        });
    }

    /**
     * Downloads with resume support: a 504/timeout/dropped connection restarts
     * from the bytes already on disk (via a Range request) instead of from
     * zero, retried up to DOWNLOAD_ATTEMPTS times before reporting failure -
     * the same kind of flaky-host retry already used for CI submodule/JDK
     * fetches, applied to the APK download itself.
     */
    private static void runDownload(UpdateInfo info) {
        File file = apkFile(info.version);
        for (int attempt = 1; attempt <= DOWNLOAD_ATTEMPTS; attempt++) {
            try {
                long existing = file.exists() ? file.length() : 0;
                HttpURLConnection c = (HttpURLConnection) new URL(info.downloadUrl).openConnection();
                c.setConnectTimeout(15000);
                c.setReadTimeout(15000);
                c.setInstanceFollowRedirects(true);
                if (existing > 0) {
                    c.setRequestProperty("Range", "bytes=" + existing + "-");
                }
                int code = c.getResponseCode();
                boolean resumed = existing > 0 && code == HttpURLConnection.HTTP_PARTIAL;
                if (!resumed) {
                    if (code != HttpURLConnection.HTTP_OK) {
                        throw new Exception("http " + code);
                    }
                    // Server ignored the Range request (or there was nothing to resume) - start over.
                    existing = 0;
                }
                long contentLength = c.getContentLengthLong();
                long total = contentLength > 0 ? existing + contentLength : -1;
                long read = existing;
                int lastPercent = total > 0 ? (int) (existing * 100 / total) : -1;
                try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(file, resumed)) {
                    byte[] buf = new byte[32 * 1024];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n);
                        read += n;
                        if (total > 0) {
                            int percent = (int) (read * 100 / total);
                            if (percent != lastPercent) {
                                lastPercent = percent;
                                notifyProgress(percent);
                            }
                        }
                    }
                }
                notifyProgress(100);
                notifyDone(file, info.version);
                return;
            } catch (Throwable e) {
                FileLog.e(e);
                if (attempt >= DOWNLOAD_ATTEMPTS) {
                    notifyError();
                    return;
                }
                try {
                    Thread.sleep(2000L * attempt);
                } catch (InterruptedException ignored) {
                }
            }
        }
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
