package tn.amin.mpro2.features.internal;

import androidx.annotation.Nullable;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import tn.amin.mpro2.features.Feature;
import tn.amin.mpro2.features.FeatureId;
import tn.amin.mpro2.features.FeatureType;
import tn.amin.mpro2.hook.HookId;
import tn.amin.mpro2.hook.all.MessagesDisplayHook;
import tn.amin.mpro2.messaging.history.MessageHistoryStore;
import tn.amin.mpro2.orca.OrcaGateway;
import tn.amin.mpro2.orca.wrapper.MessageWrapper;
import tn.amin.mpro2.orca.wrapper.MessagesCollectionWrapper;
import tn.amin.mpro2.orca.wrapper.ThreadKeyWrapper;
import tn.amin.mpro2.orca.wrapper.UserKeyWrapper;

public class HistoryMediaCaptureFeature extends Feature implements MessagesDisplayHook.MessageDisplayHookListener {
    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s\"'<>]+", Pattern.CASE_INSENSITIVE);
    private static final int LAST_MESSAGES_WINDOW = 1;
    private static final long DUPLICATE_WINDOW_MS = 120_000L;
    private static final int MAX_RECENT_FINGERPRINTS = 400;

    private final LinkedHashMap<String, Long> mRecentFingerprintTimes = new LinkedHashMap<String, Long>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(java.util.Map.Entry<String, Long> eldest) {
            return size() > MAX_RECENT_FINGERPRINTS;
        }
    };

    public HistoryMediaCaptureFeature(OrcaGateway gateway) {
        super(gateway);
    }

    @Override
    public FeatureId getId() {
        return FeatureId.INTERNAL_HISTORY_MEDIA_CAPTURE;
    }

    @Override
    public FeatureType getType() {
        return FeatureType.INTERNAL;
    }

    @Override
    public HookId[] getHookIds() {
        return new HookId[] { HookId.MESSAGES_DISPLAY };
    }

    @Override
    public void onMessageDisplay(@Nullable MessageWrapper message, int index, int count, MessagesCollectionWrapper messagesCollection) {
        if (message == null || gateway.pref == null || !gateway.pref.isMessageHistoryEnabled()) {
            return;
        }

        // Avoid replaying the whole thread each decode; only inspect tail messages.
        if (count > LAST_MESSAGES_WINDOW && index < (count - LAST_MESSAGES_WINDOW)) {
            return;
        }

        String senderUserKey = getSenderUserKey(message);
        String senderName = message.getSenderName();
        boolean selfSender = isSelfSender(senderUserKey);
        long threadKey = getThreadKey(message, messagesCollection);
        if (threadKey <= 0) {
            threadKey = getActiveThreadKey();
        }
        if (threadKey <= 0) {
            threadKey = deriveFallbackThreadKey(senderUserKey, message.getId());
        }
        if (threadKey <= 0) {
            return;
        }

        Object rawObject = message.getRawObject();
        String plainText = sanitizeLikelyMessage(message.getText());
        boolean shouldFallback = plainText == null || plainText.trim().isEmpty() || isSuspiciousInitialToken(plainText);
        if (shouldFallback) {
            String fallbackText = findBestTextInObject(rawObject, 3, java.util.Collections.newSetFromMap(new IdentityHashMap<>()));
            if (fallbackText != null) {
                plainText = fallbackText;
            }
        }

        if (plainText != null && isSuspiciousInitialToken(plainText) && (message.getId() == null || message.getId().isEmpty())) {
            // Drop short initial-like artifacts when there is no stable message id to dedupe/verify.
            plainText = null;
        }

        String messageId = message.getId();
        if (plainText != null && !plainText.trim().isEmpty() && !selfSender) {
            String fingerprint = buildFingerprint("txt", messageId, senderUserKey, threadKey, plainText);
            if (shouldCapture(fingerprint)) {
                MessageHistoryStore.appendIncomingWithNames(gateway.getContext(), plainText, messageId, senderUserKey, senderName, threadKey, null);
            }
        }

        String mediaUrl = extractFirstUrl(message.getText());
        if (mediaUrl == null) {
            mediaUrl = findUrlInObject(rawObject, 2, java.util.Collections.newSetFromMap(new IdentityHashMap<>()));
        }

        if (mediaUrl == null) {
            return;
        }

        if (!selfSender) {
            String fingerprint = buildFingerprint("url", messageId, senderUserKey, threadKey, mediaUrl);
            if (shouldCapture(fingerprint)) {
                MessageHistoryStore.appendIncoming(gateway.getContext(), mediaUrl, messageId, senderUserKey, threadKey);
            }
        }
    }

    private String buildFingerprint(String kind, String messageId, String senderUserKey, long threadKey, String content) {
        if (messageId != null && !messageId.trim().isEmpty()) {
            return kind + "|mid|" + messageId;
        }

        String normalizedContent = content == null ? "" : content.trim();
        if (normalizedContent.isEmpty()) {
            return null;
        }

        return kind + "|tk=" + threadKey + "|sender=" + (senderUserKey == null ? "" : senderUserKey) + "|c=" + normalizedContent;
    }

    private boolean shouldCapture(String fingerprint) {
        if (fingerprint == null) {
            return true;
        }

        long now = System.currentTimeMillis();
        synchronized (mRecentFingerprintTimes) {
            Long lastSeen = mRecentFingerprintTimes.get(fingerprint);
            if (lastSeen != null && (now - lastSeen) < DUPLICATE_WINDOW_MS) {
                return false;
            }
            mRecentFingerprintTimes.put(fingerprint, now);
            return true;
        }
    }

    private long deriveFallbackThreadKey(String senderUserKey, String messageId) {
        if (senderUserKey != null && !senderUserKey.trim().isEmpty()) {
            long senderHash = senderUserKey.hashCode() & 0x7fffffffL;
            return 700000000000000000L + senderHash;
        }

        if (messageId != null && !messageId.trim().isEmpty()) {
            long messageHash = messageId.hashCode() & 0x7fffffffL;
            return 710000000000000000L + messageHash;
        }

        return -1L;
    }

    private long getActiveThreadKey() {
        if (gateway != null && gateway.currentThreadKey != null && gateway.currentThreadKey > 0) {
            return gateway.currentThreadKey;
        }
        return -1L;
    }

    private boolean isSelfSender(String senderUserKey) {
        if (senderUserKey == null || gateway.authData == null) {
            return false;
        }

        String myUserId = gateway.authData.getFacebookUserID();
        if (myUserId == null || myUserId.isEmpty()) {
            return false;
        }

        return senderUserKey.equals(myUserId) || senderUserKey.equals("fbid:" + myUserId);
    }

    private long getThreadKey(MessageWrapper message, MessagesCollectionWrapper collection) {
        ThreadKeyWrapper wrapper = message.getThreadKey();
        if (wrapper != null && wrapper.getFacebookThreadKey() != null) {
            return wrapper.getFacebookThreadKey();
        }

        ThreadKeyWrapper collectionThreadKey = collection != null ? collection.getThreadKey() : null;
        if (collectionThreadKey != null && collectionThreadKey.getFacebookThreadKey() != null) {
            return collectionThreadKey.getFacebookThreadKey();
        }

        return -1L;
    }

    private String getSenderUserKey(MessageWrapper message) {
        UserKeyWrapper wrapper = message.getUserKey();
        if (wrapper == null) {
            return null;
        }

        Long sender = wrapper.getUserKeyLong();
        if (sender == null || sender <= 0) {
            return null;
        }

        return "fbid:" + sender;
    }

    private String findBestTextInObject(Object obj, int depth, Set<Object> visited) {
        ScoredText scored = findBestTextCandidate(obj, depth, visited);
        return scored != null ? scored.text : null;
    }

    private ScoredText findBestTextCandidate(Object obj, int depth, Set<Object> visited) {
        if (obj == null || depth <= 0 || visited.contains(obj)) {
            return null;
        }

        visited.add(obj);

        if (obj instanceof String) {
            String candidate = sanitizeLikelyMessage((String) obj);
            if (candidate == null) {
                return null;
            }
            return new ScoredText(candidate, scoreLikelyMessage(candidate));
        }

        if (obj instanceof List) {
            ScoredText best = null;
            for (Object item : (List<?>) obj) {
                ScoredText candidate = findBestTextCandidate(item, depth - 1, visited);
                if (candidate != null && (best == null || candidate.score > best.score)) {
                    best = candidate;
                }
            }
            return best;
        }

        Class<?> type = obj.getClass();
        if (type.isPrimitive() || type.isEnum()) {
            return null;
        }

        String typeName = type.getName();
        if (typeName.startsWith("java.lang.") || typeName.startsWith("android.")) {
            return null;
        }

        Field[] fields = type.getDeclaredFields();
        ScoredText best = null;
        for (Field field : fields) {
            try {
                field.setAccessible(true);
                Object value = field.get(obj);
                ScoredText candidate = findBestTextCandidate(value, depth - 1, visited);
                if (candidate != null && (best == null || candidate.score > best.score)) {
                    best = candidate;
                }
            } catch (Throwable ignored) {
            }
        }

        return best;
    }

    private String sanitizeLikelyMessage(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        if (trimmed.length() > 1200) {
            return null;
        }

        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.equals("unknown") || lower.equals("null")) {
            return null;
        }

        if (trimmed.startsWith("ONE_TO_ONE:") || trimmed.startsWith("GROUP:") || trimmed.startsWith("T_MESSENGER:")) {
            return null;
        }

        if (trimmed.matches("^[A-Z_]+:[A-Za-z0-9:_-]+$")) {
            return null;
        }

        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return null;
        }

        if (trimmed.startsWith("mid.") || trimmed.startsWith("fbid:")) {
            return null;
        }

        boolean hasLetter = false;
        for (int i = 0; i < trimmed.length(); i++) {
            if (Character.isLetter(trimmed.charAt(i))) {
                hasLetter = true;
                break;
            }
        }

        if (!hasLetter) {
            return null;
        }

        return trimmed;
    }

    private int scoreLikelyMessage(String value) {
        if (value == null) {
            return Integer.MIN_VALUE;
        }

        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return Integer.MIN_VALUE;
        }

        int score = 0;
        if (containsLowercase(trimmed)) score += 5;
        if (containsWhitespace(trimmed)) score += 3;
        if (containsDigit(trimmed)) score += 1;
        if (trimmed.length() >= 4) score += 2;
        if (trimmed.length() >= 8) score += 2;
        if (isSuspiciousInitialToken(trimmed)) score -= 10;
        if (trimmed.startsWith("[") || trimmed.startsWith("{")) score -= 4;
        return score;
    }

    private boolean isSuspiciousInitialToken(String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim();
        return trimmed.matches("^[A-Z]\\.\\s?[A-Z]\\.?$");
    }

    private boolean containsLowercase(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isLowerCase(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private boolean containsWhitespace(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isWhitespace(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private boolean containsDigit(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isDigit(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static class ScoredText {
        final String text;
        final int score;

        ScoredText(String text, int score) {
            this.text = text;
            this.score = score;
        }
    }

    private String findUrlInObject(Object obj, int depth, Set<Object> visited) {
        if (obj == null || depth <= 0 || visited.contains(obj)) {
            return null;
        }

        visited.add(obj);

        if (obj instanceof String) {
            return extractFirstUrl((String) obj);
        }

        if (obj instanceof List) {
            for (Object item : (List<?>) obj) {
                String url = findUrlInObject(item, depth - 1, visited);
                if (url != null) {
                    return url;
                }
            }
            return null;
        }

        Class<?> type = obj.getClass();
        if (type.isPrimitive() || type.isEnum()) {
            return null;
        }

        String typeName = type.getName();
        if (typeName.startsWith("java.lang.") || typeName.startsWith("android.")) {
            return null;
        }

        Field[] fields = type.getDeclaredFields();
        for (Field field : fields) {
            try {
                field.setAccessible(true);
                Object value = field.get(obj);
                String url = findUrlInObject(value, depth - 1, visited);
                if (url != null) {
                    return url;
                }
            } catch (Throwable ignored) {
            }
        }

        return null;
    }

    private String extractFirstUrl(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }

        Matcher matcher = URL_PATTERN.matcher(text);
        if (!matcher.find()) {
            return null;
        }

        String url = matcher.group();
        if (url == null || url.trim().isEmpty()) {
            return null;
        }
        return url.trim();
    }
}
