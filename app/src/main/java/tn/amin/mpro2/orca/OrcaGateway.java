package tn.amin.mpro2.orca;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import tn.amin.mpro2.R;
import tn.amin.mpro2.constants.OrcaClassNames;
import tn.amin.mpro2.debug.Logger;
import tn.amin.mpro2.features.MProFeatureManager;
import tn.amin.mpro2.features.util.message.formatting.MessageParser;
import tn.amin.mpro2.hook.ActivityHook;
import tn.amin.mpro2.hook.MProHookManager;
import tn.amin.mpro2.hook.unobfuscation.OrcaUnobfuscator;
import tn.amin.mpro2.orca.connector.MailboxConnector;
import tn.amin.mpro2.orca.wrapper.AuthDataWrapper;
import tn.amin.mpro2.preference.ModulePreferences;
import tn.amin.mpro2.state.ModuleState;
import tn.amin.mpro2.ui.ModuleContextWrapper;
import tn.amin.mpro2.ui.ModuleResources;
import tn.amin.mpro2.ui.Toaster;

/**
 * Holds all the data necessary to interface with Messenger package.
 */
public class OrcaGateway {

    public ClassLoader classLoader;
    public ModuleResources res = null;
    public ModulePreferences pref = null;
    public ModuleState state = null;

    /**
     * Will enable execution of user actions (sending messages, reacting...)
     */
    public MailboxConnector mailboxConnector;
    public AuthDataWrapper authData;
    public Long currentThreadKey;
    /**
     * Tracked from ThreadKey constructor hook - used as fallback when
     * ConversationEnterHook doesn't fire (Messenger 553+).
     */
    public volatile Long lastTrackedThreadKey = null;
    public volatile String lastTrackedThreadKeyType = null;
    private final MessageParser mMessageParser;

    /**
     * Undoes the obfuscation of some important Messenger components
     */
    public OrcaUnobfuscator unobfuscator;
    public ActivityHook activityHook = null;

    private Resources mPendingResources;
    private final String sourceDir;
    private final ArrayList<Runnable> mailboxCallback = new ArrayList<>();

    private WeakReference<Context> mContext = new WeakReference<>(null);
    private WeakReference<Activity> mActivity = new WeakReference<>(null);
    private WeakReference<MProHookManager> mHookManager = new WeakReference<>(null);
    private WeakReference<MProFeatureManager> mFeatureManager = new WeakReference<>(null);
    private Toaster mToaster = null;

    public OrcaGateway(String sourceDir, ClassLoader classLoader, Resources moduleResources) {
        this.sourceDir = sourceDir;
        this.classLoader = classLoader;

        mPendingResources = moduleResources;

        // TODO import delimiters
        mMessageParser = new MessageParser();

        prepareToCaptureMailbox();
        hookThreadKeyConstructor();
    }

    /**
     * Hook ThreadKey constructor as fallback tracker.
     * Filters FOLDER types, only tracks real conversation keys.
     */
    private void hookThreadKeyConstructor() {
        try {
            Class<?> threadKeyClass = XposedHelpers.findClass(OrcaClassNames.THREAD_KEY, classLoader);

            XposedBridge.hookAllConstructors(threadKeyClass, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Object tk = param.thisObject;
                    String tkStr = tk.toString();
                    String[] parts = tkStr.split(":");
                    if (parts.length >= 2) {
                        String type = parts[0];
                        if ("FOLDER".equals(type)) return;
                        try {
                            Long key = Long.parseLong(parts[1]);
                            lastTrackedThreadKey = key;
                            lastTrackedThreadKeyType = type;
                        } catch (NumberFormatException e) {
                            // ignore
                        }
                    }
                }
            });

