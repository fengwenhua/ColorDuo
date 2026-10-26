/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import java.util.UUID;
import org.json.JSONObject;

/** Runs with real Android Bundle/JSON/Canvas; absent from release APKs. */
public final class ConfigCodecProbe {
    private static int checks;
    static int count() { return checks; }
    private static void check(boolean value, String reason) {
        checks++;
        if (!value) throw new AssertionError(reason);
    }
    public static void run(android.content.Context context) throws Exception {
        checks = 0;
        checks += UpdateRegressionProbe.run();
        checks += SettingsBufferProbe.run(context);
        checks += HostTreeProbe.run(context);
        checks += FontBindingProbe.run(context);
        checks += ChargeRegressionProbe.run(context);
        checks += BatteryStyleRegressionProbe.run(context);
        String epoch = UUID.randomUUID().toString();
        for (int style : new int[]{Prefs.STYLE_RING, Prefs.STYLE_CAPSULE}) {
            TrioSettings original = TrioSettings.defaults();
            original.trioStyle = style;
            original.enabled = false;
            original.ringZones = Prefs.decodeLayout("3,1|7|8");
            String raw = ConfigPacket.encode(epoch, 42, original);
            ConfigPacket decoded = ConfigPacket.decode(raw);
            check(decoded.epoch.equals(epoch) && decoded.revision == 42, "version round-trip");
            if (!decoded.settings.equals(original)) {
                for (java.lang.reflect.Field field : TrioSettings.class.getFields()) {
                    Object expected = field.get(original);
                    Object actual = field.get(decoded.settings);
                    if (!java.util.Objects.deepEquals(expected, actual)) {
                        System.out.println("round-trip mismatch field=" + field.getName()
                                + " expected=" + expected + " actual=" + actual);
                    }
                }
            }
            check(decoded.settings.equals(original), "settings round-trip style=" + style);
            JSONObject malformed = new JSONObject(raw).put("revision", 1.5);
            boolean rejected = false;
            try { ConfigPacket.decode(malformed.toString()); } catch (Exception expected) { rejected = true; }
            check(rejected, "fractional revisions rejected");
            malformed = new JSONObject(raw).put("schema", 2);
            rejected = false;
            try { ConfigPacket.decode(malformed.toString()); } catch (Exception expected) { rejected = true; }
            check(rejected, "unsupported schemas rejected");
        }
        TrioSettings drawing = TrioSettings.defaults();
        drawing.trioStyle = Prefs.STYLE_RING;
        drawing.ringZones = Prefs.decodeLayout("3||");
        drawing.fontPath = "";
        drawing.showValue = true;
        Bitmap image = Bitmap.createBitmap(120, 100, Bitmap.Config.ARGB_8888);
        image.eraseColor(Color.MAGENTA);
        Canvas canvas = new Canvas(image);
        int saved = canvas.save();
        canvas.clipRect(0, 0, 85, 52);
        int before = canvas.getSaveCount();
        TrioRenderer.drawInto(canvas, 85, 52, 100, false, false, false, false,
                -1, -1, null, "", Color.BLACK, drawing, false, false);
        check(canvas.getSaveCount() == before, "renderer restores Canvas stack");
        canvas.restoreToCount(saved);
        check(image.getPixel(119, 99) == Color.MAGENTA, "outside pixels preserved");
        check(Color.alpha(image.getPixel(0, 0)) == 255, "no CLEAR on parent pixels");
        drawing = ColorOsRingPreset.create(drawing, false, true, new ColorOsNetworkAdapter.Plan(4, 3, null, null, null));
        ColorOsBatteryDrawable.Frame networkFrame = new ColorOsBatteryDrawable.Frame(67, Color.BLACK, drawing,
                new ColorOsNetworkAdapter.Plan(4, 3, null, null, null));
        ColorOsBatteryDrawable.preflight(85, 52, networkFrame);
        check(networkFrame.same(new ColorOsBatteryDrawable.Frame(67, Color.BLACK, drawing,
                new ColorOsNetworkAdapter.Plan(4, 3, null, null, null))), "equivalent frame does not schedule another draw");
        check(!networkFrame.same(new ColorOsBatteryDrawable.Frame(67, Color.BLACK, drawing,
                new ColorOsNetworkAdapter.Plan(2, 3, null, null, null))), "changed Wi-Fi invalidates drawing readiness");
        check(!networkFrame.same(new ColorOsBatteryDrawable.Frame(67, Color.BLACK, drawing,
                new ColorOsNetworkAdapter.Plan(4, 1, null, null, null))), "changed cellular invalidates drawing readiness");
        ColorOsBatteryDrawable preview = new ColorOsBatteryDrawable(
                new android.graphics.drawable.ColorDrawable(Color.BLACK), networkFrame,
                () -> {}, presented -> {});
        preview.setBounds(0, 0, 85, 52);
        check(!preview.hasPresented(networkFrame), "mount is not draw readiness");
        preview.draw(canvas);
        check(preview.hasPresented(networkFrame), "actual draw establishes readiness");
        check(preview.hasPresented(new ColorOsBatteryDrawable.Frame(67, Color.BLACK, drawing,
                new ColorOsNetworkAdapter.Plan(4, 3, null, null, null))), "late native layout may reuse equivalent presented frame");
        preview.setBounds(0, 0, 86, 52);
        check(!preview.hasPresented(networkFrame), "changed bounds invalidate readiness");
        preview.draw(canvas);
        ColorOsBatteryDrawable.Frame next = new ColorOsBatteryDrawable.Frame(67, Color.BLACK, drawing,
                new ColorOsNetworkAdapter.Plan(2, 3, null, null, null));
        preview.update(next);
        check(!preview.hasPresented(next), "new reading awaits its own draw");
        check(!preview.isCurrent(networkFrame), "stale draw callback cannot hide native icons");
        preview.draw(canvas);
        check(preview.hasPresented(next), "new reading ready after draw");
        preview.useNativeFallback();
        check(!preview.hasPresented(next), "fallback revokes network ownership readiness");
        check(drawing.showSim1 && drawing.ringZones[2][0] == Prefs.PART_SIM_1,
                "production preset uses a supported SIM row");
        boolean missingInkRejected = false;
        TrioSettings missingMobile = drawing.copy();
        missingMobile.showSim1 = false;
        try { new ColorOsBatteryDrawable.Frame(67, Color.BLACK, missingMobile,
                new ColorOsNetworkAdapter.Plan(4, 3, null, null, null)); }
        catch (IllegalStateException expected) { missingInkRejected = true; }
        check(missingInkRejected, "never claim native signal without a drawable signal zone");
        int[] fullSignal = drawSignal(drawing, 4);
        int[] weakSignal = drawSignal(drawing, 0);
        int differences = 0;
        for (int y = 26; y < 52; y++) for (int x = 0; x < 85; x++)
            if (fullSignal[y * 85 + x] != weakSignal[y * 85 + x]) differences++;
        check(differences > 0, "actual lower-half signal pixels change with the cellular reading");
        TrioSettings custom = drawing.copy();
        custom.ringZones = Prefs.decodeLayout("3,1||7");
        ColorOsNetworkAdapter.Plan raw = new ColorOsNetworkAdapter.Plan(4, 3, null, null, null);
        TrioSettings customPaint = ColorOsRingPreset.userLayout(custom, false, raw);
        ColorOsNetworkAdapter.Plan claimed = ColorOsRingPreset.represented(customPaint, 67, false, raw);
        check(claimed.wifi == -1 && claimed.mobile == 3, "priority fallback retains native Wi-Fi when value wins");
        custom.ringZones = Prefs.decodeLayout("1|3|8");
        custom.showSim2 = true;
        ColorOsNetworkAdapter.Plan second = new ColorOsNetworkAdapter.Plan(4, 2, null, null, null, 1);
        customPaint = ColorOsRingPreset.userLayout(custom, false, second);
        claimed = ColorOsRingPreset.represented(customPaint, 67, false, second);
        ColorOsBatteryDrawable.Frame secondFrame = new ColorOsBatteryDrawable.Frame(67, Color.BLACK, customPaint, claimed);
        check(secondFrame.slotLevels[0] == -1 && secondFrame.slotLevels[1] == 2,
                "single SIM in physical slot two keeps its identity");
        customPaint = ColorOsRingPreset.userLayout(custom, false, raw);
        claimed = ColorOsRingPreset.represented(customPaint, 67, false, raw);
        check(claimed.mobile == -1, "unrepresented card remains native");
        custom.ringZones = Prefs.decodeLayout("|1|7");
        custom.chargingShowsValue = true;
        customPaint = ColorOsRingPreset.userLayout(custom, false, raw);
        claimed = ColorOsRingPreset.represented(customPaint, 67, true, raw);
        check(claimed.wifi == -1, "charging centre override returns displaced Wi-Fi to native");
        ColorOsPowerSource.State full = new ColorOsPowerSource.State(true, false, false, true, true, false);
        ColorOsPowerSource.State charging = new ColorOsPowerSource.State(true, true, false, true, false, false);
        check(!full.same(charging), "full and actively charging are distinct frames");
        custom.ringZones = Prefs.decodeLayout("1|5|7");
        custom.mobileTypeMode = Prefs.MOBILE_TYPE_IN_RING;
        ColorOsNetworkAdapter.Plan typePlan = new ColorOsNetworkAdapter.Plan(4, 3, null, null, null, 0,
                "5G", null, true);
        customPaint = ColorOsRingPreset.userLayout(custom, false, typePlan);
        claimed = ColorOsRingPreset.represented(customPaint, 67, false, typePlan);
        check(claimed.hideType && claimed.typeLabel.equals("5G"), "type ownership survives signal filtering");
        ColorOsBatteryDrawable.Frame typed = new ColorOsBatteryDrawable.Frame(67, Color.BLACK, customPaint, claimed);
        ColorOsBatteryDrawable.preflight(85, 52, typed);
        check(TrioAppearance.of(customPaint).ring(67, false, 4, "5G", 1, true).parts[1] == Prefs.PART_TYPE,
                "network type has an actual centre zone");
        ColorOsNetworkAdapter.Plan changedType = new ColorOsNetworkAdapter.Plan(4, 3, null, null, null, 0,
                "4G", null, true);
        check(!typed.same(new ColorOsBatteryDrawable.Frame(67, Color.BLACK, customPaint, changedType)),
                "changed network type invalidates drawing readiness");
        custom.mobileTypeMode = Prefs.MOBILE_TYPE_OFF;
        custom.ringZones = Prefs.decodeLayout(Prefs.EMPTY_LAYOUT);
        customPaint = ColorOsRingPreset.userLayout(custom, false, typePlan);
        check(!ColorOsRingPreset.represented(customPaint, 67, false, typePlan).hideType,
                "empty layout retains the native type with the native signal");
        custom.ringZones = Prefs.decodeLayout("1|3|7");
        customPaint = ColorOsRingPreset.userLayout(custom, false, typePlan);
        check(ColorOsRingPreset.represented(customPaint, 67, false, typePlan).hideType,
                "folded signal follows the original native type handoff policy");
        TrioSettings capsule = drawing.copy();
        capsule.trioStyle = Prefs.STYLE_CAPSULE;
        capsule.ringZones = Prefs.decodeLayout(Prefs.EMPTY_LAYOUT);
        TrioSettings capsulePaint = ColorOsRingPreset.userLayout(capsule, false, raw);
        ColorOsNetworkAdapter.Plan capPlan = ColorOsRingPreset.represented(capsulePaint, 67, false, raw);
        String oldClock = TrioState.sClockText;
        try {
            TrioState.sClockText = "10:27";
            int[] size = ColorOsBatterySize.target(52, 65, 380, capsule.capsuleLayout.length());
            check(size[0] > 85 && size[0] <= 380 && size[1] <= 65, "capsule fits the actual status row budget");
            ColorOsBatteryDrawable.Frame capFrame = new ColorOsBatteryDrawable.Frame(67, Color.BLACK,
                    capsulePaint, capPlan, full, "10:27", size[0], size[1]);
            ColorOsBatteryDrawable.preflight(size[0], size[1], capFrame);
            check(capFrame.drawsClock, "empty capsule actually draws its default clock");
            check(!capFrame.same(new ColorOsBatteryDrawable.Frame(67, Color.BLACK, capsulePaint, capPlan,
                    full, "10:28", size[0], size[1])), "minute change invalidates the frame");
            TrioSettings noClock = capsule.copy();
            noClock.ringZones = Prefs.decodeLayout(Prefs.EMPTY_LAYOUT);
            noClock.ringZones[TrioZones.Zone.LEFT.ordinal()] = new int[]{Prefs.PART_WIFI};
            TrioSettings noClockPaint = ColorOsRingPreset.userLayout(noClock, false, raw);
            ColorOsNetworkAdapter.Plan noClockPlan = ColorOsRingPreset.represented(noClockPaint, 67, false, raw);
            check(!new ColorOsBatteryDrawable.Frame(67, Color.BLACK, noClockPaint, noClockPlan,
                    full, "10:27", size[0], size[1]).drawsClock,
                    "capsule with Wi-Fi in its left cell leaves native clock visible");
            android.widget.ImageView nativeView = new android.widget.ImageView(context);
            nativeView.setLayoutParams(new android.widget.LinearLayout.LayoutParams(85, 52));
            android.view.View meter = new android.view.View(context);
            meter.layout(0, 0, 400, 65);
            ColorOsBatterySize lease = new ColorOsBatterySize(nativeView);
            lease.apply(meter, capsule);
            check(nativeView.getLayoutParams().width > 85, "capsule occupies real layout width");
            lease.restore();
            check(nativeView.getLayoutParams().width == 85 && nativeView.getLayoutParams().height == 52,
                    "native dimensions restored");
            nativeView.setLayoutParams(new android.widget.LinearLayout.LayoutParams(90, 54));
            lease.apply(meter, capsule);
            lease.restore();
            check(nativeView.getLayoutParams().width == 90 && nativeView.getLayoutParams().height == 54,
                    "restore uses latest native style dimensions");
            lease.apply(meter, capsule);
            nativeView.getLayoutParams().width = 200;
            lease.restore();
            check(nativeView.getLayoutParams().width == 200, "another owner's later width is preserved");
            android.widget.LinearLayout.LayoutParams margins = new android.widget.LinearLayout.LayoutParams(85, 52);
            margins.bottomMargin = 10;
            nativeView.setLayoutParams(margins);
            lease.apply(meter, capsule);
            check(nativeView.getLayoutParams().height <= 55, "capsule respects OEM margins within the row");
            lease.restore();
        } finally { TrioState.sClockText = oldClock; }
        image.recycle();
        String fontKey = RemoteFontKey.create(new byte[]{0, 1, 2, 3});
        check(RemoteFontKey.valid(fontKey) && RemoteFontKey.expectedDigest(fontKey).equals(RemoteFontKey.digest(new byte[]{0, 1, 2, 3})),
                "immutable font key contains its digest");
        check(!RemoteFontKey.valid("../font.ttf") && !RemoteFontKey.valid("/system/fonts/font.ttf"), "font keys cannot carry paths");
        check(!fontKey.equals(RemoteFontKey.create(new byte[]{0, 1, 2, 3})), "same-name reimports receive new immutable keys");
        String withFont = ConfigPacket.encode(epoch, 43, TrioSettings.defaults(), fontKey);
        check(ConfigPacket.decode(withFont).fontFile.equals(fontKey), "font binding round-trip");
        JSONObject legacy = new JSONObject(withFont); legacy.remove("fontFile");
        check(ConfigPacket.decode(legacy.toString()).fontFile.isEmpty(), "old packets use system font");
        for (Object invalidFont : new Object[]{"../bad", 7, JSONObject.NULL}) {
            boolean rejected = false;
            try { ConfigPacket.decode(new JSONObject(withFont).put("fontFile", invalidFont).toString()); }
            catch (Exception expected) { rejected = true; }
            check(rejected, "invalid font reference rejected");
        }
        TrioSettings customFont = drawing.copy();
        customFont.fontPath = "remote-font:" + fontKey;
        TrioRenderer.setLoadedFont(customFont.fontPath, android.graphics.Typeface.MONOSPACE);
        int generation = TrioRenderer.fontGeneration();
        try {
            ColorOsBatteryDrawable.preflight(85, 52, new ColorOsBatteryDrawable.Frame(67, Color.BLACK,
                    customFont, ColorOsNetworkAdapter.Plan.EMPTY));
            check(!TrioRenderer.fontFailed() && generation == TrioRenderer.fontGeneration(), "drawing uses loaded face without opening opaque key");
        } finally { TrioRenderer.setLoadedFont("", null); }
        check(TrioRenderer.fontPath().isEmpty() && !TrioRenderer.fontFailed(), "clearing loaded font restores default");
        TrioSettings external = TrioSettings.defaults();
        external.signalMode = Prefs.SIGNAL_OUT_RING;
        external.stackedSignal = true;
        external.mobileTypeMode = Prefs.MOBILE_TYPE_OUT_RING;
        external.typeHideOnWifi = false;
        external.ringZones = Prefs.decodeLayout("|3|");
        ColorOsNetworkAdapter.Plan externalSource = new ColorOsNetworkAdapter.Plan(-1, 4, null, null, null, 0, "5G", null, false);
        ColorOsOutsideLayout strip = ColorOsOutsideLayout.create(external, externalSource, 52, 240, 3.25f, false);
        check(strip.hasType() && strip.hasSignal() && strip.reserve > 0 && strip.reserve <= 240, "outside strip fits measured allocation");
        TrioSettings externalPaint = ColorOsRingPreset.userLayout(external, false, externalSource);
        ColorOsNetworkAdapter.Plan externalPlan = ColorOsRingPreset.represented(externalPaint, 67, false, externalSource, strip);
        check(externalPlan.mobile == 4 && externalPlan.hideType, "outside ink can own the matching native rows");
        ColorOsBatteryDrawable.preflight(85 + strip.reserve, 52, new ColorOsBatteryDrawable.Frame(67, Color.BLACK,
                externalPaint, externalPlan, full, "", 85 + strip.reserve, 52, strip));
        ColorOsOutsideLayout rtlStrip = ColorOsOutsideLayout.create(external, externalSource, 52, 240, 3.25f, true);
        ColorOsBatteryDrawable.preflight(85 + rtlStrip.reserve, 52, new ColorOsBatteryDrawable.Frame(67, Color.BLACK,
                externalPaint, externalPlan, full, "", 85 + rtlStrip.reserve, 52, rtlStrip));
        check(rtlStrip.reserve == strip.reserve && !rtlStrip.same(strip), "RTL gets logical outside placement without changing allocation");
        ColorOsOutsideLayout noRoom = ColorOsOutsideLayout.create(external, externalSource, 52, 0, 3.25f, false);
        check(!noRoom.hasType() && !noRoom.hasSignal() && noRoom.reserve == 0, "no room retains native counterparts");
        ColorOsNetworkAdapter.Plan noRoomPlan = ColorOsRingPreset.represented(externalPaint, 67, false, externalSource, noRoom);
        check(noRoomPlan.mobile == -1 && !noRoomPlan.hideType, "unpainted outside rows cannot claim native ownership");
        android.widget.ImageView compositeView = new android.widget.ImageView(context);
        compositeView.setLayoutParams(new android.widget.LinearLayout.LayoutParams(85, 52));
        android.view.View compositeMeter = new android.view.View(context); compositeMeter.layout(0, 0, 400, 65);
        ColorOsBatterySize compositeSize = new ColorOsBatterySize(compositeView);
        compositeSize.apply(compositeMeter, external, strip.reserve);
        check(compositeView.getLayoutParams().width == 85 + strip.reserve, "outside strip occupies real width beside ring");
        compositeSize.restore();
        check(compositeView.getLayoutParams().width == 85 && compositeView.getLayoutParams().height == 52, "outside release restores OEM dimensions");
        external.perfPower = external.perfTemp = true;
        check(ColorOsReadoutSource.State.UNKNOWN.text(external).isEmpty(), "unknown readings never invent zero watts");
        ColorOsReadoutSource.State readings = new ColorOsReadoutSource.State(true, 0.7f, 30);
        check(readings.text(external).equals("0.7W\n30\u00b0C"), "attached known readings form two lines");
        ColorOsReadoutSource.State unplugged = new ColorOsReadoutSource.State(false, 0.7f, 30);
        check(unplugged.text(external).equals("30\u00b0C"), "unplugged power disappears while temperature remains");
        external.perfTempChargingOnly = true;
        check(unplugged.text(external).isEmpty(), "temperature charging-only filter follows attachment");
        ColorOsOutsideLayout readoutStrip = ColorOsOutsideLayout.create(external, externalSource, 52, 240, 3.25f, false, readings);
        check(readoutStrip.description().contains("0.7W") && readoutStrip.description().contains("30\u00b0C"), "drawn readouts have accessibility text");
        ColorOsBatteryDrawable.preflight(85 + readoutStrip.reserve, 52, new ColorOsBatteryDrawable.Frame(67, Color.BLACK,
                externalPaint, externalPlan, full, "", 85 + readoutStrip.reserve, 52, readoutStrip));
        ColorOsOutsideLayout narrowStrip = ColorOsOutsideLayout.create(external, externalSource, 52, 70, 3.25f, false, readings);
        check(narrowStrip.hasSignal() && !narrowStrip.hasType() && !narrowStrip.description().contains("W"),
                "narrow allocation sacrifices type then readouts before signal");
        external.stackedSignal = false;
        check(!ColorOsOutsideLayout.create(external, externalSource, 52, 240, 3.25f, false).hasSignal(), "stacked-off leaves native outside signal");
        external.showBolt = true;
        check(ColorOsRingPreset.userLayout(external, false, externalSource).showBolt,
                "native icon visibility does not change the user charging arrangement");
        external.stackedSignal = true;
        external.mobileTypeMode = Prefs.MOBILE_TYPE_OFF;
        ColorOsNetworkAdapter.Plan offTypeSource = new ColorOsNetworkAdapter.Plan(-1, 4, null, null, null, 0, "5G", null, true);
        ColorOsOutsideLayout onlySignal = ColorOsOutsideLayout.create(external, offTypeSource, 52, 240, 3.25f, false);
        check(ColorOsRingPreset.represented(ColorOsRingPreset.userLayout(external, false, offTypeSource), 67, false,
                offTypeSource, onlySignal).hideType, "custom outside signal also honors disabled native type");
        android.widget.FrameLayout mobileRoot = new android.widget.FrameLayout(context);
        android.widget.LinearLayout mobileGroup = new android.widget.LinearLayout(context);
        mobileRoot.addView(mobileGroup, new android.widget.FrameLayout.LayoutParams(-2, 65));
        android.view.View nativeSignal = new android.view.View(context);
        mobileGroup.addView(nativeSignal, new android.widget.LinearLayout.LayoutParams(68, 52));
        android.view.View nativeDot = new android.view.View(context); nativeDot.setVisibility(android.view.View.INVISIBLE);
        mobileRoot.addView(nativeDot, new android.widget.FrameLayout.LayoutParams(65, 65));
        nativeSignal.setVisibility(android.view.View.GONE);
        mobileRoot.measure(android.view.View.MeasureSpec.makeMeasureSpec(400, android.view.View.MeasureSpec.AT_MOST),
                android.view.View.MeasureSpec.makeMeasureSpec(65, android.view.View.MeasureSpec.EXACTLY));
        check(mobileGroup.getMeasuredWidth() == 0 && mobileRoot.getMeasuredWidth() == 65,
                "invisible overflow dot reproduces an empty 65px slot");
        ColorOsMobileGap gap = new ColorOsMobileGap(nativeDot);
        gap.sync(0, mobileGroup.getMeasuredWidth() == 0);
        mobileRoot.measure(android.view.View.MeasureSpec.makeMeasureSpec(400, android.view.View.MeasureSpec.AT_MOST),
                android.view.View.MeasureSpec.makeMeasureSpec(65, android.view.View.MeasureSpec.EXACTLY));
        check(mobileRoot.getMeasuredWidth() == 0, "empty native group releases overflow-dot allocation");
        check(!gap.needsChange(0, true), "stable compact state does not request another layout");
        nativeSignal.setVisibility(android.view.View.VISIBLE); gap.sync(0, false);
        check(nativeDot.getVisibility() == android.view.View.INVISIBLE && nativeSignal.getVisibility() == android.view.View.VISIBLE,
                "native content restores normal dot sizing");
        gap.sync(0, true); gap.sync(1, true);
        check(nativeDot.getVisibility() == android.view.View.INVISIBLE, "native overflow state cannot be compacted");
        gap.sync(0, true); nativeDot.setVisibility(android.view.View.VISIBLE); gap.restore();
        check(nativeDot.getVisibility() == android.view.View.VISIBLE, "later native dot visibility is preserved");
        nativeDot.setVisibility(android.view.View.INVISIBLE); gap.sync(0, true); gap.restore();
        check(nativeDot.getVisibility() == android.view.View.INVISIBLE, "master release restores original invisible dot");
        nativeDot.setVisibility(android.view.View.VISIBLE);
        gap.sync(0, true);
        check(nativeDot.getVisibility() == android.view.View.GONE, "ColorOS visible but inactive overflow dot also releases allocation");
        gap.restore();
        check(nativeDot.getVisibility() == android.view.View.VISIBLE, "ColorOS visible dot restores its original visibility");
        String[] orders = {"5,1", "1,5", "5,3,1", "3,5,1", "4,5,1", "6,5,1"};
        int[][] expectedTop = {
                {Prefs.PART_WIFI, Prefs.PART_WIFI, Prefs.PART_VALUE, Prefs.PART_VALUE, Prefs.PART_WIFI, Prefs.PART_CLOCK},
                {Prefs.PART_TYPE, Prefs.PART_TYPE, Prefs.PART_TYPE, Prefs.PART_VALUE, Prefs.PART_TYPE, Prefs.PART_CLOCK},
                {Prefs.PART_NONE, Prefs.PART_NONE, Prefs.PART_VALUE, Prefs.PART_VALUE, Prefs.PART_NONE, Prefs.PART_CLOCK}
        };
        ColorOsNetworkAdapter.Plan[] sources = {
                new ColorOsNetworkAdapter.Plan(4, 3, null, null, null, 0, "5G", null, true),
                new ColorOsNetworkAdapter.Plan(-1, 3, null, null, null, 0, "5G", null, true),
                new ColorOsNetworkAdapter.Plan(-1, 3, null, null, null, 0, "", null, false)
        };
        for (int style : new int[]{Prefs.STYLE_RING, Prefs.STYLE_CAPSULE}) {
            for (int state = 0; state < sources.length; state++) for (int order = 0; order < orders.length; order++) {
                TrioSettings arranged = TrioSettings.defaults();
                arranged.trioStyle = style;
                arranged.showWifi = arranged.showMobile = arranged.showSim1 = arranged.showValue = arranged.showBolt = true;
                arranged.typeHideOnWifi = true;
                arranged.mobileTypeMode = Prefs.MOBILE_TYPE_IN_RING;
                arranged.ringZones = Prefs.decodeLayout(orders[order] + "|3|7|");
                ColorOsNetworkAdapter.Plan source = sources[state];
                TrioSettings paint = ColorOsRingPreset.userLayout(arranged, false, source);
                ColorOsNetworkAdapter.Plan ownership = ColorOsRingPreset.represented(paint, 67, false, source);
                int width = style == Prefs.STYLE_CAPSULE ? 111 : 85, height = style == Prefs.STYLE_CAPSULE ? 65 : 52;
                ColorOsBatteryDrawable.Frame frame = new ColorOsBatteryDrawable.Frame(67, Color.BLACK, paint,
                        ownership, full, "19:41", width, height, ColorOsOutsideLayout.EMPTY, source);
                TrioAppearance actual = TrioAppearance.of(frame.settings);
                int[] parts = style == Prefs.STYLE_CAPSULE
                        ? actual.capsule(67, false, frame.readings.wifi, frame.readings.typeLabel, 1, frame.readings.wifi >= 0).parts
                        : actual.ring(67, false, frame.readings.wifi, frame.readings.typeLabel, 1, frame.readings.wifi >= 0).parts;
                check(parts[0] == expectedTop[state][order], "priority, Wi-Fi yield and actual frame agree style=" + style + " state=" + state + " order=" + order);
                check((ownership.wifi >= 1) == ColorOsRingPreset.resolve(paint, 67, false, source).wifi,
                        "native Wi-Fi ownership follows actual ink");
                ColorOsBatteryDrawable.preflight(width, height, frame);
                if (state == 0 && order == 2)
                    check(frame.readings.wifi == 4 && frame.network.wifi == -1,
                            "returning native Wi-Fi retains its connection for the same priority decision");
                arranged.typeHideOnWifi = false;
                if (state == 0 && order == 0) {
                    TrioSettings keepType = ColorOsRingPreset.userLayout(arranged, false, source);
                    int top = style == Prefs.STYLE_CAPSULE
                            ? TrioAppearance.of(keepType).capsule(67, false, 4, "5G", 1, true).parts[0]
                            : TrioAppearance.of(keepType).ring(67, false, 4, "5G", 1, true).parts[0];
                    check(top == Prefs.PART_TYPE, "turning the existing hide switch off preserves type priority");
                }
            }
        }
        android.view.View wifiRoot = new android.view.View(context), iconContainer = new android.view.View(context);
        android.widget.ImageView leasedSignal = new android.widget.ImageView(context), leasedType = new android.widget.ImageView(context);
        ColorOsNetworkAdapter.Plan leaseBefore = new ColorOsNetworkAdapter.Plan(4, 4, wifiRoot, iconContainer, leasedSignal, 0, "5G", leasedType, true);
        ColorOsNetworkAdapter.Plan signalChange = new ColorOsNetworkAdapter.Plan(3, 2, wifiRoot, iconContainer, leasedSignal, 0, "4G", leasedType, true);
        check(!leaseBefore.same(signalChange) && leaseBefore.sameOwners(signalChange), "reading changes redraw without restoring native owners");
        check(leaseBefore.retainedBy(signalChange).sameOwners(leaseBefore), "strength and type text changes retain all existing leases");
        ColorOsNetworkAdapter.Plan wifiRemoved = new ColorOsNetworkAdapter.Plan(-1, 4, null, null, leasedSignal, 0, "5G", leasedType, true);
        ColorOsNetworkAdapter.Plan retained = leaseBefore.retainedBy(wifiRemoved);
        check(retained.container.get() == null && retained.signal.get() == leasedSignal && retained.hideType,
                "Wi-Fi handback retains mobile signal and type ownership");
        retained = leaseBefore.retainedBy(ColorOsNetworkAdapter.Plan.EMPTY);
        check(retained.container.get() == null && retained.signal.get() == null && !retained.hideType, "disabled rendering retains no native leases");
        android.widget.ImageView activity = new android.widget.ImageView(context);
        activity.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        activity.setImageDrawable(new android.graphics.drawable.Drawable() {
            final android.graphics.Paint paint = new android.graphics.Paint();
            @Override public void draw(Canvas c) { paint.setColor(Color.BLACK); c.drawRect(10, 6, 20, 22, paint); }
            @Override public void setAlpha(int value) { paint.setAlpha(value); }
            @Override public void setColorFilter(android.graphics.ColorFilter filter) { paint.setColorFilter(filter); }
            @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
            @Override public int getIntrinsicWidth() { return 85; }
            @Override public int getIntrinsicHeight() { return 52; }
        });
        activity.setLayoutParams(new android.widget.FrameLayout.LayoutParams(-2, -2));
        activity.measure(android.view.View.MeasureSpec.makeMeasureSpec(400, android.view.View.MeasureSpec.AT_MOST),
                android.view.View.MeasureSpec.makeMeasureSpec(52, android.view.View.MeasureSpec.EXACTLY));
        activity.layout(0, 0, activity.getMeasuredWidth(), 52);
        check(activity.getWidth() == 85, "native activity canvas reproduces 85px allocation");
        ColorOsActivityLayout activityLayout = new ColorOsActivityLayout(activity);
        activityLayout.sync(true, "stat_signal_activity_default_public");
        check(activityLayout.owned() && activity.getVisibility() == android.view.View.GONE,
                "represented mobile signal hides the native data arrows and their allocation");
        check(activity.getLayoutParams().width == -2 && activity.getScaleType() == android.widget.ImageView.ScaleType.FIT_CENTER
                && !activity.getCropToPadding(), "hiding arrows preserves native sizing and transforms");
        activity.setVisibility(android.view.View.VISIBLE);
        activityLayout.onNativeUpdate("stat_signal_activity_inout_public");
        check(activityLayout.owned() && activity.getVisibility() == android.view.View.GONE,
                "OEM direction emit is hidden synchronously instead of flashing arrows");
        activityLayout.sync(true, "stat_signal_activity_inout_public");
        check(activityLayout.owned() && activity.getVisibility() == android.view.View.GONE,
                "subsequent frames retain the data arrow handoff");
        activityLayout.restore();
        check(activity.getVisibility() == android.view.View.VISIBLE, "module release restores native data arrows");
        activityLayout.sync(false, "stat_signal_activity_in_public");
        check(!activityLayout.owned() && activity.getVisibility() == android.view.View.VISIBLE,
                "native signal mode retains its data arrows");
        activityLayout.sync(true, "unknown_warning");
        check(!activityLayout.owned() && activity.getVisibility() == android.view.View.VISIBLE,
                "unknown activity resources retain native rendering");
        activityLayout.sync(true, "stat_signal_activity_in_public");
        activity.setVisibility(android.view.View.GONE);
        activityLayout.onNativeUpdate("");
        activityLayout.restore();
        check(!activityLayout.owned() && activity.getVisibility() == android.view.View.GONE,
                "release after Wi-Fi removed native arrows does not resurrect a stale visible state");
        activity.setVisibility(android.view.View.VISIBLE);
        activityLayout.sync(true, "stat_signal_activity_out_public");
        activity.setVisibility(android.view.View.VISIBLE);
        activityLayout.onNativeUpdate("unknown_warning");
        check(!activityLayout.owned() && activity.getVisibility() == android.view.View.VISIBLE,
                "unknown resource replaces an owned arrow immediately without hiding the warning");
        activityLayout.sync(true, "stat_signal_activity_in_public");
        activity.setVisibility(android.view.View.INVISIBLE);
        activityLayout.onNativeUpdate("stat_signal_activity_in_public");
        activityLayout.restore();
        check(activity.getVisibility() == android.view.View.INVISIBLE,
                "release follows the latest OEM visibility rather than the initial value");
        activity.setVisibility(android.view.View.VISIBLE);
        activityLayout.sync(true, "stat_signal_activity_in_public");
        activity.getLayoutParams().width = 123;
        activityLayout.restore();
        check(activity.getLayoutParams().width == 123, "later native activity sizing is preserved");
        for (String name : new String[]{"stat_signal_activity_default_public", "stat_signal_activity_in_public",
                "stat_signal_activity_out_public", "stat_signal_activity_inout_public"}) {
            activityLayout.sync(true, name);
            check(activity.getVisibility() == android.view.View.GONE, "ordinary activity direction hidden: " + name);
            activityLayout.restore();
        }
        System.out.println("Android config/Canvas probe: " + checks + " checks passed");
    }

    private static int[] drawSignal(TrioSettings settings, int signal) {
        Bitmap bitmap = Bitmap.createBitmap(85, 52, Bitmap.Config.ARGB_8888);
        try {
            ColorOsBatteryDrawable drawable = new ColorOsBatteryDrawable(
                    new android.graphics.drawable.ColorDrawable(Color.BLACK),
                    new ColorOsBatteryDrawable.Frame(67, Color.BLACK, settings,
                            new ColorOsNetworkAdapter.Plan(4, signal, null, null, null)), () -> {}, frame -> {});
            drawable.setBounds(0, 0, 85, 52);
            drawable.draw(new Canvas(bitmap));
            int[] pixels = new int[85 * 52];
            bitmap.getPixels(pixels, 0, 85, 0, 0, 85, 52);
            return pixels;
        } finally { bitmap.recycle(); }
    }
}
