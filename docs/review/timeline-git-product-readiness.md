---
status: product-assessment
created: 2026-06-24
scope: render-module + platform-app + V1 baseline
truth_level: validated-against-code
owner: platform
---

# Timeline Git Product Readiness Assessment

> **Assessment Date:** 2026-06-24
> **Method:** Code audit + API endpoint inventory + capability walkthrough
> **Conclusion:** Phase 1 engineering foundation is solid (75%). Product-level readiness is moderate (50%). Missing: merge API, branch UX, asset version API.

---

## 1. Executive Summary

### Overall Product Readiness: **6/10**

| Dimension | Score | Rationale |
|-----------|-------|-----------|
| **Engineering Maturity** | 8/10 | Revision chain, patch, diff, merge engine, conflict resolver, OTIO adapter — all implemented with tests (22 passing). Code is clean, follows modulith boundaries. |
| **Product Maturity** | 5/10 | History and diff have REST APIs. Merge engine exists but has no API. Asset version/lineage have no API. OTIO import/export works. No UI for diff/merge/history. |
| **User Value Maturity** | 4/10 | Core capabilities exist but most are internal-only. A user cannot browse history, compare versions, or merge branches without API-level tooling. AI proposal review is the only user-facing workflow. |

### What's Ready for Demos Today

| Capability | API? | UI? | Demo-Ready? |
|-----------|------|-----|-------------|
| Timeline history (list revisions) | ✅ | ⚠️ | Yes — API only |
| Timeline diff (compare two revisions) | ✅ | ❌ | Yes — API only |
| Timeline restore (rollback) | ✅ | ❌ | Yes — API only |
| OTIO import/export | ✅ | ❌ | Yes — API only |
| AI proposal + approve/reject | ✅ | ❌ | Yes — API only |
| Timeline merge (three-way) | ❌ | ❌ | No |
| Asset version history | ❌ | ❌ | No |
| Asset lineage tracking | ❌ | ❌ | No |

---

## 2. Capability Matrix

| Capability | Implemented | Product-Ready | Missing Pieces |
|-----------|-------------|---------------|----------------|
| **Revision History** | ✅ `timeline_revision` table + `TimelineRevisionService` + REST API | ✅ | UI for browsing history |
| **Restore** | ✅ `POST /restore` creates new head revision from historical snapshot | ✅ | UI for selecting revision to restore |
| **Patch Application** | ✅ `TimelinePatchService` (RFC6902) + `POST /patch_timeline` | ✅ | — |
| **Structural Diff** | ✅ `TimelineRevisionDiffService` + `GET /compare` | ✅ | UI visualization |
| **Semantic Diff** | ✅ `TimelineSemanticDiffService` (25 types) + `POST /diff_timelines` | ✅ | — |
| **Merge (engine)** | ✅ `TimelineMergeService.threeWayMerge()` | ⚠️ Internal only | **No REST endpoint** |
| **Merge (with resolutions)** | ✅ `threeWayMergeWithResolutions()` | ❌ | No API, no UI |
| **Conflict Detection** | ✅ `TimelineConflictDetector` (8 conflict types) | ⚠️ Internal only | No API, no UI |
| **Conflict Resolution** | ✅ `TimelineConflictResolver` (USE_SOURCE/USE_TARGET) | ❌ | No API, no MANUAL mode |
| **AI Proposal** | ✅ `AiTimelineProposalService` + adopt/reject REST API | ✅ | UI for diff preview |
| **OTIO Import** | ✅ `OpenTimelineioAdapter.fromOtioJson()` + REST API | ✅ | Kdenlive round-trip not validated |
| **OTIO Export** | ✅ `OpenTimelineioAdapter.toOtioJson()` + REST API | ✅ | Preserves bluepulse metadata |
| **Asset Versioning** | ✅ `asset_version` column + `AssetRegistryService` | ⚠️ | **No REST API** for version history |
| **Asset Governance** | ✅ 7 governance columns on `asset` table | ⚠️ | **No REST API** for governance CRUD |
| **Asset Lineage** | ✅ `asset_lineage` fields on `artifact_node` | ❌ | **No REST API**, not populated at runtime |
| **JSON-LD Export** | ✅ `AssetJsonLdExporter` + `AssetRegistryService.buildJsonLdProjection()` | ⚠️ | No REST API |
| **XMP Sidecar** | ✅ 5 domain records | ❌ | No serialization API, no file I/O |

