package com.example.platform.render.domain.spatial;

import com.example.platform.shared.test.FixturePath;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates Project Export v1 JSON schemas can be parsed correctly.
 */
class ProjectExportSchemaTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void manifestJson_shouldBeValid() throws Exception {
        Path manifestPath = FixturePath.docsFixture("media-rendering/project-export-v1-example/manifest.json");
        assertTrue(Files.exists(manifestPath), "manifest.json should exist at: " + manifestPath);

        String json = Files.readString(manifestPath);
        assertNotNull(json);
        assertFalse(json.isBlank());

        var root = MAPPER.readTree(json);
        assertNotNull(root.get("schemaVersion"));
        assertEquals("project-export-v1", root.get("schemaVersion").asText());
        assertNotNull(root.get("exportMode"));
        assertNotNull(root.get("exportedAt"));
        assertNotNull(root.get("project"));
        assertNotNull(root.get("security"));
        assertNotNull(root.get("assets"));
    }

    @Test
    void assetsJson_shouldBeValid() throws Exception {
        Path assetsPath = FixturePath.docsFixture("media-rendering/project-export-v1-example/assets.json");
        assertTrue(Files.exists(assetsPath), "assets.json should exist at: " + assetsPath);

        String json = Files.readString(assetsPath);
        assertNotNull(json);

        var root = MAPPER.readTree(json);
        assertNotNull(root.get("schemaVersion"));
        assertNotNull(root.get("exportMode"));
        assertNotNull(root.get("assets"));
        assertTrue(root.get("assets").isArray());
        assertTrue(root.get("assets").size() > 0);

        var firstAsset = root.get("assets").get(0);
        assertNotNull(firstAsset.get("assetId"));
        assertNotNull(firstAsset.get("filename"));
        assertNotNull(firstAsset.get("type"));
        assertNotNull(firstAsset.get("mimeType"));
    }

    @Test
    void spatialPlanJson_shouldBeValid() throws Exception {
        Path spatialPlanPath = FixturePath.goldenProjectRoot().resolve("manifests/golden-spatial-plan.json");
        if (!Files.exists(spatialPlanPath)) {
            // Golden assets not generated; skip
            return;
        }
        String json = Files.readString(spatialPlanPath);
        var root = MAPPER.readTree(json);
        assertNotNull(root.get("version"));
        assertNotNull(root.get("coordinateSystem"));
        assertNotNull(root.get("operations"));
        assertTrue(root.get("operations").isArray());
    }
}
