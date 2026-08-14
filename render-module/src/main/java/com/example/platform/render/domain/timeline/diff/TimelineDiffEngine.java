package com.example.platform.render.domain.timeline.diff;

import com.example.platform.render.domain.timeline.canonical.TimelineClip;
import com.example.platform.render.domain.timeline.canonical.TimelineDocument;
import com.example.platform.render.domain.timeline.canonical.TimelineTrack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Pure domain engine for computing semantic diff between two TimelineDocument instances.
 * Read-only: produces TimelineChangeSet, no mutation.
 * 
 * Complexity: O(T + C) where T = tracks, C = clips.
 * Deterministic: output order is frozen by entity kind, change kind, entity ID.
 */
public final class TimelineDiffEngine {

    private static final String CHANGE_SET_VERSION = "1.0";

    private TimelineDiffEngine() {}

    /**
     * Compute semantic diff between two TimelineDocument instances.
     * Both must be non-null and have the same schema version.
     */
    public static TimelineChangeSet diff(
            String productId,
            String baseRevisionId,
            String targetRevisionId,
            String baseDigest,
            String targetDigest,
            TimelineDocument base,
            TimelineDocument target) {

        List<TimelineChange> changes = new ArrayList<>();

        // Index tracks and clips by stable ID
        Map<String, IndexedTrack> baseTracks = indexTracks(base);
        Map<String, IndexedTrack> targetTracks = indexTracks(target);

        // Track-level diff
        diffTracks(baseTracks, targetTracks, changes);

        // Clip-level diff (per track)
        diffClips(baseTracks, targetTracks, changes);

        // Deterministic ordering
        changes.sort(TimelineDiffEngine::compareChanges);

        ChangeSummary summary = ChangeSummary.compute(changes);

        String schemaVersion = target != null ? target.getSchemaVersion() :
                (base != null ? base.getSchemaVersion() : TimelineDocument.CURRENT_SCHEMA_VERSION);

        return new TimelineChangeSet(
                CHANGE_SET_VERSION,
                productId,
                baseRevisionId,
                targetRevisionId,
                baseDigest,
                targetDigest,
                schemaVersion,
                changes,
                summary
        );
    }

    private static Map<String, IndexedTrack> indexTracks(TimelineDocument doc) {
        Map<String, IndexedTrack> index = new LinkedHashMap<>();
        if (doc == null) return index;
        List<TimelineTrack> tracks = doc.getTracks();
        for (int i = 0; i < tracks.size(); i++) {
            TimelineTrack track = tracks.get(i);
            index.put(track.trackId(), new IndexedTrack(track, i));
        }
        return index;
    }

    private static void diffTracks(
            Map<String, IndexedTrack> base,
            Map<String, IndexedTrack> target,
            List<TimelineChange> changes) {

        // Use TreeMap for deterministic ordering by trackId
        TreeMap<String, IndexedTrack> allTracks = new TreeMap<>();
        allTracks.putAll(base);
        allTracks.putAll(target);

        for (Map.Entry<String, IndexedTrack> entry : allTracks.entrySet()) {
            String trackId = entry.getKey();
            IndexedTrack baseTrack = base.get(trackId);
            IndexedTrack targetTrack = target.get(trackId);

            if (baseTrack == null) {
                changes.add(TimelineChange.added(EntityKind.TRACK, trackId, targetTrack.position));
            } else if (targetTrack == null) {
                changes.add(TimelineChange.removed(EntityKind.TRACK, trackId, baseTrack.position));
            } else {
                // Check property changes
                if (!Objects.equals(baseTrack.track.name(), targetTrack.track.name())) {
                    changes.add(TimelineChange.propertyChanged(EntityKind.TRACK, trackId, "name",
                            baseTrack.track.name(), targetTrack.track.name()));
                }
                if (!Objects.equals(baseTrack.track.type(), targetTrack.track.type())) {
                    changes.add(TimelineChange.propertyChanged(EntityKind.TRACK, trackId, "type",
                            baseTrack.track.type().name(), targetTrack.track.type().name()));
                }

                // Check reorder (same tracks, different positions)
                if (baseTrack.position != targetTrack.position) {
                    changes.add(TimelineChange.reordered(EntityKind.TRACK, trackId, targetTrack.position));
                }
            }
        }
    }

