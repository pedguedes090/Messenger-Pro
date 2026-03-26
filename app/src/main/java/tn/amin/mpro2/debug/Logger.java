package tn.amin.mpro2.debug;

import android.content.Intent;
import android.util.Log;

import androidx.core.util.Supplier;

import org.apache.commons.lang3.builder.RecursiveToStringStyle;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.StandardToStringStyle;

public class Logger {
    public static Supplier<Boolean> verbosePermissionSupplier = null;

    public static final String TAG_MPRO2 = "MPro2";

    public static void warn(String message) {
        log("W", message);
    }

    public static void info(String message) {
        log("I", message);
    }

    public static void logNoXposed(String message) {
        Log.d("LSPosed-Bridge", "(" + TAG_MPRO2 + ") [I] " + message);
    }

    public static void verbose(String message) {
        if (verbosePermissionSupplier == null || verbosePermissionSupplier.get()) {
            log("V", message);
        }
    }

    public static void error(Throwable t) {
        if (!logThrowableToXposed(t)) {
            Log.e("LSPosed-Bridge", "(" + TAG_MPRO2 + ") [E] " + Log.getStackTraceString(t));
        }
    }

    public static void error(String message) {
        log("E", message);
    }

    private static void log(String level, String message) {
        String formatted = "(" + TAG_MPRO2 + ") [" + level + "] " + message;
        if (!logToXposed(formatted)) {
            Log.d("LSPosed-Bridge", formatted);
        }
    }

    private static boolean logToXposed(String message) {
        try {
            Class<?> cls = Class.forName("de.robv.android.xposed.XposedBridge");
            cls.getMethod("log", String.class).invoke(null, message);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean logThrowableToXposed(Throwable throwable) {
        try {
            Class<?> cls = Class.forName("de.robv.android.xposed.XposedBridge");
            cls.getMethod("log", Throwable.class).invoke(null, throwable);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void logST() {
        verbose(Log.getStackTraceString(new Throwable()));
    }

    public static void logObject(Object o) {
        try {
            info(ReflectionToStringBuilder.toString(o, new StandardToStringStyle()));
        } catch (Throwable t) {
            info("Oh no! " + Log.getStackTraceString(t));
        }
    }

    public static void logObjectRecursive(Object o) {
        try {
            info(ReflectionToStringBuilder.toString(o, new RecursiveToStringStyle()));
        } catch (Throwable t) {
            info("Oh no! " + Log.getStackTraceString(t));
        }
    }

    public static void logExtras(Intent intent) {
        if (intent != null && intent.getExtras() != null) {
            for (String key: intent.getExtras().keySet()) {
                Object object = intent.getExtras().get(key);
                String type = "null";
                if (object != null)
                    type = object.getClass().getName();
                Logger.verbose("[" + key + "](" + type + ") = " + object);
            }
        }
    }
}
