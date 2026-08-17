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
 *
 * GCR2-CORRECTION-V1 (ARTIFACT_QUERY_TENANT_ARGUMENT_IS_SEMANTIC_NOT_DECORATIVE_V1):
 * every method enforces the tenant boundary at the database level (replica
 * queries scope through canonical Artifact ownership; relation/provenance
 * queries require BOTH peers in the requested tenant; traversal is tenant-scoped
 * at every hop). InMemoryArtifactQueryService and this adapter share identical
 * public semantics: maxDepth < 1 → empty, limit <= 0 → 1.
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
    public Optional<ArtifactReplicaBinding> findReplica(String tenantId, ArtifactId artifactId,
            com.example.platform.storage.contract.StorageReplicaId replicaId) {
        return artifactRepository.findReplica(tenantId, artifactId, replicaId);
    }

    @Override
    public List<ArtifactId> listParents(String tenantId, ArtifactId artifactId) {
        // Tenant-scoped relation lookup: both peers must belong to the tenant.
        return relationRepository.findByArtifactIdScopedToTenant(tenantId, artifactId.value()).stream()
                .filter(r -> r.targetId().equals(artifactId.value()))
                .map(r -> new ArtifactId(r.sourceId()))
                .toList();
    }

    @Override
    public List<ArtifactId> listChildren(String tenantId, ArtifactId artifactId) {
        // Tenant-scoped relation lookup: both peers must belong to the tenant.
        return relationRepository.findByArtifactIdScopedToTenant(tenantId, artifactId.value()).stream()
                .filter(r -> r.sourceId().equals(artifactId.value()))
                .map(r -> new ArtifactId(r.targetId()))
                .toList();
    }

    @Override
    public List<ProvenanceEdge> getDirectProvenance(String tenantId, ArtifactId artifactId) {
        List<ProvenanceEdge> edges = new ArrayList<>();
        String id = artifactId.value();
        for (var r : relationRepository.findByArtifactIdScopedToTenant(tenantId, id)) {
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
                    type, "unknown", 1, "unknown", "", "", java.time.Instant.EPOCH));
        }
        return edges;
    }

    @Override
    public List<ArtifactId> boundedAncestorTraversal(String tenantId, ArtifactId artifactId, int maxDepth) {
        // Contract (frozen): maxDepth < 1 -> empty (matches InMemory implementation).
        if (maxDepth < 1) {
            return List.of();
        }
        // Root must exist in the requested tenant.
        if (artifactRepository.findById(tenantId, artifactId).isEmpty()) {
            return List.of();
        }
        Set<ArtifactId> seen = new LinkedHashSet<>();
        List<ArtifactId> frontier = List.of(artifactId);
        for (int depth = 0; depth < maxDepth && !frontier.isEmpty(); depth++) {
            List<ArtifactId> next = new ArrayList<>();
            for (ArtifactId current : frontier) {
                // Tenant-scoped at EVERY hop: peer relations must be same-tenant.
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
        // Contract (frozen): maxDepth < 1 -> empty (matches InMemory implementation).
        if (maxDepth < 1) {
            return List.of();
        }
        // Root must exist in the requested tenant.
        if (artifactRepository.findById(tenantId, artifactId).isEmpty()) {
            return List.of();
        }
        Set<ArtifactId> seen = new LinkedHashSet<>();
        List<ArtifactId> frontier = List.of(artifactId);
        for (int depth = 0; depth < maxDepth && !frontier.isEmpty(); depth++) {
            List<ArtifactId> next = new ArrayList<>();
            for (ArtifactId current : frontier) {
                // Tenant-scoped at EVERY hop: peer relations must be same-tenant.
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
        // Contract (frozen, InMemory conformance): limit <= 0 behaves as limit = 1.
        return artifactRepository.findByContentDigest(tenantId, contentDigest, Math.max(1, limit));
    }
}
