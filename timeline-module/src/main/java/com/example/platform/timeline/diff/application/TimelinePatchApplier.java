package com.example.platform.timeline.diff.application;

import com.example.platform.timeline.canonicalmodel.EffectCanonicalSemantics;
import com.example.platform.timeline.diff.*;
import com.example.platform.timeline.diff.calculation.*;
import com.example.platform.shared.time.FrameRate;
import com.example.platform.shared.time.MediaTime;
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
                current.id(), base.revisionId() + "+patched", current.duration(),
                current.tracks(), current.captions(), current.watermarks(),
                current.templateApplications(), current.workflowSteps(),
                current.outputProfile(), current.safeMetadata(), current.textElements(),
                current.transitions(), current.automations(), current.audioMix(), current.semanticRelationships());
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
            case CLIP_MOVED -> applyClipField(s, op, "start");
            case CLIP_TRIMMED -> applyClipField(s, op, "duration");
            case CLIP_SPEED_CHANGED -> applyClipField(s, op, "rate");
            case ASSET_BINDING_CHANGED -> applyClipField(s, op, "assetBindingId");
            case CAPTION_SEGMENT_CHANGED -> applyCaptionText(s, op);
            case TEXT_STYLE_CHANGED -> applyCaptionText(s, op);
            case WATERMARK_CHANGED -> applyWatermark(s, op);
            case TEMPLATE_PARAMETER_CHANGED -> applyTemplateParam(s, op);
            case TEMPLATE_PROFILE_CHANGED -> applyTemplateProfile(s, op);
            case WORKFLOW_APPLY_TEMPLATE_STEP_CHANGED -> applyWorkflowStep(s, op);
            case OUTPUT_PROFILE_CHANGED -> applyOutputProfile(s, op);
            case METADATA_CHANGED -> applyMetadata(s, op);
            case EFFECT_CHANGED -> applyEffectChanged(s, op);
            case TRANSITION_CHANGED -> applyTransitionChanged(s, op);
            case AUTOMATION_CHANGED -> applyAutomationChanged(s, op);
            case TEXT_ELEMENT_CHANGED -> applyTextElementChanged(s, op);
            case AUDIO_MIX_CHANGED -> applyAudioMixChanged(s, op);
            case RELATIONSHIP_ADDED -> applyRelationshipAdded(s, op);
            case RELATIONSHIP_REMOVED -> applyRelationshipRemoved(s, op);
            case GROUP_MEMBER_ADDED -> applyGroupMember(s, op, true);
            case GROUP_MEMBER_REMOVED -> applyGroupMember(s, op, false);
            default -> TimelinePatchApplicationResult.unsupported("Unsupported: " + op.type());
        };
    }

    // --- CHECKPOINT_A: AudioMix / SemanticRelationship application ---

    private TimelinePatchApplicationResult applyAudioMixChanged(CanonicalTimelineSnapshot s, TimelineChangeOperation op) {
        Map<String, String> meta = op.safeMetadata() != null ? op.safeMetadata() : Map.of();
        String enc = meta.get("audioMix");
        if (enc == null || enc.isBlank()) {
            return fail(TimelinePatchApplicationIssueCode.INVALID_PAYLOAD, op.path().value(),
                    "AudioMix change without canonical payload");
        }
        try {
            com.example.platform.audio.domain.mix.AudioMix mix = com.example.platform.timeline.app.InternalTimelineJson.mapper()
                    .readValue(enc, com.example.platform.audio.domain.mix.AudioMix.class);
            return ok(s.withAudioMix(mix));
        } catch (Exception e) {
            return fail(TimelinePatchApplicationIssueCode.INVALID_PAYLOAD, op.path().value(),
                    "Malformed AudioMix payload: " + e.getMessage());
        }
    }

    private TimelinePatchApplicationResult applyRelationshipAdded(CanonicalTimelineSnapshot s, TimelineChangeOperation op) {
        Map<String, String> meta = op.safeMetadata() != null ? op.safeMetadata() : Map.of();
        String enc = meta.get("relationship");
        if (enc == null || enc.isBlank()) {
            return fail(TimelinePatchApplicationIssueCode.INVALID_PAYLOAD, op.path().value(),
                    "Relationship add without canonical payload");
        }
        try {
            com.example.platform.timeline.semantics.relationship.SemanticRelationship rel =
                    com.example.platform.timeline.app.InternalTimelineJson.mapper()
                            .readValue(enc, com.example.platform.timeline.semantics.relationship.SemanticRelationship.class);
            java.util.List<com.example.platform.timeline.semantics.relationship.SemanticRelationship> rels =
                    new java.util.ArrayList<>(s.semanticRelationships());
            rels.add(rel);
            return ok(s.withSemanticRelationships(rels));
        } catch (Exception e) {
            return fail(TimelinePatchApplicationIssueCode.INVALID_PAYLOAD, op.path().value(),
                    "Malformed relationship payload: " + e.getMessage());
        }
    }

    private TimelinePatchApplicationResult applyRelationshipRemoved(CanonicalTimelineSnapshot s, TimelineChangeOperation op) {
        String key = op.path().value().substring(op.path().value().lastIndexOf('.') + 1);
        java.util.List<com.example.platform.timeline.semantics.relationship.SemanticRelationship> rels =
                new java.util.ArrayList<>(s.semanticRelationships());
        boolean removed = rels.removeIf(r -> relationshipKey(r).equals(key));
        if (!removed) {
            return fail(TimelinePatchApplicationIssueCode.TARGET_NOT_FOUND, op.path().value(),
                    "Relationship not found for removal");
        }
        return ok(s.withSemanticRelationships(rels));
    }

    private TimelinePatchApplicationResult applyGroupMember(CanonicalTimelineSnapshot s, TimelineChangeOperation op,
                                                            boolean add) {
        String key = op.path().value().substring(op.path().value().lastIndexOf('.') + 1);
        String member = add ? op.afterValue().stringValue() : op.beforeValue().stringValue();
        java.util.List<com.example.platform.timeline.semantics.relationship.SemanticRelationship> rels =
                new java.util.ArrayList<>(s.semanticRelationships());
        for (int i = 0; i < rels.size(); i++) {
            var r = rels.get(i);
            if (relationshipKey(r).equals(key) && r instanceof com.example.platform.timeline.semantics.relationship.GroupRelationship g) {
                java.util.Set<com.example.platform.timeline.canonical.TimelineClipId> members =
                        new java.util.LinkedHashSet<>(g.members());
                com.example.platform.timeline.canonical.TimelineClipId id =
                        new com.example.platform.timeline.canonical.TimelineClipId(member);
                if (add) {
                    members.add(id);
                } else {
                    members.remove(id);
                }
                rels.set(i, new com.example.platform.timeline.semantics.relationship.GroupRelationship(g.groupId(), members));
                return ok(s.withSemanticRelationships(rels));
            }
        }
        return fail(TimelinePatchApplicationIssueCode.TARGET_NOT_FOUND, op.path().value(),
                "Group relationship not found for member update");
    }

    private static String relationshipKey(com.example.platform.timeline.semantics.relationship.SemanticRelationship r) {
        if (r instanceof com.example.platform.timeline.semantics.relationship.GroupRelationship g) {
            return "group:" + g.groupId().value();
        }
        if (r instanceof com.example.platform.timeline.semantics.relationship.SyncRelationship s) {
            String a = s.endpointA().value();
            String b = s.endpointB().value();
            return a.compareTo(b) <= 0 ? "sync:" + a + ":" + b : "sync:" + b + ":" + a;
        }
        return "rel:" + System.identityHashCode(r);
    }

    // --- Duration ---

    private TimelinePatchApplicationResult applyDuration(CanonicalTimelineSnapshot s, TimelineChangeOperation op) {
        MediaTime val = parseMediaTime(afterVal(op), s.duration());
        return ok(new CanonicalTimelineSnapshot(s.id(), s.revisionId(), val,
                s.tracks(), s.captions(), s.watermarks(),
                s.templateApplications(), s.workflowSteps(), s.outputProfile(), s.safeMetadata(), s.textElements(), s.transitions(), s.automations(), s.audioMix(), s.semanticRelationships()));
    }

    // --- Track ---

    private TimelinePatchApplicationResult applyTrackAdded(CanonicalTimelineSnapshot s, TimelineChangeOperation op) {
        String id = extractId(op.path().value(), "timeline.tracks.");
        if (s.tracks().stream().anyMatch(t -> t.trackId().equals(id))) {
            return fail(TimelinePatchApplicationIssueCode.TARGET_ALREADY_EXISTS, op.path().value(), "Track exists: " + id);
        }
        // C1-CNM1 field-preservation correction: materialize the added track
        // WITH its clips (identity, exact timing/rate, opaque effects) from the
        // op's safeMetadata. A track added without reconstruction data is a
        // failure — never an empty shell silently dropping source content.
        Map<String, String> meta = op.safeMetadata() != null ? op.safeMetadata() : Map.of();
        String kind = meta.getOrDefault("trackKind", "VIDEO");
        List<CanonicalTimelineClipSnapshot> clips = new ArrayList<>();
        String encoded = meta.get("clips");
        if (encoded != null && !encoded.isBlank()) {
            for (String clipEnc : encoded.split("\\u001f")) {
                String[] parts = clipEnc.split("\\u001e", -1);
                if (parts.length >= 8) {
                    String clipId = parts[0];
                    String assetBindingId = parts[1];
                    MediaTime start = parseMediaTime(parts[2], MediaTime.ZERO);
                    MediaTime duration = parseMediaTime(parts[3], MediaTime.ZERO);
                    MediaTime sourceStart = parseMediaTime(parts[4], MediaTime.ZERO);
                    MediaTime sourceDuration = parseMediaTime(parts[5], MediaTime.ZERO);
                    long rateNum = parseLong(parts[6], 30);
                    long rateDen = parseLong(parts[7], 1);
                    String effectsEnc = parts.length > 8 ? parts[8] : null;
                    if (effectsEnc != null && effectsEnc.startsWith("effects=")) {
                        effectsEnc = effectsEnc.substring("effects=".length());
                    }
                    clips.add(new CanonicalTimelineClipSnapshot(clipId, assetBindingId,
                            start, duration, sourceStart, sourceDuration,
                            FrameRate.of(rateNum, rateDen),
                            parseEffects(effectsEnc), Map.of(),
                            null, null, null, null, null));
                }
            }
        }
        List<CanonicalTimelineTrackSnapshot> tracks = new ArrayList<>(s.tracks());
        tracks.add(new CanonicalTimelineTrackSnapshot(id, s.tracks().size(), kind, clips, Map.of()));
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
        // C1-CNM1 SOURCE_BINDING/field-preservation correction: reconstruct the
        // added clip EXACTLY from the op's safeMetadata (asset binding, exact
        // times, exact rate, opaque effects). A clip added with no metadata is
        // a reconstruction failure — never a silent blank-asset/timing stub.
        Map<String, String> meta = op.safeMetadata() != null ? op.safeMetadata() : Map.of();
        String assetBindingId = meta.getOrDefault("assetBindingId", "");
        if (assetBindingId.isBlank()) {
            return fail(TimelinePatchApplicationIssueCode.INVALID_PAYLOAD, op.path().value(),
                    "CLIP_ADDED missing assetBindingId reconstruction data");
        }
        MediaTime start = parseMediaTime(meta.get("start"), MediaTime.ZERO);
        MediaTime duration = parseMediaTime(meta.get("duration"), MediaTime.ZERO);
        MediaTime sourceStart = parseMediaTime(meta.get("sourceStart"), MediaTime.ZERO);
        MediaTime sourceDuration = parseMediaTime(meta.get("sourceDuration"), MediaTime.ZERO);
        long rateNum = parseLong(meta.get("rateNum"), 30);
        long rateDen = parseLong(meta.get("rateDen"), 1);
        FrameRate rate = FrameRate.of(rateNum, rateDen);
        clips.add(new CanonicalTimelineClipSnapshot(clipId, assetBindingId,
                start, duration, sourceStart, sourceDuration, rate,
                parseEffects(meta.get("effects")), Map.of(),
                meta.get("sourceKind"), meta.get("mediaStreamId"),
                meta.get("artifactId"), meta.get("contentDigest"),
                parseTemporalMapping(meta.get("temporalMapping"), null)));
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
                clips.add(applyClipFieldOp(c, field, afterValue, op));
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
                cap.captionId(), cap.start(), cap.end(), newText, cap.style(), cap.safeMetadata());
        List<CanonicalTimelineCaptionSnapshot> captions = s.captions().stream()
                .map(c -> c.captionId().equals(capId) ? updated : c).collect(Collectors.toList());
        return ok(new CanonicalTimelineSnapshot(s.id(), s.revisionId(), s.duration(),
                s.tracks(), captions, s.watermarks(), s.templateApplications(),
                s.workflowSteps(), s.outputProfile(), s.safeMetadata(), s.textElements(), s.transitions(), s.automations(), s.audioMix(), s.semanticRelationships()));
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
        return ok(new CanonicalTimelineSnapshot(s.id(), s.revisionId(), s.duration(),
                s.tracks(), s.captions(), wms, s.templateApplications(),
                s.workflowSteps(), s.outputProfile(), s.safeMetadata(), s.textElements(), s.transitions(), s.automations(), s.audioMix(), s.semanticRelationships()));
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
        return ok(new CanonicalTimelineSnapshot(s.id(), s.revisionId(), s.duration(),
                s.tracks(), s.captions(), s.watermarks(), apps,
                s.workflowSteps(), s.outputProfile(), s.safeMetadata(), s.textElements(), s.transitions(), s.automations(), s.audioMix(), s.semanticRelationships()));
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
        return ok(new CanonicalTimelineSnapshot(s.id(), s.revisionId(), s.duration(),
                s.tracks(), s.captions(), s.watermarks(), apps,
                s.workflowSteps(), s.outputProfile(), s.safeMetadata(), s.textElements(), s.transitions(), s.automations(), s.audioMix(), s.semanticRelationships()));
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
        return ok(new CanonicalTimelineSnapshot(s.id(), s.revisionId(), s.duration(),
                s.tracks(), s.captions(), s.watermarks(), s.templateApplications(),
                steps, s.outputProfile(), s.safeMetadata(), s.textElements(), s.transitions(), s.automations(), s.audioMix(), s.semanticRelationships()));
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
        return ok(new CanonicalTimelineSnapshot(s.id(), s.revisionId(), s.duration(),
                s.tracks(), s.captions(), s.watermarks(), s.templateApplications(),
                s.workflowSteps(), profile, s.safeMetadata(), s.textElements(), s.transitions(), s.automations(), s.audioMix(), s.semanticRelationships()));
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
        return ok(new CanonicalTimelineSnapshot(s.id(), s.revisionId(), s.duration(),
                s.tracks(), s.captions(), s.watermarks(), s.templateApplications(),
                s.workflowSteps(), s.outputProfile(), meta, s.textElements(),
                s.transitions(), s.automations(), s.audioMix(), s.semanticRelationships()));
    }

    // --- EFFECT / TRANSITION / AUTOMATION apply
    //     (EFFECT_TRANSITION_CANONICALIZATION_V1 second correction: production
    //     patch path materializes typed semantic changes from op safeMetadata.) ---

    private TimelinePatchApplicationResult applyEffectChanged(CanonicalTimelineSnapshot s, TimelineChangeOperation op) {
        String trackId = extractTrackId(op.path().value());
        String clipId = extractId(op.path().value(), "timeline.tracks." + trackId + ".clips.");
        Optional<CanonicalTimelineTrackSnapshot> trackOpt = findTrack(s, trackId);
        if (trackOpt.isEmpty()) return fail(TimelinePatchApplicationIssueCode.TARGET_NOT_FOUND,
                op.path().value(), "Track not found");
        CanonicalTimelineTrackSnapshot track = trackOpt.get();
        List<CanonicalTimelineClipSnapshot> clips = new ArrayList<>(track.clips());
        for (int i = 0; i < clips.size(); i++) {
            CanonicalTimelineClipSnapshot c = clips.get(i);
            if (c.clipId().equals(clipId)) {
                Map<String, String> meta = op.safeMetadata() != null ? op.safeMetadata() : Map.of();
                clips.set(i, new CanonicalTimelineClipSnapshot(c.clipId(), c.assetBindingId(),
                        c.start(), c.duration(), c.sourceStart(), c.sourceDuration(), c.rate(),
                        parseEffects(meta.get("effects")), c.safeMetadata(),
                        c.sourceKind(), c.mediaStreamId(), c.artifactId(), c.contentDigest(), c.temporalMapping()));
                return ok(withUpdatedTrack(s, trackId, newTrackWithClips(track, clips)));
            }
        }
        return fail(TimelinePatchApplicationIssueCode.TARGET_NOT_FOUND, op.path().value(), "Clip not found");
    }

    /**
     * ROADMAP #19 — TimedText change application. Delegates ALL TextElement
     * field semantics to the local TimedTextCanonicalSemantics authority:
     * the op's afterValue is the canonical fingerprint payload (decode →
     * replace id-matched element; null afterValue = deletion).
     */
    private TimelinePatchApplicationResult applyTextElementChanged(CanonicalTimelineSnapshot s, TimelineChangeOperation op) {
        String elementId = op.path().value().substring("timeline.textElements.".length());
        Map<String, String> meta = op.safeMetadata() != null ? op.safeMetadata() : Map.of();
        List<com.example.platform.timeline.canonical.TextElement> elements =
                new ArrayList<>(s.textElements());
        if ("true".equals(meta.get("deleted"))) {
            boolean removed = elements.removeIf(e -> e.id().value().equals(elementId));
            if (!removed) {
                return fail(TimelinePatchApplicationIssueCode.TARGET_NOT_FOUND, op.path().value(),
                        "TextElement not found for deletion");
            }
            return ok(s.withTextElements(elements));
        }
        String afterValue = op.afterValue() != null ? op.afterValue().stringValue() : null;
        if (afterValue == null) {
            return fail(TimelinePatchApplicationIssueCode.INVALID_PAYLOAD, op.path().value(),
                    "TextElement change requires a canonical after payload");
        }
        List<com.example.platform.timeline.canonical.TextElement> decoded =
                com.example.platform.timeline.canonical.TimedTextCanonicalSemantics.decodeElements(
                        "[" + afterValue + "]");
        if (decoded.size() != 1) {
            return fail(TimelinePatchApplicationIssueCode.INVALID_PAYLOAD, op.path().value(),
                    "TextElement canonical payload must decode to exactly one element");
        }
        com.example.platform.timeline.canonical.TextElement replacement = decoded.get(0);
        if (!replacement.id().value().equals(elementId)) {
            return fail(TimelinePatchApplicationIssueCode.INVALID_PAYLOAD, op.path().value(),
                    "TextElement payload id does not match change path");
        }
        boolean replaced = false;
        for (int i = 0; i < elements.size(); i++) {
            if (elements.get(i).id().value().equals(elementId)) {
                elements.set(i, replacement);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            // NEW element (added) — append, preserving collection ordering semantics.
            elements.add(replacement);
        }
        return ok(s.withTextElements(elements));
    }

    private TimelinePatchApplicationResult applyTransitionChanged(CanonicalTimelineSnapshot s, TimelineChangeOperation op) {
        String transitionId = op.path().value().substring("timeline.transitions.".length());
        Map<String, String> meta = op.safeMetadata() != null ? op.safeMetadata() : Map.of();
        // THIRD CORRECTION: deletion is first-class — "deleted" flag removes the
        // transition; empty after-state is meaningful, never target resurrection.
        if ("true".equals(meta.get("deleted"))) {
            List<CanonicalTimelineTransitionSnapshot> transitions = new ArrayList<>(s.transitions());
            boolean removed = transitions.removeIf(t -> t.transitionId().equals(transitionId));
            if (!removed) {
                return fail(TimelinePatchApplicationIssueCode.TARGET_NOT_FOUND,
                        op.path().value(), "Transition not found for deletion");
            }
            return ok(s.withTransitions(List.copyOf(transitions)));
        }
        MediaTime duration = MediaTime.ofTicks(parseLong(meta.get("durationTicks"), 0),
                parseLong(meta.get("durationTimeScale"), 1));
        Map<String, String> params = new LinkedHashMap<>();
        String paramsEnc = meta.get("parameters");
        if (paramsEnc != null && !paramsEnc.isBlank()) {
            // CHECKPOINT_A Round 3: Transition-local canonical JSON authority —
            // no delimiter grammar, no "a,b=c" collision. Malformed payload is
            // a reconstruction failure, never a silent blank.
            com.fasterxml.jackson.databind.JsonNode enc;
            try {
                enc = com.example.platform.timeline.app.InternalTimelineJson.mapper()
                        .readTree(paramsEnc);
            } catch (Exception e) {
                return fail(TimelinePatchApplicationIssueCode.INVALID_PAYLOAD, op.path().value(),
                        "TRANSITION_CHANGED parameters not canonical JSON");
            }
            com.example.platform.timeline.semantics.transition.TransitionCanonicalSemantics
                    .fromCanonicalValue(transitionId, enc).parameters()
                    .forEach(params::put);
        }
        CanonicalTimelineTransitionSnapshot updated = new CanonicalTimelineTransitionSnapshot(
                transitionId,
                meta.getOrDefault("transitionDefinitionId", ""),
                meta.getOrDefault("transitionDefinitionVersion", "1.0"),
                meta.getOrDefault("outgoingClipId", ""),
                meta.getOrDefault("incomingClipId", ""),
                meta.getOrDefault("mediaType", "VIDEO"),
                duration,
                meta.getOrDefault("alignment", "CENTER_ON_CUT"),
                meta.getOrDefault("temporalPolicy", "USE_SOURCE_HANDLES"),
                params);
        List<CanonicalTimelineTransitionSnapshot> transitions = new ArrayList<>(s.transitions());
        boolean replaced = false;
        for (int i = 0; i < transitions.size(); i++) {
            if (transitions.get(i).transitionId().equals(transitionId)) {
                transitions.set(i, updated);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            // Transition was added on this side.
            transitions.add(updated);
        }
        return ok(new CanonicalTimelineSnapshot(s.id(), s.revisionId(), s.duration(),
                s.tracks(), s.captions(), s.watermarks(), s.templateApplications(),
                s.workflowSteps(), s.outputProfile(), s.safeMetadata(), s.textElements(),
                List.copyOf(transitions), s.automations(), s.audioMix(), s.semanticRelationships()));
    }

    private TimelinePatchApplicationResult applyAutomationChanged(CanonicalTimelineSnapshot s, TimelineChangeOperation op) {
        String automationId = op.path().value().substring("timeline.automations.".length());
        Map<String, String> meta = op.safeMetadata() != null ? op.safeMetadata() : Map.of();
        // THIRD CORRECTION: deletion is first-class — "deleted" flag removes the
        // automation; empty after-state is meaningful, never target resurrection.
        if ("true".equals(meta.get("deleted"))) {
            List<CanonicalTimelineAutomationSnapshot> automations = new ArrayList<>(s.automations());
            boolean removed = automations.removeIf(a -> a.automationId().equals(automationId));
            if (!removed) {
                return fail(TimelinePatchApplicationIssueCode.TARGET_NOT_FOUND,
                        op.path().value(), "Automation not found for deletion");
            }
            return ok(s.withAutomations(List.copyOf(automations)));
        }
        // CHECKPOINT_A Round 3: Automation-local canonical JSON authority — no
        // delimiter grammar, no collision on authored strings.
        CanonicalTimelineAutomationSnapshot updated;
        String kfEnc = meta.get("keyframes");
        if (kfEnc != null && !kfEnc.isBlank()) {
            com.fasterxml.jackson.databind.JsonNode enc;
            try {
                enc = com.example.platform.timeline.app.InternalTimelineJson.mapper().readTree(kfEnc);
            } catch (Exception e) {
                return fail(TimelinePatchApplicationIssueCode.INVALID_PAYLOAD, op.path().value(),
                        "AUTOMATION_CHANGED keyframes not canonical JSON");
            }
            updated = com.example.platform.timeline.semantics.automation.AutomationCanonicalSemantics
                    .fromCanonicalValue(automationId, enc);
        } else {
            updated = new CanonicalTimelineAutomationSnapshot(
                    automationId,
                    meta.getOrDefault("targetEntityId", ""),
                    meta.getOrDefault("parameterPath", ""),
                    meta.getOrDefault("valueType", "float"),
                    meta.getOrDefault("extrapolation", "HOLD"),
                    new ArrayList<>());
        }
        List<CanonicalTimelineAutomationSnapshot> automations = new ArrayList<>(s.automations());
        boolean replaced = false;
        for (int i = 0; i < automations.size(); i++) {
            if (automations.get(i).automationId().equals(automationId)) {
                automations.set(i, updated);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            automations.add(updated);
        }
        return ok(new CanonicalTimelineSnapshot(s.id(), s.revisionId(), s.duration(),
                s.tracks(), s.captions(), s.watermarks(), s.templateApplications(),
                s.workflowSteps(), s.outputProfile(), s.safeMetadata(), s.textElements(),
                s.transitions(), List.copyOf(automations), s.audioMix(), s.semanticRelationships()));
    }

    // --- Helpers ---

    private com.example.platform.timeline.semantics.temporal.TemporalMapping parseTemporalMapping(
            String enc, com.example.platform.timeline.semantics.temporal.TemporalMapping current) {
        if (enc == null || enc.isBlank()) {
            return current;
        }
        try {
            return com.example.platform.timeline.app.InternalTimelineJson.mapper()
                    .readValue(enc, com.example.platform.timeline.semantics.temporal.TemporalMapping.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Malformed TemporalMapping payload: " + e.getMessage());
        }
    }

    private CanonicalTimelineClipSnapshot applyClipFieldOp(
            CanonicalTimelineClipSnapshot c, String field, String afterValue, TimelineChangeOperation op) {
        return switch (field) {
            case "start" -> new CanonicalTimelineClipSnapshot(c.clipId(), c.assetBindingId(),
                    parseMediaTime(afterValue, c.start()), c.duration(), c.sourceStart(), c.sourceDuration(),
                    c.rate(), c.effects(), c.safeMetadata(), c.sourceKind(), c.mediaStreamId(), c.artifactId(), c.contentDigest(), c.temporalMapping());
            case "duration" -> new CanonicalTimelineClipSnapshot(c.clipId(), c.assetBindingId(),
                    c.start(), parseMediaTime(afterValue, c.duration()), c.sourceStart(),
                    parseMediaTime(afterValue, c.sourceDuration()),
                    c.rate(), c.effects(), c.safeMetadata(), c.sourceKind(), c.mediaStreamId(), c.artifactId(), c.contentDigest(), c.temporalMapping());
            case "rate" -> new CanonicalTimelineClipSnapshot(c.clipId(), c.assetBindingId(),
                    c.start(), c.duration(), c.sourceStart(), c.sourceDuration(),
                    parseFrameRate(afterValue, c.rate()), c.effects(), c.safeMetadata(),
                    c.sourceKind(), c.mediaStreamId(), c.artifactId(), c.contentDigest(), c.temporalMapping());
            case "temporalMapping" -> new CanonicalTimelineClipSnapshot(c.clipId(), c.assetBindingId(),
                    c.start(), c.duration(), c.sourceStart(), c.sourceDuration(),
                    c.rate(), c.effects(), c.safeMetadata(),
                    c.sourceKind(), c.mediaStreamId(), c.artifactId(), c.contentDigest(),
                    parseTemporalMapping(afterValue, c.temporalMapping()));
            case "sourceSemantics" -> {
                Map<String, String> sm = op.safeMetadata() != null ? op.safeMetadata() : Map.of();
                yield new CanonicalTimelineClipSnapshot(c.clipId(), c.assetBindingId(),
                        c.start(), c.duration(), c.sourceStart(), c.sourceDuration(),
                        c.rate(), c.effects(), c.safeMetadata(),
                        sm.get("sourceKind"), sm.get("mediaStreamId"), sm.get("artifactId"), sm.get("contentDigest"),
                        c.temporalMapping());
            }
            case "assetBindingId" -> new CanonicalTimelineClipSnapshot(c.clipId(),
                    afterValue != null ? afterValue : c.assetBindingId(),
                    c.start(), c.duration(), c.sourceStart(), c.sourceDuration(),
                    c.rate(), c.effects(), c.safeMetadata(), c.sourceKind(), c.mediaStreamId(), c.artifactId(), c.contentDigest(), c.temporalMapping());
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

    /** Exact MediaTime from canonical "ticks/timeScale" string (falls back to default). */
    private MediaTime parseMediaTime(Object v, MediaTime def) {
        if (v == null) return def;
        try { return MediaTime.parse(v.toString()); } catch (Exception e) { return def; }
    }

    /**
     * FIFTH CORRECTION: deserialize the canonical Effect encoding produced by
     * the diff (EffectCanonicalSemantics.encodeEffects). Delegates to the
     * single local Effect semantic codec authority — no independent grammar.
     */
    private List<com.example.platform.timeline.canonicalmodel.TimelineClipEffect> parseEffects(String encoded) {
        return EffectCanonicalSemantics.decodeEffects(encoded);
    }

    /** Exact FrameRate from canonical "num/den" form; falls back to current value. */
    private FrameRate parseFrameRate(Object v, FrameRate def) {
        if (v == null) return def;
        try {
            String s = v.toString().trim();
            int slash = s.indexOf('/');
            if (slash < 0) {
                return FrameRate.of(Long.parseLong(s), 1);
            }
            return FrameRate.of(Long.parseLong(s.substring(0, slash).trim()),
                    Long.parseLong(s.substring(slash + 1).trim()));
        } catch (Exception e) { return def; }
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
        return new CanonicalTimelineSnapshot(s.id(), s.revisionId(), s.duration(),
                tracks, s.captions(), s.watermarks(), s.templateApplications(),
                s.workflowSteps(), s.outputProfile(), s.safeMetadata(), s.textElements(), s.transitions(), s.automations(), s.audioMix(), s.semanticRelationships());
    }

    private CanonicalTimelineSnapshot withUpdatedTrack(CanonicalTimelineSnapshot s, String trackId, CanonicalTimelineTrackSnapshot updated) {
        List<CanonicalTimelineTrackSnapshot> tracks = s.tracks().stream()
                .map(t -> t.trackId().equals(trackId) ? updated : t).collect(Collectors.toList());
        return withTracks(s, tracks);
    }
}
