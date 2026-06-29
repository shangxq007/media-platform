package com.example.platform.render.domain.timeline.editing;

import com.example.platform.render.domain.timeline.*;
import java.util.*;

/**
 * Pure side-effect-free timeline editor.
 * Internal domain model.
 *
 * <p>Applies semantic edit operations to a timeline, returning an updated timeline.
 * Does not persist, render, create Product, call StorageRuntime/ProductRuntime,
 * call FFmpeg, call Remotion, call OpenCue, or use Artifact DAG.</p>
 *
 * <p>Input immutability: the input timeline is never mutated.</p>
 */
public final class BasicTimelineEditor {

    private BasicTimelineEditor() {}

    /**
     * Applies an edit request to a timeline.
     *
     * @param timeline the source timeline (not mutated)
     * @param request  the edit request
     * @return edit result with updated timeline or failure
     */
    public static TimelineEditResult apply(TimelineSpec timeline, TimelineEditRequest request) {
        // Validate request
        List<TimelineValidationIssue> requestIssues = BasicTimelineValidator.validateRequest(request);
        if (!requestIssues.isEmpty()) {
            return TimelineEditResult.invalidOperation(requestIssues);
        }

        if (request.operations().isEmpty()) {
            return TimelineEditResult.noOp(timeline);
        }

        // Apply operations sequentially
        TimelineSpec current = timeline;
        List<TimelineValidationIssue> allIssues = new ArrayList<>();

        for (TimelineEditOperation op : request.operations()) {
            TimelineEditResult opResult = applyOperation(current, op);
            allIssues.addAll(opResult.issues());

            if (opResult.status() == TimelineEditResultStatus.APPLIED && opResult.timeline() != null) {
                current = opResult.timeline();
            } else if (opResult.status() == TimelineEditResultStatus.VALIDATION_FAILED
                    || opResult.status() == TimelineEditResultStatus.BLOCKED
                    || opResult.status() == TimelineEditResultStatus.INVALID_OPERATION) {
                return opResult;
            }
            // NO_OP continues with current timeline
        }

        // Validate final timeline
        List<TimelineValidationIssue> validationIssues = BasicTimelineValidator.validate(current);
        allIssues.addAll(validationIssues);

        boolean hasBlocking = validationIssues.stream().anyMatch(i ->
                i.severity() == TimelineValidationIssueSeverity.BLOCKING
                        || i.severity() == TimelineValidationIssueSeverity.ERROR);

        if (hasBlocking) {
            return TimelineEditResult.validationFailed(allIssues);
        }

        return new TimelineEditResult(TimelineEditResultStatus.APPLIED, current, allIssues, Map.of());
    }

    /**
     * Applies a single operation to a timeline.
     */
    private static TimelineEditResult applyOperation(TimelineSpec timeline, TimelineEditOperation op) {
        if (op == null || op.type() == null) {
            return TimelineEditResult.invalidOperation(List.of(
                    TimelineValidationIssue.error(
                            TimelineValidationIssueCode.INVALID_TIMELINE_ID,
                            "operation", "Operation or type must not be null")));
        }

        return switch (op.type()) {
            case CREATE_TIMELINE -> applyCreateTimeline(op);
            case UPDATE_OUTPUT_PROFILE -> applyUpdateOutputProfile(timeline, op);
            case ADD_TRACK -> applyAddTrack(timeline, op);
            case REMOVE_TRACK -> applyRemoveTrack(timeline, op);
            case REORDER_TRACK -> applyReorderTrack(timeline, op);
            case ADD_CLIP -> applyAddClip(timeline, op);
            case UPDATE_CLIP -> applyUpdateClip(timeline, op);
            case REMOVE_CLIP -> applyRemoveClip(timeline, op);
            case ADD_CAPTION -> applyAddCaption(timeline, op);
            case UPDATE_CAPTION -> applyUpdateCaption(timeline, op);
            case REMOVE_CAPTION -> applyRemoveCaption(timeline, op);
            case ADD_WATERMARK -> applyAddWatermark(timeline, op);
            case UPDATE_WATERMARK -> applyUpdateWatermark(timeline, op);
            case REMOVE_WATERMARK -> applyRemoveWatermark(timeline, op);
            case ADD_EFFECT -> applyAddEffect(timeline, op);
            case UPDATE_EFFECT -> applyUpdateEffect(timeline, op);
            case REMOVE_EFFECT -> applyRemoveEffect(timeline, op);
            case ADD_TRANSITION -> applyAddTransition(timeline, op);
            case UPDATE_TRANSITION -> applyUpdateTransition(timeline, op);
            case REMOVE_TRANSITION -> applyRemoveTransition(timeline, op);
            case VALIDATE_TIMELINE -> applyValidate(timeline);
        };
    }