    private static void diffClips(
            Map<String, IndexedTrack> baseTracks,
            Map<String, IndexedTrack> targetTracks,
            List<TimelineChange> changes) {

        // Build clip indices per track
        Map<String, Map<String, IndexedClip>> baseClipsByTrack = indexClipsByTrack(baseTracks);
        Map<String, Map<String, IndexedClip>> targetClipsByTrack = indexClipsByTrack(targetTracks);

        // Collect all clip IDs across all tracks
        TreeMap<String, ClipLocation> allClips = new TreeMap<>();
        for (Map.Entry<String, Map<String, IndexedClip>> e : baseClipsByTrack.entrySet()) {
            for (Map.Entry<String, IndexedClip> ce : e.getValue().entrySet()) {
                allClips.put(ce.getKey(), new ClipLocation(e.getKey(), ce.getValue(), null, null));
            }
        }
        for (Map.Entry<String, Map<String, IndexedClip>> e : targetClipsByTrack.entrySet()) {
            for (Map.Entry<String, IndexedClip> ce : e.getValue().entrySet()) {
                ClipLocation loc = allClips.get(ce.getKey());
                if (loc == null) {
                    allClips.put(ce.getKey(), new ClipLocation(null, null, e.getKey(), ce.getValue()));
                } else {
                    // Create new record with updated target info
                    allClips.put(ce.getKey(), new ClipLocation(loc.baseTrackId, loc.base, e.getKey(), ce.getValue()));
                }
            }
        }

        for (Map.Entry<String, ClipLocation> entry : allClips.entrySet()) {
            String clipId = entry.getKey();
            ClipLocation loc = entry.getValue();

            IndexedClip baseClip = loc.base != null ? loc.base : null;
            IndexedClip targetClip = loc.target != null ? loc.target : null;
            String baseTrackId = loc.baseTrackId;
            String targetTrackId = loc.targetTrackId;

            if (baseClip == null) {
                // Clip added
                changes.add(TimelineChange.added(EntityKind.CLIP, clipId, targetClip.position));
            } else if (targetClip == null) {
                // Clip removed
                changes.add(TimelineChange.removed(EntityKind.CLIP, clipId, baseClip.position));
            } else {
                // Clip exists in both - check for move or property change
                boolean trackChanged = !Objects.equals(baseTrackId, targetTrackId);
                boolean propertyChanged = !clipsEqual(baseClip.clip, targetClip.clip);
                boolean reordered = baseClip.position != targetClip.position;

                if (trackChanged) {
                    // Clip moved to different track
                    changes.add(TimelineChange.moved(clipId, baseTrackId, targetTrackId));
                    // Also report property changes alongside move
                    if (propertyChanged) {
                        addClipPropertyChanges(clipId, baseClip.clip, targetClip.clip, changes);
                    }
                } else if (propertyChanged) {
                    // Same track, properties changed
                    addClipPropertyChanges(clipId, baseClip.clip, targetClip.clip, changes);
                }

                if (reordered && !trackChanged) {
                    // Same track, reordered
                    changes.add(TimelineChange.reordered(EntityKind.CLIP, clipId, targetClip.position));
                }
            }
        }
    }

