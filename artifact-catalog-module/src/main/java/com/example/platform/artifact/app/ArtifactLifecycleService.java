package com.example.platform.artifact.app;

import com.example.platform.artifact.domain.Artifact;
import com.example.platform.artifact.domain.ArtifactStatus;
import com.example.platform.shared.asset.StorageUriReferenceContributor;
import com.example.platform.shared.asset.StorageUriReferenceHit;
import com.example.platform.shared.events.ArtifactTombstonedEvent;
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

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

/**
 * Tombstone and delete-reference checks for catalog {@link Artifact} rows.
 */
@Service
public class ArtifactLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(ArtifactLifecycleService.class);

    private final ArtifactCatalogRepository artifactRepository;
    private final ArtifactCatalogService catalogService;
    private final Optional<DSLContext> dsl;
    private final ErrorCodeRegistry errorCodeRegistry;
    private final ApplicationEventPublisher eventPublisher;
    private final List<StorageUriReferenceContributor> referenceContributors;

    public ArtifactLifecycleService(
            @Autowired(required = false) ArtifactCatalogRepository artifactRepository,
            ArtifactCatalogService catalogService,
            @Autowired(required = false) DSLContext dsl,
            ErrorCodeRegistry errorCodeRegistry,
            ApplicationEventPublisher eventPublisher,
            @Autowired(required = false) List<StorageUriReferenceContributor> referenceContributors) {
        this.artifactRepository = artifactRepository;
        this.catalogService = catalogService;
        this.dsl = Optional.ofNullable(dsl);
        this.errorCodeRegistry = errorCodeRegistry;
        this.eventPublisher = eventPublisher;
        this.referenceContributors = referenceContributors != null ? referenceContributors : List.of();
    }

    public DeleteCheckResult deleteCheck(String artifactId) {
        Artifact artifact = catalogService.findArtifact(artifactId)
                .orElseThrow(() -> MediaAssetErrors.artifactNotFound(errorCodeRegistry, artifactId));
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

    public Artifact tombstone(String artifactId) {
        Artifact artifact = requireActiveCatalogEntry(artifactId);
        DeleteCheckResult check = deleteCheck(artifactId);
        if (!check.deletable()) {
            throw MediaAssetErrors.artifactStillReferenced(errorCodeRegistry, artifactId);
        }
        Instant now = Instant.now();
        Artifact tombstoned;
        if (artifactRepository != null) {
            tombstoned = artifactRepository.updateStatus(artifactId, ArtifactStatus.TOMBSTONED, now);
        } else {
            tombstoned = catalogService.tombstoneInMemory(artifactId);
        }
        eventPublisher.publishEvent(new ArtifactTombstonedEvent(
                tombstoned.id(), tombstoned.projectId(), tombstoned.storageUri(), now));
        return tombstoned;
    }

    public void assertUsable(Artifact artifact) {
        if (artifact == null) {
            throw MediaAssetErrors.artifactNotFound(errorCodeRegistry, "unknown");
        }
        if (artifact.status() == ArtifactStatus.TOMBSTONED || artifact.status() == ArtifactStatus.PURGED) {
            throw MediaAssetErrors.artifactTombstoned(errorCodeRegistry, artifact.id());
        }
    }

    private Artifact requireActiveCatalogEntry(String artifactId) {
        Optional<Artifact> found = catalogService.findArtifact(artifactId);
        if (found.isEmpty()) {
            throw MediaAssetErrors.artifactNotFound(errorCodeRegistry, artifactId);
        }
        Artifact artifact = found.get();
        if (artifact.status() == ArtifactStatus.TOMBSTONED || artifact.status() == ArtifactStatus.PURGED) {
            throw MediaAssetErrors.artifactTombstoned(errorCodeRegistry, artifactId);
        }
        return artifact;
    }

    private List<Map<String, Object>> findRenderJobReferences(Artifact artifact) {
        List<Map<String, Object>> refs = new ArrayList<>();
        String uri = artifact.storageUri();
        if (uri == null || uri.isBlank()) {
            return refs;
        }
        var rows = dsl.get().select(field("id", String.class), field("artifact_uri", String.class))
                .from(table("render_job"))
                .where(field("artifact_uri").eq(uri))
                .limit(50)
                .fetch();
        for (Record row : rows) {
            String jobUri = row.get(field("artifact_uri", String.class));
            if (uri.equals(jobUri)) {
                refs.add(Map.of(
                        "kind", "render_job_output",
                        "renderJobId", row.get(field("id", String.class)),
                        "storageUri", uri));
            }
        }
        return refs;
    }

    private List<Map<String, Object>> findContributorReferences(String storageUri, String projectId) {
        List<Map<String, Object>> refs = new ArrayList<>();
        for (StorageUriReferenceContributor contributor : referenceContributors) {
            for (StorageUriReferenceHit hit : contributor.findReferences(storageUri, projectId)) {
                Map<String, Object> ref = new LinkedHashMap<>();
                ref.put("kind", hit.kind());
                ref.put("contributor", contributor.contributorId());
                ref.put("entityId", hit.entityId());
                ref.put("message", hit.message());
                if (hit.details() != null) {
                    ref.putAll(hit.details());
                }
                refs.add(ref);
            }
        }
        return refs;
    }

    public record DeleteCheckResult(
            String artifactId,
            String projectId,
            boolean deletable,
            List<Map<String, Object>> references) {}
}
