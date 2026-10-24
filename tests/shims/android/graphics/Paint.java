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

import java.awt.BasicStroke;
import java.awt.Font;

/** Minimal desktop stand-in for {@code android.graphics.Paint}. */
public class Paint {

    public static final int ANTI_ALIAS_FLAG = 0x01;

    public enum Style { FILL, STROKE, FILL_AND_STROKE }

    public enum Cap { BUTT, ROUND, SQUARE }

    public enum Join { MITER, ROUND, BEVEL }

    public enum Align { LEFT, CENTER, RIGHT }

    public static class FontMetrics {
        public float ascent;
        public float descent;
    }

    private Style style = Style.FILL;
    private Cap cap = Cap.BUTT;
    private Join join = Join.MITER;
    private Align align = Align.LEFT;
    private int color = 0xFF000000;
    private float strokeWidth = 1f;
    private float textSize = 12f;
    private String family = "SansSerif";
    private int typefaceStyle = Font.PLAIN;
    private float letterSpacing = 0f;
    private boolean fakeBold = false;

    public Paint() {
    }

    public Paint(int flags) {
    }

    public void setStyle(Style s) { style = s; }
    public Style getStyle() { return style; }

    public void setStrokeCap(Cap c) { cap = c; }
    public Cap getStrokeCap() { return cap; }

    public void setStrokeJoin(Join j) { join = j; }
    public Join getStrokeJoin() { return join; }

    public void setStrokeWidth(float w) { strokeWidth = w; }
    public float getStrokeWidth() { return strokeWidth; }

    public void setTextAlign(Align a) { align = a; }
    public Align getTextAlign() { return align; }

    public void setColor(int c) { color = c; }
    public int getColor() { return color; }

    public void setTextSize(float s) { textSize = s; }
    public float getTextSize() { return textSize; }

    public void setLetterSpacing(float s) { letterSpacing = s; }
    public float getLetterSpacing() { return letterSpacing; }

    public void setTypeface(Typeface t) {
        if (t != null) {
            family = t.family();
            typefaceStyle = t.style();
        }
    }

    /**
     * Synthetic emboldening, which the renderer applies to a custom font that
     * carries a single weight. Mapped onto the desktop bold style so a geometry
     * probe sees the same thing Android would: a wider face.
     */
    public void setFakeBoldText(boolean bold) {
        fakeBold = bold;
    }

    public boolean isFakeBoldText() {
        return fakeBold;
    }

    public Font font() {
        // The synthetic flag wins over the typeface's own style, matching the
        // platform's behaviour of emboldening whatever face is installed.
        final int style = fakeBold ? (typefaceStyle | Font.BOLD) : typefaceStyle;
        return new Font(family, style, 12).deriveFont(textSize);
    }

    public BasicStroke stroke() {
        int c = cap == Cap.ROUND ? BasicStroke.CAP_ROUND
                : (cap == Cap.SQUARE ? BasicStroke.CAP_SQUARE : BasicStroke.CAP_BUTT);
        int j = join == Join.ROUND ? BasicStroke.JOIN_ROUND
                : (join == Join.BEVEL ? BasicStroke.JOIN_BEVEL : BasicStroke.JOIN_MITER);
        return new BasicStroke(strokeWidth, c, j);
    }

    /**
     * Emulates Android's tracking-aware advance. Android measures a whole
     * string including its letter spacing, which matters here because
     * {@code TrioRenderer} uses this to shrink an oversized value.
     */
    public float measureText(String text) {
        if (text == null || text.isEmpty()) {
            return 0f;
        }
        java.awt.Canvas probe = new java.awt.Canvas();
        java.awt.FontMetrics fm = probe.getFontMetrics(font());
        float total = 0f;
        for (int i = 0; i < text.length(); i++) {
            total += fm.charWidth(text.charAt(i));
            if (i < text.length() - 1) {
                total += letterSpacing * textSize;
            }
        }
        return total;
    }

    /**
     * Android's ink box for {@code text}, written into {@code out}.
     *
     * <p>Same signature as the platform, deliberately: the only overload that takes
     * a box is {@code getTextBounds(String, int, int, Rect)} and it works in whole
     * pixels. This shim previously declared a {@code RectF} variant, which does not
     * exist on the device - the harness compiled against it happily while the real
     * SDK build failed, so the shim was hiding a signature error rather than
     * standing in for the platform.
     *
     * <p>Reports the real drawn extent rather than the font metrics, which is what
     * the renderer needs to centre a reading on the slot it sits in: the metrics
     * carry leading above the capitals that a digits-only string never draws.
     */
    public void getTextBounds(String text, int start, int end, Rect out) {
        if (out == null) {
            return;
        }
        if (text == null || start < 0 || end > text.length() || start >= end) {
            out.setEmpty();
            return;
        }
        final String slice = text.substring(start, end);
        java.awt.Canvas probe = new java.awt.Canvas();
        final java.awt.Font font = font();
        final java.awt.geom.Rectangle2D bounds = font.createGlyphVector(
                probe.getFontMetrics(font).getFontRenderContext(), slice).getVisualBounds();
        // The platform reports integer bounds relative to the baseline; round the same
        // way it does rather than truncating toward zero.
        out.left = (int) Math.floor(bounds.getX());
        out.top = (int) Math.floor(bounds.getY());
        out.right = out.left + (int) Math.ceil(measureText(slice));
        out.bottom = out.top + (int) Math.ceil(bounds.getHeight());
    }

    public FontMetrics getFontMetrics() {        java.awt.Canvas probe = new java.awt.Canvas();
        java.awt.FontMetrics fm = probe.getFontMetrics(font());
        FontMetrics out = new FontMetrics();
        out.ascent = fm.getAscent();
        out.descent = fm.getDescent();
        return out;
    }
}
