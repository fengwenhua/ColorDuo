/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import java.util.EnumMap;
import java.util.Map;

/** Process-local bounded capability state; captures exception type only, never its message. */
final class ColorOsProfile {
    private static final Map<CompatibilitySnapshot.Capability, CompatibilitySnapshot.Status> STATES =
            new EnumMap<>(CompatibilitySnapshot.Capability.class);
    static synchronized void inspect(ClassLoader loader) { STATES.putAll(ColorOsSymbols.inspect(loader)); }
    static synchronized void note(CompatibilitySnapshot.Capability key, boolean available, String errorType) {
        STATES.put(key, new CompatibilitySnapshot.Status(available, available ? "None" : errorType));
    }
    static synchronized Map<CompatibilitySnapshot.Capability, CompatibilitySnapshot.Status> snapshot() {
        return Map.copyOf(STATES);
    }
    private ColorOsProfile() {}
}
