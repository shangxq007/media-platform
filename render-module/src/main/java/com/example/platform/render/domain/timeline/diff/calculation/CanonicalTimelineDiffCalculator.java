package com.example.platform.render.domain.timeline.diff.calculation;

import com.example.platform.render.domain.timeline.diff.*;
import java.util.*;

/**
 * Canonical Timeline Diff Calculator — compares two platform-owned timeline snapshots.
 *
 * <p>Internal domain service. Provider-neutral, storage-neutral, side-effect free.
 * Does not call vedit, OTIO, StorageRuntime, ProductRuntime, or render pipeline.</p>
 */
public class CanonicalTimelineDiffCalculator {

    /**
     * Calculate diff between two canonical timeline snapshots.
     */
    public CanonicalTimelineDiffCalculationResult calculate(
            CanonicalTimelineSnapshot before, CanonicalTimelineSnapshot after) {

        if (before == null || after == null) {
            return CanonicalTimelineDiffCalculationResult.failure("Both snapshots must be present");
        }

        List<TimelineChangeOperation> operations = new ArrayList<>();
        int[] opSeq = {0};

        // Duration
        if (before.durationMs() != after.durationMs()) {
            operations.add(change(opSeq, TimelineChangeType.TIMELINE_DURATION_CHANGED,
                    TimelineChangeScope.TIMELINE, "timeline.durationMs",
                    String.valueOf(before.durationMs()), String.valueOf(after.durationMs())));
        }

        // Tracks
        diffTracks(before, after, operations, opSeq);

        // Captions
        diffCaptions(before, after, operations, opSeq);

        // Watermarks
        diffWatermarks(before, after, operations, opSeq);

        // Template applications
        diffTemplateApplications(before, after, operations, opSeq);

        // Workflow steps
        diffWorkflowSteps(before, after, operations, opSeq);

        // Output profile
        diffOutputProfile(before, after, operations, opSeq);

        // Metadata
        diffMetadata(before, after, operations, opSeq);

        // Sort by type priority then path
        operations.sort(Comparator.comparingInt((TimelineChangeOperation op) -> typePriority(op.type()))
                .thenComparing(op -> op.path().value()));

        TimelineRenderImpact impact = estimateImpact(operations);

        TimelineDiff diff = new TimelineDiff(
                new TimelineDiffId("diff-" + before.revisionId() + "-" + after.revisionId()),
                before.revisionId(), after.revisionId(),
                operations, List.of(), impact, Map.of());

        return CanonicalTimelineDiffCalculationResult.success(diff);
    }

    // --- Track diff ---

    private void diffTracks(CanonicalTimelineSnapshot before, CanonicalTimelineSnapshot after,
                             List<TimelineChangeOperation> ops, int[] seq) {
        Map<String, CanonicalTimelineTrackSnapshot> beforeTracks = toMap(before.tracks());
        Map<String, CanonicalTimelineTrackSnapshot> afterTracks = toMap(after.tracks());

        // Removed tracks
        for (String id : beforeTracks.keySet()) {
            if (!afterTracks.containsKey(id)) {
                ops.add(change(seq, TimelineChangeType.TRACK_REMOVED,
                        TimelineChangeScope.TRACK, "timeline.tracks." + id,
                        id, null));
            }
        }

        // Added tracks
        for (String id : afterTracks.keySet()) {
            if (!beforeTracks.containsKey(id)) {
                ops.add(change(seq, TimelineChangeType.TRACK_ADDED,
                        TimelineChangeScope.TRACK, "timeline.tracks." + id,
                        null, id));
            }
        }

        // Modified tracks
        for (String id : beforeTracks.keySet()) {
            if (afterTracks.containsKey(id)) {
                CanonicalTimelineTrackSnapshot bt = beforeTracks.get(id);
                CanonicalTimelineTrackSnapshot at = afterTracks.get(id);

                // Track reordered
                if (bt.order() != at.order()) {
                    ops.add(change(seq, TimelineChangeType.TRACK_REORDERED,
                            TimelineChangeScope.TRACK, "timeline.tracks." + id + ".order",
                            String.valueOf(bt.order()), String.valueOf(at.order())));
                }

                // Clip diffs within track
                diffClips(bt, at, ops, seq);
            }
        }
    }

    // --- Clip diff ---

