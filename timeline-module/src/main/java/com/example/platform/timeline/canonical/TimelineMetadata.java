package com.example.platform.timeline.canonical;

import java.util.Map;

public record TimelineMetadata(
        String title,
        String description,
        Map<String, String> properties) {

    public TimelineMetadata {
        if (title == null) title = "";
        if (description == null) description = "";
        if (properties == null) properties = Map.of();
        properties = Map.copyOf(properties);
    }

    public static TimelineMetadata empty() {
        return new TimelineMetadata("", "", Map.of());
    }
}
