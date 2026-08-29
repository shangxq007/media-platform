package com.example.platform.render.domain.environment;

/**
 * Error classification for OpenCue submission failures.
 * Explicit, deterministic, non-fallback, safe for logs.
 */
public enum OpenCueSubmissionError {

    /** Backend identity is null, blank, or the reserved collapsed sentinel. */
    UNSUPPORTED_BACKEND,

    /** OpenCue configuration is missing or incomplete. */
    MISSING_CONFIGURATION,

    /** Submission input is invalid (null fields, empty tasks, etc.). */
    INVALID_SUBMISSION_INPUT,

    /** OpenCue client explicitly rejected the submission. */
    CLIENT_REJECTED,

    /** Transport-level failure (network unreachable, timeout, connection refused). */
    TRANSPORT_FAILURE,

    /** Protocol or serialization failure (malformed request, serialization error). */
    PROTOCOL_FAILURE,

    /** Unexpected client exception not covered by other categories. */
    UNEXPECTED_CLIENT_EXCEPTION
}
