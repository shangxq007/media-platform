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
                    "", "", 0, 0, 0, "", 0, false, 0,
                    false, true, List.of(), error);
        }

        public boolean hasVideo() {
            return width > 0 && height > 0 && videoCodec != null && !videoCodec.isEmpty();
        }

        /**
         * Returns true if the media has at least one audio stream.
         * Derived from audioChannels and audioCodec to avoid ambiguity with usability checks.
         */
        public boolean hasAudioStream() {
            return audioChannels > 0
                    || (audioCodec != null && !audioCodec.isEmpty());
        }

        /**
         * Returns true if the media has audio that is usable for processing
         * (e.g. mixing, waveform, auto-captions). Requires both a valid codec
         * and at least one audio channel.
         */
        public boolean hasUsableAudio() {
            return hasAudioStream()
                    && audioCodec != null && !audioCodec.isBlank()
                    && audioChannels > 0;
        }
    }
}
