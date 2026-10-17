import org.jf.dexlib2.Opcode;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.DexFileFactory;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.DexFile;
import org.jf.dexlib2.iface.Method;
import org.jf.dexlib2.iface.MethodImplementation;
import org.jf.dexlib2.iface.Field;
import org.jf.dexlib2.iface.instruction.Instruction;
import org.jf.dexlib2.iface.instruction.OffsetInstruction;
import org.jf.dexlib2.iface.instruction.ReferenceInstruction;
import org.jf.dexlib2.iface.ExceptionHandler;
import org.jf.dexlib2.iface.TryBlock;
import org.jf.dexlib2.iface.reference.FieldReference;
import org.jf.dexlib2.iface.reference.MethodReference;
import org.jf.dexlib2.iface.reference.Reference;
import org.jf.dexlib2.iface.reference.StringReference;
import org.jf.dexlib2.iface.value.StringEncodedValue;
import org.jf.dexlib2.immutable.ImmutableClassDef;
import org.jf.dexlib2.immutable.ImmutableExceptionHandler;
import org.jf.dexlib2.immutable.ImmutableMethod;
import org.jf.dexlib2.immutable.ImmutableMethodImplementation;
import org.jf.dexlib2.immutable.ImmutableTryBlock;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction10t;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction10x;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction11n;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction11x;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction21c;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction21t;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction22c;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction31c;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction22t;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction35c;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction3rc;
import org.jf.dexlib2.immutable.reference.ImmutableFieldReference;
import org.jf.dexlib2.immutable.reference.ImmutableMethodReference;
import org.jf.dexlib2.immutable.reference.ImmutableStringReference;
import org.jf.dexlib2.immutable.reference.ImmutableTypeReference;
import org.jf.dexlib2.writer.io.FileDataStore;
import org.jf.dexlib2.writer.pool.DexPool;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;

/**
 * Static Facebook ad-route patcher.
 *
 * The default profile retains the verified 576 map. The facebook-582 profile resolves
 * targets from the current GraphQL model and stable wire/render anchors, and fails open
 * when a required target is missing or ambiguous.
 */
public class PatchAds {
    static final int PREFIX_UNITS = 23;
    static final String CATEGORY = "Lcom/crossapp/graphql/facebook/enums/GraphQLFeedStoryCategory;";
    static final String IMMUTABLE_LIST = "Lcom/google/common/collect/ImmutableList;";
    static final String FEED_EDGE = "Lcom/facebook/graphql/model/GraphQLFeedUnitEdge;";
    static final String STORY = "Lcom/facebook/graphql/model/GraphQLStory;";
    static final String STORY_ATTACHMENT = "Lcom/facebook/graphql/model/GraphQLStoryAttachment;";
    static final String MULTI_ADS = "Lcom/facebook/graphql/model/GraphQLFBMultiAdsFeedUnit;";
    static final String STORY_COMMERCE_PLUGIN =
            "Lcom/facebook/feed/rows/sections/content/FeedStoryGroupCommerceAttachmentPlugin;";
    static final String FEED_CATEGORY =
            "Lcom/crossapp/graphql/facebook/enums/GraphQLFeedStoryCategory;";

    static final MethodReference B9B = new ImmutableMethodReference(
            "Lcom/facebook/graphql/model/GraphQLFeedUnitEdge;", "B9B",
            ImmutableList.<String>of(), CATEGORY);
    static final MethodReference EMPTY_LIST = new ImmutableMethodReference(
            IMMUTABLE_LIST, "of", ImmutableList.<String>of(), IMMUTABLE_LIST);

    static final String[] DROP_FIELDS = {"A0K", "A0I", "A0C", "A0D"};

    static FieldReference field(String name) {
        return new ImmutableFieldReference(CATEGORY, name, CATEGORY);
    }

    static List<Instruction> feedFilterPrefix() {
        List<Instruction> out = new ArrayList<Instruction>();
        // In the verified method (regs=30, ins=4), p1=edge is v28.
        out.add(new ImmutableInstruction3rc(Opcode.INVOKE_VIRTUAL_RANGE, 28, 1, B9B));
        out.add(new ImmutableInstruction11x(Opcode.MOVE_RESULT_OBJECT, 4));
        int[] offsets = {15, 11, 7, 3};
        for (int i = 0; i < DROP_FIELDS.length; i++) {
            out.add(new ImmutableInstruction21c(Opcode.SGET_OBJECT, 5, field(DROP_FIELDS[i])));
            out.add(new ImmutableInstruction22t(Opcode.IF_EQ, 4, 5, offsets[i]));
        }
        out.add(new ImmutableInstruction10t(Opcode.GOTO, 3));
        out.add(new ImmutableInstruction11n(Opcode.CONST_4, 0, 0));
        out.add(new ImmutableInstruction11x(Opcode.RETURN, 0));
        return out;
    }

