package tn.amin.mpro2.messaging.history;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import tn.amin.mpro2.debug.Logger;
import tn.amin.mpro2.file.StorageConstants;

public final class MessageHistoryStore {
    private static final Object LOCK = new Object();

    private static final String HISTORY_FILE_NAME = "mpro_message_history.jsonl";
    private static final String THREAD_NAMES_FILE_NAME = "mpro_thread_names.json";
    private static final int MAX_ENTRIES = 5000;
    private static final int PRUNE_EVERY_APPEND = 40;

    private static int sPendingPruneCounter = 0;
    private static volatile SnapshotData sInMemorySnapshot = null;

    private MessageHistoryStore() {
    }

    public static void appendIncoming(String message,
                                      String messageId,
                                      String senderUserKey,
                                      long conversationThreadKey) {
        append(new MessageHistoryEntry(
                System.currentTimeMillis(),
                MessageHistoryEntry.DIRECTION_INCOMING,
                conversationThreadKey,
                messageId,
                senderUserKey,
                message
        ));
    }

    public static void appendOutgoing(String message,
                                      String messageId,
                                      long conversationThreadKey) {
        append(new MessageHistoryEntry(
                System.currentTimeMillis(),
                MessageHistoryEntry.DIRECTION_OUTGOING,
                conversationThreadKey,
                messageId,
                null,
                message
        ));
    }

    public static List<MessageHistoryEntry> getRecentMessages(Long conversationThreadKey, int limit) {
        if (limit <= 0) {
            return Collections.emptyList();
        }

        synchronized (LOCK) {
            ArrayList<MessageHistoryEntry> allEntries = getEntriesSourceLocked();
            ArrayList<MessageHistoryEntry> filtered = new ArrayList<>(Math.min(limit, allEntries.size()));

            for (int i = allEntries.size() - 1; i >= 0 && filtered.size() < limit; i--) {
                MessageHistoryEntry entry = allEntries.get(i);
                if (conversationThreadKey != null && entry.threadKey != conversationThreadKey) {
                    continue;
                }
                filtered.add(entry);
            }

            Collections.reverse(filtered);
            return filtered;
        }
    }

    public static void updateThreadName(long threadKey, String threadName) {
        String sanitized = sanitizeThreadName(threadName);
        if (threadKey <= 0 || sanitized == null) {
            return;
        }

        synchronized (LOCK) {
            SnapshotData snapshot = sInMemorySnapshot;
            if (snapshot != null) {
                snapshot.threadNames.put(threadKey, sanitized);
            }

            Map<Long, String> map = readThreadNamesLocked();
            String oldName = map.get(threadKey);
            if (sanitized.equals(oldName)) {
                return;
            }

            map.put(threadKey, sanitized);
            writeThreadNamesLocked(map);
        }
    }

    public static String getThreadName(long threadKey) {
        if (threadKey <= 0) {
            return null;
        }

        synchronized (LOCK) {
            Map<Long, String> map = getThreadNamesSourceLocked();
            return map.get(threadKey);
        }
    }

    public static List<HistoryThreadInfo> getRecentThreads(int limit) {
        if (limit <= 0) {
            return Collections.emptyList();
        }

        synchronized (LOCK) {
            ArrayList<MessageHistoryEntry> allEntries = getEntriesSourceLocked();
            Map<Long, String> namesMap = getThreadNamesSourceLocked();
            LinkedHashMap<Long, ThreadAccumulator> accumulators = new LinkedHashMap<>();

            for (int i = allEntries.size() - 1; i >= 0; i--) {
                MessageHistoryEntry entry = allEntries.get(i);
                if (entry.threadKey <= 0) {
                    continue;
                }

                ThreadAccumulator accumulator = accumulators.get(entry.threadKey);
                if (accumulator == null) {
                    accumulator = new ThreadAccumulator(entry.threadKey);
                    accumulators.put(entry.threadKey, accumulator);
                }
                accumulator.messageCount++;
                if (entry.timestamp > accumulator.lastTimestamp) {
                    accumulator.lastTimestamp = entry.timestamp;
                }
                if (accumulator.fallbackSenderUserKey == null
                        && entry.senderUserKey != null
                        && !entry.senderUserKey.trim().isEmpty()) {
                    accumulator.fallbackSenderUserKey = entry.senderUserKey;
                }
            }

            ArrayList<HistoryThreadInfo> result = new ArrayList<>();
            for (ThreadAccumulator accumulator : accumulators.values()) {
                String name = namesMap.get(accumulator.threadKey);
                if (name == null || name.trim().isEmpty()) {
                    name = buildFallbackThreadName(accumulator);
                }
                result.add(new HistoryThreadInfo(
                        accumulator.threadKey,
                        name,
                        accumulator.lastTimestamp,
                        accumulator.messageCount
                ));
                if (result.size() >= limit) {
                    break;
                }
            }

            return result;
        }
    }

