package com.example.platform.render.domain.timeline.canonicalmodel;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class TimelineModelPath implements Comparable<TimelineModelPath> {
    private static final TimelineModelPath ROOT = new TimelineModelPath(List.of());

    private final List<Segment> segments;

    private TimelineModelPath(List<Segment> segments) {
        this.segments = List.copyOf(segments);
    }

    public static TimelineModelPath root() {
        return ROOT;
    }

    public TimelineModelPath field(String name) {
        return append(new Segment(SegmentKind.FIELD, name, -1));
    }

    public TimelineModelPath index(int index) {
        return append(new Segment(SegmentKind.INDEX, null, index));
    }

    public TimelineModelPath id(String id) {
        return append(new Segment(SegmentKind.ID, id, -1));
    }

    public String render() {
        if (segments.isEmpty()) {
            return "$";
        }
        StringBuilder out = new StringBuilder("$");
        for (Segment segment : segments) {
            switch (segment.kind()) {
                case FIELD -> out.append('.').append(segment.value());
                case INDEX -> out.append('[').append(segment.index()).append(']');
                case ID -> out.append("[id=").append(escape(segment.value())).append(']');
            }
        }
        return out.toString();
    }

    private TimelineModelPath append(Segment segment) {
        List<Segment> copy = new ArrayList<>(segments);
        copy.add(segment);
        return new TimelineModelPath(copy);
    }

    private static String escape(String value) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\', ']', '=' -> out.append('\\').append(c);
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (Character.isISOControl(c)) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }

    @Override
    public int compareTo(TimelineModelPath other) {
        int max = Math.min(this.segments.size(), other.segments.size());
        for (int i = 0; i < max; i++) {
            int kind = Integer.compare(this.segments.get(i).kind().ordinal(), other.segments.get(i).kind().ordinal());
            if (kind != 0) {
                return kind;
            }
            int rendered = this.segments.get(i).render().compareTo(other.segments.get(i).render());
            if (rendered != 0) {
                return rendered;
            }
        }
        return Integer.compare(this.segments.size(), other.segments.size());
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof TimelineModelPath path && segments.equals(path.segments);
    }

    @Override
    public int hashCode() {
        return segments.hashCode();
    }

    @Override
    public String toString() {
        return render();
    }

    private enum SegmentKind {
        FIELD,
        INDEX,
        ID
    }

    private record Segment(SegmentKind kind, String value, int index) {
        private Segment {
            Objects.requireNonNull(kind, "kind");
            if ((kind == SegmentKind.FIELD || kind == SegmentKind.ID) && (value == null || value.isEmpty())) {
                throw new IllegalArgumentException("path segment value must be nonempty");
            }
            if (kind == SegmentKind.INDEX && index < 0) {
                throw new IllegalArgumentException("path index must be nonnegative");
            }
        }

        String render() {
            return switch (kind) {
                case FIELD -> value;
                case INDEX -> Integer.toString(index);
                case ID -> escape(value);
            };
        }
    }
}
