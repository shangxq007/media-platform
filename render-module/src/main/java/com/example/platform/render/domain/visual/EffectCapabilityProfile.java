package com.example.platform.render.domain.visual;

import java.util.List;
import java.util.Map;

/**
 * Effect capability profile — bounded set of effect capabilities.
 * Immutable. Internal domain model.
 *
 * <p>Classifies effect capabilities into production candidates, POC candidates,
 * and forbidden/restricted capabilities.</p>
 */
public final class EffectCapabilityProfile {

    private EffectCapabilityProfile() {}

    // --- Production / Baseline Candidates ---

    public static VisualCapabilityDefinition scale() {
        return definition("SCALE", VisualCapabilityCategory.TRANSFORM,
                VisualCapabilityStatus.PRODUCTION, "Scale",
                "Scale video to target resolution",
                VisualConsistencyLevel.EXACT, VisualFallbackBehavior.NO_FALLBACK,
                VisualCapabilitySafetyLevel.SAFE);
    }

    public static VisualCapabilityDefinition crop() {
        return definition("CROP", VisualCapabilityCategory.TRANSFORM,
                VisualCapabilityStatus.PRODUCTION, "Crop",
                "Crop video to target region",
                VisualConsistencyLevel.EXACT, VisualFallbackBehavior.NO_FALLBACK,
                VisualCapabilitySafetyLevel.SAFE);
    }

    public static VisualCapabilityDefinition fit() {
        return definition("FIT", VisualCapabilityCategory.TRANSFORM,
                VisualCapabilityStatus.PRODUCTION, "Fit",
                "Fit video within target dimensions maintaining aspect ratio",
                VisualConsistencyLevel.EXACT, VisualFallbackBehavior.NO_FALLBACK,
                VisualCapabilitySafetyLevel.SAFE);
    }

    public static VisualCapabilityDefinition fill() {
        return definition("FILL", VisualCapabilityCategory.TRANSFORM,
                VisualCapabilityStatus.PRODUCTION, "Fill",
                "Fill target dimensions, cropping if needed",
                VisualConsistencyLevel.EXACT, VisualFallbackBehavior.NO_FALLBACK,
                VisualCapabilitySafetyLevel.SAFE);
    }

    public static VisualCapabilityDefinition contain() {
        return definition("CONTAIN", VisualCapabilityCategory.TRANSFORM,
                VisualCapabilityStatus.PRODUCTION, "Contain",
                "Contain video within target dimensions with letterboxing",
                VisualConsistencyLevel.EXACT, VisualFallbackBehavior.NO_FALLBACK,
                VisualCapabilitySafetyLevel.SAFE);
    }

    public static VisualCapabilityDefinition rotate() {
        return definition("ROTATE", VisualCapabilityCategory.TRANSFORM,
                VisualCapabilityStatus.PRODUCTION, "Rotate",
                "Rotate video by specified angle",
                VisualConsistencyLevel.EXACT, VisualFallbackBehavior.NO_FALLBACK,
                VisualCapabilitySafetyLevel.SAFE);
    }

    public static VisualCapabilityDefinition opacity() {
        return definition("OPACITY", VisualCapabilityCategory.EFFECT,
                VisualCapabilityStatus.PRODUCTION, "Opacity",
                "Adjust video opacity",
                VisualConsistencyLevel.EXACT, VisualFallbackBehavior.NO_FALLBACK,
                VisualCapabilitySafetyLevel.SAFE);
    }

    public static VisualCapabilityDefinition fadeIn() {
        return definition("FADE_IN", VisualCapabilityCategory.EFFECT,
                VisualCapabilityStatus.PRODUCTION, "Fade In",
                "Fade video from transparent to opaque",
                VisualConsistencyLevel.EXACT, VisualFallbackBehavior.NO_FALLBACK,
                VisualCapabilitySafetyLevel.SAFE);
    }

    public static VisualCapabilityDefinition fadeOut() {
        return definition("FADE_OUT", VisualCapabilityCategory.EFFECT,
                VisualCapabilityStatus.PRODUCTION, "Fade Out",
                "Fade video from opaque to transparent",
                VisualConsistencyLevel.EXACT, VisualFallbackBehavior.NO_FALLBACK,
                VisualCapabilitySafetyLevel.SAFE);
    }

