/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

/** Fixed validation layout, shared with the Android rendering checks. */
final class ColorOsRingPreset {
    static TrioSettings create(TrioSettings settings, boolean outsidePercent, boolean network,
                               ColorOsNetworkAdapter.Plan plan) {
        TrioSettings paint = settings.copy();
        paint.trioStyle = Prefs.STYLE_RING;
        ContentSettings c = settings.content;
        boolean mobile = plan.mobile >= 0;
        paint.content = new ContentSettings(
                plan.wifi >= 1, mobile, c.showValue() && !outsidePercent, false,
                Prefs.MOBILE_TYPE_OFF, c.valueCentred(), c.dualSim(), Prefs.SIGNAL_IN_RING,
                c.stackedSignal(), mobile && plan.mobileSlot == 0, mobile && plan.mobileSlot == 1,
                c.typeHideOnWifi(), c.chargingShowsValue(), false, false, c.perfTempChargingOnly());
        paint.roleColors = false;
        paint.fontPath = "";
        paint.ringZones = Prefs.decodeLayout(network
                ? (paint.content.showWifi() ? "1" : "") + "|" + (paint.content.showValue() ? "3" : "") + "|"
                    + (paint.content.showMobile() ? (plan.mobileSlot == 0 ? "7" : "8") : "")
                : (paint.content.showValue() ? "3||" : Prefs.EMPTY_LAYOUT));
        return paint;
    }

    static TrioSettings userLayout(TrioSettings settings, boolean outsidePercent, ColorOsNetworkAdapter.Plan plan) {
        TrioSettings paint = settings.copy();
        ContentSettings c = settings.content;
        paint.content = new ContentSettings(
                c.showWifi() && plan.wifi >= 1, c.showMobile() && plan.mobile >= 0,
                c.showValue() && !outsidePercent, c.showBolt(),
                !plan.typeLabel.isEmpty() && c.mobileTypeMode() == Prefs.MOBILE_TYPE_IN_RING
                        ? Prefs.MOBILE_TYPE_IN_RING : Prefs.MOBILE_TYPE_OFF,
                c.valueCentred(), c.dualSim(), c.signalMode(), c.stackedSignal(),
                c.showSim1() && plan.mobile >= 0 && plan.mobileSlot == 0,
                c.showSim2() && plan.mobile >= 0 && plan.mobileSlot == 1,
                c.typeHideOnWifi(), c.chargingShowsValue(), false, false, c.perfTempChargingOnly());
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
        boolean type = (plan.hideType && (plan.suppressType || drawn.mobile || outside.hasSignal() || TrioAppearance.of(paint).ownsType()))
                || outside.hasType() || outside.suppressType;
        return plan.withSignals(drawn.wifi, drawn.mobile || outside.hasSignal()).withTypeOwnership(type);
    }

    static final class Resolved {
        final boolean wifi, mobile, clock, type;
        Resolved(boolean wifi, boolean mobile, boolean clock, boolean type) {
            this.wifi = wifi; this.mobile = mobile; this.clock = clock; this.type = type;
        }
    }

    static Resolved resolve(TrioSettings paint, int level, boolean charging, ColorOsNetworkAdapter.Plan plan) {
        TrioAppearance appearance = TrioAppearance.of(paint);
        int sims = plan.mobile >= 0 ? 1 : 0;
        if (appearance.capsule) {
            TrioAppearance.Capsule drawn = appearance.capsule(level, charging, plan.wifi, plan.typeLabel,
                    sims, plan.wifi >= 0);
            return new Resolved(drawn.wifi, drawn.dots, contains(drawn.parts, Prefs.PART_CLOCK),
                    contains(drawn.parts, Prefs.PART_TYPE));
        }
        int[] parts = appearance.ring(level, charging, plan.wifi, plan.typeLabel, sims, plan.wifi >= 0).parts;
        return new Resolved(contains(parts, Prefs.PART_WIFI),
                contains(parts, plan.mobileSlot == 0 ? Prefs.PART_SIM_1 : Prefs.PART_SIM_2),
                contains(parts, Prefs.PART_CLOCK), contains(parts, Prefs.PART_TYPE));
    }

    private static boolean contains(int[] parts, int wanted) {
        for (int part : parts) if (part == wanted) return true;
        return false;
    }
}
