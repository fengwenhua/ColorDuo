/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

public final class ColorOsNetworkPolicyTest {
    @org.junit.Test public void ordinaryOemWifiCanHandoffBeforeValidationWithoutLosingItsDescription() {
        for (int level = 1; level <= 4; level++) {
            org.junit.Assert.assertEquals(level, ColorOsNetworkPolicy.wifi(true, false, true, level,
                    "stat_signal_wifi_signal_" + level, "Wi-Fi strength, no internet"));
            org.junit.Assert.assertEquals(-1, ColorOsNetworkPolicy.wifi(true, false, false, level,
                    "stat_signal_wifi_signal_" + level, "Wi-Fi strength, no internet"));
        }
    }
    @org.junit.Test public void unknownWarningFamiliesAndMissingDescriptionsRemainNative() {
        for (String resource : new String[]{"ic_qs_wifi_6_4", "wifi_no_internet", "", "stat_signal_wifi_signal_3"})
            org.junit.Assert.assertEquals(-1, ColorOsNetworkPolicy.wifi(true, false, true, 4, resource, "no internet"));
        for (String description : new String[]{null, ""})
            org.junit.Assert.assertEquals(-1, ColorOsNetworkPolicy.wifi(true, false, true, 4,
                    "stat_signal_wifi_signal_4", description));
        org.junit.Assert.assertEquals(-1, ColorOsNetworkPolicy.wifi(false, false, true, 4,
                "stat_signal_wifi_signal_4", "no internet"));
        org.junit.Assert.assertEquals(-1, ColorOsNetworkPolicy.wifi(true, false, true, 0,
                "stat_signal_wifi_signal_0", "no internet"));
    }
    private static int checks;
    private static void expect(int actual, int expected) {
        checks++;
        if (actual != expected) throw new AssertionError("expected " + expected + ", got " + actual);
    }
    public static void main(String[] args) {
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
