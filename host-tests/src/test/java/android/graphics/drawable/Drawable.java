/*
 * Copyright (C) 2026 yixing233 and HyperDuo contributors
 *
 * This file is part of ColorDuo, released under the GNU General Public
 * License v3.0. See the LICENSE file at the repository root, or
 * <https://www.gnu.org/licenses/gpl-3.0.html>.
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package android.graphics.drawable;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Rect;

/** The parts of {@code android.graphics.drawable.Drawable} that GlyphDrawable uses. */
public abstract class Drawable {

    private final Rect bounds = new Rect();

    public Rect getBounds() { return bounds; }

    public void setBounds(int l, int t, int r, int b) { bounds.set(l, t, r, b); }

    public void invalidateSelf() { }

    public abstract void draw(Canvas canvas);
    public abstract void setAlpha(int alpha);
    public abstract void setColorFilter(ColorFilter filter);
    public abstract int getOpacity();
}
