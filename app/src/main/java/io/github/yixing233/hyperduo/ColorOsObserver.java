/*
 * Copyright (C) 2026 HyperDuo contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.github.yixing233.hyperduo;

import android.content.res.Configuration;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/**
 * Read-only probes for the audited ColorOS pipeline. Each original runs exactly
 * once; probe failures cannot replace its result or swallow its exception.
 * No settings receiver, polling, flow subscription, view listener, drawable,
 * visibility, layout-parameter or ignored-slot mutations are installed here.
 */
final class ColorOsObserver {
    private static final String BATTERY = "com.oplus.systemui.statusbar.pipeline.battery.ui.";
    private static final String MOBILE = "com.oplus.systemui.statusbar.pipeline.mobile.ui.view.";
    private static final String WIFI = "com.android.systemui.statusbar.pipeline.wifi.";
    private static final String ICON = "com.android.systemui.common.shared.model.Icon";
    private final XposedModule module;
    private final ClassLoader loader;
    private final ObservationLedger ledger = new ObservationLedger(128, 500);
    // All target View maps are weak and accessed only on the main looper.
    private final Map<View, Host> hosts = new WeakHashMap<>();
    private final Map<View, String> networkViews = new WeakHashMap<>();
    private final Map<Class<?>, Map<String, Method>> getters = new ConcurrentHashMap<>();
    private int nextHost;
    private int hookCount;

    private static final class Host {
        final int id;
        String scene = "unbound";
        int generation;
        boolean attached;
        Host(int id) { this.id = id; }
    }

    private interface Probe {
        void sample(XposedInterface.Chain chain, Object result) throws Throwable;
    }

    private interface ViewProbe {
        void sample(View view) throws Throwable;
    }

    private ColorOsObserver(XposedModule module, ClassLoader loader) {
        this.module = module;
        this.loader = loader;
    }

    static void install(XposedModule module, ClassLoader loader) {
        new ColorOsObserver(module, loader).install();
    }

