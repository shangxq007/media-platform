package com.example.platform.render.infrastructure;

import com.example.platform.render.infrastructure.EffectDescriptor;
import com.example.platform.render.infrastructure.EffectParameterSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for EffectTaxonomyMappingService
 */
@ExtendWith(MockitoExtension.class)
class EffectTaxonomyMappingServiceTest {

    private EffectTaxonomyMappingService taxonomyService;
    
    @BeforeEach
    void setUp() {
        taxonomyService = new EffectTaxonomyMappingService();
    }
    
    @Test
    void getTaxonomyCategory_ShouldReturnCorrectCategories() {
        // Test video transitions
        assertEquals("temporal", taxonomyService.getTaxonomyCategory("video.fade_in"));
        assertEquals("temporal", taxonomyService.getTaxonomyCategory("video.fade_out"));
        assertEquals("transition", taxonomyService.getTaxonomyCategory("video.cross_dissolve"));
        
        // Test video filters
        assertEquals("filter", taxonomyService.getTaxonomyCategory("video.blur"));
        assertEquals("filter", taxonomyService.getTaxonomyCategory("video.sharpen"));
        assertEquals("filter", taxonomyService.getTaxonomyCategory("video.vignette"));
        assertEquals("color", taxonomyService.getTaxonomyCategory("video.brightness"));
        assertEquals("color", taxonomyService.getTaxonomyCategory("video.contrast"));
        
        // Test compositing
        assertEquals("composite", taxonomyService.getTaxonomyCategory("video.overlay"));
        assertEquals("composite", taxonomyService.getTaxonomyCategory("video.pip"));
        
        // Test text and audio
        assertEquals("text", taxonomyService.getTaxonomyCategory("text.subtitle_burn_in"));
        assertEquals("audio", taxonomyService.getTaxonomyCategory("audio.volume"));
        
        // Test non-effect operations
        assertEquals("packaging", taxonomyService.getTaxonomyCategory("video.dash_drm"));
        assertEquals("cloud_rendering", taxonomyService.getTaxonomyCategory("video.shotstack_template"));
    }
    
    @Test
    void getTaxonomyCategory_ShouldFallbackToFilterForUnknownEffects() {
        assertEquals("filter", taxonomyService.getTaxonomyCategory("unknown.effect"));
        assertEquals("filter", taxonomyService.getTaxonomyCategory("video.unknown_effect"));
    }
    
    @Test
    void isNonEffectOperation_ShouldIdentifyOperationsCorrectly() {
        // Test non-effect operations
        assertTrue(taxonomyService.isNonEffectOperation("video.dash_drm"));
        assertTrue(taxonomyService.isNonEffectOperation("video.shotstack_template"));
        assertTrue(taxonomyService.isNonEffectOperation("video.remotion_template"));
        assertTrue(taxonomyService.isNonEffectOperation("video.blender_scene"));
        
        // Test effects
        assertFalse(taxonomyService.isNonEffectOperation("video.blur"));
        assertFalse(taxonomyService.isNonEffectOperation("video.brightness"));
        assertFalse(taxonomyService.isNonEffectOperation("text.subtitle_burn_in"));
        assertFalse(taxonomyService.isNonEffectOperation("audio.volume"));
        
        // Test unknown effects
        assertFalse(taxonomyService.isNonEffectOperation("unknown.effect"));
    }
    
    @Test
    void getDisplayCategory_ShouldReturnCorrectDisplayCategories() {
        // Test effects
        assertEquals("temporal", taxonomyService.getDisplayCategory("video.fade_in", "transition"));
        assertEquals("filter", taxonomyService.getDisplayCategory("video.blur", "video"));
        assertEquals("color", taxonomyService.getDisplayCategory("video.brightness", "video"));
        assertEquals("composite", taxonomyService.getDisplayCategory("video.overlay", "compositor"));
        assertEquals("text", taxonomyService.getDisplayCategory("text.subtitle_burn_in", "text"));
        assertEquals("audio", taxonomyService.getDisplayCategory("audio.volume", "audio"));
        
        // Test non-effect operations
        assertEquals("packaging", taxonomyService.getDisplayCategory("video.dash_drm", "video"));
        assertEquals("cloud_rendering", taxonomyService.getDisplayCategory("video.shotstack_template", "video"));
        
        // Test fallback
        assertEquals("filter", taxonomyService.getDisplayCategory("unknown.effect", "video"));
        assertEquals("audio", taxonomyService.getDisplayCategory("unknown.effect", "audio"));
    }
    
