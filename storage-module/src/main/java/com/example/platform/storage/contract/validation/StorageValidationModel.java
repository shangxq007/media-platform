package com.example.platform.storage.contract.validation;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.storage.contract.StorageObjectId;
import com.example.platform.storage.contract.identity.StorageObjectLocation;
import com.example.platform.storage.contract.StorageProviderId;
import com.example.platform.storage.contract.StorageReplicaId;
import com.example.platform.storage.contract.namespace.StorageNamespace;
import com.example.platform.storage.contract.provider.StorageProviderCapabilities;
import com.example.platform.storage.contract.replica.ReplicaState;
import com.example.platform.storage.contract.write.StorageWriteSession;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;
public record StorageValidationModel(
    List<StorageObjectId> objects,
    List<StorageReplicaRecord> replicas,
    List<StorageProviderCapabilities> providers,
    List<StorageWriteSession> writeSessions,
    List<StorageObjectLocation> locations,
    String schemaVersion
) implements Serializable {
    public StorageValidationModel {
        objects = objects != null ? List.copyOf(objects) : List.of();
        replicas = replicas != null ? List.copyOf(replicas) : List.of();
        providers = providers != null ? List.copyOf(providers) : List.of();
        writeSessions = writeSessions != null ? List.copyOf(writeSessions) : List.of();
        locations = locations != null ? List.copyOf(locations) : List.of();
        schemaVersion = schemaVersion != null && !schemaVersion.isBlank() ? schemaVersion : "storage-semantics-v1";
    }
    public record StorageReplicaRecord(StorageReplicaId replicaId, StorageObjectId objectId, StorageObjectLocation location, ReplicaState state, ContentDigest committedDigest, long committedLength) {
        public StorageReplicaRecord {
            Objects.requireNonNull(replicaId, "replicaId");
            Objects.requireNonNull(objectId, "objectId");
            Objects.requireNonNull(location, "location");
            Objects.requireNonNull(state, "state");
        }
    }
}
