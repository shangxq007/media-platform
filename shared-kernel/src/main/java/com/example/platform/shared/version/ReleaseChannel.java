package com.example.platform.shared.version;

/**
 * VERSION_COMPATIBILITY_GOVERNANCE_FOUNDATION_V1 (VCG-3): release channel —
 * deployment/maturity metadata.
 *
 * <p>A channel is NOT compatibility, NOT lifecycle, NOT version identity, NOT
 * entitlement. A release version does not change solely because its channel
 * changes (2.5.0 CANARY and 2.5.0 STABLE may both exist).
 */
public enum ReleaseChannel {
    DEV,
    CANARY,
    PREVIEW,
    STABLE,
    LTS
}
