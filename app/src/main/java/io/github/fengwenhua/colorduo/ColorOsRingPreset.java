/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

/** Fixed validation layout, shared with the Android rendering checks. */
final class ColorOsRingPreset {
    static TrioSettings create(TrioSettings settings, boolean outsidePercent, boolean network,
                               ColorOsNetworkAdapter.Plan plan) {
        TrioSettings paint = settings.copy();
        paint.trioStyle = Prefs.STYLE_RING;
        paint.showWifi = plan.wifi >= 1;
        paint.showMobile = plan.mobile >= 0;
        paint.showSim1 = paint.showMobile && plan.mobileSlot == 0;
        paint.showSim2 = paint.showMobile && plan.mobileSlot == 1;
        paint.signalMode = Prefs.SIGNAL_IN_RING;
        paint.showBolt = false;
        paint.showValue = settings.showValue && !outsidePercent;
        paint.mobileTypeMode = Prefs.MOBILE_TYPE_OFF;
        paint.perfPower = paint.perfTemp = false;
        paint.roleColors = false;
        paint.fontPath = "";
        paint.ringZones = Prefs.decodeLayout(network
                ? (paint.showWifi ? "1" : "") + "|" + (paint.showValue ? "3" : "") + "|"
                    + (paint.showMobile ? (plan.mobileSlot == 0 ? "7" : "8") : "")
                : (paint.showValue ? "3||" : Prefs.EMPTY_LAYOUT));
        return paint;
    }

    static TrioSettings userLayout(TrioSettings settings, boolean outsidePercent, ColorOsNetworkAdapter.Plan plan) {
        return userLayout(settings, outsidePercent, plan, false);
    }

    static TrioSettings userLayout(TrioSettings settings, boolean outsidePercent, ColorOsNetworkAdapter.Plan plan, boolean outsideCharge) {
        TrioSettings paint = settings.copy();
        paint.showWifi = settings.showWifi && plan.wifi >= 1;
        paint.showMobile = settings.showMobile && plan.mobile >= 0;
        paint.showSim1 = settings.showSim1 && plan.mobile >= 0 && plan.mobileSlot == 0;
        paint.showSim2 = settings.showSim2 && plan.mobile >= 0 && plan.mobileSlot == 1;
        paint.showValue = settings.showValue && !outsidePercent;
        paint.showBolt = settings.showBolt && !outsideCharge;
        paint.mobileTypeMode = !plan.typeLabel.isEmpty() && settings.mobileTypeMode == Prefs.MOBILE_TYPE_IN_RING
                ? Prefs.MOBILE_TYPE_IN_RING : Prefs.MOBILE_TYPE_OFF;
        paint.perfPower = paint.perfTemp = false;
        paint.fontPath = "";
        return paint;
    }

    static ColorOsNetworkAdapter.Plan represented(TrioSettings paint, int level, boolean charging,
                                                 ColorOsNetworkAdapter.Plan plan) {
        return represented(paint, level, charging, plan, ColorOsOutsideLayout.EMPTY);
    }

    static ColorOsNetworkAdapter.Plan represented(TrioSettings paint, int level, boolean charging,
                                                 ColorOsNetworkAdapter.Plan plan, ColorOsOutsideLayout outside) {
        Resolved drawn = resolve(paint, level, charging, plan);
        boolean type = (plan.hideType && (drawn.mobile || outside.hasSignal() || TrioAppearance.of(paint).ownsType()))
                || outside.hasType() || outside.suppressType;
        return plan.withSignals(drawn.wifi, drawn.mobile || outside.hasSignal()).withTypeOwnership(type);
    }

    static final class Resolved {
        final boolean wifi, mobile, clock;
        Resolved(boolean wifi, boolean mobile, boolean clock) {
            this.wifi = wifi; this.mobile = mobile; this.clock = clock;
        }
    }

    static Resolved resolve(TrioSettings paint, int level, boolean charging, ColorOsNetworkAdapter.Plan plan) {
        TrioAppearance appearance = TrioAppearance.of(paint);
        int sims = plan.mobile >= 0 ? 1 : 0;
        if (appearance.capsule) {
            TrioAppearance.Capsule drawn = appearance.capsule(level, charging, plan.wifi, plan.typeLabel,
                    sims, plan.wifi >= 0);
            return new Resolved(drawn.wifi, drawn.dots, contains(drawn.parts, Prefs.PART_CLOCK));
        }
        int[] parts = appearance.ring(level, charging, plan.wifi, plan.typeLabel, sims, plan.wifi >= 0).parts;
        return new Resolved(contains(parts, Prefs.PART_WIFI),
                contains(parts, plan.mobileSlot == 0 ? Prefs.PART_SIM_1 : Prefs.PART_SIM_2),
                contains(parts, Prefs.PART_CLOCK));
    }

    private static boolean contains(int[] parts, int wanted) {
        for (int part : parts) if (part == wanted) return true;
        return false;
    }
}
