package com.example.platform.execution.compatibility;

import java.util.Comparator;
import java.util.Objects;

/** Deterministically ordered reference evidence; {@link StaticCompatibilityFailure} remains reason authority. */
public record CompatibilityEvidence(
        StaticCompatibilityFailure failure,
        ReferenceKind referenceKind,
        String canonicalReference) {

    public static final Comparator<CompatibilityEvidence> CANONICAL_ORDER =
            Comparator.comparing(CompatibilityEvidence::failure)
                    .thenComparing(CompatibilityEvidence::referenceKind)
                    .thenComparing(CompatibilityEvidence::canonicalReference);

    public CompatibilityEvidence {
        Objects.requireNonNull(failure, "failure");
        Objects.requireNonNull(referenceKind, "referenceKind");
        Objects.requireNonNull(canonicalReference, "canonicalReference");
        if (canonicalReference.isBlank()) {
            throw new IllegalArgumentException("canonical evidence reference must not be blank");
        }
    }

    public enum ReferenceKind {
        PROVIDER_BINDING,
        PROVIDER_CONTRACT,
        CAPABILITY,
        ARTIFACT,
        CODEC,
        DEVICE_KIND,
        PROVIDER_RUNTIME_CLASS,
        SANDBOX_MODE,
        DETERMINISM,
        CROSS_PROVIDER_BOUNDARY,
        LOWERING
    }
}
