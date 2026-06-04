package com.example.platform.artifact.app;

import com.example.platform.artifact.domain.Artifact;
import com.example.platform.artifact.domain.ArtifactStatus;
import com.example.platform.artifact.infrastructure.ArtifactGcProperties;
import com.example.platform.shared.audit.AuditPort;
import com.example.platform.storage.domain.BlobStorage;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Purges tombstoned catalog artifacts: deletes blob (when supported) and marks {@link ArtifactStatus#PURGED}.
 *
 * <p>Supports dry-run mode for safe preview of what would be cleaned up.
 */
@Service
public class ArtifactGcService {

    private static final Logger log = LoggerFactory.getLogger(ArtifactGcService.class);

    private final ArtifactCatalogRepository artifactRepository;
    private final ArtifactLifecycleService lifecycleService;
    private final Optional<BlobStorage> blobStorage;
    private final ArtifactGcProperties properties;
    private AuditPort auditPort;

    public ArtifactGcService(
            @Autowired(required = false) ArtifactCatalogRepository artifactRepository,
            ArtifactLifecycleService lifecycleService,
            @Autowired(required = false) BlobStorage blobStorage,
            ArtifactGcProperties properties) {
        this.artifactRepository = artifactRepository;
        this.lifecycleService = lifecycleService;
        this.blobStorage = Optional.ofNullable(blobStorage);
        this.properties = properties;
    }

    @Autowired(required = false)
    public void setAuditPort(AuditPort auditPort) {
        this.auditPort = auditPort;
    }

    public GcResult runGc() {
        return runGc(properties.getRetentionDays(), false, properties.getBatchSize());
    }

    public GcResult runGc(int retentionDays) {
        return runGc(retentionDays, false, properties.getBatchSize());
    }

    /**
     * Run garbage collection on tombstoned artifacts.
     *
     * @param retentionDays only tombstoned before this many days ago
     * @param dryRun if true, report what would be deleted without actually deleting
     * @param limit  max number of artifacts to process (0 = use config batchSize)
     */
    @SuppressWarnings("unchecked")
    public GcResult runGc(int retentionDays, boolean dryRun, int limit) {
        if (artifactRepository == null) {
            return new GcResult(0, 0, 0, 0, List.of(), List.of("persistent catalog unavailable"));
        }

        Instant cutoff = Instant.now().minus(Math.max(1, retentionDays), ChronoUnit.DAYS);
        List<Artifact> candidates = artifactRepository.findTombstonedBefore(cutoff);
        int scanned = candidates.size();
        int purged = 0;
        int skipped = 0;
        int failed = 0;
        List<String> actions = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        int effectiveLimit = limit > 0 ? limit : Math.max(1, properties.getBatchSize());

        for (Artifact artifact : candidates.stream().limit(effectiveLimit).toList()) {
            try {
                var check = lifecycleService.deleteCheck(artifact.id());
                if (!check.deletable()) {
                    skipped++;
                    actions.add("SKIP " + artifact.id() + " (not deletable)");
                    continue;
                }
                if (dryRun) {
                    purged++;
                    actions.add("WOULD_PURGE " + artifact.id() + " storageUri=" + artifact.storageUri());
                    log.info("[dry-run] Would purge artifact id={} uri={}", artifact.id(), artifact.storageUri());
                } else {
                    deleteBlobIfPresent(artifact.storageUri());
                    artifactRepository.updateStatus(artifact.id(), ArtifactStatus.PURGED, artifact.tombstonedAt());
                    purged++;
                    actions.add("PURGED " + artifact.id());
                    log.info("Purged artifact id={} uri={}", artifact.id(), artifact.storageUri());
                }
            } catch (Exception e) {
                failed++;
                skipped++;
                actions.add("FAILED " + artifact.id() + ": " + e.getMessage());
                errors.add(artifact.id() + ": " + e.getMessage());
                log.warn("Artifact GC failed for {}: {}", artifact.id(), e.getMessage());
            }
        }

        GcResult result = new GcResult(scanned, purged, skipped, failed, actions, errors);
        recordGcAudit(result, dryRun, retentionDays);
        return result;
    }

    private void deleteBlobIfPresent(String storageUri) {
        if (storageUri == null || storageUri.isBlank() || blobStorage.isEmpty()) {
            return;
        }
        blobStorage.get().deleteStorageUri(storageUri);
    }

    @SuppressWarnings("unchecked")
    private void recordGcAudit(GcResult result, boolean dryRun, int retentionDays) {
        try {
            if (auditPort != null) {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("action", dryRun ? "ARTIFACT_BLOB_GC_DRY_RUN" : "ARTIFACT_BLOB_GC");
                payload.put("scanned", result.scanned());
                payload.put("purged", result.purged());
                payload.put("skipped", result.skipped());
                payload.put("failed", result.failed());
                payload.put("retentionDays", retentionDays);
                payload.put("dryRun", dryRun);
                // Never include storageUri in audit
                auditPort.record("SYSTEM", "ARTIFACT_BLOB_GC", "ARTIFACT_CATALOG",
                        "artifact", "gc", payload);
            }
        } catch (Exception e) {
            log.warn("Failed to record GC audit: {}", e.getMessage());
        }
    }

    public record GcResult(
            int scanned,
            int purged,
            int skipped,
            int failed,
            List<String> actions,
            List<String> errors
    ) {}
}
