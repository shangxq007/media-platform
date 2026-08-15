package com.example.platform.shared.version;

/**
 * VERSION_COMPATIBILITY_GOVERNANCE_FOUNDATION_V1 (VCG-2): explicit lifecycle
 * metadata for contracts/formats/interfaces.
 *
 * <p>Lifecycle is NEVER inferred from version numbers (no odd/even stability,
 * no "major = stable", no version-suffix policy). It is explicit metadata,
 * independent of ReleaseChannel, capability runtime availability, plugin
 * lifecycle, entitlement, policy and quota.
 */
public enum Lifecycle {
    /** Repository/internal design; no compatibility promise. */
    DRAFT,

    /** Available for controlled use; stable compatibility guarantee not yet active. */
    PREVIEW,

    /** Normal compatibility policy enforced. */
    STABLE,

    /** Still readable/usable per explicit policy; new consumers should not adopt. */
    DEPRECATED,

    /** Not available for new execution/use. */
    RETIRED
}
