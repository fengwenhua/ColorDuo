/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import android.content.SharedPreferences;

/** Find N5 owner's portable appearance settings, captured for the 1.0.2 first-install profile. */
public final class FirstRunDefaults {
    private FirstRunDefaults() {}

    /** An existing preference file always wins, including explicitly disabled components. */
    public static synchronized boolean install(SharedPreferences preferences) {
        if (!preferences.getAll().isEmpty()) return false;
        SharedPreferences.Editor edit = preferences.edit();
        edit.putInt(Prefs.KEY_TRIO_STYLE, Prefs.STYLE_RING);
        edit.putInt(Prefs.KEY_NAV_STYLE, 2);
        edit.putBoolean(Prefs.KEY_SHOW_WIFI, true);
        edit.putBoolean(Prefs.KEY_SHOW_MOBILE, true);
        edit.putBoolean(Prefs.KEY_SHOW_VALUE, true);
        edit.putBoolean(Prefs.KEY_SHOW_BOLT, true);
        edit.putBoolean(Prefs.KEY_SHOW_SIM_1, true);
        edit.putBoolean(Prefs.KEY_SHOW_SIM_2, false);
        edit.putInt(Prefs.KEY_MOBILE_TYPE_MODE, Prefs.MOBILE_TYPE_IN_RING);
        edit.putBoolean(Prefs.KEY_TYPE_HIDE_ON_WIFI, true);
        edit.putString(Prefs.KEY_RING_LAYOUT, "5,1|3|7|");
        edit.putString(Prefs.KEY_QUICK_CHARGING_LAYOUT, "1;31;1;0;5,1|4,3|7|");
        edit.putString(Prefs.KEY_OUT_ORDER, "1,3,2");
        int capsule = Prefs.STYLE_CAPSULE;
        edit.putBoolean(Prefs.styledKey(Prefs.KEY_SHOW_WIFI, capsule), true);
        edit.putBoolean(Prefs.styledKey(Prefs.KEY_SHOW_MOBILE, capsule), true);
        edit.putBoolean(Prefs.styledKey(Prefs.KEY_SHOW_VALUE, capsule), true);
        edit.putBoolean(Prefs.styledKey(Prefs.KEY_SHOW_BOLT, capsule), true);
        edit.putBoolean(Prefs.styledKey(Prefs.KEY_SHOW_SIM_1, capsule), true);
        edit.putBoolean(Prefs.styledKey(Prefs.KEY_SHOW_SIM_2, capsule), false);
        edit.putInt(Prefs.styledKey(Prefs.KEY_MOBILE_TYPE_MODE, capsule), Prefs.MOBILE_TYPE_IN_RING);
        edit.putString(Prefs.styledKey(Prefs.KEY_RING_LAYOUT, capsule), "5,1|4,3|7|6");
        edit.putString(Prefs.styledKey(Prefs.KEY_CHARGING_LAYOUT, capsule), "1;31;1;0;5,1|3,4|7|6");
        if (!edit.commit()) throw new IllegalStateException("Cannot save first-install defaults");
        return true;
    }
}
