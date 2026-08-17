package com.example.platform.artifact.domain;
import com.example.platform.shared.identity.ArtifactId;

import com.example.platform.shared.digest.ContentDigest;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory implementation of {@link ArtifactQueryService} for testing and development.
 */
public class InMemoryArtifactQueryService implements ArtifactQueryService {

    private final Map<String, Artifact> artifacts = new ConcurrentHashMap<>();
    private final Map<String, List<ArtifactReplicaBinding>> replicaBindings = new ConcurrentHashMap<>();
    private final Map<String, List<ProvenanceEdge>> edgesByArtifact = new ConcurrentHashMap<>();
    private final Map<String, String> artifactTenants = new ConcurrentHashMap<>();

    public void addArtifact(Artifact artifact, ArtifactReplicaBinding binding) {
        artifacts.put(artifact.artifactId().value(), artifact);
        artifactTenants.put(artifact.artifactId().value(), artifact.tenantId());
        replicaBindings.computeIfAbsent(artifact.artifactId().value(), k -> new ArrayList<>()).add(binding);
    }

    public void addEdge(ProvenanceEdge edge) {
        edgesByArtifact.computeIfAbsent(edge.childArtifactId().value(), k -> new ArrayList<>()).add(edge);
        edgesByArtifact.computeIfAbsent(edge.parentArtifactId().value(), k -> new ArrayList<>()).add(edge);
    }

    @Override
    public Optional<Artifact> getArtifact(String tenantId, ArtifactId artifactId) {
        Artifact artifact = artifacts.get(artifactId.value());
        if (artifact != null && artifact.tenantId().equals(tenantId)) {
            return Optional.of(artifact);
        }
        return Optional.empty();
    }

    @Override
    public List<ArtifactReplicaBinding> listReplicas(String tenantId, ArtifactId artifactId) {
        Artifact artifact = artifacts.get(artifactId.value());
        if (artifact == null || !artifact.tenantId().equals(tenantId)) {
            return List.of();
        }
        return Collections.unmodifiableList(replicaBindings.getOrDefault(artifactId.value(), List.of()));
    }

    @Override
    public Optional<ArtifactReplicaBinding> findReplica(String tenantId, ArtifactId artifactId,
            com.example.platform.storage.contract.StorageReplicaId replicaId) {
        Artifact artifact = artifacts.get(artifactId.value());
        if (artifact == null || !artifact.tenantId().equals(tenantId)) {
            return Optional.empty();
        }
        return replicaBindings.getOrDefault(artifactId.value(), List.of()).stream()
                .filter(b -> b.storageReplicaId().equals(replicaId))
                .findFirst();
    }

    @Override
    public List<ArtifactId> listParents(String tenantId, ArtifactId artifactId) {
        Artifact artifact = artifacts.get(artifactId.value());
        if (artifact == null || !artifact.tenantId().equals(tenantId)) {
            return List.of();
        }
        // GCR2-CORRECTION-V1: peers must belong to the SAME tenant (malformed
        // cross-tenant relation defense — conformance with the jOOQ adapter).
        return edgesByArtifact.getOrDefault(artifactId.value(), List.of()).stream()
                .filter(e -> e.childArtifactId().value().equals(artifactId.value()))
                .map(ProvenanceEdge::parentArtifactId)
                .filter(peer -> tenantId.equals(artifactTenants.get(peer.value())))
                .distinct()
                .toList();
    }

    @Override
    public List<ArtifactId> listChildren(String tenantId, ArtifactId artifactId) {
        Artifact artifact = artifacts.get(artifactId.value());
        if (artifact == null || !artifact.tenantId().equals(tenantId)) {
            return List.of();
        }
        // GCR2-CORRECTION-V1: peers must belong to the SAME tenant.
        return edgesByArtifact.getOrDefault(artifactId.value(), List.of()).stream()
                .filter(e -> e.parentArtifactId().value().equals(artifactId.value()))
                .map(ProvenanceEdge::childArtifactId)
                .filter(peer -> tenantId.equals(artifactTenants.get(peer.value())))
                .distinct()
                .toList();
    }

    @Override
    public List<ProvenanceEdge> getDirectProvenance(String tenantId, ArtifactId artifactId) {
        Artifact artifact = artifacts.get(artifactId.value());
        if (artifact == null || !artifact.tenantId().equals(tenantId)) {
            return List.of();
        }
        // GCR2-CORRECTION-V1: peers must belong to the SAME tenant.
        return edgesByArtifact.getOrDefault(artifactId.value(), List.of()).stream()
                .filter(e -> tenantId.equals(artifactTenants.get(e.parentArtifactId().value()))
                        && tenantId.equals(artifactTenants.get(e.childArtifactId().value())))
                .toList();
    }

    @Override
    public List<ArtifactId> boundedAncestorTraversal(String tenantId, ArtifactId artifactId, int maxDepth) {
        if (maxDepth < 1) return List.of();
        Artifact artifact = artifacts.get(artifactId.value());
        if (artifact == null || !artifact.tenantId().equals(tenantId)) {
            return List.of();
        }

        Set<ArtifactId> visited = new HashSet<>();
        ArrayDeque<ArtifactId> queue = new ArrayDeque<>();
        List<ArtifactId> result = new ArrayList<>();
        queue.add(artifactId);
        visited.add(artifactId);

        int depth = 0;
        while (!queue.isEmpty() && depth < maxDepth) {
            int size = queue.size();
            for (int i = 0; i < size; i++) {
                ArtifactId current = queue.poll();
                for (ProvenanceEdge edge : edgesByArtifact.getOrDefault(current.value(), List.of())) {
                    if (edge.childArtifactId().value().equals(current.value()) &&
                            !visited.contains(edge.parentArtifactId()) &&
                            tenantId.equals(artifactTenants.get(edge.parentArtifactId().value()))) {
                        visited.add(edge.parentArtifactId());
                        result.add(edge.parentArtifactId());
                        queue.add(edge.parentArtifactId());
                    }
                }
            }
            depth++;
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public List<ArtifactId> boundedDescendantTraversal(String tenantId, ArtifactId artifactId, int maxDepth) {
        if (maxDepth < 1) return List.of();
        Artifact artifact = artifacts.get(artifactId.value());
        if (artifact == null || !artifact.tenantId().equals(tenantId)) {
            return List.of();
        }

        Set<ArtifactId> visited = new HashSet<>();
        ArrayDeque<ArtifactId> queue = new ArrayDeque<>();
        List<ArtifactId> result = new ArrayList<>();
        queue.add(artifactId);
        visited.add(artifactId);

        int depth = 0;
        while (!queue.isEmpty() && depth < maxDepth) {
            int size = queue.size();
            for (int i = 0; i < size; i++) {
                ArtifactId current = queue.poll();
                for (ProvenanceEdge edge : edgesByArtifact.getOrDefault(current.value(), List.of())) {
                    if (edge.parentArtifactId().value().equals(current.value()) &&
                            !visited.contains(edge.childArtifactId()) &&
                            tenantId.equals(artifactTenants.get(edge.childArtifactId().value()))) {
                        visited.add(edge.childArtifactId());
                        result.add(edge.childArtifactId());
                        queue.add(edge.childArtifactId());
                    }
                }
            }
            depth++;
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public List<Artifact> findByContentDigest(String tenantId, ContentDigest contentDigest, int limit) {
        return artifacts.values().stream()
                .filter(a -> a.tenantId().equals(tenantId))
                .filter(a -> a.contentDigest().matches(contentDigest))
                .limit(Math.max(1, limit))
                .toList();
    }
}
