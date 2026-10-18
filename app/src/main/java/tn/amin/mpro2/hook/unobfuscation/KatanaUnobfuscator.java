package tn.amin.mpro2.hook.unobfuscation;

import java.io.File;
import java.lang.reflect.Method;

import io.github.neonorbit.dexplore.DexFactory;
import io.github.neonorbit.dexplore.Dexplore;
import io.github.neonorbit.dexplore.filter.ClassFilter;
import io.github.neonorbit.dexplore.filter.DexFilter;
import io.github.neonorbit.dexplore.filter.MethodFilter;
import io.github.neonorbit.dexplore.filter.ReferenceTypes;
import io.github.neonorbit.dexplore.result.MethodData;
import tn.amin.mpro2.debug.Logger;

/**
 * Facebook (com.facebook.katana) unobfuscator.
 * <p>
 * Facebook obfuscates class/method names with Redex and renames them on EVERY build,
 * so hardcoding names (e.g. {@code X.BII.A00}) is fragile. Instead we locate the
 * right method via STABLE string anchors that cannot be obfuscated because they are
 * GraphQL wire names / log strings.
 * <p>
 * Facebook 576 also uses "superpack": the real code lives in secondary DEX files
 * packed inside a zip at {@code /data/data/com.facebook.katana/dex/z-*.zip} (loaded
 * lazily at runtime), NOT in the APK. So we read the DEX from that zip.
 */
public class KatanaUnobfuscator {
    private final ClassLoader mClassLoader;
    private final Dexplore mDexplore;

    public KatanaUnobfuscator(String dexPath, ClassLoader classLoader) {
        mClassLoader = classLoader;
        mDexplore = DexFactory.load(dexPath);
    }

    /**
     * Locate the Facebook superpack DEX zip inside the app's data dir.
     */
    public static String findSuperpackDexPath(String dataDir) {
        try {
            File dexDir = new File(dataDir, "dex");
            File[] files = dexDir.listFiles(
                    (dir, name) -> name.startsWith("z-") && name.endsWith(".zip"));
            if (files != null && files.length > 0) {
                return files[0].getAbsolutePath();
            }
        } catch (Throwable t) {
            Logger.warn("KatanaUnobfuscator: findSuperpackDexPath failed: " + t.getMessage());
        }
        return null;
    }

    /**
     * Find the story "seen" mutation builder entry method.
     * <p>
     * The method (X.BFm.A00 in build 475417168) is the void entry that builds and
     * sends the DirectSeenMutation. Anchors:
     * <ul>
     *   <li>class references the stable string {@code "surface=story_viewer"}.</li>
     *   <li>method calls {@code getRequest} (stable, non-obfuscated name).</li>
     * </ul>
     */
    public Method loadStorySeenBuilderMethod() {
        MethodData methodData = findStorySeenBuilderMethodData(mDexplore);
        if (methodData == null) {
            Logger.error("KatanaUnobfuscator: direct Story seen mutation entry not found");
            return null;
        }
        Method method = methodData.loadMethod(mClassLoader);
        if (method == null || !FacebookStorySeenFingerprint.isDirectStorySeenEntry(
                method.getReturnType() == Void.TYPE ? "V" : method.getReturnType().getName(),
                method.getParameterTypes().length,
                true)) {
            Logger.error("KatanaUnobfuscator: direct Story seen entry failed signature validation: "
                    + methodData.getSignature());
            return null;
        }
        return method;
    }

    static MethodData findStorySeenBuilderMethodData(Dexplore dexplore) {
        ClassFilter classFilter = new ClassFilter.Builder()
                .setReferenceTypes(ReferenceTypes.builder().addString().build())
                .setReferenceFilter(pool -> FacebookStorySeenFingerprint.isDirectStorySeenMutationClass(
                        pool.stringsContain("surface=story_viewer"),
                        pool.stringsContain("story_ids_list"),
                        pool.stringsContain("is_story_peek_view"),
                        pool.stringsContain("bucket_to_story_card_id_filters"),
                        pool.stringsContain("type=mutation")))
                .build();

        MethodFilter methodFilter = new MethodFilter.Builder()
                .setReturnType("void")
                .setParamSize(8)
                .setReferenceTypes(ReferenceTypes.builder().addMethodWithDetails().build())
                .setReferenceFilter(pool -> pool.methodSignaturesContain("getRequest"))
                .build();

        return dexplore.findMethod(DexFilter.MATCH_ALL, classFilter, methodFilter);
    }

