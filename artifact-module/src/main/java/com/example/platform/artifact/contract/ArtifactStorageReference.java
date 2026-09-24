package com.example.platform.artifact.contract;

import com.example.platform.storage.contract.StorageObjectId;
import com.example.platform.storage.contract.StorageProviderId;
import com.example.platform.storage.contract.StorageReplicaId;

import java.util.Objects;

/** Immutable storage issuance; providers never define Artifact identity or lifecycle. */
public record ArtifactStorageReference(
        StorageObjectId objectId,
        StorageReplicaId replicaId,
        StorageProviderId providerId,
        String issuanceId
) {
    public ArtifactStorageReference {
        Objects.requireNonNull(objectId, "objectId");
        Objects.requireNonNull(replicaId, "replicaId");
        Objects.requireNonNull(providerId, "providerId");
        requireText(issuanceId, "issuanceId");
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new ArtifactContractException(
                ArtifactContractErrorCode.MISSING_REQUIRED_FACT, name + " is required");
    }
}
