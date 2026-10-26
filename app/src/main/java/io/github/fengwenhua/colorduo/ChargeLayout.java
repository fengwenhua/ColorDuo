/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

/** Per-style charging arrangements; sizes and colours remain shared with the base layout. */
public final class ChargeLayout {
    public static final int BASE = 0, CHARGING = 1, QUICK = 2;
    private static final int MAX_FLAGS = 1023;

    private ChargeLayout() {}

    public static int scene(boolean charging, boolean quick) {
        return charging ? (quick ? QUICK : CHARGING) : BASE;
    }

    public static String key(int scene) {
        if (scene == CHARGING) return Prefs.KEY_CHARGING_LAYOUT;
        if (scene == QUICK) return Prefs.KEY_QUICK_CHARGING_LAYOUT;
        throw new IllegalArgumentException("Not a charging scene");
    }

    /** Empty means inherit the existing arrangement; an explicitly empty arrangement is encoded. */
    public static String encode(TrioSettings s) {
        int flags = (s.content.showWifi() ? 1 : 0) | (s.content.showMobile() ? 2 : 0) | (s.content.showValue() ? 4 : 0)
                | (s.content.showBolt() ? 8 : 0) | (s.content.showSim1() ? 16 : 0) | (s.content.showSim2() ? 32 : 0)
                | (s.content.dualSim() ? 64 : 0) | (s.content.valueCentred() ? 128 : 0)
                | (s.content.stackedSignal() ? 256 : 0) | (s.content.chargingShowsValue() ? 512 : 0);
        return "1;" + flags + ";" + s.content.mobileTypeMode() + ";" + s.content.signalMode() + ";"
                + Prefs.encodeLayout(s.ringZones);
    }

    public static String normalize(String raw) {
        TrioSettings decoded = decode(raw, null);
        return decoded == null ? "" : encode(decoded);
    }

    private static TrioSettings decode(String raw, TrioSettings base) {
        if (raw == null || raw.isEmpty() || raw.length() > 1024) return null;
        String[] fields = raw.split(";", -1);
        if (fields.length != 5 || !fields[0].equals("1")) return null;
        try {
            int flags = Integer.parseInt(fields[1]);
            int type = Integer.parseInt(fields[2]), signal = Integer.parseInt(fields[3]);
            if (flags < 0 || flags > MAX_FLAGS || type < Prefs.MOBILE_TYPE_OFF
                    || type > Prefs.MOBILE_TYPE_OUT_RING || signal < Prefs.MIN_SIGNAL_MODE
                    || signal > Prefs.MAX_SIGNAL_MODE) return null;
            TrioSettings s = base == null ? TrioSettings.defaults() : base.copy();
            ContentSettings c = s.content;
            s.content = new ContentSettings(
                    (flags & 1) != 0, (flags & 2) != 0, (flags & 4) != 0, (flags & 8) != 0,
                    type, (flags & 128) != 0, (flags & 64) != 0, signal, (flags & 256) != 0,
                    (flags & 16) != 0, (flags & 32) != 0, c.typeHideOnWifi(), (flags & 512) != 0,
                    c.perfPower(), c.perfTemp(), c.perfTempChargingOnly());
            s.ringZones = Prefs.decodeLayout(fields[4]);
            return s;
        } catch (NumberFormatException invalid) { return null; }
    }

    public static TrioSettings forState(TrioSettings base, boolean charging, boolean quick) {
        return forScene(base, scene(charging, quick));
    }

    /** The editor carries its scene on the snapshot so a delayed callback writes the right key. */
    public static TrioSettings forScene(TrioSettings base, int scene) {
        if (scene == BASE || base.editingChargeScene == scene) return base;
        String raw = scene == QUICK ? base.quickChargingLayout : base.chargingLayout;
        TrioSettings s = decode(raw, base);
        if (s == null) s = base.copy();
        s.editingChargeScene = scene;
        return s;
    }
}
