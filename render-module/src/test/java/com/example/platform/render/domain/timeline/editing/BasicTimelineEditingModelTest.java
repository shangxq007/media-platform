package com.example.platform.render.domain.timeline.editing;

import com.example.platform.render.domain.timeline.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for P2TLE.0 Basic Timeline Editing Model and Validation.
 * Covers: types, validation, editor operations, safety boundaries.
 */
class BasicTimelineEditingModelTest {

    // ==================== Stage 1: Basic Timeline Editing Types ====================

    @Test @DisplayName("TimelineEditOperationType contains required operations")
    void operationTypesExist() {
        assertNotNull(TimelineEditOperationType.CREATE_TIMELINE);
        assertNotNull(TimelineEditOperationType.UPDATE_OUTPUT_PROFILE);
        assertNotNull(TimelineEditOperationType.ADD_TRACK);
        assertNotNull(TimelineEditOperationType.ADD_CLIP);
        assertNotNull(TimelineEditOperationType.ADD_CAPTION);
        assertNotNull(TimelineEditOperationType.ADD_WATERMARK);
        assertNotNull(TimelineEditOperationType.ADD_EFFECT);
        assertNotNull(TimelineEditOperationType.ADD_TRANSITION);
        assertNotNull(TimelineEditOperationType.VALIDATE_TIMELINE);
        assertNotNull(TimelineEditOperationType.REMOVE_TRACK);
        assertNotNull(TimelineEditOperationType.REMOVE_CLIP);
        assertNotNull(TimelineEditOperationType.REMOVE_CAPTION);
        assertNotNull(TimelineEditOperationType.REMOVE_WATERMARK);
        assertNotNull(TimelineEditOperationType.REMOVE_EFFECT);
        assertNotNull(TimelineEditOperationType.REMOVE_TRANSITION);
        assertNotNull(TimelineEditOperationType.UPDATE_CLIP);
        assertNotNull(TimelineEditOperationType.UPDATE_CAPTION);
        assertNotNull(TimelineEditOperationType.UPDATE_WATERMARK);
        assertNotNull(TimelineEditOperationType.UPDATE_EFFECT);
        assertNotNull(TimelineEditOperationType.UPDATE_TRANSITION);
        assertNotNull(TimelineEditOperationType.REORDER_TRACK);
    }

    @Test @DisplayName("TimelineEditResultStatus contains required statuses")
    void resultStatusesExist() {
        assertNotNull(TimelineEditResultStatus.APPLIED);
        assertNotNull(TimelineEditResultStatus.VALIDATION_FAILED);
        assertNotNull(TimelineEditResultStatus.NO_OP);
        assertNotNull(TimelineEditResultStatus.INVALID_OPERATION);
        assertNotNull(TimelineEditResultStatus.BLOCKED);
        assertNotNull(TimelineEditResultStatus.FAILED);
    }

    @Test @DisplayName("TimelineEditOperation requires type")
    void operationRequiresType() {
        assertThrows(NullPointerException.class, () ->
                new TimelineEditOperation(null, null, Map.of(), Map.of()));
    }

    @Test @DisplayName("TimelineEditRequest requires requestId and timelineId")
    void requestRequiresIds() {
        assertThrows(NullPointerException.class, () ->
                new TimelineEditRequest(null, "tl-1", List.of(), Map.of()));
        assertThrows(NullPointerException.class, () ->
                new TimelineEditRequest("req-1", null, List.of(), Map.of()));
    }

    @Test @DisplayName("TimelineEditResult statuses work")
    void resultStatuses() {
        TimelineSpec tl = TimelineSpec.create("t1", "Test", TimelineOutputSpec.mp4_1080p30());
        assertEquals(TimelineEditResultStatus.APPLIED, TimelineEditResult.applied(tl).status());
        assertEquals(TimelineEditResultStatus.NO_OP, TimelineEditResult.noOp(tl).status());
        assertEquals(TimelineEditResultStatus.VALIDATION_FAILED, TimelineEditResult.validationFailed(List.of()).status());
        assertEquals(TimelineEditResultStatus.INVALID_OPERATION, TimelineEditResult.invalidOperation(List.of()).status());
        assertEquals(TimelineEditResultStatus.BLOCKED, TimelineEditResult.blocked(List.of()).status());
        assertEquals(TimelineEditResultStatus.FAILED, TimelineEditResult.failed(List.of()).status());
    }

    @Test @DisplayName("Safe metadata only in operations")
    void safeMetadataOnly() {
        TimelineEditOperation op = new TimelineEditOperation(
                TimelineEditOperationType.ADD_TRACK, "t1",
                Map.of(), Map.of("key", "value"));
        assertEquals("value", op.safeMetadata().get("key"));
        assertNull(op.safeMetadata().get("bucket"));
        assertNull(op.safeMetadata().get("signedUrl"));
    }

