package com.example.platform.web.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.platform.storage.domain.BlobStorage;
import com.example.platform.storage.domain.StoredObject;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class StorageBucketOrphanScannerTest {

    private BlobStorage blobStorage;
    private KnownStorageUriIndexService uriIndexService;
    private StorageBucketOrphanScanner scanner;

    @BeforeEach
    void setUp() {
        blobStorage = mock(BlobStorage.class);
        uriIndexService = mock(KnownStorageUriIndexService.class);
        scanner = new StorageBucketOrphanScanner(blobStorage, uriIndexService);
        ReflectionTestUtils.setField(scanner, "scanBuckets", List.of("artifacts"));
        ReflectionTestUtils.setField(scanner, "maxObjectsPerBucket", 100);
    }

    @Test
    void scanBucketsFlagsObjectsAbsentFromKnownIndex() {
        when(uriIndexService.buildKnownUriIndex())
                .thenReturn(Set.of(KnownStorageUriIndexService.normalize("localfs://artifacts/known.mp4")));
        when(uriIndexService.providerCode()).thenReturn("localfs");
        when(blobStorage.listObjects("artifacts", "", 100))
                .thenReturn(List.of(
                        new StoredObject("artifacts", "known.mp4", 10),
                        new StoredObject("artifacts", "orphan.mp4", 20)));

        StorageBucketOrphanScanner.OrphanScanResult result = scanner.scanBuckets();

        assertEquals(2, result.objectsScanned());
        assertEquals(1, result.orphanCount());
        assertEquals(1, result.knownUriCount());
        assertEquals("AST-005", result.orphans().getFirst().ruleId());
        assertEquals("orphan.mp4", result.orphans().getFirst().objectKey());
        assertTrue(result.orphans().getFirst().storageUri().endsWith("orphan.mp4"));
    }

    @Test
    void scanBucketsReturnsEmptyWhenAllObjectsAreKnown() {
        when(uriIndexService.buildKnownUriIndex())
                .thenReturn(Set.of(KnownStorageUriIndexService.normalize("localfs://artifacts/a.mp4")));
        when(uriIndexService.providerCode()).thenReturn("localfs");
        when(blobStorage.listObjects("artifacts", "", 100))
                .thenReturn(List.of(new StoredObject("artifacts", "a.mp4", 5)));

        StorageBucketOrphanScanner.OrphanScanResult result = scanner.scanBuckets();

        assertEquals(1, result.objectsScanned());
        assertEquals(0, result.orphanCount());
        assertTrue(result.orphans().isEmpty());
    }
}
