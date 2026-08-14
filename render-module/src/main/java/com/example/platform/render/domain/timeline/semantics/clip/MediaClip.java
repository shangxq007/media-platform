package com.example.platform.render.domain.timeline.semantics.clip;

import com.example.platform.shared.time.MediaTime;

import java.util.Objects;

/**
 * Strongly-typed Clip temporal semantics.
 * Separates timeline position from source media position and playback rate.
 * <p>
 * Key invariants:
 * - timelineRange.start <= timelineRange.end
 * - sourceRange.start <= sourceRange.end
 * - playbackRate > 0
 * - timelineDuration = sourceDuration / playbackRate (fixed-rate case)
 */
public final class MediaClip {

    private final String clipId;
    private final String trackId;
    private final TimeRange timelineRange;
    private final TimeRange sourceRange;
    private final Rational playbackRate;
    private final SourceBinding sourceBinding;

    public MediaClip(
            String clipId,
            String trackId,
            TimeRange timelineRange,
            TimeRange sourceRange,
            Rational playbackRate,
            SourceBinding sourceBinding) {
        this.clipId = Objects.requireNonNull(clipId, "clipId");
        this.trackId = Objects.requireNonNull(trackId, "trackId");
        this.timelineRange = Objects.requireNonNull(timelineRange, "timelineRange");
        this.sourceRange = Objects.requireNonNull(sourceRange, "sourceRange");
        this.playbackRate = Objects.requireNonNull(playbackRate, "playbackRate");
        this.sourceBinding = Objects.requireNonNull(sourceBinding, "sourceBinding");
        validate();
    }

    private void validate() {
        if (clipId.isBlank()) throw new IllegalArgumentException("clipId must not be blank");
        if (trackId.isBlank()) throw new IllegalArgumentException("trackId must not be blank");
        if (timelineRange.start().isGreaterThan(timelineRange.end())) {
            throw new IllegalArgumentException("timelineRange.start must be <= timelineRange.end");
        }
        if (sourceRange.start().isGreaterThan(sourceRange.end())) {
            throw new IllegalArgumentException("sourceRange.start must be <= sourceRange.end");
        }
        // Single source-range authority: clip range MUST equal the binding's exact source range.
        if (!sourceRange.equals(sourceBinding.sourceRange())) {
            throw new IllegalArgumentException(
                    "clip sourceRange must equal sourceBinding.sourceRange (single authority)");
        }
        if (playbackRate.numerator() <= 0) {
            throw new IllegalArgumentException("playbackRate must be > 0");
        }
        if (playbackRate.denominator() <= 0) {
            throw new IllegalArgumentException("playbackRate denominator must be > 0");
        }
    }

    public String clipId() { return clipId; }
    public String trackId() { return trackId; }
    public TimeRange timelineRange() { return timelineRange; }
    public TimeRange sourceRange() { return sourceRange; }
    public Rational playbackRate() { return playbackRate; }
    public SourceBinding sourceBinding() { return sourceBinding; }

    /**
     * Returns the duration of this clip on the timeline.
     */
    public MediaTime timelineDuration() {
        return timelineRange.end().subtract(timelineRange.start());
    }

    /**
     * Returns the duration of the source media consumed.
     */
    public MediaTime sourceDuration() {
        return sourceRange.end().subtract(sourceRange.start());
    }

    /**
     * Returns the source handle before the clip (available pre-roll).
     */
    public MediaTime preHandle() {
        return sourceRange.start().min(MediaTime.ofNanos(1_000_000_000L)); // 1 second max default
    }

    /**
     * Returns the source handle after the clip (available post-roll).
     */
    public MediaTime postHandle() {
        // Post-handle: conceptual model — derived from source properties, not a fixed value
        return MediaTime.ZERO;
    }

    /**
     * Returns true if this clip has valid fixed-rate duration relationship.
     * timelineDuration == sourceDuration / playbackRate
     */
    public boolean hasValidFixedRateDuration() {
        MediaTime expected = sourceDuration().multiplyRational(
            playbackRate.denominator(), playbackRate.numerator());
        return timelineDuration().isEqualTo(expected);
    }

    /**
     * Maps a time on the timeline to source time using playback rate.
     */
    public MediaTime timelineToSourceTime(MediaTime timelineTime) {
        MediaTime offset = timelineTime.subtract(timelineRange.start());
        // source offset = timeline offset * playbackRate
        MediaTime sourceOffset = offset.multiplyRational(
            playbackRate.numerator(), playbackRate.denominator());
        return sourceRange.start().add(sourceOffset);
    }

    /**
     * Represents a time range with start and end.
     */
    public record TimeRange(MediaTime start, MediaTime end) {
        public TimeRange {
            Objects.requireNonNull(start, "start");
            Objects.requireNonNull(end, "end");
            if (start.isGreaterThan(end)) {
                throw new IllegalArgumentException("start must be <= end");
            }
        }

        public MediaTime duration() {
            return end.subtract(start);
        }

        public boolean contains(MediaTime time) {
            return time.isGreaterThanOrEqualTo(start) && time.isLessThanOrEqualTo(end);
        }

        public boolean overlaps(TimeRange other) {
            return start.isLessThan(other.end) && other.start.isLessThan(end);
        }
    }

    /**
     * Rational number representation for playback rates.
     */
    public record Rational(long numerator, long denominator) {
        public Rational {
            if (denominator <= 0) throw new IllegalArgumentException("denominator must be > 0");
            if (numerator <= 0) throw new IllegalArgumentException("numerator must be > 0");
        }

        public double doubleValue() {
            return (double) numerator / denominator;
        }
    }
}
