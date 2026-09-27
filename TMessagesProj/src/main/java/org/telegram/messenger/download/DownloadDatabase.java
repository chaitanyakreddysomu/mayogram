/*
 * Mayogram download manager - queue/history persistence.
 *
 * Deliberately a separate SQLite file (Android's own SQLiteOpenHelper, not
 * Telegram's custom native SQLite wrapper) rather than a new table in
 * MessagesStorage. That keeps this feature additive: no schema-version bump
 * on Telegram's own database, no risk to its migration path, and it can be
 * wiped independently ("clear download history") without touching messages.
 */

package org.telegram.messenger.download;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.telegram.messenger.ApplicationLoader;

import java.util.ArrayList;

public class DownloadDatabase extends SQLiteOpenHelper {

    private static final String DB_NAME = "mayo_downloads.db";
    private static final int DB_VERSION = 1;

    private static final String TABLE = "downloads";

    private static volatile DownloadDatabase instance;

    public static DownloadDatabase getInstance() {
        if (instance == null) {
            synchronized (DownloadDatabase.class) {
                if (instance == null) {
                    instance = new DownloadDatabase(ApplicationLoader.applicationContext);
                }
            }
        }
        return instance;
    }

    private DownloadDatabase(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "account INTEGER NOT NULL," +
                "dialog_id INTEGER NOT NULL," +
                "dialog_title TEXT," +
                "message_id INTEGER NOT NULL," +
                "category TEXT NOT NULL," +
                "file_name TEXT," +
                "mime_type TEXT," +
                "total_size INTEGER," +
                "downloaded_size INTEGER," +
                "status INTEGER NOT NULL," +
                "saved_uri TEXT," +
                "created_at INTEGER," +
                "updated_at INTEGER," +
                "UNIQUE(account, dialog_id, message_id) ON CONFLICT REPLACE" +
                ");");
        db.execSQL("CREATE INDEX idx_downloads_status ON " + TABLE + "(status);");
        db.execSQL("CREATE INDEX idx_downloads_dialog ON " + TABLE + "(account, dialog_id);");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // No prior version to migrate from yet.
    }

    private ContentValues toValues(DownloadRecord r) {
        ContentValues cv = new ContentValues();
        cv.put("account", r.account);
        cv.put("dialog_id", r.dialogId);
        cv.put("dialog_title", r.dialogTitle);
        cv.put("message_id", r.messageId);
        cv.put("category", r.category.name());
        cv.put("file_name", r.fileName);
        cv.put("mime_type", r.mimeType);
        cv.put("total_size", r.totalSize);
        cv.put("downloaded_size", r.downloadedSize);
        cv.put("status", r.status);
        cv.put("saved_uri", r.savedUri);
        cv.put("created_at", r.createdAt);
        cv.put("updated_at", r.updatedAt);
        return cv;
    }

    private DownloadRecord fromCursor(Cursor c) {
        DownloadRecord r = new DownloadRecord();
        r.id = c.getLong(c.getColumnIndexOrThrow("id"));
        r.account = c.getInt(c.getColumnIndexOrThrow("account"));
        r.dialogId = c.getLong(c.getColumnIndexOrThrow("dialog_id"));
        r.dialogTitle = c.getString(c.getColumnIndexOrThrow("dialog_title"));
        r.messageId = c.getInt(c.getColumnIndexOrThrow("message_id"));
        try {
            r.category = DownloadCategory.valueOf(c.getString(c.getColumnIndexOrThrow("category")));
        } catch (IllegalArgumentException e) {
            r.category = DownloadCategory.DOCUMENT;
        }
        r.fileName = c.getString(c.getColumnIndexOrThrow("file_name"));
        r.mimeType = c.getString(c.getColumnIndexOrThrow("mime_type"));
        r.totalSize = c.getLong(c.getColumnIndexOrThrow("total_size"));
        r.downloadedSize = c.getLong(c.getColumnIndexOrThrow("downloaded_size"));
        r.status = c.getInt(c.getColumnIndexOrThrow("status"));
        r.savedUri = c.getString(c.getColumnIndexOrThrow("saved_uri"));
        r.createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"));
        r.updatedAt = c.getLong(c.getColumnIndexOrThrow("updated_at"));
        return r;
    }

    /** Insert or, if (account, dialog, message) already exists, replace it (see UNIQUE above). */
    public synchronized long upsert(DownloadRecord r) {
        long id = getWritableDatabase().insertWithOnConflict(
                TABLE, null, toValues(r), SQLiteDatabase.CONFLICT_REPLACE);
        r.id = id;
        return id;
    }

    public synchronized void updateProgress(long id, long downloadedSize, long totalSize, int status) {
        ContentValues cv = new ContentValues();
        cv.put("downloaded_size", downloadedSize);
        if (totalSize > 0) {
            cv.put("total_size", totalSize);
        }
        cv.put("status", status);
        cv.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().update(TABLE, cv, "id=?", new String[]{String.valueOf(id)});
    }

    public synchronized void updateStatus(long id, int status) {
        ContentValues cv = new ContentValues();
        cv.put("status", status);
        cv.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().update(TABLE, cv, "id=?", new String[]{String.valueOf(id)});
    }

    public synchronized void markCompleted(long id, String savedUri) {
        ContentValues cv = new ContentValues();
        cv.put("status", DownloadRecord.STATUS_COMPLETED);
        cv.put("saved_uri", savedUri);
        cv.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().update(TABLE, cv, "id=?", new String[]{String.valueOf(id)});
    }

    public synchronized void delete(long id) {
        getWritableDatabase().delete(TABLE, "id=?", new String[]{String.valueOf(id)});
    }

    public synchronized void clearCompletedForAccount(int account) {
        getWritableDatabase().delete(TABLE, "account=? AND status=?",
                new String[]{String.valueOf(account), String.valueOf(DownloadRecord.STATUS_COMPLETED)});
    }

    public synchronized void clearForDialog(int account, long dialogId) {
        getWritableDatabase().delete(TABLE, "account=? AND dialog_id=?",
                new String[]{String.valueOf(account), String.valueOf(dialogId)});
    }

    public synchronized ArrayList<DownloadRecord> getAll(int account) {
        ArrayList<DownloadRecord> list = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT * FROM " + TABLE + " WHERE account=? ORDER BY created_at DESC",
                new String[]{String.valueOf(account)});
        try {
            while (c.moveToNext()) {
                list.add(fromCursor(c));
            }
        } finally {
            c.close();
        }
        return list;
    }

    public synchronized ArrayList<DownloadRecord> getActive(int account) {
        ArrayList<DownloadRecord> list = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT * FROM " + TABLE + " WHERE account=? AND status IN (?,?,?) ORDER BY created_at ASC",
                new String[]{String.valueOf(account),
                        String.valueOf(DownloadRecord.STATUS_QUEUED),
                        String.valueOf(DownloadRecord.STATUS_DOWNLOADING),
                        String.valueOf(DownloadRecord.STATUS_PAUSED)});
        try {
            while (c.moveToNext()) {
                list.add(fromCursor(c));
            }
        } finally {
            c.close();
        }
        return list;
    }

    public synchronized DownloadRecord findByMessage(int account, long dialogId, int messageId) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT * FROM " + TABLE + " WHERE account=? AND dialog_id=? AND message_id=? LIMIT 1",
                new String[]{String.valueOf(account), String.valueOf(dialogId), String.valueOf(messageId)});
        try {
            if (c.moveToFirst()) {
                return fromCursor(c);
            }
            return null;
        } finally {
            c.close();
        }
    }

    public synchronized StorageTotals getStorageTotals(int account) {
        StorageTotals totals = new StorageTotals();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT category, SUM(total_size) FROM " + TABLE +
                        " WHERE account=? AND status=? GROUP BY category",
                new String[]{String.valueOf(account), String.valueOf(DownloadRecord.STATUS_COMPLETED)});
        try {
            while (c.moveToNext()) {
                String cat = c.getString(0);
                long size = c.getLong(1);
                try {
                    totals.byCategory.put(DownloadCategory.valueOf(cat), size);
                } catch (IllegalArgumentException ignored) {
                }
            }
        } finally {
            c.close();
        }
        return totals;
    }

    public static class StorageTotals {
        public final java.util.EnumMap<DownloadCategory, Long> byCategory =
                new java.util.EnumMap<>(DownloadCategory.class);

        public long total() {
            long sum = 0;
            for (Long v : byCategory.values()) {
                sum += v;
            }
            return sum;
        }
    }
}
