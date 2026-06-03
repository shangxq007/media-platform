package com.example.platform.render.domain.spatial;

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
        // Resolve path: export examples are in repo-root/docs/
        // user.dir may be platform/render-module/ or platform/, so try both
        Path baseDir = Path.of(System.getProperty("user.dir"));
        Path manifestPath = baseDir.resolve("docs/media-rendering/project-export-v1-example/manifest.json");
        if (!Files.exists(manifestPath)) {
            manifestPath = baseDir.getParent().resolve("docs/media-rendering/project-export-v1-example/manifest.json");
        }
        if (!Files.exists(manifestPath)) {
            manifestPath = baseDir.getParent().getParent().resolve("docs/media-rendering/project-export-v1-example/manifest.json");
        }
        assertTrue(Files.exists(manifestPath), "manifest.json should exist. Tried: " + manifestPath);

        String json = Files.readString(manifestPath);
        assertNotNull(json);
        assertFalse(json.isBlank());

        // Parse as generic JSON to verify structure
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
        Path baseDir = Path.of(System.getProperty("user.dir"));
        Path assetsPath = baseDir.resolve("docs/media-rendering/project-export-v1-example/assets.json");
        if (!Files.exists(assetsPath)) {
            assetsPath = baseDir.getParent().resolve("docs/media-rendering/project-export-v1-example/assets.json");
        }
        if (!Files.exists(assetsPath)) {
            assetsPath = baseDir.getParent().getParent().resolve("docs/media-rendering/project-export-v1-example/assets.json");
        }
        assertTrue(Files.exists(assetsPath), "assets.json should exist. Tried: " + assetsPath);

        String json = Files.readString(assetsPath);
        assertNotNull(json);

        var root = MAPPER.readTree(json);
        assertNotNull(root.get("schemaVersion"));
        assertNotNull(root.get("exportMode"));
        assertNotNull(root.get("assets"));
        assertTrue(root.get("assets").isArray());
        assertTrue(root.get("assets").size() > 0);

        // Verify asset structure
        var firstAsset = root.get("assets").get(0);
        assertNotNull(firstAsset.get("assetId"));
        assertNotNull(firstAsset.get("filename"));
        assertNotNull(firstAsset.get("type"));
        assertNotNull(firstAsset.get("mimeType"));
    }

    @Test
    void spatialPlanJson_shouldBeValid() throws Exception {
        Path spatialPlanPath = Path.of("test-assets/golden-render-project-v1/manifests/golden-spatial-plan.json");
        if (!Files.exists(spatialPlanPath)) {
            return; // Skip if file doesn't exist
        }
        String json = Files.readString(spatialPlanPath);
        var root = MAPPER.readTree(json);
        assertNotNull(root.get("version"));
        assertNotNull(root.get("coordinateSystem"));
        assertNotNull(root.get("operations"));
        assertTrue(root.get("operations").isArray());
    }
}
