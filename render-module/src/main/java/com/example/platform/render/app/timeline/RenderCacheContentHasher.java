package com.example.platform.render.app.timeline;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Content hash (SHA-256) for render cache artifact verification.
 */
public final class RenderCacheContentHasher {

    private static final String PREFIX = "sha256:";

    private RenderCacheContentHasher() {}

    public static String hashFile(Path path) throws IOException {
        MessageDigest digest = newDigest();
        try (InputStream in = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
        }
        return PREFIX + HexFormat.of().formatHex(digest.digest());
    }

    public static String hashBytes(byte[] bytes) {
        MessageDigest digest = newDigest();
        digest.update(bytes);
        return PREFIX + HexFormat.of().formatHex(digest.digest());
    }

    public static boolean matches(String expected, String actual) {
        if (expected == null || expected.isBlank()) {
            return true;
        }
        return expected.equals(actual);
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