    public static VisualCapabilityDefinition textOverlay() {
        return definition("TEXT_OVERLAY", VisualCapabilityCategory.OVERLAY,
                VisualCapabilityStatus.PRODUCTION, "Text Overlay",
                "Render text overlay on video",
                VisualConsistencyLevel.EXACT, VisualFallbackBehavior.NO_FALLBACK,
                VisualCapabilitySafetyLevel.SAFE);
    }

    public static VisualCapabilityDefinition imageOverlay() {
        return definition("IMAGE_OVERLAY", VisualCapabilityCategory.OVERLAY,
                VisualCapabilityStatus.PRODUCTION, "Image Overlay",
                "Render image overlay on video",
                VisualConsistencyLevel.EXACT, VisualFallbackBehavior.NO_FALLBACK,
                VisualCapabilitySafetyLevel.SAFE);
    }

    public static VisualCapabilityDefinition captionOverlay() {
        return definition("CAPTION_OVERLAY", VisualCapabilityCategory.CAPTION,
                VisualCapabilityStatus.PRODUCTION, "Caption Overlay",
                "Burn-in caption/subtitle overlay using FFmpeg/libass",
                VisualConsistencyLevel.EXACT, VisualFallbackBehavior.NO_FALLBACK,
                VisualCapabilitySafetyLevel.SAFE);
    }

    public static VisualCapabilityDefinition watermarkOverlay() {
        return definition("WATERMARK_OVERLAY", VisualCapabilityCategory.WATERMARK,
                VisualCapabilityStatus.PRODUCTION, "Watermark Overlay",
                "Render watermark overlay on video",
                VisualConsistencyLevel.EXACT, VisualFallbackBehavior.NO_FALLBACK,
                VisualCapabilitySafetyLevel.SAFE);
    }

    // --- POC Candidates ---

    public static VisualCapabilityDefinition blur() {
        return definition("BLUR", VisualCapabilityCategory.EFFECT,
                VisualCapabilityStatus.POC, "Blur",
                "Apply blur effect to video",
                VisualConsistencyLevel.APPROX, VisualFallbackBehavior.DISABLE_EFFECT,
                VisualCapabilitySafetyLevel.VALIDATED);
    }

    public static VisualCapabilityDefinition colorAdjust() {
        return definition("COLOR_ADJUST", VisualCapabilityCategory.COLOR,
                VisualCapabilityStatus.POC, "Color Adjust",
                "Adjust color properties of video",
                VisualConsistencyLevel.APPROX, VisualFallbackBehavior.DISABLE_EFFECT,
                VisualCapabilitySafetyLevel.VALIDATED);
    }

    public static VisualCapabilityDefinition brightness() {
        return definition("BRIGHTNESS", VisualCapabilityCategory.COLOR,
                VisualCapabilityStatus.POC, "Brightness",
                "Adjust video brightness",
                VisualConsistencyLevel.APPROX, VisualFallbackBehavior.DISABLE_EFFECT,
                VisualCapabilitySafetyLevel.VALIDATED);
    }

    public static VisualCapabilityDefinition contrast() {
        return definition("CONTRAST", VisualCapabilityCategory.COLOR,
                VisualCapabilityStatus.POC, "Contrast",
                "Adjust video contrast",
                VisualConsistencyLevel.APPROX, VisualFallbackBehavior.DISABLE_EFFECT,
                VisualCapabilitySafetyLevel.VALIDATED);
    }

    public static VisualCapabilityDefinition saturation() {
        return definition("SATURATION", VisualCapabilityCategory.COLOR,
                VisualCapabilityStatus.POC, "Saturation",
                "Adjust video saturation",
                VisualConsistencyLevel.APPROX, VisualFallbackBehavior.DISABLE_EFFECT,
                VisualCapabilitySafetyLevel.VALIDATED);
    }

    public static VisualCapabilityDefinition volumeAdjust() {
        return definition("VOLUME_ADJUST", VisualCapabilityCategory.AUDIO,
                VisualCapabilityStatus.POC, "Volume Adjust",
                "Adjust audio volume",
                VisualConsistencyLevel.EXACT, VisualFallbackBehavior.NO_FALLBACK,
                VisualCapabilitySafetyLevel.VALIDATED);
    }

    public static VisualCapabilityDefinition audioFadeIn() {
        return definition("AUDIO_FADE_IN", VisualCapabilityCategory.AUDIO,
                VisualCapabilityStatus.POC, "Audio Fade In",
                "Fade audio from silent to audible",
                VisualConsistencyLevel.EXACT, VisualFallbackBehavior.NO_FALLBACK,
                VisualCapabilitySafetyLevel.VALIDATED);
    }

