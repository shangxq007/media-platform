package com.example.platform.render.infrastructure;

import com.example.platform.render.infrastructure.EffectDescriptor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

/**
 * Effect Taxonomy v1 Mapping Service
 * 
 * Provides mapping between legacy effect categories and the new Effect Taxonomy v1.
 * Maintains backward compatibility while supporting the new 12-category system.
 */
@Service
public class EffectTaxonomyMappingService {
    
    /**
     * Legacy to Taxonomy Category Mapping
     */
    private static final Map<String, String> LEGACY_TO_TAXONOMY_MAPPING = Map.of(
        // Video Transitions -> temporal
        "transition", "temporal",
        
        // Video Effects -> filter/color/vfx/composite
        "video", "filter", // Default fallback for video effects
        
        // Audio Effects -> audio
        "audio", "audio",
        
        // Text Effects -> text
        "text", "text",
        
        // Compositing Effects -> composite
        "compositor", "composite"
    );
    
    /**
     * Effect Key to Taxonomy Category Mapping
     */
    private static final Map<String, String> EFFECT_KEY_TO_TAXONOMY = new HashMap<>();
    
    static {
        // Video Transitions -> temporal
        EFFECT_KEY_TO_TAXONOMY.put("video.fade_in", "temporal");
        EFFECT_KEY_TO_TAXONOMY.put("video.fade_out", "temporal");
        EFFECT_KEY_TO_TAXONOMY.put("video.cross_dissolve", "transition");
        EFFECT_KEY_TO_TAXONOMY.put("video.dissolve", "transition");
        EFFECT_KEY_TO_TAXONOMY.put("video.wipe", "transition");
        EFFECT_KEY_TO_TAXONOMY.put("video.slide", "transition");
        EFFECT_KEY_TO_TAXONOMY.put("video.zoom", "transition");
        
        // Video Filters -> filter/color
        EFFECT_KEY_TO_TAXONOMY.put("video.blur", "filter");
        EFFECT_KEY_TO_TAXONOMY.put("video.sharpen", "filter");
        EFFECT_KEY_TO_TAXONOMY.put("video.vignette", "filter");
        EFFECT_KEY_TO_TAXONOMY.put("video.natron_vignette", "filter");
        EFFECT_KEY_TO_TAXONOMY.put("video.chromatic", "filter");
        EFFECT_KEY_TO_TAXONOMY.put("video.natron_color_grade", "color");
        EFFECT_KEY_TO_TAXONOMY.put("video.brightness", "color");
        EFFECT_KEY_TO_TAXONOMY.put("video.contrast", "color");
        EFFECT_KEY_TO_TAXONOMY.put("video.grayscale", "color");
        EFFECT_KEY_TO_TAXONOMY.put("video.sepia", "color");
        EFFECT_KEY_TO_TAXONOMY.put("video.particle_overlay", "vfx");
        
        // Compositing Effects -> composite
        EFFECT_KEY_TO_TAXONOMY.put("video.overlay", "composite");
        EFFECT_KEY_TO_TAXONOMY.put("video.pip", "composite");
        EFFECT_KEY_TO_TAXONOMY.put("video.watermark", "composite");
        
        // Text Effects -> text
        EFFECT_KEY_TO_TAXONOMY.put("text.subtitle_burn_in", "text");
        EFFECT_KEY_TO_TAXONOMY.put("text.overlay", "text");
        
        // Audio Effects -> audio
        EFFECT_KEY_TO_TAXONOMY.put("audio.volume", "audio");
        
        // Non-Effect Operations -> operation
        EFFECT_KEY_TO_TAXONOMY.put("video.dash_drm", "packaging");
        EFFECT_KEY_TO_TAXONOMY.put("video.shotstack_template", "cloud_rendering");
        EFFECT_KEY_TO_TAXONOMY.put("video.remotion_template", "cloud_rendering");
        EFFECT_KEY_TO_TAXONOMY.put("video.blender_scene", "cloud_rendering");
    }
    
    /**
     * Non-Effect Operations
     */
    private static final List<String> NON_EFFECT_OPERATIONS = List.of(
        "video.dash_drm",
        "video.shotstack_template",
        "video.remotion_template",
        "video.blender_scene"
    );
    
    /**
     * Get taxonomy category for an effect key
     */
    public String getTaxonomyCategory(String effectKey) {
        return EFFECT_KEY_TO_TAXONOMY.getOrDefault(effectKey, "filter"); // Default fallback
    }
    
    /**
     * Check if effect key is a non-effect operation
     */
    public boolean isNonEffectOperation(String effectKey) {
        return NON_EFFECT_OPERATIONS.contains(effectKey);
    }
    