    // --- CREATE_TIMELINE ---

    private static TimelineEditResult applyCreateTimeline(TimelineEditOperation op) {
        Map<String, Object> params = op.parameters();
        String id = getString(params, "id", "timeline-" + System.currentTimeMillis());
        String name = getString(params, "name", "Untitled Timeline");

        TimelineOutputSpec outputSpec = TimelineOutputSpec.mp4_1080p30();
        if (params.containsKey("outputSpec") && params.get("outputSpec") instanceof TimelineOutputSpec os) {
            outputSpec = os;
        }

        TimelineSpec created = TimelineSpec.create(id, name, outputSpec);
        return TimelineEditResult.applied(created);
    }

    // --- UPDATE_OUTPUT_PROFILE ---

    private static TimelineEditResult applyUpdateOutputProfile(TimelineSpec timeline, TimelineEditOperation op) {
        Map<String, Object> params = op.parameters();
        TimelineOutputSpec existing = timeline.outputSpec();
        if (existing == null) existing = TimelineOutputSpec.mp4_1080p30();

        String format = getString(params, "format", existing.format());
        String resolution = getString(params, "resolution", existing.resolution());
        double frameRate = getDouble(params, "frameRate", existing.frameRate());
        String videoCodec = getString(params, "videoCodec", existing.videoCodec());
        int videoBitrate = getInt(params, "videoBitrate", existing.videoBitrate());

        TimelineOutputSpec updated = new TimelineOutputSpec(
                format, resolution, frameRate, videoCodec, videoBitrate,
                existing.audioSpec(), existing.pixelFormat());

        TimelineSpec result = new TimelineSpec(
                timeline.id(), timeline.name(), timeline.description(),
                timeline.tracks(), timeline.textOverlays(),
                updated, timeline.totalDuration(), timeline.metadata());
        return TimelineEditResult.applied(result);
    }

    // --- ADD_TRACK ---

    private static TimelineEditResult applyAddTrack(TimelineSpec timeline, TimelineEditOperation op) {
        Map<String, Object> params = op.parameters();
        String trackId = op.targetId() != null ? op.targetId() : getString(params, "id", null);
        if (trackId == null || trackId.isBlank()) {
            return TimelineEditResult.invalidOperation(List.of(
                    TimelineValidationIssue.error(
                            TimelineValidationIssueCode.DUPLICATE_TRACK_ID,
                            "track.id", "Track id must not be blank")));
        }

        // Check for duplicate
        if (timeline.tracks() != null) {
            for (TimelineTrack t : timeline.tracks()) {
                if (trackId.equals(t.id())) {
                    return TimelineEditResult.validationFailed(List.of(
                            TimelineValidationIssue.error(
                                    TimelineValidationIssueCode.DUPLICATE_TRACK_ID,
                                    "track.id", "Duplicate track id: " + trackId)));
                }
            }
        }

        String name = getString(params, "name", trackId);
        String typeStr = getString(params, "type", "VIDEO");
        TimelineTrack.TrackType type;
        try {
            type = TimelineTrack.TrackType.valueOf(typeStr.toUpperCase());
        } catch (Exception e) {
            type = TimelineTrack.TrackType.VIDEO;
        }
        int layer = getInt(params, "layer", timeline.tracks() != null ? timeline.tracks().size() : 0);

        TimelineTrack newTrack = new TimelineTrack(trackId, name, type, layer, List.of(), false, false);
        List<TimelineTrack> tracks = new ArrayList<>(timeline.tracks() != null ? timeline.tracks() : List.of());
        tracks.add(newTrack);

        TimelineSpec result = new TimelineSpec(
                timeline.id(), timeline.name(), timeline.description(),
                tracks, timeline.textOverlays(),
                timeline.outputSpec(), timeline.totalDuration(), timeline.metadata());
        return TimelineEditResult.applied(result);
    }

