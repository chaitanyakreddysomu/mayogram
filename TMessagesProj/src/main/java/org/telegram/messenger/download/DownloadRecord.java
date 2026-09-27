package org.telegram.messenger.download;

/** One row of the Mayogram download queue/history. Plain data holder. */
public class DownloadRecord {

    public static final int STATUS_QUEUED = 0;
    public static final int STATUS_DOWNLOADING = 1;
    public static final int STATUS_PAUSED = 2;
    public static final int STATUS_COMPLETED = 3;
    public static final int STATUS_FAILED = 4;

    public long id;
    public int account;
    public long dialogId;
    public String dialogTitle;
    public int messageId;
    public DownloadCategory category;
    public String fileName;
    public String mimeType;
    public long totalSize;
    public long downloadedSize;
    public int status;
    public String savedUri;
    /** Absolute path to Telegram's internal cache copy; kept so the file can be re-uploaded later (share to another chat) without re-downloading. */
    public String internalPath;
    public long createdAt;
    public long updatedAt;

    /** Not persisted: only valid while this session's queue holds the item. */
    public transient float progress;
}
