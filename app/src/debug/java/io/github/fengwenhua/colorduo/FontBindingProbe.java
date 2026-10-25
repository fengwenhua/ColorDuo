/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import android.content.Context;
import android.content.SharedPreferences;

/** Pure snapshot lookup must not open a file or start remote work, even for a new font. */
final class FontBindingProbe {
    static int run(Context context) throws Exception {
        if (!ColorOsFontPublisher.supportedDevice()) return 0;
        String name = "colorduo_font_binding_probe";
        SharedPreferences prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE);
        String path = "/colorduo-probe-does-not-exist.ttf";
        try {
            SharedPreferences.Editor edit = prefs.edit().clear().putString(Prefs.KEY_FONT_PATH, path);
            ColorOsFontPublisher.bind(edit, path, "first", ""); edit.commit();
            require(ColorOsFontPublisher.forSnapshot(new PreferencesSnapshot(prefs)).isEmpty(), "pending lookup");
            String key = RemoteFontKey.create(new byte[]{1, 2, 3});
            edit = prefs.edit(); ColorOsFontPublisher.bind(edit, path, "first", key); edit.commit();
            require(key.equals(ColorOsFontPublisher.forSnapshot(new PreferencesSnapshot(prefs))), "cached immutable binding");
            edit = prefs.edit(); ColorOsFontPublisher.bind(edit, path, "next", ""); edit.commit();
            require(ColorOsFontPublisher.forSnapshot(new PreferencesSnapshot(prefs)).isEmpty(), "new revision cannot reuse old key");
            prefs.edit().putString(Prefs.KEY_FONT_PATH, "").commit();
            require(ColorOsFontPublisher.forSnapshot(new PreferencesSnapshot(prefs)).isEmpty(), "system font");
            return 4;
        } finally { context.deleteSharedPreferences(name); }
    }
    private static void require(boolean passed, String reason) {
        if (!passed) throw new AssertionError("font binding: " + reason);
    }
}
