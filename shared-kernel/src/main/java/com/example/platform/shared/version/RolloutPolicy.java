package com.example.platform.shared.version;

import java.util.Objects;

/**
 * VERSION_COMPATIBILITY_GOVERNANCE_FOUNDATION_V1 (VCG-3B): rollout policy
 * identity + immutable policy revision.
 *
 * <p>ROLLOUT_SELECTS_A_VERSION_BUT_DOES_NOT_DEFINE_VERSION_SEMANTICS_V1:
 * a rollout policy decides WHO gets WHICH already-compatible candidate; it
 * never defines compatibility semantics.
 */
public record RolloutPolicy(
        String policyId,
        String policyRevision) {

    public RolloutPolicy {
        Objects.requireNonNull(policyId, "policyId");
        Objects.requireNonNull(policyRevision, "policyRevision");
        if (policyId.isBlank() || policyRevision.isBlank()) {
            throw new IllegalArgumentException("policy id and revision must not be blank");
        }
    }

    /** Deterministic cohort assignment: stable subject key + policy revision
     *  => stable cohort; revision change may explicitly change allocation. */
    public String cohortFor(String subjectKey, int cohortCount, long seedOffset) {
        Objects.requireNonNull(subjectKey, "subjectKey");
        if (cohortCount <= 0) {
            throw new IllegalArgumentException("cohortCount must be > 0");
        }
        long hash = (subjectKey + "::" + policyId + "::" + policyRevision).hashCode();
        long idx = Math.floorMod(hash + seedOffset, cohortCount);
        return "cohort-" + idx;
    }
}
