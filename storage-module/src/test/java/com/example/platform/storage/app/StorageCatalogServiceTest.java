package com.example.platform.storage.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.storage.domain.BlobStorage;
import com.example.platform.storage.domain.PutObjectCommand;
import com.example.platform.storage.domain.StorageObjectRef;
import java.util.List;
import org.junit.jupiter.api.Test;

class StorageCatalogServiceTest {

    @Test
    void providerCodesReturnsCodesFromProviders() {
        BlobStorage localFs = new BlobStorage() {
            @Override public String code() { return "localFsStorageProvider"; }
            @Override public StorageObjectRef put(PutObjectCommand command) { return null; }
            @Override public String presign(String objectKey) { return null; }
        };

        BlobStorage s3 = new BlobStorage() {
            @Override public String code() { return "s3StorageProvider"; }
            @Override public StorageObjectRef put(PutObjectCommand command) { return null; }
            @Override public String presign(String objectKey) { return null; }
        };

        StorageCatalogService service = new StorageCatalogService(List.of(localFs, s3), null);
        List<String> codes = service.providerCodes();

        assertNotNull(codes);
        assertEquals(2, codes.size());
        assertTrue(codes.contains("localFsStorageProvider"));
        assertTrue(codes.contains("s3StorageProvider"));
    }

    @Test
    void providerCodesReturnsEmptyWhenNoProviders() {
        StorageCatalogService service = new StorageCatalogService(List.of(), null);
        List<String> codes = service.providerCodes();

        assertNotNull(codes);
        assertTrue(codes.isEmpty());
    }

    @Test
    void providerCodesWithSingleProvider() {
        BlobStorage provider = new BlobStorage() {
            @Override public String code() { return "onlyProvider"; }
            @Override public StorageObjectRef put(PutObjectCommand command) { return null; }
            @Override public String presign(String objectKey) { return null; }
        };

        StorageCatalogService service = new StorageCatalogService(List.of(provider), null);
        List<String> codes = service.providerCodes();

        assertEquals(1, codes.size());
        assertEquals("onlyProvider", codes.get(0));
    }
}
