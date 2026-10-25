/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import java.util.EnumMap;
import org.junit.Test;
import static org.junit.Assert.*;

public final class CompatibilitySnapshotTest {
    private final CompatibilitySnapshot.Device tested = new CompatibilitySnapshot.Device("OPPO", "PKH110", "16", 36,
            "test-build", "16.99.12 (1)", "1.1.0", "COLOR_OS", true);
    @Test public void testedProfileRequiresBothSymbolsAndInstalledHooks() {
        var states = new EnumMap<CompatibilitySnapshot.Capability, CompatibilitySnapshot.Status>(CompatibilitySnapshot.Capability.class);
        for (var key : CompatibilitySnapshot.Capability.values()) states.put(key, new CompatibilitySnapshot.Status(true, "None"));
        assertEquals("matches-tested-Find-N5-profile", CompatibilitySnapshot.grade(tested, states));
        states.put(CompatibilitySnapshot.Capability.NETWORK_HOOK, new CompatibilitySnapshot.Status(false, "HookUnavailable"));
        assertEquals("missing-capabilities-native-fallback", CompatibilitySnapshot.grade(tested, states));
        assertTrue(CompatibilitySnapshot.render(tested, states).contains("network_hook.errorType=HookUnavailable"));
    }
    @Test public void missingRuntimeAndOtherDevicesAreNeverCalledVerified() {
        var states = new EnumMap<CompatibilitySnapshot.Capability, CompatibilitySnapshot.Status>(CompatibilitySnapshot.Capability.class);
        for (var key : CompatibilitySnapshot.Capability.values()) states.put(key, new CompatibilitySnapshot.Status(true, "None"));
        var other = new CompatibilitySnapshot.Device("OPPO", "Other", "16", 36, "build", "16.99.12 (1)", "1.1.0", "COLOR_OS", true);
        assertEquals("expected-profile-device-unverified", CompatibilitySnapshot.grade(other, states));
        var offline = new CompatibilitySnapshot.Device("OPPO", "PKH110", "16", 36, "build", "16.99.12 (1)", "1.1.0", "unknown", false);
        assertEquals("no-runtime-report", CompatibilitySnapshot.grade(offline, states));
    }
    @Test public void reportBoundsFieldsAndAcceptsOnlyExceptionTypes() {
        var state = new CompatibilitySnapshot.Status(false, "error message with SSID=secret\nnotification=text");
        assertEquals("Unknown", state.errorType());
        var input = new CompatibilitySnapshot.Device("OPPO\nforged=true", "x".repeat(500), "16", 36,
                "build", "version", "module", "COLOR_OS", true);
        String report = CompatibilitySnapshot.render(input, java.util.Map.of(CompatibilitySnapshot.Capability.BATTERY_HOOK, state));
        assertFalse(report.contains("\nforged=")); assertFalse(report.contains("secret"));
        for (String line : report.split("\n")) assertTrue(line.length() <= 190);
        assertTrue(report.length() < 4096);
    }
}
