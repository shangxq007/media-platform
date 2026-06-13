package com.example.platform.render.infrastructure.asset;

import com.example.platform.render.domain.asset.Asset;
import com.example.platform.shared.web.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssetServiceTest {

    private AssetRepository assetRepository;
    private AssetService assetService;

    @BeforeEach
    void setUp() {
        assetRepository = mock(AssetRepository.class);
        assetService = new AssetService(assetRepository);
        TenantContext.set("tenant-1");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void shouldRegisterAsset() {
        Asset expected = new Asset("asset-1", "tenant-1", "proj-1",
                "tenant/workspace/project/assets/asset-1/video.mp4",
                "VIDEO", "video.mp4", 1024L, "abc123", 5000L, 1920, 1080, Instant.now());
        when(assetRepository.register(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(expected);

        Asset result = assetService.register("proj-1", "tenant/workspace/project/assets/asset-1/video.mp4",
                "VIDEO", "video.mp4", 1024L, "abc123", 5000L, 1920, 1080);

        assertNotNull(result);
        assertEquals("asset-1", result.id());
        assertEquals("tenant-1", result.tenantId());
        verify(assetRepository).register("tenant-1", "proj-1",
                "tenant/workspace/project/assets/asset-1/video.mp4",
                "VIDEO", "video.mp4", 1024L, "abc123", 5000L, 1920, 1080);
    }

    @Test
    void shouldRejectRegistrationWithoutTenant() {
        TenantContext.clear();

        assertThrows(SecurityException.class, () ->
                assetService.register("proj-1", "key", "VIDEO", "file.mp4", 100L, null, null, null, null));
    }

    @Test
    void shouldRejectInvalidStorageKey() {
        assertThrows(SecurityException.class, () ->
                assetService.register("proj-1", "/absolute/path", "VIDEO", "file.mp4", 100L, null, null, null, null));
    }

    @Test
    void shouldRejectTraversalStorageKey() {
        assertThrows(SecurityException.class, () ->
                assetService.register("proj-1", "../etc/passwd", "VIDEO", "file.mp4", 100L, null, null, null, null));
    }

    @Test
    void shouldGetById() {
        Asset expected = new Asset("asset-1", "tenant-1", "proj-1", "key", "VIDEO", "file.mp4",
                100L, null, null, null, null, Instant.now());
        when(assetRepository.findById("tenant-1", "asset-1")).thenReturn(Optional.of(expected));

        Asset result = assetService.getById("proj-1", "asset-1");
        assertEquals("asset-1", result.id());
    }

    @Test
    void shouldRejectGetByIdWithWrongProject() {
        Asset asset = new Asset("asset-1", "tenant-1", "proj-other", "key", "VIDEO", "file.mp4",
                100L, null, null, null, null, Instant.now());
        when(assetRepository.findById("tenant-1", "asset-1")).thenReturn(Optional.of(asset));

        assertThrows(IllegalArgumentException.class, () -> assetService.getById("proj-1", "asset-1"));
    }

    @Test
    void shouldListByProject() {
        List<Asset> expected = List.of(
                new Asset("a1", "tenant-1", "proj-1", "key1", "VIDEO", "v1.mp4", 100L, null, null, null, null, Instant.now()),
                new Asset("a2", "tenant-1", "proj-1", "key2", "IMAGE", "img.png", 50L, null, null, 1920, 1080, Instant.now())
        );
        when(assetRepository.listByProject("tenant-1", "proj-1")).thenReturn(expected);

        List<Asset> result = assetService.listByProject("proj-1");
        assertEquals(2, result.size());
    }

    @Test
    void shouldDelete() {
        Asset asset = new Asset("asset-1", "tenant-1", "proj-1", "key", "VIDEO", "file.mp4",
                100L, null, null, null, null, Instant.now());
        when(assetRepository.findById("tenant-1", "asset-1")).thenReturn(Optional.of(asset));
        when(assetRepository.delete("tenant-1", "asset-1")).thenReturn(true);

        boolean result = assetService.delete("proj-1", "asset-1");
        assertTrue(result);
    }

    @Test
    void shouldGeneratePreviewUrl() {
        Asset asset = new Asset("asset-1", "tenant-1", "proj-1", "key", "VIDEO", "file.mp4",
                100L, null, null, null, null, Instant.now());
        when(assetRepository.findById("tenant-1", "asset-1")).thenReturn(Optional.of(asset));

        String url = assetService.getPreviewUrl("proj-1", "asset-1");
        assertEquals("/api/v1/projects/proj-1/assets/asset-1/raw", url);
    }
}
