/*
 * Copyright (C) 2026 yixing233 and HyperDuo contributors
 *
 * This file is part of ColorDuo, released under the GNU General Public
 * License v3.0. See the LICENSE file at the repository root, or
 * <https://www.gnu.org/licenses/gpl-3.0.html>.
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package android.os;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Desktop stand-in for {@code android.os.Bundle}.
 *
 * <p>Only the calls {@link io.github.fengwenhua.colorduo.TrioSettings} makes on the way in
 * and out are replayed here: the broadcast-extras path is not exercised by the
 * offline preview, it just has to compile so the real {@code TrioSettings} can
 * be linked against the desktop shims.
 */
public class Bundle {

    private final Map<String, Object> values = new HashMap<>();

    public void putBoolean(String key, boolean value) {
        values.put(key, value);
    }

    public void putInt(String key, int value) {
        values.put(key, value);
    }

    /**
     * Strings are part of the real Bundle, and the ring layout is now stored as one.
     *
     * <p>Adding it here matters beyond compiling: the layout's round trip through the
     * broadcast extras is asserted by the harness, and a shim that silently dropped strings
     * would let that assertion pass against an empty layout.
     */
    public void putString(String key, String value) {
        values.put(key, value);
    }

    public boolean getBoolean(String key, boolean defValue) {
        Object v = values.get(key);
        return v instanceof Boolean ? (Boolean) v : defValue;
    }

    public int getInt(String key, int defValue) {
        Object v = values.get(key);
        return v instanceof Integer ? (Integer) v : defValue;
    }

    public String getString(String key, String defValue) {
        Object v = values.get(key);
        return v instanceof String ? (String) v : defValue;
    }

    public boolean containsKey(String key) {
        return values.containsKey(key);
    }

    public Set<String> keySet() {
        return values.keySet();
    }
}
