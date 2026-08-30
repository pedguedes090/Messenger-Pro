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
import org.jf.dexlib2.immutable.instruction.ImmutableInstruction10x;
import org.jf.dexlib2.writer.io.FileDataStore;
import org.jf.dexlib2.writer.pool.DexPool;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class PatchDex {
    public static void main(String[] args) throws Exception {
        File in = new File(args[0]);
        File out = new File(args[1]);
        Opcodes opcodes = Opcodes.getDefault();
        DexFile dex = DexFileFactory.loadDexFile(in, opcodes);
        DexPool pool = new DexPool(opcodes);
        int patched = 0;
        for (ClassDef cd : dex.getClasses()) {
            if (cd.getType().equals("LX/BII;")) {
                List<Method> newDirect = new ArrayList<Method>();
                for (Method m : cd.getDirectMethods()) newDirect.add(m);
                List<Method> newVirtual = new ArrayList<Method>();
                for (Method m : cd.getVirtualMethods()) {
                    if (m.getName().equals("A00") && m.getReturnType().equals("V")) {
                        int regs = m.getParameterTypes().size() + 1;
                        ImmutableList<Instruction> insns = ImmutableList.<Instruction>of(
                            new ImmutableInstruction10x(Opcode.RETURN_VOID));
                        MethodImplementation impl = new ImmutableMethodImplementation(regs, insns, null, null);
                        newVirtual.add(new ImmutableMethod(
                            cd.getType(), m.getName(), m.getParameters(), m.getReturnType(),
                            m.getAccessFlags(), m.getAnnotations(), m.getHiddenApiRestrictions(), impl));
                        patched++;
                        System.out.println("Patched LX/BII;.A00 params=" + m.getParameterTypes().size() + " regs=" + regs);
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
        System.out.println("patched methods: " + patched + "  -> wrote " + out);
    }
}