    public static VisualCapabilityDefinition audioFadeOut() {
        return definition("AUDIO_FADE_OUT", VisualCapabilityCategory.AUDIO,
                VisualCapabilityStatus.POC, "Audio Fade Out",
                "Fade audio from audible to silent",
                VisualConsistencyLevel.EXACT, VisualFallbackBehavior.NO_FALLBACK,
                VisualCapabilitySafetyLevel.VALIDATED);
    }

    public static VisualCapabilityDefinition pictureInPicture() {
        return definition("PICTURE_IN_PICTURE", VisualCapabilityCategory.OVERLAY,
                VisualCapabilityStatus.POC, "Picture in Picture",
                "Render small video overlay on main video",
                VisualConsistencyLevel.APPROX, VisualFallbackBehavior.DISABLE_EFFECT,
                VisualCapabilitySafetyLevel.VALIDATED);
    }

    public static VisualCapabilityDefinition backgroundBlur() {
        return definition("BACKGROUND_BLUR", VisualCapabilityCategory.EFFECT,
                VisualCapabilityStatus.POC, "Background Blur",
                "Blur the background of a video",
                VisualConsistencyLevel.PROVIDER_SPECIFIC, VisualFallbackBehavior.DISABLE_EFFECT,
                VisualCapabilitySafetyLevel.VALIDATED);
    }

    // --- Forbidden / Restricted ---

    public static VisualCapabilityDefinition arbitraryFfmpegFiltergraph() {
        return definition("ARBITRARY_FFMPEG_FILTERGRAPH", VisualCapabilityCategory.EFFECT,
                VisualCapabilityStatus.FORBIDDEN, "Arbitrary FFmpeg Filtergraph",
                "Arbitrary FFmpeg filtergraph — FORBIDDEN",
                VisualConsistencyLevel.FORBIDDEN, VisualFallbackBehavior.REJECT_REQUEST,
                VisualCapabilitySafetyLevel.FORBIDDEN);
    }

    public static VisualCapabilityDefinition arbitraryShader() {
        return definition("ARBITRARY_SHADER", VisualCapabilityCategory.EFFECT,
                VisualCapabilityStatus.FORBIDDEN, "Arbitrary Shader",
                "Arbitrary GPU shader — FORBIDDEN",
                VisualConsistencyLevel.FORBIDDEN, VisualFallbackBehavior.REJECT_REQUEST,
                VisualCapabilitySafetyLevel.FORBIDDEN);
    }

    public static VisualCapabilityDefinition arbitraryScriptEffect() {
        return definition("ARBITRARY_SCRIPT_EFFECT", VisualCapabilityCategory.EFFECT,
                VisualCapabilityStatus.FORBIDDEN, "Arbitrary Script Effect",
                "Arbitrary script-defined effect — FORBIDDEN",
                VisualConsistencyLevel.FORBIDDEN, VisualFallbackBehavior.REJECT_REQUEST,
                VisualCapabilitySafetyLevel.FORBIDDEN);
    }

    public static VisualCapabilityDefinition arbitraryOfxPlugin() {
        return definition("ARBITRARY_OFX_PLUGIN", VisualCapabilityCategory.EFFECT,
                VisualCapabilityStatus.FORBIDDEN, "Arbitrary OFX Plugin",
                "Arbitrary OFX plugin execution — FORBIDDEN",
                VisualConsistencyLevel.FORBIDDEN, VisualFallbackBehavior.REJECT_REQUEST,
                VisualCapabilitySafetyLevel.FORBIDDEN);
    }

    public static VisualCapabilityDefinition natronNodeGraph() {
        return definition("NATRON_NODE_GRAPH", VisualCapabilityCategory.EFFECT,
                VisualCapabilityStatus.FORBIDDEN, "Natron Node Graph",
                "Arbitrary Natron node graph — FORBIDDEN",
                VisualConsistencyLevel.FORBIDDEN, VisualFallbackBehavior.REJECT_REQUEST,
                VisualCapabilitySafetyLevel.FORBIDDEN);
    }