    /**
     * Find the LOCAL story-seen store method ({@code setStorySeenInternal}).
     * It writes the "seen" state into the local feed story DB, which is what makes
     * the story ring show "seen" again after a pull-to-refresh. Anchor: the stable
     * log string {@code "setStorySeenInternal"}.
     */
    public Method loadStorySeenLocalMethod() {
        ClassFilter classFilter = new ClassFilter.Builder()
                .setReferenceTypes(ReferenceTypes.builder().addString().build())
                .setReferenceFilter(pool -> pool.stringsContain("setStorySeenInternal"))
                .build();

        MethodFilter methodFilter = new MethodFilter.Builder()
                .setReturnType("int")
                .setReferenceTypes(ReferenceTypes.builder().addString().build())
                .setReferenceFilter(pool -> pool.stringsContain("setStorySeenInternal"))
                .build();

        MethodData methodData = mDexplore.findMethod(DexFilter.MATCH_ALL, classFilter, methodFilter);
        if (methodData == null) {
            Logger.error("KatanaUnobfuscator: story seen local method not found");
            return null;
        }
        Method method = methodData.loadMethod(mClassLoader);
        if (method == null || !FacebookStorySeenFingerprint.isLocalStorySeenWriter(
                true, method.getReturnType() == Integer.TYPE ? "I" : method.getReturnType().getName())) {
            Logger.error("KatanaUnobfuscator: story seen local method failed signature validation: "
                    + methodData.getSignature());
            return null;
        }
        return method;
    }

    /**
     * Find the Story seen-receipts mutation sender. The constructor named
     * StorySeenReceiptsSeenTimeUpdateMutation only describes the GraphQL model;
     * BQU.A0I is the void entry which builds and submits it.
     */
    public Method loadStorySeenReceiptsEntryMethod() {
        MethodData methodData = findStorySeenReceiptsEntryMethodData(mDexplore);
        if (methodData == null) {
            Logger.error("KatanaUnobfuscator: Story seen-receipts sender not found");
            return null;
        }

        Method method = methodData.loadMethod(mClassLoader);
        if (method == null || method.getReturnType() != Void.TYPE || method.getParameterTypes().length != 4) {
            Logger.error("KatanaUnobfuscator: Story seen-receipts sender failed signature validation: "
                    + methodData.getSignature());
            return null;
        }
        return method;
    }

    static MethodData findStorySeenReceiptsEntryMethodData(Dexplore dexplore) {
        ClassFilter classFilter = new ClassFilter.Builder()
                .setReferenceTypes(ReferenceTypes.builder().addString().build())
                .setReferenceFilter(pool -> pool.stringsContain("actor_id")
                        && pool.stringsContain("time")
                        && pool.stringsContain("story_id")
                        && pool.stringsContain("seen_receipts_unseen_count"))
                .build();

        MethodFilter methodFilter = new MethodFilter.Builder()
                .setReturnType("void")
                .setParamSize(4)
                .setReferenceTypes(ReferenceTypes.builder().addString().build())
                .setReferenceFilter(pool -> FacebookStorySeenFingerprint.isStorySeenReceiptsEntry(
                        pool.stringsContain("actor_id"),
                        pool.stringsContain("time"),
                        pool.stringsContain("story_id"),
                        pool.stringsContain("seen_receipts_unseen_count"),
                        "V",
                        4))
                .build();

        return dexplore.findMethod(DexFilter.MATCH_ALL, classFilter, methodFilter);
    }

    /** Find the narrowly scoped FeedCSR story-seen callback (void(String)). */
    public Method loadStorySeenCsrCallbackMethod() {
        ClassFilter classFilter = new ClassFilter.Builder()
                .setReferenceTypes(ReferenceTypes.builder().addString().build())
                .setReferenceFilter(pool -> pool.stringsContain("FeedCSRStorageHandlerImpl"))
                .build();

        MethodFilter methodFilter = new MethodFilter.Builder()
                .setReturnType("void")
                .setParamSize(1)
                .setReferenceTypes(ReferenceTypes.builder().addString().build())
                .setReferenceFilter(pool -> pool.stringsContain("setStorySeen"))
                .build();

        MethodData methodData = mDexplore.findMethod(DexFilter.MATCH_ALL, classFilter, methodFilter);
        if (methodData == null) {
            Logger.error("KatanaUnobfuscator: FeedCSR setStorySeen callback not found");
            return null;
        }

        Method method = methodData.loadMethod(mClassLoader);
        if (method == null || !FacebookStorySeenFingerprint.isStorySeenCsrCallback(
                true,
                true,
                method.getReturnType().getName().equals("void") ? "V" : method.getReturnType().getName(),
                method.getParameterTypes().length == 1
                        ? method.getParameterTypes()[0].getName() : "",
                method.getParameterTypes().length)) {
            Logger.error("KatanaUnobfuscator: FeedCSR setStorySeen callback failed signature validation: "
                    + methodData.getSignature());
            return null;
        }
        return method;
    }

