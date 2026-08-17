package com.example.platform.artifact.infrastructure;

import com.example.platform.artifact.app.ArtifactRelationRepository;
import com.example.platform.artifact.domain.Artifact;
import com.example.platform.artifact.domain.ArtifactQueryService;
import com.example.platform.artifact.domain.ArtifactReplicaBinding;
import com.example.platform.artifact.domain.ProvenanceEdge;
import com.example.platform.artifact.domain.ProvenanceRelationType;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.identity.ArtifactId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * GCR-2 (ARTIFACT_AUTHORITY_CONTRACT_V1): canonical Artifact query service
 * backed by the single artifact-module persistence adapter. Consumers (e.g.
 * Timeline pin validation) depend on this contract — never on raw jOOQ rows.
 */
@Service
public class JooqArtifactQueryService implements ArtifactQueryService {

    private final ArtifactRepository artifactRepository;
    private final ArtifactRelationRepository relationRepository;

    public JooqArtifactQueryService(ArtifactRepository artifactRepository,
                                    ArtifactRelationRepository relationRepository) {
        this.artifactRepository = artifactRepository;
        this.relationRepository = relationRepository;
    }

    @Override
    public Optional<Artifact> getArtifact(String tenantId, ArtifactId artifactId) {
        return artifactRepository.findById(tenantId, artifactId);
    }

    @Override
    public List<ArtifactReplicaBinding> listReplicas(String tenantId, ArtifactId artifactId) {
        return artifactRepository.listReplicas(tenantId, artifactId);
    }

    @Override
    public List<ArtifactId> listParents(String tenantId, ArtifactId artifactId) {
        return relationRepository.findByArtifactId(artifactId.value()).stream()
                .filter(r -> r.targetId().equals(artifactId.value()))
                .map(r -> new ArtifactId(r.sourceId()))
                .toList();
    }

    @Override
    public List<ArtifactId> listChildren(String tenantId, ArtifactId artifactId) {
        return relationRepository.findByArtifactId(artifactId.value()).stream()
                .filter(r -> r.sourceId().equals(artifactId.value()))
                .map(r -> new ArtifactId(r.targetId()))
                .toList();
    }

    @Override
    public List<ProvenanceEdge> getDirectProvenance(String tenantId, ArtifactId artifactId) {
        List<ProvenanceEdge> edges = new ArrayList<>();
        String id = artifactId.value();
        for (var r : relationRepository.findByArtifactId(id)) {
            ProvenanceRelationType type;
            try {
                type = ProvenanceRelationType.valueOf(r.relationType());
            } catch (IllegalArgumentException e) {
                continue;
            }
            edges.add(new ProvenanceEdge(
                    r.id(), tenantId,
                    new ArtifactId(r.sourceId()),
                    new ArtifactId(r.targetId()),
                    type, "unknown", 1, "unknown", "", "", null));
        }
        return edges;
    }

    @Override
    public List<ArtifactId> boundedAncestorTraversal(String tenantId, ArtifactId artifactId, int maxDepth) {
        if (maxDepth < 1) {
            throw new IllegalArgumentException("maxDepth must be >= 1");
        }
        Set<ArtifactId> seen = new LinkedHashSet<>();
        List<ArtifactId> frontier = List.of(artifactId);
        for (int depth = 0; depth < maxDepth && !frontier.isEmpty(); depth++) {
            List<ArtifactId> next = new ArrayList<>();
            for (ArtifactId current : frontier) {
                for (ArtifactId parent : listParents(tenantId, current)) {
                    if (seen.add(parent)) {
                        next.add(parent);
                    }
                }
            }
            frontier = next;
        }
        return new ArrayList<>(seen);
    }

    @Override
    public List<ArtifactId> boundedDescendantTraversal(String tenantId, ArtifactId artifactId, int maxDepth) {
        if (maxDepth < 1) {
            throw new IllegalArgumentException("maxDepth must be >= 1");
        }
        Set<ArtifactId> seen = new LinkedHashSet<>();
        List<ArtifactId> frontier = List.of(artifactId);
        for (int depth = 0; depth < maxDepth && !frontier.isEmpty(); depth++) {
            List<ArtifactId> next = new ArrayList<>();
            for (ArtifactId current : frontier) {
                for (ArtifactId child : listChildren(tenantId, current)) {
                    if (seen.add(child)) {
                        next.add(child);
                    }
                }
            }
            frontier = next;
        }
        return new ArrayList<>(seen);
    }

    @Override
    public List<Artifact> findByContentDigest(String tenantId, ContentDigest contentDigest, int limit) {
        return artifactRepository.findByContentDigest(tenantId, contentDigest, limit);
    }
}
