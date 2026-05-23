package com.example.platform.render.app.timeline;

import com.example.platform.render.domain.timeline.TimelineScriptParser;
import com.example.platform.render.domain.timeline.TimelineSpec;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Resolves timeline JSON to {@link TimelineSpec}: Internal Timeline 1.0 first, legacy OTIO/editor second.
 */
@Service
public class TimelineSpecResolver {

    private final InternalTimelineAdapter internalTimelineAdapter;
    private final TimelineScriptParser timelineScriptParser;

    public TimelineSpecResolver(InternalTimelineAdapter internalTimelineAdapter,
                                TimelineScriptParser timelineScriptParser) {
        this.internalTimelineAdapter = internalTimelineAdapter;
        this.timelineScriptParser = timelineScriptParser;
    }

    public Optional<TimelineSpec> resolve(String timelineJson) {
        if (timelineJson == null || timelineJson.isBlank()) {
            return Optional.empty();
        }
        Optional<TimelineSpec> internal = internalTimelineAdapter.toSpec(timelineJson);
        if (internal.isPresent()) {
            return internal;
        }
        return timelineScriptParser.parse(timelineJson);
    }

    public boolean isInternalTimelineJson(String timelineJson) {
        try {
            return InternalTimelineJson.isInternalTimeline(InternalTimelineJson.parse(timelineJson));
        } catch (Exception e) {
            return false;
        }
    }
}
