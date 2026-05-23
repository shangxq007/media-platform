package com.example.platform.render.infrastructure;

import com.example.platform.render.app.dto.EffectPackDtos.EffectPackEffectDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Service for mapping standardized effect keys to provider-specific implementations.
 *
 * <p>Provides a single source of truth for effect naming across frontend and backend.</p>
 */
@Service
public class EffectMappingService {
    private static final Logger log = LoggerFactory.getLogger(EffectMappingService.class);

    private final Map<String, EffectDescriptor> descriptors = new LinkedHashMap<>();
    private final Map<String, List<EffectProviderMapping>> mappings = new LinkedHashMap<>();

    public EffectMappingService() {
        registerStandardEffects();
    }

    private void registerStandardEffects() {
        // Video transitions
        register("video.fade_in", "Fade In", "transition", "Fade from black",
                List.of(new EffectParameterSchema("duration", "float", 1.0, 0.1, 5.0, "Duration in seconds")),
                List.of("javacv", "ofx"), Map.of("duration", "1.0"));

        register("video.fade_out", "Fade Out", "transition", "Fade to black",
                List.of(new EffectParameterSchema("duration", "float", 1.0, 0.1, 5.0, "Duration in seconds")),
                List.of("javacv", "ofx"), Map.of("duration", "1.0"));

        register("video.cross_dissolve", "Cross Dissolve", "transition", "Cross-fade between clips",
                List.of(new EffectParameterSchema("duration", "float", 0.5, 0.1, 3.0, "Duration in seconds")),
                List.of("ofx", "javacv"), Map.of("duration", "0.5"));

        register("video.dissolve", "Dissolve", "transition", "Dissolve transition",
                List.of(new EffectParameterSchema("duration", "float", 0.8, 0.1, 3.0, "Duration in seconds")),
                List.of("ofx"), Map.of("duration", "0.8"));

        register("video.wipe", "Wipe", "transition", "Wipe transition",
                List.of(new EffectParameterSchema("duration", "float", 0.5, 0.1, 2.0, "Duration in seconds")),
                List.of("ofx"), Map.of("duration", "0.5"));

        register("video.slide", "Slide", "transition", "Slide transition",
                List.of(new EffectParameterSchema("duration", "float", 0.5, 0.1, 2.0, "Duration in seconds")),
                List.of("ofx"), Map.of("duration", "0.5"));

        register("video.zoom", "Zoom", "transition", "Zoom transition",
                List.of(new EffectParameterSchema("duration", "float", 0.5, 0.1, 2.0, "Duration in seconds")),
                List.of("ofx"), Map.of("duration", "0.5"));

        // Video filters
        register("video.blur", "Blur", "video", "Gaussian blur",
                List.of(new EffectParameterSchema("radius", "float", 2.0, 0.1, 10.0, "Blur radius")),
                List.of("ofx", "javacv"), Map.of("radius", "2.0"));

        register("video.sharpen", "Sharpen", "video", "Sharpen filter",
                List.of(new EffectParameterSchema("amount", "float", 1.5, 0.1, 3.0, "Sharpen amount")),
                List.of("ofx", "javacv"), Map.of("amount", "1.5"));

        register("video.vignette", "Vignette", "video", "Vignette effect",
                List.of(new EffectParameterSchema("intensity", "float", 0.5, 0.0, 1.0, "Vignette intensity")),
                List.of("ofx"), Map.of("intensity", "0.5"),
                List.of("PRO", "TEAM", "ENTERPRISE"));

        register("video.natron_vignette", "Natron Vignette (POC)", "video",
                "Vignette via Natron worker POC template",
                List.of(new EffectParameterSchema("intensity", "float", 0.5, 0.0, 1.0, "Vignette intensity")),
                List.of("natron", "ofx", "javacv"), Map.of("intensity", "0.5"),
                List.of("PRO", "TEAM", "ENTERPRISE"));

        register("video.natron_color_grade", "Natron Color Grade (POC)", "video",
                "Saturation/contrast grade via Natron worker POC",
                List.of(new EffectParameterSchema("saturation", "float", 1.15, 0.5, 2.0, "Saturation multiplier")),
                List.of("natron", "ofx", "javacv"), Map.of("saturation", "1.15"),
                List.of("PRO", "TEAM", "ENTERPRISE"));

        register("video.particle_overlay", "PopcornFX Particle Overlay", "video",
                "Pre-baked PopcornFX particle layer (transparent video) over main clip",
                List.of(
                        new EffectParameterSchema("assetPath", "string", "", null, null,
                                "Local path or storage URI to baked overlay (webm/mov/png seq)"),
                        new EffectParameterSchema("opacity", "float", 1.0, 0.0, 1.0, "Overlay opacity"),
                        new EffectParameterSchema("position", "string", "center", null, null, "Overlay position")
                ),
                List.of("javacv", "ffmpeg", "ofx"), Map.of("opacity", "1.0", "position", "center"),
                List.of("PRO", "TEAM", "ENTERPRISE"));

        register("video.dash_drm", "DASH with DRM packaging", "video",
                "Package output as encrypted MPEG-DASH via Bento4",
                List.of(),
                List.of("bento4", "gpac"), Map.of(),
                List.of("TEAM", "ENTERPRISE"));

        register("video.shotstack_template", "Shotstack Cloud Render", "video",
                "Render timeline via Shotstack Edit API (cloud)",
                List.of(
                        new EffectParameterSchema("templateId", "string", "", null, null, "Optional Shotstack template"),
                        new EffectParameterSchema("resolution", "string", "hd", null, null, "hd, sd, mobile")
                ),
                List.of("shotstack"), Map.of("resolution", "hd"),
                List.of("TEAM", "ENTERPRISE"));

        register("video.remotion_template", "Remotion React Render", "video",
                "Programmatic render via Remotion CLI worker",
                List.of(new EffectParameterSchema("compositionId", "string", "Main", null, null, "Remotion composition id")),
                List.of("remotion"), Map.of("compositionId", "Main"),
                List.of("PRO", "TEAM", "ENTERPRISE"));

        register("video.blender_scene", "Blender Scene Render", "video",
                "3D scene batch render via Blender",
                List.of(new EffectParameterSchema("blendFile", "string", "scene.blend", null, null, "Blend file path")),
                List.of("blender"), Map.of("blendFile", "scene.blend"),
                List.of("TEAM", "ENTERPRISE"));

        register("video.chromatic", "Chromatic Aberration", "video", "RGB channel offset",
                List.of(new EffectParameterSchema("offset", "int", 3, 1, 10, "Pixel offset")),
                List.of("ofx"), Map.of("offset", "3"));

        register("video.brightness", "Brightness", "video", "Adjust brightness",
                List.of(new EffectParameterSchema("value", "float", 1.2, 0.5, 2.0, "Brightness multiplier")),
                List.of("javacv", "ofx"), Map.of("value", "1.2"));

        register("video.contrast", "Contrast", "video", "Adjust contrast",
                List.of(new EffectParameterSchema("value", "float", 1.1, 0.5, 2.0, "Contrast multiplier")),
                List.of("javacv", "ofx"), Map.of("value", "1.1"));

        register("video.grayscale", "Grayscale", "video", "Convert to grayscale",
                List.of(), List.of("javacv", "ofx"), Map.of());

        register("video.sepia", "Sepia", "video", "Sepia tone",
                List.of(new EffectParameterSchema("intensity", "float", 0.8, 0.0, 1.0, "Sepia intensity")),
                List.of("javacv", "ofx"), Map.of("intensity", "0.8"));

        register("video.watermark", "Watermark", "video", "Overlay watermark image",
                List.of(
                        new EffectParameterSchema("imageUrl", "string", "", null, null, "Watermark image URL"),
                        new EffectParameterSchema("position", "string", "bottom-right", null, null, "Position"),
                        new EffectParameterSchema("opacity", "float", 0.5, 0.0, 1.0, "Opacity")
                ), List.of("javacv", "ofx"), Map.of("opacity", "0.5"));

        // Text effects
        register("text.subtitle_burn_in", "Subtitle Burn-in", "text", "Burn subtitles into video",
                List.of(
                        new EffectParameterSchema("text", "string", "", null, null, "Subtitle text"),
                        new EffectParameterSchema("position", "string", "bottom", null, null, "Position (top/bottom/center)"),
                        new EffectParameterSchema("fontSize", "int", 24, 8, 72, "Font size")
                ), List.of("ofx", "javacv"), Map.of("position", "bottom"));

        register("text.overlay", "Text Overlay", "text", "Overlay text on video",
                List.of(
                        new EffectParameterSchema("text", "string", "", null, null, "Text content"),
                        new EffectParameterSchema("position", "string", "center", null, null, "Position")
                ), List.of("ofx"), Map.of("position", "center"));

        // Audio effects
        register("audio.volume", "Volume", "audio", "Adjust audio volume",
                List.of(new EffectParameterSchema("gain", "float", 1.0, 0.0, 2.0, "Volume gain")),
                List.of("javacv", "ofx"), Map.of("gain", "1.0"));

        // Compositing
        register("video.overlay", "Overlay", "compositor", "Picture-in-picture overlay",
                List.of(
                        new EffectParameterSchema("sourceUrl", "string", "", null, null, "Overlay source URL"),
                        new EffectParameterSchema("position", "string", "top-right", null, null, "Position")
                ), List.of("ofx"), Map.of("position", "top-right"));

        register("video.pip", "Picture-in-Picture", "compositor", "PIP compositing",
                List.of(
                        new EffectParameterSchema("sourceUrl", "string", "", null, null, "PIP source URL"),
                        new EffectParameterSchema("scale", "float", 0.25, 0.1, 0.5, "Scale factor")
                ), List.of("ofx"), Map.of("scale", "0.25"));

        log.info("Registered {} standard effects", descriptors.size());
    }

