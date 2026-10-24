/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

/** Readings which the current ring can represent without dropping native warnings. */
final class ColorOsNetworkPolicy {
    static int wifi(boolean active, boolean validated, boolean visible, int level) {
        return active && validated && visible && level >= 1 && level <= 4 ? level : -1;
    }

    static int mobile(boolean cellular, boolean inService, boolean exclamation,
                      boolean changing, boolean softSim, int level, int levels) {
        return cellular && inService && !exclamation && !changing && !softSim
                && levels == 5 && level >= 0 && level <= 4 ? level : -1;
    }
}
