package com.example.platform.render.domain.timeline.patch;

import com.example.platform.shared.time.MediaTime;

import com.example.platform.render.domain.timeline.canonical.TimelineClip;
import com.example.platform.render.domain.timeline.canonical.TimelineDocument;
import com.example.platform.render.domain.timeline.canonical.TimelineMetadata;
import com.example.platform.render.domain.timeline.canonical.TimelineTrack;
import com.example.platform.render.domain.timeline.canonical.TrackType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TimelinePatchEngineTest {

    private static final String PRODUCT_ID = "prod-test";
    private static final String PATCH_ID = "patch-001";
    private static final String BASE_REV = "rev-base";
    private static final String BASE_DIGEST = "digest-base";

    @Test
    @DisplayName("Add track to empty document")
    void addTrack_emptyDocument() {
        TimelineDocument base = emptyDocument();
        TimelineTrack track = new TimelineTrack("track-1", "Video 1", TrackType.VIDEO, List.of());
        TimelinePatch patch = patch(opId("op1", "ADD_TRACK", track, 0));

        PatchApplicationResult result = TimelinePatchEngine.apply(base, patch);

        assertTrue(result.isSuccess());
        TimelineDocument resultDoc = ((PatchApplicationResult.Success) result).document();
        assertEquals(1, resultDoc.getTracks().size());
        assertEquals("track-1", resultDoc.getTracks().get(0).trackId());
    }

    @Test
    @DisplayName("Remove track")
    void removeTrack() {
        TimelineDocument base = documentWithTrack("track-1");
        TimelinePatch patch = patch(opId("op1", "REMOVE_TRACK", "track-1"));

        PatchApplicationResult result = TimelinePatchEngine.apply(base, patch);

        assertTrue(result.isSuccess());
        assertEquals(0, ((PatchApplicationResult.Success) result).document().getTracks().size());
    }

    @Test
    @DisplayName("Update track property")
    void updateTrackProperty() {
        TimelineDocument base = documentWithTrack("track-1");
        TimelinePatch patch = patch(opId("op1", "UPDATE_TRACK_PROPERTY", "track-1", "name", "Video 1", "Video 1 Updated"));

        PatchApplicationResult result = TimelinePatchEngine.apply(base, patch);

        assertTrue(result.isSuccess());
        assertEquals("Video 1 Updated", ((PatchApplicationResult.Success) result).document().getTracks().get(0).name());
    }

    @Test
    @DisplayName("Reorder track")
    void reorderTrack() {
        TimelineDocument base = new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(
                        new TimelineTrack("track-1", "V1", TrackType.VIDEO, List.of()),
                        new TimelineTrack("track-2", "V2", TrackType.VIDEO, List.of()),
                        new TimelineTrack("track-3", "V3", TrackType.VIDEO, List.of())),
                TimelineMetadata.empty());
        TimelinePatch patch = patch(opId("op1", "REORDER_TRACK", "track-3", 0));

        PatchApplicationResult result = TimelinePatchEngine.apply(base, patch);

        assertTrue(result.isSuccess());
        List<TimelineTrack> tracks = ((PatchApplicationResult.Success) result).document().getTracks();
        assertEquals("track-3", tracks.get(0).trackId());
        assertEquals("track-1", tracks.get(1).trackId());
        assertEquals("track-2", tracks.get(2).trackId());
    }

    @Test
    @DisplayName("Add clip to track")
    void addClip() {
        TimelineDocument base = documentWithTrack("track-1");
        TimelineClip clip = new TimelineClip("clip-1", "asset-1", null, null, null, MediaTime.ofMicros((0) * 1000L), MediaTime.ofMicros((1000) * 1000L), MediaTime.ZERO, MediaTime.ZERO, "MEDIA_STREAM");
        TimelinePatch patch = patch(opId("op1", "ADD_CLIP", "track-1", clip, 0));

        PatchApplicationResult result = TimelinePatchEngine.apply(base, patch);

        assertTrue(result.isSuccess());
        List<TimelineTrack> tracks = ((PatchApplicationResult.Success) result).document().getTracks();
        assertEquals(1, tracks.get(0).clips().size());
        assertEquals("clip-1", tracks.get(0).clips().get(0).getClipId());
    }

    @Test
    @DisplayName("Remove clip from track")
    void removeClip() {
        TimelineClip clip = new TimelineClip("clip-1", "asset-1", null, null, null,
                MediaTime.ofMicros((0) * 1000L), MediaTime.ofMicros((1000) * 1000L), MediaTime.ZERO, MediaTime.ZERO, "MEDIA_STREAM");
        TimelineDocument base = documentWithTrackAndClip("track-1", clip);
        TimelinePatch patch = patch(opId("op1", "REMOVE_CLIP", "clip-1", "track-1"));

        PatchApplicationResult result = TimelinePatchEngine.apply(base, patch);

        assertTrue(result.isSuccess());
        assertEquals(0, ((PatchApplicationResult.Success) result).document().getTracks().get(0).clips().size());
    }

    @Test
    @DisplayName("Update clip property")
    void updateClipProperty() {
        TimelineClip clip = new TimelineClip("clip-1", "asset-1", null, null, null,
                MediaTime.ofMicros((0) * 1000L), MediaTime.ofMicros((1000) * 1000L), MediaTime.ZERO, MediaTime.ZERO, "MEDIA_STREAM");
        TimelineDocument base = documentWithTrackAndClip("track-1", clip);
        TimelinePatch patch = patch(opId("op1", "UPDATE_CLIP_PROPERTY", "clip-1", "mediaAssetId", "asset-1", "asset-2"));

        PatchApplicationResult result = TimelinePatchEngine.apply(base, patch);

        assertTrue(result.isSuccess());
        assertEquals("asset-2", ((PatchApplicationResult.Success) result).document().getTracks().get(0).clips().get(0).getMediaAssetId());
    }

    @Test
    @DisplayName("Move clip across tracks")
    void moveClip() {
        TimelineClip clip = new TimelineClip("clip-1", "asset-1", null, null, null,
                MediaTime.ofMicros((0) * 1000L), MediaTime.ofMicros((1000) * 1000L), MediaTime.ZERO, MediaTime.ZERO, "MEDIA_STREAM");
        TimelineDocument base = new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(
                        new TimelineTrack("track-1", "V1", TrackType.VIDEO, List.of(clip)),
                        new TimelineTrack("track-2", "V2", TrackType.AUDIO, List.of())),
                TimelineMetadata.empty());
        TimelinePatch patch = patch(opId("op1", "MOVE_CLIP", "clip-1", "track-1", "track-2", 0));

        PatchApplicationResult result = TimelinePatchEngine.apply(base, patch);

        assertTrue(result.isSuccess());
        List<TimelineTrack> tracks = ((PatchApplicationResult.Success) result).document().getTracks();
        assertEquals(0, tracks.get(0).clips().size());
        assertEquals(1, tracks.get(1).clips().size());
        assertEquals("clip-1", tracks.get(1).clips().get(0).getClipId());
    }

    @Test
    @DisplayName("Reorder clip in same track")
    void reorderClip() {
        TimelineClip clip1 = new TimelineClip("clip-1", "asset-1", null, null, null,
                MediaTime.ofMicros((0) * 1000L), MediaTime.ofMicros((1000) * 1000L), MediaTime.ZERO, MediaTime.ZERO, "MEDIA_STREAM");
        TimelineClip clip2 = new TimelineClip("clip-2", "asset-2", null, null, null,
                MediaTime.ofMicros((1000) * 1000L), MediaTime.ofMicros((2000) * 1000L), MediaTime.ZERO, MediaTime.ZERO, "MEDIA_STREAM");
        TimelineClip clip3 = new TimelineClip("clip-3", "asset-3", null, null, null,
                MediaTime.ofMicros((2000) * 1000L), MediaTime.ofMicros((3000) * 1000L), MediaTime.ZERO, MediaTime.ZERO, "MEDIA_STREAM");
        TimelineDocument base = new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(new TimelineTrack("track-1", "V1", TrackType.VIDEO, List.of(clip1, clip2, clip3))),
                TimelineMetadata.empty());
        TimelinePatch patch = patch(opId("op1", "REORDER_CLIP", "clip-3", "track-1", 0));

        PatchApplicationResult result = TimelinePatchEngine.apply(base, patch);

        assertTrue(result.isSuccess());
        List<TimelineClip> clips = ((PatchApplicationResult.Success) result).document().getTracks().get(0).clips();
        assertEquals("clip-3", clips.get(0).getClipId());
        assertEquals("clip-1", clips.get(1).getClipId());
        assertEquals("clip-2", clips.get(2).getClipId());
    }

    @Test
    @DisplayName("Duplicate operation IDs rejected")
    void duplicateOperationIds() {
        TimelineDocument base = emptyDocument();
        TimelineTrack track = new TimelineTrack("track-1", "V1", TrackType.VIDEO, List.of());
        TimelinePatch patch = new TimelinePatch(
                "1.0", PATCH_ID, PRODUCT_ID, BASE_REV, BASE_DIGEST, BASE_REV,
                TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(
                        new TimelinePatchOperation.AddTrack("op1", track, 0),
                        new TimelinePatchOperation.AddTrack("op1", track, 1)),
                null, null);

        PatchApplicationResult result = TimelinePatchEngine.apply(base, patch);

        assertTrue(result.isFailure());
        assertEquals(PatchErrorCode.TIMELINE_PATCH_CONFLICTING_OPERATIONS,
                ((PatchApplicationResult.Failure) result).errors().get(0).code());
    }

    @Test
    @DisplayName("Add duplicate track ID rejected")
    void addDuplicateTrackId() {
        TimelineDocument base = documentWithTrack("track-1");
        TimelineTrack track = new TimelineTrack("track-1", "V1", TrackType.VIDEO, List.of());
        TimelinePatch patch = patch(opId("op1", "ADD_TRACK", track, 0));

        PatchApplicationResult result = TimelinePatchEngine.apply(base, patch);

        assertTrue(result.isFailure());
        assertEquals(PatchErrorCode.TIMELINE_PATCH_TARGET_ALREADY_EXISTS,
                ((PatchApplicationResult.Failure) result).errors().get(0).code());
    }

    @Test
    @DisplayName("Remove non-existent track rejected")
    void removeNonExistentTrack() {
        TimelineDocument base = emptyDocument();
        TimelinePatch patch = patch(opId("op1", "REMOVE_TRACK", "track-nonexistent"));

        PatchApplicationResult result = TimelinePatchEngine.apply(base, patch);

        assertTrue(result.isFailure());
        assertEquals(PatchErrorCode.TIMELINE_PATCH_TARGET_NOT_FOUND,
                ((PatchApplicationResult.Failure) result).errors().get(0).code());
    }

    @Test
    @DisplayName("Invalid position rejected")
    void invalidPosition() {
        TimelineDocument base = emptyDocument();
        TimelineTrack track = new TimelineTrack("track-1", "V1", TrackType.VIDEO, List.of());
        TimelinePatch patch = patch(opId("op1", "ADD_TRACK", track, -1));

        PatchApplicationResult result = TimelinePatchEngine.apply(base, patch);

        assertTrue(result.isFailure());
        assertEquals(PatchErrorCode.TIMELINE_PATCH_POSITION_INVALID,
                ((PatchApplicationResult.Failure) result).errors().get(0).code());
    }

    @Test
    @DisplayName("Precondition mismatch rejected")
    void preconditionMismatch() {
        TimelineDocument base = documentWithTrack("track-1");
        TimelinePatch patch = patch(opId("op1", "UPDATE_TRACK_PROPERTY", "track-1", "name", "Wrong Name", "New Name"));

        PatchApplicationResult result = TimelinePatchEngine.apply(base, patch);

        assertTrue(result.isFailure());
    }

    @Test
    @DisplayName("Deterministic: same base + patch = same result")
    void deterministic() {
        TimelineDocument base = documentWithTrack("track-1");
        TimelineTrack track = new TimelineTrack("track-2", "V2", TrackType.VIDEO, List.of());
        TimelinePatch patch = patch(opId("op1", "ADD_TRACK", track, 1));

        PatchApplicationResult result1 = TimelinePatchEngine.apply(base, patch);
        PatchApplicationResult result2 = TimelinePatchEngine.apply(base, patch);

        assertTrue(result1.isSuccess());
        assertTrue(result2.isSuccess());
        assertEquals(
                ((PatchApplicationResult.Success) result1).document().getTracks().size(),
                ((PatchApplicationResult.Success) result2).document().getTracks().size());
    }

    @Test
    @DisplayName("No mutation of input document")
    void noMutation() {
        TimelineDocument base = documentWithTrack("track-1");
        TimelineTrack track = new TimelineTrack("track-2", "V2", TrackType.VIDEO, List.of());
        TimelinePatch patch = patch(opId("op1", "ADD_TRACK", track, 1));

        TimelinePatchEngine.apply(base, patch);

        assertEquals(1, base.getTracks().size());
        assertEquals("track-1", base.getTracks().get(0).trackId());
    }

    // ==================== Helpers ====================

    private TimelineDocument emptyDocument() {
        return new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION, List.of(), TimelineMetadata.empty());
    }

    private TimelineDocument documentWithTrack(String trackId) {
        return new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(new TimelineTrack(trackId, "Video 1", TrackType.VIDEO, List.of())),
                TimelineMetadata.empty());
    }

    private TimelineDocument documentWithTrackAndClip(String trackId, TimelineClip clip) {
        return new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(new TimelineTrack(trackId, "Video 1", TrackType.VIDEO, List.of(clip))),
                TimelineMetadata.empty());
    }

    private TimelinePatch patch(TimelinePatchOperation op) {
        return new TimelinePatch(
                "1.0", PATCH_ID, PRODUCT_ID, BASE_REV, BASE_DIGEST, BASE_REV,
                TimelineDocument.CURRENT_SCHEMA_VERSION, List.of(op), null, null);
    }

    private TimelinePatchOperation opId(String id, String kind, TimelineTrack track, int pos) {
        return switch (kind) {
            case "ADD_TRACK" -> new TimelinePatchOperation.AddTrack(id, track, pos);
            default -> throw new IllegalArgumentException();
        };
    }

    private TimelinePatchOperation opId(String id, String kind, String trackId) {
        return switch (kind) {
            case "REMOVE_TRACK" -> new TimelinePatchOperation.RemoveTrack(id, trackId);
            default -> throw new IllegalArgumentException();
        };
    }

    private TimelinePatchOperation opId(String id, String kind, String trackId, int pos) {
        return switch (kind) {
            case "REORDER_TRACK" -> new TimelinePatchOperation.ReorderTrack(id, trackId, pos);
            default -> throw new IllegalArgumentException();
        };
    }

    private TimelinePatchOperation opId(String id, String kind, String entityId, String property, String before, String after) {
        return switch (kind) {
            case "UPDATE_TRACK_PROPERTY" -> new TimelinePatchOperation.UpdateTrackProperty(id, entityId, property, before, after);
            case "UPDATE_CLIP_PROPERTY" -> new TimelinePatchOperation.UpdateClipProperty(id, entityId, property, before, after);
            default -> throw new IllegalArgumentException();
        };
    }

    private TimelinePatchOperation opId(String id, String kind, String trackId, TimelineClip clip, int pos) {
        return new TimelinePatchOperation.AddClip(id, trackId, clip, pos);
    }

    private TimelinePatchOperation opId(String id, String kind, String clipId, String expectedTrackId) {
        return new TimelinePatchOperation.RemoveClip(id, clipId, expectedTrackId);
    }

    private TimelinePatchOperation opId(String id, String kind, String clipId, String sourceTrackId, String targetTrackId, int pos) {
        return new TimelinePatchOperation.MoveClip(id, clipId, sourceTrackId, targetTrackId, pos);
    }

    private TimelinePatchOperation opId(String id, String kind, String clipId, String trackId, int pos) {
        return new TimelinePatchOperation.ReorderClip(id, clipId, trackId, pos);
    }
}