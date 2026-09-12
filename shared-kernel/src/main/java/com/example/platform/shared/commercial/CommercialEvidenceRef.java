package com.example.platform.shared.commercial;

/** Structured reference to authority evidence used by a commercial decision. */
public record CommercialEvidenceRef(
        String authority,
        String evidenceType,
        String evidenceId) {

    public CommercialEvidenceRef {
        authority = requireNonBlank(authority, "authority");
        evidenceType = requireNonBlank(evidenceType, "evidenceType");
        evidenceId = requireNonBlank(evidenceId, "evidenceId");
    }
    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be null/blank");
        return value;
    }
}
