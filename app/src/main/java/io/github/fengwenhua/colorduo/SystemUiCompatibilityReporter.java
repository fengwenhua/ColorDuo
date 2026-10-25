/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Lives only in SystemUI. The app-side client has no Xposed API dependencies. */
final class SystemUiCompatibilityReporter {
    private static boolean registered;
    private static RomProfile runtimeRom = RomProfile.UNKNOWN;

    static void install(XposedModule module, ClassLoader loader, RomProfile rom) {
        runtimeRom = rom;
        try {
            if (rom == RomProfile.COLOR_OS) ColorOsProfile.inspect(loader);
            Class<?> service = Class.forName("com.android.systemui.SystemUIService", false, loader);
            module.hook(service.getDeclaredMethod("onCreate")).setId("colorduo-compatibility-service")
                    .setExceptionMode(XposedInterface.ExceptionMode.PASSTHROUGH).intercept(chain -> {
                        Object result = chain.proceed();
                        try { register((Context) chain.getThisObject()); }
                        catch (Throwable ignored) {} // Diagnostics must not interrupt SystemUI service creation.
                        return result;
                    });
        } catch (Throwable failure) {
            module.log(5, "ColorDuo", "compatibility endpoint unavailable: " + failure.getClass().getSimpleName());
        }
    }

    @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag") // Pre-33 overload; signature permission still restricts senders.
    static synchronized void register(Context context) {
        if (registered) return;
        Context app = context.getApplicationContext();
        if (app == null) return;
        try {
            BroadcastReceiver receiver = new BroadcastReceiver() {
                @Override public void onReceive(Context received, Intent intent) {
                    if (!isOrderedBroadcast() || intent == null || !CompatibilityReport.ACTION.equals(intent.getAction())) return;
                    try {
                        if (Build.VERSION.SDK_INT >= 34 && (getSentFromUid() != received.getPackageManager()
                                .getPackageUid(BuildConfig.APPLICATION_ID, 0)
                                || !BuildConfig.APPLICATION_ID.equals(getSentFromPackage()))) return;
                        setResultData(CompatibilityReport.render(received, true, runtimeRom));
                        setResultCode(Activity.RESULT_OK);
                    } catch (RuntimeException | android.content.pm.PackageManager.NameNotFoundException ignored) {}
                }
            };
            IntentFilter filter = new IntentFilter(CompatibilityReport.ACTION);
            if (Build.VERSION.SDK_INT >= 33)
                app.registerReceiver(receiver, filter, Prefs.CONFIG_PERMISSION, null, Context.RECEIVER_EXPORTED);
            else app.registerReceiver(receiver, filter, Prefs.CONFIG_PERMISSION, null);
            registered = true;
        } catch (RuntimeException ignored) {}
    }

    private SystemUiCompatibilityReporter() {}
}
