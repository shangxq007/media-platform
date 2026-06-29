---
status: blueprint
created: 2026-06-24
scope: render-module / future timeline-module
truth_level: target
owner: platform
---

# Timeline Git Blueprint

> **Reality Check (2026-06-24):** Timeline revision chain exists via `timeline_revision` table and `TimelineRevisionService`. Patch application exists via `TimelinePatchService` (RFC6902 JSON Patch). Semantic diff exists via `TimelineSemanticDiffService`. Editor sync exists via `TimelineEditorSyncService`. What is missing: formal merge strategy, conflict resolution model, branch model, AI proposal review workflow, and full Timeline Git productization. `AssetRegistryService` and JSON-LD export are Phase 1 complete — providing stable asset identity foundation for Timeline Git.

---

## 1. Executive Summary

### Why Timeline Git is the Next-Phase Core Capability

Video editing is the only major creative discipline without a version control system. Developers have Git. Designers have Figma version history. Writers have Google Docs revision history. Video editors have **undo/redo** — and nothing more.

Timeline Git fills this gap. It provides:

| Capability | Traditional NLE | Timeline Git |
|-----------|----------------|--------------|
| **Undo mistakes** | Ctrl+Z (limited stack) | Snapshot history — jump to any point |
| **Diff changes** | Impossible | Semantic diff: what clips, effects, tracks changed |
| **Branch experiment** | "Save As" copy | Lightweight branch — zero-copy divergence |
| **Merge concurrent edits** | Manual re-edit | Three-way merge with conflict resolution |
| **Review AI edits** | No mechanism | Patch proposal → semantic diff → approve/reject |
| **Track edit history** | File timestamp only | Full revision chain with author, message, hash |
| **Collaborate** | One person at a time | Branch + merge + conflict resolution |

### Relationship with Other Platform Layers

```
Timeline Git (version control)
    │
    ├── Timeline IR (canonical editing model — what is versioned)
    │       │
    │       ├── OTIO (exchange format — import/export from NLEs)
    │       └── Asset Registry (stable asset identity — clips reference assetId + version)
    │
    ├── Artifact DAG (derived from Timeline IR — what to produce)
    │       │
    │       └── Execution Graph (provider-bound — how to produce it)
    │
    └── AI Proposal Review (LLM generates patches — human reviews & approves)
```

**Timeline Git does not:**
- Replace OTIO (OTIO is the exchange format; Timeline IR is the canonical editing model)
- Replace the Asset Registry (the Registry provides stable identity; Timeline Git versions the timeline)
- Replace the Artifact DAG (the DAG is derived from Timeline IR; Timeline Git versions the source)
- Replace the Render Engine (Timeline Git is version control, not execution)

---

## 2. Problem Statement

### The Traditional Video Editing Crisis

Video editing today suffers from fundamental version control problems:

| Problem | Impact |
|---------|--------|
| **Cannot Diff** | "What changed between version A and B?" — impossible to answer without manual inspection |
| **Cannot Merge** | Two editors working on the same project must serialize work or risk overwriting each other |
| **Cannot Branch** | "Let me try a different cut" requires duplicating the entire project file |
| **Cannot Review AI Changes** | AI generates edits, but there is no structured way to see what changed, why, or approve/reject |
| **Cannot Track History** | Only file modification timestamps — no commit messages, no author, no semantic changelog |
| **Cannot Rollback Granularly** | Undo is a linear stack — cannot restore a specific clip trim from 20 edits ago |
| **Cannot Annotate Decisions** | No commit messages explaining why a cut was made |

### What Timeline Git Solves

```
Problem                          → Timeline Git Solution
─────────────────────────────────────────────────────────
Cannot Diff                      → Semantic Timeline Diff between any two snapshots
Cannot Merge                     → Three-way merge of concurrent edit branches
Cannot Branch                    → Lightweight branch as divergent revision chain
Cannot Review AI Changes         → Patch proposal → semantic diff → human approve/reject
Cannot Track History              → Revision chain with author, message, timestamp, hash
Cannot Rollback Granularly       → Restore any snapshot; apply inverse patch for partial revert
Cannot Annotate Decisions         → Commit messages attached to every snapshot
```

---

## 3. Three Sources of Truth

The platform maintains three distinct sources of truth, each with a single owner:

### Timeline IR — Editing Truth

```
Owner:       User / LLM
Mutability:  Mutable (via patches applied by user or AI)
Content:     Clips, tracks, effects, markers, text overlays, output spec
Representation: Internal Timeline Schema 1.0 JSON / OTIO JSON
Persistence: timeline_snapshot (payload_json) + timeline_revision
```

The Timeline IR describes **what the user intended to edit**. It is the canonical editing source of truth. Every snapshot captures the full editorial state at a point in time.

### Artifact Dependency Graph — Execution Truth

```
Owner:       Platform (ArtifactGraphCompiler)
Mutability:  Immutable per snapshot (derived from Timeline IR)
Content:     Artifact nodes, dependencies, cache keys, hashes
Representation: artifact_node + artifact_graph tables
Persistence: artifact_node, artifact_graph
```

The Artifact DAG describes **what intermediate products must be produced** to render the timeline. It is derived from the Timeline IR, never edited directly. When the timeline changes, the DAG is recompiled incrementally.

### Render Execution Graph — Scheduling Truth

```
Owner:       Platform (Provider Binder + Worker Scheduler)
Mutability:  Ephemeral (created per render, discarded after completion)
Content:     Provider-bound execution nodes, worker assignments, timeouts
Representation: unified_request_graph + unified_graph_node + unified_graph_edge
Persistence: unified_request_graph, unified_graph_node, unified_graph_edge
```

