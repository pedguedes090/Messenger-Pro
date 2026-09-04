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
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class PatchGameAds {
    public static void main(String[] args) throws Exception {
        File in = new File(args[0]);
        File out = new File(args[1]);
        Opcodes opcodes = Opcodes.getDefault();
        DexFile dex = DexFileFactory.loadDexFile(in, opcodes);
        DexPool pool = new DexPool(opcodes);
        int patched = 0;
        for (ClassDef cd : dex.getClasses()) {
            String classType = cd.getType();
            boolean isTarget = classType.equals("Lcom/facebook/ads/AudienceNetworkActivity;") || classType.contains("AudienceNetwork");
            if (isTarget) {
                List<Method> newDirect = new ArrayList<Method>();
                List<Method> newVirtual = new ArrayList<Method>();
                for (Method m : cd.getDirectMethods()) newDirect.add(patchMethod(m));
                for (Method m : cd.getVirtualMethods()) newVirtual.add(patchMethod(m));
                pool.internClass(new ImmutableClassDef(cd.getType(), cd.getAccessFlags(), cd.getSuperclass(), cd.getInterfaces(), cd.getSourceFile(), cd.getAnnotations(), cd.getStaticFields(), cd.getInstanceFields(), newDirect, newVirtual));
                patched++;
            } else { pool.internClass(cd); }
        }
        pool.writeTo(new FileDataStore(out));
        System.out.println("Game ads patched classes: " + patched + " -> " + out);
    }
    static Method patchMethod(Method m) {
        MethodImplementation impl = m.getImplementation();
        if (impl == null) return m;
        String mName = m.getName();
        List<Instruction> insns = new ArrayList<Instruction>();
        for (Instruction i : impl.getInstructions()) insns.add(i);
        if (mName.equals("postMessage")) {
            insns.clear();
            insns.add(new ImmutableInstruction11x(Opcode.RETURN_VOID, 0));
            MethodImplementation nimpl = new ImmutableMethodImplementation(2, ImmutableList.copyOf(insns), ImmutableList.of(), ImmutableList.of());
            return new ImmutableMethod(m.getDefiningClass(), mName, m.getParameters(), m.getReturnType(), m.getAccessFlags(), m.getAnnotations(), m.getHiddenApiRestrictions(), nimpl);
        }
        if (mName.equals("<init>") && m.getParameters().isEmpty()) {
            insns.clear();
            insns.add(new ImmutableInstruction11n(Opcode.CONST_4, 0, 1));
            insns.add(new ImmutableInstruction11x(Opcode.THROW, 0));
            MethodImplementation nimpl = new ImmutableMethodImplementation(2, ImmutableList.copyOf(insns), ImmutableList.of(), ImmutableList.of());
            return new ImmutableMethod(m.getDefiningClass(), mName, m.getParameters(), m.getReturnType(), m.getAccessFlags(), m.getAnnotations(), m.getHiddenApiRestrictions(), nimpl);
        }
        return m;
    }
}