/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import android.graphics.Canvas;
import android.graphics.Paint;

/** Measured outside strip within the battery ImageView's real layout allocation. */
final class ColorOsOutsideLayout {
    static final ColorOsOutsideLayout EMPTY = new ColorOsOutsideLayout(null, "", -1, 0, 0, 0, false, false, null);
    final OutRingLayout.Plan layout;
    final String type;
    final int signal, reserve, height;
    final float typeSize;
    final boolean suppressType, rtl;
    final TrioAppearance appearance;
    private String readout = "";
    private Paint readoutPaint;
    private String[] readoutLines;
    private float readoutBaseline, readoutLineHeight;

    private ColorOsOutsideLayout(OutRingLayout.Plan layout, String type, int signal, int reserve, int height,
                                 float typeSize, boolean suppressType, boolean rtl, TrioAppearance appearance) {
        this.layout = layout; this.type = type; this.signal = signal; this.reserve = reserve; this.height = height;
        this.typeSize = typeSize; this.suppressType = suppressType; this.rtl = rtl; this.appearance = appearance;
    }

    static ColorOsOutsideLayout create(TrioSettings settings, ColorOsNetworkAdapter.Plan source,
                                       int height, int room, float density, boolean rtl) {
        return create(settings, source, height, room, density, rtl, ColorOsReadoutSource.State.UNKNOWN);
    }

    static ColorOsOutsideLayout create(TrioSettings settings, ColorOsNetworkAdapter.Plan source,
                                       int height, int room, float density, boolean rtl, ColorOsReadoutSource.State readings) {
        return create(settings, source, height, room, density, rtl, readings,
                ColorOsRingPreset.resolve(settings, 50, false, source).wifi);
    }

    static ColorOsOutsideLayout create(TrioSettings settings, ColorOsNetworkAdapter.Plan source,
                                       int height, int room, float density, boolean rtl, ColorOsReadoutSource.State readings, boolean wifiInk) {
        return create(settings, source, height, room, density, rtl, readings, wifiInk, false);
    }

