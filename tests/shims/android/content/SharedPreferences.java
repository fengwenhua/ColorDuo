/*
 * Copyright (C) 2026 yixing233 and HyperDuo contributors
 *
 * This file is part of ColorDuo, released under the GNU General Public
 * License v3.0. See the LICENSE file at the repository root, or
 * <https://www.gnu.org/licenses/gpl-3.0.html>.
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package android.content;

/** Minimal desktop stand-in for {@code android.content.SharedPreferences}. */
public interface SharedPreferences {

    default java.util.Map<String, ?> getAll() { throw new UnsupportedOperationException("getAll not modelled"); }

    default long getLong(String key, long fallback) {
        Object value = getAll().get(key);
        return value instanceof Long ? (Long) value : fallback;
    }

    default float getFloat(String key, float fallback) { throw new UnsupportedOperationException("getFloat not modelled"); }

    default java.util.Set<String> getStringSet(String key, java.util.Set<String> fallback) {
        throw new UnsupportedOperationException("getStringSet not modelled");
    }

    interface OnSharedPreferenceChangeListener { void onSharedPreferenceChanged(SharedPreferences p, String key); }

    default void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {}

    default void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {}

    boolean getBoolean(String key, boolean defValue);

    int getInt(String key, int defValue);

    /** The ring layout is a string; the real interface has this overload. */
    String getString(String key, String defValue);

    boolean contains(String key);

    interface Editor {
        Editor putBoolean(String key, boolean value);

        Editor putInt(String key, int value);

        Editor putString(String key, String value);

        boolean commit();
    }

    Editor edit();
}
