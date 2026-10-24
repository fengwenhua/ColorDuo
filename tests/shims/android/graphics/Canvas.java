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

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Desktop stand-in for {@code android.graphics.Canvas} that replays the calls
 * {@code TrioRenderer} makes onto a Java2D surface. Angles are degrees,
 * clockwise-positive, matching Android's {@code drawArc}.
 */
public class Canvas {

    private final Graphics2D g;
    private final Deque<AffineTransform> stack = new ArrayDeque<>();

    public Canvas(Graphics2D g) {
        this.g = g;
        this.g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        this.g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                RenderingHints.VALUE_STROKE_PURE);
    }

    public Graphics2D graphics() {
        return g;
    }

    /**
     * Android semantics: the returned handle is the save count <em>before</em> this
     * save, i.e. 0 for a canvas that has never been saved. restoreToCount(handle)
     * must therefore pop exactly this one frame.
     */
    public int save() {
        stack.push(g.getTransform());
        return stack.size() - 1;
    }

    public void restoreToCount(int count) {
        while (stack.size() > count) {
            g.setTransform(stack.pop());
        }
    }

    public void translate(float dx, float dy) {
        g.translate(dx, dy);
    }

    public void scale(float sx, float sy) {
        g.scale(sx, sy);
    }

    public void drawColor(int color, PorterDuff.Mode mode) {
        java.awt.Composite old = g.getComposite();
        if (mode == PorterDuff.Mode.CLEAR) {
            g.setComposite(java.awt.AlphaComposite.Clear);
            g.fillRect(-100000, -100000, 200000, 200000);
        } else {
            g.setColor(new java.awt.Color(color, true));
            g.fillRect(-100000, -100000, 200000, 200000);
        }
        g.setComposite(old);
    }

    public void drawArc(RectF oval, float startAngle, float sweepAngle,
                        boolean useCenter, Paint paint) {
        double w = oval.width();
        double h = oval.height();
        // Android measures angles clockwise from 3 o'clock; Java2D measures
        // them counter-clockwise. Convert rather than mirroring the renderer.
        Arc2D arc = new Arc2D.Double(oval.left, oval.top, w, h,
                -(startAngle + sweepAngle), sweepAngle, useCenter ? Arc2D.PIE : Arc2D.OPEN);
        apply(paint, arc);
    }

    public void drawCircle(float cx, float cy, float radius, Paint paint) {
        Ellipse2D e = new Ellipse2D.Float(cx - radius, cy - radius, radius * 2f, radius * 2f);
        apply(paint, e);
    }

    /**
     * Java2D's arc width/height are full diameters where Android takes corner
     * radii, hence the doubling.
     */
    public void drawRoundRect(RectF rect, float rx, float ry, Paint paint) {
        apply(paint, new java.awt.geom.RoundRectangle2D.Float(
                rect.left, rect.top, rect.width(), rect.height(), rx * 2f, ry * 2f));
    }

    public void drawPath(Path path, Paint paint) {
        apply(paint, path.delegate);
    }

    /**
     * A stroked straight line. Needed by the capsule arrangement, which walks its
     * outline in segments so the charged fraction is exactly the fraction drawn.
     * Java2D has no call that honours the Android paint's stroke, so the stroke is
     * taken from the paint the same way {@link #apply} takes it.
     */
    public void drawLine(float x1, float y1, float x2, float y2, Paint paint) {
        g.setColor(new java.awt.Color(paint.getColor(), true));
        g.setStroke(paint.stroke());
        g.draw(new java.awt.geom.Line2D.Float(x1, y1, x2, y2));
    }

    public void drawText(String text, float x, float y, Paint paint) {
        g.setColor(new java.awt.Color(paint.getColor(), true));
        java.awt.Font font = paint.font();
        java.awt.FontMetrics fm = g.getFontMetrics(font);

        // Android's y is the baseline; Java2D's drawString y is also the
        // baseline, so no adjustment is needed.
        float total = paint.measureText(text);
        float startX;
        switch (paint.getTextAlign()) {
            case CENTER: startX = x - total / 2f; break;
            case RIGHT: startX = x - total; break;
            default: startX = x;
        }
        if (paint.getLetterSpacing() == 0f) {
            g.setFont(font);
            g.drawString(text, startX, y);
        } else {
            // Deliver each glyph individually so tracking is honoured.
            float cursor = startX;
            float tracking = paint.getLetterSpacing() * paint.getTextSize();
            for (int i = 0; i < text.length(); i++) {
                String ch = String.valueOf(text.charAt(i));
                g.setFont(font);
                g.drawString(ch, cursor, y);
                cursor += fm.charWidth(text.charAt(i)) + tracking;
            }
        }
        // fm is intentionally queried above so a missing font fails loudly.
        if (fm == null) {
            throw new IllegalStateException("no font metrics");
        }
    }

    private void apply(Paint paint, java.awt.Shape shape) {
        g.setColor(new java.awt.Color(paint.getColor(), true));
        if (paint.getStyle() == Paint.Style.FILL) {
            g.fill(shape);
        } else {
            g.setStroke(paint.stroke());
            g.draw(shape);
        }
    }
}