    static Method replaceWithVoid(Method m) {
        List<Instruction> insns = ImmutableList.<Instruction>of(
                new ImmutableInstruction10x(Opcode.RETURN_VOID));
        return copyMethod(m, new ImmutableMethodImplementation(
                registerCount(m), insns, ImmutableList.of(), ImmutableList.of()));
    }

    static Method replaceWithFalse(Method m) {
        List<Instruction> insns = ImmutableList.<Instruction>of(
                new ImmutableInstruction11n(Opcode.CONST_4, 0, 0),
                new ImmutableInstruction11x(Opcode.RETURN, 0));
        return copyMethod(m, new ImmutableMethodImplementation(
                registerCount(m), insns, ImmutableList.of(), ImmutableList.of()));
    }

    static Method replaceWithNull(Method m) {
        List<Instruction> insns = ImmutableList.<Instruction>of(
                new ImmutableInstruction11n(Opcode.CONST_4, 0, 0),
                new ImmutableInstruction11x(Opcode.RETURN_OBJECT, 0));
        return copyMethod(m, new ImmutableMethodImplementation(
                registerCount(m), insns, ImmutableList.of(), ImmutableList.of()));
    }

    static Method replaceWithEmptyList(Method m) {
        List<Instruction> insns = ImmutableList.<Instruction>of(
                new ImmutableInstruction35c(Opcode.INVOKE_STATIC, 0, 0, 0, 0, 0, 0, EMPTY_LIST),
                new ImmutableInstruction11x(Opcode.MOVE_RESULT_OBJECT, 0),
                new ImmutableInstruction11x(Opcode.RETURN_OBJECT, 0));
        return copyMethod(m, new ImmutableMethodImplementation(
                registerCount(m), insns, ImmutableList.of(), ImmutableList.of()));
    }

    static int registerCount(Method m) {
        int words = ((m.getAccessFlags() & 0x8) == 0) ? 1 : 0;
        for (CharSequence p : m.getParameterTypes()) {
            words += (p.equals("J") || p.equals("D")) ? 2 : 1;
        }
        MethodImplementation old = m.getImplementation();
        return Math.max(words, old == null ? words : old.getRegisterCount());
    }

    static Method copyMethod(Method m, MethodImplementation impl) {
        return new ImmutableMethod(m.getDefiningClass(), m.getName(), m.getParameters(),
                m.getReturnType(), m.getAccessFlags(), m.getAnnotations(),
                m.getHiddenApiRestrictions(), impl);
    }

    private static final class Branch {
        final int instructionIndex;
        final Opcode opcode;
        final int register;
        final String label;

        Branch(int instructionIndex, Opcode opcode, int register, String label) {
            this.instructionIndex = instructionIndex;
            this.opcode = opcode;
            this.register = register;
            this.label = label;
        }
    }

    private static final class PrefixBuilder {
        final List<Instruction> instructions = new ArrayList<Instruction>();
        final List<Integer> lengths = new ArrayList<Integer>();
        final Map<String, Integer> labelOffsets = new HashMap<String, Integer>();
        final List<Branch> branches = new ArrayList<Branch>();
        int codeUnits;

        void add(Instruction instruction) {
            instructions.add(instruction);
            lengths.add(instruction.getCodeUnits());
            codeUnits += instruction.getCodeUnits();
        }

        void mark(String label) {
            labelOffsets.put(label, codeUnits);
        }

        void branch(Opcode opcode, int register, String label) {
            branches.add(new Branch(instructions.size(), opcode, register, label));
            instructions.add(null);
            int length = opcode == Opcode.GOTO ? 1 : 2;
            lengths.add(length);
            codeUnits += length;
        }

        List<Instruction> build() {
            for (Branch branch : branches) {
                Integer target = labelOffsets.get(branch.label);
                if (target == null) throw new IllegalStateException("Missing prefix label " + branch.label);
                int offset = target - offsetOf(branch.instructionIndex);
                Instruction instruction = branch.opcode == Opcode.GOTO
                        ? new ImmutableInstruction10t(Opcode.GOTO, offset)
                        : new ImmutableInstruction21t(branch.opcode, branch.register, offset);
                instructions.set(branch.instructionIndex, instruction);
                lengths.set(branch.instructionIndex, instruction.getCodeUnits());
            }
            return instructions;
        }

        private int offsetOf(int instructionIndex) {
            int offset = 0;
            for (int i = 0; i < instructionIndex; i++) offset += lengths.get(i);
            return offset;
        }
    }

    private static String paramType(Method method, int index) {
        return method.getParameters().get(index).getType();
    }

    private static boolean isStatic(Method method) {
        return (method.getAccessFlags() & 0x8) != 0;
    }

