package tn.amin.mpro2.hook.unobfuscation;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FacebookStorySeenFingerprintTest {
    @Test
    public void distinguishesStorySeenMutationFromStoryReplyMutation() {
        assertTrue(FacebookStorySeenFingerprint.isDirectStorySeenMutationClass(
                true, true, true, true, true));
        assertFalse(FacebookStorySeenFingerprint.isDirectStorySeenMutationClass(
                true, false, false, false, true));
    }

    @Test
    public void requiresVoidEntryThatCallsTheStorySeenRequestBuilder() {
        assertTrue(FacebookStorySeenFingerprint.isDirectStorySeenEntry("V", 8, true));
        assertFalse(FacebookStorySeenFingerprint.isDirectStorySeenEntry("V", 8, false));
        assertFalse(FacebookStorySeenFingerprint.isDirectStorySeenEntry("L", 8, true));
    }

    @Test
    public void recognizesTheStorySeenReceiptsUpdateEntryByItsFieldSet() {
        assertTrue(FacebookStorySeenFingerprint.isStorySeenReceiptsEntry(
                true, true, true, true, "V", 4));
        assertFalse(FacebookStorySeenFingerprint.isStorySeenReceiptsEntry(
                true, true, true, false, "V", 4));
        assertFalse(FacebookStorySeenFingerprint.isStorySeenReceiptsEntry(
                true, true, true, true, "L", 4));
        assertFalse(FacebookStorySeenFingerprint.isStorySeenReceiptsEntry(
                true, true, true, true, "V", 5));
    }

    @Test
    public void recognizesStorySeenCsrCallbackOnlyWithItsStableOwnerAndSignature() {
        assertTrue(FacebookStorySeenFingerprint.isStorySeenCsrCallback(
                true, true, "V", "java.lang.String", 1));
        assertFalse(FacebookStorySeenFingerprint.isStorySeenCsrCallback(
                false, true, "V", "java.lang.String", 1));
        assertFalse(FacebookStorySeenFingerprint.isStorySeenCsrCallback(
                true, true, "I", "java.lang.String", 1));
        assertFalse(FacebookStorySeenFingerprint.isStorySeenCsrCallback(
                true, true, "V", "int", 1));
        assertFalse(FacebookStorySeenFingerprint.isStorySeenCsrCallback(
                true, true, "V", "java.lang.String", 2));
    }

    @Test
    public void recognizesLocalStorySeenWriterOnlyWithIntResultAndAnchor() {
        assertTrue(FacebookStorySeenFingerprint.isLocalStorySeenWriter(true, "I"));
        assertFalse(FacebookStorySeenFingerprint.isLocalStorySeenWriter(true, "V"));
        assertFalse(FacebookStorySeenFingerprint.isLocalStorySeenWriter(false, "I"));
    }
}
