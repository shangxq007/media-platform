package com.example.platform.render.infrastructure.media;

import com.example.platform.media.domain.probe.MediaProbeObservation;
import com.example.platform.media.domain.probe.MediaProbePort;
import com.example.platform.render.infrastructure.FfprobeMediaProbeExecutor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * ffprobe provider adapter for the media-domain {@link MediaProbePort}
 * (INGEST_NORMALIZATION_BOUNDARY_V1).
 *
 * <p>Returns a RAW {@link MediaProbeObservation}: provider-specific payload,
 * opaque to the canonical model. No raw/approximate value from this adapter
 * ever becomes canonical persistence authority — the media-domain normalizer
 * produces the canonical structural model.
 */
@Component
public class MediaProbePortAdapter implements MediaProbePort {

    private static final Logger log = LoggerFactory.getLogger(MediaProbePortAdapter.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final FfprobeMediaProbeExecutor probeService;

    public MediaProbePortAdapter(FfprobeMediaProbeExecutor probeService) {
        this.probeService = probeService;
    }

    @Override
    public MediaProbeObservation probe(String assetUri) {
        try {
            var internal = probeService.probeAbsolute("", assetUri);
            return new MediaProbeObservation(
                    "ffprobe",
                    toFfprobeJson(internal),
                    internal.valid(),
                    isClientExportCompatible(internal),
                    isNormalizeRequired(internal),
                    internal.warnings() != null ? internal.warnings() : List.of(),
                    internal.errorMessage());
        } catch (Exception e) {
            log.error("MediaProbePort: probe failed for {}: {}", assetUri, e.getMessage());
            return MediaProbeObservation.failed("ffprobe", e.getMessage());
        }
    }

    private static String toFfprobeJson(com.example.platform.render.infrastructure.MediaProbeResult r) {
        try {
            ObjectNode root = MAPPER.createObjectNode();
            ObjectNode format = root.putObject("format");
            format.put("format_name", guessContainer(r.filePath()));
            format.put("duration", String.valueOf(r.durationMs() / 1000.0));
            ArrayNode streams = root.putArray("streams");
            if (r.hasVideo()) {
                ObjectNode v = streams.addObject();
                v.put("codec_type", "video");
                v.put("codec_name", r.videoCodec());
                v.put("width", r.width());
                v.put("height", r.height());
                v.put("r_frame_rate", "0/0");
                v.put("avg_frame_rate", "0/0");
                v.put("pix_fmt", "");
            }
            if (r.hasAudioStream()) {
                ObjectNode a = streams.addObject();
                a.put("codec_type", "audio");
                a.put("codec_name", r.audioCodec());
                a.put("sample_rate", String.valueOf(r.sampleRate()));
                a.put("channels", r.audioChannels());
                a.put("channel_layout", "");
                a.put("sample_fmt", "");
            }
            return MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            return "{}";
        }
    }

    private boolean isClientExportCompatible(com.example.platform.render.infrastructure.MediaProbeResult r) {
        if (!r.valid()) {
            return false;
        }
        if (!r.hasVideo()) {
            return false;
        }
        if (r.width() > 1920 || r.height() > 1080) {
            return false;
        }
        if (r.durationMs() > 300_000) {
            return false;
        }
        return true;
    }

    private boolean isNormalizeRequired(com.example.platform.render.infrastructure.MediaProbeResult r) {
        return !r.valid() || r.hasAudioStream() && !r.hasUsableAudio();
    }

    private static String guessContainer(String filePath) {
        if (filePath == null) {
            return "";
        }
        String lower = filePath.toLowerCase();
        if (lower.endsWith(".mov")) {
            return "mov";
        }
        if (lower.endsWith(".webm")) {
            return "webm";
        }
        if (lower.endsWith(".mkv")) {
            return "matroska";
        }
        return "mp4";
    }
}
