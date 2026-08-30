package tn.amin.mpro2.messaging.history;

public class MessageHistoryEntry {
    public static final String DIRECTION_INCOMING = "incoming";
    public static final String DIRECTION_OUTGOING = "outgoing";

    public final long timestamp;
    public final String direction;
    public final long threadKey;
    public final String messageId;
    public final String senderUserKey;
    public final String senderName;
    public final String threadName;
    public final String content;

    public MessageHistoryEntry(long timestamp,
                               String direction,
                               long threadKey,
                               String messageId,
                               String senderUserKey,
                               String senderName,
                               String threadName,
                               String content) {
        this.timestamp = timestamp;
        this.direction = direction;
        this.threadKey = threadKey;
        this.messageId = messageId;
        this.senderUserKey = senderUserKey;
        this.senderName = senderName;
        this.threadName = threadName;
        this.content = content;
    }

    public boolean isIncoming() {
        return DIRECTION_INCOMING.equals(direction);
    }
}
