package com.example.platform.render.domain.timeline.patch;

import com.example.platform.render.domain.timeline.canonical.TimelineClipId;
import com.example.platform.render.domain.timeline.canonical.TimelineClip;
import com.example.platform.render.domain.timeline.canonical.TimelineDocument;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.render.domain.timeline.canonical.TimelineTrack;
import com.example.platform.render.domain.timeline.canonical.TrackType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * Pure domain engine for applying TimelinePatch to TimelineDocument.
 * No database, no mutation, deterministic.
 * Complexity: O(T + C + O) where T=tracks, C=clips, O=operations.
 */
public final class TimelinePatchEngine {

    private TimelinePatchEngine() {}

    /**
     * Apply patch to base document, returning new document.
     * Pure function: no mutation of input.
     */
    public static PatchApplicationResult apply(TimelineDocument base, TimelinePatch patch) {
        // Build indices for O(1) lookup
        Map<String, IndexedTrack> trackIndex = new LinkedHashMap<>();
        Map<TimelineClipId, IndexedClip> clipIndex = new LinkedHashMap<>();
        indexDocument(base, trackIndex, clipIndex);

        // Preflight validation
        List<PatchError> errors = preflight(patch, trackIndex, clipIndex);
        if (!errors.isEmpty()) {
            return PatchApplicationResult.failure(errors);
        }

        // Apply operations sequentially
        List<TimelineTrack> tracks = new ArrayList<>(base.getTracks());
        try {
            for (TimelinePatchOperation op : patch.operations()) {
                tracks = applyOperation(op, tracks, trackIndex, clipIndex);
                rebuildIndices(tracks, trackIndex, clipIndex);
            }
        } catch (PatchExecutionException e) {
            return PatchApplicationResult.failure(List.of(
                    new PatchError(PatchErrorCode.TIMELINE_PATCH_PRECONDITION_FAILED,
                            e.getMessage(), null, null)));
        }

        TimelineDocument result = new TimelineDocument(
                base.getSchemaVersion(),
                tracks,
                base.getMetadata());

        return PatchApplicationResult.success(result);
    }

    private static List<TimelineTrack> applyOperation(
            TimelinePatchOperation op,
            List<TimelineTrack> tracks,
            Map<String, IndexedTrack> trackIndex,
            Map<TimelineClipId, IndexedClip> clipIndex) {

        if (op instanceof TimelinePatchOperation.AddTrack add) {
            return applyAddTrack(add, tracks);
        } else if (op instanceof TimelinePatchOperation.RemoveTrack remove) {
            return applyRemoveTrack(remove, tracks, trackIndex);
        } else if (op instanceof TimelinePatchOperation.UpdateTrackProperty update) {
            return applyUpdateTrackProperty(update, tracks, trackIndex);
        } else if (op instanceof TimelinePatchOperation.ReorderTrack reorder) {
            return applyReorderTrack(reorder, tracks, trackIndex);
        } else if (op instanceof TimelinePatchOperation.AddClip add) {
            return applyAddClip(add, tracks, trackIndex);
        } else if (op instanceof TimelinePatchOperation.RemoveClip remove) {
            return applyRemoveClip(remove, tracks, trackIndex, clipIndex);
        } else if (op instanceof TimelinePatchOperation.UpdateClipProperty update) {
            return applyUpdateClipProperty(update, tracks, trackIndex, clipIndex);
        } else if (op instanceof TimelinePatchOperation.MoveClip move) {
            return applyMoveClip(move, tracks, trackIndex, clipIndex);
        } else if (op instanceof TimelinePatchOperation.ReorderClip reorder) {
            return applyReorderClip(reorder, tracks, trackIndex, clipIndex);
        }
        throw new IllegalArgumentException("Unknown operation type: " + op.getClass());
    }

