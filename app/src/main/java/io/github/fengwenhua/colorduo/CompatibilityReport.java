/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import android.app.Activity;
import android.app.BroadcastOptions;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Explicit, signature-permission-protected ordered request; no exported app reply endpoint. */
public final class CompatibilityReport {
    static final String ACTION = "io.github.fengwenhua.colorduo.COMPATIBILITY_REPORT";
    public static void request(Context context, Consumer<String> result) {
        Context app = context.getApplicationContext();
        Handler main = new Handler(Looper.getMainLooper());
        AtomicBoolean complete = new AtomicBoolean();
        Runnable timeout = () -> { if (complete.compareAndSet(false, true)) result.accept(render(app, false, RomProfile.UNKNOWN)); };
        BroadcastReceiver reply = new BroadcastReceiver() {
            @Override public void onReceive(Context received, Intent intent) {
                String report = getResultData();
                if (getResultCode() == Activity.RESULT_OK && report != null && report.length() < 4096
                        && report.startsWith("ColorDuo compatibility report v1\n")) {
                    if (complete.compareAndSet(false, true)) { main.removeCallbacks(timeout); result.accept(report); }
                } else timeout.run();
            }
        };
        main.postDelayed(timeout, 3_000);
        try {
            Intent query = new Intent(ACTION).setPackage(Prefs.SYSTEMUI_PACKAGE);
            if (Build.VERSION.SDK_INT >= 34)
                app.sendOrderedBroadcast(query, null, BroadcastOptions.makeBasic().setShareIdentityEnabled(true).toBundle(),
                        reply, main, Activity.RESULT_CANCELED, null, null);
            else app.sendOrderedBroadcast(query, null, reply, main, Activity.RESULT_CANCELED, null, null);
        } catch (RuntimeException ignored) { main.removeCallbacks(timeout); timeout.run(); }
    }

    static String render(Context context, boolean live, RomProfile rom) {
        String systemui = "unknown";
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(Prefs.SYSTEMUI_PACKAGE, 0);
            systemui = info.versionName + " (" + info.getLongVersionCode() + ")";
        } catch (android.content.pm.PackageManager.NameNotFoundException ignored) {}
        CompatibilitySnapshot.Device device = new CompatibilitySnapshot.Device(Build.MANUFACTURER, Build.MODEL,
                Build.VERSION.RELEASE, Build.VERSION.SDK_INT, Build.VERSION.INCREMENTAL, systemui,
                BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")",
                live ? rom.name() : "unavailable", live);
        return CompatibilitySnapshot.render(device, live ? ColorOsProfile.snapshot() : Map.of());
    }
    private CompatibilityReport() {}
}
