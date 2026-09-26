package android.content;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class InMemorySharedPreferences implements SharedPreferences {
    private final Map<String, Object> values = new HashMap<>();
    private final List<OnSharedPreferenceChangeListener> listeners = new ArrayList<>();

    @Override
    public synchronized Map<String, ?> getAll() {
        return new HashMap<>(values);
    }

    @Override
    public synchronized String getString(String key, String defValue) {
        Object val = values.get(key);
        return val instanceof String ? (String) val : defValue;
    }

    @Override
    @SuppressWarnings("unchecked")
    public synchronized Set<String> getStringSet(String key, Set<String> defValues) {
        Object val = values.get(key);
        return val instanceof Set ? new HashSet<>((Set<String>) val) : defValues;
    }

    @Override
    public synchronized int getInt(String key, int defValue) {
        Object val = values.get(key);
        return val instanceof Number ? ((Number) val).intValue() : defValue;
    }

    @Override
    public synchronized long getLong(String key, long defValue) {
        Object val = values.get(key);
        return val instanceof Number ? ((Number) val).longValue() : defValue;
    }

    @Override
    public synchronized float getFloat(String key, float defValue) {
        Object val = values.get(key);
        return val instanceof Number ? ((Number) val).floatValue() : defValue;
    }

    @Override
    public synchronized boolean getBoolean(String key, boolean defValue) {
        Object val = values.get(key);
        return val instanceof Boolean ? (Boolean) val : defValue;
    }

    @Override
    public synchronized boolean contains(String key) {
        return values.containsKey(key);
    }

    @Override
    public Editor edit() {
        return new EditorImpl();
    }

    @Override
    public synchronized void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
        if (!listeners.contains(listener)) listeners.add(listener);
    }

    @Override
    public synchronized void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
        listeners.remove(listener);
    }

    private class EditorImpl implements Editor {
        private final Map<String, Object> modified = new HashMap<>();
        private boolean clear = false;

        @Override
        public Editor putString(String key, String value) {
            modified.put(key, value);
            return this;
        }

        @Override
        public Editor putStringSet(String key, Set<String> values) {
            modified.put(key, values != null ? new HashSet<>(values) : null);
            return this;
        }

        @Override
        public Editor putInt(String key, int value) {
            modified.put(key, value);
            return this;
        }

        @Override
        public Editor putLong(String key, long value) {
            modified.put(key, value);
            return this;
        }

        @Override
        public Editor putFloat(String key, float value) {
            modified.put(key, value);
            return this;
        }

        @Override
        public Editor putBoolean(String key, boolean value) {
            modified.put(key, value);
            return this;
        }

        @Override
        public Editor remove(String key) {
            modified.put(key, this); // Marker for removal
            return this;
        }

        @Override
        public Editor clear() {
            clear = true;
            return this;
        }

        @Override
        public boolean commit() {
            apply();
            return true;
        }

        @Override
        public void apply() {
            synchronized (InMemorySharedPreferences.this) {
                if (clear) {
                    values.clear();
                }
                for (Map.Entry<String, Object> entry : modified.entrySet()) {
                    if (entry.getValue() == this) {
                        values.remove(entry.getKey());
                    } else if (entry.getValue() == null) {
                        values.remove(entry.getKey());
                    } else {
                        values.put(entry.getKey(), entry.getValue());
                    }
                    for (OnSharedPreferenceChangeListener listener : listeners) {
                        listener.onSharedPreferenceChanged(InMemorySharedPreferences.this, entry.getKey());
                    }
                }
            }
        }
    }
}
