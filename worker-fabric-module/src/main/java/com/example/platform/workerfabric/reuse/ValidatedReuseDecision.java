package com.example.platform.workerfabric.reuse;

import java.util.Objects;
import java.util.Optional;

/** Explainable outcome after reuse-index lookup and Artifact authority validation. */
public record ValidatedReuseDecision(
        Outcome outcome,
        Optional<ReusableArtifactRecord> record,
        String reason) {

    public ValidatedReuseDecision {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(record, "record");
        Objects.requireNonNull(reason, "reason");
        if (outcome == Outcome.VALIDATED_HIT && record.isEmpty()) {
            throw new IllegalArgumentException("validated hit requires reusable Artifact record");
        }
        if (outcome != Outcome.VALIDATED_HIT && record.isPresent()) {
            throw new IllegalArgumentException("non-hit decision must not expose reusable Artifact");
        }
    }

    public static ValidatedReuseDecision hit(ReusableArtifactRecord record) {
        return new ValidatedReuseDecision(
                Outcome.VALIDATED_HIT,
                Optional.of(record),
                "Artifact authority validated exact tenant, AVAILABLE state and ContentDigest");
    }

    public static ValidatedReuseDecision reject(Outcome outcome, String reason) {
        return new ValidatedReuseDecision(outcome, Optional.empty(), reason);
    }

    public enum Outcome {
        VALIDATED_HIT,
        MISS,
        STALE,
        CORRUPT,
        UNAUTHORIZED,
        NOT_CACHEABLE
    }
}
