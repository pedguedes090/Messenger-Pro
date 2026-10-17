package tn.amin.mpro2.hook.all;

import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MessageReceivedHookTest {
    public void dispatch(int action, int eventKind, long threadKey, Object sender,
                         Object timestamp, Object messageId, Object messageKey,
                         Object content, Object trailing) {
    }

    public void primitivePayload(int action, int eventKind, long threadKey, int sender,
                                 Object timestamp, Object messageId, Object messageKey,
                                 Object content, Object trailing) {
    }

    public String wrongReturnType(int action, int eventKind, long threadKey, Object sender,
                                  Object timestamp, Object messageId, Object messageKey,
                                  Object content, Object trailing) {
        return "";
    }

    @Test
    public void acceptsOnlyVoidDispatchMethodsWithExpectedPayloadShape() throws Exception {
        Method valid = getClass().getMethod("dispatch", int.class, int.class, long.class,
                Object.class, Object.class, Object.class, Object.class, Object.class, Object.class);
        Method primitive = getClass().getMethod("primitivePayload", int.class, int.class,
                long.class, int.class, Object.class, Object.class, Object.class, Object.class,
                Object.class);
        Method wrongReturn = getClass().getMethod("wrongReturnType", int.class, int.class,
                long.class, Object.class, Object.class, Object.class, Object.class, Object.class,
                Object.class);

        assertTrue(NotificationDispatchSignature.matches(valid));
        assertFalse(NotificationDispatchSignature.matches(primitive));
        assertFalse(NotificationDispatchSignature.matches(wrongReturn));
    }

    @Test
    public void doesNotGuessNotificationActionForNewBuilds() {
        assertEquals(29, NotificationDispatchPolicy.resolve(-1, 345212666,
                345212666, 29));
        assertEquals(-1, NotificationDispatchPolicy.resolve(-1, 346611418,
                345212666, 29));
        assertEquals(26, NotificationDispatchPolicy.resolve(26, 346611418,
                345212666, 29));
        assertEquals(31, NotificationDispatchPolicy.resolve(31, 346611418,
                345212666, 29));
    }
}
