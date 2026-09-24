package com.example.platform.artifact.domain.typed;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/** Media facts owned by an Artifact. The artifact id is the only identity. */
public record MediaArtifactDetails(
        String artifactId,
        String subtype,
        String container,
        String mimeType,
        String codec,
        Duration duration,
        Integer width,
        Integer height,
        String frameRate,
        List<Track> tracks,
        String colorSpace,
        Integer sampleRate,
        String channelLayout,
        String keyframeIndexReference,
        String thumbnailArtifactId) {
    public MediaArtifactDetails {
        require(artifactId, "artifactId");
        require(mimeType, "mimeType");
        if (duration != null && duration.isNegative()) throw new IllegalArgumentException("duration must be non-negative");
        if (width != null && width <= 0 || height != null && height <= 0 || sampleRate != null && sampleRate <= 0)
            throw new IllegalArgumentException("media dimensions and sample rate must be positive");
        tracks = tracks == null ? List.of() : List.copyOf(tracks);
        if (thumbnailArtifactId != null && thumbnailArtifactId.isBlank()) throw new IllegalArgumentException("thumbnailArtifactId must not be blank");
    }

    private static void require(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
    }

    public record Track(String kind, String codec, Integer index, Integer channels) {
        public Track {
            if (kind == null || kind.isBlank()) throw new IllegalArgumentException("track kind is required");
            if (index != null && index < 0) throw new IllegalArgumentException("track index must be non-negative");
            if (channels != null && channels <= 0) throw new IllegalArgumentException("track channels must be positive");
        }
    }
}