The Execution Graph describes **where and how to execute each artifact production step**. It is provider-bound, worker-assigned, and ephemeral.

### Why Three Sources, Not One

| If we had only... | Problem |
|-------------------|---------|
| One DAG for everything | Editing changes would require re-planning execution; cache invalidation couples to provider choice |
| Timeline IR as execution plan | Timeline would be provider-specific; switching from FFmpeg to BMF changes the timeline |
| Execution Graph as editing truth | Users would edit provider-bound commands, not editorial intent |

**Key invariant:** Timeline IR → Artifact DAG → Execution Graph. Each layer is derived from the layer above. No layer edits a layer above it. Provider binding is always the last step.

> **Note:** Artifact DAG is indefinitely deferred (P2A.2) and retained only as an extension layer. The current pipeline derives Execution Graph from Timeline IR without Artifact DAG. See [ADR-025](../adr/ADR-025-artifact-dag-indefinite-deferral.md).

---

## 4. Timeline Snapshot Model

### Concept

A Timeline Snapshot is analogous to a Git commit. It captures the complete Timeline IR at a specific point in time, along with metadata about who made the change and why.

### Model

```
TimelineSnapshot {
    snapshotId:       String       // unique identifier (e.g., "snap_abc123")
    projectId:        String       // parent project
    tenantId:         String       // tenant scope
    parentSnapshotId: String|null  // previous snapshot in chain (null = root)
    revisionNumber:   int          // monotonically increasing per project
    author:           String|null  // user or AI agent that created this snapshot
    message:          String|null  // human-readable commit message
    createdAt:        Instant      // timestamp
    contentHash:      String       // SHA-256 of canonicalized timeline payload
    schemaVersion:    String       // "internal-1.0"
    payloadJson:      String       // full Internal Timeline Schema 1.0 JSON
}
```

### Existing Implementation

| Component | Exists? | Location |
|-----------|---------|----------|
| `timeline_snapshot` table | Yes | V1:427 — `id, project_id, tenant_id, payload_json, schema_version, content_hash, revision_number` |
| `timeline_revision` table | Yes | V1:440 — `id, project_id, tenant_id, parent_revision_id, revision_number, snapshot_id, content_hash, author_user_id, edit_session_id, message, patch_ops_json` |
| `TimelineRevisionService` | Yes | Snapshot creation, diff, patch preview, restore, compare |
| `TimelineRevisionRepository` | Yes | jOOQ-based persistence for revision rows |

### What Snapshots Do NOT Contain

- Provider bindings (Timeline IR is provider-neutral)
- Artifact DAG nodes (derived from timeline, not stored in it)
- Execution commands (these are ephemeral)
- Storage paths (clips reference `assetId`, not file paths — Asset Registry resolves locations)
- Render cache keys (derived from snapshot hash + asset hashes at compile time)

---

## 5. Timeline Patch Model

### Concept

A Timeline Patch describes a discrete, semantic change to the Timeline IR. It is the atomic unit of editing — every user action or AI suggestion produces one or more patches that, when applied, transform the timeline from one state to another.

### Patch Operations

| Operation | Description | Example |
|-----------|-------------|---------|
| `CLIP_ADDED` | A new clip inserted into a track | Add clip C5 to track V1 at time 12.0s |
| `CLIP_REMOVED` | A clip deleted from a track | Remove clip C3 from track V1 |
| `CLIP_MOVED` | A clip shifted in time or moved between tracks | Move clip C2 from track V1 to track V2 at time 5.0s |
| `CLIP_SPLIT` | A clip split at a specific time point | Split clip C1 at 3.5s into C1a and C1b |
| `CLIP_TRIMMED` | In-point or out-point adjusted | Trim C4 out-point from 10.0s to 8.5s |
| `CLIP_METADATA_CHANGED` | Asset reference or metadata updated | Change C1 asset to asset_456 v2 |
| `EFFECT_ADDED` | An effect applied to a clip or track | Add blur effect to C2 |
| `EFFECT_REMOVED` | An effect removed | Remove color grade from C2 |
| `EFFECT_PARAM_CHANGED` | Effect parameters modified | Change blur radius from 5 to 10 |
| `TRACK_ADDED` | A new track created | Add audio track A2 |
| `TRACK_REMOVED` | A track deleted | Remove subtitle track S1 |
| `TRACK_REORDERED` | Track z-order changed | Move V2 above V1 |
| `MARKER_ADDED` | A marker placed on the timeline | Add review marker at 30.0s |
| `MARKER_REMOVED` | A marker removed | Remove marker M3 |
| `MARKER_CHANGED` | Marker comment or position changed | Update M1 comment to "fix audio" |
| `OUTPUT_CHANGED` | Output specification changed | Change resolution from 1080p to 4K |

### Model

```
TimelinePatch {
    patchId:      String              // unique patch identifier
    snapshotId:   String              // snapshot this patch was derived from
    operations:   List<PatchOp>       // ordered list of operations
    author:       String|null         // user or AI agent
    createdAt:    Instant             // timestamp
}

PatchOp {
    opType:       PatchOpType         // enum: CLIP_ADDED, CLIP_REMOVED, etc.
    targetType:   String              // "clip", "track", "effect", "marker", "output"
    targetId:     String              // entity ID being modified
    path:         String              // JSON Pointer path within Internal Timeline JSON
    oldValue:     Object|null         // value before change (for diff context)
    newValue:     Object|null         // value after change
    trackId:      String|null         // parent track (for clip operations)
    position:     Double|null         // timeline position (for move/split operations)
}
```