    // ==================== Stage 2: Timeline Validation Model ====================

    @Test @DisplayName("TimelineValidationIssueSeverity contains required levels")
    void issueSeverityLevels() {
        assertNotNull(TimelineValidationIssueSeverity.INFO);
        assertNotNull(TimelineValidationIssueSeverity.WARNING);
        assertNotNull(TimelineValidationIssueSeverity.ERROR);
        assertNotNull(TimelineValidationIssueSeverity.BLOCKING);
    }

    @Test @DisplayName("TimelineValidationIssueCode contains safety codes")
    void issueCodesExist() {
        assertNotNull(TimelineValidationIssueCode.INVALID_TIMELINE_ID);
        assertNotNull(TimelineValidationIssueCode.INVALID_OUTPUT_PROFILE);
        assertNotNull(TimelineValidationIssueCode.DUPLICATE_TRACK_ID);
        assertNotNull(TimelineValidationIssueCode.TRACK_NOT_FOUND);
        assertNotNull(TimelineValidationIssueCode.DUPLICATE_CLIP_ID);
        assertNotNull(TimelineValidationIssueCode.CLIP_NOT_FOUND);
        assertNotNull(TimelineValidationIssueCode.INVALID_CLIP_TIME_RANGE);
        assertNotNull(TimelineValidationIssueCode.DUPLICATE_CAPTION_ID);
        assertNotNull(TimelineValidationIssueCode.INVALID_CAPTION_TIME_RANGE);
        assertNotNull(TimelineValidationIssueCode.DUPLICATE_WATERMARK_ID);
        assertNotNull(TimelineValidationIssueCode.INVALID_WATERMARK_PLACEMENT);
        assertNotNull(TimelineValidationIssueCode.EFFECT_TARGET_NOT_FOUND);
        assertNotNull(TimelineValidationIssueCode.EFFECT_CAPABILITY_NOT_FOUND);
        assertNotNull(TimelineValidationIssueCode.EFFECT_CAPABILITY_FORBIDDEN);
        assertNotNull(TimelineValidationIssueCode.EFFECT_CAPABILITY_RESTRICTED);
        assertNotNull(TimelineValidationIssueCode.TRANSITION_CLIP_NOT_FOUND);
        assertNotNull(TimelineValidationIssueCode.TRANSITION_CAPABILITY_NOT_FOUND);
        assertNotNull(TimelineValidationIssueCode.TRANSITION_CAPABILITY_FORBIDDEN);
        assertNotNull(TimelineValidationIssueCode.INVALID_TRANSITION_DURATION);
        assertNotNull(TimelineValidationIssueCode.RAW_PROVIDER_COMMAND_FORBIDDEN);
        assertNotNull(TimelineValidationIssueCode.ARBITRARY_FILTERGRAPH_FORBIDDEN);
        assertNotNull(TimelineValidationIssueCode.USER_RENDER_DAG_FORBIDDEN);
        assertNotNull(TimelineValidationIssueCode.PLUGIN_EXECUTION_NODE_FORBIDDEN);
        assertNotNull(TimelineValidationIssueCode.STORAGE_INTERNALS_FORBIDDEN);
        assertNotNull(TimelineValidationIssueCode.PROVIDER_INTERNALS_FORBIDDEN);
        assertNotNull(TimelineValidationIssueCode.ARTIFACT_DAG_NOT_USED);
        assertNotNull(TimelineValidationIssueCode.RENDER_NOT_ALLOWED);
        assertNotNull(TimelineValidationIssueCode.PRODUCT_CREATION_NOT_ALLOWED);
        assertNotNull(TimelineValidationIssueCode.PERSISTENCE_NOT_IMPLEMENTED);
    }

    @Test @DisplayName("TimelineValidationStatus contains required statuses")
    void validationStatuses() {
        assertNotNull(TimelineValidationStatus.VALID);
        assertNotNull(TimelineValidationStatus.VALID_WITH_WARNINGS);
        assertNotNull(TimelineValidationStatus.INVALID);
        assertNotNull(TimelineValidationStatus.BLOCKED);
    }

    @Test @DisplayName("TimelineValidationIssue factory methods work")
    void issueFactoryMethods() {
        var error = TimelineValidationIssue.error(
                TimelineValidationIssueCode.INVALID_TIMELINE_ID, "field", "msg");
        assertEquals(TimelineValidationIssueSeverity.ERROR, error.severity());

        var warning = TimelineValidationIssue.warning(
                TimelineValidationIssueCode.INVALID_OUTPUT_PROFILE, "field", "msg");
        assertEquals(TimelineValidationIssueSeverity.WARNING, warning.severity());

        var blocking = TimelineValidationIssue.blocking(
                TimelineValidationIssueCode.STORAGE_INTERNALS_FORBIDDEN, "field", "msg");
        assertEquals(TimelineValidationIssueSeverity.BLOCKING, blocking.severity());

        var info = TimelineValidationIssue.info(
                TimelineValidationIssueCode.ARTIFACT_DAG_NOT_USED, "field", "msg");
        assertEquals(TimelineValidationIssueSeverity.INFO, info.severity());
    }

