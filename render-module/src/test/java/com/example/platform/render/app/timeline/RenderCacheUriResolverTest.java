package com.example.platform.render.app.timeline;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.render.infrastructure.RenderCacheProperties;
import org.junit.jupiter.api.Test;

class RenderCacheUriResolverTest {

    @Test
    void resolvesRemoteUriFromCacheKey() {
        RenderCacheProperties props = new RenderCacheProperties();
        props.setRemoteEnabled(true);
        props.setRemoteUriPrefix("s3://tenant-cache/render");
        RenderCacheUriResolver resolver = new RenderCacheUriResolver(props);
        String uri = resolver.resolve("reuse://effects", "segment:tl:seg_0:r42:SEGMENT", "ten_demo");
        assertEquals("s3://tenant-cache/render/ten_demo/segment:tl:seg_0:r42:SEGMENT", uri);
    }

    @Test
    void keepsExplicitUriWhenPresent() {
        RenderCacheProperties props = new RenderCacheProperties();
        props.setRemoteEnabled(true);
        RenderCacheUriResolver resolver = new RenderCacheUriResolver(props);
        assertEquals("localFs://artifacts/out.mp4",
                resolver.resolve("localFs://artifacts/out.mp4", "ck", "ten"));
    }
}