    private static int parameterRegister(Method method, int parameterIndex) {
        int incomingWords = isStatic(method) ? 0 : 1;
        for (CharSequence parameter : method.getParameterTypes()) {
            incomingWords += parameter.equals("J") || parameter.equals("D") ? 2 : 1;
        }
        int register = method.getImplementation().getRegisterCount() - incomingWords;
        if (!isStatic(method)) register++;
        for (int i = 0; i < parameterIndex; i++) {
            String type = paramType(method, i);
            register += type.equals("J") || type.equals("D") ? 2 : 1;
        }
        return register;
    }

    private static ClassDef findClass(Collection<? extends ClassDef> classes, String type) {
        for (ClassDef cd : classes) if (cd.getType().equals(type)) return cd;
        return null;
    }

    private static boolean hasString(Method method, String value) {
        MethodImplementation impl = method.getImplementation();
        if (impl == null) return false;
        for (Instruction instruction : impl.getInstructions()) {
            if (instruction instanceof ReferenceInstruction) {
                Reference reference = ((ReferenceInstruction) instruction).getReference();
                if (reference instanceof StringReference
                        && ((StringReference) reference).getString().equals(value)) return true;
            }
        }
        return false;
    }

    private static boolean invokes(Method method, MethodReference target) {
        MethodImplementation impl = method.getImplementation();
        if (impl == null) return false;
        for (Instruction instruction : impl.getInstructions()) {
            if (!(instruction instanceof ReferenceInstruction)) continue;
            Reference reference = ((ReferenceInstruction) instruction).getReference();
            if (!(reference instanceof MethodReference)) continue;
            MethodReference called = (MethodReference) reference;
            if (called.getDefiningClass().equals(target.getDefiningClass())
                    && called.getName().equals(target.getName())
                    && called.getReturnType().equals(target.getReturnType())
                    && called.getParameterTypes().equals(target.getParameterTypes())) return true;
        }
        return false;
    }

    private static boolean sameMethod(Method left, Method right) {
        return left != null && right != null
                && left.getDefiningClass().equals(right.getDefiningClass())
                && left.getName().equals(right.getName())
                && left.getReturnType().equals(right.getReturnType())
                && left.getParameterTypes().equals(right.getParameterTypes());
    }

    private static MethodReference methodReference(Method method) {
        return new ImmutableMethodReference(method.getDefiningClass(), method.getName(),
                method.getParameterTypes(), method.getReturnType());
    }

    private static List<File> dexFiles(File directory, File currentDex) {
        File[] files = directory != null && directory.isDirectory()
                ? directory.listFiles((dir, name) -> name.matches("classes(\\d+)?\\.dex")) : null;
        List<File> result = new ArrayList<File>();
        if (files != null) {
            Arrays.sort(files, Comparator.comparing(File::getName));
            result.addAll(Arrays.asList(files));
        }
        if (currentDex != null && !result.contains(currentDex)) result.add(currentDex);
        return result;
    }

    private static Method findMethodDefinition(File root, File currentDex, MethodReference reference) throws Exception {
        Method found = null;
        for (File file : dexFiles(root, currentDex)) {
            DexFile dex = DexFileFactory.loadDexFile(file, Opcodes.getDefault());
            for (ClassDef cd : dex.getClasses()) {
                if (!cd.getType().equals(reference.getDefiningClass())) continue;
                for (Method method : cd.getMethods()) {
                    if (method.getName().equals(reference.getName())
                            && method.getReturnType().equals(reference.getReturnType())
                            && method.getParameterTypes().equals(reference.getParameterTypes())) {
                        if (found != null) return null;
                        found = method;
                    }
                }
            }
        }
        return found;
    }

