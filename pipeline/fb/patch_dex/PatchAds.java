import org.jf.dexlib2.Opcode;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.DexFileFactory;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.DexFile;
import org.jf.dexlib2.iface.Method;
import org.jf.dexlib2.iface.MethodImplementation;
import org.jf.dexlib2.iface.Field;
import org.jf.dexlib2.iface.instruction.Instruction;
import org.jf.dexlib2.iface.ExceptionHandler;
import org.jf.dexlib2.iface.TryBlock;
import org.jf.dexlib2.iface.reference.FieldReference;
import org.jf.dexlib2.iface.reference.MethodReference;
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
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction22t;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction35c;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction3rc;
import org.jf.dexlib2.immutable.reference.ImmutableFieldReference;
import org.jf.dexlib2.immutable.reference.ImmutableMethodReference;
import org.jf.dexlib2.writer.io.FileDataStore;
import org.jf.dexlib2.writer.pool.DexPool;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Static Facebook 576 ad-route patcher.
 *
 * The target names/descriptors are the verified 576 map in ADS_BLOCK_576_REPORT.md.
 * The patcher is intentionally no-op for every other class/method so it can be
 * run over all 18 superpack DEX files.
 */
public class PatchAds {
    static final int PREFIX_UNITS = 23;
    static final String CATEGORY = "Lcom/crossapp/graphql/facebook/enums/GraphQLFeedStoryCategory;";
    static final String IMMUTABLE_LIST = "Lcom/google/common/collect/ImmutableList;";

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
        if (args.length != 2) throw new IllegalArgumentException("usage: PatchAds input.dex output.dex");
        DexFile dex = DexFileFactory.loadDexFile(new File(args[0]), Opcodes.getDefault());
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
