package com.example.platform.artifact.app;

import com.example.platform.artifact.domain.Artifact;
import com.example.platform.artifact.domain.ArtifactStatus;
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

    private final ArtifactCatalogRepository artifactRepository;
    private final Optional<BlobStorage> blobStorage;

    public ArtifactStorageIntegrityScanner(
            @Autowired(required = false) ArtifactCatalogRepository artifactRepository,
            @Autowired(required = false) BlobStorage blobStorage) {
        this.artifactRepository = artifactRepository;
        this.blobStorage = Optional.ofNullable(blobStorage);
    }

    public List<StorageFinding> scanCatalog() {
        List<StorageFinding> findings = new ArrayList<>();
        if (artifactRepository == null) {
            return findings;
        }
        for (Artifact artifact : artifactRepository.findAll()) {
            String uri = artifact.storageUri();
            if (uri == null || uri.isBlank()) {
                continue;
            }
            Optional<StorageObjectRef> ref = BlobStorage.parseUri(uri);
            if (ref.isEmpty() || blobStorage.isEmpty()) {
                continue;
            }
            StorageObjectRef objectRef = ref.get();
            boolean exists = blobStorage.get()
                    .get(objectRef.bucket(), objectRef.objectKey())
                    .isPresent();
            if (artifact.status() == ArtifactStatus.TOMBSTONED && exists) {
                findings.add(new StorageFinding(
                        "AST-002", artifact.id(), artifact.projectId(), uri,
                        "Tombstoned artifact still has blob in storage"));
            }
            if (artifact.status() == ArtifactStatus.ACTIVE && !exists) {
                findings.add(new StorageFinding(
                        "AST-004", artifact.id(), artifact.projectId(), uri,
                        "Active artifact storage object not found"));
            }
        }
        return findings;
    }

    public record StorageFinding(String ruleId, String artifactId, String projectId,
                                 String storageUri, String message) {}
}
