package com.example.platform.artifact.app;

import com.example.platform.artifact.domain.Artifact;
import com.example.platform.artifact.domain.ArtifactCatalogEntry;
import com.example.platform.artifact.domain.ArtifactState;
import com.example.platform.artifact.domain.ArtifactStatus;
import com.example.platform.artifact.infrastructure.ArtifactPinRepository;
import com.example.platform.artifact.infrastructure.ArtifactRepository;
import com.example.platform.shared.asset.StorageUriReferenceContributor;
import com.example.platform.shared.asset.StorageUriReferenceHit;
import com.example.platform.shared.events.ArtifactTombstonedEvent;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.shared.web.ErrorCodeRegistry;
import com.example.platform.shared.web.MediaAssetErrors;
import com.example.platform.shared.web.PlatformException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.exception.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import static com.example.platform.typedschema.jooq.generated.tables.RenderJob.RENDER_JOB;


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

    private final ArtifactCatalogRepository artifactRepository;
    private final ArtifactCatalogService catalogService;
    private final Optional<ArtifactRepository> canonicalArtifactRepository;
    private final Optional<ArtifactPinRepository> pinRepository;
    private final Optional<DSLContext> dsl;
    private final ErrorCodeRegistry errorCodeRegistry;
    private final ApplicationEventPublisher eventPublisher;
    private final List<StorageUriReferenceContributor> referenceContributors;

    public ArtifactLifecycleService(
            @Autowired(required = false) ArtifactCatalogRepository artifactRepository,
            ArtifactCatalogService catalogService,
            @Autowired(required = false) ArtifactRepository canonicalArtifactRepository,
            @Autowired(required = false) ArtifactPinRepository pinRepository,
            @Autowired(required = false) DSLContext dsl,
            ErrorCodeRegistry errorCodeRegistry,
            ApplicationEventPublisher eventPublisher,
            @Autowired(required = false) List<StorageUriReferenceContributor> referenceContributors) {
        this.artifactRepository = artifactRepository;
        this.catalogService = catalogService;
        this.canonicalArtifactRepository = Optional.ofNullable(canonicalArtifactRepository);
        this.pinRepository = Optional.ofNullable(pinRepository);
        this.dsl = Optional.ofNullable(dsl);
        this.errorCodeRegistry = errorCodeRegistry;
        this.eventPublisher = eventPublisher;
        this.referenceContributors = referenceContributors != null ? referenceContributors : List.of();
    }

    public DeleteCheckResult deleteCheck(String artifactId) {
        ArtifactCatalogEntry artifact = catalogService.findArtifact(artifactId)
                .orElseThrow(() -> MediaAssetErrors.artifactNotFound(errorCodeRegistry, artifactId));

        // C14: historical pin protection — pinned Artifact cannot be logically deleted.
        if (pinRepository.isPresent() && pinRepository.get().isPinned(artifactId)) {
            return new DeleteCheckResult(artifactId, artifact.projectId(), false,
                    List.of(Map.of("reason", "PINNED_BY_HISTORICAL_REVISION",
                            "detail", "Artifact is protected by a historical revision pin")));
        }

        List<Map<String, Object>> references = new ArrayList<>();
        references.addAll(catalogService.findRelationReferences(artifactId));
        if (dsl.isPresent()) {
            try {
                references.addAll(findRenderJobReferences(artifact));
            } catch (DataAccessException e) {
                log.debug("render_job reference scan skipped: {}", e.getMessage());
            }
        }
        references.addAll(findContributorReferences(artifact.storageUri(), artifact.projectId()));
        boolean deletable = references.isEmpty();
        return new DeleteCheckResult(artifactId, artifact.projectId(), deletable, references);
    }

    /**
     * Replica deletion check: deleting the LAST usable replica of a PINNED
     * Artifact is rejected (PINNED_LAST_USABLE_REPLICA_DELETE_FAILS_CLOSED_V1).
     * Non-last replica deletion of pinned artifacts is conservatively rejected
     * (bounded lifecycle policy — full replica lifecycle is out of scope).
     */
    public ReplicaDeleteCheckResult replicaDeleteCheck(String artifactId, String replicaId) {
        boolean pinned = pinRepository.isPresent() && pinRepository.get().isPinned(artifactId);
        if (!pinned) {
            return new ReplicaDeleteCheckResult(artifactId, replicaId, true, "UNPINNED");
        }
        long replicaCount = canonicalArtifactRepository
                .map(r -> r.countReplicas(artifactId))
                .orElse(1L);
        if (replicaCount <= 1) {
            return new ReplicaDeleteCheckResult(artifactId, replicaId, false,
                    "PINNED_LAST_USABLE_REPLICA");
        }
        // Conservative bounded policy: pinned artifacts keep all replicas.
        return new ReplicaDeleteCheckResult(artifactId, replicaId, false,
                "PINNED_MULTI_REPLICA_CONSERVATIVE");
    }

    public ArtifactCatalogEntry tombstone(String artifactId) {
        ArtifactCatalogEntry artifact = requireActiveCatalogEntry(artifactId);
        DeleteCheckResult check = deleteCheck(artifactId);
        if (!check.deletable()) {
            throw MediaAssetErrors.artifactStillReferenced(errorCodeRegistry, artifactId);
        }
        Instant now = Instant.now();
        ArtifactCatalogEntry tombstoned;
        if (canonicalArtifactRepository.isPresent()) {
            canonicalArtifactRepository.get().updateState(artifactId, ArtifactState.DELETING,
                    java.time.LocalDateTime.ofInstant(now, java.time.ZoneOffset.UTC));
            ArtifactCatalogEntry existing = catalogService.findArtifact(artifactId).orElse(artifact);
            tombstoned = new ArtifactCatalogEntry(
                    existing.id(), existing.renderJobId(), existing.projectId(), existing.storageUri(),
                    existing.format(), existing.resolution(), existing.duration(), existing.sizeBytes(),
                    existing.checksum(), ArtifactStatus.TOMBSTONED, now, existing.createdAt());
        } else {
            tombstoned = catalogService.tombstoneInMemory(artifactId);
        }
        eventPublisher.publishEvent(new ArtifactTombstonedEvent(
                tombstoned.id(), tombstoned.projectId(), tombstoned.storageUri(), now));
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

    private ArtifactCatalogEntry requireActiveCatalogEntry(String artifactId) {
        Optional<ArtifactCatalogEntry> found = catalogService.findArtifact(artifactId);
        if (found.isEmpty()) {
            throw MediaAssetErrors.artifactNotFound(errorCodeRegistry, artifactId);
        }
        ArtifactCatalogEntry artifact = found.get();
        if (artifact.status() == ArtifactStatus.TOMBSTONED || artifact.status() == ArtifactStatus.PURGED) {
            throw MediaAssetErrors.artifactTombstoned(errorCodeRegistry, artifactId);
        }
        return artifact;
    }

    private List<Map<String, Object>> findRenderJobReferences(ArtifactCatalogEntry artifact) {
        List<Map<String, Object>> refs = new ArrayList<>();
        String uri = artifact.storageUri();
        if (uri == null || uri.isBlank()) {
            return refs;
        }
        var rows = dsl.get().select(RENDER_JOB.ID, RENDER_JOB.ARTIFACT_URI)
                .from(RENDER_JOB)
                .where(RENDER_JOB.ARTIFACT_URI.eq(uri))
                .fetch();
        for (Record row : rows) {
            refs.add(Map.of(
                    "type", "RENDER_JOB",
                    "id", row.get(RENDER_JOB.ID),
                    "detail", row.get(RENDER_JOB.ARTIFACT_URI)));
        }
        return refs;
    }

    private List<Map<String, Object>> findContributorReferences(String storageUri, String projectId) {
        List<Map<String, Object>> refs = new ArrayList<>();
        for (StorageUriReferenceContributor contributor : referenceContributors) {
            List<StorageUriReferenceHit> hits = contributor.findReferences(storageUri, projectId);
            for (StorageUriReferenceHit hit : hits) {
                refs.add(Map.of(
                        "type", hit.kind(),
                        "id", hit.entityId(),
                        "detail", hit.message()));
            }
        }
        return refs;
    }

    public record DeleteCheckResult(String artifactId, String projectId, boolean deletable,
            List<Map<String, Object>> references) {}

    public record ReplicaDeleteCheckResult(String artifactId, String replicaId, boolean deletable,
            String reason) {}
}
