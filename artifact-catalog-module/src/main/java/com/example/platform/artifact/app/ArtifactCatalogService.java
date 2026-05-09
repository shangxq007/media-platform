package com.example.platform.artifact.app;

import com.example.platform.artifact.domain.Artifact;
import com.example.platform.artifact.domain.ArtifactRelation;
import com.example.platform.artifact.domain.ArtifactStatus;
import com.example.platform.shared.Ids;
import java.time.Instant;
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
    private final boolean persistent;

    private final Map<String, Artifact> artifacts = new ConcurrentHashMap<>();
    private final Map<String, ArtifactRelation> relations = new ConcurrentHashMap<>();
    private final AtomicLong artifactSeq = new AtomicLong(0);
    private final AtomicLong relationSeq = new AtomicLong(0);

    public ArtifactCatalogService(@Autowired(required = false) ArtifactCatalogRepository artifactRepository) {
        this.artifactRepository = artifactRepository;
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
        if (persistent) {
            String id = Ids.newId("art");
            Artifact artifact = new Artifact(id, renderJobId, projectId, storageUri,
                    format, resolution, duration, Instant.now());
            return artifactRepository.save(artifact);
        } else {
            String id = "art-" + artifactSeq.incrementAndGet();
            Artifact artifact = new Artifact(id, renderJobId, projectId, storageUri,
                    format, resolution, duration, Instant.now());
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
                throw new IllegalArgumentException("Source artifact not found: " + sourceId);
            }
            if (artifactRepository.findById(targetId).isEmpty()) {
                throw new IllegalArgumentException("Target artifact not found: " + targetId);
            }
        } else {
            if (!artifacts.containsKey(sourceId)) {
                throw new IllegalArgumentException("Source artifact not found: " + sourceId);
            }
            if (!artifacts.containsKey(targetId)) {
                throw new IllegalArgumentException("Target artifact not found: " + targetId);
            }
        }
        if (persistent) {
            String id = Ids.newId("rel");
            return new ArtifactRelation(id, sourceId, targetId, relationType);
        } else {
            String id = "rel-" + relationSeq.incrementAndGet();
            ArtifactRelation relation = new ArtifactRelation(id, sourceId, targetId, relationType);
            relations.put(id, relation);
            return relation;
        }
    }

    public Artifact updateStatus(String artifactId, ArtifactStatus newStatus) {
        if (persistent) {
            Artifact existing = artifactRepository.findById(artifactId)
                    .orElseThrow(() -> new IllegalArgumentException("Artifact not found: " + artifactId));
            return existing;
        }
        Artifact existing = artifacts.get(artifactId);
        if (existing == null) {
            throw new IllegalArgumentException("Artifact not found: " + artifactId);
        }
        return existing;
    }
}
