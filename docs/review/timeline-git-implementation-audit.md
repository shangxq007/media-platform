---
status: audit
created: 2026-06-24
scope: render-module / timeline_revision chain
truth_level: validated-against-code
owner: platform
---

# Timeline Git Implementation Audit

> **Audit Date:** 2026-06-24
> **Blueprint:** `docs/architecture/blueprint/timeline-git-blueprint.md`
> **Codebase:** render-module (600+ files), V1 schema
> **Method:** Full-text search + source reading of all revision/patch/diff/merge/conflict/proposal code

---

## 1. Executive Summary

### Timeline Git Implementation Status: **62% complete**

| Layer | Status | Completion |
|-------|--------|------------|
| Snapshot / Revision chain | ✅ Implemented | 95% |
| Patch application | ✅ Implemented | 90% |
| Diff (structural) | ✅ Implemented | 90% |
| Diff (semantic) | ✅ Implemented | 85% |
| Incremental render impact | ✅ Implemented | 80% |
| Editor sync | ✅ Implemented | 85% |
| AI proposal + review | ✅ Implemented | 75% |
| Merge (three-way) | ❌ Not implemented | 0% |
| Branch model | ❌ Not implemented | 0% |
| Conflict (timeline) | ❌ Not implemented | 0% |
| Rebase | ❌ Not implemented | 0% |

**Key finding:** The codebase already implements a full Git-like linear version-control system (snapshot → revision → patch → diff → impact → render) plus an AI proposal review workflow. The missing pieces are **multi-branch support**: three-way merge, branch model, and timeline-level conflict resolution.

---

## 2. Validation Scope

### Modules Audited

| Module | Files Scanned | Relevance |
|--------|--------------|-----------|
| `render-module` | `app/timeline/` (20 files), `domain/timeline/` (25 files), `domain/timeline/internal/` (10 files), `infrastructure/soul/`, `infrastructure/productization/collaboration/` | Primary — all timeline version control lives here |
| `platform-app` | Flyway V1 schema | `timeline_snapshot`, `timeline_revision` tables |
| `ai-module` | Not applicable | No timeline version control in ai-module |
| `workflow-module` | Not applicable | Temporal orchestration, not timeline versioning |

---

## 3. Timeline IR Audit

### Existing IR Model

| Entity | Java Record | Location | Blueprint Match |
|--------|------------|----------|-----------------|
| Timeline | `TimelineSpec` | `domain/timeline/TimelineSpec.java` | ✅ |
| Track | `TimelineTrack` | `domain/timeline/TimelineTrack.java` | ✅ |
| Clip | `TimelineClip` | `domain/timeline/TimelineClip.java` | ✅ |
| Asset Ref | `TimelineAssetRef` | `domain/timeline/TimelineAssetRef.java` | ✅ |
| Effect | `TimelineClipEffect` | `domain/timeline/TimelineClipEffect.java` | ✅ |
| Marker | `TimelineMarker` | `domain/timeline/TimelineMarker.java` | ✅ |
| Transition | `TimelineTransition` | `domain/timeline/TimelineTransition.java` | ✅ |
| Text Overlay | `TimelineTextOverlay` | `domain/timeline/TimelineTextOverlay.java` | ✅ |
| Output Spec | `TimelineOutputSpec` | `domain/timeline/TimelineOutputSpec.java` | ✅ |
| Metadata keys | `TimelinePlatformMetadata` | `domain/timeline/TimelinePlatformMetadata.java` | ✅ |

**Verdict: Full Timeline IR exists.** Every entity type the blueprint describes has a corresponding Java record. The IR is the foundation for all version-control operations.

### Entity Index (Semantic Diff Foundation)

| Class | Purpose |
|-------|---------|
| `EntityKind` (15 types) | PROJECT, ASSET, CLIP, TRACK, LAYER, SUBTITLE_TRACK, SUBTITLE_CUE, AUDIO_BUS, AUDIO_MIX, EXTERNAL_NODE, TRANSITION, OUTPUT, PACKAGING, FINAL_COMPOSER, STYLE, TEMPLATE |
| `EntityRef` | Stable `(kind, id)` key for entity identity across revisions |
| `TimelineEntityIndex` | Builds index of all entities in a timeline snapshot |

