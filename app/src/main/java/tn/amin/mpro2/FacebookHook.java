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
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Facebook (com.facebook.katana) patches.
 * <p>
 * Facebook obfuscates class/method names with Redex and renames them on EVERY build,
 * so nothing here is hardcoded by name. Instead every target is located dynamically
 * via {@link KatanaUnobfuscator} using STABLE string anchors (GraphQL wire names /
 * log strings that cannot be obfuscated).
 * <p>
 * Some Facebook builds use "superpack" and expose DEX in a zip under the app data
 * directory; other builds keep DEX in the installed APK. Hook discovery is deferred
 * until the first Activity is created and runs on a background thread.
 */
public class FacebookHook {
    public static final String FACEBOOK_PACKAGE = "com.facebook.katana";
    private static final int STORY_SUPPRESSION_LOG_LIMIT = 20;
    private static final AtomicInteger STORY_SUPPRESSION_LOG_COUNT = new AtomicInteger();

    public static void install(final XC_LoadPackage.LoadPackageParam lpparam) {
        Log.e("MProFB", "install() called");
        final ClassLoader classLoader = lpparam.classLoader;
        final String dataDir = lpparam.appInfo.dataDir;
        final String sourceDir = lpparam.appInfo.sourceDir;
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
                                    () -> installAllHooks(context, dataDir, sourceDir, classLoader),
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

    private static void installAllHooks(final Context context, String dataDir,
                                        String sourceDir, ClassLoader classLoader) {
        String dexPath = KatanaUnobfuscator.findSuperpackDexPath(dataDir);
        if (dexPath == null && sourceDir != null && new java.io.File(sourceDir).isFile()) {
            dexPath = sourceDir;
            Logger.info("FacebookHook: scanning installed APK DEX container");
        }
        Log.e("MProFB", "installAllHooks dexPath=" + dexPath);
        if (dexPath == null) {
            Logger.warn("FacebookHook: no Superpack DEX or APK source available");
            toast(context, "MPro FB: khong tim thay DEX");
            return;
        }
        try {
            long startedAt = System.currentTimeMillis();
            Logger.info("FacebookHook: indexing Story seen targets from "
                    + (dexPath.equals(sourceDir) ? "installed APK" : "Superpack DEX"));
            KatanaUnobfuscator unobfuscator = new KatanaUnobfuscator(dexPath, classLoader);
            Logger.info("FacebookHook: Story seen DEX index ready in "
                    + (System.currentTimeMillis() - startedAt) + "ms");
            int ok = 0;
            if (installStorySeenBlock(unobfuscator)) ok++;
            if (installStorySeenLocalBlock(unobfuscator)) ok++;
            if (installStorySeenSyncBlock(unobfuscator)) ok++;
            if (installStorySeenReceiptsBlock(unobfuscator)) ok++;
            Logger.info("FacebookHook: installed " + ok + "/4 targeted Story seen hooks");
            toast(context, "MPro FB: da cai " + ok + "/4 story hooks");
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
            if (method == null) {
                Logger.warn("FacebookHook: direct Story seen entry not found");
                return false;
            }
            if (method.getReturnType() != Void.TYPE) {
                Logger.warn("FacebookHook: reject direct Story seen target with non-void return: "
                        + describeMethod(method));
                return false;
            }
            Logger.info("FacebookHook: resolved direct Story seen entry " + describeMethod(method));
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    param.setResult(null);
                    logStorySuppression("direct mutation");
                }
            });
            Logger.info("FacebookHook: installed direct Story seen hook");
            return true;
        } catch (Throwable t) {
            Logger.warn("FacebookHook: direct Story seen hook failed: " + t.getClass().getSimpleName()
                    + ": " + t.getMessage());
            return false;
        }
    }

    private static boolean installStorySeenLocalBlock(KatanaUnobfuscator unobfuscator) {
        try {
            Method method = unobfuscator.loadStorySeenLocalMethod();
            if (method == null) {
                Logger.warn("FacebookHook: local Story seen writer not found");
                return false;
            }
            if (method.getReturnType() != Integer.TYPE) {
                Logger.warn("FacebookHook: reject local Story seen target with non-int return: "
                        + describeMethod(method));
                return false;
            }
            Logger.info("FacebookHook: resolved local Story seen writer " + describeMethod(method));
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    param.setResult(0);
                    logStorySuppression("local state");
                }
            });
            Logger.info("FacebookHook: installed local Story seen hook");
            return true;
        } catch (Throwable t) {
            Logger.warn("FacebookHook: local Story seen hook failed: " + t.getClass().getSimpleName()
                    + ": " + t.getMessage());
            return false;
        }
    }

    private static boolean installStorySeenSyncBlock(KatanaUnobfuscator unobfuscator) {
        try {
            Method method = unobfuscator.loadStorySeenCsrCallbackMethod();
            if (method == null) {
                Logger.warn("FacebookHook: FeedCSR Story seen callback not found");
                return false;
            }
            Logger.info("FacebookHook: resolved FeedCSR Story seen callback " + describeMethod(method));
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    param.setResult(null);
                    logStorySuppression("FeedCSR callback");
                }
            });
            Logger.info("FacebookHook: installed FeedCSR Story seen hook");
            return true;
        } catch (Throwable t) {
            Logger.warn("FacebookHook: FeedCSR Story seen hook failed: " + t.getClass().getSimpleName()
                    + ": " + t.getMessage());
            return false;
        }
    }

    private static boolean installStorySeenReceiptsBlock(KatanaUnobfuscator unobfuscator) {
        try {
            Method method = unobfuscator.loadStorySeenReceiptsEntryMethod();
            if (method == null) {
                Logger.warn("FacebookHook: Story seen-receipts mutation entry not found");
                return false;
            }
            if (method.getReturnType() != Void.TYPE) {
                Logger.warn("FacebookHook: reject Story seen-receipts target with non-void return: "
                        + describeMethod(method));
                return false;
            }
            Logger.info("FacebookHook: resolved Story seen-receipts entry " + describeMethod(method));
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    param.setResult(null);
                    logStorySuppression("receipt mutation");
                }
            });
            Logger.info("FacebookHook: installed Story seen-receipts hook");
            return true;
        } catch (Throwable t) {
            Logger.warn("FacebookHook: Story seen-receipts hook failed: " + t.getClass().getSimpleName()
                    + ": " + t.getMessage());
            return false;
        }
    }

    private static String describeMethod(Method method) {
        StringBuilder signature = new StringBuilder(method.getDeclaringClass().getName())
                .append('#').append(method.getName()).append('(');
        Class<?>[] parameterTypes = method.getParameterTypes();
        for (int i = 0; i < parameterTypes.length; i++) {
            if (i > 0) signature.append(',');
            signature.append(parameterTypes[i].getName());
        }
        return signature.append(")->").append(method.getReturnType().getName()).toString();
    }

    private static void logStorySuppression(String target) {
        if (STORY_SUPPRESSION_LOG_COUNT.getAndIncrement() < STORY_SUPPRESSION_LOG_LIMIT) {
            Logger.info("FacebookHook: suppressed Story seen via " + target);
        }
    }

}
