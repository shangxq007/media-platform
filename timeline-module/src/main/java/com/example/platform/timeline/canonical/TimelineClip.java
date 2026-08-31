package com.example.platform.timeline.canonical;

import com.example.platform.timeline.semantics.temporal.ConstantRateTemporalMapping;
import com.example.platform.timeline.semantics.temporal.TemporalMapping;
import com.example.platform.timeline.canonicalmodel.TimelineClipEffect;
import com.example.platform.shared.time.MediaTime;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.List;

/**
 * Canonical Timeline Clip — stable entity with clipId (TIMELINE_V2).
 *
 * <p>Source binding is TYPED via explicit identity fields (mediaAssetId /
 * mediaStreamId / artifactId / contentDigest) — the legacy ambiguous
 * {@code assetId} String is retired. All time fields are EXACT rational
 * {@link MediaTime} ({@code num/den} canonical form), never double/Duration.
 *
 * <p>This record is the revision persistence/API projection; the typed
 * canonical authority is {@code MediaStreamSourceBinding} + {@code MediaClip} in
 * semantics (single typed source binding, no duplicated media metadata).
 */
public class TimelineClip {

    /**
     * Timeline-owned projection from the typed semantic clip into the canonical
     * revision representation. Callers do not need to reach through the source
     * binding into Media-owned identity implementation types.
     */
    public static TimelineClip fromSemanticClip(
            com.example.platform.timeline.semantics.clip.MediaClip clip) {
        var binding = clip.sourceBinding();
        return new TimelineClip(
                clip.clipId(), binding.mediaAssetId().value(),
                binding.mediaStreamId().value(), binding.artifactId().value(),
                binding.contentDigest().canonicalValue(), clip.timelineRange().start(),
                clip.timelineRange().end(), clip.sourceRange().start(),
                clip.sourceRange().end(), binding.sourceKind().name(),
                clip.temporalMapping());
    }

    @JsonProperty("clipId")
    private final TimelineClipId clipId;

    @JsonProperty("sourceKind")
    private final String sourceKind;

    @JsonProperty("mediaAssetId")
    private final String mediaAssetId;

    @JsonProperty("mediaStreamId")
    private final String mediaStreamId;

    @JsonProperty("artifactId")
    private final String artifactId;

    @JsonProperty("contentDigest")
    private final String contentDigest;

    @JsonProperty("startTime")
    @JsonSerialize(using = MediaTimeJsonCodec.Serializer.class)
    @JsonDeserialize(using = MediaTimeJsonCodec.Deserializer.class)
    private final MediaTime startTime;

    @JsonProperty("endTime")
    @JsonSerialize(using = MediaTimeJsonCodec.Serializer.class)
    @JsonDeserialize(using = MediaTimeJsonCodec.Deserializer.class)
    private final MediaTime endTime;

    @JsonProperty("trimStart")
    @JsonSerialize(using = MediaTimeJsonCodec.Serializer.class)
    @JsonDeserialize(using = MediaTimeJsonCodec.Deserializer.class)
    private final MediaTime trimStart;

    @JsonProperty("trimEnd")
    @JsonSerialize(using = MediaTimeJsonCodec.Serializer.class)
    @JsonDeserialize(using = MediaTimeJsonCodec.Deserializer.class)
    private final MediaTime trimEnd;

    @JsonProperty("temporalMapping")
    private final TemporalMapping temporalMapping;

    /** Authored per-clip Effect semantics carried by the sole persisted document. */
    @JsonProperty("effects")
    private final List<TimelineClipEffect> effects;

    /** Convenience constructor: identity temporal mapping (1/1 FORWARD). */
    public TimelineClip(
            String clipId,
            String mediaAssetId,
            String mediaStreamId,
            String artifactId,
            String contentDigest,
            MediaTime startTime,
            MediaTime endTime,
            MediaTime trimStart,
            MediaTime trimEnd,
            String sourceKind) {
        this(clipId, mediaAssetId, mediaStreamId, artifactId, contentDigest,
                startTime, endTime, trimStart, trimEnd, sourceKind, null, List.of());
    }

