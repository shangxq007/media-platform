package com.example.platform.timeline.app;

import com.example.platform.timeline.patch.PatchError;

/**
 * Result of patch preview (dry run).
 */
public sealed interface PatchPreviewResult {

    record Success(String resultDigest) implements PatchPreviewResult {}

    record Failure(PatchError error) implements PatchPreviewResult {}

    static PatchPreviewResult success(String resultDigest) {
        return new Success(resultDigest);
    }

    static PatchPreviewResult failure(PatchError error) {
        return new Failure(error);
    }

    default boolean isSuccess() { return this instanceof Success; }
    default boolean isFailure() { return this instanceof Failure; }
}
