package com.example.platform.capability.effective;

/** Immutable, source-owned evidence reference carried without reinterpretation. */
public record AuthorityProvenance(String authority, String evidenceType, String evidenceId) {

    public AuthorityProvenance {
        authority = EffectiveCapabilityValidation.requireNonBlank(authority, "authority");
        evidenceType = EffectiveCapabilityValidation.requireNonBlank(evidenceType, "evidenceType");
        evidenceId = EffectiveCapabilityValidation.requireNonBlank(evidenceId, "evidenceId");
    }
}
