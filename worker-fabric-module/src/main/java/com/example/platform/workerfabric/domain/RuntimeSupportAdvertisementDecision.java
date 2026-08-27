package com.example.platform.workerfabric.domain;

import java.util.Objects;

/** Candidate-evidence decision; deliberately incapable of authorizing execution. */
public record RuntimeSupportAdvertisementDecision(
        boolean acceptedAsCandidateEvidence,
        RuntimeSupportAdvertisementReason reason) {

    public RuntimeSupportAdvertisementDecision {
        Objects.requireNonNull(reason, "reason");
        if (acceptedAsCandidateEvidence
                != (reason == RuntimeSupportAdvertisementReason.ACCEPTED_CANDIDATE_EVIDENCE)) {
            throw new IllegalArgumentException("candidate-evidence status must match its reason");
        }
    }

    public boolean authorizesExecution() {
        return false;
    }
}
