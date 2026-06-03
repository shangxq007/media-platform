package com.example.platform.render.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * Validates Golden Render Project v1 manifests.
 */
class GoldenProjectManifestTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadManifest(String resourcePath) throws Exception {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            assertNotNull(is, "Manifest not found: " + resourcePath);
            return MAPPER.readValue(is, Map.class);
        }
    }

    @Test
    void goldenProjectManifest_shouldBeValid() throws Exception {
        Map<String, Object> manifest = loadManifest("golden-render-project-v1/manifests/golden-project.json");

        assertEquals("golden-project-v1", manifest.get("schemaVersion"));
        assertEquals("golden-render-project-v1", manifest.get("projectId"));
        assertEquals(30000, manifest.get("durationMs"));
    }

    @Test
    void goldenProjectManifest_shouldHave17Assets() throws Exception {
        Map<String, Object> manifest = loadManifest("golden-render-project-v1/manifests/golden-project.json");
        List<Map<String, Object>> assets = (List<Map<String, Object>>) manifest.get("assets");
        assertNotNull(assets);
        assertEquals(17, assets.size());
    }

    @Test
    void goldenProjectManifest_shouldHave6Tracks() throws Exception {
        Map<String, Object> manifest = loadManifest("golden-render-project-v1/manifests/golden-project.json");
        List<Map<String, Object>> tracks = (List<Map<String, Object>>) manifest.get("tracks");
        assertNotNull(tracks);
        assertEquals(6, tracks.size());
    }

    @Test
    void goldenProjectManifest_shouldHave6ValidationPoints() throws Exception {
        Map<String, Object> manifest = loadManifest("golden-render-project-v1/manifests/golden-project.json");
        List<Integer> points = (List<Integer>) manifest.get("validationPoints");
        assertNotNull(points);
        assertEquals(6, points.size());
        // Verify sorted
        for (int i = 1; i < points.size(); i++) {
            assertTrue(points.get(i) > points.get(i - 1), "validationPoints should be sorted");
        }
        // Verify all within duration
        for (int point : points) {
            assertTrue(point > 0 && point <= 30000, "validationPoint out of range: " + point);
        }
    }

    @Test
    void goldenProjectManifest_assetReferencesShouldHaveRequiredFields() throws Exception {
        Map<String, Object> manifest = loadManifest("golden-render-project-v1/manifests/golden-project.json");
        List<Map<String, Object>> assets = (List<Map<String, Object>>) manifest.get("assets");
        for (Map<String, Object> asset : assets) {
            assertNotNull(asset.get("assetId"), "assetId required");
            assertNotNull(asset.get("type"), "type required");
            assertNotNull(asset.get("path"), "path required");
            assertNotNull(asset.get("purpose"), "purpose required");
        }
    }

    @Test
    void goldenRenderPlanManifest_shouldBeValid() throws Exception {
        Map<String, Object> manifest = loadManifest("golden-render-project-v1/manifests/golden-render-plan.json");

        assertEquals("render-plan-v1", manifest.get("version"));
        assertEquals("golden-render-project-v1", manifest.get("projectId"));
    }

    @Test
    void goldenRenderPlanManifest_shouldHave3OutputProfiles() throws Exception {
        Map<String, Object> manifest = loadManifest("golden-render-project-v1/manifests/golden-render-plan.json");
        List<Map<String, Object>> profiles = (List<Map<String, Object>>) manifest.get("outputProfiles");
        assertNotNull(profiles);
        assertEquals(3, profiles.size());
    }

    @Test
    void goldenRenderPlanManifest_shouldHaveUnsupportedList() throws Exception {
        Map<String, Object> manifest = loadManifest("golden-render-project-v1/manifests/golden-render-plan.json");
        List<Map<String, Object>> unsupported = (List<Map<String, Object>>) manifest.get("unsupported");
        assertNotNull(unsupported);
        assertTrue(unsupported.size() >= 3, "Should list unsupported operations");
        // Verify keying and deform are in unsupported
        List<String> types = unsupported.stream()
                .map(m -> (String) m.get("type"))
                .toList();
        assertTrue(types.contains("keying"), "keying should be unsupported");
        assertTrue(types.contains("deform"), "deform should be unsupported");
    }

    @Test
    void goldenSpatialPlanManifest_shouldBeValid() throws Exception {
        Map<String, Object> manifest = loadManifest("golden-render-project-v1/manifests/golden-spatial-plan.json");

        assertEquals("spatial-plan-v1", manifest.get("version"));
        assertEquals("normalized_ppm", manifest.get("coordinatePrecision"));
    }

    @Test
    void goldenSpatialPlanManifest_coordinatesShouldBeIntegerPpm() throws Exception {
        Map<String, Object> manifest = loadManifest("golden-render-project-v1/manifests/golden-spatial-plan.json");
        List<Map<String, Object>> operations = (List<Map<String, Object>>) manifest.get("operations");
        assertNotNull(operations);

        for (Map<String, Object> op : operations) {
            Map<String, Object> position = (Map<String, Object>) op.get("position");
            if (position != null) {
                for (Map.Entry<String, Object> entry : position.entrySet()) {
                    assertInstanceOf(Integer.class, entry.getValue(),
                            "Coordinate " + entry.getKey() + " in " + op.get("id") + " should be Integer PPM");
                    int val = (Integer) entry.getValue();
                    assertTrue(val >= 0 && val <= 1000000,
                            "Coordinate " + entry.getKey() + " out of PPM range: " + val);
                }
            }
            Map<String, Object> region = (Map<String, Object>) op.get("region");
            if (region != null) {
                for (Map.Entry<String, Object> entry : region.entrySet()) {
                    assertInstanceOf(Integer.class, entry.getValue(),
                            "Region " + entry.getKey() + " in " + op.get("id") + " should be Integer PPM");
                }
            }
        }
    }

    @Test
    void goldenSpatialPlanManifest_shouldNotUseFloatCoordinates() throws Exception {
        Map<String, Object> manifest = loadManifest("golden-render-project-v1/manifests/golden-spatial-plan.json");
        String json = MAPPER.writeValueAsString(manifest);
        // Check no decimal points in coordinate values (simple heuristic)
        assertFalse(json.contains("\"x\": 0.") || json.contains("\"y\": 0."),
                "Spatial plan should not use float coordinates");
    }

    @Test
    void goldenOtioManifest_shouldBeValid() throws Exception {
        Map<String, Object> manifest = loadManifest("golden-render-project-v1/manifests/golden-otio.json");

        assertEquals("Timeline.1", manifest.get("OTIO_SCHEMA"));
        assertEquals("golden-render-project-v1", manifest.get("name"));
    }

    @Test
    void goldenOtioManifest_shouldHave4Tracks() throws Exception {
        Map<String, Object> manifest = loadManifest("golden-render-project-v1/manifests/golden-otio.json");
        List<Map<String, Object>> tracks = (List<Map<String, Object>>) manifest.get("tracks");
        assertNotNull(tracks);
        assertEquals(4, tracks.size());
    }

    @Test
    void goldenOtioManifest_shouldLinkToRenderPlan() throws Exception {
        Map<String, Object> manifest = loadManifest("golden-render-project-v1/manifests/golden-otio.json");
        Map<String, Object> metadata = (Map<String, Object>) manifest.get("metadata");
        assertNotNull(metadata);
        Map<String, Object> mediaPlatform = (Map<String, Object>) metadata.get("mediaPlatform");
        assertNotNull(mediaPlatform);
        assertNotNull(mediaPlatform.get("renderPlanPath"));
        assertNotNull(mediaPlatform.get("spatialPlanPath"));
        assertNotNull(mediaPlatform.get("assetBindings"));
    }
}
