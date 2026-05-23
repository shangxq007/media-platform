package com.example.platform.render.app.timeline;

import com.example.platform.render.domain.timeline.TimelineExtensionsReader;
import com.example.platform.shared.web.ErrorCodeRegistry;

/** Shared fixtures for timeline unit tests. */
public final class TimelineTestSupport {

    private TimelineTestSupport() {}

    public static InternalTimelineAdapter internalTimelineAdapter() {
        return internalTimelineAdapter(new TimelineExtensionsReader());
    }

    public static InternalTimelineAdapter internalTimelineAdapter(TimelineExtensionsReader extensionsReader) {
        ErrorCodeRegistry registry = new ErrorCodeRegistry();
        registry.loadErrorCodes();
        return new InternalTimelineAdapter(extensionsReader, new TimelineAssetUriResolver(registry));
    }
}
