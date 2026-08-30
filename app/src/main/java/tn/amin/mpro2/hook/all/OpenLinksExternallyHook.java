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
 * Opens links in the external browser instead of the in-app browser.
 * <p>
 * MessengerBrowserLauncher.A0M(Uri, FbUserSession) returns true to use the in-app
 * browser and false to skip it. Forcing false hands every URL to the default
 * external browser.
 * <p>
 * Ported from morphe-patches "Open links externally" (originally X.KU2.A0H in
 * Messenger 573; in 576 it lives in the non-obfuscated MessengerBrowserLauncher as A0M).
 */
public class OpenLinksExternallyHook extends BaseHook {
    @Override
    public HookId getId() {
        return HookId.OPEN_LINKS_EXTERNALLY;
    }

    @Override
    protected Set<XC_MethodHook.Unhook> injectInternal(OrcaGateway gateway) {
        Set<XC_MethodHook.Unhook> hooks = new HashSet<>();
        try {
            Class<?> launcher = XposedHelpers.findClass(
                    "com.facebook.messaging.browser.util.MessengerBrowserLauncher", gateway.classLoader);
            for (Method m : launcher.getDeclaredMethods()) {
                if ("A0M".equals(m.getName())
                        && m.getReturnType() == boolean.class
                        && m.getParameterCount() == 2) {
                    hooks.add(XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            notifyListenersWithResult((listener) ->
                                    ((OpenLinksExternallyListener) listener).onLinkOpen());
                            HookListenerResult<Object> result = getListenersReturnValue();
                            if (result != null && result.isConsumed) {
                                param.setResult(false);
                            }
                        }
                    }));
                }
            }
        } catch (Throwable t) {
            Logger.warn("OpenLinksExternallyHook: MessengerBrowserLauncher unavailable: " + t.getMessage());
        }
        return hooks;
    }

    public interface OpenLinksExternallyListener {
        HookListenerResult<Object> onLinkOpen();
    }
}
