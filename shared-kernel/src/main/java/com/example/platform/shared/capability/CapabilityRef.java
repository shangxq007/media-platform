package com.example.platform.shared.capability;

import java.util.Objects;

/**
 * Lightweight capability dependency reference (CAPABILITY_OPERATION_PARAMETER_MODEL / E-2b-1b, option d).
 *
 * <p>An operation definition's <em>intrinsic</em> capability dependency: "this
 * operation depends on capability {@code X} at contract range {@code R}". It is
 * implicitly required (a definition cannot execute without its mechanism) and
 * carries no alternatives and no selection policy — those belong to the
 * consumer-side {@code extension.domain.CapabilityRequirement} (runtime
 * admission/compatibility/selection), which stays in the capability authority.</p>
 *
 * <p>Provider-neutral: no provider/plugin/backend/worker identity.</p>
 *
 * @param capabilityId capability identity
 * @param versionRange accepted capability contract version range
 */
public record CapabilityRef(CapabilityId capabilityId, ContractVersionRange versionRange) {

    public CapabilityRef {
        Objects.requireNonNull(capabilityId, "capabilityId");
        Objects.requireNonNull(versionRange, "versionRange");
    }
}