**Verdict:** The entity identity model (stable IDs across revisions) is the prerequisite for semantic diff and merge — both already partially exist.

---

## 4. Snapshot Audit

### Implementation Status: **95% — Fully Implemented**

| Capability | Class/Table | Status |
|-----------|------------|--------|
| Snapshot storage | `timeline_snapshot` table | ✅ V1:427 |
| Snapshot service | `TimelineSnapshotService` | ✅ 8 methods |
| Content hashing | `TimelineContentHasher` | ✅ SHA-256 canonicalized |
| Content deduplication | `TimelineRevisionService.recordRevision()` | ✅ Skips duplicate hash |

### Existing Model

```java
// TimelineSnapshotService (render-module/.../app/TimelineSnapshotService.java)
record SnapshotInfo(
    String id,           // unique snapshot identifier
    String projectId,    // parent project
    String tenantId,     // tenant scope
    String payloadJson,  // full Internal Timeline Schema 1.0 JSON
    String schemaVersion // "2.0.0"
)

// timeline_snapshot table
id varchar(64) primary key,
project_id varchar(64) not null,
tenant_id varchar(64),
payload_json text not null,      -- full Internal Timeline JSON
schema_version varchar(32),
created_at timestamp,
content_hash varchar(64),        -- SHA-256 of canonicalized payload
revision_number int
```

**Gap analysis:**
- `SnapshotInfo` has no `message` or `author` (those are on revision, not snapshot — correct separation)
- Snapshot is a content-addressed blob (correct — analogous to Git blob objects)
- **No gap.** Snapshot model matches blueprint exactly.

---

## 5. Patch Audit

### Implementation Status: **90% — Fully Implemented**

| Capability | Class | Status |
|-----------|-------|--------|
| Patch application | `TimelinePatchService.applyPatch()` | ✅ RFC6902 `replace`/`add`/`remove` |
| Patch serialization | `TimelinePatchOpsJson` | ✅ JSON ↔ List\<PatchOperation\> |
| Patch persistence | `patch_ops_json` column on `timeline_revision` | ✅ |
| Patch validation | Validates via `TimelineValidationService` after apply | ✅ |
| Revision auto-bump | Auto-increments `revision` field in JSON | ✅ |

### Existing Model

```java
// TimelinePatchService.PatchOperation
record PatchOperation(String op, String path, JsonNode value)

// Operations supported:
"replace"  // modify existing field
"add"      // add to array or set new field
"remove"   // remove from array or delete field
```

**Gap analysis:**
- Patch operations are JSON Path-based (RFC6902), not semantic operation types (`CLIP_ADDED`, `CLIP_TRIMMED`, etc.)
- The blueprint defines semantic patch types; the existing code uses generic JSON Patch ops
- **Small gap:** Need a mapper from semantic patch types → JSON Patch ops for the blueprint's richer interface. The blueprint's semantic types can layer on top of existing JSON Patch engine.

---

## 6. Diff Audit

### Implementation Status: **87% — Two Engines, Both Implemented**

| Capability | Class | Status |
|-----------|-------|--------|
| Structural diff | `TimelineRevisionDiffService` | ✅ Entity counts + per-entity verb |
| Semantic diff | `TimelineSemanticDiffService` | ✅ 25 semantic change types |
| Entity identity | `EntityRef`, `EntityKind` | ✅ Stable IDs across revisions |
| Change type enum | `SemanticChangeType` (25 values) | ✅ PROJECT_TIMEBASE_CHANGED … UNKNOWN |
| Dirty scope | `DirtyScope` (10 values) | ✅ PROJECT, ASSET, CLIP, LAYER … FULL_TIMELINE |
| Diff → impacted rendering | `RenderImpactAnalyzer` | ✅ Change → DirtyScope → IncrementalTask |

### Semantic Change Types (25 total)

```java
PROJECT_TIMEBASE_CHANGED, PROJECT_RESOLUTION_CHANGED, PROJECT_COLOR_CHANGED,
ASSET_URI_CHANGED, ASSET_PROBE_CHANGED,
CLIP_ADDED, CLIP_REMOVED, CLIP_RANGE_CHANGED, CLIP_SPEED_CHANGED, CLIP_EFFECT_CHANGED,
LAYER_ADDED, LAYER_REMOVED, LAYER_CONTENT_CHANGED, LAYER_TRANSFORM_CHANGED,
SUBTITLE_CUE_CHANGED, SUBTITLE_STYLE_CHANGED,
AUDIO_BUS_CHANGED, AUDIO_STEM_CHANGED,
EXTERNAL_NODE_CHANGED, TRANSITION_CHANGED,
OUTPUT_PROFILE_CHANGED, PACKAGING_PARAM_CHANGED, FINAL_COMPOSER_CHANGED,
REVISION_ONLY, UNKNOWN
```

