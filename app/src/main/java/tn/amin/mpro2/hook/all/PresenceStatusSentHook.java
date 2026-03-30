package tn.amin.mpro2.hook.all;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import tn.amin.mpro2.constants.OrcaClassNames;
import tn.amin.mpro2.debug.Logger;
import tn.amin.mpro2.hook.BaseHook;
import tn.amin.mpro2.hook.HookId;
import tn.amin.mpro2.hook.HookTime;
import tn.amin.mpro2.hook.listener.HookListenerResult;
import tn.amin.mpro2.orca.OrcaGateway;

/**
 * Blocks outgoing presence/online activity updates.
 *
 * Safety model:
 * - Never blocks thread-scoped payloads (token starts with T_), which are typically typing/seen paths.
 * - Default mode logs candidates only.
 * - Blocking requires feature toggle + either configured action code match or debug force-block mode.
 */
public class PresenceStatusSentHook extends BaseHook {
    private static final String MAILBOX_ORCA_JNI = "com.facebook.orca.mca.MailboxOrcaJNI";
    private static final int CAPTURE_LOG_LIMIT = 1200;
    private static final String[] EXTRA_MAILBOX_JNI_CLASSES = new String[] {
            "com.facebook.presence.mca.MailboxPresenceJNI",
            "com.facebook.copresence.mca.MailboxCopresenceJNI",
            "com.facebook.status.mca.MailboxStatusJNI",
            "com.facebook.syncstates.mca.MailboxSyncStatesJNI",
            "com.facebook.broadcastflow.mca.MailboxBroadcastFlowJNI",
            "com.facebook.events.mca.MailboxEventsJNI",
            "com.facebook.orcaslim.mca.MailboxOrcaSlimJNI",
            "com.facebook.qp.mca.MailboxQPJNI"
    };

    // Exclude known non-presence actions.
    private static final int ACTION_SEEN_DISPATCH_V553 = 62;
    private static final int ACTION_TYPING_SUBSCRIPTION = 81;
    private static final int ACTION_MARK_READ_DISPATCH_V553 = 23;
    private static final int ACTION_TYPING_OUTBOUND_V553 = 88;
    private static final int ACTION_MESSAGE_SEND_V553 = 71;
    private static final int ACTION_MESSAGE_SEND_V552 = 61;
    private static final int ACTION_CONVERSATION_ENTER = 6;
    private static final int ACTION_CONVERSATION_LEAVE = 7;

    private int mCaptureLogCount = 0;

    @Override
    public HookId getId() {
        return HookId.PRESENCE_STATUS_SEND;
    }

    @Override
    public HookTime getHookTime() {
        return HookTime.AFTER_DEOBFUSCATION;
    }

    @Override
    protected Set<XC_MethodHook.Unhook> injectInternal(OrcaGateway gateway) {
        boolean captureDebug = gateway.pref != null && gateway.pref.isPresenceCaptureDebugEnabled();
        boolean forceBlock = gateway.pref != null && gateway.pref.isPresenceForceBlockEnabled();
        Set<Integer> configuredActionCodes = gateway.pref != null
            ? gateway.pref.getPresenceBlockActionCodes()
            : Collections.emptySet();

        Logger.info("PresenceStatusSentHook: captureDebug=" + captureDebug
                + " forceBlock=" + forceBlock
            + " actionCodes=" + configuredActionCodes);

        Set<XC_MethodHook.Unhook> unhooks = new HashSet<>();
        hookDispatchMethods(unhooks, gateway.classLoader, OrcaClassNames.MAILBOX_SDK_JNI, "SDK",
            captureDebug, forceBlock, configuredActionCodes);
        hookDispatchMethods(unhooks, gateway.classLoader, OrcaClassNames.MAILBOX_CORE_JNI, "CORE",
            captureDebug, forceBlock, configuredActionCodes);
        hookDispatchMethods(unhooks, gateway.classLoader, MAILBOX_ORCA_JNI, "ORCA",
            captureDebug, forceBlock, configuredActionCodes);

        for (String extraClass : EXTRA_MAILBOX_JNI_CLASSES) {
            String tag = extraClass.replace("com.facebook.", "").replace(".mca", "").replace("Mailbox", "").replace("JNI", "").toUpperCase(Locale.ROOT);
            hookDispatchMethods(unhooks, gateway.classLoader, extraClass, tag,
                captureDebug, forceBlock, configuredActionCodes);
        }
        return unhooks;
    }