    private void register(String key, String displayName, String category, String description,
                           List<EffectParameterSchema> params, List<String> providers, Map<String, Object> defaults) {
        register(key, displayName, category, description, params, providers, defaults,
                List.of("FREE", "PRO", "TEAM", "ENTERPRISE"));
    }

    private void register(String key, String displayName, String category, String description,
                           List<EffectParameterSchema> params, List<String> providers,
                           Map<String, Object> defaults, List<String> allowedTiers) {
        descriptors.put(key, new EffectDescriptor(key, displayName, category, description, params, providers,
                defaults, allowedTiers));
        for (String provider : providers) {
            mappings.computeIfAbsent(key, k -> new ArrayList<>())
                    .add(new EffectProviderMapping(key, provider, displayName, Map.of()));
        }
    }

    public Optional<EffectDescriptor> getDescriptor(String effectKey) {
        return Optional.ofNullable(descriptors.get(effectKey));
    }

    public List<EffectDescriptor> getAllDescriptors() {
        return List.copyOf(descriptors.values());
    }

    public List<EffectDescriptor> getDescriptorsByCategory(String category) {
        return descriptors.values().stream()
                .filter(d -> d.category().equals(category))
                .toList();
    }

    public List<EffectProviderMapping> getMappings(String effectKey) {
        return mappings.getOrDefault(effectKey, List.of());
    }

