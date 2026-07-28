package com.example.platform.artifact.domain;

/**
 * Role of a storage replica bound to an Artifact.
 *
 * <p>Stable, closed enum — serialized by name for canonical representation.
 */
public enum ReplicaRole {
    PRIMARY,
    SECONDARY,
    CACHE,
    ARCHIVE,
    DELIVERY
}
