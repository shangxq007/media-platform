package com.example.platform.render.api.event;
import com.example.platform.render.api.request.RenderInitiator;
import com.example.platform.render.domain.RenderJobStatus;
import com.example.platform.render.domain.RenderJobStateMachine;
import java.time.Instant;
import java.util.Objects;
/** An accepted transition, never a command or inferred worker/Product status. */
public record RenderJobStatusChangedEvent(String renderJobId,String projectId,RenderJobStatus oldStatus,
        RenderJobStatus newStatus,Instant updatedAt,RenderInitiator initiator) {
 public RenderJobStatusChangedEvent {RenderEventIdentity.require(renderJobId,"job");RenderEventIdentity.require(projectId,"project");Objects.requireNonNull(oldStatus);Objects.requireNonNull(newStatus);Objects.requireNonNull(updatedAt);Objects.requireNonNull(initiator);new RenderJobStateMachine().validateTransition(oldStatus,newStatus);}
 public String tenantId(){return initiator.tenantId();}
 public String factKey(){return RenderEventIdentity.key("render.transition",tenantId(),projectId,renderJobId,oldStatus+":"+newStatus);}
}