            Logger.info("ThreadKey constructor hook installed successfully");
        } catch (Throwable t) {
            Logger.error("Failed to hook ThreadKey constructor: " + t.getMessage());
            Logger.error(t);
        }
    }

    /**
     * Initialize ResourcesManager
     */
    public void initStateAndResources(Context context) {
        res = new ModuleResources(context, mPendingResources);
        state = new ModuleState(context);
        mPendingResources = null;

        mToaster = new Toaster(getContext(), res);
    }

    public void setPreferences(ModulePreferences preferences) {
        this.pref = preferences;
    }

    /**
     * Initialize unobfuscator which finishes its job by the end of the method.
     */
    public void initUnobfuscator(Context context, boolean searchAgain) {
        unobfuscator = new OrcaUnobfuscator(context, sourceDir, classLoader, searchAgain);
        unobfuscator.save();
    }

    /**
     * This is the first hook to be set up.
     * Waits for Mailbox to be constructed and then stores it in a variable.
     */
    public void prepareToCaptureMailbox() {
//        Mailboxinfo no longer has authData as parameter so we use AccountSession to get it instead
        final Class<?> AccountSessionClass = XposedHelpers.findClass("com.facebook.msys.mci.AccountSession", classLoader);
        final Class<?> Mailbox = XposedHelpers.findClass(OrcaClassNames.MAILBOX, classLoader);

        for (Method method : AccountSessionClass.getDeclaredMethods()) {
            if (method.getName().equals("createWithAuthData")) {
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        Logger.info("Captured mailbox configuration!");
                        authData = new AuthDataWrapper(param.args[0]);
                    }
                });
            }
        }

        XposedBridge.hookAllConstructors(Mailbox, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Logger.info("Captured mailbox !");
                Object mailbox = param.thisObject;
                mailboxConnector = new MailboxConnector(mailbox, authData, classLoader);
                for (Runnable callback : mailboxCallback) {
                    callback.run();
                }
                mailboxCallback.clear();
            }
        });

        // Hook MailboxFeature constructor to capture 7Da (MailboxSDK wrapper) instance
        try {
            final Class<?> cls7Da = XposedHelpers.findClass("X.7Da", classLoader);

            // Hook MailboxFeature constructor
            final Class<?> MailboxFeature = XposedHelpers.findClass("com.facebook.msys.mca.MailboxFeature", classLoader);
            XposedBridge.hookAllConstructors(MailboxFeature, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (cls7Da.isInstance(param.thisObject)) {
                        Logger.info("Captured 7Da from MailboxFeature constructor!");
                        if (mailboxConnector != null) {
                            mailboxConnector.set7Da(param.thisObject);
                        }
                    }
                }
            });

            // Also hook 7Da factory methods: X.7TM.A02 and X.GF0.A00
            try {
                Class<?> cls7TM = XposedHelpers.findClass("X.7TM", classLoader);
                for (Method m : cls7TM.getDeclaredMethods()) {
                    if (m.getName().equals("A02") && m.getReturnType() == cls7Da) {
                        XposedBridge.hookMethod(m, new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                Object result = param.getResult();
                                if (result != null && cls7Da.isInstance(result)) {
                                    Logger.info("Captured 7Da from 7TM.A02 factory!");
                                    if (mailboxConnector != null) {
                                        mailboxConnector.set7Da(result);
                                    }
                                }
                            }
                        });
                        Logger.info("Hooked 7TM.A02 for 7Da capture");
                        break;
                    }
                }
            } catch (Throwable t) {
                Logger.info("7TM.A02 hook failed: " + t.getMessage());
            }

            try {
                Class<?> clsGF0 = XposedHelpers.findClass("X.GF0", classLoader);
                for (Method m : clsGF0.getDeclaredMethods()) {
                    if (m.getName().equals("A00") && m.getReturnType() == cls7Da) {
                        XposedBridge.hookMethod(m, new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                Object result = param.getResult();
                                if (result != null && cls7Da.isInstance(result)) {
                                    Logger.info("Captured 7Da from GF0.A00 factory!");
                                    if (mailboxConnector != null) {
                                        mailboxConnector.set7Da(result);
                                    }
                                }
                            }
                        });
                        Logger.info("Hooked GF0.A00 for 7Da capture");
                        break;
                    }
                }
            } catch (Throwable t) {
                Logger.info("GF0.A00 hook failed: " + t.getMessage());
            }

            Logger.info("Set up 7Da capture hooks");
        } catch (Throwable t) {
            Logger.error("Failed to set up 7Da capture: " + t.getMessage());
        }
    }

    /**
     * Ensure that Mailbox has been captured.
     * To be used when execution of user actions is needed right after the modules has been loaded.
     *
     * @param callback run just after Mailbox is captured.
     */
    public void doOnMailboxCaptured(Runnable callback) {
        if (mailboxConnector == null) {
            mailboxCallback.add(callback);
        } else {
            callback.run();
        }
    }

    public boolean isPackageInstalled(String packageName) {
        PackageManager pm = getContext().getPackageManager();
        try {
            pm.getPackageInfo(packageName, 0);
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }

        return true;
    }

    public Toaster getToaster() {
        return mToaster;
    }

    public void setContext(Context context) {
        mContext = new WeakReference<>(context);
    }

    public Context getContext() {
        return mContext.get();
    }

    public Context getActivityWithModuleResources() {
        return new ModuleContextWrapper(mActivity.get(), res.unwrap());
    }

    ArrayList<Runnable> mActivityCallbacks = new ArrayList<>();
    public void doOnActivity(Runnable runnable) {
        if (getActivity() == null) {
            mActivityCallbacks.add(runnable);
        } else {
            runnable.run();
        }
    }

    public void setActivity(Activity activity) {
        mActivity = new WeakReference<>(activity);
        for (Runnable callback: mActivityCallbacks) {
            callback.run();
        }
        mActivityCallbacks.clear();
    }

    public Activity getActivity() {
        return mActivity.get();
    }

    public MessageParser getMessageParser() {
        return mMessageParser;
    }

    public boolean requireThreadKey() {
        return requireThreadKey(true);
    }

    public boolean requireThreadKey(boolean tellUser) {
        if (currentThreadKey == null && lastTrackedThreadKey != null) {
            currentThreadKey = lastTrackedThreadKey;
            Logger.info("requireThreadKey: using tracked ThreadKey=" + currentThreadKey);
        }
        if (currentThreadKey == null) {
            if (tellUser && getContext() != null) {
                Toast.makeText(getContext(), res.getString(R.string.threadkey_required), Toast.LENGTH_SHORT).show();
            }
            return false;
        }
        return true;
    }

    public void setHookManager(MProHookManager hookManager) {
        mHookManager = new WeakReference<>(hookManager);
    }

    public MProHookManager getHookManager() {
        return mHookManager.get();
    }

    public void setFeatureManager(MProFeatureManager featureManager) {
        mFeatureManager = new WeakReference<>(featureManager);
    }

    public MProFeatureManager getFeatureManager() {
        return mFeatureManager.get();
    }
}
