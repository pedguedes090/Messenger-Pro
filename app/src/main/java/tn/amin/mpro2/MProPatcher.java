package tn.amin.mpro2;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.ContextMenu;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.StringRes;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import tn.amin.mpro2.constants.ModuleInfo;
import tn.amin.mpro2.constants.OrcaClassNames;
import tn.amin.mpro2.constants.OrcaInfo;
import tn.amin.mpro2.debug.Logger;
import tn.amin.mpro2.debug.OrcaExplorer;
import tn.amin.mpro2.features.FeatureId;
import tn.amin.mpro2.features.MProFeatureManager;
import tn.amin.mpro2.features.action.DownloadVideoFeature;
import tn.amin.mpro2.file.StorageConstants;
import tn.amin.mpro2.hook.ActivityHook;
import tn.amin.mpro2.hook.ApplicationHook;
import tn.amin.mpro2.hook.BroadcastReceiverHook;
import tn.amin.mpro2.hook.HookTime;
import tn.amin.mpro2.hook.MProHookManager;
import tn.amin.mpro2.messaging.history.MessageHistoryStore;
import tn.amin.mpro2.orca.OrcaBridge;
import tn.amin.mpro2.orca.OrcaGateway;
import tn.amin.mpro2.orca.connector.MailboxConnector;
import tn.amin.mpro2.orca.datatype.GenericMessage;
import tn.amin.mpro2.preference.MapSharedPreferences;
import tn.amin.mpro2.preference.ModulePreferences;
import tn.amin.mpro2.ui.toolbar.MProToolbar;
import tn.amin.mpro2.ui.touch.LongPressDetector;
import tn.amin.mpro2.util.Range;


/**
 * Implements all features of the module
 */
