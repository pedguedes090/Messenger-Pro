package tn.amin.mpro2.hook.all;

import java.lang.reflect.Method;
import java.util.HashSet;
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
import tn.amin.mpro2.hook.unobfuscation.OrcaUnobfuscator;
import tn.amin.mpro2.orca.OrcaGateway;

public class SeenIndicatorHook extends BaseHook {
    private static final int ACTION_TYPING_SUBSCRIPTION = 81;
    private static final int ACTION_TYPING_OUTBOUND_V553 = 88;
    private static final int ACTION_SEEN_DISPATCH_V553 = 62;
    private static final int ACTION_MARK_READ_DISPATCH_V553 = 23;
    private static final int ACTION_SEEN_CANDIDATE_V553_ALT = 10;
    private static final int ACTION_CONVERSATION_ENTER = 6;
    private static final int ACTION_CONVERSATION_LEAVE = 7;
    private static final int ACTION_PRESENCE_ORCA_V553 = 8;
    private static final int ACTION_MESSAGE_SEND_V552 = 61;
    private static final int ACTION_MESSAGE_SEND_V553 = 71;
    private static final String MAILBOX_ORCA_JNI = "com.facebook.orca.mca.MailboxOrcaJNI";
    private static final int DEBUG_MISS_LOG_LIMIT = 200;

    private int mDebugMissLogCount = 0;

    @Override
    public HookId getId() {
        return HookId.SEEN_INDICATOR_SEND;
    }

    @Override
    public HookTime getHookTime() {
        return HookTime.AFTER_DEOBFUSCATION;
    }

