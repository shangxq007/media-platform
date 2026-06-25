---
status: current
created: 2026-06-24
scope: render-module + V1 baseline
truth_level: validated-against-code
owner: platform
---

# Current Timeline Git Status

> **Last verified:** 2026-06-24 — validated against render-module code and V1 schema.

## Capability Matrix

| Capability | Status | Implemented In | Tests | REST API | Known Gaps |
|-----------|--------|---------------|-------|----------|------------|
| **Revision (Commit)** | ✅ Production Ready | `TimelineRevisionService`, `TimelineRevisionRepository`, `timeline_revision` table | ✅ | `GET /revisions`, `GET /head`, `GET /{id}` | None |
| **Snapshot** | ✅ Production Ready | `TimelineSnapshotService`, `timeline_snapshot` table | ✅ | `POST /timeline-snapshots` | None |
| **Patch (RFC6902)** | ✅ Production Ready | `TimelinePatchService`, `TimelinePatchOpsJson` | ✅ | `POST /patch_timeline` | JSON Path-based; no semantic patch type mapper |
| **Structural Diff** | ✅ Production Ready | `TimelineRevisionDiffService` | ✅ | `GET /compare?from=&to=` | Entity-level only (counts + verbs) |
| **Semantic Diff** | ✅ Production Ready | `TimelineSemanticDiffService` (25 change types), `SemanticChange`, `EntityRef`, `DirtyScope` | ✅ | `POST /diff_timelines`, `POST /analyze_render_impact` | None |
| **Restore** | ✅ Production Ready | `TimelineRevisionService.restore()` | ✅ | `POST /revisions/{id}/restore` | Creates new head revision (not in-place) |
| **AI Proposal** | ✅ Production Ready | `AiTimelineProposalService`, `AiTimelineEditService`, `AiTimelineEditResponseParser` | ✅ | `POST /ai-edit`, `/adopt`, `/reject` | No auto-computed diff preview on proposals |
| **Merge (engine)** | ⚠️ Internal Only | `TimelineMergeService.threeWayMerge()` | ✅ (22 tests) | **❌ Missing** | Engine complete — no REST endpoint |
| **Merge (with resolutions)** | ⚠️ Internal Only | `TimelineMergeService.threeWayMergeWithResolutions()` | ✅ | **❌ Missing** | USE_SOURCE/USE_TARGET supported; MANUAL not implemented |
| **Conflict Detection** | ⚠️ Internal Only | `TimelineConflictDetector` (8 conflict types) | ✅ (7 tests) | **❌ Missing** | Conservative approach (when in doubt, report conflict) |
| **Conflict Resolution** | ⚠️ Internal Only | `TimelineConflictResolver` (USE_SOURCE, USE_TARGET) | ✅ (6 tests) | **❌ Missing** | MANUAL mode reserved for future UI |
| **Merge Summary** | ⚠️ Internal Only | `TimelineMergeSummary` | ✅ (4 tests) | **❌ Missing** | Generated only during merge — no standalone query |
| **Branch** | ❌ Not Implemented | None | ❌ | ❌ | Revisions are linear (single `parent_revision_id`). No `timeline_branch` table. |
| **Rebase** | ❌ Not Implemented | None | ❌ | ❌ | Sequential patch replay not implemented. |
| **Incremental Render** | ✅ Production Ready | `RenderImpactAnalyzer`, `IncrementalRenderPlanService`, `SegmentTimelinePlanner` | ✅ | `POST /analyze_render_impact`, `POST /explain_incremental_plan` | Diff → dirty scope → incremental tasks pipeline works |
| **Editor Sync** | ✅ Production Ready | `TimelineEditorSyncService` | ✅ | `POST /timeline-sync/push`, `/pull`, `/sync` | Bidirectional editor ↔ internal format |

## Asset Registry Status

| Capability | Status | Schema? | Java? | REST API? |
|-----------|--------|---------|-------|-----------|
| Asset identity (id, storage_key, media_type) | ✅ | ✅ | ✅ | ✅ |
| Asset version (`asset_version`) | ⚠️ | ✅ | ✅ | ❌ |
| Entity reference (`entity_ref`) | ⚠️ | ✅ | ✅ | ❌ |
| Governance classification | ⚠️ | ✅ | ✅ | ❌ |
| Governance license | ⚠️ | ✅ | ✅ | ❌ |
| Retention policy | ⚠️ | ✅ | ✅ | ❌ |
| PII flag | ⚠️ | ✅ | ✅ | ❌ |
| AI-generated flag | ⚠️ | ✅ | ✅ | ❌ |
| XMP sidecar records | ⚠️ | N/A | ✅ | ❌ |
| JSON-LD export | ⚠️ | N/A | ✅ (exporter) | ❌ |
| Lineage fields | ⚠️ | ✅ (artifact_node) | ✅ | ❌ |

## Test Coverage

| Area | Test Files | Tests |
|------|-----------|-------|
| Revision | `TimelineRevisionServiceTest`, `TimelineEditorSyncServiceTest` | ~15 |
| Patch | `TimelinePatchServiceTest` | ~5 |
| Diff | `TimelineRevisionDiffServiceTest`, `TimelineSemanticDiffService` (internal) | ~8 |
| Merge | `TimelineMergeServiceTest`, `TimelineMergeApplicationTest` | 7 |
| Conflict | `TimelineConflictDetectorTest`, `TimelineConflictResolverTest` | 13 |
| Merge metadata | `TimelineRevisionRepositoryMergeMetadataTest`, `TimelineMergeSummaryTest` | 6 |
| **Total** | **10 test files** | **~54 tests** |

## Implementation Sprints

| Sprint | Date | Scope | Status |
|--------|------|-------|--------|
| Sprint 001 | 2026-06-24 | Asset Registry Phase 1 (schema + XMP + JSON-LD) | ✅ Complete |
| Sprint 002 | 2026-06-24 | Merge Core (metadata + conflict detection) | ✅ Complete |
| Sprint 003 | 2026-06-24 | True Merge + Resolution Foundation | ✅ Complete |
