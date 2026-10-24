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
            result.putString("stream", "Android configuration and Canvas checks passed: " + ConfigCodecProbe.count() + "\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable failure) {
            result.putString("stream", failure.toString() + "\n");
            finish(Activity.RESULT_CANCELED, result);
        }
    }
}
