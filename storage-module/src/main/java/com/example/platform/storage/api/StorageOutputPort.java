package com.example.platform.storage.api;

import com.example.platform.storage.contract.StorageReference;
import java.util.Objects;

/** Writes an internal scoped output and returns evidence accepted by Storage. */
public interface StorageOutputPort {
    WrittenOutput write(OutputCommand command);
    record OutputCommand(StorageOwnershipScope owner, IssuanceIdempotencyKey key,
                         String relativePath, String contentType) {
        public OutputCommand {
            Objects.requireNonNull(owner); Objects.requireNonNull(key);
            if (owner.projectId() == null || owner.projectId().isBlank()) throw new IllegalArgumentException("output project required");
            if (relativePath == null || relativePath.isBlank()) throw new IllegalArgumentException("relativePath must not be null or blank");
            if (contentType == null || contentType.isBlank()) throw new IllegalArgumentException("content type required");
        }
    }
    record WrittenOutput(StorageObjectIssuance.IssuanceResult issuance, StorageReference reference) {
        public WrittenOutput { Objects.requireNonNull(issuance); Objects.requireNonNull(reference); }
    }
}