    /**
     * Resolve the commerce attachment predicate from the render plugin's call site,
     * then require its implementation to contain the product-item type check.
     */
    private static MethodReference findProductAttachmentHelper(
            File root, File currentDex, Collection<? extends ClassDef> currentClasses) throws Exception {
        List<MethodReference> helperRefs = new ArrayList<MethodReference>();
        ClassDef plugin = findClass(currentClasses, STORY_COMMERCE_PLUGIN);
        if (plugin == null) {
            for (File file : dexFiles(root, currentDex)) {
                DexFile dex = DexFileFactory.loadDexFile(file, Opcodes.getDefault());
                plugin = findClass(dex.getClasses(), STORY_COMMERCE_PLUGIN);
                if (plugin != null) break;
            }
        }
        if (plugin == null) return null;
        for (Method method : plugin.getMethods()) {
            MethodImplementation impl = method.getImplementation();
            if (impl == null) continue;
            for (Instruction instruction : impl.getInstructions()) {
                if (!(instruction instanceof ReferenceInstruction)
                        || !instruction.getOpcode().name().startsWith("INVOKE_STATIC")) continue;
                Reference reference = ((ReferenceInstruction) instruction).getReference();
                if (!(reference instanceof MethodReference)) continue;
                MethodReference ref = (MethodReference) reference;
                if (ref.getParameterTypes().size() == 1
                        && ref.getParameterTypes().get(0).equals(STORY)
                        && ref.getReturnType().equals(STORY_ATTACHMENT)
                        && !helperRefs.contains(ref)) helperRefs.add(ref);
            }
        }
        if (helperRefs.size() != 1) return null;
        Method helper = findMethodDefinition(root, currentDex, helperRefs.get(0));
        if (helper == null || helper.getImplementation() == null || !isStatic(helper)) return null;

        List<MethodReference> predicateRefs = new ArrayList<MethodReference>();
        for (Instruction instruction : helper.getImplementation().getInstructions()) {
            if (!(instruction instanceof ReferenceInstruction)
                    || !instruction.getOpcode().name().startsWith("INVOKE_STATIC")) continue;
            Reference reference = ((ReferenceInstruction) instruction).getReference();
            if (!(reference instanceof MethodReference)) continue;
            MethodReference ref = (MethodReference) reference;
            if (ref.getParameterTypes().size() == 1
                    && ref.getParameterTypes().get(0).equals(STORY_ATTACHMENT)
                    && ref.getReturnType().equals("Z") && !predicateRefs.contains(ref)) {
                predicateRefs.add(ref);
            }
        }
        if (predicateRefs.size() != 1) return null;
        Method predicate = findMethodDefinition(root, currentDex, predicateRefs.get(0));
        if (predicate == null || predicate.getImplementation() == null
                || !hasString(predicate, "GroupCommerceProductItem")) return null;
        return helperRefs.get(0);
    }

    private static Method findUniqueFeedUnitGetter(ClassDef edge, ClassDef story, ClassDef multiAds) {
        if (edge == null || story == null || multiAds == null) return null;
        List<Method> candidates = new ArrayList<Method>();
        for (Method method : edge.getMethods()) {
            if (method.getImplementation() == null || !method.getParameterTypes().isEmpty()) continue;
            String returnType = method.getReturnType();
            if (!story.getInterfaces().contains(returnType) || !multiAds.getInterfaces().contains(returnType)) continue;
            boolean cachedVirtualModel = false;
            for (Instruction instruction : method.getImplementation().getInstructions()) {
                if (!(instruction instanceof ReferenceInstruction)) continue;
                Reference reference = ((ReferenceInstruction) instruction).getReference();
                if (reference instanceof MethodReference) {
                    MethodReference called = (MethodReference) reference;
                    if (called.getName().equals("getCachedVirtualModel")) cachedVirtualModel = true;
                }
            }
            if (cachedVirtualModel) candidates.add(method);
        }
        return candidates.size() == 1 ? candidates.get(0) : null;
    }

    private static Method findUniqueCategoryGetter(ClassDef edge) {
        if (edge == null) return null;
        List<Method> candidates = new ArrayList<Method>();
        for (Method method : edge.getMethods()) {
            if (method.getImplementation() != null && method.getParameterTypes().isEmpty()
                    && method.getReturnType().equals(FEED_CATEGORY)) candidates.add(method);
        }
        return candidates.size() == 1 ? candidates.get(0) : null;
    }

    private static Method findUniqueEdgeInsertion(Collection<? extends ClassDef> classes) {
        List<Method> candidates = new ArrayList<Method>();
        for (ClassDef cd : classes) for (Method method : cd.getMethods()) {
            if (method.getImplementation() == null || !method.getName().equals("addNewEdgeToCollection")
                    || !method.getReturnType().equals("Z") || method.getParameterTypes().size() != 3) continue;
            if (paramType(method, 0).startsWith("Lcom/google/common/collect/ImmutableList$Builder;")
                    && paramType(method, 1).equals(FEED_EDGE)) candidates.add(method);
        }
        return candidates.size() == 1 ? candidates.get(0) : null;
    }

    private static void compareString(PrefixBuilder b, int nameReg, int tempReg,
                                      String value, String dropLabel) {
        b.add(new ImmutableInstruction31c(Opcode.CONST_STRING_JUMBO, tempReg,
                new ImmutableStringReference(value)));
        b.add(new ImmutableInstruction35c(Opcode.INVOKE_VIRTUAL, 2, tempReg, nameReg,
                0, 0, 0, new ImmutableMethodReference("Ljava/lang/String;", "equals",
                ImmutableList.<String>of("Ljava/lang/Object;"), "Z")));
        b.add(new ImmutableInstruction11x(Opcode.MOVE_RESULT, tempReg));
        b.branch(Opcode.IF_NEZ, tempReg, dropLabel);
    }

