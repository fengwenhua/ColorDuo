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

import android.view.View;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/** HyperOS native icon bookkeeping and posted effects; OEM hook installation stays in TrioHooks. */
final class HyperOsIconLease {
    record MeterFields(Field charging, Field style, Field battery, Field hollow,
                       Field percent, Method update, int boltStyle) {}

    final Map<View, Boolean> keyguardRows = new WeakHashMap<>();
    final Map<View, Boolean> collapsed = Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<Object, List<String>> keyguardAdded = new WeakHashMap<>();
    private final Supplier<TrioAppearance> appearance;
    private final BooleanSupplier island;
    private final List<String> wifiSlots, mobileSlots;
    private final Function<View, String> slotOf;
    private final Consumer<View> foldContainers;
    private final Supplier<MeterFields> meterFields;
    private final BooleanSupplier debug;
    private final Consumer<String> log;

    HyperOsIconLease(Supplier<TrioAppearance> appearance, BooleanSupplier island,
                     List<String> wifiSlots, List<String> mobileSlots,
                     Function<View, String> slotOf, Consumer<View> foldContainers,
                     Supplier<MeterFields> meterFields, BooleanSupplier debug, Consumer<String> log) {
        this.appearance = appearance; this.island = island;
        this.wifiSlots = wifiSlots; this.mobileSlots = mobileSlots;
        this.slotOf = slotOf; this.foldContainers = foldContainers; this.meterFields = meterFields;
        this.debug = debug; this.log = log;
    }

    List<String> foldedSlots() {
        final TrioAppearance a = appearance.get();
        if (!a.glyph) {
            return Collections.emptyList();
        }
        final List<String> slots = new ArrayList<>(wifiSlots.size() + mobileSlots.size());
        if (a.ownsWifi()) {
            // The island is the one state that hands Wi-Fi back while the module
            // draws it: the battery is hidden, so the glyph cannot carry the
            // arcs and the user asked for the native icon to take over.
            if (!island.getAsBoolean()) {
                slots.addAll(wifiSlots);
            }
        }
        // The mobile slot is folded exactly while the module draws the signal
        // itself: as the glyph's dots, or as the out-of-ring reading. Leaving it
        // in place out of ring with the stacked switch off is what keeps MIUI's
        // own icon on screen, which is the whole difference the switch makes.
        // The island does not change this: what it changes is *where* the
        // module draws the signal (out of ring, since the glyph is gone), not
        // whether the user asked for it - so the fold condition is untouched and
        // the reading takes over in syncOutSignal.
        if (a.foldsMobile()) {
            slots.addAll(mobileSlots);
        }
        return slots;
    }

    void applyMeterText(List<View> meters) {
        for (int i = 0; i < meters.size(); i++) {
            final View meter = meters.get(i);
            meter.post(new Runnable() {
                @Override
                public void run() {
                    final TrioAppearance a = appearance.get();
                    final MeterFields fields = meterFields.get();
                    final Object charging = Refl.get(fields.charging(), meter);
                    // The PR's repair, kept under main's predicate. Both sides ask
                    // hidesNativeBolt(); the difference is only how the hand-back is
                    // performed - a bare showIfHidden leaves the bolt dark whenever MIUI
                    // has not happened to re-run its own style pass, because
                    // updateChargeAndText only reveals the charging view while
                    // mBatteryStyle is 1 or 2 and the module forces it to 0.
                    if (!a.hidesNativeBolt()) {
                        Refl.set(fields.style(), meter, Integer.valueOf(fields.boltStyle()));
                        showIfHidden(charging);
                        // Asking for the bolt is what performs MIUI's view swap: the
                        // style-1 branch hides mBatteryIconView - the view the trio is
                        // painted on - and shows the hollow outline. Put the pair back.
                        showIfHidden(Refl.get(fields.battery(), meter));
                        hide(Refl.get(fields.hollow(), meter));
                    } else {
                        hide(charging);
                    }
                    // The percentage text is ours to hide the whole time the
                    // module is on, whichever sub-toggles are set.
                    final Object percent = Refl.get(fields.percent(), meter);
                    if (a.glyph && !a.handsBackPercentage()) {
                        hide(percent);
                    } else {
                        showIfHidden(percent);
                    }
                    // Let MIUI re-derive the bolt from the battery state. It only
                    // runs the text refresh on its own battery callbacks, and this
                    // is a settings change, so without the call the hand-back
                    // would wait for the next charge event.
                    Refl.invoke(fields.update(), meter);
                }
            });
        }
    }

