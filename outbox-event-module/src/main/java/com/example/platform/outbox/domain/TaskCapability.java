package com.example.platform.outbox.domain;

/**
 * Task capability types for the platform coordination layer.
 */
public enum TaskCapability {
    PROBE,
    ASR,
    OCR,
    VISION,
    EMBEDDING,
    REINDEX,
    PACKAGE,
    VALIDATE,
    MEDIA_PIPELINE,
    TRANSCODE,
    FRAME_EXTRACTION,
    FILTER,
    THUMBNAIL
}
