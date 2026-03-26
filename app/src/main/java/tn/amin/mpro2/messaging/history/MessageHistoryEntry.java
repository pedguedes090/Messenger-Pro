package tn.amin.mpro2.messaging.history;

public class MessageHistoryEntry {
    public static final String DIRECTION_INCOMING = "incoming";
    public static final String DIRECTION_OUTGOING = "outgoing";

    public final long timestamp;
    public final String direction;
    public final long threadKey;
    public final String messageId;
    public final String senderUserKey;
    public final String content;

    public MessageHistoryEntry(long timestamp,
                               String direction,
                               long threadKey,
                               String messageId,
                               String senderUserKey,
                               String content) {
        this.timestamp = timestamp;
        this.direction = direction;
        this.threadKey = threadKey;
        this.messageId = messageId;
        this.senderUserKey = senderUserKey;
        this.content = content;
    }

    public boolean isIncoming() {
        return DIRECTION_INCOMING.equals(direction);
    }
}
