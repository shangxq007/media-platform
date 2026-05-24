package com.example.platform.shared.media;

import java.util.List;

public interface MediaProbePort {

    MediaProbeResult probe(String assetUri);

    MediaProbeResult probe(String assetUri, String storageRoot);

    record MediaProbeResult(
            String assetUri,
            boolean valid,
            String container,
            long fileSizeBytes,
            double durationMs,
            int width,
            int height,
            double fps,
            String videoCodec,
            String audioCodec,
            int audioSampleRate,
            int audioChannels,
            boolean hasAudio,
            int rotation,
            String colorSpace,
            long bitrate,
            boolean isVfr,
            int streamCount,
            boolean clientExportCompatible,
            boolean normalizeRequired,
            List<String> warnings,
            String error) {

        public static MediaProbeResult failed(String assetUri, String error) {
            return new MediaProbeResult(
                    assetUri, false, "", 0, 0, 0, 0, 0,
                    "", "", 0, 0, false, 0, "", 0, false, 0,
                    false, true, List.of(), error);
        }

        public boolean hasVideo() {
            return width > 0 && height > 0 && videoCodec != null && !videoCodec.isEmpty();
        }

        public boolean hasAudio() {
            return audioChannels > 0 && audioCodec != null && !audioCodec.isEmpty();
        }
    }
}
