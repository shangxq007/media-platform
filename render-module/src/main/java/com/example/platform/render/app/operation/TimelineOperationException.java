package com.example.platform.render.app.operation;

import java.util.List;

/** Typed backend failure projection for the canonical Timeline operation path. */
public final class TimelineOperationException extends RuntimeException {

    public enum Code {
        BASE_REVISION_NOT_FOUND,
        STALE_BASE_REVISION,
        SOURCE_REFERENCE_INVALID,
        CANDIDATE_INVALID,
        PLAN_CHANGED,
        INVALID_PLAN,
        STALE_TARGET_REF,
        AUTHORIZATION_DENIED,
        AUTHORIZATION_CONTEXT_MISMATCH,
        AUTHORIZATION_STALE,
        IDEMPOTENCY_KEY_CONFLICT,
        TARGET_MISSING,
        UNSUPPORTED_TEMPORAL_STATE,
        UNSUPPORTED_AUDIO_TEMPORAL_BEHAVIOR,
        SYNC_ANCHOR_INVALIDATED,
        GROUP_CARDINALITY_CONFLICT,
        PLACEMENT_CONFLICT,
        BATCH_CONFLICT,
        CANONICAL_INVARIANT_VIOLATION,
        PERSISTENCE_FAILURE,
        REF_UPDATE_FAILURE,
        APPLY_UNKNOWN_FAILURE
    }

    private final Code code;
    private final List<String> failures;

    public TimelineOperationException(Code code, List<String> failures) {
        super(failures == null || failures.isEmpty() ? code.name() : String.join("; ", failures));
        this.code = code;
        this.failures = failures == null ? List.of() : List.copyOf(failures);
    }

    public Code code() {
        return code;
    }

    public List<String> failures() {
        return failures;
    }
}
