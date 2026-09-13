package com.example.platform.timeline.api.event;
/** A Timeline review of one owned revision, never a targetType-discriminated asset review. */
public record TimelineReviewReference(String reviewId,TimelineRevisionIdentity revision) {
 public TimelineReviewReference{TimelineRevisionIdentity.require(reviewId);java.util.Objects.requireNonNull(revision);}
 public String tenantId(){return revision.tenantId();}
 public String projectId(){return revision.projectId();}
}
