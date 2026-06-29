package com.example.platform.render.domain.timeline.diff.application;

import com.example.platform.render.domain.timeline.diff.*;
import com.example.platform.render.domain.timeline.diff.calculation.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Pure in-memory TimelinePatch applier. Side-effect free, provider-neutral.
 * Internal domain model. Does not persist, merge, or execute.
 */
public class TimelinePatchApplier {

    private final TimelinePatchValidator validator = new TimelinePatchValidator();

    public TimelinePatchApplicationResult apply(
            CanonicalTimelineSnapshot base, TimelinePatch patch) {

        TimelinePatchValidationResult validation = validator.validate(base, patch);
        if (!validation.valid()) {
            return TimelinePatchApplicationResult.validationFailed(validation.issues());
        }

        if (patch.operations() == null || patch.operations().isEmpty()) {
            return TimelinePatchApplicationResult.noOp(base);
        }

        CanonicalTimelineSnapshot current = base;
        for (TimelineChangeOperation op : patch.operations()) {
            TimelinePatchApplicationResult opResult = applyOperation(current, op);
            if (opResult.status() != TimelinePatchApplicationStatus.APPLIED
                    && opResult.status() != TimelinePatchApplicationStatus.NO_OP) {
                return opResult;
            }
            if (opResult.patchedSnapshot() != null) {
                current = opResult.patchedSnapshot();
            }
        }

        CanonicalTimelineSnapshot patched = new CanonicalTimelineSnapshot(
                current.id(), base.revisionId() + "+patched", current.durationMs(),
                current.tracks(), current.captions(), current.watermarks(),
                current.templateApplications(), current.workflowSteps(),
                current.outputProfile(), current.safeMetadata());
        return TimelinePatchApplicationResult.applied(patched);
    }

    private TimelinePatchApplicationResult applyOperation(
            CanonicalTimelineSnapshot s, TimelineChangeOperation op) {
        return switch (op.type()) {
            case TIMELINE_DURATION_CHANGED -> applyDuration(s, op);
            case TRACK_ADDED -> applyTrackAdded(s, op);
            case TRACK_REMOVED -> applyTrackRemoved(s, op);
            case TRACK_REORDERED -> applyTrackReordered(s, op);
            case CLIP_ADDED -> applyClipAdded(s, op);
            case CLIP_REMOVED -> applyClipRemoved(s, op);
            case CLIP_MOVED -> applyClipField(s, op, "startMs");
            case CLIP_TRIMMED -> applyClipField(s, op, "durationMs");
            case ASSET_BINDING_CHANGED -> applyClipField(s, op, "assetBindingId");
            case CAPTION_SEGMENT_CHANGED -> applyCaptionText(s, op);
            case TEXT_STYLE_CHANGED -> applyCaptionText(s, op);
            case WATERMARK_CHANGED -> applyWatermark(s, op);
            case TEMPLATE_PARAMETER_CHANGED -> applyTemplateParam(s, op);
            case TEMPLATE_PROFILE_CHANGED -> applyTemplateProfile(s, op);
            case WORKFLOW_APPLY_TEMPLATE_STEP_CHANGED -> applyWorkflowStep(s, op);
            case OUTPUT_PROFILE_CHANGED -> applyOutputProfile(s, op);
            case METADATA_CHANGED -> applyMetadata(s, op);
            default -> TimelinePatchApplicationResult.unsupported("Unsupported: " + op.type());
        };
    }

    // --- Duration ---

    private TimelinePatchApplicationResult applyDuration(CanonicalTimelineSnapshot s, TimelineChangeOperation op) {
        long val = parseLong(afterVal(op), s.durationMs());
        return ok(new CanonicalTimelineSnapshot(s.id(), s.revisionId(), val,
                s.tracks(), s.captions(), s.watermarks(),
                s.templateApplications(), s.workflowSteps(), s.outputProfile(), s.safeMetadata()));
    }

    // --- Track ---

