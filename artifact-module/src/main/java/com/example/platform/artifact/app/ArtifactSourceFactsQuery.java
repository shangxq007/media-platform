package com.example.platform.artifact.app;

import com.example.platform.artifact.contract.ArtifactSourceFacts;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.identity.ArtifactId;
import java.util.Optional;

/** Artifact-owned source structure, bound to tenant, project, identity, and exact digest. */
public interface ArtifactSourceFactsQuery {
    Optional<ArtifactSourceFacts> find(String tenantId, String projectId, ArtifactId artifactId, ContentDigest digest);
}
