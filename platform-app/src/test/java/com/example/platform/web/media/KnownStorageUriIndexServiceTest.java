package com.example.platform.web.media;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.platform.artifact.app.ArtifactStorageMaintenanceEntry;
import com.example.platform.artifact.app.ArtifactStorageMaintenanceQuery;
import com.example.platform.artifact.domain.ArtifactState;
import com.example.platform.storage.domain.BlobStorage;
import com.example.platform.timeline.app.SystemMaintenanceReader;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class KnownStorageUriIndexServiceTest {

    @Test
    void includesActiveMaintenanceLocationsAndExcludesDeletedEntries() {
        ArtifactStorageMaintenanceQuery query = mock(ArtifactStorageMaintenanceQuery.class);
        SystemMaintenanceReader systemMaintenanceReader = mock(SystemMaintenanceReader.class);
        BlobStorage blobStorage = mock(BlobStorage.class);
        when(query.scanStorageMaintenanceEntries()).thenReturn(List.of(
                new ArtifactStorageMaintenanceEntry(
                        "artifact-active", "project-1", ArtifactState.AVAILABLE,
                        "artifact-bucket/active.mov"),
                new ArtifactStorageMaintenanceEntry(
                        "artifact-deleted", "project-1", ArtifactState.DELETED,
                        "artifact-bucket/deleted.mov")));
        when(systemMaintenanceReader.listProjectIdsWithSnapshots()).thenReturn(List.of());

        KnownStorageUriIndexService service = new KnownStorageUriIndexService(
                null, query, null, systemMaintenanceReader, blobStorage);

        Set<String> index = service.buildKnownUriIndex();

        assertTrue(index.contains("artifact-bucket/active.mov"));
        assertFalse(index.contains("artifact-bucket/deleted.mov"));
        verify(query).scanStorageMaintenanceEntries();
    }
}