    private TimelinePatchApplicationResult applyTrackAdded(CanonicalTimelineSnapshot s, TimelineChangeOperation op) {
        String id = extractId(op.path().value(), "timeline.tracks.");
        if (s.tracks().stream().anyMatch(t -> t.trackId().equals(id))) {
            return fail(TimelinePatchApplicationIssueCode.TARGET_ALREADY_EXISTS, op.path().value(), "Track exists: " + id);
        }
        List<CanonicalTimelineTrackSnapshot> tracks = new ArrayList<>(s.tracks());
        tracks.add(new CanonicalTimelineTrackSnapshot(id, s.tracks().size(), "VIDEO", List.of(), Map.of()));
        return ok(withTracks(s, tracks));
    }

    private TimelinePatchApplicationResult applyTrackRemoved(CanonicalTimelineSnapshot s, TimelineChangeOperation op) {
        String id = extractId(op.path().value(), "timeline.tracks.");
        List<CanonicalTimelineTrackSnapshot> tracks = s.tracks().stream()
                .filter(t -> !t.trackId().equals(id)).collect(Collectors.toList());
        if (tracks.size() == s.tracks().size()) {
            return fail(TimelinePatchApplicationIssueCode.TARGET_NOT_FOUND, op.path().value(), "Track not found: " + id);
        }
        return ok(withTracks(s, tracks));
    }

    private TimelinePatchApplicationResult applyTrackReordered(CanonicalTimelineSnapshot s, TimelineChangeOperation op) {
        String trackId = extractMiddleId(op.path().value(), "timeline.tracks.", ".order");
        if (trackId == null) return fail(TimelinePatchApplicationIssueCode.INVALID_CHANGE_PATH, op.path().value(), "Bad path");
        int newOrder = parseInt(afterVal(op), 0);
        List<CanonicalTimelineTrackSnapshot> tracks = s.tracks().stream()
                .map(t -> t.trackId().equals(trackId)
                        ? new CanonicalTimelineTrackSnapshot(t.trackId(), newOrder, t.kind(), t.clips(), t.safeMetadata())
                        : t)
                .collect(Collectors.toList());
        return ok(withTracks(s, tracks));
    }

    // --- Clip ---

    private TimelinePatchApplicationResult applyClipAdded(CanonicalTimelineSnapshot s, TimelineChangeOperation op) {
        String trackId = extractTrackId(op.path().value());
        String clipId = extractId(op.path().value(), "timeline.tracks." + trackId + ".clips.");
        Optional<CanonicalTimelineTrackSnapshot> trackOpt = findTrack(s, trackId);
        if (trackOpt.isEmpty()) return fail(TimelinePatchApplicationIssueCode.TARGET_NOT_FOUND, op.path().value(), "Track not found");
        CanonicalTimelineTrackSnapshot track = trackOpt.get();
        if (track.clips().stream().anyMatch(c -> c.clipId().equals(clipId))) {
            return fail(TimelinePatchApplicationIssueCode.TARGET_ALREADY_EXISTS, op.path().value(), "Clip exists");
        }
        List<CanonicalTimelineClipSnapshot> clips = new ArrayList<>(track.clips());
        clips.add(new CanonicalTimelineClipSnapshot(clipId, "", 0, 0, 0, 0, Map.of()));
        return ok(withUpdatedTrack(s, trackId, newTrackWithClips(track, clips)));
    }

    private TimelinePatchApplicationResult applyClipRemoved(CanonicalTimelineSnapshot s, TimelineChangeOperation op) {
        String trackId = extractTrackId(op.path().value());
        String clipId = extractId(op.path().value(), "timeline.tracks." + trackId + ".clips.");
        Optional<CanonicalTimelineTrackSnapshot> trackOpt = findTrack(s, trackId);
        if (trackOpt.isEmpty()) return fail(TimelinePatchApplicationIssueCode.TARGET_NOT_FOUND, op.path().value(), "Track not found");
        CanonicalTimelineTrackSnapshot track = trackOpt.get();
        List<CanonicalTimelineClipSnapshot> clips = track.clips().stream()
                .filter(c -> !c.clipId().equals(clipId)).collect(Collectors.toList());
        if (clips.size() == track.clips().size()) {
            return fail(TimelinePatchApplicationIssueCode.TARGET_NOT_FOUND, op.path().value(), "Clip not found");
        }
        return ok(withUpdatedTrack(s, trackId, newTrackWithClips(track, clips)));
    }

