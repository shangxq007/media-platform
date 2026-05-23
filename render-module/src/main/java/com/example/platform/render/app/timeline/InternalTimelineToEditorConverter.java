package com.example.platform.render.app.timeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Converts Internal Timeline Schema 1.0 JSON into editor v2 JSON ({@code schemaVersion: 2.0.0}).
 */
@Service
public class InternalTimelineToEditorConverter {

    public String toEditorJson(String internalTimelineJson) {
        if (internalTimelineJson == null || internalTimelineJson.isBlank()) {
            throw new IllegalArgumentException("internalTimelineJson is required");
        }
        try {
            JsonNode root = InternalTimelineJson.parse(internalTimelineJson);
            if (!InternalTimelineJson.isInternalTimeline(root)) {
                throw new IllegalArgumentException("Not an Internal Timeline 1.0 document");
            }
            int fps = frameRate(root);
            ObjectNode editor = InternalTimelineJson.mapper().createObjectNode();
            editor.put("id", InternalTimelineJson.timelineId(root));
            editor.put("name", root.path("name").asText(editor.path("id").asText("timeline")));
            editor.put("schemaVersion", "2.0.0");
            editor.put("syncHint", "internal-to-editor-v2");
            editor.put("internalRevision", InternalTimelineJson.revision(root));

            double durationSec = projectDurationSec(root, fps);
            editor.put("duration", durationSec);
            editor.put("currentTime", 0);
            editor.put("zoom", 1);
            editor.put("playing", false);

            JsonNode assets = root.path("assetRegistry").path("assets");
            Map<String, ObjectNode> clipCatalog = new LinkedHashMap<>();
            ArrayNode editorClips = editor.putArray("clips");
            ArrayNode editorTracks = editor.putArray("tracks");

            JsonNode compositionTracks = root.path("composition").path("tracks");
            if (compositionTracks.isArray()) {
                for (JsonNode trackNode : compositionTracks) {
                    ObjectNode editorTrack = editorTracks.addObject();
                    String trackId = trackNode.path("id").asText("track");
                    editorTrack.put("id", trackId);
                    editorTrack.put("name", trackId);
                    editorTrack.put("type", mapTrackType(trackNode.path("type").asText("VIDEO")));
                    editorTrack.put("muted", false);
                    editorTrack.put("locked", false);
                    ArrayNode trackClips = editorTrack.putArray("clips");

                    JsonNode clipsNode = trackNode.path("clips");
                    if (clipsNode.isArray()) {
                        for (JsonNode clipNode : clipsNode) {
                            String assetId = clipNode.path("assetId").asText("asset");
                            ObjectNode catalogClip = clipCatalog.computeIfAbsent(
                                    assetId, id -> buildCatalogClip(id, assets, fps));
                            if (!containsClipId(editorClips, catalogClip.path("id").asText())) {
                                editorClips.add(catalogClip.deepCopy());
                            }

                            double timelineStart = rangeStartSec(clipNode.path("timelineRange"), fps);
                            double timelineDuration = rangeDurationSec(clipNode.path("timelineRange"), fps);
                            double sourceStart = rangeStartSec(clipNode.path("sourceRange"), fps);
                            double sourceDuration = rangeDurationSec(clipNode.path("sourceRange"), fps);
                            if (timelineDuration <= 0) {
                                timelineDuration = sourceDuration;
                            }
                            if (sourceDuration <= 0) {
                                sourceDuration = timelineDuration;
                            }

                            ObjectNode trackClip = trackClips.addObject();
                            trackClip.put("id", clipNode.path("id").asText("tc_" + assetId));
                            trackClip.put("clipId", catalogClip.path("id").asText(assetId));
                            trackClip.put("trackId", trackId);
                            trackClip.put("start", timelineStart);
                            trackClip.put("duration", Math.max(0.1, timelineDuration));
                            trackClip.put("clipStart", sourceStart);
                            trackClip.put("clipEnd", sourceStart + Math.max(0.1, sourceDuration));
                            if (clipNode.has("effects") && clipNode.get("effects").isArray()) {
                                trackClip.set("effects", clipNode.get("effects").deepCopy());
                            }
                        }
                    }
                }
            }

            if (editorTracks.isEmpty()) {
                editor.set("tracks", buildTracksFromRenderGraphLayers(root, assets, fps, clipCatalog, editorClips));
            }

            return InternalTimelineJson.write(editor);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to convert Internal Timeline to editor JSON", e);
        }
    }

