package tn.amin.mpro2.messaging.history;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import tn.amin.mpro2.debug.Logger;
import tn.amin.mpro2.file.StorageConstants;

/**
 * SQLite-backed message history store.
 * <p>
 * Captures (from Messenger's process) are written through {@link HistoryProvider}
 * (a ContentProvider), so the data lands in MessengerPro's own private storage.
 * The module's Settings UI reads the DB directly via {@link #openDb()}.
 */
public final class MessageHistoryStore {
    private static final String TABLE = "messages";
    private static final Object LOCK = new Object();

    private MessageHistoryStore() {
    }

    public static void appendIncoming(Context context, String message, String messageId, String senderUserKey, long threadKey) {
        appendIncomingWithNames(context, message, messageId, senderUserKey, null, threadKey, null);
    }

    public static void appendIncomingWithNames(Context context, String message, String messageId, String senderUserKey,
                                               String senderName, long threadKey, String threadName) {
        append(context, message, messageId, senderUserKey, senderName, threadKey, threadName,
                MessageHistoryEntry.DIRECTION_INCOMING);
    }

    public static void appendOutgoing(Context context, String message, String messageId, long threadKey) {
        append(context, message, messageId, null, null, threadKey, null, MessageHistoryEntry.DIRECTION_OUTGOING);
    }

    private static void append(Context context, String message, String messageId, String senderUserKey,
                               String senderName, long threadKey, String threadName, String direction) {
        if (message == null || message.trim().isEmpty() || context == null) {
            return;
        }

        try {
            ContentValues values = new ContentValues();
            values.put("ts", System.currentTimeMillis());
            values.put("direction", direction);
            values.put("thread_key", threadKey);
            values.put("message_id", messageId);
            values.put("sender_key", senderUserKey);
            values.put("sender_name", senderName);
            values.put("thread_name", threadName);
            values.put("content", message);
            context.getContentResolver().insert(HistoryProvider.CONTENT_URI, values);
        } catch (Throwable t) {
            Logger.error("MessageHistoryStore: append failed: " + t.getMessage());
        }
    }

    public static void updateThreadName(long threadKey, String threadName) {
        if (threadKey <= 0 || threadName == null) {
            return;
        }
        String name = threadName.trim();
        if (name.isEmpty() || "Messenger".equalsIgnoreCase(name) || "Chats".equalsIgnoreCase(name)) {
            return;
        }

        synchronized (LOCK) {
            SQLiteDatabase db = openDb();
            if (db == null) return;
            try {
                ContentValues values = new ContentValues();
                values.put("thread_name", name);
                db.update(TABLE, values, "thread_key=?", new String[]{String.valueOf(threadKey)});
            } catch (Throwable t) {
                Logger.error(t);
            }
        }
    }

    public static String getThreadName(long threadKey) {
        if (threadKey <= 0) return null;
        synchronized (LOCK) {
            SQLiteDatabase db = openDb();
            if (db == null) return null;
            try (Cursor c = db.rawQuery(
                    "SELECT thread_name FROM " + TABLE + " WHERE thread_key=? AND thread_name IS NOT NULL ORDER BY id DESC LIMIT 1",
                    new String[]{String.valueOf(threadKey)})) {
                if (c.moveToFirst()) return c.isNull(0) ? null : c.getString(0);
            } catch (Throwable t) {
                Logger.error(t);
            }
        }
        return null;
    }

    public static List<MessageHistoryEntry> getRecentMessages(Long conversationThreadKey, int limit) {
        if (limit <= 0) return Collections.emptyList();
        synchronized (LOCK) {
            SQLiteDatabase db = openDb();
            if (db == null) return Collections.emptyList();
            ArrayList<MessageHistoryEntry> result = new ArrayList<>();
            try {
                String sql;
                String[] args;
                if (conversationThreadKey != null) {
                    sql = "SELECT ts,direction,thread_key,message_id,sender_key,sender_name,thread_name,content FROM "
                            + TABLE + " WHERE thread_key=? ORDER BY id DESC LIMIT ?";
                    args = new String[]{String.valueOf(conversationThreadKey), String.valueOf(limit)};
                } else {
                    sql = "SELECT ts,direction,thread_key,message_id,sender_key,sender_name,thread_name,content FROM "
                            + TABLE + " ORDER BY id DESC LIMIT ?";
                    args = new String[]{String.valueOf(limit)};
                }
                try (Cursor c = db.rawQuery(sql, args)) {
                    while (c.moveToNext()) result.add(readEntry(c));
                }
            } catch (Throwable t) {
                Logger.error(t);
            }
            Collections.reverse(result);
            return result;
        }
    }

    public static List<HistoryThreadInfo> getRecentThreads(int limit) {
        if (limit <= 0) return Collections.emptyList();
        synchronized (LOCK) {
            SQLiteDatabase db = openDb();
            if (db == null) return Collections.emptyList();
            ArrayList<HistoryThreadInfo> result = new ArrayList<>();
            try (Cursor c = db.rawQuery(
                    "SELECT thread_key, MAX(ts) AS last_ts, COUNT(*) AS cnt, "
                            + "MAX(CASE WHEN thread_name IS NOT NULL THEN thread_name END) AS tname, "
                            + "MAX(CASE WHEN sender_name IS NOT NULL THEN sender_name END) AS sname "
                            + "FROM " + TABLE + " WHERE thread_key > 0 GROUP BY thread_key "
                            + "ORDER BY last_ts DESC LIMIT ?",
                    new String[]{String.valueOf(limit)})) {
                while (c.moveToNext()) {
                    long threadKey = c.getLong(0);
                    long lastTs = c.getLong(1);
                    int count = c.getInt(2);
                    String threadName = c.isNull(3) ? null : c.getString(3);
                    String senderName = c.isNull(4) ? null : c.getString(4);
                    String name = (threadName != null && !threadName.isEmpty()) ? threadName : senderName;
                    result.add(new HistoryThreadInfo(threadKey, name, lastTs, count));
                }
            } catch (Throwable t) {
                Logger.error(t);
            }
            return result;
        }
    }

    public static void clear() {
        synchronized (LOCK) {
            SQLiteDatabase db = openDb();
            if (db == null) return;
            try {
                db.delete(TABLE, null, null);
            } catch (Throwable t) {
                Logger.error(t);
            }
        }
    }

    private static MessageHistoryEntry readEntry(Cursor c) {
        return new MessageHistoryEntry(
                c.getLong(0),
                c.getString(1),
                c.getLong(2),
                c.isNull(3) ? null : c.getString(3),
                c.isNull(4) ? null : c.getString(4),
                c.isNull(5) ? null : c.getString(5),
                c.isNull(6) ? null : c.getString(6),
                c.isNull(7) ? null : c.getString(7));
    }

    private static SQLiteDatabase openDb() {
        try {
            File file = StorageConstants.moduleHistoryDb;
            if (file.getParentFile() != null && !file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            return SQLiteDatabase.openOrCreateDatabase(file, null);
        } catch (Throwable t) {
            Logger.error("MessageHistoryStore: openDb failed: " + t.getMessage());
            return null;
        }
    }
}