    private static List<TimelineTrack> applyAddTrack(TimelinePatchOperation.AddTrack op, List<TimelineTrack> tracks) {
        List<TimelineTrack> result = new ArrayList<>(tracks);
        int pos = Math.min(op.targetPosition(), result.size());
        result.add(pos, op.track());
        return result;
    }

    private static List<TimelineTrack> applyRemoveTrack(TimelinePatchOperation.RemoveTrack op, List<TimelineTrack> tracks, Map<String, IndexedTrack> trackIndex) {
        IndexedTrack indexed = trackIndex.get(op.trackId());
        if (indexed == null) throw new PatchExecutionException("Track not found: " + op.trackId());
        List<TimelineTrack> result = new ArrayList<>(tracks);
        result.remove(indexed.position());
        return result;
    }

    private static List<TimelineTrack> applyUpdateTrackProperty(TimelinePatchOperation.UpdateTrackProperty op, List<TimelineTrack> tracks, Map<String, IndexedTrack> trackIndex) {
        IndexedTrack indexed = trackIndex.get(op.trackId());
        if (indexed == null) throw new PatchExecutionException("Track not found: " + op.trackId());
        TimelineTrack track = indexed.track();
        String currentValue = switch (op.property()) {
            case "name" -> track.name();
            case "type" -> track.type().name();
            default -> throw new PatchExecutionException("Unknown track property: " + op.property());
        };
        if (!Objects.equals(currentValue, op.expectedBefore())) {
            throw new PatchExecutionException("Precondition failed for track " + op.trackId() + " property " + op.property() + ": expected " + op.expectedBefore() + ", actual " + currentValue);
        }
        TimelineTrack updated = switch (op.property()) {
            case "name" -> new TimelineTrack(track.trackId(), op.newValue(), track.type(), track.clips());
            case "type" -> new TimelineTrack(track.trackId(), track.name(), TrackType.valueOf(op.newValue()), track.clips());
            default -> throw new PatchExecutionException("Unknown track property: " + op.property());
        };
        List<TimelineTrack> result = new ArrayList<>(tracks);
        result.set(indexed.position(), updated);
        return result;
    }

    private static List<TimelineTrack> applyReorderTrack(TimelinePatchOperation.ReorderTrack op, List<TimelineTrack> tracks, Map<String, IndexedTrack> trackIndex) {
        IndexedTrack indexed = trackIndex.get(op.trackId());
        if (indexed == null) throw new PatchExecutionException("Track not found: " + op.trackId());
        List<TimelineTrack> result = new ArrayList<>(tracks);
        TimelineTrack track = result.remove(indexed.position());
        int pos = Math.min(op.targetPosition(), result.size());
        result.add(pos, track);
        return result;
    }

    private static List<TimelineTrack> applyAddClip(TimelinePatchOperation.AddClip op, List<TimelineTrack> tracks, Map<String, IndexedTrack> trackIndex) {
        IndexedTrack indexed = trackIndex.get(op.targetTrackId());
        if (indexed == null) throw new PatchExecutionException("Target track not found: " + op.targetTrackId());
        TimelineTrack track = indexed.track();
        List<TimelineClip> clips = new ArrayList<>(track.clips());
        int pos = Math.min(op.targetPosition(), clips.size());
        clips.add(pos, op.clip());
        TimelineTrack updated = new TimelineTrack(track.trackId(), track.name(), track.type(), clips);
        List<TimelineTrack> result = new ArrayList<>(tracks);
        result.set(indexed.position(), updated);
        return result;
    }

