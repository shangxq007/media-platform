package com.example.platform.artifact.app;

import com.example.platform.artifact.domain.ArtifactState;
import com.example.platform.storage.domain.BlobStorage;
import com.example.platform.storage.domain.StorageObjectRef;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Finds tombstoned artifacts whose blobs still exist, and active artifacts whose remote blobs are missing.
 */
@Service
public class ArtifactStorageIntegrityScanner {

    private final ArtifactStorageMaintenanceQuery storageMaintenanceQuery;
    private final Optional<BlobStorage> blobStorage;

    public ArtifactStorageIntegrityScanner(
            @Autowired(required = false) ArtifactStorageMaintenanceQuery storageMaintenanceQuery,
            @Autowired(required = false) BlobStorage blobStorage) {
        this.storageMaintenanceQuery = storageMaintenanceQuery;
        this.blobStorage = Optional.ofNullable(blobStorage);
    }

    public List<StorageFinding> scanCatalog() {
        List<StorageFinding> findings = new ArrayList<>();
        if (storageMaintenanceQuery == null) {
            return findings;
        }
        for (ArtifactStorageMaintenanceEntry artifact
                : storageMaintenanceQuery.scanStorageMaintenanceEntries()) {
            String location = artifact.storageObjectId();
            if (location == null || location.isBlank()) {
                continue;
            }
            Optional<StorageObjectRef> ref = toStorageObjectRef(location);
            if (ref.isEmpty() || blobStorage.isEmpty()) {
                continue;
            }
            StorageObjectRef objectRef = ref.get();
            boolean exists = blobStorage.get()
                    .get(objectRef.bucket(), objectRef.objectKey())
                    .isPresent();
            if ((artifact.state() == ArtifactState.DELETING || artifact.state() == ArtifactState.DELETED) && exists) {
                findings.add(new StorageFinding(
                        "AST-002", artifact.artifactId(), artifact.projectId(),
                        "Tombstoned artifact still has blob in storage"));
            }
            if (artifact.state() == ArtifactState.AVAILABLE && !exists) {
                findings.add(new StorageFinding(
                        "AST-004", artifact.artifactId(), artifact.projectId(),
                        "Active artifact storage object not found"));
            }
        }
        return findings;
    }

    private static Optional<StorageObjectRef> toStorageObjectRef(String location) {
        Optional<StorageObjectRef> parsed = BlobStorage.parseUri(location);
        if (parsed.isPresent()) {
            return parsed;
        }
        String[] parts = location.split("/", 2);
        return parts.length == 2 && !parts[0].isBlank() && !parts[1].isBlank()
                ? Optional.of(new StorageObjectRef("internal", parts[0], parts[1]))
                : Optional.empty();
    }

    public record StorageFinding(String ruleId, String artifactId, String projectId,
                                 String message) {}
}
