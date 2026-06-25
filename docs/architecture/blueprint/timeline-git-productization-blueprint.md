---
status: blueprint
created: 2026-06-24
scope: platform-app + render-module + future frontend
truth_level: target
owner: platform
---

# Timeline Git Productization Blueprint

> **Reality Check (2026-06-24):** Timeline Git engine is 80% implemented (revision, snapshot, patch, diff, merge, conflict, resolution, restore, AI proposal — 22 tests passing). Product APIs for merge, asset version, and governance are missing. No review workflow, comment system, approval workflow, or frontend UX exists. This blueprint defines the **product surface** — the APIs and UX that make the engine usable.

---

## 1. Product Vision

### What Users See

```
Timeline Git Product Surface
  ├── History          (browse all revisions — who changed what, when)
  ├── Diff              (compare any two revisions — semantic, not raw JSON)
  ├── Restore           (jump to any historical state — creates new revision, never overwrites)
  ├── Merge             (combine divergent edits — auto-merge or conflict resolution)
  ├── Review            (review timeline changes — Frame.io-style, anchored to entities)
  ├── Comment           (discuss specific clips, effects, markers — threaded, resolvable)
  └── Approval          (structured approval — approve, request changes, reject)
```

### How Users Operate

| Action | How | Reference |
|--------|-----|-----------|
| **Compare** | Select two revisions → semantic diff | GitHub Pull Request diff |
| **Review** | View diff → comment on clips/effects → approve/reject | Frame.io review panel |
| **Approve** | Mark review as approved → merge enabled | GitHub PR approval |
| **Request Changes** | Comment + "request changes" → author revises | GitHub PR review |
| **Restore** | Select historical revision → preview diff → confirm → new head revision | Git revert |
| **Merge** | Select source/target branches → auto-merge or resolve conflicts → merge commit | GitHub merge |

### How AI Participates

```
User instruction
    │
    ▼
AI generates Patch Proposal
    │  (never directly mutates timeline)
    ▼
Human reviews diff
    │  (see what would change)
    ▼
Human approves / requests changes / rejects
    │
    ▼
Approved → Apply patch → Create revision
```

**Invariant:** AI never directly modifies the Timeline IR. All AI changes go through Proposal → Review → Approval → Apply.

---

## 2. History API Design

### Endpoint

```
GET /api/v1/timelines/{projectId}/history
```

### Query Parameters

| Param | Type | Description |
|-------|------|-------------|
| `from` | timestamp | Filter revisions after this time |
| `to` | timestamp | Filter revisions before this time |
| `author` | string | Filter by author user ID |
| `source` | string | Filter by source (sync, merge, ai_proposal, manual) |
| `isMerge` | boolean | Filter merge commits only |
| `label` | string | Filter by label/tag |
| `limit` | int | Max results (default 50, max 200) |
| `offset` | int | Pagination offset |

### Response

```json
[
  {
    "revisionId": "trev_a1b2c3",
    "revisionNumber": 42,
    "parentRevisionId": "trev_x9y8z7",
    "author": "user_alice",
    "authorName": "Alice Chen",
    "createdAt": "2026-06-24T14:30:00Z",
    "message": "Trim intro to 3 seconds",
    "source": "sync",
    "isMerge": false,
    "mergeParents": null,
    "labels": ["v1.0-rc"],
    "changeSummary": {
      "clipsAdded": 0,
      "clipsRemoved": 0,
      "clipsModified": 1,
      "tracksAdded": 0,
      "tracksModified": 0
    },
    "snapshotId": "snap_d4e5f6"
  }
]
```

### History UI Blueprint

```
┌─ Timeline History ──────────────────────────────────────────────┐
│ [Labels: v1.0 ▼] [Author: All ▼] [Source: All ▼] [Only Merges □]│
│──────────────────────────────────────────────────────────────────│
│ ● trev_a1b2c3  #42  Alice Chen       2 min ago                  │
│   "Trim intro to 3 seconds"                        [sync]        │
│   1 clip modified                                                │
│                                                                  │
│ ○ trev_x9y8z7  #41  Bob Zhang        5 min ago                  │
│   "Add color grade to hero shot"                   [sync]        │
│   1 effect added                                                 │
│                                                                  │
│ ● trev_m1n2o3  #40  Merge Commit      8 min ago      [MERGE]    │
│   "Merge alice-cut into main"                                    │
│   Parents: #38, #39   3 clips auto-merged                        │
│──────────────────────────────────────────────────────────────────│
│ [Restore] [Compare] [View Snapshot]                              │
└──────────────────────────────────────────────────────────────────┘
```

