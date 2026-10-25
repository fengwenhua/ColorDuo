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

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * Renders the trio glyph for the settings app's live preview.
 *
 * <p>It exists so the preview reuses {@link TrioRenderer} exactly as the status
 * bar does. A copy of the geometry inside the app would drift the moment either
 * side is tweaked, and the whole point of the preview is that what you see is
 * what the status bar will draw.
 *
 * <p>Nothing here touches the Xposed API: this class is loaded in the app's own
 * process.
 */
public final class TrioPreviewView extends View {

    private final Paint background = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    /**
     * Scratch rect for the rounded background and its clip.
     *
     * <p>Allocated once rather than per frame: {@link #onDraw} runs on every invalidate,
     * including the ones the glyph's own prefs broadcast causes, and a rect per frame is
     * garbage on a path that is already repainting a bitmap-sized surface.
     */
    private final RectF cornerRect = new RectF();
    /** Reused each frame by {@link #onDraw} to build the rounded clip; see {@link #cornerRect}. */
    private final Path cornerPath = new Path();

    /**
     * Corner radius of the cell's own background, in dp.
     *
     * <p>Matches the Compose cards that host these cells (10-12dp); 12 is the host
     * {@code RoundedCornerShape} and the ceiling, because a radius larger than half the
     * cell would stop being a rounded corner at all.
     */
    private static final float CORNER_RADIUS_DP = 12f;

    private TrioSettings settings = TrioSettings.defaults();
    private int level = 72;
    private boolean charging;
    private boolean quickCharging;
    private boolean powerSave;
    private boolean low;
    private int wifiLevel = 3;
    private int mobileLevel = 4;
    /** Per-SIM levels for the dual reading, or null for the single row. */
    private int[] slotLevels;
    private String mobileType = "";
    private int foreground = 0xFFFFFFFF;
    private int backgroundColor = 0xFF1C1B1F;

    /** Fraction of the shorter edge left empty around the glyph. */
    private float inset = 0.06f;

    /** Draws the out-of-ring label on its own instead of the glyph. */
    private boolean outTypeOnly;

    /** Draws the out-of-ring signal reading on its own instead of the glyph. */
    private boolean outSignalOnly;

    /**
     * The zone whose outline is drawn over the glyph, or {@code null} for none.
     *
     * <p>Set by the visual editor to show which third a press would land in. The
     * outline is derived from {@link TrioZones#boxes} through the same transform
     * the glyph is drawn with, so the line and the tappable area cannot disagree.
     */
    private TrioZones.Zone highlight;

    /**
     * Ink for the {@link #highlight} FILL, ARGB.
     *
     * <p>A wash rather than a tinted glyph: the point is to say "this cell is what the
     * press will act on" without restating the cell's own colours. The theme decides the
     * hue - see the caller - because a fixed white is invisible on a light preview and the
     * preview follows the system theme.
     */
    private int highlightFillColor = 0x1AFFFFFF;

    /** Ink for the {@link #highlight} outline, ARGB. */
    private int highlightColor = 0x66FFFFFF;

    /** Stroke width of the highlight outline, in density pixels. */
    private static final float HIGHLIGHT_STROKE_DP = 1.5f;

    /** Corner radius of the highlight outline, in density pixels. */
    private static final float HIGHLIGHT_CORNER_DP = 3f;

    private final Paint guidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF guideRect = new RectF();

    /** Scratch for the design -> view mapping; rewritten per outline, never kept. */
    private final float[] guidePoint = new float[2];

    /**
     * Height of the box the status bar draws the glyph in, in density pixels:
     * {@code status_bar_icon_height}. The out-of-ring label is positioned
     * against the icon it stands beside, so its size only means anything
     * relative to this box.
     */
    private static final float HOST_ICON_HEIGHT_DP = 20f;

    public TrioPreviewView(Context context) {
        super(context);
    }