    /** Locate Facebook's model/cache transaction that marks a Story as seen. */
    public Method loadStorySeenModelStateMethod() {
        ClassFilter classFilter = new ClassFilter.Builder()
                .setReferenceTypes(ReferenceTypes.builder().addString().build())
                .setReferenceFilter(pool -> FacebookStorySeenFingerprint.isStorySeenModelStateEntry(
                        pool.stringsContain("_updateSeenState"),
                        pool.stringsContain("primaryKey"),
                        pool.stringsContain("setStoryAsSeen"),
                        pool.stringsContain("setStorySeen"),
                        pool.stringsContain("updateSeenState"),
                        "V", "java.lang.String", 1))
                .build();

        MethodFilter methodFilter = new MethodFilter.Builder()
                .setReturnType("void")
                .setParamSize(1)
                .setReferenceTypes(ReferenceTypes.builder().addString().build())
                .setReferenceFilter(pool -> FacebookStorySeenFingerprint.isStorySeenModelStateEntry(
                        pool.stringsContain("_updateSeenState"),
                        pool.stringsContain("primaryKey"),
                        pool.stringsContain("setStoryAsSeen"),
                        pool.stringsContain("setStorySeen"),
                        pool.stringsContain("updateSeenState"),
                        "V", "java.lang.String", 1))
                .build();

        MethodData methodData = mDexplore.findMethod(DexFilter.MATCH_ALL, classFilter, methodFilter);
        if (methodData == null) {
            Logger.warn("KatanaUnobfuscator: Story model seen-state entry not found");
            return null;
        }

        Method method = methodData.loadMethod(mClassLoader);
        if (method == null || !FacebookStorySeenFingerprint.isStorySeenModelStateEntry(
                true, true, true, true, true,
                method.getReturnType() == Void.TYPE ? "V" : method.getReturnType().getName(),
                method.getParameterTypes().length == 1
                        ? method.getParameterTypes()[0].getName() : "",
                method.getParameterTypes().length)) {
            Logger.warn("KatanaUnobfuscator: Story model seen-state entry failed signature validation: "
                    + methodData.getSignature());
            return null;
        }
        return method;
    }

    /**
     * Generic lookup: find the first method whose class AND method reference the given
     * exact string constant. This only resolves reflectively invocable methods; a
     * constructor anchor will not produce a {@link Method}.
     */
    public Method loadMethodByStringAnchor(String anchor) {
        ClassFilter classFilter = new ClassFilter.Builder()
                .setReferenceTypes(ReferenceTypes.builder().addString().build())
                .setReferenceFilter(pool -> pool.stringsContain(anchor))
                .build();

        MethodFilter methodFilter = new MethodFilter.Builder()
                .setReferenceTypes(ReferenceTypes.builder().addString().build())
                .setReferenceFilter(pool -> pool.stringsContain(anchor))
                .build();

        MethodData methodData = mDexplore.findMethod(DexFilter.MATCH_ALL, classFilter, methodFilter);
        if (methodData == null) {
            Logger.error("KatanaUnobfuscator: no method for anchor: " + anchor);
            return null;
        }
        return methodData.loadMethod(mClassLoader);
    }

    /**
     * Generic class lookup by an exact string anchor.
     */
    public Class<?> loadClassByStringAnchor(String anchor) {
        ClassFilter classFilter = new ClassFilter.Builder()
                .setReferenceTypes(ReferenceTypes.builder().addString().build())
                .setReferenceFilter(pool -> pool.stringsContain(anchor))
                .build();

        io.github.neonorbit.dexplore.result.ClassData classData =
                mDexplore.findClass(DexFilter.MATCH_ALL, classFilter);
        if (classData == null) {
            Logger.error("KatanaUnobfuscator: no class for anchor: " + anchor);
            return null;
        }
        return classData.loadClass(mClassLoader);
    }

    /**
     * Find the video "ad break" fetch method (void entry that requests a mid-roll /
     * commercial ad break). Anchor: the class references the stable string
     * {@code "AdBreakServerAPI"}; the method is the 4-arg void method referencing it.
     */
    public Method loadVideoAdBreakMethod() {
        ClassFilter classFilter = new ClassFilter.Builder()
                .setReferenceTypes(ReferenceTypes.builder().addString().build())
                .setReferenceFilter(pool -> pool.stringsContain("AdBreakServerAPI"))
                .build();

        MethodFilter methodFilter = new MethodFilter.Builder()
                .setReturnType("void")
                .setParamSize(4)
                .setReferenceTypes(ReferenceTypes.builder().addString().build())
                .setReferenceFilter(pool -> pool.stringsContain("AdBreakServerAPI"))
                .build();

        MethodData methodData = mDexplore.findMethod(DexFilter.MATCH_ALL, classFilter, methodFilter);
        if (methodData == null) {
            Logger.error("KatanaUnobfuscator: video ad break method not found");
            return null;
        }
        return methodData.loadMethod(mClassLoader);
    }

