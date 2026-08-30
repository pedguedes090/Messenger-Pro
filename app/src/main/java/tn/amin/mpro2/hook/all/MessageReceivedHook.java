package tn.amin.mpro2.hook.all;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import tn.amin.mpro2.debug.Logger;
import tn.amin.mpro2.hook.BaseHook;
import tn.amin.mpro2.hook.HookId;
import tn.amin.mpro2.hook.HookTime;
import tn.amin.mpro2.hook.helper.OrcaHookHelper;
import tn.amin.mpro2.messaging.history.MessageHistoryStore;
import tn.amin.mpro2.orca.OrcaGateway;

/**
 * Captures incoming message content for Messenger 576.
 * <p>
 * In Messenger 576 the "new message" notification is action 29 on MailboxCoreJNI:
 * <pre>
 *   dispatchV...OZ(
 *       29,                // action code (new message event)
 *       AccountSession,    // [1]
 *       long threadKey,    // [2]
 *       String senderKey,  // [3]  (e.g. "100023693990349")
 *       int type,          // [4]
 *       long timestamp,    // [5]
 *       String messageId,  // [6]  (e.g. "mid.$...")
 *       String msgKey,     // [7]
 *       String content,    // [8]  <-- the message text
 *       ... , boolean, null, NotificationScope, boolean
 *   )
 * </pre>
 * This rewrite only hooks the exact action (29) with a fixed layout, replacing the
 * old heuristic "learn + recursive scan" approach that captured unrelated dispatches.
 */
public class MessageReceivedHook extends BaseHook {
    private static final int NOTIFICATION_ACTION_576 = 29;
    private static final String[] DISPATCH_CATEGORIES = {"Core", "SDK", "Orca"};

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
        Set<XC_MethodHook.Unhook> hooks = new HashSet<>();

        XC_MethodHook hook = wrap(new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                handleDispatch(gateway, param);
            }
        });

        for (String category : DISPATCH_CATEGORIES) {
            try {
                hooks.addAll(OrcaHookHelper.hookFeature(
                        NOTIFICATION_ACTION_576, "V", category, gateway.classLoader, hook));
            } catch (Throwable t) {
                Logger.warn("MessageReceivedHook: failed to hook " + category + ": " + t.getMessage());
            }
        }

        return hooks;
    }

    private void handleDispatch(OrcaGateway gateway, XC_MethodHook.MethodHookParam param) {
        Object[] args = param.args;
        if (args == null || args.length < 9) {
            return;
        }

        String content = readStringAt(args, 8);
        String messageId = readStringAt(args, 6);
        String sender = readStringAt(args, 3);
        long threadKey = readLongAt(args, 2);

        // Only capture incoming messages (skip our own outgoing ones).
        if (sender != null && gateway.authData != null) {
            String myId = gateway.authData.getFacebookUserID();
            if (myId != null && (sender.equals(myId) || sender.equals("fbid:" + myId))) {
                return;
            }
        }

        if (content == null || content.trim().isEmpty()) {
            return;
        }
        if (isSystemSignal(content)) {
            return;
        }

        Logger.verbose("MessageReceivedHook: sender=" + safe(sender) + " mid=" + safe(messageId)
                + " tk=" + threadKey + " msg=" + safe(content));

        if (gateway.pref != null && gateway.pref.isMessageHistoryEnabled()) {
            MessageHistoryStore.appendIncoming(gateway.getContext(), content, messageId, sender, threadKey);
        }

        notifyListeners((listener) ->
                ((MessageReceivedListener) listener).onMessageReceived(
                        content, messageId, sender != null ? sender : "", threadKey));
    }

    private String readStringAt(Object[] args, int index) {
        if (index < 0 || index >= args.length) {
            return null;
        }
        Object value = args[index];
        return value instanceof String ? (String) value : null;
    }

    private long readLongAt(Object[] args, int index) {
        if (index < 0 || index >= args.length) {
            return -1L;
        }
        Object value = args[index];
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return -1L;
    }

    private boolean isSystemSignal(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.startsWith("should_present_")) {
            return true;
        }
        if (lower.contains("peer_device_change") || lower.contains("security_alert")) {
            return true;
        }
        // Machine event identifiers are usually long underscore tokens.
        return lower.matches("^[a-z0-9_]{18,}$");
    }

    private String safe(String value) {
        if (value == null) {
            return "null";
        }
        return value.length() > 80 ? value.substring(0, 80) + "..." : value;
    }

    public interface MessageReceivedListener {
        void onMessageReceived(String message, String messageId, String senderUserKey, long threadKey);
    }
}