    public static VisualCapabilityDefinition blenderCompositorGraph() {
        return definition("BLENDER_COMPOSITOR_GRAPH", VisualCapabilityCategory.EFFECT,
                VisualCapabilityStatus.FORBIDDEN, "Blender Compositor Graph",
                "Arbitrary Blender compositor graph — FORBIDDEN",
                VisualConsistencyLevel.FORBIDDEN, VisualFallbackBehavior.REJECT_REQUEST,
                VisualCapabilitySafetyLevel.FORBIDDEN);
    }

    public static VisualCapabilityDefinition remotionComponentExecution() {
        return definition("REMOTION_COMPONENT_EXECUTION", VisualCapabilityCategory.EFFECT,
                VisualCapabilityStatus.FORBIDDEN, "Remotion Component Execution",
                "Remotion component execution — FORBIDDEN (non-executable)",
                VisualConsistencyLevel.FORBIDDEN, VisualFallbackBehavior.REJECT_REQUEST,
                VisualCapabilitySafetyLevel.FORBIDDEN);
    }

    public static VisualCapabilityDefinition userDefinedRenderDag() {
        return definition("USER_DEFINED_RENDER_DAG", VisualCapabilityCategory.EFFECT,
                VisualCapabilityStatus.FORBIDDEN, "User-defined Render DAG",
                "User-submitted render DAG — FORBIDDEN",
                VisualConsistencyLevel.FORBIDDEN, VisualFallbackBehavior.REJECT_REQUEST,
                VisualCapabilitySafetyLevel.FORBIDDEN);
    }

    public static VisualCapabilityDefinition pluginInsertedRenderNode() {
        return definition("PLUGIN_INSERTED_RENDER_NODE", VisualCapabilityCategory.EFFECT,
                VisualCapabilityStatus.FORBIDDEN, "Plugin-inserted Render Node",
                "Plugin-inserted execution node — FORBIDDEN",
                VisualConsistencyLevel.FORBIDDEN, VisualFallbackBehavior.REJECT_REQUEST,
                VisualCapabilitySafetyLevel.FORBIDDEN);
    }

    public static VisualCapabilityDefinition providerSpecificRawCommand() {
        return definition("PROVIDER_SPECIFIC_RAW_COMMAND", VisualCapabilityCategory.EFFECT,
                VisualCapabilityStatus.FORBIDDEN, "Provider-specific Raw Command",
                "Provider-specific raw command — FORBIDDEN",
                VisualConsistencyLevel.FORBIDDEN, VisualFallbackBehavior.REJECT_REQUEST,
                VisualCapabilitySafetyLevel.FORBIDDEN);
    }

    // --- All capabilities ---

    /**
     * Returns all defined effect capabilities.
     */
    public static List<VisualCapabilityDefinition> all() {
        return List.of(
                scale(), crop(), fit(), fill(), contain(), rotate(),
                opacity(), fadeIn(), fadeOut(),
                textOverlay(), imageOverlay(), captionOverlay(), watermarkOverlay(),
                blur(), colorAdjust(), brightness(), contrast(), saturation(),
                volumeAdjust(), audioFadeIn(), audioFadeOut(),
                pictureInPicture(), backgroundBlur(),
                arbitraryFfmpegFiltergraph(), arbitraryShader(), arbitraryScriptEffect(),
                arbitraryOfxPlugin(), natronNodeGraph(), blenderCompositorGraph(),
                remotionComponentExecution(), userDefinedRenderDag(),
                pluginInsertedRenderNode(), providerSpecificRawCommand());
    }

    /**
     * Returns production-allowed capabilities.
     */
    public static List<VisualCapabilityDefinition> productionAllowed() {
        return all().stream().filter(VisualCapabilityDefinition::isProductionAllowed).toList();
    }

    /**
     * Returns forbidden capabilities.
     */
    public static List<VisualCapabilityDefinition> forbidden() {
        return all().stream().filter(d -> d.status() == VisualCapabilityStatus.FORBIDDEN).toList();
    }

    // --- Helpers ---

    private static VisualCapabilityDefinition definition(
            String id, VisualCapabilityCategory category, VisualCapabilityStatus status,
            String displayName, String description,
            VisualConsistencyLevel consistency, VisualFallbackBehavior fallback,
            VisualCapabilitySafetyLevel safety) {
        return new VisualCapabilityDefinition(
                new VisualCapabilityId(id), category, status,
                displayName, description, consistency, fallback, safety,
                List.of(), List.of(), Map.of());
    }
}