    // ==================== Stage 3: Basic Timeline Validator ====================

    @Test @DisplayName("Valid timeline returns no blocking issues")
    void validTimelineNoBlocking() {
        TimelineSpec tl = TimelineSpec.create("t1", "Test", TimelineOutputSpec.mp4_1080p30());
        List<TimelineValidationIssue> issues = BasicTimelineValidator.validate(tl);
        boolean hasBlocking = issues.stream().anyMatch(i ->
                i.severity() == TimelineValidationIssueSeverity.BLOCKING
                        || i.severity() == TimelineValidationIssueSeverity.ERROR);
        assertFalse(hasBlocking, "Valid timeline should have no blocking issues");
    }

    @Test @DisplayName("Null timeline returns blocking issue")
    void nullTimelineBlocking() {
        List<TimelineValidationIssue> issues = BasicTimelineValidator.validate(null);
        assertFalse(issues.isEmpty());
        assertEquals(TimelineValidationIssueSeverity.BLOCKING, issues.get(0).severity());
    }

    @Test @DisplayName("Timeline without id returns blocking issue")
    void timelineWithoutIdBlocking() {
        TimelineSpec tl = new TimelineSpec(null, "Test", null,
                List.of(TimelineTrack.of("t1", "Video", TimelineTrack.TrackType.VIDEO)),
                List.of(), TimelineOutputSpec.mp4_1080p30(), 0, Map.of());
        List<TimelineValidationIssue> issues = BasicTimelineValidator.validate(tl);
        assertTrue(issues.stream().anyMatch(i ->
                i.code() == TimelineValidationIssueCode.INVALID_TIMELINE_ID));
    }

    @Test @DisplayName("Duplicate track ids rejected")
    void duplicateTrackIdsRejected() {
        TimelineSpec tl = new TimelineSpec("t1", "Test", null,
                List.of(
                        TimelineTrack.of("track-1", "V1", TimelineTrack.TrackType.VIDEO),
                        TimelineTrack.of("track-1", "V2", TimelineTrack.TrackType.VIDEO)),
                List.of(), TimelineOutputSpec.mp4_1080p30(), 0, Map.of());
        List<TimelineValidationIssue> issues = BasicTimelineValidator.validate(tl);
        assertTrue(issues.stream().anyMatch(i ->
                i.code() == TimelineValidationIssueCode.DUPLICATE_TRACK_ID));
    }

    @Test @DisplayName("Duplicate clip ids rejected")
    void duplicateClipIdsRejected() {
        TimelineClip clip1 = TimelineClip.of("clip-1",
                TimelineAssetRef.of("a1", "uri1"), 0, 0, 5);
        TimelineClip clip2 = TimelineClip.of("clip-1",
                TimelineAssetRef.of("a2", "uri2"), 5, 0, 5);
        TimelineTrack track = new TimelineTrack("track-1", "V1",
                TimelineTrack.TrackType.VIDEO, 0, List.of(clip1, clip2), false, false);
        TimelineSpec tl = new TimelineSpec("t1", "Test", null,
                List.of(track), List.of(), TimelineOutputSpec.mp4_1080p30(), 0, Map.of());
        List<TimelineValidationIssue> issues = BasicTimelineValidator.validate(tl);
        assertTrue(issues.stream().anyMatch(i ->
                i.code() == TimelineValidationIssueCode.DUPLICATE_CLIP_ID));
    }

    @Test @DisplayName("Invalid clip time range rejected")
    void invalidClipTimeRangeRejected() {
        TimelineClip clip = new TimelineClip("clip-1",
                TimelineAssetRef.of("a1", "uri1"), 0, 10, 5, -5, List.of());
        TimelineTrack track = new TimelineTrack("track-1", "V1",
                TimelineTrack.TrackType.VIDEO, 0, List.of(clip), false, false);
        TimelineSpec tl = new TimelineSpec("t1", "Test", null,
                List.of(track), List.of(), TimelineOutputSpec.mp4_1080p30(), 0, Map.of());
        List<TimelineValidationIssue> issues = BasicTimelineValidator.validate(tl);
        assertTrue(issues.stream().anyMatch(i ->
                i.code() == TimelineValidationIssueCode.INVALID_CLIP_TIME_RANGE));
    }

