package com.example.platform.capability.effective;

/** A source-qualified reason; source vocabulary is intentionally not rewritten. */
public record EffectiveCapabilityReason(
        EffectiveCapabilitySource source,
        String authorityName,
        String code,
        String decisionReference) {

    public EffectiveCapabilityReason {
        authorityName = EffectiveCapabilityValidation.requireNonBlank(authorityName, "authorityName");
        code = EffectiveCapabilityValidation.requireNonBlank(code, "code");
        decisionReference = EffectiveCapabilityValidation.requireNonBlank(decisionReference, "decisionReference");
    }
}