**Gap analysis:**
- Blueprint defines `SemanticChangeType` with ~16 types (CLIP_ADDED, CLIP_REMOVED, CLIP_MOVED, etc.). Existing code has 25 types — more granular.
- Blueprint defines `CLIP_MOVED` as a distinct type; existing code represents moves as `CLIP_REMOVED` + `CLIP_ADDED` or `CLIP_RANGE_CHANGED`
- **Small gap:** Add `CLIP_MOVED`, `CLIP_METADATA_CHANGED` as explicit semantic types

---

## 7. Merge Audit

### Implementation Status: **5% — Skeleton Only, Not Connected**

| Capability | Location | Status |
|-----------|----------|--------|
| Real-time OT merge | `CollaborationEngine.transformOperation()` | Partial skeleton |
| Timeline three-way merge | **None** | ❌ Not implemented |
| Branch merge | **None** | ❌ Not implemented |
| Rebase | **None** | ❌ Not implemented |

### What Exists (Not Connected to Revision Chain)

```java
// CollaborationEngine.java — operational transform skeleton
transformOperation(op1, op2)        // Adjusts position on INSERT collision
mergeOperations(local, remote)      // Simple concatenation (not full OT)

// Workspace.java — conflict strategy enum
ConflictResolutionStrategy {
    LAST_WRITE_WINS, FIRST_WRITE_WINS, MANUAL_MERGE, OPERATIONAL_TRANSFORM
}
```

**Gap analysis:**
- `CollaborationEngine` handles real-time operational transform (OT) for multi-user editing sessions
- No three-way merge service exists that operates on `timeline_revision` rows
- Revisions are strictly linear (`parent_revision_id` is a single parent — no merge commits with multiple parents)
- **Large gap:** Need `TimelineMergeService` implementing three-way merge on the revision chain. The blueprint's merge algorithm can be built on top of existing `TimelineSemanticDiffService` (diff base→source, diff base→target, partition changes, detect conflicts).

---

## 8. Conflict Audit

### Implementation Status: **10% — Subsystem Only, Not Timeline**

| Capability | Location | Status |
|-----------|----------|--------|
| Subsystem conflict resolver | `ConflictResolver.java` | ✅ Billing vs Policy, Strategy vs Provider |
| Timeline merge conflict | **None** | ❌ Not implemented |
| Conflict detection | **None** (for timeline entities) | ❌ Not implemented |
| Conflict resolution | **None** (for timeline entities) | ❌ Not implemented |

### What Exists (Different Domain)

```java
// ConflictResolver.java — subsystem-level, not timeline-level
detectConflicts(List<SubsystemRecommendation>)  // BILLING_VS_POLICY, STRATEGY_VS_PROVIDER
resolveConflicts(conflicts, GlobalObjectiveFunction) // Pick winner by score

ConflictType: BILLING_VS_POLICY, STRATEGY_VS_PROVIDER, COST_VS_QUALITY, SPEED_VS_RELIABILITY
```

**Gap analysis:**
- Existing conflict resolver operates on infrastructure subsystem decisions, not timeline entities
- No overlap between existing `ConflictResolver` and the blueprint's `TimelineConflict` model
- **Large gap:** Need `TimelineConflictDetector` that partitions `SemanticChange[]` by entity and detects same-entity conflicts

---

## 9. Proposal Review Audit

### Implementation Status: **75% — Core Loop Exists, Needs Productization**

