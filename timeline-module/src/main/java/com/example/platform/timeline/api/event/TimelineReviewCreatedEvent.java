package com.example.platform.timeline.api.event;
import java.time.Instant;
/** An accepted Timeline-owned review transition. */
public record TimelineReviewCreatedEvent(TimelineReviewReference reference,String authorUserId,String title,Instant occurredAt){
 public TimelineReviewCreatedEvent{java.util.Objects.requireNonNull(reference);java.util.Objects.requireNonNull(occurredAt);TimelineRevisionIdentity.require(authorUserId);TimelineRevisionIdentity.require(title);}
 public String reviewId(){return reference.reviewId();}
 public String projectId(){return reference.projectId();}
 public String tenantId(){return reference.tenantId();}
 public String revisionId(){return reference.revision().revisionId();}
 public String factKey(){return "timeline-reviewcreated:"+tenantId()+":"+reference.reviewId();}
}