    private static List<TimelineTrack> applyRemoveClip(TimelinePatchOperation.RemoveClip op, List<TimelineTrack> tracks, Map<String, IndexedTrack> trackIndex, Map<TimelineClipId, IndexedClip> clipIndex) {
        IndexedClip clipIdx = clipIndex.get(new TimelineClipId(op.clipId()));
        if (clipIdx == null) throw new PatchExecutionException("Clip not found: " + op.clipId());
        if (!clipIdx.trackId().equals(op.expectedTrackId())) {
            throw new PatchExecutionException("Clip " + op.clipId() + " not in expected track: " + op.expectedTrackId());
        }
        IndexedTrack trackIdx = trackIndex.get(op.expectedTrackId());
        TimelineTrack track = trackIdx.track();
        List<TimelineClip> clips = new ArrayList<>(track.clips());
        clips.remove(clipIdx.position());
        TimelineTrack updated = new TimelineTrack(track.trackId(), track.name(), track.type(), clips);
        List<TimelineTrack> result = new ArrayList<>(tracks);
        result.set(trackIdx.position(), updated);
        return result;
    }

    private static List<TimelineTrack> applyUpdateClipProperty(TimelinePatchOperation.UpdateClipProperty op, List<TimelineTrack> tracks, Map<String, IndexedTrack> trackIndex, Map<TimelineClipId, IndexedClip> clipIndex) {
        IndexedClip clipIdx = clipIndex.get(new TimelineClipId(op.clipId()));
        if (clipIdx == null) throw new PatchExecutionException("Clip not found: " + op.clipId());
        IndexedTrack trackIdx = trackIndex.get(clipIdx.trackId());
        TimelineTrack track = trackIdx.track();
        TimelineClip clip = clipIdx.clip();
        String currentValue = switch (op.property()) {
            case "mediaAssetId" -> clip.getMediaAssetId();
            case "startTime" -> clip.getStartTime().toString();
            case "endTime" -> clip.getEndTime().toString();
            case "trimStart" -> clip.getTrimStart().toString();
            case "trimEnd" -> clip.getTrimEnd().toString();
            default -> throw new PatchExecutionException("Unknown clip property: " + op.property());
        };
        if (!Objects.equals(currentValue, op.expectedBefore())) {
            throw new PatchExecutionException("Precondition failed for clip " + op.clipId() + " property " + op.property() + ": expected " + op.expectedBefore() + ", actual " + currentValue);
        }
        TimelineClip updated = switch (op.property()) {
            case "mediaAssetId" -> new TimelineClip(clip.getClipId().value(), op.newValue(),
                    clip.getMediaStreamId(), clip.getArtifactId(), clip.getContentDigest(),
                    clip.getStartTime(), clip.getEndTime(), clip.getTrimStart(), clip.getTrimEnd(), clip.getSourceKind());
            case "startTime" -> new TimelineClip(clip.getClipId().value(), clip.getMediaAssetId(),
                    clip.getMediaStreamId(), clip.getArtifactId(), clip.getContentDigest(),
                    parseMediaTime(op.newValue()), clip.getEndTime(), clip.getTrimStart(), clip.getTrimEnd(), clip.getSourceKind());
            case "endTime" -> new TimelineClip(clip.getClipId().value(), clip.getMediaAssetId(),
                    clip.getMediaStreamId(), clip.getArtifactId(), clip.getContentDigest(),
                    clip.getStartTime(), parseMediaTime(op.newValue()), clip.getTrimStart(), clip.getTrimEnd(), clip.getSourceKind());
            case "trimStart" -> new TimelineClip(clip.getClipId().value(), clip.getMediaAssetId(),
                    clip.getMediaStreamId(), clip.getArtifactId(), clip.getContentDigest(),
                    clip.getStartTime(), clip.getEndTime(), parseMediaTime(op.newValue()), clip.getTrimEnd(), clip.getSourceKind());
            case "trimEnd" -> new TimelineClip(clip.getClipId().value(), clip.getMediaAssetId(),
                    clip.getMediaStreamId(), clip.getArtifactId(), clip.getContentDigest(),
                    clip.getStartTime(), clip.getEndTime(), clip.getTrimStart(), parseMediaTime(op.newValue()), clip.getSourceKind());
            default -> throw new PatchExecutionException("Unknown clip property: " + op.property());
        };
        List<TimelineClip> clips = new ArrayList<>(track.clips());
        clips.set(clipIdx.position(), updated);
        TimelineTrack updatedTrack = new TimelineTrack(track.trackId(), track.name(), track.type(), clips);
        List<TimelineTrack> result = new ArrayList<>(tracks);
        result.set(trackIdx.position(), updatedTrack);
        return result;
    }

