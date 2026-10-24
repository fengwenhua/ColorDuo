/*
 * Copyright (C) 2026 yixing233 and HyperDuo contributors
 *
 * This file is part of ColorDuo, released under the GNU General Public
 * License v3.0. See the LICENSE file at the repository root, or
 * <https://www.gnu.org/licenses/gpl-3.0.html>.
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package android.graphics;

/** Minimal desktop stand-in for {@code android.graphics.RectF}. */
public class RectF {

    public float left;
    public float top;
    public float right;
    public float bottom;

    public RectF() {
    }

    public RectF(float l, float t, float r, float b) {
        set(l, t, r, b);
    }

    public void set(float l, float t, float r, float b) {
        left = l;
        top = t;
        right = r;
        bottom = b;
    }

    public float width() { return right - left; }

    public float height() { return bottom - top; }
}
