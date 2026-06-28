# Canonical Timeline Diff Calculator v0 (P2V.1)

## Purpose

Platform-owned deterministic diff calculator comparing canonical timeline snapshots. Produces TimelineDiff operations without vedit, OTIO, StorageRuntime, ProductRuntime, or render pipeline.

## Package Placement

`render-module/.../domain/timeline/diff/calculation/` — 11 types.

## Snapshot Input Model

| Type | Purpose |
|------|---------|
| CanonicalTimelineSnapshotId | Snapshot identity |
| CanonicalTimelineSnapshot | Top-level: revisionId, durationMs, tracks, captions, watermarks, templates, workflowSteps, outputProfile |
| CanonicalTimelineTrackSnapshot | trackId, order, kind, clips |
| CanonicalTimelineClipSnapshot | clipId, assetBindingId, startMs, durationMs, sourceStartMs, sourceDurationMs |
| CanonicalTimelineCaptionSnapshot | captionId, startMs, endMs, text, style |
| CanonicalTimelineWatermarkSnapshot | watermarkId, assetBindingId, position, opacityPercent |
| CanonicalTimelineTemplateApplicationSnapshot | templateApplicationId, templateId, templateVersion, parameters |
| CanonicalTimelineWorkflowStepSnapshot | workflowStepId, stepType, templateApplicationId |
| CanonicalTimelineOutputProfileSnapshot | profileId, format, aspectRatio, width, height |

No provider/storage/backend fields. String maps only for safe metadata.

## Calculator Behavior

`CanonicalTimelineDiffCalculator.calculate(before, after)`:

1. Validates both snapshots present
2. Compares duration, tracks, clips, captions, watermarks, templates, workflow steps, output profile, metadata
3. Produces deterministic TimelineChangeOperation list
4. Estimates TimelineRenderImpact
5. Returns CanonicalTimelineDiffCalculationResult

## Change Mapping

| Snapshot Diff | TimelineChangeType | Scope |
|--------------|-------------------|-------|
| durationMs changed | TIMELINE_DURATION_CHANGED | TIMELINE |
| track added | TRACK_ADDED | TRACK |
| track removed | TRACK_REMOVED | TRACK |
| track order changed | TRACK_REORDERED | TRACK |
| clip added | CLIP_ADDED | CLIP |
| clip removed | CLIP_REMOVED | CLIP |
| clip startMs changed | CLIP_MOVED | CLIP |
| clip durationMs changed | CLIP_TRIMMED | CLIP |
| clip assetBindingId changed | ASSET_BINDING_CHANGED | ASSET_BINDING |
| caption text/timing changed | CAPTION_SEGMENT_CHANGED | CAPTION |
| caption style changed | TEXT_STYLE_CHANGED | TEXT_OVERLAY |
| watermark changed | WATERMARK_CHANGED | WATERMARK |
| template parameters changed | TEMPLATE_PARAMETER_CHANGED | TEMPLATE_APPLICATION |
| template id/version changed | TEMPLATE_PROFILE_CHANGED | TEMPLATE_APPLICATION |
| workflow step changed | WORKFLOW_APPLY_TEMPLATE_STEP_CHANGED | WORKFLOW_STEP |
| output profile changed | OUTPUT_PROFILE_CHANGED | OUTPUT_PROFILE |
| metadata changed | METADATA_CHANGED | METADATA |

## Deterministic Ordering

Operations sorted by type priority (TIMELINE_DURATION_CHANGED first, METADATA_CHANGED last), then by path.

## Render Impact Estimation

| Condition | Level |
|-----------|-------|
| No operations | NONE |
| Only METADATA_CHANGED | METADATA_ONLY |
| Caption/watermark/clip/template changes | PARTIAL_RERENDER |
| Duration/output profile/track add/remove | FULL_RERENDER |

## Safety

- No vedit/pyvedit/OpenTimelineIO dependency
- No provider/storage internals
- No Remotion references
- No patch application
- No merge
- No Timeline Git persistence
- No render pipeline calls

## Follow-up

- P2V.2: TimelinePatch Application Semantics
- P2V.3: Timeline Merge Conflict Taxonomy
