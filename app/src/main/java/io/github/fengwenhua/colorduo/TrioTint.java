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

import android.graphics.Rect;
import android.view.View;

import java.util.ArrayList;
import java.util.Collection;

/** OS4 DarkIconDispatcher's area rule, applied at the battery's own position. */
final class TrioTint {
    private TrioTint() { }

    static final class Inputs {
        final ArrayList<Rect> areas = new ArrayList<Rect>();
        final float intensity;
        final int tint, light, dark;
        final boolean useTint;

        Inputs(Object source, float intensity, int tint, int light, int dark,
               boolean useTint) {
            // SystemUI reuses/mutates its list and Rects. Keep the parameters
            // of this dispatch, not aliases that another row can change later.
            if (source instanceof Collection<?>) {
                for (Object item : (Collection<?>) source) {
                    if (item instanceof Rect) {
                        areas.add(new Rect((Rect) item));
                    }
                }
            }
            this.intensity = intensity;
            this.tint = tint;
            this.light = light;
            this.dark = dark;
            this.useTint = useTint;
        }

        int colour(View host) {
            final boolean inside = inAreas(areas, host);
            final int colour = useTint ? (inside ? tint : 0xFFFFFFFF)
                    : (inside && intensity > 0f ? dark : light);
            return colour == 0 ? 0xFFFFFFFF : colour;
        }
    }

    static boolean inAreas(Collection<Rect> areas, View host) {
        if (areas.isEmpty() || host == null) {
            return true;
        }
        final int[] location = new int[2];
        host.getLocationOnScreen(location);
        final int width = host.getWidth();
        for (Rect area : areas) {
            if (area.isEmpty()) {
                return true;
            }
            final int overlap = Math.max(0,
                    Math.min(location[0] + width, area.right)
                            - Math.max(location[0], area.left));
            if (overlap * 2 > width && area.top <= 0) {
                return true;
            }
        }
        return false;
    }
}
