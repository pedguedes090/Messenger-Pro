package tn.amin.mpro2.hook.all;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import tn.amin.mpro2.debug.Logger;
import tn.amin.mpro2.hook.BaseHook;
import tn.amin.mpro2.hook.HookId;
import tn.amin.mpro2.hook.HookTime;
import tn.amin.mpro2.hook.helper.OrcaHookHelper;
import tn.amin.mpro2.hook.unobfuscation.OrcaUnobfuscator;
import tn.amin.mpro2.messaging.history.MessageHistoryStore;
import tn.amin.mpro2.orca.OrcaGateway;

public class MessageReceivedHook extends BaseHook {
    private static final String[] DISPATCH_CATEGORIES = new String[] {"Orca", "SDK", "Core"};
    private static final Pattern USER_KEY_PATTERN = Pattern.compile("^(fbid:)?\\d{4,}$");
    private static final int MAX_RECENT_KEYS = 80;
    private static final int MIN_API_LEARN_HITS = 2;
    private static final int MAX_PROBE_LOGS = 120;

    private final ArrayDeque<String> mRecentEventKeys = new ArrayDeque<>();
    private final Set<String> mRecentEventSet = new HashSet<>();
    private final Map<Integer, Integer> mActionLearnHits = new HashMap<>();
    private volatile int mRuntimeApiCode = -1;
    private int mProbeLogCount = 0;

    @Override
    public HookId getId() {
        return HookId.MESSAGE_RECEIVE;
    }

    @Override
    public HookTime getHookTime() {
        return HookTime.AFTER_DEOBFUSCATION;
    }

