package com.example.platform.render.domain.timeline.diff.calculation;

import com.example.platform.render.domain.timeline.canonical.TimelineClip;
import com.example.platform.render.domain.timeline.canonical.TimelineDocument;
import com.example.platform.render.domain.timeline.canonical.TimelineTrack;
import com.example.platform.render.domain.timeline.canonical.TrackType;
import com.example.platform.render.domain.timeline.canonicalmodel.TimelineCandidate;
import com.example.platform.render.domain.timeline.semantics.time.MediaTime;

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
     * Convert the canonical gate's {@link TimelineCandidate} (produced from the
     * persisted internal-1.0 payload by {@code InternalTimelineCandidateAdapter})
     * into the semantic merge snapshot model.
     *
     * <p>Frozen contract (C1-CRR1): the candidate is the canonical semantic
     * model of the persisted revision payload; this conversion is the single
     * bounded bridge into the merge snapshot space. Track order = declaration
     * order (matching the TimelineDocument mapping). Clip fields: clipId,
     * assetBindingId (sourceRef), startMs (timelineStart), durationMs,
     * sourceStartMs (sourceStart), sourceDurationMs (duration).</p>
     */
    public static CanonicalTimelineSnapshot toSnapshot(TimelineCandidate candidate, String revisionId) {
        List<CanonicalTimelineTrackSnapshot> tracks = new ArrayList<>();
        long durationMs = 0L;
        List<TimelineCandidate.Track> candidateTracks = candidate.tracks();
        for (int i = 0; i < candidateTracks.size(); i++) {
            TimelineCandidate.Track track = candidateTracks.get(i);
            List<CanonicalTimelineClipSnapshot> clips = new ArrayList<>();
            for (TimelineCandidate.Clip clip : track.clips()) {
                long startMs = toMillis(clip.timelineStart());
                long duration = toMillis(clip.duration());
                long sourceStartMs = toMillis(clip.sourceStart());
                long sourceDurationMs = toMillis(clip.duration());
                long endMs = startMs + duration;
                durationMs = Math.max(durationMs, endMs);
                clips.add(new CanonicalTimelineClipSnapshot(
                        clip.clipId(),
                        clip.sourceRef() != null ? clip.sourceRef().value() : "",
                        startMs,
                        duration,
                        sourceStartMs,
                        sourceDurationMs,
                        Map.of()));
            }
            String kind = track.type() != null ? track.type().name() : "VIDEO";
            tracks.add(new CanonicalTimelineTrackSnapshot(
                    track.trackId(), i, kind, List.copyOf(clips), Map.of()));
        }
        return new CanonicalTimelineSnapshot(
                new CanonicalTimelineSnapshotId("snap-" + revisionId),
                revisionId,
                durationMs,
                List.copyOf(tracks),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                Map.of("schemaVersion", "internal-1.0"));
    }

    /** Exact rational MediaTime -> milliseconds (truncated to long, same convention as the double path). */
    private static long toMillis(MediaTime time) {
        if (time == null) {
            return 0L;
        }
        long timeScale = time.timeScale();
        if (timeScale == 0) {
            return 0L;
        }
        // ticks / timeScale seconds -> milliseconds
        return (time.ticks() * 1000L) / timeScale;
    }

    public static CanonicalTimelineSnapshot toSnapshot(TimelineDocument document, String revisionId) {
        List<CanonicalTimelineTrackSnapshot> tracks = new ArrayList<>();
        long durationMs = 0L;
        List<TimelineTrack> docTracks = document.getTracks();
        for (int i = 0; i < docTracks.size(); i++) {
            TimelineTrack track = docTracks.get(i);
            List<CanonicalTimelineClipSnapshot> clips = new ArrayList<>();
            for (TimelineClip clip : track.clips()) {
                long startMs = clip.getStartTime().toMillis();
                long endMs = clip.getEndTime().toMillis();
                long duration = Math.max(0L, endMs - startMs);
                durationMs = Math.max(durationMs, endMs);
                clips.add(new CanonicalTimelineClipSnapshot(
                        clip.getClipId(),
                        clip.getAssetId(),
                        startMs,
                        duration,
                        clip.getTrimStart().toMillis(),
                        clip.getTrimEnd().toMillis(),
                        Map.of()));
            }
            String kind = track.type() != null ? track.type().name() : "VIDEO";
            tracks.add(new CanonicalTimelineTrackSnapshot(
                    track.trackId(), i, kind, List.copyOf(clips), Map.of()));
        }
        return new CanonicalTimelineSnapshot(
                new CanonicalTimelineSnapshotId("snap-" + revisionId),
                revisionId,
                durationMs,
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
                java.time.Duration start = java.time.Duration.ofMillis(clip.startMs());
                java.time.Duration end = java.time.Duration.ofMillis(clip.startMs() + clip.durationMs());
                clips.add(new TimelineClip(
                        clip.clipId(),
                        clip.assetBindingId(),
                        start,
                        end,
                        java.time.Duration.ofMillis(clip.sourceStartMs()),
                        java.time.Duration.ofMillis(clip.sourceDurationMs())));
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
}
