package com.example.platform.render.domain.timeline.diff.calculation;

import com.example.platform.render.domain.timeline.canonical.TimelineClip;
import com.example.platform.render.domain.timeline.canonical.TimelineDocument;
import com.example.platform.render.domain.timeline.canonical.TimelineTrack;
import com.example.platform.render.domain.timeline.canonical.TrackType;
import com.example.platform.render.domain.timeline.canonicalmodel.TimelineCandidate;
import com.example.platform.render.domain.timeline.canonicalmodel.TimelineClipEffect;
import com.example.platform.shared.time.FrameRate;
import com.example.platform.shared.time.MediaTime;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Bidirectional converter between canonical {@link TimelineDocument} and
 * {@link CanonicalTimelineSnapshot} for the semantic merge pipeline.
 *
 * <p>The snapshot space covers tracks and clips (position/trim/asset binding)
 * as defined by the canonical diff model. Document-level fields outside that
 * space (metadata, schema version) are carried through {@code safeMetadata}
 * so the reverse conversion is lossless for the merge scope.</p>
 *
 * <p>C1-CRR1: {@link #toSnapshot(TimelineCandidate, String)} converts the
 * canonical persisted internal-1.0 payload (via the E1b gate's own
 * {@link TimelineCandidate} representation) into the semantic merge model.
 * The candidate carries exact rational {@link MediaTime}; the snapshot space
 * uses milliseconds, mirroring the existing {@code TimelineDocument} mapping
 * (start/duration/source bounds) with zero floating-point loss.</p>
 */
public final class TimelineSnapshotConverter {

    private TimelineSnapshotConverter() {
    }

    /**
     * C1-CNM1: convert the canonical gate's {@link TimelineCandidate} (produced
     * from the persisted internal-1.0 payload by
     * {@code InternalTimelineCandidateAdapter}) into the semantic merge snapshot
     * model.
     *
     * <p>Frozen contract: the candidate is the canonical semantic model of the
     * persisted revision payload; this conversion is the single bounded bridge
     * into the merge snapshot space. All time fields are copied EXACTLY as
     * {@link MediaTime} (no integer-ms step — integer milliseconds are a
     * projection, never merge semantic authority). Clip rate (exact rational
     * {@link FrameRate}) and opaque effects are carried through so the merged
     * payload preserves denominator and effect payloads.</p>
     */
    public static CanonicalTimelineSnapshot toSnapshot(TimelineCandidate candidate, String revisionId) {
        List<CanonicalTimelineTrackSnapshot> tracks = new ArrayList<>();
        MediaTime duration = MediaTime.ZERO;
        List<TimelineCandidate.Track> candidateTracks = candidate.tracks();
        for (int i = 0; i < candidateTracks.size(); i++) {
            TimelineCandidate.Track track = candidateTracks.get(i);
            List<CanonicalTimelineClipSnapshot> clips = new ArrayList<>();
            for (TimelineCandidate.Clip clip : track.clips()) {
                MediaTime end = clip.timelineStart().add(clip.duration());
                if (end.isGreaterThan(duration)) {
                    duration = end;
                }
                clips.add(new CanonicalTimelineClipSnapshot(
                        clip.clipId(),
                        clip.sourceRef() != null ? clip.sourceRef().value() : "",
                        clip.timelineStart(),
                        clip.duration(),
                        clip.sourceStart(),
                        clip.duration(),
                        clip.rate() != null ? clip.rate() : FrameRate.of(30, 1),
                        clip.effects() != null ? List.copyOf(clip.effects()) : List.of(),
                        Map.of()));
            }
            String kind = track.type() != null ? track.type().name() : "VIDEO";
            tracks.add(new CanonicalTimelineTrackSnapshot(
                    track.trackId(), i, kind, List.copyOf(clips), Map.of()));
        }
        return new CanonicalTimelineSnapshot(
                new CanonicalTimelineSnapshotId("snap-" + revisionId),
                revisionId,
                duration,
                List.copyOf(tracks),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                Map.of("schemaVersion", "internal-1.0"));
    }

    public static CanonicalTimelineSnapshot toSnapshot(TimelineDocument document, String revisionId) {
        List<CanonicalTimelineTrackSnapshot> tracks = new ArrayList<>();
        MediaTime duration = MediaTime.ZERO;
        List<TimelineTrack> docTracks = document.getTracks();
        for (int i = 0; i < docTracks.size(); i++) {
            TimelineTrack track = docTracks.get(i);
            List<CanonicalTimelineClipSnapshot> clips = new ArrayList<>();
            for (TimelineClip clip : track.clips()) {
                MediaTime start = clip.getStartTime();
                MediaTime end = clip.getEndTime();
                MediaTime clipDuration = end.subtract(start).max(MediaTime.ZERO);
                if (end.isGreaterThan(duration)) {
                    duration = end;
                }
                clips.add(new CanonicalTimelineClipSnapshot(
                        clip.getClipId(),
                        clip.getMediaAssetId(),
                        start,
                        clipDuration,
                        clip.getTrimStart(),
                        clip.getTrimEnd(),
                        FrameRate.of(30, 1),
                        List.of(),
                        Map.of()));
            }
            String kind = track.type() != null ? track.type().name() : "VIDEO";
            tracks.add(new CanonicalTimelineTrackSnapshot(
                    track.trackId(), i, kind, List.copyOf(clips), Map.of()));
        }
        return new CanonicalTimelineSnapshot(
                new CanonicalTimelineSnapshotId("snap-" + revisionId),
                revisionId,
                duration,
                List.copyOf(tracks),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                Map.of("schemaVersion", document.getSchemaVersion()));
    }

    /**
     * Reconstruct a {@link TimelineDocument} from a merged snapshot, using
     * {@code template} as the source for document-level fields (schema version,
     * metadata) that are outside the canonical snapshot space.
     *
     * <p>Legacy timeline-1.0 document boundary: TimelineDocument is expressed
     * in {@link java.time.Duration}; the exact {@link MediaTime} snapshot
     * values are projected through the exact rational ms conversion (media
     * time is never re-quantized through floating point here).</p>
     */
    public static TimelineDocument toDocument(CanonicalTimelineSnapshot snapshot, TimelineDocument template) {
        String schemaVersion = snapshot.safeMetadata() != null
                ? snapshot.safeMetadata().getOrDefault("schemaVersion",
                        template != null ? template.getSchemaVersion() : TimelineDocument.CURRENT_SCHEMA_VERSION)
                : (template != null ? template.getSchemaVersion() : TimelineDocument.CURRENT_SCHEMA_VERSION);
        List<TimelineTrack> tracks = new ArrayList<>();
        List<CanonicalTimelineTrackSnapshot> ordered = new ArrayList<>(snapshot.tracks());
        ordered.sort(java.util.Comparator.comparingInt(CanonicalTimelineTrackSnapshot::order));
        for (CanonicalTimelineTrackSnapshot track : ordered) {
            List<TimelineClip> clips = new ArrayList<>();
            for (CanonicalTimelineClipSnapshot clip : track.clips()) {
                MediaTime start = clip.start();
                MediaTime end = clip.start().add(clip.duration());
                // Snapshot boundary: single binding id is restored as mediaAssetId;
                // stream/artifact/digest are not carried in the merge snapshot
                // (full binding restoration is a follow-up bounded delivery).
                clips.add(new TimelineClip(
                        clip.clipId(),
                        clip.assetBindingId(),
                        null,
                        null,
                        null,
                        start,
                        end,
                        clip.sourceStart(),
                        clip.sourceStart().add(clip.sourceDuration())));
            }
            TrackType type = TrackType.VIDEO;
            try {
                if (track.kind() != null) {
                    type = TrackType.valueOf(track.kind());
                }
            } catch (IllegalArgumentException ignored) {
                type = TrackType.VIDEO;
            }
            tracks.add(new TimelineTrack(track.trackId(), track.trackId(), type, List.copyOf(clips)));
        }
        return new TimelineDocument(
                schemaVersion,
                List.copyOf(tracks),
                template != null ? template.getMetadata() : null);
    }

    /**
     * Exact rational MediaTime -&gt; integer milliseconds (half-up).
     *
     * <p>PROJECTION ONLY — used at the legacy timeline-1.0 document boundary
     * ({@link TimelineDocument} expresses time in {@link java.time.Duration}).
     * Never a merge semantic authority; canonical merge time is exact
     * {@link MediaTime} end-to-end (C1-CNM1).</p>
     */
    private static long toMillis(MediaTime time) {
        if (time == null) {
            return 0L;
        }
        return (time.ticks() * 1000L + time.timeScale() / 2) / time.timeScale();
    }
}
