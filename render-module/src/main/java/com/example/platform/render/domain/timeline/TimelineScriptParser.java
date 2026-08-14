package com.example.platform.render.domain.timeline;

import com.example.platform.shared.time.CanonicalFrameRateCodec;
import com.example.platform.shared.time.FrameRate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Parses render input scripts (canonical {@link TimelineSpec} JSON or OTIO-style maps)
 * and resolves media URIs to local filesystem paths.
 */
@Component
public class TimelineScriptParser {

    private static final Logger log = LoggerFactory.getLogger(TimelineScriptParser.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final TimelineExtensionsReader extensionsReader;

    public TimelineScriptParser() {
        this(new TimelineExtensionsReader());
    }

    public TimelineScriptParser(TimelineExtensionsReader extensionsReader) {
        this.extensionsReader = extensionsReader;
    }

    public boolean isTimelineJson(String script) {
        if (script == null || script.isBlank()) {
            return false;
        }
        String trimmed = script.trim();
        return trimmed.startsWith("{") && trimmed.contains("tracks");
    }

    public Optional<TimelineSpec> parse(String script) {
        if (!isTimelineJson(script)) {
            return Optional.empty();
        }
        try {
            JsonNode root = MAPPER.readTree(script);
            TimelineSpec spec;
            if (root.has("id") && root.has("outputSpec")) {
                try {
                    spec = MAPPER.readValue(script, TimelineSpec.class);
                } catch (Exception canonicalError) {
                    log.debug("Canonical timeline parse failed, using OTIO shape: {}",
                            canonicalError.getMessage());
                    spec = parseOtioRoot(root);
                }
            } else {
                spec = parseOtioRoot(root);
            }
            return Optional.of(enrichFromRoot(root, spec));
        } catch (Exception e) {
            log.warn("TimelineScriptParser: parse failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public TimelineSpec parseOtioRoot(JsonNode root) {
        String id = textOr(root, "id", "timeline");
        String name = textOr(root, "name", id);
        TimelineOutputSpec output = parseOutputSpec(root);
        List<TimelineTrack> tracks = new ArrayList<>();

        JsonNode tracksNode = root.get("tracks");
        if (tracksNode != null && tracksNode.isArray()) {
            int layer = 0;
            for (JsonNode trackNode : tracksNode) {
                TimelineTrack track = parseTrack(trackNode, layer++);
                if (track != null) tracks.add(track);
            }
        }

        String format = textOr(root, "format", output.format());
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("format", format);

        return enrichFromRoot(root, new TimelineSpec(id, name, null, tracks, List.of(), output, 0, metadata));
    }

    private TimelineSpec enrichFromRoot(JsonNode root, TimelineSpec spec) {
        if (root == null || spec == null) {
            return spec;
        }
        TimelineExtensions ext = extensionsReader.fromJsonRoot(root);
        Map<String, String> meta = new LinkedHashMap<>(spec.metadata() != null ? spec.metadata() : Map.of());
        extensionsReader.mergeIntoMetadata(meta, ext);
        if (root.has("stickers") && root.get("stickers").isArray()) {
            try {
                meta.put("platform.stickers", MAPPER.writeValueAsString(root.get("stickers")));
            } catch (Exception ignored) {
                // skip
            }
        }
        if (root.has("textOverlays") && root.get("textOverlays").isArray()) {
            try {
                List<TimelineTextOverlay> overlays = MAPPER.readerForListOf(TimelineTextOverlay.class)
                        .readValue(root.get("textOverlays"));
                return new TimelineSpec(spec.id(), spec.name(), spec.description(), spec.tracks(),
                        overlays, spec.outputSpec(), spec.computeDuration(), meta);
            } catch (Exception e) {
                log.debug("Could not parse textOverlays: {}", e.getMessage());
            }
        }
        return new TimelineSpec(spec.id(), spec.name(), spec.description(), spec.tracks(),
                spec.textOverlays(), spec.outputSpec(), spec.computeDuration(), meta);
    }

    private TimelineTrack parseTrack(JsonNode trackNode, int layer) {
        String trackId = textOr(trackNode, "id", "track-" + layer);
        String trackName = textOr(trackNode, "name", trackId);
        TimelineTrack.TrackType type = parseTrackType(trackNode);
        List<TimelineClip> clips = new ArrayList<>();

        JsonNode children = trackNode.get("children");
        if (children != null && children.isArray()) {
            int clipIndex = 0;
            for (JsonNode child : children) {
                clips.add(parseClip(child, trackId + "-clip-" + clipIndex++, 0));
            }
        }

        JsonNode clipsNode = trackNode.get("clips");
        if (clipsNode != null && clipsNode.isArray()) {
            int clipIndex = 0;
            for (JsonNode clipNode : clipsNode) {
                double timelineStart = clipNode.path("timelineStart").asDouble(
                        clipNode.path("timeline_start").asDouble(0));
                clips.add(parseClip(clipNode, trackId + "-clip-" + clipIndex++, timelineStart));
            }
        }

        return new TimelineTrack(trackId, trackName, type, layer, clips, false, false);
    }

    private TimelineClip parseClip(JsonNode clipNode, String defaultId, double timelineStart) {
        String clipId = textOr(clipNode, "id", defaultId);
        String mediaRef = textOr(clipNode, "media_reference",
                textOr(clipNode, "mediaReference", ""));

        JsonNode assetRefNode = clipNode.get("assetRef");
        if (assetRefNode != null && assetRefNode.isObject()) {
            mediaRef = textOr(assetRefNode, "storageUri", mediaRef);
        }

        double startTime = 0;
        double duration = 30;
        JsonNode sourceRange = clipNode.get("source_range");
        if (sourceRange == null) {
            sourceRange = clipNode.get("sourceRange");
        }
        if (sourceRange != null) {
            startTime = sourceRange.path("start_time").asDouble(sourceRange.path("startTime").asDouble(0));
            duration = sourceRange.path("duration").asDouble(30);
        } else {
            startTime = clipNode.path("assetInPoint").asDouble(clipNode.path("asset_in_point").asDouble(0));
            double outPoint = clipNode.path("assetOutPoint").asDouble(clipNode.path("asset_out_point").asDouble(0));
            if (outPoint > startTime) {
                duration = outPoint - startTime;
            } else {
                duration = clipNode.path("clipDuration").asDouble(clipNode.path("clip_duration").asDouble(30));
            }
        }

        double outPoint = startTime + duration;
        Map<String, String> assetMetadata = parseStringMap(clipNode.get("metadata"));
        if (assetRefNode != null && assetRefNode.isObject()) {
            assetMetadata = mergeMaps(assetMetadata, parseStringMap(assetRefNode.get("metadata")));
        }
        // C1-CNM1 SOURCE_BINDING_PRESERVATION_CORRECTION: clip identity and
        // media asset identity are DISTINCT authorities. The production payload
        // carries the authoritative asset id under assetRef.assetId; the legacy
        // OTIO/script shape must preserve it (never substitute clipId).
        String assetId = clipId;
        if (assetRefNode != null && assetRefNode.isObject()) {
            String refAssetId = textOr(assetRefNode, "assetId", "");
            if (!refAssetId.isBlank()) {
                assetId = refAssetId;
            }
        }
        TimelineAssetRef assetRef = new TimelineAssetRef(
                assetId, mediaRef, "unknown", 0, 0, 0, assetMetadata, null);
        List<TimelineClipEffect> effects = parseClipEffects(clipNode);
        return new TimelineClip(clipId, assetRef, timelineStart, startTime, outPoint,
                duration, effects);
    }

    private List<TimelineClipEffect> parseClipEffects(JsonNode clipNode) {
        JsonNode effectsNode = clipNode.get("effects");
        if (effectsNode == null || !effectsNode.isArray()) {
            return List.of();
        }
        List<TimelineClipEffect> effects = new ArrayList<>();
        for (JsonNode node : effectsNode) {
            String effectKey = textOr(node, "effectKey", textOr(node, "effect_key", ""));
            if (effectKey.isBlank()) {
                continue;
            }
            String id = textOr(node, "id", null);
            String packId = textOr(node, "packId", textOr(node, "pack_id", null));
            String packVersion = textOr(node, "packVersion", textOr(node, "pack_version", null));
            List<String> providerPreference = new ArrayList<>();
            JsonNode pref = node.get("providerPreference");
            if (pref == null) {
                pref = node.get("provider_preference");
            }
            if (pref != null && pref.isArray()) {
                pref.forEach(p -> providerPreference.add(p.asText()));
            }
            Map<String, Object> parameters = new LinkedHashMap<>();
            JsonNode params = node.get("parameters");
            if (params != null && params.isObject()) {
                params.fields().forEachRemaining(e ->
                        parameters.put(e.getKey(), jsonValue(e.getValue())));
            }
            effects.add(new TimelineClipEffect(id, effectKey, packId, packVersion, providerPreference, parameters));
        }
        return effects;
    }

    private static Object jsonValue(JsonNode node) {
        if (node.isNumber()) {
            return node.numberValue();
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        return node.asText();
    }

    private static Map<String, String> parseStringMap(JsonNode node) {
        Map<String, String> map = new LinkedHashMap<>();
        if (node == null || !node.isObject()) {
            return map;
        }
        node.fields().forEachRemaining(e -> map.put(e.getKey(), e.getValue().asText("")));
        return map;
    }

    private static Map<String, String> mergeMaps(Map<String, String> base, Map<String, String> extra) {
        Map<String, String> merged = new LinkedHashMap<>(base);
        merged.putAll(extra);
        return merged;
    }

    private TimelineOutputSpec parseOutputSpec(JsonNode root) {
        JsonNode output = root.get("outputSpec");
        if (output != null && output.isObject()) {
            // C1-CNM1-CR1: a present-but-invalid canonical frameRate is a
            // REJECTED input, never silently defaulted through the profile
            // fallback. Parse the rate FIRST, outside the tolerant catch.
            FrameRate frameRate = parseFrameRateNode(output.get("frameRate"));
            try {
                String format = textOr(output, "format", "mp4");
                String resolution = textOr(output, "resolution", "1920x1080");
                String videoCodec = textOr(output, "videoCodec", "h264");
                int videoBitrate = output.path("videoBitrate").asInt(8000);
                TimelineAudioSpec audioSpec = output.has("audioSpec")
                        ? MAPPER.treeToValue(output.get("audioSpec"), TimelineAudioSpec.class)
                        : TimelineAudioSpec.aacDefault();
                String pixelFormat = textOr(output, "pixelFormat", "yuv420p");
                return new TimelineOutputSpec(format, resolution, frameRate, videoCodec,
                        videoBitrate, audioSpec, pixelFormat);
            } catch (Exception ignored) {
                // Non-rate output fields are tolerant: fall through to profile
                // default. Invalid RATE input was already rejected above.
            }
        }
        String profile = textOr(root, "profile", "default_1080p");
        if (profile.contains("720")) {
            return TimelineOutputSpec.mp4_720p30();
        }
        return TimelineOutputSpec.mp4_1080p30();
    }

    /**
     * C1-CNM1-CR1: exact rational frame rate from a JSON node.
     * Structured {@code {num, den}} is the canonical form, parsed through
     * {@link CanonicalFrameRateCodec} (int32-bounded, zero/negative/partial
     * rejected — never defaulted). A legacy decimal fps number is
     * rationalized from its decimal string (e.g. 29.97 -&gt; 2997/100) and
     * validated against the same bounded domain. Never a binary floating
     * authority; never silently truncated to an integer; never silently
     * defaulted on invalid input.
     */
    private static FrameRate parseFrameRateNode(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            // Absent outputSpec frameRate: optional field, documented default.
            return CanonicalFrameRateCodec.DEFAULT_RATE;
        }
        if (node.isObject()) {
            // Canonical {num, den} form, OR the OTIO/Jackson bean alias
            // {numerator, denominator} produced by toOtioJson serializing the
            // FrameRate record. Both are exact-integer, int32-bounded.
            if (!node.has("num") && node.has("numerator")) {
                ObjectNode canonical = MAPPER.createObjectNode();
                canonical.set("num", node.get("numerator"));
                canonical.set("den", node.get("denominator"));
                return CanonicalFrameRateCodec.parse(canonical, false);
            }
            return CanonicalFrameRateCodec.parse(node, false);
        }
        if (node.isNumber() || node.isTextual()) {
            String s = node.isTextual() ? node.asText() : node.asText();
            int slash = s.indexOf('/');
            long num;
            long den;
            if (slash > 0) {
                num = parseBounded(s.substring(0, slash).trim());
                den = parseBounded(s.substring(slash + 1).trim());
            } else {
                double d = node.asDouble();
                if (!Double.isFinite(d) || d <= 0) {
                    throw new CanonicalFrameRateCodec.InvalidCanonicalRateException(
                            "decimal fps must be finite and positive: " + s);
                }
                if (d == Math.rint(d)) {
                    num = (long) d;
                    den = 1L;
                } else {
                    String ds = String.valueOf(d);
                    int dot = ds.indexOf('.');
                    String frac = dot < 0 ? "" : ds.substring(dot + 1);
                    den = frac.isEmpty() ? 1L : (long) Math.pow(10, frac.length());
                    num = (long) Math.round(d * den);
                }
            }
            if (num <= 0 || den <= 0 || num > Integer.MAX_VALUE || den > Integer.MAX_VALUE) {
                throw new CanonicalFrameRateCodec.InvalidCanonicalRateException(
                        "fps out of canonical wire domain: " + num + "/" + den);
            }
            return FrameRate.of(num, den);
        }
        throw new CanonicalFrameRateCodec.InvalidCanonicalRateException(
                "unexpected frameRate node type: " + node.getNodeType());
    }

    private static long parseBounded(String s) {
        try {
            long v = Long.parseLong(s);
            if (v > Integer.MAX_VALUE || v < Integer.MIN_VALUE) {
                throw new CanonicalFrameRateCodec.InvalidCanonicalRateException(
                        "component out of int32 wire domain: " + v);
            }
            return v;
        } catch (NumberFormatException e) {
            throw new CanonicalFrameRateCodec.InvalidCanonicalRateException(
                    "malformed rate component: " + s);
        }
    }

    private TimelineTrack.TrackType parseTrackType(JsonNode trackNode) {
        String type = textOr(trackNode, "type", "VIDEO").toUpperCase();
        return switch (type) {
            case "AUDIO" -> TimelineTrack.TrackType.AUDIO;
            case "SUBTITLE", "TEXT" -> TimelineTrack.TrackType.SUBTITLE;
            default -> TimelineTrack.TrackType.VIDEO;
        };
    }

    public String resolveLocalPath(String storageUri, String storageRoot) {
        if (storageUri == null || storageUri.isBlank()) {
            return "";
        }
        if (storageUri.startsWith("file://")) {
            return storageUri.substring("file://".length());
        }
        if (storageUri.startsWith("localFsStorageProvider://")) {
            String relative = storageUri.substring("localFsStorageProvider://".length());
            return Path.of(storageRoot, relative).toString();
        }
        if (storageUri.startsWith("storage://")) {
            String relative = storageUri.substring("storage://".length());
            return Path.of(storageRoot, relative).toString();
        }
        if (storageUri.startsWith("/")) {
            return storageUri;
        }
        return Path.of(storageRoot, storageUri).toString();
    }

    public boolean mediaFileExists(String storageUri, String storageRoot) {
        String path = resolveLocalPath(storageUri, storageRoot);
        return path != null && !path.isBlank() && Files.isRegularFile(Path.of(path));
    }

    public Optional<TimelineClip> firstVideoClip(TimelineSpec timeline) {
        if (timeline.tracks() == null) {
            return Optional.empty();
        }
        for (TimelineTrack track : timeline.tracks()) {
            if (track.type() != TimelineTrack.TrackType.VIDEO || track.clips() == null) {
                continue;
            }
            for (TimelineClip clip : track.clips()) {
                if (clip.assetRef() != null
                        && clip.assetRef().storageUri() != null
                        && !clip.assetRef().storageUri().isBlank()) {
                    return Optional.of(clip);
                }
            }
        }
        return Optional.empty();
    }

    public List<TimelineClip> videoClipsInOrder(TimelineSpec timeline) {
        List<TimelineClip> clips = new ArrayList<>();
        if (timeline.tracks() == null) {
            return clips;
        }
        for (TimelineTrack track : timeline.tracks()) {
            if (track.type() == TimelineTrack.TrackType.VIDEO && track.clips() != null) {
                clips.addAll(track.clips());
            }
        }
        return clips;
    }

    public String toJson(TimelineSpec timeline) {
        try {
            return MAPPER.writeValueAsString(timeline);
        } catch (Exception e) {
            return "{}";
        }
    }

    private static String textOr(JsonNode node, String field, String defaultValue) {
        JsonNode value = node.get(field);
        if (value != null && !value.isNull()) {
            return value.asText(defaultValue);
        }
        return defaultValue;
    }
}
