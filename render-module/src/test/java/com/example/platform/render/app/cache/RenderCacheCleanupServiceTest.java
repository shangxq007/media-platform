package com.example.platform.render.app.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RenderCacheCleanupServiceTest {

    @Test
    void collectsRemoteUrisFromExecutionState() {
        Map<String, String> segment = Map.of(
                "remoteUri", "s3StorageProvider://render-cache/ten/segment/tl/seg_0.mp4");
        Map<String, Object> state = Map.of(
                "segmentCacheIndex", Map.of("segment:tl:seg_0:r1:SEGMENT", segment),
                "mezzanineCacheIndex", Map.of(
                        "remoteUri", "s3StorageProvider://render-cache/ten/final.mp4"));
        Set<String> uris = RenderCacheCleanupService.collectRemoteUris(state);
        assertEquals(2, uris.size());
    }

    @Test
    void ignoresLocalOnlyEntries() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("segmentCacheIndex", Map.of("k", Map.of("uri", "localFsStorageProvider://artifacts/x.mp4")));
        assertEquals(0, RenderCacheCleanupService.collectRemoteUris(state).size());
    }
}
