/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/**
 * Own only a ready native battery ImageView and the native rows its actual frame represents.
 * Unsupported battery styles, outside percent, reverse charge and exceptional network icons stay native.
 */
final class ColorOsBatteryAdapter {
    private static final String UI = ColorOsSymbols.BATTERY_UI_PREFIX;
    private final XposedModule module;
    private final ClassLoader loader;
    private final Map<View, Host> hosts = new WeakHashMap<>();
    private final ObservationLedger ledger = new ObservationLedger(64, 500);
    private final ColorOsConfig config;
    private final ColorOsNetworkAdapter network;
    private final ColorOsPowerSource power;
    private final ColorOsClockBridge clock;
    private final ColorOsFontSource font;
    private final ColorOsReadoutSource readouts;
    private final boolean outsideStage = "outside".equals(BuildConfig.COLOROS_STAGE);
    private final boolean capsuleStage = "capsule".equals(BuildConfig.COLOROS_STAGE) || "font".equals(BuildConfig.COLOROS_STAGE) || outsideStage;
    private final boolean userLayout = "ring".equals(BuildConfig.COLOROS_STAGE)
            || "type".equals(BuildConfig.COLOROS_STAGE) || capsuleStage;
    private Class<?> meterType;
    private Method batteryIcon;
    private Method batteryStyle;
    private Method nativeDrawable;
    private Method powerLevel;
    private Method tintColor;
    private Method contentDescription;
    private Method iconStyle;
    private Method moduleTag;
    private Field hidden;
    private Field disabled;
    private boolean capable;

    private static final class Host {
        final WeakReference<View> meter;
        final WeakReference<ImageView> image;
        final String scene;
        final String key;
        final ColorOsBatterySize size;
        final ColorOsPlugBridge plugBridge = new ColorOsPlugBridge();
        int generation;
        boolean attached;
        int latestLevel = -1;
        int latestTint;
        int latestStyle = -1;
        long mountToken;
        boolean awaitLayout;
        boolean resizing;
        boolean configurationHandoff;
        boolean needsNativeStyle;
        int nativeStyleDepth;
        boolean failed;
        boolean presentationPending;
        boolean refreshQueued;
        long refreshSequence;
        boolean lastOutsidePercent;
        ColorOsChargeIcon chargeIcon;
        TrioSettings layoutSource;
        TrioSettings layoutSettings;
        int layoutScene = -1;
        int testedWidth;
        int testedHeight;
        TrioSettings sourceSettings;
        TrioSettings paintSettings;
        ColorOsNetworkAdapter.Plan networkPlan = ColorOsNetworkAdapter.Plan.EMPTY;
        ColorOsNetworkAdapter.Plan readingsPlan = ColorOsNetworkAdapter.Plan.EMPTY;
        ColorOsOutsideLayout outside = ColorOsOutsideLayout.EMPTY;
        ColorOsBatteryDrawable preview;
        WeakReference<ViewTreeObserver> visibilityTree;
        ViewTreeObserver.OnGlobalLayoutListener visibilityListener;
        boolean lastGlobalShown;
        String nativeDescription;
        String moduleDescription;
        Host(View meter, ImageView image, String scene) {
            this.meter = new WeakReference<>(meter);
            this.image = new WeakReference<>(image);
            this.scene = scene;
            size = new ColorOsBatterySize(image);
            key = scene + "/" + Integer.toHexString(System.identityHashCode(meter));
        }
    }

    private interface Action { void run(XposedInterface.Chain chain) throws Throwable; }

    private ColorOsBatteryAdapter(XposedModule module, ClassLoader loader) {
        this.module = module;
        this.loader = loader;
        config = new ColorOsConfig(module, this::refreshAll);
        network = ("network".equals(BuildConfig.COLOROS_STAGE) || userLayout)
                ? new ColorOsNetworkAdapter(module, loader, this::refreshAll) : null;
        power = userLayout ? new ColorOsPowerSource(module, loader, this::refreshAll, () -> config.get().debugLog) : null;
        clock = capsuleStage ? new ColorOsClockBridge(module, loader, this::refreshAll) : null;
        font = ("font".equals(BuildConfig.COLOROS_STAGE) || outsideStage) ? new ColorOsFontSource(module, this::refreshAll) : null;
        readouts = outsideStage ? new ColorOsReadoutSource(this::refreshAll) : null;
    }

    static void install(XposedModule module, ClassLoader loader) {
        new ColorOsBatteryAdapter(module, loader).install();
    }

