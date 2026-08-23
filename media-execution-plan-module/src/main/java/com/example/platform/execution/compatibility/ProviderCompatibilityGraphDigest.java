package com.example.platform.execution.compatibility;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.regex.Pattern;

/** SHA-256 digest of the versioned canonical ProviderCompatibilityGraph encoding. */
public record ProviderCompatibilityGraphDigest(String sha256Hex) {

    private static final Pattern CANONICAL_SHA_256 = Pattern.compile("[0-9a-f]{64}");

    public ProviderCompatibilityGraphDigest {
        Objects.requireNonNull(sha256Hex, "sha256Hex");
        if (!CANONICAL_SHA_256.matcher(sha256Hex).matches()) {
            throw new IllegalArgumentException("graph digest must be lower-case SHA-256 hex");
        }
    }

    static ProviderCompatibilityGraphDigest fromCanonicalBytes(byte[] canonicalBytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonicalBytes);
            return new ProviderCompatibilityGraphDigest(HexFormat.of().formatHex(digest));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}