    private void install() {
        hook(BATTERY + "view.StatBatteryMeterView", "bindViewModel", null, (chain, result) -> {
            Object vm = chain.getArg(0);
            String scene = String.valueOf(get(vm, "getModuleTag"));
            withView(chain.getThisObject(), view -> {
                host(view).scene = scene;
                sampleBattery(view, "bind");
            });
        }, BATTERY + "viewmodel.LocationBasedBatteryViewModel");

        for (String event : new String[]{"onAttachedToWindow", "onDetachedFromWindow"}) {
            hook(BATTERY + "view.StatBatteryMeterView", event, null, (chain, result) ->
                    withView(chain.getThisObject(), view -> {
                        Host host = host(view);
                        host.generation++;
                        host.attached = event.equals("onAttachedToWindow");
                        sampleBattery(view, event);
                    }));
        }
        hook(BATTERY + "view.StatBatteryMeterView", "onConfigurationChanged", null,
                (chain, result) -> withView(chain.getThisObject(), view -> sampleBattery(view, "config")),
                Configuration.class.getName());
        hook(BATTERY + "view.StatBatteryMeterView", "onLayout", null,
                (chain, result) -> withView(chain.getThisObject(), view -> sampleBattery(view, "layout")),
                "boolean", "int", "int", "int", "int");
        hook(BATTERY + "view.StatBatteryMeterView", "onDarkChanged", null,
                (chain, result) -> withView(chain.getThisObject(), view -> sampleBattery(view, "tint")),
                ArrayList.class.getName(), "float", "int");
        hook(BATTERY + "view.StatBatteryMeterView", "onHiddenChanged", null,
                (chain, result) -> withView(chain.getThisObject(), view -> sampleBattery(view, "hidden")),
                ArrayList.class.getName(), "boolean");
        hook(BATTERY + "binder.BatteryViewBinder", "bind$updateBatteryContentView", null,
                (chain, result) -> withView(chain.getArg(0), view -> sampleBattery(view, "content")),
                BATTERY + "view.StatBatteryMeterView", BATTERY + "model.StatBatteryIcon");
        hook(BATTERY + "binder.BatteryViewBinder", "bind$updateBatteryIconStyle", null,
                (chain, result) -> withView(chain.getArg(1), view -> sampleBattery(view, "style")),
                ImageView.class.getName(), BATTERY + "view.StatBatteryMeterView",
                BATTERY + "viewmodel.LocationBasedBatteryViewModel", "android.widget.FrameLayout",
                "android.widget.TextView", BATTERY + "model.BatteryIconStyle");
        hook(BATTERY + "view.StatBatteryMeterView", "dump", null, (chain, result) -> {
            PrintWriter out = (PrintWriter) chain.getArg(0);
            out.println("  HyperDuo ColorOS: probes=READ_ONLY adapterStage=" + BuildConfig.COLOROS_STAGE);
            for (String line : ledger.snapshot()) out.println("    " + line);
        }, PrintWriter.class.getName(), String[].class.getName());

        hook(WIFI + "ui.model.WifiIcon$Companion", "fromModel", null, (chain, result) -> {
            Object model = chain.getArg(0);
            emit("wifi/model", "kind=" + type(model) + " level=" + get(model, "getLevel")
                    + " validated=" + field(model, "isValidated") + " result=" + type(result));
        }, WIFI + "shared.model.WifiNetworkModel", "android.content.Context", "boolean");
        hook(WIFI + "ui.binder.WifiViewBinder", "bind", (chain, result) -> {
            String location = type(chain.getArg(1));
            withView(chain.getArg(0), view -> {
                    String row = "wifi/" + location + "/" + identity(view);
                    register(view, "wifi_signal", row);
                    emit(row, "slot=" + get(view, "getSlot") + " " + geometry(view));
            });
        }, null, ViewGroup.class.getName(), WIFI + "ui.viewmodel.LocationBasedWifiViewModel");

        for (String binder : new String[]{"OplusStatusBarMobileViewBinder", "BigTypeStatusBarMobileViewBinder",
                "SprintStatusBarMobileViewBinder", "TMOStatusBarMobileViewBinder"}) {
            hook(MOBILE + binder, "bindCustEx", (chain, result) -> {
                Object vm = chain.getArg(0);
                String subscription = String.valueOf(get(vm, "getSubscriptionId"));
                String location = String.valueOf(get(vm, "getLocation"));
                withView(chain.getArg(1), view -> {
                        String row = "mobile/" + binder + "/sub=" + subscription
                                + "/" + identity(view);
                        for (String id : new String[]{"mobile_signal", "mobile_type", "mobile_type_alone"}) {
                            register(view, id, row + "/" + id);
                        }
                        emit(row, "location=" + location + " " + geometry(view));
                });
            }, null,
                    "com.android.systemui.statusbar.pipeline.mobile.ui.viewmodel.LocationBasedMobileViewModel",
                    ViewGroup.class.getName(), "kotlinx.coroutines.flow.Flow", "kotlinx.coroutines.flow.StateFlow",
                    "com.android.systemui.statusbar.pipeline.mobile.ui.MobileViewLogger");
            hook(MOBILE + binder, "bindCustEx$updateSignalIcon", null, (chain, result) -> {
                Object model = chain.getArg(1);
                String state = "kind=" + type(model) + " level=" + get(model, "getLevel")
                        + " levels=" + get(model, "getNumberOfLevels") + " phoneId=" + get(model, "getPhoneId")
                        + " inService=" + field(model, "isInService");
                withView(chain.getArg(0), view -> {
                    String row = networkViews.get(view);
                    emit(row == null ? "mobile/unbound/" + identity(view) : row, state);
                });
            }, ImageView.class.getName(),
                    "com.android.systemui.statusbar.pipeline.mobile.domain.model.SignalIconModel");
        }
        hook("com.android.systemui.common.ui.binder.IconViewBinder", "bind", null, (chain, result) -> {
            Object icon = chain.getArg(0);
            withView(chain.getArg(1), view -> {
                String row = networkViews.get(view);
                if (row == null) return;
                Object res = get(icon, "getRes");
                if (res == null) res = field(icon, "res");
                String name = res instanceof Number ? resourceName(view, ((Number) res).intValue()) : "unknown";
                emit(row + "/icon", "kind=" + type(icon) + " resource=" + name);
            });
        }, ICON, ImageView.class.getName());
        emit("install", "probes=READ_ONLY hooks=" + hookCount
                + " adapterStage=" + BuildConfig.COLOROS_STAGE + " probeMutations=false polling=false");
    }

    private void hook(String className, String methodName, Probe before, Probe after, String... parameters) {
        String key = "hook/" + className.substring(className.lastIndexOf('.') + 1) + "/" + methodName;
        try {
            Class<?> target = Class.forName(className, false, loader);
            Class<?>[] signature = new Class<?>[parameters.length];
            for (int i = 0; i < parameters.length; i++) {
                switch (parameters[i]) {
                    case "boolean": signature[i] = boolean.class; break;
                    case "int": signature[i] = int.class; break;
                    case "float": signature[i] = float.class; break;
                    default: signature[i] = Class.forName(parameters[i], false, loader);
                }
            }
            Method method = target.getDeclaredMethod(methodName, signature);
            method.setAccessible(true);
            module.hook(method).setId("hyperduo-coloros-observe-" + key)
                    .setExceptionMode(XposedInterface.ExceptionMode.PASSTHROUGH)
                    .intercept(chain -> {
                        probe(key + "/before", before, chain, null);
                        Object result = chain.proceed();
                        probe(key + "/after", after, chain, result);
                        return result;
                    });
            hookCount++;
            emit(key, "installed=true");
        } catch (Throwable failure) {
            emit(key, "installed=false reason=" + failure.getClass().getSimpleName());
        }
    }