| Capability | Class | Status |
|-----------|-------|--------|
| AI edit generation | `AiTimelineEditService.editTimeline()` | ✅ Full timeline or patch ops |
| AI response parsing | `AiTimelineEditResponseParser` | ✅ FullTimeline | PatchOps |
| Proposal storage | `InternalTimelineAiProposals` | ✅ Embedded in `platformExtensions.aiProposals` |
| Proposal list | `AiTimelineProposalService.listProposals()` | ✅ |
| Proposal adopt (apply) | `AiTimelineProposalService.adopt()` | ✅ |
| Proposal reject | `AiTimelineProposalService.reject()` | ✅ |
| Human-in-the-loop flag | `AiTimelineEditContext.humanInTheLoop` | ✅ |
| Multi-turn context | `AiTimelineEditContext` (conversationId, lastInstruction) | ✅ |
| Audit trail | `TimelinePlatformMetadata` AI keys + revision source | ✅ |

### Existing Model

```java
// Proposals embedded in Internal Timeline JSON under platformExtensions.aiProposals:
{
  "id": "prop_abc123",
  "status": "PENDING",      // PENDING | ACCEPTED | REJECTED
  "summary": "Trim intro by 40%",
  "createdAt": "2026-06-24T10:00:00Z",
  "resolvedAt": null,
  "operations": [            // RFC6902 patch operations
    { "op": "replace", "path": "/composition/tracks/0/clips/0/sourceRange/duration/frame", "value": 90 }
  ]
}
```

**Gap analysis:**
- Blueprint defines a separate `TimelinePatchProposal` record with `proposalId`, `author`, `agentModel`, `confidence`, `reason`, `semanticDiff` — existing code stores proposals inline in JSON with base fields
- Missing from current: `confidence` score, `reason` explanation, pre-computed `semanticDiff` for preview
- **Small gap:** Enrich proposal model with confidence, reason, and auto-computed semantic diff preview. Extract to dedicated `TimelineProposal` record instead of embedded JSON.

---

## 10. Persistence Audit

### Existing Tables

| Blueprint Concept | Table | Exists? | Details |
|-------------------|-------|---------|---------|
| Timeline Snapshot | `timeline_snapshot` | ✅ | `id, project_id, tenant_id, payload_json, schema_version, content_hash, revision_number, created_at` |
| Revision (Commit) | `timeline_revision` | ✅ | `id, project_id, tenant_id, parent_revision_id, revision_number, snapshot_id, content_hash, schema_version, source, author_user_id, edit_session_id, message, change_summary_json, patch_ops_json, labels_json, created_at` |
| Branch | **None** | ❌ | No branch table. Revisions are linear (single parent). |
| Merge commit | **None** | ❌ | `parent_revision_id` is a single value — no multi-parent merge commits. |
| Proposal | **None** | ❌ | Proposals stored inline in `payload_json` under `platformExtensions.aiProposals`. |
| Review | **None** | ❌ | No separate review table. Proposal status managed inline. |

### Schema Gap Analysis

| Gap | Severity | Recommendation |
|-----|----------|----------------|
| No branch table | Medium | `parent_revision_id` is single-valued — can't represent merge. Add `branch_name` and allow multi-parent via array column or join table. |
| Proposals in JSON | Low | Inline storage works for Phase 1. Extract to `timeline_proposal` table in Phase 2 for queryability. |
| No merge commit marker | Medium | Add `merge_parent_revision_ids text[]` or `is_merge boolean` to `timeline_revision`. |

---

## 11. Existing Reusable Components

These components are production-ready and should be reused directly:

| Class | File | Purpose | Reuse Recommendation |
|-------|------|---------|---------------------|
| `TimelineRevisionService` | `app/timeline/TimelineRevisionService.java` | Full revision lifecycle: create, list, compare, restore, annotate, patch preview | **Use as-is.** Core of any merge/rebase service. |
| `TimelineSemanticDiffService` | `app/timeline/TimelineSemanticDiffService.java` | 25 semantic change types from two timeline states | **Use as-is.** Merge algorithm calls this twice (diff base→A, diff base→B). |
| `TimelineRevisionDiffService` | `app/timeline/TimelineRevisionDiffService.java` | Entity-level structural diff + change summary | **Use as-is.** Provides the high-level summary for merge UI. |
| `TimelinePatchService` | `app/TimelinePatchService.java` | RFC6902 JSON Patch application | **Use as-is.** Merge applies resolved changes as patch operations. |
| `AiTimelineProposalService` | `app/timeline/AiTimelineProposalService.java` | Proposal lifecycle: propose, adopt, reject | **Extend** with confidence + reason fields. |
| `TimelineEditorSyncService` | `app/timeline/TimelineEditorSyncService.java` | Bidirectional editor ↔ internal sync | **Use as-is.** Merge result synced back to editor. |
| `RenderImpactAnalyzer` | `app/timeline/RenderImpactAnalyzer.java` | Semantic change → dirty scope → incremental tasks | **Use as-is.** Post-merge: compute what needs re-rendering. |
| `TimelineContentHasher` | `app/timeline/TimelineContentHasher.java` | SHA-256 canonical hash | **Use as-is.** Merge result hash for dedup. |
| `EntityRef` / `EntityKind` | `domain/timeline/internal/EntityRef.java` | Stable entity identity across revisions | **Use as-is.** Foundation for conflict detection (same entity modified in both branches). |
| `SemanticChange` / `SemanticChangeType` | `domain/timeline/internal/` | Typed change records for diff results | **Extend** with `CLIP_MOVED`, `CLIP_METADATA_CHANGED`. |

