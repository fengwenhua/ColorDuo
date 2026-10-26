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

/**
 * Records what the drawable asked it to paint instead of painting.
 *
 * <p>This is the seam the whole class exists for: it receives the box, so the
 * assertions can be about the numbers handed over rather than about pixels the
 * desktop would have to render.
 */
final class TrioRenderer {

    static int calls;
    static int lastW;
    static int lastH;
    static Canvas lastCanvas;
    static TrioState lastState;

    private TrioRenderer() { }

    static void drawState(Canvas canvas, int w, int h, TrioState s, TrioSettings cfg) {
        calls++;
        lastW = w;
        lastH = h;
        lastCanvas = canvas;
        lastState = s;
    }
    static void draw(Canvas canvas,android.view.View host,TrioState s){
        drawState(canvas,host.getWidth(),host.getHeight(),s,TrioConfig.get());
    }

    static void reset() {
        calls = 0;
        lastW = -1;
        lastH = -1;
        lastCanvas = null;
        lastState = null;
    }
}
