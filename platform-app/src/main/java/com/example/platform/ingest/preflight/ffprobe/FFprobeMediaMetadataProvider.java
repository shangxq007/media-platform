package com.example.platform.ingest.preflight.ffprobe;

import com.example.platform.ingest.contract.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * FFprobe media metadata provider for local POC.
 * Disabled by default. Not for production upload integration.
 */
@Component
public class FFprobeMediaMetadataProvider {

    private static final Logger log = LoggerFactory.getLogger(FFprobeMediaMetadataProvider.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public FFprobeProbeResult probe(Path mediaPath, String filename, String declaredContentType) {
        return probe(mediaPath, filename, declaredContentType, 5000);
    }

    public FFprobeProbeResult probe(Path mediaPath, String filename, String declaredContentType, long timeoutMs) {
        if (mediaPath == null || !Files.exists(mediaPath)) {
            return FFprobeProbeResult.failed("File not found", List.of("MEDIA_PROBE_FAILED"));
        }

        List<String> warnings = new ArrayList<>();
        long startTime = System.currentTimeMillis();

        try {
            List<String> command = List.of(
                "ffprobe", "-v", "error", "-print_format", "json",
                "-show_format", "-show_streams", mediaPath.toString()
            );

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(false);
            Process process = pb.start();

            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();

            try (BufferedReader outReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                 BufferedReader errReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = outReader.readLine()) != null) {
                    stdout.append(line).append("\n");
                }
                while ((line = errReader.readLine()) != null) {
                    stderr.append(line).append("\n");
                }
            }

            boolean completed = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            long durationMs = System.currentTimeMillis() - startTime;

            if (!completed) {
                process.destroyForcibly();
                warnings.add("MEDIA_PROBE_TIMEOUT");
                return FFprobeProbeResult.timeout(durationMs, warnings);
            }

            if (process.exitValue() != 0) {
                warnings.add("MEDIA_PROBE_FAILED");
                return FFprobeProbeResult.failed("FFprobe exited with code " + process.exitValue(), warnings);
            }

            JsonNode root = MAPPER.readTree(stdout.toString());
            return parseFfprobeOutput(root, filename, declaredContentType, durationMs, warnings);

        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - startTime;
            log.warn("FFprobe failed: {}", e.getMessage());
            warnings.add("MEDIA_PROBE_FAILED");
            return FFprobeProbeResult.failed(e.getMessage(), warnings);
        }
    }

    private FFprobeProbeResult parseFfprobeOutput(JsonNode root, String filename, String declaredContentType,
                                                   long durationMs, List<String> warnings) {
        JsonNode format = root.path("format");
        JsonNode streams = root.path("streams");

        Long probeDurationMs = parseDuration(format.path("duration").asText(null));
        String containerFormat = format.path("format_name").asText(null);
        String formatLongName = format.path("format_long_name").asText(null);
        Long bitrate = parseLong(format.path("bit_rate").asText(null));
        Long sizeBytes = parseLong(format.path("size").asText(null));

        List<VideoStreamMetadata> videoStreams = new ArrayList<>();
        List<AudioStreamMetadata> audioStreams = new ArrayList<>();
        List<SubtitleStreamMetadata> subtitleStreams = new ArrayList<>();

        for (JsonNode stream : streams) {
            String codecType = stream.path("codec_type").asText("");
            switch (codecType) {
                case "video" -> videoStreams.add(parseVideoStream(stream));
                case "audio" -> audioStreams.add(parseAudioStream(stream));
                case "subtitle" -> subtitleStreams.add(parseSubtitleStream(stream));
            }
        }

        boolean hasVideo = !videoStreams.isEmpty();
        boolean hasAudio = !audioStreams.isEmpty();
        boolean hasSubtitle = !subtitleStreams.isEmpty();

        MediaCategory category = hasVideo ? MediaCategory.VIDEO : (hasAudio ? MediaCategory.AUDIO : MediaCategory.UNKNOWN);

        VideoStreamMetadata primaryVideo = videoStreams.isEmpty() ? null : videoStreams.get(0);
        AudioStreamMetadata primaryAudio = audioStreams.isEmpty() ? null : audioStreams.get(0);

        var metadata = new MediaTechnicalMetadata(
            category, probeDurationMs, containerFormat, formatLongName, bitrate, sizeBytes,
            hasVideo, hasAudio, hasSubtitle,
            videoStreams.size(), audioStreams.size(), subtitleStreams.size(),
            primaryVideo != null ? primaryVideo.codec() : null,
            primaryAudio != null ? primaryAudio.codec() : null,
            primaryVideo != null ? primaryVideo.width() : null,
            primaryVideo != null ? primaryVideo.height() : null,
            primaryVideo != null ? primaryVideo.frameRate() : null,
            primaryAudio != null ? primaryAudio.sampleRate() : null,
            primaryAudio != null ? primaryAudio.channels() : null,
            primaryVideo != null ? primaryVideo.rotation() : null,
            primaryVideo != null ? primaryVideo.pixelFormat() : null,
            primaryVideo != null ? primaryVideo.colorSpace() : null,
            videoStreams, audioStreams, subtitleStreams,
            new MediaProbeSummary(MediaProbeProvider.FFPROBE, null, MediaProbeStatus.SUCCESS,
                durationMs, null, null, warnings)
        );

        warnings.add("RAW_METADATA_DROPPED");

        return FFprobeProbeResult.success(metadata, warnings);
    }

    private VideoStreamMetadata parseVideoStream(JsonNode stream) {
        return new VideoStreamMetadata(
            stream.path("codec_name").asText(null),
            stream.path("width").asInt(0) > 0 ? stream.path("width").asInt() : null,
            stream.path("height").asInt(0) > 0 ? stream.path("height").asInt() : null,
            parseFrameRate(stream.path("r_frame_rate").asText(null)),
            parseLong(stream.path("bit_rate").asText(null)),
            stream.path("pix_fmt").asText(null),
            stream.path("color_space").asText(null),
            parseRotation(stream),
            stream.path("index").asInt()
        );
    }

    private AudioStreamMetadata parseAudioStream(JsonNode stream) {
        return new AudioStreamMetadata(
            stream.path("codec_name").asText(null),
            parseInt(stream.path("sample_rate").asText(null)),
            stream.path("channels").asInt(0) > 0 ? stream.path("channels").asInt() : null,
            parseLong(stream.path("bit_rate").asText(null)),
            stream.path("index").asInt()
        );
    }

    private SubtitleStreamMetadata parseSubtitleStream(JsonNode stream) {
        return new SubtitleStreamMetadata(
            stream.path("codec_name").asText(null),
            stream.path("tags").path("language").asText(null),
            stream.path("index").asInt()
        );
    }

    private Long parseDuration(String duration) {
        if (duration == null) return null;
        try {
            return (long) (Double.parseDouble(duration) * 1000);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long parseLong(String value) {
        if (value == null) return null;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer parseInt(String value) {
        if (value == null) return null;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private BigDecimal parseFrameRate(String rate) {
        if (rate == null) return null;
        try {
            String[] parts = rate.split("/");
            if (parts.length == 2) {
                BigDecimal num = new BigDecimal(parts[0]);
                BigDecimal den = new BigDecimal(parts[1]);
                if (den.compareTo(BigDecimal.ZERO) != 0) {
                    return num.divide(den, 2, BigDecimal.ROUND_HALF_UP);
                }
            }
            return new BigDecimal(rate);
        } catch (Exception e) {
            return null;
        }
    }

    private Integer parseRotation(JsonNode stream) {
        JsonNode tags = stream.path("tags");
        if (tags.has("rotate")) {
            try {
                return Integer.parseInt(tags.path("rotate").asText());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    public record FFprobeProbeResult(
        MediaProbeStatus status,
        MediaTechnicalMetadata metadata,
        List<String> warnings,
        String errorSummary,
        long durationMs
    ) {
        public static FFprobeProbeResult success(MediaTechnicalMetadata metadata, List<String> warnings) {
            return new FFprobeProbeResult(MediaProbeStatus.SUCCESS, metadata, warnings, null,
                metadata.probe() != null ? metadata.probe().durationMs() : 0);
        }

        public static FFprobeProbeResult failed(String error, List<String> warnings) {
            return new FFprobeProbeResult(MediaProbeStatus.FAILED, null, warnings, error, 0);
        }

        public static FFprobeProbeResult timeout(long durationMs, List<String> warnings) {
            return new FFprobeProbeResult(MediaProbeStatus.TIMEOUT, null, warnings, "FFprobe timed out", durationMs);
        }
    }
}
