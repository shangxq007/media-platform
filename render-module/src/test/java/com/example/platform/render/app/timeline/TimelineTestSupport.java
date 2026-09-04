package com.example.platform.render.app.timeline;

import com.example.platform.render.domain.interchange.TimelineExtensionsReader;

/** Shared fixtures for timeline unit tests. */
public final class TimelineTestSupport {

    private TimelineTestSupport() {}

    public static InternalTimelineAdapter internalTimelineAdapter() {
        return internalTimelineAdapter(new TimelineExtensionsReader());
    }

    public static InternalTimelineAdapter internalTimelineAdapter(TimelineExtensionsReader extensionsReader) {
        return new InternalTimelineAdapter(extensionsReader, new TimelineAssetUriResolver());
    }
}
