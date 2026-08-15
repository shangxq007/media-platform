package com.example.platform.shared.version;

import java.util.Objects;

/**
 * VERSION_COMPATIBILITY_GOVERNANCE_FOUNDATION_V1 (VCG-2B): bounded
 * CompatibilityAdvisory — a format/contract may be supported while a specific
 * producer release is known-bad.
 *
 * <p>Example: Timeline format 2.4 supported; producer media-platform 2.4.3
 * known-bad (issue X) -> special validation required. This is NOT expressed by
 * faking a Timeline "2.4.3" data version.
 */
public record CompatibilityAdvisory(
        String advisoryId,
        String affectedContractOrFormat,
        VersionRange<ReleaseVersion> affectedProducerRange,
        Severity severity,
        String issueCode,
        Handling handling) {

    public CompatibilityAdvisory {
        Objects.requireNonNull(advisoryId, "advisoryId");
        Objects.requireNonNull(affectedContractOrFormat, "affectedContractOrFormat");
        Objects.requireNonNull(affectedProducerRange, "affectedProducerRange");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(issueCode, "issueCode");
        Objects.requireNonNull(handling, "handling");
        if (advisoryId.isBlank()) {
            throw new IllegalArgumentException("advisoryId must not be blank");
        }
    }

    public enum Severity {
        INFO, WARNING, CRITICAL
    }

    /** Bounded handling semantics — no scripting mechanism. */
    public enum Handling {
        WARN, VALIDATE, REJECT, RECOVER, MIGRATE
    }
}