    @Test
    void mapLegacyCategory_ShouldMapLegacyCategoriesCorrectly() {
        assertEquals("temporal", taxonomyService.mapLegacyCategory("transition"));
        assertEquals("filter", taxonomyService.mapLegacyCategory("video"));
        assertEquals("audio", taxonomyService.mapLegacyCategory("audio"));
        assertEquals("text", taxonomyService.mapLegacyCategory("text"));
        assertEquals("composite", taxonomyService.mapLegacyCategory("compositor"));
        
        // Test fallback
        assertEquals("filter", taxonomyService.mapLegacyCategory("unknown"));
    }
    
    @Test
    void enhanceWithTaxonomy_ShouldAddTaxonomyInformation() {
        // Create original descriptor
        EffectDescriptor original = new EffectDescriptor(
            "video.blur",
            "Blur Effect",
            "video",
            "Applies blur filter to video",
            List.of(new EffectParameterSchema("radius", "float", 5.0, 0.1, 10.0, "Blur Radius")),
            List.of("ffmpeg", "javacv"),
            Map.of("radius", 5.0),
            List.of("FREE", "PRO"),
            null, // No taxonomy category originally
            null  // No effect flag originally
        );
        
        // Enhance with taxonomy
        EffectDescriptor enhanced = taxonomyService.enhanceWithTaxonomy(original);
        
        // Check that original fields are preserved
        assertEquals("video.blur", enhanced.effectKey());
        assertEquals("Blur Effect", enhanced.displayName());
        assertEquals("video", enhanced.category()); // Legacy category preserved
        assertEquals("Applies blur filter to video", enhanced.description());
        assertEquals(1, enhanced.paramSchemas().size());
        assertEquals(2, enhanced.providerKeys().size());
        assertEquals(2, enhanced.allowedTiers().size());
        
        // Check that taxonomy information is added
        assertEquals("filter", enhanced.taxonomyCategory());
        assertTrue(enhanced.isEffect());
    }
    
    @Test
    void getAllTaxonomyCategories_ShouldReturnAllCategories() {
        List<String> categories = taxonomyService.getAllTaxonomyCategories();
        
        assertEquals(12, categories.size());
        assertTrue(categories.contains("crop"));
        assertTrue(categories.contains("transform"));
        assertTrue(categories.contains("color"));
        assertTrue(categories.contains("filter"));
        assertTrue(categories.contains("composite"));
        assertTrue(categories.contains("keying"));
        assertTrue(categories.contains("deform"));
        assertTrue(categories.contains("text"));
        assertTrue(categories.contains("vfx"));
        assertTrue(categories.contains("temporal"));
        assertTrue(categories.contains("transition"));
        assertTrue(categories.contains("audio"));
    }
    
    @Test
    void getNonEffectOperationCategories_ShouldReturnOperationCategories() {
        List<String> categories = taxonomyService.getNonEffectOperationCategories();
        
        assertEquals(3, categories.size());
        assertTrue(categories.contains("packaging"));
        assertTrue(categories.contains("cloud_rendering"));
        assertTrue(categories.contains("infrastructure"));
    }
    
    @Test
    void getEffectsByCategory_ShouldFilterEffectsCorrectly() {
        // Create test effects
        EffectDescriptor blur = new EffectDescriptor("video.blur", "Blur", "video", "Blur effect", List.of(), List.of(), Map.of(), List.of(), "filter", true);
        EffectDescriptor brightness = new EffectDescriptor("video.brightness", "Brightness", "video", "Brightness effect", List.of(), List.of(), Map.of(), List.of(), "color", true);
        EffectDescriptor dashDrm = new EffectDescriptor("video.dash_drm", "DRM", "video", "DRM packaging", List.of(), List.of(), Map.of(), List.of(), "packaging", false);
        
        List<EffectDescriptor> allEffects = List.of(blur, brightness, dashDrm);
        
        // Test filter category
        List<EffectDescriptor> filterEffects = taxonomyService.getEffectsByCategory(allEffects, "filter");
        assertEquals(1, filterEffects.size());
        assertEquals("video.blur", filterEffects.get(0).effectKey());
        
        // Test color category
        List<EffectDescriptor> colorEffects = taxonomyService.getEffectsByCategory(allEffects, "color");
        assertEquals(1, colorEffects.size());
        assertEquals("video.brightness", colorEffects.get(0).effectKey());
        
        // Test packaging category
        List<EffectDescriptor> packagingEffects = taxonomyService.getEffectsByCategory(allEffects, "packaging");
        assertEquals(1, packagingEffects.size());
        assertEquals("video.dash_drm", packagingEffects.get(0).effectKey());
    }
    
