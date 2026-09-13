package com.example.platform.artifact.api.event;
import com.example.platform.artifact.app.ArtifactOutputReference;
import java.time.Instant;
import java.util.Objects;
/** A new Render output was accepted by Artifact commitment. Replay is not creation. */
public record ArtifactCreatedEvent(ArtifactOutputReference result, Instant createdAt) {
    public ArtifactCreatedEvent { Objects.requireNonNull(result); Objects.requireNonNull(createdAt); }
    public String artifactId() { return result.artifactId().value(); }
    public String tenantId() { return result.scope().tenantId(); }
    public String projectId() { return result.scope().projectId(); }
    public String renderJobId() { return result.scope().renderJobId(); }
    public String factKey() { return "artifact-created:"+tenantId()+":"+artifactId(); }
}
