package com.example.platform.render.app.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.platform.identity.api.authorization.AuthorizationDeniedException;
import com.example.platform.render.app.RenderSurfaceAuthorization;
import com.example.platform.render.app.planner.PipelinePlanPersistenceService;
import com.example.platform.render.infrastructure.RenderCacheProperties;
import com.example.platform.storage.domain.BlobStorage;
import com.example.platform.shared.authorization.AuthorizationDecision;
import com.example.platform.shared.authorization.CanonicalActor;
import com.example.platform.shared.web.TenantContext;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RenderCachePresignServiceTest {

    private RenderCacheTenantGuard tenantGuard;
    private PipelinePlanPersistenceService persistence;
    private BlobStorage blobStorage;
    private RenderCacheProperties props;
    private RenderCachePresignService service;

    @BeforeEach
    void setUp() {
        TenantContext.set("ten");
        tenantGuard = mock(RenderCacheTenantGuard.class);
        persistence = mock(PipelinePlanPersistenceService.class);
        blobStorage = mock(BlobStorage.class);
        props = new RenderCacheProperties();
        props.setRemoteEnabled(true);
        service = new RenderCachePresignService(tenantGuard, persistence, blobStorage, props,
                com.example.platform.render.testsupport.RenderSurfaceAuthorizationTestSupport.allowAll());
    }

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Test
    void presignsSegmentCacheEntries() {
        when(persistence.loadExecutionState("rj_1")).thenReturn(Optional.of(executionState()));
        when(blobStorage.presignStorageUri("s3StorageProvider://render-cache/ten/segment/tl/seg_0.mp4"))
                .thenReturn(Optional.of("https://cdn.example/seg.mp4"));

        var response = service.presignAll("ten", "proj", "rj_1");

        verify(tenantGuard).requireJobAccess("ten", "proj", "rj_1");
        assertEquals("rj_1", response.jobId());
        assertFalse(response.entries().isEmpty());
        assertEquals("https://cdn.example/seg.mp4", response.entries().get(0).downloadUrl());
    }

    @Test
    void presignIsDeniedWhenProjectIsNotAuthorized() {
        RenderCachePresignService denied = new RenderCachePresignService(tenantGuard, persistence,
                blobStorage, props, new RenderSurfaceAuthorization(
                        request -> AuthorizationDecision.deny("TEST_DENY", "TEST", "scope not authorized"),
                        () -> Optional.of(CanonicalActor.user("user-1", "ten", Set.of("EDITOR"), "test"))));

        assertThrows(AuthorizationDeniedException.class,
                () -> denied.presignAll("ten", "proj", "rj_1"));
        assertThrows(AuthorizationDeniedException.class,
                () -> denied.presignOne("ten", "proj", "rj_1", "segment:tl:seg_0:r1:SEGMENT"));
        verifyNoInteractions(persistence);
        verifyNoInteractions(blobStorage);
    }

    private static Map<String, Object> executionState() {
        Map<String, String> segmentEntry = new LinkedHashMap<>();
        segmentEntry.put("segmentId", "seg_0");
        segmentEntry.put("cacheKey", "segment:tl:seg_0:r1:SEGMENT");
        segmentEntry.put("remoteUri", "s3StorageProvider://render-cache/ten/segment/tl/seg_0.mp4");
        Map<String, Object> index = Map.of("segment:tl:seg_0:r1:SEGMENT", segmentEntry);
        return Map.of("segmentCacheIndex", index);
    }
}
