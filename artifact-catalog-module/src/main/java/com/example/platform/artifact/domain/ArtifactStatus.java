package com.example.platform.artifact.domain;

/**
 * Catalog artifact lifecycle states.
 *
 * <p>{@link #TOMBSTONED} blocks new render references; {@link #PURGED} is reserved for GC.</p>
 */
public enum ArtifactStatus {
    ACTIVE,
    TOMBSTONED,
    PURGED,
    @Deprecated
    DEPRECATED,
    @Deprecated
    ARCHIVED
}
