package com.example.platform.shared.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StorageKeyPolicyTest {

    @Test
    void tenantPathFollowsFormat() {
        String path = StorageKeyPolicy.tenantPath("tenant-1", "ws-1", "proj-1", "assets", "asset-1");
        assertEquals("tenant/tenant-1/workspace/ws-1/project/proj-1/assets/asset-1", path);
    }

    @Test
    void assetPathFollowsFormat() {
        String path = StorageKeyPolicy.assetPath("tenant-1", "ws-1", "proj-1", "asset-1", "video.mp4");
        assertEquals("tenant/tenant-1/workspace/ws-1/project/proj-1/assets/asset-1/video.mp4", path);
    }

    @Test
    void exportPathFollowsFormat() {
        String path = StorageKeyPolicy.exportPath("tenant-1", "ws-1", "proj-1", "export-1", "output.mp4");
        assertEquals("tenant/tenant-1/workspace/ws-1/project/proj-1/exports/export-1/output.mp4", path);
    }

    @Test
    void tempPathFollowsFormat() {
        String path = StorageKeyPolicy.tempPath("tenant-1", "ws-1", "tmp-file-uuid");
        assertEquals("tenant/tenant-1/workspace/ws-1/tmp/tmp-file-uuid", path);
    }

    @Test
    void defaultWorkspaceUsedWhenNull() {
        String path = StorageKeyPolicy.tenantPath("tenant-1", null, "proj-1");
        assertTrue(path.contains("workspace/default/"));
    }

    @Test
    void assertValidPathRejectsTraversal() {
        assertThrows(SecurityException.class, () ->
                StorageKeyPolicy.assertValidPath("tenant/tenant-1/../../../etc/passwd"));
    }

    @Test
    void assertValidPathRejectsBackslash() {
        assertThrows(SecurityException.class, () ->
                StorageKeyPolicy.assertValidPath("tenant\\tenant-1\\file"));
    }

    @Test
    void assertValidPathRejectsAbsolute() {
        assertThrows(SecurityException.class, () ->
                StorageKeyPolicy.assertValidPath("/etc/passwd"));
    }

    @Test
    void assertValidPathAcceptsValidPath() {
        StorageKeyPolicy.assertValidPath("tenant/tenant-1/workspace/ws-1/file.mp4");
    }

    @Test
    void hasTenantPrefixWorks() {
        assertTrue(StorageKeyPolicy.hasTenantPrefix("tenant/tenant-1/workspace/ws-1/file.mp4", "tenant-1"));
        assertFalse(StorageKeyPolicy.hasTenantPrefix("tenant/tenant-2/workspace/ws-1/file.mp4", "tenant-1"));
        assertFalse(StorageKeyPolicy.hasTenantPrefix(null, "tenant-1"));
    }

    @Test
    void extractTenantFromPathWorks() {
        assertEquals("tenant-1", StorageKeyPolicy.extractTenantFromPath("tenant/tenant-1/workspace/ws-1/file.mp4"));
        assertEquals(null, StorageKeyPolicy.extractTenantFromPath(null));
        assertEquals(null, StorageKeyPolicy.extractTenantFromPath("artifacts/file.mp4"));
    }

    @Test
    void assertValidIdRejectsTraversal() {
        assertThrows(SecurityException.class, () ->
                StorageKeyPolicy.tenantPath("tenant../evil", "ws-1", "proj-1"));
    }

    @Test
    void sanitizeStripsSpecialCharacters() {
        String path = StorageKeyPolicy.tenantPath("tenant-1;rm -rf", "ws-1", "proj-1");
        assertFalse(path.contains(";"));
        assertFalse(path.contains(" "));
    }
}
