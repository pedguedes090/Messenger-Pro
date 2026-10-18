package tn.amin.mpro2.hook.unobfuscation;

/** Stable, schema-based fingerprints for Facebook Story seen paths. */
final class FacebookStorySeenFingerprint {
    private FacebookStorySeenFingerprint() {
    }

    static boolean isDirectStorySeenMutationClass(boolean surfaceStoryViewer,
                                                    boolean storyIdsList,
                                                    boolean isStoryPeekView,
                                                    boolean bucketStoryCardFilters,
                                                    boolean isMutation) {
        return surfaceStoryViewer && storyIdsList && isStoryPeekView
                && bucketStoryCardFilters && isMutation;
    }

    static boolean isDirectStorySeenEntry(String returnType, int parameterCount,
                                          boolean callsGetRequest) {
        return "V".equals(returnType) && parameterCount == 8 && callsGetRequest;
    }

    static boolean isStorySeenReceiptsEntry(boolean actorId, boolean time,
                                            boolean storyId, boolean unseenCount,
                                            String returnType, int parameterCount) {
        return actorId && time && storyId && unseenCount
                && "V".equals(returnType) && parameterCount == 4;
    }

    static boolean isStorySeenCsrCallback(boolean feedCsrStorageOwner, boolean setStorySeenAnchor,
                                          String returnType, String parameterType, int parameterCount) {
        return feedCsrStorageOwner && setStorySeenAnchor
                && "V".equals(returnType) && "java.lang.String".equals(parameterType)
                && parameterCount == 1;
    }

    static boolean isLocalStorySeenWriter(boolean setStorySeenInternalAnchor, String returnType) {
        return setStorySeenInternalAnchor && "I".equals(returnType);
    }

    static boolean isStorySeenModelStateEntry(boolean updateSeenStateAnchor,
                                               boolean primaryKeyAnchor,
                                               boolean setStoryAsSeenAnchor,
                                               boolean setStorySeenAnchor,
                                               boolean updateSeenStateMethodAnchor,
                                               String returnType,
                                               String parameterType,
                                               int parameterCount) {
        return updateSeenStateAnchor && primaryKeyAnchor && setStoryAsSeenAnchor
                && setStorySeenAnchor && updateSeenStateMethodAnchor
                && "V".equals(returnType)
                && "java.lang.String".equals(parameterType)
                && parameterCount == 1;
    }
}