    public String resolveNativeName(String effectKey, String providerKey) {
        return mappings.getOrDefault(effectKey, List.of()).stream()
                .filter(m -> m.providerKey().equals(providerKey))
                .map(EffectProviderMapping::nativeName)
                .findFirst()
                .orElse(effectKey);
    }

    public List<String> getEffectKeysForProvider(String providerKey) {
        return mappings.entrySet().stream()
                .filter(e -> e.getValue().stream().anyMatch(m -> m.providerKey().equals(providerKey)))
                .map(Map.Entry::getKey)
                .toList();
    }

    /**
     * Merges catalog rows from the database (single source for API) into the in-memory registry.
     */
    public synchronized void reloadFromCatalog(List<EffectPackEffectDto> catalogEffects) {
        if (catalogEffects == null || catalogEffects.isEmpty()) {
            return;
        }
        for (EffectPackEffectDto row : catalogEffects) {
            List<EffectParameterSchema> params = new ArrayList<>();
            if (row.parameterSchema() != null) {
                row.parameterSchema().forEach((name, def) -> {
                    if (def instanceof Map<?, ?> raw) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> map = (Map<String, Object>) raw;
                        params.add(new EffectParameterSchema(
                                name,
                                map.get("type") != null ? map.get("type").toString() : "string",
                                map.get("defaultValue"),
                                map.get("min") instanceof Number n ? n.doubleValue() : null,
                                map.get("max") instanceof Number n ? n.doubleValue() : null,
                                map.get("description") != null ? map.get("description").toString() : ""));
                    }
                });
            }
            List<String> providers = row.providerMappings() != null ? row.providerMappings() : List.of("javacv");
            Map<String, Object> defaults = row.defaultValues() != null
                    ? new LinkedHashMap<>(row.defaultValues()) : Map.of();
            List<String> tiers = row.allowedTiers() != null && !row.allowedTiers().isEmpty()
                    ? row.allowedTiers() : List.of("FREE", "PRO", "TEAM", "ENTERPRISE");
            register(row.effectKey(), row.displayName(), row.category(), row.description(),
                    params, providers, defaults, tiers);
        }
        log.info("Effect catalog merged: {} descriptors in registry", descriptors.size());
    }
}