    public static void clear() {
        synchronized (LOCK) {
            sInMemorySnapshot = null;

            File file = getHistoryFile();
            if (!file.exists()) {
                return;
            }

            if (!file.delete()) {
                Logger.warn("MessageHistoryStore: unable to delete history file");
            }
        }
    }

    public static void setInMemorySnapshot(List<String> historyLines, Map<String, String> threadNames) {
        synchronized (LOCK) {
            if (historyLines == null && threadNames == null) {
                sInMemorySnapshot = null;
                return;
            }

            ArrayList<MessageHistoryEntry> entries = new ArrayList<>();
            if (historyLines != null) {
                for (String line : historyLines) {
                    if (line == null || line.isEmpty()) {
                        continue;
                    }

                    try {
                        entries.add(fromJson(new JSONObject(line)));
                    } catch (Throwable ignored) {
                    }
                }
            }

            HashMap<Long, String> namesMap = new HashMap<>();
            if (threadNames != null) {
                for (Map.Entry<String, String> entry : threadNames.entrySet()) {
                    try {
                        long key = Long.parseLong(entry.getKey());
                        String value = sanitizeThreadName(entry.getValue());
                        if (value != null) {
                            namesMap.put(key, value);
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }

            sInMemorySnapshot = new SnapshotData(entries, namesMap);
        }
    }

    public static void clearInMemorySnapshot() {
        synchronized (LOCK) {
            sInMemorySnapshot = null;
        }
    }

    public static ArrayList<String> exportHistoryLinesForSettings(int maxLines) {
        synchronized (LOCK) {
            ArrayList<String> lines = readRawHistoryLinesLocked();
            if (maxLines > 0 && lines.size() > maxLines) {
                int from = lines.size() - maxLines;
                return new ArrayList<>(lines.subList(from, lines.size()));
            }
            return lines;
        }
    }

    public static HashMap<String, String> exportThreadNamesForSettings() {
        synchronized (LOCK) {
            Map<Long, String> map = readThreadNamesLocked();
            HashMap<String, String> out = new HashMap<>();
            for (Map.Entry<Long, String> entry : map.entrySet()) {
                out.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return out;
        }
    }

    private static void append(MessageHistoryEntry entry) {
        synchronized (LOCK) {
            if (entry.messageId != null && !entry.messageId.isEmpty() && containsMessageIdLocked(entry.messageId)) {
                return;
            }

            File file = getHistoryFile();
            if (!ensureParentDir(file)) {
                return;
            }

            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file, true), StandardCharsets.UTF_8))) {
                writer.write(toJson(entry).toString());
                writer.newLine();
            } catch (Throwable t) {
                Logger.error(t);
                return;
            }

            sPendingPruneCounter++;
            if (sPendingPruneCounter >= PRUNE_EVERY_APPEND) {
                sPendingPruneCounter = 0;
                pruneIfNeededLocked(file);
            }
        }
    }

    private static JSONObject toJson(MessageHistoryEntry entry) throws JSONException {
        JSONObject object = new JSONObject();
        object.put("ts", entry.timestamp);
        object.put("direction", entry.direction);
        object.put("threadKey", entry.threadKey);
        object.put("messageId", entry.messageId == null ? JSONObject.NULL : entry.messageId);
        object.put("senderUserKey", entry.senderUserKey == null ? JSONObject.NULL : entry.senderUserKey);
        object.put("content", entry.content == null ? "" : entry.content);
        return object;
    }

    private static MessageHistoryEntry fromJson(JSONObject object) {
        long timestamp = object.optLong("ts", 0L);
        String direction = object.optString("direction", MessageHistoryEntry.DIRECTION_INCOMING);
        long threadKey = object.optLong("threadKey", -1L);
        String messageId = object.isNull("messageId") ? null : object.optString("messageId", null);
        String senderUserKey = object.isNull("senderUserKey") ? null : object.optString("senderUserKey", null);
        String content = object.optString("content", "");
        return new MessageHistoryEntry(timestamp, direction, threadKey, messageId, senderUserKey, content);
    }

    private static Map<Long, String> readThreadNamesLocked() {
        File file = getThreadNamesFile();
        HashMap<Long, String> map = new HashMap<>();
        if (!file.exists()) {
            return map;
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }

            String json = builder.toString();
            if (json.isEmpty()) {
                return map;
            }

            JSONObject object = new JSONObject(json);
            Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                try {
                    long threadKey = Long.parseLong(key);
                    String value = sanitizeThreadName(object.optString(key, null));
                    if (value != null) {
                        map.put(threadKey, value);
                    }
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable t) {
            Logger.error(t);
        }

        return map;
    }

