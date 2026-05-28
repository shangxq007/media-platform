package com.example.platform.render.app.cache;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.shared.tenant.StorageKeyPolicy;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

@ExtendWith(MockitoExtension.class)
class RenderCacheTenantGuardTest {

    @Mock
    private DSLContext dsl;

    private RenderCacheTenantGuard guard;

    @BeforeEach
    void setUp() {
        guard = new RenderCacheTenantGuard(dsl);
    }

    // ========== requireJobTenant tests ==========

    @Test
    void requireJobTenant_throwsWhenTenantIdIsNull() {
        assertThrows(IllegalArgumentException.class,
                () -> guard.requireJobTenant(null, "job-1"));
    }

    @Test
    void requireJobTenant_throwsWhenTenantIdIsBlank() {
        assertThrows(IllegalArgumentException.class,
                () -> guard.requireJobTenant("  ", "job-1"));
    }

    @Test
    void requireJobTenant_throwsWhenJobIdIsNull() {
        assertThrows(IllegalArgumentException.class,
                () -> guard.requireJobTenant("tenant-1", null));
    }

    // ========== assertRemoteUriTenantPrefix tests ==========

    @Test
    void assertRemoteUriTenantPrefix_acceptsValidTenantPrefix() {
        assertDoesNotThrow(() ->
                guard.assertRemoteUriTenantPrefix("tenant-1",
                        "s3StorageProvider://render-cache/tenant/tenant-1/segment/tl/seg_0.mp4"));
    }

    @Test
    void assertRemoteUriTenantPrefix_rejectsMismatchedTenant() {
        assertThrows(IllegalArgumentException.class,
                () -> guard.assertRemoteUriTenantPrefix("tenant-1",
                        "s3StorageProvider://render-cache/tenant/tenant-2/segment/tl/seg_0.mp4"));
    }

    @Test
    void assertRemoteUriTenantPrefix_rejectsNoTenantPrefix() {
        assertThrows(IllegalArgumentException.class,
                () -> guard.assertRemoteUriTenantPrefix("tenant-1",
                        "s3StorageProvider://render-cache/segment/tl/seg_0.mp4"));
    }

    @Test
    void assertRemoteUriTenantPrefix_acceptsNullUri() {
        assertDoesNotThrow(() -> guard.assertRemoteUriTenantPrefix("tenant-1", null));
    }

    @Test
    void assertRemoteUriTenantPrefix_acceptsBlankUri() {
        assertDoesNotThrow(() -> guard.assertRemoteUriTenantPrefix("tenant-1", "  "));
    }

    @Test
    void assertRemoteUriTenantPrefix_acceptsNullTenantId() {
        assertDoesNotThrow(() -> guard.assertRemoteUriTenantPrefix(null,
                "s3StorageProvider://render-cache/tenant-1/file.mp4"));
    }

    @Test
    void assertRemoteUriTenantPrefix_rejectsPathTraversalAttempt() {
        // StorageKeyPolicy.hasTenantPrefix checks "tenant/{tenantId}/" prefix
        // Path traversal like "tenant/tenant-2/../tenant-1" won't match "tenant/tenant-1/"
        assertThrows(IllegalArgumentException.class,
                () -> guard.assertRemoteUriTenantPrefix("tenant-1",
                        "s3StorageProvider://render-cache/tenant/tenant-2/../tenant-1/file.mp4"));
    }

    @Test
    void assertRemoteUriTenantPrefix_usesStorageKeyPolicy() {
        String validUri = "s3StorageProvider://render-cache/tenant/tenant-1/workspace/ws-1/project/proj-1/file.mp4";
        assertDoesNotThrow(() -> guard.assertRemoteUriTenantPrefix("tenant-1", validUri));

        String invalidUri = "s3StorageProvider://render-cache/tenant/other-tenant/workspace/ws-1/file.mp4";
        assertThrows(IllegalArgumentException.class,
                () -> guard.assertRemoteUriTenantPrefix("tenant-1", invalidUri));
    }

    @Test
    void assertRemoteUriTenantPrefix_acceptsValidLocalFsUri() {
        assertDoesNotThrow(() ->
                guard.assertRemoteUriTenantPrefix("tenant-1",
                        "localFsStorageProvider://artifacts/tenant/tenant-1/workspace/ws-1/file.mp4"));
    }

