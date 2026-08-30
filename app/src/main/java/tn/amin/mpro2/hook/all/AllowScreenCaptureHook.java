package tn.amin.mpro2.hook.all;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import tn.amin.mpro2.debug.Logger;
import tn.amin.mpro2.hook.BaseHook;
import tn.amin.mpro2.hook.HookId;
import tn.amin.mpro2.hook.listener.HookListenerResult;
import tn.amin.mpro2.orca.OrcaGateway;

/**
 * Allows screenshots and screen recording in all conversations, including E2EE ones.
 * <p>
 * X.AjM.A00()V registers the ScreenshotContentObserver whose path later applies
 * FLAG_SECURE to the conversation window. No-op'ing A00 prevents the observer from
 * ever being registered, so FLAG_SECURE is never applied.
 * <p>
 * In Messenger 573 this class was X.9Sa; in 576 it was renamed to X.AjM.
 */
public class AllowScreenCaptureHook extends BaseHook {
    @Override
    public HookId getId() {
        return HookId.ALLOW_SCREEN_CAPTURE;
    }

    @Override
    protected Set<XC_MethodHook.Unhook> injectInternal(OrcaGateway gateway) {
        Set<XC_MethodHook.Unhook> hooks = new HashSet<>();
        try {
            Class<?> ajm = XposedHelpers.findClass("X.AjM", gateway.classLoader);
            for (Method m : ajm.getDeclaredMethods()) {
                if ("A00".equals(m.getName()) && m.getParameterCount() == 0) {
                    hooks.add(XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            notifyListenersWithResult((listener) ->
                                    ((AllowScreenCaptureListener) listener).onScreenCaptureRegistration());
                            HookListenerResult<Object> result = getListenersReturnValue();
                            if (result != null && result.isConsumed) {
                                param.setResult(null);
                            }
                        }
                    }));
                }
            }
        } catch (Throwable t) {
            Logger.warn("AllowScreenCaptureHook: X.AjM unavailable: " + t.getMessage());
        }
        return hooks;
    }

    public interface AllowScreenCaptureListener {
        HookListenerResult<Object> onScreenCaptureRegistration();
    }
}
