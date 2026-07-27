package com.example.platform.render.domain.timeline.patch;

import java.util.List;

/**
 * Immutable, strongly typed, schema-versioned TimelinePatch.
 * Declares operations to apply to a Base Revision.
 */
public record TimelinePatch(
        String patchVersion,
        String patchId,
        String productId,
        String baseRevisionId,
        String baseContentDigest,
        String expectedCurrentRevisionId,
        String timelineSchemaVersion,
        List<TimelinePatchOperation> operations,
        String expectedResultDigest,
        PatchMetadata metadata) {

    public TimelinePatch {
        if (patchVersion == null || patchVersion.isBlank())
            throw new IllegalArgumentException("patchVersion must not be blank");
        if (patchId == null || patchId.isBlank())
            throw new IllegalArgumentException("patchId must not be blank");
        if (productId == null || productId.isBlank())
            throw new IllegalArgumentException("productId must not be blank");
        if (baseRevisionId == null || baseRevisionId.isBlank())
            throw new IllegalArgumentException("baseRevisionId must not be blank");
        if (baseContentDigest == null || baseContentDigest.isBlank())
            throw new IllegalArgumentException("baseContentDigest must not be blank");
        if (expectedCurrentRevisionId == null || expectedCurrentRevisionId.isBlank())
            throw new IllegalArgumentException("expectedCurrentRevisionId must not be blank");
        if (timelineSchemaVersion == null || timelineSchemaVersion.isBlank())
            throw new IllegalArgumentException("timelineSchemaVersion must not be blank");
        if (operations == null)
            throw new IllegalArgumentException("operations must not be null");
        operations = List.copyOf(operations);
    }

    public static final String CURRENT_PATCH_VERSION = "1.0";

    public boolean hasExpectedResultDigest() {
        return expectedResultDigest != null && !expectedResultDigest.isBlank();
    }
}
