package com.example.platform.capability.effective;

/** Five independent source decisions supplied to the neutral application projection. */
public record EffectiveCapabilityInputs(
        String capabilityId,
        AuthorityDecisionInput capabilityLifecycle,
        AuthorityDecisionInput runtimeAvailability,
        AuthorityDecisionInput entitlement,
        AuthorityDecisionInput quota,
        AuthorityDecisionInput roleWorkspacePolicy) {

    public EffectiveCapabilityInputs {
        capabilityId = EffectiveCapabilityValidation.requireNonBlank(capabilityId, "capabilityId");
        requireSource(capabilityLifecycle, EffectiveCapabilitySource.CAPABILITY_LIFECYCLE);
        requireSource(runtimeAvailability, EffectiveCapabilitySource.H1_RUNTIME_AVAILABILITY);
        requireSource(entitlement, EffectiveCapabilitySource.H5_ENTITLEMENT);
        requireSource(quota, EffectiveCapabilitySource.H5_COMMERCIAL_QUOTA);
        requireSource(roleWorkspacePolicy, EffectiveCapabilitySource.ROLE_WORKSPACE_POLICY);
    }

    private static void requireSource(
            AuthorityDecisionInput input, EffectiveCapabilitySource expected) {
        if (input != null && input.source() != expected) {
            throw new IllegalArgumentException(expected + " input has source " + input.source());
        }
    }
}