        private void hookDispatchMethods(Set<XC_MethodHook.Unhook> unhooks, ClassLoader classLoader,
                         String className, String tag,
                         boolean captureDebug, boolean forceBlock,
                         Set<Integer> configuredActionCodes) {
        Class<?> cls = findClassOrNull(className, classLoader);
        if (cls == null) {
            Logger.info("PresenceStatusSentHook: " + tag + " class not found: " + className);
            return;
        }

        int hooked = 0;
        for (Method method : cls.getDeclaredMethods()) {
            if (!method.getName().startsWith("dispatch")) continue;

            unhooks.add(XposedBridge.hookMethod(method, wrap(new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (!hasMailboxArgNearStart(param.args)) return;

                    Integer actionCode = getActionCodeInt(param.args);
                    if (actionCode == null) {
                        if (captureDebug && shouldLogCapture()) {
                            Logger.info("PresenceCapture[" + tag + "]: method=" + method.getName()
                                    + " action=NA"
                                    + " firstInt=" + findFirstIntegerArg(param.args, 6)
                                    + " args=" + summarizeArgs(param.args));
                        }
                        return;
                    }
                    if (isExcludedActionCode(actionCode)) return;

                    PresenceShape shape = inspectShape(param.args);
                    boolean actionMatched = configuredActionCodes.contains(actionCode);
                    boolean candidateMatched = forceBlock && shape.looksLikePresence;
                    boolean shouldBlock = (actionMatched || candidateMatched) && shape.safeToBlock;

                    if (captureDebug && shouldLogCapture()) {
                        Logger.info("PresenceCapture[" + tag + "]: method=" + method.getName()
                                + " action=" + actionCode
                                + " safe=" + shape.safeToBlock
                                + " looksLike=" + shape.looksLikePresence
                                + " actionMatched=" + actionMatched
                                + " args=" + summarizeArgs(param.args));
                    }

                    if (!shouldBlock) return;

                    notifyListenersWithResult((listener) ->
                            ((PresenceStatusSentListener) listener).onPresenceStatusSent());
                    HookListenerResult<Boolean> decision = getListenersReturnValue();
                    boolean allowPresence = decision == null
                            || !decision.isConsumed
                            || Boolean.TRUE.equals(decision.value);

                    if (allowPresence) return;

                    Method hookedMethod = (Method) param.method;
                    param.setResult(getDefaultReturnValue(hookedMethod.getReturnType()));
                    Logger.verbose("PresenceStatusSentHook: blocked " + tag
                            + " method=" + method.getName()
                            + " action=" + actionCode);
                }
            })));
            hooked++;
        }

        Logger.info("PresenceStatusSentHook: hooked " + hooked + " " + tag + " dispatch methods");
    }

    private Class<?> findClassOrNull(String className, ClassLoader classLoader) {
        try {
            return XposedHelpers.findClass(className, classLoader);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private boolean hasMailboxArgNearStart(Object[] args) {
        if (args == null) return false;
        for (int i = 1; i < Math.min(args.length, 8); i++) {
            if (args[i] == null) continue;

            String cls = args[i].getClass().getName();
            if (OrcaClassNames.MAILBOX.equals(cls) || cls.contains(".msys.mca.Mailbox")) {
                return true;
            }
        }
        return false;
    }

    private PresenceShape inspectShape(Object[] args) {
        boolean hasThreadToken = false;
        boolean hasNotificationScope = false;
        boolean hasBoolean = false;
        boolean hasPresenceMarker = false;
        boolean hasTypingMarker = false;

        if (args != null) {
            for (Object arg : args) {
                if (arg == null) continue;

                if (arg instanceof Boolean) {
                    hasBoolean = true;
                    continue;
                }

                String className = arg.getClass().getName();
                if (className.contains("NotificationScope")) {
                    hasNotificationScope = true;
                }

                if (arg instanceof String) {
                    String value = ((String) arg).toLowerCase(Locale.ROOT);
                    if (((String) arg).startsWith("T_")) {
                        hasThreadToken = true;
                    }
                    if (value.contains("typing") || value.contains("t_st")) {
                        hasTypingMarker = true;
                    }
                    if (value.contains("presence")
                            || value.contains("copresence")
                            || value.contains("active")
                            || value.contains("online")
                            || value.contains("foreground")
                            || value.contains("background")
                            || value.contains("heartbeat")
                            || value.contains("last_active")) {
                        hasPresenceMarker = true;
                    }
                }
            }
        }

        boolean safeToBlock = !hasThreadToken && !hasTypingMarker;
        boolean looksLikePresence = safeToBlock && (hasPresenceMarker || (hasBoolean && hasNotificationScope));
        return new PresenceShape(safeToBlock, looksLikePresence);
    }

    private Integer getActionCodeInt(Object[] args) {
        if (args == null || args.length == 0) return null;
        if (args[0] instanceof Integer) return (Integer) args[0];
        return null;
    }

    private boolean isExcludedActionCode(int actionCode) {
        return actionCode == ACTION_SEEN_DISPATCH_V553
                || actionCode == ACTION_TYPING_SUBSCRIPTION
                || actionCode == ACTION_MARK_READ_DISPATCH_V553
                || actionCode == ACTION_TYPING_OUTBOUND_V553
                || actionCode == ACTION_MESSAGE_SEND_V553
                || actionCode == ACTION_MESSAGE_SEND_V552
                || actionCode == ACTION_CONVERSATION_ENTER
                || actionCode == ACTION_CONVERSATION_LEAVE;
    }

    private Integer findFirstIntegerArg(Object[] args, int maxIndexExclusive) {
        if (args == null) return null;

        int maxIndex = Math.min(args.length, Math.max(maxIndexExclusive, 0));
        for (int i = 0; i < maxIndex; i++) {
            if (args[i] instanceof Integer) {
                return (Integer) args[i];
            }
        }

        return null;
    }

    private synchronized boolean shouldLogCapture() {
        if (mCaptureLogCount >= CAPTURE_LOG_LIMIT) return false;
        mCaptureLogCount++;
        return true;
    }

    private String summarizeArgs(Object[] args) {
        if (args == null) return "[]";

        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < args.length; i++) {
            if (i > 0) sb.append(", ");
            Object arg = args[i];
            if (arg == null) {
                sb.append(i).append(":null");
            } else {
                sb.append(i).append(":").append(arg.getClass().getSimpleName());
                String text = String.valueOf(arg);
                if (text.length() > 40) text = text.substring(0, 40) + "...";
                sb.append("=").append(text);
            }
        }
        sb.append("]");
        return sb.toString();
    }

    private Object getDefaultReturnValue(Class<?> returnType) {
        if (returnType == null || Void.TYPE.equals(returnType)) return null;
        if (!returnType.isPrimitive()) return null;
        if (Boolean.TYPE.equals(returnType)) return Boolean.FALSE;
        if (Character.TYPE.equals(returnType)) return Character.valueOf('\0');
        if (Byte.TYPE.equals(returnType)) return Byte.valueOf((byte) 0);
        if (Short.TYPE.equals(returnType)) return Short.valueOf((short) 0);
        if (Integer.TYPE.equals(returnType)) return Integer.valueOf(0);
        if (Long.TYPE.equals(returnType)) return Long.valueOf(0L);
        if (Float.TYPE.equals(returnType)) return Float.valueOf(0f);
        if (Double.TYPE.equals(returnType)) return Double.valueOf(0d);
        return null;
    }

    private static final class PresenceShape {
        final boolean safeToBlock;
        final boolean looksLikePresence;

        PresenceShape(boolean safeToBlock, boolean looksLikePresence) {
            this.safeToBlock = safeToBlock;
            this.looksLikePresence = looksLikePresence;
        }
    }

    public interface PresenceStatusSentListener {
        HookListenerResult<Boolean> onPresenceStatusSent();
    }
}