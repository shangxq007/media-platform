package com.example.platform.render.app.asset;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AssetJsonLdExporterTest {

    @Test
    void shouldExportBasicProjection() {
        Map<String, Object> projection = AssetJsonLdExporter.buildProjection(
                "asset_123", "v7", "video", "s3://bucket/key.mp4",
                "sha256:abc123", "internal", "enterprise-owned", true);

        String json = AssetJsonLdExporter.export(projection);

        assertNotNull(json);
        assertTrue(json.contains("@context"));
        assertTrue(json.contains("asset:asset_123"));
        assertTrue(json.contains("MediaAsset"));
        assertTrue(json.contains("asset:id"));
        assertTrue(json.contains("governance:classification"));
        assertTrue(json.contains("governance:aiGenerated"));
    }

    @Test
    void shouldExportProjectionWithLineage() {
        Map<String, Object> projection = AssetJsonLdExporter.buildProjectionWithLineage(
                "asset_456", "v3", "video", "s3://bucket/out.mp4",
                List.of("asset_1", "asset_2"), "wf_789", "run_012");

        String json = AssetJsonLdExporter.export(projection);

        assertNotNull(json);
        assertTrue(json.contains("lineage:derivedFrom"));
        assertTrue(json.contains("asset:asset_1"));
        assertTrue(json.contains("asset:asset_2"));
        assertTrue(json.contains("lineage:workflowId"));
        assertTrue(json.contains("wf_789"));
        assertTrue(json.contains("lineage:runId"));
        assertTrue(json.contains("run_012"));
    }

    @Test
    void shouldIncludeBluepulseContextUris() {
        Map<String, Object> projection = AssetJsonLdExporter.buildProjection(
                "asset_xyz", "v1", "image", "s3://b/img.png",
                null, null, null, false);

        String json = AssetJsonLdExporter.export(projection);

        assertTrue(json.contains("open-media.org/xmp/asset/1.0/"));
        assertTrue(json.contains("open-media.org/xmp/ai/1.0/"));
        assertTrue(json.contains("open-media.org/xmp/lineage/1.0/"));
        assertTrue(json.contains("open-media.org/xmp/governance/1.0/"));
    }
}