    private TimelinePatchApplicationResult applyClipField(CanonicalTimelineSnapshot s, TimelineChangeOperation op, String field) {
        String trackId = extractTrackId(op.path().value());
        String rawClipId = extractId(op.path().value(), "timeline.tracks." + trackId + ".clips.");
        final String clipId = rawClipId.contains(".") ? rawClipId.split("\\.")[0] : rawClipId;
        Optional<CanonicalTimelineTrackSnapshot> trackOpt = findTrack(s, trackId);
        if (trackOpt.isEmpty()) return fail(TimelinePatchApplicationIssueCode.TARGET_NOT_FOUND, op.path().value(), "Track not found");
        CanonicalTimelineTrackSnapshot track = trackOpt.get();
        boolean found = track.clips().stream().anyMatch(c -> c.clipId().equals(clipId));
        if (!found) return fail(TimelinePatchApplicationIssueCode.TARGET_NOT_FOUND, op.path().value(), "Clip not found");

        String afterValue = afterVal(op);
        List<CanonicalTimelineClipSnapshot> clips = new ArrayList<>();
        for (CanonicalTimelineClipSnapshot c : track.clips()) {
            if (!c.clipId().equals(clipId)) {
                clips.add(c);
            } else {
                clips.add(applyClipFieldOp(c, field, afterValue));
            }
        }
        return ok(withUpdatedTrack(s, trackId, newTrackWithClips(track, clips)));
    }

    // --- Caption ---

    private TimelinePatchApplicationResult applyCaptionText(CanonicalTimelineSnapshot s, TimelineChangeOperation op) {
        String rawCapId = extractId(op.path().value(), "timeline.captions.");
        final String capId = rawCapId.contains(".") ? rawCapId.split("\\.")[0] : rawCapId;
        Optional<CanonicalTimelineCaptionSnapshot> capOpt = s.captions().stream()
                .filter(c -> c.captionId().equals(capId)).findFirst();
        if (capOpt.isEmpty()) return fail(TimelinePatchApplicationIssueCode.TARGET_NOT_FOUND, op.path().value(), "Caption not found");
        CanonicalTimelineCaptionSnapshot cap = capOpt.get();
        String newText = afterVal(op) != null ? afterVal(op) : cap.text();
        CanonicalTimelineCaptionSnapshot updated = new CanonicalTimelineCaptionSnapshot(
                cap.captionId(), cap.startMs(), cap.endMs(), newText, cap.style(), cap.safeMetadata());
        List<CanonicalTimelineCaptionSnapshot> captions = s.captions().stream()
                .map(c -> c.captionId().equals(capId) ? updated : c).collect(Collectors.toList());
        return ok(new CanonicalTimelineSnapshot(s.id(), s.revisionId(), s.durationMs(),
                s.tracks(), captions, s.watermarks(), s.templateApplications(),
                s.workflowSteps(), s.outputProfile(), s.safeMetadata()));
    }

    // --- Watermark ---

