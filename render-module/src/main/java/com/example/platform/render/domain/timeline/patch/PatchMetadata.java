package com.example.platform.render.domain.timeline.patch;

import java.util.Map;

/**
 * Typed, bounded metadata for a patch.
 */
public record PatchMetadata(
        String source,
        String description,
        Map<String, String> tags) {

    public PatchMetadata {
        tags = tags != null ? Map.copyOf(tags) : Map.of();
    }
}
