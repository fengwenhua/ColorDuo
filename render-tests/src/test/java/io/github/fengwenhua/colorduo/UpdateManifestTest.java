/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public final class UpdateManifestTest {
    private final String cert = "a".repeat(64);
    private final byte[] apk = "signed-test-fixture".getBytes(StandardCharsets.UTF_8);
    private KeyPair keys() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
        g.initialize(new ECGenParameterSpec("secp256r1"));
        return g.generateKeyPair();
    }
    private byte[] manifest() throws Exception {
        return new JSONObject().put("schema", 1).put("packageName", "io.github.fengwenhua.colorduo")
            .put("versionName", "1.1.0").put("versionCode", 10100).put("apkName", "ColorDuo-1.1.0.apk")
            .put("apkSize", apk.length).put("sha256", RemoteFontKey.digest(apk))
            .put("certificateSha256", cert).toString().getBytes(StandardCharsets.UTF_8);
    }
    private byte[] sign(KeyPair keys, byte[] payload) throws Exception {
        Signature signature = Signature.getInstance("SHA256withECDSA");
        signature.initSign(keys.getPrivate()); signature.update(payload); return signature.sign();
    }
    private String pem(KeyPair keys) {
        return "-----BEGIN PUBLIC KEY-----\n" + Base64.getEncoder().encodeToString(keys.getPublic().getEncoded())
            + "\n-----END PUBLIC KEY-----";
    }
    @Test public void acceptsValidSignatureAndRejectsTamperedBytesAndWrongKey() throws Exception {
        KeyPair keys = keys(); byte[] payload = manifest(), signature = sign(keys, payload);
        UpdateManifest valid = UpdateManifest.verify(payload, signature, pem(keys), cert);
        assertEquals(10100, valid.versionCode);
        byte[] changed = payload.clone(); changed[changed.length / 2] ^= 1;
        assertThrows(Exception.class, () -> UpdateManifest.verify(changed, signature, pem(keys), cert));
        assertThrows(Exception.class, () -> UpdateManifest.verify(payload, signature, pem(keys()), cert));
        assertThrows(Exception.class, () -> UpdateManifest.verify(payload, signature, pem(keys), "b".repeat(64)));
    }
    @Test public void rejectsWrongPackagePathOversizeAndMalformedMetadataEvenWhenSigned() throws Exception {
        KeyPair keys = keys();
        for (String field : new String[]{"packageName", "apkName", "apkSize", "versionCode", "schema"}) {
            JSONObject json = new JSONObject(new String(manifest(), StandardCharsets.UTF_8));
            switch (field) {
                case "packageName": json.put(field, "wrong.package"); break;
                case "apkName": json.put(field, "../../fake.apk"); break;
                case "apkSize": json.put(field, UpdateManifest.MAX_APK_BYTES + 1); break;
                case "versionCode": json.put(field, -1); break;
                default: json.put(field, 2);
            }
            byte[] invalid = json.toString().getBytes(StandardCharsets.UTF_8);
            assertThrows(Exception.class, () -> UpdateManifest.verify(invalid, sign(keys, invalid), pem(keys), cert));
        }
    }
    @Test public void fileVerificationRejectsTruncationAndSameLengthCorruption() throws Exception {
        KeyPair keys = keys(); byte[] payload = manifest();
        UpdateManifest valid = UpdateManifest.verify(payload, sign(keys, payload), pem(keys), cert);
        java.nio.file.Path path = Files.createTempFile("colorduo-update-test-", ".bin");
        try {
            Files.write(path, apk); valid.verifyFile(path.toFile());
            byte[] corrupt = apk.clone(); corrupt[0] ^= 1;
            Files.write(path, corrupt);
            assertThrows(SecurityException.class, () -> valid.verifyFile(path.toFile()));
            Files.write(path, new byte[1]);
            assertThrows(SecurityException.class, () -> valid.verifyFile(path.toFile()));
        } finally { Files.deleteIfExists(path); }
    }
}
