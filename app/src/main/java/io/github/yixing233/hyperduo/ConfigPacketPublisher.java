/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.yixing233.hyperduo;

import android.app.BroadcastOptions;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;
import java.util.UUID;
import io.github.libxposed.service.XposedService;

/** App-side single writer. An applied ColorOS packet has already committed to framework storage. */
public final class ConfigPacketPublisher {
    private ConfigPacketPublisher() {}

    public static synchronized void publish(Context context, XposedService service) {
        try {
            if (service == null) return; // onServiceBind replays the saved local values.
            SharedPreferences local = context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE);
            PreferencesSnapshot frozen = new PreferencesSnapshot(local);
            String epoch = frozen.getString(Prefs.KEY_SNAPSHOT_EPOCH, null);
            if (epoch == null) epoch = UUID.randomUUID().toString();
            long previous = frozen.getLong(Prefs.KEY_SNAPSHOT_REVISION, 0);
            if (previous < 0 || previous == Long.MAX_VALUE) throw new IllegalStateException("invalid local revision");
            long revision = previous + 1;
            TrioSettings settings = TrioSettings.from(frozen, context.getResources().getDisplayMetrics().density);
            String packet = ConfigPacket.encode(epoch, revision, settings,
                    ColorOsFontPublisher.forSnapshot(context, service, frozen));
            if (!local.edit().putString(Prefs.KEY_SNAPSHOT_EPOCH, epoch)
                    .putLong(Prefs.KEY_SNAPSHOT_REVISION, revision).putString(Prefs.KEY_SNAPSHOT, packet).commit()) {
                throw new IllegalStateException("cannot persist snapshot");
            }
            if (!service.getRemotePreferences(Prefs.NAME).edit().putString(Prefs.KEY_SNAPSHOT, packet).commit()) {
                throw new IllegalStateException("framework snapshot not committed");
            }
            if (Build.VERSION.SDK_INT >= 34) {
                Intent intent = new Intent(Prefs.ACTION_SNAPSHOT).setPackage(Prefs.SYSTEMUI_PACKAGE)
                        .putExtra(Prefs.KEY_SNAPSHOT, packet);
                context.sendBroadcast(intent, null, BroadcastOptions.makeBasic()
                        .setShareIdentityEnabled(true).toBundle());
            }
            Log.i("HyperDuo", "snapshot persisted revision=" + revision);
            if (ColorOsFontPublisher.supportedDevice()) ColorOsFontPublisher.afterPublished(context, service, ConfigPacket.decode(packet).fontFile);
        } catch (Exception failure) {
            Log.w("HyperDuo", "snapshot pending framework sync", failure);
        }
    }
}
