/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ImageView;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Event driven network bridge. Native collectors stay attached and keep updating. */
final class ColorOsNetworkAdapter {
    private static final String WIFI = "com.android.systemui.statusbar.pipeline.wifi.";
    private static final String MOBILE = "com.oplus.systemui.statusbar.pipeline.mobile.ui.view.";
    private final XposedModule module;
    private final ClassLoader loader;
    private final Runnable changed;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ObservationLedger ledger = new ObservationLedger(64, 500);
    private final Map<View, Boolean> wifiRows = new WeakHashMap<>();
    private final Map<ImageView, Mobile> mobileRows = new WeakHashMap<>();
    private final Map<ImageView, TypeRow> typeRows = new WeakHashMap<>();
    private final Map<ImageView, Integer> pendingTypes = new WeakHashMap<>();
    private final Map<ImageView, String> activityResources = new WeakHashMap<>();
    private final Map<View, Lease> leases = new WeakHashMap<>();
    private Class<?> activeType, visibleType, cellularType, containerType;
    private Method wifiLevel, cellLevel, cellLevels, cellChanging, cellExclamation, cellSlot;
    private Method iconVisible, iconBlocked, visibleState, slot, addIgnored, removeIgnored;
    private Field validated, inService, softSim, ignoredSlots;
    private boolean capable;
    private boolean pending;
    private volatile int latestWifi = -1;
    private final boolean typeStage = "type".equals(BuildConfig.COLOROS_STAGE) || "capsule".equals(BuildConfig.COLOROS_STAGE)
            || "font".equals(BuildConfig.COLOROS_STAGE) || "outside".equals(BuildConfig.COLOROS_STAGE);
    private boolean typeCapable;

    static final class Plan {
        static final Plan EMPTY = new Plan(-1, -1, null, null, null);
        final int wifi, mobile, mobileSlot;
        final WeakReference<View> wifiRoot, container;
        final WeakReference<ImageView> signal;
        final String typeLabel;
        final WeakReference<ImageView> typeImage;
        final boolean hideType;
        Plan(int wifi, int mobile, View root, View container, ImageView signal) {
            this(wifi, mobile, root, container, signal, 0);
        }
        Plan(int wifi, int mobile, View root, View container, ImageView signal, int mobileSlot) {
            this(wifi, mobile, root, container, signal, mobileSlot, "", null, false);
        }
        Plan(int wifi, int mobile, View root, View container, ImageView signal, int mobileSlot,
             String typeLabel, ImageView typeImage, boolean hideType) {
            this.wifi = wifi; this.mobile = mobile;
            this.mobileSlot = mobileSlot;
            wifiRoot = new WeakReference<>(root);
            this.container = new WeakReference<>(container);
            this.signal = new WeakReference<>(signal);
            this.typeLabel = typeLabel;
            this.typeImage = new WeakReference<>(typeImage);
            this.hideType = hideType;
        }
        boolean same(Plan other) {
            return other != null && wifi == other.wifi && mobile == other.mobile && mobileSlot == other.mobileSlot
                    && wifiRoot.get() == other.wifiRoot.get() && container.get() == other.container.get()
                    && signal.get() == other.signal.get() && sameType(other);
        }
        boolean sameType(Plan other) {
            return other != null && typeLabel.equals(other.typeLabel) && typeImage.get() == other.typeImage.get()
                    && hideType == other.hideType;
        }
        boolean sameOwners(Plan other) {
            return other != null && wifiRoot.get() == other.wifiRoot.get() && container.get() == other.container.get()
                    && signal.get() == other.signal.get()
                    && (hideType ? typeImage.get() : null) == (other.hideType ? other.typeImage.get() : null);
        }
        Plan retainedBy(Plan next) {
            boolean keepWifi = container.get() != null && container.get() == next.container.get() && wifiRoot.get() == next.wifiRoot.get();
            boolean keepSignal = signal.get() != null && signal.get() == next.signal.get();
            boolean keepType = hideType && next.hideType && typeImage.get() == next.typeImage.get();
            return new Plan(keepWifi ? wifi : -1, keepSignal ? mobile : -1,
                    keepWifi ? wifiRoot.get() : null, keepWifi ? container.get() : null,
                    keepSignal ? signal.get() : null, mobileSlot, typeLabel,
                    keepType ? typeImage.get() : null, keepType);
        }
        Plan withSignals(boolean wifiInk, boolean mobileInk) {
            return new Plan(wifiInk ? wifi : -1, mobileInk ? mobile : -1,
                    wifiInk ? wifiRoot.get() : null, wifiInk ? container.get() : null,
                    mobileInk ? signal.get() : null, mobileInk ? mobileSlot : 0,
                    typeLabel, typeImage.get(), hideType);
        }
        Plan withTypeOwnership(boolean owned) {
            return new Plan(wifi, mobile, wifiRoot.get(), container.get(), signal.get(), mobileSlot,
                    typeLabel, typeImage.get(), owned);
        }
    }

