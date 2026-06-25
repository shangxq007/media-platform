package com.example.platform.render.domain.timeline.internal;

import java.time.Instant;

/**
 * A comment on a timeline review, anchored to an entity (clip, track, effect, marker)
 * rather than a raw timecode. Comments belong to threads for threaded discussion.
 */
public record TimelineComment(
        String commentId,
        String reviewId,
        String threadId,
        String revisionId,
        EntityRef entityRef,
        String authorUserId,
        String content,
        Instant createdAt) {

    public static TimelineComment create(String commentId, String reviewId,
                                           String threadId, String revisionId,
                                           EntityRef entityRef, String authorUserId,
                                           String content) {
        return new TimelineComment(commentId, reviewId, threadId, revisionId,
                entityRef, authorUserId, content, Instant.now());
    }
}
