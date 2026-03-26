package tn.amin.mpro2.ui;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import tn.amin.mpro2.debug.Logger;

public class WrapperHelper {
    public static boolean fieldSet(Field field, Object thisObject, Object object) {
        if (field == null || thisObject == null) {
            return false;
        }
        try {
            field.setAccessible(true);
            field.set(thisObject, object);
        } catch (Throwable e) {
            Logger.error(e);
            return false;
        }
        return true;
    }

    public static Object fieldGet(Field field, Object thisObject) {
        if (field == null || thisObject == null) {
            return null;
        }
        try {
            field.setAccessible(true);
            return field.get(thisObject);
        } catch (Throwable e) {
            Logger.error(e);
            return null;
        }
    }

    public static Object methodGet(Method method, Object thisObject) {
        if (method == null || thisObject == null) {
            return null;
        }
        try {
            method.setAccessible(true);
            return method.invoke(thisObject);
        } catch (IllegalAccessException | InvocationTargetException e) {
            Logger.error(e);
            return null;
        } catch (Throwable e) {
            Logger.error(e);
            return null;
        }
    }
}
