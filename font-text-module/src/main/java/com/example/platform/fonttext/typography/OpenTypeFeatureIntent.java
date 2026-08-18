package com.example.platform.fonttext.typography;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** ROADMAP_19 (C30): bounded feature intent — enabled/disabled per extensible tag, sorted. */
@com.fasterxml.jackson.annotation.JsonAutoDetect(fieldVisibility = com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY)
public final class OpenTypeFeatureIntent {

    public enum State { ENABLED, DISABLED }

    private final List<OpenTypeFeatureSetting> settings;

    public record OpenTypeFeatureSetting(OpenTypeFeatureTag tag, State state) {}

    @com.fasterxml.jackson.annotation.JsonCreator
public OpenTypeFeatureIntent(@com.fasterxml.jackson.annotation.JsonProperty("settings") List<OpenTypeFeatureSetting> settings) {
        List<OpenTypeFeatureSetting> s = new ArrayList<>(settings);
        s.sort((a, b) -> a.tag().compareTo(b.tag()));
        this.settings = Collections.unmodifiableList(s);
    }

    public static OpenTypeFeatureIntent empty() { return new OpenTypeFeatureIntent(List.of()); }

    public List<OpenTypeFeatureSetting> settings() { return settings; }

    @Override
    public boolean equals(Object o) { return o instanceof OpenTypeFeatureIntent i && settings.equals(i.settings); }

    @Override
    public int hashCode() { return settings.hashCode(); }
}
