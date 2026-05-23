package com.example.platform.web.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.platform.shared.web.ErrorCodeRegistry;
import com.example.platform.shared.web.PlatformException;
import com.example.platform.storage.domain.BlobStorage;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
class StorageOrphanPurgeServiceTest {

    private StorageBucketOrphanScanner scanner;
    private KnownStorageUriIndexService uriIndex;
    private BlobStorage blobStorage;
    private StorageOrphanPurgeProperties properties;
    private ErrorCodeRegistry registry;
    private StorageOrphanPurgeService service;

    @BeforeEach
    void setUp() {
        scanner = mock(StorageBucketOrphanScanner.class);
        uriIndex = mock(KnownStorageUriIndexService.class);
        blobStorage = mock(BlobStorage.class);
        properties = new StorageOrphanPurgeProperties();
        properties.setEnabled(true);
        properties.setApprovalToken("secret-token");
        registry = new ErrorCodeRegistry();
        registry.loadErrorCodes();
        service = new StorageOrphanPurgeService(scanner, uriIndex, blobStorage, properties, registry);
    }

    @Test
    void rejectsInvalidApprovalToken() {
        assertThrows(PlatformException.class, () -> service.purge(true, "wrong", List.of()));
    }

    @Test
    void dryRunDoesNotDelete() {
        when(scanner.scanBuckets())
                .thenReturn(new StorageBucketOrphanScanner.OrphanScanResult(
                        1,
                        1,
                        List.of(new StorageBucketOrphanScanner.OrphanFinding(
                                "AST-005",
                                "artifacts",
                                "orphan.mp4",
                                "localfs://artifacts/orphan.mp4",
                                10,
                                "orphan")),
                        0));
        when(uriIndex.buildKnownUriIndex()).thenReturn(Set.of());

        StorageOrphanPurgeService.PurgeResult result =
                service.purge(true, "secret-token", List.of("localfs://artifacts/orphan.mp4"));

        assertEquals(1, result.deletedOrPlanned());
        verify(blobStorage, never()).deleteStorageUri(any());
    }

    @Test
    void purgeDeletesWhenApproved() {
        when(scanner.scanBuckets())
                .thenReturn(new StorageBucketOrphanScanner.OrphanScanResult(
                        1,
                        1,
                        List.of(new StorageBucketOrphanScanner.OrphanFinding(
                                "AST-005",
                                "artifacts",
                                "orphan.mp4",
                                "localfs://artifacts/orphan.mp4",
                                10,
                                "orphan")),
                        0));
        when(uriIndex.buildKnownUriIndex()).thenReturn(Set.of());
        when(blobStorage.deleteStorageUri("localfs://artifacts/orphan.mp4")).thenReturn(true);

        StorageOrphanPurgeService.PurgeResult result =
                service.purge(false, "secret-token", null);

        assertEquals(1, result.deletedOrPlanned());
        verify(blobStorage).deleteStorageUri("localfs://artifacts/orphan.mp4");
    }
}
