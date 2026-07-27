package com.example.platform.render.domain.storage.read;
import com.example.platform.render.domain.storage.identity.StorageObjectId;
import java.io.Serializable;
import java.util.Objects;
public record StorageDeletionRequest(StorageObjectId objectId, boolean force) implements Serializable {
    public StorageDeletionRequest { Objects.requireNonNull(objectId, "objectId"); }
}