    private static void addClipPropertyChanges(String clipId, TimelineClip base, TimelineClip target,
                                                List<TimelineChange> changes) {
        if (!Objects.equals(base.getMediaAssetId(), target.getMediaAssetId())) {
            changes.add(TimelineChange.propertyChanged(EntityKind.CLIP, clipId, "assetId",
                    base.getMediaAssetId(), target.getMediaAssetId()));
        }
        if (!Objects.equals(base.getStartTime(), target.getStartTime())) {
            changes.add(TimelineChange.propertyChanged(EntityKind.CLIP, clipId, "startTime",
                    base.getStartTime().toString(), target.getStartTime().toString()));
        }
        if (!Objects.equals(base.getEndTime(), target.getEndTime())) {
            changes.add(TimelineChange.propertyChanged(EntityKind.CLIP, clipId, "endTime",
                    base.getEndTime().toString(), target.getEndTime().toString()));
        }
        if (!Objects.equals(base.getTrimStart(), target.getTrimStart())) {
            changes.add(TimelineChange.propertyChanged(EntityKind.CLIP, clipId, "trimStart",
                    base.getTrimStart().toString(), target.getTrimStart().toString()));
        }
        if (!Objects.equals(base.getTrimEnd(), target.getTrimEnd())) {
            changes.add(TimelineChange.propertyChanged(EntityKind.CLIP, clipId, "trimEnd",
                    base.getTrimEnd().toString(), target.getTrimEnd().toString()));
        }
    }

    private static boolean clipsEqual(TimelineClip a, TimelineClip b) {
        return Objects.equals(a.getMediaAssetId(), b.getMediaAssetId())
                && Objects.equals(a.getStartTime(), b.getStartTime())
                && Objects.equals(a.getEndTime(), b.getEndTime())
                && Objects.equals(a.getTrimStart(), b.getTrimStart())
                && Objects.equals(a.getTrimEnd(), b.getTrimEnd());
    }

    private static Map<String, Map<String, IndexedClip>> indexClipsByTrack(Map<String, IndexedTrack> tracks) {
        Map<String, Map<String, IndexedClip>> index = new LinkedHashMap<>();
        for (Map.Entry<String, IndexedTrack> e : tracks.entrySet()) {
            Map<String, IndexedClip> clips = new LinkedHashMap<>();
            List<TimelineClip> clipList = e.getValue().track.clips();
            for (int i = 0; i < clipList.size(); i++) {
                clips.put(clipList.get(i).getClipId(), new IndexedClip(clipList.get(i), i));
            }
            index.put(e.getKey(), clips);
        }
        return index;
    }

    /**
     * Deterministic ordering: entity kind → change kind → entity ID → property name → position.
     */
    private static int compareChanges(TimelineChange a, TimelineChange b) {
        int cmp = kindOrder(a.getEntityKind()) - kindOrder(b.getEntityKind());
        if (cmp != 0) return cmp;

        cmp = changeOrder(a.getChangeType()) - changeOrder(b.getChangeType());
        if (cmp != 0) return cmp;

        cmp = a.getEntityId().compareTo(b.getEntityId());
        if (cmp != 0) return cmp;

        if (a.getPropertyName() != null && b.getPropertyName() != null) {
            cmp = a.getPropertyName().compareTo(b.getPropertyName());
            if (cmp != 0) return cmp;
        }

        return Integer.compare(a.getTargetPosition(), b.getTargetPosition());
    }

    private static int kindOrder(EntityKind kind) {
        return kind == EntityKind.TRACK ? 0 : 1;
    }

    private static int changeOrder(ChangeType type) {
        return switch (type) {
            case TRACK_ADDED, CLIP_ADDED, ADDED -> 0;
            case TRACK_REMOVED, CLIP_REMOVED, REMOVED -> 1;
            case TRACK_PROPERTY_CHANGED, CLIP_PROPERTY_CHANGED, PROPERTY_CHANGED -> 2;
            case CLIP_MOVED -> 3;
            case TRACK_REORDERED, CLIP_REORDERED, REORDERED -> 4;
        };
    }

    private record IndexedTrack(TimelineTrack track, int position) {}
    private record IndexedClip(TimelineClip clip, int position) {}
    private record ClipLocation(String baseTrackId, IndexedClip base, String targetTrackId, IndexedClip target) {}
}
