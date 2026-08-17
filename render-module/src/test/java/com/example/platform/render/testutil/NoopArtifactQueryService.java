package com.example.platform.render.testutil;

import com.example.platform.artifact.domain.Artifact;
import com.example.platform.artifact.domain.ArtifactQueryService;
import com.example.platform.artifact.domain.ArtifactReplicaBinding;
import com.example.platform.artifact.domain.ProvenanceEdge;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.identity.ArtifactId;
import java.util.List;
import java.util.Optional;

/**
 * Test-only ArtifactQueryService that resolves nothing. Suitable for timeline
 * tests whose source bindings carry no exact Artifact pins (empty query
 * responses are correct there).
 */
public final class NoopArtifactQueryService implements ArtifactQueryService {

    @Override
    public Optional<Artifact> getArtifact(String tenantId, ArtifactId artifactId) {
        return Optional.empty();
    }

    @Override
    public List<ArtifactReplicaBinding> listReplicas(String tenantId, ArtifactId artifactId) {
        return List.of();
    }

    @Override
    public java.util.Optional<ArtifactReplicaBinding> findReplica(String tenantId, ArtifactId artifactId,
            com.example.platform.storage.contract.StorageReplicaId replicaId) {
        return java.util.Optional.empty();
    }

    @Override
    public List<ArtifactId> listParents(String tenantId, ArtifactId artifactId) {
        return List.of();
    }

    @Override
    public List<ArtifactId> listChildren(String tenantId, ArtifactId artifactId) {
        return List.of();
    }

    @Override
    public List<ProvenanceEdge> getDirectProvenance(String tenantId, ArtifactId artifactId) {
        return List.of();
    }

    @Override
    public List<ArtifactId> boundedAncestorTraversal(String tenantId, ArtifactId artifactId, int maxDepth) {
        return List.of();
    }

    @Override
    public List<ArtifactId> boundedDescendantTraversal(String tenantId, ArtifactId artifactId, int maxDepth) {
        return List.of();
    }

    @Override
    public List<Artifact> findByContentDigest(String tenantId, ContentDigest contentDigest, int limit) {
        return List.of();
    }
}
