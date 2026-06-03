package com.example.platform.render.infrastructure.ffmpeg;

import com.example.platform.extension.app.ProcessToolRunner;
import com.example.platform.render.infrastructure.RenderProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads golden-render-plan.json and converts to TimelineSpec JSON for FFmpegRenderProvider.
 */
public class GoldenRenderPlanAdapter {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final Path assetsBasePath;

    public GoldenRenderPlanAdapter(Path assetsBasePath) {
        this.assetsBasePath = assetsBasePath;
    }

    public String loadTimelineJson(Path planPath) throws Exception {
        JsonNode root = MAPPER.readTree(planPath.toFile());
        JsonNode tracksNode = root.get("tracks");
        JsonNode opsNode = root.get("operations");

        // Build OTIO-format JSON for TimelineScriptParser.parseOtioRoot()
        var spec = MAPPER.createObjectNode();
        spec.put("id", "golden-v1");
        spec.put("name", "Golden Render Project v1");

        var outputSpec = MAPPER.createObjectNode();
        outputSpec.put("format", "mp4");
        outputSpec.put("width", 1920);
        outputSpec.put("height", 1080);
        outputSpec.put("frameRate", 30);
        outputSpec.put("videoCodec", "h264");
        outputSpec.put("audioCodec", "aac");
        outputSpec.put("videoBitrateKbps", 5000);
        outputSpec.put("audioBitrateKbps", 192);
        spec.set("outputSpec", outputSpec);

        var tracksArray = MAPPER.createArrayNode();
        if (tracksNode != null && tracksNode.isArray()) {
            for (JsonNode track : tracksNode) {
                String type = track.path("type").asText("video");
                JsonNode converted = "video".equals(type) ? convertVideoTrack(track) : null;
                if (converted != null) tracksArray.add(converted);
            }
        }
        spec.set("tracks", tracksArray);
        spec.put("duration", 30);

        // Metadata: unsupported operations
        if (opsNode != null && opsNode.isArray()) {
            var unsupported = MAPPER.createArrayNode();
            for (JsonNode op : opsNode) {
                String t = op.path("type").asText("");
                if (isUnsupported(t)) {
                    var item = MAPPER.createObjectNode();
                    item.put("type", t);
                    unsupported.add(item);
                }
            }
            if (unsupported.size() > 0) {
                var meta = MAPPER.createObjectNode();
                meta.set("unsupportedOperations", unsupported);
                spec.set("metadata", meta);
            }
        }

        return MAPPER.writeValueAsString(spec);
    }

    private JsonNode convertVideoTrack(JsonNode track) {
        String trackId = track.path("trackId").asText("v1");
        String trackName = track.path("name").asText("Video");
        var trackJson = MAPPER.createObjectNode();
        trackJson.put("id", trackId);
        trackJson.put("name", trackName);
        trackJson.put("type", "VIDEO");

        var childrenArray = MAPPER.createArrayNode();
        JsonNode clipsNode = track.get("clips");
        if (clipsNode != null && clipsNode.isArray()) {
            int idx = 0;
            for (JsonNode clip : clipsNode) {
                String assetId = clip.path("assetId").asText("");
                long durationMs = clip.path("durationMs").asLong(0);
                long offsetMs = clip.path("sourceOffsetMs").asLong(0);
                String localPath = resolveAssetPath(assetId);
                if (localPath == null) continue;

                var child = MAPPER.createObjectNode();
                child.put("id", trackId + "-clip-" + idx++);
                child.put("name", assetId);
                child.put("media_reference", "file://" + localPath);
                var sr = MAPPER.createObjectNode();
                sr.put("start_time", offsetMs / 1000.0);
                sr.put("duration", durationMs / 1000.0);
                child.set("source_range", sr);
                childrenArray.add(child);
            }
        }
        trackJson.set("children", childrenArray);
        return trackJson;
    }

    private String resolveAssetPath(String assetId) {
        for (String ext : new String[]{".mp4", ".mov", ".mkv"}) {
            Path p = assetsBasePath.resolve("video/" + assetId + ext);
            if (Files.isRegularFile(p)) return p.toString();
        }
        for (String ext : new String[]{".wav", ".mp3", ".aac"}) {
            Path p = assetsBasePath.resolve("audio/" + assetId + ext);
            if (Files.isRegularFile(p)) return p.toString();
        }
        return null;
    }

    private static boolean isUnsupported(String opType) {
        return switch (opType) {
            case "keying", "deform", "advanced_tracking", "crop_runtime", "transform_runtime",
                 "subtitle_burn_in", "watermark", "overlay", "cross_dissolve" -> true;
            default -> false;
        };
    }

    public static FFmpegRenderProvider createLocalProvider(Path storageRoot, ProcessToolRunner toolRunner) {
        FFmpegCommandFactory commandFactory = new FFmpegCommandFactory();
        var parser = new com.example.platform.render.domain.timeline.TimelineScriptParser();
        var assetResolver = new com.example.platform.render.infrastructure.media.MediaAssetResolver(
                storageRoot.toString(), java.util.Optional.empty());
        FFmpegRenderProvider provider = new FFmpegRenderProvider(toolRunner, commandFactory, parser, assetResolver);
        provider.setStorageRoot(storageRoot.toString());
        return provider;
    }
}
