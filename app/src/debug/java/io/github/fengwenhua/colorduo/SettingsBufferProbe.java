/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.concurrent.atomic.AtomicInteger;

/** Uses an isolated file; never edits the user's actual settings or remote preferences. */
final class SettingsBufferProbe {
    static int run(Context context) {
        String name = "colorduo-buffer-probe";
        SharedPreferences disk = context.getSharedPreferences(name, Context.MODE_PRIVATE);
        disk.edit().clear().commit();
        AtomicInteger writes = new AtomicInteger();
        BufferedSettings buffered = new BufferedSettings(disk, patch -> {
            writes.incrementAndGet();
            SharedPreferences.Editor edit = disk.edit();
            patch.forEach((key, value) -> edit.putInt(key, (Integer) value));
            return edit.commit();
        });
        try {
            for (int i = 0; i < 240; i++) buffered.putInt(Prefs.KEY_GLYPH_SCALE, i);
            require(buffered.getInt(Prefs.KEY_GLYPH_SCALE, -1) == 239, "immediate preview");
            require(buffered.flushBlocking(), "final flush");
            require(writes.get() == 1, "one transaction per burst");
            require(disk.getInt(Prefs.KEY_GLYPH_SCALE, -1) == 239, "persisted final value");
            buffered.sync();
            require(buffered.flushBlocking(), "reconnect flush");
            require(writes.get() == 2, "one full replay");
            return 6;
        } finally {
            buffered.closeForProbe();
            context.deleteSharedPreferences(name);
        }
    }
    private static void require(boolean value, String label) {
        if (!value) throw new AssertionError("settings buffer: " + label);
    }
}