    private static List<Instruction> modernFeedFilterPrefix(
            Method method, Method feedUnitGetter, Method categoryGetter,
            MethodReference commerceHelper) {
        PrefixBuilder b = new PrefixBuilder();
        int edgeReg = parameterRegister(method, 1);
        // Locals v0-v2 are unused at entry. Null/unknown model paths go to the
        // original method, so an incomplete schema remains fail-open.
        b.branch(Opcode.IF_EQZ, edgeReg, "keep");
        b.add(new ImmutableInstruction3rc(Opcode.INVOKE_VIRTUAL_RANGE, edgeReg, 1,
                methodReference(feedUnitGetter)));
        b.add(new ImmutableInstruction11x(Opcode.MOVE_RESULT_OBJECT, 0));
        b.branch(Opcode.IF_EQZ, 0, "keep");
        b.add(new ImmutableInstruction22c(Opcode.INSTANCE_OF, 1, 0,
                new ImmutableTypeReference(MULTI_ADS)));
        b.branch(Opcode.IF_NEZ, 1, "drop");

        b.add(new ImmutableInstruction3rc(Opcode.INVOKE_VIRTUAL_RANGE, edgeReg, 1,
                methodReference(categoryGetter)));
        b.add(new ImmutableInstruction11x(Opcode.MOVE_RESULT_OBJECT, 1));
        b.branch(Opcode.IF_EQZ, 1, "keep");
        b.add(new ImmutableInstruction35c(Opcode.INVOKE_VIRTUAL, 1, 1, 0, 0, 0, 0,
                new ImmutableMethodReference("Ljava/lang/Enum;", "name",
                        ImmutableList.<String>of(), "Ljava/lang/String;")));
        b.add(new ImmutableInstruction11x(Opcode.MOVE_RESULT_OBJECT, 1));

        compareString(b, 1, 2, "SPONSORED", "drop");
        compareString(b, 1, 2, "PROMOTION", "drop");
        compareString(b, 1, 2, "SHOWCASE", "drop");
        compareString(b, 1, 2, "FIXED_POSITION", "drop");
        compareString(b, 1, 2, "HIGH_VALUE_PROMOTION", "drop");

        if (commerceHelper != null) {
            compareString(b, 1, 2, "FB_SHORTS", "check_commerce");
            b.branch(Opcode.GOTO, 0, "keep");
            b.mark("check_commerce");
            b.add(new ImmutableInstruction22c(Opcode.INSTANCE_OF, 2, 0,
                    new ImmutableTypeReference(STORY)));
            b.branch(Opcode.IF_EQZ, 2, "keep");
            b.add(new ImmutableInstruction21c(Opcode.CHECK_CAST, 0,
                    new ImmutableTypeReference(STORY)));
            b.add(new ImmutableInstruction3rc(Opcode.INVOKE_STATIC_RANGE, 0, 1, commerceHelper));
            b.add(new ImmutableInstruction11x(Opcode.MOVE_RESULT_OBJECT, 2));
            b.branch(Opcode.IF_NEZ, 2, "drop");
        }
        b.branch(Opcode.GOTO, 0, "keep");
        b.mark("drop");
        b.add(new ImmutableInstruction11n(Opcode.CONST_4, 0, 0));
        b.add(new ImmutableInstruction11x(Opcode.RETURN, 0));
        b.mark("keep");

        return b.build();
    }

    private static Method prefixModernFeedFilter(Method method, List<Instruction> prefix) {
        MethodImplementation impl = method.getImplementation();
        List<Instruction> insns = new ArrayList<Instruction>(prefix);
        for (Instruction instruction : impl.getInstructions()) insns.add(instruction);
        int prefixUnits = 0;
        for (Instruction instruction : prefix) prefixUnits += instruction.getCodeUnits();
        List<ImmutableTryBlock> tries = new ArrayList<ImmutableTryBlock>();
        for (TryBlock<? extends ExceptionHandler> tb : impl.getTryBlocks()) {
            List<ImmutableExceptionHandler> handlers = new ArrayList<ImmutableExceptionHandler>();
            for (ExceptionHandler handler : tb.getExceptionHandlers()) {
                handlers.add(new ImmutableExceptionHandler(handler.getExceptionType(),
                        handler.getHandlerCodeAddress() + prefixUnits));
            }
            tries.add(new ImmutableTryBlock(tb.getStartCodeAddress() + prefixUnits,
                    tb.getCodeUnitCount(), handlers));
        }
        Method patched = copyMethod(method, new ImmutableMethodImplementation(
                impl.getRegisterCount(), insns, tries, ImmutableList.of()));
        validateBranchTargets(patched);
        return patched;
    }