    public TrioPreviewView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public TrioPreviewView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setSettings(TrioSettings value) {
        this.settings = value == null ? TrioSettings.defaults() : value;
        // This View belongs to the settings process: use synthetic time just as
        // its battery and SIM readings are synthetic. SystemUI uses ClockTicker.
        if ((this.settings.trioStyle == Prefs.STYLE_CAPSULE
                || this.settings.trioStyle == Prefs.STYLE_RING
                && TrioAppearance.of(this.settings).hasRingPart(Prefs.PART_CLOCK))
                && TrioState.sClockText.isEmpty()) {
            TrioState.sClockText = "19:41";
        }
        invalidate();
    }

    public void setState(int level, boolean charging, boolean powerSave, boolean low,
                         int wifiLevel, int mobileLevel) {
        setState(level, charging, false, powerSave, low, wifiLevel, mobileLevel, null, null);
    }

    public void setState(int level, boolean charging, boolean powerSave, boolean low,
                         int wifiLevel, int mobileLevel, String mobileType) {
        setState(level, charging, false, powerSave, low, wifiLevel, mobileLevel, null, mobileType);
    }

    public void setState(int level, boolean charging, boolean quickCharging, boolean powerSave,
                         boolean low, int wifiLevel, int mobileLevel, String mobileType) {
        setState(level, charging, quickCharging, powerSave, low, wifiLevel, mobileLevel,
                null, mobileType);
    }

    /**
     * The full state, including the per-SIM levels the dual reading draws.
     *
     * <p>Public because the settings screen lives in another package
     * ({@code io.github.fengwenhua.colorduo.ui}) and has to call this across it.
     *
     * @param slotLevels one level per SIM slot, SIM 1 first, or {@code null} to
     *                   preview the single row the status bar draws when only one
     *                   of the two rows is wanted
     */
    public void setState(int level, boolean charging, boolean quickCharging, boolean powerSave,
                         boolean low, int wifiLevel, int mobileLevel, int[] slotLevels,
                         String mobileType) {
        this.level = level;
        this.charging = charging;
        this.quickCharging = quickCharging;
        this.powerSave = powerSave;
        this.low = low;
        this.wifiLevel = wifiLevel;
        this.mobileLevel = mobileLevel;
        this.slotLevels = slotLevels;
        this.mobileType = mobileType == null ? "" : mobileType;
        invalidate();
    }

    public void setForeground(int color) {
        this.foreground = color;
        invalidate();
    }

    public void setPreviewBackground(int color) {
        this.backgroundColor = color;
        invalidate();
    }

    public void setInset(float fraction) {
        this.inset = Math.max(0f, Math.min(0.4f, fraction));
        invalidate();
    }

    /**
     * Shows the out-of-ring type label instead of the glyph.
     *
     * <p>That label is not part of the glyph: the hooked status bar lays it out
     * beside the icon and sizes it in raw pixels against the icon box. There is
     * no way to fold it into {@link TrioRenderer#drawInto}, so the preview draws
     * it here and at the same scale.
     */
    public void setOutTypeOnly(boolean value) {
        this.outTypeOnly = value;
        invalidate();
    }

    /**
     * Shows the out-of-ring signal reading instead of the glyph.
     *
     * <p>Like the label, this has nothing to do with the glyph: the status bar
     * folds the mobile slot away and draws the reading in a view of its own,
     * sized against the icon box. In out-of-ring mode the glyph itself carries
     * no signal ink at all, so without this cell the two switches would change
     * nothing anywhere on this screen.
     */
    public void setOutSignalOnly(boolean value) {
        this.outSignalOnly = value;
        invalidate();
    }

    /**
     * Outlines one third of the glyph, or clears the outline when given null.
     *
     * <p>This is the visual editor's feedback: it tells the user which third the
     * press they are about to make will act on, and it does so from the same rule
     * the press itself consults.
     */
    public void setHighlight(TrioZones.Zone zone) {
        this.highlight = zone;
        invalidate();
    }

    /** Ink of the highlight outline. */
    public void setHighlightColor(int color) {
        this.highlightColor = color;
        invalidate();
    }

