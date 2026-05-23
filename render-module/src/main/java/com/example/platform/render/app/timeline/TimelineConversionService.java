package com.example.platform.render.app.timeline;

import com.example.platform.render.domain.timeline.TimelineSpec;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Converts editor / OTIO / legacy timeline JSON into Internal Timeline Schema 1.0.
 */
@Service
public class TimelineConversionService {

    private final TimelineSpecResolver timelineSpecResolver;
    private final InternalTimelineWriter internalTimelineWriter;

    public TimelineConversionService(
            TimelineSpecResolver timelineSpecResolver, InternalTimelineWriter internalTimelineWriter) {
        this.timelineSpecResolver = timelineSpecResolver;
        this.internalTimelineWriter = internalTimelineWriter;
    }

    public String ensureInternalTimelineJson(String timelineJson) {
        if (timelineJson == null || timelineJson.isBlank()) {
            throw new IllegalArgumentException("timelineJson is required");
        }
        if (timelineSpecResolver.isInternalTimelineJson(timelineJson)) {
            return timelineJson;
        }
        Optional<TimelineSpec> spec = timelineSpecResolver.resolve(timelineJson);
        if (spec.isEmpty()) {
            throw new IllegalArgumentException("Unable to parse timeline JSON into TimelineSpec");
        }
        return internalTimelineWriter.toJson(spec.get());
    }

    public PreviewResult preview(String timelineJson) {
        String sourceSchema = detectSourceSchema(timelineJson);
        boolean alreadyInternal = timelineSpecResolver.isInternalTimelineJson(timelineJson);
        String internal = ensureInternalTimelineJson(timelineJson);
        PreviewSummary summary = summarize(timelineJson, internal, alreadyInternal);
        return new PreviewResult(internal, sourceSchema, alreadyInternal, summary);
    }

    private static String detectSourceSchema(String timelineJson) {
        try {
            JsonNode root = InternalTimelineJson.parse(timelineJson);
            if (InternalTimelineJson.isInternalTimeline(root)) {
                return "internal-1.0";
            }
            String version = root.path("schemaVersion").asText("");
            if (!version.isBlank()) {
                return "editor-" + version;
            }
            if (root.has("OTIO_SCHEMA") || root.has("tracks")) {
                return "otio-legacy";
            }
            return "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }

    private static PreviewSummary summarize(String source, String internal, boolean alreadyInternal) {
        try {
            JsonNode src = InternalTimelineJson.parse(source);
            JsonNode tgt = InternalTimelineJson.parse(internal);
            int srcTracks = countArray(src, "tracks", "layers");
            int tgtTracks = countArray(tgt, "tracks", "layers");
            int srcClips = countClips(src);
            int tgtClips = countClips(tgt);
            return new PreviewSummary(
                    alreadyInternal,
                    srcTracks,
                    tgtTracks,
                    srcClips,
                    tgtClips,
                    tgt.path("revision").asInt(0),
                    internal.length() - source.length());
        } catch (Exception e) {
            return new PreviewSummary(alreadyInternal, 0, 0, 0, 0, 0, internal.length() - source.length());
        }
    }

    private static int countArray(JsonNode root, String... fields) {
        for (String f : fields) {
            if (root.has(f) && root.get(f).isArray()) {
                return root.get(f).size();
            }
        }
        return 0;
    }

    private static int countClips(JsonNode root) {
        int n = 0;
        JsonNode layers = root.path("composition").path("layers");
        if (layers.isArray()) {
            for (JsonNode layer : layers) {
                if (layer.path("clips").isArray()) {
                    n += layer.get("clips").size();
                }
            }
        }
        JsonNode tracks = root.get("tracks");
        if (tracks != null && tracks.isArray()) {
            for (JsonNode track : tracks) {
                if (track.path("clips").isArray()) {
                    n += track.get("clips").size();
                }
            }
        }
        return n;
    }

    public record PreviewSummary(
            boolean noConversionNeeded,
            int sourceTrackOrLayerCount,
            int internalTrackOrLayerCount,
            int sourceClipCount,
            int internalClipCount,
            int targetRevision,
            int jsonByteDelta) {}

    public record PreviewResult(
            String internalTimelineJson, String sourceSchema, boolean alreadyInternal, PreviewSummary summary) {}
}
