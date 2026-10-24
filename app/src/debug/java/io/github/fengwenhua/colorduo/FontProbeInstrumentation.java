/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;
import android.content.Context;
import android.content.SharedPreferences;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import io.github.libxposed.service.XposedService;
import io.github.fengwenhua.colorduo.ui.SettingsRepository;

/** Debug-only reversible font fixture, using the production exporter and repository. */
public final class FontProbeInstrumentation extends Instrumentation {
    private String action;
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); action = arguments.getString("action", "apply"); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            Context context = getTargetContext();
            XposedService service = null;
            for (int attempt = 0; attempt < 50; attempt++) {
                service = ColorDuoApp.Companion.getXposedService();
                if (service != null) break;
                Thread.sleep(100);
            }
            if (service == null) throw new IllegalStateException("framework service not bound");
            SharedPreferences backup = context.getSharedPreferences("coloros_font_probe", Context.MODE_PRIVATE);
            SharedPreferences prefs = context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE);
            File root = context.getExternalMediaDirs()[0];
            File dir = new File(root, "fonts");
            if (!dir.isDirectory() && !dir.mkdirs()) throw new IllegalStateException("font directory");
            File target = new File(dir, "ColorOS-Probe-Mono.ttf");
            String path, remote;
            if ("restore".equals(action)) {
                if (!backup.contains("original")) throw new IllegalStateException("no saved font configuration");
                path = backup.getString("original", "");
                remote = path.isEmpty() ? "" : ColorOsFontPublisher.exportNow(context, service, path);
            } else {
                if (!backup.contains("original") && !backup.edit().putString("original", prefs.getString(Prefs.KEY_FONT_PATH, "")).commit())
                    throw new IllegalStateException("font backup");
                Files.copy(new File("/system/fonts/DroidSansMono.ttf").toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                path = target.getAbsolutePath();
                remote = ColorOsFontPublisher.exportNow(context, service, path);
            }
            String nextPath = path, nextRemote = remote;
            runOnMainSync(() -> new SettingsRepository(context).setFontPath(nextPath, nextRemote, java.util.UUID.randomUUID().toString()));
            if (!ConfigPacket.decode(service.getRemotePreferences(Prefs.NAME).getString(Prefs.KEY_SNAPSHOT, "")).fontFile.equals(remote))
                throw new IllegalStateException("shared font was not published");
            if ("restore".equals(action)) { target.delete(); backup.edit().clear().commit(); }
            result.putString("stream", "Font fixture " + action + ": published and verified\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable failure) {
            result.putString("stream", failure.toString() + "\n");
            finish(Activity.RESULT_CANCELED, result);
        }
    }
}