    /**
     * Both inks of the highlight, from one colour.
     *
     * <p>One setter rather than two because the two are a pair: the fill is the outline's
     * colour at a fraction of its alpha, and a caller that set them separately could leave
     * a fill and an outline of different hues on screen. The fill stays much fainter than
     * the line - it has to read as a highlight over ink it must not hide.
     */
    public void setHighlightColors(int accent) {
        this.highlightColor = withAlpha(accent, 0xB0);
        this.highlightFillColor = withAlpha(accent, 0x24);
        invalidate();
    }

    /** The same ARGB with its alpha replaced. */
    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    /**
     * Which third of the glyph a point in this view lands in, or {@code null} for
     * a miss and for the two out-of-ring cells.
     *
     * <p>The point is taken in this view's own coordinates, which is what the
     * gesture layer hands over, and it is resolved through the very transform
     * {@link #onDraw} paints with - see {@link #zoneTransform}, which both call.
     */
    public TrioZones.Zone zoneAt(float x, float y) {
        if (outTypeOnly || outSignalOnly) {
            // Those two cells draw the status bar's own views, not the glyph, so
            // the glyph's thirds do not describe them.
            return null;
        }
        final float[] t = zoneTransform(getWidth(), getHeight(), inset, settings);
        if (t == null) {
            return null;
        }
        return TrioZones.zoneAt(t, TrioAppearance.of(ChargeLayout.forState(settings, charging, quickCharging)), x, y);
    }

    /** Whether a tap hits the battery arc rather than an internal component. */
    public boolean isBatteryRingAt(float x, float y) {
        if (outTypeOnly || outSignalOnly) return false;
        TrioAppearance a = TrioAppearance.of(ChargeLayout.forState(settings, charging, quickCharging));
        int sims = 0;
        if (slotLevels != null) for (int signal : slotLevels) if (signal >= 0) sims++;
        return TrioZones.batteryRingAt(zoneTransform(getWidth(), getHeight(), inset, settings),
                a, a.ring(level, charging, wifiLevel, mobileType, sims), x, y);
    }

    /**
     * The design-to-view transform for a glyph box of this size, or {@code null}
     * when there is nothing to map onto.
     *
     * <p>A forwarder to {@link TrioZones#viewTransform}, and thin on purpose: the
     * arithmetic lives there because that class is compiled against the desktop
     * shims by the offline harness, whereas a {@code View} needs an Android
     * {@code Context} to exist at all. Keeping it there is what lets
     * {@code tests/drawing-zones.ps1} check the mapping the user's finger actually
     * goes through, rather than only the renderer's half of it.
     *
     * <p>Offered here as well because the settings screen's editor wants the same
     * mapping from outside the view: it reads the box size its {@code AndroidView}
     * will be handed and asks for the transform instead of keeping a copy of the
     * arithmetic, which is how a hot spot drifts off the piece it names.
     *
     * @param inset fraction of the shorter edge left empty around the glyph, as
     *              {@link #setInset} takes it
     */
    public static float[] zoneTransform(int width, int height, float inset, TrioSettings settings) {
        if (settings == null) {
            return null;
        }
        // The shape has to be in force before the transform is derived from it, or the hit
        // boxes and the highlight guide land on the reference-length capsule while a longer
        // one is drawn.
        TrioGeometry.setCapsuleLength(settings.capsuleLength);
        return TrioZones.viewTransform(width, height, inset, TrioAppearance.of(settings));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        final int w = getWidth();
        final int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }

        // Rounded, not a plain rect: this view paints its own background, and a Compose
        // `clip(RoundedCornerShape(...))` on the AndroidView does NOT clip the canvas
        // inside it - exactly like any other Android View. A square fill therefore showed
        // its corners poking out of the Compose card that hosts it, in the glyph cell and
        // in the two out-of-ring cells alike. The radius is the host's, expressed in dp so
        // every cell gets the same corner whatever its size; `getWidth`/`getHeight` only
        // decide how far the fill reaches.
        background.setStyle(Paint.Style.FILL);
        background.setColor(backgroundColor);
        final float radius = Math.min(CORNER_RADIUS_DP * getResources().getDisplayMetrics().density,
                Math.min(w, h) * 0.5f);
        if (radius > 0f) {
            cornerRect.set(0f, 0f, w, h);
            canvas.drawRoundRect(cornerRect, radius, radius, background);
        } else {
            canvas.drawRect(0f, 0f, w, h, background);
        }

