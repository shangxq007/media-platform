package com.example.platform.artifact.app;

import com.example.platform.artifact.domain.ArtifactCatalogEntry;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    public ArtifactCatalogService(
            ArtifactCatalogRepository artifactRepository,
            ArtifactRelationRepository relationRepository) {
        this.artifactRepository = artifactRepository;
        this.relationRepository = relationRepository;
        log.info("ArtifactCatalogService initialized as a read-only lifecycle projection");
    }

    public Map<String, Object> overview() {
        return Map.of(
                "authority", "artifact",
                "capability", "catalog",
                "status", "active",
                "description", "ArtifactCatalogEntry projection over canonical Artifact persistence.",
                "artifactCount", artifactRepository.countAll(),
                "persistent", true);
    }

    public Optional<ArtifactCatalogEntry> findArtifact(String tenantId, String id) {
        return artifactRepository.findById(tenantId, id);
    }

    public List<Map<String, Object>> findRelationReferences(String tenantId, String artifactId) {
        return relationRepository.findReferenceMapsScopedToTenant(tenantId, artifactId);
    }
}
