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

import android.graphics.Canvas;
import android.view.View;
import org.junit.Test;
import static org.junit.Assert.*;

public final class GlyphFallbackTest {
    public static final class IncompatibleHost extends View { public String mTintBgDrawable = "native value"; }
    @Test public void incompatibleSlotUsesDirectDrawAndNeverClearsForeignValue() throws Throwable {
        TrioConfig.get().enabled = true; TrioHooks.island = false; TrioHooks.LOGGED.clear();
        IncompatibleHost host = new IncompatibleHost();
        for (int frame = 0; frame < 3; frame++) {
            TrioRenderer.reset(); TrioHooks.draw(host, new Canvas());
            assertEquals(1, TrioRenderer.calls); assertEquals("native value", host.mTintBgDrawable);
        }
        assertEquals(1, TrioHooks.LOGGED.stream().filter(s -> s.contains("tint slot write failed")).count());
        GlyphDrawable.uninstall(host);
        assertEquals("native value", host.mTintBgDrawable); assertEquals(0, host.clearTintCalls);
    }
}