    private static final class Mobile {
        final WeakReference<View> root;
        int level = -1;
        int slot = -1;
        Mobile(View root) { this.root = new WeakReference<>(root); }
    }

    private static final class Lease {
        Plan plan;
        int signalVisibility;
        ColorOsMobileGap gap;
        ColorOsActivityLayout activity;
        WeakReference<View> compactRoot = new WeakReference<>(null);
        Lease(Plan plan, int visibility) { this.plan = plan; signalVisibility = visibility; }
    }

    private static final class TypeRow {
        final WeakReference<View> root;
        String label = "";
        int nativeVisibility = View.GONE;
        TypeRow(View root) { this.root = new WeakReference<>(root); }
    }

    private interface After { void run(XposedInterface.Chain chain, Object result) throws Throwable; }

    ColorOsNetworkAdapter(XposedModule module, ClassLoader loader, Runnable changed) {
        this.module = module; this.loader = loader; this.changed = changed;
    }

    void install() {
        try {
            activeType = type(WIFI + "shared.model.WifiNetworkModel$Active");
            visibleType = type(WIFI + "ui.model.WifiIcon$Visible");
            cellularType = type("com.android.systemui.statusbar.pipeline.mobile.domain.model.SignalIconModel$Cellular");
            containerType = type("com.android.systemui.statusbar.phone.StatusIconContainer");
            Class<?> modern = type("com.android.systemui.statusbar.pipeline.shared.ui.view.ModernStatusBarView");
            wifiLevel = activeType.getMethod("getLevel");
            validated = activeType.getDeclaredField("isValidated");
            cellLevel = cellularType.getMethod("getLevel");
            cellLevels = cellularType.getMethod("getNumberOfLevels");
            cellChanging = cellularType.getMethod("getCarrierNetworkChange");
            cellExclamation = cellularType.getMethod("getShowExclamationMark");
            cellSlot = cellularType.getMethod("getPhoneId");
            inService = cellularType.getDeclaredField("isInService");
            softSim = cellularType.getDeclaredField("isSoftSimCard");
            iconVisible = modern.getMethod("isIconVisible");
            iconBlocked = modern.getMethod("isIconBlocked");
            visibleState = modern.getMethod("getVisibleState");
            slot = modern.getMethod("getSlot");
            addIgnored = containerType.getMethod("addIgnoredSlot", String.class);
            removeIgnored = containerType.getMethod("removeIgnoredSlot", String.class);
            ignoredSlots = containerType.getDeclaredField("mIgnoredSlots");
            ignoredSlots.setAccessible(true);
            boolean valid = hook(WIFI + "ui.viewmodel.WifiViewModel$wifiIcon$1", "invoke", (chain, result) -> {
                Object model = chain.getArg(3);
                int next = ColorOsNetworkPolicy.wifi(activeType.isInstance(model),
                        activeType.isInstance(model) && validated.getBoolean(model), visibleType.isInstance(result),
                        activeType.isInstance(model) ? ((Number) wifiLevel.invoke(model)).intValue() : -1);
                if (next != latestWifi) { latestWifi = next; requestRefresh(); }
            }, Object.class, Object.class, Object.class, Object.class, Object.class);
            valid &= hook(WIFI + "ui.binder.WifiViewBinder", "bind", (chain, result) -> {
                requireMain();
                View root = (View) chain.getArg(0);
                if (!wifiRows.containsKey(root)) { wifiRows.put(root, true); watch(root); }
                requestRefresh();
            }, ViewGroup.class, type(WIFI + "ui.viewmodel.LocationBasedWifiViewModel"));
            // Only the exact normal Binder observed on this ROM is owned. Other variants stay native.
            valid &= hook(MOBILE + "OplusStatusBarMobileViewBinder", "bindCustEx", (chain, result) -> {
                requireMain();
                View root = (View) chain.getArg(1);
                View signal = child(root, "mobile_signal");
                if (signal instanceof ImageView && !mobileRows.containsKey(signal)) {
                    mobileRows.put((ImageView) signal, new Mobile(root)); watch(root);
                }
                if (typeCapable) {
                    View typeView = child(root, "mobile_type");
                    if (typeView instanceof ImageView) {
                        ImageView image = (ImageView) typeView;
                        typeRows.put(image, new TypeRow(root));
                        noteType(image, pendingTypes.remove(image), image.getVisibility());
                    }
                }
                requestRefresh();
            }, type("com.android.systemui.statusbar.pipeline.mobile.ui.viewmodel.LocationBasedMobileViewModel"),
                    ViewGroup.class, type("kotlinx.coroutines.flow.Flow"), type("kotlinx.coroutines.flow.StateFlow"),
                    type("com.android.systemui.statusbar.pipeline.mobile.ui.MobileViewLogger"));
            valid &= hook(MOBILE + "OplusStatusBarMobileViewBinder", "bindCustEx$updateSignalIcon", (chain, result) -> {
                requireMain();
                ImageView image = (ImageView) chain.getArg(0);
                Mobile row = mobileRows.get(image);
                if (row == null) {
                    // Initial update is inside bindCustEx, before its after-hook registers the row.
                    View root = image;
                    while (root != null && !type("com.android.systemui.statusbar.pipeline.shared.ui.view.ModernStatusBarView").isInstance(root)) {
                        ViewParent parent = root.getParent(); root = parent instanceof View ? (View) parent : null;
                    }
                    if (root == null) return;
                    row = new Mobile(root); mobileRows.put(image, row); watch(root);
                }
                Object model = chain.getArg(1);
                boolean cell = cellularType.isInstance(model);
                int next = cell ? ColorOsNetworkPolicy.mobile(true, inService.getBoolean(model),
                        (Boolean) cellExclamation.invoke(model), (Boolean) cellChanging.invoke(model),
                        softSim.getBoolean(model), ((Number) cellLevel.invoke(model)).intValue(),
                        ((Number) cellLevels.invoke(model)).intValue()) : -1;
                int nextSlot = cell ? ((Number) cellSlot.invoke(model)).intValue() : -1;
                if (row.level != next || row.slot != nextSlot) {
                    row.level = next; row.slot = nextSlot; requestRefresh();
                }
            }, ImageView.class, type("com.android.systemui.statusbar.pipeline.mobile.domain.model.SignalIconModel"));
            capable = valid;
            if (typeStage) installTypes();
            log("install", "networkReady=" + capable + " types=OEM multiSim=OEM warnings=OEM");
        } catch (Throwable failure) { disable(failure); }
    }