    private static void validateBranchTargets(Method method) {
        Set<Integer> instructionStarts = new HashSet<Integer>();
        List<Instruction> instructions = new ArrayList<Instruction>();
        int offset = 0;
        for (Instruction instruction : method.getImplementation().getInstructions()) {
            instructionStarts.add(offset);
            instructions.add(instruction);
            offset += instruction.getCodeUnits();
        }
        offset = 0;
        for (Instruction instruction : instructions) {
            if (instruction instanceof OffsetInstruction) {
                int target = offset + ((OffsetInstruction) instruction).getCodeOffset();
                if (!instructionStarts.contains(target)) {
                    throw new IllegalStateException("Invalid branch target in "
                            + method.getDefiningClass() + "->" + method.getName());
                }
            }
            offset += instruction.getCodeUnits();
        }
    }

    private static Method findUniqueAnchorMethod(Collection<? extends ClassDef> classes, String anchor,
                                                String returnType, int parameterCount,
                                                boolean requireEndingAnchor) {
        List<Method> candidates = new ArrayList<Method>();
        for (ClassDef cd : classes) for (Method method : cd.getMethods()) {
            if (method.getImplementation() == null || !hasString(method, anchor)
                    || !method.getReturnType().equals(returnType)
                    || method.getParameterTypes().size() != parameterCount) continue;
            if (requireEndingAnchor && !hasString(method, "get_top_valid_ad_end")) continue;
            candidates.add(method);
        }
        return candidates.size() == 1 ? candidates.get(0) : null;
    }

    private static boolean hasType(Collection<? extends ClassDef> classes, String type) {
        return findClass(classes, type) != null;
    }

    private static int[] patchModernDex(DexFile dex, File currentDex, File root, File output) throws Exception {
        Collection<? extends ClassDef> classes = dex.getClasses();
        Method edgeInsertion = findUniqueEdgeInsertion(classes);
        ClassDef edge = findClass(classes, FEED_EDGE);
        ClassDef story = findClass(classes, STORY);
        ClassDef multiAds = findClass(classes, MULTI_ADS);
        Method feedUnitGetter = findUniqueFeedUnitGetter(edge, story, multiAds);
        Method categoryGetter = findUniqueCategoryGetter(edge);
        MethodReference commerceHelper = findProductAttachmentHelper(root, currentDex, classes);

        Method shortsPool = findUniqueAnchorMethod(classes, "fb_shorts_native_in_feed_unit",
                "LX/5j9;", 4, false);
        Method sponsoredSelector = findUniqueAnchorMethod(classes, "get_top_valid_ad",
                FEED_EDGE, 3, true);
        Method videoAdRequest = findUniqueAnchorMethod(classes, "VideoHomeDataFetcher.fetchAds",
                "V", 1, false);
        Method storyAdFetch = findUniqueAnchorMethod(classes, "AdsPaginatingNetworkAdBucketFetcher",
                "V", 4, false);

        boolean canFilterEdge = edgeInsertion != null && feedUnitGetter != null && categoryGetter != null;
        int[] counts = new int[] {0, 0, 0, 0, 0};
        System.out.println("facebook-582 targets: edgeFilter=" + canFilterEdge
                + " affiliateProductHelper=" + (commerceHelper != null)
                + " shortsPool=" + (shortsPool != null)
                + " sponsoredSelector=" + (sponsoredSelector != null)
                + " videoAdRequest=" + (videoAdRequest != null)
                + " storyAdFetch=" + (storyAdFetch != null));

        DexPool pool = new DexPool(Opcodes.getDefault());
        for (ClassDef cd : classes) {
            List<Method> direct = new ArrayList<Method>();
            List<Method> virtual = new ArrayList<Method>();
            for (Method method : cd.getDirectMethods()) {
                Method replacement = patchModernMethod(method, edgeInsertion, shortsPool,
                        sponsoredSelector, videoAdRequest, storyAdFetch, feedUnitGetter, categoryGetter,
                        commerceHelper);
                if (replacement != method) {
                    if (sameMethod(method, edgeInsertion)) counts[0]++;
                    else if (sameMethod(method, shortsPool)) counts[1]++;
                    else if (sameMethod(method, sponsoredSelector)) counts[2]++;
                    else if (sameMethod(method, videoAdRequest)) counts[3]++;
                    else if (sameMethod(method, storyAdFetch)) counts[4]++;
                }
                direct.add(replacement);
            }
            for (Method method : cd.getVirtualMethods()) {
                Method replacement = patchModernMethod(method, edgeInsertion, shortsPool,
                        sponsoredSelector, videoAdRequest, storyAdFetch, feedUnitGetter, categoryGetter,
                        commerceHelper);
                if (replacement != method) {
                    if (sameMethod(method, edgeInsertion)) counts[0]++;
                    else if (sameMethod(method, shortsPool)) counts[1]++;
                    else if (sameMethod(method, sponsoredSelector)) counts[2]++;
                    else if (sameMethod(method, videoAdRequest)) counts[3]++;
                    else if (sameMethod(method, storyAdFetch)) counts[4]++;
                }
                virtual.add(replacement);
            }
            pool.internClass(new ImmutableClassDef(cd.getType(), cd.getAccessFlags(),
                    cd.getSuperclass(), cd.getInterfaces(), cd.getSourceFile(),
                    cd.getAnnotations(), cd.getStaticFields(), cd.getInstanceFields(), direct, virtual));
        }
        pool.writeTo(new FileDataStore(output));
        System.out.println("facebook-582 patched methods: feedEdge=" + counts[0]
                + " shortsPool=" + counts[1] + " sponsoredSelector=" + counts[2]
                + " videoAdRequest=" + counts[3] + " storyAdFetch=" + counts[4]);
        return counts;
    }