---

## 3. User Journey Assessment

### Scenario 1: Import OTIO → Edit → History → Restore → Export

```
1. User imports OTIO file           → POST /api/v1/media/tools/import_otio ✅
2. User edits timeline               → Editor sync (push/pull) ✅
3. User views edit history           → GET .../timeline/revisions ✅
4. User compares v3 vs v5            → GET .../revisions/compare?from=trev_3&to=trev_5 ✅
5. User restores to v3               → POST .../revisions/trev_3/restore ✅
6. User exports as OTIO              → POST /api/v1/media/tools/export_otio ✅
```

**Verdict: FULLY COMPLETABLE via REST API** ✨

### Scenario 2: AI Proposal → Review → Approve → Render

```
1. User requests AI edit             → POST .../timeline/ai-edit ✅
2. AI generates patch proposal        → AiTimelineProposalService (internal) ✅
3. User reviews diff                  → Needs semantic diff preview ⚠️ (proposal stores ops, not diff)
4. User approves                     → POST .../ai-proposals/{id}/adopt ✅
5. Platform re-renders                → Render pipeline (existing) ✅
```

**Verdict: COMPLETABLE — review UX gap (diff preview not auto-computed)**

### Scenario 3: Two Editors Modify Same Timeline

```
1. Editor A edits timeline            → Push → snapshot → revision ✅
2. Editor B edits same timeline       → Push → snapshot → revision (different branch?) ⚠️
3. Merge A's and B's changes         → TimelineMergeService exists BUT NO API ❌
4. Resolve conflicts                 → TimelineConflictResolver exists BUT NO API ❌
5. View merged result                → ❌
```

**Verdict: NOT COMPLETABLE — Merge engine exists but has no REST endpoint. No branch model.**

### Scenario 4: Import Kdenlive → Edit → Export OTIO

```
1. User saves Kdenlive project        → Kdenlive exports .kdenlive XML (MLT format) ❌
2. Project imports MLT                → MLT adapter exists but not exposed via OTIO import path ⚠️
3. User exports OTIO                  → ✅
```

**Verdict: NOT COMPLETABLE — Kdenlive uses MLT format. MLT project XML adapter exists internally but not in import toolchain.**

---

## 4. Timeline Git Readiness

| Layer | Level | Evidence |
|-------|-------|----------|
| **Revision** | Production Ready | REST API, snapshot table, content hashing, deduplication, restore endpoint |
| **Patch** | Production Ready | RFC6902 engine, REST API, AI/MCP integration |
| **Diff** | Production Ready | Two engines (structural + semantic), REST APIs (compare + diff_timelines), 25 change types |
| **Merge** | Internal Capability | Engine works, tested (22 tests) —**no REST API exposed** |
| **Conflict Detection** | Internal Capability | 8 conflict types, conservative rules —**no REST API** |
| **Conflict Resolution** | Internal Capability | USE_SOURCE/USE_TARGET implemented —**no REST API, no MANUAL** |

### Gap: Merge→API

The merge engine is the most significant gap between "engineering complete" and "product usable." The service exists and passes tests, but no REST endpoint allows a client to request a three-way merge.

---

## 5. OTIO Readiness

### Actual Capability (Code-Verified)

| Feature | Status | Evidence |
|---------|--------|----------|
| **Import** | ✅ Working | `OpenTimelineioAdapter.fromOtioJson()` + `POST /import_otio` |
| **Export** | ✅ Working | `OpenTimelineioAdapter.toOtioJson()` + `POST /export_otio` |
| **Round-trip** | ⚠️ Partial | Basic round-trip works. Bluepulse metadata is export-only (not reconstructed on import). |
| **Metadata Preservation** | ⚠️ Partial | `platform.*` metadata passes through. `bluepulse.*` metadata is injected during export but lost on re-import. |
| **Asset References** | ⚠️ Partial | Clips reference `storageUri`. `assetId` + `assetVersion` metadata only present if populated by client. |
| **Additional Formats** | ✅ Extensive | EDL, AAF, FCP XML, SRT, WebVTT adapters exist + REST endpoints |

