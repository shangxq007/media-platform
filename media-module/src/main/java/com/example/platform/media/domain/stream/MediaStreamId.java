package com.example.platform.media.domain.stream;

import java.io.Serializable;

/**
 * Source-level stream identity (SOURCE_STREAM_IDENTITY_AUTHORITY_V1).
 *
 * <p>A {@link MediaStreamId} is stable across re-probe and metadata
 * enrichment. It identifies a stream of the SOURCE media — distinct from
 * Timeline Track identity, Caption domain identity, and audio mix bus.
 */
public record MediaStreamId(String value) implements Serializable {

    public MediaStreamId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("MediaStreamId must not be blank");
        }
    }

    public static MediaStreamId of(String value) {
        return new MediaStreamId(value);
    }
}
