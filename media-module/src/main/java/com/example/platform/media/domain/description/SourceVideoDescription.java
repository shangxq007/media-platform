package com.example.platform.media.domain.description;

import java.io.Serializable;

/**
 * Source video structural description (F3 contract). Canonical structural
 * truth, normalized — not a provider probe DTO.
 */
public record SourceVideoDescription(
        Integer width,
        Integer height,
        String pixelFormat,
        String aspectRatioDescription) implements Serializable {

    public SourceVideoDescription {
        if (width != null && width <= 0) {
            throw new IllegalArgumentException("width must be > 0 when present");
        }
        if (height != null && height <= 0) {
            throw new IllegalArgumentException("height must be > 0 when present");
        }
    }
}
