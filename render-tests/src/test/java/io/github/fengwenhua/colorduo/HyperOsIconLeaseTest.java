/*
 * Copyright (C) 2026 yixing233 and HyperDuo contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.github.fengwenhua.colorduo;

import android.view.View;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.junit.Test;
import static org.junit.Assert.*;

/** Real production lease effects against queued Views and native manager/meter contracts. */
public final class HyperOsIconLeaseTest {
    private static final class Fixture {
        final TrioSettings settings = TrioSettings.defaults();
        boolean island;
        final List<String> log = new ArrayList<>();
        HyperOsIconLease.MeterFields fields = nativeFields();
        final HyperOsIconLease lease = new HyperOsIconLease(
                () -> TrioAppearance.of(settings), () -> island, List.of("wifi"), List.of("mobile", "stacked_mobile"),
                view -> view.slot, view -> ((Bar) view).folds++, () -> fields, () -> false, log::add);
        Fixture() {
            settings.enabled = true; settings.trioStyle = Prefs.STYLE_RING;
            settings.setShowWifi(settings.setShowMobile(settings.setShowValue(settings.setShowBolt(true))));
            settings.setShowSim1(settings.setShowSim2(true));
            layout("1||");
        }
        void layout(String value) { settings.ringZones = Prefs.decodeLayout(value); }
    }

    static HyperOsIconLease.MeterFields nativeFields() {
        return new HyperOsIconLease.MeterFields(Refl.field(Meter.class, "charging"), Refl.field(Meter.class, "style"),
                Refl.field(Meter.class, "battery"), Refl.field(Meter.class, "hollow"), Refl.field(Meter.class, "percent"),
                Refl.method(Meter.class, "refresh"), 1);
    }

    @Test public void postedHideReadsCurrentOwnershipAndDoesNotClaimNativeHiddenIcons() {
        Fixture f = new Fixture(); View wifi = new View(); wifi.slot = "wifi"; wifi.setSize(20, 20);
        f.lease.hideIcon(wifi); f.layout("||"); wifi.flush();
        assertEquals(View.VISIBLE, wifi.getVisibility()); assertFalse(f.lease.collapsed.containsKey(wifi));
        f.layout("1||"); f.lease.hideIcon(wifi); wifi.flush();
        assertEquals(View.GONE, wifi.getVisibility()); assertTrue(f.lease.collapsed.containsKey(wifi));
        assertEquals(0, wifi.getWidth()); assertEquals(0, wifi.getHeight());
        View nativeHidden = new View(); nativeHidden.slot = "wifi"; nativeHidden.setVisibility(View.GONE);
        f.lease.hideIcon(nativeHidden); nativeHidden.flush();
        assertFalse(f.lease.collapsed.containsKey(nativeHidden));
    }

    @Test public void keyguardRemovesOnlyModuleAdditionsAndPreservesNativePolicy() {
        Fixture f = new Fixture(); Bar bar = new Bar();
        bar.mTintedIconManager.mBlockList.addAll(List.of("vpn", "mobile"));
        f.lease.blockIcons(bar);
        assertEquals(List.of("vpn", "mobile", "wifi"), bar.mTintedIconManager.mBlockList);
        assertNotSame(bar.mTintedIconManager.mBlockList, bar.mTintedIconManager.argument);
        f.layout("||"); f.lease.blockIcons(bar);
        assertEquals(List.of("vpn", "mobile"), bar.mTintedIconManager.mBlockList);
        f.layout("1||"); f.lease.blockIcons(bar); f.settings.enabled = false; f.lease.blockIcons(bar);
        assertEquals(List.of("vpn", "mobile"), bar.mTintedIconManager.mBlockList);
    }

    @Test public void managersKeepIndependentBookkeeping() {
        Fixture f = new Fixture(); Bar nativeOwned = new Bar(), moduleOwned = new Bar();
        nativeOwned.mTintedIconManager.mBlockList.add("wifi");
        f.lease.blockIcons(nativeOwned); f.lease.blockIcons(moduleOwned);
        f.layout("||"); f.lease.blockIcons(moduleOwned); f.lease.blockIcons(nativeOwned);
        assertEquals(List.of("wifi"), nativeOwned.mTintedIconManager.mBlockList);
        assertTrue(moduleOwned.mTintedIconManager.mBlockList.isEmpty());
    }

    @Test public void islandAndPostedKeyguardChangesUseLatestLayout() {
        Fixture f = new Fixture(); Bar row = new Bar(); f.lease.keyguardRows.put(row, true);
        f.lease.syncKeyguardRows(); row.flush(); assertTrue(row.mTintedIconManager.mBlockList.contains("wifi"));
        f.island = true; f.lease.syncKeyguardRows(); row.flush();
        assertFalse(row.mTintedIconManager.mBlockList.contains("wifi"));
        f.island = false; f.lease.syncKeyguardRows(); row.flush();
        assertTrue(row.mTintedIconManager.mBlockList.contains("wifi"));
        f.lease.syncKeyguardRows(); f.layout("||"); row.flush();
        assertFalse(row.mTintedIconManager.mBlockList.contains("wifi")); assertEquals(4, row.folds);
    }