    // --- ADD_CLIP ---

    private static TimelineEditResult applyAddClip(TimelineSpec timeline, TimelineEditOperation op) {
        Map<String, Object> params = op.parameters();
        String clipId = op.targetId() != null ? op.targetId() : getString(params, "id", null);
        if (clipId == null || clipId.isBlank()) {
            return TimelineEditResult.invalidOperation(List.of(
                    TimelineValidationIssue.error(
                            TimelineValidationIssueCode.DUPLICATE_CLIP_ID,
                            "clip.id", "Clip id must not be blank")));
        }

        String trackId = getString(params, "trackId", null);
        if (trackId == null) {
            return TimelineEditResult.validationFailed(List.of(
                    TimelineValidationIssue.error(
                            TimelineValidationIssueCode.TRACK_NOT_FOUND,
                            "clip.trackId", "Track id is required for add clip")));
        }

        // Find target track
        int trackIdx = -1;
        for (int i = 0; i < timeline.tracks().size(); i++) {
            if (trackId.equals(timeline.tracks().get(i).id())) {
                trackIdx = i;
                break;
            }
        }
        if (trackIdx < 0) {
            return TimelineEditResult.validationFailed(List.of(
                    TimelineValidationIssue.error(
                            TimelineValidationIssueCode.TRACK_NOT_FOUND,
                            "clip.trackId", "Track not found: " + trackId)));
        }

        // Check duplicate clip id
        for (TimelineTrack t : timeline.tracks()) {
            if (t.clips() != null) {
                for (TimelineClip c : t.clips()) {
                    if (clipId.equals(c.id())) {
                        return TimelineEditResult.validationFailed(List.of(
                                TimelineValidationIssue.error(
                                        TimelineValidationIssueCode.DUPLICATE_CLIP_ID,
                                        "clip.id", "Duplicate clip id: " + clipId)));
                    }
                }
            }
        }

        String assetId = getString(params, "assetId", clipId);
        String storageUri = getString(params, "storageUri", "");
        double timelineStart = getDouble(params, "timelineStart", 0);
        double assetInPoint = getDouble(params, "assetInPoint", 0);
        double assetOutPoint = getDouble(params, "assetOutPoint", 10);
        double clipDuration = assetOutPoint - assetInPoint;

        TimelineAssetRef assetRef = TimelineAssetRef.of(assetId, storageUri);
        TimelineClip newClip = new TimelineClip(
                clipId, assetRef, timelineStart, assetInPoint, assetOutPoint, clipDuration, List.of());

        List<TimelineTrack> tracks = new ArrayList<>();
        for (int i = 0; i < timeline.tracks().size(); i++) {
            TimelineTrack t = timeline.tracks().get(i);
            if (i == trackIdx) {
                List<TimelineClip> clips = new ArrayList<>(t.clips() != null ? t.clips() : List.of());
                clips.add(newClip);
                tracks.add(new TimelineTrack(t.id(), t.name(), t.type(), t.layer(), clips, t.muted(), t.locked()));
            } else {
                tracks.add(t);
            }
        }

        TimelineSpec result = new TimelineSpec(
                timeline.id(), timeline.name(), timeline.description(),
                tracks, timeline.textOverlays(),
                timeline.outputSpec(), timeline.totalDuration(), timeline.metadata());
        return TimelineEditResult.applied(result);
    }

    // --- ADD_CAPTION ---