    private void install() {
        try {
            if (network != null) network.install();
            if (clock != null) clock.install();
            meterType = type(UI + ColorOsSymbols.CLASS_VIEW_STAT_BATTERY_METER_VIEW);
            batteryIcon = meterType.getMethod(ColorOsSymbols.NAME_GET_BATTERY_ICON);
            batteryStyle = meterType.getMethod(ColorOsSymbols.NAME_GET_BATTERY_ICON_STYLE);
            nativeDrawable = meterType.getMethod(ColorOsSymbols.NAME_GET_BATTERY_STYLE_DRAWABLE);
            powerLevel = type(UI + ColorOsSymbols.CLASS_MODEL_STAT_BATTERY_ICON).getMethod(ColorOsSymbols.NAME_GET_POWER_LEVEL);
            tintColor = type(UI + ColorOsSymbols.CLASS_MODEL_STAT_BATTERY_ICON).getMethod(ColorOsSymbols.NAME_GET_TINT_COLOR);
            contentDescription = type(UI + ColorOsSymbols.CLASS_MODEL_STAT_BATTERY_ICON).getMethod(ColorOsSymbols.NAME_GET_CONTENT_DESCRIPTION);
            iconStyle = type(UI + ColorOsSymbols.CLASS_MODEL_BATTERY_ICON_STYLE).getMethod(ColorOsSymbols.NAME_GET_ICON_STYLE);
            moduleTag = type(UI + ColorOsSymbols.CLASS_VIEWMODEL_LOCATION_BASED_BATTERY_VIEW_MODEL).getMethod(ColorOsSymbols.NAME_GET_MODULE_TAG);
            hidden = Refl.field(meterType, ColorOsSymbols.NAME_HIDDEN);
            disabled = Refl.field(meterType, ColorOsSymbols.NAME_DISABLE);
            boolean valid = hidden != null && disabled != null;
            valid &= hook(meterType, ColorOsSymbols.NAME_BIND_VIEW_MODEL, null, chain -> {
                View meter = (View) chain.getThisObject();
                String scene = String.valueOf(moduleTag.invoke(chain.getArg(0)));
                if (!knownScene(scene)) return;
                View target = child(meter, ColorOsSymbols.ID_BATTERY_ICON_VIEW);
                if (!(target instanceof ImageView)) return;
                Host previous = hosts.get(meter);
                if (previous != null) { unwatchVisibility(previous); release(previous, "rebind"); }
                Host host = new Host(meter, (ImageView) target, scene);
                host.attached = meter.isAttachedToWindow();
                captureIcon(host, batteryIcon.invoke(meter));
                captureStyle(host, batteryStyle.invoke(meter));
                hosts.put(meter, host);
                watchVisibility(host);
                config.registerReceiver(meter.getContext());
                SystemUiCompatibilityReporter.register(meter.getContext());
                syncPower();
                reconcile(host);
            }, type(UI + ColorOsSymbols.CLASS_VIEWMODEL_LOCATION_BASED_BATTERY_VIEW_MODEL));
            valid &= hook(meterType, ColorOsSymbols.NAME_ON_ATTACHED_TO_WINDOW, null, chain -> {
                Host host = hosts.get(chain.getThisObject());
                if (host != null) {
                    host.generation++; host.attached = true;
                    watchVisibility(host); syncPower(); reconcile(host);
                }
            });
            valid &= hook(meterType, ColorOsSymbols.NAME_ON_DETACHED_FROM_WINDOW, chain -> {
                Host host = hosts.get(chain.getThisObject());
                if (host != null) {
                    host.generation++;
                    host.attached = false;
                    unwatchVisibility(host);
                    host.needsNativeStyle = true;
                    release(host, "detach");
                    syncPower();
                }
            }, null);
            valid &= hook(meterType, ColorOsSymbols.NAME_ON_CONFIGURATION_CHANGED, chain -> {
                Host host = hosts.get(chain.getThisObject());
                if (host != null) {
                    View meter = host.meter.get();
                    ImageView image = host.image.get();
                    HostDecision.Decision transition = HostDecision.configuration(new HostDecision.Configuration(
                            capable && config.ready() && config.get().enabled,
                            meter != null && meter.isAttachedToWindow(),
                            meter != null && image != null && meter.isShown() && image.isShown(),
                            image != null && image.getDrawable() == host.preview,
                            host.preview != null && host.preview.hasDrawn(), host.failed, host.needsNativeStyle,
                            meter != null && !Refl.getBool(hidden, meter, true) && !Refl.getBool(disabled, meter, true),
                            host.latestStyle, host.plugBridge.active()));
                    host.generation++; // Folding may retain both View identity and displayId 0.
                    host.awaitLayout = true;
                    host.failed = false;
                    host.paintSettings = null;
                    host.testedWidth = host.testedHeight = 0;
                    if (transition.releaseLeases()) release(host, transition.reason());
                    else {
                        // Do not destroy a proven style-2 popup while the same host changes its layout.
                        // The old frame can scale safely; new leases still wait for the new frame's draw.
                        host.configurationHandoff = true;
                        host.resizing = true;
                        host.refreshQueued = false;
                        host.refreshSequence++;
                        log(host.key + "/configuration", "retained=true nativeStyle=" + host.latestStyle
                                + " generation=" + host.generation);
                        scheduleReconcile(host);
                    }
                }
            }, null, Configuration.class);
            valid &= hook(meterType, ColorOsSymbols.NAME_ON_LAYOUT, null, chain -> {
                Host host = hosts.get(chain.getThisObject());
                if (host != null) { host.awaitLayout = false; host.resizing = false; reconcile(host); }
            }, boolean.class, int.class, int.class, int.class, int.class);
            valid &= hook(meterType, ColorOsSymbols.NAME_ON_HIDDEN_CHANGED, null, chain -> refresh(chain.getThisObject()),
                    ArrayList.class, boolean.class);
            valid &= hook(meterType, ColorOsSymbols.NAME_ON_DARK_CHANGED, null, chain -> refresh(chain.getThisObject()),
                    ArrayList.class, float.class, int.class);
            Class<?> binder = type(UI + ColorOsSymbols.CLASS_BINDER_BATTERY_VIEW_BINDER);
            valid &= hook(binder, ColorOsSymbols.NAME_BIND_UPDATE_BATTERY_CONTENT_VIEW, null,
                    chain -> {
                        Host host = hosts.get(chain.getArg(0));
                        if (host != null) {
                            // The native collector may assign meter.batteryIcon only AFTER this helper.
                            captureIcon(host, chain.getArg(1));
                            reconcile(host);
                        }
                    }, meterType, type(UI + ColorOsSymbols.CLASS_MODEL_STAT_BATTERY_ICON));
            valid &= installStyleHook(binder);
            valid &= hook(binder, ColorOsSymbols.NAME_BIND_UPDATE_CHARGING_VIEW, null, chain -> {
                Host host = hosts.get(chain.getArg(1));
                if (host != null && chain.getArg(2) != null && chain.getArg(0) instanceof ImageView) {
                    ColorOsChargeIcon icon = chargeIcon(host, (ImageView) chain.getArg(0));
                    icon.onNativeUpdate();
                    reconcile(host);
                }
            }, ImageView.class, meterType, type(UI + ColorOsSymbols.CLASS_MODEL_CHARGE_ICON));
            capable = valid;
            ColorOsProfile.note(CompatibilitySnapshot.Capability.BATTERY_HOOK, valid, "HookUnavailable");
            installDump();
            log("install", "stage=" + BuildConfig.COLOROS_STAGE + " coreReady=" + capable);
            config.start();
        } catch (Throwable failure) {
            capable = false;
            ColorOsProfile.note(CompatibilitySnapshot.Capability.BATTERY_HOOK, false, failure.getClass().getSimpleName());
            log("install", "coreReady=false reason=" + failure.getClass().getSimpleName());
        }
    }

