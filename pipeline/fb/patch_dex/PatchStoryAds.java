import org.jf.dexlib2.Opcode;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.DexFileFactory;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.DexFile;
import org.jf.dexlib2.iface.Method;
import org.jf.dexlib2.iface.MethodImplementation;
import org.jf.dexlib2.iface.instruction.Instruction;
import org.jf.dexlib2.immutable.ImmutableClassDef;
import org.jf.dexlib2.immutable.ImmutableMethod;
import org.jf.dexlib2.immutable.ImmutableMethodImplementation;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction11n;
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction11x;
import org.jf.dexlib2.writer.io.FileDataStore;
import org.jf.dexlib2.writer.pool.DexPool;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class PatchStoryAds {
    public static void main(String[] args) throws Exception {
        File in = new File(args[0]);
        File out = new File(args[1]);
        Opcodes opcodes = Opcodes.getDefault();
        DexFile dex = DexFileFactory.loadDexFile(in, opcodes);
        DexPool pool = new DexPool(opcodes);
        int patched = 0;
        for (ClassDef cd : dex.getClasses()) {
            boolean hasAdString = false;
            for (Method m : cd.getMethods()) {
                if (m.getImplementation() != null) {
                    for (Instruction i : m.getImplementation().getInstructions()) {
                        String str = i.getCode() + "";
                        if (str.contains("AD_BUCKETS") || str.contains("IN_DISC") || str.contains("story_ad")) {
                            hasAdString = true; break;
                        }
                    }
                }
                if (hasAdString) break;
            }
            if (hasAdString) {
                List<Method> newDirect = new ArrayList<>(), newVirtual = new ArrayList<>();
                for (Method m : cd.getDirectMethods()) newDirect.add(patchMethod(m));
                for (Method m : cd.getVirtualMethods()) newVirtual.add(patchMethod(m));
                pool.internClass(new ImmutableClassDef(cd.getType(), cd.getAccessFlags(), cd.getSuperclass(), cd.getInterfaces(), cd.getSourceFile(), cd.getAnnotations(), cd.getStaticFields(), cd.getInstanceFields(), newDirect, newVirtual));
                patched++;
            } else { pool.internClass(cd); }
        }
        pool.writeTo(new FileDataStore(out));
        System.out.println("Story ads patched classes: " + patched + " -> " + out);
    }
    static Method patchMethod(Method m) {
        MethodImplementation impl = m.getImplementation();
        if (impl == null || impl.getInstructions().isEmpty()) return m;
        String mName = m.getName();
        if (mName.contains("fetch") || mName.contains("load") || mName.contains("get") || mName.contains("provide") || mName.contains("merge")) {
            List<Instruction> insns = new ArrayList<>(impl.getInstructions());
            insns.add(0, new ImmutableInstruction11n(Opcode.CONST_4, 0, 0));
            insns.add(1, new ImmutableInstruction11x(Opcode.RETURN, 0));
            MethodImplementation nimpl = new ImmutableMethodImplementation(impl.getRegisterCount() + 2, insns, new ArrayList<>(), null);
            return new ImmutableMethod(m.getDefiningClass(), mName, m.getParameters(), m.getReturnType(), m.getAccessFlags(), m.getAnnotations(), m.getHiddenApiRestrictions(), nimpl);
        }
        return m;
    }
}