package com.example.platform.timeline.api.event;
import java.time.Instant;
/** Intentional publication contract (CAR-0096); no new producer is implied by this definition. */
public record TimelineRevisionCreatedEvent(TimelineRevisionIdentity reference,Integer revisionNumber,String author,
 String source,String summary,Boolean isMerge,Instant occurredAt){
 public TimelineRevisionCreatedEvent{java.util.Objects.requireNonNull(reference);if(revisionNumber==null||revisionNumber<1)throw new IllegalArgumentException("revision number required");TimelineRevisionIdentity.require(author);TimelineRevisionIdentity.require(source);java.util.Objects.requireNonNull(isMerge);java.util.Objects.requireNonNull(occurredAt);}
 public String tenantId(){return reference.tenantId();}public String projectId(){return reference.projectId();}public String revisionId(){return reference.revisionId();}
}
