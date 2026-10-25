/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import android.app.BroadcastOptions;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import io.github.libxposed.service.XposedService;

/** Serial atomic local write plus one ROM-specific remote transaction/notification per batch. */
public final class ConfigPacketPublisher {
    private ConfigPacketPublisher() {}
    public static boolean publish(Context context, XposedService service) { return publish(context, service, Map.of()); }

    public static synchronized boolean publish(Context context, XposedService service, Map<String, Object> changes) {
        try {
            SharedPreferences local = context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE);
            SharedPreferences.Editor edit = local.edit();
            put(edit, changes);
            if (service == null) {
                return changes.isEmpty() || edit.commit(); // Reconnect always requests a full replay.
            }
            Map<String, Object> values = new HashMap<>(local.getAll());
            values.putAll(changes); values.values().removeIf(java.util.Objects::isNull);
            PreferencesSnapshot frozen = new PreferencesSnapshot(values);
            TrioSettings settings = TrioSettings.from(frozen, context.getResources().getDisplayMetrics().density);
            if (!ColorOsFontPublisher.supportedDevice()) {
                SharedPreferences.Editor remote = service.getRemotePreferences(Prefs.NAME).edit();
                put(remote, values);
                changes.forEach((key, value) -> { if (value == null) remote.remove(key); });
                return SettingsPublication.commit(edit::commit, remote::commit,
                        () -> broadcast(context, new Intent(Prefs.ACTION_RELOAD)
                                .setPackage(Prefs.SYSTEMUI_PACKAGE).putExtras(settings.toBundle())));
            }
            String epoch = frozen.getString(Prefs.KEY_SNAPSHOT_EPOCH, null);
            if (epoch == null) epoch = UUID.randomUUID().toString();
            long previous = frozen.getLong(Prefs.KEY_SNAPSHOT_REVISION, 0);
            if (previous < 0 || previous == Long.MAX_VALUE) throw new IllegalStateException("invalid local revision");
            String packet = ConfigPacket.encode(epoch, previous + 1, settings,
                    ColorOsFontPublisher.forSnapshot(context, service, frozen));
            edit.putString(Prefs.KEY_SNAPSHOT_EPOCH, epoch).putLong(Prefs.KEY_SNAPSHOT_REVISION, previous + 1)
                    .putString(Prefs.KEY_SNAPSHOT, packet);
            SharedPreferences.Editor remote = service.getRemotePreferences(Prefs.NAME).edit().putString(Prefs.KEY_SNAPSHOT, packet);
            if (!SettingsPublication.commit(edit::commit, remote::commit,
                    () -> broadcast(context, new Intent(Prefs.ACTION_SNAPSHOT)
                            .setPackage(Prefs.SYSTEMUI_PACKAGE).putExtra(Prefs.KEY_SNAPSHOT, packet)))) return false;
            if (settings.debugLog) Log.i("ColorDuo", "snapshot persisted revision=" + (previous + 1));
            ColorOsFontPublisher.afterPublished(context, service, ConfigPacket.decode(packet).fontFile);
            return true;
        } catch (Exception failure) {
            Log.w("ColorDuo", "settings pending framework sync: " + failure.getClass().getSimpleName());
            return false;
        }
    }

    private static void broadcast(Context context, Intent intent) {
        if (Build.VERSION.SDK_INT >= 34) {
            context.sendBroadcast(intent, null, BroadcastOptions.makeBasic().setShareIdentityEnabled(true).toBundle());
        } else context.sendBroadcast(intent);
    }

    @SuppressWarnings("unchecked")
    private static void put(SharedPreferences.Editor edit, Map<String, ?> values) {
        values.forEach((key, value) -> {
            if (value == null) edit.remove(key);
            else if (value instanceof Boolean) edit.putBoolean(key, (Boolean) value);
            else if (value instanceof Integer) edit.putInt(key, (Integer) value);
            else if (value instanceof Long) edit.putLong(key, (Long) value);
            else if (value instanceof Float) edit.putFloat(key, (Float) value);
            else if (value instanceof String) edit.putString(key, (String) value);
            else if (value instanceof java.util.Set<?>) edit.putStringSet(key, (java.util.Set<String>) value);
            else throw new IllegalArgumentException("unsupported preference type");
        });
    }
}
