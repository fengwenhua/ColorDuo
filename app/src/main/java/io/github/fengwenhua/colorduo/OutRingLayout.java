/*
 * Copyright (C) 2026 yixing233 and HyperDuo contributors
 *
 * This file is part of ColorDuo, released under the GNU General Public
 * License v3.0. See the LICENSE file at the repository root, or
 * <https://www.gnu.org/licenses/gpl-3.0.html>.
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.fengwenhua.colorduo;

/** One logical strip for signal, performance and type, ordered from the battery. */
final class OutRingLayout {
    private OutRingLayout() { }

    static final class Plan {
        /** Scale applied to the surviving views. 1 unless even the highest priority overflows. */
        final float scale;
        /** Start-side coordinate of each surviving view, in row pixels from the logical start. */
        final float[] near;
        /** Per-slot width to lay out, or 0 for a slot that was dropped this frame. */
        final int[] widths;
        /** Total width the surviving views want, before {@link #scale}. */
        final int total;
        /** Pixels to reserve at the row's start for the surviving views. */
        final int reserve;
        /** Which slots were dropped, so callers can tell "nothing there" from "no room". */
        final boolean[] dropped;

        Plan(float scale, float[] near, int[] widths, int total, boolean[] dropped) {
            this.scale = scale;
            this.near = near;
            this.widths = widths;
            this.total = total;
            this.dropped = dropped;
            this.reserve = (int) Math.ceil(total * scale);
        }
    }

    /**
     * Coordinates increase from the row's logical start, before RTL mirroring.
     *
     * <p>The island value is a start-side exclusion boundary, never a translation.
     *
     * <p><b>When the strip does not fit, items are dropped, not shrunk.</b> An earlier
     * version scaled all three together, which kept them on screen but made the readings
     * illegible the moment a wide notification island appeared - the font is a fixed dp
     * size, so scaling it is the one thing that cannot be absorbed. Dropping keeps every
     * surviving view at its designed size and only costs the least important slot.
     *
     * <p><b>Array order is POSITION, and the sacrifice order is passed separately.</b> Those
     * were one thing while the strip had a fixed arrangement: index 0 was both nearest the
     * battery and the most important. The arrangement is now a setting, so the two questions
     * have to be asked separately - putting a reading first says where the user wants to read
     * it, not that it should outlive the others in a narrow row. {@code dropOrder} is the
     * priority answer: the indices to give up, least important first. The four-argument-list
     * overload passes null and keeps the old behaviour exactly.
     *
     * <p>When even the highest priority slot cannot fit, it is scaled as a last resort:
     * showing it small beats showing nothing.
     */
    static Plan solve(int rowWidth, int paddingStart, int paddingEnd,
                      int islandBoundary, int availableHeight,
                      int[] widths, int[] heights, int[] gaps, int outwardGap) {
        return solve(rowWidth, paddingStart, paddingEnd, islandBoundary, availableHeight,
                widths, heights, gaps, outwardGap, null);
    }

    /**
     * The same, with the sacrifice order named explicitly.
     *
     * @param dropOrder indices to drop, least important first; null means "from the last
     *   index backwards", which is what every caller got before the arrangement was
     *   settable. An index it does not name is only chosen once everything it names is gone,
     *   so a partial list still terminates.
     */
    static Plan solve(int rowWidth, int paddingStart, int paddingEnd,
                      int islandBoundary, int availableHeight,
                      int[] widths, int[] heights, int[] gaps, int outwardGap,
                      int[] dropOrder) {
        if (widths.length != heights.length || widths.length != gaps.length) {
            throw new IllegalArgumentException("strip metrics have different lengths");
        }
        final float end = Math.max(0, rowWidth - Math.max(0, paddingEnd));
        final float floor = Math.max(Math.max(0, paddingStart), islandBoundary);
        final float room = Math.max(0, end - floor);
        final float height = Math.max(0, availableHeight);

        final boolean[] dropped = new boolean[widths.length];
        // Only slots that have something to draw can be dropped; an empty slot is not a
        // victim, it simply contributes nothing.
        for (int i = 0; i < widths.length; i++) {
            dropped[i] = widths[i] <= 0;
        }

        int total;
        int tallest;
        while (true) {
            total = Math.max(0, outwardGap);
            tallest = 0;
            for (int i = 0; i < widths.length; i++) {
                if (!dropped[i]) {
                    total += widths[i] + Math.max(0, gaps[i]);
                    tallest = Math.max(tallest, heights[i]);
                }
            }
            // Height is a separate axis and cannot be fixed by dropping a sibling: all three
            // views are about one row tall, so dropping one would not make the rest shorter.
            final boolean fitsWidth = total <= room;
            final boolean fitsHeight = tallest <= height;
            if ((fitsWidth && fitsHeight) || dropLowest(dropped, dropOrder)) {
                break;
            }
        }

        // Last resort: the only surviving slot is wider or taller than the row allows.
        float scale = 1f;
        if (total > 0) {
            scale = Math.min(scale, room / total);
        }
        if (tallest > 0) {
            scale = Math.min(scale, height / tallest);
        }
        scale = Math.max(0f, Math.min(1f, scale));

        final int[] layoutWidths = new int[widths.length];
        final float[] near = new float[widths.length];
        float cursor = end;
        for (int i = 0; i < widths.length; i++) {
            layoutWidths[i] = dropped[i] ? 0 : widths[i];
        }
        for (int i = 0; i < layoutWidths.length; i++) {
            if (layoutWidths[i] > 0) {
                cursor -= Math.max(0, gaps[i]) * scale;
                near[i] = cursor;
                cursor -= layoutWidths[i] * scale;
            }
        }
        return new Plan(scale, near, layoutWidths, total, dropped);
    }

    /**
     * Drops the least important surviving slot and reports whether it dropped anything.
     *
     * <p>Walks {@code dropOrder} in the order it is given - least important first - and takes
     * the first slot still standing. With no order to go by it falls back to scanning from
     * the end, which is the fixed arrangement's rule and still the right default: the
     * static type label has always been the cheapest thing to lose.
     */
    private static boolean dropLowest(boolean[] dropped, int[] dropOrder) {
        if (dropOrder != null) {
            for (int index : dropOrder) {
                if (index >= 0 && index < dropped.length && !dropped[index]) {
                    dropped[index] = true;
                    return true;
                }
            }
            // Everything named is already gone; fall through to the positional rule so a
            // short or partial order cannot leave the loop unable to make progress.
        }
        for (int i = dropped.length - 1; i >= 0; i--) {
            if (!dropped[i]) {
                dropped[i] = true;
                return true;
            }
        }
        return false;
    }
}