    private static Method patchModernMethod(Method method, Method edgeInsertion,
            Method shortsPool, Method sponsoredSelector, Method videoAdRequest, Method storyAdFetch,
            Method feedUnitGetter, Method categoryGetter, MethodReference commerceHelper) {
        if (edgeInsertion != null && sameMethod(method, edgeInsertion)) {
            List<Instruction> prefix = modernFeedFilterPrefix(method, feedUnitGetter,
                    categoryGetter, commerceHelper);
            return prefixModernFeedFilter(method, prefix);
        }
        if (shortsPool != null && sameMethod(method, shortsPool)) return replaceWithNull(method);
        if (sponsoredSelector != null && sameMethod(method, sponsoredSelector)) return replaceWithNull(method);
        if (videoAdRequest != null && sameMethod(method, videoAdRequest)) return replaceWithVoid(method);
        if (storyAdFetch != null && sameMethod(method, storyAdFetch)) return replaceWithVoid(method);
        return method;
    }

    static Method prefixFeedFilter(Method m) {
        MethodImplementation impl = m.getImplementation();
        if (impl == null || impl.getRegisterCount() < 6) return m;
        List<Instruction> insns = new ArrayList<Instruction>();
        insns.addAll(feedFilterPrefix());
        for (Instruction i : impl.getInstructions()) insns.add(i);

        List<ImmutableTryBlock> tries = new ArrayList<ImmutableTryBlock>();
        for (TryBlock<? extends ExceptionHandler> tb : impl.getTryBlocks()) {
            List<ImmutableExceptionHandler> handlers = new ArrayList<ImmutableExceptionHandler>();
            for (ExceptionHandler h : tb.getExceptionHandlers()) {
                handlers.add(new ImmutableExceptionHandler(h.getExceptionType(),
                        h.getHandlerCodeAddress() + PREFIX_UNITS));
            }
            tries.add(new ImmutableTryBlock(tb.getStartCodeAddress() + PREFIX_UNITS,
                    tb.getCodeUnitCount(), handlers));
        }
        return copyMethod(m, new ImmutableMethodImplementation(
                impl.getRegisterCount(), insns, tries, ImmutableList.of()));
    }

