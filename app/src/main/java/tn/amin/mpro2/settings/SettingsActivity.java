package tn.amin.mpro2.settings;

import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.TextView;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.preference.DropDownPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreference;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import tn.amin.mpro2.R;
import tn.amin.mpro2.debug.Logger;
import tn.amin.mpro2.features.util.biometric.ConversationLock;
import tn.amin.mpro2.file.StorageConstants;
import tn.amin.mpro2.messaging.history.MessageHistoryStore;
import tn.amin.mpro2.orca.OrcaBridge;
import tn.amin.mpro2.preference.MapSharedPreferences;
import tn.amin.mpro2.settings.history.HistoryDialogs;
import tn.amin.mpro2.settings.hookstate.HookStateFragment;

public class SettingsActivity extends AppCompatActivity {
    private MaterialToolbar mToolbar;
    private ExtendedFloatingActionButton mFab;
    private Map<String, SharedPreferences> mSharedPreferences;
    private boolean mHasPendingChanges;

    private final SharedPreferences.OnSharedPreferenceChangeListener mChangeListener = (sharedPreferences, key) -> setPendingChanges(true);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        if (!getIntent().hasExtra("mpro_pref")) {
            Toast.makeText(this, "Please launch settings from messenger toolbar", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        applyHistorySnapshotFromIntent(getIntent());

        @SuppressWarnings("unchecked")
        Map<String, Object> normalPrefMap = (Map<String, Object>) getIntent().getSerializableExtra(StorageConstants.prefName);
        @SuppressWarnings("unchecked")
        Map<String, Object> unobfPrefMap = (Map<String, Object>) getIntent().getSerializableExtra(StorageConstants.unobfPrefName);
        @SuppressWarnings("unchecked")
        Map<String, Object> statePrefMap = (Map<String, Object>) getIntent().getSerializableExtra(StorageConstants.statePrefName);

        SharedPreferences normalSharedPreferences = new MapSharedPreferences(normalPrefMap, null);
        SharedPreferences unobfuscatorSharedPreferences = new MapSharedPreferences(unobfPrefMap, null);
        SharedPreferences stateSharedPreferences = new MapSharedPreferences(statePrefMap, null);

        Map<String, SharedPreferences> sharedPreferences = new HashMap<>();
        sharedPreferences.put(StorageConstants.prefName, normalSharedPreferences);
        sharedPreferences.put(StorageConstants.unobfPrefName, unobfuscatorSharedPreferences);
        sharedPreferences.put(StorageConstants.statePrefName, stateSharedPreferences);
        mSharedPreferences = sharedPreferences;

        for (SharedPreferences pref : mSharedPreferences.values()) {
            pref.registerOnSharedPreferenceChangeListener(mChangeListener);
        }

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.settings, new SettingsFragment(sharedPreferences, SettingsType.ROOT))
                .commit();

        mToolbar = findViewById(R.id.toolbar);
        setSupportActionBar(mToolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        updateToolbarFor(SettingsType.ROOT);

        mFab = findViewById(R.id.fab);
        setPendingChanges(false);
        mFab.setOnClickListener(o -> {
            applyChanges();
            finish();
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent == null) {
            return;
        }

        setIntent(intent);
        applyHistorySnapshotFromIntent(intent);
    }

    @SuppressWarnings("unchecked")
    private void applyHistorySnapshotFromIntent(Intent intent) {
        ArrayList<String> historyLines = intent.getStringArrayListExtra(StorageConstants.historyLinesExtra);
        Map<String, String> historyThreadNames = null;
        Object historyThreadNamesObj = intent.getSerializableExtra(StorageConstants.historyThreadNamesExtra);
        if (historyThreadNamesObj instanceof Map) {
            historyThreadNames = (Map<String, String>) historyThreadNamesObj;
        }
        MessageHistoryStore.setInMemorySnapshot(historyLines, historyThreadNames);
    }

    @Override
    protected void onDestroy() {
        if (mSharedPreferences != null) {
            for (SharedPreferences pref : mSharedPreferences.values()) {
                pref.unregisterOnSharedPreferenceChangeListener(mChangeListener);
            }
        }
        MessageHistoryStore.clearInMemorySnapshot();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
            getSupportFragmentManager().popBackStack();
            return;
        }

        attemptExit();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                if (getSupportFragmentManager().getBackStackEntryCount() > 0)
                    getSupportFragmentManager().popBackStack();
                else
                    attemptExit();
                break;
        }
        return true;
    }