    private static ArrayNode buildTracksFromRenderGraphLayers(
            JsonNode root, JsonNode assets, int fps, Map<String, ObjectNode> clipCatalog, ArrayNode editorClips) {
        ArrayNode editorTracks = InternalTimelineJson.mapper().createArrayNode();
        JsonNode layers = root.path("renderGraph").path("layers");
        if (!layers.isArray()) {
            return editorTracks;
        }
        for (JsonNode layer : layers) {
            ObjectNode editorTrack = editorTracks.addObject();
            String trackId = layer.path("id").asText("layer");
            editorTrack.put("id", trackId);
            editorTrack.put("name", trackId);
            editorTrack.put("type", "video");
            editorTrack.put("muted", false);
            editorTrack.put("locked", false);
            ArrayNode trackClips = editorTrack.putArray("clips");
            JsonNode clipsNode = layer.path("clips");
            if (!clipsNode.isArray()) {
                continue;
            }
            for (JsonNode clipNode : clipsNode) {
                String assetId = clipNode.path("assetId").asText("asset");
                ObjectNode catalogClip =
                        clipCatalog.computeIfAbsent(assetId, id -> buildCatalogClip(id, assets, fps));
                if (!containsClipId(editorClips, catalogClip.path("id").asText())) {
                    editorClips.add(catalogClip.deepCopy());
                }
                double timelineStart = rangeStartSec(clipNode.path("timelineRange"), fps);
                double timelineDuration = rangeDurationSec(clipNode.path("timelineRange"), fps);
                ObjectNode trackClip = trackClips.addObject();
                trackClip.put("id", clipNode.path("id").asText("tc_" + assetId));
                trackClip.put("clipId", catalogClip.path("id").asText(assetId));
                trackClip.put("trackId", trackId);
                trackClip.put("start", timelineStart);
                trackClip.put("duration", Math.max(0.1, timelineDuration));
                trackClip.put("clipStart", 0);
                trackClip.put("clipEnd", Math.max(0.1, timelineDuration));
            }
        }
        return editorTracks;
    }

    private static ObjectNode buildCatalogClip(String assetId, JsonNode assets, int fps) {
        ObjectNode clip = InternalTimelineJson.mapper().createObjectNode();
        clip.put("id", assetId);
        clip.put("name", assetId);
        JsonNode asset = assets.path(assetId);
        String kind = asset.path("kind").asText("VIDEO");
        clip.put("type", mapAssetKind(kind));
        String uri = asset.path("uri").asText("");
        if (!uri.isBlank()) {
            clip.put("storageUri", uri);
            if (uri.startsWith("http://") || uri.startsWith("https://") || uri.startsWith("s3://")) {
                clip.put("sourceUrl", uri);
            }
        }
        double durationSec = rangeDurationSec(asset.path("probe").path("duration"), fps);
        if (durationSec <= 0) {
            durationSec = 10;
        }
        clip.put("duration", durationSec);
        clip.put("startTime", 0);
        clip.put("endTime", durationSec);
        clip.putObject("metadata");
        return clip;
    }

    private static boolean containsClipId(ArrayNode clips, String clipId) {
        for (JsonNode clip : clips) {
            if (clipId.equals(clip.path("id").asText())) {
                return true;
            }
        }
        return false;
    }

    private static double projectDurationSec(JsonNode root, int fps) {
        JsonNode duration = root.path("project").path("duration");
        double sec = rangeDurationSec(duration, fps);
        if (sec > 0) {
            return sec;
        }
        double maxEnd = 0;
        JsonNode tracks = root.path("composition").path("tracks");
        if (tracks.isArray()) {
            for (JsonNode track : tracks) {
                JsonNode clips = track.path("clips");
                if (!clips.isArray()) {
                    continue;
                }
                for (JsonNode clip : clips) {
                    double start = rangeStartSec(clip.path("timelineRange"), fps);
                    double dur = rangeDurationSec(clip.path("timelineRange"), fps);
                    maxEnd = Math.max(maxEnd, start + dur);
                }
            }
        }
        return maxEnd > 0 ? maxEnd : 60;
    }

    private static String mapTrackType(String internalType) {
        return switch (internalType.toUpperCase()) {
            case "AUDIO" -> "audio";
            case "SUBTITLE", "TEXT" -> "subtitle";
            case "IMAGE" -> "image";
            default -> "video";
        };
    }

    private static String mapAssetKind(String kind) {
        return switch (kind.toUpperCase()) {
            case "AUDIO" -> "audio";
            case "IMAGE" -> "image";
            case "SUBTITLE", "TEXT" -> "subtitle";
            default -> "video";
        };
    }

    private static int frameRate(JsonNode root) {
        JsonNode rate = root.path("project").path("frameRate");
        if (rate.has("num") && rate.has("den") && rate.get("den").asInt(1) > 0) {
            return Math.max(1, rate.get("num").asInt(30) / rate.get("den").asInt(1));
        }
        return rate.asInt(30);
    }

    private static double rangeStartSec(JsonNode range, int fps) {
        if (range.isMissingNode() || range.isNull()) {
            return 0;
        }
        if (range.has("frame")) {
            int frame = range.path("frame").asInt(0);
            int rate = fpsFromNode(range.path("rate"), fps);
            return frame / (double) rate;
        }
        JsonNode start = range.path("start");
        int frame = start.path("frame").asInt(0);
        int rate = fpsFromNode(start.path("rate"), fps);
        return frame / (double) rate;
    }

    private static double rangeDurationSec(JsonNode range, int fps) {
        if (range.isMissingNode() || range.isNull()) {
            return 0;
        }
        if (range.has("frame") && !range.has("start")) {
            int frame = range.path("frame").asInt(0);
            int rate = fpsFromNode(range.path("rate"), fps);
            return frame / (double) rate;
        }
        JsonNode duration = range.path("duration");
        if (duration.isMissingNode()) {
            duration = range;
        }
        int frame = duration.path("frame").asInt(0);
        int rate = fpsFromNode(duration.path("rate"), fps);
        return frame / (double) rate;
    }

    private static int fpsFromNode(JsonNode rate, int defaultFps) {
        if (rate.has("num") && rate.has("den") && rate.get("den").asInt(1) > 0) {
            return Math.max(1, rate.get("num").asInt(defaultFps) / rate.get("den").asInt(1));
        }
        return defaultFps;
    }
}
