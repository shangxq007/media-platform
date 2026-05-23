package com.example.platform.render.app.cache;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class RenderCacheTenantGuardTest {

    @Test
    void allowsMatchingTenantObjectKey() {
        RenderCacheTenantGuard guard = new RenderCacheTenantGuard(null);
        assertDoesNotThrow(() -> guard.assertRemoteUriTenantPrefix(
                "tenant-a",
                "s3StorageProvider://render-cache/tenant-a/segment/tl/seg_0.mp4"));
    }

    @Test
    void deniesMismatchedObjectKeyPrefix() {
        RenderCacheTenantGuard guard = new RenderCacheTenantGuard(null);
        assertThrows(IllegalArgumentException.class,
                () -> guard.assertRemoteUriTenantPrefix(
                        "tenant-a",
                        "s3StorageProvider://render-cache/tenant-b/segment/tl/seg_0.mp4"));
    }
}
