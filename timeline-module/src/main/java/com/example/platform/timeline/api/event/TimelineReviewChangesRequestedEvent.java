package com.example.platform.timeline.api.event;
import java.time.Instant;
/** An accepted reviewer decision, identified by decisionId. Aggregate status may already match. */
public record TimelineReviewChangesRequestedEvent(TimelineReviewReference reference,String decisionId,String reviewerUserId,Instant occurredAt){
 public TimelineReviewChangesRequestedEvent{java.util.Objects.requireNonNull(reference);java.util.Objects.requireNonNull(occurredAt);TimelineRevisionIdentity.require(decisionId);TimelineRevisionIdentity.require(reviewerUserId);}
 public String reviewId(){return reference.reviewId();}
 public String projectId(){return reference.projectId();}
 public String tenantId(){return reference.tenantId();}
 public String revisionId(){return reference.revision().revisionId();}
 public String factKey(){return "timeline-reviewchangesrequested:"+tenantId()+":"+decisionId;}
}
