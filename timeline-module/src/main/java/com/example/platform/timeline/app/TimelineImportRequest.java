package com.example.platform.timeline.app;

import com.example.platform.fonttext.typography.FontFamilyName;
import com.example.platform.shared.time.FrameRate;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;

/**
 * GCR-1 CORRECTION V2: typed Timeline-owned import contract.
 *
 * <p>External / editor / OTIO / legacy representations are adapted into this
 * contract by a boundary adapter (render-side {@code TimelineSpecImportAdapter});
 * the CANONICAL MAPPING AND SEMANTIC CONSTRUCTION AUTHORITY then lives in
 * {@link TimelineImportService}, which constructs Internal Timeline Schema 1.0
 * from this request and runs the canonical gate (E1b) before returning.</p>
 *
 * <p>Pure data: no render-domain types, no repository/network/current-time
 * access. Time values are double seconds at the legacy input boundary; the
 * canonical frame quantization (round-half-up at the exact rational rate) is
 * decided by {@link TimelineImportService}, never by the adapter.</p>
 */
public record TimelineImportRequest(
        String id,
        String name,
        int revision,
        ImportOutput output,
        List<ImportTrack> tracks,
        List<ImportTextOverlay> textOverlays,
        JsonNode styles,
        JsonNode templates,
        JsonNode renderGraphLayers,
        JsonNode segmentPolicy,
        boolean segmentPolicyEnabled,
        List<ImportExternalRenderNode> externalRenderNodes,
        String finalComposer,
        boolean otioExportLossy,
        Map<String, String> packagingHints,
        Map<String, String> metadata,
        double durationSec,
        List<ImportTransition> transitions,
        List<ImportAutomationCurve> automations) {

    /** Backward-compatible convenience constructor: no transitions/automations. */
    public TimelineImportRequest(
            String id,
            String name,
            int revision,
            ImportOutput output,
            List<ImportTrack> tracks,
            List<ImportTextOverlay> textOverlays,
            JsonNode styles,
            JsonNode templates,
            JsonNode renderGraphLayers,
            JsonNode segmentPolicy,
            boolean segmentPolicyEnabled,
            List<ImportExternalRenderNode> externalRenderNodes,
            String finalComposer,
            boolean otioExportLossy,
            Map<String, String> packagingHints,
            Map<String, String> metadata,
            double durationSec) {
        this(id, name, revision, output, tracks, textOverlays, styles, templates,
                renderGraphLayers, segmentPolicy, segmentPolicyEnabled, externalRenderNodes,
                finalComposer, otioExportLossy, packagingHints, metadata, durationSec,
                List.of(), List.of());
    }

    /** Output specification of the imported timeline. */
    public record ImportOutput(
            String format,
            int width,
            int height,
            FrameRate frameRate) {}

    /** A single track in the imported composition. */
    public record ImportTrack(
            String id,
            String type,
            int zIndex,
            List<ImportClip> clips) {}

    /** A single clip in an imported track. */
    public record ImportClip(
            String id,
            String assetId,
            String storageUri,
            int width,
            int height,
            double timelineStartSec,
            double clipDurationSec,
            double assetInSec,
            double assetOutSec,
            List<ImportClipEffect> effects) {}

    /** A clip-level effect (editor / OTIO shape). */
    public record ImportClipEffect(
            String id,
            String effectKey,
            Map<String, Object> parameters) {}

    /** A text overlay (subtitle cue) in the imported timeline. */
    public record ImportTextOverlay(
            String id,
            String text,
            FontFamilyName fontFamily,
            double startTimeSec,
            double durationSec) {}

    /** An external render node attached to the imported timeline. */
    public record ImportExternalRenderNode(
            String id,
            String backend,
            String templateId,
            String graphId,
            String attachToClipId,
            double timelineStartSec,
            double durationSec,
            Map<String, Object> params,
            String intermediateFormat) {}

    /**
     * First-class transition relationship (EFECT_TRANSITION_CANONICALIZATION_V1
     * C9): typed participants, exact MediaTime duration, alignment, temporal policy.
     */
    public record ImportTransition(
            String id,
            String definitionId,
            String definitionVersion,
            String outgoingClipId,
            String incomingClipId,
            String mediaType,
            long durationTicks,
            long durationTimeScale,
            String alignment,
            String temporalPolicy,
            Map<String, String> parameters) {}

    /**
     * Timeline-authored automation curve (EFFECT_TRANSITION_CANONICALIZATION_V1
     * C7/C8): exact MediaTime keyframes, deterministic ordering, HOLD/LINEAR.
     */
    public record ImportAutomationCurve(
            String automationId,
            String targetEntityId,
            String parameterPath,
            String valueType,
            String extrapolation,
            List<ImportAutomationKeyframe> keyframes) {}

    /** Keyframe: stable id, exact MediaTime, typed value, interpolation. */
    public record ImportAutomationKeyframe(
            String keyframeId,
            long timeTicks,
            long timeTimeScale,
            double value,
            String interpolation) {}
}
