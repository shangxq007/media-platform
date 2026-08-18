package com.example.platform.fonttext.resource;

import java.util.Objects;

/** ROADMAP_19 (C4): explicit non-negative typed collection face index (TTC/OTC). */
@com.fasterxml.jackson.annotation.JsonAutoDetect(fieldVisibility = com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY)


public final class FaceIndex {

    private final int value;

    @com.fasterxml.jackson.annotation.JsonCreator
public FaceIndex(@com.fasterxml.jackson.annotation.JsonProperty("value") int value) {
        if (value < 0) {
            throw new IllegalArgumentException("FaceIndex must be >= 0");
        }
        this.value = value;
    }

    public int value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof FaceIndex f && value == f.value;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(value);
    }

    @Override
    public String toString() {
        return Integer.toString(value);
    }
}
