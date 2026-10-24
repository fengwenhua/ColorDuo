/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import java.util.Map;

/** Exact resources from this ROM's MobileIconSets.DATA_TYPE; unknown assets stay native. */
final class ColorOsTypeLabels {
    private static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("stat_signal_connected_g_lte_big", "G"),
            Map.entry("stat_signal_connected_e_lte_big", "E"),
            Map.entry("stat_signal_connected_2g_lte_big", "2G"),
            Map.entry("stat_signal_connected_3g_lte_big", "3G"),
            Map.entry("stat_signal_connected_3gp_lte_big", "3G+"),
            Map.entry("stat_signal_connected_h_lte_big", "H"),
            Map.entry("stat_signal_connected_h_p_lte_big", "H+"),
            Map.entry("stat_signal_connected_4g_lte_big", "4G"),
            Map.entry("stat_signal_connected_4gp_lte_big", "4G+"),
            Map.entry("stat_signal_connected_4_5g_lte_big", "4.5G"),
            Map.entry("stat_signal_connected_lte_big", "LTE"),
            Map.entry("stat_signal_connected_lte_plus_big", "LTE+"),
            Map.entry("stat_signal_connected_5g", "5G"),
            Map.entry("stat_sys_data_fully_connected_5g_plus", "5G+"),
            Map.entry("stat_sys_data_fully_connected_5g_e", "5GE"),
            Map.entry("stat_sys_data_fully_connected_5g_a", "5GA"),
            Map.entry("stat_sys_data_fully_connected_5_dot_5g", "5.5G"));

    static String get(String resource) { return resource == null ? "" : LABELS.getOrDefault(resource, ""); }
}