**Reference patterns:**
- **GitHub commit history** — linear list with author, timestamp, message, hash
- **Perforce changelist view** — numbered revisions with summary stats
- **Differentiator:** Our history shows timeline-specific stats (clips modified, tracks added, effects changed) — not just code changes.

---

## 3. Diff API Design

### Endpoint

```
GET /api/v1/timelines/{projectId}/diff
```

### Query Parameters

| Param | Type | Description |
|-------|------|-------------|
| `from` | string | Source revision ID (required) |
| `to` | string | Target revision ID (required) |
| `format` | string | `semantic` (default) or `structural` |

### Response — Semantic Diff

```json
{
  "fromRevision": "trev_x9y8z7",
  "toRevision": "trev_a1b2c3",
  "structurallyEqual": false,
  "changes": [
    {
      "kind": "CLIP",
      "entityId": "clip_intro_title",
      "action": "MODIFIED",
      "changeType": "CLIP_RANGE_CHANGED",
      "summary": "Trimmed intro from 5.0s to 3.0s",
      "details": {
        "oldOutPoint": "5.0s",
        "newOutPoint": "3.0s"
      },
      "commentCount": 2
    },
    {
      "kind": "CLIP",
      "entityId": "clip_broll_forest",
      "action": "ADDED",
      "changeType": "CLIP_ADDED",
      "summary": "Added forest b-roll at 3.0s",
      "details": {
        "atTime": "3.0s",
        "assetId": "asset_789",
        "assetVersion": "v3"
      },
      "commentCount": 0
    },
    {
      "kind": "CLIP",
      "entityId": "clip_hero_shot",
      "action": "MODIFIED",
      "changeType": "CLIP_EFFECT_CHANGED",
      "summary": "Added color grade effect",
      "details": {
        "effectKey": "color.grade.cinematic",
        "parameters": { "saturation": 1.3 }
      },
      "commentCount": 1
    }
  ],
  "summary": {
    "clipsAdded": 1,
    "clipsRemoved": 0,
    "clipsModified": 2,
    "tracksAdded": 0,
    "tracksModified": 0
  }
}
```

### Diff UI Blueprint

```
┌─ Timeline Diff: #41 → #42 ──────────────────────────────────────┐
│ [Overview] [By Track] [By Clip]                                  │
│──────────────────────────────────────────────────────────────────│
│ Changes (3)                                                      │
│                                                                  │
│ ┌ CLIP_MODIFIED: clip_intro_title ─────────────────────── [2 💬]─┐
│ │ Trimmed intro from 5.0s to 3.0s                               │
│ │ [Expand Details]                                               │
│ └────────────────────────────────────────────────────────────────┘│
│                                                                  │
│ ┌ CLIP_ADDED: clip_broll_forest ────────────────────────────────┐│
│ │ Added forest b-roll at 3.0s                                    │
│ │ Asset: asset_789 v3                                            │
│ └────────────────────────────────────────────────────────────────┘│
│                                                                  │
│ ┌ CLIP_MODIFIED: clip_hero_shot ───────────────────────── [1 💬]─┐│
│ │ Added color grade effect (saturation: 1.3)                    │
│ └────────────────────────────────────────────────────────────────┘│
│──────────────────────────────────────────────────────────────────│
│ [Approve] [Request Changes] [Comment]                            │
└──────────────────────────────────────────────────────────────────┘
```

**Reference patterns:**
- **GitHub PR diff** — file-level changes with expand/collapse, inline comments
- **Frame.io version compare** — side-by-side visual player (future)
- **Differentiator:** Semantic diff shows WHAT changed at the entity level (clip trimmed, effect added), not raw JSON diffs.

---

## 4. Restore API Design

### Endpoint

```
POST /api/v1/timelines/{projectId}/restore
```

### Request

