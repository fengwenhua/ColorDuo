/*
 * Copyright (C) 2026 yixing233 and HyperDuo contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.github.fengwenhua.colorduo;

import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

/** Stateless live-view discovery; never owns or hides a native view. */
final class HyperOsHostTree {
    static String slotOf(View child) {
        final Object slot = Refl.callByName(child, "getSlot");
        return (slot instanceof String) ? (String) slot : null;
    }

    static boolean isDescendant(View child, View ancestor) {
        for (ViewParent p = child.getParent(); p != null;
             p = (p instanceof View) ? ((View) p).getParent() : null) {
            if (p == ancestor) {
                return true;
            }
        }
        return false;
    }

    static View meterIn(View host, View container) {
        View child = host;
        for (ViewParent p = host.getParent(); p != null;
             p = (p instanceof View) ? ((View) p).getParent() : null) {
            if (p == container) {
                return child;
            }
            if (!(p instanceof View)) {
                return null;
            }
            child = (View) p;
        }
        return null;
    }
    private HyperOsHostTree() {}
}
