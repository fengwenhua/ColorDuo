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

import android.view.View;
import java.lang.reflect.*;
import java.util.*;

public final class RingHandoffTest {
    static int checks;
    static void ok(boolean value, String message) {
        checks++; if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        TrioSettings s = TrioSettings.defaults(); s.enabled = true;
        s.ringZones = Prefs.decodeLayout("1||");
        TrioConfig.settings = s;
        View wifi = new View(); wifi.slot = "wifi";
        HandoffHooks.hideIcon(wifi);
        s.ringZones = Prefs.decodeLayout("||"); // The user releases Wi-Fi before the posted hide executes.
        wifi.flush();
        ok(wifi.getVisibility() == View.VISIBLE && !HandoffHooks.COLLAPSED.containsKey(wifi), "stale posted hide leaves released Wi-Fi visible");
        s.ringZones = Prefs.decodeLayout("1||");
        HandoffHooks.hideIcon(wifi); wifi.flush();
        ok(wifi.getVisibility() == View.GONE && HandoffHooks.COLLAPSED.containsKey(wifi), "owned hide records restoration bookkeeping");
        View nativeHidden = new View(); nativeHidden.slot = "wifi"; nativeHidden.setVisibility(View.GONE);
        HandoffHooks.hideIcon(nativeHidden); nativeHidden.flush();
        ok(!HandoffHooks.COLLAPSED.containsKey(nativeHidden), "native-hidden view is not claimed for forced restoration");

        Bar row = new Bar(); row.mTintedIconManager.mBlockList.add("vpn");
        row.mTintedIconManager.mBlockList.add("mobile"); // Native policy owns this one.
        HandoffHooks.blockIcons(row);
        ok(row.mTintedIconManager.mBlockList.containsAll(Arrays.asList("vpn", "mobile", "wifi")), "keyguard combines module and native policy");
        s.ringZones = Prefs.decodeLayout("||");
        HandoffHooks.blockIcons(row);
        ok(row.mTintedIconManager.mBlockList.equals(Arrays.asList("vpn", "mobile")), "release removes only module additions");
        s.ringZones = Prefs.decodeLayout("1||"); HandoffHooks.blockIcons(row);
        s.enabled = false; HandoffHooks.blockIcons(row);
        ok(row.mTintedIconManager.mBlockList.equals(Arrays.asList("vpn", "mobile")), "master off releases keyguard suppression");

        s.enabled = true; s.ringZones = Prefs.decodeLayout("1||");
        HandoffHooks.island = true;
        ok(!HandoffHooks.foldedSlots().contains("wifi"), "island releases unavailable ring Wi-Fi");
        Bar lockscreen = new Bar();
        HandoffHooks.KEYGUARD_ROWS.put(lockscreen, true);
        HandoffHooks.island = false;
        HandoffHooks.syncKeyguardRows(); lockscreen.flush();
        ok(lockscreen.mTintedIconManager.mBlockList.contains("wifi"), "lockscreen initially suppresses owned Wi-Fi");
        HandoffHooks.island = true;
        HandoffHooks.syncKeyguardRows(); lockscreen.flush();
        ok(!lockscreen.mTintedIconManager.mBlockList.contains("wifi"), "island releases lockscreen Wi-Fi without a settings edit");
        HandoffHooks.island = false;
        HandoffHooks.syncKeyguardRows(); lockscreen.flush();
        ok(lockscreen.mTintedIconManager.mBlockList.contains("wifi"), "lockscreen ownership returns after island");
        HandoffHooks.syncKeyguardRows();
        s.ringZones = Prefs.decodeLayout("||"); lockscreen.flush();
        ok(!lockscreen.mTintedIconManager.mBlockList.contains("wifi"), "posted lockscreen refresh reads latest layout");
        ok(lockscreen.folds == 4, "each lockscreen refresh re-folds its containers");
        HandoffHooks.island = false;
        // Placement is the only rule: a card that is not placed leaves MIUI's icon alone, and
        // placing it is what claims the slot. The old "out of ring, stacked on/off" pair asked
        // the same question in two switches, which is why it is gone.
        s.ringZones = Prefs.decodeLayout("||");
        ok(!HandoffHooks.foldedSlots().contains("mobile"), "an unplaced card does not fold the native slot");
        s.ringZones = Prefs.decodeLayout("2||");
        ok(HandoffHooks.foldedSlots().contains("mobile"), "placing the meter claims the native slot");

        Meter meter = new Meter();
        HandoffHooks.applyMeterText(Arrays.asList(meter));
        s.ringZones = Prefs.decodeLayout("4||"); // Ownership changes after posting. The runnable must re-read.
        meter.flush();
        ok(meter.charging.getVisibility() == View.GONE, "meter handoff uses latest bolt ownership");
        s.ringZones = Prefs.decodeLayout("||"); meter.nativePercent = true;
        HandoffHooks.applyMeterText(Arrays.asList(meter)); meter.flush();
        ok(meter.percent.getVisibility() == View.VISIBLE && meter.charging.getVisibility() == View.VISIBLE,
                "released battery children follow native refresh");
        s.ringZones = Prefs.decodeLayout("3||");
        HandoffHooks.applyMeterText(Arrays.asList(meter)); meter.flush();
        ok(meter.percent.getVisibility() == View.GONE, "owned percentage remains hidden after native refresh");
        System.out.println("PASS: " + checks + " production handoff assertions");
    }
    public static final class Manager {
        public final List<String> mBlockList = new ArrayList<>();
        public void setBlockList(List<String> value) { mBlockList.clear(); mBlockList.addAll(value); }
    }
    public static final class Bar extends View {
        public final Manager mTintedIconManager = new Manager();
        int folds;
    }
    public static final class Meter extends View {
        public View charging = new View(), percent = new View();
        boolean nativePercent;
        public void refresh() {
            charging.setVisibility(TrioConfig.appearance().hidesNativeBolt() ? View.GONE : View.VISIBLE);
            percent.setVisibility(nativePercent && !TrioConfig.appearance().ownsValue() ? View.VISIBLE : View.GONE);
        }
    }
}

final class TrioConfig {
    static TrioSettings settings;
    static TrioAppearance appearance() { return TrioAppearance.of(settings); }
}

final class HandoffHooks {
    static boolean island;
    static Object sModule;
    static final List<String> WIFI_SLOTS = Arrays.asList("wifi");
    static final List<String> MOBILE_SLOTS = Arrays.asList("mobile");
    static final List<String> MANAGED_SLOTS = Arrays.asList("wifi", "mobile");
    static final Map<Object, List<String>> KEYGUARD_ADDED = new WeakHashMap<>();
    static final Map<View, Boolean> KEYGUARD_ROWS = new WeakHashMap<>();
    static final Map<View, Boolean> COLLAPSED = new WeakHashMap<>();
    static final Field sChargingViewField = Refl.field(RingHandoffTest.Meter.class, "charging");
    static final Field sPercentContainerField = Refl.field(RingHandoffTest.Meter.class, "percent");
    static final Method sUpdateChargeAndTextField = Refl.method(RingHandoffTest.Meter.class, "refresh");
    static boolean islandHideBattery() { return island; }
    static String slotOf(View view) { return view.slot; }
    static void log(Object module, String text) {}
    static void foldContainers(View row) { ((RingHandoffTest.Bar) row).folds++; }
    // PRODUCTION_METHODS
}