    private static void writeThreadNamesLocked(Map<Long, String> map) {
        File file = getThreadNamesFile();
        if (!ensureParentDir(file)) {
            return;
        }

        JSONObject object = new JSONObject();
        try {
            for (Map.Entry<Long, String> entry : map.entrySet()) {
                object.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        } catch (JSONException e) {
            Logger.error(e);
            return;
        }

        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file, false), StandardCharsets.UTF_8))) {
            writer.write(object.toString());
        } catch (Throwable t) {
            Logger.error(t);
        }
    }

    private static String sanitizeThreadName(String threadName) {
        if (threadName == null) {
            return null;
        }

        String trimmed = threadName.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        // Generic titles are not useful as thread labels.
        if ("Messenger".equalsIgnoreCase(trimmed) || "Chats".equalsIgnoreCase(trimmed)) {
            return null;
        }

        return trimmed;
    }

    private static String buildFallbackThreadName(ThreadAccumulator accumulator) {
        if (accumulator.fallbackSenderUserKey != null && !accumulator.fallbackSenderUserKey.isEmpty()) {
            String sender = accumulator.fallbackSenderUserKey;
            if (sender.startsWith("fbid:")) {
                sender = sender.substring(5);
            }
            return "User " + sender;
        }

        return "Thread " + accumulator.threadKey;
    }

    private static void pruneIfNeededLocked(File file) {
        ArrayList<MessageHistoryEntry> allEntries = readAllEntriesLocked();
        if (allEntries.size() <= MAX_ENTRIES) {
            return;
        }

        int startIndex = allEntries.size() - MAX_ENTRIES;
        ArrayList<MessageHistoryEntry> tail = new ArrayList<>(allEntries.subList(startIndex, allEntries.size()));
        overwriteEntriesLocked(file, tail);
    }

    private static ArrayList<MessageHistoryEntry> readAllEntriesLocked() {
        File file = getHistoryFile();
        ArrayList<MessageHistoryEntry> result = new ArrayList<>();
        if (!file.exists()) {
            return result;
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    continue;
                }

                try {
                    result.add(fromJson(new JSONObject(line)));
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable t) {
            Logger.error(t);
        }

        return result;
    }

    private static ArrayList<String> readRawHistoryLinesLocked() {
        File file = getHistoryFile();
        ArrayList<String> result = new ArrayList<>();
        if (!file.exists()) {
            return result;
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isEmpty()) {
                    result.add(line);
                }
            }
        } catch (Throwable t) {
            Logger.error(t);
        }

        return result;
    }

    private static ArrayList<MessageHistoryEntry> getEntriesSourceLocked() {
        SnapshotData snapshot = sInMemorySnapshot;
        if (snapshot != null) {
            return snapshot.entries;
        }
        return readAllEntriesLocked();
    }

    private static Map<Long, String> getThreadNamesSourceLocked() {
        SnapshotData snapshot = sInMemorySnapshot;
        if (snapshot != null) {
            return snapshot.threadNames;
        }
        return readThreadNamesLocked();
    }

    private static boolean containsMessageIdLocked(String messageId) {
        if (messageId == null || messageId.isEmpty()) {
            return false;
        }

        File file = getHistoryFile();
        if (!file.exists()) {
            return false;
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    continue;
                }

                try {
                    JSONObject object = new JSONObject(line);
                    if (!object.isNull("messageId") && messageId.equals(object.optString("messageId", null))) {
                        return true;
                    }
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable t) {
            Logger.error(t);
        }

        return false;
    }

    private static void overwriteEntriesLocked(File file, List<MessageHistoryEntry> entries) {
        if (!ensureParentDir(file)) {
            return;
        }

        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file, false), StandardCharsets.UTF_8))) {
            for (MessageHistoryEntry entry : entries) {
                writer.write(toJson(entry).toString());
                writer.newLine();
            }
        } catch (Throwable t) {
            Logger.error(t);
        }
    }

    private static boolean ensureParentDir(File file) {
        File parent = file.getParentFile();
        if (parent == null || parent.exists()) {
            return true;
        }
        return parent.mkdirs();
    }

    private static File getHistoryFile() {
        return new File(StorageConstants.moduleFiles, HISTORY_FILE_NAME);
    }

    private static File getThreadNamesFile() {
        return new File(StorageConstants.moduleFiles, THREAD_NAMES_FILE_NAME);
    }

    private static class ThreadAccumulator {
        final long threadKey;
        long lastTimestamp;
        int messageCount;
        String fallbackSenderUserKey;

        ThreadAccumulator(long threadKey) {
            this.threadKey = threadKey;
            this.lastTimestamp = 0L;
            this.messageCount = 0;
            this.fallbackSenderUserKey = null;
        }
    }

    private static class SnapshotData {
        final ArrayList<MessageHistoryEntry> entries;
        final Map<Long, String> threadNames;

        SnapshotData(ArrayList<MessageHistoryEntry> entries, Map<Long, String> threadNames) {
            this.entries = entries;
            this.threadNames = threadNames;
        }
    }
}
