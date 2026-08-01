package com.example.platform.render.domain.environment;

/**
 * Client seam for OpenCue submission.
 *
 * <p>Injectable port — no static mutable state.
 * Accepts immutable request, returns immutable acknowledgement.
 * Explicit rejection/transport/protocol failure reporting.
 *
 * <p>Default production implementation may use gRPC or REST.
 * Test implementations must NOT establish real network connections.
 */
public interface OpenCueSubmissionClient {

    /**
     * Submit an immutable request to OpenCue.
     *
     * <p>Only returns ACCEPTED when OpenCue actually accepted the job.
     * Client invocation is NOT acceptance.
     *
     * @param request immutable submission request
     * @return immutable acknowledgement
     */
    OpenCueSubmissionResult submit(OpenCueSubmissionRequest request);
}