    /** Compatibility constructor for callers without authored clip effects. */
    public TimelineClip(
            String clipId,
            String mediaAssetId,
            String mediaStreamId,
            String artifactId,
            String contentDigest,
            MediaTime startTime,
            MediaTime endTime,
            MediaTime trimStart,
            MediaTime trimEnd,
            String sourceKind,
            TemporalMapping temporalMapping) {
        this(clipId, mediaAssetId, mediaStreamId, artifactId, contentDigest,
                startTime, endTime, trimStart, trimEnd, sourceKind, temporalMapping, List.of());
    }

    @JsonCreator
    public TimelineClip(
            @JsonProperty("clipId") String clipId,
            @JsonProperty("mediaAssetId") String mediaAssetId,
            @JsonProperty("mediaStreamId") String mediaStreamId,
            @JsonProperty("artifactId") String artifactId,
            @JsonProperty("contentDigest") String contentDigest,
            @JsonProperty("startTime") MediaTime startTime,
            @JsonProperty("endTime") MediaTime endTime,
            @JsonProperty("trimStart") MediaTime trimStart,
            @JsonProperty("trimEnd") MediaTime trimEnd,
            @JsonProperty("sourceKind") String sourceKind,
            @JsonProperty("temporalMapping") TemporalMapping temporalMapping,
            @JsonProperty("effects") List<TimelineClipEffect> effects) {
        if (clipId == null || clipId.isBlank()) {
            throw new IllegalArgumentException("clipId must not be blank");
        }
        // NOTE: mediaAssetId blankness is deliberately NOT rejected here — the
        // canonical adapter (TimelineDocumentCandidateMapper) owns the frozen
        // TimelineCanonicalRejectionException(TIMELINE_SOURCE_REF_INVALID) error
        // contract for invalid source references.
        this.clipId = new TimelineClipId(clipId);
        this.sourceKind = sourceKind != null ? sourceKind : "MEDIA_STREAM";
        this.mediaAssetId = mediaAssetId;
        this.mediaStreamId = mediaStreamId;
        this.artifactId = artifactId;
        this.contentDigest = contentDigest;
        this.startTime = startTime != null ? startTime : MediaTime.ZERO;
        this.endTime = endTime != null ? endTime : MediaTime.ZERO;
        // POST_FINAL_REVIEW_P2-B: trimStart/trimEnd are SOURCE-RANGE semantics.
        // They must NOT be defaulted to ZERO here — MISSING (null) must remain
        // distinguishable from an authored zero so the document mapper can
        // enforce "binding intent ⇒ exact source range required" instead of
        // synthesizing 0..0. Consumers that need a non-null projection must
        // validate intent first (mapper/aggregate rules).
        this.trimStart = trimStart;
        this.trimEnd = trimEnd;
        this.temporalMapping = temporalMapping != null
                ? temporalMapping
                : ConstantRateTemporalMapping.of(1, 1,
                        com.example.platform.timeline.semantics.temporal.PlaybackDirection.FORWARD);
        this.effects = effects != null ? List.copyOf(effects) : List.of();
    }

    public TimelineClipId getClipId() { return clipId; }
    public TemporalMapping getTemporalMapping() { return temporalMapping; }
    public String getSourceKind() { return sourceKind; }
    public String getMediaAssetId() { return mediaAssetId; }
    public String getMediaStreamId() { return mediaStreamId; }
    public String getArtifactId() { return artifactId; }
    public String getContentDigest() { return contentDigest; }
    public MediaTime getStartTime() { return startTime; }
    public MediaTime getEndTime() { return endTime; }
    public MediaTime getTrimStart() { return trimStart; }
    public MediaTime getTrimEnd() { return trimEnd; }
    public List<TimelineClipEffect> getEffects() { return effects; }
}
