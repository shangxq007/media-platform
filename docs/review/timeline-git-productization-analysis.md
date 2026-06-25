---
status: analysis
created: 2026-06-24
scope: platform-app + render-module
truth_level: validated
owner: platform
---

# Timeline Git Productization Analysis

> **Analysis Date:** 2026-06-24
> **Method:** Product readiness assessment + API design + UX blueprint
> **Reference:** Frame.io, GitHub PR, Perforce, Figma Community

---

## 1. Current State

### What Works (Engine Layer)

| Capability | Engine | Tests | REST API |
|-----------|--------|-------|----------|
| Revision chain | `TimelineRevisionService` | ✅ | ✅ `GET /revisions` |
| Snapshot | `TimelineSnapshotService` | ✅ | ✅ `POST /snapshots` |
| Structural Diff | `TimelineRevisionDiffService` | ✅ | ✅ `GET /compare` |
| Semantic Diff | `TimelineSemanticDiffService` | ✅ | ✅ `POST /diff_timelines` |
| Restore | `TimelineRevisionService.restore()` | ✅ | ✅ `POST /restore` |
| AI Proposal | `AiTimelineProposalService` | ✅ | ✅ `POST /ai-edit`, `/adopt`, `/reject` |
| Merge (engine) | `TimelineMergeService.threeWayMerge()` | ✅ | ❌ |
| Merge (resolutions) | `TimelineMergeService.threeWayMergeWithResolutions()` | ✅ | ❌ |
| Conflict detection | `TimelineConflictDetector` | ✅ | ❌ (embedded in merge) |
| Conflict resolution | `TimelineConflictResolver` | ✅ | ❌ (embedded in merge) |

### What's Missing (Product Layer)

| Layer | Gap | Priority |
|-------|-----|----------|
| **API** | Merge REST endpoint | P0 |
| **API** | Asset version history endpoint | P0 |
| **API** | Asset governance CRUD endpoint | P0 |
| **Product** | Review workflow (OPEN → APPROVED → MERGED) | P1 |
| **Product** | Comment system (entity-anchored, threaded) | P1 |
| **Product** | Approval workflow (APPROVE/REQUEST_CHANGES/REJECT) | P1 |
| **UX** | History panel | P1 |
| **UX** | Semantic diff view | P1 |
| **UX** | Merge preview + conflict resolution UI | P1 |

---

## 2. API Surface Summary

### Existing APIs (Ready)

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/revisions` | GET | List revision history |
| `/revisions/{id}` | GET | Get single revision |
| `/revisions/{id}/restore` | POST | Restore to historical revision |
| `/revisions/compare?from=&to=` | GET | Structural diff |
| `/diff_timelines` | POST | Semantic diff |
| `/ai-edit` | POST | AI proposal generation |
| `/ai-proposals/{id}/adopt` | POST | Approve AI proposal |
| `/ai-proposals/{id}/reject` | POST | Reject AI proposal |
| `/import_otio` | POST | Import OTIO timeline |
| `/export_otio` | POST | Export OTIO timeline |

### Missing APIs (P0)

| Endpoint | Method | Purpose | Engine |
|----------|--------|---------|--------|
| `/merge` | POST | Three-way merge | `TimelineMergeService.threeWayMerge()` |
| `/merge/resolve` | POST | Merge with conflict resolutions | `TimelineMergeService.threeWayMergeWithResolutions()` |
| `/assets/{id}/versions` | GET | Asset version history | `AssetRegistryService` |
| `/assets/{id}/governance` | PUT | Update asset governance | `AssetRegistryService` |
| `/assets/{id}/jsonld` | GET | JSON-LD export | `AssetJsonLdExporter` |

### Future APIs (P1)

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/reviews` | POST | Create review |
| `/reviews/{id}` | GET | Get review with comments |
| `/reviews/{id}/approve` | POST | Approve review |
| `/reviews/{id}/request-changes` | POST | Request changes |
| `/reviews/{id}/comments` | POST | Add comment |
| `/reviews/{id}/comments/{threadId}` | GET | Get comment thread |

---

## 3. Review Workflow Analysis

### Why Review Matters

The Timeline Git Product Readiness Assessment rated **User Value Maturity at 4/10**. The biggest gap was "no way to review and discuss timeline changes." Review workflow addresses this directly:

| User Need | Today | With Review |
|-----------|-------|-------------|
| "Show me what changed" | `GET /compare` (JSON only) | Semantic diff view with entity-level cards |
| "Discuss this specific change" | Not possible | Comment on CLIP:clip_hero with threaded replies |
| "Is this ready to merge?" | No mechanism | ReviewStatus: APPROVED → merge enabled |
| "I need you to fix X" | Not possible | Request changes → author revises → new revision |
| "Who approved this?" | No audit trail | TimelineApproval records reviewer, timestamp |

### Frame.io vs Our Review Model

| Dimension | Frame.io | Our Model |
|-----------|----------|-----------|
| **What is reviewed** | Rendered video output | Timeline change (pre-render) |
| **Where comments anchor** | Frame timecode | Entity ID (clip, effect, marker) |
| **Review cycle** | Export → Upload → Review → Re-edit → Re-export | Edit → Review → Approve → Render (no export cycle) |
| **Visual comparison** | Side-by-side video player | Future: Render preview for diff |
| **Approval** | Version-level | Revision-level with merge gate |
| **AI participation** | None | AI can propose edits, review proposals |

---

## 4. Comment System Analysis

### Design Tradeoff: Timecode vs Entity

| Approach | Stability | Example |
|----------|-----------|---------|
| **Timecode** (Frame.io) | Fragile — shifts when timeline edits | "At 1:23, the color is off" |
| **Entity ID** (ours) | Stable — survives timeline edits | "CLIP:clip_hero has saturation 1.3" |

**Decision:** Comments are entity-anchored. Entity IDs are stable across revisions. Timecode comments break when clips are moved, trimmed, or split.

### Comment Lifecycle

```
User creates comment → ACTIVE
    │
    ▼
Author replies → Thread grows
    │
    ▼
Issue addressed → RESOLVED
    │
    ▼
New revision invalidates fix → REOPENED
    │
    ▼
Original clip deleted → OUTDATED
```

---

## 5. Approval System Analysis

### Model

Three decisions, each recorded as a `TimelineApproval`:

| Decision | What It Means | Effect |
|----------|--------------|--------|
| `APPROVE` | "This change is good, ready to merge" | Increments approval count |
| `REQUEST_CHANGES` | "Fix these issues before merging" | Blocks merge until resolved |
| `REJECT` | "This change should not be merged" | Closes review |

### Merge Gate

```
Can merge? = 
  Review.status == APPROVED
  AND (requiredApprovalCount satisfied)
  AND (no pending REQUEST_CHANGES)
  AND (author != sole approver)
```

### Multi-Reviewer Support

| Config | Description |
|--------|-------------|
| Min 1 approval | Default — any one reviewer can approve |
| Min 2 approvals | Two reviewers must approve |
| All must approve | Every assigned reviewer must approve |
| Specific approver | Only designated approvers count |

---

## 6. Future Roadmap

### P0 — Ship Next (Sprint 004)

```
Merge API:
  POST /merge
  POST /merge/resolve

Asset API:
  GET /assets/{id}/versions
  PUT /assets/{id}/governance
  GET /assets/{id}/jsonld
```

### P1 — Next Quarter

```
Review Workflow:
  POST /reviews (create)
  GET /reviews/{id} (view with comments)
  POST /reviews/{id}/approve
  POST /reviews/{id}/request-changes

Comment System:
  POST /reviews/{id}/comments
  GET /comments/{threadId}
  POST /comments/{id}/resolve

Schema:
  timeline_review table
  timeline_comment table
  timeline_approval table
```

### P2 — Following Quarter

```
Asset Ingestion Blueprint
  Upload pipeline
  ASR/OCR/Vision provider interfaces
  Embedding generation strategy
```

### P3 — Later

```
Asset Search
  PostgreSQL identity/governance/lineage search
  ElasticSearch full-text (transcripts, OCR)

Marketplace Foundation
  Template/effect/plugin listing
  Install API
```

### Deferred

```
Branch model
Rebase
OpenCue
OpenAssetIO
Knowledge Graph
Cloudflare Worker
```

---

## 7. Related Documents

| Document | Relationship |
|----------|-------------|
| [Timeline Git Productization Blueprint](../architecture/blueprint/timeline-git-productization-blueprint.md) | Full product surface design |
| [Timeline Git Blueprint](../architecture/blueprint/timeline-git-blueprint.md) | Engine architecture |
| [Timeline Git Product Readiness](timeline-git-product-readiness.md) | Product assessment |
| [Frame.io Reference Analysis](frameio-reference-analysis.md) | Review workflow reference |
| [Architecture Re-Prioritization Sprint](architecture-reprioritization-sprint.md) | Strategic decisions |