    private void installTypes() {
        try {
            Class<?> collector = type(MOBILE + "OplusStatusBarMobileViewBinder$bindCustEx$1$2$1$2$1");
            Field image = collector.getDeclaredField("$iconView");
            Field branch = collector.getDeclaredField("$r8$classId");
            image.setAccessible(true); branch.setAccessible(true);
            Class<?> resource = type("com.android.systemui.common.shared.model.Icon$Resource");
            Method resourceId = resource.getMethod("getRes");
            boolean valid = hook("com.android.systemui.common.ui.binder.IconViewBinder", "bind", (chain, result) -> {
                if (Looper.myLooper() != Looper.getMainLooper()) return;
                ImageView view = (ImageView) chain.getArg(1);
                if (isNamed(view, "mobile_type")
                        && resource.isInstance(chain.getArg(0))) {
                    pendingTypes.put(view, ((Number) resourceId.invoke(chain.getArg(0))).intValue());
                }
            }, type("com.android.systemui.common.shared.model.Icon"), ImageView.class);
            valid &= hook(collector.getName(), "emit", (chain, result) -> {
                requireMain();
                ImageView view = (ImageView) image.get(chain.getThisObject());
                if (branch.getInt(chain.getThisObject()) == 1 && isNamed(view, "data_inout")) {
                    int id = ((Number) chain.getArg(0)).intValue();
                    String name = "";
                    try { if (id > 0) name = view.getResources().getResourceEntryName(id); } catch (RuntimeException ignored) {}
                    activityResources.put(view, name);
                    for (Lease lease : leases.values()) {
                        if (lease.activity != null && lease.activity.isFor(view)) lease.activity.onNativeUpdate(name);
                    }
                    requestRefresh(); return;
                }
                if (branch.getInt(chain.getThisObject()) != 2) return;
                if (!typeRows.containsKey(view)) return;
                Object value = chain.getArg(0);
                Integer id = resource.isInstance(value) ? ((Number) resourceId.invoke(value)).intValue() : null;
                pendingTypes.remove(view);
                noteType(view, id, view.getVisibility());
            }, Object.class, type("kotlin.coroutines.Continuation"));
            typeCapable = valid;
            log("type/install", "typeReady=" + valid);
        } catch (Throwable failure) {
            typeCapable = false;
            log("type/install", "typeReady=false reason=" + failure.getClass().getSimpleName());
        }
    }

