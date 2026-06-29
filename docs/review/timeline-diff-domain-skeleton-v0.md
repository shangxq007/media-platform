# Timeline Diff Domain Skeleton v0 (P2V.0)

## Purpose

Compile-safe platform-owned Timeline Diff domain skeleton. Defines vocabulary only — no diff calculation, no merge, no persistence, no external dependencies.

## Package Placement

`render-module/.../domain/timeline/diff/` — 22 types.

## Types Added

### Identity Types
- `TimelineDiffId`, `TimelinePatchId`, `TimelineChangeOperationId`
- `TimelineConflictId`, `TimelineCommitId`, `TimelineSemanticHash`

### Change Model
- `TimelineChangeOperation` — single change: type + scope + path + before/after payloads
- `TimelineChangeType` — 24 types (TRACK_ADDED through METADATA_CHANGED)
- `TimelineChangeScope` — 14 scopes (TIMELINE, TRACK, CLIP, CAPTION, WATERMARK, TEMPLATE_APPLICATION, COMPOSITE_TEMPLATE, WORKFLOW_STEP, etc.)
- `TimelineChangePath` — safe string path
- `TimelineChangePayload` — string or map values

### Diff and Patch
- `TimelineDiff` — semantic diff between two revisions (operations + conflicts + render impact)
- `TimelinePatch` — ordered operations with merge policy

### Conflict Model
- `TimelineConflict` — conflict with type, severity, path, message
- `TimelineConflictType` — 13 types (TRACK_ORDER through UNKNOWN)
- `TimelineConflictSeverity` — INFO, WARNING, BLOCKING
- `TimelineMergePolicy` — FAIL_FAST, BASELINE_WINS, INCOMING_WINS, MERGE_IF_COMPATIBLE, MANUAL_REVIEW_REQUIRED

### Version Graph
- `TimelineCommit` — immutable version snapshot with parents, semantic hash
- `TimelineCommitParent` — parent reference with relationship type
- `TimelineVersionGraph` — version history graph

### Impact Analysis
- `TimelineRenderImpact` — render cache invalidation analysis
- `TimelineRenderImpactLevel` — NONE through FULL_RERENDER
- `ArtifactDAGImpact` — artifact graph impact (safe node keys)
- `ProductLineageImpact` — product lineage impact (safe product IDs)

### Bridge Types
- `TemplateApplicationDiff` — template application changes
- `CompositeTemplateDiff` — composite template child changes
- `WorkflowApplyTemplateStepDiff` — workflow APPLY_TEMPLATE step changes

## Safety Boundaries

- No provider/backend/storage internals
- No binary data, no commands, no URLs
- No vedit/IPFS/Merkle-CRDT dependencies
- No diff calculation, no merge execution
- No persistence, no public APIs

## What Is Intentionally Not Implemented

- Diff calculation algorithm
- Timeline comparison
- Merge algorithm
- Conflict resolution execution
- Timeline Git repository
- Content-addressed object store
- vedit adapter
- OTIO diff integration

## Follow-up

- P2V.1: Canonical Timeline Diff Calculator
- P2V.2: TimelinePatch Application Semantics
- P2V.3: Timeline Merge Conflict Taxonomy
- P2V.4: Timeline Merge Preview Service
- P2T.4: Composite Template Semantics
- P2D.0: Artifact DAG / Cache Identity Review