public class MProPatcher implements
        ApplicationHook.ApplicationEventListener,
        ActivityHook.ActivityEventListener,
        BroadcastReceiverHook.BroadcastReceiverEventListener,
        MProToolbar.Listener, LongPressDetector.LongPressListener {
    private final OrcaGateway gateway;
    private WeakReference<Application> mApplication = new WeakReference<>(null);
    private WeakReference<Activity> mActivity = new WeakReference<>(null);
    private WeakReference<Context> mReceiverContext = new WeakReference<>(null);
    private WeakReference<Context> mContext = new WeakReference<>(null);
    private ModulePreferences mPreferences;
    private MProFeatureManager mFeatureManager;
    private MProHookManager mHookManager;

    private ApplicationHook applicationHook;
    private ActivityHook activityHook;
    private BroadcastReceiverHook broadcastReceiverHook;

    private MProToolbar mToolbar;
    private boolean mUiHooked = false;

    private Runnable mOnInternalSetupFinishedCallback = () -> {};
    private String mPendingError = null;
    private String mPendingWarning = null;
    private final ArrayList<Runnable> mOnActivitySetCallbacks = new ArrayList<>();
    private final ArrayList<Runnable> mOnSetupFinishedCallbacks = new ArrayList<>();
    public final LongPressDetector mSettingsLongPressDetector = new LongPressDetector(null, new Range(0, 200));
    public final LongPressDetector mMessageLongPressDetector = new LongPressDetector(null, new Range(200, 5000));

    private boolean mInternalSetupFinished = false;
    private boolean mSetupFinished = false;

    public MProPatcher(OrcaGateway gateway) {
        this.gateway = gateway;
    }

    /**
     * Initializes the 3 possible triggers to implement features.
     * <ul>
     *     <li>Messenger's MainActivity (in case the user launches messenger)</li>
     *     <li>Messenger's BroadcastReceiver (in case messenger is launched by tasker)</li>
     *     <li>ApplicationContext (in case messenger is launched in the background)</li>
     * </ul>
     */
    public void init() {
        applicationHook = new ApplicationHook(OrcaInfo.ORCA_APPLICATION,
                gateway.classLoader, this);
        activityHook = new ActivityHook(OrcaInfo.ORCA_MAIN_ACTIVITY,
                gateway.classLoader, this);
        broadcastReceiverHook = new BroadcastReceiverHook(OrcaInfo.ORCA_SAMPLE_EXPORTED_RECEIVER,
                    gateway.classLoader, OrcaBridge.PARAM_ACTION,  this);
        gateway.activityHook = activityHook;

        mSettingsLongPressDetector.setLongPressListener(this);
        mMessageLongPressDetector.setLongPressListener(() -> {
            DownloadVideoFeature downloadFeature = (DownloadVideoFeature)
                    mFeatureManager.getFeature(FeatureId.VIDEO_DOWNLOAD);
            if (downloadFeature != null) {
                downloadFeature.onLongPressInMessageArea();
            }
        });
    }

    /**
     * Ensures module is installed, and messenger version is compatible
     * Sets {@link MProPatcher#mPendingError} if not
     */
    private void ensureCompatibility() {
        if (!gateway.isPackageInstalled(BuildConfig.APPLICATION_ID)) {
            mPendingError = gateway.res.getString(R.string.need_install_module);
        }

        if (gateway.state.getOrcaVersion() < ModuleInfo.MIN_ORCA_VERSION) {
            mPendingWarning = gateway.res.getString(R.string.too_old_version, ModuleInfo.RECOMMENDED_ORCA_VERSION_STRING);
        }

        if (gateway.state.getOrcaVersion() > ModuleInfo.MAX_ORCA_VERSION) {
            mPendingWarning = gateway.res.getString(R.string.too_new_version, ModuleInfo.RECOMMENDED_ORCA_VERSION_STRING);
        }
    }

    /**
     * Called when context is captured.
     * Initializes essential components and injects hooks
     */
    private void setupNormal() {
        Logger.info("Starting normal setup");

        Logger.info("Initializing module preferences...");
        mPreferences = new ModulePreferences(getContext());
        gateway.setPreferences(mPreferences);

        Logger.info("Initializing state and resources...");
        gateway.initStateAndResources(getContext());

        // ensure module is installed (for non root version)
        // and messenger version is compatible
        ensureCompatibility();

        if (mPendingError != null) {
            doOnActivitySet(() -> new Handler(Looper.getMainLooper()).postDelayed(() -> {
                showErrorDialog(mPendingError);
            }, 500));
            return;
        }

        else if (mPendingWarning != null) {
//            doOnActivitySet(() -> new Handler(Looper.getMainLooper()).postDelayed(() -> {
//                showWarningDialog(mPendingWarning);
//            }, 500));
            doOnActivitySet(() -> new Handler(Looper.getMainLooper()).post(() -> {
                Toast.makeText(getContext(), mPendingWarning, Toast.LENGTH_LONG).show();
            }));
        }

        else if (gateway.state.isFirstTime()) {
            doOnActivitySet(() -> new Handler(Looper.getMainLooper()).postDelayed(() -> {
                showWelcomeDialog();
            }, 500));
        }

        try {
            Logger.info("Injecting normal hooks...");
            mHookManager = new MProHookManager(gateway);
            mHookManager.initStateTracking(gateway.state.sp);

            Logger.info("Injecting normal hooks...");
            mHookManager.inject(gateway, hook -> HookTime.NORMAL.equals(hook.getHookTime()));
            gateway.setHookManager(mHookManager);

            mFeatureManager = new MProFeatureManager(mHookManager);
            mFeatureManager.initFeatures(gateway);
            gateway.setFeatureManager(mFeatureManager);

            Logger.verbosePermissionSupplier = mPreferences::isVerboseLoggingEnabled;
            // Hook the 7Da.A0N (sendImageAttachmentMessage) to discover its signature and params
            try {
                Class<?> cls7Da = XposedHelpers.findClass("X.7Da", gateway.classLoader);
                for (java.lang.reflect.Method m : cls7Da.getDeclaredMethods()) {
                    if (m.getName().equals("A0N")) {
                        XposedBridge.hookMethod(m, new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                StringBuilder sb = new StringBuilder("7Da.A0N CALLED! thisObj=" + param.thisObject + " args=(");
                                for (int i = 0; i < param.args.length; i++) {
                                    if (i > 0) sb.append(", ");
                                    Object arg = param.args[i];
                                    if (arg == null) sb.append("null");
                                    else sb.append("[" + i + "]" + arg.getClass().getName() + "=" + String.valueOf(arg).substring(0, Math.min(String.valueOf(arg).length(), 100)));
                                }
                                sb.append(")");
                                Logger.info(sb.toString());
                                // Capture 7Da instance
                                if (param.thisObject != null && gateway.mailboxConnector != null) {
                                    gateway.mailboxConnector.set7Da(param.thisObject);
                                }
                            }
                        });
                        Logger.info("Hooked 7Da.A0N for image send discovery");
                        break;
                    }
                }
                // Also hook A0T (sendTextMessage) on 7Da to capture instance
                for (java.lang.reflect.Method m : cls7Da.getDeclaredMethods()) {
                    if (m.getName().equals("A0T")) {
                        XposedBridge.hookMethod(m, new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                Logger.info("7Da.A0T (sendText) CALLED! thisObj class=" + (param.thisObject != null ? param.thisObject.getClass().getName() : "null"));
                                // Capture 7Da instance for sendAttachment
                                if (param.thisObject != null && gateway.mailboxConnector != null) {
                                    gateway.mailboxConnector.set7Da(param.thisObject);
                                }
                            }
                        });
                        Logger.info("Hooked 7Da.A0T for text send discovery");
                        break;
                    }
                }
                // Hook ALL 7Da instance methods to capture the 7Da reference as early as possible
                for (java.lang.reflect.Method m : cls7Da.getDeclaredMethods()) {
                    String mName = m.getName();
                    // Skip already-hooked methods and static methods
                    if (mName.equals("A0N") || mName.equals("A0T") || mName.equals("<clinit>")) continue;
                    if (java.lang.reflect.Modifier.isStatic(m.getModifiers())) continue;
                    try {
                        XposedBridge.hookMethod(m, new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                if (param.thisObject != null && gateway.mailboxConnector != null && gateway.mailboxConnector.get7Da() == null) {
                                    Logger.info("7Da captured from method " + mName + "!");
                                    gateway.mailboxConnector.set7Da(param.thisObject);
                                }
                            }
                        });
                    } catch (Throwable ignored) {}
                }
                Logger.info("Hooked all 7Da methods for instance capture");
            } catch (Throwable t2) {
                Logger.error("Failed to hook 7Da: " + t2.getMessage());
            }
        } catch (Throwable t) {
            mPreferences = null;

            // If any error occurs here the module will disable itself
            Logger.error(t);
            doOnActivitySet(() -> new Handler(Looper.getMainLooper()).postDelayed(() -> {
                showErrorDialog(R.string.failed_load_module);
            }, 500));
        }

        Logger.info("Normal setup finished");
    }

    public void setupHeavy() {
        Logger.info("Starting heavy setup");

        // Check if any of messenger / module version has changed.
        // If so, reset everything and search again for methods
        if (gateway.state.hasOrcaVersionChanged() || gateway.state.hasModuleVersionChanged()) {
            Logger.info("Detected version change");
            new Handler(Looper.getMainLooper()).post(() -> {
                Toast.makeText(getContext(), "New messenger / MPro installation or update detected", Toast.LENGTH_LONG).show();
            });

            // States are only useful for debugging,
            // they appear in Settings > Advanced > Hooks state
            mHookManager.resetStates();

            Logger.info("Unobfuscating components...");
            gateway.initUnobfuscator(getContext(), true);
        } else {
            Logger.info("Messenger version is unchanged, skipping deobfuscation search");

            Logger.info("Unobfuscating components...");
            gateway.initUnobfuscator(getContext(), false);
        }

        mHookManager.inject(gateway, hook -> HookTime.AFTER_DEOBFUSCATION.equals(hook.getHookTime()));

        Logger.info("Heavy setup finished");

        notifyInternalSetupFinished();

        Logger.info("Saving current versions...");
        gateway.state.saveOrcaAndModuleVersion();
    }

    private AlertDialog.Builder buildDialog(@StringRes int title, String message) {
        return new AlertDialog.Builder(getActivity())
                .setTitle(gateway.res.getString(title))
                .setMessage(message)
                .setCancelable(false);
    }

    private AlertDialog.Builder buildDialog(@StringRes int title, @StringRes int message) {
        return buildDialog(title, gateway.res.getString(message));
    }

    private void showDialog(AlertDialog.Builder dialog) {
        try {
            dialog.show();
        } catch (WindowManager.BadTokenException e) {
            Logger.verbose("Failed to show dialog, trying again in 1s");
            new Handler(Looper.getMainLooper()).postDelayed(() -> showDialog(dialog), 1000);
        }
    }

    private void showWelcomeDialog() {
        showDialog(buildDialog(R.string.app_name, R.string.welcome_to_mpro_message)
                .setPositiveButton(android.R.string.ok, (d, i) -> {
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(ModuleInfo.LINK_GITHUB_WIKI_USAGE_GUIDE));
                    getActivity().startActivity(browserIntent);
                    showProgressUntilSetupFinished();
                })
                .setNegativeButton(android.R.string.cancel, (d, i) -> {
                    showProgressUntilSetupFinished();
                }));
    }

    private void showWarningDialog(@StringRes int message) {
        showWarningDialog(gateway.res.getString(message));
    }

    private void showWarningDialog(String message) {
        showDialog(
                buildDialog(R.string.warning_dialog_title, message)
                        .setPositiveButton(android.R.string.ok, (dialogInterface, i) -> {
                        }));
    }

    private void showErrorDialog(@StringRes int message) {
        showErrorDialog(gateway.res.getString(message));
    }

    private void showErrorDialog(String message) {
        showDialog(
            buildDialog(R.string.warning_dialog_title, message)
                    .setPositiveButton(android.R.string.ok, (dialogInterface, i) -> {
                        getActivity().finishAffinity();
                        System.exit(0);
                    }));
    }

    private void showPatreonPopup() {
        showDialog(buildDialog(R.string.app_name, R.string.patreon_popup_message)
                    .setPositiveButton(gateway.res.getString(R.string.take_me), (dialogInterface, i) -> {
                        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(ModuleInfo.LINK_PATREON_PAGE));
                        getActivity().startActivity(browserIntent);
                    })
                    .setNeutralButton(gateway.res.getString(R.string.later), (dialogInterface, i) -> {
                    })
                    .setNegativeButton(gateway.res.getString(R.string.dont_show_again), (dialogInterface, i) -> {
                        gateway.pref.setDoNotDisplayPatreon(true);
                    }));
    }

    private void showProgressUntilSetupFinished() {
        if (!mSetupFinished) {
            ProgressDialog dialog = ProgressDialog.show(getContext(),
                    gateway.res.getString(R.string.deobfuscation_loading_title),
                    gateway.res.getString(R.string.deobfuscation_loading_message));
            doOnSetupFinished(dialog::dismiss);
        }
    }

    @Override
    public void onApplicationCreate(Application application) {
        setApplication(application);
        triggerContextSet(application);

        setupNormal();
        new Thread(this::setupHeavy).start();
    }

    /**
     * Called when Messenger app is launched
     * @param activity Messenger's MainActivity
     */
    @Override
    public void onActivityCreate(final Activity activity) {
        setActivity(activity);

        // Register clipboard listener for video download feature
        DownloadVideoFeature downloadFeature = (DownloadVideoFeature)
                mFeatureManager.getFeature(FeatureId.VIDEO_DOWNLOAD);
        if (downloadFeature != null) {
            downloadFeature.registerClipboardListener(activity);
        }

        // Wait for preferences to be ready before implementing UI-related features
        doOnInternalSetupFinished(() -> {
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (!mUiHooked) {
                    mUiHooked = true;
                    gateway.res.refreshTheme(activity);

                    Logger.info("Injecting UI hooks...");
                    mHookManager.inject(gateway, hook -> HookTime.UI.equals(hook.getHookTime()));

                    // Disabled: exploration hooks not needed for production
                    // Logger.info("Injecting UI exploration hooks...");
                    // OrcaExplorer.exploreUI(gateway, activity);

                    Logger.info("mPreferences = " + (mPreferences!=null));
                    Logger.info("Summoning toolbar...");

                } else {
                    mToolbar.attacher.detach();
                }

                mToolbar = MProToolbar.summon(activity, mPreferences, gateway.res, mFeatureManager,
                        this, mPreferences.getToolbarX(), mPreferences.getToolbarY());
                mToolbar.setVisibility(View.GONE);
                notifySetupFinished();
            }, 500);
        });
    }

    @Override
    public void onActivityDestroy(Activity activity) {
        if (mToolbar != null && mToolbar.attacher != null) {
            try {
                mToolbar.attacher.detach();
            } catch (Throwable t) {
                Logger.error(t);
            }
        }
        if (mActivity.get() == activity) {
            mActivity = new WeakReference<>(null);
        }
    }

    /**
     * Called when Messenger app is resumed
     * @param activity Messenger's MainActivity
     */
    private boolean mFirstResume = true;

    @Override
    public void onActivityResume(Activity activity) {
        doOnSetupFinished(() -> {
            if (gateway.requireThreadKey(false) && gateway.currentThreadKey != null) {
                CharSequence title = activity.getTitle();
                if (title != null) {
                    MessageHistoryStore.updateThreadName(gateway.currentThreadKey, title.toString());
                }
            }

            if (mFirstResume) {
                mFirstResume = false;
                // Trigger pull-to-refresh immediately on first launch
                View rootView = activity.getWindow().getDecorView();
                rootView.post(() -> findAndTriggerRefresh(rootView));
            }

            long timeElapsed = gateway.state.getTimeElapsed("lastOpenP");

            // If 2 days have elapsed, show patreon popup
            if (timeElapsed > 1000 * 3600 * 48 && gateway.pref.getDoNotDisplayPatreon()) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    showPatreonPopup();
                    gateway.state.resetTimeElapsed("lastOpenP");
                });
            }
        });
    }

    /**
     * Called when a special Broadcast is received from automation apps
     * @param context BroadcastReceiver's context
     * @param intent received intent
     * @param action intent's action
     */
    @Override
    public void onReceiveBroadcast(Context context, Intent intent, String action) {
        Logger.info("Received broadcast");

        setReceiverContext(context);

        OrcaBridge.handleIntent(intent, new OrcaBridge.ActionCallback() {
            @Override
            public void sendMessage(String message, String replyMessageId, long threadKey) {
                gateway.doOnMailboxCaptured(() -> {
                    // TODO add option to disable formatting this text

                    List<GenericMessage> messageList = gateway.getMessageParser().parse(message, threadKey, true);
                    for (GenericMessage messageToSend: messageList) {
                        messageToSend.replyMessageId = replyMessageId;
                        getMailbox().sendMessage(messageToSend, threadKey, 100);
                    }
                });
            }

            @Override
            public void reactToMessage(String reaction, String messageId, long threadKey) {
                gateway.doOnMailboxCaptured(() -> {
                    getMailbox().reactToMessage(reaction, messageId, threadKey, 0);
                });
            }

            @Override
            public void reloadPreferences(Map<String, Map<String, ?>> prefMap) {
                MapSharedPreferences.assignMapToSharedPreferences(
                        mPreferences.sp, prefMap.get(StorageConstants.prefName));
                MapSharedPreferences.assignMapToSharedPreferences(
                        gateway.unobfuscator.getPreferences(), prefMap.get(StorageConstants.unobfPrefName));
                MapSharedPreferences.assignMapToSharedPreferences(
                        gateway.state.sp, prefMap.get(StorageConstants.statePrefName));
                gateway.unobfuscator.reloadAll();
                mHookManager.reloadPending(gateway);
                mToolbar.reloadAll();
            }
        });
    }

    @Override
    public void onToolbarPositionChanged(int x, int y) {
        mPreferences.setToolbarPosition(x, y);
    }

    @Override
    public void onCreateContextMenu(Activity activity, ContextMenu menu, View view, ContextMenu.ContextMenuInfo menuInfo) {
        Logger.info("onCreateContextMenu");
    }

    @Override
    public void onLongPress() {
        if (mPreferences.isLongPressOnTopSettingsEnabled())
            mFeatureManager.getFeature(FeatureId.SETTINGS_LAUNCH).executeAction();
    }

    @Override
    public boolean onDispatchTouchEvent(MotionEvent event) {
        mSettingsLongPressDetector.handleTouchEvent(event);
        mMessageLongPressDetector.handleTouchEvent(event);
        return mToolbar.handleTouchEvent(event);
    }

    /**
     * Find FbSwipeRefreshLayout and trigger pull-to-refresh to reload conversation list.
     */
    private void findAndTriggerRefresh(View view) {
        String className = view.getClass().getName();
        
        if (className.contains("SwipeRefresh")) {
            try {
                Class<?> swipeClass = view.getClass().getSuperclass();
                
                // Find obfuscated setRefreshing(boolean) — look for single-boolean-param method
                for (java.lang.reflect.Method m : swipeClass.getDeclaredMethods()) {
                    if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == boolean.class
                            && !m.getName().equals("setEnabled") && !m.getName().equals("setNestedScrollingEnabled")
                            && !m.getName().equals("requestDisallowInterceptTouchEvent")) {
                        m.setAccessible(true);
                        m.invoke(view, true);
                        Logger.info("Triggered setRefreshing via " + m.getName());
                        break;
                    }
                }
                
                // Find OnRefreshListener field — it's an interface with a single void onRefresh() method
                for (java.lang.reflect.Field f : swipeClass.getDeclaredFields()) {
                    Class<?> fieldType = f.getType();
                    if (fieldType.isInterface()) {
                        // OnRefreshListener interface has exactly 1 method: void onRefresh()
                        java.lang.reflect.Method[] ifMethods = fieldType.getDeclaredMethods();
                        if (ifMethods.length == 1 && ifMethods[0].getParameterCount() == 0
                                && ifMethods[0].getReturnType() == void.class) {
                            f.setAccessible(true);
                            Object listener = f.get(view);
                            if (listener != null) {
                                // Call the actual implementation method 
                                String methodName = ifMethods[0].getName();
                                java.lang.reflect.Method implMethod = listener.getClass().getMethod(methodName);
                                implMethod.invoke(listener);
                                Logger.info("Triggered onRefresh via " + methodName + " on " + listener.getClass().getName());
                                return;
                            }
                        }
                    }
                }
            } catch (Throwable e) {
                Logger.info("SwipeRefresh error: " + e.getMessage());
            }
        }

        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                findAndTriggerRefresh(vg.getChildAt(i));
            }
        }
    }

    private void doOnInternalSetupFinished(Runnable runnable) {
        if (mInternalSetupFinished) {
            runnable.run();
        } else {
            mOnInternalSetupFinishedCallback = runnable;
        }
    }

    private void notifyInternalSetupFinished() {
        mInternalSetupFinished = true;
        mOnInternalSetupFinishedCallback.run();
    }

    private void doOnSetupFinished(Runnable runnable) {
        if (mSetupFinished) {
            runnable.run();
        } else {
            mOnSetupFinishedCallbacks.add(runnable);
        }
    }

    private void notifySetupFinished() {
        mSetupFinished = true;
        for (Runnable callback: mOnSetupFinishedCallbacks) {
            callback.run();
        }
        mOnSetupFinishedCallbacks.clear();
    }


    private Application reflectApplication() {
        return (Application) XposedHelpers.callStaticMethod(
                    XposedHelpers.findClass("android.app.ActivityThread", null),
                    "currentApplication"
        );
    }

    private Application getApplication() {
        return mApplication.get();
    }

    private Activity getActivity() {
        return mActivity.get();
    }

    private Context getReceiverContext() {
        return mReceiverContext.get();
    }

    private void setApplication(Application application) {
        Logger.info("Setting Application");
        mApplication = new WeakReference<>(application);
    }

    private void setActivity(Activity activity) {
        Logger.info("Setting Activity");
        mActivity = new WeakReference<>(activity);
        gateway.setActivity(activity);
        if (activity != null) {
            for (Runnable callback: mOnActivitySetCallbacks) {
                callback.run();
            }
            mOnActivitySetCallbacks.clear();
        }
    }

    private void doOnActivitySet(Runnable callback) {
        if (getActivity() == null) {
            mOnActivitySetCallbacks.add(callback);
        } else {
            callback.run();
        }
    }

    private void setReceiverContext(Context context) {
        Logger.info("Setting receiver Context");
        mReceiverContext = new WeakReference<>(context);
    }

    private void triggerContextSet(Context context) {
        if (mContext.get() == null) {
            mContext = new WeakReference<>(context);
            gateway.setContext(context);
        }
    }

    private Context getContext() {
        return mContext.get();
    }

    private MailboxConnector getMailbox() {
        return gateway.mailboxConnector;
    }
}
