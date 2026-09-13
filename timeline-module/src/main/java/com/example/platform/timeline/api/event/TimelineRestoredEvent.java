package com.example.platform.timeline.api.event;
import java.time.Instant;
/** A new revision reissues the verified historical revision's semantics. */
public record TimelineRestoredEvent(TimelineRevisionIdentity result,TimelineRevisionIdentity restoredFrom,String authorUserId,Instant occurredAt){
 public TimelineRestoredEvent{java.util.Objects.requireNonNull(result);result.requireSameScope(restoredFrom);TimelineRevisionIdentity.require(authorUserId);java.util.Objects.requireNonNull(occurredAt);}
 public String tenantId(){return result.tenantId();}public String projectId(){return result.projectId();}
 public String newRevisionId(){return result.revisionId();}public String restoredFromRevisionId(){return restoredFrom.revisionId();}
 public String factKey(){return "timeline-restored:"+tenantId()+":"+newRevisionId();}
}
