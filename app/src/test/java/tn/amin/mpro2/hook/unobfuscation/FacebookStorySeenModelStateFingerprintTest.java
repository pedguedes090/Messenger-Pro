package tn.amin.mpro2.hook.unobfuscation;

import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class FacebookStorySeenModelStateFingerprintTest {
    @Test
    public void modelSeenStateEntryRequiresAllEventAnchorsAndStringVoidSignature() throws Exception {
        Method matcher;
        try {
            matcher = FacebookStorySeenFingerprint.class.getDeclaredMethod(
                    "isStorySeenModelStateEntry",
                    boolean.class, boolean.class, boolean.class, boolean.class, boolean.class,
                    String.class, String.class, int.class);
        } catch (NoSuchMethodException missingFingerprint) {
            fail("Missing Story model seen-state fingerprint");
            return;
        }
        matcher.setAccessible(true);

        assertTrue((Boolean) matcher.invoke(null,
                true, true, true, true, true, "V", "java.lang.String", 1));
        assertFalse((Boolean) matcher.invoke(null,
                true, false, true, true, true, "V", "java.lang.String", 1));
        assertFalse((Boolean) matcher.invoke(null,
                true, true, true, true, true, "V", "long", 1));
        assertFalse((Boolean) matcher.invoke(null,
                true, true, true, true, true, "I", "java.lang.String", 1));
        assertFalse((Boolean) matcher.invoke(null,
                true, true, true, true, true, "V", "java.lang.String", 2));
    }
}
