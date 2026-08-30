import com.reandroid.apk.ApkBundle;
import com.reandroid.apk.ApkModule;
import java.io.File;

public class MergeApk {
    public static void main(String[] args) throws Exception {
        String inDir = args[0];
        String outPath = args[1];
        ApkBundle bundle = new ApkBundle();
        bundle.loadApkDirectory(new File(inDir));
        System.out.println("modules: " + bundle.listModuleNames());
        ApkModule merged = bundle.mergeModules();
        System.out.println("merged module name: " + merged.getModuleName());
        File out = new File(outPath);
        if (out.exists()) out.delete();
        merged.writeApk(out);
        System.out.println("wrote " + outPath + " (" + out.length() + " bytes)");
        merged.close();
        bundle.close();
    }
}
