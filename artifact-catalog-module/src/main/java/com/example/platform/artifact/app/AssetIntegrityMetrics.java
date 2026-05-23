package com.example.platform.artifact.app;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Exposes last global asset-integrity scan counts to Prometheus ({@code asset.integrity.*}).
 */
@Component
public class AssetIntegrityMetrics {

    private final AtomicInteger orphanBlobCount = new AtomicInteger(0);
    private final AtomicInteger missingBlobCount = new AtomicInteger(0);
    private final AtomicInteger danglingAssetRefCount = new AtomicInteger(0);
    private final AtomicInteger unresolvedUriCount = new AtomicInteger(0);
    private final AtomicInteger projectsScanned = new AtomicInteger(0);
    private final AtomicInteger bucketOrphanCount = new AtomicInteger(0);

    public AssetIntegrityMetrics(@Autowired(required = false) MeterRegistry meterRegistry) {
        if (meterRegistry == null) {
            return;
        }
        Gauge.builder("asset.integrity.orphan_blobs", orphanBlobCount, AtomicInteger::get)
                .description("Tombstoned catalog artifacts whose storage blob still exists")
                .register(meterRegistry);
        Gauge.builder("asset.integrity.missing_blobs", missingBlobCount, AtomicInteger::get)
                .description("Active catalog artifacts whose storage blob is missing")
                .register(meterRegistry);
        Gauge.builder("asset.integrity.dangling_timeline_refs", danglingAssetRefCount, AtomicInteger::get)
                .description("Timeline clips referencing missing or tombstoned assetIds")
                .register(meterRegistry);
        Gauge.builder("asset.integrity.unresolved_registry_uris", unresolvedUriCount, AtomicInteger::get)
                .description("Timeline assetRegistry entries without concrete storage URI")
                .register(meterRegistry);
        Gauge.builder("asset.integrity.projects_scanned", projectsScanned, AtomicInteger::get)
                .description("Projects included in the last global integrity scan")
                .register(meterRegistry);
        Gauge.builder("asset.integrity.bucket_orphan_objects", bucketOrphanCount, AtomicInteger::get)
                .description("Storage objects in scanned buckets with no known URI reference (AST-005)")
                .register(meterRegistry);
    }

    public void recordGlobalScan(GlobalScanSummary summary) {
        orphanBlobCount.set(summary.orphanBlobCount());
        missingBlobCount.set(summary.missingBlobCount());
        danglingAssetRefCount.set(summary.danglingTimelineRefCount());
        unresolvedUriCount.set(summary.unresolvedUriCount());
        projectsScanned.set(summary.projectsScanned());
        bucketOrphanCount.set(summary.bucketOrphanCount());
    }

    public record GlobalScanSummary(
            int projectsScanned,
            int orphanBlobCount,
            int missingBlobCount,
            int danglingTimelineRefCount,
            int unresolvedUriCount,
            int bucketOrphanCount) {}
}
