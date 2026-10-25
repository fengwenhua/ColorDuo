/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import android.app.Instrumentation;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Sustained UI input with slower persistence, in an isolated file and without framework writes. */
final class SettingsStreamProbe {
    static int run(Instrumentation instrumentation, Context context) throws Exception {
        String name = "colorduo_settings_stream_probe";
        SharedPreferences disk = context.getSharedPreferences(name, Context.MODE_PRIVATE);
        disk.edit().clear().commit();
        AtomicInteger writes = new AtomicInteger();
        BufferedSettings buffer = new BufferedSettings(disk, patch -> {
            writes.incrementAndGet();
            try { Thread.sleep(20); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return false; }
            SharedPreferences.Editor edit = disk.edit();
            patch.forEach((key, value) -> edit.putInt(key, (Integer) value));
            return edit.commit();
        });
        Handler main = new Handler(Looper.getMainLooper());
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        try {
            instrumentation.runOnMainSync(() -> {
                for (int i = 0; i < 240; i++) {
                    int value = i;
                    main.postDelayed(() -> buffer.putInt(Prefs.KEY_GLYPH_SCALE, value), i * 8L);
                }
                main.postDelayed(() -> {
                    try {
                        require(buffer.flushBlocking(), "final flush");
                        require(disk.getInt(Prefs.KEY_GLYPH_SCALE, -1) == 239, "final value");
                        require(writes.get() > 0 && writes.get() <= 30, "80ms bound, observed " + writes.get());
                    } catch (Throwable thrown) { failure.set(thrown); }
                    finally { done.countDown(); }
                }, 2_200);
            });
            require(done.await(8, TimeUnit.SECONDS), "stream completion");
            if (failure.get() != null) throw new AssertionError("settings stream", failure.get());
            android.util.Log.i("ColorDuoProbe", "stream240 batchWrites=" + writes.get());
            return 4;
        } finally {
            instrumentation.runOnMainSync(() -> {
                main.removeCallbacksAndMessages(null);
                buffer.flushBlocking(); buffer.closeForProbe();
                context.deleteSharedPreferences(name);
            });
        }
    }
    private static void require(boolean passed, String message) {
        if (!passed) throw new AssertionError("settings stream: " + message);
    }
}
