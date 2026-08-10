package com.example.platform.storage.contract.read;
import com.example.platform.storage.contract.StorageObjectId;
import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;
public record StorageReadRequest(StorageObjectId objectId, Optional<ByteRange> byteRange, IntegrityRequirement integrityRequirement) implements Serializable {
    public StorageReadRequest {
        Objects.requireNonNull(objectId, "objectId");
        Objects.requireNonNull(byteRange, "byteRange");
        Objects.requireNonNull(integrityRequirement, "integrityRequirement");
    }
}