    @Override
    protected Set<XC_MethodHook.Unhook> injectInternal(OrcaGateway gateway) {
        mRuntimeApiCode = gateway.unobfuscator.getAPICode(OrcaUnobfuscator.API_NOTIFICATION);
        Logger.info("MessageReceivedHook: configured apiCode=" + mRuntimeApiCode);

        Set<XC_MethodHook.Unhook> hooks = new HashSet<>();
        XC_MethodHook configuredHook = wrap(new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                MessagePayload payload = extractPayload(param.args, true);
                if (!looksLikeIncomingPayload(payload)) {
                    maybeLogProbe(gateway, mRuntimeApiCode, param, payload, "configured-miss");
                    return;
                }

                if (!isLikelyChatPayload(payload)) {
                    maybeLogProbe(gateway, mRuntimeApiCode, param, payload, "configured-skip");
                    return;
                }

                dispatchMessage(gateway, payload, "configured");
            }
        });

        XC_MethodHook discoveryHook = wrapIgnoreWorking(new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (param.args == null || param.args.length == 0 || !(param.args[0] instanceof Integer)) {
                    return;
                }

                int actionCode = (Integer) param.args[0];
                if (actionCode == mRuntimeApiCode) {
                    return;
                }

                MessagePayload payload = extractPayload(param.args, true);
                if (!looksLikeIncomingPayload(payload)) {
                    maybeLogProbe(gateway, actionCode, param, payload, "learn-miss");
                    return;
                }

                String normalizedMessage = normalizeIncomingMessage(payload.message);
                if (normalizedMessage == null) {
                    maybeLogProbe(gateway, actionCode, param, payload, "learn-nullmsg");
                    return;
                }
                payload = new MessagePayload(normalizedMessage, payload.messageId, payload.senderUserKey, payload.convThreadKey);

                if (!isLikelyChatPayload(payload)) {
                    maybeLogProbe(gateway, actionCode, param, payload, "learn-skip");
                    return;
                }

                boolean safeForLearning = isSafeForApiLearning(payload);

                if (safeForLearning) {
                    int hitCount = recordActionLearnHit(actionCode);
                    maybeLogProbe(gateway, actionCode, param, payload, "learn-hit#" + hitCount);

                    if (hitCount >= MIN_API_LEARN_HITS) {
                        Logger.warn("MessageReceivedHook: learned API_NOTIFICATION=" + actionCode
                                + " (old=" + mRuntimeApiCode + ", hits=" + hitCount + ")");
                        mRuntimeApiCode = actionCode;
                        gateway.unobfuscator.getPreferences().edit()
                                .putString(OrcaUnobfuscator.API_NOTIFICATION, String.valueOf(actionCode))
                                .apply();
                    }
                } else {
                    maybeLogProbe(gateway, actionCode, param, payload, "learn-unsafe");
                }

                dispatchMessage(gateway, payload, safeForLearning ? "learned" : "learned-weak");
            }
        });

        if (mRuntimeApiCode > 0) {
            for (String category : DISPATCH_CATEGORIES) {
                try {
                    hooks.addAll(OrcaHookHelper.hookFeature(mRuntimeApiCode,
                            "V", category, gateway.classLoader, configuredHook));
                } catch (Throwable t) {
                    Logger.warn("MessageReceivedHook: failed configured hook for " + category + ": " + t.getMessage());
                }
            }
        } else {
            Logger.warn("MessageReceivedHook: configured apiCode invalid, relying on discovery hooks");
        }

        for (String category : DISPATCH_CATEGORIES) {
            try {
                hooks.addAll(OrcaHookHelper.hookDispatch(
                        "V", category, gateway.classLoader, discoveryHook));
            } catch (Throwable t) {
                Logger.warn("MessageReceivedHook: failed discovery hook for " + category + ": " + t.getMessage());
            }
        }

        return hooks;

    }

    private void dispatchMessage(OrcaGateway gateway, MessagePayload payload, String source) {
        long resolvedThreadKey = resolveThreadKey(gateway, payload);
        if (resolvedThreadKey <= 0) {
            return;
        }

        String persistedMessage = normalizeIncomingMessage(payload.message);

        if (isDuplicateEvent(payload, resolvedThreadKey)) {
            return;
        }

        if (gateway.pref != null && gateway.pref.isMessageHistoryEnabled() && persistedMessage != null) {
            MessageHistoryStore.appendIncoming(
                    persistedMessage,
                    payload.messageId,
                    payload.senderUserKey,
                resolvedThreadKey
            );
        }

        Logger.verbose("MessageReceivedHook[" + source + "]: sender=" + safeLog(payload.senderUserKey, 32)
                + " mid=" + safeLog(payload.messageId, 48)
                + " tk=" + resolvedThreadKey
                + " message=" + safeLog(payload.message, 80));

        String listenerSenderUserKey = payload.senderUserKey != null ? payload.senderUserKey : "";

        notifyListeners((listener) ->
                ((MessageReceivedListener) listener).onMessageReceived(
                        payload.message,
                        payload.messageId,
                listenerSenderUserKey,
                        resolvedThreadKey));
    }

    private long resolveThreadKey(OrcaGateway gateway, MessagePayload payload) {
        if (payload.convThreadKey > 0) {
            return payload.convThreadKey;
        }

        if (gateway != null && gateway.currentThreadKey != null && gateway.currentThreadKey > 0) {
            return gateway.currentThreadKey;
        }

        if (payload.senderUserKey != null && !payload.senderUserKey.isEmpty()) {
            long senderHash = payload.senderUserKey.hashCode() & 0x7fffffffL;
            return 700000000000000000L + senderHash;
        }

        if (payload.messageId != null && !payload.messageId.isEmpty()) {
            long messageHash = payload.messageId.hashCode() & 0x7fffffffL;
            return 710000000000000000L + messageHash;
        }

        return -1L;
    }

    private MessagePayload extractPayload(Object[] args, boolean useHeuristic) {
        String message = readStringAt(args, 8);
        String messageId = readStringAt(args, 6);
        String senderUserKey = readStringAt(args, 3);
        long convThreadKey = readLongAt(args, 2);

        MessagePayload fixedLayoutPayload = new MessagePayload(message, messageId, senderUserKey, convThreadKey);
        if (fixedLayoutPayload.isComplete() || !useHeuristic) {
            return fixedLayoutPayload;
        }

        String guessedMessage = message;
        String guessedMessageId = messageId;
        String guessedSender = senderUserKey;
        long guessedThreadKey = convThreadKey;

        List<String> stringArgs = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            Object arg = args[i];
            if (arg instanceof String) {
                stringArgs.add((String) arg);
            } else if (arg instanceof Number && guessedThreadKey <= 0) {
                long value = ((Number) arg).longValue();
                if (isLikelyThreadKey(value)) guessedThreadKey = value;
            }
        }

        if (guessedThreadKey <= 0) {
            guessedThreadKey = findThreadKeyFromArgs(args);
        }

        if (guessedMessageId == null) {
            guessedMessageId = findMessageId(stringArgs);
            if (guessedMessageId == null) {
                guessedMessageId = findMessageIdFromArgs(args);
            }
        }

        if (guessedSender == null) {
            guessedSender = findSenderKey(stringArgs);
            if (guessedSender == null) {
                guessedSender = findSenderKeyFromArgs(args);
            }
        }

        if (guessedMessage == null) {
            guessedMessage = findMessageText(stringArgs, guessedMessageId, guessedSender);
            if (guessedMessage == null) {
                guessedMessage = findMessageTextFromArgs(args);
            }
        }

        return new MessagePayload(guessedMessage, guessedMessageId, guessedSender, guessedThreadKey);
    }

    private String findMessageId(List<String> candidates) {
        for (String candidate : candidates) {
            if (candidate == null) continue;
            if (candidate.startsWith("mid.") || candidate.contains("mid.")) {
                return candidate;
            }
        }
        return null;
    }

    private String findSenderKey(List<String> candidates) {
        for (String candidate : candidates) {
            if (candidate == null) continue;
            if (USER_KEY_PATTERN.matcher(candidate).matches()) {
                return candidate;
            }
        }
        return null;
    }

    private String findMessageIdFromArgs(Object[] args) {
        if (args == null) {
            return null;
        }

        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Object arg : args) {
            String candidate = findMessageIdInObject(arg, 3, visited);
            if (candidate != null) {
                return candidate;
            }
        }

        return null;
    }

    private String findMessageIdInObject(Object value, int depth, Set<Object> visited) {
        if (value == null || depth <= 0 || visited.contains(value)) {
            return null;
        }

        visited.add(value);

        if (value instanceof String) {
            String str = ((String) value).trim();
            if (str.startsWith("mid.") || str.contains("mid.")) {
                return str;
            }
            return null;
        }

        if (value instanceof List) {
            for (Object item : (List<?>) value) {
                String candidate = findMessageIdInObject(item, depth - 1, visited);
                if (candidate != null) {
                    return candidate;
                }
            }
            return null;
        }

        Class<?> type = value.getClass();
        if (type.isPrimitive() || type.isEnum() || value instanceof Number || value instanceof Boolean || value instanceof Character) {
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
                String candidate = findMessageIdInObject(field.get(value), depth - 1, visited);
                if (candidate != null) {
                    return candidate;
                }
            } catch (Throwable ignored) {
            }
        }

        return null;
    }

    private String findSenderKeyFromArgs(Object[] args) {
        if (args == null) {
            return null;
        }

        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Object arg : args) {
            String candidate = findSenderKeyInObject(arg, 3, visited);
            if (candidate != null) {
                return candidate;
            }
        }

        return null;
    }

    private String findSenderKeyInObject(Object value, int depth, Set<Object> visited) {
        if (value == null || depth <= 0 || visited.contains(value)) {
            return null;
        }

        visited.add(value);

        if (value instanceof String) {
            String str = ((String) value).trim();
            if (USER_KEY_PATTERN.matcher(str).matches()) {
                return str;
            }
            return null;
        }

        if (value instanceof List) {
            for (Object item : (List<?>) value) {
                String candidate = findSenderKeyInObject(item, depth - 1, visited);
                if (candidate != null) {
                    return candidate;
                }
            }
            return null;
        }

        Class<?> type = value.getClass();
        if (type.isPrimitive() || type.isEnum() || value instanceof Number || value instanceof Boolean || value instanceof Character) {
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
                String candidate = findSenderKeyInObject(field.get(value), depth - 1, visited);
                if (candidate != null) {
                    return candidate;
                }
            } catch (Throwable ignored) {
            }
        }

        return null;
    }

    private long findThreadKeyFromArgs(Object[] args) {
        if (args == null) {
            return -1;
        }

        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Object arg : args) {
            long candidate = findThreadKeyInObject(arg, 3, visited);
            if (isLikelyThreadKey(candidate)) {
                return candidate;
            }
        }

        return -1;
    }

    private long findThreadKeyInObject(Object value, int depth, Set<Object> visited) {
        if (value == null || depth <= 0 || visited.contains(value)) {
            return -1;
        }

        visited.add(value);

        if (value instanceof Number) {
            long number = ((Number) value).longValue();
            return isLikelyThreadKey(number) ? number : -1;
        }

        if (value instanceof String) {
            String str = ((String) value).trim();
            if (str.startsWith("T_MESSENGER:")) {
                String[] parts = str.split(":");
                for (int i = parts.length - 1; i >= 0; i--) {
                    try {
                        long number = Long.parseLong(parts[i]);
                        if (isLikelyThreadKey(number)) {
                            return number;
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }

            if (str.matches("^\\d{10,}$")) {
                try {
                    long number = Long.parseLong(str);
                    if (isLikelyThreadKey(number)) {
                        return number;
                    }
                } catch (Throwable ignored) {
                }
            }

            return -1;
        }

        if (value instanceof List) {
            for (Object item : (List<?>) value) {
                long candidate = findThreadKeyInObject(item, depth - 1, visited);
                if (isLikelyThreadKey(candidate)) {
                    return candidate;
                }
            }
            return -1;
        }

        Class<?> type = value.getClass();
        if (type.isPrimitive() || type.isEnum() || value instanceof Boolean || value instanceof Character) {
            return -1;
        }

        String typeName = type.getName();
        if (typeName.startsWith("java.lang.") || typeName.startsWith("android.")) {
            return -1;
        }

        Field[] fields = type.getDeclaredFields();
        for (Field field : fields) {
            try {
                field.setAccessible(true);
                long candidate = findThreadKeyInObject(field.get(value), depth - 1, visited);
                if (isLikelyThreadKey(candidate)) {
                    return candidate;
                }
            } catch (Throwable ignored) {
            }
        }

        return -1;
    }

    private String findMessageText(List<String> candidates, String messageId, String senderUserKey) {
        String best = null;
        int bestScore = Integer.MIN_VALUE;

        for (String candidate : candidates) {
            if (candidate == null || candidate.isEmpty()) continue;
            if (candidate.equals(messageId) || candidate.equals(senderUserKey)) continue;
            if (candidate.length() > 4000) continue;

            String normalized = normalizeIncomingMessage(candidate);
            if (normalized == null) continue;

            int score = 0;
            if (normalized.contains(" ")) score += 3;
            if (containsLetter(normalized)) score += 2;
            if (normalized.length() > 8) score += 1;
            if (normalized.startsWith("mid.") || normalized.contains("mid.")) score -= 6;
            if (USER_KEY_PATTERN.matcher(normalized).matches()) score -= 5;
            if (normalized.startsWith("Lcom/facebook/")) score -= 5;

            if (score > bestScore) {
                bestScore = score;
                best = normalized;
            }
        }

        return best;
    }

    private boolean containsLetter(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isLetter(value.charAt(i))) return true;
        }
        return false;
    }

    private String readStringAt(Object[] args, int index) {
        if (args == null || index >= args.length || index < 0) return null;
        return args[index] instanceof String ? (String) args[index] : null;
    }

    private long readLongAt(Object[] args, int index) {
        if (args == null || index >= args.length || index < 0) return -1;
        Object value = args[index];
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return -1;
    }

    private String findMessageTextFromArgs(Object[] args) {
        if (args == null) {
            return null;
        }

        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Object arg : args) {
            String candidate = findLikelyTextInObject(arg, 2, visited);
            if (candidate != null) {
                return candidate;
            }
        }

        return null;
    }

    private String findLikelyTextInObject(Object value, int depth, Set<Object> visited) {
        if (value == null || depth <= 0 || visited.contains(value)) {
            return null;
        }

        visited.add(value);

        if (value instanceof String) {
            return sanitizeCandidateMessage((String) value);
        }

        if (value instanceof List) {
            for (Object item : (List<?>) value) {
                String candidate = findLikelyTextInObject(item, depth - 1, visited);
                if (candidate != null) {
                    return candidate;
                }
            }
            return null;
        }

        Class<?> type = value.getClass();
        if (type.isPrimitive() || type.isEnum() || value instanceof Number || value instanceof Boolean || value instanceof Character) {
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
                String candidate = findLikelyTextInObject(field.get(value), depth - 1, visited);
                if (candidate != null) {
                    return candidate;
                }
            } catch (Throwable ignored) {
            }
        }

        return null;
    }

    private String sanitizeCandidateMessage(String value) {
        return normalizeIncomingMessage(value);
    }

    private String normalizeIncomingMessage(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        if (trimmed.isEmpty() || trimmed.length() > 1200) {
            return null;
        }

        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (trimmed.startsWith("mid.") || trimmed.startsWith("fbid:") || USER_KEY_PATTERN.matcher(trimmed).matches()) {
            return null;
        }

        if (lower.equals("unknown") || lower.equals("null")) {
            return null;
        }

        if (trimmed.startsWith("ONE_TO_ONE:") || trimmed.startsWith("GROUP:") || trimmed.startsWith("T_MESSENGER:")) {
            return null;
        }

        if (trimmed.matches("^[A-Z_]+:[A-Za-z0-9:_-]+$")) {
            return null;
        }

        if (isSystemSignal(trimmed)) {
            return null;
        }

        boolean hasLetter = false;
        for (int i = 0; i < trimmed.length(); i++) {
            if (Character.isLetter(trimmed.charAt(i))) {
                hasLetter = true;
                break;
            }
        }

        return hasLetter ? trimmed : null;
    }

    private boolean looksLikeIncomingPayload(MessagePayload payload) {
        if (!payload.isComplete()) return false;
        if (payload.message.length() > 4000) return false;
        if (normalizeIncomingMessage(payload.message) == null) return false;
        boolean hasSender = payload.senderUserKey != null && USER_KEY_PATTERN.matcher(payload.senderUserKey).matches();
        boolean hasMessageId = payload.messageId != null && payload.messageId.contains("mid.");
        boolean hasLikelyThread = isLikelyThreadKey(payload.convThreadKey);
        if (!hasSender && !hasMessageId && !hasLikelyThread) {
            return false;
        }
        return true;
    }

    private boolean isLikelyChatPayload(MessagePayload payload) {
        if (payload == null || !payload.isComplete()) {
            return false;
        }

        String normalized = normalizeIncomingMessage(payload.message);
        if (normalized == null) {
            return false;
        }

        boolean hasSender = payload.senderUserKey != null && USER_KEY_PATTERN.matcher(payload.senderUserKey).matches();
        boolean hasMessageId = payload.messageId != null && payload.messageId.contains("mid.");
        if (hasSender || hasMessageId) {
            return true;
        }

        // Fallback: allow natural text with spaces if thread key looks valid.
        return isLikelyThreadKey(payload.convThreadKey) && normalized.contains(" ");
    }

    private boolean isSafeForApiLearning(MessagePayload payload) {
        if (payload == null) {
            return false;
        }

        boolean hasSender = payload.senderUserKey != null && USER_KEY_PATTERN.matcher(payload.senderUserKey).matches();
        boolean hasMessageId = payload.messageId != null && payload.messageId.contains("mid.");
        return hasSender || hasMessageId;
    }

    private boolean isSystemSignal(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.startsWith("should_present_")) {
            return true;
        }
        if (lower.contains("security_alert") || lower.contains("peer_device_change")) {
            return true;
        }

        // Machine event identifiers are usually long underscore tokens.
        return lower.matches("^[a-z0-9_]{18,}$");
    }

    private boolean isLikelyThreadKey(long value) {
        if (value <= 0) {
            return false;
        }

        // Real thread keys are large positive identifiers, not tiny counters/flags.
        return value >= 1_000_000_000L;
    }

    private int recordActionLearnHit(int actionCode) {
        synchronized (mActionLearnHits) {
            int next = mActionLearnHits.getOrDefault(actionCode, 0) + 1;
            mActionLearnHits.put(actionCode, next);
            return next;
        }
    }

    private void maybeLogProbe(OrcaGateway gateway,
                               int actionCode,
                               XC_MethodHook.MethodHookParam param,
                               MessagePayload payload,
                               String reason) {
        if (gateway == null || gateway.pref == null || !gateway.pref.isTypingCaptureDebugEnabled()) {
            return;
        }

        synchronized (this) {
            if (mProbeLogCount >= MAX_PROBE_LOGS) {
                return;
            }
            mProbeLogCount++;
        }

        String methodName = "unknown";
        if (param != null && param.method instanceof Method) {
            methodName = ((Method) param.method).getName();
        }

        Logger.info("MessageReceivedHook: probe[" + reason + "] action=" + actionCode
                + " method=" + methodName
                + " payload={msg=" + safeLog(payload != null ? payload.message : null, 80)
                + ", mid=" + safeLog(payload != null ? payload.messageId : null, 48)
                + ", sender=" + safeLog(payload != null ? payload.senderUserKey : null, 32)
                + ", tk=" + (payload != null ? payload.convThreadKey : -1L)
                + "} args=" + summarizeArgs(param != null ? param.args : null));
    }

    private String summarizeArgs(Object[] args) {
        if (args == null) {
            return "[]";
        }

        StringBuilder sb = new StringBuilder("[");
        int max = Math.min(args.length, 12);
        for (int i = 0; i < max; i++) {
            if (i > 0) {
                sb.append(", ");
            }

            Object arg = args[i];
            if (arg == null) {
                sb.append(i).append(":null");
                continue;
            }

            sb.append(i).append(":").append(arg.getClass().getSimpleName());
            if (arg instanceof String) {
                sb.append("=").append(safeLog((String) arg, 40));
            } else if (arg instanceof Number || arg instanceof Boolean) {
                sb.append("=").append(arg);
            }
        }

        if (args.length > max) {
            sb.append(", ...+").append(args.length - max).append(" args");
        }

        sb.append("]");
        return sb.toString();
    }

    private boolean isDuplicateEvent(MessagePayload payload, long resolvedThreadKey) {
        String dedupeKey;
        if (payload.messageId != null && !payload.messageId.isEmpty()) {
            dedupeKey = "mid:" + payload.messageId;
        } else {
            dedupeKey = "tk:" + resolvedThreadKey + "|msg:" + payload.message;
        }

        synchronized (mRecentEventSet) {
            if (mRecentEventSet.contains(dedupeKey)) {
                return true;
            }

            mRecentEventSet.add(dedupeKey);
            mRecentEventKeys.addLast(dedupeKey);

            while (mRecentEventKeys.size() > MAX_RECENT_KEYS) {
                String oldest = mRecentEventKeys.removeFirst();
                mRecentEventSet.remove(oldest);
            }
        }

        return false;
    }

    private String safeLog(String value, int maxLength) {
        if (value == null) return "null";
        if (value.length() <= maxLength) return value;
        return value.substring(0, maxLength) + "...";
    }

    private static class MessagePayload {
        final String message;
        final String messageId;
        final String senderUserKey;
        final long convThreadKey;

        MessagePayload(String message, String messageId, String senderUserKey, long convThreadKey) {
            this.message = message;
            this.messageId = messageId;
            this.senderUserKey = senderUserKey;
            this.convThreadKey = convThreadKey;
        }

        boolean isComplete() {
            return message != null;
        }
    }

    public interface MessageReceivedListener {
        void onMessageReceived(String message, String messageId, String senderUserKey, long convThreadKey);
    }
}
