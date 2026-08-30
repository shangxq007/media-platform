package com.example.platform.shared.commercial;

/** Canonical provider-neutral H5 application port consumed by technical callers. */
@FunctionalInterface
public interface CommercialAdmissionPort {
    CommercialDecision decide(CommercialAdmissionRequest request);
}