### Existing Implementation

| Component | Exists? | Notes |
|-----------|---------|-------|
| `TimelinePatchService` | Yes | Applies RFC6902 JSON Patch to Internal Timeline Schema 1.0 JSON |
| `TimelinePatchOpsJson` | Yes | Patch operation definitions for AI/MCP-safe edits |
| Semantic patch types | Partial | Existing service operates on raw JSON patches; needs semantic operation types above |

### What Patches Do NOT Do

- Execute rendering (patches describe intent, not how to produce pixels)
- Reference provider-specific commands
- Modify the Artifact DAG directly
- Contain storage URIs (patches reference `assetId`, not file paths)

---

## 6. Timeline Diff Model

### Concept

A Timeline Diff compares two Timeline Snapshots and produces a structured description of what changed. Unlike a raw JSON diff, a semantic diff understands timeline entities (clips, tracks, effects) and reports changes at the entity level.

### Why Semantic Diff, Not JSON Diff

| Approach | Result | Problem |
|----------|--------|---------|
| JSON Diff | `{"op": "replace", "path": "/composition/tracks/0/clips/2/sourceRange/duration/frame", "value": 150}` | Incomprehensible — what changed? |
| Semantic Diff | `CLIP_TRIMMED: clip "intro_broll" out-point changed from 5.0s to 3.75s` | Human-readable, machine-actionable |

### Model

```
TimelineDiff {
    diffId:          String
    sourceSnapshotId: String         // snapshot A (older)
    targetSnapshotId: String         // snapshot B (newer)
    changes:         List<SemanticChange>
    affectedTrackIds: List<String>   // tracks that changed
    affectedClipIds: List<String>    // clips that changed
    totalChangedEntities: int
    createdAt:       Instant
}

SemanticChange {
    changeType:      SemanticChangeType  // ADDED, REMOVED, MODIFIED, MOVED
    entityKind:      EntityKind          // CLIP, TRACK, EFFECT, MARKER, OVERLAY, OUTPUT
    entityId:        String              // stable entity identifier
    parentId:        String|null         // parent track (for clip/effect changes)
    path:            String              // JSON Pointer path
    oldValue:        Object|null
    newValue:        Object|null
    changeSummary:   String              // human-readable description
}
```

### Diff Output Example

```
TimelineDiff: snap_001 → snap_002 (3 changes)

  MODIFIED  clip "intro_title" (track V1)
    → out-point: 5.0s → 3.0s
    → summary: Trimmed intro from 5s to 3s

  ADDED     clip "broll_forest" (track V1)
    → asset: asset_789 v3 at 3.0s
    → summary: Inserted forest b-roll after intro

  MODIFIED  effect "color_grade" (clip "hero_shot", track V1)
    → parameters.saturation: 1.0 → 1.3
    → summary: Increased saturation on hero shot
```

### Existing Implementation

| Component | Exists? | Notes |
|-----------|---------|-------|
| `TimelineSemanticDiffService` | Yes | Semantic diff by stable entity ID after canonicalization |
| `TimelineRevisionDiffService` | Yes | Diff between two revision snapshots |
| `SemanticChange` / `SemanticChangeType` | Yes | Domain models in `render-module/.../domain/timeline/internal/` |
| `DirtyScope` / `IncrementalTask` | Yes | Used by incremental render planner to identify affected segments |

### Diff Powers Incremental Rendering

```
TimelineDiff(sourceSnapshot, targetSnapshot)
    → DirtyScope (which tracks, clips, time ranges changed)
        → IncrementalTask[] (which artifact nodes to re-render)
            → Only re-renders affected segments
```

---

## 7. Timeline Merge Model

### Concept

Timeline Merge combines two divergent revision chains into a single reconciled timeline. This is the core mechanism that enables multi-user collaboration and AI proposal review.

### Merge Strategies

| Strategy | When to Use | Behavior |
|----------|-------------|----------|
| **Fast Forward** | Branch A is a direct descendant of Branch B | Simply move Branch B's HEAD to Branch A's HEAD. No merge commit. |
| **Three-way Merge** | Branches A and B diverged from common ancestor C | Compute diff(C→A) and diff(C→B). Apply both sets of changes to C. Resolve conflicts. Create merge commit. |
| **Rebase** | Want linear history; replay A's patches on top of B | Compute patches from common ancestor to A. Apply those patches sequentially onto B. Resolve conflicts at each step. |

### Three-way Merge Algorithm

```
Input:
  Base:    snapshot at common ancestor (C)
  Source:  snapshot at branch A HEAD (A)
  Target:  snapshot at branch B HEAD (B)

Steps:
  1. Diff C→A → changeset_A
  2. Diff C→B → changeset_B
  3. Partition changesets by [track → clip → effect → marker]
  4. For each partition:
     a. If only in A → apply to result (no conflict)
     b. If only in B → apply to result (no conflict)
     c. If in both A and B → detect conflict
  5. For each conflict → resolve or flag for human
  6. Produce merged Timeline IR
  7. Create merge snapshot with parentSnapshotIds = [A, B]
```

### Model

```
TimelineMerge {
    mergeId:           String
    baseSnapshotId:    String              // common ancestor
    sourceSnapshotId:  String              // branch A HEAD
    targetSnapshotId:  String              // branch B HEAD
    strategy:          MergeStrategy       // FAST_FORWARD, THREE_WAY, REBASE
    changesFromSource: List<SemanticChange>
    changesFromTarget: List<SemanticChange>
    conflicts:         List<TimelineConflict>
    resolvedSnapshotId: String|null        // result snapshot (null if unresolved conflicts)
    status:            MergeStatus         // IN_PROGRESS, RESOLVED, CONFLICT, ABORTED
    createdAt:         Instant
    resolvedAt:        Instant|null
}

MergeStrategy: FAST_FORWARD | THREE_WAY | REBASE
MergeStatus:   IN_PROGRESS | RESOLVED | CONFLICT | ABORTED
```

