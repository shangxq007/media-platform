package com.example.platform.render.api.event;
import com.example.platform.artifact.app.ArtifactOutputReference;
import com.example.platform.shared.events.RenderInitiator;
import java.time.Instant;
import java.util.Objects;
/** Render completion after Artifact output acceptance. Storage coordinates are not lifecycle facts. */
public record RenderJobCompletedEvent(ArtifactOutputReference result,Instant completedAt,RenderInitiator initiator) {
 public RenderJobCompletedEvent {Objects.requireNonNull(result);Objects.requireNonNull(completedAt);Objects.requireNonNull(initiator);if(!result.scope().tenantId().equals(initiator.tenantId()))throw new IllegalArgumentException("completion tenant mismatch");}
 public String renderJobId(){return result.scope().renderJobId();}
 public String projectId(){return result.scope().projectId();}
 public String tenantId(){return result.scope().tenantId();}
 public String factKey(){return RenderEventIdentity.key("render.completed",tenantId(),projectId(),renderJobId(),"");}
}
