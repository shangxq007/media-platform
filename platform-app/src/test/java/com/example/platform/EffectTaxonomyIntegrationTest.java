package com.example.platform;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.ai.openai.api-key=test-key",
        "app.security.enabled=false",
        "app.identity.api-key-auth-enabled=false",
        "app.outbox.dispatch-interval-ms=999999999",
        "spring.flyway.enabled=true"
})
class EffectTaxonomyIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void verifyEffectPackEffectSchema() {
        // Check if taxonomy columns exist
        List<Map<String, Object>> columns = jdbcTemplate.queryForList(
            "SELECT COLUMN_NAME, DATA_TYPE, IS_NULLABLE " +
            "FROM INFORMATION_SCHEMA.COLUMNS " +
            "WHERE TABLE_NAME = 'EFFECT_PACK_EFFECT' " +
            "AND COLUMN_NAME IN ('taxonomy_category', 'is_effect')"
        );

        assertEquals(2, columns.size(), "Should have both taxonomy_category and is_effect columns");

        boolean hasTaxonomyCategory = columns.stream()
            .anyMatch(col -> "taxonomy_category".equals(col.get("COLUMN_NAME")));
        boolean hasIsEffect = columns.stream()
            .anyMatch(col -> "is_effect".equals(col.get("COLUMN_NAME")));

        assertTrue(hasTaxonomyCategory, "taxonomy_category column should exist");
        assertTrue(hasIsEffect, "is_effect column should exist");
    }

    @Test
    void verifyDataBackfill() {
        // Check total number of effect keys
        Integer totalEffects = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM effect_pack_effect", Integer.class
        );
        System.out.println("Total effect pack effects: " + totalEffects);

        // Check taxonomy categories for specific effects
        Map<String, Object> fadeIn = jdbcTemplate.queryForMap(
            "SELECT effect_key, taxonomy_category, is_effect " +
            "FROM effect_pack_effect " +
            "WHERE effect_key = 'video.fade_in'"
        );
        assertEquals("temporal", fadeIn.get("taxonomy_category"));
        assertEquals(true, fadeIn.get("is_effect"));

        Map<String, Object> blur = jdbcTemplate.queryForMap(
            "SELECT effect_key, taxonomy_category, is_effect " +
            "FROM effect_pack_effect " +
            "WHERE effect_key = 'video.blur'"
        );
        assertEquals("filter", blur.get("taxonomy_category"));
        assertEquals(true, blur.get("is_effect"));

        Map<String, Object> brightness = jdbcTemplate.queryForMap(
            "SELECT effect_key, taxonomy_category, is_effect " +
            "FROM effect_pack_effect " +
            "WHERE effect_key = 'video.brightness'"
        );
        assertEquals("color", brightness.get("taxonomy_category"));
        assertEquals(true, brightness.get("is_effect"));

        Map<String, Object> watermark = jdbcTemplate.queryForMap(
            "SELECT effect_key, taxonomy_category, is_effect " +
            "FROM effect_pack_effect " +
            "WHERE effect_key = 'video.watermark'"
        );
        assertEquals("composite", watermark.get("taxonomy_category"));
        assertEquals(true, watermark.get("is_effect"));

        // Check non-effect operations
        List<Map<String, Object>> nonEffects = jdbcTemplate.queryForList(
            "SELECT effect_key, taxonomy_category, is_effect " +
            "FROM effect_pack_effect " +
            "WHERE is_effect = false"
        );
        
        System.out.println("Non-effect operations:");
        nonEffects.forEach(nonEffect -> {
            System.out.println("  " + nonEffect.get("effect_key") + 
                             " -> category: " + nonEffect.get("taxonomy_category") + 
                             ", is_effect: " + nonEffect.get("is_effect"));
        });

        assertEquals(4, nonEffects.size(), "Should have 4 non-effect operations");
        
        List<String> nonEffectKeys = nonEffects.stream()
            .map(e -> (String) e.get("effect_key"))
            .toList();
        
        assertTrue(nonEffectKeys.contains("video.dash_drm"));
        assertTrue(nonEffectKeys.contains("video.shotstack_template"));
        assertTrue(nonEffectKeys.contains("video.remotion_template"));
        assertTrue(nonEffectKeys.contains("video.blender_scene"));
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

        System.out.println("Taxonomy categories:");
        categories.forEach(category -> {
            System.out.println("  " + category.get("taxonomy_category") + 
                             ": " + category.get("count") + " effects");
        });

        // Verify expected categories exist
        List<String> categoryNames = categories.stream()
            .map(c -> (String) c.get("taxonomy_category"))
            .toList();

        assertTrue(categoryNames.contains("temporal"));
        assertTrue(categoryNames.contains("filter"));
        assertTrue(categoryNames.contains("color"));
        assertTrue(categoryNames.contains("composite"));
        assertTrue(categoryNames.contains("text"));
        assertTrue(categoryNames.contains("audio"));
        assertTrue(categoryNames.contains("transition"));
        assertTrue(categoryNames.contains("vfx"));
        assertTrue(categoryNames.contains("packaging"));
        assertTrue(categoryNames.contains("cloud_rendering"));
    }
}