**Verdict: OTIO exchange is production-ready for basic workflows. Metadata round-trip fidelity needs improvement for bluepulse namespace.**

---

## 6. Asset Registry Readiness

| Feature | Schema? | Java? | API? | Status |
|---------|--------|-------|------|--------|
| `asset_version` | ✅ | ✅ | ❌ | Data structure only |
| `entity_ref` | ✅ | ✅ | ❌ | Data structure only |
| `xmp_uri` | — | ✅ (`AssetIdentity`) | ❌ | Domain model only |
| `classification` | ✅ | ✅ | ❌ | Data structure only |
| `license` | ✅ | ✅ | ❌ | Data structure only |
| `retention_policy` | ✅ | ✅ | ❌ | Data structure only |
| `security_level` | ✅ | ✅ | ❌ | Data structure only |
| `contains_pii` | ✅ | ✅ | ❌ | Data structure only |
| `ai_generated` | ✅ | ✅ | ❌ | Data structure only |
| `asset_lineage` | ✅ (artifact_node) | ✅ (`AssetLineageMetadata`) | ❌ | Not populated at runtime |
| JSON-LD export | — | ✅ (`AssetJsonLdExporter`) | ❌ | Not exposed |

**Verdict: Asset Registry is a data layer — governance fields exist in schema but have no CRUD API. Version and lineage have no read API. JSON-LD export exists as Java code but has no REST endpoint.**

---

## 7. Competitive Comparison

| Product | Strength | Our Status |
|---------|----------|------------|
| **Premiere Team Projects** | Real-time collaboration, visual timeline | We have revision chain + merge engine but no real-time presence. Lagging on UI. |
| **Avid Nexis** | Shared storage, bin locking | We have no shared storage abstraction at timeline level. Lagging. |
| **Perforce Helix Core** | Binary version control, branching | We have text/JSON version control. Branching not implemented. Lagging. |
| **Git** | Branch, merge, rebase, blame, stash | We have commit-like revisions + diff + merge engine. Branching missing. Behind on rebase/blame/stash. |
| **Kdenlive** | MLT-based editing, OTIO support | We have MLT adapter but not in import path. Our OTIO is broader (EDL/AAF/FCP XML). |
| **OpenTimelineIO** | Reference implementation, ecosystem | Our OTIO adapter is more feature-rich (editor sync, bluepulse metadata). Ahead. |

### Where We Lead

| Capability | Competitor Gap |
|------------|---------------|
| **Semantic Diff** (25 change types → render impact) | No competitor has timeline-entity-aware diff |
| **AI Proposal Review** (LLM → patch → approve/reject) | No competitor has structured AI review workflow |
| **Asset Versioning** (clip references assetId + version) | Premiere/Resolve reference file paths, not stable IDs |
| **Governance Metadata** (classification, license, PII, AI flag) | No video platform has governance fields on assets |

### Where We Lag

| Capability | Gap |
|------------|-----|
| **Visual diff UI** | No UI for comparing timeline versions |
| **Branch UX** | No branch concept exposed to users |
| **Real-time collaboration** | No presence, no OT, no shared editing |
| **Kdenlive round-trip** | MLT adapter not in import chain |

---

## 8. Product Differentiators (Top 10)

If demonstrating to a user today:

