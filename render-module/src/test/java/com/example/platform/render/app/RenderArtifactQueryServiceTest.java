package com.example.platform.render.app;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.platform.render.app.dto.ArtifactInfoResponse;
import com.example.platform.render.infrastructure.RenderJobRepository;
import com.example.platform.shared.web.TenantContext;
import com.example.platform.storage.api.StorageCatalogPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

class RenderArtifactQueryServiceTest {

    private RenderJobRepository renderJobRepository;
    private StorageCatalogPort storageCatalogPort;
    private RenderArtifactQueryService service;

    @BeforeEach
    void setUp() {
        renderJobRepository = mock(RenderJobRepository.class);
        storageCatalogPort = mock(StorageCatalogPort.class);
        service = new RenderArtifactQueryService(renderJobRepository, storageCatalogPort, List.of());
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void getArtifactsByJobReturnsArtifacts() {
        TenantContext.set("tenant-1");
        when(renderJobRepository.requireTenantIdByJobId("rj-1")).thenReturn("tenant-1");

        var ref = new StorageCatalogPort.ArtifactRef(
                "art-1", "rj-1", "proj-1", "localFsStorageProvider://output.mp4",
                "mp4", "1920x1080", 10L, Instant.now());
        when(storageCatalogPort.findArtifactsByJob("rj-1")).thenReturn(List.of(ref));

        List<ArtifactInfoResponse> result = service.getArtifactsByJob("rj-1");

        assertEquals(1, result.size());
        assertEquals("art-1", result.get(0).artifactId());
        assertEquals("rj-1", result.get(0).renderJobId());
        assertEquals("proj-1", result.get(0).projectId());
        assertEquals("mp4", result.get(0).format());
    }

    @Test
    void getArtifactsByJobReturnsEmptyListWhenNoArtifacts() {
        TenantContext.set("tenant-2");
        when(renderJobRepository.requireTenantIdByJobId("rj-2")).thenReturn("tenant-2");
        when(storageCatalogPort.findArtifactsByJob("rj-2")).thenReturn(List.of());

        List<ArtifactInfoResponse> result = service.getArtifactsByJob("rj-2");

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getArtifactsByJobRejectsCrossTenantAccess() {
        TenantContext.set("tenant-a");
        when(renderJobRepository.requireTenantIdByJobId("rj-3")).thenReturn("tenant-b");

        assertThrows(IllegalArgumentException.class,
                () -> service.getArtifactsByJob("rj-3"));

        verifyNoInteractions(storageCatalogPort);
    }

    @Test
    void getArtifactsByJobThrowsWhenJobNotFound() {
        when(renderJobRepository.requireTenantIdByJobId("nonexistent"))
                .thenThrow(new IllegalArgumentException("Render job not found: nonexistent"));

        assertThrows(IllegalArgumentException.class,
                () -> service.getArtifactsByJob("nonexistent"));
    }

    @Test
    void getArtifactsByJobMapsMultipleArtifacts() {
        TenantContext.set("tenant-4");
        when(renderJobRepository.requireTenantIdByJobId("rj-4")).thenReturn("tenant-4");

        var ref1 = new StorageCatalogPort.ArtifactRef(
                "art-1", "rj-4", "proj-4", "storage://out1.mp4", "mp4", "1920x1080", 10L, Instant.now());
        var ref2 = new StorageCatalogPort.ArtifactRef(
                "art-2", "rj-4", "proj-4", "storage://out2.mp4", "mp4", "1280x720", 5L, Instant.now());
        when(storageCatalogPort.findArtifactsByJob("rj-4")).thenReturn(List.of(ref1, ref2));

        List<ArtifactInfoResponse> result = service.getArtifactsByJob("rj-4");

        assertEquals(2, result.size());
        assertEquals("art-1", result.get(0).artifactId());
        assertEquals("art-2", result.get(1).artifactId());
    }

    @Test
    void doesNotCallProviderOrRouter() {
        TenantContext.set("tenant-5");
        when(renderJobRepository.requireTenantIdByJobId("rj-5")).thenReturn("tenant-5");
        when(storageCatalogPort.findArtifactsByJob("rj-5")).thenReturn(List.of());

        service.getArtifactsByJob("rj-5");

        // Only storageCatalogPort should be called, no render/quota/ai interactions
        verify(storageCatalogPort).findArtifactsByJob("rj-5");
        verifyNoMoreInteractions(storageCatalogPort);
    }
}
