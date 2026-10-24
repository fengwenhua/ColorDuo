/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import java.lang.ref.WeakReference;
import java.util.function.Consumer;

/** Bounded Canvas bridge; never occupies the OEM's typed batteryStyleDrawable field. */
final class ColorOsBatteryDrawable extends Drawable {
    static final class Frame {
        final int level;
        final int tint;
        final TrioSettings settings;
        final ColorOsNetworkAdapter.Plan network;
        final ColorOsNetworkAdapter.Plan readings;
        final int[] slotLevels;
        final ColorOsPowerSource.State power;
        final String clock;
        final boolean drawsClock;
        final ColorOsOutsideLayout outside;
        final int viewWidth, viewHeight;
        Frame(int level, int tint, TrioSettings settings) {
            this(level, tint, settings, ColorOsNetworkAdapter.Plan.EMPTY);
        }
        Frame(int level, int tint, TrioSettings settings, ColorOsNetworkAdapter.Plan network) {
            this(level, tint, settings, network, ColorOsPowerSource.State.UNKNOWN);
        }
        Frame(int level, int tint, TrioSettings settings, ColorOsNetworkAdapter.Plan network,
              ColorOsPowerSource.State power) {
            this(level, tint, settings, network, power, "", 0, 0);
        }
        Frame(int level, int tint, TrioSettings settings, ColorOsNetworkAdapter.Plan network,
              ColorOsPowerSource.State power, String clock, int width, int height) {
            this(level, tint, settings, network, power, clock, width, height, ColorOsOutsideLayout.EMPTY);
        }
        Frame(int level, int tint, TrioSettings settings, ColorOsNetworkAdapter.Plan network,
              ColorOsPowerSource.State power, String clock, int width, int height, ColorOsOutsideLayout outside) {
            this(level, tint, settings, network, power, clock, width, height, outside, network);
        }
        Frame(int level, int tint, TrioSettings settings, ColorOsNetworkAdapter.Plan network,
              ColorOsPowerSource.State power, String clock, int width, int height, ColorOsOutsideLayout outside,
              ColorOsNetworkAdapter.Plan readings) {
            this.level = level; this.tint = tint; this.settings = settings;
            this.network = network;
            this.readings = readings;
            this.power = power;
            this.clock = clock;
            this.outside = outside;
            viewWidth = width; viewHeight = height;
            slotLevels = readings.mobile >= 0 ? new int[]{-1, -1} : null;
            if (slotLevels != null) slotLevels[readings.mobileSlot] = readings.mobile;
            ColorOsRingPreset.Resolved resolved = ColorOsRingPreset.resolve(settings, level, power.charging, readings);
            drawsClock = resolved.clock && !clock.isEmpty();
            if ((network.wifi >= 1 && !resolved.wifi) || (network.mobile >= 0 && !resolved.mobile && !outside.hasSignal()))
                throw new IllegalStateException("Network reading has no drawable ring zone");
        }
        boolean same(Frame other) {
            return other != null && level == other.level && tint == other.tint
                    && settings == other.settings && network.same(other.network) && readings.same(other.readings) && power.same(other.power)
                    && clock.equals(other.clock) && viewWidth == other.viewWidth && viewHeight == other.viewHeight && outside.same(other.outside);
        }
    }

    private final WeakReference<Drawable> nativeDrawable;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable onFailed;
    private final Consumer<Frame> onDrawn;
    private final int intrinsicWidth;
    private final int intrinsicHeight;
    private final Paint layer = new Paint();
    private Frame frame;
    private boolean failed;
    private Frame reportedFrame;

    ColorOsBatteryDrawable(Drawable original, Frame frame, Runnable onFailed, Consumer<Frame> onDrawn) {
        nativeDrawable = new WeakReference<>(original);
        intrinsicWidth = (frame.settings.trioStyle == Prefs.STYLE_CAPSULE || frame.outside.reserve > 0) && frame.viewWidth > 0
                ? frame.viewWidth : original.getIntrinsicWidth();
        intrinsicHeight = (frame.settings.trioStyle == Prefs.STYLE_CAPSULE || frame.outside.reserve > 0) && frame.viewHeight > 0
                ? frame.viewHeight : original.getIntrinsicHeight();
        this.frame = frame;
        this.onFailed = onFailed;
        this.onDrawn = onDrawn;
    }

    void update(Frame next) {
        if (!frame.same(next)) {
            frame = next;
            invalidateSelf();
        }
    }

    Drawable original() { return nativeDrawable.get(); }
    boolean isCurrent(Frame presented) { return !failed && frame == presented; }
    boolean hasPresented(Frame desired) { return !failed && reportedFrame == frame && frame.same(desired); }
    void useNativeFallback() { failed = true; invalidateSelf(); }

    @Override protected void onBoundsChange(Rect bounds) {
        reportedFrame = null;
        invalidateSelf();
    }

    static void preflight(int width, int height, Frame frame) {
        if (width <= 0 || height <= 0 || width > 2048 || height > 2048) throw new IllegalArgumentException("bounds");
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        try { render(new Canvas(bitmap), width, height, frame); }
        finally { bitmap.recycle(); }
    }

    private static void render(Canvas canvas, int width, int height, Frame frame) {
        int saved = canvas.save();
        try {
        canvas.translate(frame.outside.rtl ? 0 : frame.outside.reserve, 0);
        canvas.clipRect(0, 0, width - frame.outside.reserve, height);
        TrioRenderer.drawInto(canvas, width - frame.outside.reserve, height, frame.level, frame.power.charging, frame.power.quick,
                frame.power.saving, frame.level <= frame.settings.lowThreshold,
                frame.readings.wifi, frame.readings.mobile, frame.slotLevels, frame.readings.typeLabel, frame.tint, frame.settings, false,
                frame.readings.wifi >= 0);
        } finally { canvas.restoreToCount(saved); }
        saved = canvas.save();
        try {
            if (frame.outside.rtl) canvas.translate(width - frame.outside.reserve, 0);
            frame.outside.draw(canvas, frame.tint);
        } finally { canvas.restoreToCount(saved); }
    }

    @Override public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        if (bounds.isEmpty()) return;
        boolean success = false;
        if (!failed) {
            int saved = canvas.save();
            try {
                canvas.clipRect(bounds);
                if (layer.getAlpha() != 255 || layer.getColorFilter() != null) {
                    canvas.saveLayer(bounds.left, bounds.top, bounds.right, bounds.bottom, layer);
                }
                canvas.translate(bounds.left, bounds.top);
                render(canvas, bounds.width(), bounds.height(), frame);
                success = true;
            } catch (Throwable failure) {
                failed = true;
                main.post(onFailed);
            } finally { canvas.restoreToCount(saved); }
        }
        if (success && reportedFrame != frame) {
            Frame presented = frame;
            reportedFrame = presented;
            main.post(() -> onDrawn.accept(presented));
        }
        if (!success) {
            Drawable original = nativeDrawable.get();
            if (original != null) {
                int saved = canvas.save();
                try { canvas.clipRect(bounds); original.draw(canvas); }
                finally { canvas.restoreToCount(saved); }
            }
        }
    }

    @Override public void setAlpha(int alpha) { layer.setAlpha(alpha); invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter filter) { layer.setColorFilter(filter); invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    @Override public int getIntrinsicWidth() { return intrinsicWidth; }
    @Override public int getIntrinsicHeight() { return intrinsicHeight; }
}
