package com.example.platform.artifact.app;

import com.example.platform.artifact.domain.ArtifactKind;
import com.example.platform.artifact.domain.ArtifactMediaType;
import com.example.platform.artifact.domain.ArtifactState;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.identity.ArtifactId;
import java.time.Instant;

/**
 * Application-facing Artifact projection.
 *
 * <p>Contains logical identity, immutable materialization digest, lifecycle and
 * bounded descriptive facts only. Replica identifiers, provider identifiers,
 * object keys, raw storage URIs and signed URLs are intentionally absent.</p>
 */
public record ArtifactSummary(
        ArtifactId artifactId,
        ArtifactMediaType mediaType,
        ArtifactKind artifactKind,
        ContentDigest contentDigest,
        long byteLength,
        ArtifactState state,
        ArtifactIntegrityState integrityState,
        Instant createdAt) {
}
