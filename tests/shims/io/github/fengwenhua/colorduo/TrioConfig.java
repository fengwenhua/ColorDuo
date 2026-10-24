/*
 * Copyright (C) 2026 yixing233 and HyperDuo contributors
 *
 * This file is part of ColorDuo, released under the GNU General Public
 * License v3.0. See the LICENSE file at the repository root, or
 * <https://www.gnu.org/licenses/gpl-3.0.html>.
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.fengwenhua.colorduo;

/**
 * Desktop-only stand-in for the real {@code TrioConfig}, which needs the Xposed
 * API. The harness always renders with {@link TrioSettings#defaults()}.
 */
final class TrioConfig {

    private static final TrioSettings SNAPSHOT = TrioSettings.defaults();

    private TrioConfig() {
    }

    static TrioSettings get() {
        return SNAPSHOT;
    }

    static boolean debugLog() {
        return false;
    }
}
