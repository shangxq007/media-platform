package com.example.platform.render.domain.timeline.compile.remotion;

import java.util.*;

/**
 * Deterministic JSON serializer for RemotionInputProps.
 *
 * <p>Internal only — produces stable, deterministic JSON output.
 * No timestamps, no random UUIDs, no local paths.</p>
 *
 * <p>Uses manual JSON construction for determinism. If the codebase
 * has ObjectMapper conventions, this can be adapted.</p>
 */
public class RemotionInputPropsSerializer {

    /**
     * Serialize RemotionInputProps to deterministic JSON string.
     */
    public String serialize(RemotionInputProps props) {
        if (props == null) return "null";
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"schemaVersion\":").append(jsonString(props.schemaVersion()));
        sb.append(",\"composition\":").append(serializeComposition(props.composition()));
        sb.append(",\"timeline\":").append(serializeTimeline(props.timeline()));
        sb.append(",\"mediaAssets\":").append(serializeMediaAssets(props.mediaAssets()));
        sb.append(",\"captions\":").append(serializeCaptions(props.captions()));
        sb.append(",\"fonts\":").append(serializeFonts(props.fonts()));
        sb.append(",\"output\":").append(serializeOutput(props.output()));
        sb.append(",\"metadata\":").append(serializeMetadata(props.metadata()));
        sb.append("}");
        return sb.toString();
    }

    private String serializeComposition(RemotionCompositionSpec c) {
        if (c == null) return "null";
        return "{" +
                "\"compositionId\":" + jsonString(c.compositionId()) +
                ",\"width\":" + c.width() +
                ",\"height\":" + c.height() +
                ",\"fps\":" + c.fps() +
                ",\"durationInFrames\":" + c.durationInFrames() +
                ",\"durationSeconds\":" + c.durationSeconds() +
                "}";
    }

    private String serializeTimeline(RemotionTimelineSpec t) {
        if (t == null) return "null";
        StringBuilder sb = new StringBuilder();
        sb.append("{\"tracks\":[");
        if (t.tracks() != null) {
            for (int i = 0; i < t.tracks().size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(serializeTrack(t.tracks().get(i)));
            }
        }
        sb.append("],\"totalDurationSeconds\":").append(t.totalDurationSeconds()).append("}");
        return sb.toString();
    }

    private String serializeTrack(RemotionTrackSpec t) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"trackId\":").append(jsonString(t.trackId()));
        sb.append(",\"name\":").append(jsonString(t.name()));
        sb.append(",\"type\":").append(jsonString(t.type()));
        sb.append(",\"layer\":").append(t.layer());
        sb.append(",\"muted\":").append(t.muted());
        sb.append(",\"clips\":[");
        if (t.clips() != null) {
            for (int i = 0; i < t.clips().size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(serializeClip(t.clips().get(i)));
            }
        }
        sb.append("]}");
        return sb.toString();
    }

    private String serializeClip(RemotionClipSpec c) {
        return "{" +
                "\"clipId\":" + jsonString(c.clipId()) +
                ",\"assetId\":" + jsonString(c.assetId()) +
                ",\"startSeconds\":" + c.startSeconds() +
                ",\"durationSeconds\":" + c.durationSeconds() +
                ",\"assetInPoint\":" + c.assetInPoint() +
                ",\"assetOutPoint\":" + c.assetOutPoint() +
                "}";
    }

    private String serializeMediaAssets(List<RemotionMediaAssetSpec> assets) {
        if (assets == null) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < assets.size(); i++) {
            if (i > 0) sb.append(",");
            RemotionMediaAssetSpec a = assets.get(i);
            sb.append("{");
            sb.append("\"assetId\":").append(jsonString(a.assetId()));
            sb.append(",\"mediaType\":").append(jsonString(a.mediaType()));
            sb.append(",\"format\":").append(jsonString(a.format()));
            sb.append(",\"durationSeconds\":").append(a.durationSeconds());
            sb.append(",\"width\":").append(a.width());
            sb.append(",\"height\":").append(a.height());
            sb.append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    private String serializeCaptions(List<RemotionCaptionSpec> captions) {
        if (captions == null) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < captions.size(); i++) {
            if (i > 0) sb.append(",");
            RemotionCaptionSpec c = captions.get(i);
            sb.append("{");
            sb.append("\"captionLayerId\":").append(jsonString(c.captionLayerId()));
            sb.append(",\"text\":").append(jsonString(c.text()));
            sb.append(",\"startSeconds\":").append(c.startSeconds());
            sb.append(",\"endSeconds\":").append(c.endSeconds());
            sb.append(",\"fontFamily\":").append(jsonString(c.fontFamily()));
            sb.append(",\"fontSize\":").append(c.fontSize());
            sb.append(",\"color\":").append(jsonString(c.color()));
            sb.append(",\"positionX\":").append(jsonString(c.positionX()));
            sb.append(",\"positionY\":").append(jsonString(c.positionY()));
            if (c.backgroundColor() != null) {
                sb.append(",\"backgroundColor\":").append(jsonString(c.backgroundColor()));
            }
            sb.append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    private String serializeFonts(List<RemotionFontSpec> fonts) {
        if (fonts == null) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < fonts.size(); i++) {
            if (i > 0) sb.append(",");
            RemotionFontSpec f = fonts.get(i);
            sb.append("{");
            sb.append("\"family\":").append(jsonString(f.family()));
            sb.append(",\"weight\":").append(f.weight());
            sb.append(",\"style\":").append(jsonString(f.style()));
            if (f.safeFontRef() != null) {
                sb.append(",\"safeFontRef\":").append(jsonString(f.safeFontRef()));
            }
            sb.append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    private String serializeOutput(RemotionOutputSpec o) {
        if (o == null) return "null";
        return "{" +
                "\"outputProfile\":" + jsonString(o.outputProfile()) +
                ",\"width\":" + o.width() +
                ",\"height\":" + o.height() +
                ",\"fps\":" + o.fps() +
                ",\"container\":" + jsonString(o.container()) +
                ",\"codecIntent\":" + jsonString(o.codecIntent()) +
                "}";
    }

    private String serializeMetadata(Map<String, String> metadata) {
        if (metadata == null) return "{}";
        // Sort keys for determinism
        TreeMap<String, String> sorted = new TreeMap<>(metadata);
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : sorted.entrySet()) {
            if (!first) sb.append(",");
            sb.append(jsonString(entry.getKey())).append(":").append(jsonString(entry.getValue()));
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    private String jsonString(String value) {
        if (value == null) return "null";
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
