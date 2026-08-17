package com.example.platform.render.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.platform.artifact.app.ArtifactCatalogService;
import com.example.platform.artifact.domain.Artifact;
import com.example.platform.artifact.domain.ArtifactKind;
import com.example.platform.artifact.domain.ArtifactMediaType;
import com.example.platform.artifact.domain.ArtifactQueryService;
import com.example.platform.artifact.domain.ArtifactReplicaBinding;
import com.example.platform.artifact.domain.ArtifactState;
import com.example.platform.artifact.domain.ReplicaRole;
import com.example.platform.render.app.dto.ArtifactInfoResponse;
import com.example.platform.render.infrastructure.RenderJobRepository;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.shared.web.TenantContext;
import com.example.platform.storage.contract.StorageObjectId;
import com.example.platform.storage.contract.StorageProviderId;
import com.example.platform.storage.contract.StorageReplicaId;
import com.example.platform.storage.domain.BlobStorage;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * GCR-2: render consumes Artifact through artifact-module contracts (single
 * canonical query authority); physical bytes via storage data-plane.
 */
class RenderArtifactQueryServiceTest {

    private RenderJobRepository renderJobRepository;
    private ArtifactQueryService artifactQueryService;
    private ArtifactCatalogService artifactCatalogService;
    private RenderArtifactQueryService service;
    private BlobStorage blobStorage;

    @BeforeEach
    void setUp() {
        renderJobRepository = mock(RenderJobRepository.class);
        artifactQueryService = mock(ArtifactQueryService.class);
        artifactCatalogService = mock(ArtifactCatalogService.class);
        blobStorage = mock(BlobStorage.class);
        service = new RenderArtifactQueryService(
                renderJobRepository, artifactQueryService, artifactCatalogService, List.of(blobStorage));
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void getArtifactsByJobReturnsArtifactsFromCatalogProjection() {
        TenantContext.set("tenant-1");
        when(renderJobRepository.requireTenantIdByJobId("rj-1")).thenReturn("tenant-1");

        com.example.platform.artifact.domain.ArtifactCatalogEntry entry =
                new com.example.platform.artifact.domain.ArtifactCatalogEntry(
                        "art-1", "rj-1", "proj-1", "local://output.mp4",
                        "mp4", "1920x1080", 10L, 1024L, "abc", 
                        com.example.platform.artifact.domain.ArtifactStatus.ACTIVE, null, Instant.now());
        when(artifactCatalogService.listArtifactsByRenderJob("rj-1")).thenReturn(List.of(entry));

        List<ArtifactInfoResponse> result = service.getArtifactsByJob("rj-1");

        assertEquals(1, result.size());
        assertEquals("art-1", result.get(0).artifactId());
        assertEquals("rj-1", result.get(0).renderJobId());
        assertEquals("local://output.mp4", result.get(0).storageUri());
    }

    @Test
    void getArtifactContentReadsBytesFromReplicaViaStorageDataPlane() {
        TenantContext.set("tenant-1");
        ArtifactId id = new ArtifactId("art-1");
        Artifact artifact = new Artifact(id, "tenant-1",
                ContentDigest.sha256("a".repeat(64)), 1024L,
                ArtifactMediaType.VIDEO, ArtifactKind.RENDER_MASTER, ArtifactState.AVAILABLE, 1, Instant.now());
        when(artifactQueryService.getArtifact("tenant-1", id)).thenReturn(Optional.of(artifact));
        ArtifactReplicaBinding replica = new ArtifactReplicaBinding(
                "rep-1", id, new StorageObjectId("bucket/key.mp4"),
                new StorageReplicaId("replica-1"), new StorageProviderId("local"),
                ReplicaRole.PRIMARY, "default", Instant.now());
        when(artifactQueryService.listReplicas("tenant-1", id)).thenReturn(List.of(replica));
        when(blobStorage.get("bucket", "key.mp4")).thenReturn(Optional.of(new byte[] {1, 2, 3}));

        byte[] content = service.getArtifactContent("art-1");

        assertEquals(3, content.length);
    }

    @Test
    void getArtifactContentReturnsNullWhenArtifactMissing() {
        TenantContext.set("tenant-1");
        ArtifactId id = new ArtifactId("missing");
        when(artifactQueryService.getArtifact("tenant-1", id)).thenReturn(Optional.empty());

        assertEquals(null, service.getArtifactContent("missing"));
    }
}
