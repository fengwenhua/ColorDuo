/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import java.util.Arrays;
import java.util.UUID;
import java.util.HashMap;
import java.util.Map;
import io.github.fengwenhua.colorduo.ui.SettingsRepository;

/** Real storage, configuration transport, Canvas and native-icon lease regressions. */
final class ChargeRegressionProbe {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    private static int centre(TrioSettings s, boolean charging, boolean quick) {
        TrioSettings active = ChargeLayout.forState(s, charging, quick);
        TrioAppearance a = TrioAppearance.of(active);
        return (a.capsule ? a.capsule(88, charging, 3, "5G", 1, true).parts
                : a.ring(88, charging, 3, "5G", 1, true).parts)[1];
    }

    private static Bitmap render(TrioSettings s, boolean charging, boolean quick) {
        int width = s.trioStyle == Prefs.STYLE_CAPSULE ? 180 : 85;
        Bitmap image = Bitmap.createBitmap(width, 52, Bitmap.Config.ARGB_8888);
        TrioRenderer.drawInto(new Canvas(image), width, 52, 88, charging, quick, false, false,
                3, 3, new int[]{3, -1}, "5G", Color.BLACK, s, false, true);
        return image;
    }

    static int run(Context context) throws Exception {
        checks = 0;
        String preferenceName = "colorduo_charge_regression";
        SharedPreferences prefs = context.getSharedPreferences(preferenceName, Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        try {
            check(FirstRunDefaults.install(prefs), "fresh install receives the captured appearance profile");
            TrioSettings firstRing = TrioSettings.from(prefs, 3.25f, Prefs.STYLE_RING);
            TrioSettings firstCapsule = TrioSettings.from(prefs, 3.25f, Prefs.STYLE_CAPSULE);
            check(firstRing.trioStyle == Prefs.STYLE_RING && firstRing.typeHideOnWifi,
                    "first-install ring and Wi-Fi type policy match the phone");
            check(Prefs.encodeLayout(firstRing.ringZones).equals("5,1|3|7|"),
                    "first-install ring exactly matches the captured layout");
            check(Prefs.encodeLayout(firstCapsule.ringZones).equals("5,1|4,3|7|6"),
                    "first-install capsule exactly matches the captured layout");
            check(centre(firstRing, true, false) == Prefs.PART_VALUE
                            && centre(firstRing, true, true) == Prefs.PART_BOLT,
                    "first-install ring keeps ordinary digits and fast-charge lightning");
            check(centre(firstCapsule, true, false) == Prefs.PART_VALUE
                            && centre(firstCapsule, true, true) == Prefs.PART_BOLT,
                    "first-install capsule keeps ordinary digits and fast-charge lightning");
            check(firstCapsule.ringZones[3][0] == Prefs.PART_CLOCK,
                    "first-install capsule keeps the phone clock placement");
            check(!FirstRunDefaults.install(prefs), "defaults are not reinstalled on a later launch");
            prefs.edit().putBoolean(Prefs.KEY_SHOW_WIFI, false).putString(Prefs.KEY_RING_LAYOUT, "|||").commit();
            check(!FirstRunDefaults.install(prefs) && !prefs.getBoolean(Prefs.KEY_SHOW_WIFI, true)
                            && prefs.getString(Prefs.KEY_RING_LAYOUT, "").equals("|||"),
                    "upgrade preserves explicitly disabled components and empty custom layouts");
            prefs.edit().clear().putBoolean(Prefs.KEY_ENABLED, false).commit();
            check(!FirstRunDefaults.install(prefs) && prefs.getAll().size() == 1,
                    "an existing minimal configuration also remains untouched");
            prefs.edit().clear().commit();
            for (int style : new int[]{Prefs.STYLE_RING, Prefs.STYLE_CAPSULE}) {
                TrioSettings base = TrioSettings.defaults();
                base.trioStyle = style;
                base.showWifi = base.showMobile = base.showSim1 = base.showValue = true;
                base.ringZones = Prefs.decodeLayout("1|3|7|6");
                check(centre(base, true, false) == Prefs.PART_VALUE, "legacy ordinary charge layout retained");
                check(centre(base, true, true) == Prefs.PART_VALUE, "legacy fast charge layout retained");
                TrioSettings ordinary = ChargeLayout.forScene(base, ChargeLayout.CHARGING);
                ordinary.ringZones = Prefs.decodeLayout("1|3|7|6");
                ordinary.chargingShowsValue = false;
                TrioSettings fast = ChargeLayout.forScene(base, ChargeLayout.QUICK);
                fast.ringZones = Prefs.decodeLayout("1|4|7|6");
                fast.chargingShowsValue = false;
                base.chargingLayout = ChargeLayout.encode(ordinary);
                base.quickChargingLayout = ChargeLayout.encode(fast);
                base.chargingShowsValue = true; // A legacy global override must not defeat an explicit scene.
                check(centre(base, true, false) == Prefs.PART_VALUE, "ordinary charge keeps number");
                check(centre(base, true, true) == Prefs.PART_BOLT, "fast charge keeps bolt");
                check(centre(base, false, true) == Prefs.PART_VALUE, "fast flag without charging uses base");
                check(centre(base, false, false) == Prefs.PART_VALUE, "unplug returns to base layout");
                check(base.ringZones[1][0] == Prefs.PART_VALUE, "scene resolution never mutates base");
                check(ChargeLayout.forState(base, true, true).editingChargeScene == ChargeLayout.QUICK,
                        "scene routing survives a renderer/editor snapshot");
                check(ChargeLayout.forState(base, true, true).copy().editingChargeScene == ChargeLayout.QUICK,
                        "delayed edit callback keeps its original scene");
                check(base.copy().equals(base), "copy preserves both charge arrangements");
                TrioSettings transported = ConfigPacket.decode(ConfigPacket.encode(UUID.randomUUID().toString(), 17, base)).settings;
                check(transported.equals(base), "both arrangements survive real JSON/Bundle transport");
                check(centre(transported, true, true) == Prefs.PART_BOLT, "transport retains fast-charge placement");
                check(transported.editingChargeScene == ChargeLayout.BASE, "editor routing never leaks into stored settings");
                TrioSettings changed = base.copy();
                changed.quickChargingLayout = "";
                check(!changed.equals(base), "fast layout participates in equality");
                Bundle partial = new Bundle();
                partial.putBoolean(Prefs.KEY_ENABLED, false);
                TrioSettings updated = TrioSettings.fromBundle(partial, base);
                check(updated.chargingLayout.equals(base.chargingLayout)
                        && updated.quickChargingLayout.equals(base.quickChargingLayout), "unrelated delta keeps both arrangements");
                TrioSettings remote = base.copy();
                remote.quickChargingLayout = "";
                check(remote.applyKeyFrom(base, Prefs.styledKey(Prefs.KEY_QUICK_CHARGING_LAYOUT, style))
                        && remote.quickChargingLayout.equals(base.quickChargingLayout), "remote key update carries the matching scene");
                check(!Prefs.styledKey(Prefs.KEY_CHARGING_LAYOUT, style)
                        .equals(Prefs.styledKey(Prefs.KEY_QUICK_CHARGING_LAYOUT, style)), "charging and quick storage keys differ");
                prefs.edit().putInt(Prefs.KEY_TRIO_STYLE, style)
                        .putBoolean(Prefs.styledKey(Prefs.KEY_SHOW_VALUE, style), true)
                        .putString(Prefs.styledKey(Prefs.KEY_RING_LAYOUT, style), "1|3|7|6")
                        .putString(Prefs.styledKey(Prefs.KEY_CHARGING_LAYOUT, style), base.chargingLayout)
                        .putString(Prefs.styledKey(Prefs.KEY_QUICK_CHARGING_LAYOUT, style), base.quickChargingLayout).commit();
                TrioSettings stored = TrioSettings.from(prefs, 3.25f);
                check(centre(stored, true, false) == Prefs.PART_VALUE && centre(stored, true, true) == Prefs.PART_BOLT,
                        "real preferences retain the two arrangements independently");
                Bitmap chargeImage = render(base, true, false), fastImage = render(base, true, true);
                Bitmap resolvedFastImage = render(ChargeLayout.forState(base, true, true), true, true);
                check(!chargeImage.sameAs(fastImage), "ordinary and fast charge produce different pixels");
                check(fastImage.sameAs(resolvedFastImage), "raw and resolved snapshots render identical fast-charge pixels");
                chargeImage.recycle(); fastImage.recycle(); resolvedFastImage.recycle();
                ColorOsNetworkAdapter.Plan source = new ColorOsNetworkAdapter.Plan(3, 3, null, null, null, 0, "5G", null, true);
                ColorOsPowerSource.State state = new ColorOsPowerSource.State(true, true, true, true, false, false);
                ColorOsBatteryDrawable.Frame frame = new ColorOsBatteryDrawable.Frame(88, Color.BLACK, base, source,
                        state, "19:41", style == Prefs.STYLE_CAPSULE ? 180 : 85, 52);
                check(frame.settings.ringZones[1][0] == Prefs.PART_BOLT, "ColorOS frame resolves actual fast charge");
                ColorOsBatteryDrawable.preflight(frame.viewWidth, 52, frame);
                int otherStyle = style == Prefs.STYLE_RING ? Prefs.STYLE_CAPSULE : Prefs.STYLE_RING;
                TrioSettings other = TrioSettings.defaults(); other.trioStyle = otherStyle;
                other.quickChargingLayout = ChargeLayout.encode(ordinary);
                TrioSettings tile = base.withComponentsFrom(other, otherStyle);
                check(tile.quickChargingLayout.equals(other.quickChargingLayout)
                        && tile.editingChargeScene == ChargeLayout.BASE, "inactive style tile gets its own charge arrangement");
            }
            prefs.edit().clear().putString(Prefs.KEY_CHARGING_LAYOUT, "1;4;0;0;|3||").commit();
            check(TrioSettings.from(prefs, 3.25f, Prefs.STYLE_CAPSULE).chargingLayout.isEmpty(),
                    "ring charge override never leaks into an unedited capsule");
            check(ChargeLayout.normalize("").isEmpty(), "missing scene inherits legacy layout");
            check(ChargeLayout.normalize("broken").isEmpty(), "malformed scene safely falls back");
            check(ChargeLayout.normalize("1;999999;0;0;|||").isEmpty(), "invalid flags rejected");
            check(ChargeLayout.normalize("1;4;999;0;|||").isEmpty(), "invalid type rejected");
            check(ChargeLayout.normalize("2;4;0;0;|||").isEmpty(), "unknown scene schema rejected");
            TrioSettings empty = TrioSettings.defaults(); empty.ringZones = Prefs.decodeLayout("|||");
            check(!ChargeLayout.encode(empty).isEmpty(), "explicit empty scene differs from inheritance");

            ImageView nativeIcon = new ImageView(context);
            nativeIcon.setVisibility(View.VISIBLE);
            ColorOsChargeIcon icon = new ColorOsChargeIcon(nativeIcon);
            check(!icon.owned() && nativeIcon.getVisibility() == View.VISIBLE, "mount alone never hides native charge");
            icon.commit(true);
            check(icon.owned() && nativeIcon.getVisibility() == View.GONE, "drawn charging frame removes icon and its space");
            nativeIcon.setVisibility(View.VISIBLE); icon.onNativeUpdate();
            check(nativeIcon.getVisibility() == View.GONE && icon.nativeVisibility() == View.VISIBLE,
                    "native charging refresh stays hidden and records latest state");
            icon.restore();
            check(!icon.owned() && nativeIcon.getVisibility() == View.VISIBLE, "disable/failure restores latest native charge");
            icon.commit(true);
            nativeIcon.setVisibility(View.GONE); icon.onNativeUpdate(); icon.restore();
            check(nativeIcon.getVisibility() == View.GONE, "unplug while owned never restores a stale lightning icon");
            nativeIcon.setVisibility(View.VISIBLE); icon.commit(true); icon.commit(false);
            check(nativeIcon.getVisibility() == View.VISIBLE && !icon.owned(), "non-charging frame returns native ownership");
            icon.restore();
            check(nativeIcon.getVisibility() == View.VISIBLE, "repeated release is harmless");
        } finally {
            prefs.edit().clear().commit();
            context.deleteSharedPreferences(preferenceName);
        }
        repository(context);
        return checks;
    }

    /** Exercise the actual editor repository against a private test file and sink. */
    private static void repository(Context context) {
        String name = "colorduo_repository_probe";
        SharedPreferences prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        BufferedSettings buffer = new BufferedSettings(prefs, patch -> {
            SharedPreferences.Editor edit = prefs.edit();
            patch.forEach((key, value) -> {
                if (value == null) edit.remove(key);
                else if (value instanceof String) edit.putString(key, (String) value);
                else if (value instanceof Boolean) edit.putBoolean(key, (Boolean) value);
                else if (value instanceof Integer) edit.putInt(key, (Integer) value);
                else throw new AssertionError("unexpected probe value");
            });
            return edit.commit();
        });
        try { repository(context, buffer); }
        finally {
            buffer.flushBlocking(); buffer.closeForProbe();
            context.deleteSharedPreferences(name);
        }
    }

    private static void repository(Context context, BufferedSettings prefs) {
        SettingsRepository repository = new SettingsRepository(context, ChargeLayout.BASE, prefs);
        TrioSettings before = repository.read();
        Map<String, Object> saved = new HashMap<>();
        Map<String, ?> all = prefs.getAll();
        for (String base : Prefs.STYLED_KEYS) {
            String key = Prefs.styledKey(base, before.trioStyle);
            saved.put(key, all.get(key));
        }
        saved.put(Prefs.KEY_CHARGING_SHOWS_VALUE, all.get(Prefs.KEY_CHARGING_SHOWS_VALUE));
        try {
            SettingsRepository ordinary = repository.forChargeScene(ChargeLayout.CHARGING);
            SettingsRepository fast = repository.forChargeScene(ChargeLayout.QUICK);
            ordinary.applyRingAssignment(TrioZones.withPiece(ordinary.read(), 1, TrioZones.Part.VALUE), true);
            fast.applyRingAssignment(TrioZones.withPiece(fast.read(), 1, TrioZones.Part.BOLT), true);
            check(centre(ordinary.read(), true, false) == Prefs.PART_VALUE,
                    "actual repository keeps ordinary-charge number after editing fast charge");
            check(centre(fast.read(), true, true) == Prefs.PART_BOLT,
                    "actual repository keeps fast-charge bolt independently");
            check(Arrays.deepEquals(repository.read().ringZones, before.ringZones),
                    "actual scene edits leave the base layout untouched");
            ordinary.applyRingAssignment(TrioZones.withPiece(ordinary.read(), 1, TrioZones.Part.WIFI), true);
            check(ordinary.read().ringZones[1][0] == Prefs.PART_WIFI
                            && fast.read().ringZones[1][0] == Prefs.PART_BOLT,
                    "editing ordinary charge cannot overwrite fast charge");
            fast.setShowValue(false);
            ordinary.setShowValue(true);
            check(ordinary.read().showValue && !fast.read().showValue,
                    "component switches in their sheets also follow the selected scene");
            fast.setChargingShowsValue(false);
            ordinary.setChargingShowsValue(true);
            check(ordinary.read().chargingShowsValue && !fast.read().chargingShowsValue,
                    "legacy centre override can be set separately for each charging scene");
        } finally {
            SharedPreferences.Editor edit = prefs.edit();
            for (Map.Entry<String, Object> entry : saved.entrySet()) {
                Object value = entry.getValue(); String key = entry.getKey();
                if (value == null) edit.remove(key);
                else if (value instanceof String) edit.putString(key, (String) value);
                else if (value instanceof Boolean) edit.putBoolean(key, (Boolean) value);
                else if (value instanceof Integer) edit.putInt(key, (Integer) value);
                else throw new AssertionError("unexpected component preference type");
            }
            if (!edit.commit()) throw new AssertionError("could not restore component preferences");
            check(prefs.flushBlocking(), "isolated repository edits flushed");
        }
        check(repository.read().equals(before), "repository probe restores all original user settings");
    }
}
