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

/** Minimal desktop stand-in for the parts of {@code android.view.View} used by {@code TrioRenderer}. */
public class View {

    private int width;
    private int height;

    public int getWidth() { return width; }

    public int getHeight() { return height; }

    public void setSize(int w, int h) {
        width = w;
        height = h;
    }
}
