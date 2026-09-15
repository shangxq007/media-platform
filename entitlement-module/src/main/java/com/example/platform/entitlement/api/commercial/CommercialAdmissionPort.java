package com.example.platform.entitlement.api.commercial;

/** Canonical provider-neutral H5 application port consumed by technical callers. */
@FunctionalInterface
public interface CommercialAdmissionPort {
    CommercialDecision decide(CommercialAdmissionRequest request);

    /** Repeat admission with owner grant locks held through the caller's transaction commit. */
    default CommercialDecision decideForAcceptance(CommercialAdmissionRequest request) {
        throw new IllegalStateException("Transactional acceptance is unavailable");
    }
}
