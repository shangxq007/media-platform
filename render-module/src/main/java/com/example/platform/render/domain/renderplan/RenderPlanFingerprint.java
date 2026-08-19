package com.example.platform.render.domain.renderplan;

import java.util.Objects;

/**
 * Deterministic SHA-256 digest over the canonical encoding of plan semantics (C7).
 * Excludes provider names, timestamps, resolution state, capability context,
 * execution requirements, request id, plan id, status and diagnostics.
 */
public record RenderPlanFingerprint(String sha256Hex) {

    public RenderPlanFingerprint {
        Objects.requireNonNull(sha256Hex, "sha256Hex");
        if (sha256Hex.isBlank()) {
            throw new IllegalArgumentException("RenderPlanFingerprint must not be blank");
        }
    }

    @Override
    public String toString() {
        return sha256Hex;
    }
}