    @Test
    void assertRemoteUriTenantPrefix_rejectsLocalFsUriWithoutTenant() {
        assertThrows(IllegalArgumentException.class,
                () -> guard.assertRemoteUriTenantPrefix("tenant-1",
                        "localFsStorageProvider://artifacts/workspace/ws-1/file.mp4"));
    }

    // ========== assertExecutionStateTenant tests ==========

    @Test
    void assertExecutionStateTenant_acceptsEmptyState() {
        assertDoesNotThrow(() -> guard.assertExecutionStateTenant("tenant-1", Map.of()));
    }

    @Test
    void assertExecutionStateTenant_acceptsNullState() {
        assertDoesNotThrow(() -> guard.assertExecutionStateTenant("tenant-1", null));
    }

    @Test
    void assertExecutionStateTenant_validatesSegmentCacheIndex() {
        Map<String, Object> entry = new HashMap<>();
        entry.put("remoteUri", "s3StorageProvider://render-cache/tenant/tenant-1/segment/tl/seg_0.mp4");
        Map<String, Object> index = new HashMap<>();
        index.put("seg_0", entry);
        Map<String, Object> state = new HashMap<>();
        state.put("segmentCacheIndex", index);

        assertDoesNotThrow(() -> guard.assertExecutionStateTenant("tenant-1", state));
    }

    @Test
    void assertExecutionStateTenant_rejectsMismatchedTenantInSegmentCache() {
        Map<String, Object> entry = new HashMap<>();
        entry.put("remoteUri", "s3StorageProvider://render-cache/tenant/tenant-2/segment/tl/seg_0.mp4");
        Map<String, Object> index = new HashMap<>();
        index.put("seg_0", entry);
        Map<String, Object> state = new HashMap<>();
        state.put("segmentCacheIndex", index);

        assertThrows(IllegalArgumentException.class,
                () -> guard.assertExecutionStateTenant("tenant-1", state));
    }

    @Test
    void assertExecutionStateTenant_validatesMezzanineCacheIndex() {
        Map<String, Object> entry = new HashMap<>();
        entry.put("remoteUri", "s3StorageProvider://render-cache/tenant/tenant-1/mezzanine/final.mp4");
        Map<String, Object> state = new HashMap<>();
        state.put("mezzanineCacheIndex", entry);

        assertDoesNotThrow(() -> guard.assertExecutionStateTenant("tenant-1", state));
    }

    @Test
    void assertExecutionStateTenant_rejectsMismatchedTenantInMezzanine() {
        Map<String, Object> entry = new HashMap<>();
        entry.put("remoteUri", "s3StorageProvider://render-cache/tenant/tenant-2/mezzanine/final.mp4");
        Map<String, Object> state = new HashMap<>();
        state.put("mezzanineCacheIndex", entry);

        assertThrows(IllegalArgumentException.class,
                () -> guard.assertExecutionStateTenant("tenant-1", state));
    }

    // ========== Integration: StorageKeyPolicy compatibility ==========

    @Test
    void storageKeyPolicy_generatesCorrectTenantPrefix() {
        String key = StorageKeyPolicy.assetPath("tenant-1", "ws-1", "proj-1", "asset-1", "video.mp4");
        assertTrue(key.startsWith("tenant/tenant-1/"),
                "Generated key should start with tenant/{tenantId}/");
        assertTrue(StorageKeyPolicy.hasTenantPrefix(key, "tenant-1"));
        assertFalse(StorageKeyPolicy.hasTenantPrefix(key, "tenant-2"));
    }

    @Test
    void storageKeyPolicy_extractTenantFromGeneratedPath() {
        String key = StorageKeyPolicy.exportPath("tenant-abc", "ws-1", "proj-1", "export-1", "output.mp4");
        assertEquals("tenant-abc", StorageKeyPolicy.extractTenantFromPath(key));
    }

    @Test
    void storageKeyPolicy_hasTenantPrefix_worksWithGuard() {
        // Verify that StorageKeyPolicy.hasTenantPrefix is consistent with guard expectations
        String key = StorageKeyPolicy.tenantPath("tenant-1", "ws-1", "proj-1", "cache", "seg_0.mp4");
        assertTrue(StorageKeyPolicy.hasTenantPrefix(key, "tenant-1"));
        assertFalse(StorageKeyPolicy.hasTenantPrefix(key, "tenant-2"));
        assertFalse(StorageKeyPolicy.hasTenantPrefix(key, null));
        assertFalse(StorageKeyPolicy.hasTenantPrefix(null, "tenant-1"));
    }
}
