package tn.amin.mpro2.hook.helper;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import tn.amin.mpro2.debug.Logger;

public class OrcaHookHelper {
    public static Set<XC_MethodHook.Unhook> hookFeature(int featureId, String requiredPrefix, String category, ClassLoader classLoader, XC_MethodHook methodHook) {
        HashSet<XC_MethodHook.Unhook> unhooks = new HashSet<>();
        Class<?> cls = XposedHelpers.findClass(
                "com.facebook." + category.toLowerCase() + ".mca.Mailbox" + category + "JNI", classLoader);
        Logger.info("hookFeature: featureId=" + featureId + " prefix=dispatch" + requiredPrefix + " class=" + cls.getName());

        // Cache method lookups once instead of reflecting per-call
        Method cachedBefore = resolveHookMethod(methodHook, "beforeHookedMethod");
        Method cachedAfter = resolveHookMethod(methodHook, "afterHookedMethod");

        for (Method method: cls.getDeclaredMethods()) {
            if (method.getName().startsWith("dispatch" + requiredPrefix)) {
                Logger.info("hookFeature: hooking " + method.getName() + " paramCount=" + method.getParameterCount());

                unhooks.add(XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (matchesFeatureId(param, featureId) && cachedBefore != null) {
                            cachedBefore.invoke(methodHook, param);
                        }
                    }

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (matchesFeatureId(param, featureId) && cachedAfter != null) {
                            cachedAfter.invoke(methodHook, param);
                        }
                    }
                }));
            }
        }
        return unhooks;
    }

    public static Set<XC_MethodHook.Unhook> hookDispatch(String requiredPrefix, String category, ClassLoader classLoader, XC_MethodHook methodHook) {
        HashSet<XC_MethodHook.Unhook> unhooks = new HashSet<>();
        Class<?> cls = XposedHelpers.findClass(
                "com.facebook." + category.toLowerCase() + ".mca.Mailbox" + category + "JNI", classLoader);
        Logger.info("hookDispatch: prefix=dispatch" + requiredPrefix + " class=" + cls.getName());

        Method cachedBefore = resolveHookMethod(methodHook, "beforeHookedMethod");
        Method cachedAfter = resolveHookMethod(methodHook, "afterHookedMethod");

        for (Method method: cls.getDeclaredMethods()) {
            if (method.getName().startsWith("dispatch" + requiredPrefix)) {
                Logger.info("hookDispatch: hooking " + method.getName() + " paramCount=" + method.getParameterCount());

                unhooks.add(XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (cachedBefore != null) {
                            cachedBefore.invoke(methodHook, param);
                        }
                    }

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (cachedAfter != null) {
                            cachedAfter.invoke(methodHook, param);
                        }
                    }
                }));
            }
        }

        return unhooks;
    }

    private static boolean matchesFeatureId(XC_MethodHook.MethodHookParam param, int featureId) {
        if (param.args == null || param.args.length == 0) return false;
        if (!(param.args[0] instanceof Integer)) return false;
        return ((Integer) param.args[0]) == featureId;
    }

    private static Method resolveHookMethod(XC_MethodHook hook, String name) {
        try {
            Method m = hook.getClass().getDeclaredMethod(name, XC_MethodHook.MethodHookParam.class);
            m.setAccessible(true);
            return m;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }
}