    private void noteType(ImageView image, Integer resource, int visibility) {
        TypeRow row = typeRows.get(image);
        if (row == null) return;
        String label = "";
        if (resource != null && resource != 0) {
            try { label = ColorOsTypeLabels.get(image.getResources().getResourceEntryName(resource)); }
            catch (RuntimeException ignored) {}
        }
        row.label = label;
        row.nativeVisibility = visibility;
        if (!label.isEmpty() && ownsType(image) && image.getVisibility() == View.VISIBLE) image.setVisibility(View.GONE);
        requestRefresh();
    }

    private Class<?> type(String name) throws ClassNotFoundException { return Class.forName(name, false, loader); }

    private boolean hook(String owner, String name, After action, Class<?>... parameters) {
        try {
            Method method = type(owner).getDeclaredMethod(name, parameters);
            method.setAccessible(true);
            module.hook(method).setId("colorduo-coloros-network-" + name)
                    .setExceptionMode(XposedInterface.ExceptionMode.PASSTHROUGH).intercept(chain -> {
                        Object result = chain.proceed();
                        try { action.run(chain, result); } catch (Throwable failure) { disable(failure); }
                        return result;
                    });
            return true;
        } catch (Throwable failure) { log("hook/" + name, failure.getClass().getSimpleName()); return false; }
    }

    private static void requireMain() {
        if (Looper.myLooper() != Looper.getMainLooper()) throw new IllegalStateException("View event off main");
    }

