package tn.amin.mpro2.hook.all;

import java.lang.reflect.Method;

/** Pure signature predicate, kept independent of the Xposed runtime for testing. */
final class NotificationDispatchSignature {
    private NotificationDispatchSignature() {
    }

    static boolean matches(Method method) {
        Class<?>[] parameters = method.getParameterTypes();
        return method.getReturnType() == void.class
                && parameters.length >= 9
                && parameters[0] == int.class
                && parameters[2] == long.class
                && !parameters[3].isPrimitive()
                && !parameters[6].isPrimitive()
                && !parameters[8].isPrimitive();
    }
}
