/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;

/** Debug-only standard Android runner; executes under the module app's own UID. */
public final class CodecInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            java.util.concurrent.atomic.AtomicReference<Throwable> failed = new java.util.concurrent.atomic.AtomicReference<>();
            runOnMainSync(() -> {
                try { ConfigCodecProbe.run(getTargetContext()); }
                catch (Throwable failure) { failed.set(failure); }
            });
            if (failed.get() != null) throw failed.get();
            // The sustained test needs a visible activity on OEM systems with background freezing.
            // It never edits the real settings; the test buffer uses an isolated preference file.
            Activity screen = startActivitySync(new android.content.Intent()
                    .setClassName(getTargetContext(), "io.github.fengwenhua.colorduo.ui.MainActivity")
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
            int checks;
            try {
                runOnMainSync(() -> screen.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));
                checks = ConfigCodecProbe.count() + SettingsStreamProbe.run(this, getTargetContext());
            } finally {
                runOnMainSync(() -> screen.getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));
            }
            result.putString("stream", "Android configuration and Canvas checks passed: " + checks + "\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable failure) {
            result.putString("stream", failure.toString() + "\n");
            finish(Activity.RESULT_CANCELED, result);
        }
    }
}
