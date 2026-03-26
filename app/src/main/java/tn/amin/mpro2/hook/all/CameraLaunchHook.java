package tn.amin.mpro2.hook.all;

import android.hardware.camera2.CameraManager;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import tn.amin.mpro2.debug.Logger;
import tn.amin.mpro2.hook.BaseHook;
import tn.amin.mpro2.hook.HookId;
import tn.amin.mpro2.hook.HookTime;
import tn.amin.mpro2.hook.listener.HookListenerResult;
import tn.amin.mpro2.orca.OrcaGateway;

public class CameraLaunchHook extends BaseHook {

    private static final long DEBOUNCE_MS = 10000;
    private final AtomicLong lastLaunchTime = new AtomicLong(0);

    @Override
    public HookId getId() {
        return HookId.CAMERA_LAUNCH;
    }

    @Override
    public HookTime getHookTime() {
        return HookTime.UI;
    }

    @Override
    protected Set<XC_MethodHook.Unhook> injectInternal(OrcaGateway gateway) {
        Set<XC_MethodHook.Unhook> hooks = new HashSet<>();

        // Hook CameraManager.openCamera() - Messenger uses Camera2 API inline
        try {
            hooks.addAll(XposedBridge.hookAllMethods(
                    CameraManager.class, "openCamera",
                    wrapIgnoreWorking(new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            String cameraId = (String) param.args[0];
                            Logger.info("CameraLaunchHook: openCamera called, cameraId=" + cameraId);

                            // Debounce: skip if launched within last 5 seconds
                            long now = System.currentTimeMillis();
                            long last = lastLaunchTime.get();
                            if (now - last < DEBOUNCE_MS) {
                                Logger.info("CameraLaunchHook: debounced (" + (now - last) + "ms since last)");
                                param.setResult(null);
                                return;
                            }

                            notifyListenersWithResult((listener) ->
                                    ((CameraLaunchListener) listener).onCameraLaunch());
                            HookListenerResult<Boolean> result = getListenersReturnValue();

                            if (result != null && result.isConsumed && Boolean.TRUE.equals(result.value)) {
                                lastLaunchTime.set(now);
                                Logger.info("CameraLaunchHook: intercepted, launching default camera");
                                param.setResult(null);
                            }
                        }
                    })
            ));
            Logger.info("CameraLaunchHook: hooked CameraManager.openCamera()");
        } catch (Throwable t) {
            Logger.error("CameraLaunchHook: failed to hook CameraManager.openCamera(): " + t.getMessage());
        }

        return hooks;
    }

    public interface CameraLaunchListener {
        HookListenerResult<Boolean> onCameraLaunch();
    }
}