    /**
     * Find the async feed ads builder (returns the request/result for async tail-load
     * ads). Anchor: the class references the stable string {@code "native_in_feed_unit"}.
     */
    public Method loadAsyncFeedAdsMethod() {
        ClassFilter classFilter = new ClassFilter.Builder()
                .setReferenceTypes(ReferenceTypes.builder().addString().build())
                .setReferenceFilter(pool -> pool.stringsContain("native_in_feed_unit"))
                .build();

        MethodFilter methodFilter = new MethodFilter.Builder()
                .setReferenceTypes(ReferenceTypes.builder().addString().build())
                .setReferenceFilter(pool -> pool.stringsContain("native_in_feed_unit"))
                .build();

        MethodData methodData = mDexplore.findMethod(DexFilter.MATCH_ALL, classFilter, methodFilter);
        if (methodData == null) {
            Logger.error("KatanaUnobfuscator: async feed ads method not found");
            return null;
        }
        return methodData.loadMethod(mClassLoader);
    }

    /**
     * Find the story ad bucket merge method.
     * Anchor: stable string "IN_DISC_METADATA_KEY" used in FbStoryAdInDiscStoreImpl.
     */
    public Method loadStoryAdMergeMethod() {
        ClassFilter classFilter = new ClassFilter.Builder()
                .setReferenceTypes(ReferenceTypes.builder().addString().build())
                .setReferenceFilter(pool -> pool.stringsContain("IN_DISC_METADATA_KEY"))
                .build();

        MethodFilter methodFilter = new MethodFilter.Builder()
                .setReferenceTypes(ReferenceTypes.builder().addString().build())
                .setReferenceFilter(pool -> pool.stringsContain("AD_BUCKETS_KEY"))
                .build();

        MethodData methodData = mDexplore.findMethod(DexFilter.MATCH_ALL, classFilter, methodFilter);
        if (methodData == null) {
            Logger.error("KatanaUnobfuscator: story ad merge method not found");
            return null;
        }
        return methodData.loadMethod(mClassLoader);
    }

    /**
     * Find the story ad fetch method.
     * Anchor: stable string "AdsPaginatingNetworkAdBucketFetcher" (class name survives in some contexts).
     */
    public Method loadStoryAdFetchMethod() {
        ClassFilter classFilter = new ClassFilter.Builder()
                .setReferenceTypes(ReferenceTypes.builder().addString().build())
                .setReferenceFilter(pool -> pool.stringsContain("AdsPaginatingNetworkAdBucketFetcher"))
                .build();

        MethodFilter methodFilter = new MethodFilter.Builder()
                .setReturnType("void")
                .setParamSize(1)
                .setReferenceTypes(ReferenceTypes.builder().addString().build())
                .setReferenceFilter(pool -> pool.stringsContain("fetchMoreAds"))
                .build();

        MethodData methodData = mDexplore.findMethod(DexFilter.MATCH_ALL, classFilter, methodFilter);
        if (methodData == null) {
            Logger.error("KatanaUnobfuscator: story ad fetch method not found");
            return null;
        }
        return methodData.loadMethod(mClassLoader);
    }

    /**
     * Find the AudienceNetworkActivity class for blocking game ads.
     */
    public Class<?> loadAudienceNetworkActivityClass() {
        ClassFilter classFilter = new ClassFilter.Builder()
                .setReferenceTypes(ReferenceTypes.builder().addString().build())
                .setReferenceFilter(pool -> pool.stringsContain("com.facebook.ads.AudienceNetworkActivity"))
                .build();

        io.github.neonorbit.dexplore.result.ClassData classData =
                mDexplore.findClass(DexFilter.MATCH_ALL, classFilter);
        if (classData == null) {
            Logger.error("KatanaUnobfuscator: AudienceNetworkActivity class not found");
            return null;
        }
        return classData.loadClass(mClassLoader);
    }

    /**
     * Find the Quicksilver postMessage method for game ad interception.
     * Anchor: stable string "quicksilver" in the message type.
     */
    public Method loadQuicksilverPostMessageMethod() {
        ClassFilter classFilter = new ClassFilter.Builder()
                .setReferenceTypes(ReferenceTypes.builder().addString().build())
                .setReferenceFilter(pool -> pool.stringsContain("quicksilver"))
                .build();

        MethodFilter methodFilter = new MethodFilter.Builder()
                .setReferenceTypes(ReferenceTypes.builder().addMethodWithDetails().build())
                .setReferenceFilter(pool -> pool.stringsContain("postMessage"))
                .build();

        MethodData methodData = mDexplore.findMethod(DexFilter.MATCH_ALL, classFilter, methodFilter);
        if (methodData == null) {
            Logger.error("KatanaUnobfuscator: quicksilver postMessage method not found");
            return null;
        }
        return methodData.loadMethod(mClassLoader);
    }
}
