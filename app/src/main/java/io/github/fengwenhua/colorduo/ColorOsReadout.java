/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import android.os.BatteryManager;

/** Battery-side power for the OPlus GKI battery supply used by this ColorOS adapter. */
final class ColorOsReadout {
    private ColorOsReadout() {}

    /** A connected cable is not enough: full, paused and unknown states are not charging. */
    static boolean charging(int status, int plugged) {
        return plugged != 0 && status == BatteryManager.BATTERY_STATUS_CHARGING;
    }

    /**
     * OPlus exposes GAUGE_ITEM_CURR in milliamps through CURRENT_NOW, despite Android's
     * microamp contract. Keep this conversion separate from HyperOS and use the same
     * unit even near full charge; guessing units from the current's magnitude is unsafe.
     * The voltage in BATTERY_CHANGED remains millivolts.
     */
    static float wattsOf(int currentMilliAmps, int voltageMilliVolts) {
        if (currentMilliAmps == Integer.MIN_VALUE || voltageMilliVolts <= 0) return Float.NaN;
        return Math.abs((long) currentMilliAmps) * voltageMilliVolts / 1_000_000f;
    }
}
