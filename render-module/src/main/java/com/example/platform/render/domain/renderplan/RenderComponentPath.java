package com.example.platform.render.domain.renderplan;

import java.util.List;
import java.util.Objects;

/**
 * Typed path identifying the authored component a render node is bound to
 * (trackId/clipId/effectInstanceId/textElementId/audioMixInput/...). Canonical
 * string form: {@code "kind:seg1:seg2"} (C6).
 *
 * @param kind     component-kind discriminator
 * @param segments ordered, non-blank path segments (never empty; at least one segment)
 */
public record RenderComponentPath(RenderComponentKind kind, List<String> segments) {

    public RenderComponentPath {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(segments, "segments");
        if (segments.isEmpty()) {
            throw new IllegalArgumentException("segments must not be empty");
        }
        for (String segment : segments) {
            Objects.requireNonNull(segment, "segment");
            if (segment.isBlank()) {
                throw new IllegalArgumentException("segment must not be blank");
            }
        }
        segments = List.copyOf(segments);
    }

    /**
     * Factory for a single-segment path.
     */
    public static RenderComponentPath of(RenderComponentKind kind, String segment) {
        return new RenderComponentPath(kind, List.of(segment));
    }

    /**
     * Canonical form: {@code "kind:seg1:seg2"}. The kind is encoded by its
     * canonical name() to keep the path deterministic and provider-neutral.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(kind.name());
        for (String segment : segments) {
            sb.append(':').append(segment);
        }
        return sb.toString();
    }
}
