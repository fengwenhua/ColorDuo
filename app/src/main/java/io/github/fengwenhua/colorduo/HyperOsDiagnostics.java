/*
 * Copyright (C) 2026 yixing233 and HyperDuo contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.github.fengwenhua.colorduo;

import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

/** View-only diagnostic formatting; owns no lifecycle or telemetry. */
final class HyperOsDiagnostics {
    static String childrenOf(ViewGroup container) {
        final StringBuilder sb = new StringBuilder();
        final int count;
        try {
            count = container.getChildCount();
        } catch (Throwable t) {
            return sb.toString();
        }
        for (int i = 0; i < count; i++) {
            final View child;
            try {
                child = container.getChildAt(i);
            } catch (Throwable t) {
                continue;
            }
            if (child == null) {
                continue;
            }
            sb.append(" | ").append(i)
                    .append(' ').append(child.getClass().getSimpleName())
                    .append(" slot=").append(HyperOsHostTree.slotOf(child))
                    .append(" l=").append(child.getLeft())
                    .append(" r=").append(child.getRight())
                    .append(" w=").append(child.getWidth())
                    .append(" mw=").append(child.getMeasuredWidth())
                    .append(" tx=").append(child.getTranslationX())
                    .append(" a=").append(child.getAlpha())
                    .append(" v=").append(child.getVisibility());
        }
        return sb.toString();
    }

    static String pathOf(View v) {
        final StringBuilder sb = new StringBuilder();
        for (ViewParent p = v.getParent(); p != null;
             p = (p instanceof View) ? ((View) p).getParent() : null) {
            sb.append(p.getClass().getSimpleName()).append('<');
        }
        return sb.toString();
    }
    private HyperOsDiagnostics() {}
}
