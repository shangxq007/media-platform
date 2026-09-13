package com.example.platform.render.api.event;
import com.example.platform.render.api.request.RenderInitiator;
import com.example.platform.render.domain.RenderJobStatus;
import java.time.Instant;
import java.util.Objects;
/** Unsuccessful accepted terminal outcome; commercial rejection remains REJECTED. */
public record RenderJobFailedEvent(String renderJobId,String projectId,RenderFailureReason reason,Instant failedAt,
        RenderInitiator initiator,RenderJobStatus outcome) {
 public RenderJobFailedEvent {RenderEventIdentity.require(renderJobId,"job");RenderEventIdentity.require(projectId,"project");Objects.requireNonNull(reason);Objects.requireNonNull(failedAt);Objects.requireNonNull(initiator);if(outcome!=RenderJobStatus.FAILED&&outcome!=RenderJobStatus.REJECTED)throw new IllegalArgumentException("unsuccessful terminal outcome required");if((reason==RenderFailureReason.COMMERCIAL_REJECTED)!=(outcome==RenderJobStatus.REJECTED))throw new IllegalArgumentException("failure reason/outcome mismatch");}
 public String tenantId(){return initiator.tenantId();}
 public String factKey(){return RenderEventIdentity.key("render.failed",tenantId(),projectId,renderJobId,outcome.name());}
}