    static void showIfHidden(Object view) {
        if (view instanceof View) {
            final View v = (View) view;
            if (v.getVisibility() != View.VISIBLE) {
                v.setVisibility(View.VISIBLE);
            }
        }
    }

    static void hide(Object view) {
        if (view instanceof View) {
            ((View) view).setVisibility(View.GONE);
        }
    }

    void blockIcons(Object bar) {
        try {
            final Object manager = Refl.get(Refl.field(bar.getClass(), "mTintedIconManager"), bar);
            if (manager == null) {
                log.accept("keyguard: no tinted icon manager");
                return;
            }
            final List<String> wanted = new ArrayList<>();
            final Object current = Refl.get(Refl.field(manager.getClass(), "mBlockList"), manager);
            if (current instanceof List<?>) {
                for (Object slot : (List<?>) current) if (slot instanceof String) wanted.add((String) slot);
            }
            synchronized (keyguardAdded) {
                final List<String> previous = keyguardAdded.get(manager);
                if (previous != null) wanted.removeAll(previous);
                final List<String> added = new ArrayList<>();
                for (String slot : foldedSlots()) if (!wanted.contains(slot)) {
                    wanted.add(slot);
                    added.add(slot);
                }
                keyguardAdded.put(manager, added);
            }
            // IconManager.setBlockList(List) clears the list, fills it and calls
            // refreshIconGroup in one go, and refreshIconGroup is what makes MIUI
            // lay the row's icons out again without the named slots. Writing the
            // list by hand and asking for the refresh separately does not work:
            // both the list and the controller that would be asked are on
            // IconManager, one level up from the manager the row holds.
            final Method[] methods = manager.getClass().getMethods();
            for (int i = 0; i < methods.length; i++) {
                final Method m = methods[i];
                if ("setBlockList".equals(m.getName()) && m.getParameterTypes().length == 1) {
                    m.invoke(manager, wanted);
                    if (debug.getAsBoolean()) {
                        log.accept("keyguard block list set: " + wanted);
                    }
                    return;
                }
            }
            log.accept("keyguard: setBlockList missing");
        } catch (Throwable t) {
            log.accept("keyguard block list failed: " + t);
        }
    }

    void syncKeyguardRows() {
        synchronized (keyguardRows) {
            for (View row : new ArrayList<>(keyguardRows.keySet())) {
                row.post(() -> { blockIcons(row); foldContainers.accept(row); });
            }
        }
    }

    void hideIcon(final View icon) {
        try {
            icon.post(new Runnable() {
                @Override
                public void run() {
                    // Ownership can change between addHolder and this posted work.
                    if (!foldedSlots().contains(slotOf.apply(icon))) return;
                    if (icon.getVisibility() == View.VISIBLE) markCollapsed(icon);
                    try {
                        if (icon.getWidth() != 0 || icon.getHeight() != 0) {
                            icon.layout(0, 0, 0, 0);
                        }
                        icon.setVisibility(View.GONE);
                    } catch (Throwable ignored) {
                        // never let one icon abort the pass
                    }
                    try {
                        final Method state = icon.getClass()
                                .getMethod("setVisibleState", int.class, boolean.class);
                        state.invoke(icon, Integer.valueOf(0), Boolean.TRUE);
                    } catch (Throwable ignored) {
                        // the old pipeline views have no such method
                    }
                }
            });
        } catch (Throwable ignored) {
            // posting is best-effort; the fold path still runs on layout
        }
    }

    boolean markCollapsed(View child) {
        synchronized (collapsed) {
            return collapsed.put(child, Boolean.TRUE) == null;
        }
    }

    boolean unmarkCollapsed(View child) {
        synchronized (collapsed) {
            return collapsed.remove(child) != null;
        }
    }
}
