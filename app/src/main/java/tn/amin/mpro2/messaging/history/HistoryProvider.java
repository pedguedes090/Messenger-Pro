package tn.amin.mpro2.messaging.history;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

/**
 * Cross-process bridge: the hook (running inside Messenger's process) inserts captured
 * messages here; the module's own Settings process reads the SQLite DB. The DB lives in
 * MessengerPro's private storage, not Messenger's.
 */
public class HistoryProvider extends ContentProvider {
    public static final String AUTHORITY = "tn.amin.mpro2.history";
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/messages");

    private static final String TABLE = "messages";
    private static final int MAX_ENTRIES = 5000;
    private SQLiteDatabase mDb;

    @Override
    public boolean onCreate() {
        try {
            java.io.File dbFile = getContext().getDatabasePath("mpro_history.db");
            if (dbFile.getParentFile() != null && !dbFile.getParentFile().exists()) {
                dbFile.getParentFile().mkdirs();
            }
            mDb = SQLiteDatabase.openOrCreateDatabase(dbFile, null);
            mDb.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE + " ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "ts INTEGER,"
                    + "direction TEXT,"
                    + "thread_key INTEGER,"
                    + "message_id TEXT,"
                    + "sender_key TEXT,"
                    + "sender_name TEXT,"
                    + "thread_name TEXT,"
                    + "content TEXT)");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        try {
            String messageId = values.getAsString("message_id");
            if (messageId != null && !messageId.isEmpty()) {
                Cursor c = mDb.rawQuery("SELECT 1 FROM " + TABLE + " WHERE message_id=? LIMIT 1",
                        new String[]{messageId});
                boolean exists = c.moveToFirst();
                c.close();
                if (exists) {
                    updateNamesIfPresent(values, messageId);
                    return Uri.withAppendedPath(CONTENT_URI, messageId);
                }
            }

            long id = mDb.insert(TABLE, null, values);
            pruneIfNeeded();
            return Uri.withAppendedPath(CONTENT_URI, String.valueOf(id));
        } catch (Throwable t) {
            return null;
        }
    }

    private void updateNamesIfPresent(ContentValues values, String messageId) {
        ContentValues update = new ContentValues();
        String senderName = values.getAsString("sender_name");
        String threadName = values.getAsString("thread_name");
        if (senderName != null && !senderName.isEmpty()) update.put("sender_name", senderName);
        if (threadName != null && !threadName.isEmpty()) update.put("thread_name", threadName);
        if (update.size() > 0) {
            mDb.update(TABLE, update, "message_id=?", new String[]{messageId});
        }
    }

    private void pruneIfNeeded() {
        try (Cursor c = mDb.rawQuery("SELECT COUNT(*) FROM " + TABLE, null)) {
            if (c.moveToFirst() && c.getInt(0) > MAX_ENTRIES) {
                int excess = c.getInt(0) - MAX_ENTRIES;
                mDb.execSQL("DELETE FROM " + TABLE + " WHERE id IN "
                        + "(SELECT id FROM " + TABLE + " ORDER BY id ASC LIMIT " + excess + ")");
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return mDb.query(TABLE, projection, selection, selectionArgs, null, null, sortOrder);
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return mDb.update(TABLE, values, selection, selectionArgs);
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return mDb.delete(TABLE, selection, selectionArgs);
    }

    @Override
    public String getType(Uri uri) {
        return "vnd.android.cursor.dir/tn.amin.mpro2.history.messages";
    }
}