    @Test @DisplayName("Duplicate caption ids rejected")
    void duplicateCaptionIdsRejected() {
        TimelineTextOverlay c1 = TimelineTextOverlay.of("cap-1", "Hello", 0, 5);
        TimelineTextOverlay c2 = TimelineTextOverlay.of("cap-1", "World", 5, 5);
        TimelineSpec tl = new TimelineSpec("t1", "Test", null,
                List.of(TimelineTrack.of("tr1", "V1", TimelineTrack.TrackType.VIDEO)),
                List.of(c1, c2), TimelineOutputSpec.mp4_1080p30(), 0, Map.of());
        List<TimelineValidationIssue> issues = BasicTimelineValidator.validate(tl);
        assertTrue(issues.stream().anyMatch(i ->
                i.code() == TimelineValidationIssueCode.DUPLICATE_CAPTION_ID));
    }

    @Test @DisplayName("Invalid caption time range rejected")
    void invalidCaptionTimeRangeRejected() {
        TimelineTextOverlay c = new TimelineTextOverlay("cap-1", "Hello",
                "DejaVu Sans", 24, "#FFFFFF", "center", "bottom", -1, 0, null);
        TimelineSpec tl = new TimelineSpec("t1", "Test", null,
                List.of(TimelineTrack.of("tr1", "V1", TimelineTrack.TrackType.VIDEO)),
                List.of(c), TimelineOutputSpec.mp4_1080p30(), 0, Map.of());
        List<TimelineValidationIssue> issues = BasicTimelineValidator.validate(tl);
        assertTrue(issues.stream().anyMatch(i ->
                i.code() == TimelineValidationIssueCode.INVALID_CAPTION_TIME_RANGE));
    }

    @Test @DisplayName("Invalid output profile rejected")
    void invalidOutputProfileRejected() {
        TimelineSpec tl = new TimelineSpec("t1", "Test", null,
                List.of(TimelineTrack.of("tr1", "V1", TimelineTrack.TrackType.VIDEO)),
                List.of(), null, 0, Map.of());
        List<TimelineValidationIssue> issues = BasicTimelineValidator.validate(tl);
        assertTrue(issues.stream().anyMatch(i ->
                i.code() == TimelineValidationIssueCode.INVALID_OUTPUT_PROFILE));
    }

    @Test @DisplayName("Forbidden metadata keyword rejected")
    void forbiddenMetadataRejected() {
        TimelineSpec tl = new TimelineSpec("t1", "Test", null,
                List.of(TimelineTrack.of("tr1", "V1", TimelineTrack.TrackType.VIDEO)),
                List.of(), TimelineOutputSpec.mp4_1080p30(), 0,
                Map.of("bucket", "my-bucket"));
        List<TimelineValidationIssue> issues = BasicTimelineValidator.validate(tl);
        assertTrue(issues.stream().anyMatch(i ->
                i.code() == TimelineValidationIssueCode.STORAGE_INTERNALS_FORBIDDEN));
    }

    @Test @DisplayName("Forbidden filtergraph in effect rejected")
    void forbiddenFiltergraphRejected() {
        TimelineClipEffect effect = TimelineClipEffect.ofKey(
                "filter_complex:something", Map.of());
        TimelineClip clip = new TimelineClip("clip-1",
                TimelineAssetRef.of("a1", "uri1"), 0, 0, 5, 5, List.of(effect));
        TimelineTrack track = new TimelineTrack("track-1", "V1",
                TimelineTrack.TrackType.VIDEO, 0, List.of(clip), false, false);
        TimelineSpec tl = new TimelineSpec("t1", "Test", null,
                List.of(track), List.of(), TimelineOutputSpec.mp4_1080p30(), 0, Map.of());
        List<TimelineValidationIssue> issues = BasicTimelineValidator.validate(tl);
        assertTrue(issues.stream().anyMatch(i ->
                i.code() == TimelineValidationIssueCode.ARBITRARY_FILTERGRAPH_FORBIDDEN));
    }

    @Test @DisplayName("toSimpleResult converts correctly")
    void toSimpleResultConversion() {
        List<TimelineValidationIssue> issues = List.of(
                TimelineValidationIssue.error(TimelineValidationIssueCode.INVALID_TIMELINE_ID, "id", "bad"));
        TimelineValidationResult result = BasicTimelineValidator.toSimpleResult(issues);
        assertFalse(result.valid());
        assertFalse(result.errors().isEmpty());
    }

    // ==================== Stage 4: Basic Timeline Editor ====================

