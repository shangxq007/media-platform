package com.example.platform.identity.infrastructure.export;

import com.example.platform.artifact.app.ArtifactCatalogService;
import com.example.platform.artifact.domain.Artifact;
import com.example.platform.artifact.domain.ArtifactStatus;
import com.example.platform.identity.api.dto.ProjectResponse;
import com.example.platform.identity.app.TenantProjectService;
import com.example.platform.shared.asset.AssetDownloadUrlPort;
import com.example.platform.shared.export.ProjectAssetDescriptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ArtifactCatalogProjectAssetExportAdapterTest {

    @Mock
    private ArtifactCatalogService artifactCatalogService;

    @Mock
    private TenantProjectService tenantProjectService;

    @Mock
    private AssetDownloadUrlPort assetDownloadUrlPort;

    private ArtifactCatalogProjectAssetExportAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new ArtifactCatalogProjectAssetExportAdapter(
                artifactCatalogService, tenantProjectService, assetDownloadUrlPort);
    }

    @Test
    void listProjectAssetsShouldReturnProjectAssets() {
        ProjectResponse project = new ProjectResponse("prj-1", "tenant-1", "Test", "Desc", "ACTIVE", Instant.now());
        when(tenantProjectService.getProject("prj-1")).thenReturn(project);

        Artifact artifact = new Artifact("art-1", "job-1", "prj-1",
                "s3://bucket/video.mp4", "mp4", "1920x1080", 10L,
                ArtifactStatus.ACTIVE, null, Instant.now());
        when(artifactCatalogService.listArtifactsByProject("prj-1")).thenReturn(List.of(artifact));

        List<ProjectAssetDescriptor> result = adapter.listProjectAssets("prj-1");

        assertEquals(1, result.size());
        ProjectAssetDescriptor descriptor = result.get(0);
        assertEquals("art-1", descriptor.assetId());
        assertEquals("video.mp4", descriptor.filename());
        assertEquals("video", descriptor.type());
        assertEquals("video/mp4", descriptor.mimeType());
        assertEquals("s3://bucket/video.mp4", descriptor.storageUri());
    }

    @Test
    void listProjectAssetsShouldSkipTombstonedArtifacts() {
        ProjectResponse project = new ProjectResponse("prj-1", "tenant-1", "Test", "Desc", "ACTIVE", Instant.now());
        when(tenantProjectService.getProject("prj-1")).thenReturn(project);

        Artifact active = new Artifact("art-1", "job-1", "prj-1",
                "s3://bucket/video.mp4", "mp4", "1920x1080", 10L,
                ArtifactStatus.ACTIVE, null, Instant.now());
        Artifact tombstoned = new Artifact("art-2", "job-1", "prj-1",
                "s3://bucket/audio.mp3", "mp3", null, 30L,
                ArtifactStatus.TOMBSTONED, Instant.now(), Instant.now());
        when(artifactCatalogService.listArtifactsByProject("prj-1")).thenReturn(List.of(active, tombstoned));

        List<ProjectAssetDescriptor> result = adapter.listProjectAssets("prj-1");

        assertEquals(1, result.size());
        assertEquals("art-1", result.get(0).assetId());
    }

    @Test
    void listProjectAssetsShouldReturnEmptyForNonExistentProject() {
        when(tenantProjectService.getProject("prj-nonexistent")).thenReturn(null);

        List<ProjectAssetDescriptor> result = adapter.listProjectAssets("prj-nonexistent");

        assertTrue(result.isEmpty());
        verifyNoInteractions(artifactCatalogService);
    }

    @Test
    void generateSignedAssetUrlShouldCallPort() {
        when(assetDownloadUrlPort.generateSignedUrl("art-1", "s3://bucket/video.mp4", Duration.ofHours(1)))
                .thenReturn(Optional.of("https://signed.example.com/video.mp4?token=abc"));

        Optional<String> result = adapter.generateSignedAssetUrl(
                "prj-1", "art-1", "s3://bucket/video.mp4", Duration.ofHours(1));

        assertTrue(result.isPresent());
        assertEquals("https://signed.example.com/video.mp4?token=abc", result.get());
    }

    @Test
    void generateSignedAssetUrlShouldReturnEmptyForNullStorageUri() {
        Optional<String> result = adapter.generateSignedAssetUrl(
                "prj-1", "art-1", null, Duration.ofHours(1));

        assertTrue(result.isEmpty());
        verifyNoInteractions(assetDownloadUrlPort);
    }

    @Test
    void isAvailableShouldDelegateToPort() {
        when(assetDownloadUrlPort.isAvailable()).thenReturn(true);
        assertTrue(adapter.isAvailable());

        when(assetDownloadUrlPort.isAvailable()).thenReturn(false);
        assertFalse(adapter.isAvailable());
    }

    @Test
    void isAvailableShouldReturnFalseWhenPortIsNull() {
        adapter = new ArtifactCatalogProjectAssetExportAdapter(
                artifactCatalogService, tenantProjectService, null);
        assertFalse(adapter.isAvailable());
    }
}
