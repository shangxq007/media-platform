package com.example.platform.render.domain.renderplan;

import java.util.Objects;

/**
 * Typed RenderRequest identity. Non-blank. The request id participates in
 * {@link RenderPlanId} correlation but is EXCLUDED from the plan fingerprint (C7).
 */
public record RenderRequestId(String value) {

    public RenderRequestId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("RenderRequestId must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