```json
{
  "revisionId": "trev_x9y8z7",
  "message": "Restore to revision #36 (fix intro timing)"
}
```

### Response

```json
{
  "restoredRevisionId": "trev_new_head",
  "sourceRevisionId": "trev_x9y8z7",
  "previousHeadRevisionId": "trev_old_head",
  "diffSummary": {
    "clipsAdded": 0,
    "clipsRemoved": 1,
    "clipsModified": 2
  }
}
```

### Workflow

```
Select historical revision
    │
    ▼
Preview diff (current head → selected revision)
    │  "Restoring will remove clip X, modify clip Y"
    ▼
Confirm restore
    │
    ▼
Create new head revision
    │  NEVER overwrites history
    │  parent_revision_id = current head
    │  snapshot = copy of selected revision's snapshot
    │  source = "restore"
    ▼
Return new revision ID + diff summary
```

### Principle

**Restore creates a new revision. History is immutable.** This is Git's model (`git revert` creates a new commit). Never overwrite the revision chain.

---

## 5. Merge API Design

### Endpoint

```
POST /api/v1/timelines/{projectId}/merge
```

### Request (Auto-Merge)

```json
{
  "baseRevisionId": "trev_base",
  "sourceRevisionId": "trev_alice_cut",
  "targetRevisionId": "trev_main",
  "authorUserId": "user_alice",
  "message": "Merge alice-cut into main"
}
```

### Request (With Resolutions)

```json
{
  "baseRevisionId": "trev_base",
  "sourceRevisionId": "trev_alice_cut",
  "targetRevisionId": "trev_bob_color",
  "authorUserId": "user_alice",
  "message": "Merge bob-color into alice-cut with resolutions",
  "resolutions": {
    "CLIP:clip_hero": { "mode": "USE_SOURCE" },
    "CLIP:clip_broll": { "mode": "USE_TARGET" }
  }
}
```

### Response — Merged

```json
{
  "status": "MERGED",
  "mergeRevisionId": "trev_merge_abc",
  "baseRevisionId": "trev_base",
  "sourceRevisionId": "trev_alice_cut",
  "targetRevisionId": "trev_bob_color",
  "mergeSummary": {
    "autoMergedCount": 5,
    "conflictCount": 0,
    "sourceChangesApplied": 3,
    "targetChangesApplied": 2,
    "mergedEntityIds": ["CLIP:clip_a", "CLIP:clip_b"]
  }
}
```

### Response — Conflicts

```json
{
  "status": "CONFLICTS",
  "baseRevisionId": "trev_base",
  "sourceRevisionId": "trev_alice_cut",
  "targetRevisionId": "trev_bob_color",
  "mergeSummary": {
    "autoMergedCount": 3,
    "conflictCount": 2,
    "sourceChangesApplied": 2,
    "targetChangesApplied": 1,
    "sourceChangesRejected": 1,
    "targetChangesRejected": 1,
    "mergedEntityIds": ["CLIP:clip_a"],
    "conflictedEntityIds": ["CLIP:clip_hero", "CLIP:clip_broll"]
  },
  "conflicts": [
    {
      "conflictId": "conflict_CLIP_clip_hero",
      "entityRef": { "kind": "CLIP", "id": "clip_hero" },
      "conflictType": "SAME_ENTITY_MODIFIED",
      "sourceChange": {
        "changeType": "CLIP_RANGE_CHANGED",
        "summary": "Trimmed to 3.0s"
      },
      "targetChange": {
        "changeType": "CLIP_SPEED_CHANGED",
        "summary": "Speed changed to 1.5x"
      },
      "message": "same entity modified on both branches: clip_hero"
    }
  ]
}
```

### Consistency with Engine

This API directly wraps the existing engine:

| API Operation | Engine Method |
|--------------|---------------|
| `POST /merge` (no resolutions) | `TimelineMergeService.threeWayMerge(request)` |
| `POST /merge` (with resolutions) | `TimelineMergeService.threeWayMergeWithResolutions(request, resolutions)` |
| Conflict detection | `TimelineConflictDetector.detect(sourceChanges, targetChanges)` |
| Conflict resolution | `TimelineConflictResolver.resolve(conflict, intent)` |

---

## 6. Review Workflow Blueprint