---

## 12. Blueprint Mapping

| Blueprint Capability | Existing Implementation | Gap | Recommendation |
|---------------------|------------------------|-----|----------------|
| **Snapshot** ($4) | `TimelineSnapshotService` + `timeline_snapshot` table | None | Use as-is |
| **Patch** ($5) | `TimelinePatchService` + `TimelinePatchOpsJson` | Semantic patch types (CLIP_ADDED etc.) need mapper to JSON Patch ops | Layer semantic types on top of existing JSON Patch engine |
| **Diff** ($6) | `TimelineSemanticDiffService` + `TimelineRevisionDiffService` | Missing `CLIP_MOVED`, `CLIP_METADATA_CHANGED` explicit types | Extend `SemanticChangeType` enum |
| **Merge** ($7) | None | Full gap — no three-way merge service | **Build new:** `TimelineMergeService` using existing `TimelineSemanticDiffService` for base→A and base→B diffs |
| **Conflict** ($8) | `ConflictResolver.java` (subsystem only) | Full gap — no timeline merge conflicts | **Build new:** `TimelineConflictDetector` partitioning `SemanticChange[]` by entity |
| **Proposal Review** ($9) | `AiTimelineProposalService` + `AiTimelineEditService` | Missing confidence, reason, semantic diff preview in proposal model | Extend proposal model, add pre-computed diff |
| **Asset Registry** ($10) | `AssetRegistryService` + `asset` table w/ version | Clips already reference `assetId` — version field exists but not enforced in patch ops | Wire clip patches to require `assetId` + `assetVersion` |
| **OTIO** ($11) | `OpenTimelineioAdapter` | Round-trip exists; bluepulse metadata injection added in Sprint 001 | Use as-is |
| **Artifact DAG** ($12) | `ArtifactGraph` + `ArtifactGraphCompiler` | DAG compilation exists; incremental via `RenderImpactAnalyzer` | Use as-is |
| **Incremental Render** ($13) | `IncrementalRenderPlanService` + `SegmentTimelinePlanner` | Full pipeline: diff → impact → incremental tasks | Use as-is |
| **Collaboration** ($14) | `CollaborationEngine` (OT skeleton) | Not connected to revision chain | Wire OT merge results into `TimelineRevisionService.recordRevision()` |
| **Placement** ($17) | All in `render-module` | Blueprint suggests extraction to `timeline-module` in Phase 3 | Defer extraction until merge/branch stabilize |

---

## 13. Placement Review

### Current: All in `render-module`

```
render-module/src/main/java/.../render/
  ├── domain/timeline/          ← 25 files (IR + OTIO + extensions)
  │   └── internal/             ← 10 files (diff, impact, entity index)
  ├── app/timeline/             ← 20 files (revision, patch, diff, sync, AI)
  ├── app/                      ← TimelinePatchService, TimelineSnapshotService
  └── infrastructure/timeline/  ← TimelineRevisionRepository
```

### Options

| Option | Pros | Cons | When |
|--------|------|------|------|
| **Stay in `render-module`** | Zero migration; all tests pass; no new module boundary | Timeline Git grows; render module gets large | Sprint 002-003 (Phase 2 core: merge + conflict) |
| **Extract `timeline-module`** | Clean separation; independent scaling; focused test suite | Cross-module dependency: render-module depends on timeline-module for IR types; AssetRegistryService stays in render-module | Sprint 004+ (Phase 3: branch + collaboration) |
| **New `timeline-git-module`** | Explicit version-control semantics | Premature; duplicates existing revision service; "git" in name is confusing | Not recommended |

