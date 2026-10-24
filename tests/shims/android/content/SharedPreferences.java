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
