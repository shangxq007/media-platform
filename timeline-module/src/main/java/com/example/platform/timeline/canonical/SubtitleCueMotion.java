package com.example.platform.timeline.canonical;

import com.example.platform.fonttext.typography.SubtitleBlockLayout;
import com.example.platform.shared.time.MediaTime;
import java.util.List;
import com.example.platform.fonttext.typography.SubtitleFontRun;

/** Whole-block motion for an editable TextElement. Coordinates are composition micros. */
public record SubtitleCueMotion(
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        String fontProfile, List<Key> keys,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY)
        List<SubtitleFontRun> fontRuns) {
    public SubtitleCueMotion(String fontProfile, List<Key> keys) {
        this(fontProfile, keys, List.of());
    }

    public static final int MAX_KEYS = 16;
    public static final int STATIC_X = 100_000;
    public static final int STATIC_Y = 550_000;

    public record Key(String time, int xMicros, int yMicros, int opacityMicros) {
        public Key {
            if (time == null || !time.matches("(?:0|[0-9]{1,19}/[1-9][0-9]{0,18})")
                    || xMicros < -16_000_000 || xMicros > 16_000_000 || yMicros < -16_000_000 || yMicros > 16_000_000
                    || opacityMicros < 0 || opacityMicros > 1_000_000) {
                throw new IllegalArgumentException("Invalid exact subtitle motion key");
            }
            time = MediaTime.parse(time).toString();
        }
    }

    public SubtitleCueMotion {
        if (fontProfile != null) SubtitleBlockLayout.resolveProfile(fontProfile);
        if (keys == null || keys.size() == 1 || keys.size() > MAX_KEYS) {
            throw new IllegalArgumentException("Choose either zero or two to sixteen subtitle motion keys");
        }
        keys = List.copyOf(keys);
        fontRuns = fontRuns == null ? List.of() : List.copyOf(fontRuns);
        try {
            for (int i = 1; i < keys.size(); i++) {
                if (!MediaTime.parse(keys.get(i - 1).time()).isLessThan(MediaTime.parse(keys.get(i).time()))) {
                    throw new IllegalArgumentException("Subtitle key times must strictly increase");
                }
            }
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException("Subtitle key times exceed the exact arithmetic range", overflow);
        }
    }

    /** Compatibility only. The mutable authoring default never participates in saved revisions. */
    public String effectiveFontProfile() {
        return SubtitleBlockLayout.resolveProfile(fontProfile);
    }

    public void requireInside(MediaTime duration) {
        if (keys.isEmpty()) return;
        try {
            if (!MediaTime.parse(keys.getLast().time()).isLessThan(duration)) {
                throw new IllegalArgumentException("Subtitle motion keys must lie inside the cue");
            }
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException("Subtitle timing exceeds the exact arithmetic range", overflow);
        }
    }
}