### Model — TimelineReview

```
TimelineReview {
    reviewId:          String
    projectId:         String
    revisionId:        String          // the revision being reviewed
    parentRevisionId:  String          // baseline for comparison
    authorUserId:      String          // who created the timeline change
    status:            ReviewStatus    // OPEN | APPROVED | CHANGES_REQUESTED | MERGED | CLOSED
    title:             String          // review title (e.g., "Alice's intro cut")
    description:       String          // optional description
    createdAt:         Instant
    updatedAt:         Instant
    approvedBy:        String|null     // who approved
    approvedAt:        Instant|null
    mergedBy:          String|null     // who merged
    mergedAt:          Instant|null
    mergeRevisionId:   String|null     // resulting merge revision
}

ReviewStatus: OPEN | APPROVED | CHANGES_REQUESTED | MERGED | CLOSED
```

### Workflow States

```
OPEN ──────────────────────────────────────────────────────────────
  │  Review created. Reviewers can comment.
  │
  ├── APPROVED ───────────────────────────────────────────────────
  │     All reviewers approved. Merge is enabled.
  │     │
  │     └── MERGED ───────────────────────────────────────────────
  │           Merged into target branch.
  │
  ├── CHANGES_REQUESTED ──────────────────────────────────────────
  │     Reviewer requested changes. Author must revise.
  │     │
  │     └── OPEN (author pushes new revision → review reopened)
  │
  └── CLOSED ─────────────────────────────────────────────────────
        Review closed without merging (abandoned or superseded).
```

### Reference Mapping

```
Our Review Workflow    ≈  GitHub PR Review  +  Frame.io Approval
──────────────────────────────────────────────────────────────────
GitHub PR Review        →  Comment threads, approve/request changes, merge gate
Frame.io Approval       →  Structured review status, stakeholder roles, visual comparison
Our Differentiator      →  Entity-anchored comments (clip-level, not frame-level)
                           + Semantic diff as the review artifact
                           + AI can be a reviewer (proposing alternatives)
```

---

## 7. Comment Model

### Design Principle

Comments must be anchored to **stable platform entities**, not raw timecodes. Timecodes change when the timeline is edited. Entity IDs are stable.

### Comment Types

| Type | Anchor | Example |
|------|--------|---------|
| **Review Comment** | `reviewId` | General feedback on the whole review |
| **Diff Comment** | `diffId` + `entityRef` | Comment on a specific changed clip in the diff |
| **Clip Comment** | `revisionId` + `entityRef(CLIP, id)` | "This clip needs better audio" |
| **Effect Comment** | `revisionId` + `entityRef(CLIP, id)` + `effectKey` | "Color grade is too saturated" |
| **Marker Comment** | `revisionId` + `entityRef(MARKER, id)` | "Legal needs to review this section" |
| **Conflict Comment** | `conflictId` | Discussion on how to resolve a merge conflict |

### Model

```
TimelineComment {
    commentId:          String
    reviewId:           String|null      // null = standalone comment on revision
    revisionId:         String|null      // the revision being commented on
    diffId:             String|null      // the diff context
    entityRef:          EntityRef|null   // CLIP:clip_hero, EFFECT:clip_hero:color_grade
    threadId:           String|null      // parent thread (null = top-level)
    authorUserId:       String
    body:               String           // markdown
    status:             CommentStatus    // ACTIVE | RESOLVED | OUTDATED
    createdAt:          Instant
    updatedAt:          Instant
}

CommentStatus: ACTIVE | RESOLVED | OUTDATED
```

### Thread Model

```
TimelineCommentThread {
    threadId:           String
    reviewId:           String
    entityRef:          EntityRef
    status:             ThreadStatus     // OPEN | RESOLVED | REOPENED
    commentCount:       int
    firstComment:       TimelineComment
    resolvedBy:         String|null
    resolvedAt:         Instant|null
}

ThreadStatus: OPEN | RESOLVED | REOPENED
```

### Why NOT Timecode-Only