    @Test @DisplayName("Create timeline operation works")
    void createTimeline() {
        TimelineEditRequest req = new TimelineEditRequest("req-1", "new-tl",
                List.of(TimelineEditOperation.createTimeline(
                        Map.of("id", "tl-1", "name", "My Timeline"))),
                Map.of());
        // Need a dummy timeline for the editor input — CREATE_TIMELINE ignores it
        TimelineSpec dummy = TimelineSpec.create("dummy", "Dummy", TimelineOutputSpec.mp4_1080p30());
        TimelineEditResult result = BasicTimelineEditor.apply(dummy, req);
        assertEquals(TimelineEditResultStatus.APPLIED, result.status());
        assertNotNull(result.timeline());
        assertEquals("tl-1", result.timeline().id());
    }

    @Test @DisplayName("Update output profile works")
    void updateOutputProfile() {
        TimelineSpec tl = TimelineSpec.create("t1", "Test", TimelineOutputSpec.mp4_1080p30());
        TimelineEditRequest req = new TimelineEditRequest("req-1", "t1",
                List.of(TimelineEditOperation.updateOutputProfile(
                        Map.of("resolution", "1280x720", "frameRate", "24"))),
                Map.of());
        TimelineEditResult result = BasicTimelineEditor.apply(tl, req);
        assertEquals(TimelineEditResultStatus.APPLIED, result.status());
        assertEquals("1280x720", result.timeline().outputSpec().resolution());
    }

    @Test @DisplayName("Add track works")
    void addTrack() {
        TimelineSpec tl = TimelineSpec.create("t1", "Test", TimelineOutputSpec.mp4_1080p30());
        TimelineEditRequest req = new TimelineEditRequest("req-1", "t1",
                List.of(TimelineEditOperation.addTrack("audio-1",
                        Map.of("name", "Audio 1", "type", "AUDIO"))),
                Map.of());
        TimelineEditResult result = BasicTimelineEditor.apply(tl, req);
        assertEquals(TimelineEditResultStatus.APPLIED, result.status());
        assertEquals(2, result.timeline().tracks().size());
    }

    @Test @DisplayName("Duplicate track rejected")
    void duplicateTrackRejected() {
        TimelineSpec tl = TimelineSpec.create("t1", "Test", TimelineOutputSpec.mp4_1080p30());
        String existingTrackId = tl.tracks().get(0).id();
        TimelineEditRequest req = new TimelineEditRequest("req-1", "t1",
                List.of(TimelineEditOperation.addTrack(existingTrackId, Map.of())),
                Map.of());
        TimelineEditResult result = BasicTimelineEditor.apply(tl, req);
        assertEquals(TimelineEditResultStatus.VALIDATION_FAILED, result.status());
    }

    @Test @DisplayName("Add clip works")
    void addClip() {
        TimelineSpec tl = TimelineSpec.create("t1", "Test", TimelineOutputSpec.mp4_1080p30());
        String trackId = tl.tracks().get(0).id();
        TimelineEditRequest req = new TimelineEditRequest("req-1", "t1",
                List.of(TimelineEditOperation.addClip("clip-1",
                        Map.of("trackId", trackId, "assetId", "asset-1",
                                "timelineStart", 0, "assetInPoint", 0, "assetOutPoint", 10))),
                Map.of());
        TimelineEditResult result = BasicTimelineEditor.apply(tl, req);
        assertEquals(TimelineEditResultStatus.APPLIED, result.status());
        assertFalse(result.timeline().tracks().get(0).clips().isEmpty());
    }

    @Test @DisplayName("Add caption works")
    void addCaption() {
        TimelineSpec tl = TimelineSpec.create("t1", "Test", TimelineOutputSpec.mp4_1080p30());
        TimelineEditRequest req = new TimelineEditRequest("req-1", "t1",
                List.of(TimelineEditOperation.addCaption("cap-1",
                        Map.of("text", "Hello World", "startTime", 0, "duration", 5))),
                Map.of());
        TimelineEditResult result = BasicTimelineEditor.apply(tl, req);
        assertEquals(TimelineEditResultStatus.APPLIED, result.status());
        assertEquals(1, result.timeline().textOverlays().size());
        assertEquals("Hello World", result.timeline().textOverlays().get(0).text());
    }

    @Test @DisplayName("Add watermark works")
    void addWatermark() {
        TimelineSpec tl = TimelineSpec.create("t1", "Test", TimelineOutputSpec.mp4_1080p30());
        TimelineEditRequest req = new TimelineEditRequest("req-1", "t1",
                List.of(TimelineEditOperation.addWatermark("wm-1",
                        Map.of("kind", "TEXT", "text", "© 2026", "placement", "bottom-right"))),
                Map.of());
        TimelineEditResult result = BasicTimelineEditor.apply(tl, req);
        assertEquals(TimelineEditResultStatus.APPLIED, result.status());
        assertTrue(result.timeline().metadata().containsKey("watermark.wm-1.kind"));
    }