    private Class<?> type(String name) throws ClassNotFoundException {
        return Class.forName(name, false, loader);
    }

    private void installDump() {
        try {
            Method dump = meterType.getDeclaredMethod(ColorOsSymbols.NAME_DUMP, java.io.PrintWriter.class, String[].class);
            dump.setAccessible(true);
            module.hook(dump).setId("colorduo-coloros-battery-dump")
                    .setExceptionMode(XposedInterface.ExceptionMode.PASSTHROUGH).intercept(chain -> {
                        Object result = chain.proceed();
                        try {
                            java.io.PrintWriter out = (java.io.PrintWriter) chain.getArg(0);
                            out.println("  ColorDuo ColorOS adapter: stage=" + BuildConfig.COLOROS_STAGE);
                            if (clock != null) out.println("    clockReady=" + clock.ready() + " time=" + clock.time());
                            if (font != null) out.println("    fontStatus=" + font.status());
                            if (readouts != null) {
                                ColorOsReadoutSource.State readings = readouts.get();
                                out.println("    readouts active=" + readouts.active() + " charging=" + readings.charging
                                        + " watts=" + readings.watts + " temperature=" + readings.temperature);
                            }
                            if (power != null) {
                                ColorOsPowerSource.State state = power.get();
                                out.println("    nativePower ready=" + state.ready + " charging=" + state.charging
                                        + " quick=" + state.quick + " plugged=" + state.plugged
                                        + " full=" + state.full + " saving=" + state.saving);
                            }
                            Host host = hosts.get(chain.getThisObject());
                            if (host != null) {
                                ImageView image = host.image.get();
                                android.view.ViewGroup.LayoutParams params = image == null ? null : image.getLayoutParams();
                                out.println("    batteryLayout measured=" + (image == null ? "missing" : image.getWidth() + "x" + image.getHeight())
                                        + " requested=" + (params == null ? "missing" : params.width + "x" + params.height)
                                        + " outside=" + host.outside.reserve + " pending=" + host.awaitLayout + " failed=" + host.failed);
                            }
                            if (host != null && host.chargeIcon != null)
                                out.println("    chargeIcon owned=" + host.chargeIcon.owned()
                                        + " nativeVisibility=" + host.chargeIcon.nativeVisibility());
                            for (String line : ledger.snapshot()) out.println("    adapter/" + line);
                            if (network != null) network.dump(out);
                        } catch (Throwable ignored) {}
                        return result;
                    });
        } catch (Throwable failure) { log(ColorOsSymbols.NAME_DUMP, "diagnostics unavailable=" + failure.getClass().getSimpleName()); }
    }

