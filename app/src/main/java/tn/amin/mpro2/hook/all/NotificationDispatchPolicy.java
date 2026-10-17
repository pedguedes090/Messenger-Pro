package tn.amin.mpro2.hook.all;

/** Resolves only verified legacy defaults; newer builds require an explicit action ID. */
final class NotificationDispatchPolicy {
    private static final int STALE_LEGACY_DEFAULT = 26;

    private NotificationDispatchPolicy() {
    }

    static int resolve(int configuredAction, int orcaVersion,
                       int lastVerifiedLegacyVersion, int verifiedLegacyAction) {
        if (orcaVersion <= lastVerifiedLegacyVersion
                && (configuredAction < 0 || configuredAction == STALE_LEGACY_DEFAULT)) {
            return verifiedLegacyAction;
        }
        return configuredAction;
    }
}
