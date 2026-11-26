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

    /** The editor's single implicit editable subtitle track, not the browser locale. */
    public static final String SUBTITLE_TRACK_LANGUAGE = "subtitleTrack.language";

    public static String checkedSubtitleLanguage(String value) {
        if (value == null) return null;
        if (value.length() > 63) throw new IllegalArgumentException("Subtitle track language is too long");
        return com.example.platform.fonttext.text.LanguageTag.of(value).value();
    }

    public String subtitleTrackLanguage() {
        return properties.get(SUBTITLE_TRACK_LANGUAGE);
    }

    public static TimelineMetadata forSubtitleLanguage(String value) {
        return value == null ? empty() : new TimelineMetadata("", "", Map.of(SUBTITLE_TRACK_LANGUAGE, value));
    }

    public CompositionAspectRatio effectiveCompositionAspectRatio() {
        return compositionAspectRatio == null ? CompositionAspectRatio.DEFAULT : compositionAspectRatio;
    }

    public TimelineMetadata {
        if (title == null) title = "";
        if (description == null) description = "";
        if (properties == null) properties = Map.of();
        if (properties.containsKey(SUBTITLE_TRACK_LANGUAGE)) {
            var declared = new java.util.HashMap<>(properties);
            declared.put(SUBTITLE_TRACK_LANGUAGE, checkedSubtitleLanguage(declared.get(SUBTITLE_TRACK_LANGUAGE)));
            properties = declared;
        }
        properties = Map.copyOf(properties);
        if (CompositionAspectRatio.DEFAULT.equals(compositionAspectRatio)) compositionAspectRatio = null;
    }

    public static TimelineMetadata empty() {
        return new TimelineMetadata("", "", Map.of());
    }
}
