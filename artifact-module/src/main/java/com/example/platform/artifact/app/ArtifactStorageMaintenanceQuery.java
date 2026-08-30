package com.example.platform.artifact.app;

import java.util.List;

/** Application-owned port for bounded internal Artifact storage-maintenance reads. */
public interface ArtifactStorageMaintenanceQuery {

    List<ArtifactStorageMaintenanceEntry> scanStorageMaintenanceEntries();
}
