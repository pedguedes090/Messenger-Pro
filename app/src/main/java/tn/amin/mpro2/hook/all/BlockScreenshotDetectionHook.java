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
 * Prevents Messenger from notifying other participants when a screenshot or screen
 * recording is taken. ScreenshotContentObserver.onChange dispatches the "screenshot
 * taken" event; no-op'ing it silences the whole detection/notification pipeline.
 */
public class BlockScreenshotDetectionHook extends BaseHook {
    @Override
    public HookId getId() {
        return HookId.BLOCK_SCREENSHOT_DETECTION;
    }

    @Override
    protected Set<XC_MethodHook.Unhook> injectInternal(OrcaGateway gateway) {
        Set<XC_MethodHook.Unhook> hooks = new HashSet<>();
        try {
            Class<?> observer = XposedHelpers.findClass(
                    "com.facebook.screenshot.ScreenshotContentObserver", gateway.classLoader);
            for (Method m : observer.getDeclaredMethods()) {
                if ("onChange".equals(m.getName())) {
                    hooks.add(XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            notifyListenersWithResult((listener) ->
                                    ((BlockScreenshotDetectionListener) listener).onScreenshotDetected());
                            HookListenerResult<Object> result = getListenersReturnValue();
                            if (result != null && result.isConsumed) {
                                param.setResult(null);
                            }
                        }
                    }));
                }
            }
        } catch (Throwable t) {
            Logger.warn("BlockScreenshotDetectionHook: ScreenshotContentObserver unavailable: " + t.getMessage());
        }
        return hooks;
    }

    public interface BlockScreenshotDetectionListener {
        HookListenerResult<Object> onScreenshotDetected();
    }
}