    private static TimelineEditResult applyAddCaption(TimelineSpec timeline, TimelineEditOperation op) {
        Map<String, Object> params = op.parameters();
        String captionId = op.targetId() != null ? op.targetId() : getString(params, "id", null);
        if (captionId == null || captionId.isBlank()) {
            return TimelineEditResult.invalidOperation(List.of(
                    TimelineValidationIssue.error(
                            TimelineValidationIssueCode.DUPLICATE_CAPTION_ID,
                            "caption.id", "Caption id must not be blank")));
        }

        // Check duplicate
        if (timeline.textOverlays() != null) {
            for (TimelineTextOverlay t : timeline.textOverlays()) {
                if (captionId.equals(t.id())) {
                    return TimelineEditResult.validationFailed(List.of(
                            TimelineValidationIssue.error(
                                    TimelineValidationIssueCode.DUPLICATE_CAPTION_ID,
                                    "caption.id", "Duplicate caption id: " + captionId)));
                }
            }
        }

        String text = getString(params, "text", "");
        double startTime = getDouble(params, "startTime", 0);
        double duration = getDouble(params, "duration", 5);

        TimelineTextOverlay newCaption = TimelineTextOverlay.of(captionId, text, startTime, duration);
        List<TimelineTextOverlay> overlays = new ArrayList<>(
                timeline.textOverlays() != null ? timeline.textOverlays() : List.of());
        overlays.add(newCaption);

        TimelineSpec result = new TimelineSpec(
                timeline.id(), timeline.name(), timeline.description(),
                timeline.tracks(), overlays,
                timeline.outputSpec(), timeline.totalDuration(), timeline.metadata());
        return TimelineEditResult.applied(result);
    }

    // --- ADD_WATERMARK ---

    private static TimelineEditResult applyAddWatermark(TimelineSpec timeline, TimelineEditOperation op) {
        // Watermarks are stored as metadata annotations in current model
        // This is a semantic placeholder — actual watermark rendering is future work
        Map<String, Object> params = op.parameters();
        String watermarkId = op.targetId() != null ? op.targetId() : getString(params, "id", null);
        if (watermarkId == null || watermarkId.isBlank()) {
            return TimelineEditResult.invalidOperation(List.of(
                    TimelineValidationIssue.error(
                            TimelineValidationIssueCode.DUPLICATE_WATERMARK_ID,
                            "watermark.id", "Watermark id must not be blank")));
        }

        // Store watermark info in metadata as safe annotation
        Map<String, String> metadata = new LinkedHashMap<>(timeline.metadata() != null ? timeline.metadata() : Map.of());
        String kind = getString(params, "kind", "TEXT");
        String text = getString(params, "text", "");
        String imageRef = getString(params, "imageRef", "");
        String placement = getString(params, "placement", "bottom-right");
        double opacity = getDouble(params, "opacity", 0.5);

        metadata.put("watermark." + watermarkId + ".kind", kind);
        if (!text.isBlank()) metadata.put("watermark." + watermarkId + ".text", text);
        if (!imageRef.isBlank()) metadata.put("watermark." + watermarkId + ".imageRef", imageRef);
        metadata.put("watermark." + watermarkId + ".placement", placement);
        metadata.put("watermark." + watermarkId + ".opacity", String.valueOf(opacity));

        TimelineSpec result = new TimelineSpec(
                timeline.id(), timeline.name(), timeline.description(),
                timeline.tracks(), timeline.textOverlays(),
                timeline.outputSpec(), timeline.totalDuration(), Map.copyOf(metadata));
        return TimelineEditResult.applied(result);
    }

    // --- ADD_EFFECT ---