**Recommendation:** Keep in `render-module` for Sprint 002-003 (merge + conflict implementation). The existing revision/patch/diff infrastructure is tightly integrated with the render pipeline (diff → impact → incremental tasks). Extract to `timeline-module` only after merge/branch stabilize and the module boundary is clear.

---

## 14. Recommended Implementation Order

### Re-ordered from Blueprint (Matching Existing Code Readiness)

The blueprint prioritizes: Snapshot → Patch → Diff → Merge → Conflict → Proposal → Branch → Incremental Render.

**Reality:** Snapshot, Patch, Diff, Incremental Render, and Proposal are already implemented. The missing pieces are Merge, Conflict, and Branch.

### Sprint 002 — Merge Core (Highest Priority)

**Why first:** Merge is the biggest gap and the highest-value missing capability. It depends on existing Diff (complete) and Patch (complete).

```
Build:
  TimelineMergeService.java
    ├── threeWayMerge(baseSnapshotId, sourceSnapshotId, targetSnapshotId)
    ├── Uses TimelineSemanticDiffService for base→source and base→target
    ├── Partitions changes by entity (EntityRef key)
    ├── Auto-resolves non-conflicting changes
    └── Returns merge result + conflict list

  TimelineConflictDetector.java
    ├── detect(SemanticChange[] fromA, SemanticChange[] fromB)
    ├── Groups by EntityRef
    ├── Flags same-entity changes from both branches
    └── Returns List<TimelineConflict>

  Extend timeline_revision table:
    ├── Add merge_parent_revision_ids TEXT[] (for merge commits)
    └── Add is_merge BOOLEAN DEFAULT FALSE

  Extend SemanticChangeType:
    ├── Add CLIP_MOVED
    └── Add CLIP_METADATA_CHANGED
```

**Effort:** 5-7 days (1 developer)

### Sprint 003 — Conflict Resolution + Branch Support

```
Build:
  TimelineConflictResolver.java
    ├── Auto-resolution rules (non-overlapping adds, independent tracks, markers)
    ├── Manual resolution data model
    └── Integration with TimelinePatchService for apply

  Branch model:
    ├── timeline_branch table (project_id, branch_name, head_revision_id)
    ├── Revisions linked to branch
    └── listBranches(), createBranch(), switchBranch()

  Wire CollaborationEngine to revision chain:
    ├── Real-time OT results → recordRevision()
    └── Session management → branch association
```

**Effort:** 5-7 days (1 developer)

### Sprint 004 — Proposal Enhancement + Rebase

```
Build:
  TimelineRebaseService.java
    ├── Replay branch patches on top of updated base
    └── Conflict detection at each step

  Enrich proposal model:
    ├── Add confidence, reason, semanticDiff to AiProposalView
    ├── Extract timeline_proposal table (from inline JSON)
    └── Proposal diff preview API
```

**Effort:** 4-5 days (1 developer)

---

## 15. Technical Debt Risks

| Risk | Severity | Description | Mitigation |
|------|----------|-------------|------------|
| **Duplicate implementation** | Medium | The blueprint proposes `TimelineSnapshot`, `TimelinePatch`, `TimelineDiff` records — but `TimelineRevisionService`, `TimelinePatchService`, `TimelineSemanticDiffService` already exist. Implementing new models alongside existing ones would create two parallel version-control systems. | **Extend existing services, don't create new ones.** Use `adopt/reject` pattern: blueprint models are reference, not replacement. |
| **Wrong abstraction** | Medium | Blueprint proposes `TimelineMerge` as a standalone record with `changesFromSource`, `changesFromTarget`, `conflicts`. Existing code models each revision as a standalone immutable row with `parentRevisionId`. A merge commit should be a revision with two parents, not a separate entity type. | Model merge as a revision row with `merge_parent_revision_ids TEXT[]`. Use existing `TimelineRevisionService` to create merge revisions. |
| **Module boundary** | Low | All timeline code is in `render-module`. Extraction to `timeline-module` introduces a cross-module dependency (render depends on timeline). Spring Modulith supports this via `@NamedInterface`. | Defer extraction until merge/branch stabilize. Verify `ModularityTest` passes with dependency. |
| **Database design** | Medium | `parent_revision_id` is a single VARCHAR — no support for multi-parent merge commits. Proposals stored inline in JSON — not queryable by status/author/date. | Add `merge_parent_revision_ids TEXT[]` for merge commits. Extract `timeline_proposal` table for Phase 3. |
| **OT vs Git merge** | Medium | `CollaborationEngine` implements OT (real-time), while blueprint describes Git-style merge (offline, snapshot-based). These are complementary but distinct models. Don't conflate them. | OT handles real-time cursor sync; three-way merge handles branch reconciliation. Keep them separate. |

