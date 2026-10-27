/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Hide only the local native VPN slot after a replacement VPN indicator has drawn. */
final class ColorOsVpnIcon {
    private Class<?> containerType;
    private Method addIgnored, removeIgnored, slot;
    private Field ignoredSlots;
    private String error = "None";
    private final IgnoredSlotLease<View, View> leases = new IgnoredSlotLease<>(new IgnoredSlotLease.Sink<>() {
        @Override public boolean ignored(View container) {
            try {
                Object slots = ignoredSlots.get(container);
                return slots instanceof List && ((List<?>) slots).contains("vpn");
            } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
        }
        @Override public void hide(View container) { invoke(addIgnored, container); }
        @Override public void restore(View container) { invoke(removeIgnored, container); }
    });

    ColorOsVpnIcon(ClassLoader loader) {
        try {
            containerType = Class.forName(ColorOsSymbols.CLASS_PHONE_STATUS_ICON_CONTAINER, false, loader);
            addIgnored = containerType.getMethod(ColorOsSymbols.NAME_ADD_IGNORED_SLOT, String.class);
            removeIgnored = containerType.getMethod(ColorOsSymbols.NAME_REMOVE_IGNORED_SLOT, String.class);
            ignoredSlots = containerType.getDeclaredField(ColorOsSymbols.NAME_M_IGNORED_SLOTS);
            ignoredSlots.setAccessible(true);
            slot = Class.forName("com.android.systemui.statusbar.StatusIconDisplayable", false, loader)
                    .getMethod(ColorOsSymbols.NAME_GET_SLOT);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            error = failure.getClass().getSimpleName(); containerType = null;
        }
    }

    void commit(View meter, boolean indicatorDrawn) {
        if (containerType == null) return;
        try { leases.set(meter, indicatorDrawn ? containersNear(meter) : List.of()); }
        catch (RuntimeException failure) {
            error = failure.getClass().getSimpleName();
            try { leases.releaseAll(); } catch (RuntimeException ignored) {}
        }
    }

    void release(View meter) {
        try { leases.release(meter); }
        catch (RuntimeException failure) { error = failure.getClass().getSimpleName(); }
    }

    String status() { return "containers=" + leases.size() + " error=" + error; }

    private static void invoke(Method method, View container) {
        try { method.invoke(container, "vpn"); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }

    private List<View> containersNear(View meter) {
        // Same local-row boundary as the network bridge; never cross the window root.
        ViewParent parent = meter.getParent();
        for (int depth = 0; depth < 4 && parent instanceof ViewGroup; depth++, parent = parent.getParent()) {
            if (parent == meter.getRootView()) break;
            List<View> found = new ArrayList<>();
            collect((View) parent, meter, 0, new int[]{256}, found);
            if (!found.isEmpty()) return found;
        }
        return List.of();
    }

    private void collect(View view, View meter, int depth, int[] budget, List<View> found) {
        if (view == meter || depth > 5 || --budget[0] < 0 || !view.isShown()
                || !view.isAttachedToWindow() || view.getWindowToken() != meter.getWindowToken()) return;
        if (containerType.isInstance(view)) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                try {
                    if ("vpn".equals(slot.invoke(group.getChildAt(i)))) { found.add(view); break; }
                } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
            }
            return;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) collect(group.getChildAt(i), meter, depth + 1, budget, found);
        }
    }
}