        final float pad = Math.min(w, h) * inset;
        final int innerW = Math.round(w - 2f * pad);
        final int innerH = Math.round(h - 2f * pad);
        if (innerW <= 0 || innerH <= 0) {
            return;
        }

        // Everything the cell paints is clipped to the same rounded shape, so a glyph,
        // a magnified label or a too-wide reading cannot spill past the corner it is
        // drawn inside.
        final int clip = canvas.save();
        if (radius > 0f) {
            // clipPath rather than clipRoundRect: the latter is API 30 and this module's
            // minSdk is 29. The path is rebuilt here rather than reused because a Path
            // cannot be reset-and-rewound as cheaply as a RectF, and onDraw only runs on
            // invalidate.
            cornerPath.reset();
            cornerRect.set(0f, 0f, w, h);
            cornerPath.addRoundRect(cornerRect, radius, radius, Path.Direction.CW);
            canvas.clipPath(cornerPath);
        }

        final int save = canvas.save();
        canvas.translate(pad, pad);
        if (outTypeOnly) {
            drawOutTypeLabel(canvas, innerW, innerH);
        } else if (outSignalOnly) {
            drawOutSignal(canvas, innerW, innerH);
        } else {
            // false: the glyph must not punch a hole through the preview background.
            // A scene's Wi-Fi is "connected" when it carries a live level at all. The preview
            // has no WifiPresence to ask - it is a fixed scene, drawn in another process - so
            // the level IS the scene's statement about connectivity, which is exactly the
            // conflation the renderer had to stop making on the bar. Kept here because in a
            // scene it is not a conflation: nothing else knows.
            TrioRenderer.drawInto(canvas, innerW, innerH, level, charging, quickCharging,
                    powerSave, low, wifiLevel, mobileLevel, slotLevels, mobileType, foreground,
                    settings, false, wifiLevel >= 0);
        }
        canvas.restoreToCount(save);