```
WRONG:
  "At 1:23 in the video, the color is off"

RIGHT:
  "clip_hero_shot has a color grade effect with saturation 1.3 — reduce to 1.1"

The right approach:
  - entityRef = "CLIP:clip_hero_shot"
  - effectKey = "color.grade.cinematic"
  - parameter = "saturation": 1.3 → 1.1

Why:
  - If the timeline is edited and clip_hero_shot moves from 1:23 to 2:05, 
    the comment still points to the right clip
  - Entity IDs are stable across revisions
  - Timecodes shift; semantics don't
```

---

## 8. Approval Workflow

### Model — TimelineApproval

```
TimelineApproval {
    approvalId:         String
    reviewId:           String
    reviewerUserId:     String
    decision:           ApprovalDecision   // APPROVE | REQUEST_CHANGES | REJECT
    comment:            String|null
    createdAt:          Instant
}

ApprovalDecision: APPROVE | REQUEST_CHANGES | REJECT
```

### Workflow Rules

| Rule | Description |
|------|-------------|
| **Gate** | Review must be APPROVED before merge. CHANGES_REQUESTED blocks merge. |
| **Multi-reviewer** | Multiple reviewers may be required (configurable: 1, 2, or all must approve). |
| **Self-review** | Author cannot approve their own review. |
| **Re-approval** | If author pushes new revision after approval, previous approvals are reset. |
| **Override** | Project admins may override approval requirements (audited). |

### Mapping to GitHub PR

```
GitHub PR                →  Timeline Review
─────────────────────────────────────────────────────
PR created               →  Review created (status: OPEN)
Reviewer comments        →  TimelineComment with reviewId
"Request changes"        →  ApprovalDecision.REQUEST_CHANGES
"Approve"                →  ApprovalDecision.APPROVE
PR merged                →  Status → MERGED
PR closed without merge  →  Status → CLOSED
```

### Mapping to Frame.io

```
Frame.io                 →  Timeline Review
─────────────────────────────────────────────────────
Version uploaded         →  Revision snapshot created
Reviewer comments        →  Entity-anchored comments
"Needs Review" → "Approved" →  ReviewStatus OPEN → APPROVED
Share link               →  Guest review access (future)
```

---

## 9. AI Proposal Workflow

### Existing Review Loop (Already Implemented)

```
User instruction ("Make intro shorter")
    │
    ▼
AI generates Patch Proposal          ← AiTimelineEditService.editTimeline()
    │                                   
    ▼
Proposal stored as pending           ← InternalTimelineAiProposals
    │  (under platformExtensions.aiProposals)
    ▼
Human reviews proposal               ← Needs: semantic diff preview
    │
    ├── APPROVE  → Apply patch       ← AiTimelineProposalService.adopt()
    │               Create revision   ← TimelineRevisionService.recordAiAdoptRevision()
    │
    └── REJECT   → Discard           ← AiTimelineProposalService.reject()
```

### Enhancement: AI Proposal with Diff Preview

The current adoption flow lacks a pre-computed diff preview. Enhancement:

```
POST /api/v1/timelines/{projectId}/ai-proposals/{proposalId}/preview

Response:
{
  "proposalId": "prop_abc123",
  "status": "PENDING",
  "agentModel": "gpt-4o",
  "userInstruction": "Make intro shorter",
  "confidence": 0.87,
  "reason": "Trimmed intro by 40% while preserving key message. 
             Removed 2 redundant establishing shots.",
  "semanticDiff": {
    "changes": [
      { "changeType": "CLIP_RANGE_CHANGED", "entityId": "clip_intro", 
        "summary": "Trimmed from 5.0s to 3.0s" },
      { "changeType": "CLIP_REMOVED", "entityId": "clip_establish_2",
        "summary": "Removed redundant establishing shot" }
    ]
  }
}
```

### AI Participation Rules

| Rule | Rationale |
|------|-----------|
| AI never directly mutates the timeline | All changes go through Proposal → Review → Approval → Apply |
| AI proposal includes confidence + reason | Reviewer needs to understand AI's intent and trustworthiness |
| AI can be a reviewer | AI can analyze proposals from other AI agents or humans |
| AI merge assistance | AI can suggest resolutions for merge conflicts (future) |
| AI cannot approve | Only humans can approve proposals |

---

## 10. Frontend UX Blueprint

### Layout

