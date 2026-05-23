package com.example.platform.render.app.timeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.platform.shared.web.ErrorCodeRegistry;
import com.example.platform.shared.web.MediaAssetErrors;
import com.example.platform.shared.web.PlatformException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

class TimelineAssetUriResolverTest {

    private final TimelineAssetUriResolver resolver;

    TimelineAssetUriResolverTest() {
        ErrorCodeRegistry registry = new ErrorCodeRegistry();
        registry.loadErrorCodes();
        resolver = new TimelineAssetUriResolver(registry);
    }

    @Test
    void resolvesUriFromRegistry() throws Exception {
        ObjectNode root = InternalTimelineJson.mapper().createObjectNode();
        ObjectNode assets = root.putObject("assetRegistry").putObject("assets");
        assets.putObject("a1").put("uri", "s3://bucket/video.mp4");
        String uri = resolver.resolve(null, "a1", assets);
        assertEquals("s3://bucket/video.mp4", uri);
    }

    @Test
    void throwsWhenRegistryMissingAsset() {
        ObjectNode assets = InternalTimelineJson.mapper().createObjectNode();
        PlatformException ex = assertThrows(PlatformException.class,
                () -> resolver.resolve(null, "missing", assets));
        assertEquals(MediaAssetErrors.ASSET_NOT_FOUND, ex.getErrorCode().code());
    }

    @Test
    void throwsWhenAssetTombstoned() {
        ObjectNode assets = InternalTimelineJson.mapper().createObjectNode();
        assets.putObject("a1").put("status", "TOMBSTONED").put("uri", "s3://bucket/video.mp4");
        PlatformException ex = assertThrows(PlatformException.class,
                () -> resolver.resolve(null, "a1", assets));
        assertEquals(MediaAssetErrors.ASSET_TOMBSTONED, ex.getErrorCode().code());
    }

    @Test
    void rejectsPlaceholderUri() {
        ObjectNode assets = InternalTimelineJson.mapper().createObjectNode();
        assets.putObject("a1").put("uri", "asset://a1");
        PlatformException ex = assertThrows(PlatformException.class,
                () -> resolver.resolve(null, "a1", assets));
        assertEquals(MediaAssetErrors.ASSET_NOT_FOUND, ex.getErrorCode().code());
    }
}
