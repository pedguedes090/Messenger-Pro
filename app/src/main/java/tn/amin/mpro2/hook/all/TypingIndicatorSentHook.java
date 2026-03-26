package tn.amin.mpro2.hook.all;

import java.lang.reflect.Method;
import java.util.Arrays;
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
import tn.amin.mpro2.hook.unobfuscation.OrcaUnobfuscator;
import tn.amin.mpro2.orca.OrcaGateway;

public class TypingIndicatorSentHook extends BaseHook {
    private static final int OUTBOUND_TYPING_ACTION_V553 = 88;
    private static final String OUTBOUND_TYPING_METHOD_V553 = "dispatchVOOOZ";

    private static final Set<String> KNOWN_TYPING_DISPATCH_METHODS = new HashSet<>(Arrays.asList(
            "dispatchVOOOOZ",
            "dispatchVOOOOZZ",
            "dispatchVOOOZ"
    ));
    private static final int CAPTURE_LOG_LIMIT = 240;

    private int mCaptureLogCount = 0;

    @Override
    public HookId getId() {
        return HookId.TYPING_INDICATOR_SEND;
    }

    @Override
    public HookTime getHookTime() {
        return HookTime.AFTER_DEOBFUSCATION;
    }

    @Override
    protected Set<XC_MethodHook.Unhook> injectInternal(OrcaGateway gateway) {
        Class<?> TypingIndicatorDispatcher = gateway.unobfuscator.getClass(OrcaUnobfuscator.CLASS_TYPING_INDICATOR_DISPATCHER);
        boolean captureDebug = gateway.pref != null && gateway.pref.isTypingCaptureDebugEnabled();
        boolean forceBlock = gateway.pref != null && gateway.pref.isTypingForceBlockEnabled();
        String dispatcherClassName = TypingIndicatorDispatcher != null ? TypingIndicatorDispatcher.getName() : null;

        Logger.info("TypingIndicatorSentHook: captureDebug=" + captureDebug + " forceBlock=" + forceBlock);

        Set<XC_MethodHook.Unhook> unhooks = new HashSet<>();
        hookTypingDispatches(unhooks, gateway.classLoader, OrcaClassNames.MAILBOX_SDK_JNI, "SDK", captureDebug, forceBlock, dispatcherClassName);
        hookTypingDispatches(unhooks, gateway.classLoader, OrcaClassNames.MAILBOX_CORE_JNI, "CORE", captureDebug, forceBlock, dispatcherClassName);
        hookTypingDispatches(unhooks, gateway.classLoader, "com.facebook.orca.mca.MailboxOrcaJNI", "ORCA", captureDebug, forceBlock, dispatcherClassName);

        if (TypingIndicatorDispatcher != null) {
            int dispatcherHooks = 0;
            for (Method m : TypingIndicatorDispatcher.getDeclaredMethods()) {
                if (isIgnoredJavaObjectMethod(m.getName())) continue;

                unhooks.add(XposedBridge.hookMethod(m, wrap(new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (captureDebug && shouldLogCapture()) {
                            Logger.info("TypingCapture[DISPATCHER]: method=" + m.getName()
                                    + " args=" + summarizeArgs(param.args));
                        }

                        // New Messenger builds appear to route both outgoing and incoming typing
                        // through this dispatcher class. To avoid hiding "other user typing",
                        // only block this path in explicit force-block debug mode.
                        if (!forceBlock) return;

                        notifyListenersWithResult((listener) -> ((TypingIndicatorSentListener) listener).onTypingIndicatorSent());
                        boolean allowTypingIndicator = !getListenersReturnValue().isConsumed || (Boolean) getListenersReturnValue().value;
                        if (allowTypingIndicator) return;

                        forceBooleanArgsFalse(param.args);
                        Class<?> returnType = ((Method) param.method).getReturnType();
                        param.setResult(defaultReturnValue(returnType));
                    }
                })));
                dispatcherHooks++;
            }
            Logger.info("TypingIndicatorSentHook: hooked " + dispatcherHooks + " dispatcher methods");
        } else {
            Logger.warn("TypingIndicatorSentHook: TypingIndicatorDispatcher is null, fallback dispatch hooks only");
        }

        return unhooks;
    }

    private void hookTypingDispatches(Set<XC_MethodHook.Unhook> unhooks, ClassLoader classLoader, String className,
                                      String tag, boolean captureDebug, boolean forceBlock,
                                      String dispatcherClassName) {
        Class<?> mailboxClass = findClassOrNull(className, classLoader);
        if (mailboxClass == null) {
            Logger.info("TypingIndicatorSentHook: " + tag + " class not found: " + className);
            return;
        }

        int hooked = 0;
        for (Method method : mailboxClass.getDeclaredMethods()) {
            if (!method.getName().startsWith("dispatchV") || !method.getName().endsWith("Z")) continue;

            unhooks.add(XposedBridge.hookMethod(method, wrap(new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (!looksLikeTypingCandidate(param.args)) return;

                    boolean strictMatch = looksLikeTypingDispatch(method.getName(), param.args);
                    if (captureDebug && shouldLogCapture()) {
                        Logger.info("TypingCapture[" + tag + "]: method=" + method.getName()
                                + " strict=" + strictMatch
                                + " action=" + getActionCode(param.args)
                                + " args=" + summarizeArgs(param.args));
                    }

                    if (!strictMatch && !forceBlock) return;

                    // Enforce blocking only on the known outbound typing signature.
                    // Combined with targeted boolean mutation, this avoids broad side effects.
                    boolean allowBlockOnThisPath = forceBlock
                            || isKnownOutboundTypingDispatch(tag, method.getName(), param.args);
                    if (!allowBlockOnThisPath) return;

                    if (captureDebug && !forceBlock && shouldLogCapture()) {
                        Logger.info("TypingCapture[" + tag + "]: blocking confirmed method=" + method.getName()
                                + " action=" + getActionCode(param.args));
                    }

                    notifyListenersWithResult((listener) -> ((TypingIndicatorSentListener) listener).onTypingIndicatorSent());
                    boolean allowTypingIndicator = !getListenersReturnValue().isConsumed || (Boolean) getListenersReturnValue().value;
                    if (!allowTypingIndicator) {
                        boolean changed;
                        if (forceBlock) {
                            changed = forceBooleanArgsFalse(param.args);
                        } else {
                            changed = suppressKnownOutboundTypingCall(param, tag, method.getName(), param.args);
                        }
                        if (!changed) {
                            Logger.warn("TypingIndicatorSentHook: skip block (no safe target) "
                                    + tag + " method=" + method.getName());
                            return;
                        }
                        Object actionCode = (param.args != null && param.args.length > 0) ? param.args[0] : "?";
                        Logger.verbose("TypingIndicatorSentHook: blocked " + tag + " typing method=" + method.getName() + " action=" + actionCode);
                    }
                }
            })));
            hooked++;
        }

        Logger.info("TypingIndicatorSentHook: hooked " + hooked + " " + tag + " dispatch methods");
    }

    private Class<?> findClassOrNull(String className, ClassLoader classLoader) {
        try {
            return XposedHelpers.findClass(className, classLoader);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private boolean looksLikeTypingDispatch(String methodName, Object[] args) {
        if (args == null || args.length < 3) return false;

        boolean hasMailboxArg = false;
        boolean hasTypingMarker = false;
        boolean hasThreadMarker = false;

        for (Object arg : args) {
            if (arg == null) continue;

            String className = arg.getClass().getName();
            if (OrcaClassNames.MAILBOX.equals(className) || className.contains(".msys.mca.Mailbox")) {
                hasMailboxArg = true;
            }

            if (className.contains("ThreadKey") || className.contains("6kh")) {
                hasThreadMarker = true;
            }

            if (arg instanceof String) {
                String value = ((String) arg).toLowerCase(Locale.ROOT);
                if (value.contains("typing") || value.contains("t_st")) {
                    hasTypingMarker = true;
                }
            }
        }

        if (!hasMailboxArg || !hasTrueBooleanArg(args)) return false;
        return hasTypingMarker || hasThreadMarker || KNOWN_TYPING_DISPATCH_METHODS.contains(methodName);
    }

    private boolean looksLikeTypingCandidate(Object[] args) {
        if (args == null || args.length < 3) return false;

        boolean hasMailboxArg = false;
        for (Object arg : args) {
            if (arg == null) continue;
            String className = arg.getClass().getName();
            if (OrcaClassNames.MAILBOX.equals(className) || className.contains(".msys.mca.Mailbox")) {
                hasMailboxArg = true;
                break;
            }
        }

        return hasMailboxArg && hasTrueBooleanArg(args);
    }

    private boolean hasTrueBooleanArg(Object[] args) {
        if (args == null) return false;
        for (Object arg : args) {
            if (arg instanceof Boolean && Boolean.TRUE.equals(arg)) {
                return true;
            }
        }
        return false;
    }

    private boolean forceBooleanArgsFalse(Object[] args) {
        boolean changed = false;
        if (args == null) return false;

        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof Boolean && Boolean.TRUE.equals(args[i])) {
                args[i] = Boolean.FALSE;
                changed = true;
            }
        }
        return changed;
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
                continue;
            }

            String cls = arg.getClass().getSimpleName();
            sb.append(i).append(":").append(cls).append("=");
            if (arg instanceof String) {
                String s = (String) arg;
                sb.append('"').append(trimForLog(s, 40)).append('"');
            } else {
                sb.append(trimForLog(String.valueOf(arg), 32));
            }
        }
        sb.append("]");
        return sb.toString();
    }

    private Object getActionCode(Object[] args) {
        if (args == null || args.length == 0) return "?";
        return args[0];
    }

    private String trimForLog(String value, int max) {
        if (value == null) return "null";
        if (value.length() <= max) return value;
        return value.substring(0, max) + "...";
    }

    private boolean isKnownOutboundTypingDispatch(String tag, String methodName, Object[] args) {
        if (!"SDK".equals(tag)) return false;
        if (!OUTBOUND_TYPING_METHOD_V553.equals(methodName)) return false;
        Integer actionCode = getActionCodeInt(args);
        if (actionCode == null || actionCode != OUTBOUND_TYPING_ACTION_V553) return false;
        return isThreadScopedTypingArgs(args);
    }

    private boolean isThreadScopedTypingArgs(Object[] args) {
        if (args == null || args.length < 5) return false;

        Object threadToken = args[2];
        Object scopeObj = args[3];
        Object boolObj = args[4];

        if (!(threadToken instanceof String)) return false;
        String token = (String) threadToken;
        if (!token.startsWith("T_")) return false;

        if (scopeObj == null) return false;
        String scopeClass = scopeObj.getClass().getName();
        if (!scopeClass.equals(OrcaClassNames.NOTIFICATION_SCOPE)
                && !scopeClass.contains("NotificationScope")) {
            return false;
        }

        return Boolean.TRUE.equals(boolObj);
    }

    private boolean suppressKnownOutboundTypingCall(XC_MethodHook.MethodHookParam param, String tag, String methodName, Object[] args) {
        if (!isKnownOutboundTypingDispatch(tag, methodName, args)) return false;
        Method hookedMethod = (Method) param.method;
        param.setResult(defaultReturnValue(hookedMethod.getReturnType()));
        return true;
    }

    private Integer getActionCodeInt(Object[] args) {
        if (args == null || args.length == 0) return null;
        if (args[0] instanceof Integer) return (Integer) args[0];
        return null;
    }

    private boolean isIgnoredJavaObjectMethod(String name) {
        return "equals".equals(name)
                || "hashCode".equals(name)
                || "toString".equals(name)
                || "getClass".equals(name);
    }

    private Object defaultReturnValue(Class<?> returnType) {
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

    public interface TypingIndicatorSentListener {
        HookListenerResult<Boolean> onTypingIndicatorSent();
    }
}
