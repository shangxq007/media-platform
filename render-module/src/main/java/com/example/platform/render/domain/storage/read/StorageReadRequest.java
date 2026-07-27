package com.example.platform.render.domain.storage.read;
import com.example.platform.render.domain.storage.identity.StorageObjectId;
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
