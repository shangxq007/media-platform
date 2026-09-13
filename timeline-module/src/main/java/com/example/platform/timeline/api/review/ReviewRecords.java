package com.example.platform.timeline.api.review;
import java.time.OffsetDateTime;
public final class ReviewRecords { private ReviewRecords() {}
    public record ReviewRow(String id, String projectId, String tenantId, String revisionId,
                             String authorUserId, String title, String description,
                             String status, OffsetDateTime createdAt, OffsetDateTime updatedAt) {}

    public record CommentRow(String id, String reviewId, String threadId, String revisionId,
                               String entityRef, String authorUserId, String content,
                               OffsetDateTime createdAt) {}

    public record ThreadRow(String id, String reviewId, String entityRef, String diffId,
                              String status, OffsetDateTime createdAt) {}

    public record DecisionRow(String id, String reviewId, String reviewerUserId,
                                String decision, OffsetDateTime createdAt) {}
}
