package com.example.platform.render.api.event;
import com.example.platform.render.api.request.RenderInitiator;
import java.time.Instant;
import java.util.Objects;
/** Fact of a persisted QUEUED job, independent of provider selection. */
public record RenderJobCreatedEvent(String renderJobId,String projectId,String timelineSnapshotId,
        String profile,RenderInitiator initiator,Instant createdAt) {
 public RenderJobCreatedEvent {RenderEventIdentity.require(renderJobId,"job");RenderEventIdentity.require(projectId,"project");RenderEventIdentity.require(timelineSnapshotId,"snapshot");RenderEventIdentity.require(profile,"profile");Objects.requireNonNull(initiator);Objects.requireNonNull(createdAt);}
 public String tenantId(){return initiator.tenantId();}
 public String factKey(){return RenderEventIdentity.key("render.created",tenantId(),projectId,renderJobId,"");}
}
