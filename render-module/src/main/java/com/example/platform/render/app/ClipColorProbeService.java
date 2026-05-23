package com.example.platform.render.app;

import com.example.platform.render.domain.timeline.TimelineClip;
import com.example.platform.render.domain.timeline.TimelineScriptParser;
import com.example.platform.render.domain.timeline.TimelineSpec;
import com.example.platform.render.domain.timeline.TimelineTrack;
import com.example.platform.render.infrastructure.ColorProbeMetadata;
import com.example.platform.render.infrastructure.MediaProbeResult;
import com.example.platform.render.infrastructure.MediaProbeService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Probes clip media URIs and writes color/HDR metadata onto matching clips in timeline JSON.
 */
@Service
public class ClipColorProbeService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final TimelineScriptParser timelineScriptParser;
    private final MediaProbeService mediaProbeService;
    private final TimelineColorMetadataService timelineColorMetadataService;

    public ClipColorProbeService(TimelineScriptParser timelineScriptParser,
                                   MediaProbeService mediaProbeService,
                                   TimelineColorMetadataService timelineColorMetadataService) {
        this.timelineScriptParser = timelineScriptParser;
        this.mediaProbeService = mediaProbeService;
        this.timelineColorMetadataService = timelineColorMetadataService;
    }

    public ClipProbeResult probeAndEnrichTimeline(String timelineJson, String probeJobIdPrefix) {
        TimelineSpec spec = timelineScriptParser.parse(timelineJson).orElse(null);
        if (spec == null) {
            return ClipProbeResult.failed(List.of("Invalid timeline JSON"));
        }

        Map<String, String> uniquePaths = collectProbePaths(spec);
        Map<String, ColorProbeMetadata> probedByPath = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();

        int index = 0;
        for (Map.Entry<String, String> entry : uniquePaths.entrySet()) {
            String normalized = entry.getKey();
            String probePath = entry.getValue();
            String jobId = probeJobIdPrefix + "-clip-" + (++index);
            MediaProbeResult result = probePath.startsWith("/") || probePath.contains("://")
                    ? mediaProbeService.probeAbsolute(jobId, toAbsolutePath(probePath))
                    : mediaProbeService.probe(jobId, probePath);
            if (!result.valid()) {
                warnings.add("Probe failed for " + normalized + ": " + result.errorMessage());
                continue;
            }
            probedByPath.put(normalized, result.color());
        }

        String enriched = mergeClipMetadata(timelineJson, spec, probedByPath);
        enriched = probedByPath.isEmpty()
                ? enriched
                : timelineColorMetadataService.mergeProbeMetadata(enriched, aggregateColor(probedByPath));
        return new ClipProbeResult(true, enriched, probedByPath.size(), warnings);
    }

    private Map<String, String> collectProbePaths(TimelineSpec spec) {
        Map<String, String> paths = new LinkedHashMap<>();
        if (spec.tracks() == null) {
            return paths;
        }
        for (TimelineTrack track : spec.tracks()) {
            if (track.clips() == null) {
                continue;
            }
            for (TimelineClip clip : track.clips()) {
                if (clip.assetRef() == null || clip.assetRef().storageUri() == null) {
                    continue;
                }
                String uri = clip.assetRef().storageUri().trim();
                if (uri.isBlank() || uri.startsWith("edl://") || uri.startsWith("aaf://")
                        || uri.startsWith("fcpxml://")) {
                    continue;
                }
                String key = normalizeUri(uri);
                paths.putIfAbsent(key, uri);
            }
        }
        return paths;
    }

    private String mergeClipMetadata(String timelineJson, TimelineSpec spec,
                                     Map<String, ColorProbeMetadata> probedByPath) {
        try {
            JsonNode root = MAPPER.readTree(timelineJson);
            if (!root.isObject() || !root.has("tracks")) {
                return timelineJson;
            }
            ObjectNode rootObj = (ObjectNode) root;
            for (JsonNode trackNode : root.get("tracks")) {
                if (!trackNode.has("clips")) {
                    continue;
                }
                for (JsonNode clipNode : trackNode.get("clips")) {
                    if (!clipNode.isObject()) {
                        continue;
                    }
                    ObjectNode clipObj = (ObjectNode) clipNode;
                    String uri = resolveClipUri(clipObj);
                    if (uri == null) {
                        continue;
                    }
                    ColorProbeMetadata color = probedByPath.get(normalizeUri(uri));
                    if (color == null) {
                        continue;
                    }
                    ObjectNode assetRef = clipObj.has("assetRef") && clipObj.get("assetRef").isObject()
                            ? (ObjectNode) clipObj.get("assetRef")
                            : clipObj.putObject("assetRef");
                    ObjectNode meta = assetRef.has("metadata") && assetRef.get("metadata").isObject()
                            ? (ObjectNode) assetRef.get("metadata")
                            : assetRef.putObject("metadata");
                    for (Map.Entry<String, String> e : color.toTimelineMetadata().entrySet()) {
                        meta.put(e.getKey(), e.getValue());
                    }
                }
            }
            return MAPPER.writeValueAsString(rootObj);
        } catch (Exception e) {
            return timelineJson;
        }
    }

    private static String resolveClipUri(JsonNode clipNode) {
        if (clipNode.has("assetRef") && clipNode.get("assetRef").has("storageUri")) {
            return clipNode.get("assetRef").get("storageUri").asText();
        }
        if (clipNode.has("media_reference")) {
            return clipNode.get("media_reference").asText();
        }
        if (clipNode.has("mediaReference")) {
            return clipNode.get("mediaReference").asText();
        }
        return null;
    }

    private static ColorProbeMetadata aggregateColor(Map<String, ColorProbeMetadata> probed) {
        boolean hdr = probed.values().stream().anyMatch(ColorProbeMetadata::hdr);
        return probed.values().stream().findFirst()
                .map(c -> new ColorProbeMetadata(
                        c.colorSpace(), c.colorPrimaries(), c.colorTransfer(), c.colorRange(),
                        c.pixelFormat(), hdr))
                .orElse(ColorProbeMetadata.empty());
    }

    static String normalizeUri(String uri) {
        String u = uri.trim();
        if (u.startsWith("file://")) {
            return Path.of(u.substring("file://".length())).normalize().toString();
        }
        return u;
    }

    private static String toAbsolutePath(String uri) {
        if (uri.startsWith("file://")) {
            return Path.of(uri.substring("file://".length())).toAbsolutePath().toString();
        }
        return uri;
    }

    public record ClipProbeResult(
            boolean success,
            String timelineJson,
            int clipsProbed,
            List<String> warnings) {

        static ClipProbeResult failed(List<String> errors) {
            return new ClipProbeResult(false, null, 0, errors);
        }
    }
}
