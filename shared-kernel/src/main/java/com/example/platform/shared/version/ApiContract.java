package com.example.platform.shared.version;

import java.util.Objects;

/**
 * VERSION_COMPATIBILITY_GOVERNANCE_FOUNDATION_V1 (VCG-5): API interface
 * lifecycle governance — independent of PlatformReleaseVersion, ReleaseChannel
 * and RolloutPolicy.
 *
 * <p>INTERFACE_VERSION_IS_NOT_PLATFORM_RELEASE_VERSION_V1 and
 * INTERFACE_LIFECYCLE_IS_EXPLICIT_METADATA_NOT_VERSION_PARITY_V1:
 * ApiContractVersion = EPOCH.RELEASE; lifecycle is explicit metadata.
 */
public record ApiContract(String contractId, CanonicalFormatVersion contractVersion, ApiLifecycle lifecycle) {

    public ApiContract {
        Objects.requireNonNull(contractId, "contractId");
        Objects.requireNonNull(contractVersion, "contractVersion");
        Objects.requireNonNull(lifecycle, "lifecycle");
        if (contractId.isBlank()) {
            throw new IllegalArgumentException("contractId must not be blank");
        }
    }

    /** API lifecycle — DRAFT/PREVIEW/STABLE/DEPRECATED/RETIRED. */
    public enum ApiLifecycle {
        DRAFT, PREVIEW, STABLE, DEPRECATED, RETIRED
    }
}