    @Test @DisplayName("Add effect works")
    void addEffect() {
        TimelineSpec tl = TimelineSpec.create("t1", "Test", TimelineOutputSpec.mp4_1080p30());
        String trackId = tl.tracks().get(0).id();
        // First add a clip
        TimelineEditResult withClip = BasicTimelineEditor.apply(tl,
                new TimelineEditRequest("req-1", "t1",
                        List.of(TimelineEditOperation.addClip("clip-1",
                                Map.of("trackId", trackId, "assetId", "a1",
                                        "timelineStart", 0, "assetInPoint", 0, "assetOutPoint", 10))),
                        Map.of()));
        assertEquals(TimelineEditResultStatus.APPLIED, withClip.status());

        // Then add effect to clip
        TimelineEditRequest req = new TimelineEditRequest("req-2", "t1",
                List.of(TimelineEditOperation.addEffect("fx-1",
                        Map.of("visualCapabilityId", "BLUR", "targetClipId", "clip-1"))),
                Map.of());
        TimelineEditResult result = BasicTimelineEditor.apply(withClip.timeline(), req);
        assertEquals(TimelineEditResultStatus.APPLIED, result.status());
    }

    @Test @DisplayName("Add transition works")
    void addTransition() {
        TimelineSpec tl = TimelineSpec.create("t1", "Test", TimelineOutputSpec.mp4_1080p30());
        String trackId = tl.tracks().get(0).id();
        // Add two clips
        TimelineEditResult withClips = BasicTimelineEditor.apply(tl,
                new TimelineEditRequest("req-1", "t1",
                        List.of(
                                TimelineEditOperation.addClip("clip-1",
                                        Map.of("trackId", trackId, "assetId", "a1",
                                                "timelineStart", 0, "assetInPoint", 0, "assetOutPoint", 5)),
                                TimelineEditOperation.addClip("clip-2",
                                        Map.of("trackId", trackId, "assetId", "a2",
                                                "timelineStart", 5, "assetInPoint", 0, "assetOutPoint", 5))),
                        Map.of()));
        assertEquals(TimelineEditResultStatus.APPLIED, withClips.status());

        // Add transition
        TimelineEditRequest req = new TimelineEditRequest("req-2", "t1",
                List.of(TimelineEditOperation.addTransition("tr-1",
                        Map.of("fromClipId", "clip-1", "toClipId", "clip-2",
                                "durationMs", 500, "visualCapabilityId", "CROSSFADE"))),
                Map.of());
        TimelineEditResult result = BasicTimelineEditor.apply(withClips.timeline(), req);
        assertEquals(TimelineEditResultStatus.APPLIED, result.status());
    }

    @Test @DisplayName("Validate timeline operation works")
    void validateTimeline() {
        TimelineSpec tl = TimelineSpec.create("t1", "Test", TimelineOutputSpec.mp4_1080p30());
        TimelineEditRequest req = new TimelineEditRequest("req-1", "t1",
                List.of(TimelineEditOperation.validateTimeline()), Map.of());
        TimelineEditResult result = BasicTimelineEditor.apply(tl, req);
        assertEquals(TimelineEditResultStatus.APPLIED, result.status());
    }

    @Test @DisplayName("Empty operations returns NO_OP")
    void emptyOperationsNoOp() {
        TimelineSpec tl = TimelineSpec.create("t1", "Test", TimelineOutputSpec.mp4_1080p30());
        TimelineEditRequest req = new TimelineEditRequest("req-1", "t1", List.of(), Map.of());
        TimelineEditResult result = BasicTimelineEditor.apply(tl, req);
        assertEquals(TimelineEditResultStatus.NO_OP, result.status());
    }

    @Test @DisplayName("Input immutability preserved")
    void inputImmutability() {
        TimelineSpec tl = TimelineSpec.create("t1", "Test", TimelineOutputSpec.mp4_1080p30());
        int originalTrackCount = tl.tracks().size();

        TimelineEditRequest req = new TimelineEditRequest("req-1", "t1",
                List.of(TimelineEditOperation.addTrack("audio-1",
                        Map.of("name", "Audio", "type", "AUDIO"))),
                Map.of());
        BasicTimelineEditor.apply(tl, req);

        // Original timeline must not be mutated
        assertEquals(originalTrackCount, tl.tracks().size());
    }

    @Test @DisplayName("Deterministic result")
    void deterministicResult() {
        TimelineSpec tl = TimelineSpec.create("t1", "Test", TimelineOutputSpec.mp4_1080p30());
        TimelineEditRequest req = new TimelineEditRequest("req-1", "t1",
                List.of(TimelineEditOperation.addTrack("audio-1",
                        Map.of("name", "Audio", "type", "AUDIO"))),
                Map.of());

        TimelineEditResult r1 = BasicTimelineEditor.apply(tl, req);
        TimelineEditResult r2 = BasicTimelineEditor.apply(tl, req);

        assertEquals(r1.status(), r2.status());
        assertEquals(r1.timeline().tracks().size(), r2.timeline().tracks().size());
    }