    /** The OEM replacement is synchronous: restore our drawn frame before the next traversal. */
    private boolean installStyleHook(Class<?> binder) {
        try {
            Method method = binder.getDeclaredMethod(ColorOsSymbols.NAME_BIND_UPDATE_BATTERY_ICON_STYLE, ImageView.class,
                    meterType, type(UI + ColorOsSymbols.CLASS_VIEWMODEL_LOCATION_BASED_BATTERY_VIEW_MODEL),
                    android.widget.FrameLayout.class, android.widget.TextView.class,
                    type(UI + ColorOsSymbols.CLASS_MODEL_BATTERY_ICON_STYLE));
            method.setAccessible(true);
            module.hook(method).setId("colorduo-coloros-battery-bind$updateBatteryIconStyle")
                    .setExceptionMode(XposedInterface.ExceptionMode.PASSTHROUGH).intercept(chain -> {
                        Host host = hosts.get(chain.getArg(1));
                        if (host == null || Looper.myLooper() != Looper.getMainLooper()) return chain.proceed();
                        boolean completed = false;
                        host.nativeStyleDepth++;
                        host.needsNativeStyle = true;
                        try {
                            // The native helper reads/mutates the same LayoutParams in place.
                            // Do not request an intermediate native-sized traversal here.
                            try { host.size.beforeNativeStyle(); }
                            catch (Throwable failure) {
                                host.failed = true;
                                log(host.key + "/style-size-error", failure.getClass().getSimpleName());
                                try { release(host, "native-style-size-failure"); } catch (Throwable ignored) {}
                            }
                            Object result = chain.proceed();
                            completed = true;
                            return result;
                        } finally {
                            host.nativeStyleDepth--;
                            try {
                                if (!completed) {
                                    host.failed = true;
                                    release(host, "native-style-failure");
                                } else if (host.nativeStyleDepth == 0) {
                                    captureStyle(host, chain.getArg(5));
                                    host.needsNativeStyle = false;
                                    finishNativeStyle(host);
                                }
                            } catch (Throwable failure) {
                                host.failed = true;
                                log(host.key + "/style-error", failure.getClass().getSimpleName());
                                try { release(host, "native-style-handoff-failure"); } catch (Throwable ignored) {}
                            }
                        }
                    });
            return true;
        } catch (Throwable failure) {
            log("hook/bind$updateBatteryIconStyle", "installed=false reason=" + failure.getClass().getSimpleName());
            return false;
        }
    }