    private static List<TimelineTrack> applyMoveClip(TimelinePatchOperation.MoveClip op, List<TimelineTrack> tracks, Map<String, IndexedTrack> trackIndex, Map<TimelineClipId, IndexedClip> clipIndex) {
        IndexedClip clipIdx = clipIndex.get(new TimelineClipId(op.clipId()));
        if (clipIdx == null) throw new PatchExecutionException("Clip not found: " + op.clipId());
        if (!clipIdx.trackId().equals(op.expectedSourceTrackId())) {
            throw new PatchExecutionException("Clip " + op.clipId() + " not in expected source track: " + op.expectedSourceTrackId());
        }
        IndexedTrack targetTrackIdx = trackIndex.get(op.targetTrackId());
        if (targetTrackIdx == null) throw new PatchExecutionException("Target track not found: " + op.targetTrackId());
        IndexedTrack sourceTrackIdx = trackIndex.get(op.expectedSourceTrackId());
        TimelineClip clip = clipIdx.clip();
        // Remove from source
        TimelineTrack sourceTrack = sourceTrackIdx.track();
        List<TimelineClip> sourceClips = new ArrayList<>(sourceTrack.clips());
        sourceClips.remove(clipIdx.position());
        TimelineTrack updatedSource = new TimelineTrack(sourceTrack.trackId(), sourceTrack.name(), sourceTrack.type(), sourceClips);
        // Add to target
        TimelineTrack targetTrack = targetTrackIdx.track();
        List<TimelineClip> targetClips = new ArrayList<>(targetTrack.clips());
        int pos = Math.min(op.targetPosition(), targetClips.size());
        targetClips.add(pos, clip);
        TimelineTrack updatedTarget = new TimelineTrack(targetTrack.trackId(), targetTrack.name(), targetTrack.type(), targetClips);
        // Rebuild tracks list
        List<TimelineTrack> result = new ArrayList<>(tracks);
        result.set(sourceTrackIdx.position(), updatedSource);
        result.set(targetTrackIdx.position(), updatedTarget);
        return result;
    }

    private static List<TimelineTrack> applyReorderClip(TimelinePatchOperation.ReorderClip op, List<TimelineTrack> tracks, Map<String, IndexedTrack> trackIndex, Map<TimelineClipId, IndexedClip> clipIndex) {
        IndexedClip clipIdx = clipIndex.get(new TimelineClipId(op.clipId()));
        if (clipIdx == null) throw new PatchExecutionException("Clip not found: " + op.clipId());
        if (!clipIdx.trackId().equals(op.trackId())) {
            throw new PatchExecutionException("Clip " + op.clipId() + " not in track: " + op.trackId());
        }
        IndexedTrack trackIdx = trackIndex.get(op.trackId());
        TimelineTrack track = trackIdx.track();
        List<TimelineClip> clips = new ArrayList<>(track.clips());
        TimelineClip clip = clips.remove(clipIdx.position());
        int pos = Math.min(op.targetPosition(), clips.size());
        clips.add(pos, clip);
        TimelineTrack updated = new TimelineTrack(track.trackId(), track.name(), track.type(), clips);
        List<TimelineTrack> result = new ArrayList<>(tracks);
        result.set(trackIdx.position(), updated);
        return result;
    }

    private static MediaTime parseMediaTime(String text) {
        if ("0".equals(text)) {
            return MediaTime.ZERO;
        }
        int slash = text.indexOf('/');
        if (slash < 1 || slash == text.length() - 1) {
            throw new IllegalArgumentException("Invalid exact MediaTime: " + text);
        }
        long num = Long.parseLong(text.substring(0, slash).trim());
        long den = Long.parseLong(text.substring(slash + 1).trim());
        if (den <= 0) {
            throw new IllegalArgumentException("MediaTime denominator must be > 0: " + text);
        }
        return MediaTime.ofRational(num, den);
    }