    private static TimelineEditResult applyAddEffect(TimelineSpec timeline, TimelineEditOperation op) {
        Map<String, Object> params = op.parameters();
        String effectId = op.targetId() != null ? op.targetId() : getString(params, "id", null);
        if (effectId == null || effectId.isBlank()) {
            return TimelineEditResult.invalidOperation(List.of(
                    TimelineValidationIssue.error(
                            TimelineValidationIssueCode.EFFECT_CAPABILITY_NOT_FOUND,
                            "effect.id", "Effect id must not be blank")));
        }

        String visualCapabilityId = getString(params, "visualCapabilityId", effectId);

        // Check for forbidden capabilities
        String lowerCap = visualCapabilityId.toLowerCase();
        if (lowerCap.contains("filtergraph") || lowerCap.contains("filter_complex")) {
            return TimelineEditResult.blocked(List.of(
                    TimelineValidationIssue.blocking(
                            TimelineValidationIssueCode.ARBITRARY_FILTERGRAPH_FORBIDDEN,
                            "effect.visualCapabilityId", "Arbitrary FFmpeg filtergraph forbidden")));
        }
        if (lowerCap.contains("rawcommand") || lowerCap.contains("shell command")) {
            return TimelineEditResult.blocked(List.of(
                    TimelineValidationIssue.blocking(
                            TimelineValidationIssueCode.RAW_PROVIDER_COMMAND_FORBIDDEN,
                            "effect.visualCapabilityId", "Raw provider command forbidden")));
        }

        String targetClipId = getString(params, "targetClipId", null);
        Map<String, Object> effectParams = getMap(params, "parameters");

        // Find target clip and add effect
        List<TimelineTrack> tracks = new ArrayList<>();
        boolean found = false;
        for (TimelineTrack t : timeline.tracks()) {
            List<TimelineClip> clips = new ArrayList<>();
            for (TimelineClip c : t.clips()) {
                if (targetClipId != null && targetClipId.equals(c.id())) {
                    List<TimelineClipEffect> effects = new ArrayList<>(c.effects() != null ? c.effects() : List.of());
                    effects.add(TimelineClipEffect.ofKey(visualCapabilityId, effectParams));
                    clips.add(new TimelineClip(c.id(), c.assetRef(), c.timelineStart(),
                            c.assetInPoint(), c.assetOutPoint(), c.clipDuration(), effects));
                    found = true;
                } else {
                    clips.add(c);
                }
            }
            tracks.add(new TimelineTrack(t.id(), t.name(), t.type(), t.layer(), clips, t.muted(), t.locked()));
        }

        if (!found && targetClipId != null) {
            return TimelineEditResult.validationFailed(List.of(
                    TimelineValidationIssue.error(
                            TimelineValidationIssueCode.EFFECT_TARGET_NOT_FOUND,
                            "effect.targetClipId", "Target clip not found: " + targetClipId)));
        }

        TimelineSpec result = new TimelineSpec(
                timeline.id(), timeline.name(), timeline.description(),
                tracks, timeline.textOverlays(),
                timeline.outputSpec(), timeline.totalDuration(), timeline.metadata());
        return TimelineEditResult.applied(result);
    }

    // --- ADD_TRANSITION ---

    private static TimelineEditResult applyAddTransition(TimelineSpec timeline, TimelineEditOperation op) {
        Map<String, Object> params = op.parameters();
        String transitionId = op.targetId() != null ? op.targetId() : getString(params, "id", null);
        if (transitionId == null || transitionId.isBlank()) {
            return TimelineEditResult.invalidOperation(List.of(
                    TimelineValidationIssue.error(
                            TimelineValidationIssueCode.TRANSITION_CAPABILITY_NOT_FOUND,
                            "transition.id", "Transition id must not be blank")));
        }

        String fromClipId = getString(params, "fromClipId", null);
        String toClipId = getString(params, "toClipId", null);
        double durationMs = getDouble(params, "durationMs", 500);
        String visualCapabilityId = getString(params, "visualCapabilityId", "CROSSFADE");

        // Validate clip references
        boolean foundFrom = false, foundTo = false;
        for (TimelineTrack t : timeline.tracks()) {
            for (TimelineClip c : t.clips()) {
                if (fromClipId != null && fromClipId.equals(c.id())) foundFrom = true;
                if (toClipId != null && toClipId.equals(c.id())) foundTo = true;
            }
        }

        if (fromClipId == null || !foundFrom) {
            return TimelineEditResult.validationFailed(List.of(
                    TimelineValidationIssue.error(
                            TimelineValidationIssueCode.TRANSITION_CLIP_NOT_FOUND,
                            "transition.fromClipId", "From clip not found: " + fromClipId)));
        }
        if (toClipId == null || !foundTo) {
            return TimelineEditResult.validationFailed(List.of(
                    TimelineValidationIssue.error(
                            TimelineValidationIssueCode.TRANSITION_CLIP_NOT_FOUND,
                            "transition.toClipId", "To clip not found: " + toClipId)));
        }
        if (durationMs <= 0) {
            return TimelineEditResult.validationFailed(List.of(
                    TimelineValidationIssue.error(
                            TimelineValidationIssueCode.INVALID_TRANSITION_DURATION,
                            "transition.durationMs", "Duration must be positive")));
        }

        // Store transition as metadata annotation
        Map<String, String> metadata = new LinkedHashMap<>(timeline.metadata() != null ? timeline.metadata() : Map.of());
        metadata.put("transition." + transitionId + ".from", fromClipId);
        metadata.put("transition." + transitionId + ".to", toClipId);
        metadata.put("transition." + transitionId + ".durationMs", String.valueOf(durationMs));
        metadata.put("transition." + transitionId + ".capability", visualCapabilityId);

        TimelineSpec result = new TimelineSpec(
                timeline.id(), timeline.name(), timeline.description(),
                timeline.tracks(), timeline.textOverlays(),
                timeline.outputSpec(), timeline.totalDuration(), Map.copyOf(metadata));
        return TimelineEditResult.applied(result);
    }