        // After the glyph, so the outline reads as a frame over the ink rather
        // than as part of it. Skipped for the two out-of-ring cells: they do not
        // draw the glyph, so its thirds say nothing about them.
        if (highlight != null && !outTypeOnly && !outSignalOnly) {
            drawZoneOutline(canvas);
        }
        canvas.restoreToCount(clip);
    }

    /**
     * Outlines the highlighted third.
     *
     * <p>Each box is mapped through the same transform
     * {@link TrioRenderer#designTransform} gives the glyph, so the rule for what
     * is tappable and the rule for what is outlined are one rule. A zone may have
     * more than one box - the rectangular arrangement's bottom zone is its two dot
     * columns plus the bar - and every one of them is drawn.
     */
    private void drawZoneOutline(Canvas canvas) {
        final float[] t = zoneTransform(getWidth(), getHeight(), inset, settings);
        if (t == null) {
            return;
        }
        final float density = getResources().getDisplayMetrics().density;
        // A wash first, then the line: the fill is what makes a 20px-tall cell legible in a
        // 1000px preview, and the outline is what makes its EDGE unmistakable. Neither alone
        // did the job - a thin line vanished among the cell's own ink, and a fill with no
        // edge read as a rendering artefact rather than as a selected region.
        guidePaint.setStyle(Paint.Style.FILL);
        guidePaint.setColor(highlightFillColor);
        guidePaint.setStrokeWidth(0f);

        // Two passes over the same boxes: every fill, then every outline. Interleaving them
        // would let one box's fill wash over the previous box's line, which the rectangular
        // arrangement can produce - its bottom zone is two dot columns plus the bar.
        final java.util.List<float[]> boxes =
                java.util.Arrays.asList(
                        TrioZones.highlightBoxes(highlight, TrioAppearance.of(ChargeLayout.forState(settings, charging, quickCharging))));
        final float r = HIGHLIGHT_CORNER_DP * density;
        for (int pass = 0; pass < 2; pass++) {
            if (pass == 1) {
                guidePaint.setStyle(Paint.Style.STROKE);
                guidePaint.setColor(highlightColor);
                guidePaint.setStrokeWidth(Math.max(1f, HIGHLIGHT_STROKE_DP * density));
            }
            for (float[] box : boxes) {
                // Two opposite corners is enough: the transform is a uniform scale and
                // a translation, so an axis-aligned box stays axis-aligned.
                if (!TrioZones.toView(t, box[0], box[1], guidePoint)) {
                    continue;
                }
                guideRect.left = guidePoint[0];
                guideRect.top = guidePoint[1];
                if (!TrioZones.toView(t, box[2], box[3], guidePoint)) {
                    continue;
                }
                guideRect.right = guidePoint[0];
                guideRect.bottom = guidePoint[1];
                // The design box is inset from the view by `inset`, and the outline is
                // drawn in view coordinates, so the canvas is not translated here.
                canvas.drawRoundRect(guideRect, r, r, guidePaint);
            }
        }
    }

    /**
     * Draws the out-of-ring label on its own, centred in the glyph box.
     *
     * <p>The size is taken the way {@code TrioHooks.outTypeSizePx} takes it: the
     * {@code outTypeSize} dp resolved against the display density, with the
     * weight from {@code typeWeight}. Those pixels only mean something against
     * the icon box they are measured in, so the label is scaled by the ratio of
     * this preview's box to {@link #HOST_ICON_HEIGHT_DP}.
     */
    private void drawOutTypeLabel(Canvas canvas, int innerW, int innerH) {
        final float density = getResources().getDisplayMetrics().density;
        final float hostHeight = HOST_ICON_HEIGHT_DP * density;
        if (mobileType.isEmpty() || hostHeight <= 0f) {
            return;
        }

        final TrioAppearance a = TrioAppearance.of(ChargeLayout.forState(settings, charging, quickCharging));
        labelPaint.setColor(foreground);
        labelPaint.setTypeface(TrioRenderer.typefaceFor(a.typeWeight));
        // Mirrors what the status-bar label does with the same weight: with a
        // custom single-weight font the weight only becomes visible through the
        // synthetic embolden, so the preview has to apply it too or the cell would
        // show a plainer face than the bar actually draws.
        labelPaint.setFakeBoldText(TrioRenderer.fakeBoldFor(a.typeWeight));
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTextScaleX(1f);
        // dp setting to px exactly as the hooked side resolves it, then scaled
        // by this preview's box over the icon box it is tuned against.
        final float size = innerH * a.outTypeSize * density / hostHeight;
        labelPaint.setTextSize(size);

        // Shrink the trailing "A" the way the status bar label does, and the way
        // the ring canvas does not have to: this preview draws with a Paint, so
        // it takes the same two-run path TrioRenderer.drawType takes rather than
        // a span.
        final boolean shrunk = TrioGeometry.hasShrunkSuffix(mobileType, a.typeSuffixScale);
        final String base = shrunk ? TrioGeometry.typeBase(mobileType) : mobileType;
        final String suffix = shrunk ? String.valueOf(TrioGeometry.TYPE_SUFFIX) : "";
        final float suffixSize = size * a.typeSuffixScale / 100f;
        final float baseWidth = labelPaint.measureText(base);
        labelPaint.setTextSize(suffixSize);
        final float suffixWidth = labelPaint.measureText(suffix);
        final float width = baseWidth + suffixWidth;
        // On screen the label is wrap-content beside the icon box, so nothing
        // trims it; this cell is a square and would. Squeeze a too-wide glyph
        // horizontally instead of letting the view bounds cut it off. The height
        // is left exact - that is the dimension outTypeSize actually sets.
        final float scaleX = (width > innerW && width > 0f) ? innerW / width : 1f;
        labelPaint.setTextScaleX(scaleX);

        final Paint.FontMetrics metrics = labelPaint.getFontMetrics();
        final float baseline = innerH * 0.5f - (metrics.ascent + metrics.descent) * 0.5f;
        // The label is placed as the status bar places it: one gap to the anchor
        // (the inward margin), one to the native icons (the outward margin). In
        // this cell those neighbours are the cell edges, so the text block is
        // centred in the room *between* the two margins rather than in the whole
        // cell - a one-sided margin then visibly shifts the label instead of
        // leaving it dead centre.
        final float pxPerDp = innerH / HOST_ICON_HEIGHT_DP;
        final float roomLeft = a.outTypeMarginLeft * pxPerDp;
        final float roomRight = innerW - a.outTypeMarginRight * pxPerDp;
        final float scaledWidth = width * scaleX;
        final float left = (roomRight > roomLeft)
                ? roomLeft + (roomRight - roomLeft - scaledWidth) * 0.5f
                : innerW * 0.5f - scaledWidth * 0.5f;
        labelPaint.setTextSize(size);
        labelPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(base, left, baseline, labelPaint);
        if (shrunk) {
            labelPaint.setTextSize(suffixSize);
            canvas.drawText(TrioGeometry.TYPE_SUFFIX + "", left + baseWidth * scaleX,
                    baseline, labelPaint);
        }
        labelPaint.setTextAlign(Paint.Align.CENTER);
    }

    /**
     * Draws the out-of-ring signal reading on its own, centred in the glyph box.
     *
     * <p>The status bar does not draw this in the glyph either: it folds the
     * mobile slot away and gives the reading a view of its own, as tall as the
     * icon box and as wide as that height's reference aspect. So the reading is
     * first laid out at that on-screen size, exactly as the status bar measures
     * it, and only then scaled to this preview's box - the same host-height
     * ratio {@link #drawOutTypeLabel} uses, so both cells of the out-of-ring
     * pair are shown at one scale.
     *
     * <p>The data slot is left unknown ({@code -1}) on purpose: which SIM is the
     * data SIM is runtime state the settings app has not sampled, and guessing
     * one would show the switch's effect on the wrong card. With the switch on
     * the reading falls back to the first answered card, which is still the
     * point of the cell - one row instead of two.
     */
    private void drawOutSignal(Canvas canvas, int innerW, int innerH) {
        final float density = getResources().getDisplayMetrics().density;
        if (density <= 0f) {
            return;
        }

        final TrioAppearance a = TrioAppearance.of(ChargeLayout.forState(settings, charging, quickCharging));
        final int[] reading = new int[2];
        TrioRenderer.outSignalLevels(a.dataSimOnly, mobileLevel, slotLevels, -1, reading);
        if (reading[0] < 0) {
            reading[0] = 0;
        }

        // Same two steps the status bar takes: the setting, in dp, resolved
        // against the density, then the reference aspect. The setting is not a
        // ratio of the icon box here any more than it is on the status bar, so
        // a smaller reading really does show up smaller in this cell.
        final int hostHeight = TrioRenderer.outSignalHeight(a.outSignalSize, density);
        final int hostWidth = TrioRenderer.outSignalWidth(hostHeight);
        if (hostWidth <= 0) {
            return;
        }

        // The reading is shown at its own size, centred in the cell - NOT offset by its
        // margin.
        //
        // <p>The margin is a layout quantity: on the status bar it is padding the native
        // icon row gives up so the reading never lands on a neighbour. This cell has no
        // neighbours and no battery edge to be a distance from, so drawing the gap there
        // showed an empty gutter whose only effect was to make the reading sit off-centre
        // for no visible reason. The slider still moves; the cell simply answers "how big
        // is it", which is the question the size row next to it asks.
        final float scale = Math.min(innerW / (float) hostWidth, innerH / (float) hostHeight);
        final int drawW = Math.max(1, Math.round(hostWidth * scale));
        final int drawH = Math.max(1, Math.round(hostHeight * scale));
        final float left = (innerW - drawW) * 0.5f;
        final float top = (innerH - drawH) * 0.5f;

        final int save = canvas.save();
        canvas.translate(left, top);
        TrioRenderer.drawOutSignal(canvas, drawW, drawH, reading[0], reading[1], foreground, a);
        canvas.restoreToCount(save);
    }
}
