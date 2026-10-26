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

    public static final int VISIBLE = 0, INVISIBLE = 4, GONE = 8;
    public String slot;
    private int width;
    private int height;
    private int visibility = VISIBLE;
    private final java.util.List<Runnable> posted = new java.util.ArrayList<>();

    public int getVisibility() { return visibility; }
    public void setVisibility(int value) { visibility = value; }
    public boolean post(Runnable task) { posted.add(task); return true; }
    public void flush() {
        java.util.List<Runnable> batch = new java.util.ArrayList<>(posted);
        posted.clear();
        for (Runnable task : batch) task.run();
    }
    public void layout(int left, int top, int right, int bottom) { setSize(right - left, bottom - top); }
    public void setVisibleState(int state, boolean animate) {}

    public int getWidth() { return width; }

    public int getHeight() { return height; }

    public void setSize(int w, int h) {
        width = w;
        height = h;
    }
}