    // --- VALIDATE_TIMELINE ---

    private static TimelineEditResult applyValidate(TimelineSpec timeline) {
        List<TimelineValidationIssue> issues = BasicTimelineValidator.validate(timeline);
        boolean hasBlocking = issues.stream().anyMatch(i ->
                i.severity() == TimelineValidationIssueSeverity.BLOCKING
                        || i.severity() == TimelineValidationIssueSeverity.ERROR);

        if (hasBlocking) {
            return TimelineEditResult.validationFailed(issues);
        }
        return new TimelineEditResult(TimelineEditResultStatus.APPLIED, timeline, issues, Map.of());
    }

    // --- Stubs for remaining operations (future work) ---

    private static TimelineEditResult applyRemoveTrack(TimelineSpec t, TimelineEditOperation o) {
        return TimelineEditResult.noOp(t);
    }
    private static TimelineEditResult applyReorderTrack(TimelineSpec t, TimelineEditOperation o) {
        return TimelineEditResult.noOp(t);
    }
    private static TimelineEditResult applyUpdateClip(TimelineSpec t, TimelineEditOperation o) {
        return TimelineEditResult.noOp(t);
    }
    private static TimelineEditResult applyRemoveClip(TimelineSpec t, TimelineEditOperation o) {
        return TimelineEditResult.noOp(t);
    }
    private static TimelineEditResult applyUpdateCaption(TimelineSpec t, TimelineEditOperation o) {
        return TimelineEditResult.noOp(t);
    }
    private static TimelineEditResult applyRemoveCaption(TimelineSpec t, TimelineEditOperation o) {
        return TimelineEditResult.noOp(t);
    }
    private static TimelineEditResult applyUpdateWatermark(TimelineSpec t, TimelineEditOperation o) {
        return TimelineEditResult.noOp(t);
    }
    private static TimelineEditResult applyRemoveWatermark(TimelineSpec t, TimelineEditOperation o) {
        return TimelineEditResult.noOp(t);
    }
    private static TimelineEditResult applyUpdateEffect(TimelineSpec t, TimelineEditOperation o) {
        return TimelineEditResult.noOp(t);
    }
    private static TimelineEditResult applyRemoveEffect(TimelineSpec t, TimelineEditOperation o) {
        return TimelineEditResult.noOp(t);
    }
    private static TimelineEditResult applyUpdateTransition(TimelineSpec t, TimelineEditOperation o) {
        return TimelineEditResult.noOp(t);
    }
    private static TimelineEditResult applyRemoveTransition(TimelineSpec t, TimelineEditOperation o) {
        return TimelineEditResult.noOp(t);
    }

    // --- Utility methods ---

    private static String getString(Map<String, Object> params, String key, String defaultVal) {
        Object v = params.get(key);
        return v instanceof String s ? s : defaultVal;
    }

    private static double getDouble(Map<String, Object> params, String key, double defaultVal) {
        Object v = params.get(key);
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof String s) {
            try { return Double.parseDouble(s); } catch (Exception e) { return defaultVal; }
        }
        return defaultVal;
    }

    private static int getInt(Map<String, Object> params, String key, int defaultVal) {
        Object v = params.get(key);
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s) {
            try { return Integer.parseInt(s); } catch (Exception e) { return defaultVal; }
        }
        return defaultVal;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getMap(Map<String, Object> params, String key) {
        Object v = params.get(key);
        return v instanceof Map ? (Map<String, Object>) v : Map.of();
    }
}