    private void finishNativeStyle(Host host) throws Throwable {
        View meter = host.meter.get();
        ImageView image = host.image.get();
        if (meter == null || image == null) { release(host, "native-style-host-missing"); return; }
        Object original = nativeDrawable.invoke(meter);
        boolean popup = host.plugBridge.style(host.latestStyle,
                config.ready() && config.get().enabled && !host.failed && host.preview != null && host.preview.hasDrawn()
                        && meter.isAttachedToWindow() && meter.isShown()
                        && !Refl.getBool(hidden, meter, true) && !Refl.getBool(disabled, meter, true),
                SystemClock.uptimeMillis());
        boolean supported = host.latestStyle == 1 || host.latestStyle == 3 || host.latestStyle == 4 || popup;
        if (popup) {
            original = new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT);
            image.setVisibility(View.VISIBLE);
            log(host.key + "/plug-bridge", "nativeStyle=2 retained=true generation=" + host.generation);
        }
        if (config.ready() && config.get().enabled && supported && !host.failed
                && original instanceof Drawable && host.preview != null && host.preview.hasDrawn()) {
            // Network, clock and charging leases still correspond to our last successful frame.
            // Only the OEM fallback and sizing change; the next frame uses the newest readings.
            host.preview.updateNative((Drawable) original);
            host.size.apply(meter, config.get(), host.outside.reserve);
            image.setImageDrawable(host.preview);
            android.view.ViewGroup.LayoutParams params = image.getLayoutParams();
            if (params != null && (image.getWidth() != params.width || image.getHeight() != params.height)) {
                host.awaitLayout = true;
                host.resizing = true;
                scheduleReconcile(host);
            }
            log(host.key + "/style", "nativeStyle=" + host.latestStyle + " retained=true generation=" + host.generation);
        }
        reconcile(host);
    }

    private boolean hook(Class<?> owner, String name, Action before, Action after, Class<?>... signature) {
        try {
            Method method = owner.getDeclaredMethod(name, signature);
            method.setAccessible(true);
            module.hook(method).setId("colorduo-coloros-battery-" + name)
                    .setExceptionMode(XposedInterface.ExceptionMode.PASSTHROUGH)
                    .intercept(chain -> {
                        action(name, before, chain);
                        Object result = chain.proceed();
                        action(name, after, chain);
                        return result;
                    });
            return true;
        } catch (Throwable failure) {
            log("hook/" + name, "installed=false reason=" + failure.getClass().getSimpleName());
            return false;
        }
    }

    private void action(String name, Action action, XposedInterface.Chain chain) {
        if (action == null) return;
        if (Looper.myLooper() != Looper.getMainLooper()) {
            log("thread", "offMainSkipped=" + name);
            return;
        }
        try { action.run(chain); }
        catch (Throwable failure) {
            log("error/" + name, failure.getClass().getSimpleName());
            for (Host host : new ArrayList<>(hosts.values())) {
                host.failed = true;
                try { release(host, "probe-failure"); } catch (Throwable ignored) {}
            }
        }
    }

    private static boolean knownScene(String scene) {
        return "home".equals(scene) || "keyguard".equals(scene) || "qs".equals(scene) || "qsSplit".equals(scene);
    }

    private void refresh(Object meter) throws Throwable {
        Host host = hosts.get(meter);
        if (host != null) reconcile(host);
    }

    private void captureIcon(Host host, Object icon) throws ReflectiveOperationException {
        if (icon == null) return;
        host.latestLevel = ((Number) powerLevel.invoke(icon)).intValue();
        host.latestTint = ((Number) tintColor.invoke(icon)).intValue();
        host.nativeDescription = (String) contentDescription.invoke(icon);
    }

    private void captureStyle(Host host, Object style) throws ReflectiveOperationException {
        if (style != null) host.latestStyle = ((Number) iconStyle.invoke(style)).intValue();
    }

    private void refreshAll() {
        syncPower();
        for (Host host : new ArrayList<>(hosts.values())) {
            try { reconcile(host); }
            catch (Throwable failure) {
                host.failed = true;
                try { release(host, "config-failure"); } catch (Throwable ignored) {}
            }
        }
    }

    private void syncPower() {
        if (power == null) return;
        android.content.Context context = null;
        if (config.ready() && config.get().enabled) {
            for (Host host : new ArrayList<>(hosts.values())) {
                View meter = host.meter.get();
                if (host.attached && meter != null) { context = meter.getContext(); break; }
            }
        }
        power.setActive(context, context != null);
        if (font != null) font.setActive(config.fontFile(), context != null);
        if (network != null) {
            TrioSettings settings = config.get();
            boolean needsType = settings.content.mobileTypeMode() != Prefs.MOBILE_TYPE_OFF
                    || ChargeLayout.forScene(settings, ChargeLayout.CHARGING).content.mobileTypeMode() != Prefs.MOBILE_TYPE_OFF
                    || ChargeLayout.forScene(settings, ChargeLayout.QUICK).content.mobileTypeMode() != Prefs.MOBILE_TYPE_OFF;
            network.setActive(context, context != null && needsType);
        }
        if (readouts != null) readouts.setActive(context, context != null && (config.get().content.perfPower() || config.get().content.perfTemp()));
        if (clock != null) {
            TrioSettings settings = config.get();
            boolean needsClock = settings.trioStyle == Prefs.STYLE_CAPSULE
                    || TrioAppearance.of(settings).hasRingPart(Prefs.PART_CLOCK)
                    || TrioAppearance.of(ChargeLayout.forScene(settings, ChargeLayout.CHARGING)).hasRingPart(Prefs.PART_CLOCK)
                    || TrioAppearance.of(ChargeLayout.forScene(settings, ChargeLayout.QUICK)).hasRingPart(Prefs.PART_CLOCK);
            clock.setActive(context, context != null && needsClock);
        }
    }

    private void watchVisibility(Host host) {
        unwatchVisibility(host);
        View meter = host.meter.get();
        if (meter == null) return;
        ViewTreeObserver tree = meter.getViewTreeObserver();
        host.visibilityListener = () -> {
            View current = host.meter.get();
            ImageView image = host.image.get();
            boolean shown = current != null && image != null && current.isShown() && image.isShown();
            if (network != null && current != null) network.onHostLayout(current);
            if (host.presentationPending) scheduleReconcile(host);
            if (shown == host.lastGlobalShown) return;
            host.lastGlobalShown = shown;
            try { reconcile(host); }
            catch (Throwable failure) {
                host.failed = true;
                try { release(host, "visibility-failure"); } catch (Throwable ignored) {}
            }
        };
        host.visibilityTree = new WeakReference<>(tree);
        tree.addOnGlobalLayoutListener(host.visibilityListener);
    }

    private void unwatchVisibility(Host host) {
        ViewTreeObserver tree = host.visibilityTree == null ? null : host.visibilityTree.get();
        if (tree != null && tree.isAlive() && host.visibilityListener != null)
            tree.removeOnGlobalLayoutListener(host.visibilityListener);
        // A floating observer can merge its listeners into the attached window's observer.
        View meter = host.meter.get();
        ViewTreeObserver current = meter == null ? null : meter.getViewTreeObserver();
        if (current != null && current != tree && current.isAlive() && host.visibilityListener != null)
            current.removeOnGlobalLayoutListener(host.visibilityListener);
        host.visibilityTree = null;
        host.visibilityListener = null;
    }

    private void reconcile(Host host) throws Throwable {
        View meter = host.meter.get();
        ImageView image = host.image.get();
        if (meter == null || image == null) return;
        if (host.nativeStyleDepth > 0) return; // Nested OEM content callbacks cannot release a drawn frame mid-update.
        TrioSettings settings = layoutSettings(host);
        boolean blocked = !capable || !config.ready() || !config.get().enabled || host.failed
                || host.needsNativeStyle || !meter.isAttachedToWindow() || !meter.isShown() || !image.isShown()
                || (power != null && !power.get().ready)
                || (userLayout && config.get().trioStyle != Prefs.STYLE_RING
                    && !(capsuleStage && config.get().trioStyle == Prefs.STYLE_CAPSULE))
                || (clock != null && (settings.trioStyle == Prefs.STYLE_CAPSULE
                    || TrioAppearance.of(settings).hasRingPart(Prefs.PART_CLOCK)) && !clock.ready())
                || Refl.getBool(hidden, meter, true) || Refl.getBool(disabled, meter, true);
        HostDecision.Decision gate = HostDecision.gate(new HostDecision.Gate(true, false, !blocked,
                host.awaitLayout, host.resizing));
        if (gate.action() == HostDecision.Action.RELEASE) {
            log(host.key + "/blocked", "attached=" + meter.isAttachedToWindow()
                    + " meterShown=" + meter.isShown() + " imageShown=" + image.isShown()
                    + " meterVisibility=" + meter.getVisibility() + " imageVisibility=" + image.getVisibility()
                    + " policyHidden=" + Refl.getBool(hidden, meter, true)
                    + " disabled=" + Refl.getBool(disabled, meter, true)
                    + " style=" + host.latestStyle + " failed=" + host.failed + " nativeStylePending=" + host.needsNativeStyle
                    + " powerReady=" + (power == null || power.get().ready)
                    + " clockReady=" + (clock == null || clock.ready())
                    + " ancestors=" + visibilityPath(meter));
            release(host, gate.reason());
            return;
        }
        if (gate.action() == HostDecision.Action.WAIT) {
            // A native tint callback can run inside the very layout that applies our size.
            // Keep the pending dimensions until that layout finishes; all ownership is released.
            if (gate.releaseLeases()) release(host, gate.reason());
            return;
        }
        Object original = nativeDrawable.invoke(meter);
        if (original == null && host.plugBridge.active() && host.preview != null) original = host.preview.original();
        if (host.latestLevel < 0) captureIcon(host, batteryIcon.invoke(meter));
        if (host.latestStyle < 0) captureStyle(host, batteryStyle.invoke(meter));
        int styleId = host.latestStyle;
        int level = host.latestLevel;
        int tint = host.latestTint;
        int width = image.getWidth();
        int height = image.getHeight();
        HostDecision.Decision model = HostDecision.model(new HostDecision.Model(original instanceof Drawable,
                styleId, host.plugBridge.active(), level, tint, width, height));
        if (model.action() == HostDecision.Action.RELEASE) {
            release(host, model.reason()); return;
        }
        ColorOsNetworkAdapter.Plan rawPlan = network == null ? ColorOsNetworkAdapter.Plan.EMPTY : network.prepare(meter, settings);
        View nativePercent = child(meter, ColorOsSymbols.ID_BATTERY_PERCENTAGE_VIEW);
        boolean outsidePercent = nativePercent != null && nativePercent.getVisibility() == View.VISIBLE;

        if (font != null) font.prepareRenderer();
        int[] base = host.size.baseSize(meter, config.get());
        ColorOsOutsideLayout outside = outsideLayout(meter, settings, rawPlan, outsidePercent, level,
                base[1], meter.getResources().getDisplayMetrics().widthPixels / 3 - base[0]);
        HostDecision.Decision preparation = HostDecision.prepare(new HostDecision.Preparation(
                capsuleStage && host.size.resizeNeeded(meter, config.get(), outside.reserve),
                outside.reserve <= 0 || (width >= base[0] + outside.reserve && height >= base[1]),
                image.isLayoutRequested()));
        if (preparation.reason().equals("resize")) {
            // Keep the proven frame and its leases while a power/temperature label
            // changes width. New ownership still waits for the resized frame to draw.
            if (!host.configurationHandoff && !(host.preview != null && host.preview.hasDrawn()
                    && image.getDrawable() == host.preview)) release(host, preparation.reason());
            host.awaitLayout = true;
            host.resizing = true;
            host.size.apply(meter, config.get(), outside.reserve);
            scheduleReconcile(host);
            return;
        }
        if (preparation.action() == HostDecision.Action.WAIT) {
            host.awaitLayout = true;
            host.resizing = true;
            scheduleReconcile(host);
            return;
        }
        if (preparation.action() == HostDecision.Action.FIT_OUTSIDE) {
            log(host.key + "/outside-fit", "measured=" + width + "x" + height
                    + " base=" + base[0] + "x" + base[1] + " requestedOutside=" + outside.reserve);
            outside = outsideLayout(meter, settings, rawPlan, outsidePercent, level,
                    Math.min(height, base[1]), Math.max(0, width - base[0]));
        }
        if (host.preview != null && image.getDrawable() != host.preview) {
            release(host, "drawable-replaced");
        }
        HostDecision.Decision attachment = HostDecision.attach(new HostDecision.Attachment(host.preview != null,
                image.getDrawable() == original, host.testedWidth == width && host.testedHeight == height));
        if (attachment.action() == HostDecision.Action.WAIT) {
            log(host.scene, "nativeOwnershipRetained=drawable-conflict"); return;
        }
        ColorOsNetworkAdapter.Plan plan = rawPlan;
        host.outside = outside;
        ColorOsPowerSource.State powerState = power == null ? ColorOsPowerSource.State.UNKNOWN : power.get();
        if (userLayout) {
            TrioSettings desired = ColorOsRingPreset.userLayout(settings, outsidePercent, plan);
            plan = ColorOsRingPreset.represented(desired, level, powerState.charging, plan, outside);
        }
        boolean networkChanged = !host.networkPlan.same(plan);
        boolean readingsChanged = !host.readingsPlan.same(rawPlan);
        if (networkChanged && network != null) network.retain(meter, plan);
        host.networkPlan = plan;
        host.readingsPlan = rawPlan;
        if (host.paintSettings == null || host.sourceSettings != settings || host.lastOutsidePercent != outsidePercent
                || networkChanged || readingsChanged || !host.paintSettings.fontPath.equals(font == null ? "" : font.key())) {
            host.sourceSettings = settings;
            host.lastOutsidePercent = outsidePercent;
            host.paintSettings = userLayout ? ColorOsRingPreset.userLayout(settings, outsidePercent, rawPlan)
                    : ColorOsRingPreset.create(settings, outsidePercent, network != null, rawPlan);
            host.paintSettings.fontPath = font == null ? "" : font.key();
        }
        if (font != null) font.prepareRenderer();
        ColorOsBatteryDrawable.Frame frame = new ColorOsBatteryDrawable.Frame(level, tint, host.paintSettings, plan, powerState,
                clock == null ? "" : clock.time(), width, height, outside, rawPlan);
        if (attachment.action() == HostDecision.Action.PREFLIGHT) {
            if (font == null) TrioRenderer.setFont("");
            ColorOsBatteryDrawable.preflight(width, height, frame);
            host.testedWidth = width; host.testedHeight = height;
        }
        if (HostDecision.mount(host.preview != null).action() == HostDecision.Action.MOUNT) {
            final long token = ++host.mountToken;
            ColorOsBatteryDrawable drawable = new ColorOsBatteryDrawable((Drawable) original, frame,
                    () -> {
                        if (host.mountToken != token) return;
                        host.failed = true;
                        try { release(host, "draw-failure"); } catch (Throwable ignored) {}
                    }, presented -> {
                        try {
                        if (host.mountToken != token || host.preview == null || !host.preview.isCurrent(presented)) return;
                        log(host.key + "/drawn", "stage=DRAWN generation=" + host.generation);
                        View readyMeter = host.meter.get();
                        boolean planMatches = network != null && config.ready() && config.get().enabled
                                && !host.awaitLayout && readyMeter != null
                                && readyMeter.isAttachedToWindow() && readyMeter.isShown()
                                && presented.network.same(currentPlan(readyMeter, host, presented.level));
                        HostDecision.Decision presentation = HostDecision.presented(new HostDecision.Presentation(true,
                                config.ready() && config.get().enabled, !host.awaitLayout,
                                readyMeter != null && readyMeter.isAttachedToWindow(),
                                readyMeter != null && readyMeter.isShown(), planMatches));
                        if (presentation.action() == HostDecision.Action.COMMIT_LEASES) {
                            network.commit(readyMeter, presented.network);
                            commitClock(readyMeter, host, presented);
                            commitCharge(readyMeter, host, presented);
                            host.presentationPending = false;
                            host.configurationHandoff = false;
                        } else {
                            host.presentationPending = true;
                            scheduleReconcile(host);
                        }
                        } catch (Throwable failure) {
                            host.failed = true;
                            try { release(host, "present-failure"); } catch (Throwable ignored) {}
                        }
                    });
            host.preview = drawable;
            image.setImageDrawable(drawable);
            log(host.key + "/mount", "stage=MOUNTED generation=" + host.generation + " size=" + width + "x" + height);
        } else {
            host.preview.update(frame);
            // A native row can finish its visibility/layout transition after our draw callback.
            // Reuse the exact already-presented frame, without waiting for a redundant draw.
            if (host.preview.hasPresented(frame)) {
                if (network != null) network.commit(meter, plan);
                commitClock(meter, host, frame);
                commitCharge(meter, host, frame);
                host.presentationPending = false;
                host.configurationHandoff = false;
            }
        }
    }

    /** Finish a handoff against the next UI traversal rather than a later clock/network event. */
    private void scheduleReconcile(Host host) {
        View meter = host.meter.get();
        if (host.refreshQueued || meter == null || !meter.isAttachedToWindow()) return;
        host.refreshQueued = true;
        long sequence = ++host.refreshSequence, token = host.mountToken;
        int generation = host.generation;
        meter.postOnAnimation(() -> {
            if (host.refreshSequence != sequence) return;
            host.refreshQueued = false;
            if (host.mountToken != token || host.generation != generation) return;
            ImageView image = host.image.get();
            if (host.resizing && image != null && !image.isLayoutRequested()) {
                // A parent may clamp the requested size. Reconcile against its final
                // allocation instead of waiting forever for LayoutParams equality.
                host.awaitLayout = false; host.resizing = false;
            }
            try { reconcile(host); }
            catch (Throwable failure) {
                host.failed = true;
                try { release(host, "handoff-failure"); } catch (Throwable ignored) {}
            }
        });
    }

    private ColorOsOutsideLayout outsideLayout(View meter, TrioSettings settings, ColorOsNetworkAdapter.Plan plan,
                                               boolean outsidePercent, int level, int height, int room) {
        if (!outsideStage || height <= 0) return ColorOsOutsideLayout.EMPTY;
        return ColorOsOutsideLayout.create(settings, plan, height, room,
                meter.getResources().getDisplayMetrics().density, meter.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL,
                readouts == null ? ColorOsReadoutSource.State.UNKNOWN : readouts.get(),
                ColorOsRingPreset.resolve(ColorOsRingPreset.userLayout(settings, outsidePercent, plan),
                        level, power.get().charging, plan).wifi);
    }

    private static String visibilityPath(View view) {
        StringBuilder result = new StringBuilder();
        for (int depth = 0; depth < 6 && view != null; depth++) {
            if (depth > 0) result.append('/');
            result.append(view.getClass().getSimpleName()).append(':').append(view.getVisibility());
            android.view.ViewParent parent = view.getParent();
            view = parent instanceof View ? (View) parent : null;
        }
        return result.toString();
    }

    private ColorOsNetworkAdapter.Plan currentPlan(View meter, Host host, int level) {
        TrioSettings settings = layoutSettings(host);
        ColorOsNetworkAdapter.Plan plan = network.prepare(meter, settings);
        if (!userLayout) return plan;
        TrioSettings paint = ColorOsRingPreset.userLayout(settings, host.lastOutsidePercent, plan);
        return ColorOsRingPreset.represented(paint, level, power.get().charging, plan, host.outside);
    }

    private TrioSettings layoutSettings(Host host) {
        TrioSettings source = config.get();
        ColorOsPowerSource.State state = power == null ? ColorOsPowerSource.State.UNKNOWN : power.get();
        if (state.ready) host.plugBridge.power(state.plugged, SystemClock.uptimeMillis());
        int scene = ChargeLayout.scene(state.charging, state.quick);
        if (host.layoutSource != source || host.layoutScene != scene) {
            host.layoutSource = source;
            host.layoutScene = scene;
            host.layoutSettings = ChargeLayout.forScene(source, scene);
        }
        return host.layoutSettings;
    }

    private ColorOsChargeIcon chargeIcon(Host host, ImageView view) {
        if (host.chargeIcon == null || !host.chargeIcon.isFor(view)) {
            if (host.chargeIcon != null) host.chargeIcon.restore();
            host.chargeIcon = new ColorOsChargeIcon(view);
        }
        return host.chargeIcon;
    }

    private void commitCharge(View meter, Host host, ColorOsBatteryDrawable.Frame frame) {
        View view = child(meter, ColorOsSymbols.ID_BATTERY_CHARGE_ICON);
        if (!(view instanceof ImageView)) return;
        ColorOsPowerSource.State current = power == null ? ColorOsPowerSource.State.UNKNOWN : power.get();
        // A delayed draw callback must never hide a newer native charging state.
        chargeIcon(host, (ImageView) view).commit(HostDecision.chargeIcon(new HostDecision.ChargePresentation(
                userLayout, current.same(frame.power), frame.power.ready, frame.power.charging, frame.power.plugged)));
    }

    private void release(Host host, String reason) throws Throwable {
        host.configurationHandoff = false;
        boolean popup = host.plugBridge.active();
        host.plugBridge.clear();
        host.presentationPending = false;
        host.refreshQueued = false;
        host.refreshSequence++;
        host.resizing = false;
        View meter = host.meter.get();
        ImageView image = host.image.get();
        restoreDescription(host);
        if (host.chargeIcon != null) host.chargeIcon.restore();
        host.lastGlobalShown = meter != null && image != null && meter.isShown() && image.isShown();
        if (clock != null && meter != null) clock.release(meter);
        if (network != null && meter != null) network.release(meter);
        host.size.restore();
        ColorOsBatteryDrawable preview = host.preview;
        if (preview == null) return;
        boolean imageOwned = image != null && image.getDrawable() == preview;
        preview.useNativeFallback();
        if (image != null && meter != null && image.getDrawable() == preview) {
            // The OEM field was never replaced and its model updates kept running.
            Object current;
            try { current = nativeDrawable.invoke(meter); }
            catch (ReflectiveOperationException failure) { current = preview.original(); }
            image.setImageDrawable(current instanceof Drawable ? (Drawable) current : preview.original());
        }
        if (popup && imageOwned && host.latestStyle == 2) {
            image.setImageDrawable(null);
            image.setVisibility(View.GONE); // Restore the exact OEM style-2 state on disable/failure.
        }
        host.preview = null;
        host.mountToken++;
        log(host.key + "/native", "stage=NATIVE reason=" + reason + " generation=" + host.generation);
    }

    private boolean updateClockDescription(Host host, ColorOsBatteryDrawable.Frame frame) {
        View meter = host.meter.get();
        if (meter == null) return false;
        CharSequence current = meter.getContentDescription();
        if (!android.text.TextUtils.equals(current, host.nativeDescription)
                && !android.text.TextUtils.equals(current, host.moduleDescription)) return false;
        String extra = frame.drawsClock ? frame.clock : "";
        String outsideDescription = frame.outside.description();
        if (!outsideDescription.isEmpty()) extra += (extra.isEmpty() ? "" : ", ") + outsideDescription;
        if (extra.isEmpty()) { restoreDescription(host); return true; }
        String next = host.nativeDescription == null || host.nativeDescription.isEmpty()
                ? extra : host.nativeDescription + ", " + extra;
        host.moduleDescription = next;
        if (!android.text.TextUtils.equals(current, next)) meter.setContentDescription(next);
        return true;
    }

    private void commitClock(View meter, Host host, ColorOsBatteryDrawable.Frame frame) {
        if (clock == null) return;
        boolean described = updateClockDescription(host, frame);
        clock.commit(meter, host.scene, frame.drawsClock && described);
    }

    private void restoreDescription(Host host) {
        View meter = host.meter.get();
        if (meter != null && host.moduleDescription != null
                && android.text.TextUtils.equals(meter.getContentDescription(), host.moduleDescription))
            meter.setContentDescription(host.nativeDescription);
        host.moduleDescription = null;
    }

    private View child(View meter, String name) {
        int id = meter.getResources().getIdentifier(name, "id", "com.android.systemui");
        return id == 0 ? null : meter.findViewById(id);
    }

    private void log(String key, String message) {
        try {
            String value = ledger.record(key, message, SystemClock.uptimeMillis());
            boolean important = key.equals("install") || key.startsWith("hook/") || key.startsWith("error/")
                    || key.endsWith("/native") || key.contains("error");
            if (value != null && (important || BuildConfig.DEBUG || config.get().debugLog))
                module.log(4, "ColorDuoColorOSBattery", value);
        } catch (Throwable ignored) {}
    }
}
