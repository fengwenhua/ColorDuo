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

/** Counts the refreshes, because draw has to refresh before it builds a frame. */
final class TrioState {

    int refreshes;
    int outRingInk;
    int foreground(){return 0xFF000000;}
    static void attachContext(Object context){}

    void refresh() { refreshes++; }
}
