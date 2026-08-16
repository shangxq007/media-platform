package com.example.platform.timeline.patch;

import com.example.platform.timeline.canonical.TimelineDocument;

import java.util.List;

/**
 * Result of patch application: either success with new document or failure with errors.
 */
public sealed interface PatchApplicationResult {

    record Success(TimelineDocument document) implements PatchApplicationResult {}

    record Failure(List<PatchError> errors) implements PatchApplicationResult {}

    static PatchApplicationResult success(TimelineDocument document) {
        return new Success(document);
    }

    static PatchApplicationResult failure(List<PatchError> errors) {
        return new Failure(errors);
    }

    default boolean isSuccess() {
        return this instanceof Success;
    }

    default boolean isFailure() {
        return this instanceof Failure;
    }
}