| Rank | Capability | Why It Wins |
|------|-----------|-------------|
| 1 | **Timeline History** — browse all revisions, see who changed what, when | No NLE has this. Game-changer for audit and collaboration. |
| 2 | **Semantic Diff** — "clip C1 trimmed from 10s to 8s" vs raw JSON diff | Competitors show before/after frames. We show **what** changed. |
| 3 | **OTIO Exchange** — import/export with metadata | Industry standard. We support more formats than any single NLE. |
| 4 | **AI Proposal Review** — LLM suggests edits, you approve/reject | No video editor has structured AI review. Everyone else just runs AI directly. |
| 5 | **Timeline Restore** — jump to any historical state | Undo is a stack. Restore is a timeline. |
| 6 | **Asset Versioning** — clip references asset_789 v3, not a file path | File paths break when storage moves. Asset IDs don't. |
| 7 | **Asset Governance** — classification, license, PII flagged on every asset | Legal/compliance teams need this. No NLE provides it. |
| 8 | **JSON-LD Export** — asset metadata as linked data | Foundation for knowledge graph search. Unique in video space. |
| 9 | **Incremental Render** — only re-render changed segments | Saves hours on long timelines. Diff-driven, not heuristic. |
| 10 | **Merge (engine)** — three-way merge of divergent edits | Exists as engine. Productization needed but unique capability. |

---

## 9. Missing Product Features (User Perspective)

### Critical (Blockers for demo)

| # | Feature | Why |
|---|---------|-----|
| 1 | **Merge REST API** | Engine works but no endpoint. User cannot merge branches. |
| 2 | **Asset version history API** | `asset_version` column exists but no `/assets/{id}/versions` endpoint. |
| 3 | **Diff visualization** | Semantic diff is JSON-only. User can't see what changed without reading raw data. |

### High (Expected by users)

| # | Feature | Why |
|---|---------|-----|
| 4 | **Branch UI** | Users expect "Create Branch" as a first-class action. Currently implicit. |
| 5 | **Merge conflict resolution UI** | Engine resolves conflicts. User needs a UI to choose sides. |
| 6 | **Timeline history UI** | API exists but no visual timeline of revisions. |
| 7 | **Asset governance CRUD API** | Governance fields exist but can't be set/read via API. |

### Medium (Nice to have)

| # | Feature | Why |
|---|---------|-----|
| 8 | **Collaboration presence** | "Alice is editing clip C1 right now" |
| 9 | **Kdenlive round-trip** | MLT adapter not exposed |
| 10 | **Blame** | "Who last changed this clip?" |

### Low (Future)

| # | Feature | Why |
|---|---------|-----|
| 11 | **Stash** | Git stash equivalent for timeline experiments |
| 12 | **Cherry-pick** | Apply a specific patch from another branch |

---

## 10. Branch Assessment

### Recommendation: **Branch is P1, not P0**

| Reason | Explanation |
|--------|-------------|
| **Merge engine works without branches** | Two editors can merge their revision chains regardless of branch table. Branch = named revision pointer. |
| **Branch is a UX layer on top of existing revisions** | The `timeline_revision` table already supports parent→child chains. A `timeline_branch` table would just name revision pointers. |
| **Merge API gap is more urgent** | Without a merge REST endpoint, branches have zero user value. Fix merge API first. |
| **P0 blockers are all API gaps** | Merge API, asset version API, diff visualization — these block user value more than branch naming. |

**Proposal:** Ship merge REST API (Sprint 004) before branch model (Sprint 005). Branch adds UX sugar; merge API adds capability.

---

## 11. API Readiness

| API | Status | Endpoint |
|-----|--------|----------|
| **History API** | ✅ Ready | `GET /revisions`, `GET /revisions/head`, `GET /revisions/{id}` |
| **Diff API** | ✅ Ready | `GET /compare`, `POST /diff_timelines`, `GET /patch-preview` |
| **Restore API** | ✅ Ready | `POST /revisions/{id}/restore` |
| **Merge API** | ❌ Missing | No endpoint |
| **Asset version API** | ❌ Missing | No endpoint |
| **Asset governance API** | ❌ Missing | Only GC/integrity scans exist |
| **OTIO API** | ✅ Ready | `POST /import_otio`, `POST /export_otio` |
| **AI proposal API** | ✅ Ready | `POST /ai-edit`, `POST /adopt`, `POST /reject` |
| **Editor sync API** | ✅ Ready | `POST /timeline-sync/push`, `/pull`, `/sync` |
| **JSON-LD API** | ❌ Missing | `AssetJsonLdExporter` exists but not exposed |