    private void probe(String stage, Probe probe, XposedInterface.Chain chain, Object result) {
        if (probe == null) return;
        try { probe.sample(chain, result); }
        catch (Throwable failure) { error(stage, failure); }
    }

    private void withView(Object object, ViewProbe action) {
        if (!(object instanceof View)) return;
        if (Looper.myLooper() == Looper.getMainLooper()) {
            try { action.sample((View) object); }
            catch (Throwable failure) { error("view", failure); }
        } else {
            // UI binders normally run on main. Skip an anomalous sample instead
            // of retaining a callback/target or reading a live tree off-thread.
            emit("error/off-main-view", "sampleSkipped=true");
        }
    }

    private Host host(View view) {
        Host host = hosts.get(view);
        if (host == null) {
            host = new Host(++nextHost);
            host.attached = view.isAttachedToWindow();
            hosts.put(view, host);
        }
        return host;
    }

    private void sampleBattery(View view, String event) {
        Host host = host(view);
        Object icon = get(view, "getBatteryIcon");
        Object style = get(view, "getBatteryIconStyle");
        View image = child(view, "battery_icon_view");
        emit("battery/" + host.scene + "/host=" + host.id + "/" + event,
                "generation=" + host.generation + " " + geometry(view)
                        + " level=" + get(icon, "getPowerLevel") + " tint=" + get(icon, "getTintColor")
                        + " style=" + get(style, "getIconStyle") + " scale=" + get(style, "getScaleFactor")
                        + " hidden=" + field(view, "hidden") + " icon=" + (image == null ? "missing" : geometry(image))
                        + " parent=" + (view.getParent() == null ? "none" : view.getParent().getClass().getSimpleName())
                        + " liveHosts=" + hosts.values().stream().filter(item -> item.attached).count());
    }

    private void register(View root, String id, String row) {
        View view = child(root, id);
        if (view != null) networkViews.put(view, row);
        else emit(row + "/capability", "missing=" + id);
    }

    private View child(View root, String name) {
        int id = root.getResources().getIdentifier(name, "id", "com.android.systemui");
        return id == 0 ? null : root.findViewById(id);
    }

    private String resourceName(View view, int id) {
        try { return view.getResources().getResourceEntryName(id); }
        catch (RuntimeException ignored) { return "unknown:" + Integer.toHexString(id); }
    }

    private String geometry(View view) {
        return "display=" + (view.getDisplay() == null ? -1 : view.getDisplay().getDisplayId())
                + " attached=" + view.isAttachedToWindow() + " visible=" + view.getVisibility()
                + " shown=" + view.isShown() + " size=" + view.getWidth() + "x" + view.getHeight()
                + " measured=" + view.getMeasuredWidth() + "x" + view.getMeasuredHeight()
                + " density=" + view.getResources().getDisplayMetrics().densityDpi
                + " rtl=" + view.getLayoutDirection();
    }

    private Object get(Object object, String name) {
        if (object == null) return null;
        try {
            Map<String, Method> methods = getters.computeIfAbsent(object.getClass(), key -> new ConcurrentHashMap<>());
            Method method = methods.get(name);
            if (method == null) {
                method = object.getClass().getMethod(name);
                method.setAccessible(true);
                methods.put(name, method);
            }
            return method.invoke(object);
        } catch (ReflectiveOperationException | RuntimeException ignored) { return null; }
    }

    private Object field(Object object, String name) {
        if (object == null) return null;
        Field field = Refl.field(object.getClass(), name);
        return Refl.get(field, object);
    }

    private static String identity(Object object) {
        return Integer.toHexString(System.identityHashCode(object));
    }

    private static String type(Object object) {
        return object == null ? "null" : object.getClass().getSimpleName();
    }

    private void error(String stage, Throwable failure) {
        emit("error/" + stage, failure.getClass().getSimpleName());
    }

    private void emit(String key, String value) {
        try {
            String line = ledger.record(key, value, SystemClock.uptimeMillis());
            if (line != null) module.log(4, "HyperDuoColorOS", line);
        } catch (Throwable ignored) { /* Logging must not affect SystemUI. */ }
    }
}