### Timeline-Specific Merge Rules

Unlike text files, timeline merges have domain-specific invariants:

| Rule | Rationale |
|------|-----------|
| **Clip time ranges must not overlap on same track** | Two clips cannot occupy the same time position on one track |
| **Track z-order must be unique** | Each track has a z-index; merged tracks must not collide |
| **Output spec is single-source** | Only one output spec; if both branches change it, flag conflict |
| **Marker times are independent** | Markers from both branches can coexist (additive merge) |
| **Effect order on a clip is significant** | Effects applied in sequence; merged effect lists must preserve intended order |
| **Clip split in one branch invalidates time-based operations in the other** | If branch A splits clip C1 at 3s, and branch B trims C1, the trim may apply to the wrong half |

---

## 8. Timeline Conflict Model

### Concept

A Timeline Conflict occurs when two branches make incompatible changes to the same timeline entity. Conflicts must be detected, classified, and either resolved automatically or presented to a human for manual resolution.

### Conflict Types

| Conflict Type | Example | Auto-Resolvable? |
|---------------|---------|------------------|
| `SAME_CLIP_MODIFIED` | Both branches change the same clip's duration | No — need human to choose |
| `SAME_TRACK_MODIFIED` | Both branches add clips that overlap in time on the same track | Partial — can shift one clip forward |
| `OVERLAPPING_EDIT` | Branch A moves clip to 5s; Branch B adds clip at 5s | Partial — auto-shift with warning |
| `EFFECT_CONFLICT` | Both branches modify the same effect's parameters | No — need human to merge parameters |
| `METADATA_CONFLICT` | Both branches change the same metadata key | No — need human to choose value |
| `OUTPUT_CONFLICT` | Both branches change output spec (resolution, format) | No — single source of truth |
| `SPLIT_TRIMMED` | Branch A splits clip; Branch B trims the same clip | No — trim applies to wrong segment |
| `TRACK_DELETED` | Branch A deletes track; Branch B adds clip to that track | No — track doesn't exist to add clip to |

### Automatic Resolution Rules

| Rule | When Applied | Resolution |
|------|-------------|------------|
| **Non-overlapping clip adds** | Branches add clips to different time ranges | Accept both (no conflict) |
| **Independent track changes** | Branches modify different tracks | Accept both (no conflict) |
| **Marker additions** | Both branches add markers | Accept both (markers are additive) |
| **Sequential effect adds** | Branch A adds effect, Branch B adds different effect to same clip | Accept both, preserve order (A then B by timestamp) |
| **Metadata: different keys** | Branches change different metadata keys | Accept both |
| **Identical change** | Both branches make the exact same change | Accept once (deduplicate) |

### Manual Resolution Required

| Scenario | Resolution UI |
|----------|---------------|
| Same clip modified differently | Side-by-side: "Keep A's version" / "Keep B's version" / "Custom" |
| Overlapping clip positions | Timeline view: drag to resolve overlap |
| Same effect parameter changed | Parameter diff: choose value per parameter |
| Output spec conflict | "Choose A's output" / "Choose B's output" |

### Model

```
TimelineConflict {
    conflictId:       String
    conflictType:     ConflictType
    entityKind:       EntityKind           // CLIP, TRACK, EFFECT, MARKER, OUTPUT
    entityId:         String
    sourceChange:     SemanticChange       // change from branch A
    targetChange:     SemanticChange       // change from branch B
    autoResolvable:   boolean
    resolution:       ConflictResolution|null
}

ConflictResolution {
    strategy:         ResolutionStrategy   // KEEP_SOURCE, KEEP_TARGET, CUSTOM, DEFER
    customValue:      Object|null          // manually specified merged value
    resolvedBy:       String|null          // user who resolved
    resolvedAt:       Instant|null
}
```

---

## 9. AI Proposal Review

### Concept

AI does not directly modify the Timeline IR. Instead, AI generates **Timeline Patch Proposals** — structured suggestions that a human reviews, possibly modifies, and then approves or rejects.

### Why Proposals, Not Direct Edits

| Direct AI Edit | AI Proposal |
|----------------|-------------|
| AI changes timeline without human visibility | Human sees exact diff before applying |
| No explanation of why | Proposal includes reason, confidence, affected clips |
| No rollback if wrong | Rejected proposals never touch the timeline |
| Cannot iterate | Human can comment, request changes, modify proposal |
| Audit trail lost | Proposal history tracks all AI suggestions |

### Model

```
TimelinePatchProposal {
    proposalId:       String
    projectId:        String
    baseSnapshotId:   String              // snapshot the proposal was generated against
    author:           String              // AI agent identifier (e.g., "ai-edit-gpt4o")
    agentModel:       String              // model name (e.g., "gpt-4o")
    conversationId:   String|null         // chat session that produced this proposal
    userInstruction:  String              // original user instruction (e.g., "Make the intro shorter")
    patch:            TimelinePatch       // the proposed change
    semanticDiff:     TimelineDiff        // semantic diff showing what would change
    affectedClipIds:  List<String>        // clips that would be modified
    affectedTrackIds: List<String>        // tracks that would be modified
    reason:           String              // AI explanation (e.g., "Trimmed intro by 40% while preserving key message")
    confidence:       Double              // 0.0 - 1.0 (AI's confidence in this proposal)
    status:           ProposalStatus      // PENDING, APPROVED, REJECTED, MODIFIED, EXPIRED
    reviewedBy:       String|null         // human reviewer
    reviewedAt:       Instant|null
    reviewComment:    String|null         // human feedback
    createdAt:        Instant
}

ProposalStatus: PENDING | APPROVED | REJECTED | MODIFIED | EXPIRED
```

