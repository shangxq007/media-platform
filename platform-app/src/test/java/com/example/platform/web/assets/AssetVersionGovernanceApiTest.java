package com.example.platform.web.assets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.platform.render.app.asset.AssetRegistryService;
import com.example.platform.render.app.event.TimelineReviewEventPublisher;
import com.example.platform.render.domain.asset.AssetGovernanceMetadata;
import com.example.platform.render.domain.asset.AssetRegistryRecord;
import com.example.platform.render.infrastructure.asset.AssetService;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class AssetVersionGovernanceApiTest {

    private AssetService assetService;
    private AssetRegistryService assetRegistryService;
    private AssetController controller;

    @BeforeEach
    void setUp() {
        assetService = mock(AssetService.class);
        assetRegistryService = mock(AssetRegistryService.class);
        TimelineReviewEventPublisher ep = mock(TimelineReviewEventPublisher.class);
        controller = new AssetController(assetService, assetRegistryService, ep);
    }

    @Test
    void shouldReturnVersionInfo() {
        Instant now = Instant.now();
        AssetRegistryRecord record = new AssetRegistryRecord(
                "asset_123", "v7", "VIDEO", "user_001", "proj_1",
                "asset://asset_123?v=v7", "xmp://asset/asset_123/version/v7",
                "s3://bucket/key.mp4", "sha256:abc", AssetGovernanceMetadata.defaults(),
                now, now);

        when(assetRegistryService.resolve("asset_123")).thenReturn(Optional.of(record));

        ResponseEntity<AssetController.AssetVersionResponse> response =
                controller.getVersions("proj_1", "asset_123");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("asset_123", response.getBody().assetId());
        assertEquals("v7", response.getBody().assetVersion());
        assertEquals("VIDEO", response.getBody().assetType());
        assertEquals("asset://asset_123?v=v7", response.getBody().entityRef());
    }

    @Test
    void shouldReturnGovernanceMetadata() {
        Instant now = Instant.now();
        AssetGovernanceMetadata gov = new AssetGovernanceMetadata(
                "internal", "enterprise-owned", "30-day", "L2", true, false);
        AssetRegistryRecord record = new AssetRegistryRecord(
                "asset_456", "v1", "IMAGE", "user_001", "proj_1",
                "asset://asset_456?v=v1", "", "s3://bucket/img.png", "", gov, now, now);

        when(assetRegistryService.resolve("asset_456")).thenReturn(Optional.of(record));

        ResponseEntity<AssetController.AssetGovernanceResponse> response =
                controller.getGovernance("proj_1", "asset_456");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("asset_456", response.getBody().assetId());
        assertEquals("internal", response.getBody().classification());
        assertEquals("enterprise-owned", response.getBody().license());
        assertEquals("L2", response.getBody().securityLevel());
        assertTrue(response.getBody().containsPii());
        assertFalse(response.getBody().aiGenerated());
    }

    @Test
    void shouldReturn404ForMissingAsset() {
        when(assetRegistryService.resolve("asset_999")).thenReturn(Optional.empty());

        ResponseEntity<AssetController.AssetVersionResponse> response =
                controller.getVersions("proj_1", "asset_999");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void shouldExportJsonLd() {
        Instant now = Instant.now();
        AssetRegistryRecord record = new AssetRegistryRecord(
                "asset_789", "v3", "VIDEO", "user_001", "proj_1",
                "asset://asset_789?v=v3", "", "s3://bucket/video.mp4", "", AssetGovernanceMetadata.defaults(),
                now, now);

        when(assetRegistryService.resolve("asset_789")).thenReturn(Optional.of(record));
        when(assetRegistryService.buildJsonLdProjection("asset_789")).thenReturn(Map.of(
                "@id", "asset:asset_789",
                "@type", "MediaAsset",
                "asset:id", "asset_789"));

        ResponseEntity<Map<String, Object>> response =
                controller.exportJsonLd("proj_1", "asset_789");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("asset:asset_789", response.getBody().get("@id"));
    }
}
