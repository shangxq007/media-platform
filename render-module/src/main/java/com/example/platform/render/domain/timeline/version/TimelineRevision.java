package com.example.platform.render.domain.timeline.version;

import com.example.platform.render.domain.timeline.canonical.TimelineDocument;
import java.time.Instant;

/**
 * Immutable TimelineRevision - represents a single immutable timeline snapshot.
 */
public record TimelineRevision(
        String revisionId,
        String productId,
        String parentRevisionId,
        String timelineSchemaVersion,
        TimelineDocument canonicalTimeline,
        String contentDigest,
        Instant createdAt,
        String createdBy) {

    public TimelineRevision {
        if (revisionId == null || revisionId.isBlank())
            throw new IllegalArgumentException("revisionId must not be blank");
        if (productId == null || productId.isBlank())
            throw new IllegalArgumentException("productId must not be blank");
        if (timelineSchemaVersion == null || timelineSchemaVersion.isBlank())
            throw new IllegalArgumentException("timelineSchemaVersion must not be blank");
        if (contentDigest == null || contentDigest.isBlank())
            throw new IllegalArgumentException("contentDigest must not be blank");
        if (createdAt == null)
            throw new IllegalArgumentException("createdAt must not be null");
        // canonicalTimeline CAN be null when loading from DB without full document
    }

    public boolean isRoot() {
        return parentRevisionId == null;
    }

    public TimelineRevision withContent(TimelineDocument newContent, String newDigest) {
        return new TimelineRevision(revisionId, productId, parentRevisionId,
                timelineSchemaVersion, newContent, newDigest, createdAt, createdBy);
    }
}