```
┌─ Timeline Review ─────────────────────────────────────────────────────────┐
│ Review #42: "Alice's intro cut"                      [APPROVED] [Merge ▼] │
├──────────────┬──────────────────────────────────┬────────────────────────┤
│ LEFT PANEL   │        CENTER PANEL              │     RIGHT PANEL        │
│              │                                  │                        │
│ History      │  Timeline Diff                   │  Review Thread         │
│              │                                  │                        │
│ #42 (HEAD) ● │  ┌──────────────────────────┐   │  Alice Chen (author)   │
│ #41         │  │ CLIP_MODIFIED: intro      │   │  "Trimmed intro..."    │
│ #40 [MERGE] │  │ Trimmed from 5s to 3s     │   │                        │
│ #39         │  │                      [💬2]│   │  ─────────────────────  │
│ #38         │  └──────────────────────────┘   │  Bob Zhang (reviewer)   │
│ #37         │                                  │  ✅ Approved            │
│ ...         │  ┌──────────────────────────┐   │  "Looks good!"          │
│              │  │ CLIP_REMOVED: est_2     │   │                        │
│ [Select #36]│  │ Removed redundant shot   │   │  ─────────────────────  │
│ [Compare]   │  │                      [💬1]│   │  Thread: clip_intro     │
│ [Restore]   │  └──────────────────────────┘   │  💬 Carol: "Too short?" │
│              │                                  │  💬 Alice: "Perf now"  │
│              │  ┌──────────────────────────┐   │  ↻ Resolved            │
│              │  │ CLIP_ADDED: broll       │   │                        │
│              │  │ Forest b-roll at 3s      │   │                        │
│              │  └──────────────────────────┘   │  [Reply...]            │
│              │                                  │                        │
│              │  [Request Changes] [Approve]    │  [Resolve Thread]      │
├──────────────┴──────────────────────────────────┴────────────────────────┤
│ Timeline Player: ───●──────────────────────────────────── 0:00 / 2:30     │
│ (Optional: Preview render output for the selected revision)               │
└───────────────────────────────────────────────────────────────────────────┘
```

### Panel Descriptions

| Panel | Function | Reference |
|-------|----------|-----------|
| **Left: History** | Revision list with selection, compare, restore | GitHub commit list |
| **Center: Diff** | Semantic diff of selected revisions. Click entity → jump to comment thread. | GitHub PR file diff |
| **Right: Review** | Review status, approvals, comment threads, reply box | GitHub PR conversation + Frame.io comment panel |
| **Bottom: Player** | Optional render preview for visual comparison (future) | Frame.io video player |

---

## 11. Updated Roadmap

### Productization Priorities

| Priority | Layer | Capability | Status |
|----------|-------|-----------|--------|
| **P0** | API | History API (`GET /history`) | ✅ Exists |
| **P0** | API | Diff API (`GET /diff`, `GET /compare`) | ✅ Exists |
| **P0** | API | Restore API (`POST /restore`) | ✅ Exists |
| **P0** | API | Merge API (`POST /merge`) | ❌ Missing |
| **P1** | Product | Review Workflow (create/update/status/approve) | Not designed |
| **P1** | Product | Comment System (threaded, entity-anchored) | Not designed |
| **P1** | Product | Approval Workflow (approve/reject/gate merge) | Not designed |
| **P2** | Product | Asset Ingestion Blueprint (Upload, ASR, OCR, Vision) | Blueprint only |
| **P3** | Product | Asset Search API | Not started |
| **P4** | Product | Marketplace Foundation | Blueprint only |
| **Deferred** | Engine | Branch model, Rebase, OpenCue, OpenAssetIO, Knowledge Graph | Future |

### Sprint 004 Plan

```
Goal: Expose Merge API + Start Review model schema

Tasks:
  1. POST /merge endpoint                         (1-2 days)
  2. POST /merge/resolve endpoint                 (0.5 day)
  3. timeline_review table (V1 baseline)           (0.5 day)
  4. timeline_comment table (V1 baseline)          (0.5 day)
  5. timeline_approval table (V1 baseline)         (0.5 day)
  6. Review REST API (CRUD + status transitions)   (2 days)
  7. Tests                                         (1 day)

Estimated: 5-7 days
Constraints: No new modules. Extend V1 baseline (pre-deployment).
```
