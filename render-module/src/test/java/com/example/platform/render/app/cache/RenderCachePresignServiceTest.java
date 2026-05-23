package com.example.platform.render.app.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.platform.render.app.planner.PipelinePlanPersistenceService;
import com.example.platform.render.infrastructure.RenderCacheProperties;
import com.example.platform.storage.domain.BlobStorage;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RenderCachePresignServiceTest {

    private RenderCacheTenantGuard tenantGuard;
    private PipelinePlanPersistenceService persistence;
    private BlobStorage blobStorage;
    private RenderCachePresignService service;

    @BeforeEach
    void setUp() {
        tenantGuard = mock(RenderCacheTenantGuard.class);
        persistence = mock(PipelinePlanPersistenceService.class);
        blobStorage = mock(BlobStorage.class);
        RenderCacheProperties props = new RenderCacheProperties();
        props.setRemoteEnabled(true);
        service = new RenderCachePresignService(tenantGuard, persistence, blobStorage, props);
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

    private static Map<String, Object> executionState() {
        Map<String, String> segmentEntry = new LinkedHashMap<>();
        segmentEntry.put("segmentId", "seg_0");
        segmentEntry.put("cacheKey", "segment:tl:seg_0:r1:SEGMENT");
        segmentEntry.put("remoteUri", "s3StorageProvider://render-cache/ten/segment/tl/seg_0.mp4");
        Map<String, Object> index = Map.of("segment:tl:seg_0:r1:SEGMENT", segmentEntry);
        return Map.of("segmentCacheIndex", index);
    }
}
