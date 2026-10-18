package tn.amin.mpro2.hook.all;

import org.junit.Test;


import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MailboxIndicatorDispatchPolicyTest {
    private static final int ORCA_576 = 345212652;
    private static final int ORCA_583 = 346611418;

    @Test
    public void recognizesTypingDispatchActionForMessenger583AndKeeps576() {
        assertTrue(MailboxIndicatorDispatchPolicy.isKnownOutboundTypingDispatch(
                "SDK", "dispatchVOOOZ", 96, true, ORCA_583));
        assertTrue(MailboxIndicatorDispatchPolicy.isKnownOutboundTypingDispatch(
                "SDK", "dispatchVOOOZ", 91, true, ORCA_576));
        assertFalse(MailboxIndicatorDispatchPolicy.isKnownOutboundTypingDispatch(
                "SDK", "dispatchVOOOZ", 96, true, ORCA_576));
        assertFalse(MailboxIndicatorDispatchPolicy.isKnownOutboundTypingDispatch(
                "CORE", "dispatchVOOOZ", 96, true, ORCA_583));
        assertFalse(MailboxIndicatorDispatchPolicy.isKnownOutboundTypingDispatch(
                "SDK", "dispatchVOOOZ", 96, false, ORCA_583));
    }

    @Test
    public void recognizesMessenger583MarkReadWhenOptionalLongIdentifierIsNull() {
        Object[] args = {88, new Object(), "thread-id", null, "operation", new NotificationScopeProbe()};
        Class<?>[] parameterTypes = {
                int.class, Object.class, String.class, Object.class, String.class, NotificationScopeProbe.class
        };
        boolean nullableLongIdentifier = MailboxIndicatorDispatchPolicy
                .hasNullableLongIdentifierAtSlot(args, parameterTypes, 3);

        assertTrue(MailboxIndicatorDispatchPolicy.isMessenger583MarkReadDispatch(
                ORCA_583, 88, true, "dispatchVOOOOO", 6, true, nullableLongIdentifier, 2, true));
    }

    @Test
    public void allowsNullReadIdentifierInGenericObjectSlotButRejectsUnrelatedValues() {
        Object[] nullIdentifierArgs = {88, new Object(), "thread-id", null, "operation", new NotificationScopeProbe()};
        Object[] longIdentifierArgs = {
                88, new Object(), "thread-id", Long.valueOf(7), "operation", new NotificationScopeProbe()
        };
        Class<?>[] genericObjectParameters = {
                int.class, Object.class, String.class, Object.class, String.class, NotificationScopeProbe.class
        };
        Object[] unrelatedIdentifierArgs = {
                88, new Object(), "thread-id", "not-a-long", "operation", new NotificationScopeProbe()
        };
        Class<?>[] stringParameters = {
                int.class, Object.class, String.class, String.class, String.class, NotificationScopeProbe.class
        };

        assertTrue(MailboxIndicatorDispatchPolicy.hasNullableLongIdentifierAtSlot(
                nullIdentifierArgs, genericObjectParameters, 3));
        assertTrue(MailboxIndicatorDispatchPolicy.hasNullableLongIdentifierAtSlot(
                longIdentifierArgs, genericObjectParameters, 3));
        assertFalse(MailboxIndicatorDispatchPolicy.hasNullableLongIdentifierAtSlot(
                nullIdentifierArgs, stringParameters, 3));
        assertFalse(MailboxIndicatorDispatchPolicy.hasNullableLongIdentifierAtSlot(
                unrelatedIdentifierArgs, genericObjectParameters, 3));
    }

    @Test
    public void recognizesMessenger583MarkReadDispatchWithoutBooleanPayload() {
        assertTrue(MailboxIndicatorDispatchPolicy.isMessenger583MarkReadDispatch(
                ORCA_583, 88, true, "dispatchVOOOOO", 6, true, true, 1, true));
        assertFalse(MailboxIndicatorDispatchPolicy.isMessenger583MarkReadDispatch(
                ORCA_583, 88, true, "dispatchVOOOOO", 6, true, true, 1, false));
        assertFalse(MailboxIndicatorDispatchPolicy.isMessenger583MarkReadDispatch(
                ORCA_583, 88, true, "dispatchVOOOZ", 5, true, true, 1, true));
        assertFalse(MailboxIndicatorDispatchPolicy.isMessenger583MarkReadDispatch(
                ORCA_583, 96, true, "dispatchVOOOOO", 6, true, true, 1, true));
        assertFalse(MailboxIndicatorDispatchPolicy.isMessenger583MarkReadDispatch(
                ORCA_576, 88, true, "dispatchVOOOOO", 6, true, true, 1, true));
        assertFalse(MailboxIndicatorDispatchPolicy.isMessenger583MarkReadDispatch(
                ORCA_583, 88, true, "dispatchVOOOOO", 6, false, true, 1, true));
        assertFalse(MailboxIndicatorDispatchPolicy.isMessenger583MarkReadDispatch(
                ORCA_583, 88, true, "dispatchVOOOOO", 6, true, false, 1, true));
        assertFalse(MailboxIndicatorDispatchPolicy.isMessenger583MarkReadDispatch(
                ORCA_583, 88, true, "dispatchVOOOOO", 6, true, true, 0, true));
        assertFalse(MailboxIndicatorDispatchPolicy.isMessenger583MarkReadDispatch(
                ORCA_583, 88, false, "dispatchVOOOOO", 6, true, true, 1, true));
    }

    @Test
    public void doesNotReusePersistedLegacySeenActionOnMessenger583() {
        assertEquals(-1, MailboxIndicatorDispatchPolicy.resolveLegacySeenActionCode(ORCA_583, 62));
        assertEquals(62, MailboxIndicatorDispatchPolicy.resolveLegacySeenActionCode(ORCA_576, 62));
    }

    @Test
    public void recognizesMessenger583TypingThreadIdentifierArray() {
        Object[] messenger583Args = {
                96, new Object(), new String[] {"thread-id"}, new NotificationScopeProbe(), true
        };
        Object[] unrelatedArrayArgs = {
                96, new Object(), new String[0], new NotificationScopeProbe(), true
        };

        assertTrue(MailboxIndicatorDispatchPolicy.hasThreadScopedTypingPayload(messenger583Args));
        assertFalse(MailboxIndicatorDispatchPolicy.hasThreadScopedTypingPayload(unrelatedArrayArgs));
    }

    private static final class NotificationScopeProbe {
    }
}
