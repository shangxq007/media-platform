package com.example.platform.web.media;

import com.example.platform.artifact.app.ArtifactStorageIntegrityScanner;
import com.example.platform.artifact.app.AssetIntegrityMetrics;
import com.example.platform.audit.app.ProblematicDataDetectionService;
import com.example.platform.audit.domain.ProblematicDataRecord;
import com.example.platform.render.app.TimelineSnapshotService;
import com.example.platform.render.app.timeline.TimelineAssetIntegrityScanner;
import com.example.platform.shared.web.TenantContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class GlobalAssetIntegrityService {

    private final TimelineSnapshotService timelineSnapshotService;
    private final TimelineAssetIntegrityScanner timelineScanner;
    private final ArtifactStorageIntegrityScanner storageScanner;
    private final AssetIntegrityMetrics metrics;
    private final ProblematicDataDetectionService problematicDataDetectionService;
    private final StorageBucketOrphanScanner bucketOrphanScanner;

    public GlobalAssetIntegrityService(
            TimelineSnapshotService timelineSnapshotService,
            TimelineAssetIntegrityScanner timelineScanner,
            ArtifactStorageIntegrityScanner storageScanner,
            AssetIntegrityMetrics metrics,
            ProblematicDataDetectionService problematicDataDetectionService,
            StorageBucketOrphanScanner bucketOrphanScanner) {
        this.timelineSnapshotService = timelineSnapshotService;
        this.timelineScanner = timelineScanner;
        this.storageScanner = storageScanner;
        this.metrics = metrics;
        this.problematicDataDetectionService = problematicDataDetectionService;
        this.bucketOrphanScanner = bucketOrphanScanner;
    }

    public GlobalScanReport scanAll(boolean recordProblematicData) {
        String tenantId = TenantContext.get();
        List<Map<String, Object>> findings = new ArrayList<>();
        int orphanBlobs = 0;
        int missingBlobs = 0;
        int dangling = 0;
        int unresolved = 0;
        int projectsScanned = 0;

        for (String projectId : timelineSnapshotService.listDistinctProjectIds()) {
            projectsScanned++;
            TimelineAssetIntegrityScanner.ScanResult timeline = timelineScanner.scanProject(projectId);
            for (TimelineAssetIntegrityScanner.Finding f : timeline.findings()) {
                findings.add(findingMap(f.ruleId(), projectId + ":" + f.assetId(), f.message(), Map.of(
                        "projectId", projectId,
                        "snapshotId", f.snapshotId(),
                        "assetId", f.assetId(),
                        "clipRef", f.clipRef())));
                switch (f.ruleId()) {
                    case "AST-001" -> dangling++;
                    case "AST-003" -> unresolved++;
                    default -> { }
                }
            }
        }

        for (ArtifactStorageIntegrityScanner.StorageFinding f : storageScanner.scanCatalog()) {
            findings.add(findingMap(f.ruleId(), f.artifactId(), f.message(), Map.of(
                    "projectId", f.projectId(),
                    "artifactId", f.artifactId(),
                    "storageUri", f.storageUri())));
            if ("AST-002".equals(f.ruleId())) {
                orphanBlobs++;
            } else if ("AST-004".equals(f.ruleId())) {
                missingBlobs++;
            }
        }

        StorageBucketOrphanScanner.OrphanScanResult bucketScan = bucketOrphanScanner.scanBuckets();
        int bucketOrphans = bucketScan.orphanCount();
        for (StorageBucketOrphanScanner.OrphanFinding orphan : bucketScan.orphans()) {
            findings.add(findingMap(orphan.ruleId(), orphan.storageUri(), orphan.message(), Map.of(
                    "bucket", orphan.bucket(),
                    "objectKey", orphan.objectKey(),
                    "storageUri", orphan.storageUri(),
                    "sizeBytes", orphan.sizeBytes())));
        }

        AssetIntegrityMetrics.GlobalScanSummary summary = new AssetIntegrityMetrics.GlobalScanSummary(
                projectsScanned, orphanBlobs, missingBlobs, dangling, unresolved, bucketOrphans);
        metrics.recordGlobalScan(summary);

        Map<String, Integer> countsByRule = new LinkedHashMap<>();
        countsByRule.put("AST-001", dangling);
        countsByRule.put("AST-002", orphanBlobs);
        countsByRule.put("AST-003", unresolved);
        countsByRule.put("AST-004", missingBlobs);
        countsByRule.put("AST-005", bucketOrphans);

        int recorded = 0;
        if (recordProblematicData && !findings.isEmpty()) {
            for (String projectId : timelineSnapshotService.listDistinctProjectIds()) {
                List<Map<String, Object>> projectFindings = findings.stream()
                        .filter(f -> projectId.equals(String.valueOf(f.get("projectId"))))
                        .toList();
                if (!projectFindings.isEmpty()) {
                    List<ProblematicDataRecord> records = problematicDataDetectionService
                            .detectAssetIntegrityFindings(projectId, tenantId, projectFindings);
                    recorded += records.size();
                }
            }
            List<Map<String, Object>> catalogOnly = findings.stream()
                    .filter(f -> f.containsKey("artifactId") && !f.containsKey("clipRef"))
                    .toList();
            if (!catalogOnly.isEmpty()) {
                List<ProblematicDataRecord> records = problematicDataDetectionService
                        .detectAssetIntegrityFindings("_catalog", tenantId, catalogOnly);
                recorded += records.size();
            }
        }

        return new GlobalScanReport(projectsScanned, findings.size(), recorded, countsByRule, summary);
    }

    private static Map<String, Object> findingMap(String ruleId, String dataId, String message,
                                                    Map<String, Object> context) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("ruleId", ruleId);
        map.put("dataId", dataId);
        map.put("message", message);
        map.putAll(context);
        return map;
    }

    public record GlobalScanReport(
            int projectsScanned,
            int findingCount,
            int recordedCount,
            Map<String, Integer> countsByRule,
            AssetIntegrityMetrics.GlobalScanSummary metricsSummary) {}
}
