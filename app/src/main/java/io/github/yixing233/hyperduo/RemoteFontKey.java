/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.yixing233.hyperduo;

import java.security.MessageDigest;
import java.util.UUID;
import java.util.regex.Pattern;

/** Opaque immutable basename. It cannot name a path outside the module's shared directory. */
final class RemoteFontKey {
    static final int MAX_BYTES = 16 * 1024 * 1024;
    private static final Pattern NAME = Pattern.compile("hd_font_[0-9a-f]{64}_[0-9a-f]{16}");
    static boolean valid(String name) { return name != null && (name.isEmpty() || NAME.matcher(name).matches()); }
    static String digest(byte[] bytes) throws Exception { return hex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    static String create(byte[] bytes) throws Exception {
        return "hd_font_" + digest(bytes) + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
    static String expectedDigest(String name) {
        if (!valid(name) || name.isEmpty()) throw new IllegalArgumentException("font key");
        return name.substring(8, 72);
    }
    static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) out.append(Character.forDigit((value >>> 4) & 15, 16)).append(Character.forDigit(value & 15, 16));
        return out.toString();
    }
}
