/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.fengwenhua.colorduo;

import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import org.json.JSONObject;

/** Checks the actual Android JCA and JSON implementations; private test key exists only in memory. */
final class UpdateRegressionProbe {
    static int run() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        java.security.KeyPair pair = generator.generateKeyPair();
        String publicKey = "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()) + "\n-----END PUBLIC KEY-----";
        byte[] bytes = new JSONObject().put("schema", 1).put("packageName", "io.github.fengwenhua.colorduo")
                .put("versionName", "1.1.0").put("versionCode", 10100).put("apkName", "ColorDuo-1.1.0.apk")
                .put("apkSize", 1).put("sha256", "a".repeat(64)).put("certificateSha256", "b".repeat(64))
                .toString().getBytes(StandardCharsets.UTF_8);
        Signature signer = Signature.getInstance("SHA256withECDSA");
        signer.initSign(pair.getPrivate()); signer.update(bytes);
        byte[] signature = signer.sign();
        UpdateManifest accepted = UpdateManifest.verify(bytes, signature, publicKey, "b".repeat(64));
        if (accepted.versionCode != 10100) throw new AssertionError("Android signed manifest round-trip");
        bytes[bytes.length / 2] ^= 1;
        boolean rejected = false;
        try { UpdateManifest.verify(bytes, signature, publicKey, "b".repeat(64)); }
        catch (Exception expected) { rejected = true; }
        if (!rejected) throw new AssertionError("Android must reject modified metadata");
        return 2;
    }
}