    private static void indexDocument(TimelineDocument doc, Map<String, IndexedTrack> trackIndex, Map<TimelineClipId, IndexedClip> clipIndex) {
        int ti = 0;
        for (TimelineTrack track : doc.getTracks()) {
            trackIndex.put(track.trackId(), new IndexedTrack(track, ti));
            int ci = 0;
            for (TimelineClip clip : track.clips()) {
                clipIndex.put(clip.getClipId(), new IndexedClip(clip, track.trackId(), ci));
                ci++;
            }
            ti++;
        }
    }

    private static void rebuildIndices(List<TimelineTrack> tracks, Map<String, IndexedTrack> trackIndex, Map<TimelineClipId, IndexedClip> clipIndex) {
        trackIndex.clear();
        clipIndex.clear();
        int ti = 0;
        for (TimelineTrack track : tracks) {
            trackIndex.put(track.trackId(), new IndexedTrack(track, ti));
            int ci = 0;
            for (TimelineClip clip : track.clips()) {
                clipIndex.put(clip.getClipId(), new IndexedClip(clip, track.trackId(), ci));
                ci++;
            }
            ti++;
        }
    }

    private static List<PatchError> preflight(TimelinePatch patch, Map<String, IndexedTrack> trackIndex, Map<TimelineClipId, IndexedClip> clipIndex) {
        List<PatchError> errors = new ArrayList<>();
        Set<String> operationIds = new HashSet<>();
        Set<String> addedTrackIds = new HashSet<>();
        Set<TimelineClipId> addedClipIds = new HashSet<>();
        Set<String> removedEntityIds = new HashSet<>();

        for (TimelinePatchOperation op : patch.operations()) {
            // Check duplicate operation IDs
            if (!operationIds.add(op.operationId())) {
                errors.add(new PatchError(PatchErrorCode.TIMELINE_PATCH_CONFLICTING_OPERATIONS, "Duplicate operation ID: " + op.operationId(), op.operationId(), null));
                continue;
            }

            switch (op) {
                case TimelinePatchOperation.AddTrack add -> {
                    if (trackIndex.containsKey(add.track().trackId())) {
                        errors.add(new PatchError(PatchErrorCode.TIMELINE_PATCH_TARGET_ALREADY_EXISTS, "Track already exists: " + add.track().trackId(), add.operationId(), add.track().trackId()));
                    } else if (!addedTrackIds.add(add.track().trackId())) {
                        errors.add(new PatchError(PatchErrorCode.TIMELINE_PATCH_TARGET_ALREADY_EXISTS, "Track already added in patch: " + add.track().trackId(), add.operationId(), add.track().trackId()));
                    }
                    if (add.targetPosition() < 0 || add.targetPosition() > trackIndex.size() + addedTrackIds.size()) {
                        errors.add(new PatchError(PatchErrorCode.TIMELINE_PATCH_POSITION_INVALID, "Invalid position: " + add.targetPosition(), add.operationId(), null));
                    }
                }
                case TimelinePatchOperation.RemoveTrack remove -> {
                    if (!trackIndex.containsKey(remove.trackId()) || !removedEntityIds.add("track:" + remove.trackId())) {
                        errors.add(new PatchError(PatchErrorCode.TIMELINE_PATCH_TARGET_NOT_FOUND, "Track not found: " + remove.trackId(), remove.operationId(), remove.trackId()));
                    }
                }
                case TimelinePatchOperation.UpdateTrackProperty update -> {
                    if (!trackIndex.containsKey(update.trackId()) || removedEntityIds.contains("track:" + update.trackId())) {
                        errors.add(new PatchError(PatchErrorCode.TIMELINE_PATCH_TARGET_NOT_FOUND, "Track not found: " + update.trackId(), update.operationId(), update.trackId()));
                    }
                }
                case TimelinePatchOperation.ReorderTrack reorder -> {
                    if (!trackIndex.containsKey(reorder.trackId()) || removedEntityIds.contains("track:" + reorder.trackId())) {
                        errors.add(new PatchError(PatchErrorCode.TIMELINE_PATCH_TARGET_NOT_FOUND, "Track not found: " + reorder.trackId(), reorder.operationId(), reorder.trackId()));
                    }
                }
                case TimelinePatchOperation.AddClip add -> {
                    if (!trackIndex.containsKey(add.targetTrackId()) || removedEntityIds.contains("track:" + add.targetTrackId())) {
                        errors.add(new PatchError(PatchErrorCode.TIMELINE_PATCH_TARGET_NOT_FOUND, "Target track not found: " + add.targetTrackId(), add.operationId(), add.targetTrackId()));
                    }
                    if (clipIndex.containsKey(add.clip().getClipId()) || !addedClipIds.add(add.clip().getClipId())) {
                        errors.add(new PatchError(PatchErrorCode.TIMELINE_PATCH_TARGET_ALREADY_EXISTS, "Clip already exists: " + add.clip().getClipId(), add.operationId(), add.clip().getClipId().value()));
                    }
                }
                case TimelinePatchOperation.RemoveClip remove -> {
                    if (!clipIndex.containsKey(new TimelineClipId(remove.clipId())) || !removedEntityIds.add("clip:" + remove.clipId())) {
                        errors.add(new PatchError(PatchErrorCode.TIMELINE_PATCH_TARGET_NOT_FOUND, "Clip not found: " + remove.clipId(), remove.operationId(), remove.clipId()));
                    }
                }
                case TimelinePatchOperation.UpdateClipProperty update -> {
                    if (!clipIndex.containsKey(new TimelineClipId(update.clipId())) || removedEntityIds.contains("clip:" + update.clipId())) {
                        errors.add(new PatchError(PatchErrorCode.TIMELINE_PATCH_TARGET_NOT_FOUND, "Clip not found: " + update.clipId(), update.operationId(), update.clipId()));
                    }
                }
                case TimelinePatchOperation.MoveClip move -> {
                    if (!clipIndex.containsKey(new TimelineClipId(move.clipId())) || removedEntityIds.contains("clip:" + move.clipId())) {
                        errors.add(new PatchError(PatchErrorCode.TIMELINE_PATCH_TARGET_NOT_FOUND, "Clip not found: " + move.clipId(), move.operationId(), move.clipId()));
                    }
                    if (!trackIndex.containsKey(move.targetTrackId()) || removedEntityIds.contains("track:" + move.targetTrackId())) {
                        errors.add(new PatchError(PatchErrorCode.TIMELINE_PATCH_TARGET_NOT_FOUND, "Target track not found: " + move.targetTrackId(), move.operationId(), move.targetTrackId()));
                    }
                }
                case TimelinePatchOperation.ReorderClip reorder -> {
                    if (!clipIndex.containsKey(new TimelineClipId(reorder.clipId())) || removedEntityIds.contains("clip:" + reorder.clipId())) {
                        errors.add(new PatchError(PatchErrorCode.TIMELINE_PATCH_TARGET_NOT_FOUND, "Clip not found: " + reorder.clipId(), reorder.operationId(), reorder.clipId()));
                    }
                    if (!trackIndex.containsKey(reorder.trackId()) || removedEntityIds.contains("track:" + reorder.trackId())) {
                        errors.add(new PatchError(PatchErrorCode.TIMELINE_PATCH_TARGET_NOT_FOUND, "Track not found: " + reorder.trackId(), reorder.operationId(), reorder.trackId()));
                    }
                }
            }
        }
        return errors;
    }

    private record IndexedTrack(TimelineTrack track, int position) {}
    private record IndexedClip(TimelineClip clip, String trackId, int position) {}
}
