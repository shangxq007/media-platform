package com.example.platform.render.app.timeline;

import com.example.platform.render.domain.timeline.ExternalRenderNode;
import com.example.platform.render.domain.timeline.TimelineAssetRef;
import com.example.platform.render.domain.timeline.TimelineAudioSpec;
import com.example.platform.render.domain.timeline.TimelineClip;
import com.example.platform.render.domain.timeline.TimelineExtensionsReader;
import com.example.platform.render.domain.timeline.TimelineOutputSpec;
import com.example.platform.render.domain.timeline.TimelineSpec;
import com.example.platform.render.domain.timeline.TimelineTextOverlay;
import com.example.platform.render.domain.timeline.TimelineTrack;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Adapts Internal Timeline JSON to {@link TimelineSpec} for {@link com.example.platform.render.app.planner.RenderPlannerService}.
 */
@Service
public class InternalTimelineAdapter {

    private final TimelineExtensionsReader extensionsReader;
    private final TimelineAssetUriResolver assetUriResolver;

    public InternalTimelineAdapter(TimelineExtensionsReader extensionsReader,
                                   TimelineAssetUriResolver assetUriResolver) {
        this.extensionsReader = extensionsReader;
        this.assetUriResolver = assetUriResolver;
    }

    public Optional<TimelineSpec> toSpec(String timelineJson) {
        if (timelineJson == null || timelineJson.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode root = InternalTimelineJson.parse(timelineJson);
            if (!InternalTimelineJson.isInternalTimeline(root)) {
                return Optional.empty();
            }
            return Optional.of(buildFromInternal(root));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private TimelineSpec buildFromInternal(JsonNode root) throws Exception {
        String id = InternalTimelineJson.timelineId(root);
        String name = root.path("name").asText(id);
        int fps = frameRate(root);
        TimelineOutputSpec output = parseOutput(root, fps);

        List<TimelineTrack> tracks = new ArrayList<>();
        JsonNode assetRegistry = root.path("assetRegistry").path("assets");
        JsonNode composition = root.path("composition");
        JsonNode trackNodes = composition.path("tracks");
        if (trackNodes.isArray()) {
            for (JsonNode trackNode : trackNodes) {
                tracks.add(parseTrack(trackNode, fps, assetRegistry));
            }
        }

        List<TimelineTextOverlay> overlays = parseSubtitleOverlays(composition.path("subtitleTracks"), fps);

        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("schemaVersion", InternalTimelineJson.schemaVersion(root));
        metadata.put("revision", String.valueOf(InternalTimelineJson.revision(root)));

        JsonNode renderGraph = root.path("renderGraph");
        if (renderGraph.has("finalComposer")) {
            metadata.put("platform.finalComposer", renderGraph.get("finalComposer").asText("auto"));
        }
        ObjectMapper mapper = InternalTimelineJson.mapper();
        if (renderGraph.has("externalRenderNodes") && renderGraph.get("externalRenderNodes").isArray()) {
            List<ExternalRenderNode> nodes = new ArrayList<>();
            for (JsonNode node : renderGraph.get("externalRenderNodes")) {
                nodes.add(parseExternalNode(node, fps));
            }
            metadata.put("platform.externalRenderNodes", mapper.writeValueAsString(nodes));
        }
        if (root.has("packaging") && root.get("packaging").isObject()) {
            Map<String, String> packaging = new LinkedHashMap<>();
            root.get("packaging").fields().forEachRemaining(e ->
                    packaging.put(e.getKey(), e.getValue().asText("")));
            metadata.put("platform.packaging", mapper.writeValueAsString(packaging));
        }
        preserveJsonBlob(metadata, InternalTimelineJson.META_TEMPLATES, root.get("templates"));
        preserveJsonBlob(metadata, InternalTimelineJson.META_STYLES, root.get("styles"));
        JsonNode layers = renderGraph.path("layers");
        if (layers.isArray() && !layers.isEmpty()) {
            preserveJsonBlob(metadata, InternalTimelineJson.META_RENDER_GRAPH_LAYERS, layers);
        }
        if (renderGraph.has("segmentPolicy")) {
            preserveJsonBlob(metadata, SegmentTimelinePlanner.META_SEGMENT_POLICY, renderGraph.get("segmentPolicy"));
        }
        syncStickerMetadataFromLayers(metadata, layers);
        extensionsReader.mergeIntoMetadata(metadata, extensionsReader.fromJsonRoot(root));

        return new TimelineSpec(id, name, null, tracks, overlays, output, 0, metadata);
    }

    private TimelineTrack parseTrack(JsonNode trackNode, int fps, JsonNode assetRegistry) {
        String trackId = trackNode.path("id").asText("track");
        TimelineTrack.TrackType type = parseTrackType(trackNode.path("type").asText("VIDEO"));
        List<TimelineClip> clips = new ArrayList<>();
        JsonNode clipsNode = trackNode.path("clips");
        if (clipsNode.isArray()) {
            for (JsonNode clipNode : clipsNode) {
                clips.add(parseClip(clipNode, fps, assetRegistry));
            }
        }
        int layer = trackNode.path("zIndex").asInt(0);
        return new TimelineTrack(trackId, trackId, type, layer, clips, false, false);
    }

    private TimelineClip parseClip(JsonNode clipNode, int fps, JsonNode assetRegistry) {
        String clipId = clipNode.path("id").asText("clip");
        String assetId = clipNode.path("assetId").asText("asset");
        String uri = assetUriResolver.resolve(clipNode, assetId, assetRegistry);
        TimelineAssetRef assetRef = TimelineAssetRef.of(assetId, uri);

        double timelineStart = rangeStartSec(clipNode.path("timelineRange"), fps);
        double assetIn = rangeStartSec(clipNode.path("sourceRange"), fps);
        double duration = rangeDurationSec(clipNode.path("timelineRange"), fps);
        double assetOut = assetIn + rangeDurationSec(clipNode.path("sourceRange"), fps);
        if (duration <= 0) {
            duration = assetOut - assetIn;
        }
        return TimelineClip.of(clipId, assetRef, timelineStart, assetIn, assetOut);
    }

    private ExternalRenderNode parseExternalNode(JsonNode node, int fps) {
        double start = rangeStartSec(node.path("timelineRange"), fps);
        double duration = rangeDurationSec(node.path("timelineRange"), fps);
        Map<String, Object> params = new LinkedHashMap<>();
        if (node.has("params") && node.get("params").isObject()) {
            node.get("params").fields().forEachRemaining(e ->
                    params.put(e.getKey(), jsonToObject(e.getValue())));
        }
        if (node.has("dependsOn") && node.get("dependsOn").isArray()) {
            List<String> deps = new ArrayList<>();
            node.get("dependsOn").forEach(d -> deps.add(d.asText()));
            params.put("dependsOn", deps);
        }
        return new ExternalRenderNode(
                node.path("id").asText("ext"),
                node.path("backend").asText("blender"),
                textOrNull(node, "templateId"),
                textOrNull(node, "graphId"),
                textOrNull(node, "attachToClipId"),
                start,
                duration,
                params,
                node.path("output").path("format").asText(null));
    }

    private List<TimelineTextOverlay> parseSubtitleOverlays(JsonNode subtitleTracks, int fps) {
        List<TimelineTextOverlay> overlays = new ArrayList<>();
        if (!subtitleTracks.isArray()) {
            return overlays;
        }
        for (JsonNode track : subtitleTracks) {
            String trackId = track.path("id").asText("sub");
            JsonNode cues = track.path("cues");
            if (!cues.isArray()) {
                continue;
            }
            for (JsonNode cue : cues) {
                String cueId = cue.path("id").asText(trackId + "_cue");
                double start = rangeStartSec(cue.path("timelineRange"), fps);
                double duration = rangeDurationSec(cue.path("timelineRange"), fps);
                overlays.add(TimelineTextOverlay.of(cueId, cue.path("text").asText(""), start, duration));
            }
        }
        return overlays;
    }

    private TimelineOutputSpec parseOutput(JsonNode root, int fps) {
        JsonNode outputs = root.path("outputs");
        if (outputs.isArray() && !outputs.isEmpty()) {
            JsonNode out = outputs.get(0);
            int w = out.path("width").asInt(root.path("project").path("width").asInt(1920));
            int h = out.path("height").asInt(root.path("project").path("height").asInt(1080));
            String format = out.path("container").asText(out.path("format").asText("mp4"));
            return outputSpec(format, w, h, fps);
        }
        JsonNode project = root.path("project");
        int w = project.path("width").asInt(1920);
        int h = project.path("height").asInt(1080);
        return outputSpec("mp4", w, h, fps);
    }

    private static int frameRate(JsonNode root) {
        JsonNode rate = root.path("project").path("frameRate");
        if (rate.has("num") && rate.has("den") && rate.get("den").asInt(1) > 0) {
            return rate.get("num").asInt(30) / rate.get("den").asInt(1);
        }
        return rate.asInt(30);
    }

    private static double rangeStartSec(JsonNode range, int fps) {
        if (range.isMissingNode()) {
            return 0;
        }
        JsonNode start = range.path("start");
        int frame = start.path("frame").asInt(0);
        int rate = fpsFromNode(start, fps);
        return frame / (double) rate;
    }

    private static double rangeDurationSec(JsonNode range, int fps) {
        if (range.isMissingNode()) {
            return 0;
        }
        JsonNode dur = range.path("duration");
        int frame = dur.path("frame").asInt(0);
        int rate = fpsFromNode(dur, fps);
        return frame / (double) rate;
    }

    private static int fpsFromNode(JsonNode node, int defaultFps) {
        JsonNode rate = node.path("rate");
        if (rate.has("num") && rate.has("den") && rate.get("den").asInt(1) > 0) {
            return Math.max(1, rate.get("num").asInt(defaultFps) / rate.get("den").asInt(1));
        }
        return defaultFps;
    }

    private static TimelineTrack.TrackType parseTrackType(String type) {
        return switch (type.toUpperCase()) {
            case "AUDIO" -> TimelineTrack.TrackType.AUDIO;
            case "SUBTITLE" -> TimelineTrack.TrackType.SUBTITLE;
            default -> TimelineTrack.TrackType.VIDEO;
        };
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v != null && !v.isNull() ? v.asText() : null;
    }

    private static TimelineOutputSpec outputSpec(String format, int w, int h, int fps) {
        return new TimelineOutputSpec(
                format, w + "x" + h, fps, "h264", 8000,
                TimelineAudioSpec.aacDefault(), "yuv420p");
    }

    private static Object jsonToObject(JsonNode node) {
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isNumber()) {
            return node.numberValue();
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        return node.toString();
    }

    private static void syncStickerMetadataFromLayers(Map<String, String> metadata, JsonNode layers) {
        if (layers == null || !layers.isArray()) {
            return;
        }
        ArrayNode stickers = InternalTimelineJson.mapper().createArrayNode();
        for (JsonNode layer : layers) {
            if (!"STICKER".equalsIgnoreCase(layer.path("kind").asText())) {
                continue;
            }
            if (!layer.has("stickers") || !layer.get("stickers").isArray()) {
                continue;
            }
            layer.get("stickers").forEach(stickers::add);
        }
        if (!stickers.isEmpty()) {
            preserveJsonBlob(metadata, "platform.stickers", stickers);
        }
    }

    private static void preserveJsonBlob(Map<String, String> metadata, String key, JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        try {
            metadata.put(key, InternalTimelineJson.mapper().writeValueAsString(node));
        } catch (Exception ignored) {
            // skip preservation
        }
    }
}