### Review Workflow

```
1. User instruction → AI generates patch proposal
2. Platform computes semantic diff (what would change)
3. Platform presents: diff view + reason + confidence
4. Human reviews:
   a. APPROVE  → apply patch → create snapshot → re-render affected segments
   b. REJECT   → discard proposal (logged for audit)
   c. MODIFY   → human edits the patch → resubmit for preview
   d. COMMENT  → request AI to refine → new proposal generated
```

### Existing Implementation

| Component | Exists? | Notes |
|-----------|---------|-------|
| `AiTimelineEditService` | Yes | LLM-powered timeline editing pipeline |
| `AiTimelineProposalService` | Yes | LLM proposal generation |
| `AiTimelineEditContext` | Yes | Context for LLM editing |
| `AiTimelineEditResponseParser` | Yes | Parse LLM responses into structured edits |
| Proposal review workflow | Partial | Edit services exist; formal proposal→review→approve workflow not yet productized |

---

## 10. Relationship with Asset Registry

### Why Clips Reference assetId + assetVersion, Not File Paths

```
WRONG (traditional NLE approach):
  Clip → "file:///Users/alice/project/footage/broll_forest_v3.mp4"

RIGHT (Timeline Git approach):
  Clip → assetId: "asset_789"  +  assetVersion: "v3"
         ↓
  Asset Registry resolves:
         → storageUri: "s3://media-platform/proj_abc/asset_789/v3/master.mov"
         → checksum: "sha256:abc123..."
         → governance: classification=internal, license=enterprise-owned
```

### Benefits for Timeline Git

| Benefit | Mechanism |
|---------|-----------|
| **Stable identity across renames** | If file moves from S3 bucket A to B, assetId stays the same — timeline unchanged |
| **Version-aware diffs** | "Clip C1 upgraded from asset_789 v2 to v3" is a semantic change, not a path change |
| **Content-addressable rendering** | Cache key uses asset checksum — if file content unchanged, cache hits |
| **Governance-aware editing** | Before rendering, check: does this user have rights to use asset_789? |
| **Multi-tenant safety** | assetId is scoped to tenant — no cross-tenant file path leakage |
| **Collaboration safety** | Two editors can reference the same asset by ID — no file path conflicts |

### Timeline Git Does NOT Store

- Storage URIs in patch/snapshot payloads (references assetId instead)
- File system paths (these are ephemeral and environment-specific)
- Signed URLs (generated at render time by storage service)

---

## 11. Relationship with OTIO

### Three Layers, Three Purposes

```
Layer           | Purpose                         | Owner       | Format
─────────────────────────────────────────────────────────────────
OTIO            | Exchange format                 | Industry    | .otio JSON
                | Import from / export to NLEs    | standard    |
─────────────────────────────────────────────────────────────────
Timeline IR     | Canonical editing model         | Platform    | Internal
                | All editing operations target   | (internal)  | Timeline
                | this representation             |             | Schema 1.0
─────────────────────────────────────────────────────────────────
Timeline Git    | Version control layer           | Platform    | snapshots,
                | Snapshots, patches, diffs,      | (internal)  | revisions
                | merges, branches                |             | table
```

### Data Flow

```
NLE (Premiere / Resolve)
    │
    ▼ (export)
OTIO JSON
    │
    ▼ (OpenTimelineioAdapter.toOtioJson / fromOtioJson)
Timeline IR (Internal Timeline Schema 1.0)
    │
    ▼ (TimelineRevisionService.create)
Timeline Snapshot
    │
    ▼ (TimelineSemanticDiffService.diff)
Timeline Diff
    │
    ▼ (TimelinePatchService.apply)
New Timeline IR
    │
    ▼ (round-trip)
OTIO JSON → back to NLE
```

### What Timeline Git Adds Beyond OTIO

| OTIO Provides | Timeline Git Adds |
|---------------|-------------------|
| Clip/Track/Effect/Marker data model | Versioned history of that model |
| Single timeline state | Revision chain of states |
| Metadata key-value pairs | Semantic diff of metadata changes |
| No versioning concept | Snapshot → parentSnapshot chain (like Git) |
| No merge concept | Three-way merge of divergent timelines |

---

## 12. Relationship with Artifact DAG

### Why Timeline Git Does NOT Generate the DAG

Timeline Git versions the **source** (Timeline IR). The Artifact DAG is a **derived product** — compiled from the Timeline IR by the `ArtifactGraphCompiler`. These are orthogonal concerns:

```
Timeline Git (version control)
    │
    │  "Here is snapshot S at revision 42"
    │
    ▼
ArtifactGraphCompiler (compilation)
    │
    │  "Here is the DAG of intermediate products needed to render S"
    │
    ▼
Artifact DAG (execution source of truth)
```

### Why Separation Matters

| If Timeline Git generated the DAG... | Problem |
|--------------------------------------|---------|
| DAG would be version-controlled | DAG structure depends on provider capabilities, which change over time |
| Diff would include DAG changes | DAG changes when providers upgrade — not when the timeline changes |
| Merge would need to merge DAGs | DAG is derived, not authored — merging derived artifacts is nonsensical |
| Snapshot size would balloon | DAG may be 10x larger than the timeline payload |

