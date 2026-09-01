package com.example.platform.artifact.app;

import com.example.platform.artifact.domain.Artifact;
import com.example.platform.artifact.domain.ArtifactCatalogEntry;
import com.example.platform.artifact.domain.ArtifactState;
import com.example.platform.artifact.domain.ArtifactStatus;
import com.example.platform.artifact.infrastructure.ArtifactPinRepository;
import com.example.platform.artifact.infrastructure.ArtifactRepository;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.shared.web.ErrorCodeRegistry;
import com.example.platform.shared.web.MediaAssetErrors;
import com.example.platform.shared.web.PlatformException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;


/**
 * GCR-2 (ARTIFACT_AUTHORITY_CONTRACT_V1 C13/C14): Artifact-owned lifecycle
 * service. Delete checks FAIL CLOSED on historical pin protection
 * (PINNED_ARTIFACT_LOGICAL_DELETE_FAILS_CLOSED_V1): a pinned Artifact cannot be
 * tombstoned/deleted, and its last usable replica cannot be deleted. Render
 * TimelineAssetGc* remain timeline-side (representation-level assetRegistry)
 * and are NOT Artifact lifecycle authority.
 */
@Service
public class ArtifactLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(ArtifactLifecycleService.class);

    private final ArtifactCatalogService catalogService;
    private final ArtifactRepository canonicalArtifactRepository;
    private final ArtifactPinRepository pinRepository;
    private final ErrorCodeRegistry errorCodeRegistry;

    public ArtifactLifecycleService(
            ArtifactCatalogService catalogService,
            ArtifactRepository canonicalArtifactRepository,
            ArtifactPinRepository pinRepository,
            ErrorCodeRegistry errorCodeRegistry) {
        this.catalogService = catalogService;
        this.canonicalArtifactRepository = canonicalArtifactRepository;
        this.pinRepository = pinRepository;
        this.errorCodeRegistry = errorCodeRegistry;
    }

    public DeleteCheckResult deleteCheck(String tenantId, String artifactId) {
        requireTenantId(tenantId);
        ArtifactCatalogEntry artifact = catalogService.findArtifact(tenantId, artifactId)
                .orElseThrow(() -> MediaAssetErrors.artifactNotFound(errorCodeRegistry, artifactId));

        // C14: historical pin protection — pinned Artifact cannot be logically deleted.
        if (pinRepository.isPinned(tenantId, artifactId)) {
            return new DeleteCheckResult(artifactId, artifact.projectId(), false,
                    List.of(Map.of("reason", "PINNED_BY_HISTORICAL_REVISION",
                            "detail", "Artifact is protected by a historical revision pin")));
        }

        List<Map<String, Object>> references = new ArrayList<>();
        references.addAll(catalogService.findRelationReferences(tenantId, artifactId));
        boolean deletable = references.isEmpty();
        return new DeleteCheckResult(artifactId, artifact.projectId(), deletable, references);
    }

    /**
     * Replica deletion check: deleting the LAST usable replica of a PINNED
     * Artifact is rejected (PINNED_LAST_USABLE_REPLICA_DELETE_FAILS_CLOSED_V1).
     * Non-last replica deletion of pinned artifacts is conservatively rejected
     * (bounded lifecycle policy — full replica lifecycle is out of scope).
     */
    public ReplicaDeleteCheckResult replicaDeleteCheck(String tenantId, String artifactId, String replicaId) {
        requireTenantId(tenantId);
        if (catalogService.findArtifact(tenantId, artifactId).isEmpty()) {
            throw MediaAssetErrors.artifactNotFound(errorCodeRegistry, artifactId);
        }
        boolean pinned = pinRepository.isPinned(tenantId, artifactId);
        if (!pinned) {
            return new ReplicaDeleteCheckResult(artifactId, replicaId, true, "UNPINNED");
        }
        long replicaCount = canonicalArtifactRepository.countReplicas(tenantId, artifactId);
        if (replicaCount <= 1) {
            return new ReplicaDeleteCheckResult(artifactId, replicaId, false,
                    "PINNED_LAST_USABLE_REPLICA");
        }
        // Conservative bounded policy: pinned artifacts keep all replicas.
        return new ReplicaDeleteCheckResult(artifactId, replicaId, false,
                "PINNED_MULTI_REPLICA_CONSERVATIVE");
    }

    public ArtifactCatalogEntry tombstone(String tenantId, String artifactId) {
        requireTenantId(tenantId);
        ArtifactCatalogEntry artifact = requireActiveCatalogEntry(tenantId, artifactId);
        DeleteCheckResult check = deleteCheck(tenantId, artifactId);
        if (!check.deletable()) {
            throw MediaAssetErrors.artifactStillReferenced(errorCodeRegistry, artifactId);
        }
        Instant now = Instant.now();
        ArtifactCatalogEntry tombstoned;
        if (!canonicalArtifactRepository.updateState(tenantId, artifactId, ArtifactState.DELETING,
                java.time.LocalDateTime.ofInstant(now, java.time.ZoneOffset.UTC))) {
            throw MediaAssetErrors.artifactNotFound(errorCodeRegistry, artifactId);
        }
        ArtifactCatalogEntry existing = catalogService.findArtifact(tenantId, artifactId).orElse(artifact);
        tombstoned = new ArtifactCatalogEntry(
                existing.id(), existing.renderJobId(), existing.projectId(),
                existing.format(), existing.resolution(), existing.duration(), existing.sizeBytes(),
                existing.checksum(), ArtifactStatus.TOMBSTONED, now, existing.createdAt());
        return tombstoned;
    }

    public void assertUsable(ArtifactCatalogEntry artifact) {
        if (artifact == null) {
            throw MediaAssetErrors.artifactNotFound(errorCodeRegistry, "unknown");
        }
        if (artifact.status() == ArtifactStatus.TOMBSTONED || artifact.status() == ArtifactStatus.PURGED) {
            throw MediaAssetErrors.artifactTombstoned(errorCodeRegistry, artifact.id());
        }
    }

    private ArtifactCatalogEntry requireActiveCatalogEntry(String tenantId, String artifactId) {
        java.util.Optional<ArtifactCatalogEntry> found = catalogService.findArtifact(tenantId, artifactId);
        if (found.isEmpty()) {
            throw MediaAssetErrors.artifactNotFound(errorCodeRegistry, artifactId);
        }
        ArtifactCatalogEntry artifact = found.get();
        if (artifact.status() == ArtifactStatus.TOMBSTONED || artifact.status() == ArtifactStatus.PURGED) {
            throw MediaAssetErrors.artifactTombstoned(errorCodeRegistry, artifactId);
        }
        return artifact;
    }

    private static void requireTenantId(String tenantId) {
        if (tenantId == null || tenantId.isBlank() || "*".equals(tenantId)) {
            throw new IllegalArgumentException("explicit tenantId is required");
        }
    }

    public record DeleteCheckResult(String artifactId, String projectId, boolean deletable,
            List<Map<String, Object>> references) {}

    public record ReplicaDeleteCheckResult(String artifactId, String replicaId, boolean deletable,
            String reason) {}
}
