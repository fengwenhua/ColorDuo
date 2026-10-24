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

import java.awt.geom.Path2D;

/** Minimal desktop stand-in for {@code android.graphics.Path}. */
public class Path {

    /** Winding direction, as Android names it. The desktop delegate fills non-zero, so both wind the same. */
    public enum Direction { CW, CCW }

    /** Circle-to-Bezier constant; Android's round rects are visually identical. */
    private static final float K = 0.5522847498307936f;

    final Path2D.Float delegate = new Path2D.Float(Path2D.WIND_NON_ZERO);

    public void moveTo(float x, float y) { delegate.moveTo(x, y); }

    public void lineTo(float x, float y) { delegate.lineTo(x, y); }

    public void quadTo(float x1, float y1, float x2, float y2) { delegate.quadTo(x1, y1, x2, y2); }

    public void cubicTo(float x1, float y1, float x2, float y2, float x3, float y3) {
        delegate.curveTo(x1, y1, x2, y2, x3, y3);
    }

    public void close() { delegate.closePath(); }

    public void reset() { delegate.reset(); }

    /**
     * Eight radii in Android's order: top-left, top-right, bottom-right,
     * bottom-left, two floats each. Java2D can only round all four corners
     * alike, so the outline is walked here - a bar with one square end is a
     * shape the renderer actually draws.
     */
    public void addRoundRect(RectF rect, float[] radii, Direction direction) {
        final float left = rect.left;
        final float top = rect.top;
        final float right = rect.right;
        final float bottom = rect.bottom;
        final float max = Math.min(rect.width(), rect.height()) * 0.5f;
        final float tlx = clamp(radii[0], max);
        final float tly = clamp(radii[1], max);
        final float trx = clamp(radii[2], max);
        final float tryy = clamp(radii[3], max);
        final float brx = clamp(radii[4], max);
        final float bry = clamp(radii[5], max);
        final float blx = clamp(radii[6], max);
        final float bly = clamp(radii[7], max);

        delegate.moveTo(left + tlx, top);
        delegate.lineTo(right - trx, top);
        delegate.curveTo(right - trx + trx * K, top, right, top + tryy - tryy * K, right, top + tryy);
        delegate.lineTo(right, bottom - bry);
        delegate.curveTo(right, bottom - bry + bry * K, right - brx + brx * K, bottom, right - brx, bottom);
        delegate.lineTo(left + blx, bottom);
        delegate.curveTo(left + blx - blx * K, bottom, left, bottom - bly + bly * K, left, bottom - bly);
        delegate.lineTo(left, top + tly);
        delegate.curveTo(left, top + tly - tly * K, left + tlx - tlx * K, top, left + tlx, top);
        delegate.closePath();
    }

    private static float clamp(float radius, float max) {
        if (radius < 0f) {
            return 0f;
        }
        return radius > max ? max : radius;
    }
}