### How They Connect

```
Timeline Snapshot (S₁)
    │
    ▼
compile(S₁) → Artifact DAG (D₁) → cache keys for S₁

Timeline Diff(S₁, S₂) → DirtyScope
    │
    ▼
incrementalCompile(D₁, DirtyScope) → Artifact DAG (D₂)
    │                                     │
    │                                     ▼
    │                          Only re-render changed nodes
```

---

## 13. Incremental Render Strategy

### How Timeline Diff Drives Incremental Rendering

```
Step 1: Create snapshot S₂ from user edits
Step 2: Compute TimelineDiff(S₁, S₂)
Step 3: From SemanticChange[] → identify:
        - Which clips changed (trim, move, effect, metadata)
        - Which tracks changed (new, removed, reordered)
        - Which time ranges are affected
Step 4: From affected clips → identify affected artifact nodes
Step 5: From artifact_node cache keys → identify cache hits/misses
Step 6: Re-render only artifact nodes with cache misses
```

### Dirty Scope Computation

```
Input:
  TimelineDiff with changes: [
    CLIP_TRIMMED: C1 out-point 10s→8s
    EFFECT_ADDED: blur on C3
    CLIP_ADDED: C5 at 15s
  ]

Output:
  DirtyScope {
    affectedTrackIds: [V1]
    affectedClipIds: [C1, C3]
    affectedTimeRanges: [(0s→15s)]  // C1 trim shifts everything after 8s
    addedClipIds: [C5]
    addedTimeRanges: [(15s→25s)]

    // Segments that MUST re-render:
    dirtyArtifactNodes: [
      "seg_v1_clip_c1_trim",     // C1 changed
      "seg_v1_clip_c3_blur",     // C3 has new effect
      "seg_v1_clip_c5_new"       // C5 is new
    ]

    // Segments that can reuse cache:
    cachedArtifactNodes: [
      "seg_v1_clip_c2",          // C2 unchanged
      "seg_v1_clip_c4"           // C4 unchanged
    ]
  }
```

### Cache Key Stability

Cache keys are content-addressable, derived from:
- Input asset checksum (from Asset Registry)
- Timeline segment specification hash (from snapshot)
- Effect parameter hash
- Engine version
- Font manifest hash

Because clips reference `assetId` (stable identity) and timeline segment hashes are derived from the canonicalized Timeline IR, a clip trim on C1 does NOT invalidate the cache key for C2 — only C1's segment is re-rendered.

---

## 14. Multi-user Collaboration

### How Timeline Git Enables Collaboration

Traditional NLEs require one person to work at a time (file-locked). Timeline Git enables:

```
             editor A                    editor B
                │                           │
     snapshot S₁ (shared base)
                │                           │
     branch "alice-cut"           branch "bob-color"
                │                           │
     S₂: trim intro              S₃: add color grade to C3
     S₄: add b-roll              S₅: adjust saturation on C1
                │                           │
                └───────────┬───────────────┘
                            │
                     merge(S₂, S₅, base=S₁)
                            │
                     snapshot S₆ (merged)
```

### Collaboration Workflow

```
1. Editor A and Editor B start from shared snapshot S₁
2. Each works on their own branch:
   - Editor A creates S₂, S₃, S₄ (branch "alice-cut")
   - Editor B creates S₅, S₆ (branch "bob-color")
3. Editor A initiates merge: merge("alice-cut", "bob-color", base=S₁)
4. TimelineGit computes:
   - diff(S₁, S₄) → A's changes
   - diff(S₁, S₆) → B's changes
5. Conflict detection:
   - Both modified C1? → CONFLICT
   - A modified C2, B modified C3? → NO CONFLICT (different clips)
   - A added clip to V1 at 5s, B added clip to V1 at 5s? → OVERLAP CONFLICT
6. If no conflicts → auto-merge → create S₇ (merge snapshot)
7. If conflicts → present conflict resolution UI → human resolves → create S₇
```

### Why Better Than Traditional NLE Collaboration

| Traditional NLE | Timeline Git |
|-----------------|--------------|
| One person at a time (file lock) | Multiple branches, merge later |
| "Save As" to try alternatives | Branch creates zero-copy divergence |
| Email files back and forth | Shared revision chain with merge |
| No way to see what colleague changed | Semantic diff shows exact changes |
| Manual re-edit to combine work | Three-way merge automates combination |
| No audit trail of who did what | Every snapshot has author + message |
| "Undo" only in your session | Full project history, jump to any state |

---

## 15. Competitive Analysis

### What We Borrow, What We Reject

| Reference | Borrow | Reject | Why |
|-----------|--------|--------|-----|
| **Git** | Commit model, parent chain, three-way merge, rebase, branch, conflict markers | Line-based diff, text file storage, staging area | Timeline entities (clips, tracks, effects) are semantic, not lines of text. No "staging area" needed — every edit creates a snapshot. |
| **vedit** | Timeline snapshots as commits, branch/merge for video, AI agent workflow, diff visualization | Provider/render architecture, execution engine | vedit focuses on editing-only metadata. Our platform adds rendering, asset registry, governance. vedit's diff is structural; we add semantic diff. |
| **Vit** | Git-backed timeline metadata, collaboration workflow, efficient serialization | Render orchestration, media production | Vit stores timeline metadata in Git repos. We store in PostgreSQL with Git-like semantics. Vit focuses on metadata; we add rendering. |
| **OpenTimelineIO** | Clip/Track/Effect/Marker data model, adapter architecture, custom metadata schema | Version control (OTIO has none), merge (OTIO has none), rendering (OTIO has none) | OTIO is the exchange format. We build version control on top of it. OTIO adapters provide import/export. |
| **Figma version history** | Visual diff, named versions, branch exploration | Real-time collaboration protocol, WebGL rendering, design-specific data model | Figma shows what changed visually. We need semantic diff for machine-actionable changes. |
| **Google Docs revision history** | Named versions, "see what changed", restore any version | Real-time OT/CRDT, text-focused diff | We need timeline-entity-aware diff, not character-level diff. |

