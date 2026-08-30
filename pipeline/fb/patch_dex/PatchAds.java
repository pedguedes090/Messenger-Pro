import org.jf.dexlib2.Opcode;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.DexFileFactory;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.DexFile;
import org.jf.dexlib2.iface.Method;
import org.jf.dexlib2.iface.MethodImplementation;
import org.jf.dexlib2.iface.instruction.Instruction;
import org.jf.dexlib2.iface.TryBlock;
import org.jf.dexlib2.iface.ExceptionHandler;
import org.jf.dexlib2.iface.reference.MethodReference;
import org.jf.dexlib2.iface.reference.FieldReference;
import org.jf.dexlib2.immutable.ImmutableClassDef;
import org.jf.dexlib2.immutable.ImmutableMethod;
import org.jf.dexlib2.immutable.ImmutableMethodImplementation;
import org.jf.dexlib2.immutable.ImmutableTryBlock;
import org.jf.dexlib2.immutable.ImmutableExceptionHandler;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction10t;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction11x;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction11n;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction21c;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction22t;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction3rc;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction35c;
import org.jf.dexlib2.immutable.reference.ImmutableMethodReference;
import org.jf.dexlib2.immutable.reference.ImmutableFieldReference;
import org.jf.dexlib2.writer.io.FileDataStore;
import org.jf.dexlib2.writer.pool.DexPool;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Static dexlib2 patch for Facebook 576 feed-ads block (Route C).
 *
 * Patches LX/1lJ;.addNewEdgeToCollection(ImmutableList$Builder,
 *   GraphQLFeedUnitEdge, LX/1et;) -> Z (FeedUnitCollectionManager, classes.dex).
 *
 * Injected prefix (23 code units). Parameters live in the high register group
 * (regs=30, insSize=4): v26=this, v27=builder, v28=edge, v29=enum. Scratch v4/v5
 * are locals (the original body writes them only later), v0 is the return reg.
 *
 *   0x00 invoke-virtual/range {v28}, GraphQLFeedUnitEdge.B9B():GraphQLFeedStoryCategory
 *   0x03 move-result-object v4            // v4 = edge category
 *   0x04 sget-object v5, A0K              // SPONSORED
 *   0x06 if-eq v4, v5, +15                // -> DROP (0x15)
 *   0x08 sget-object v5, A0I              // PROMOTION
 *   0x0a if-eq v4, v5, +11                // -> DROP
 *   0x0c sget-object v5, A0C              // FRIENDLY_FEED_PROMOTION
 *   0x0e if-eq v4, v5, +7                 // -> DROP
 *   0x10 sget-object v5, A0D              // HIGH_VALUE_PROMOTION
 *   0x12 if-eq v4, v5, +3                 // -> DROP
 *   0x14 goto +3                          // -> original body (0x17)
 *   0x15 const/4 v0, 0                    // DROP
 *   0x16 return v0                        // return false
 *   0x17 <original body>
 *
 * Enum constants (verified in GraphQLFeedStoryCategory <clinit>):
 *   A0K=SPONSORED, A0I=PROMOTION, A0C=FRIENDLY_FEED_PROMOTION, A0D=HIGH_VALUE_PROMOTION.
 */
public class PatchAds {
    static final int PREFIX_UNITS = 23; // 3+1+2+2+2+2+2+2+2+2+1+1+1

    static final String CATEGORY = "Lcom/crossapp/graphql/facebook/enums/GraphQLFeedStoryCategory;";

    static final MethodReference B9B = new ImmutableMethodReference(
            "Lcom/facebook/graphql/model/GraphQLFeedUnitEdge;",
            "B9B", ImmutableList.<String>of(), CATEGORY);

    /** Ad categories to drop before the original body runs. */
    static final String[] DROP_FIELDS = { "A0K", "A0I", "A0C", "A0D" };

    static FieldReference field(String name) {
        return new ImmutableFieldReference(CATEGORY, name, CATEGORY);
    }