    @Test
    void getNonEffectOperations_ShouldFilterOperationsCorrectly() {
        // Create test effects
        EffectDescriptor blur = new EffectDescriptor("video.blur", "Blur", "video", "Blur effect", List.of(), List.of(), Map.of(), List.of(), "filter", true);
        EffectDescriptor dashDrm = new EffectDescriptor("video.dash_drm", "DRM", "video", "DRM packaging", List.of(), List.of(), Map.of(), List.of(), "packaging", false);
        EffectDescriptor shotstack = new EffectDescriptor("video.shotstack_template", "Shotstack", "video", "Cloud rendering", List.of(), List.of(), Map.of(), List.of(), "cloud_rendering", false);
        
        List<EffectDescriptor> allEffects = List.of(blur, dashDrm, shotstack);
        
        List<EffectDescriptor> operations = taxonomyService.getNonEffectOperations(allEffects);
        assertEquals(2, operations.size());
        
        // Check that only operations are returned
        assertTrue(operations.stream().allMatch(effect -> !effect.isEffect()));
        assertTrue(operations.stream().anyMatch(effect -> effect.effectKey().equals("video.dash_drm")));
        assertTrue(operations.stream().anyMatch(effect -> effect.effectKey().equals("video.shotstack_template")));
    }
    
    @Test
    void validateTaxonomyConsistency_ShouldReturnValidations() {
        // Create test effects with potential issues
        EffectDescriptor validEffect = new EffectDescriptor("video.blur", "Blur", "video", "Blur effect", List.of(), List.of(), Map.of(), List.of(), "filter", true);
        EffectDescriptor invalidCategory = new EffectDescriptor("video.unknown", "Unknown", "video", "Unknown effect", List.of(), List.of(), Map.of(), List.of(), "invalid_category", true);
        EffectDescriptor operationWithProviders = new EffectDescriptor("video.dash_drm", "DRM", "video", "DRM packaging", List.of(), List.of("ffmpeg"), Map.of(), List.of(), "packaging", false);
        
        // Debug: Check if effects are correctly identified as non-effect operations
        System.out.println("video.dash_drm is non-effect: " + taxonomyService.isNonEffectOperation("video.dash_drm"));
        System.out.println("operationWithProviders providerKeys: " + operationWithProviders.providerKeys());
        System.out.println("operationWithProviders providerKeys empty: " + (operationWithProviders.providerKeys() == null || operationWithProviders.providerKeys().isEmpty()));
        
        List<EffectDescriptor> effects = List.of(validEffect, invalidCategory, operationWithProviders);
        
        List<String> errors = taxonomyService.validateTaxonomyConsistency(effects);
        
        // Check which specific errors we're getting
        boolean hasInvalidCategoryError = errors.stream().anyMatch(error -> error.contains("invalid_category"));
        boolean hasProviderError = errors.stream().anyMatch(error -> error.contains("should not have provider mappings"));
        
        System.out.println("Has invalid category error: " + hasInvalidCategoryError);
        System.out.println("Has provider error: " + hasProviderError);
        System.out.println("Total errors: " + errors.size());
        
        // Should have errors for invalid category and operation with providers
        assertEquals(2, errors.size());
        assertTrue(hasInvalidCategoryError);
        assertTrue(hasProviderError);
    }
    
    @Test
    void getTaxonomyStatistics_ShouldReturnCorrectStatistics() {
        // Create test effects
        EffectDescriptor blur = new EffectDescriptor("video.blur", "Blur", "video", "Blur effect", List.of(), List.of(), Map.of(), List.of(), "filter", true);
        EffectDescriptor brightness = new EffectDescriptor("video.brightness", "Brightness", "video", "Brightness effect", List.of(), List.of(), Map.of(), List.of(), "color", true);
        EffectDescriptor fade = new EffectDescriptor("video.fade_in", "Fade In", "transition", "Fade in effect", List.of(), List.of(), Map.of(), List.of(), "temporal", true);
        EffectDescriptor dashDrm = new EffectDescriptor("video.dash_drm", "DRM", "video", "DRM packaging", List.of(), List.of(), Map.of(), List.of(), "packaging", false);
        
        List<EffectDescriptor> effects = List.of(blur, brightness, fade, dashDrm);
        
        Map<String, Integer> stats = taxonomyService.getTaxonomyStatistics(effects);
        
        // Check counts
        assertEquals(1, (int) stats.get("filter"));
        assertEquals(1, (int) stats.get("color"));
        assertEquals(1, (int) stats.get("temporal"));
        assertEquals(1, (int) stats.get("packaging"));
        assertEquals(0, (int) stats.get("other"));
        
        // Check that all categories are initialized
        assertNotNull(stats.get("crop"));
        assertNotNull(stats.get("transform"));
        assertNotNull(stats.get("composite"));
        assertNotNull(stats.get("keying"));
        assertNotNull(stats.get("deform"));
        assertNotNull(stats.get("text"));
        assertNotNull(stats.get("vfx"));
        assertNotNull(stats.get("transition"));
        assertNotNull(stats.get("audio"));
        assertNotNull(stats.get("cloud_rendering"));
        assertNotNull(stats.get("infrastructure"));
    }
}