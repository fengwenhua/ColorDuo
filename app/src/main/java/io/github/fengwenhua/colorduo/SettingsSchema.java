/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import android.os.Bundle;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;

/** Wire registry. Historical preference migration stays in TrioSettings; tests enforce parity. */
final class SettingsSchema {
    static final int VERSION = 1;
    enum Kind { BOOLEAN, INT, STRING }
    enum Group { CONTENT, RING_LAYOUT, CAPSULE_LAYOUT, APPEARANCE, GENERAL }
    record Entry(String key, String field, Kind kind, int minimum, IntUnaryOperator maximum,
                 Function<TrioSettings, Object> read) {
        boolean styled() { return Prefs.STYLED_KEYS.contains(key); }
        boolean chargeScene() { return styled() || key.equals(Prefs.KEY_CHARGING_SHOWS_VALUE); }
        String wireKey(int style) { return Prefs.styledKey(key, style); }
        Object defaultValue() { return read.apply(TrioSettings.defaults()); }
        Group group() {
            if (field.startsWith("capsule")) return Group.CAPSULE_LAYOUT;
            if (field.equals("ringZones") || field.endsWith("ChargingLayout") || field.equals("chargingLayout")) return Group.RING_LAYOUT;
            if (styled() || field.startsWith("show") || field.equals("typeHideOnWifi")) return Group.CONTENT;
            if (kind == Kind.INT || field.equals("fontPath") || field.equals("roleColors")) return Group.APPEARANCE;
            return Group.GENERAL;
        }
        Object normalize(Object value, int style) {
            if (kind == Kind.BOOLEAN && value instanceof Boolean) return value;
            if (kind == Kind.STRING && value instanceof String && ((String) value).length() <= 8192) return value;
            if (kind == Kind.INT && value instanceof Integer) {
                int number = (Integer) value;
                if (key.equals(Prefs.KEY_TRIO_STYLE)) return Prefs.normalizeTrioStyle(number);
                if (key.equals(Prefs.KEY_NAV_STYLE)) return Prefs.normalizeNavStyle(number);
                if (key.equals(Prefs.KEY_NAV_LABELS)) return Prefs.normalizeNavLabels(number);
                return Prefs.clamp(number, minimum, maximum.applyAsInt(style));
            }
            throw new IllegalArgumentException("invalid type: " + key);
        }
    }
    static final List<Entry> ENTRIES = List.of(
        entry(Prefs.KEY_NAV_STYLE, "navStyle", Kind.INT, Integer.MIN_VALUE, style -> Integer.MAX_VALUE, s -> Prefs.normalizeNavStyle(s.navStyle)),
        entry(Prefs.KEY_NAV_LABELS, "navLabels", Kind.INT, Integer.MIN_VALUE, style -> Integer.MAX_VALUE, s -> Prefs.normalizeNavLabels(s.navLabels)),
        entry(Prefs.KEY_TRIO_STYLE, "trioStyle", Kind.INT, Integer.MIN_VALUE, style -> Integer.MAX_VALUE, s -> Prefs.normalizeTrioStyle(s.trioStyle)),
        entry(Prefs.KEY_ENABLED, "enabled", Kind.BOOLEAN, Integer.MIN_VALUE, style -> Integer.MAX_VALUE, s -> s.enabled),
        entry(Prefs.KEY_SHOW_WIFI, "showWifi", Kind.BOOLEAN, Integer.MIN_VALUE, style -> Integer.MAX_VALUE, s -> s.showWifi),
        entry(Prefs.KEY_SHOW_MOBILE, "showMobile", Kind.BOOLEAN, Integer.MIN_VALUE, style -> Integer.MAX_VALUE, s -> s.showMobile),
        entry(Prefs.KEY_SHOW_VALUE, "showValue", Kind.BOOLEAN, Integer.MIN_VALUE, style -> Integer.MAX_VALUE, s -> s.showValue),
        entry(Prefs.KEY_SHOW_BOLT, "showBolt", Kind.BOOLEAN, Integer.MIN_VALUE, style -> Integer.MAX_VALUE, s -> s.showBolt),
        entry(Prefs.KEY_MOBILE_TYPE_MODE, "mobileTypeMode", Kind.INT, Prefs.MIN_MOBILE_TYPE_MODE, style -> Prefs.MAX_MOBILE_TYPE_MODE, s -> s.mobileTypeMode),
        entry(Prefs.KEY_VALUE_CENTRED, "valueCentred", Kind.BOOLEAN, Integer.MIN_VALUE, style -> Integer.MAX_VALUE, s -> s.valueCentred),
        entry(Prefs.KEY_RING_LAYOUT, "ringZones", Kind.STRING, Integer.MIN_VALUE, style -> Integer.MAX_VALUE, s -> Prefs.encodeLayout(s.ringZones)),
        entry(Prefs.KEY_CHARGING_LAYOUT, "chargingLayout", Kind.STRING, Integer.MIN_VALUE, style -> Integer.MAX_VALUE, s -> s.chargingLayout),
        entry(Prefs.KEY_QUICK_CHARGING_LAYOUT, "quickChargingLayout", Kind.STRING, Integer.MIN_VALUE, style -> Integer.MAX_VALUE, s -> s.quickChargingLayout),
        entry(Prefs.KEY_DUAL_SIM, "dualSim", Kind.BOOLEAN, Integer.MIN_VALUE, style -> Integer.MAX_VALUE, s -> s.dualSim),
        entry(Prefs.KEY_SIGNAL_MODE, "signalMode", Kind.INT, Prefs.MIN_SIGNAL_MODE, style -> Prefs.MAX_SIGNAL_MODE, s -> Prefs.clamp(s.signalMode, Prefs.MIN_SIGNAL_MODE, Prefs.MAX_SIGNAL_MODE)),
        entry(Prefs.KEY_STACKED_SIGNAL, "stackedSignal", Kind.BOOLEAN, Integer.MIN_VALUE, style -> Integer.MAX_VALUE, s -> s.stackedSignal),
        entry(Prefs.KEY_SHOW_SIM_1, "showSim1", Kind.BOOLEAN, Integer.MIN_VALUE, style -> Integer.MAX_VALUE, s -> s.showSim1),
        entry(Prefs.KEY_SHOW_SIM_2, "showSim2", Kind.BOOLEAN, Integer.MIN_VALUE, style -> Integer.MAX_VALUE, s -> s.showSim2),
        entry(Prefs.KEY_DATA_SIM_ONLY, "dataSimOnly", Kind.BOOLEAN, Integer.MIN_VALUE, style -> Integer.MAX_VALUE, s -> s.dataSimOnly),
        entry(Prefs.KEY_PERF_POWER, "perfPower", Kind.BOOLEAN, Integer.MIN_VALUE, style -> Integer.MAX_VALUE, s -> s.perfPower),
        entry(Prefs.KEY_PERF_TEMP, "perfTemp", Kind.BOOLEAN, Integer.MIN_VALUE, style -> Integer.MAX_VALUE, s -> s.perfTemp),
        entry(Prefs.KEY_PERF_TEMP_CHARGING_ONLY, "perfTempChargingOnly", Kind.BOOLEAN, Integer.MIN_VALUE, style -> Integer.MAX_VALUE, s -> s.perfTempChargingOnly),
        entry(Prefs.KEY_PERF_SCALE, "perfScale", Kind.INT, Prefs.MIN_PERF_SCALE, style -> Prefs.MAX_PERF_SCALE, s -> s.perfScale),
        entry(Prefs.KEY_PERF_WEIGHT, "perfWeight", Kind.INT, Prefs.MIN_PERF_WEIGHT, style -> Prefs.MAX_PERF_WEIGHT, s -> s.perfWeight),
        entry(Prefs.KEY_GLYPH_SCALE, "glyphScale", Kind.INT, Prefs.MIN_GLYPH_SCALE, style -> Prefs.MAX_GLYPH_SCALE, s -> s.glyphScale),
        entry(Prefs.KEY_CAPSULE_LEFT_DX, "capsuleLeftDx", Kind.INT, Prefs.MIN_CAPSULE_NUDGE_X, style -> Prefs.MAX_CAPSULE_NUDGE_X, s -> s.capsuleLayout.leftDx()),
        entry(Prefs.KEY_CAPSULE_LEFT_DY, "capsuleLeftDy", Kind.INT, Prefs.MIN_CAPSULE_NUDGE, style -> Prefs.MAX_CAPSULE_NUDGE, s -> s.capsuleLayout.leftDy()),
        entry(Prefs.KEY_CAPSULE_RIGHT_DX, "capsuleRightDx", Kind.INT, Prefs.MIN_CAPSULE_NUDGE_X_RIGHT, style -> Prefs.MAX_CAPSULE_NUDGE_X_RIGHT, s -> s.capsuleLayout.rightDx()),
        entry(Prefs.KEY_CAPSULE_RIGHT_DY, "capsuleRightDy", Kind.INT, Prefs.MIN_CAPSULE_NUDGE, style -> Prefs.MAX_CAPSULE_NUDGE, s -> s.capsuleLayout.rightDy()),
        entry(Prefs.KEY_CAPSULE_LENGTH, "capsuleLength", Kind.INT, Prefs.MIN_CAPSULE_LENGTH, style -> Prefs.MAX_CAPSULE_LENGTH, s -> s.capsuleLayout.length()),
        entry(Prefs.KEY_ROLE_COLORS, "roleColors", Kind.BOOLEAN, Integer.MIN_VALUE, style -> Integer.MAX_VALUE, s -> s.roleColors),
        entry(Prefs.KEY_COLOR_CRITICAL_ON_DARK, "criticalOnDark", Kind.INT, Integer.MIN_VALUE, style -> Integer.MAX_VALUE, s -> s.criticalOnDark),
        entry(Prefs.KEY_COLOR_CRITICAL_ON_LIGHT, "criticalOnLight", Kind.INT, Integer.MIN_VALUE, style -> Integer.MAX_VALUE, s -> s.criticalOnLight),
        entry(Prefs.KEY_COLOR_CHARGING_ON_DARK, "chargingOnDark", Kind.INT, Integer.MIN_VALUE, style -> Integer.MAX_VALUE, s -> s.chargingOnDark),
        entry(Prefs.KEY_COLOR_CHARGING_ON_LIGHT, "chargingOnLight", Kind.INT, Integer.MIN_VALUE, style -> Integer.MAX_VALUE, s -> s.chargingOnLight),
        entry(Prefs.KEY_COLOR_LOW_ON_DARK, "lowOnDark", Kind.INT, Integer.MIN_VALUE, style -> Integer.MAX_VALUE, s -> s.lowOnDark),
        entry(Prefs.KEY_COLOR_LOW_ON_LIGHT, "lowOnLight", Kind.INT, Integer.MIN_VALUE, style -> Integer.MAX_VALUE, s -> s.lowOnLight),
        entry(Prefs.KEY_LOW_THRESHOLD, "lowThreshold", Kind.INT, Prefs.MIN_LOW_THRESHOLD, style -> Prefs.MAX_LOW_THRESHOLD, s -> s.lowThreshold),
        entry(Prefs.KEY_RING_STROKE, "ringStroke", Kind.INT, Prefs.MIN_RING_STROKE, style -> Prefs.MAX_RING_STROKE, s -> s.ringStroke),
        entry(Prefs.KEY_ARC_STROKE, "arcStroke", Kind.INT, Prefs.MIN_ARC_STROKE, style -> Prefs.MAX_ARC_STROKE, s -> s.arcStroke),
        entry(Prefs.KEY_VALUE_SIZE, "valueSize", Kind.INT, Prefs.MIN_VALUE_SIZE, style -> Prefs.maxValueSize(style), s -> s.valueSize),
        entry(Prefs.KEY_VALUE_WEIGHT, "valueWeight", Kind.INT, Prefs.MIN_VALUE_WEIGHT, style -> Prefs.MAX_VALUE_WEIGHT, s -> s.valueWeight),
        entry(Prefs.KEY_CLOCK_SIZE, "clockSize", Kind.INT, Prefs.MIN_CLOCK_SIZE, style -> Prefs.maxClockSize(style), s -> s.clockSize),
        entry(Prefs.KEY_CLOCK_WEIGHT, "clockWeight", Kind.INT, Prefs.MIN_CLOCK_WEIGHT, style -> Prefs.MAX_CLOCK_WEIGHT, s -> s.clockWeight),
        entry(Prefs.KEY_TYPE_SIZE, "typeSize", Kind.INT, Prefs.MIN_TYPE_SIZE, style -> Prefs.maxTypeSize(style), s -> s.typeSize),
        entry(Prefs.KEY_OUT_TYPE_SIZE_DP, "outTypeSize", Kind.INT, Prefs.MIN_OUT_TYPE_SIZE_DP, style -> Prefs.MAX_OUT_TYPE_SIZE_DP, s -> s.outTypeSize),
        entry(Prefs.KEY_OUT_SIGNAL_SIZE, "outSignalSize", Kind.INT, Prefs.MIN_OUT_SIGNAL_SIZE, style -> Prefs.MAX_OUT_SIGNAL_SIZE, s -> s.outSignalSize),
        entry(Prefs.KEY_OUT_TYPE_MARGIN_LEFT, "outTypeMarginLeft", Kind.INT, Prefs.MIN_OUT_TYPE_MARGIN, style -> Prefs.MAX_OUT_TYPE_MARGIN, s -> s.outTypeMarginLeft),
        entry(Prefs.KEY_OUT_TYPE_MARGIN_RIGHT, "outTypeMarginRight", Kind.INT, Prefs.MIN_OUT_TYPE_MARGIN, style -> Prefs.MAX_OUT_TYPE_MARGIN, s -> s.outTypeMarginRight),
        entry(Prefs.KEY_OUT_SIGNAL_MARGIN, "outSignalMargin", Kind.INT, Prefs.MIN_OUT_SIGNAL_MARGIN, style -> Prefs.MAX_OUT_SIGNAL_MARGIN, s -> s.outSignalMargin),
        entry(Prefs.KEY_TYPE_SUFFIX_SCALE, "typeSuffixScale", Kind.INT, Prefs.MIN_TYPE_SUFFIX_SCALE, style -> Prefs.MAX_TYPE_SUFFIX_SCALE, s -> s.typeSuffixScale),
        entry(Prefs.KEY_BOLT_SCALE, "boltScale", Kind.INT, Prefs.MIN_BOLT_SCALE, style -> Prefs.MAX_BOLT_SCALE, s -> s.boltScale),
        entry(Prefs.KEY_WIFI_SCALE, "wifiScale", Kind.INT, Prefs.MIN_WIFI_SCALE, style -> Prefs.MAX_WIFI_SCALE, s -> s.wifiScale),
        entry(Prefs.KEY_TYPE_HIDE_ON_WIFI, "typeHideOnWifi", Kind.BOOLEAN, Integer.MIN_VALUE, style -> Integer.MAX_VALUE, s -> s.typeHideOnWifi),
        entry(Prefs.KEY_CHARGING_SHOWS_VALUE, "chargingShowsValue", Kind.BOOLEAN, Integer.MIN_VALUE, style -> Integer.MAX_VALUE, s -> s.chargingShowsValue),
        entry(Prefs.KEY_FONT_PATH, "fontPath", Kind.STRING, Integer.MIN_VALUE, style -> Integer.MAX_VALUE, s -> s.fontPath),
        entry(Prefs.KEY_DOT_SCALE, "dotScale", Kind.INT, Prefs.MIN_DOT_SCALE, style -> Prefs.MAX_DOT_SCALE, s -> s.dotScale),
        entry(Prefs.KEY_OUT_ORDER, "outOrder", Kind.STRING, Integer.MIN_VALUE, style -> Integer.MAX_VALUE, s -> Prefs.encodeOutOrder(s.outOrder)),
        entry(Prefs.KEY_TYPE_WEIGHT, "typeWeight", Kind.INT, Prefs.MIN_TYPE_WEIGHT, style -> Prefs.MAX_TYPE_WEIGHT, s -> s.typeWeight),
        entry(Prefs.KEY_TRACK_ALPHA, "trackAlpha", Kind.INT, Prefs.MIN_TRACK_ALPHA, style -> Prefs.MAX_TRACK_ALPHA, s -> s.trackAlpha),
        entry(Prefs.KEY_DEBUG_LOG, "debugLog", Kind.BOOLEAN, Integer.MIN_VALUE, style -> Integer.MAX_VALUE, s -> s.debugLog)
    );
    private static final Map<String, Entry> BY_KEY = index();
    private static Entry entry(String key, String field, Kind kind, int min, IntUnaryOperator max, Function<TrioSettings, Object> read) {
        return new Entry(key, field, kind, min, max, read);
    }
    private static Map<String, Entry> index() {
        Map<String, Entry> result = new LinkedHashMap<>();
        for (Entry entry : ENTRIES) {
            if (result.put(entry.key, entry) != null) throw new IllegalStateException("duplicate schema key");
            if (entry.styled()) result.put(entry.wireKey(Prefs.STYLE_CAPSULE), entry);
        }
        return Map.copyOf(result);
    }
    static Entry find(String wireKey) { return BY_KEY.get(wireKey); }
    static Bundle encode(TrioSettings settings) {
        Bundle result = new Bundle();
        int style = Prefs.normalizeTrioStyle(settings.trioStyle);
        for (Entry entry : ENTRIES) put(result, entry.wireKey(style), entry.read.apply(settings));
        return result;
    }
    static void put(Bundle target, String key, Object value) {
        if (value instanceof Boolean) target.putBoolean(key, (Boolean) value);
        else if (value instanceof Integer) target.putInt(key, (Integer) value);
        else if (value instanceof String) target.putString(key, (String) value);
        else throw new IllegalArgumentException("invalid schema value");
    }
    private SettingsSchema() {}
}
