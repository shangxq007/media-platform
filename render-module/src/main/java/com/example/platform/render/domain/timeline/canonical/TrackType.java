package com.example.platform.render.domain.timeline.canonical;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum TrackType {
    VIDEO("video"),
    AUDIO("audio"),
    TEXT("text"),
    EFFECT("effect");

    private final String value;

    TrackType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static TrackType fromValue(String value) {
        for (TrackType type : values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown TrackType: " + value);
    }
}
