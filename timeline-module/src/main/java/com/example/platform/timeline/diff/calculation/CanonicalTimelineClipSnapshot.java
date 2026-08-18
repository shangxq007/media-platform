package com.example.platform.timeline.diff.calculation;

import com.example.platform.timeline.canonicalmodel.TimelineClipEffect;
import com.example.platform.timeline.semantics.clip.MediaStreamSourceBinding;
import com.example.platform.timeline.semantics.clip.TimelineSourceBinding;
import com.example.platform.shared.time.FrameRate;
import com.example.platform.shared.time.MediaTime;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Canonical semantic merge clip snapshot (C1-CNM1 exact-time contract).
 *
 * <p>All time fields are EXACT {@link MediaTime} (rational ticks/timeScale) —
 * integer milliseconds are a projection, never merge semantic authority.
 * Frame rate is the exact rational {@link FrameRate} of the clip's timeline
 * range; the denominator is preserved end-to-end through merge.
 *
 * <p>Effects are carried as an OPAQUE payload list ({@link TimelineClipEffect},
 * never semantically merged — preserved target/source-side per CNM1
 * effect-preservation contract). Unknown effect internals are not diffed.
 *
 * <p>R4-B (CHECKPOINT_A Round 4): the clip carries the REAL typed
 * {@link TimelineSourceBinding} semantic value — never independently
 * authoritative flattened Strings. The legacy flat accessors
 * ({@code sourceKind()}, {@code mediaStreamId()}, {@code artifactId()},
 * {@code contentDigest()}) are DERIVED projections of the typed binding for
 * serialization-boundary compatibility; they are not a parallel semantic
 * representation and carry no merge authority.
 */
public record CanonicalTimelineClipSnapshot(
        String clipId,
        String assetBindingId,
        MediaTime start,
        MediaTime duration,
        MediaTime sourceStart,
        MediaTime sourceDuration,
        FrameRate rate,
        List<TimelineClipEffect> effects,
        Map<String, String> safeMetadata,
        TimelineSourceBinding sourceBinding,
        com.example.platform.timeline.semantics.temporal.TemporalMapping temporalMapping) {

    public CanonicalTimelineClipSnapshot {
        Objects.requireNonNull(clipId, "clipId");
        if (clipId.isBlank()) {
            throw new IllegalArgumentException("clipId must not be blank");
        }
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(duration, "duration");
        Objects.requireNonNull(sourceStart, "sourceStart");
        Objects.requireNonNull(sourceDuration, "sourceDuration");
        // MediaTime is non-negative by construction (ofTicks rejects ticks < 0);
        // durationMs >= 0 invariants are therefore guaranteed at type level.
        effects = effects == null ? List.of() : List.copyOf(effects);
        safeMetadata = safeMetadata == null ? Map.of() : Map.copyOf(safeMetadata);
    }

    /**
     * R4-B legacy projection convenience constructor: builds the typed source
     * binding from flat fields when the authored payload carried them. A
     * binding is only constructed when the source kind is present AND all
     * required typed fields are non-blank — never a MediaAssetId-only
     * fallback, never silent String-field narrowing. Absent/partial flat
     * fields yield a null binding (clip has no typed source binding).
     */
    public CanonicalTimelineClipSnapshot(
            String clipId,
            String assetBindingId,
            MediaTime start,
            MediaTime duration,
            MediaTime sourceStart,
            MediaTime sourceDuration,
            FrameRate rate,
            List<TimelineClipEffect> effects,
            Map<String, String> safeMetadata,
            String sourceKind,
            String mediaStreamId,
            String artifactId,
            String contentDigest,
            com.example.platform.timeline.semantics.temporal.TemporalMapping temporalMapping) {
        this(clipId, assetBindingId, start, duration, sourceStart, sourceDuration, rate,
                effects, safeMetadata,
                toTypedBinding(sourceKind, assetBindingId, mediaStreamId, artifactId,
                        contentDigest, sourceStart, sourceDuration),
                temporalMapping);
    }

    /** Typed-binding construction rule — the ONLY flat→typed narrowing in the
     *  snapshot; it requires the complete typed field set (no partial binding). */
    private static TimelineSourceBinding toTypedBinding(String sourceKind, String mediaAssetId,
            String mediaStreamId, String artifactId, String contentDigest,
            MediaTime sourceStart, MediaTime sourceDuration) {
        if (sourceKind == null || sourceKind.isBlank()
                || !TimelineSourceBinding.SourceKind.MEDIA_STREAM.name().equals(sourceKind)) {
            return null;
        }
        if (mediaAssetId == null || mediaAssetId.isBlank()
                || mediaStreamId == null || mediaStreamId.isBlank()
                || artifactId == null || artifactId.isBlank()
                || contentDigest == null || contentDigest.isBlank()) {
            return null;
        }
        MediaTime end;
        try {
            end = sourceStart.add(sourceDuration);
        } catch (ArithmeticException overflow) {
            end = sourceStart;
        }
        return new MediaStreamSourceBinding(
                new com.example.platform.media.domain.identity.MediaAssetId(mediaAssetId),
                new com.example.platform.media.domain.stream.MediaStreamId(mediaStreamId),
                new com.example.platform.shared.identity.ArtifactId(artifactId),
                com.example.platform.shared.digest.ContentDigest.sha256(contentDigest),
                new com.example.platform.timeline.semantics.clip.MediaClip.TimeRange(sourceStart, end));
    }

    // ── R4-B derived projections (serialization-boundary compatibility only) ──

    /** Derived projection: source-kind discriminator of the typed binding. */
    public String sourceKind() {
        return sourceBinding == null ? null : sourceBinding.sourceKind().name();
    }

    /** Derived projection: media stream id of the typed binding. */
    public String mediaStreamId() {
        return sourceBinding instanceof MediaStreamSourceBinding m ? m.mediaStreamId().value() : null;
    }

    /** Derived projection: artifact id of the typed binding. */
    public String artifactId() {
        return sourceBinding instanceof MediaStreamSourceBinding m ? m.artifactId().value() : null;
    }

    /** Derived projection: content digest of the typed binding. */
    public String contentDigest() {
        return sourceBinding instanceof MediaStreamSourceBinding m ? m.contentDigest().value() : null;
    }

    /** True when the clip carries no effect payload. */
    public boolean hasNoEffects() {
        return effects.isEmpty();
    }
}
