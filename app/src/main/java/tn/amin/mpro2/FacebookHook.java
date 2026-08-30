package tn.amin.mpro2;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import de.robv.android.xposed.callbacks.XC_LoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import tn.amin.mpro2.debug.Logger;
import tn.amin.mpro2.hook.unobfuscation.KatanaUnobfuscator;

import java.lang.reflect.Method;

/**
 * Facebook (com.facebook.katana) patches.
 * <p>
 * Facebook obfuscates class/method names with Redex and renames them on EVERY build,
 * so nothing here is hardcoded by name. Instead every target is located dynamically
 * via {@link KatanaUnobfuscator} using STABLE string anchors (GraphQL wire names /
 * log strings that cannot be obfuscated).
 * <p>
 * Facebook 576 also uses "superpack": the real code lives in secondary DEX packed in
 * a zip at {@code /data/data/com.facebook.katana/dex/z-*.zip}, loaded lazily. So all
 * hooks are deferred until the first Activity is created (background thread).
 */
public class FacebookHook {
    public static final String FACEBOOK_PACKAGE = "com.facebook.katana";

    public static void install(final XC_LoadPackage.LoadPackageParam lpparam) {
        Log.e("MProFB", "install() called");
        final ClassLoader classLoader = lpparam.classLoader;
        final String dataDir = lpparam.appInfo.dataDir;
        try {
            XposedHelpers.findAndHookMethod(
                    "android.app.Activity", classLoader, "onCreate", android.os.Bundle.class,
                    new XC_MethodHook() {
                        private volatile boolean started = false;

                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            if (started) {
                                return;
                            }
                            started = true;
                            final Context context = (Context) param.thisObject;
                            Log.e("MProFB", "Activity.onCreate fired");
                            Thread t = new Thread(
                                    () -> installAllHooks(context, dataDir, classLoader),
                                    "mpro-katana-unobf");
                            t.setDaemon(true);
                            t.start();
                        }
                    });
            Logger.info("FacebookHook: deferred setup scheduled");
        } catch (Throwable t) {
            Log.e("MProFB", "setup failed: " + t, t);
            Logger.warn("FacebookHook: setup failed: " + t.getMessage());
        }
    }

    private static void installAllHooks(final Context context, String dataDir, ClassLoader classLoader) {
        String dexPath = KatanaUnobfuscator.findSuperpackDexPath(dataDir);
        Log.e("MProFB", "installAllHooks dexPath=" + dexPath);
        if (dexPath == null) {
            toast(context, "MPro FB: KHONG tim thay superpack dex");
            return;
        }
        try {
            KatanaUnobfuscator unobfuscator = new KatanaUnobfuscator(dexPath, classLoader);
            int ok = 0;
            if (installStorySeenBlock(unobfuscator)) ok++;
            if (installStorySeenLocalBlock(unobfuscator)) ok++;
            if (installStorySeenSyncBlock(unobfuscator)) ok++;
            if (installStorySeenReceiptsBlock(unobfuscator)) ok++;
            if (installStoryViewerSeenHelperBlock(unobfuscator)) ok++;
            toast(context, "MPro FB: da cai " + ok + "/5 hook chan seen");
        } catch (Throwable t) {
            Log.e("MProFB", "unobfuscation failed: " + t, t);
            toast(context, "MPro FB: LOI " + t.getMessage());
        }
    }

    private static void toast(Context context, String msg) {
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
            } catch (Throwable ignored) {
            }
        });
    }

    private static boolean installStorySeenBlock(KatanaUnobfuscator unobfuscator) {
        try {
            Method method = unobfuscator.loadStorySeenBuilderMethod();
            if (method == null) return false;
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    param.setResult(null);
                }
            });
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean installStorySeenLocalBlock(KatanaUnobfuscator unobfuscator) {
        try {
            Method method = unobfuscator.loadStorySeenLocalMethod();
            if (method == null) return false;
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    param.setResult(0);
                }
            });
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean installStorySeenSyncBlock(KatanaUnobfuscator unobfuscator) {
        try {
            Method method = unobfuscator.loadMethodByStringAnchor("setStorySeen");
            if (method == null) return false;
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    param.setResult(null);
                }
            });
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean installStorySeenReceiptsBlock(KatanaUnobfuscator unobfuscator) {
        try {
            Method method = unobfuscator.loadMethodByStringAnchor("StorySeenReceiptsSeenTimeUpdateMutation");
            if (method == null) return false;
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    param.setResult(null);
                }
            });
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean installStoryViewerSeenHelperBlock(KatanaUnobfuscator unobfuscator) {
        try {
            Class<?> seenHelperClass = unobfuscator.loadClassByStringAnchor("found_aggregated_bucket");
            if (seenHelperClass == null) return false;
            for (Method method : seenHelperClass.getDeclaredMethods()) {
                if (method.getName().startsWith("<")) continue;
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        param.setResult(null);
                    }
                });
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
