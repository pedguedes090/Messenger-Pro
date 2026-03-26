package tn.amin.mpro2.messaging.history;

public class HistoryThreadInfo {
    public final long threadKey;
    public final String threadName;
    public final long lastTimestamp;
    public final int messageCount;

    public HistoryThreadInfo(long threadKey, String threadName, long lastTimestamp, int messageCount) {
        this.threadKey = threadKey;
        this.threadName = threadName;
        this.lastTimestamp = lastTimestamp;
        this.messageCount = messageCount;
    }
}
