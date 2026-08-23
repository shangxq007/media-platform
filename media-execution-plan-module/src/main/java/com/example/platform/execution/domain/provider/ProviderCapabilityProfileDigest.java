package com.example.platform.execution.domain.provider;

import java.util.Objects;
import java.util.regex.Pattern;

/** Canonical lower-case SHA-256 identity for immutable provider capability profile content. */
public record ProviderCapabilityProfileDigest(String sha256Hex) {

    private static final Pattern CANONICAL_SHA_256 = Pattern.compile("[0-9a-f]{64}");

    public ProviderCapabilityProfileDigest {
        Objects.requireNonNull(sha256Hex, "sha256Hex");
        if (!CANONICAL_SHA_256.matcher(sha256Hex).matches()) {
            throw new IllegalArgumentException("provider capability profile digest must be lower-case SHA-256 hex");
        }
    }

    public static ProviderCapabilityProfileDigest sha256(String sha256Hex) {
        return new ProviderCapabilityProfileDigest(sha256Hex);
    }
}