    @Override
    protected Set<XC_MethodHook.Unhook> injectInternal(OrcaGateway gateway) {
        final boolean captureDebug = gateway.pref != null && gateway.pref.isTypingCaptureDebugEnabled();
        final int configuredSeenApiCode = gateway.unobfuscator.getAPICode(OrcaUnobfuscator.API_MESSAGE_SEEN);
        Logger.info("SeenIndicatorHook: configured legacy seen apiCode=" + configuredSeenApiCode);
        var wrapped = wrap(new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                String methodName = ((Method) param.method).getName();
                Integer actionCode = (param.args != null && param.args.length > 0 && param.args[0] instanceof Integer)
                        ? (Integer) param.args[0]
                        : null;

                if (actionCode == null) return;

                // Check if any arg is Mailbox type (seen indicator signal)
                boolean hasMailbox = hasMailboxArgNearStart(param.args);
                if (!hasMailbox) return;

                // Legacy seen-indicator pattern: Mailbox in args[1] or args[2], args[3] is Long.
                // Null in args[3] matches non-seen dispatches (including typing-related calls).
                boolean matchesLegacySeen = configuredSeenApiCode > 0
                    && param.args.length >= 4
                        && actionCode == configuredSeenApiCode
                        && (
                    (param.args[1] != null && param.args[1].getClass().getName().equals(OrcaClassNames.MAILBOX)) ||
                    (param.args.length > 2 && param.args[2] != null && param.args[2].getClass().getName().equals(OrcaClassNames.MAILBOX))
                ) && (param.args[3] != null && param.args[3].getClass().getName().equals(Long.class.getName()));

                String threadToken = findThreadTokenArg(param.args);
                boolean hasThreadToken = threadToken != null;
                boolean hasNotificationScope = hasNotificationScopeArg(param.args);
                boolean hasBoolean = hasBooleanArg(param.args);
                boolean excludedAction = isExcludedActionCode(actionCode);

                // Messenger 553 pattern observed in logs:
                // dispatchVOOOOOZ(Integer=62, Mailbox, threadToken, "", null, NotificationScope, Boolean)
                // Keep this resilient to arg index shifts across one-to-one vs group variants.
                boolean matchesV553Seen = actionCode != null
                    && actionCode == ACTION_SEEN_DISPATCH_V553
                    && methodName.startsWith("dispatchV")
                    && param.args.length >= 5
                    && hasThreadToken
                    && hasNotificationScope
                    && hasBoolean;

                // Version-resilient fallback for "seen-like" thread-scoped notification dispatches
                // where action codes can drift between Messenger builds.
                boolean matchesThreadScopedSeenFallback = !excludedAction
                        && methodName.startsWith("dispatchV")
                        && param.args.length >= 5
                        && hasThreadToken
                        && hasNotificationScope
                        && hasBoolean;

                    // User-requested hard block for 81 path.
                    // Common shape: dispatchVOOOO(Integer=81, Mailbox, threadToken, null, NotificationScope)
                        boolean matchesV553SubscriptionSeen = actionCode == ACTION_TYPING_SUBSCRIPTION
                            && methodName.startsWith("dispatchV")
                            && param.args.length >= 4
                            && hasThreadToken
                            && hasNotificationScope;

                    // Messenger 553 can also issue a compact mark-read dispatch:
                    // dispatchVO(Integer=23, Mailbox)
                    boolean matchesV553MarkRead = actionCode != null
                        && actionCode == ACTION_MARK_READ_DISPATCH_V553
                        && methodName.startsWith("dispatchV")
                        && param.args.length >= 2
                        && param.args.length <= 4;

                    // Compact mark-read fallback used by some versions/flows (especially private chats).
                    boolean matchesCompactMarkReadFallback = !excludedAction
                            && "dispatchVO".equals(methodName)
                            && param.args.length == 2
                            && isMailboxArg(param.args[1]);

                            // Additional candidate observed while opening chats on v553:
                        // - dispatchVOOO(Integer=10, Mailbox, NativeHolder, null)
                        boolean matchesSeenCandidateAction10 = actionCode == ACTION_SEEN_CANDIDATE_V553_ALT
                            && "dispatchVOOO".equals(methodName)
                            && param.args.length == 4
                            && isMailboxArg(param.args[1])
                            && isNativeHolderArg(param.args[2]);

                        boolean matchesSeen = matchesLegacySeen
                            || matchesV553Seen
                            || matchesThreadScopedSeenFallback
                            || matchesV553SubscriptionSeen
                            || matchesV553MarkRead
                            || matchesCompactMarkReadFallback
                            || matchesSeenCandidateAction10;

                if (matchesSeen) {
                    notifyListenersWithResult((listener) -> ((SeenIndicatorListener) listener).onSeenIndicator());
                    boolean allowSeen = !getListenersReturnValue().isConsumed || (Boolean) getListenersReturnValue().value;
                    if (!allowSeen) {
                        Logger.verbose("SeenIndicatorHook: blocked seen dispatch method=" + methodName
                                + " action=" + actionCode
                                + " args=" + summarizeArgs(param.args));
                        Method hookedMethod = (Method) param.method;
                        param.setResult(getDefaultReturnValue(hookedMethod.getReturnType()));
                    }
                } else if (captureDebug && shouldLogDebugMiss()) {
                    Logger.info("SeenIndicatorHook: unmatched mailbox dispatch method=" + methodName
                            + " action=" + actionCode
                            + " args=" + summarizeArgs(param.args));
                }
            }
        });

        Set<XC_MethodHook.Unhook> unhooks = new HashSet<>();
        hookDispatchVMethods(unhooks, gateway.classLoader, OrcaClassNames.MAILBOX_SDK_JNI, "SDK", wrapped);
        hookDispatchVMethods(unhooks, gateway.classLoader, OrcaClassNames.MAILBOX_CORE_JNI, "CORE", wrapped);
        hookDispatchVMethods(unhooks, gateway.classLoader, MAILBOX_ORCA_JNI, "ORCA", wrapped);
        return unhooks;
    }

    private void hookDispatchVMethods(Set<XC_MethodHook.Unhook> unhooks, ClassLoader classLoader,
                                      String className, String tag, XC_MethodHook hook) {
        Class<?> cls = findClassOrNull(className, classLoader);
        if (cls == null) {
            Logger.info("SeenIndicatorHook: " + tag + " class not found: " + className);
            return;
        }

        int hooked = 0;
        for (Method m : cls.getDeclaredMethods()) {
            if (!m.getName().startsWith("dispatchV")) continue;
            unhooks.add(XposedBridge.hookMethod(m, hook));
            hooked++;
        }
        Logger.info("SeenIndicatorHook: hooked " + hooked + " " + tag + " dispatchV methods");
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
        for (int i = 1; i < Math.min(args.length, 5); i++) {
            if (isMailboxArg(args[i])) {
                return true;
            }
        }
        return false;
    }

    private boolean isMailboxArg(Object arg) {
        if (arg == null) return false;

        String cls = arg.getClass().getName();
        return OrcaClassNames.MAILBOX.equals(cls) || cls.contains(".msys.mca.Mailbox");
    }

    private boolean isNativeHolderArg(Object arg) {
        if (arg == null) return false;

        String cls = arg.getClass().getName();
        return cls.contains("NativeHolder") || cls.contains("simplejni");
    }

    private boolean isExcludedActionCode(int actionCode) {
        return actionCode == ACTION_CONVERSATION_ENTER
                || actionCode == ACTION_CONVERSATION_LEAVE
                || actionCode == ACTION_PRESENCE_ORCA_V553
                || actionCode == ACTION_MESSAGE_SEND_V552
                || actionCode == ACTION_MESSAGE_SEND_V553
                || actionCode == ACTION_TYPING_OUTBOUND_V553;
    }

    private synchronized boolean shouldLogDebugMiss() {
        if (mDebugMissLogCount >= DEBUG_MISS_LOG_LIMIT) return false;
        mDebugMissLogCount++;
        return true;
    }

    private boolean isThreadTokenLike(String value) {
        if (value == null) return false;

        String trimmed = value.trim();
        if (trimmed.isEmpty()) return false;

        return trimmed.startsWith("T_")
                || trimmed.startsWith("ONE_TO_ONE")
                || trimmed.startsWith("GROUP")
                || trimmed.startsWith("THREAD")
                || trimmed.contains(":");
    }

    private String findThreadTokenArg(Object[] args) {
        if (args == null) return null;

        for (Object arg : args) {
            if (!(arg instanceof String)) continue;

            String candidate = (String) arg;
            if (isThreadTokenLike(candidate)) {
                return candidate;
            }
        }

        return null;
    }

    private boolean hasNotificationScopeArg(Object[] args) {
        if (args == null) return false;

        for (Object arg : args) {
            if (arg == null) continue;
            if (arg.getClass().getName().contains("NotificationScope")) {
                return true;
            }
        }

        return false;
    }

    private boolean hasBooleanArg(Object[] args) {
        if (args == null) return false;

        for (Object arg : args) {
            if (arg instanceof Boolean) {
                return true;
            }
        }

        return false;
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
                if (text.length() > 36) text = text.substring(0, 36) + "...";
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

    public interface SeenIndicatorListener {
        HookListenerResult<Boolean> onSeenIndicator();
    }
}
