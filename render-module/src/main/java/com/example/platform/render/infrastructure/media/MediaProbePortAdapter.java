package com.example.platform.render.infrastructure.media;

import com.example.platform.render.infrastructure.MediaProbeService;
import com.example.platform.shared.media.MediaProbePort;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MediaProbePortAdapter implements MediaProbePort {

    private static final Logger log = LoggerFactory.getLogger(MediaProbePortAdapter.class);

    private final MediaProbeService probeService;

    public MediaProbePortAdapter(MediaProbeService probeService) {
        this.probeService = probeService;
    }

    @Override
    public MediaProbeResult probe(String assetUri) {
        return probe(assetUri, null);
    }

    @Override
    public MediaProbeResult probe(String assetUri, String storageRoot) {
        try {
            if (storageRoot != null) {
                probeService.setStorageRoot(storageRoot);
            }

            var internal = probeService.probeAbsolute("", assetUri);

            boolean clientExportCompatible = isClientExportCompatible(internal);
            boolean normalizeRequired = isNormalizeRequired(internal);

            return new MediaProbeResult(
                    assetUri,
                    internal.valid(),
                    guessContainer(assetUri),
                    internal.fileSizeBytes(),
                    internal.durationMs(),
                    internal.width(),
                    internal.height(),
                    internal.frameRate(),
                    internal.videoCodec(),
                    internal.audioCodec(),
                    internal.sampleRate(),
                    internal.audioChannels(),
                    0,
                    internal.color() != null ? internal.color().colorSpace() : "",
                    internal.bitrate(),
                    false,
                    countStreams(internal),
                    clientExportCompatible,
                    normalizeRequired,
                    internal.warnings() != null ? internal.warnings() : List.of(),
                    internal.errorMessage());
        } catch (Exception e) {
            log.error("MediaProbePort: probe failed for {}: {}", assetUri, e.getMessage());
            return MediaProbeResult.failed(assetUri, e.getMessage());
        }
    }

    private boolean isClientExportCompatible(com.example.platform.render.infrastructure.MediaProbeResult r) {
        if (!r.valid()) return false;
        if (!r.hasVideo()) return false;
        if (r.width() > 1920 || r.height() > 1080) return false;
        if (r.durationMs() > 300_000) return false;
        String vc = r.videoCodec() != null ? r.videoCodec().toLowerCase() : "";
        if (!vc.contains("h264") && !vc.contains("avc")) return false;
        return true;
    }

    private boolean isNormalizeRequired(com.example.platform.render.infrastructure.MediaProbeResult r) {
        if (!r.valid()) return true;
        if (!r.hasUsableAudio()) return true;
        if (r.frameRate() == 0) return true;
        return false;
    }

    private String guessContainer(String uri) {
        if (uri == null) return "";
        String lower = uri.toLowerCase();
        if (lower.endsWith(".mp4")) return "mp4";
        if (lower.endsWith(".webm")) return "webm";
        if (lower.endsWith(".mov")) return "mov";
        if (lower.endsWith(".mkv")) return "mkv";
        if (lower.endsWith(".avi")) return "avi";
        if (lower.endsWith(".ts")) return "mpegts";
        return "unknown";
    }

    private int countStreams(com.example.platform.render.infrastructure.MediaProbeResult r) {
        int count = 0;
        if (r.hasVideo()) count++;
        if (r.hasAudioStream()) count++;
        return count;
    }
}
