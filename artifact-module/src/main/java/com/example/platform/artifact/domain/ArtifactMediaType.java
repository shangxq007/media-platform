package com.example.platform.artifact.domain;

import java.io.Serializable;

/**
 * Coarse media-type classification of an Artifact's content.
 *
 * <p>This is NOT a file-extension authority. It is an intentional, stable classification
 * chosen at registration time, independent of any filename or extension. Serialization is
 * by enum name for canonical stability.
 */
public enum ArtifactMediaType {
    VIDEO,
    AUDIO,
    IMAGE,
    TEXT,
    BINARY,
    MULTIPLEX,
    MANIFEST
}
