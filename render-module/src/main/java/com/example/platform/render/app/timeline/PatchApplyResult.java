package com.example.platform.render.app.timeline;

import com.example.platform.render.domain.timeline.patch.PatchError;

/**
 * Result of patch application.
 */
public sealed interface PatchApplyResult {

    record Success(String newRevisionId, String parentRevisionId, String resultDigest) implements PatchApplyResult {}

    record Failure(PatchError error) implements PatchApplyResult {}

    record NoChanges(String baseRevisionId) implements PatchApplyResult {}

    static PatchApplyResult success(String newRevisionId, String parentRevisionId, String resultDigest) {
        return new Success(newRevisionId, parentRevisionId, resultDigest);
    }

    static PatchApplyResult failure(PatchError error) {
        return new Failure(error);
    }

    static PatchApplyResult noChanges(String baseRevisionId) {
        return new NoChanges(baseRevisionId);
    }

    default boolean isSuccess() { return this instanceof Success; }
    default boolean isFailure() { return this instanceof Failure; }
    default boolean isNoChanges() { return this instanceof NoChanges; }
}