    /**
     * Get display category for an effect (with fallback)
     */
    public String getDisplayCategory(String effectKey, String legacyCategory) {
        String taxonomyCategory = getTaxonomyCategory(effectKey);
        
        // If it's a non-effect operation, return the operation category
        if (isNonEffectOperation(effectKey)) {
            return taxonomyCategory;
        }
        
        // For effects, if the effect key is known (has specific taxonomy mapping), use taxonomy category
        // If the effect key is unknown, use "filter" for legacy "video", otherwise use legacy category
        if (EFFECT_KEY_TO_TAXONOMY.containsKey(effectKey)) {
            return taxonomyCategory;
        } else {
            return "video".equals(legacyCategory) ? "filter" : legacyCategory;
        }
    }
    
    /**
     * Map legacy category to taxonomy category
     */
    public String mapLegacyCategory(String legacyCategory) {
        return LEGACY_TO_TAXONOMY_MAPPING.getOrDefault(legacyCategory, "filter");
    }
    
    /**
     * Enhance effect descriptor with taxonomy information
     */
    public EffectDescriptor enhanceWithTaxonomy(EffectDescriptor descriptor) {
        String taxonomyCategory = getTaxonomyCategory(descriptor.effectKey());
        boolean isEffect = !isNonEffectOperation(descriptor.effectKey());
        
        // Create enhanced descriptor with taxonomy information
        return new EffectDescriptor(
            descriptor.effectKey(),
            descriptor.displayName(),
            descriptor.category(), // Keep legacy category for compatibility
            descriptor.description(),
            descriptor.paramSchemas(),
            descriptor.providerKeys(),
            descriptor.defaultParams(),
            descriptor.allowedTiers(),
            taxonomyCategory, // Add taxonomy category
            isEffect // Add effect flag
        );
    }
    
    /**
     * Get all taxonomy categories
     */
    public List<String> getAllTaxonomyCategories() {
        return List.of(
            "crop", "transform", "color", "filter", "composite", "keying", 
            "deform", "text", "vfx", "temporal", "transition", "audio"
        );
    }
    
    /**
     * Get non-effect operation categories
     */
    public List<String> getNonEffectOperationCategories() {
        return List.of("packaging", "cloud_rendering", "infrastructure");
    }
    
    /**
     * Get effects by taxonomy category
     */
    public List<EffectDescriptor> getEffectsByCategory(List<EffectDescriptor> effects, String category) {
        return effects.stream()
            .filter(effect -> getTaxonomyCategory(effect.effectKey()).equals(category))
            .collect(Collectors.toList());
    }
    
    /**
     * Get non-effect operations
     */
    public List<EffectDescriptor> getNonEffectOperations(List<EffectDescriptor> effects) {
        return effects.stream()
            .filter(effect -> isNonEffectOperation(effect.effectKey()))
            .collect(Collectors.toList());
    }
    
    /**
     * Validate effect taxonomy consistency
     */
    public List<String> validateTaxonomyConsistency(List<EffectDescriptor> effects) {
        List<String> errors = new ArrayList<>();
        
        for (EffectDescriptor effect : effects) {
            String taxonomyCategory = effect.taxonomyCategory() != null ? effect.taxonomyCategory() : getTaxonomyCategory(effect.effectKey());
            
            // Check if taxonomy category is valid
            if (!getAllTaxonomyCategories().contains(taxonomyCategory) && 
                !getNonEffectOperationCategories().contains(taxonomyCategory)) {
                errors.add(String.format("Invalid taxonomy category '%s' for effect '%s'", 
                    taxonomyCategory, effect.effectKey()));
            }
            
            // Check if effect is properly classified
            if (isNonEffectOperation(effect.effectKey())) {
                // Non-effect operations should not have provider mappings for execution
                if (effect.providerKeys() != null && !effect.providerKeys().isEmpty()) {
                    errors.add(String.format("Non-effect operation '%s' should not have provider mappings", 
                        effect.effectKey()));
                }
            }
        }
        
        return errors;
    }
    
    /**
     * Get statistics about taxonomy distribution
     */
    public Map<String, Integer> getTaxonomyStatistics(List<EffectDescriptor> effects) {
        Map<String, Integer> stats = new HashMap<>();
        
        // Initialize all categories with 0
        getAllTaxonomyCategories().forEach(cat -> stats.put(cat, 0));
        getNonEffectOperationCategories().forEach(cat -> stats.put(cat, 0));
        stats.put("other", 0);
        
        // Count effects by category
        for (EffectDescriptor effect : effects) {
            String category = getTaxonomyCategory(effect.effectKey());
            stats.put(category, stats.getOrDefault(category, 0) + 1);
        }
        
        return stats;
    }
}