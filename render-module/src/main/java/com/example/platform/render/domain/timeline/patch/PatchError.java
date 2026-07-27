package com.example.platform.render.domain.timeline.patch;

/**
 * Patch error with code, message, and context.
 */
public record PatchError(
        PatchErrorCode code,
        String message,
        String operationId,
        String entityId) {

    public PatchError {
        if (code == null) throw new IllegalArgumentException("code must not be null");
        if (message == null) throw new IllegalArgumentException("message must not be null");
    }
}
