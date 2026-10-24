/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import android.os.Bundle;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.Iterator;
import java.util.UUID;

/** Scalar-only bounded JSON; no Parcel/Serializable objects cross this channel. */
final class ConfigPacket {
    static final int MAX_LENGTH = 65_536;
    final String epoch;
    final long revision;
    final TrioSettings settings;
    final String fontFile;

    private ConfigPacket(String epoch, long revision, TrioSettings settings, String fontFile) {
        this.epoch = epoch;
        this.revision = revision;
        this.settings = settings;
        this.fontFile = fontFile;
    }

    static String encode(String epoch, long revision, TrioSettings settings) throws JSONException {
        return encode(epoch, revision, settings, "");
    }

    static String encode(String epoch, long revision, TrioSettings settings, String fontFile) throws JSONException {
        if (!RemoteFontKey.valid(fontFile)) throw new JSONException("invalid font key");
        JSONObject payload = new JSONObject();
        Bundle bundle = settings.toBundle();
        for (String key : bundle.keySet()) payload.put(key, bundle.get(key));
        String result = new JSONObject().put("schema", 1).put("epoch", epoch)
                .put("revision", revision).put("fontFile", fontFile).put("payload", payload).toString();
        if (result.length() > MAX_LENGTH) throw new JSONException("configuration too large");
        return result;
    }

    static ConfigPacket decode(String raw) throws JSONException {
        if (raw == null || raw.length() > MAX_LENGTH) throw new JSONException("invalid packet size");
        JSONObject root = new JSONObject(raw);
        if (!(root.get("schema") instanceof Integer) || root.getInt("schema") != 1) {
            throw new JSONException("unsupported schema");
        }
        String epoch = root.getString("epoch");
        try {
            if (!UUID.fromString(epoch).toString().equals(epoch)) throw new IllegalArgumentException();
        } catch (IllegalArgumentException invalid) { throw new JSONException("invalid epoch"); }
        Object number = root.get("revision");
        if (!(number instanceof Integer) && !(number instanceof Long)) throw new JSONException("invalid revision");
        long revision = ((Number) number).longValue();
        if (revision <= 0) throw new JSONException("invalid revision");
        JSONObject payload = root.getJSONObject("payload");
        if (payload.length() > 256 || !(payload.opt(Prefs.KEY_ENABLED) instanceof Boolean)) {
            throw new JSONException("invalid payload");
        }
        Bundle values = new Bundle();
        Iterator<String> keys = payload.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (key.length() > 128) throw new JSONException("invalid key");
            Object value = payload.get(key);
            if (value instanceof Boolean) values.putBoolean(key, (Boolean) value);
            else if (value instanceof Integer) values.putInt(key, (Integer) value);
            else if (value instanceof String && ((String) value).length() <= 8192) values.putString(key, (String) value);
            else throw new JSONException("invalid setting type");
        }
        Object font = root.has("fontFile") ? root.get("fontFile") : "";
        if (!(font instanceof String) || !RemoteFontKey.valid((String) font)) throw new JSONException("invalid font key");
        try { return new ConfigPacket(epoch, revision, TrioSettings.fromBundle(values), (String) font); }
        catch (RuntimeException invalid) { throw new JSONException("invalid setting value"); }
    }
}
