package com.example.platform.timeline.api.event;
import java.time.Instant;
/** An accepted Timeline-owned review transition. */
public record TimelineReviewThreadResolvedEvent(TimelineReviewReference reference,String factId,String threadId,String entityRef,Instant occurredAt){
 public TimelineReviewThreadResolvedEvent{java.util.Objects.requireNonNull(reference);java.util.Objects.requireNonNull(occurredAt);TimelineRevisionIdentity.require(factId);TimelineRevisionIdentity.require(threadId);}
 public String reviewId(){return reference.reviewId();}
 public String projectId(){return reference.projectId();}
 public String tenantId(){return reference.tenantId();}
 public String revisionId(){return reference.revision().revisionId();}
 public String factKey(){return "timeline-reviewthreadresolved:"+tenantId()+":"+factId;}
}
