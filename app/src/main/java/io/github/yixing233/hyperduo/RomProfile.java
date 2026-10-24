/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.yixing233.hyperduo;

/** Pure capability decision: ambiguous or unknown ROMs must not install hiding hooks. */
enum RomProfile {
    HYPER_OS, COLOR_OS, UNKNOWN, AMBIGUOUS;

    static RomProfile select(boolean hasMiuiBattery, boolean hasOplusBattery) {
        if (hasMiuiBattery && hasOplusBattery) return AMBIGUOUS;
        if (hasOplusBattery) return COLOR_OS;
        if (hasMiuiBattery) return HYPER_OS;
        return UNKNOWN;
    }
}
