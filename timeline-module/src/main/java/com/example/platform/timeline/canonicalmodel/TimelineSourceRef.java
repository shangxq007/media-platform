package com.example.platform.timeline.canonicalmodel;

import java.util.Objects;

public record TimelineSourceRef(String value) {
    public TimelineSourceRef {
        if (value == null || value.isBlank() || !value.equals(value.strip())) {
            throw new IllegalArgumentException("source ref must be nonblank and already normalized");
        }
    }

    public static TimelineSourceRef of(String value) {
        return new TimelineSourceRef(value);
    }
}
