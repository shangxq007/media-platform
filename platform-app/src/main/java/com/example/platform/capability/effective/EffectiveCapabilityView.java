package com.example.platform.capability.effective;

import java.util.List;
import java.util.Objects;

/** Immutable read-only application projection; it owns none of its source decisions. */
public record EffectiveCapabilityView(
        String projectionVersion,
        String capabilityId,
        EffectiveCapabilityStatus status,
        List<AuthorityDecisionInput> sourceDecisions,
        List<EffectiveCapabilityReason> reasons) {

    public EffectiveCapabilityView {
        projectionVersion = EffectiveCapabilityValidation.requireNonBlank(
                projectionVersion, "projectionVersion");
        capabilityId = EffectiveCapabilityValidation.requireNonBlank(capabilityId, "capabilityId");
        Objects.requireNonNull(status, "status must not be null");
        sourceDecisions = List.copyOf(sourceDecisions);
        reasons = List.copyOf(reasons);
    }

    public boolean effective() {
        return status == EffectiveCapabilityStatus.EFFECTIVE;
    }
}