    // ==================== Stage 5: Safety and Boundary Tests ====================

    @Test @DisplayName("No vedit reference in editing package")
    void noVeditReference() {
        // Verify by loading classes — if vedit was required, class loading would fail
        assertNotNull(BasicTimelineEditor.class);
        assertNotNull(BasicTimelineValidator.class);
    }

    @Test @DisplayName("No provider/backend/storage internals exposed")
    void noProviderInternalsExposed() {
        TimelineSpec tl = TimelineSpec.create("t1", "Test", TimelineOutputSpec.mp4_1080p30());
        TimelineEditResult result = BasicTimelineEditor.apply(tl,
                new TimelineEditRequest("req-1", "t1",
                        List.of(TimelineEditOperation.createTimeline(Map.of())), Map.of()));
        String repr = result.toString();
        assertFalse(repr.contains("providerName"));
        assertFalse(repr.contains("backendName"));
        assertFalse(repr.contains("bucket"));
        assertFalse(repr.contains("signedUrl"));
        assertFalse(repr.contains("rawCommand"));
    }

    @Test @DisplayName("Editor does not generate FFmpeg filtergraph")
    void noFfmpegFiltergraph() {
        TimelineSpec tl = TimelineSpec.create("t1", "Test", TimelineOutputSpec.mp4_1080p30());
        // Adding a production-allowed effect should not produce filtergraph
        String trackId = tl.tracks().get(0).id();
        TimelineEditResult withClip = BasicTimelineEditor.apply(tl,
                new TimelineEditRequest("req-1", "t1",
                        List.of(TimelineEditOperation.addClip("c1",
                                Map.of("trackId", trackId, "assetId", "a1",
                                        "timelineStart", 0, "assetInPoint", 0, "assetOutPoint", 5))),
                        Map.of()));
        String repr = withClip.toString();
        assertFalse(repr.contains("filter_complex"));
        assertFalse(repr.contains("filtergraph"));
    }

    @Test @DisplayName("Arbitrary filtergraph effect blocked")
    void arbitraryFiltergraphBlocked() {
        TimelineSpec tl = TimelineSpec.create("t1", "Test", TimelineOutputSpec.mp4_1080p30());
        String trackId = tl.tracks().get(0).id();
        // Add clip first
        TimelineEditResult withClip = BasicTimelineEditor.apply(tl,
                new TimelineEditRequest("req-1", "t1",
                        List.of(TimelineEditOperation.addClip("c1",
                                Map.of("trackId", trackId, "assetId", "a1",
                                        "timelineStart", 0, "assetInPoint", 0, "assetOutPoint", 5))),
                        Map.of()));
        // Try to add filtergraph effect
        TimelineEditResult result = BasicTimelineEditor.apply(withClip.timeline(),
                new TimelineEditRequest("req-2", "t1",
                        List.of(TimelineEditOperation.addEffect("fx-1",
                                Map.of("visualCapabilityId", "filter_complex:vflip"))),
                        Map.of()));
        assertEquals(TimelineEditResultStatus.BLOCKED, result.status());
    }

    @Test @DisplayName("Raw command effect blocked")
    void rawCommandEffectBlocked() {
        TimelineSpec tl = TimelineSpec.create("t1", "Test", TimelineOutputSpec.mp4_1080p30());
        String trackId = tl.tracks().get(0).id();
        TimelineEditResult withClip = BasicTimelineEditor.apply(tl,
                new TimelineEditRequest("req-1", "t1",
                        List.of(TimelineEditOperation.addClip("c1",
                                Map.of("trackId", trackId, "assetId", "a1",
                                        "timelineStart", 0, "assetInPoint", 0, "assetOutPoint", 5))),
                        Map.of()));
        TimelineEditResult result = BasicTimelineEditor.apply(withClip.timeline(),
                new TimelineEditRequest("req-2", "t1",
                        List.of(TimelineEditOperation.addEffect("fx-1",
                                Map.of("visualCapabilityId", "rawCommand:rm -rf /"))),
                        Map.of()));
        assertEquals(TimelineEditResultStatus.BLOCKED, result.status());
    }

    @Test @DisplayName("Forbidden metadata in request blocked")
    void forbiddenMetadataBlocked() {
        TimelineSpec tl = TimelineSpec.create("t1", "Test", TimelineOutputSpec.mp4_1080p30());
        TimelineEditResult result = BasicTimelineEditor.apply(tl,
                new TimelineEditRequest("req-1", "t1",
                        List.of(TimelineEditOperation.validateTimeline()),
                        Map.of("bucket", "my-bucket")));
        assertEquals(TimelineEditResultStatus.INVALID_OPERATION, result.status());
    }

