package com.example.platform.artifact.app;

import com.example.platform.artifact.domain.ArtifactCatalogEntry;
import com.example.platform.artifact.domain.ArtifactRelation;
import com.example.platform.shared.Ids;
import com.example.platform.shared.web.ErrorCodeRegistry;
import com.example.platform.shared.web.MediaAssetErrors;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * GCR-2 (ARTIFACT_AUTHORITY_CONTRACT_V1 C6/C16): Artifact catalog is a
 * read-only lifecycle projection. Canonical creation routes directly through
 * the typed commit service; this projection has no registration or fallback
 * storage and cannot become a second Artifact authority.
 */
@Service
public class ArtifactCatalogService {

    private static final Logger log = LoggerFactory.getLogger(ArtifactCatalogService.class);

    private final ArtifactCatalogRepository artifactRepository;
    private final ArtifactRelationRepository relationRepository;
    private final ErrorCodeRegistry errorCodeRegistry;
    private final AtomicLong relationSeq = new AtomicLong(0);

    public ArtifactCatalogService(
            @Autowired(required = false) ArtifactCatalogRepository artifactRepository,
            @Autowired(required = false) ArtifactRelationRepository relationRepository,
            ErrorCodeRegistry errorCodeRegistry) {
        this.artifactRepository = artifactRepository;
        this.relationRepository = relationRepository;
        this.errorCodeRegistry = errorCodeRegistry;
        log.info("ArtifactCatalogService initialized as a read-only lifecycle projection");
    }

    public Map<String, Object> overview() {
        return Map.of(
                "authority", "artifact",
                "capability", "catalog",
                "status", "active",
                "description", "ArtifactCatalogEntry projection over canonical Artifact persistence.",
                "artifactCount", artifactRepository != null ? artifactRepository.countAll() : 0,
                "persistent", artifactRepository != null);
    }

    public Optional<ArtifactCatalogEntry> findArtifact(String id) {
        return artifactRepository == null ? Optional.empty() : artifactRepository.findById(id);
    }

    public ArtifactRelation relateArtifacts(String sourceId, String targetId, String relationType) {
        String id = Ids.newId("rel");
        ArtifactRelation relation = new ArtifactRelation(id, sourceId, targetId, relationType);
        if (relationRepository == null) {
            throw new IllegalStateException("Artifact relation repository unavailable");
        }
        relationRepository.save(relation);
        return relation;
    }

    public List<Map<String, Object>> findRelationReferences(String artifactId) {
        return relationRepository == null ? List.of() : relationRepository.findReferenceMaps(artifactId);
    }
}