    private void diffClips(CanonicalTimelineTrackSnapshot beforeTrack,
                            CanonicalTimelineTrackSnapshot afterTrack,
                            List<TimelineChangeOperation> ops, int[] seq) {
        Map<String, CanonicalTimelineClipSnapshot> beforeClips = toClipMap(beforeTrack.clips());
        Map<String, CanonicalTimelineClipSnapshot> afterClips = toClipMap(afterTrack.clips());
        String trackPath = "timeline.tracks." + beforeTrack.trackId() + ".clips";

        // Removed
        for (String id : beforeClips.keySet()) {
            if (!afterClips.containsKey(id)) {
                ops.add(change(seq, TimelineChangeType.CLIP_REMOVED,
                        TimelineChangeScope.CLIP, trackPath + "." + id, id, null));
            }
        }

        // Added
        for (String id : afterClips.keySet()) {
            if (!beforeClips.containsKey(id)) {
                ops.add(change(seq, TimelineChangeType.CLIP_ADDED,
                        TimelineChangeScope.CLIP, trackPath + "." + id, null, id));
            }
        }

        // Modified
        for (String id : beforeClips.keySet()) {
            if (afterClips.containsKey(id)) {
                CanonicalTimelineClipSnapshot bc = beforeClips.get(id);
                CanonicalTimelineClipSnapshot ac = afterClips.get(id);
                String clipPath = trackPath + "." + id;

                if (bc.startMs() != ac.startMs()) {
                    ops.add(change(seq, TimelineChangeType.CLIP_MOVED,
                            TimelineChangeScope.CLIP, clipPath + ".startMs",
                            String.valueOf(bc.startMs()), String.valueOf(ac.startMs())));
                }
                if (bc.durationMs() != ac.durationMs()
                        || bc.sourceStartMs() != ac.sourceStartMs()
                        || bc.sourceDurationMs() != ac.sourceDurationMs()) {
                    ops.add(change(seq, TimelineChangeType.CLIP_TRIMMED,
                            TimelineChangeScope.CLIP, clipPath + ".durationMs",
                            String.valueOf(bc.durationMs()), String.valueOf(ac.durationMs())));
                }
                if (!Objects.equals(bc.assetBindingId(), ac.assetBindingId())) {
                    ops.add(change(seq, TimelineChangeType.ASSET_BINDING_CHANGED,
                            TimelineChangeScope.ASSET_BINDING, clipPath + ".assetBindingId",
                            bc.assetBindingId(), ac.assetBindingId()));
                }
            }
        }
    }

    // --- Caption diff ---

    private void diffCaptions(CanonicalTimelineSnapshot before, CanonicalTimelineSnapshot after,
                               List<TimelineChangeOperation> ops, int[] seq) {
        Map<String, CanonicalTimelineCaptionSnapshot> beforeCaptions = toCaptionMap(before.captions());
        Map<String, CanonicalTimelineCaptionSnapshot> afterCaptions = toCaptionMap(after.captions());

        for (String id : beforeCaptions.keySet()) {
            CanonicalTimelineCaptionSnapshot bc = beforeCaptions.get(id);
            if (!afterCaptions.containsKey(id)) {
                ops.add(change(seq, TimelineChangeType.CAPTION_SEGMENT_CHANGED,
                        TimelineChangeScope.CAPTION, "timeline.captions." + id,
                        bc.text(), null));
            } else {
                CanonicalTimelineCaptionSnapshot ac = afterCaptions.get(id);
                if (!Objects.equals(bc.text(), ac.text())
                        || bc.startMs() != ac.startMs()
                        || bc.endMs() != ac.endMs()) {
                    ops.add(change(seq, TimelineChangeType.CAPTION_SEGMENT_CHANGED,
                            TimelineChangeScope.CAPTION, "timeline.captions." + id + ".text",
                            bc.text(), ac.text()));
                }
                if (!Objects.equals(bc.style(), ac.style())) {
                    ops.add(change(seq, TimelineChangeType.TEXT_STYLE_CHANGED,
                            TimelineChangeScope.TEXT_OVERLAY, "timeline.captions." + id + ".style",
                            String.valueOf(bc.style()), String.valueOf(ac.style())));
                }
            }
        }
        for (String id : afterCaptions.keySet()) {
            if (!beforeCaptions.containsKey(id)) {
                CanonicalTimelineCaptionSnapshot ac = afterCaptions.get(id);
                ops.add(change(seq, TimelineChangeType.CAPTION_SEGMENT_CHANGED,
                        TimelineChangeScope.CAPTION, "timeline.captions." + id,
                        null, ac.text()));
            }
        }
    }

    // --- Watermark diff ---

