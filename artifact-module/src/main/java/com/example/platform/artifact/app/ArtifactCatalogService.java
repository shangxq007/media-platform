package com.example.platform.artifact.app;

import com.example.platform.artifact.domain.ArtifactCatalogEntry;
import com.example.platform.artifact.domain.ArtifactCommitRequest;
import com.example.platform.artifact.domain.ArtifactCommitService;
import com.example.platform.artifact.domain.ArtifactKind;
import com.example.platform.artifact.domain.ArtifactMediaType;
import com.example.platform.artifact.domain.ArtifactRelation;
import com.example.platform.artifact.domain.ArtifactStatus;
import com.example.platform.artifact.domain.ReplicaRole;
import com.example.platform.shared.Ids;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.shared.web.ErrorCodeRegistry;
import com.example.platform.shared.web.MediaAssetErrors;
import com.example.platform.storage.contract.StorageObjectId;
import com.example.platform.storage.contract.StorageProviderId;
import com.example.platform.storage.contract.StorageReplicaId;
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
 * GCR-2 (ARTIFACT_AUTHORITY_CONTRACT_V1 C6/C16): Artifact catalog is a
 * PROJECTION / read model. Canonical Artifact creation routes through
 * {@link ArtifactCommitService} (single write authority); the catalog never
 * writes canonical Artifact truth, never owns identity/lifecycle/replicas.
 *
 * <p>Registration paths that carry a valid SHA-256 checksum commit a canonical
 * Artifact via the commit service (persistent). Registration without a content
 * checksum is kept as an in-memory projection only (no canonical row without a
 * content-integrity assertion). Catalog rebuild/delete cannot mutate canonical
 * identity.</p>
 */
@Service
public class ArtifactCatalogService {

    private static final Logger log = LoggerFactory.getLogger(ArtifactCatalogService.class);

    private final ArtifactCatalogRepository artifactRepository;
    private final ArtifactRelationRepository relationRepository;
    private final Optional<ArtifactCommitService> commitService;
    private final ErrorCodeRegistry errorCodeRegistry;
    private final boolean persistent;

    private final Map<String, ArtifactCatalogEntry> artifacts = new ConcurrentHashMap<>();
    private final Map<String, ArtifactRelation> relations = new ConcurrentHashMap<>();
    private final AtomicLong artifactSeq = new AtomicLong(0);
    private final AtomicLong relationSeq = new AtomicLong(0);

    public ArtifactCatalogService(
            @Autowired(required = false) ArtifactCatalogRepository artifactRepository,
            @Autowired(required = false) ArtifactRelationRepository relationRepository,
            @Autowired(required = false) ArtifactCommitService commitService,
            ErrorCodeRegistry errorCodeRegistry) {
        this.artifactRepository = artifactRepository;
        this.relationRepository = relationRepository;
        this.commitService = Optional.ofNullable(commitService);
        this.errorCodeRegistry = errorCodeRegistry;
        this.persistent = artifactRepository != null && this.commitService.isPresent();
        log.info("ArtifactCatalogService initialized (persistent={})", persistent);
    }

    public Map<String, Object> overview() {
        return Map.of(
                "authority", "artifact",
                "capability", "catalog",
                "status", "active",
                "description", "ArtifactCatalogEntry projection over canonical Artifact persistence.",
                "artifactCount", persistent ? artifactRepository.findAll().size() : artifacts.size(),
                "persistent", persistent);
    }

    /**
     * Registers an artifact into the catalog projection. When a valid SHA-256
     * checksum is supplied the canonical Artifact is committed via
     * {@link ArtifactCommitService}; otherwise the entry is kept in the
     * in-memory projection only.
     */
    public ArtifactCatalogEntry registerArtifact(String renderJobId, String projectId,
            String storageUri, String format, String resolution, long duration) {
        return registerArtifact(renderJobId, projectId, storageUri, format, resolution,
                duration, null, null);
    }

