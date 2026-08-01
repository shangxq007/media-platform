package com.example.platform.render.domain.environment;

/**
 * Immutable acknowledgement from OpenCue submission.
 *
 * <p>Distinguishes accepted, rejected, and transport/protocol failures.
 * Only ACCEPTED means OpenCue actually accepted the job.
 * Client invocation is NOT acceptance.
 */
public record OpenCueSubmissionResult(
        OpenCueSubmissionOutcome outcome,
        String externalJobId,
        OpenCueSubmissionError error,
        String errorMessage,
        long submissionDurationMs) {

    public enum OpenCueSubmissionOutcome {
        /** OpenCue accepted the job. externalJobId is populated. */
        ACCEPTED,
        /** OpenCue explicitly rejected the job. */
        REJECTED,
        /** Transport or protocol failure. */
        FAILURE
    }

    /**
     * Accepted result with external job ID.
     */
    public static OpenCueSubmissionResult accepted(String externalJobId, long durationMs) {
        return new OpenCueSubmissionResult(
                OpenCueSubmissionOutcome.ACCEPTED,
                externalJobId, null, null, durationMs);
    }

    /**
     * Rejected result.
     */
    public static OpenCueSubmissionResult rejected(OpenCueSubmissionError error,
                                                     String message, long durationMs) {
        return new OpenCueSubmissionResult(
                OpenCueSubmissionOutcome.REJECTED,
                null, error, message, durationMs);
    }

    /**
     * Failure result (transport, protocol, unexpected).
     */
    public static OpenCueSubmissionResult failure(OpenCueSubmissionError error,
                                                    String message, long durationMs) {
        return new OpenCueSubmissionResult(
                OpenCueSubmissionOutcome.FAILURE,
                null, error, message, durationMs);
    }

    public boolean isAccepted() {
        return outcome == OpenCueSubmissionOutcome.ACCEPTED;
    }

    public boolean isRejected() {
        return outcome == OpenCueSubmissionOutcome.REJECTED;
    }

    public boolean isFailure() {
        return outcome == OpenCueSubmissionOutcome.FAILURE;
    }
}