    static ColorOsOutsideLayout create(TrioSettings settings, ColorOsNetworkAdapter.Plan source,
                                       int height, int room, float density, boolean rtl, ColorOsReadoutSource.State readings,
                                       boolean wifiInk, boolean vpnActive) {
        TrioAppearance appearance = TrioAppearance.of(settings, vpnActive);
        boolean typeWanted = settings.content.mobileTypeMode() == Prefs.MOBILE_TYPE_OUT_RING && !source.typeLabel.isEmpty();
        boolean suppress = typeWanted && appearance.hidesTypeForWifi(source.wifi >= 1)
                && wifiInk;
        String type = typeWanted && !suppress ? source.typeLabel : "";
        int signal = settings.content.showMobile() && settings.content.signalMode() == Prefs.SIGNAL_OUT_RING && settings.content.stackedSignal() ? source.mobile : -1;
        int signalHeight = Math.min(height, TrioRenderer.outSignalHeight(settings.outSignalSize, density));
        float size = Math.min(settings.outTypeSize * density, height * 0.8f);
        String readout = readings.text(settings);
        if (type.isEmpty() && signal < 0 && readout.isEmpty())
            return suppress ? new ColorOsOutsideLayout(null, "", -1, 0, height, 0, true, rtl, appearance) : EMPTY;
        Paint readoutPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        readoutPaint.setTypeface(TrioRenderer.typefaceFor(settings.perfWeight));
        readoutPaint.setFakeBoldText(TrioRenderer.fakeBoldFor(settings.perfWeight));
        float textSize = settings.outTypeSize * density * settings.perfScale / 100f;
        readoutPaint.setTextSize(textSize);
        int lines = readout.contains("\n") ? 2 : 1;
        Paint.FontMetrics metrics = readoutPaint.getFontMetrics();
        float fullHeight = (metrics.descent - metrics.ascent) * lines;
        if (fullHeight > height) readoutPaint.setTextSize(textSize * height / fullHeight);
        char widest = '0'; float widestWidth = 0;
        for (char digit = '0'; digit <= '9'; digit++) {
            float width = readoutPaint.measureText(String.valueOf(digit));
            if (width > widestWidth) { widestWidth = width; widest = digit; }
        }
        int readoutWidth = 0;
        for (String line : readout.split("\n"))
            readoutWidth = Math.max(readoutWidth, (int) Math.ceil(readoutPaint.measureText(line.replaceAll("[0-9]", String.valueOf(widest)))));
        int[] ids = Prefs.decodeOutOrder(Prefs.encodeOutOrder(settings.outOrder));
        int[] widths = new int[3], heights = new int[3], gaps = new int[3];
        for (int i = 0; i < ids.length; i++) {
            heights[i] = height;
            if (ids[i] == Prefs.OUT_SLOT_SIGNAL && signal >= 0) {
                widths[i] = TrioRenderer.outSignalWidth(signalHeight);
                gaps[i] = Math.round(settings.outSignalMargin * density);
            } else if (ids[i] == Prefs.OUT_SLOT_TYPE && !type.isEmpty()) {
                widths[i] = TrioRenderer.outsideTypeWidth(type, size, appearance)
                        + Math.round(settings.outTypeMarginLeft * density);
                gaps[i] = Math.round(settings.outTypeMarginRight * density);
            } else if (ids[i] == Prefs.OUT_SLOT_READOUTS && !readout.isEmpty()) {
                widths[i] = readoutWidth;
                gaps[i] = Math.round(2 * density);
            }
        }
        int[] drop = {Prefs.outOrderIndex(ids, Prefs.OUT_SLOT_TYPE), Prefs.outOrderIndex(ids, Prefs.OUT_SLOT_READOUTS),
                Prefs.outOrderIndex(ids, Prefs.OUT_SLOT_SIGNAL)};
        OutRingLayout.Plan plan = OutRingLayout.solve(Math.max(0, room), 0, 0, 0, height, widths, heights, gaps, 0, drop);
        // Legacy solver may return after one sacrifice. Continue with only its survivors.
        for (int attempt = 0; plan.scale < 1f && attempt < 3; attempt++)
            plan = OutRingLayout.solve(Math.max(0, room), 0, 0, 0, height, plan.widths, heights, gaps, 0, drop);
        if (plan.scale < 1f) return EMPTY;
        // Freeze coordinates in the actual allocation rather than the larger available budget.
        plan = OutRingLayout.solve(plan.reserve, 0, 0, 0, height, plan.widths, heights, gaps, 0, drop);
        // Do not claim a clipped or scaled final survivor. Keep its native counterpart instead.
        if (plan.scale < 1f) return EMPTY;
        if (plan.widths[Prefs.outOrderIndex(ids, Prefs.OUT_SLOT_TYPE)] <= 0) type = "";
        if (plan.widths[Prefs.outOrderIndex(ids, Prefs.OUT_SLOT_SIGNAL)] <= 0) signal = -1;
        ColorOsOutsideLayout result = new ColorOsOutsideLayout(plan, type, signal, plan.reserve, height, size, suppress, rtl, appearance);
        if (plan.widths[Prefs.outOrderIndex(ids, Prefs.OUT_SLOT_READOUTS)] > 0) {
            result.readout = readout; result.readoutPaint = readoutPaint;
            result.readoutLines = readout.split("\n");
            Paint.FontMetrics finalMetrics = readoutPaint.getFontMetrics();
            result.readoutLineHeight = finalMetrics.descent - finalMetrics.ascent;
            result.readoutBaseline = (height - result.readoutLineHeight * result.readoutLines.length) / 2f - finalMetrics.ascent;
        }
        return result;
    }

    boolean hasType() { return !type.isEmpty(); }
    boolean hasSignal() { return signal >= 0; }
    String description() { return type + (type.isEmpty() || readout.isEmpty() ? "" : ", ") + readout.replace('\n', ' '); }
    boolean same(ColorOsOutsideLayout other) {
        return other != null && type.equals(other.type) && signal == other.signal && reserve == other.reserve
                && height == other.height && typeSize == other.typeSize && suppressType == other.suppressType && rtl == other.rtl
                && readout.equals(other.readout);
    }

    void draw(Canvas canvas, int tint) {
        if (layout == null || reserve <= 0) return;
        int[] ids = appearance.outOrder;
        for (int i = 0; i < ids.length; i++) {
            int width = layout.widths[i];
            if (width <= 0) continue;
            float left = rtl ? reserve - layout.near[i] : layout.near[i] - width;
            int saved = canvas.save();
            try {
                canvas.translate(left, 0); canvas.clipRect(0, 0, width, height);
                if (ids[i] == Prefs.OUT_SLOT_TYPE && hasType())
                    TrioRenderer.drawOutsideType(canvas, type, width, height, typeSize, tint, appearance);
                else if (ids[i] == Prefs.OUT_SLOT_SIGNAL && hasSignal())
                    TrioRenderer.drawOutSignal(canvas, width, height, signal, -1, tint, appearance);
                else if (ids[i] == Prefs.OUT_SLOT_READOUTS && !readout.isEmpty()) {
                    readoutPaint.setColor(tint); readoutPaint.setTextAlign(Paint.Align.CENTER);
                    float baseline = readoutBaseline;
                    for (String line : readoutLines) { canvas.drawText(line, width / 2f, baseline, readoutPaint); baseline += readoutLineHeight; }
                }
            } finally { canvas.restoreToCount(saved); }
        }
    }
}