    public ArtifactCatalogEntry registerArtifact(String renderJobId, String projectId,
            String storageUri, String format, String resolution, long duration,
            Long sizeBytes, String checksum) {
        String id = Ids.newId("art");
        ArtifactCatalogEntry entry = new ArtifactCatalogEntry(
                id, renderJobId, projectId, storageUri, format, resolution, duration,
                sizeBytes, checksum, ArtifactStatus.ACTIVE, null, Instant.now());

        if (persistent && checksum != null && checksum.matches("[0-9a-fA-F]{64}")) {
            try {
                commitService.get().commit(new ArtifactCommitRequest(
                        new ArtifactId(id),
                        "system",
                        ContentDigest.sha256(checksum),
                        sizeBytes != null ? sizeBytes : 0L,
                        mediaTypeFrom(format),
                        ArtifactKind.RENDER_MASTER,
                        1,
                        new StorageObjectId(storageUri != null ? storageUri : id),
                        new StorageReplicaId("replica-1"),
                        new StorageProviderId("local"),
                        ReplicaRole.PRIMARY,
                        "default",
                        id,
                        List.of(),
                        Instant.now(),
                        Instant.now(),
                        renderJobId,
                        projectId));
            } catch (Exception e) {
                log.warn("Catalog registration skipped canonical commit for {}: {}", id, e.getMessage());
            }
        } else {
            artifacts.put(id, entry);
        }
        return entry;
    }

    public Optional<ArtifactCatalogEntry> findArtifact(String id) {
        if (persistent) {
            return artifactRepository.findById(id);
        }
        return Optional.ofNullable(artifacts.get(id));
    }

    public List<ArtifactCatalogEntry> listArtifacts() {
        if (persistent) {
            return artifactRepository.findAll();
        }
        return new ArrayList<>(artifacts.values());
    }

    public List<ArtifactCatalogEntry> listArtifactsByProject(String projectId) {
        if (persistent) {
            return artifactRepository.findByProjectId(projectId);
        }
        return artifacts.values().stream()
                .filter(a -> projectId.equals(a.projectId()))
                .toList();
    }

    public List<ArtifactCatalogEntry> listArtifactsByRenderJob(String renderJobId) {
        if (persistent) {
            return artifactRepository.findAll().stream()
                    .filter(a -> renderJobId.equals(a.renderJobId()))
                    .toList();
        }
        return artifacts.values().stream()
                .filter(a -> renderJobId.equals(a.renderJobId()))
                .toList();
    }

    public ArtifactRelation relateArtifacts(String sourceId, String targetId, String relationType) {
        String id = Ids.newId("rel");
        ArtifactRelation relation = new ArtifactRelation(id, sourceId, targetId, relationType);
        if (persistent) {
            relationRepository.save(relation);
        } else {
            relations.put(id, relation);
        }
        return relation;
    }

    public List<Map<String, Object>> findRelationReferences(String artifactId) {
        if (persistent) {
            return relationRepository.findReferenceMaps(artifactId);
        }
        return relations.values().stream()
                .filter(r -> artifactId.equals(r.sourceId()) || artifactId.equals(r.targetId()))
                .map(r -> Map.<String, Object>of(
                        "relationId", r.id(),
                        "sourceId", r.sourceId(),
                        "targetId", r.targetId(),
                        "relationType", r.relationType()))
                .toList();
    }

    public ArtifactCatalogEntry tombstoneInMemory(String artifactId) {
        ArtifactCatalogEntry existing = artifacts.get(artifactId);
        if (existing == null) {
            return null;
        }
        ArtifactCatalogEntry tombstoned = new ArtifactCatalogEntry(
                existing.id(), existing.renderJobId(), existing.projectId(), existing.storageUri(),
                existing.format(), existing.resolution(), existing.duration(), existing.sizeBytes(),
                existing.checksum(), ArtifactStatus.TOMBSTONED, Instant.now(), existing.createdAt());
        artifacts.put(artifactId, tombstoned);
        return tombstoned;
    }

    private static ArtifactMediaType mediaTypeFrom(String format) {
        if (format == null) {
            return ArtifactMediaType.VIDEO;
        }
        String f = format.toLowerCase();
        if (f.contains("mp4") || f.contains("mov") || f.contains("webm") || f.contains("mkv")) {
            return ArtifactMediaType.VIDEO;
        }
        if (f.contains("mp3") || f.contains("wav") || f.contains("aac") || f.contains("flac")) {
            return ArtifactMediaType.AUDIO;
        }
        if (f.contains("png") || f.contains("jpg") || f.contains("jpeg") || f.contains("webp")) {
            return ArtifactMediaType.IMAGE;
        }
        return ArtifactMediaType.VIDEO;
    }
}
