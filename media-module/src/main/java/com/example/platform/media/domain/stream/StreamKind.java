package com.example.platform.media.domain.stream;

/**
 * Canonical source stream kind (SOURCE_STREAM_IDENTITY_AUTHORITY_V1).
 *
 * <p>Distinct from Timeline Track, Caption Track, audio mix bus, and render
 * track. This enum names the SOURCE container stream kind only.
 */
public enum StreamKind {
    VIDEO,
    AUDIO,
    DATA,
    SUBTITLE
}
