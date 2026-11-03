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
        return wifi(active, validated, visible, level, "", "");
    }

    static int wifi(boolean active, boolean validated, boolean visible, int level,
                    String resource, String description) {
        // This OEM family uses exactly the same ordinary icon before/after validation.
        // Other families may carry a warning we cannot draw; keep those native.
        boolean ordinary = ("stat_signal_wifi_signal_" + level).equals(resource)
                && description != null && !description.isEmpty();
        return active && (validated || ordinary) && visible && level >= 1 && level <= 4 ? level : -1;
    }

    static int mobile(boolean cellular, boolean inService, int inetCondition,
                      boolean changing, boolean softSim, int level, int levels) {
        return mobile(cellular, inService, inetCondition, changing, softSim, level, levels, "");
    }

    static int mobile(boolean cellular, boolean inService, int inetCondition,
                      boolean changing, boolean softSim, int level, int levels, String resource) {
        // OPlus renders Cellular with inetCondition, not AOSP's showExclamationMark.
        // The latter also becomes true when data is deliberately disabled on working Wi-Fi.
        // The normal OPlus binder's LTE-single family ignores inetCondition and
        // carrierNetworkChange: validation may change while the radio bars do not.
        // Do not extend this to warning, no-service, soft-SIM or roaming variants.
        boolean ordinary = ("stat_signal_signal_lte_single_" + level).equals(resource);
        boolean legacy = resource.isEmpty() && inetCondition == 1 && !changing;
        return cellular && inService && (ordinary || legacy) && !softSim
                && levels == 5 && level >= 0 && level <= 4 ? level : -1;
    }
}