    @Test public void islandReleasesWifiWhileBothMobileAliasesRemainOwned() {
        Fixture f = new Fixture(); f.layout("1||7"); f.island = true;
        assertEquals(List.of("mobile", "stacked_mobile"), f.lease.foldedSlots());
        f.island = false; assertEquals(List.of("wifi", "mobile", "stacked_mobile"), f.lease.foldedSlots());
        f.layout("||"); assertTrue(f.lease.foldedSlots().isEmpty());
        f.layout("1||7"); f.settings.enabled = false; assertTrue(f.lease.foldedSlots().isEmpty());
    }

    @Test public void postedMeterUsesLatestLayoutAndRestoresNativeChildren() {
        Fixture f = new Fixture(); Meter meter = new Meter(() -> TrioAppearance.of(f.settings));
        f.lease.applyMeterText(List.of(meter)); f.layout("4||"); meter.flush();
        assertEquals(View.GONE, meter.charging.getVisibility()); assertEquals(1, meter.updates);
        assertEquals("a newly owned bolt must not hand back native style from the older queued layout", 0, meter.style);
        f.layout("||"); meter.nativePercent = true; meter.battery.setVisibility(View.GONE);
        meter.hollow.setVisibility(View.VISIBLE); meter.style = 0;
        f.lease.applyMeterText(List.of(meter)); meter.flush();
        assertEquals(View.VISIBLE, meter.charging.getVisibility()); assertEquals(View.VISIBLE, meter.percent.getVisibility());
        assertEquals(View.VISIBLE, meter.battery.getVisibility()); assertEquals(View.GONE, meter.hollow.getVisibility());
        assertEquals(1, meter.style);
        f.layout("3||"); f.lease.applyMeterText(List.of(meter)); meter.flush();
        assertEquals(View.GONE, meter.percent.getVisibility());
    }

    @Test public void nativeFieldsAreSampledWhenTheQueuedCallbackRuns() {
        Fixture f = new Fixture(); f.layout("||"); Meter meter = new Meter(() -> TrioAppearance.of(f.settings));
        f.fields = new HyperOsIconLease.MeterFields(null, null, null, null, null, null, 1);
        f.lease.applyMeterText(List.of(meter)); f.fields = nativeFields(); meter.flush();
        assertEquals(1, meter.updates); assertEquals(1, meter.style);
    }

    @Test public void missingOrFailingNativeContractsDoNotEscapeIntoSystemUi() {
        Fixture f = new Fixture(); f.lease.blockIcons(new Object());
        Object missingSetter = new Object() { public Object mTintedIconManager = new Object(); };
        f.lease.blockIcons(missingSetter);
        Object failingSetter = new Object() { public Manager mTintedIconManager = new Manager() {
            @Override public void setBlockList(List<String> value) { throw new IllegalStateException("OEM failure"); }
        }; };
        f.lease.blockIcons(failingSetter);
        assertEquals(3, f.log.size());
        View failingPost = new View() { @Override public boolean post(Runnable task) { throw new IllegalStateException("detached"); } };
        f.lease.hideIcon(failingPost); assertFalse(f.lease.collapsed.containsKey(failingPost));
    }

    @Test public void collapsedChildrenHaveOnceOnlyAcquireAndRelease() {
        Fixture f = new Fixture(); View view = new View();
        assertTrue(f.lease.markCollapsed(view)); assertFalse(f.lease.markCollapsed(view));
        assertTrue(f.lease.unmarkCollapsed(view)); assertFalse(f.lease.unmarkCollapsed(view));
    }

    public static class Manager {
        public final List<String> mBlockList = new ArrayList<>();
        List<String> argument;
        public void setBlockList(List<String> value) { argument = value; mBlockList.clear(); mBlockList.addAll(value); }
    }
    public static final class Bar extends View {
        public final Manager mTintedIconManager = new Manager();
        int folds;
    }
    public static final class Meter extends View {
        public final View charging = new View(), percent = new View(), battery = new View(), hollow = new View();
        public int style;
        boolean nativePercent;
        int updates;
        private final Supplier<TrioAppearance> appearance;
        Meter(Supplier<TrioAppearance> appearance) { this.appearance = appearance; }
        // Mirrors the legacy harness's native refresh plus after-hook predicates.
        // This is not a simulation of every MIUI firmware's battery implementation.
        public void refresh() {
            updates++;
            charging.setVisibility(appearance.get().hidesNativeBolt() ? View.GONE : View.VISIBLE);
            percent.setVisibility(nativePercent && !appearance.get().ownsValue() ? View.VISIBLE : View.GONE);
        }
    }
}
