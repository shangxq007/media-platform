package com.example.platform.colorimage;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Objects;

/**
 * ROADMAP_18 (CI9/CIC3): immutable exact profile content identity — SHA-256.
 * Canonical hex form, exact length validation. NOT ArtifactId/path/URI/name.
 */
public record ColorProfileContentDigest(String sha256Hex) {

    public static final int HEX_LENGTH = 64;

    public ColorProfileContentDigest {
        Objects.requireNonNull(sha256Hex, "digest");
        String normalized = sha256Hex.toLowerCase(Locale.ROOT);
        if (normalized.length() != HEX_LENGTH || !normalized.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("profile digest must be a 64-char hex SHA-256");
        }
        sha256Hex = normalized;
    }

    public static ColorProfileContentDigest of(byte[] profileBytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(profileBytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : d) {
                sb.append(String.format("%02x", b));
            }
            return new ColorProfileContentDigest(sb.toString());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static ColorProfileContentDigest of(String hex) {
        return new ColorProfileContentDigest(hex);
    }

    public static ColorProfileContentDigest ofText(String text) {
        return of(text.getBytes(StandardCharsets.UTF_8));
    }
}
