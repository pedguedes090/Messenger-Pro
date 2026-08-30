package tn.amin.mpro2;

import android.content.res.AssetManager;
import android.content.res.Resources;
import android.content.res.XModuleResources;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import tn.amin.mpro2.constants.OrcaInfo;
import tn.amin.mpro2.debug.Logger;
import tn.amin.mpro2.debug.OrcaExplorer;
import tn.amin.mpro2.orca.OrcaGateway;

public class MainHook implements IXposedHookLoadPackage, IXposedHookZygoteInit {
    private String modulePath = null;

    @Override
    public void initZygote(StartupParam startupParam) throws Throwable {
        this.modulePath = startupParam.modulePath;
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        android.util.Log.e("MProFB", "handleLoadPackage: " + lpparam.packageName);
        switch (lpparam.packageName) {
            case OrcaInfo.ORCA_PACKAGE_NAME:
                orcaHook(lpparam);
                break;
            case FacebookHook.FACEBOOK_PACKAGE:
                FacebookHook.install(lpparam);
                break;
        }
    }

    /**
     * Initialize gateway with Messenger package and implement all features.
     * @param lpparam information about messenger package (version, dir...)
     */
    private void orcaHook(XC_LoadPackage.LoadPackageParam lpparam) {
        OrcaGateway gateway = new OrcaGateway(lpparam.appInfo.sourceDir, lpparam.classLoader, getResources());

        MProPatcher featuresBox = new MProPatcher(gateway);
        featuresBox.init();

        OrcaExplorer.exploreEarly(lpparam.classLoader);
    }

    private Resources getResources() {
        try {
            return XModuleResources.createInstance(modulePath, null);
        } catch (Throwable t) {
            // Android 15: XModuleResources relies on ActivityThread#mResourcesManager,
            // which was removed. Fall back to loading the module APK via AssetManager.
            Logger.warn("XModuleResources.createInstance failed, using AssetManager fallback: " + t.getMessage());
            try {
                Constructor<AssetManager> ctor = AssetManager.class.getDeclaredConstructor();
                ctor.setAccessible(true);
                AssetManager assetManager = ctor.newInstance();
                Method addAssetPath = AssetManager.class.getDeclaredMethod("addAssetPath", String.class);
                addAssetPath.setAccessible(true);
                addAssetPath.invoke(assetManager, modulePath);
                Resources system = Resources.getSystem();
                return new Resources(assetManager, system.getDisplayMetrics(), system.getConfiguration());
            } catch (Throwable t2) {
                Logger.warn("AssetManager fallback failed: " + t2.getMessage());
                return Resources.getSystem();
            }
        }
    }
}