---

## 16. Phase Roadmap

### Current Implementation Status (2026-06-24)

After three implementation sprints (22 tests passing), the roadmap has been recalibrated against code reality:

| Capability | Blueprint Phase | Actual Status | Status |
|-----------|----------------|---------------|--------|
| Snapshot | Phase 1 | ✅ Implemented + REST API | Complete |
| Patch | Phase 1 | ✅ Implemented + REST API | Complete |
| Diff (structural) | Phase 1 | ✅ Implemented + REST API | Complete |
| Diff (semantic) | Phase 1 | ✅ Implemented + REST API (25 types) | Complete |
| Restore | Phase 1 | ✅ Implemented + REST API | Complete |
| AI Proposal Review | Phase 2 | ✅ Implemented + REST API (adopt/reject) | Complete |
| Merge (engine) | Phase 2 | ✅ Implemented (three-way + conflict detection) | Internal only |
| Conflict Detection | Phase 2 | ✅ Implemented (8 conflict types) | Internal only |
| Conflict Resolution | Phase 2 | ✅ Implemented (USE_SOURCE/USE_TARGET) | Internal only |
| Merge Summary | — | ✅ Implemented | Internal only |
| Branch model | Phase 3 | ❌ Not implemented | Planned |
| Rebase | Phase 3 | ❌ Not implemented | Planned |
| Multi-user collaboration | Phase 3 | ❌ Not implemented | Planned |
| Artifact-aware incremental render | Phase 4 | ✅ Implemented (diff → impact → tasks) | Complete |

### Re-Prioritized Phases

#### Phase 1 — Productization (Now) ← HIGHEST PRIORITY

```
P0: Merge REST API          (engine done, needs endpoint)
P0: Asset version history API (schema done, needs endpoint)
P0: Asset governance CRUD API (schema done, needs endpoint)
P0: JSON-LD export API       (exporter done, needs endpoint)
```

**Rationale:** Three engines exist but have no REST APIs. Users cannot use merge, asset version history, or governance today. 3-6 days of API work unlocks 80% of user value.

#### Phase 2 — Productization Continued (P1)

```
P1: Branch model + REST API (branch = named revision pointer on existing chain)
P1: Merge conflict resolution REST API (resolver done, needs wrapper)
P1: AI proposal diff preview (auto-computed semantic diff on proposal)
```

#### Phase 3 — Collaboration (P2)

```
P2: Rebase engine (sequential patch replay)
P2: Blame (last-modified-by per clip/effect)
P2: Real-time presence (who's editing what)
```

#### Phase 4 — Asset Ecosystem (P3)

```
P3: Asset search
P3: Marketplace foundation
```

#### Deferred (P4+)

```
P4+: OpenLineage
P4+: Knowledge Graph
P4+: OpenAssetIO
P4+: Cloudflare Worker
```

---

## 17. Implementation Placement

### Current State

All Timeline Git infrastructure currently lives in `render-module`:

```
render-module/src/main/java/.../render/
  ├── domain/timeline/          ← TimelineSpec, TimelineClip, etc.
  │   └── internal/             ← SemanticChange, DirtyScope, EntityKind
  ├── app/timeline/             ← TimelineRevisionService, TimelinePatchService, 
  │                                TimelineEditorSyncService, TimelineSemanticDiffService
  └── infrastructure/timeline/  ← TimelineRevisionRepository
```

### Future Placement Recommendation

| Option | Pros | Cons | Recommendation |
|--------|------|------|----------------|
| **Stay in `render-module`** | No new module; zero migration cost; existing tests continue working | Timeline Git grows large; coupling with render concerns | Acceptable for Phase 1-2 |
| **Extract to `timeline-module`** | Clean separation of editing from rendering; dedicated module boundary | Cross-module dependency: render-module needs TimelineSpec from timeline-module; AssetRegistryService lives in render-module | **Recommended for Phase 3** — after merge and branch are stable |
| **New `timeline-git-module`** | Most clean; explicit Git semantics in module name | Premature extraction; too many small modules; "git" in name may be confusing | Not recommended — timeline version control is a capability of the timeline domain, not a separate domain |

**Decision: Keep in `render-module` for Phase 1-2. Plan extraction to `timeline-module` for Phase 3.**

The extraction boundary:
- `timeline-module` owns: `TimelineSpec`, `TimelineClip`, `TimelineSnapShot`, `TimelinePatch`, `TimelineDiff`, `TimelineMerge`
- `render-module` owns: `ArtifactGraphCompiler`, render providers, cache, execution graph
- `render-module` depends on `timeline-module` (reads TimelineSpec; writes Artifact DAG)
- `timeline-module` does NOT depend on `render-module` (pure editing domain)

---

## 18. Final Recommendation

### Timeline Git Should Be the Phase 1 Product Differentiator

**Yes.** Timeline Git is the first user-visible capability that distinguishes this platform from every other video rendering service. Rendering is a commodity (FFmpeg, Shotstack, Renderforest all do it). Version-controlled, AI-reviewable, merge-capable video editing is not.

### Why Timeline Git Has Higher Priority Than OpenLineage / Knowledge Graph / OpenAssetIO

