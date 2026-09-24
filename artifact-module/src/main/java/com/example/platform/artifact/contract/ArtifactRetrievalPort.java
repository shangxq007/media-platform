package com.example.platform.artifact.contract;

import com.example.platform.shared.identity.ArtifactId;

/** Retrieval boundary; implementations must scope and verify integrity before returning. */
public interface ArtifactRetrievalPort {
    ArtifactRetrievalResult retrieve(ArtifactId artifactId, ArtifactScope scope);
}