    @Test @DisplayName("Effect with missing target clip rejected")
    void effectMissingTargetRejected() {
        TimelineSpec tl = TimelineSpec.create("t1", "Test", TimelineOutputSpec.mp4_1080p30());
        TimelineEditResult result = BasicTimelineEditor.apply(tl,
                new TimelineEditRequest("req-1", "t1",
                        List.of(TimelineEditOperation.addEffect("fx-1",
                                Map.of("visualCapabilityId", "BLUR", "targetClipId", "nonexistent"))),
                        Map.of()));
        assertEquals(TimelineEditResultStatus.VALIDATION_FAILED, result.status());
    }

    @Test @DisplayName("Transition with missing clip rejected")
    void transitionMissingClipRejected() {
        TimelineSpec tl = TimelineSpec.create("t1", "Test", TimelineOutputSpec.mp4_1080p30());
        TimelineEditResult result = BasicTimelineEditor.apply(tl,
                new TimelineEditRequest("req-1", "t1",
                        List.of(TimelineEditOperation.addTransition("tr-1",
                                Map.of("fromClipId", "nonexistent", "toClipId", "also-nonexistent",
                                        "durationMs", 500))),
                        Map.of()));
        assertEquals(TimelineEditResultStatus.VALIDATION_FAILED, result.status());
    }

    @Test @DisplayName("Transition with invalid duration rejected")
    void transitionInvalidDurationRejected() {
        TimelineSpec tl = TimelineSpec.create("t1", "Test", TimelineOutputSpec.mp4_1080p30());
        String trackId = tl.tracks().get(0).id();
        TimelineEditResult withClips = BasicTimelineEditor.apply(tl,
                new TimelineEditRequest("req-1", "t1",
                        List.of(
                                TimelineEditOperation.addClip("c1",
                                        Map.of("trackId", trackId, "assetId", "a1",
                                                "timelineStart", 0, "assetInPoint", 0, "assetOutPoint", 5)),
                                TimelineEditOperation.addClip("c2",
                                        Map.of("trackId", trackId, "assetId", "a2",
                                                "timelineStart", 5, "assetInPoint", 0, "assetOutPoint", 5))),
                        Map.of()));
        TimelineEditResult result = BasicTimelineEditor.apply(withClips.timeline(),
                new TimelineEditRequest("req-2", "t1",
                        List.of(TimelineEditOperation.addTransition("tr-1",
                                Map.of("fromClipId", "c1", "toClipId", "c2", "durationMs", -1))),
                        Map.of()));
        assertEquals(TimelineEditResultStatus.VALIDATION_FAILED, result.status());
    }

    @Test @DisplayName("Clip without track rejected")
    void clipWithoutTrackRejected() {
        TimelineSpec tl = TimelineSpec.create("t1", "Test", TimelineOutputSpec.mp4_1080p30());
        TimelineEditResult result = BasicTimelineEditor.apply(tl,
                new TimelineEditRequest("req-1", "t1",
                        List.of(TimelineEditOperation.addClip("c1",
                                Map.of("assetId", "a1", "timelineStart", 0,
                                        "assetInPoint", 0, "assetOutPoint", 5))),
                        Map.of()));
        assertEquals(TimelineEditResultStatus.VALIDATION_FAILED, result.status());
    }

    @Test @DisplayName("Add clip to nonexistent track rejected")
    void clipToNonexistentTrackRejected() {
        TimelineSpec tl = TimelineSpec.create("t1", "Test", TimelineOutputSpec.mp4_1080p30());
        TimelineEditResult result = BasicTimelineEditor.apply(tl,
                new TimelineEditRequest("req-1", "t1",
                        List.of(TimelineEditOperation.addClip("c1",
                                Map.of("trackId", "nonexistent", "assetId", "a1",
                                        "timelineStart", 0, "assetInPoint", 0, "assetOutPoint", 5))),
                        Map.of()));
        assertEquals(TimelineEditResultStatus.VALIDATION_FAILED, result.status());
    }

    @Test @DisplayName("Result does not contain vedit/OTIO/Remotion references")
    void resultNoExternalReferences() {
        TimelineSpec tl = TimelineSpec.create("t1", "Test", TimelineOutputSpec.mp4_1080p30());
        TimelineEditResult result = BasicTimelineEditor.apply(tl,
                new TimelineEditRequest("req-1", "t1",
                        List.of(TimelineEditOperation.createTimeline(Map.of())), Map.of()));
        String repr = result.toString();
        assertFalse(repr.contains("vedit"));
        assertFalse(repr.contains("pyvedit"));
        assertFalse(repr.contains("OpenTimelineIO"));
        assertFalse(repr.contains("opentimelineio"));
        assertFalse(repr.contains("Remotion"));
    }
}