    private void diffWatermarks(CanonicalTimelineSnapshot before, CanonicalTimelineSnapshot after,
                                 List<TimelineChangeOperation> ops, int[] seq) {
        Map<String, CanonicalTimelineWatermarkSnapshot> beforeWm = toWatermarkMap(before.watermarks());
        Map<String, CanonicalTimelineWatermarkSnapshot> afterWm = toWatermarkMap(after.watermarks());

        for (String id : beforeWm.keySet()) {
            CanonicalTimelineWatermarkSnapshot bw = beforeWm.get(id);
            if (!afterWm.containsKey(id)) {
                ops.add(change(seq, TimelineChangeType.WATERMARK_CHANGED,
                        TimelineChangeScope.WATERMARK, "timeline.watermarks." + id,
                        bw.watermarkId(), null));
            } else {
                CanonicalTimelineWatermarkSnapshot aw = afterWm.get(id);
                if (!Objects.equals(bw.assetBindingId(), aw.assetBindingId())
                        || !Objects.equals(bw.position(), aw.position())
                        || bw.opacityPercent() != aw.opacityPercent()) {
                    ops.add(change(seq, TimelineChangeType.WATERMARK_CHANGED,
                            TimelineChangeScope.WATERMARK, "timeline.watermarks." + id,
                            bw.position() + ":" + bw.opacityPercent(),
                            aw.position() + ":" + aw.opacityPercent()));
                }
            }
        }
        for (String id : afterWm.keySet()) {
            if (!beforeWm.containsKey(id)) {
                ops.add(change(seq, TimelineChangeType.WATERMARK_CHANGED,
                        TimelineChangeScope.WATERMARK, "timeline.watermarks." + id,
                        null, afterWm.get(id).watermarkId()));
            }
        }
    }

    // --- Template application diff ---

    private void diffTemplateApplications(CanonicalTimelineSnapshot before, CanonicalTimelineSnapshot after,
                                           List<TimelineChangeOperation> ops, int[] seq) {
        Map<String, CanonicalTimelineTemplateApplicationSnapshot> beforeTa = toTemplateMap(before.templateApplications());
        Map<String, CanonicalTimelineTemplateApplicationSnapshot> afterTa = toTemplateMap(after.templateApplications());

        for (String id : beforeTa.keySet()) {
            CanonicalTimelineTemplateApplicationSnapshot bt = beforeTa.get(id);
            if (!afterTa.containsKey(id)) {
                ops.add(change(seq, TimelineChangeType.TEMPLATE_PARAMETER_CHANGED,
                        TimelineChangeScope.TEMPLATE_APPLICATION,
                        "timeline.templateApplications." + id, bt.templateId(), null));
            } else {
                CanonicalTimelineTemplateApplicationSnapshot at = afterTa.get(id);
                if (!Objects.equals(bt.templateId(), at.templateId())
                        || !Objects.equals(bt.templateVersion(), at.templateVersion())) {
                    ops.add(change(seq, TimelineChangeType.TEMPLATE_PROFILE_CHANGED,
                            TimelineChangeScope.TEMPLATE_APPLICATION,
                            "timeline.templateApplications." + id + ".templateId",
                            bt.templateId(), at.templateId()));
                }
                if (!Objects.equals(bt.parameters(), at.parameters())) {
                    ops.add(change(seq, TimelineChangeType.TEMPLATE_PARAMETER_CHANGED,
                            TimelineChangeScope.TEMPLATE_APPLICATION,
                            "timeline.templateApplications." + id + ".parameters",
                            String.valueOf(bt.parameters()), String.valueOf(at.parameters())));
                }
            }
        }
        for (String id : afterTa.keySet()) {
            if (!beforeTa.containsKey(id)) {
                ops.add(change(seq, TimelineChangeType.TEMPLATE_PARAMETER_CHANGED,
                        TimelineChangeScope.TEMPLATE_APPLICATION,
                        "timeline.templateApplications." + id, null, afterTa.get(id).templateId()));
            }
        }
    }

    // --- Workflow step diff ---

