/* Copyright (C) 2026 yixing233 and HyperDuo contributors
 * SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import android.content.SharedPreferences;
import android.os.Bundle;

/**
 * Immutable component visibility and placement. The first eleven values are stored per style;
 * Wi-Fi type suppression, the legacy charging-centre override and readout flags are global.
 *
 * @param mobileTypeMode network type off, inside or outside the glyph; clamped when read
 * @param valueCentred legacy enlarged ring percentage; resolved differently for rectangle/capsule
 * @param dualSim aggregate two-row signal shape, independent of the placed SIM components
 * @param signalMode signal inside the glyph or outside it; placement within a glyph uses ringZones
 * @param stackedSignal draw the module's stacked reading outside instead of the native signal
 * @param showSim1 draw SIM 1 as an independently placed component
 * @param showSim2 draw SIM 2 as an independently placed component
 * @param typeHideOnWifi suppress the mobile type while Wi-Fi is connected
 * @param chargingShowsValue legacy percentage-in-centre override while charging
 * @param perfPower display charging power in the shared outside readout strip
 * @param perfTemp display battery temperature in the same strip
 * @param perfTempChargingOnly restrict temperature to charging periods
 */
public record ContentSettings(
        boolean showWifi,
        boolean showMobile,
        boolean showValue,
        boolean showBolt,
        int mobileTypeMode,
        boolean valueCentred,
        boolean dualSim,
        int signalMode,
        boolean stackedSignal,
        boolean showSim1,
        boolean showSim2,
        boolean typeHideOnWifi,
        boolean chargingShowsValue,
        boolean perfPower,
        boolean perfTemp,
        boolean perfTempChargingOnly) {
    /** Bare snapshots historically start with false/zero, unlike preference defaults. */
    public static final ContentSettings EMPTY = new ContentSettings(
            false, false, false, false, 0, false, false, 0, false, false, false, false, false, false, false, false);
    public static final ContentSettings DEFAULT = new ContentSettings(
            Prefs.DEF_SHOW_WIFI,
            Prefs.DEF_SHOW_MOBILE,
            Prefs.DEF_SHOW_VALUE,
            Prefs.DEF_SHOW_BOLT,
            Prefs.DEF_MOBILE_TYPE_MODE,
            Prefs.DEF_VALUE_CENTRED,
            Prefs.DEF_DUAL_SIM,
            Prefs.DEF_SIGNAL_MODE,
            Prefs.DEF_STACKED_SIGNAL,
            Prefs.DEF_SHOW_SIM_1,
            Prefs.DEF_SHOW_SIM_2,
            Prefs.DEF_TYPE_HIDE_ON_WIFI,
            Prefs.DEF_CHARGING_SHOWS_VALUE,
            Prefs.DEF_PERF_POWER,
            Prefs.DEF_PERF_TEMP,
            Prefs.DEF_PERF_TEMP_CHARGING_ONLY);

    private interface Source {
        boolean contains(String key);
        boolean getBoolean(String key, boolean fallback);
        int getInt(String key, int fallback);
    }

    static ContentSettings read(SharedPreferences preferences, int style, boolean retiredMeter) {
        return read(new Source() {
            public boolean contains(String key) { return preferences.contains(key); }
            public boolean getBoolean(String key, boolean fallback) { return preferences.getBoolean(key, fallback); }
            public int getInt(String key, int fallback) { return preferences.getInt(key, fallback); }
        }, style, retiredMeter);
    }

    static ContentSettings read(Bundle bundle, int style) {
        return read(new Source() {
            public boolean contains(String key) { return bundle.containsKey(key); }
            public boolean getBoolean(String key, boolean fallback) { return bundle.getBoolean(key, fallback); }
            public int getInt(String key, int fallback) { return bundle.getInt(key, fallback); }
        }, style, false);
    }

    private static ContentSettings read(Source p, int style, boolean retiredMeter) {
        return new ContentSettings(
            readStyledBoolean(p, Prefs.KEY_SHOW_WIFI, Prefs.DEF_SHOW_WIFI, style),
            readStyledBoolean(p, Prefs.KEY_SHOW_MOBILE, Prefs.DEF_SHOW_MOBILE, style),
            readStyledBoolean(p, Prefs.KEY_SHOW_VALUE, Prefs.DEF_SHOW_VALUE, style),
            readStyledBoolean(p, Prefs.KEY_SHOW_BOLT, Prefs.DEF_SHOW_BOLT, style),
            readMobileTypeMode(p, style),
            readStyledBoolean(p, Prefs.KEY_VALUE_CENTRED, Prefs.DEF_VALUE_CENTRED, style),
            readStyledBoolean(p, Prefs.KEY_DUAL_SIM, Prefs.DEF_DUAL_SIM, style),
            Prefs.clamp(readStyledInt(p, Prefs.KEY_SIGNAL_MODE, Prefs.DEF_SIGNAL_MODE, style),
                    Prefs.MIN_SIGNAL_MODE, Prefs.MAX_SIGNAL_MODE),
            readStyledBoolean(p, Prefs.KEY_STACKED_SIGNAL, Prefs.DEF_STACKED_SIGNAL, style),
            readStyledBoolean(p, Prefs.KEY_SHOW_SIM_1, Prefs.DEF_SHOW_SIM_1, style) || retiredMeter,
            readStyledBoolean(p, Prefs.KEY_SHOW_SIM_2, Prefs.DEF_SHOW_SIM_2, style),
            p.getBoolean(Prefs.KEY_TYPE_HIDE_ON_WIFI, Prefs.DEF_TYPE_HIDE_ON_WIFI),
            p.getBoolean(Prefs.KEY_CHARGING_SHOWS_VALUE, Prefs.DEF_CHARGING_SHOWS_VALUE),
            p.getBoolean(Prefs.KEY_PERF_POWER, Prefs.DEF_PERF_POWER),
            p.getBoolean(Prefs.KEY_PERF_TEMP, Prefs.DEF_PERF_TEMP),
            p.getBoolean(Prefs.KEY_PERF_TEMP_CHARGING_ONLY, Prefs.DEF_PERF_TEMP_CHARGING_ONLY));
    }

    /** Missing capsule keys inherit the ring; a present value equal to default still wins. */
    private static String styledKey(Source source, String key, int style) {
        String own = Prefs.styledKey(key, style);
        return source.contains(own) ? own : key;
    }
    private static boolean readStyledBoolean(Source source, String key, boolean fallback, int style) {
        return source.getBoolean(styledKey(source, key, style), fallback);
    }
    private static int readStyledInt(Source source, String key, int fallback, int style) {
        return source.getInt(styledKey(source, key, style), fallback);
    }
    /** The retired boolean is consulted only when neither own nor inherited integer exists. */
    private static int readMobileTypeMode(Source source, int style) {
        String key = styledKey(source, Prefs.KEY_MOBILE_TYPE_MODE, style);
        if (source.contains(key)) {
            return Prefs.clamp(source.getInt(key, Prefs.DEF_MOBILE_TYPE_MODE),
                    Prefs.MIN_MOBILE_TYPE_MODE, Prefs.MAX_MOBILE_TYPE_MODE);
        }
        return source.getBoolean(Prefs.KEY_SHOW_MOBILE_TYPE, Prefs.DEF_SHOW_MOBILE_TYPE)
                ? Prefs.MOBILE_TYPE_IN_RING : Prefs.MOBILE_TYPE_OFF;
    }

    /** Replace the per-style components while keeping this snapshot's global readout flags. */
    public ContentSettings withStyledFrom(ContentSettings other) {
        return new ContentSettings(
            other.showWifi, other.showMobile, other.showValue, other.showBolt, other.mobileTypeMode,
            other.valueCentred, other.dualSim, other.signalMode, other.stackedSignal, other.showSim1,
            other.showSim2, typeHideOnWifi, chargingShowsValue, perfPower, perfTemp, perfTempChargingOnly);
    }

    public ContentSettings withShowWifi(boolean value) {
        if (showWifi == value) return this;
        return new ContentSettings(
                value, showMobile, showValue, showBolt, mobileTypeMode, valueCentred, dualSim, signalMode,
                stackedSignal, showSim1, showSim2, typeHideOnWifi, chargingShowsValue, perfPower, perfTemp,
                perfTempChargingOnly);
    }

    public ContentSettings withShowMobile(boolean value) {
        if (showMobile == value) return this;
        return new ContentSettings(
                showWifi, value, showValue, showBolt, mobileTypeMode, valueCentred, dualSim, signalMode,
                stackedSignal, showSim1, showSim2, typeHideOnWifi, chargingShowsValue, perfPower, perfTemp,
                perfTempChargingOnly);
    }

    public ContentSettings withShowValue(boolean value) {
        if (showValue == value) return this;
        return new ContentSettings(
                showWifi, showMobile, value, showBolt, mobileTypeMode, valueCentred, dualSim, signalMode,
                stackedSignal, showSim1, showSim2, typeHideOnWifi, chargingShowsValue, perfPower, perfTemp,
                perfTempChargingOnly);
    }

    public ContentSettings withShowBolt(boolean value) {
        if (showBolt == value) return this;
        return new ContentSettings(
                showWifi, showMobile, showValue, value, mobileTypeMode, valueCentred, dualSim, signalMode,
                stackedSignal, showSim1, showSim2, typeHideOnWifi, chargingShowsValue, perfPower, perfTemp,
                perfTempChargingOnly);
    }

    public ContentSettings withMobileTypeMode(int value) {
        if (mobileTypeMode == value) return this;
        return new ContentSettings(
                showWifi, showMobile, showValue, showBolt, value, valueCentred, dualSim, signalMode,
                stackedSignal, showSim1, showSim2, typeHideOnWifi, chargingShowsValue, perfPower, perfTemp,
                perfTempChargingOnly);
    }

    public ContentSettings withValueCentred(boolean value) {
        if (valueCentred == value) return this;
        return new ContentSettings(
                showWifi, showMobile, showValue, showBolt, mobileTypeMode, value, dualSim, signalMode,
                stackedSignal, showSim1, showSim2, typeHideOnWifi, chargingShowsValue, perfPower, perfTemp,
                perfTempChargingOnly);
    }

    public ContentSettings withDualSim(boolean value) {
        if (dualSim == value) return this;
        return new ContentSettings(
                showWifi, showMobile, showValue, showBolt, mobileTypeMode, valueCentred, value, signalMode,
                stackedSignal, showSim1, showSim2, typeHideOnWifi, chargingShowsValue, perfPower, perfTemp,
                perfTempChargingOnly);
    }

    public ContentSettings withSignalMode(int value) {
        if (signalMode == value) return this;
        return new ContentSettings(
                showWifi, showMobile, showValue, showBolt, mobileTypeMode, valueCentred, dualSim, value,
                stackedSignal, showSim1, showSim2, typeHideOnWifi, chargingShowsValue, perfPower, perfTemp,
                perfTempChargingOnly);
    }

    public ContentSettings withStackedSignal(boolean value) {
        if (stackedSignal == value) return this;
        return new ContentSettings(
                showWifi, showMobile, showValue, showBolt, mobileTypeMode, valueCentred, dualSim, signalMode,
                value, showSim1, showSim2, typeHideOnWifi, chargingShowsValue, perfPower, perfTemp,
                perfTempChargingOnly);
    }

    public ContentSettings withShowSim1(boolean value) {
        if (showSim1 == value) return this;
        return new ContentSettings(
                showWifi, showMobile, showValue, showBolt, mobileTypeMode, valueCentred, dualSim, signalMode,
                stackedSignal, value, showSim2, typeHideOnWifi, chargingShowsValue, perfPower, perfTemp,
                perfTempChargingOnly);
    }

    public ContentSettings withShowSim2(boolean value) {
        if (showSim2 == value) return this;
        return new ContentSettings(
                showWifi, showMobile, showValue, showBolt, mobileTypeMode, valueCentred, dualSim, signalMode,
                stackedSignal, showSim1, value, typeHideOnWifi, chargingShowsValue, perfPower, perfTemp,
                perfTempChargingOnly);
    }

    public ContentSettings withTypeHideOnWifi(boolean value) {
        if (typeHideOnWifi == value) return this;
        return new ContentSettings(
                showWifi, showMobile, showValue, showBolt, mobileTypeMode, valueCentred, dualSim, signalMode,
                stackedSignal, showSim1, showSim2, value, chargingShowsValue, perfPower, perfTemp,
                perfTempChargingOnly);
    }

    public ContentSettings withChargingShowsValue(boolean value) {
        if (chargingShowsValue == value) return this;
        return new ContentSettings(
                showWifi, showMobile, showValue, showBolt, mobileTypeMode, valueCentred, dualSim, signalMode,
                stackedSignal, showSim1, showSim2, typeHideOnWifi, value, perfPower, perfTemp,
                perfTempChargingOnly);
    }

    public ContentSettings withPerfPower(boolean value) {
        if (perfPower == value) return this;
        return new ContentSettings(
                showWifi, showMobile, showValue, showBolt, mobileTypeMode, valueCentred, dualSim, signalMode,
                stackedSignal, showSim1, showSim2, typeHideOnWifi, chargingShowsValue, value, perfTemp,
                perfTempChargingOnly);
    }

    public ContentSettings withPerfTemp(boolean value) {
        if (perfTemp == value) return this;
        return new ContentSettings(
                showWifi, showMobile, showValue, showBolt, mobileTypeMode, valueCentred, dualSim, signalMode,
                stackedSignal, showSim1, showSim2, typeHideOnWifi, chargingShowsValue, perfPower, value,
                perfTempChargingOnly);
    }

    public ContentSettings withPerfTempChargingOnly(boolean value) {
        if (perfTempChargingOnly == value) return this;
        return new ContentSettings(
                showWifi, showMobile, showValue, showBolt, mobileTypeMode, valueCentred, dualSim, signalMode,
                stackedSignal, showSim1, showSim2, typeHideOnWifi, chargingShowsValue, perfPower, perfTemp,
                value);
    }
}
