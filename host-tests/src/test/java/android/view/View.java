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

import android.graphics.Canvas;
import android.graphics.drawable.Drawable;

/** Shared host protocol; optional fields/methods are declared only by concrete shapes. */
public class View {
    public boolean mUseLegacyDrawable;
    public int clearTintCalls, darkCalls, nativeArt;
    public boolean tinted = true;
    public void onDarkChangeInternal() { darkCalls++; tinted = true; }
    public Object getContext() { return null; }
    public int getWidth() { return 84; }
    public int getHeight() { return 60; }
    public void nativeDraw(Canvas canvas) {
        Object value = null;
        try { value = getClass().getField("mTintBgDrawable").get(this); }
        catch (ReflectiveOperationException ignored) {}
        if (!mUseLegacyDrawable && value instanceof Drawable) {
            Drawable drawable = (Drawable) value;
            if (!drawable.getClass().getSimpleName().equals("GlyphDrawable")) nativeArt++;
            drawable.setBounds(0, 0, getWidth(), getHeight());
            drawable.draw(canvas);
        } else { nativeArt++; }
    }
}
