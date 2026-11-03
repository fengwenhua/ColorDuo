/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

public final class ColorOsNetworkPolicyTest {
    private static void ordinaryOemWifiCanHandoffBeforeValidationWithoutLosingItsDescription() {
        for (int level = 1; level <= 4; level++) {
            expect(level, ColorOsNetworkPolicy.wifi(true, false, true, level,
                    "stat_signal_wifi_signal_" + level, "Wi-Fi strength, no internet"));
            expect(-1, ColorOsNetworkPolicy.wifi(true, false, false, level,
                    "stat_signal_wifi_signal_" + level, "Wi-Fi strength, no internet"));
        }
    }
    private static void unknownWarningFamiliesAndMissingDescriptionsRemainNative() {
        for (String resource : new String[]{"ic_qs_wifi_6_4", "wifi_no_internet", "", "stat_signal_wifi_signal_3"})
            expect(-1, ColorOsNetworkPolicy.wifi(true, false, true, 4, resource, "no internet"));
        for (String description : new String[]{null, ""})
            expect(-1, ColorOsNetworkPolicy.wifi(true, false, true, 4,
                    "stat_signal_wifi_signal_4", description));
        expect(-1, ColorOsNetworkPolicy.wifi(false, false, true, 4,
                "stat_signal_wifi_signal_4", "no internet"));
        expect(-1, ColorOsNetworkPolicy.wifi(true, false, true, 0,
                "stat_signal_wifi_signal_0", "no internet"));
    }
    private static int checks;
    private static void expect(int actual, int expected) {
        checks++;
        if (actual != expected) throw new AssertionError("expected " + expected + ", got " + actual);
    }
    public static void main(String[] args) {
        ordinaryOemWifiCanHandoffBeforeValidationWithoutLosingItsDescription();
        unknownWarningFamiliesAndMissingDescriptionsRemainNative();
        for (int level = 0; level <= 4; level++) {
            String resource = "stat_signal_signal_lte_single_" + level;
            for (int condition : new int[]{-1, 0, 1}) for (boolean changing : new boolean[]{false, true})
                expect(ColorOsNetworkPolicy.mobile(true, true, condition, changing, false, level, 5, resource), level);
            expect(ColorOsNetworkPolicy.mobile(true, false, 0, false, false, level, 5, resource), -1);
            expect(ColorOsNetworkPolicy.mobile(true, true, 0, false, true, level, 5, resource), -1);
            expect(ColorOsNetworkPolicy.mobile(false, true, 0, false, false, level, 5, resource), -1);
            expect(ColorOsNetworkPolicy.mobile(true, true, 0, false, false, level, 6, resource), -1);
        }
        for (String resource : new String[]{"stat_signal_noservice_lte", "stat_signal_single_novoice_4",
                "stat_signal_soft_signal_4", "stat_signal_signal_lte_single_3", "unknown"})
            for (int condition : new int[]{0, 1})
                expect(ColorOsNetworkPolicy.mobile(true, true, condition, false, false, 4, 5, resource), -1);
        for (int level = 1; level <= 4; level++) expect(ColorOsNetworkPolicy.wifi(true, true, true, level), level);
        expect(ColorOsNetworkPolicy.wifi(true, true, true, 0), -1); // Current arcs have no ink at zero.
        expect(ColorOsNetworkPolicy.wifi(true, true, true, 5), -1);
        expect(ColorOsNetworkPolicy.wifi(false, true, true, 4), -1);
        expect(ColorOsNetworkPolicy.wifi(true, false, true, 4), -1); // Preserve no-internet warning.
        expect(ColorOsNetworkPolicy.wifi(true, true, false, 4), -1); // Force-hidden/carrier-merged.
        for (int level = 0; level <= 4; level++)
            expect(ColorOsNetworkPolicy.mobile(true, true, 1, false, false, level, 5), level);
        expect(ColorOsNetworkPolicy.mobile(false, true, 1, false, false, 4, 5), -1);
        expect(ColorOsNetworkPolicy.mobile(true, false, 1, false, false, 4, 5), -1);
        expect(ColorOsNetworkPolicy.mobile(true, true, 0, false, false, 4, 5), -1);
        expect(ColorOsNetworkPolicy.mobile(true, true, 1, true, false, 4, 5), -1);
        expect(ColorOsNetworkPolicy.mobile(true, true, 1, false, true, 4, 5), -1);
        expect(ColorOsNetworkPolicy.mobile(true, true, 1, false, false, 5, 5), -1);
        expect(ColorOsNetworkPolicy.mobile(true, true, 1, false, false, -1, 5), -1);
        expect(ColorOsNetworkPolicy.mobile(true, true, 1, false, false, 4, 6), -1);
        System.out.println("ColorOS network warning preservation: " + checks + " checks passed");
    }
}