    private TimelinePatchApplicationResult applyWatermark(CanonicalTimelineSnapshot s, TimelineChangeOperation op) {
        String wmId = extractId(op.path().value(), "timeline.watermarks.");
        Optional<CanonicalTimelineWatermarkSnapshot> wmOpt = s.watermarks().stream()
                .filter(w -> w.watermarkId().equals(wmId)).findFirst();
        if (wmOpt.isEmpty()) return fail(TimelinePatchApplicationIssueCode.TARGET_NOT_FOUND, op.path().value(), "Watermark not found");
        CanonicalTimelineWatermarkSnapshot wm = wmOpt.get();
        String afterVal = afterVal(op);
        String newPos = wm.position();
        int newOpacity = wm.opacityPercent();
        if (afterVal != null && afterVal.contains(":")) {
            String[] parts = afterVal.split(":");
            newPos = parts[0];
            newOpacity = parseInt(parts[1], wm.opacityPercent());
        }
        CanonicalTimelineWatermarkSnapshot updated = new CanonicalTimelineWatermarkSnapshot(
                wm.watermarkId(), wm.assetBindingId(), newPos, newOpacity, wm.safeMetadata());
        List<CanonicalTimelineWatermarkSnapshot> wms = s.watermarks().stream()
                .map(w -> w.watermarkId().equals(wmId) ? updated : w).collect(Collectors.toList());
        return ok(new CanonicalTimelineSnapshot(s.id(), s.revisionId(), s.durationMs(),
                s.tracks(), s.captions(), wms, s.templateApplications(),
                s.workflowSteps(), s.outputProfile(), s.safeMetadata()));
    }

    // --- Template ---

    private TimelinePatchApplicationResult applyTemplateParam(CanonicalTimelineSnapshot s, TimelineChangeOperation op) {
        String rawAppId = extractId(op.path().value(), "timeline.templateApplications.");
        final String appId = rawAppId.contains(".") ? rawAppId.split("\\.")[0] : rawAppId;
        Optional<CanonicalTimelineTemplateApplicationSnapshot> taOpt = s.templateApplications().stream()
                .filter(t -> t.templateApplicationId().equals(appId)).findFirst();
        if (taOpt.isEmpty()) return fail(TimelinePatchApplicationIssueCode.TARGET_NOT_FOUND, op.path().value(), "Template app not found");
        CanonicalTimelineTemplateApplicationSnapshot ta = taOpt.get();
        Map<String, String> newParams = new HashMap<>(ta.parameters() != null ? ta.parameters() : Map.of());
        String afterVal = afterVal(op);
        if (afterVal != null && afterVal.contains("=")) {
            String[] parts = afterVal.split("=", 2);
            newParams.put(parts[0], parts[1]);
        }
        CanonicalTimelineTemplateApplicationSnapshot updated = new CanonicalTimelineTemplateApplicationSnapshot(
                ta.templateApplicationId(), ta.templateId(), ta.templateVersion(), newParams, ta.safeMetadata());
        List<CanonicalTimelineTemplateApplicationSnapshot> apps = s.templateApplications().stream()
                .map(t -> t.templateApplicationId().equals(appId) ? updated : t).collect(Collectors.toList());
        return ok(new CanonicalTimelineSnapshot(s.id(), s.revisionId(), s.durationMs(),
                s.tracks(), s.captions(), s.watermarks(), apps,
                s.workflowSteps(), s.outputProfile(), s.safeMetadata()));
    }

    private TimelinePatchApplicationResult applyTemplateProfile(CanonicalTimelineSnapshot s, TimelineChangeOperation op) {
        String rawAppId2 = extractId(op.path().value(), "timeline.templateApplications.");
        final String appId = rawAppId2.contains(".") ? rawAppId2.split("\\.")[0] : rawAppId2;
        Optional<CanonicalTimelineTemplateApplicationSnapshot> taOpt = s.templateApplications().stream()
                .filter(t -> t.templateApplicationId().equals(appId)).findFirst();
        if (taOpt.isEmpty()) return fail(TimelinePatchApplicationIssueCode.TARGET_NOT_FOUND, op.path().value(), "Template app not found");
        CanonicalTimelineTemplateApplicationSnapshot ta = taOpt.get();
        String newId = afterVal(op) != null ? afterVal(op) : ta.templateId();
        CanonicalTimelineTemplateApplicationSnapshot updated = new CanonicalTimelineTemplateApplicationSnapshot(
                ta.templateApplicationId(), newId, ta.templateVersion(), ta.parameters(), ta.safeMetadata());
        List<CanonicalTimelineTemplateApplicationSnapshot> apps = s.templateApplications().stream()
                .map(t -> t.templateApplicationId().equals(appId) ? updated : t).collect(Collectors.toList());
        return ok(new CanonicalTimelineSnapshot(s.id(), s.revisionId(), s.durationMs(),
                s.tracks(), s.captions(), s.watermarks(), apps,
                s.workflowSteps(), s.outputProfile(), s.safeMetadata()));
    }