    public void updateToolbarFor(SettingsType type) {
        switch (type) {
            case ROOT:
                updateToolbar(R.string.title_activity_settings, R.string.settings_subtitle_root);
                break;
            case FEATURES:
                updateToolbar(R.string.pref_features, R.string.pref_features_summary);
                break;
            case AI_CONFIG:
                updateToolbar(R.string.pref_aiconfig, R.string.settings_subtitle_ai);
                break;
            case TOOLBAR:
                updateToolbar(R.string.pref_toolbar, R.string.pref_toolbar_summary);
                break;
            case ADVANCED:
                updateToolbar(R.string.pref_advanced, R.string.pref_advanced_summary);
                break;
            case CHAT_HISTORY:
                updateToolbar(R.string.pref_chat_history, R.string.chat_history_threads_subtitle);
                break;
            case UNOBFUSCATOR:
                updateToolbar(R.string.pref_unobfuscator, R.string.pref_unobfuscator_summary);
                break;
            case API_CODES:
                updateToolbar(R.string.pref_apicodes, R.string.pref_apicodes_summary);
                break;
            case HOOK_STATE:
                updateToolbar(R.string.pref_hookstate, R.string.pref_hookstate_summary);
                break;
        }
    }

    private void updateToolbar(@StringRes int title, @StringRes int subtitle) {
        if (mToolbar == null) {
            return;
        }

        mToolbar.setTitle(title);
        mToolbar.setSubtitle(subtitle);
    }

    public void updateToolbarText(CharSequence title, CharSequence subtitle) {
        if (mToolbar == null) {
            return;
        }

        mToolbar.setTitle(title);
        mToolbar.setSubtitle(subtitle);
    }

    public void setApplyButtonVisible(boolean visible) {
        if (mFab == null) {
            return;
        }

        mFab.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void applyChanges() {
        if (mSharedPreferences == null) {
            return;
        }

        Map<String, Map<String, ?>> allPreferences = new HashMap<>();
        for (Map.Entry<String, SharedPreferences> entry : mSharedPreferences.entrySet()) {
            allPreferences.put(entry.getKey(), entry.getValue().getAll());
        }

        OrcaBridge.reloadPreferences(this, allPreferences);
        setPendingChanges(false);
    }

    private void attemptExit() {
        if (!mHasPendingChanges) {
            finish();
            return;
        }

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_settings_exit, null, false);
        TextView titleView = dialogView.findViewById(R.id.dialog_title);
        TextView messageView = dialogView.findViewById(R.id.dialog_message);

        titleView.setText(R.string.settings_unsaved_title);
        messageView.setText(R.string.settings_unsaved_message);

        new MaterialAlertDialogBuilder(this)
                .setIcon(R.drawable.ic_toolbar_settings)
                .setView(dialogView)
                .setNegativeButton(R.string.settings_keep_editing, null)
                .setNeutralButton(R.string.settings_discard, (dialog, which) -> finish())
                .setPositiveButton(R.string.settings_apply_and_exit, (dialog, which) -> {
                    applyChanges();
                    finish();
                })
                .show();
    }

    private void setPendingChanges(boolean hasPendingChanges) {
        mHasPendingChanges = hasPendingChanges;
        if (mFab == null) {
            return;
        }

        mFab.setEnabled(hasPendingChanges);
        mFab.setAlpha(hasPendingChanges ? 1f : 0.7f);
        mFab.setText(hasPendingChanges ? R.string.settings_apply_changes : R.string.settings_up_to_date);
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        private final SettingsType mSettingsType;
        private final Map<String, SharedPreferences> mSharedPreferences;
        private final String mActiveSharedPreferencesName;

        public SettingsFragment(Map<String, SharedPreferences> sharedPreferences, SettingsType settingsType) {
            mSharedPreferences = sharedPreferences;
            mSettingsType = settingsType;

            switch (settingsType) {
                case UNOBFUSCATOR:
                case API_CODES:
                    mActiveSharedPreferencesName = StorageConstants.unobfPrefName;
                    break;
                case HOOK_STATE:
                    mActiveSharedPreferencesName = StorageConstants.statePrefName;
                    break;
                default:
                    mActiveSharedPreferencesName = StorageConstants.prefName;
                    break;
            }
        }

        private void assignSharedPreferences(PreferenceManager manager) {
            try {
                Field sharedPreferencesField = PreferenceManager.class.getDeclaredField("mSharedPreferences");
                sharedPreferencesField.setAccessible(true);
                sharedPreferencesField.set(manager, getActiveSharedPreferences());
            } catch (Throwable t) {
                Logger.error(t);
            }
        }

        private SharedPreferences getActiveSharedPreferences() {
            return mSharedPreferences.get(mActiveSharedPreferencesName);
        }

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        }

