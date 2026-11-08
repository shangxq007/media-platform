package com.example.platform.workerfabric.reuse;

/**
 * Bounded-retryable admission outcome: no eligible work is currently available because legal
 * reservations occupy capacity. It must not be treated as a deterministic contract failure; the
 * activity retry policy may re-run admission after existing work releases capacity. All eligibility,
 * lease, fence and grant checks remain mandatory on every retry.
 */
public final class AdmissionCapacityRetryableException extends IllegalStateException {
    public AdmissionCapacityRetryableException(String detail) {
        super("GRANT_MINT_REJECTED: ADMISSION_NO_WORK: " + detail);
    }
}