    // --- Workflow ---

    private TimelinePatchApplicationResult applyWorkflowStep(CanonicalTimelineSnapshot s, TimelineChangeOperation op) {
        String stepId = extractId(op.path().value(), "timeline.workflowSteps.");
        Optional<CanonicalTimelineWorkflowStepSnapshot> wsOpt = s.workflowSteps().stream()
                .filter(w -> w.workflowStepId().equals(stepId)).findFirst();
        if (wsOpt.isEmpty()) return fail(TimelinePatchApplicationIssueCode.TARGET_NOT_FOUND, op.path().value(), "Workflow step not found");
        CanonicalTimelineWorkflowStepSnapshot ws = wsOpt.get();
        String newAppId = afterVal(op) != null ? afterVal(op) : ws.templateApplicationId();
        CanonicalTimelineWorkflowStepSnapshot updated = new CanonicalTimelineWorkflowStepSnapshot(
                ws.workflowStepId(), ws.stepType(), newAppId, ws.safeMetadata());
        List<CanonicalTimelineWorkflowStepSnapshot> steps = s.workflowSteps().stream()
                .map(w -> w.workflowStepId().equals(stepId) ? updated : w).collect(Collectors.toList());
        return ok(new CanonicalTimelineSnapshot(s.id(), s.revisionId(), s.durationMs(),
                s.tracks(), s.captions(), s.watermarks(), s.templateApplications(),
                steps, s.outputProfile(), s.safeMetadata()));
    }

    // --- Output profile ---

    private TimelinePatchApplicationResult applyOutputProfile(CanonicalTimelineSnapshot s, TimelineChangeOperation op) {
        CanonicalTimelineOutputProfileSnapshot old = s.outputProfile();
        int newW = old != null ? old.width() : 1920;
        int newH = old != null ? old.height() : 1080;
        String afterVal = afterVal(op);
        if (afterVal != null && afterVal.contains("x")) {
            String[] parts = afterVal.split("x");
            newW = parseInt(parts[0], newW);
            newH = parseInt(parts[1], newH);
        }
        CanonicalTimelineOutputProfileSnapshot profile = new CanonicalTimelineOutputProfileSnapshot(
                old != null ? old.profileId() : "default", old != null ? old.format() : "mp4",
                old != null ? old.aspectRatio() : "16:9", newW, newH, Map.of());
        return ok(new CanonicalTimelineSnapshot(s.id(), s.revisionId(), s.durationMs(),
                s.tracks(), s.captions(), s.watermarks(), s.templateApplications(),
                s.workflowSteps(), profile, s.safeMetadata()));
    }

    // --- Metadata ---

    private TimelinePatchApplicationResult applyMetadata(CanonicalTimelineSnapshot s, TimelineChangeOperation op) {
        Map<String, String> meta = new HashMap<>(s.safeMetadata() != null ? s.safeMetadata() : Map.of());
        String afterVal = afterVal(op);
        if (afterVal != null) {
            // Handle {key=value, key2=value2} format from diff calculator
            String cleaned = afterVal.startsWith("{") && afterVal.endsWith("}")
                    ? afterVal.substring(1, afterVal.length() - 1) : afterVal;
            for (String entry : cleaned.split(", ")) {
                if (entry.contains("=")) {
                    String[] parts = entry.split("=", 2);
                    meta.put(parts[0].trim(), parts[1].trim());
                }
            }
        }
        return ok(new CanonicalTimelineSnapshot(s.id(), s.revisionId(), s.durationMs(),
                s.tracks(), s.captions(), s.watermarks(), s.templateApplications(),
                s.workflowSteps(), s.outputProfile(), meta));
    }

    // --- Helpers ---