    static Method patchMethod(String type, Method m) {
        if (m.getImplementation() == null) return m;

        if (type.equals("LX/1lJ;") && m.getName().equals("addNewEdgeToCollection")
                && m.getReturnType().equals("Z") && m.getParameters().size() == 3) {
            return prefixFeedFilter(m);
        }
        if (type.equals("LX/3Le;") && m.getName().equals("Di7")
                && m.getReturnType().equals(IMMUTABLE_LIST) && m.getParameters().size() == 2) {
            return replaceWithEmptyList(m);
        }
        if (type.equals("LX/3Le;") && m.getName().equals("A0D")
                && m.getReturnType().equals("LX/6mV;") && m.getParameters().size() == 3) {
            return replaceWithNull(m);
        }
        if (type.equals("LX/1mb;") && m.getName().equals("A09")
                && m.getReturnType().equals("V") && m.getParameters().size() == 1) {
            return replaceWithVoid(m);
        }
        if (type.equals("LX/2yQ;") && m.getName().equals("isAd")
                && m.getReturnType().equals("Z") && m.getParameters().size() == 1) {
            return replaceWithFalse(m);
        }
        // FeedSponsoredStoryHolder.getTopValidAd: the sponsored story selector
        // used by feed/video surfaces. Returning no edge lets the organic path
        // continue instead of vending the selected sponsored edge.
        if (type.equals("LX/21f;") && m.getName().equals("A0R")
                && m.getReturnType().equals("Lcom/facebook/graphql/model/GraphQLFeedUnitEdge;")
                && m.getParameters().isEmpty()) {
            return replaceWithNull(m);
        }
        // FBShortsSponsoredPool: interface implementation that vends the next
        // sponsored short from the pool. Returning no item covers cached/prefetched
        // sponsored shorts that bypass VideoHomeDataFetcher.fetchAds.
        if (type.equals("LX/4r3;") && m.getName().equals("A0I")
                && m.getReturnType().equals("LX/6Za;") && m.getParameters().size() == 4) {
            return replaceWithNull(m);
        }
        if (type.equals("Lcom/facebook/graphql/model/GraphQLFBMultiAdsFeedUnit;")
                && m.getName().equals("A00") && !m.getReturnType().equals("V")) {
            return replaceWithNull(m);
        }
        if (type.contains("MainFeedCSRDataLoaderImpl$maybeDoAsyncAdsTailLoad$1")
                && m.getName().equals("run") && m.getReturnType().equals("V")) {
            return replaceWithVoid(m);
        }
        if (type.equals("LX/OF5;") && m.getName().equals("A05")
                && m.getReturnType().equals("V") && m.getParameters().size() == 6) {
            return replaceWithVoid(m);
        }
        if (type.equals("LX/OJB;") && m.getName().equals("A03")
                && m.getReturnType().equals("V") && m.getParameters().size() == 16) {
            return replaceWithVoid(m);
        }
        // VideoHomeDataFetcher.fetchAds: sponsored video/reels feed request.
        if (type.equals("LX/6F5;") && m.getName().equals("Av8")
                && m.getReturnType().equals("V") && m.getParameters().size() == 1) {
            return replaceWithVoid(m);
        }
        // FeedNetworkController.doAdChannelNetworkRequest: dedicated feed ad
        // channel request that can repopulate the sponsored-story pool.
        if (type.equals("LX/1kU;") && m.getName().equals("Af0")
                && m.getReturnType().equals("V") && m.getParameters().size() == 2) {
            return replaceWithVoid(m);
        }
        // AdsChannelNetworkHandlerHelper.doFetchAdditionalSponsoredStoriesFromNetwork:
        // secondary sponsored-story replenishment path.
        if (type.equals("LX/iR6;") && m.getName().equals("A01")
                && m.getReturnType().equals("V") && m.getParameters().size() == 4) {
            return replaceWithVoid(m);
        }
        return m;
    }

    static boolean hasOriginalName(ClassDef cd, String anchor) {
        for (Field f : cd.getInstanceFields()) {
            if (f.getName().equals("__redex_internal_original_name")
                    && f.getInitialValue() instanceof StringEncodedValue
                    && ((StringEncodedValue) f.getInitialValue()).getValue().equals(anchor)) {
                return true;
            }
        }
        for (Field f : cd.getStaticFields()) {
            if (f.getName().equals("__redex_internal_original_name")
                    && f.getInitialValue() instanceof StringEncodedValue
                    && ((StringEncodedValue) f.getInitialValue()).getValue().equals(anchor)) {
                return true;
            }
        }
        return false;
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2 || args.length > 3) {
            throw new IllegalArgumentException("usage: PatchAds input.dex output.dex [facebook-582]");
        }
        DexFile dex = DexFileFactory.loadDexFile(new File(args[0]), Opcodes.getDefault());
        if (args.length == 3) {
            if (!args[2].equals("facebook-582")) {
                throw new IllegalArgumentException("unknown patch profile: " + args[2]);
            }
            patchModernDex(dex, new File(args[0]), new File(args[0]).getParentFile(), new File(args[1]));
            return;
        }
        DexPool pool = new DexPool(Opcodes.getDefault());
        int patched = 0;
        for (ClassDef cd : dex.getClasses()) {
            List<Method> direct = new ArrayList<Method>();
            List<Method> virtual = new ArrayList<Method>();
            for (Method m : cd.getDirectMethods()) {
                Method n = patchMethod(cd.getType(), m);
                if (n != m) patched++;
                direct.add(n);
            }
            for (Method m : cd.getVirtualMethods()) {
                Method n = patchMethod(cd.getType(), m);
                if (n != m) patched++;
                virtual.add(n);
            }
            if (hasOriginalName(cd, "MainFeedCSRDataLoaderImpl$maybeDoAsyncAdsTailLoad$1")) {
                List<Method> patchedVirtual = new ArrayList<Method>();
                for (Method m : virtual) {
                    if (m.getName().equals("run") && m.getReturnType().equals("V")) {
                        Method n = replaceWithVoid(m);
                        if (n != m) patched++;
                        patchedVirtual.add(n);
                    } else {
                        patchedVirtual.add(m);
                    }
                }
                virtual = patchedVirtual;
            }
            pool.internClass(new ImmutableClassDef(cd.getType(), cd.getAccessFlags(),
                    cd.getSuperclass(), cd.getInterfaces(), cd.getSourceFile(),
                    cd.getAnnotations(), cd.getStaticFields(), cd.getInstanceFields(),
                    direct, virtual));
        }
        pool.writeTo(new FileDataStore(new File(args[1])));
        System.out.println("patched methods: " + patched + "  -> " + args[1]);
    }
}
