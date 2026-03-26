package tn.amin.mpro2.preference;

import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class MapSharedPreferences implements SharedPreferences {
    private final HashMap<String, Object> mMap;
    private final Set<OnSharedPreferenceChangeListener> mListeners = new HashSet<>();

    public final SharedPreferences.Editor fileEditor;

    public MapSharedPreferences(Map<String, ?> map, SharedPreferences.Editor fileSpEditor) {
        mMap = new HashMap<>(map);
        fileEditor = fileSpEditor;
    }

    public MapSharedPreferences(SharedPreferences sharedPreferences) {
        this(sharedPreferences.getAll(), sharedPreferences.edit());
    }

    @Override
    public Map<String, ?> getAll() {
        return mMap;
    }

    @Nullable
    @Override
    public String getString(String key, @Nullable String defaultValue) {
        return getObject(key, defaultValue);
    }

    @Nullable
    @Override
    public Set<String> getStringSet(String key, @Nullable Set<String> defaultValue) {
        return getObject(key, defaultValue);
    }

    @Override
    public int getInt(String key, int defaultValue) {
        return getObject(key, defaultValue);
    }

    @Override
    public long getLong(String key, long defaultValue) {
        return getObject(key, defaultValue);
    }

    @Override
    public float getFloat(String key, float defaultValue) {
        return getObject(key, defaultValue);
    }

    @Override
    public boolean getBoolean(String key, boolean defaultValue) {
        return getObject(key, defaultValue);
    }

    private <T> T getObject(String key, T defaultValue) {
        Object o = mMap.get(key);
        if (o != null) {
            return (T) o;
        } else {
            return defaultValue;
        }
    }

    @Override
    public boolean contains(String key) {
        return mMap.containsKey(key);
    }

    @Override
    public Editor edit() {
        return new Editor();
    }

    @Override
    public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
        if (listener != null) {
            mListeners.add(listener);
        }
    }

    @Override
    public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
        if (listener != null) {
            mListeners.remove(listener);
        }
    }

    private void notifyListeners(Set<String> changedKeys) {
        if (changedKeys.isEmpty() || mListeners.isEmpty()) {
            return;
        }

        Set<OnSharedPreferenceChangeListener> listenerSnapshot = new HashSet<>(mListeners);
        for (String key : changedKeys) {
            for (OnSharedPreferenceChangeListener listener : listenerSnapshot) {
                listener.onSharedPreferenceChanged(this, key);
            }
        }
    }

    public class Editor implements SharedPreferences.Editor {
        private final Set<String> mChangedKeys = new HashSet<>();

        @Override
        public SharedPreferences.Editor putString(String key, @Nullable String value) {
            mMap.put(key, value);
            mChangedKeys.add(key);
            if (fileEditor != null) {
                fileEditor.putString(key, value);
            }
            return this;
        }

        @Override
        public SharedPreferences.Editor putStringSet(String key, @Nullable Set<String> values) {
            mMap.put(key, values);
            mChangedKeys.add(key);
            if (fileEditor != null) {
                fileEditor.putStringSet(key, values);
            }
            return this;
        }

        @Override
        public SharedPreferences.Editor putInt(String key, int value) {
            mMap.put(key, value);
            mChangedKeys.add(key);
            if (fileEditor != null) {
                fileEditor.putInt(key, value);
            }
            return this;
        }

        @Override
        public SharedPreferences.Editor putLong(String key, long value) {
            mMap.put(key, value);
            mChangedKeys.add(key);
            if (fileEditor != null) {
                fileEditor.putLong(key, value);
            }
            return this;
        }

        @Override
        public SharedPreferences.Editor putFloat(String key, float value) {
            mMap.put(key, value);
            mChangedKeys.add(key);
            if (fileEditor != null) {
                fileEditor.putFloat(key, value);
            }
            return this;
        }

        @Override
        public SharedPreferences.Editor putBoolean(String key, boolean value) {
            mMap.put(key, value);
            mChangedKeys.add(key);
            if (fileEditor != null) {
                fileEditor.putBoolean(key, value);
            }
            return this;
        }

        @Override
        public SharedPreferences.Editor remove(String key) {
            mMap.remove(key);
            mChangedKeys.add(key);
            if (fileEditor != null) {
                fileEditor.remove(key);
            }
            return this;
        }

        @Override
        public SharedPreferences.Editor clear() {
            mChangedKeys.addAll(mMap.keySet());
            mMap.clear();
            if (fileEditor != null) {
                fileEditor.clear();
            }
            return this;
        }

        @Override
        public boolean commit() {
            boolean committed = true;
            if (fileEditor != null) {
                committed = fileEditor.commit();
            }
            if (committed) {
                notifyListeners(new HashSet<>(mChangedKeys));
                mChangedKeys.clear();
            }
            return committed;
        }

        @Override
        public void apply() {
            if (fileEditor != null) {
                fileEditor.apply();
            }
            notifyListeners(new HashSet<>(mChangedKeys));
            mChangedKeys.clear();
        }
    }

    @SuppressWarnings("unchecked")
    public static void assignMapToSharedPreferences(SharedPreferences sharedPreferences, Map<String, ?> prefMap) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        for (Map.Entry<String, ?> entry : prefMap.entrySet()) {
            if (entry.getValue() instanceof String) {
                editor.putString(entry.getKey(), (String) entry.getValue());
            } else if (entry.getValue() instanceof Integer) {
                editor.putInt(entry.getKey(), (Integer) entry.getValue());
            } else if (entry.getValue() instanceof Long) {
                editor.putLong(entry.getKey(), (Long) entry.getValue());
            } else if (entry.getValue() instanceof Float) {
                editor.putFloat(entry.getKey(), (Float) entry.getValue());
            } else if (entry.getValue() instanceof Boolean) {
                editor.putBoolean(entry.getKey(), (Boolean) entry.getValue());
            } else if (entry.getValue() instanceof Set) {
                editor.putStringSet(entry.getKey(), (Set<String>) entry.getValue());
            }
        }
        editor.apply();
    }
}
