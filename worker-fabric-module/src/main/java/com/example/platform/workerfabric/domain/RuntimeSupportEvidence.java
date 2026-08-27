package com.example.platform.workerfabric.domain;

/** Immutable provenance for a statically installed runtime-support identifier. */
public record RuntimeSupportEvidence(String evidenceType, String value) {

    public RuntimeSupportEvidence {
        if (evidenceType == null || evidenceType.isBlank() || value == null || value.isBlank()) {
            throw new IllegalArgumentException("runtime support evidence must not be blank");
        }
        String canonicalType = evidenceType.toLowerCase(java.util.Locale.ROOT);
        if (!evidenceType.equals(canonicalType)
                || !canonicalType.matches("[a-z0-9]+(?:[._-][a-z0-9]+)*")
                || canonicalType.matches(
                        ".*(capacity|availability|reservation|usage|can[-_.]?run|authorize|eligible).*")) {
            throw new IllegalArgumentException(
                    "runtime support evidence type cannot represent mutable runtime authority");
        }
    }
}
