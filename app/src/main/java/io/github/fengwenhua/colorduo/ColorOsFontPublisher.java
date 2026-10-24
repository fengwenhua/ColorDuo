/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.system.Os;
import android.system.OsConstants;
import android.util.Log;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Locale;
import io.github.libxposed.service.XposedService;

/** App-UID font export. Files are immutable and configuration is published only after verification. */
public final class ColorOsFontPublisher {
    public static final String REVISION = "font_source_revision_v1";
    private static final String SOURCE = "font_remote_source_v1";
    private static final String SOURCE_REVISION = "font_remote_revision_v1";
    private static final String NAME = "font_remote_name_v1";
    private static final LatestWorker WORKER = new LatestWorker("ColorDuo-font-export");
    private static final LatestWorker CLEANER = new LatestWorker("ColorDuo-font-cleanup");
    private static String failedBinding = "";
    private static String runningBinding = "";

    private ColorOsFontPublisher() {}

    public static boolean supportedDevice() {
        String maker = Build.MANUFACTURER.toLowerCase(Locale.ROOT);
        return Build.VERSION.SDK_INT >= 34 && (maker.equals("oppo") || maker.equals("oneplus") || maker.equals("realme"));
    }

    static String forSnapshot(Context context, XposedService service, PreferencesSnapshot frozen) {
        String path = frozen.getString(Prefs.KEY_FONT_PATH, "");
        if (!supportedDevice() || path.isEmpty()) return "";
        String revision = frozen.getString(REVISION, "legacy");
        String cached = frozen.getString(NAME, "");
        if (path.equals(frozen.getString(SOURCE, "")) && revision.equals(frozen.getString(SOURCE_REVISION, ""))
                && !cached.isEmpty() && RemoteFontKey.valid(cached)) return cached;
        request(context, service, path, revision, false);
        return "";
    }

