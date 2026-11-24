package com.example.platform.timeline.canonical;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

public record TimelineMetadata(
        String title,
        String description,
        Map<String, String> properties,
        @JsonInclude(JsonInclude.Include.NON_NULL) CompositionAspectRatio compositionAspectRatio) {

    public TimelineMetadata(String title, String description, Map<String, String> properties) {
        this(title, description, properties, null);
    }

    public CompositionAspectRatio effectiveCompositionAspectRatio() {
        return compositionAspectRatio == null ? CompositionAspectRatio.LANDSCAPE_4_3 : compositionAspectRatio;
    }

    public TimelineMetadata {
        if (title == null) title = "";
        if (description == null) description = "";
        if (properties == null) properties = Map.of();
        properties = Map.copyOf(properties);
        if (compositionAspectRatio == CompositionAspectRatio.LANDSCAPE_4_3) compositionAspectRatio = null;
    }

    public static TimelineMetadata empty() {
        return new TimelineMetadata("", "", Map.of());
    }
}