| Capability | User-Visible? | Product Value | Phase |
|-----------|--------------|---------------|-------|
| **Timeline Git** | ✅ Yes — undo, diff, merge, AI review | **High** — every editor needs this daily | Phase 1-2 |
| OpenLineage | ❌ No — internal lineage event stream | Medium — audit/compliance value | Phase 3+ |
| Knowledge Graph | ⚠️ Partial — search use case | Medium — search/discovery value | Phase 3+ |
| OpenAssetIO | ❌ No — internal resolution layer | Low — integration value only | Phase 3+ |

**Rationale:** Timeline Git creates product value users can see and touch. OpenLineage, Knowledge Graph, and OpenAssetIO are infrastructure layers that enable future capabilities but deliver zero user-facing value on their own. Build what users need first.

### Strategic Ordering

```
Now (Phase 1-2):     Timeline Git (snapshot → patch → diff → merge → branch → AI review)
                      + Asset Registry (identity, version, governance)

Later (Phase 3+):    OpenLineage (lineage event stream)
                      + Knowledge Graph (asset relationship search)
                      + OpenAssetIO (DAM/MAM integration)

Much later (Phase 4+): Cross-cloud federation
                      + AI provenance standards
                      + Multi-tenant knowledge graph
```

---

## 19. Related Documents

| Document | Relationship |
|----------|-------------|
| [OTIO Render Platform Blueprint](otio-render-platform-blueprint.md) | Parent blueprint — Timeline Git is prioritized in §16-17 |
| [Current Timeline Git Status](../current/current-timeline-git-status.md) | Implemented capabilities (2026-06-24) |
| [Timeline Git Product Readiness](../../review/timeline-git-product-readiness.md) | Product assessment with user journey analysis |
| [Timeline Git Sprint 002 Implementation](../../review/timeline-git-sprint-002-merge-core.md) | Merge metadata + conflict detection |
| [Timeline Git Sprint 003 Implementation](../../review/timeline-git-sprint-003-true-merge.md) | True merge + conflict resolution |
| [OTIO + XMP + Asset Registry Placement Decision](../../review/otio-xmp-asset-registry-placement-decision.md) | Asset identity foundation for Timeline Git |
| [Reference Architecture Map](reference-architecture-map.md) | External references (vedit, Vit, OpenTimelineIO, Git, Perforce) |
| [Architecture Re-Prioritization Sprint](../../review/architecture-reprioritization-sprint.md) | Strategic decisions (2026-06-24) |

---

## 20. Timeline Git Productization

Timeline Git is now the **primary product differentiator** for the platform. Three sprints have built a solid engine (revision chain, patch, diff, merge, conflict, resolution — 22 tests passing). The next step is **productization**: exposing existing engines through REST APIs so users can actually use them.

### Productization Checklist

| Capability | Engine | REST API | Status |
|-----------|--------|----------|--------|
| History (list revisions) | ✅ | ✅ | Complete |
| Diff (compare revisions) | ✅ | ✅ | Complete |
| Restore (rollback) | ✅ | ✅ | Complete |
| AI Proposal | ✅ | ✅ | Complete |
| **Merge** | ✅ | **❌** | **Gap** |
| **Asset version history** | ✅ (schema) | **❌** | **Gap** |
| **Asset governance CRUD** | ✅ (schema) | **❌** | **Gap** |
| **JSON-LD export** | ✅ | **❌** | **Gap** |

### Why Productization Before Branch/Rebase

Branch and rebase are engine capabilities that build on merge. But merge has no REST API — users cannot merge today even though the engine works. Productization (exposing existing engines) delivers more user value faster than building new engines.

**Strategy:** Ship what works first. Build new engines after users can use the existing ones.

---

## 21. Productization Layer

### Product Surface

The productization layer transforms internal engine capabilities into user-facing features:

| Engine Capability | Product Feature | API | UX |
|-------------------|----------------|-----|-----|
| `TimelineRevisionService` | **History** — browse all revisions | `GET /history` | Revision list panel |
| `TimelineSemanticDiffService` | **Diff** — compare any two revisions | `GET /diff` | Semantic diff view |
| `TimelineRevisionService.restore()` | **Restore** — jump to any revision | `POST /restore` | Preview → Confirm dialog |
| `TimelineMergeService` | **Merge** — combine divergent edits | `POST /merge` | Merge preview + conflict resolution UI |
| `TimelineConflictDetector` | **Conflict View** — see what conflicts | Embedded in merge response | Side-by-side conflict cards |
| `TimelineConflictResolver` | **Resolve** — choose source or target | Embedded in merge request | Resolution picker UI |

### Review Layer (Future)

| Capability | Model | Reference |
|-----------|-------|-----------|
| **Review** — structured review workflow | `TimelineReview` (OPEN → APPROVED → MERGED) | GitHub PR |
| **Comment** — entity-anchored, threaded | `TimelineComment` + `TimelineCommentThread` | Frame.io timestamp comments |
| **Approval** — gated merge | `TimelineApproval` (APPROVE/REQUEST_CHANGES/REJECT) | GitHub PR approval |

### AI Integration

```
User instruction → AI generates Patch Proposal
     → Human reviews semantic diff
         → Approve / Request Changes / Reject
             → Approved → Apply → Create revision
```

AI never directly mutates the timeline. All changes go through Proposal → Review → Approval → Apply.

### Related Documents

| Document | Relationship |
|----------|-------------|
| [Timeline Git Productization Blueprint](timeline-git-productization-blueprint.md) | Full product surface design — APIs, UX, review, comment, approval |
