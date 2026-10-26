/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

/** Native-style transactions retain a drawn frame and restore the newest OEM fallback and size. */
final class BatteryStyleRegressionProbe {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    private static final class MeterImage extends ImageView {
        int layoutRequests;
        MeterImage(Context context) { super(context); }
        @Override public void requestLayout() { layoutRequests++; super.requestLayout(); }
    }

    static int run(Context context) {
        checks = 0;
        for (int style : new int[]{Prefs.STYLE_RING, Prefs.STYLE_CAPSULE}) {
            TrioSettings settings = TrioSettings.defaults();
            settings.trioStyle = style;
            settings.setShowValue(true);
            settings.ringZones = Prefs.decodeLayout("|3||");
            int width = style == Prefs.STYLE_CAPSULE ? 111 : 85;
            int height = style == Prefs.STYLE_CAPSULE ? 65 : 52;
            ColorDrawable oldNative = new ColorDrawable(Color.RED), newNative = new ColorDrawable(Color.BLUE);
            ColorOsBatteryDrawable.Frame frame = new ColorOsBatteryDrawable.Frame(80, Color.BLACK, settings,
                    ColorOsNetworkAdapter.Plan.EMPTY, ColorOsPowerSource.State.UNKNOWN, "", width, height);
            ColorOsBatteryDrawable preview = new ColorOsBatteryDrawable(oldNative, frame, () -> {}, drawn -> {});
            preview.setBounds(0, 0, width, height);
            check(!preview.hasDrawn(), "preflight or mounting does not authorize retaining a frame");
            Bitmap before = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            preview.draw(new Canvas(before));
            check(preview.hasDrawn(), "a successful Canvas draw authorizes the style bridge");
            check(preview.hasPresented(frame), "drawn frame is ready before the native update");
            newNative.setBounds(0, 0, width, height);
            preview.updateNative(newNative);
            check(preview.original() == newNative, "the bridge replaces the destroyed native fallback");
            check(preview.hasDrawn() && preview.hasPresented(frame), "fallback replacement does not discard the current frame");
            Bitmap after = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            preview.draw(new Canvas(after));
            check(before.sameAs(after), "native-style change never paints native pixels into the retained frame");
            preview.useNativeFallback();
            check(!preview.hasDrawn(), "failure or release revokes retention eligibility");
            preview.draw(new Canvas(after));
            check(after.getPixel(width / 2, height / 2) == Color.BLUE, "failure paints the newest native drawable");
            before.recycle(); after.recycle();
        }
        TrioSettings capsule = TrioSettings.defaults(); capsule.trioStyle = Prefs.STYLE_CAPSULE;
        View meter = new View(context); meter.layout(0, 0, 400, 65);
        MeterImage image = new MeterImage(context);
        image.setLayoutParams(new LinearLayout.LayoutParams(85, 52));
        ColorOsBatterySize size = new ColorOsBatterySize(image);
        size.apply(meter, capsule, 12);
        int customWidth = image.getLayoutParams().width, customHeight = image.getLayoutParams().height;
        image.layout(0, 0, customWidth, customHeight);
        image.layoutRequests = 0;
        size.beforeNativeStyle();
        check(image.layoutRequests == 0, "before native style never schedules an intermediate native-size traversal");
        check(image.getWidth() == customWidth && image.getHeight() == customHeight, "the measured capsule remains on screen");
        check(image.getLayoutParams().width == 85 && image.getLayoutParams().height == 52, "OEM helper sees its own dimensions");
        image.getLayoutParams().width = 90; image.getLayoutParams().height = 54;
        size.apply(meter, capsule, 12);
        check(image.getLayoutParams().width > 90, "the newest native size is wrapped in the custom capsule again");
        size.restore();
        check(image.getLayoutParams().width == 90 && image.getLayoutParams().height == 54, "disable restores the newest OEM dimensions");
        size.apply(meter, capsule);
        image.getLayoutParams().width = 200;
        boolean refused = false;
        try { size.beforeNativeStyle(); } catch (IllegalStateException expected) { refused = true; }
        check(refused, "a conflicting size owner cannot be silently overwritten");
        size.restore();
        check(image.getLayoutParams().width == 200, "conflicting native width survives release");
        ColorOsPlugBridge plug = new ColorOsPlugBridge();
        plug.power(true, 0);
        check(!plug.style(2, true, 1), "startup while plugged does not override an unrelated hidden style");
        plug.power(false, 100); plug.power(true, 200);
        check(plug.style(2, true, 600), "the observed plug-to-style-2 transition can retain a drawn frame");
        check(plug.style(2, true, 20_000), "an already recognized popup is driven by its style lifecycle");
        check(!plug.style(1, true, 20_001) && !plug.active(), "normal style ends the charging bridge immediately");
        check(!plug.style(2, true, 20_002), "a later unrelated style-2 request cannot reuse the consumed plug event");
        plug.power(false, 30_000); plug.power(true, 31_000);
        check(!plug.style(2, true, 36_001), "an expired plug event cannot override unrelated hidden icons");
        plug.power(false, 40_000); plug.power(true, 41_000);
        check(!plug.style(2, false, 41_100), "a disabled, hidden or unpainted host never starts the bridge");
        check(!plug.style(2, true, 41_200), "a rejected hide event is not retried against another context");
        plug.power(false, 50_000); plug.power(true, 51_000);
        check(plug.style(2, true, 51_100), "a fresh plug event may be recognized again");
        plug.clear();
        check(!plug.active() && !plug.style(2, true, 51_200), "disable or failure clears both active and pending overrides");
        return checks;
    }
}
