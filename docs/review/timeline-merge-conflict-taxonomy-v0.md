# Timeline Merge Conflict Taxonomy v0 (P2V.3)

## Purpose

Platform-owned merge conflict analysis for canonical timeline snapshots. Pure in-memory, side-effect free. Detects divergent changes between two competing timelines without performing merge, conflict resolution, or persistence.

## Package Placement

`render-module/.../domain/timeline/diff/merge/` — 13 types.

## Merge Conflict Analysis Model

`TimelineMergeConflictDetector.analyze(base, ours, theirs)`:
1. Validates base/ours/theirs are present
2. Computes `oursDiff = diff(base, ours)` via `CanonicalTimelineDiffCalculator`
3. Computes `theirsDiff = diff(base, theirs)`
4. Compares operations by path and change type
5. Creates `TimelineConflict` for divergent same-path changes
6. Produces `TimelineMergeReadiness` and `TimelineMergeConflictSummary`
7. Does not merge, resolve, apply patches, or call services

## Three-way Input Model

```
base snapshot + ours snapshot → ours diff (TimelineDiff)
base snapshot + theirs snapshot → theirs diff (TimelineDiff)
ours diff + theirs diff → TimelineMergeConflictAnalysis
```

## Readiness Statuses

| Status | Meaning |
|--------|---------|
| MERGE_READY | No conflicts, no blocking issues |
| MANUAL_REVIEW_REQUIRED | Conflicts exist but analysis completed |
| BLOCKED | Invalid structural input or forbidden internal exposure |
| UNSUPPORTED | Unsupported change type prevents safe analysis |
| INVALID_INPUT | Missing base/ours/theirs |

## Issue Code Taxonomy

| Code | Meaning |
|------|---------|
| MISSING_BASE | Base snapshot null |
| MISSING_OURS | Ours snapshot null |
| MISSING_THEIRS | Theirs snapshot null |
| BASE_REVISION_MISMATCH | Patch base revision mismatch |
| UNSUPPORTED_CHANGE_TYPE | Change type cannot be analyzed |
| SAME_PATH_DIVERGENT_CHANGE | Both sides change same path differently |
| TARGET_REMOVED_AND_MODIFIED | One side removes, other modifies |
| TARGET_REMOVED_AND_MOVED | One side removes, other moves |
| TRACK_ORDER_DIVERGENCE | Track reorder conflict |
| CLIP_TIMING_OVERLAP | Clip timing conflict |
| CAPTION_TEXT_DIVERGENCE | Caption text conflict |
| TEXT_STYLE_DIVERGENCE | Text style conflict |
| WATERMARK_POSITION_DIVERGENCE | Watermark position conflict |
| TEMPLATE_PARAMETER_DIVERGENCE | Template parameter conflict |
| TEMPLATE_PROFILE_DIVERGENCE | Template profile conflict |
| WORKFLOW_STEP_DIVERGENCE | Workflow step conflict |
| OUTPUT_PROFILE_DIVERGENCE | Output profile conflict |
| RENDER_IMPACT_DIVERGENCE | Render impact conflict |
| PROVIDER_INTERNALS_NOT_ALLOWED | Forbidden provider path |
| STORAGE_INTERNALS_NOT_ALLOWED | Forbidden storage path |
| EXECUTION_NOT_ALLOWED | Forbidden execution path |

## Conflict Type Mapping

Maps to existing `TimelineConflictType`:

| Detector Finding | ConflictType |
|---|---|
| Same path divergent change | UNKNOWN_CONFLICT or specific type |
| Clip timing overlap | CLIP_TIMING_CONFLICT |
| Asset changed on same clip | ASSET_BINDING_CONFLICT |
| Caption text changed differently | CAPTION_TEXT_CONFLICT |
| Caption style changed differently | TEXT_STYLE_CONFLICT |
| Watermark position changed differently | WATERMARK_POSITION_CONFLICT |
| Template parameter changed differently | TEMPLATE_PARAMETER_CONFLICT |
| Workflow step changed differently | WORKFLOW_STEP_CONFLICT |
| Output profile changed differently | OUTPUT_PROFILE_CONFLICT |

## Minimal Conflict Heuristics

Implemented:
1. Different metadata keys changed → MERGE_READY
2. Same metadata key same value → MERGE_READY
3. Same metadata key different value → MANUAL_REVIEW_REQUIRED
4. Different captions changed → MERGE_READY
5. Same caption text same value → MERGE_READY
6. Same caption text different value → CAPTION_TEXT_CONFLICT
7. Different clips changed → MERGE_READY
8. Same clip moved differently → CLIP_TIMING_CONFLICT
9. Same clip trimmed differently → CLIP_TIMING_CONFLICT
10. Different template parameters changed → MERGE_READY
11. Same template parameter changed differently → TEMPLATE_PARAMETER_CONFLICT
12. Same output profile changed differently → OUTPUT_PROFILE_CONFLICT
13. Watermark position changed differently → WATERMARK_POSITION_CONFLICT
14. Watermark opacity changed differently → WATERMARK_POSITION_CONFLICT
15. Workflow step changed differently → WORKFLOW_STEP_CONFLICT
16. Track reordered differently → TRACK_ORDER_CONFLICT
17. Clip removed vs moved → conflict (via parent path detection)

## Deterministic Ordering

Conflicts ordered by:
1. Severity: BLOCKING > WARNING > INFO
2. TimelineConflictType enum ordinal
3. Conflict path
4. Message

## Manual Review Policy

Prefer conservative `MANUAL_REVIEW_REQUIRED` over unsafe `MERGE_READY`.

## What is Intentionally Not Implemented

- Automatic merge
- Conflict resolution
- Patch combination
- Timeline Git persistence
- TimelineVersionGraph persistence
- Merkle-DAG
- Provider/storage materialization
- Render execution
- Remotion execution
- FFmpeg commands
- Workflow execution
- Template application execution

## Difference from Merge Engine

This is conflict **analysis** only. A merge engine would apply patches and produce merged snapshots. This module detects conflicts and reports readiness without applying anything.

## Difference from Patch Application

P2V.2 `TimelinePatchApplier` applies a single patch to a base snapshot. P2V.3 `TimelineMergeConflictDetector` analyzes two competing diffs against a common base and reports conflicts. P2V.3 does not apply patches.

## vedit Boundary

No vedit dependency. vedit POC/benchmark role is RECOMMENDED but not adopted as production dependency.

## OTIO Boundary

No OpenTimelineIO runtime dependency.

## Provider/Storage/Remotion Safety Boundaries

- No providerName/providerType/backendName exposure
- No bucket/objectKey/signedUrl/local path exposure
- No StorageRuntime calls
- No ProductRuntime calls
- No render pipeline calls
- No Remotion references
- No FFmpeg commands
- Forbidden path keyword blocklist in detector

## Follow-up Tasks

- P2V.4: Timeline Merge Engine (applies non-conflicting patches)
- P2V.5: Timeline Conflict Resolution Strategies
- P2V.6: Timeline Git Persistence