        @Override
        public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);

            RecyclerView listView = getListView();
            listView.setClipToPadding(false);
            listView.setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(96));
        }

        @Override
        public void onResume() {
            super.onResume();
            if (getActivity() instanceof SettingsActivity) {
                ((SettingsActivity) getActivity()).updateToolbarFor(mSettingsType);
            }
        }

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            PreferenceManager manager = getPreferenceManager();
            assignSharedPreferences(manager);

            switch (mSettingsType) {
                case ROOT:
                    displayRootSettings();
                    break;

                case FEATURES:
                    displayFeaturesSettings();
                    break;

                case AI_CONFIG:
                    displayAiConfigSettings();
                    break;

                case TOOLBAR:
                    displayToolbarSettings();
                    break;

                case ADVANCED:
                    displayAdvancedSettings();
                    break;

                case UNOBFUSCATOR:
                    displayUnobfuscatorSettings();
                    break;

                case API_CODES:
                    displayApiCodesSettings();
                    break;

                case HOOK_STATE:
                    // another Fragment is used
                    break;
            }
        }

        private void displayApiCodesSettings() {
            addPreferencesFromResource(R.xml.preferences_apicodes);
        }

        private void displayUnobfuscatorSettings() {
            addPreferencesFromResource(R.xml.preferences_unobfuscator);
        }

        private void displayAdvancedSettings() {
            addPreferencesFromResource(R.xml.preferences_advanced);

            linkPreferenceToFragment("mpro_advanced_unobfuscator", SettingsType.UNOBFUSCATOR, "fragUnobfuscator");
            linkPreferenceToFragment("mpro_advanced_apicodes", SettingsType.API_CODES, "fragApiCodes");
            linkPreferenceToFragment("mpro_advanced_hookstate", new HookStateFragment(mSharedPreferences.get(StorageConstants.statePrefName)), "fragHookState", SettingsType.HOOK_STATE);

            Preference chatHistory = Objects.requireNonNull(findPreference("mpro_advanced_chat_history"));
            chatHistory.setOnPreferenceClickListener(preference -> {
                HistoryDialogs.showThreadsDialog(requireContext());
                return true;
            });
        }

        private void displayToolbarSettings() {
            addPreferencesFromResource(R.xml.preferences_toolbar);

            DropDownPreference preferenceFingers = Objects.requireNonNull(findPreference("mpro_toolbar_summon_fingers"));
            SwitchPreference preferenceFromEdge = Objects.requireNonNull(findPreference("mpro_toolbar_summon_edge"));
            Preference.OnPreferenceChangeListener preferenceFingersListener = (preference, newValue) -> {
                if ("1".equals(newValue)) {
                    preferenceFromEdge.setChecked(true);
                    preferenceFromEdge.setEnabled(false);
                    preferenceFromEdge.setSummary(R.string.use_multi_finger_to_disable);
                } else {
                    preferenceFromEdge.setEnabled(true);
                    preferenceFromEdge.setSummary(null);
                }

                return true;
            };
            preferenceFingers.setOnPreferenceChangeListener(preferenceFingersListener);
            preferenceFingersListener.onPreferenceChange(preferenceFingers, preferenceFingers.getValue());
        }

        public void displayFeaturesSettings() {
            addPreferencesFromResource(R.xml.preferences_features);

            SwitchPreference enableConversationLockPreference = Objects.requireNonNull(findPreference("mpro_conversation_lock"));
            enableConversationLockPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                        ConversationLock lock = new ConversationLock();
                        lock.promptAuthentication(getContext(), () -> {
                            enableConversationLockPreference.setChecked((Boolean) newValue);
                        }, ()->{}, true);

                        return false;
                    });

            // Disabled: color theme feature removed for Messenger 553+
            // SwitchPreference customThemePreference = Objects.requireNonNull(findPreference("mpro_ui_color_theme_enable"));
            // customThemePreference.setOnPreferenceChangeListener((preference, newValue) -> {
            //             if (!(Boolean) newValue) {
            //                 getActiveSharedPreferences().edit()
            //                         .putInt("mpro_ui_color_theme", 0)
            //                         .apply();
            //             }
            //             return true;
            //         });

            linkPreferenceToFragment("mpro_commands_ai_config", SettingsType.AI_CONFIG, "fragAiConfig");

            SwitchPreference commandsEnabled = Objects.requireNonNull(findPreference("mpro_commands"));
            Preference commandsInput = Objects.requireNonNull(findPreference("mpro_commands_send_input"));
            ListPreference commandsAllowOther = Objects.requireNonNull(findPreference("mpro_commands_allow_other"));
            Preference aiConfig = Objects.requireNonNull(findPreference("mpro_commands_ai_config"));

            Preference.OnPreferenceChangeListener commandsToggleListener = (preference, newValue) -> {
                boolean enabled = (Boolean) newValue;
                setCommandsSubPreferencesEnabled(enabled, commandsInput, commandsAllowOther, aiConfig);
                return true;
            };
            commandsEnabled.setOnPreferenceChangeListener(commandsToggleListener);
            setCommandsSubPreferencesEnabled(commandsEnabled.isChecked(), commandsInput, commandsAllowOther, aiConfig);
        }

        private void displayAiConfigSettings() {
            addPreferencesFromResource(R.xml.preferences_aiconfig);
        }

        private void displayRootSettings() {
            addPreferencesFromResource(R.xml.preferences_root);

            linkPreferenceToFragment("mpro_root_features", SettingsType.FEATURES, "fragPreferences");
            linkPreferenceToFragment("mpro_root_toolbar", SettingsType.TOOLBAR, "fragToolbar");
            linkPreferenceToFragment("mpro_root_advanced", SettingsType.ADVANCED, "fragAdvanced");
            linkPreferenceToActivity("mpro_root_about", new ComponentName("tn.amin.mpro2", "tn.amin.mpro2.AboutActivity"));
        }

        private void setCommandsSubPreferencesEnabled(boolean enabled, Preference commandsInput, ListPreference commandsAllowOther, Preference aiConfig) {
            commandsInput.setEnabled(enabled);
            commandsAllowOther.setEnabled(enabled);
            aiConfig.setEnabled(enabled);
        }

        private void linkPreferenceToFragment(String key, Fragment fragment, String backStackName) {
            linkPreferenceToFragment(key, fragment, backStackName, null);
        }

        private void linkPreferenceToFragment(String key, Fragment fragment, String backStackName, @Nullable SettingsType targetType) {
            Preference targetPreference = findPreference(key);
            Objects.requireNonNull(targetPreference)
                    .setOnPreferenceClickListener(preference -> {
                        if (targetType != null && getActivity() instanceof SettingsActivity) {
                            ((SettingsActivity) getActivity()).updateToolbarFor(targetType);
                        }

                        getParentFragmentManager()
                                .beginTransaction()
                                .replace(R.id.settings, fragment)
                                .addToBackStack(backStackName)
                                .commit();
                        return true;
                    });
        }

        private void linkPreferenceToFragment(String key, SettingsType type, String backStackName) {
            linkPreferenceToFragment(key, new SettingsFragment(mSharedPreferences, type), backStackName, type);
        }

        private void linkPreferenceToActivity(String key, ComponentName componentName) {
            Preference targetPreference = findPreference(key);
            Objects.requireNonNull(targetPreference)
                    .setOnPreferenceClickListener(preference -> {
                        Intent intent = new Intent();
                        intent.setComponent(componentName);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                        requireContext().startActivity(intent);
                        return true;
                    });
        }

        private int dpToPx(int dp) {
            return Math.round(dp * requireContext().getResources().getDisplayMetrics().density);
        }
    }
}