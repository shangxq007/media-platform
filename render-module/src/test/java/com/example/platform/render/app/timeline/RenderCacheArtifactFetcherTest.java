package com.example.platform.render.app.timeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.platform.render.domain.timeline.TimelineScriptParser;
import com.example.platform.shared.web.ErrorCodeRegistry;
import com.example.platform.shared.web.MediaAssetErrors;
import com.example.platform.shared.web.PlatformException;
import com.example.platform.storage.domain.BlobStorage;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RenderCacheArtifactFetcherTest {

    private BlobStorage blobStorage;
    private RenderCacheArtifactFetcher fetcher;

    @BeforeEach
    void setUp() {
        blobStorage = mock(BlobStorage.class);
        ErrorCodeRegistry registry = new ErrorCodeRegistry();
        registry.loadErrorCodes();
        fetcher = new RenderCacheArtifactFetcher(blobStorage, new TimelineScriptParser(), registry);
    }

    @Test
    void throwsStorageNotFoundForMissingRemoteBlob() {
        when(blobStorage.get(anyString(), anyString())).thenReturn(Optional.empty());
        PlatformException ex = assertThrows(PlatformException.class,
                () -> fetcher.fetchBytes("s3://bucket/missing.mp4"));
        assertEquals(MediaAssetErrors.STORAGE_NOT_FOUND, ex.getErrorCode().code());
    }
}
