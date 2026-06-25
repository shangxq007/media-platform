package com.example.platform.web.assets;

import com.example.platform.render.domain.asset.Asset;
import com.example.platform.render.infrastructure.asset.AssetService;
import com.example.platform.render.app.asset.AssetRegistryService;
import com.example.platform.render.app.event.TimelineReviewEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssetControllerTest {

    private AssetService assetService;
    private AssetController controller;

    @BeforeEach
    void setUp() {
        assetService = mock(AssetService.class);
        AssetRegistryService assetRegistryService = mock(AssetRegistryService.class);
        TimelineReviewEventPublisher eventPublisher = mock(TimelineReviewEventPublisher.class);
        controller = new AssetController(assetService, assetRegistryService, eventPublisher);
    }

    @Test
    void shouldListAssets() {
        List<Asset> expected = List.of(testAsset("a1"));
        when(assetService.listByProject("proj-1")).thenReturn(expected);

        ResponseEntity<List<Asset>> response = controller.listAssets("proj-1");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
    }

    @Test
    void shouldGetAsset() {
        Asset expected = testAsset("a1");
        when(assetService.getById("proj-1", "a1")).thenReturn(expected);

        ResponseEntity<Asset> response = controller.getAsset("proj-1", "a1");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("a1", response.getBody().id());
    }

    @Test
    void shouldRegisterAsset() {
        Asset expected = testAsset("a1");
        when(assetService.register(any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(expected);

        AssetController.RegisterAssetRequest request = new AssetController.RegisterAssetRequest(
                "tenant/workspace/project/assets/a1/v.mp4", "VIDEO", "v.mp4", 100L, null, null, null, null);
        ResponseEntity<Asset> response = controller.registerAsset("proj-1", request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("a1", response.getBody().id());
    }

    @Test
    void shouldGetPreviewUrl() {
        when(assetService.getPreviewUrl("proj-1", "a1")).thenReturn("/api/v1/projects/proj-1/assets/a1/raw");

        ResponseEntity<Map<String, String>> response = controller.getPreviewUrl("proj-1", "a1");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("/api/v1/projects/proj-1/assets/a1/raw", response.getBody().get("previewUrl"));
    }

    @Test
    void shouldDeleteAsset() {
        when(assetService.delete("proj-1", "a1")).thenReturn(true);

        ResponseEntity<Map<String, Object>> response = controller.deleteAsset("proj-1", "a1");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("deleted"));
    }

    private static Asset testAsset(String id) {
        return new Asset(id, "t1", "p1", "key1", "VIDEO", "v.mp4",
                100L, null, null, null, null,
                "v1", null, null, null, null, null, null, false, false, "DRAFT",
                Instant.now(), Instant.now());
    }
}