---

## 16. Final Recommendation

### Next Step: **Build Merge + Conflict (Sprint 002)**

**Why Merge first:**

1. **Biggest gap** — Snapshot, Patch, Diff, and Proposal are already built. Merge is the only major missing piece from the blueprint's Phase 1-2 vision.
2. **Highest value** — Multi-user collaboration is impossible without merge. AI proposal review is already built (adopt/reject), but collaborative editing requires merge.
3. **Lowest risk** — Merge builds on existing stable infrastructure (Diff → partition changes → detect conflicts). No new tables needed initially (just add columns to `timeline_revision`).
4. **Unblocks everything else** — After merge, branch support is trivial (branch = named revision pointer). Conflict resolution UI builds on merge conflict detection. Rebase is merge applied sequentially.

### Priority Order (Corrected)

```
Sprint 002: Merge + Conflict Detection        ← BUILD THIS NEXT
Sprint 003: Branch Support + Conflict Resolution
Sprint 004: Rebase + Proposal Enhancement
```

### Why NOT Proposal Enhancement First

Proposal review (adopt/reject) already works. Enhancing it (confidence, reason, semantic diff preview) is a UX polish, not a capability unlock. Merge enables a new capability that doesn't exist at all.

### Why NOT Snapshot/Patch/Diff Enhancement First

These are already implemented at 85-95% completion. The remaining 5-15% (e.g., adding `CLIP_MOVED` type) can be done inline during merge implementation.

---

## Appendix: File Inventory

### Already Implemented (18 files — usable as-is)

| # | File | Capability |
|---|------|------------|
| 1 | `TimelineSnapshotService.java` | Snapshot blob store |
| 2 | `TimelineRevisionService.java` | Revision chain lifecycle |
| 3 | `TimelineRevisionRepository.java` | Revision persistence |
| 4 | `TimelineRevisionDiffService.java` | Structural entity diff |
| 5 | `TimelineSemanticDiffService.java` | Semantic change classification |
| 6 | `TimelinePatchService.java` | RFC6902 JSON Patch application |
| 7 | `TimelinePatchOpsJson.java` | Patch serialization |
| 8 | `TimelineContentHasher.java` | Content hashing + dedup |
| 9 | `TimelineEditorSyncService.java` | Editor ↔ internal sync |
| 10 | `AiTimelineProposalService.java` | Proposal lifecycle |
| 11 | `AiTimelineEditService.java` | AI edit generation |
| 12 | `AiTimelineEditResponseParser.java` | AI response parsing |
| 13 | `InternalTimelineAiProposals.java` | Proposal JSON storage |
| 14 | `AiTimelineEditContext.java` | Edit provenance metadata |
| 15 | `RenderImpactAnalyzer.java` | Diff → dirty scope → incremental tasks |
| 16 | `TimelineRevisionLabelsJson.java` | Labels/tags on revisions |
| 17 | `EntityRef.java` + `EntityKind.java` | Stable entity identity |
| 18 | `SemanticChange.java` + `SemanticChangeType.java` | Typed change records |

### To Build (Sprint 002-004)

| # | File | Sprint | Capability |
|---|------|--------|------------|
| 1 | `TimelineMergeService.java` | 002 | Three-way merge engine |
| 2 | `TimelineConflictDetector.java` | 002 | Merge conflict detection |
| 3 | `TimelineConflictResolver.java` | 003 | Auto + manual resolution |
| 4 | `TimelineBranchService.java` | 003 | Branch model |
| 5 | `TimelineRebaseService.java` | 004 | Patch replay |
| 6 | `TimelineProposalRepository.java` | 004 | Extracted proposal persistence |
