package com.example.platform.render.app.timeline;

import com.example.platform.render.domain.interchange.TimelineExtensions;
import com.example.platform.render.domain.interchange.TimelineExtensionsReader;
import com.example.platform.render.domain.interchange.TimelineOutputSpec;
import com.example.platform.render.domain.interchange.TimelineSpec;
import com.example.platform.render.domain.interchange.TimelineTextOverlay;
import com.example.platform.render.domain.legacy.TimelineClip;
import com.example.platform.render.domain.legacy.TimelineClipEffect;
import com.example.platform.render.domain.legacy.TimelineTrack;
import com.example.platform.render.domain.planning.ExternalRenderNode;
import com.example.platform.timeline.app.TimelineImportRequest;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * GCR-1 CORRECTION V2: render-side boundary adapter for canonical Timeline
 * import.
 *
 * <p>Maps the render interchange/legacy representation
 * ({@link TimelineSpec} + {@link TimelineExtensions}) into the typed
 * Timeline-owned {@link TimelineImportRequest} contract. This adapter performs
 * NO canonical construction, NO schema acceptance, NO serialization decisions:
 * it is a mechanical field translation. The CANONICAL MAPPING AND SEMANTIC
 * CONSTRUCTION AUTHORITY lives in timeline-module
 * ({@code TimelineImportService}), which this adapter feeds.</p>
 */
@Component
public class TimelineSpecImportAdapter {

    private final TimelineExtensionsReader extensionsReader;

    public TimelineSpecImportAdapter(TimelineExtensionsReader extensionsReader) {
        this.extensionsReader = extensionsReader;
    }

    /** Map a spec with extensions derived from its own metadata. */
    public TimelineImportRequest toRequest(TimelineSpec spec) {
        return toRequest(spec, extensionsReader.fromSpec(spec));
    }

    /** Map a spec with explicit extensions into the Timeline-owned import contract. */
    public TimelineImportRequest toRequest(TimelineSpec spec, TimelineExtensions extensions) {
        Map<String, String> metadata = spec.metadata() != null
                ? new LinkedHashMap<>(spec.metadata()) : new LinkedHashMap<>();

        int revision = 1;
        if (metadata.containsKey("revision")) {
            try {
                revision = Integer.parseInt(metadata.get("revision"));
            } catch (NumberFormatException ignored) {
                revision = 1;
            }
        }

        TimelineOutputSpec out = spec.outputSpec();
        TimelineImportRequest.ImportOutput output = out != null
                ? new TimelineImportRequest.ImportOutput(
                        out.format(),
                        out.width() > 0 ? out.width() : 1920,
                        out.height() > 0 ? out.height() : 1080,
                        out.frameRate())
                : new TimelineImportRequest.ImportOutput("mp4", 1920, 1080, null);

        List<TimelineImportRequest.ImportTrack> tracks = new ArrayList<>();
        if (spec.tracks() != null) {
            for (TimelineTrack track : spec.tracks()) {
                List<TimelineImportRequest.ImportClip> clips = new ArrayList<>();
                if (track.clips() != null) {
                    for (TimelineClip clip : track.clips()) {
                        clips.add(new TimelineImportRequest.ImportClip(
                                clip.id(),
                                clip.assetRef() != null && clip.assetRef().assetId() != null
                                        ? clip.assetRef().assetId() : null,
                                clip.assetRef() != null ? clip.assetRef().storageUri() : null,
                                clip.assetRef() != null && clip.assetRef().width() > 0
                                        ? clip.assetRef().width() : 0,
                                clip.assetRef() != null && clip.assetRef().height() > 0
                                        ? clip.assetRef().height() : 0,
                                clip.timelineStart(),
                                clip.clipDuration(),
                                clip.assetInPoint(),
                                clip.assetOutPoint(),
                                mapEffects(clip.effects())));
                    }
                }
                tracks.add(new TimelineImportRequest.ImportTrack(
                        track.id(),
                        track.type() != null ? track.type().name() : "VIDEO",
                        track.layer(),
                        clips));
            }
        }

        List<TimelineImportRequest.ImportTextOverlay> overlays = new ArrayList<>();
        if (spec.textOverlays() != null) {
            for (TimelineTextOverlay overlay : spec.textOverlays()) {
                overlays.add(new TimelineImportRequest.ImportTextOverlay(
                        overlay.id(),
                        overlay.text(),
                        overlay.fontFamily(),
                        overlay.startTime(),
                        overlay.duration()));
            }
        }

        List<TimelineImportRequest.ImportExternalRenderNode> nodes = new ArrayList<>();
        if (extensions.externalRenderNodes() != null) {
            for (ExternalRenderNode node : extensions.externalRenderNodes()) {
                nodes.add(new TimelineImportRequest.ImportExternalRenderNode(
                        node.id(),
                        node.backend(),
                        node.templateId(),
                        node.graphId(),
                        node.attachToClipId(),
                        node.timelineStart(),
                        node.duration(),
                        node.params(),
                        node.intermediateFormat()));
            }
        }

        return new TimelineImportRequest(
                spec.id(),
                spec.name(),
                revision,
                output,
                tracks,
                overlays,
                preservedObject(spec, com.example.platform.timeline.app.InternalTimelineJson.META_STYLES),
                preservedObject(spec, com.example.platform.timeline.app.InternalTimelineJson.META_TEMPLATES),
                preservedArray(spec, com.example.platform.timeline.app.InternalTimelineJson.META_RENDER_GRAPH_LAYERS),
                preservedObject(spec, SegmentTimelinePlanner.META_SEGMENT_POLICY),
                "true".equals(metadata.get("platform.segmentPolicyEnabled")),
                nodes,
                extensions.finalComposer() != null ? extensions.finalComposer().name() : null,
                extensions.otioExportLossy(),
                extensions.packagingHints() != null
                        ? new LinkedHashMap<>(extensions.packagingHints()) : new LinkedHashMap<>(),
                metadata,
                spec.computeDuration() > 0 ? spec.computeDuration() : 30.0,
                List.of(), List.of());
    }

    private static List<TimelineImportRequest.ImportClipEffect> mapEffects(List<TimelineClipEffect> effects) {
        List<TimelineImportRequest.ImportClipEffect> mapped = new ArrayList<>();
        if (effects != null) {
            for (TimelineClipEffect effect : effects) {
                mapped.add(new TimelineImportRequest.ImportClipEffect(
                        effect.id(),
                        effect.effectKey(),
                        effect.parameters() != null
                                ? new LinkedHashMap<>(effect.parameters()) : new LinkedHashMap<>()));
            }
        }
        return mapped;
    }

    private static JsonNode preservedObject(TimelineSpec spec, String metadataKey) {
        JsonNode node = preservedJson(spec, metadataKey);
        return node != null && node.isObject() ? node.deepCopy() : null;
    }

    private static JsonNode preservedArray(TimelineSpec spec, String metadataKey) {
        JsonNode node = preservedJson(spec, metadataKey);
        return node != null && node.isArray() ? node.deepCopy() : null;
    }

    private static JsonNode preservedJson(TimelineSpec spec, String metadataKey) {
        if (spec.metadata() == null || !spec.metadata().containsKey(metadataKey)) {
            return null;
        }
        try {
            return com.example.platform.timeline.app.InternalTimelineJson.mapper()
                    .readTree(spec.metadata().get(metadataKey));
        } catch (Exception e) {
            return null;
        }
    }
}
