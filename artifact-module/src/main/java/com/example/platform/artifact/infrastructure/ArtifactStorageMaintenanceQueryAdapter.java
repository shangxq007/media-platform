package com.example.platform.artifact.infrastructure;

import com.example.platform.artifact.app.ArtifactStorageMaintenanceEntry;
import com.example.platform.artifact.app.ArtifactStorageMaintenanceQuery;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Infrastructure adapter for the internal Artifact storage-maintenance application port. */
@Repository
public class ArtifactStorageMaintenanceQueryAdapter implements ArtifactStorageMaintenanceQuery {

    private final ArtifactRepository artifactRepository;

    public ArtifactStorageMaintenanceQueryAdapter(ArtifactRepository artifactRepository) {
        this.artifactRepository = artifactRepository;
    }

    @Override
    public List<ArtifactStorageMaintenanceEntry> scanStorageMaintenanceEntries() {
        return artifactRepository.scanStorageMaintenanceRecords().stream()
                .map(record -> new ArtifactStorageMaintenanceEntry(
                        record.artifactId(),
                        record.projectId(),
                        record.state(),
                        record.storageObjectId()))
                .toList();
    }
}
