package com.example.platform.timeline.api.event;
import java.time.Instant;
/** An accepted Timeline-owned review transition. */
public record TimelineReviewApprovedEvent(TimelineReviewReference reference,String decisionId,String reviewerUserId,Instant occurredAt){
 public TimelineReviewApprovedEvent{java.util.Objects.requireNonNull(reference);java.util.Objects.requireNonNull(occurredAt);TimelineRevisionIdentity.require(decisionId);TimelineRevisionIdentity.require(reviewerUserId);}
 public String reviewId(){return reference.reviewId();}
 public String projectId(){return reference.projectId();}
 public String tenantId(){return reference.tenantId();}
 public String revisionId(){return reference.revision().revisionId();}
 public String factKey(){return "timeline-reviewapproved:"+tenantId()+":"+decisionId;}
}
