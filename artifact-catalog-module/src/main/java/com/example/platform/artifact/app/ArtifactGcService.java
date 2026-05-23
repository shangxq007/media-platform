package com.example.platform.artifact.app;

import com.example.platform.artifact.domain.Artifact;
import com.example.platform.artifact.domain.ArtifactStatus;
import com.example.platform.artifact.infrastructure.ArtifactGcProperties;
import com.example.platform.storage.domain.BlobStorage;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Purges tombstoned catalog artifacts: deletes blob (when supported) and marks {@link ArtifactStatus#PURGED}.
 */
@Service
public class ArtifactGcService {

    private static final Logger log = LoggerFactory.getLogger(ArtifactGcService.class);

    private final ArtifactCatalogRepository artifactRepository;
    private final ArtifactLifecycleService lifecycleService;
    private final Optional<BlobStorage> blobStorage;
    private final ArtifactGcProperties properties;

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

    public GcResult runGc() {
        return runGc(properties.getRetentionDays());
    }

    public GcResult runGc(int retentionDays) {
        if (artifactRepository == null) {
            return new GcResult(0, 0, 0, List.of("persistent catalog unavailable"));
        }
        Instant cutoff = Instant.now().minus(Math.max(1, retentionDays), ChronoUnit.DAYS);
        List<Artifact> candidates = artifactRepository.findTombstonedBefore(cutoff);
        int scanned = candidates.size();
        int purged = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();
        int limit = Math.max(1, properties.getBatchSize());
        for (Artifact artifact : candidates.stream().limit(limit).toList()) {
            try {
                var check = lifecycleService.deleteCheck(artifact.id());
                if (!check.deletable()) {
                    skipped++;
                    continue;
                }
                deleteBlobIfPresent(artifact.storageUri());
                artifactRepository.updateStatus(artifact.id(), ArtifactStatus.PURGED, artifact.tombstonedAt());
                purged++;
                log.info("Purged artifact id={} uri={}", artifact.id(), artifact.storageUri());
            } catch (Exception e) {
                skipped++;
                errors.add(artifact.id() + ": " + e.getMessage());
                log.warn("Artifact GC failed for {}: {}", artifact.id(), e.getMessage());
            }
        }
        return new GcResult(scanned, purged, skipped, errors);
    }

    private void deleteBlobIfPresent(String storageUri) {
        if (storageUri == null || storageUri.isBlank() || blobStorage.isEmpty()) {
            return;
        }
        blobStorage.get().deleteStorageUri(storageUri);
    }

    public record GcResult(int scanned, int purged, int skipped, List<String> errors) {}
}
