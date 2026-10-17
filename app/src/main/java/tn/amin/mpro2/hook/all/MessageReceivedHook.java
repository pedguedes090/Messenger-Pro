package tn.amin.mpro2.hook.all;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
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

/**
 * Captures incoming message content from a configured Mailbox notification action.
 * The legacy action is used only for builds where it was verified; newer builds fail
 * closed until the action code is confirmed from runtime evidence.
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
    private static final int LEGACY_NOTIFICATION_ACTION = 29;
    private static final int LAST_VERSION_WITH_VERIFIED_LEGACY_ACTION = 345212666;
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
        Integer configuredAction = gateway.unobfuscator.getAPICode(OrcaUnobfuscator.API_NOTIFICATION);
        int version = gateway.state.getOrcaVersion();
        int action = NotificationDispatchPolicy.resolve(
                configuredAction == null ? -1 : configuredAction,
                version,
                LAST_VERSION_WITH_VERIFIED_LEGACY_ACTION,
                LEGACY_NOTIFICATION_ACTION);
        if (action < 0) {
            Logger.warn("MessageReceivedHook: API_NOTIFICATION is not verified for Orca build "
                    + version + "; hook left disabled. Set API_NOTIFICATION in API Codes after runtime verification.");
            return hooks;
        }
        Logger.info("MessageReceivedHook: action=" + action + " build=" + version);

        XC_MethodHook hook = wrap(new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                handleDispatch(gateway, param);
            }
        });

        for (String category : DISPATCH_CATEGORIES) {
            try {
                hooks.addAll(OrcaHookHelper.hookFeature(
                        action, "V", category, gateway.classLoader, hook,
                        MessageReceivedHook::isNotificationDispatchSignature));
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

        Logger.verbose("MessageReceivedHook: received notification payload (content omitted)");

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

    static boolean isNotificationDispatchSignature(Method method) {
        return NotificationDispatchSignature.matches(method);
    }

    public interface MessageReceivedListener {
        void onMessageReceived(String message, String messageId, String senderUserKey, long threadKey);
    }
}
