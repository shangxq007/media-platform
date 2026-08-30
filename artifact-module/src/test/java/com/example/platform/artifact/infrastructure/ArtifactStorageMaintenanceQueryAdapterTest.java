package com.example.platform.artifact.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.example.platform.artifact.app.ArtifactStorageMaintenanceEntry;
import com.example.platform.artifact.domain.ArtifactState;
import java.util.List;
import org.junit.jupiter.api.Test;

class ArtifactStorageMaintenanceQueryAdapterTest {

    @Test
    void mapsAllFieldsAndDelegatesExactlyOnce() {
        ArtifactRepository repository = mock(ArtifactRepository.class);
        when(repository.scanStorageMaintenanceRecords()).thenReturn(List.of(
                new ArtifactRepository.StorageMaintenanceRecord(
                        "artifact-1", "project-1", ArtifactState.AVAILABLE, "bucket/object-1"),
                new ArtifactRepository.StorageMaintenanceRecord(
                        "artifact-2", "project-2", ArtifactState.DELETING, "bucket/object-2")));

        ArtifactStorageMaintenanceQueryAdapter adapter =
                new ArtifactStorageMaintenanceQueryAdapter(repository);

        assertEquals(List.of(
                new ArtifactStorageMaintenanceEntry(
                        "artifact-1", "project-1", ArtifactState.AVAILABLE, "bucket/object-1"),
                new ArtifactStorageMaintenanceEntry(
                        "artifact-2", "project-2", ArtifactState.DELETING, "bucket/object-2")),
                adapter.scanStorageMaintenanceEntries());
        verify(repository, times(1)).scanStorageMaintenanceRecords();
        verifyNoMoreInteractions(repository);
    }
}