    private CanonicalTimelineClipSnapshot applyClipFieldOp(
            CanonicalTimelineClipSnapshot c, String field, String afterValue) {
        return switch (field) {
            case "startMs" -> new CanonicalTimelineClipSnapshot(c.clipId(), c.assetBindingId(),
                    parseLong(afterValue, c.startMs()), c.durationMs(), c.sourceStartMs(), c.sourceDurationMs(), c.safeMetadata());
            case "durationMs" -> new CanonicalTimelineClipSnapshot(c.clipId(), c.assetBindingId(),
                    c.startMs(), parseLong(afterValue, c.durationMs()), c.sourceStartMs(), parseLong(afterValue, c.sourceDurationMs()), c.safeMetadata());
            case "assetBindingId" -> new CanonicalTimelineClipSnapshot(c.clipId(),
                    afterValue != null ? afterValue : c.assetBindingId(),
                    c.startMs(), c.durationMs(), c.sourceStartMs(), c.sourceDurationMs(), c.safeMetadata());
            default -> c;
        };
    }

    private TimelinePatchApplicationResult ok(CanonicalTimelineSnapshot s) {
        return TimelinePatchApplicationResult.applied(s);
    }

    private TimelinePatchApplicationResult fail(TimelinePatchApplicationIssueCode code, String field, String msg) {
        return TimelinePatchApplicationResult.validationFailed(
                List.of(new TimelinePatchApplicationIssue(
                        TimelinePatchApplicationIssueSeverity.ERROR, code, field, msg, Map.of())));
    }

    private String afterVal(TimelineChangeOperation op) {
        return op.afterValue() != null ? op.afterValue().stringValue() : null;
    }

    private String extractId(String path, String prefix) {
        if (!path.startsWith(prefix)) return path;
        String rest = path.substring(prefix.length());
        int dot = rest.indexOf('.');
        return dot > 0 ? rest.substring(0, dot) : rest;
    }

    private String extractMiddleId(String path, String prefix, String suffix) {
        if (path.startsWith(prefix) && path.endsWith(suffix)) {
            return path.substring(prefix.length(), path.length() - suffix.length());
        }
        return null;
    }

    private String extractTrackId(String path) {
        String after = path.substring("timeline.tracks.".length());
        int idx = after.indexOf(".clips.");
        return idx > 0 ? after.substring(0, idx) : after;
    }

    private long parseLong(Object v, long def) {
        if (v == null) return def;
        try { return Long.parseLong(v.toString()); } catch (Exception e) { return def; }
    }

    private int parseInt(Object v, int def) {
        if (v == null) return def;
        try { return Integer.parseInt(v.toString()); } catch (Exception e) { return def; }
    }

    private Optional<CanonicalTimelineTrackSnapshot> findTrack(CanonicalTimelineSnapshot s, String id) {
        return s.tracks().stream().filter(t -> t.trackId().equals(id)).findFirst();
    }

    private CanonicalTimelineTrackSnapshot newTrackWithClips(CanonicalTimelineTrackSnapshot t, List<CanonicalTimelineClipSnapshot> clips) {
        return new CanonicalTimelineTrackSnapshot(t.trackId(), t.order(), t.kind(), clips, t.safeMetadata());
    }

    private CanonicalTimelineSnapshot withTracks(CanonicalTimelineSnapshot s, List<CanonicalTimelineTrackSnapshot> tracks) {
        return new CanonicalTimelineSnapshot(s.id(), s.revisionId(), s.durationMs(),
                tracks, s.captions(), s.watermarks(), s.templateApplications(),
                s.workflowSteps(), s.outputProfile(), s.safeMetadata());
    }

    private CanonicalTimelineSnapshot withUpdatedTrack(CanonicalTimelineSnapshot s, String trackId, CanonicalTimelineTrackSnapshot updated) {
        List<CanonicalTimelineTrackSnapshot> tracks = s.tracks().stream()
                .map(t -> t.trackId().equals(trackId) ? updated : t).collect(Collectors.toList());
        return withTracks(s, tracks);
    }
}
