package com.example.platform.shared.commercial;

/** Structured reference to authority evidence used by a commercial decision. */
public record CommercialEvidenceRef(
        String authority,
        String evidenceType,
        String evidenceId) {

    public CommercialEvidenceRef {
        authority = CommercialValidation.requireNonBlank(authority, "authority");
        evidenceType = CommercialValidation.requireNonBlank(evidenceType, "evidenceType");
        evidenceId = CommercialValidation.requireNonBlank(evidenceId, "evidenceId");
    }
}
