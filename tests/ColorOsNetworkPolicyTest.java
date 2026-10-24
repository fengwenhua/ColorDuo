/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.yixing233.hyperduo;

public final class ColorOsNetworkPolicyTest {
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
            expect(ColorOsNetworkPolicy.mobile(true, true, false, false, false, level, 5), level);
        expect(ColorOsNetworkPolicy.mobile(false, true, false, false, false, 4, 5), -1);
        expect(ColorOsNetworkPolicy.mobile(true, false, false, false, false, 4, 5), -1);
        expect(ColorOsNetworkPolicy.mobile(true, true, true, false, false, 4, 5), -1);
        expect(ColorOsNetworkPolicy.mobile(true, true, false, true, false, 4, 5), -1);
        expect(ColorOsNetworkPolicy.mobile(true, true, false, false, true, 4, 5), -1);
        expect(ColorOsNetworkPolicy.mobile(true, true, false, false, false, 5, 5), -1);
        expect(ColorOsNetworkPolicy.mobile(true, true, false, false, false, -1, 5), -1);
        expect(ColorOsNetworkPolicy.mobile(true, true, false, false, false, 4, 6), -1);
        System.out.println("ColorOS network warning preservation: " + checks + " checks passed");
    }
}
