package com.example.platform.fonttext.resource;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** ROADMAP_19 (C3): exact immutable SHA-256 font content identity. */
@com.fasterxml.jackson.annotation.JsonAutoDetect(fieldVisibility = com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY)


public final class FontContentDigest {

    private final String sha256Hex;

    @com.fasterxml.jackson.annotation.JsonCreator(mode = com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING)
    private FontContentDigest(String sha256Hex) {
        this.sha256Hex = sha256Hex;
    }

    public static FontContentDigest of(String sha256Hex) {
        Objects.requireNonNull(sha256Hex, "sha256Hex");
        String normalized = sha256Hex.toLowerCase();
        if (normalized.length() != 64 || !normalized.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("FontContentDigest must be exactly 64 lowercase hex chars");
        }
        return new FontContentDigest(normalized);
    }

    public static FontContentDigest ofBytes(byte[] content) {
        Objects.requireNonNull(content, "content");
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return of(HexFormat.of().formatHex(md.digest(content)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public static FontContentDigest ofText(String text) {
        return ofBytes(text.getBytes(StandardCharsets.UTF_8));
    }

    public String sha256Hex() {
        return sha256Hex;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof FontContentDigest d && sha256Hex.equals(d.sha256Hex);
    }

    @Override
    public int hashCode() {
        return sha256Hex.hashCode();
    }

    @Override
    public String toString() {
        return sha256Hex;
    }
}