    public static void onServiceBound(Context context, XposedService service) {
        if (!supportedDevice()) return;
        SharedPreferences prefs = context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE);
        String path = prefs.getString(Prefs.KEY_FONT_PATH, "");
        if (path != null && !path.isEmpty()) request(context, service, path, prefs.getString(REVISION, "legacy"), true);
    }

    static void afterPublished(Context context, XposedService service, String name) {
        Context app = context.getApplicationContext();
        CLEANER.submit(() -> {
            try {
                synchronized (ColorOsFontPublisher.class) {
                    SharedPreferences prefs = app.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE);
                    org.json.JSONArray old = new org.json.JSONArray(prefs.getString("font_remote_history_v1", "[]"));
                    java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
                    String current = ConfigPacket.decode(service.getRemotePreferences(Prefs.NAME)
                            .getString(Prefs.KEY_SNAPSHOT, "")).fontFile;
                    if (!current.isEmpty()) names.add(current);
                    if (!name.isEmpty()) names.add(name);
                    for (int i = 0; i < old.length(); i++) {
                        String value = old.getString(i);
                        if (!value.isEmpty() && RemoteFontKey.valid(value)) names.add(value);
                    }
                    // Keep the current font and two previous versions for in-flight readers.
                    org.json.JSONArray kept = new org.json.JSONArray();
                    java.util.ArrayList<String> retired = new java.util.ArrayList<>();
                    int index = 0;
                    for (String value : names) { if (index++ < 3) kept.put(value); else retired.add(value); }
                    if (!prefs.edit().putString("font_remote_history_v1", kept.toString()).commit()) return;
                    for (String value : retired) service.deleteRemoteFile(value);
                }
            } catch (Exception ignored) {}
        });
    }

    /** Called by the existing IO-dispatched importer before it reports success. */
    public static String exportNow(Context context, XposedService service, String path) throws Exception {
        File source = ownedFont(context, path);
        long size = source.length(), stamp = source.lastModified();
        if (size <= 0 || size > RemoteFontKey.MAX_BYTES) throw new IOException("font size");
        byte[] bytes;
        try (FileInputStream input = new FileInputStream(source); ByteArrayOutputStream output = new ByteArrayOutputStream((int) size)) {
            byte[] buffer = new byte[16_384];
            int count, total = 0;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > RemoteFontKey.MAX_BYTES) throw new IOException("font too large");
                output.write(buffer, 0, count);
            }
            bytes = output.toByteArray();
        }
        if (bytes.length != size || stamp != source.lastModified()) throw new IOException("font changed during export");
        String name = RemoteFontKey.create(bytes);
        try (ParcelFileDescriptor.AutoCloseOutputStream output = new ParcelFileDescriptor.AutoCloseOutputStream(service.openRemoteFile(name))) {
            output.write(bytes); output.flush();
            Os.ftruncate(output.getFD(), bytes.length);
            Os.fsync(output.getFD());
            Os.lseek(output.getFD(), 0, OsConstants.SEEK_SET);
            if (new Typeface.Builder(output.getFD()).build() == null) throw new IOException("invalid shared font");
        } catch (Exception failure) {
            try { service.deleteRemoteFile(name); } catch (Exception ignored) {}
            throw failure;
        }
        return name;
    }

    /** Include these values in the same commit as the user's font path. */
    public static void bind(SharedPreferences.Editor editor, String path, String revision, String name) {
        editor.putString(REVISION, revision);
        editor.putString(SOURCE, path).putString(SOURCE_REVISION, revision)
                .putString(NAME, RemoteFontKey.valid(name) ? name : "");
    }

    private static synchronized void request(Context context, XposedService service, String path, String revision, boolean retry) {
        String binding = System.identityHashCode(service) + ":" + revision + ":" + path;
        if (binding.equals(runningBinding) || (!retry && binding.equals(failedBinding))) return;
        runningBinding = binding;
        Context app = context.getApplicationContext();
        WORKER.submit(() -> {
            String exported = "";
            try {
                SharedPreferences prefs = app.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE);
                if (!current(prefs, path, revision)) return;
                String cached = prefs.getString(NAME, "");
                boolean cachedBinding = path.equals(prefs.getString(SOURCE, ""))
                        && revision.equals(prefs.getString(SOURCE_REVISION, ""));
                if (cachedBinding && cached != null && !cached.isEmpty() && RemoteFontKey.valid(cached)
                        && verify(service, cached)) return;
                exported = exportNow(app, service, path);
                synchronized (ColorOsFontPublisher.class) {
                    if (!current(prefs, path, revision)) {
                        service.deleteRemoteFile(exported); return;
                    }
                    SharedPreferences.Editor editor = prefs.edit();
                    bind(editor, path, revision, exported);
                    if (!editor.commit()) throw new IOException("font binding not persisted");
                }
                ConfigPacketPublisher.publish(app, service);
            } catch (Exception failure) {
                synchronized (ColorOsFontPublisher.class) { failedBinding = binding; }
                Log.w("ColorDuo", "Font export unavailable: " + failure.getClass().getSimpleName());
            } finally {
                synchronized (ColorOsFontPublisher.class) { if (runningBinding.equals(binding)) runningBinding = ""; }
            }
        });
    }

    private static boolean current(SharedPreferences prefs, String path, String revision) {
        return path.equals(prefs.getString(Prefs.KEY_FONT_PATH, "")) && revision.equals(prefs.getString(REVISION, "legacy"));
    }

    private static boolean verify(XposedService service, String name) {
        if (!java.util.Arrays.asList(service.listRemoteFiles()).contains(name)) return false;
        try (ParcelFileDescriptor.AutoCloseInputStream input = new ParcelFileDescriptor.AutoCloseInputStream(service.openRemoteFile(name))) {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[16_384]; int count, total = 0;
            while ((count = input.read(buffer)) != -1) {
                total += count; if (total > RemoteFontKey.MAX_BYTES) return false; digest.update(buffer, 0, count);
            }
            return total > 0 && RemoteFontKey.hex(digest.digest()).equals(RemoteFontKey.expectedDigest(name));
        } catch (Exception failure) { return false; }
    }

    private static File ownedFont(Context context, String path) throws IOException {
        File source = new File(path).getCanonicalFile();
        for (File root : context.getExternalMediaDirs()) {
            if (root == null) continue;
            if (new File(root, "fonts").getCanonicalFile().equals(source.getParentFile()) && source.isFile()) return source;
        }
        throw new IOException("font is outside the module's import directory");
    }
}
