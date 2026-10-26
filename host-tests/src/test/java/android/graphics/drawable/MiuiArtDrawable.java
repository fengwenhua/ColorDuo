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
import android.graphics.PixelFormat;

/** Stands in for MIUI's own artwork, the thing the slot normally holds. */
public class MiuiArtDrawable extends Drawable {

    @Override public void draw(Canvas canvas) { }

    @Override public void setAlpha(int alpha) { }

    @Override public void setColorFilter(ColorFilter filter) { }

    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
