package com.example.platform.artifact.domain;

import com.example.platform.storage.contract.StorageObjectId;
import com.example.platform.storage.contract.StorageProviderId;
import com.example.platform.storage.contract.StorageReplicaId;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * Immutable binding between an Artifact and a Storage Replica.
 *
 * <p>An artifact cannot become AVAILABLE without at least one AVAILABLE verified StorageReplica.
 * Deleted/failed StorageReplicas cannot satisfy Artifact availability.
 */
public record ArtifactReplicaBinding(
        String bindingId,
        ArtifactId artifactId,
        StorageObjectId storageObjectId,
        StorageReplicaId storageReplicaId,
        StorageProviderId providerId,
        ReplicaRole replicaRole,
        String region,
        Instant createdAt
) implements Serializable {

    public ArtifactReplicaBinding {
        Objects.requireNonNull(bindingId, "bindingId");
        if (bindingId.isBlank()) throw new IllegalArgumentException("bindingId must not be blank");
        Objects.requireNonNull(artifactId, "artifactId");
        Objects.requireNonNull(storageObjectId, "storageObjectId");
        Objects.requireNonNull(storageReplicaId, "storageReplicaId");
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(replicaRole, "replicaRole");
        Objects.requireNonNull(region, "region");
        if (region.isBlank()) throw new IllegalArgumentException("region must not be blank");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    /**
     * Canonical serialization with deterministic field ordering.
     */
    public String canonicalForm() {
        return "binding{" +
                "id=" + bindingId +
                ",artifact=" + artifactId.value() +
                ",object=" + storageObjectId.value() +
                ",replica=" + storageReplicaId.value() +
                ",provider=" + providerId.value() +
                ",role=" + replicaRole.name() +
                ",region=" + region +
                ",created=" + createdAt.toString() +
                '}';
    }

    @Override
    public String toString() {
        return canonicalForm();
    }
}
