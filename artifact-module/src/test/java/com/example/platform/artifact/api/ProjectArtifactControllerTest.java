package com.example.platform.artifact.api;

import com.example.platform.artifact.app.*;
import com.example.platform.artifact.domain.*;
import com.example.platform.artifact.infrastructure.ArtifactContentReadService;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.shared.web.TenantContext;
import com.example.platform.storage.api.StoragePlacementQuery;
import com.example.platform.storage.api.VerifiedStorageRead;
import com.example.platform.storage.contract.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Unit boundary tests; real authenticated HTTP/byte transfer is separately exercised end-to-end. */
class ProjectArtifactControllerTest {
    final ArtifactProjectScope scope = new ArtifactProjectScope("tenant-a", "project-a");
    final ArtifactId id = new ArtifactId("image-a");
    final byte[] image = HexFormat.of().parseHex("89504e470d0a1a0a0000000d49484452");
    final ArtifactApplicationQuery scoped = mock(ArtifactApplicationQuery.class);
    final ArtifactQueryService queries = mock(ArtifactQueryService.class);
    final VerifiedStorageRead storage = mock(VerifiedStorageRead.class);
    final StoragePlacementQuery placements = mock(StoragePlacementQuery.class);
    final ArtifactProjectAuthorizationPort authorization = mock(ArtifactProjectAuthorizationPort.class);
    final ArtifactContentRead contents = new ArtifactContentReadService(scoped, queries, storage, placements);
    final ArtifactApplicationService application = new ArtifactApplicationService(scoped, queries);
    final ProjectArtifactController controller = new ProjectArtifactController(application, contents, authorization);
    final ContentDigest digest = digest(image);

    @BeforeEach void ready() {
        TenantContext.set("tenant-a");
        when(scoped.findProjectArtifact(scope, id)).thenReturn(Optional.of(artifact(ArtifactState.AVAILABLE)));
        when(queries.listReplicas("tenant-a", id)).thenReturn(List.of(new ArtifactReplicaBinding(
                "binding", id, new StorageObjectId("opaque-object"), new StorageReplicaId("replica"),
                new StorageProviderId("local-object-store"), ReplicaRole.PRIMARY, "local", Instant.EPOCH)));
        when(storage.read(any(), any(), eq(digest), eq((long) image.length), anyLong())).thenReturn(image);
    }
    @AfterEach void clear() { TenantContext.clear(); }

    @Test void projectAccessAndVerifiedImageNeedNoRenderJobIdentity() {
        var response = controller.access("tenant-a", "project-a", "image-a");
        var descriptor = ((ArtifactApplicationController.ArtifactAccessResponse) response.getBody()).access();
        assertEquals("/api/tenants/tenant-a/projects/project-a/artifacts/image-a/content", descriptor.accessUrl());
        var result = controller.content("tenant-a", "project-a", "image-a");
        assertEquals(200, result.getStatusCode().value());
        assertArrayEquals(image, (byte[]) result.getBody());
        assertEquals(image.length, result.getHeaders().getContentLength());
        assertEquals("image/png", result.getHeaders().getContentType().toString());
        assertFalse(descriptor.toString().contains("opaque-object"));
        verify(scoped, never()).findArtifact(any(), any());
    }
    @Test void deniedProjectCannotLookupArtifactOrReadBytes() {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN)).when(authorization).requireRead("tenant-a", "project-a");
        assertThrows(ResponseStatusException.class, () -> controller.access("tenant-a", "project-a", "image-a"));
        assertThrows(ResponseStatusException.class, () -> controller.content("tenant-a", "project-a", "image-a"));
        verifyNoInteractions(scoped, queries, storage);
    }
    @Test void otherTenantCannotReadEvenIfAnAuthorizationAdapterErroneouslyAllowsIt() {
        TenantContext.set("tenant-b");
        assertThrows(RuntimeException.class, () -> controller.content("tenant-a", "project-a", "image-a"));
        verifyNoInteractions(scoped, queries, storage);
    }
    @Test void wrongProjectIsNotFoundBeforeReplicaOrByteAccess() {
        assertEquals(404, controller.access("tenant-a", "project-b", "image-a").getStatusCode().value());
        assertEquals(404, controller.content("tenant-a", "project-b", "image-a").getStatusCode().value());
        verifyNoInteractions(queries, storage);
    }
    @Test void everyUncommittedOrDeletedStateIsUnavailableBeforeStorageAccess() {
        for (var state : ArtifactState.values()) {
            if (state == ArtifactState.AVAILABLE) continue;
            when(scoped.findProjectArtifact(scope, id)).thenReturn(Optional.of(artifact(state)));
            assertEquals(503, controller.access("tenant-a", "project-a", "image-a").getStatusCode().value());
            assertEquals(503, controller.content("tenant-a", "project-a", "image-a").getStatusCode().value());
        }
        verifyNoInteractions(queries, storage);
    }
    @Test void storageFailureCannotDisclosePhysicalPathOrSecret() {
        when(storage.read(any(), any(), any(), anyLong(), anyLong())).thenThrow(new IllegalStateException("private/path/secret"));
        var response = controller.content("tenant-a", "project-a", "image-a");
        assertEquals(503, response.getStatusCode().value());
        assertFalse(response.getBody().toString().contains("private/path"));
        assertFalse(response.getBody().toString().contains("secret"));
    }
    private Artifact artifact(ArtifactState state) {
        return new Artifact(id, "tenant-a", digest, image.length, ArtifactMediaType.IMAGE,
                ArtifactKind.DERIVED_MEDIA, state, 1, Instant.EPOCH);
    }
    private static ContentDigest digest(byte[] bytes) {
        try { return ContentDigest.sha256(HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
}
