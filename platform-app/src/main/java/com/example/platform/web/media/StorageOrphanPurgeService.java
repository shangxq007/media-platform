package com.example.platform.web.media;

import com.example.platform.shared.web.ErrorCodeRegistry;
import com.example.platform.shared.web.MediaAssetErrors;
import com.example.platform.storage.domain.BlobStorage;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Deletes storage objects flagged as orphans (AST-005) after approval token validation and re-scan guard.
 */
@Service
public class StorageOrphanPurgeService {

    private static final Logger log = LoggerFactory.getLogger(StorageOrphanPurgeService.class);

    private final StorageBucketOrphanScanner orphanScanner;
    private final KnownStorageUriIndexService uriIndexService;
    private final BlobStorage blobStorage;
    private final StorageOrphanPurgeProperties properties;
    private final ErrorCodeRegistry errorCodeRegistry;

    public StorageOrphanPurgeService(
            StorageBucketOrphanScanner orphanScanner,
            KnownStorageUriIndexService uriIndexService,
            BlobStorage blobStorage,
            StorageOrphanPurgeProperties properties,
            ErrorCodeRegistry errorCodeRegistry) {
        this.orphanScanner = orphanScanner;
        this.uriIndexService = uriIndexService;
        this.blobStorage = blobStorage;
        this.properties = properties;
        this.errorCodeRegistry = errorCodeRegistry;
    }

    public PurgeResult purge(boolean dryRun, String approvalToken, List<String> storageUris) {
        assertApproval(approvalToken);

        StorageBucketOrphanScanner.OrphanScanResult scan = orphanScanner.scanBuckets();
        Set<String> orphanUris = scan.orphans().stream()
                .map(StorageBucketOrphanScanner.OrphanFinding::storageUri)
                .map(KnownStorageUriIndexService::normalize)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<String> targets = resolveTargets(storageUris, orphanUris);
        int limit = Math.max(1, properties.getMaxDeletesPerRun());
        List<String> errors = new ArrayList<>();
        int deleted = 0;
        int skipped = 0;
        int planned = 0;

        Set<String> knownBeforeDelete = uriIndexService.buildKnownUriIndex();

        for (String uri : targets.stream().limit(limit).toList()) {
            String normalized = KnownStorageUriIndexService.normalize(uri);
            if (!orphanUris.contains(normalized)) {
                skipped++;
                errors.add(uri + ": not in latest orphan scan");
                continue;
            }
            if (knownBeforeDelete.contains(normalized)) {
                skipped++;
                errors.add(uri + ": URI became referenced before purge");
                continue;
            }
            if (dryRun) {
                planned++;
                continue;
            }
            try {
                boolean removed = blobStorage.deleteStorageUri(normalized);
                if (removed) {
                    deleted++;
                    log.info("Purged storage orphan uri={}", normalized);
                } else {
                    skipped++;
                    errors.add(uri + ": delete not supported or object absent");
                }
            } catch (Exception e) {
                skipped++;
                errors.add(uri + ": " + e.getMessage());
                log.warn("Orphan purge failed for {}: {}", normalized, e.getMessage());
            }
        }

        return new PurgeResult(
                dryRun,
                scan.objectsScanned(),
                scan.orphanCount(),
                targets.size(),
                dryRun ? planned : deleted,
                skipped,
                errors);
    }

    private void assertApproval(String approvalToken) {
        if (!properties.isPurgeAllowed()) {
            throw MediaAssetErrors.storagePurgeForbidden(
                    errorCodeRegistry, "orphan purge disabled; set platform.storage.orphan-purge.enabled and approval-token");
        }
        if (approvalToken == null
                || approvalToken.isBlank()
                || !properties.getApprovalToken().equals(approvalToken)) {
            throw MediaAssetErrors.storagePurgeForbidden(errorCodeRegistry, "invalid or missing approval token");
        }
    }

    private static List<String> resolveTargets(List<String> requested, Set<String> orphanUris) {
        if (requested == null || requested.isEmpty()) {
            return new ArrayList<>(orphanUris);
        }
        return requested.stream()
                .map(KnownStorageUriIndexService::normalize)
                .filter(orphanUris::contains)
                .distinct()
                .toList();
    }

    public record PurgeResult(
            boolean dryRun,
            int objectsScanned,
            int orphanCountAtScan,
            int targetsConsidered,
            int deletedOrPlanned,
            int skipped,
            List<String> errors) {}
}
