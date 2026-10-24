/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.yixing233.hyperduo;

public final class ColorOsTypeLabelsTest {
    private static int checks;
    private static void expect(String resource, String wanted) {
        checks++;
        if (!ColorOsTypeLabels.get(resource).equals(wanted)) throw new AssertionError(resource);
    }
    public static void main(String[] args) {
        expect("stat_signal_connected_5g", "5G");
        expect("stat_sys_data_fully_connected_5g_a", "5GA");
        expect("stat_sys_data_fully_connected_5g_e", "5GE");
        expect("stat_sys_data_fully_connected_5g_plus", "5G+");
        expect("stat_sys_data_fully_connected_5_dot_5g", "5.5G");
        expect("stat_signal_connected_4_5g_lte_big", "4.5G");
        expect("stat_signal_connected_lte_plus_big", "LTE+");
        expect("stat_signal_connected_3gp_lte_big", "3G+");
        expect("stat_signal_connected_h_p_lte_big", "H+");
        expect("stat_signal_connected_4gp_lte_big", "4G+");
        expect("stat_signal_connected_soft_5g", "");
        expect("unknown_5g_warning", "");
        expect("stat_signal_connected_5g_uc", "");
        expect(null, "");
        System.out.println("ColorOS exact network type labels: " + checks + " checks passed");
    }
}
