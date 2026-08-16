package com.example.platform.timeline.diff;

import com.example.platform.timeline.canonical.TimelineClipId;
import com.example.platform.audio.domain.mix.AudioMix;
import com.example.platform.timeline.canonical.TimelineClip;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.canonical.TimelineTrack;

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

        // AUDIO_V2 (A13): document-level canonical audio mix semantic diff
        diffAudioMix(base, target, changes);
        diffSemanticRelationships(base, target, changes);

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

    /**
     * SEMANTIC_RELATIONSHIP_SELECTION_POST_CLOSE: typed semantic relationship
     * diff. Sync matched by normalized endpoint identity; Group matched by
     * GroupId. Hash change alone is never treated as semantic diff.
     */
    private static void diffSemanticRelationships(TimelineDocument base, TimelineDocument target,
                                                  List<TimelineChange> changes) {
        if (base == null || target == null) {
            return;
        }
        java.util.Map<String, com.example.platform.timeline.semantics.relationship.SyncRelationship> baseSync =
                new java.util.LinkedHashMap<>();
        java.util.Map<String, com.example.platform.timeline.semantics.relationship.GroupRelationship> baseGroups =
                new java.util.LinkedHashMap<>();
        for (var rel : base.getSemanticRelationships()) {
            if (rel instanceof com.example.platform.timeline.semantics.relationship.SyncRelationship s) {
                baseSync.put(s.identityKey(), s);
            } else if (rel instanceof com.example.platform.timeline.semantics.relationship.GroupRelationship g) {
                baseGroups.put(g.groupId().value(), g);
            }
        }
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (var rel : target.getSemanticRelationships()) {
            if (rel instanceof com.example.platform.timeline.semantics.relationship.SyncRelationship s) {
                seen.add(s.identityKey());
                com.example.platform.timeline.semantics.relationship.SyncRelationship b = baseSync.get(s.identityKey());
                if (b == null) {
                    changes.add(TimelineChange.relationshipChanged(ChangeType.RELATIONSHIP_ADDED, s.identityKey(), "relationship", "ABSENT", "SYNC"));
                } else {
                    diffSyncAnchors(b, s, changes);
                }
            } else if (rel instanceof com.example.platform.timeline.semantics.relationship.GroupRelationship g) {
                seen.add("G:" + g.groupId().value());
                com.example.platform.timeline.semantics.relationship.GroupRelationship b = baseGroups.get(g.groupId().value());
                if (b == null) {
                    changes.add(TimelineChange.relationshipChanged(ChangeType.RELATIONSHIP_ADDED, "G:" + g.groupId().value(), "relationship", "ABSENT", "GROUP"));
                } else {
                    diffGroupMembers(b, g, changes);
                }
            }
        }
        for (var entry : baseSync.entrySet()) {
            if (!seen.contains(entry.getKey())) {
                changes.add(TimelineChange.relationshipChanged(ChangeType.RELATIONSHIP_REMOVED, entry.getKey(), "relationship", "SYNC", "ABSENT"));
            }
        }
        for (var entry : baseGroups.entrySet()) {
            if (!seen.contains("G:" + entry.getKey())) {
                changes.add(TimelineChange.relationshipChanged(ChangeType.RELATIONSHIP_REMOVED, "G:" + entry.getKey(), "relationship", "GROUP", "ABSENT"));
            }
        }
    }

    private static void diffSyncAnchors(com.example.platform.timeline.semantics.relationship.SyncRelationship b,
                                        com.example.platform.timeline.semantics.relationship.SyncRelationship t,
                                        List<TimelineChange> changes) {
        if (!b.localAnchorA().equals(t.localAnchorA())) {
            changes.add(TimelineChange.relationshipChanged(ChangeType.SYNC_ANCHOR_CHANGED, t.identityKey(), "sync.anchorA",
                    b.localAnchorA().toString(), t.localAnchorA().toString()));
        }
        if (!b.localAnchorB().equals(t.localAnchorB())) {
            changes.add(TimelineChange.relationshipChanged(ChangeType.SYNC_ANCHOR_CHANGED, t.identityKey(), "sync.anchorB",
                    b.localAnchorB().toString(), t.localAnchorB().toString()));
        }
    }

    private static void diffGroupMembers(com.example.platform.timeline.semantics.relationship.GroupRelationship b,
                                         com.example.platform.timeline.semantics.relationship.GroupRelationship t,
                                         List<TimelineChange> changes) {
        String gid = "G:" + t.groupId().value();
        for (var m : t.members()) {
            if (!b.members().contains(m)) {
                changes.add(TimelineChange.relationshipChanged(ChangeType.GROUP_MEMBER_ADDED, gid, "group.member", "ABSENT", m.value()));
            }
        }
        for (var m : b.members()) {
            if (!t.members().contains(m)) {
                changes.add(TimelineChange.relationshipChanged(ChangeType.GROUP_MEMBER_REMOVED, gid, "group.member", m.value(), "ABSENT"));
            }
        }
    }

    private static void diffAudioMix(TimelineDocument base, TimelineDocument target, List<TimelineChange> changes) {
        AudioMix baseMix = base != null ? base.getAudioMix() : AudioMix.EMPTY;
        AudioMix targetMix = target != null ? target.getAudioMix() : AudioMix.EMPTY;
        if (!baseMix.equals(targetMix)) {
            changes.add(new TimelineChange(
                    ChangeType.AUDIO_MIX_CHANGED,
                    EntityKind.AUDIO_MIX,
                    "audioMix",
                    null,
                    baseMix.toString(),
                    targetMix.toString(),
                    0));
        }
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
        Map<String, Map<TimelineClipId, IndexedClip>> baseClipsByTrack = indexClipsByTrack(baseTracks);
        Map<String, Map<TimelineClipId, IndexedClip>> targetClipsByTrack = indexClipsByTrack(targetTracks);

        // Collect all clip IDs across all tracks
        TreeMap<TimelineClipId, ClipLocation> allClips = new TreeMap<>();
        for (Map.Entry<String, Map<TimelineClipId, IndexedClip>> e : baseClipsByTrack.entrySet()) {
            for (Map.Entry<TimelineClipId, IndexedClip> ce : e.getValue().entrySet()) {
                allClips.put(ce.getKey(), new ClipLocation(e.getKey(), ce.getValue(), null, null));
            }
        }
        for (Map.Entry<String, Map<TimelineClipId, IndexedClip>> e : targetClipsByTrack.entrySet()) {
            for (Map.Entry<TimelineClipId, IndexedClip> ce : e.getValue().entrySet()) {
                ClipLocation loc = allClips.get(ce.getKey());
                if (loc == null) {
                    allClips.put(ce.getKey(), new ClipLocation(null, null, e.getKey(), ce.getValue()));
                } else {
                    // Create new record with updated target info
                    allClips.put(ce.getKey(), new ClipLocation(loc.baseTrackId, loc.base, e.getKey(), ce.getValue()));
                }
            }
        }

        for (Map.Entry<TimelineClipId, ClipLocation> entry : allClips.entrySet()) {
            TimelineClipId clipId = entry.getKey();
            ClipLocation loc = entry.getValue();

            IndexedClip baseClip = loc.base != null ? loc.base : null;
            IndexedClip targetClip = loc.target != null ? loc.target : null;
            String baseTrackId = loc.baseTrackId;
            String targetTrackId = loc.targetTrackId;

            if (baseClip == null) {
                // Clip added
                changes.add(TimelineChange.added(EntityKind.CLIP, clipId.value(), targetClip.position));
            } else if (targetClip == null) {
                // Clip removed
                changes.add(TimelineChange.removed(EntityKind.CLIP, clipId.value(), baseClip.position));
            } else {
                // Clip exists in both - check for move or property change
                boolean trackChanged = !Objects.equals(baseTrackId, targetTrackId);
                boolean propertyChanged = !clipsEqual(baseClip.clip, targetClip.clip);
                boolean reordered = baseClip.position != targetClip.position;

                if (trackChanged) {
                    // Clip moved to different track
                    changes.add(TimelineChange.moved(clipId.value(), baseTrackId, targetTrackId));
                    // Also report property changes alongside move
                    if (propertyChanged) {
                        addClipPropertyChanges(clipId.value(), baseClip.clip, targetClip.clip, changes);
                    }
                } else if (propertyChanged) {
                    // Same track, properties changed
                    addClipPropertyChanges(clipId.value(), baseClip.clip, targetClip.clip, changes);
                }

                if (reordered && !trackChanged) {
                    // Same track, reordered
                    changes.add(TimelineChange.reordered(EntityKind.CLIP, clipId.value(), targetClip.position));
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

    private static Map<String, Map<TimelineClipId, IndexedClip>> indexClipsByTrack(Map<String, IndexedTrack> tracks) {
        Map<String, Map<TimelineClipId, IndexedClip>> index = new LinkedHashMap<>();
        for (Map.Entry<String, IndexedTrack> e : tracks.entrySet()) {
            Map<TimelineClipId, IndexedClip> clips = new LinkedHashMap<>();
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
        return kind == EntityKind.TRACK ? 0 : (kind == EntityKind.CLIP ? 1 : 2);
    }

    private static int changeOrder(ChangeType type) {
        return switch (type) {
            case TRACK_ADDED, CLIP_ADDED, ADDED -> 0;
            case TRACK_REMOVED, CLIP_REMOVED, REMOVED -> 1;
            case TRACK_PROPERTY_CHANGED, CLIP_PROPERTY_CHANGED, PROPERTY_CHANGED -> 2;
            case CLIP_MOVED -> 3;
            case TRACK_REORDERED, CLIP_REORDERED, REORDERED -> 4;
            case AUDIO_MIX_CHANGED -> 5;
            case RELATIONSHIP_ADDED, GROUP_MEMBER_ADDED -> 6;
            case RELATIONSHIP_REMOVED, GROUP_MEMBER_REMOVED -> 7;
            case SYNC_ANCHOR_CHANGED -> 8;
        };
    }

    private record IndexedTrack(TimelineTrack track, int position) {}
    private record IndexedClip(TimelineClip clip, int position) {}
    private record ClipLocation(String baseTrackId, IndexedClip base, String targetTrackId, IndexedClip target) {}
}
