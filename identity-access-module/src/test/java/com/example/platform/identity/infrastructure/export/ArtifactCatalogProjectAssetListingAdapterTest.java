package com.example.platform.identity.infrastructure.export;

import com.example.platform.artifact.app.ArtifactCatalogService;
import com.example.platform.artifact.domain.Artifact;
import com.example.platform.artifact.domain.ArtifactStatus;
import com.example.platform.identity.api.dto.ProjectResponse;
import com.example.platform.identity.app.TenantProjectService;
import com.example.platform.shared.export.ProjectAssetRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ArtifactCatalogProjectAssetListingAdapterTest {

    @Mock
    private ArtifactCatalogService artifactCatalogService;

    @Mock
    private TenantProjectService tenantProjectService;

    private ArtifactCatalogProjectAssetListingAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new ArtifactCatalogProjectAssetListingAdapter(artifactCatalogService, tenantProjectService);
    }

    @Test
    void shouldMapSizeBytesAndChecksum() {
        ProjectResponse project = new ProjectResponse("prj-1", "tenant-1", "Test", "Desc", "ACTIVE", Instant.now());
        when(tenantProjectService.getProject("prj-1")).thenReturn(project);

        Artifact artifact = new Artifact("art-1", "job-1", "prj-1",
                "s3://bucket/video.mp4", "mp4", "1920x1080", 10L,
                12345L, "sha256:abc123def456",
                ArtifactStatus.ACTIVE, null, Instant.now());
        when(artifactCatalogService.listArtifactsByProject("prj-1")).thenReturn(List.of(artifact));

        List<ProjectAssetRef> result = adapter.listAssets("tenant-1", "prj-1");

        assertEquals(1, result.size());
        ProjectAssetRef ref = result.get(0);
        assertEquals(12345L, ref.sizeBytes());
        assertEquals("sha256:abc123def456", ref.checksum());
    }

    @Test
    void shouldAllowMissingSizeBytesAndChecksum() {
        ProjectResponse project = new ProjectResponse("prj-1", "tenant-1", "Test", "Desc", "ACTIVE", Instant.now());
        when(tenantProjectService.getProject("prj-1")).thenReturn(project);

        Artifact artifact = new Artifact("art-1", "job-1", "prj-1",
                "s3://bucket/video.mp4", "mp4", "1920x1080", 10L,
                null, null,
                ArtifactStatus.ACTIVE, null, Instant.now());
        when(artifactCatalogService.listArtifactsByProject("prj-1")).thenReturn(List.of(artifact));

        List<ProjectAssetRef> result = adapter.listAssets("tenant-1", "prj-1");

        assertEquals(1, result.size());
        ProjectAssetRef ref = result.get(0);
        assertNull(ref.sizeBytes());
        assertNull(ref.checksum());
    }

    @Test
    void shouldStillRejectWrongTenant() {
        ProjectResponse project = new ProjectResponse("prj-1", "tenant-1", "Test", "Desc", "ACTIVE", Instant.now());
        when(tenantProjectService.getProject("prj-1")).thenReturn(project);

        List<ProjectAssetRef> result = adapter.listAssets("tenant-wrong", "prj-1");

        assertTrue(result.isEmpty());
        verifyNoInteractions(artifactCatalogService);
    }

    @Test
    void shouldStillFilterUnusableArtifacts() {
        ProjectResponse project = new ProjectResponse("prj-1", "tenant-1", "Test", "Desc", "ACTIVE", Instant.now());
        when(tenantProjectService.getProject("prj-1")).thenReturn(project);

        Artifact active = new Artifact("art-1", "job-1", "prj-1",
                "s3://bucket/video.mp4", "mp4", "1920x1080", 10L,
                1024L, "sha256:abc",
                ArtifactStatus.ACTIVE, null, Instant.now());
        Artifact tombstoned = new Artifact("art-2", "job-1", "prj-1",
                "s3://bucket/audio.mp3", "mp3", null, 30L,
                512L, "sha256:def",
                ArtifactStatus.TOMBSTONED, Instant.now(), Instant.now());
        when(artifactCatalogService.listArtifactsByProject("prj-1")).thenReturn(List.of(active, tombstoned));

        List<ProjectAssetRef> result = adapter.listAssets("tenant-1", "prj-1");

        assertEquals(1, result.size());
        assertEquals("art-1", result.get(0).assetId());
    }

    @Test
    void isAvailableShouldReturnTrue() {
        assertTrue(adapter.isAvailable());
    }
}
