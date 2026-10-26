/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

/** Readings which the current ring can represent without dropping native warnings. */
final class ColorOsNetworkPolicy {
    static boolean dataSettingChanged(String name) {
        return name == null || name.equals("global") || name.startsWith("mobile_data")
                || name.equals("multi_sim_data_call") || name.equals("airplane_mode_on");
    }

    static String typeLabel(String nativeLabel, Boolean dataEnabled) {
        // A registered radio technology is not an enabled data connection. Unknown
        // permission/device state keeps the old observed label rather than guessing.
        return Boolean.FALSE.equals(dataEnabled) ? "" : nativeLabel;
    }

    static int wifi(boolean active, boolean validated, boolean visible, int level) {
        return active && validated && visible && level >= 1 && level <= 4 ? level : -1;
    }

    static int mobile(boolean cellular, boolean inService, int inetCondition,
                      boolean changing, boolean softSim, int level, int levels) {
        // OPlus renders Cellular with inetCondition, not AOSP's showExclamationMark.
        // The latter also becomes true when data is deliberately disabled on working Wi-Fi.
        return cellular && inService && inetCondition == 1 && !changing && !softSim
                && levels == 5 && level >= 0 && level <= 4 ? level : -1;
    }
}
