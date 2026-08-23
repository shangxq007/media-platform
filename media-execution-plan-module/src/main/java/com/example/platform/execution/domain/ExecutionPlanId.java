package com.example.platform.execution.domain;

import java.io.Serializable;
import java.util.Objects;

/**
 * ROADMAP #21 canonical plan identity (frozen ledger REUSE_AS_CANONICAL).
 *
 * <p>ExecutionPlanId is IDENTITY — independent from semantic content digest.
 * MUST NOT be derived from RenderPlanFingerprint or any semantic-content hash
 * (frozen contract: identity != digest). Deterministic creation from
 * non-semantic provenance identity is permitted; otherwise the id is supplied
 * as planning input / context.
 */
public record ExecutionPlanId(String value) implements Serializable {

    public ExecutionPlanId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("ExecutionPlanId must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
