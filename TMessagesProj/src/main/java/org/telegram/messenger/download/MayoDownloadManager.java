/*
 * Mayogram advanced download manager - core engine.
 *
 * This is a management layer on top of Telegram's existing FileLoader /
 * FileLoadOperation, not a replacement for it. FileLoadOperation already:
 *   - resumes interrupted downloads from a partial .temp file using
 *     byte-range tracking (see FileLoadOperation.notLoadedBytesRanges),
 *   - runs a priority queue (FileLoader.PRIORITY_*),
 *   - respects Telegram's own request pacing / server limits.
 * "Pause" here means FileLoader.cancelLoadFile(..., deleteFile=false), which
 * keeps that partial file so a later "resume" continues the same download
 * rather than restarting it - this client does not, and cannot, bypass
 * Telegram's server-side transfer limits.
 *
 * Once a file finishes in Telegram's internal cache, this class copies it
 * into the public Downloads collection under Download/Mayogram/<chat>/<type>/
 * via MediaStore (scoped storage, same mechanism MediaController.saveFile
 * already uses for "save to gallery" - see MediaController.saveFileInternal).
 */

package org.telegram.messenger.download;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.MimeTypeMap;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DownloadController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class MayoDownloadManager {

    /**
     * Fired on the UI thread whenever any tracked download's state changes. args[0] = DownloadRecord.
     * NotificationCenter.totalEvents is private with no accessor, so this uses a fixed id far above
     * any id Telegram itself assigns (its own count is a few hundred as of this fork), reserved for
     * Mayogram's own events rather than trying to chain off Telegram's counter.
     */
    public static final int EVENT_DOWNLOAD_UPDATED = 100001;

    private static final MayoDownloadManager[] instances = new MayoDownloadManager[UserConfig.MAX_ACCOUNT_COUNT];
    private static final long PROGRESS_DB_WRITE_INTERVAL_MS = 800;

    private final int currentAccount;
    private final DownloadDatabase db = DownloadDatabase.getInstance();

    /** fileName -> the live entry, for downloads active in this process. */
    private final Map<String, DownloadRecord> activeByFileName = new ConcurrentHashMap<>();
    /** fileName -> the live message, needed to re-issue loadFile() on resume. */
    private final Map<String, MessageObject> messageByFileName = new ConcurrentHashMap<>();
    /** fileName -> our registered listener, kept as a strong ref (DownloadController holds only a WeakReference). */
    private final Map<String, DownloadController.FileDownloadProgressListener> listeners = new ConcurrentHashMap<>();
    /** fileName currently being paused deliberately, so the failure callback does not mark it FAILED. */
    private final Set<String> pausing = new HashSet<>();
    private final Map<String, Long> lastProgressWriteAt = new HashMap<>();

    public static MayoDownloadManager getInstance(int account) {
        MayoDownloadManager instance = instances[account];
        if (instance == null) {
            synchronized (MayoDownloadManager.class) {
                instance = instances[account];
                if (instance == null) {
                    instance = instances[account] = new MayoDownloadManager(account);
                }
            }
        }
        return instance;
    }

    private MayoDownloadManager(int account) {
        currentAccount = account;
    }

    // ------------------------------------------------------------------
    // Queue control
    // ------------------------------------------------------------------

    /** Enqueues a message's media for download. Returns null if the message carries no downloadable media. */
    public DownloadRecord enqueue(MessageObject messageObject, long dialogId, String dialogTitle) {
        if (messageObject == null) {
            return null;
        }

        TLRPC.Document document = messageObject.getDocument();
        TLRPC.PhotoSize photoSize = null;
        TLRPC.Photo photo = null;
        String fileName;
        long size;

        if (document != null) {
            fileName = FileLoader.getAttachFileName(document);
            size = document.size;
        } else if (messageObject.messageOwner != null && messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaPhoto
                && messageObject.messageOwner.media.photo != null) {
            photo = messageObject.messageOwner.media.photo;
            photoSize = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, Integer.MAX_VALUE);
            if (photoSize == null) {
                return null;
            }
            fileName = FileLoader.getAttachFileName(photoSize);
            size = photoSize.size;
        } else {
            return null;
        }

        DownloadRecord existing = db.findByMessage(currentAccount, dialogId, messageObject.getId());
        if (existing != null && existing.status == DownloadRecord.STATUS_COMPLETED) {
            return existing;
        }

        DownloadCategory category = DownloadCategory.of(messageObject);
        DownloadRecord record = existing != null ? existing : new DownloadRecord();
        record.account = currentAccount;
        record.dialogId = dialogId;
        record.dialogTitle = dialogTitle;
        record.messageId = messageObject.getId();
        record.category = category;
        record.fileName = fileName;
        record.mimeType = document != null ? document.mime_type : "image/jpeg";
        record.totalSize = size;
        record.downloadedSize = 0;
        record.status = DownloadRecord.STATUS_QUEUED;
        long now = System.currentTimeMillis();
        record.createdAt = existing != null ? existing.createdAt : now;
        record.updatedAt = now;
        db.upsert(record);

        activeByFileName.put(fileName, record);
        messageByFileName.put(fileName, messageObject);
        startLoad(record, messageObject, document, photoSize);
        return record;
    }

    private void startLoad(DownloadRecord record, MessageObject messageObject, TLRPC.Document document, TLRPC.PhotoSize photoSize) {
        registerListener(record);
        record.status = DownloadRecord.STATUS_DOWNLOADING;
        db.updateStatus(record.id, DownloadRecord.STATUS_DOWNLOADING);
        notifyUpdated(record);

        if (document != null) {
            FileLoader.getInstance(currentAccount).loadFile(document, messageObject, FileLoader.PRIORITY_NORMAL, 0);
        } else if (photoSize != null) {
            FileLoader.getInstance(currentAccount).loadFile(
                    org.telegram.messenger.ImageLocation.getForObject(photoSize, messageObject.messageOwner.media.photo),
                    messageObject, "jpg", FileLoader.PRIORITY_NORMAL, 0);
        }
    }

    public void pause(DownloadRecord record) {
        if (record == null || record.status != DownloadRecord.STATUS_DOWNLOADING) {
            return;
        }
        pausing.add(record.fileName);
        MessageObject messageObject = messageByFileName.get(record.fileName);
        TLRPC.Document document = messageObject != null ? messageObject.getDocument() : null;
        if (document != null) {
            FileLoader.getInstance(currentAccount).cancelLoadFile(document, false);
        } else {
            FileLoader.getInstance(currentAccount).cancelLoadFile(record.fileName);
        }
        record.status = DownloadRecord.STATUS_PAUSED;
        db.updateStatus(record.id, DownloadRecord.STATUS_PAUSED);
        notifyUpdated(record);
    }

    /** Resumes a paused/failed download. Requires the message still be resolvable in this session. */
    public void resume(DownloadRecord record) {
        if (record == null) {
            return;
        }
        MessageObject messageObject = messageByFileName.get(record.fileName);
        if (messageObject == null) {
            FileLog.d("Mayogram: cannot resume " + record.fileName + " - message not held in this session");
            return;
        }
        startLoad(record, messageObject, messageObject.getDocument(),
                messageObject.getDocument() == null
                        ? FileLoader.getClosestPhotoSizeWithSize(messageObject.messageOwner.media.photo.sizes, Integer.MAX_VALUE)
                        : null);
    }

    public void cancel(DownloadRecord record, boolean deleteFile) {
        if (record == null) {
            return;
        }
        pausing.remove(record.fileName);
        MessageObject messageObject = messageByFileName.get(record.fileName);
        TLRPC.Document document = messageObject != null ? messageObject.getDocument() : null;
        if (document != null) {
            FileLoader.getInstance(currentAccount).cancelLoadFile(document, deleteFile);
        } else {
            FileLoader.getInstance(currentAccount).cancelLoadFile(record.fileName);
        }
        unregisterListener(record.fileName);
        db.delete(record.id);
        activeByFileName.remove(record.fileName);
        messageByFileName.remove(record.fileName);
        record.status = DownloadRecord.STATUS_FAILED;
        notifyUpdated(record);
    }

    public java.util.ArrayList<DownloadRecord> getAll() {
        return db.getAll(currentAccount);
    }

    public java.util.ArrayList<DownloadRecord> getActive() {
        return db.getActive(currentAccount);
    }

    public DownloadDatabase.StorageTotals getStorageTotals() {
        return db.getStorageTotals(currentAccount);
    }

    public void clearCompleted() {
        db.clearCompletedForAccount(currentAccount);
    }

    // ------------------------------------------------------------------
    // FileLoader observation
    // ------------------------------------------------------------------

    private void registerListener(DownloadRecord record) {
        String fileName = record.fileName;
        if (listeners.containsKey(fileName)) {
            return;
        }
        final int tag = DownloadController.getInstance(currentAccount).generateObserverTag();
        DownloadController.FileDownloadProgressListener listener = new DownloadController.FileDownloadProgressListener() {
            @Override
            public void onFailedDownload(String name, boolean canceled) {
                onDownloadFailed(name, canceled);
            }

            @Override
            public void onSuccessDownload(String name) {
                onDownloadSucceeded(name);
            }

            @Override
            public void onProgressDownload(String name, long downloadSize, long totalSize) {
                onDownloadProgress(name, downloadSize, totalSize);
            }

            @Override
            public void onProgressUpload(String name, long downloadSize, long totalSize, boolean isEncrypted) {
                // Downloads only; uploads are out of scope for this manager.
            }

            @Override
            public int getObserverTag() {
                return tag;
            }
        };
        listeners.put(fileName, listener);
        MessageObject messageObject = messageByFileName.get(fileName);
        DownloadController.getInstance(currentAccount).addLoadingFileObserver(fileName, messageObject, listener);
    }

    private void unregisterListener(String fileName) {
        DownloadController.FileDownloadProgressListener listener = listeners.remove(fileName);
        if (listener != null) {
            DownloadController.getInstance(currentAccount).removeLoadingFileObserver(listener);
        }
        pausing.remove(fileName);
        lastProgressWriteAt.remove(fileName);
    }

    private void onDownloadProgress(String fileName, long downloadedSize, long totalSize) {
        DownloadRecord record = activeByFileName.get(fileName);
        if (record == null) {
            return;
        }
        record.downloadedSize = downloadedSize;
        if (totalSize > 0) {
            record.totalSize = totalSize;
            record.progress = downloadedSize / (float) totalSize;
        }
        long now = System.currentTimeMillis();
        Long last = lastProgressWriteAt.get(fileName);
        if (last == null || now - last >= PROGRESS_DB_WRITE_INTERVAL_MS) {
            lastProgressWriteAt.put(fileName, now);
            db.updateProgress(record.id, downloadedSize, totalSize, DownloadRecord.STATUS_DOWNLOADING);
        }
        notifyUpdated(record);
    }

    private void onDownloadFailed(String fileName, boolean canceled) {
        DownloadRecord record = activeByFileName.get(fileName);
        unregisterListener(fileName);
        if (record == null) {
            return;
        }
        boolean wasPausing = pausing.remove(fileName);
        record.status = wasPausing ? DownloadRecord.STATUS_PAUSED : DownloadRecord.STATUS_FAILED;
        db.updateStatus(record.id, record.status);
        notifyUpdated(record);
    }

    private void onDownloadSucceeded(String fileName) {
        DownloadRecord record = activeByFileName.get(fileName);
        unregisterListener(fileName);
        if (record == null) {
            return;
        }
        MessageObject messageObject = messageByFileName.remove(fileName);
        activeByFileName.remove(fileName);

        Utilities.globalQueue.postRunnable(() -> {
            String uri = copyToPublicDownloads(record, messageObject);
            record.status = DownloadRecord.STATUS_COMPLETED;
            record.savedUri = uri;
            db.markCompleted(record.id, uri);
            AndroidUtilities.runOnUIThread(() -> notifyUpdated(record));
        });
    }

    // ------------------------------------------------------------------
    // Organized storage: Download/Mayogram/<chat>/<category>/<file>
    // ------------------------------------------------------------------

    private String copyToPublicDownloads(DownloadRecord record, MessageObject messageObject) {
        try {
            File source = resolveInternalFile(record, messageObject);
            if (source == null || !source.exists()) {
                FileLog.e("Mayogram: source file missing for " + record.fileName);
                return null;
            }

            String safeChatName = sanitizeForPath(record.dialogTitle != null ? record.dialogTitle : "Unknown chat");
            String displayName = buildDisplayName(record, source);
            String relativeDir = "Mayogram" + File.separator + safeChatName + File.separator + record.category.folderName;

            String extension = FileLoader.getFileExtension(source);
            String mimeType = extension != null
                    ? MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                    : record.mimeType;

            Context context = ApplicationLoader.applicationContext;
            Uri destUri;
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, displayName);
                values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
                values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS + File.separator + relativeDir);
                destUri = context.getContentResolver().insert(
                        MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values);
                if (destUri == null) {
                    return null;
                }
                try (FileInputStream in = new FileInputStream(source);
                     OutputStream out = context.getContentResolver().openOutputStream(destUri)) {
                    AndroidUtilities.copyFile(in, out);
                }
            } else {
                // Pre-scoped-storage: NO_SCOPED_STORAGE already gates this path elsewhere in
                // BuildVars for the app's existing save-to-gallery feature; same assumption here.
                File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), relativeDir);
                //noinspection ResultOfMethodCallIgnored
                dir.mkdirs();
                File destFile = new File(dir, displayName);
                try (FileInputStream in = new FileInputStream(source);
                     OutputStream out = new java.io.FileOutputStream(destFile)) {
                    AndroidUtilities.copyFile(in, out);
                }
                destUri = Uri.fromFile(destFile);
                context.sendBroadcast(new android.content.Intent(
                        android.content.Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, destUri));
            }
            return destUri.toString();
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    private File resolveInternalFile(DownloadRecord record, MessageObject messageObject) {
        if (messageObject != null && messageObject.getDocument() != null) {
            return FileLoader.getInstance(currentAccount).getPathToAttach(messageObject.getDocument(), true);
        }
        if (messageObject != null && messageObject.messageOwner != null
                && messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaPhoto
                && messageObject.messageOwner.media.photo != null) {
            TLRPC.PhotoSize size = FileLoader.getClosestPhotoSizeWithSize(
                    messageObject.messageOwner.media.photo.sizes, Integer.MAX_VALUE);
            if (size != null) {
                return FileLoader.getInstance(currentAccount).getPathToAttach(size, true);
            }
        }
        return null;
    }

    private String buildDisplayName(DownloadRecord record, File source) {
        if (record.fileName != null && record.fileName.contains(".") && !record.fileName.startsWith(source.getName())) {
            // FileLoader's internal name is a hash, not a human-readable one; prefer the source's
            // extension but keep downloads from colliding by prefixing the message id.
        }
        String base = source.getName();
        int dot = base.lastIndexOf('.');
        String ext = dot >= 0 ? base.substring(dot) : "";
        return "msg" + record.messageId + ext;
    }

    private String sanitizeForPath(String name) {
        String cleaned = name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (cleaned.isEmpty()) {
            cleaned = "Unknown chat";
        }
        return cleaned.length() > 80 ? cleaned.substring(0, 80) : cleaned;
    }

    private void notifyUpdated(DownloadRecord record) {
        AndroidUtilities.runOnUIThread(() ->
                NotificationCenter.getInstance(currentAccount).postNotificationName(EVENT_DOWNLOAD_UPDATED, record));
    }
}
