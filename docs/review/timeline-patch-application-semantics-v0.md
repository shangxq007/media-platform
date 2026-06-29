# Timeline Patch Application Semantics v0 (P2V.2)

## Purpose

Pure in-memory TimelinePatch application for canonical timeline snapshots. Side-effect free, provider-neutral. Does not persist, merge, or execute.

## Package Placement

`render-module/.../domain/timeline/diff/application/` — 8 types.

## Patch Application Model

`TimelinePatchApplier.apply(base, patch)`:
1. Validates base + patch (revision match, path safety)
2. Returns NO_OP for empty operations
3. Applies operations in deterministic order
4. Returns new immutable snapshot (never mutates base)
5. Sets revision id to `baseRevisionId + "+patched"`
6. Fail-fast on unsupported operation

## Supported Operations

| Operation | Behavior |
|-----------|----------|
| TIMELINE_DURATION_CHANGED | Updates durationMs |
| TRACK_ADDED | Adds new empty track |
| TRACK_REMOVED | Removes track by ID |
| TRACK_REORDERED | Updates track order |
| CLIP_ADDED | Adds new empty clip |
| CLIP_REMOVED | Removes clip by ID |
| CLIP_MOVED | Updates clip startMs |
| CLIP_TRIMMED | Updates clip durationMs |
| ASSET_BINDING_CHANGED | Updates clip assetBindingId |
| CAPTION_SEGMENT_CHANGED | Updates caption text |
| TEXT_STYLE_CHANGED | Updates caption text (same handler) |
| WATERMARK_CHANGED | Updates position:opacity |
| TEMPLATE_PARAMETER_CHANGED | Adds/updates template parameter |
| TEMPLATE_PROFILE_CHANGED | Updates template ID |
| WORKFLOW_APPLY_TEMPLATE_STEP_CHANGED | Updates workflow step templateApplicationId |
| OUTPUT_PROFILE_CHANGED | Updates output dimensions |
| METADATA_CHANGED | Adds/updates metadata key-value |

## Validation

TimelinePatchValidator checks:
- Base and patch not null
- Base revision matches patch baseRevisionId
- Operations have type, scope, path
- Paths start with `timeline.`
- No forbidden keywords (bucket, objectKey, signedUrl, providerName, etc.)

## Round-trip Diff → Patch → Apply

`CanonicalTimelineDiffCalculator.calculate(before, after)` → operations
→ `TimelinePatch` → `TimelinePatchApplier.apply(before, patch)` → patched snapshot

## Safety

- No vedit/pyvedit/OpenTimelineIO dependency
- No provider/storage internals
- No Remotion references
- No merge, no conflict resolution
- No Timeline Git persistence
- No render pipeline calls
- No StorageRuntime/ProductRuntime calls
- Input snapshot never mutated

## Follow-up

- P2V.3: Timeline Merge Conflict Taxonomy
