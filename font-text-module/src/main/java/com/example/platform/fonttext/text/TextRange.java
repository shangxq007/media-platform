package com.example.platform.fonttext.text;

import java.util.Objects;

/** ROADMAP_19 (C11): canonical half-open range [start, end) in Unicode scalar offsets. */
@com.fasterxml.jackson.annotation.JsonAutoDetect(fieldVisibility = com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY)


public final class TextRange {

    private final int start;
    private final int end;

    public TextRange(int start, int end) {
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("invalid scalar range [" + start + ", " + end + ")");
        }
        this.start = start;
        this.end = end;
    }

    public static TextRange of(int start, int end) { return new TextRange(start, end); }

    public int start() { return start; }
    public int end() { return end; }
    public int length() { return end - start; }

    public TextRange withBound(int maxScalars) {
        if (end > maxScalars) {
            throw new IndexOutOfBoundsException("range end " + end + " exceeds scalar count " + maxScalars);
        }
        return this;
    }

    public boolean overlaps(TextRange other) {
        return start < other.end && other.start < end;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof TextRange r && start == r.start && end == r.end;
    }

    @Override
    public int hashCode() {
        return Objects.hash(start, end);
    }

    @Override
    public String toString() {
        return start + ":" + end;
    }
}