---

## 12. Product Roadmap Re-Prioritization

### P0 — Ship next (block user value)

| # | Feature | Current State | Effort |
|---|---------|--------------|--------|
| 1 | **Merge REST API** | Engine complete, no endpoint | 1-2 days |
| 2 | **Asset version history API** | Schema + model exist, no endpoint | 1-2 days |
| 3 | **Asset governance CRUD API** | Schema + model exist, no endpoint | 1-2 days |

### P1 — Next sprint after P0

| # | Feature | Current State | Effort |
|---|---------|--------------|--------|
| 4 | **Branch model + API** | Engine supports merge; need branch table + named pointers | 3-5 days |
| 5 | **Merge conflict resolution API** | `TimelineConflictResolver` exists; needs REST wrapper | 1-2 days |
| 6 | **JSON-LD export API** | `AssetJsonLdExporter` exists; needs REST wrapper | 0.5 days |

### P2 — Longer horizon

| # | Feature |
|---|---------|
| 7 | Timeline history UI (visual revision browser) |
| 8 | Diff UI (visual comparison) |
| 9 | AI proposal diff preview (auto-computed semantic diff) |
| 10 | OTIO metadata round-trip enhancement |
| 11 | Kdenlive/MLT import chain |

### P3 — Future

| # | Feature |
|---|---------|
| 12 | Rebase engine |
| 13 | Blame |
| 14 | Real-time collaboration |
| 15 | OpenLineage events |
| 16 | Knowledge Graph deployment |
| 17 | Cloudflare Worker edge deployment |

---

## 13. Final Recommendation

### Next Step: **Productize Timeline Git (not enhance core engine)**

**Rationale:**

The core engine is solid. Revision chain, patch, diff, merge, conflict resolution — all implemented and tested (22 tests passing). The bottleneck is not engineering capability. It's **API gaps**.

Three REST endpoints would unlock 80% of user value from existing code:
1. `POST /merge` — expose `TimelineMergeService` (1-2 days)
2. `GET /assets/{id}/versions` — expose asset version history (1-2 days)
3. `POST /assets/{id}/governance` — expose governance CRUD (1-2 days)

**Risk:** Low. The services already exist and are tested. REST controllers are thin wrappers. No new schema, no new modules, no new algorithms.

**Estimated effort:** 3-6 days for all three P0 items.
4, 5, and 6 items for P1.

**Why not continue enhancing merge/branch engine:** The engine is ahead of the product. Users cannot use merge today because there's no API. Adding rebase or MANUAL resolution before the merge API exists is premature optimization. Ship what works first.

**Next sprint recommendation:** Sprint 004 — Merge API + Asset API productization.

## 14. Timeline Core Testable R1 Status (2026-06-27)

### What R1 Proves

- Timeline/TimelineRevision can produce a RenderJob-compatible request via `TimelineRenderJobMapper`
- A controlled render output file can be registered through `RenderOutputRegistrationService`
- The output becomes a READY `Product` with `ProductType.FINAL_RENDER` + `RepresentationKind.MEDIA_FILE`
- StorageReference is populated with checksum verification
- Product is queryable via `ProductRuntimeService.find()` and `findByProject()`
- Mapper validates all inputs fail-closed (duration, fps, canvas, format, path safety)

### What R1 Does NOT Prove

- **Not full FFmpeg/libass integration** — smoke test uses controlled local output file
- **Not full Timeline Git productization** — branch/merge/conflict UI not included
- **Not full Workflow Runtime** — no Temporal orchestration
- **Not production dispatch** — no real OpenCue or Remotion production submit
- **Not Storage production** — MinIO/S3 deferred, LOCAL provider only

### Architecture Compliance

- FFmpeg/libass remains baseline subtitle burn-in
- Remotion remains gated for advanced template rendering
- OpenCue remains disabled by default
- No signed URLs persisted
- No Artifact Runtime introduced
- Product remains canonical communication object
- Timeline input does not expose internal provider/backend/environment selection
