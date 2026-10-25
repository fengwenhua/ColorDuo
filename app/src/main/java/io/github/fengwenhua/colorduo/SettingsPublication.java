/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import java.util.function.BooleanSupplier;

/** Persistence must finish before notifying SystemUI; stop immediately on either write failure. */
final class SettingsPublication {
    static boolean commit(BooleanSupplier local, BooleanSupplier remote, Runnable notify) {
        if (!local.getAsBoolean() || !remote.getAsBoolean()) return false;
        notify.run();
        return true;
    }
    private SettingsPublication() {}
}
