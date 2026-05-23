package com.example.platform.web.media;

import com.example.platform.artifact.app.ArtifactStorageIntegrityScanner;
import com.example.platform.audit.app.ProblematicDataDetectionService;
import com.example.platform.audit.domain.ProblematicDataRecord;
import com.example.platform.render.app.timeline.TimelineAssetIntegrityScanner;
import com.example.platform.shared.web.TenantContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AssetIntegrityScanService {

    private final TimelineAssetIntegrityScanner timelineScanner;
    private final ArtifactStorageIntegrityScanner storageScanner;
    private final ProblematicDataDetectionService problematicDataDetectionService;

    public AssetIntegrityScanService(
            TimelineAssetIntegrityScanner timelineScanner,
            ArtifactStorageIntegrityScanner storageScanner,
            ProblematicDataDetectionService problematicDataDetectionService) {
        this.timelineScanner = timelineScanner;
        this.storageScanner = storageScanner;
        this.problematicDataDetectionService = problematicDataDetectionService;
    }

    public ScanReport scanProject(String projectId) {
        String tenantId = TenantContext.get();
        List<Map<String, Object>> findings = new ArrayList<>();

        TimelineAssetIntegrityScanner.ScanResult timeline = timelineScanner.scanProject(projectId);
        for (TimelineAssetIntegrityScanner.Finding f : timeline.findings()) {
            findings.add(findingMap(f.ruleId(), projectId + ":" + f.assetId(), f.message(), Map.of(
                    "projectId", projectId,
                    "snapshotId", f.snapshotId(),
                    "assetId", f.assetId(),
                    "clipRef", f.clipRef())));
        }

        for (ArtifactStorageIntegrityScanner.StorageFinding f : storageScanner.scanCatalog()) {
            if (!projectId.equals(f.projectId())) {
                continue;
            }
            findings.add(findingMap(f.ruleId(), f.artifactId(), f.message(), Map.of(
                    "projectId", f.projectId(),
                    "artifactId", f.artifactId(),
                    "storageUri", f.storageUri())));
        }

        List<ProblematicDataRecord> records =
                problematicDataDetectionService.detectAssetIntegrityFindings(projectId, tenantId, findings);
        return new ScanReport(projectId, findings.size(), records.size(), findings);
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

    public record ScanReport(String projectId, int findingCount, int recordedCount,
                             List<Map<String, Object>> findings) {}
}
