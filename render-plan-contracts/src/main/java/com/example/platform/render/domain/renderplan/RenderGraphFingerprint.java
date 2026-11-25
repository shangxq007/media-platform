package com.example.platform.render.domain.renderplan;

import java.util.Objects;

/**
 * Deterministic SHA-256 digest over the canonical encoding of graph topology (C8).
 */
public record RenderGraphFingerprint(String sha256Hex) {

    public RenderGraphFingerprint {
        Objects.requireNonNull(sha256Hex, "sha256Hex");
        if (sha256Hex.isBlank()) {
            throw new IllegalArgumentException("RenderGraphFingerprint must not be blank");
        }
    }

    @Override
    public String toString() {
        return sha256Hex;
    }
}
