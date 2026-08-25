package com.example.platform.workerfabric.reuse;

import java.util.Objects;

/** Artifact authority failed after durable bytes existed; evidence is retained for reconciliation. */
public final class ArtifactCommitAfterDurablePublicationException extends RuntimeException {

    private final DurableStoragePublication orphanedPublication;

    public ArtifactCommitAfterDurablePublicationException(
            DurableStoragePublication orphanedPublication,
            Throwable cause) {
        super("Artifact commit failed after durable storage publication", cause);
        this.orphanedPublication = Objects.requireNonNull(
                orphanedPublication, "orphanedPublication");
    }

    public DurableStoragePublication orphanedPublication() {
        return orphanedPublication;
    }
}