    private void diffWorkflowSteps(CanonicalTimelineSnapshot before, CanonicalTimelineSnapshot after,
                                    List<TimelineChangeOperation> ops, int[] seq) {
        Map<String, CanonicalTimelineWorkflowStepSnapshot> beforeWs = toWorkflowMap(before.workflowSteps());
        Map<String, CanonicalTimelineWorkflowStepSnapshot> afterWs = toWorkflowMap(after.workflowSteps());

        for (String id : beforeWs.keySet()) {
            CanonicalTimelineWorkflowStepSnapshot bw = beforeWs.get(id);
            if (!afterWs.containsKey(id)) {
                ops.add(change(seq, TimelineChangeType.WORKFLOW_APPLY_TEMPLATE_STEP_CHANGED,
                        TimelineChangeScope.WORKFLOW_STEP,
                        "timeline.workflowSteps." + id, bw.stepType(), null));
            } else {
                CanonicalTimelineWorkflowStepSnapshot aw = afterWs.get(id);
                if (!Objects.equals(bw.stepType(), aw.stepType())
                        || !Objects.equals(bw.templateApplicationId(), aw.templateApplicationId())) {
                    ops.add(change(seq, TimelineChangeType.WORKFLOW_APPLY_TEMPLATE_STEP_CHANGED,
                            TimelineChangeScope.WORKFLOW_STEP,
                            "timeline.workflowSteps." + id,
                            bw.templateApplicationId(), aw.templateApplicationId()));
                }
            }
        }
        for (String id : afterWs.keySet()) {
            if (!beforeWs.containsKey(id)) {
                ops.add(change(seq, TimelineChangeType.WORKFLOW_APPLY_TEMPLATE_STEP_CHANGED,
                        TimelineChangeScope.WORKFLOW_STEP,
                        "timeline.workflowSteps." + id, null, afterWs.get(id).stepType()));
            }
        }
    }

    // --- Output profile diff ---

    private void diffOutputProfile(CanonicalTimelineSnapshot before, CanonicalTimelineSnapshot after,
                                    List<TimelineChangeOperation> ops, int[] seq) {
        CanonicalTimelineOutputProfileSnapshot bp = before.outputProfile();
        CanonicalTimelineOutputProfileSnapshot ap = after.outputProfile();
        if (bp == null && ap == null) return;
        if (bp == null || ap == null) {
            ops.add(change(seq, TimelineChangeType.OUTPUT_PROFILE_CHANGED,
                    TimelineChangeScope.OUTPUT_PROFILE, "timeline.outputProfile",
                    String.valueOf(bp), String.valueOf(ap)));
            return;
        }
        if (bp.width() != ap.width() || bp.height() != ap.height()
                || !Objects.equals(bp.format(), ap.format())
                || !Objects.equals(bp.aspectRatio(), ap.aspectRatio())) {
            ops.add(change(seq, TimelineChangeType.OUTPUT_PROFILE_CHANGED,
                    TimelineChangeScope.OUTPUT_PROFILE, "timeline.outputProfile",
                    bp.width() + "x" + bp.height(), ap.width() + "x" + ap.height()));
        }
    }

    // --- Metadata diff ---

    private void diffMetadata(CanonicalTimelineSnapshot before, CanonicalTimelineSnapshot after,
                               List<TimelineChangeOperation> ops, int[] seq) {
        Map<String, String> bm = before.safeMetadata() != null ? before.safeMetadata() : Map.of();
        Map<String, String> am = after.safeMetadata() != null ? after.safeMetadata() : Map.of();
        if (!bm.equals(am)) {
            ops.add(change(seq, TimelineChangeType.METADATA_CHANGED,
                    TimelineChangeScope.METADATA, "timeline.metadata",
                    String.valueOf(bm), String.valueOf(am)));
        }
    }

    // --- Impact estimation ---

    private TimelineRenderImpact estimateImpact(List<TimelineChangeOperation> ops) {
        if (ops.isEmpty()) return new TimelineRenderImpact(
                TimelineRenderImpactLevel.NONE, List.of(), List.of(), Map.of());

        boolean hasFullRerender = ops.stream().anyMatch(op ->
                op.type() == TimelineChangeType.TIMELINE_DURATION_CHANGED
                || op.type() == TimelineChangeType.OUTPUT_PROFILE_CHANGED
                || op.type() == TimelineChangeType.TRACK_ADDED
                || op.type() == TimelineChangeType.TRACK_REMOVED);

        boolean hasPartialRerender = ops.stream().anyMatch(op ->
                op.type() == TimelineChangeType.CLIP_ADDED
                || op.type() == TimelineChangeType.CLIP_REMOVED
                || op.type() == TimelineChangeType.CLIP_MOVED
                || op.type() == TimelineChangeType.CLIP_TRIMMED
                || op.type() == TimelineChangeType.CAPTION_SEGMENT_CHANGED
                || op.type() == TimelineChangeType.TEXT_STYLE_CHANGED
                || op.type() == TimelineChangeType.WATERMARK_CHANGED
                || op.type() == TimelineChangeType.TEMPLATE_PARAMETER_CHANGED
                || op.type() == TimelineChangeType.TEMPLATE_PROFILE_CHANGED);

        boolean metadataOnly = ops.stream().allMatch(op ->
                op.type() == TimelineChangeType.METADATA_CHANGED);

        TimelineRenderImpactLevel level;
        if (metadataOnly) level = TimelineRenderImpactLevel.METADATA_ONLY;
        else if (hasFullRerender) level = TimelineRenderImpactLevel.FULL_RERENDER;
        else if (hasPartialRerender) level = TimelineRenderImpactLevel.PARTIAL_RERENDER;
        else level = TimelineRenderImpactLevel.NONE;

        List<String> affectedPaths = ops.stream()
                .map(op -> op.path().value()).distinct().toList();

        return new TimelineRenderImpact(level, affectedPaths, List.of(), Map.of());
    }

