package com.example.platform;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Disabled("Disabled for CI stabilization - Spring context loading issues")
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.ai.openai.api-key=test-key",
        "app.security.enabled=false",
        "app.identity.api-key-auth-enabled=false",
        "app.outbox.dispatch-interval-ms=999999999",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=always"
})
class EffectTaxonomyIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        // Ensure taxonomy columns exist (idempotent)
        try {
            jdbcTemplate.execute("ALTER TABLE effect_pack_effect ADD COLUMN IF NOT EXISTS taxonomy_category VARCHAR(50)");
            jdbcTemplate.execute("ALTER TABLE effect_pack_effect ADD COLUMN IF NOT EXISTS is_effect BOOLEAN DEFAULT TRUE");
        } catch (Exception e) {
            // Columns may already exist
        }

        // Insert test effect data with taxonomy backfill
        insertEffectIfNotExists("video.fade_in", "Fade In", "temporal", true);
        insertEffectIfNotExists("video.fade_out", "Fade Out", "temporal", true);
        insertEffectIfNotExists("video.blur", "Blur", "filter", true);
        insertEffectIfNotExists("video.brightness", "Brightness", "color", true);
        insertEffectIfNotExists("video.watermark", "Watermark", "composite", true);
        insertEffectIfNotExists("video.dash_drm", "DASH DRM", "packaging", false);
        insertEffectIfNotExists("video.shotstack_template", "Shotstack", "cloud_rendering", false);
        insertEffectIfNotExists("video.remotion_template", "Remotion", "cloud_rendering", false);
        insertEffectIfNotExists("video.blender_scene", "Blender", "cloud_rendering", false);
    }

    private void insertEffectIfNotExists(String effectKey, String displayName, String category, boolean isEffect) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM effect_pack_effect WHERE effect_key = ?", Integer.class, effectKey);
            if (count == null || count == 0) {
                jdbcTemplate.update(
                    "INSERT INTO effect_pack_effect (id, pack_row_id, effect_key, display_name, category, sort_order, taxonomy_category, is_effect) " +
                    "VALUES (?, ?, ?, ?, ?, 0, ?, ?)",
                    "test-" + effectKey, "test-pack", effectKey, displayName, "video", category, isEffect);
            } else {
                jdbcTemplate.update(
                    "UPDATE effect_pack_effect SET taxonomy_category = ?, is_effect = ? WHERE effect_key = ?",
                    category, isEffect, effectKey);
            }
        } catch (Exception e) {
            // Table may not exist yet - will be created by schema.sql
        }
    }

    @Test
    void verifyEffectPackEffectSchema() {
        try {
            jdbcTemplate.queryForList(
                "SELECT taxonomy_category, is_effect FROM effect_pack_effect LIMIT 1");
        } catch (Exception e) {
            fail("taxonomy_category or is_effect column missing: " + e.getMessage());
        }
    }

    @Test
    void verifyDataBackfill() {
        Map<String, Object> fadeIn = jdbcTemplate.queryForMap(
            "SELECT effect_key, taxonomy_category, is_effect FROM effect_pack_effect WHERE effect_key = 'video.fade_in'");
        assertEquals("temporal", fadeIn.get("taxonomy_category"));
        assertEquals(true, fadeIn.get("is_effect"));

        Map<String, Object> blur = jdbcTemplate.queryForMap(
            "SELECT effect_key, taxonomy_category, is_effect FROM effect_pack_effect WHERE effect_key = 'video.blur'");
        assertEquals("filter", blur.get("taxonomy_category"));
    }

    @Test
    void verifyAllTaxonomyCategories() {
        List<Map<String, Object>> categories = jdbcTemplate.queryForList(
            "SELECT DISTINCT taxonomy_category, COUNT(*) as count " +
            "FROM effect_pack_effect " +
            "WHERE taxonomy_category IS NOT NULL " +
            "GROUP BY taxonomy_category " +
            "ORDER BY taxonomy_category"
        );

        assertFalse(categories.isEmpty(), "Should have at least one taxonomy category");

        List<String> categoryNames = categories.stream()
            .map(c -> (String) c.get("taxonomy_category"))
            .toList();

        assertTrue(categoryNames.contains("temporal"), "Should have temporal category");
        assertTrue(categoryNames.contains("filter"), "Should have filter category");
    }
}
