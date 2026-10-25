/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import org.json.JSONObject;

/** Authenticity is established over original bytes before parsing any download instructions. */
public final class UpdateManifest {
    public static final int MAX_BYTES = 65_536;
    public static final long MAX_APK_BYTES = 64L * 1024 * 1024;
    public final String version, apkName, sha256, certificateSha256;
    public final long versionCode, apkSize;

    private UpdateManifest(JSONObject json, String pinnedCertificate) throws org.json.JSONException {
        if (json.getInt("schema") != 1 || !"io.github.fengwenhua.colorduo".equals(json.getString("packageName")))
            throw new SecurityException("Invalid update identity");
        version = json.getString("versionName");
        if (!version.matches("[0-9]+\\.[0-9]+\\.[0-9]+(?:-[A-Za-z0-9.]+)?"))
            throw new SecurityException("Invalid version name");
        Object code = json.get("versionCode"), size = json.get("apkSize");
        if (!(code instanceof Integer || code instanceof Long) || !(size instanceof Integer || size instanceof Long))
            throw new SecurityException("Invalid numeric metadata");
        versionCode = ((Number) code).longValue();
        apkSize = ((Number) size).longValue();
        apkName = json.getString("apkName");
        sha256 = json.getString("sha256");
        certificateSha256 = json.getString("certificateSha256");
        if (versionCode < 1 || versionCode > 2_100_000_000 || apkSize < 1 || apkSize > MAX_APK_BYTES
                || !apkName.equals("ColorDuo-" + version + ".apk") || !sha256.matches("[a-f0-9]{64}")
                || !certificateSha256.matches("[a-f0-9]{64}") || !certificateSha256.equals(pinnedCertificate))
            throw new SecurityException("Untrusted update metadata");
    }

    public static UpdateManifest verify(byte[] payload, byte[] signature, String publicPem, String pinnedCertificate) throws Exception {
        if (payload.length == 0 || payload.length > MAX_BYTES || signature.length < 8 || signature.length > 80)
            throw new SecurityException("Invalid signed metadata size");
        String key = publicPem.replace("-----BEGIN PUBLIC KEY-----", "").replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        Signature verifier = Signature.getInstance("SHA256withECDSA");
        verifier.initVerify(KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(key))));
        verifier.update(payload);
        if (!verifier.verify(signature)) throw new SecurityException("Update signature mismatch");
        return new UpdateManifest(new JSONObject(new String(payload, StandardCharsets.UTF_8)), pinnedCertificate);
    }

    public void verifyFile(File file) throws Exception {
        if (!file.isFile() || file.length() != apkSize) throw new SecurityException("Update size mismatch");
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[65_536];
            int read;
            long total = 0;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > apkSize) throw new SecurityException("Update exceeds signed size");
                digest.update(buffer, 0, read);
            }
            if (total != apkSize) throw new SecurityException("Truncated update");
        }
        if (!RemoteFontKey.hex(digest.digest()).equals(sha256)) throw new SecurityException("Update digest mismatch");
    }
}