    static List<Instruction> buildPrefix() {
        List<Instruction> out = new ArrayList<Instruction>();
        // v28 = edge (param index 1 lives in high reg group v26..v29: v26=this v27=builder v28=edge v29=enum)
        // 35c cannot encode v28, so use invoke-virtual/range (3rc).
        out.add(new ImmutableInstruction3rc(Opcode.INVOKE_VIRTUAL_RANGE, 28, 1, B9B));
        out.add(new ImmutableInstruction11x(Opcode.MOVE_RESULT_OBJECT, 4));
        // DROP const/4 v0,0 sits at address 0x15 (21); branch offsets are relative
        // to each if-eq instruction address (6, 10, 14, 18).
        int[] offsets = { 15, 11, 7, 3 };
        for (int i = 0; i < DROP_FIELDS.length; i++) {
            out.add(new ImmutableInstruction21c(Opcode.SGET_OBJECT, 5, field(DROP_FIELDS[i])));
            out.add(new ImmutableInstruction22t(Opcode.IF_EQ, 4, 5, offsets[i]));
        }
        out.add(new ImmutableInstruction10t(Opcode.GOTO, 3));          // skip DROP -> original
        out.add(new ImmutableInstruction11n(Opcode.CONST_4, 0, 0));    // DROP: v0 = 0 (false)
        out.add(new ImmutableInstruction11x(Opcode.RETURN, 0));        // return v0
        return out;
    }

    public static void main(String[] args) throws Exception {
        File in = new File(args[0]);
        File out = new File(args[1]);
        Opcodes opcodes = Opcodes.getDefault();
        DexFile dex = DexFileFactory.loadDexFile(in, opcodes);
        DexPool pool = new DexPool(opcodes);
        int patched = 0;
        for (ClassDef cd : dex.getClasses()) {
            if (cd.getType().equals("LX/1lJ;")) {
                List<Method> newDirect = new ArrayList<Method>();
                for (Method m : cd.getDirectMethods()) newDirect.add(m);
                List<Method> newVirtual = new ArrayList<Method>();
                for (Method m : cd.getVirtualMethods()) {
                    if (isTarget(m)) {
                        newVirtual.add(patch(m));
                        patched++;
                    } else {
                        newVirtual.add(m);
                    }
                }
                ImmutableClassDef ncd = new ImmutableClassDef(
                        cd.getType(), cd.getAccessFlags(), cd.getSuperclass(), cd.getInterfaces(),
                        cd.getSourceFile(), cd.getAnnotations(),
                        cd.getStaticFields(), cd.getInstanceFields(), newDirect, newVirtual);
                pool.internClass(ncd);
            } else {
                pool.internClass(cd);
            }
        }
        pool.writeTo(new FileDataStore(out));
        System.out.println("patched methods: " + patched + "  -> " + out);
    }

    static boolean isTarget(Method m) {
        return m.getName().equals("addNewEdgeToCollection")
                && m.getReturnType().equals("Z")
                && m.getParameters().size() == 3;
    }

    static Method patch(Method m) {
        MethodImplementation impl = m.getImplementation();
        int newRegs = impl.getRegisterCount();
        int insnCount = 0;
        for (Instruction i : impl.getInstructions()) insnCount++;
        System.out.println("patching " + m.getDefiningClass() + "->" + m.getName()
                + " params=" + m.getParameters().size()
                + " ret=" + m.getReturnType()
                + " regs=" + newRegs
                + " insns=" + insnCount
                + " tryBlocks=" + impl.getTryBlocks().size());

        List<Instruction> insns = new ArrayList<Instruction>();
        insns.addAll(buildPrefix());
        for (Instruction i : impl.getInstructions()) insns.add(i);

        List<ImmutableTryBlock> newTry = new ArrayList<ImmutableTryBlock>();
        for (TryBlock<? extends ExceptionHandler> tb : impl.getTryBlocks()) {
            List<ImmutableExceptionHandler> nh = new ArrayList<ImmutableExceptionHandler>();
            for (ExceptionHandler h : tb.getExceptionHandlers()) {
                nh.add(new ImmutableExceptionHandler(h.getExceptionType(),
                        h.getHandlerCodeAddress() + PREFIX_UNITS));
            }
            newTry.add(new ImmutableTryBlock(tb.getStartCodeAddress() + PREFIX_UNITS,
                    tb.getCodeUnitCount(), nh));
        }

        MethodImplementation nimpl = new ImmutableMethodImplementation(newRegs, insns, newTry, null);
        return new ImmutableMethod(
                m.getDefiningClass(), m.getName(), m.getParameters(), m.getReturnType(),
                m.getAccessFlags(), m.getAnnotations(), m.getHiddenApiRestrictions(), nimpl);
    }
}
