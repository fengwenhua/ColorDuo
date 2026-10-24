/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.yixing233.hyperduo;

import android.content.SharedPreferences;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Stable getAll() snapshot so a multi-field read cannot span two local editor commits. */
final class PreferencesSnapshot implements SharedPreferences {
    private final Map<String, ?> values;
    PreferencesSnapshot(SharedPreferences source) {
        values = Collections.unmodifiableMap(new HashMap<>(source.getAll()));
    }
    @Override public Map<String, ?> getAll() { return values; }
    @Override public boolean contains(String key) { return values.containsKey(key); }
    @Override public boolean getBoolean(String key, boolean fallback) {
        Object value = values.get(key); return value instanceof Boolean ? (Boolean) value : fallback;
    }
    @Override public int getInt(String key, int fallback) {
        Object value = values.get(key); return value instanceof Integer ? (Integer) value : fallback;
    }
    @Override public long getLong(String key, long fallback) {
        Object value = values.get(key); return value instanceof Long ? (Long) value : fallback;
    }
    @Override public float getFloat(String key, float fallback) {
        Object value = values.get(key); return value instanceof Float ? (Float) value : fallback;
    }
    @Override public String getString(String key, String fallback) {
        Object value = values.get(key); return value instanceof String ? (String) value : fallback;
    }
    @Override public Set<String> getStringSet(String key, Set<String> fallback) {
        Object value = values.get(key);
        if (!(value instanceof Set<?>)) return fallback;
        Set<String> result = new HashSet<>();
        for (Object item : (Set<?>) value) if (item instanceof String) result.add((String) item);
        return Collections.unmodifiableSet(result);
    }
    @Override public Editor edit() { throw new UnsupportedOperationException("snapshot"); }
    @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
        throw new UnsupportedOperationException("snapshot");
    }
    @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {}
}