    private void watch(View root) {
        root.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> requestRefresh());
        root.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) { requestRefresh(); }
            @Override public void onViewDetachedFromWindow(View v) { releaseAll(); requestRefresh(); }
        });
    }

    private synchronized void requestRefresh() {
        if (pending) return;
        pending = true;
        main.post(() -> {
            synchronized (ColorOsNetworkAdapter.this) { pending = false; }
            // Invalidate any old handoff before asking for the next hardware-drawn frame.
            if (!capable) releaseAll();
            changed.run();
        });
    }

    private void disable(Throwable failure) {
        capable = false;
        log("failure", "nativeFallback=" + failure.getClass().getSimpleName());
        requestRefresh();
    }

    Plan prepare(View meter, TrioSettings settings) {
        requireMain();
        if (!capable) return Plan.EMPTY;
        try {
            View wifi = null, container = null;
            ImageView signal = null;
            int bestWifi = 99, bestMobile = 99, wifiCount = 0, mobileCount = 0;
            for (View root : new ArrayList<>(wifiRows.keySet())) {
                int distance = distance(meter, root);
                if (distance > bestWifi || !eligible(root)) continue;
                if (distance < bestWifi) { bestWifi = distance; wifiCount = 0; }
                wifiCount++; wifi = root;
            }
            int mobile = -1;
            int mobileSlot = -1;
            View mobileRoot = null;
            for (Map.Entry<ImageView, Mobile> entry : new ArrayList<>(mobileRows.entrySet())) {
                View root = entry.getValue().root.get();
                int distance = distance(meter, root);
                if (distance > bestMobile || !eligible(root) || ((Number) visibleState.invoke(root)).intValue() != 0) continue;
                if (distance < bestMobile) { bestMobile = distance; mobileCount = 0; }
                mobileCount++; signal = entry.getKey(); mobile = entry.getValue().level;
                mobileSlot = entry.getValue().slot;
                mobileRoot = root;
            }
            if (wifiCount != 1 || !settings.showWifi || latestWifi < 1) wifi = null;
            if (wifi != null) {
                ViewParent parent = wifi.getParent();
                container = parent instanceof View && containerType.isInstance(parent) ? (View) parent : null;
                if (container == null || !"wifi".equals(slot.invoke(wifi))
                        || (ignored(container) && !ownsWifi(container))) wifi = container = null;
            }
            if (mobileCount != 1 || !settings.showMobile
                    || (settings.signalMode != Prefs.SIGNAL_IN_RING && !"outside".equals(BuildConfig.COLOROS_STAGE)) || mobile < 0
                    || mobileSlot < 0 || mobileSlot > 1
                    || (signal.getVisibility() != View.VISIBLE && !ownsSignal(signal))) signal = null;
            String label = "";
            ImageView typeImage = null;
            boolean hideType = false;
            if (typeCapable && mobileCount == 1 && mobileRoot != null) {
                View view = child(mobileRoot, "mobile_type");
                TypeRow row = view instanceof ImageView ? typeRows.get(view) : null;
                if (row != null && row.nativeVisibility == View.VISIBLE && !row.label.isEmpty()) {
                    label = row.label;
                    typeImage = (ImageView) view;
                    // OUT_RING uses the native label until the custom outside strip is available.
                    hideType = settings.mobileTypeMode != Prefs.MOBILE_TYPE_OUT_RING;
                }
            }
            return new Plan(wifi == null ? -1 : latestWifi, signal == null ? -1 : mobile, wifi, container, signal,
                    signal == null ? 0 : mobileSlot, label, typeImage, hideType);
        } catch (Throwable failure) { disable(failure); return Plan.EMPTY; }
    }

    private boolean eligible(View root) throws ReflectiveOperationException {
        return root != null && root.isAttachedToWindow() && root.isShown()
                && Boolean.TRUE.equals(iconVisible.invoke(root)) && !Boolean.TRUE.equals(iconBlocked.invoke(root))
                && (((Number) visibleState.invoke(root)).intValue() == 0 || ownsRoot(root));
    }

    /** Closest shared ancestor, bounded to the local status row; never match across windows. */
    private static int distance(View meter, View root) {
        if (root == null || meter.getWindowToken() == null || meter.getWindowToken() != root.getWindowToken()) return 100;
        ViewParent ancestor = meter.getParent();
        for (int distance = 1; distance <= 4 && ancestor instanceof View; distance++, ancestor = ancestor.getParent()) {
            if (ancestor == meter.getRootView()) break;
            for (ViewParent parent = root.getParent(); parent != null; parent = parent.getParent()) {
                if (parent == ancestor) return distance;
            }
        }
        return 100;
    }

    private boolean ignored(View container) throws IllegalAccessException {
        Object slots = ignoredSlots.get(container);
        return slots instanceof List && ((List<?>) slots).contains("wifi");
    }
    private boolean ownsWifi(View container) {
        for (Lease lease : leases.values()) if (lease.plan.container.get() == container) return true;
        return false;
    }
    private boolean ownsSignal(ImageView signal) {
        for (Lease lease : leases.values()) if (lease.plan.signal.get() == signal) return true;
        return false;
    }
    private boolean ownsType(ImageView image) {
        for (Lease lease : leases.values()) if (lease.plan.hideType && lease.plan.typeImage.get() == image) return true;
        return false;
    }
    private boolean ownsRoot(View root) {
        if (root == null) return false;
        for (Lease lease : leases.values()) {
            if (lease.plan.wifiRoot.get() == root) return true;
            ImageView image = lease.plan.signal.get();
            Mobile row = image == null ? null : mobileRows.get(image);
            if (row != null && row.root.get() == root) return true;
        }
        return false;
    }

    /** Called only after the exact plan has actually drawn and is still current. */
    void commit(View meter, Plan plan) {
        requireMain();
        Lease old = leases.get(meter);
        if (old != null && old.plan.sameOwners(plan)) {
            old.plan = plan;
            // Native emit may reapply VISIBLE even when the resource/text did not change.
            ImageView signal = plan.signal.get();
            if (signal != null && signal.getVisibility() != View.GONE) signal.setVisibility(View.GONE);
            ImageView typeImage = plan.hideType ? plan.typeImage.get() : null;
            if (typeImage != null && typeImage.getVisibility() != View.GONE) typeImage.setVisibility(View.GONE);
            syncGap(old, true);
            return;
        }
        retain(meter, plan);
        old = leases.get(meter);
        View container = plan.container.get();
        ImageView signal = plan.signal.get();
        ImageView typeImage = plan.hideType ? plan.typeImage.get() : null;
        if (container == null && signal == null && typeImage == null) return;
        try {
            // Prevent two meters from sharing a handoff of one native network row.
            if ((container != null && (old == null || old.plan.container.get() != container) && ownsWifi(container))
                    || (signal != null && (old == null || old.plan.signal.get() != signal) && ownsSignal(signal))
                    || (typeImage != null && (old == null || old.plan.typeImage.get() != typeImage) && ownsType(typeImage))) return;
            int visibility = old != null && old.plan.signal.get() == signal ? old.signalVisibility
                    : signal == null ? View.VISIBLE : signal.getVisibility();
            Lease next = old == null ? new Lease(plan, visibility) : old;
            boolean addWifi = container != null && (old == null || old.plan.container.get() != container);
            next.plan = plan; next.signalVisibility = visibility; leases.put(meter, next);
            if (addWifi) addIgnored.invoke(container, "wifi");
            if (signal != null) signal.setVisibility(View.GONE);
            if (typeImage != null) typeImage.setVisibility(View.GONE);
            syncGap(leases.get(meter), true);
            log("handoff/" + Integer.toHexString(System.identityHashCode(meter)),
                    "drawn=true wifi=" + plan.wifi + " mobile=" + plan.mobile
                            + " type=" + plan.typeLabel + " hideType=" + plan.hideType);
        } catch (Throwable failure) { release(meter); disable(failure); }
    }

    void release(View meter) {
        requireMain();
        Lease lease = leases.remove(meter);
        if (lease == null) return;
        if (lease.activity != null) lease.activity.restore();
        if (lease.gap != null) lease.gap.restore();
        View container = lease.plan.container.get();
        ImageView signal = lease.plan.signal.get();
        ImageView typeImage = lease.plan.hideType ? lease.plan.typeImage.get() : null;
        try { if (container != null) removeIgnored.invoke(container, "wifi"); }
        catch (Throwable failure) { log("restore/wifi", failure.getClass().getSimpleName()); }
        if (signal != null && signal.getVisibility() == View.GONE) signal.setVisibility(lease.signalVisibility);
        TypeRow currentType = typeImage == null ? null : typeRows.get(typeImage);
        if (typeImage != null && currentType != null && typeImage.getVisibility() == View.GONE)
            typeImage.setVisibility(currentType.nativeVisibility);
        log("restore/" + Integer.toHexString(System.identityHashCode(meter)), "networkNative=true");
    }

    /** Revoke only views absent from the next frame; signal-strength changes keep their lease. */
    void retain(View meter, Plan next) {
        Lease old = leases.get(meter);
        if (old == null) return;
        Plan previous = old.plan, kept = previous.retainedBy(next);
        if (previous.sameOwners(kept)) return;
        old.plan = kept;
        if (previous.signal.get() != kept.signal.get()) {
            if (old.activity != null) { old.activity.restore(); old.activity = null; }
            if (old.gap != null) { old.gap.restore(); old.gap = null; }
            old.compactRoot = new WeakReference<>(null);
            ImageView signal = previous.signal.get();
            if (signal != null && signal.getVisibility() == View.GONE) signal.setVisibility(old.signalVisibility);
        }
        if (previous.container.get() != kept.container.get()) {
            try { if (previous.container.get() != null) removeIgnored.invoke(previous.container.get(), "wifi"); }
            catch (Throwable failure) { log("restore/wifi", failure.getClass().getSimpleName()); }
        }
        if (previous.hideType && (!kept.hideType || previous.typeImage.get() != kept.typeImage.get())) {
            ImageView image = previous.typeImage.get(); TypeRow row = image == null ? null : typeRows.get(image);
            if (image != null && row != null && image.getVisibility() == View.GONE) image.setVisibility(row.nativeVisibility);
        }
        if (kept.container.get() == null && kept.signal.get() == null && !kept.hideType) {
            if (old.activity != null) old.activity.restore();
            if (old.gap != null) old.gap.restore();
            leases.remove(meter);
        }
    }

    private void releaseAll() {
        requireMain();
        for (View meter : new ArrayList<>(leases.keySet())) release(meter);
    }

    /** Layout listeners only schedule work when the empty/nonempty group boundary changes. */
    void onHostLayout(View meter) {
        Lease lease = leases.get(meter);
        if (lease != null && syncGap(lease, false)) requestRefresh();
    }

    private boolean syncGap(Lease lease, boolean apply) {
        try {
            ImageView signal = lease.plan.signal.get();
            Mobile mobile = signal == null ? null : mobileRows.get(signal);
            View root = mobile == null ? null : mobile.root.get();
            if (root == null && lease.plan.hideType) {
                TypeRow row = typeRows.get(lease.plan.typeImage.get());
                if (row != null) root = row.root.get();
            }
            if (root == null) return false;
            View group = child(root, "mobile_group");
            View dot = child(root, "status_bar_dot");
            if (group == null || dot == null) return false;
            if (lease.compactRoot.get() != root) {
                if (lease.activity != null) lease.activity.restore();
                if (lease.gap != null) lease.gap.restore();
                lease.activity = null; lease.gap = null; lease.compactRoot = new WeakReference<>(root);
            }
            if (lease.gap == null) lease.gap = new ColorOsMobileGap(dot);
            View activityView = child(root, "data_inout");
            boolean activityChanged = false;
            if (activityView instanceof ImageView) {
                if (lease.activity == null) lease.activity = new ColorOsActivityLayout((ImageView) activityView);
                if (apply) {
                    int before = activityView.getVisibility();
                    lease.activity.sync(signal != null && signal.getVisibility() == View.GONE, activityResources.get(activityView));
                    activityChanged = before != activityView.getVisibility();
                    if (activityChanged) log("activity/" + Integer.toHexString(System.identityHashCode(root)),
                            "hidden=" + lease.activity.owned() + " visibility=" + activityView.getVisibility());
                }
            }
            boolean empty = group.getVisibility() == View.GONE || group.getMeasuredWidth() == 0 || hasNoVisibleContent(group);
            int nativeState = ((Number) visibleState.invoke(root)).intValue();
            boolean changed = lease.gap.needsChange(nativeState, empty);
            if (apply && changed) {
                lease.gap.sync(nativeState, empty);
                log("gap/" + Integer.toHexString(System.identityHashCode(root)),
                        "emptyGroup=" + empty + " dotVisibility=" + dot.getVisibility() + " nativeState=" + nativeState);
            }
            return changed || activityChanged;
        } catch (Throwable failure) {
            if (lease.gap != null) lease.gap.restore();
            return false;
        }
    }

    private static boolean hasNoVisibleContent(View view) {
        if (view.getVisibility() != View.VISIBLE) return true;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) if (!hasNoVisibleContent(group.getChildAt(i))) return false;
            return true;
        }
        return view.getMeasuredWidth() == 0 || view.getMeasuredHeight() == 0;
    }

    private static View child(View root, String name) {
        int id = root.getResources().getIdentifier(name, "id", "com.android.systemui");
        return id == 0 ? null : root.findViewById(id);
    }

    private static boolean isNamed(View view, String name) {
        if (view == null || view.getId() == View.NO_ID || view.getId() == 0) return false;
        try { return name.equals(view.getResources().getResourceEntryName(view.getId())); }
        catch (RuntimeException ignored) { return false; }
    }

    private void log(String key, String message) {
        try {
            String line = ledger.record(key, message, SystemClock.uptimeMillis());
            if (line != null) module.log(4, "ColorDuoColorOSNetwork", line);
        } catch (Throwable ignored) {}
    }
}
