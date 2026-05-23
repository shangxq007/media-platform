package com.example.platform.render.infrastructure.timeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

/**
 * Converts editor timeline JSON (Pinia {@code timelineStore}) into OTIO-style JSON
 * consumed by {@link com.example.platform.render.domain.timeline.TimelineScriptParser}.
 */
@Component
public class EditorTimelineConverter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public String toOtioJson(String editorPayload) {
        try {
            JsonNode root = MAPPER.readTree(editorPayload);
            if (root.has("tracks") && root.get("tracks").isArray()) {
                JsonNode firstTrack = root.get("tracks").get(0);
                if (firstTrack != null && firstTrack.has("children")) {
                    return editorPayload;
                }
            }
            return convertEditorToOtio(root);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid editor timeline JSON: " + e.getMessage(), e);
        }
    }

    private String convertEditorToOtio(JsonNode editor) throws Exception {
        ObjectNode otio = MAPPER.createObjectNode();
        otio.put("id", editor.path("id").asText("timeline"));
        otio.put("name", editor.path("name").asText("Editor Timeline"));
        otio.put("profile", "default_1080p");

        JsonNode clipsIndex = buildClipIndex(editor.get("clips"));
        ArrayNode tracks = otio.putArray("tracks");

        JsonNode editorTracks = editor.get("tracks");
        if (editorTracks != null && editorTracks.isArray()) {
            for (JsonNode track : editorTracks) {
                ObjectNode trackNode = tracks.addObject();
                trackNode.put("id", track.path("id").asText("track"));
                trackNode.put("name", track.path("name").asText("Track"));
                trackNode.put("type", mapTrackType(track.path("type").asText("video")));

                ArrayNode children = trackNode.putArray("children");
                JsonNode trackClips = track.get("clips");
                if (trackClips != null && trackClips.isArray()) {
                    for (JsonNode tc : trackClips) {
                        JsonNode clipMeta = clipsIndex.get(tc.path("clipId").asText(""));
                        if (clipMeta == null) {
                            continue;
                        }
                        ObjectNode child = children.addObject();
                        child.put("id", tc.path("id").asText("clip"));
                        String mediaRef = resolveMediaReference(clipMeta);
                        if (!mediaRef.isBlank()) {
                            child.put("media_reference", mediaRef);
                        }
                        ObjectNode sourceRange = child.putObject("source_range");
                        double clipStart = tc.path("clipStart").asDouble(0);
                        double clipEnd = tc.path("clipEnd").asDouble(clipStart + tc.path("duration").asDouble(10));
                        sourceRange.put("start_time", clipStart);
                        sourceRange.put("duration", Math.max(0.1, clipEnd - clipStart));
                        child.put("timeline_start", tc.path("start").asDouble(0));

                        if (tc.has("effects") && tc.get("effects").isArray()) {
                            child.set("effects", tc.get("effects"));
                        }
                    }
                }
            }
        }

        return MAPPER.writeValueAsString(otio);
    }

    private JsonNode buildClipIndex(JsonNode clips) {
        ObjectNode index = MAPPER.createObjectNode();
        if (clips == null || !clips.isArray()) {
            return index;
        }
        for (JsonNode clip : clips) {
            index.set(clip.path("id").asText(), clip);
        }
        return index;
    }

    private String resolveMediaReference(JsonNode clipMeta) {
        if (clipMeta.has("sourceUrl") && !clipMeta.get("sourceUrl").asText("").isBlank()) {
            String url = clipMeta.get("sourceUrl").asText();
            return url.startsWith("file://") || url.startsWith("/") || url.contains("://")
                    ? url : "file://" + url;
        }
        if (clipMeta.has("storageUri")) {
            return clipMeta.get("storageUri").asText();
        }
        return "";
    }

    private String mapTrackType(String editorType) {
        return switch (editorType.toLowerCase()) {
            case "audio" -> "AUDIO";
            case "subtitle", "text" -> "SUBTITLE";
            default -> "VIDEO";
        };
    }
}
