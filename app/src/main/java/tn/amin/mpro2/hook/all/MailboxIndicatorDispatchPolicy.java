package tn.amin.mpro2.hook.all;

/** Version-verified Mailbox actions for read receipts and outbound typing. */
final class MailboxIndicatorDispatchPolicy {
    static final int ORCA_576_VERSION = 345212652;
    static final int ORCA_583_VERSION = 346611418;

    // dispatchVOOOZ for setTypingIndicatorForThreadWithThreadIdentifier is 96 on Orca 583;
    // 91 remains the previously verified action for older supported builds.
    private static final int LEGACY_TYPING_ACTION = 91;
    static final int ORCA_583_TYPING_ACTION = 96;
    // markAsReadThreadWithThreadIdentifier dispatches action 88 via dispatchVOOOOO on Orca 583.
    static final int ORCA_583_MARK_READ_ACTION = 88;
    static final String ORCA_583_MARK_READ_METHOD = "dispatchVOOOOO";

    private MailboxIndicatorDispatchPolicy() {
    }

    static boolean isKnownOutboundTypingDispatch(String tag, String methodName, int actionCode,
                                                 boolean hasThreadScopedTypingPayload,
                                                 int orcaVersion) {
        if (!"SDK".equals(tag) || !"dispatchVOOOZ".equals(methodName)
                || !hasThreadScopedTypingPayload) return false;

        if (orcaVersion == ORCA_583_VERSION) {
            return actionCode == ORCA_583_TYPING_ACTION;
        }

        // Keep the previously verified action for builds predating Messenger 583.
        return (orcaVersion < 0 || orcaVersion < ORCA_583_VERSION)
                && actionCode == LEGACY_TYPING_ACTION;
    }

    static int resolveLegacySeenActionCode(int orcaVersion, int configuredActionCode) {
        // The persisted API code predates versioned cache invalidation. Do not reuse
        // it on 583; that build has a separately verified mark-read dispatch below.
        return orcaVersion == ORCA_583_VERSION ? -1 : configuredActionCode;
    }

    static boolean hasThreadScopedTypingPayload(Object[] args) {
        if (args == null || args.length < 5 || !Boolean.TRUE.equals(args[4])) return false;

        boolean hasThreadIdentifier = false;
        Object threadIdentifier = args[2];
        if (threadIdentifier instanceof String) {
            hasThreadIdentifier = isThreadTokenLike((String) threadIdentifier);
        } else if (threadIdentifier instanceof String[]) {
            // Orca 583 changed the typing API payload from a scalar token to String[].
            for (String candidate : (String[]) threadIdentifier) {
                // The API name and action code already identify this as the thread-identifier
                // array; do not assume its serialized identifier prefix remains unchanged.
                if (candidate != null && !candidate.trim().isEmpty()) {
                    hasThreadIdentifier = true;
                    break;
                }
            }
        }

        Object scope = args[3];
        boolean hasNotificationScope = scope != null
                && ("com.facebook.msys.util.NotificationScope".equals(scope.getClass().getName())
                || scope.getClass().getName().contains("NotificationScope"));
        return hasThreadIdentifier && hasNotificationScope;
    }

    static boolean isMessenger583MarkReadDispatch(int orcaVersion, int actionCode,
                                                   boolean sdkMailbox, String methodName,
                                                   int argumentCount,
                                                   boolean hasMailbox, boolean hasLongIdentifier,
                                                   int stringIdentifierCount,
                                                   boolean hasNotificationScope) {
        return orcaVersion == ORCA_583_VERSION
                && actionCode == ORCA_583_MARK_READ_ACTION
                && sdkMailbox
                && ORCA_583_MARK_READ_METHOD.equals(methodName)
                && argumentCount == 6
                && hasMailbox
                && hasLongIdentifier
                && stringIdentifierCount >= 1
                && hasNotificationScope;
    }

    private static boolean isThreadTokenLike(String value) {
        if (value == null || value.trim().isEmpty()) return false;
        return value.startsWith("T_")
                || value.startsWith("ONE_TO_ONE")
                || value.startsWith("GROUP")
                || value.startsWith("THREAD")
                || value.contains(":");
    }
}
