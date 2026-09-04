package com.example.platform.render.app.timeline;

import com.example.platform.render.infrastructure.RenderCacheProperties;
import com.example.platform.render.domain.interchange.TimelineScriptParser;
import com.example.platform.storage.domain.BlobStorage;
import static org.mockito.Mockito.mock;

final class RenderCacheTestSupport {

    private RenderCacheTestSupport() {}

    static RenderCacheReuseValidator testCacheReuseValidator() {
        BlobStorage blobStorage = mock(BlobStorage.class);
        TimelineScriptParser parser = new TimelineScriptParser();
        return new RenderCacheReuseValidator(
                new RenderCacheProperties(), parser, new RenderCacheArtifactFetcher(blobStorage, parser));
    }
}
