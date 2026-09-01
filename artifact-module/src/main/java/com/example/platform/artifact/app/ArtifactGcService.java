package com.example.platform.artifact.app;

import com.example.platform.artifact.domain.Artifact;
import com.example.platform.artifact.infrastructure.ArtifactRepository;
import com.example.platform.artifact.infrastructure.ArtifactGcProperties;
import com.example.platform.shared.audit.AuditPort;
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
 * GCR-2 (ARTIFACT_AUTHORITY_CONTRACT_V1 C13/C14): Artifact-owned GC.
 *
 * <p>Purges tombstoned canonical Artifacts (state DELETING past retention) by
 * marking them DELETED. Every candidate passes {@link ArtifactLifecycleService}
 * deleteCheck, which FAILS CLOSED on historical pin protection — a pinned
 * Artifact (or its last usable replica) is never GC'd
 * (HISTORICAL_PIN_GC_BYPASS_COUNT = 0). Physical placement deletion remains
 * exclusively behind a Storage-owned lifecycle boundary.</p>
 */
@Service
public class ArtifactGcService {

    private static final Logger log = LoggerFactory.getLogger(ArtifactGcService.class);

    private final Optional<ArtifactRepository> artifactRepository;
    private final ArtifactLifecycleService lifecycleService;
    private final ArtifactGcProperties properties;
    private final AuditPort auditPort;

    public ArtifactGcService(
            @Autowired(required = false) ArtifactRepository artifactRepository,
            ArtifactLifecycleService lifecycleService,
            ArtifactGcProperties properties,
            @Autowired(required = false) AuditPort auditPort) {
        this.artifactRepository = Optional.ofNullable(artifactRepository);
        this.lifecycleService = lifecycleService;
        this.properties = properties;
        this.auditPort = auditPort;
    }

    public GcResult runGc(String tenantId, int retentionDays) {
        return runGc(tenantId, retentionDays, false, properties.getBatchSize());
    }

    public GcResult runGc(String tenantId, int retentionDays, boolean dryRun, int limit) {
        requireTenantId(tenantId);
        if (artifactRepository.isEmpty()) {
            return new GcResult(0, 0, 0, 0, List.of(), List.of("persistent catalog unavailable"));
        }

        Instant cutoff = Instant.now().minus(Math.max(1, retentionDays), ChronoUnit.DAYS);
        List<Artifact> candidates = artifactRepository.get().findTombstonedBefore(tenantId, cutoff);
        int scanned = candidates.size();
        int purged = 0;
        int skipped = 0;
        int failed = 0;
        List<String> actions = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        int effectiveLimit = limit > 0 ? limit : Math.max(1, properties.getBatchSize());

        for (Artifact artifact : candidates.stream().limit(effectiveLimit).toList()) {
            try {
                var check = lifecycleService.deleteCheck(tenantId, artifact.artifactId().value());
                if (!check.deletable()) {
                    skipped++;
                    actions.add("SKIP " + artifact.artifactId().value() + " (" + check.references() + ")");
                    continue;
                }
                if (dryRun) {
                    purged++;
                    actions.add("WOULD_PURGE " + artifact.artifactId().value());
                    log.info("[dry-run] Would purge artifact id={}", artifact.artifactId().value());
                } else {
                    if (!artifactRepository.get().markPurged(tenantId, artifact.artifactId().value())) {
                        throw new IllegalStateException("Artifact ownership changed before purge");
                    }
                    purged++;
                    actions.add("PURGED " + artifact.artifactId().value());
                    log.info("Purged artifact id={}", artifact.artifactId().value());
                }
            } catch (Exception e) {
                failed++;
                skipped++;
                actions.add("FAILED " + artifact.artifactId().value() + ": " + e.getMessage());
                errors.add(artifact.artifactId().value() + ": " + e.getMessage());
                log.warn("Artifact GC failed for {}: {}", artifact.artifactId().value(), e.getMessage());
            }
        }

        GcResult result = new GcResult(scanned, purged, skipped, failed, actions, errors);
        recordGcAudit(result, dryRun, retentionDays);
        return result;
    }

    private static void requireTenantId(String tenantId) {
        if (tenantId == null || tenantId.isBlank() || "*".equals(tenantId)) {
            throw new IllegalArgumentException("explicit tenantId is required");
        }
    }

    @SuppressWarnings("unchecked")
    private void recordGcAudit(GcResult result, boolean dryRun, int retentionDays) {
        try {
            if (auditPort != null) {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("action", dryRun ? "ARTIFACT_GC_DRY_RUN" : "ARTIFACT_GC");
                payload.put("scanned", result.scanned());
                payload.put("purged", result.purged());
                payload.put("skipped", result.skipped());
                payload.put("failed", result.failed());
                payload.put("retentionDays", retentionDays);
                payload.put("dryRun", dryRun);
                auditPort.record("SYSTEM", "ARTIFACT_GC", "ARTIFACT",
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
