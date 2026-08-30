package com.example.platform.artifact.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.example.platform.artifact.domain.ArtifactState;
import com.example.platform.storage.domain.BlobStorage;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ArtifactStorageIntegrityScannerTest {

    @Test
    void reportsBoundedLifecycleFindingsWithoutPhysicalLocations() {
        ArtifactStorageMaintenanceQuery query = mock(ArtifactStorageMaintenanceQuery.class);
        BlobStorage blobStorage = mock(BlobStorage.class);
        when(query.scanStorageMaintenanceEntries()).thenReturn(List.of(
                new ArtifactStorageMaintenanceEntry(
                        "artifact-deleted", "project-1", ArtifactState.DELETED, "archive/deleted.bin"),
                new ArtifactStorageMaintenanceEntry(
                        "artifact-active", "project-2", ArtifactState.AVAILABLE, "active/missing.bin"),
                new ArtifactStorageMaintenanceEntry(
                        "artifact-invalid", "project-3", ArtifactState.AVAILABLE, "not-a-location")));
        when(blobStorage.get("archive", "deleted.bin")).thenReturn(Optional.of(new byte[] {1}));
        when(blobStorage.get("active", "missing.bin")).thenReturn(Optional.empty());

        List<ArtifactStorageIntegrityScanner.StorageFinding> findings =
                new ArtifactStorageIntegrityScanner(query, blobStorage).scanCatalog();

        assertEquals(List.of(
                new ArtifactStorageIntegrityScanner.StorageFinding(
                        "AST-002", "artifact-deleted", "project-1",
                        "Tombstoned artifact still has blob in storage"),
                new ArtifactStorageIntegrityScanner.StorageFinding(
                        "AST-004", "artifact-active", "project-2",
                        "Active artifact storage object not found")), findings);
        assertEquals(List.of("ruleId", "artifactId", "projectId", "message"),
                Arrays.stream(ArtifactStorageIntegrityScanner.StorageFinding.class.getRecordComponents())
                        .map(component -> component.getName())
                        .toList());
        verify(query).scanStorageMaintenanceEntries();
        verify(blobStorage).get("archive", "deleted.bin");
        verify(blobStorage).get("active", "missing.bin");
        verifyNoMoreInteractions(query, blobStorage);
    }
}
