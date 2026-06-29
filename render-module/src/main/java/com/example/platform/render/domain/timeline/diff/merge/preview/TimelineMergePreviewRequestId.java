package com.example.platform.render.domain.timeline.diff.merge.preview;

/**
 * Identifier for a merge preview request.
 * Internal domain model. Provider-neutral, storage-neutral.
 */
public record TimelineMergePreviewRequestId(String value) {
    public TimelineMergePreviewRequestId {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("TimelineMergePreviewRequestId must not be blank");
    }
}
