package com.example.platform.timeline.canonical;

import com.example.platform.fonttext.typography.SubtitleBlockLayout;
import com.example.platform.fonttext.typography.SubtitleTextRange;
import com.example.platform.shared.time.MediaTime;
import java.util.List;
import com.example.platform.fonttext.typography.SubtitleFontRun;

/** Whole-block motion for an editable TextElement. Coordinates are composition micros. */
public record SubtitleCueMotion(
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        String fontProfile, List<Key> keys,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY)
        List<SubtitleFontRun> fontRuns,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        SelectedTextFade selectedTextFade,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        Decoration decoration) {
    public SubtitleCueMotion(String fontProfile, List<Key> keys, List<SubtitleFontRun> fontRuns, SelectedTextFade selectedTextFade) {
        this(fontProfile, keys, fontRuns, selectedTextFade, null);
    }

    public SubtitleCueMotion(String fontProfile, List<Key> keys, List<SubtitleFontRun> fontRuns) {
        this(fontProfile, keys, fontRuns, null);
    }

    public SubtitleCueMotion(String fontProfile, List<Key> keys) {
        this(fontProfile, keys, List.of());
    }

    public static final int MAX_KEYS = 16;
    public static final int STATIC_X = 100_000;
    public static final int STATIC_Y = 550_000;

    public record SelectedTextFade(int start, int end, String time, String duration) {
        public SelectedTextFade {
            new SubtitleTextRange(start, end);
            time = exactFadeTime(time).toString();
            if (duration == null || !duration.matches("[1-9][0-9]{0,18}/[1-9][0-9]{0,18}")) {
                throw new IllegalArgumentException("Subtitle fade duration must be a positive rational");
            }
            var length = exactFadeTime(duration);
            if (length.ticks() == 0) throw new IllegalArgumentException("Subtitle fade duration must be positive");
            duration = length.toString();
            try {
                var finish = MediaTime.parse(time).add(length);
                for (var point : List.of(MediaTime.parse(time), length, finish)) {
                    if (point.ticks() > 9_007_199_254_740_991L || point.timeScale() > 9_007_199_254_740_991L) {
                        throw new IllegalArgumentException("Subtitle fade timing cannot be read exactly by the editor");
                    }
                }
            } catch (ArithmeticException overflow) {
                throw new IllegalArgumentException("Subtitle fade timing exceeds the exact arithmetic range", overflow);
            }
        }

        private static MediaTime exactFadeTime(String value) {
            if (value == null || !value.matches("(?:0|[0-9]{1,19}/[1-9][0-9]{0,18})")) {
                throw new IllegalArgumentException("Subtitle fade time must be an exact nonnegative rational");
            }
            return MediaTime.parse(value);
        }

        public SubtitleTextRange range() {
            return new SubtitleTextRange(start, end);
        }

        public String canonical() {
            return start + ";" + end + ";" + time + ";" + duration;
        }
    }

    public record Decoration(String assetId, String placement, boolean enabled, String start, String end) {
        public Decoration {
            if (!SubtitleBlockLayout.decoration(assetId).placement().equals(placement)) {
                throw new IllegalArgumentException("Unsupported subtitle decoration placement");
            }
            var begin = decorationTime(start);
            var finish = decorationTime(end);
            if (!begin.isLessThan(finish)) {
                throw new IllegalArgumentException("Subtitle decoration needs an increasing cue-relative interval");
            }
            start = begin.toString();
            end = finish.toString();
        }

        private static MediaTime decorationTime(String value) {
            if (value == null || !value.matches("(?:0|[0-9]{1,19}/[1-9][0-9]{0,18})")) {
                throw new IllegalArgumentException("Subtitle decoration times must be exact nonnegative rationals");
            }
            var time = MediaTime.parse(value);
            if (time.ticks() > 9_007_199_254_740_991L || time.timeScale() > 9_007_199_254_740_991L)
                throw new IllegalArgumentException("Subtitle decoration timing cannot be read exactly by the editor");
            return time;
        }

        public void requireInside(MediaTime duration) {
            if (MediaTime.parse(end).isGreaterThan(duration)) {
                throw new IllegalArgumentException("Subtitle decoration does not fit the cue interval. Adjust its timing or send decoration: null to remove it.");
            }
        }

        public String canonical() {
            return assetId + ";" + placement + ";" + enabled + ";" + start + ";" + end;
        }
    }

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
        try {
            if (!keys.isEmpty() && !MediaTime.parse(keys.getLast().time()).isLessThan(duration)) {
                throw new IllegalArgumentException("Subtitle motion keys must lie inside the cue");
            }
            if (selectedTextFade != null && MediaTime.parse(selectedTextFade.time())
                    .add(MediaTime.parse(selectedTextFade.duration())).isGreaterThan(duration)) {
                throw new IllegalArgumentException("Selected text fade must lie inside the cue");
            }
            if (decoration != null) decoration.requireInside(duration);
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException("Subtitle timing exceeds the exact arithmetic range", overflow);
        }
    }
}