    // --- Priority ordering ---

    private int typePriority(TimelineChangeType type) {
        return switch (type) {
            case TIMELINE_DURATION_CHANGED -> 1;
            case TRACK_REMOVED -> 2;
            case TRACK_ADDED -> 3;
            case TRACK_REORDERED -> 4;
            case CLIP_REMOVED -> 5;
            case CLIP_ADDED -> 6;
            case CLIP_MOVED -> 7;
            case CLIP_TRIMMED -> 8;
            case ASSET_BINDING_CHANGED -> 9;
            case CAPTION_SEGMENT_CHANGED -> 10;
            case TEXT_STYLE_CHANGED -> 11;
            case WATERMARK_CHANGED -> 12;
            case TEMPLATE_PROFILE_CHANGED -> 13;
            case TEMPLATE_PARAMETER_CHANGED -> 14;
            case WORKFLOW_APPLY_TEMPLATE_STEP_CHANGED -> 15;
            case OUTPUT_PROFILE_CHANGED -> 16;
            case METADATA_CHANGED -> 17;
            default -> 99;
        };
    }

    // --- Helpers ---

    private TimelineChangeOperation change(int[] seq, TimelineChangeType type,
                                             TimelineChangeScope scope, String path,
                                             String before, String after) {
        return new TimelineChangeOperation(
                new TimelineChangeOperationId("op-" + (++seq[0])),
                type, scope, new TimelineChangePath(path),
                before != null ? TimelineChangePayload.ofString(before) : TimelineChangePayload.empty(),
                after != null ? TimelineChangePayload.ofString(after) : TimelineChangePayload.empty(),
                Map.of());
    }

    private Map<String, CanonicalTimelineTrackSnapshot> toMap(List<CanonicalTimelineTrackSnapshot> tracks) {
        Map<String, CanonicalTimelineTrackSnapshot> map = new LinkedHashMap<>();
        if (tracks != null) tracks.forEach(t -> map.put(t.trackId(), t));
        return map;
    }

    private Map<String, CanonicalTimelineClipSnapshot> toClipMap(List<CanonicalTimelineClipSnapshot> clips) {
        Map<String, CanonicalTimelineClipSnapshot> map = new LinkedHashMap<>();
        if (clips != null) clips.forEach(c -> map.put(c.clipId(), c));
        return map;
    }

    private Map<String, CanonicalTimelineCaptionSnapshot> toCaptionMap(List<CanonicalTimelineCaptionSnapshot> captions) {
        Map<String, CanonicalTimelineCaptionSnapshot> map = new LinkedHashMap<>();
        if (captions != null) captions.forEach(c -> map.put(c.captionId(), c));
        return map;
    }

    private Map<String, CanonicalTimelineWatermarkSnapshot> toWatermarkMap(List<CanonicalTimelineWatermarkSnapshot> watermarks) {
        Map<String, CanonicalTimelineWatermarkSnapshot> map = new LinkedHashMap<>();
        if (watermarks != null) watermarks.forEach(w -> map.put(w.watermarkId(), w));
        return map;
    }

    private Map<String, CanonicalTimelineTemplateApplicationSnapshot> toTemplateMap(List<CanonicalTimelineTemplateApplicationSnapshot> apps) {
        Map<String, CanonicalTimelineTemplateApplicationSnapshot> map = new LinkedHashMap<>();
        if (apps != null) apps.forEach(a -> map.put(a.templateApplicationId(), a));
        return map;
    }

    private Map<String, CanonicalTimelineWorkflowStepSnapshot> toWorkflowMap(List<CanonicalTimelineWorkflowStepSnapshot> steps) {
        Map<String, CanonicalTimelineWorkflowStepSnapshot> map = new LinkedHashMap<>();
        if (steps != null) steps.forEach(s -> map.put(s.workflowStepId(), s));
        return map;
    }
}
