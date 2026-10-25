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

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

/**
 * Selects the ROM-specific entry point and the configured ColorOS adapter stage;
 * the established MIUI implementation is never installed in a ColorOS process.
 *
 * <p>Entry point declared in {@code META-INF/xposed/java_init.list}; scope in
 * {@code META-INF/xposed/scope.list}.
 */
public final class ColorDuoModule extends XposedModule {

    private static final String TAG = "ColorDuo";
    private static final int LOG_INFO = 4;
    private static final int LOG_WARN = 5;

    private static final String TARGET_PACKAGE = "com.android.systemui";
    private String processName;
    private boolean installed;

    @Override
    public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        processName = param.getProcessName();
        log(LOG_INFO, "loaded process=" + processName + " api=" + getApiVersion()
                + " framework=" + getFrameworkName() + " " + getFrameworkVersion());
    }

    @Override
    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        final String pkg = param.getPackageName();
        if (!TARGET_PACKAGE.equals(pkg) || !TARGET_PACKAGE.equals(processName) || installed) {
            return;
        }
        installed = true;
        try {
            final ClassLoader cl = param.getClassLoader();
            final RomProfile profile = RomProfile.select(
                    Refl.cls("com.android.systemui.statusbar.views.MiuiBatteryMeterIconView", cl) != null,
                    Refl.cls("com.oplus.systemui.statusbar.pipeline.battery.ui.view.StatBatteryMeterView", cl) != null);
            log(LOG_INFO, "adapter=" + profile);
            SystemUiCompatibilityReporter.install(this, cl, profile);
            switch (profile) {
                case HYPER_OS:
                    TrioHooks.install(this, cl);
                    break;
                case COLOR_OS:
                    // R8 removes the observer and its sampling hooks from normal releases.
                    // An explicit observe release remains useful for OEM compatibility work.
                    if (BuildConfig.DEBUG || "observe".equals(BuildConfig.COLOROS_STAGE)) {
                        ColorOsObserver.install(this, cl);
                    }
                    if ("battery".equals(BuildConfig.COLOROS_STAGE) || "network".equals(BuildConfig.COLOROS_STAGE)
                            || "ring".equals(BuildConfig.COLOROS_STAGE) || "type".equals(BuildConfig.COLOROS_STAGE)
                            || "capsule".equals(BuildConfig.COLOROS_STAGE) || "font".equals(BuildConfig.COLOROS_STAGE)
                            || "outside".equals(BuildConfig.COLOROS_STAGE)) {
                        ColorOsBatteryAdapter.install(this, cl);
                    }
                    break;
                default:
                    log(LOG_WARN, "unsupported or ambiguous ROM; native icons retained");
            }
        } catch (Throwable t) {
            log(LOG_WARN, "install failed: " + t);
        }
    }

    private void log(int priority, String msg) {
        try {
            log(priority, TAG, msg);
        } catch (Throwable ignored) {
            // never let logging break the module
        }
    }
}
