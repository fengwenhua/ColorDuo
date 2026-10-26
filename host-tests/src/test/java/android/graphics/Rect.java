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

/** Minimal desktop stand-in for {@code android.graphics.Rect}. */
public class Rect {

    public int left;
    public int top;
    public int right;
    public int bottom;

    public Rect() { }

    public void set(int l, int t, int r, int b) {
        left = l; top = t; right = r; bottom = b;
    }

    public int width() { return right - left; }

    public int height() { return bottom - top; }
}
