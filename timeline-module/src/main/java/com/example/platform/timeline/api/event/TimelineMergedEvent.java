package com.example.platform.timeline.api.event;
import java.time.Instant;
/** Accepted merge result and the frozen source/base/target revisions. */
public record TimelineMergedEvent(TimelineRevisionIdentity result,TimelineRevisionIdentity base,
 TimelineRevisionIdentity source,TimelineRevisionIdentity target,String authorUserId,Instant occurredAt){
 public TimelineMergedEvent{java.util.Objects.requireNonNull(result);result.requireSameScope(base);result.requireSameScope(source);result.requireSameScope(target);TimelineRevisionIdentity.require(authorUserId);java.util.Objects.requireNonNull(occurredAt);}
 public String tenantId(){return result.tenantId();}public String projectId(){return result.projectId();}
 public String mergeRevisionId(){return result.revisionId();}public String baseRevisionId(){return base.revisionId();}
 public String sourceRevisionId(){return source.revisionId();}public String targetRevisionId(){return target.revisionId();}
 public String factKey(){return "timeline-merged:"+tenantId()+":"+mergeRevisionId();}
}
