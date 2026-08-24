package com.example.platform.workerfabric.domain;

import java.util.Optional;

/**
 * Durable one-platform-attempt-to-one-OpenCue-submission invariant.
 *
 * <p>Implementations must enforce uniqueness by {@link ExecutionAttemptId}. Re-registering the same
 * job is idempotent; a second job for that attempt is rejected rather than creating dual authority.
 */
public interface OpenCueSubmissionLedgerPort {

    RegistrationResult register(OpenCueBackendExecutionHandle handle);

    Optional<OpenCueBackendExecutionHandle> findByAttempt(ExecutionAttemptId executionAttemptId);

    enum RegistrationResult {
        RECORDED,
        DUPLICATE_SAME_SUBMISSION_NOOP,
        CONFLICTING_SECOND_SUBMISSION_REJECTED
    }
}
