/*
 * Copyright (C) 2026 yixing233 and HyperDuo contributors
 *
 * This file is part of ColorDuo, released under the GNU General Public
 * License v3.0. See the LICENSE file at the repository root, or
 * <https://www.gnu.org/licenses/gpl-3.0.html>.
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package android.view;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.MiuiArtDrawable;

/** Each class has a different real reflection surface, matching the five historical host shapes. */
public final class NativeHosts {
    private NativeHosts() {}
    public static View create(String shape) {
        return switch (shape) {
            case "full" -> new Full();
            case "legacy" -> new Legacy();
            case "noclear" -> new NoClear();
            case "nonpublic" -> new NonPublicClear();
            case "nofield" -> new NoField();
            default -> throw new IllegalArgumentException(shape);
        };
    }
    public static class Full extends View {
        public Drawable mTintBgDrawable;
        public void clearTint() { clearTintCalls++; tinted = false; mTintBgDrawable = new MiuiArtDrawable(); }
    }
    public static final class Legacy extends Full { public Legacy() { mUseLegacyDrawable = true; } }
    public static final class NoClear extends View { public Drawable mTintBgDrawable; }
    public static final class NonPublicClear extends View {
        public Drawable mTintBgDrawable;
        void clearTint() { clearTintCalls++; tinted = false; mTintBgDrawable = new MiuiArtDrawable(); }
    }
    public static final class NoField extends View {}
}
