/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

/** Native Android parent chains and reflection; independent of the OEM/LSPosed process. */
final class HostTreeProbe {
    public static final class SlotView extends View {
        SlotView(Context context) { super(context); }
        public String getSlot() { return "mobile"; }
    }
    static int run(Context context) {
        FrameLayout row = new FrameLayout(context);
        FrameLayout meter = new FrameLayout(context);
        SlotView glyph = new SlotView(context);
        row.addView(meter); meter.addView(glyph);
        require(HyperOsHostTree.isDescendant(glyph, row), "nested host");
        require(HyperOsHostTree.isDescendant(glyph, meter), "direct host");
        require(!HyperOsHostTree.isDescendant(row, row), "ancestor excludes self");
        require(!HyperOsHostTree.isDescendant(glyph, new View(context)), "foreign parent");
        require(HyperOsHostTree.meterIn(glyph, row) == meter, "direct child of battery row");
        require(HyperOsHostTree.meterIn(glyph, meter) == glyph, "direct glyph");
        require(HyperOsHostTree.meterIn(glyph, new View(context)) == null, "missing row");
        require("mobile".equals(HyperOsHostTree.slotOf(glyph)), "slot reflection");
        require(HyperOsHostTree.slotOf(meter) == null, "foreign view has no slot");
        require(HyperOsDiagnostics.childrenOf(meter).contains("slot=mobile"), "child diagnostics");
        require("FrameLayout<FrameLayout<".equals(HyperOsDiagnostics.pathOf(glyph)), "parent diagnostics");
        meter.removeView(glyph);
        require(!HyperOsHostTree.isDescendant(glyph, row), "detached host");
        return 12;
    }
    private static void require(boolean value, String reason) {
        if (!value) throw new AssertionError("host tree: " + reason);
    }
}
