package com.example.platform.artifact.app;

import com.example.platform.artifact.domain.Artifact;
import com.example.platform.artifact.domain.ArtifactRelation;
import com.example.platform.artifact.domain.ArtifactStatus;
import com.example.platform.shared.Ids;
import com.example.platform.shared.web.ErrorCodeRegistry;
import com.example.platform.shared.web.MediaAssetErrors;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Service for managing artifact catalog entries.
 *
 * <p>When {@link ArtifactCatalogRepository} is available (DSLContext bean present),
 * artifacts are persisted to the database. Otherwise falls back to in-memory
 * storage for backward compatibility.</p>
 */
@Service
public class ArtifactCatalogService {

    private static final Logger log = LoggerFactory.getLogger(ArtifactCatalogService.class);

    private final ArtifactCatalogRepository artifactRepository;
    private final ArtifactRelationRepository relationRepository;
    private final ErrorCodeRegistry errorCodeRegistry;
    private final boolean persistent;

    private final Map<String, Artifact> artifacts = new ConcurrentHashMap<>();
    private final Map<String, ArtifactRelation> relations = new ConcurrentHashMap<>();
    private final AtomicLong artifactSeq = new AtomicLong(0);
    private final AtomicLong relationSeq = new AtomicLong(0);

    public ArtifactCatalogService(
            @Autowired(required = false) ArtifactCatalogRepository artifactRepository,
            @Autowired(required = false) ArtifactRelationRepository relationRepository,
            ErrorCodeRegistry errorCodeRegistry) {
        this.artifactRepository = artifactRepository;
        this.relationRepository = relationRepository;
        this.errorCodeRegistry = errorCodeRegistry;
        this.persistent = artifactRepository != null;
        log.info("ArtifactCatalogService initialized (persistent={})", persistent);
    }

    public Map<String, Object> overview() {
        return Map.of(
                "module", "artifact-catalog-module",
                "status", "active",
                "description", "Artifact catalog — persistent storage of render artifacts, relations, and provenance.",
                "artifactCount", persistent ? artifactRepository.findAll().size() : artifacts.size(),
                "persistent", persistent
        );
    }

    public Artifact registerArtifact(String renderJobId, String projectId,
            String storageUri, String format, String resolution, long duration) {
        return registerArtifact(renderJobId, projectId, storageUri, format, resolution,
                duration, null, null);
    }

    public Artifact registerArtifact(String renderJobId, String projectId,
            String storageUri, String format, String resolution, long duration,
            Long sizeBytes, String checksum) {
        if (persistent) {
            String id = Ids.newId("art");
            Artifact artifact = new Artifact(id, renderJobId, projectId, storageUri,
                    format, resolution, duration, sizeBytes, checksum,
                    ArtifactStatus.ACTIVE, null, Instant.now());
            return artifactRepository.save(artifact);
        } else {
            String id = "art-" + artifactSeq.incrementAndGet();
            Artifact artifact = new Artifact(id, renderJobId, projectId, storageUri,
                    format, resolution, duration, sizeBytes, checksum,
                    ArtifactStatus.ACTIVE, null, Instant.now());
            artifacts.put(id, artifact);
            return artifact;
        }
    }

    public Optional<Artifact> findArtifact(String id) {
        if (persistent) {
            return artifactRepository.findById(id);
        }
        return Optional.ofNullable(artifacts.get(id));
    }

    public List<Artifact> listArtifacts() {
        if (persistent) {
            return artifactRepository.findAll();
        }
        return List.copyOf(artifacts.values());
    }

    public List<Artifact> listArtifactsByProject(String projectId) {
        if (persistent) {
            return artifactRepository.findByProjectId(projectId);
        }
        return artifacts.values().stream()
                .filter(a -> a.projectId().equals(projectId))
                .toList();
    }

    public List<Artifact> listArtifactsByRenderJob(String renderJobId) {
        if (persistent) {
            return artifactRepository.findByRenderJobId(renderJobId);
        }
        return artifacts.values().stream()
                .filter(a -> a.renderJobId().equals(renderJobId))
                .toList();
    }

    public ArtifactRelation relateArtifacts(String sourceId, String targetId, String relationType) {
        if (persistent) {
            if (artifactRepository.findById(sourceId).isEmpty()) {
                throw MediaAssetErrors.artifactNotFound(errorCodeRegistry, sourceId);
            }
            if (artifactRepository.findById(targetId).isEmpty()) {
                throw MediaAssetErrors.artifactNotFound(errorCodeRegistry, targetId);
            }
        } else {
            if (!artifacts.containsKey(sourceId)) {
                throw MediaAssetErrors.artifactNotFound(errorCodeRegistry, sourceId);
            }
            if (!artifacts.containsKey(targetId)) {
                throw MediaAssetErrors.artifactNotFound(errorCodeRegistry, targetId);
            }
        }
        String id = persistent ? Ids.newId("rel") : "rel-" + relationSeq.incrementAndGet();
        ArtifactRelation relation = new ArtifactRelation(id, sourceId, targetId, relationType);
        if (persistent && relationRepository != null) {
            relationRepository.save(relation);
        }
        relations.put(id, relation);
        return relation;
    }

    public List<Map<String, Object>> findRelationReferences(String artifactId) {
        if (persistent && relationRepository != null) {
            return relationRepository.findReferenceMaps(artifactId);
        }
        List<Map<String, Object>> refs = new ArrayList<>();
        for (ArtifactRelation relation : relations.values()) {
            if (artifactId.equals(relation.sourceId())) {
                refs.add(Map.of(
                        "kind", "artifact_relation",
                        "relationId", relation.id(),
                        "role", "source",
                        "peerId", relation.targetId(),
                        "relationType", relation.relationType()));
            }
            if (artifactId.equals(relation.targetId())) {
                refs.add(Map.of(
                        "kind", "artifact_relation",
                        "relationId", relation.id(),
                        "role", "target",
                        "peerId", relation.sourceId(),
                        "relationType", relation.relationType()));
            }
        }
        return refs;
    }

    public Artifact tombstoneInMemory(String artifactId) {
        Artifact existing = artifacts.get(artifactId);
        if (existing == null) {
            throw MediaAssetErrors.artifactNotFound(errorCodeRegistry, artifactId);
        }
        Artifact updated = new Artifact(
                existing.id(), existing.renderJobId(), existing.projectId(), existing.storageUri(),
                existing.format(), existing.resolution(), existing.duration(),
                existing.sizeBytes(), existing.checksum(),
                ArtifactStatus.TOMBSTONED, Instant.now(), existing.createdAt());
        artifacts.put(artifactId, updated);
        return updated;
    }

    public Artifact updateStatus(String artifactId, ArtifactStatus newStatus) {
        if (persistent) {
            return artifactRepository.updateStatus(artifactId, newStatus,
                    newStatus == ArtifactStatus.TOMBSTONED ? Instant.now() : null);
        }
        Artifact existing = artifacts.get(artifactId);
        if (existing == null) {
            throw MediaAssetErrors.artifactNotFound(errorCodeRegistry, artifactId);
        }
        Artifact updated = new Artifact(
                existing.id(), existing.renderJobId(), existing.projectId(), existing.storageUri(),
                existing.format(), existing.resolution(), existing.duration(),
                existing.sizeBytes(), existing.checksum(),
                newStatus,
                newStatus == ArtifactStatus.TOMBSTONED ? Instant.now() : existing.tombstonedAt(),
                existing.createdAt());
        artifacts.put(artifactId, updated);
        return updated;
    }
}
