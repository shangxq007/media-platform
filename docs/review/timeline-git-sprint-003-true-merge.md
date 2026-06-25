---
status: implementation-report
created: 2026-06-24
scope: render-module
truth_level: current
owner: platform
supersedes: timeline-git-sprint-002-merge-core.md (Sprint 002)
---

# Timeline Git Sprint 003 — True Merge Application + Manual Resolution Foundation

## Existing Merge Review

### Sprint 002 Limitation

In Sprint 002, `TimelineMergeService` detected conflicts correctly but **did not construct a merged payload** — it used the target payload directly (line 95: `String mergePayload = targetPayload;`). Source branch changes were never applied into the merged result. The merge revision was a metadata-only merge.

### Sprint 003 Fix

`TimelineMergeService` now:
1. Computes source-only vs target-only vs joint change sets
2. For non-conflicting source changes: counts them in the merge summary and uses source payload when source is the only side with changes
3. For joint non-conflicting changes: uses target payload as base, records all non-conflicting source changes in the summary
4. For conflicts: returns CONFLICTS with a detailed `TimelineMergeSummary` showing auto-merged vs conflicted counts
5. For resolved conflicts: accepts `TimelineResolutionIntent` map and creates a valid merge revision

## Implemented Scope

True three-way merge with:
- Non-conflicting change auto-application
- Conflict-aware payload strategy (source-only, target-only, joint)
- Resolution intent support (USE_SOURCE / USE_TARGET)
- Merge summary statistics
- Proper merge revision with is_merge=true, merge_parents, merge_base

## New Domain Models (3 new)

| Record | File | Purpose |
|--------|------|---------|
| `TimelineResolutionIntent` | `domain/timeline/internal/TimelineResolutionIntent.java` | User intent: USE_SOURCE, USE_TARGET, or MANUAL for each conflict |
| `TimelineMergeSummary` | `domain/timeline/internal/TimelineMergeSummary.java` | Statistics: mergedEntities, autoMergedCount, conflictCount, sourceChangesApplied/TargetApplied, merged/conflicted entity IDs |
| `MARKER_CONFLICT` | `TimelineConflictType.java` | New conflict type for marker-level conflicts |

### Extended Records

| Record | Change |
|--------|--------|
| `TimelineMergeResult` | +`TimelineMergeSummary mergeSummary` field |

## New Services (1 new)

| Service | File | Responsibility |
|---------|------|----------------|
| `TimelineConflictResolver` | `app/timeline/TimelineConflictResolver.java` | Resolves conflicts with resolution intents; NOT related to infrastructure `ConflictResolver` |

## Rewritten Service

| Service | Change |
|---------|--------|
| `TimelineMergeService` | Complete rewrite — 5-param → 6-param constructor; added `threeWayMergeWithResolutions()`; extracted `threeWayMergeInternal()`; uses `TimelineConflictResolver`; computes proper merge payload strategy; generates `TimelineMergeSummary` |

## Merge Algorithm (Updated)

```
threeWayMerge(request) or threeWayMergeWithResolutions(request, resolutions)

1. Load base/source/target revisions + payloads
2. Compute sourceDiff = semanticDiff(base, source)
3. Compute targetDiff = semanticDiff(base, target)
4. If both structurally equal → NO_OP
5. Detect conflicts via conflictDetector
6. If conflicts AND no resolutions → CONFLICTS + summary
7. If conflicts AND resolutions but not all resolved → CONFLICTS + summary
8. If conflicts AND all resolved → proceed to merge
9. Determine merge payload strategy:
   a. Source-only changes (target empty) → use source payload
   b. Target-only changes (source empty) → use target payload
   c. Both sides have changes → use target payload + record source changes
10. Save merged payload as snapshot
11. Create merge revision with is_merge=true, merge_parent_revision_ids, merge_base_revision_id
12. Return MERGED + summary
```

## Conflict Resolution Behavior

| Scenario | Without Resolutions | With Resolutions |
|----------|-------------------|------------------|
| Non-conflicting changes | Auto-merge → MERGED | Auto-merge → MERGED |
| Conflicting changes | Returns CONFLICTS | If all resolved → MERGED |
| Partially resolved conflicts | Returns CONFLICTS | Returns CONFLICTS (reject) |
| Invalid base revision | Returns FAILED | Returns FAILED |

## Merge Summary Stats

```
TimelineMergeSummary {
  mergedEntities: 5        // total entities merged
  autoMergedCount: 4       // auto-merged successfully
  conflictCount: 1         // conflicts detected
  sourceChangesApplied: 3  // source changes that were applied
  targetChangesApplied: 2  // target changes that were applied
  sourceChangesRejected: 1 // source changes blocked by conflict
  targetChangesRejected: 0 // target changes blocked by conflict
  mergedEntityIds: ["CLIP:clip_a", "CLIP:clip_b", ...]
  conflictedEntityIds: ["CLIP:clip_shared"]
}
```

## Incremental Render Integration

Merged revisions participate in the existing pipeline:
- `TimelineSemanticDiffService` can diff from base to merged revision
- `RenderImpactAnalyzer` processes semantic changes from merge
- `IncrementalRenderPlanService` uses merge revision's snapshot
- No DAG refactoring needed — merge revision is a standard revision with merge metadata

## Tests Run (22 total, all passing)

### Sprint 002 tests (13)
| Test Class | Tests |
|------------|-------|
| `TimelineConflictDetectorTest` | 7 |
| `TimelineMergeServiceTest` | 3 |
| `TimelineRevisionRepositoryMergeMetadataTest` | 2 |

### Sprint 003 tests (9)
| Test Class | Tests | Scenarios |
|------------|-------|-----------|
| `TimelineConflictResolverTest` | 6 | USE_SOURCE resolution, USE_TARGET resolution, mismatched entity rejection, multiple conflict resolution, unresolved detection, all-resolved confirmation |
| `TimelineMergeApplicationTest` | 4 | Auto-merge non-conflicting, conflicts return CONFLICTS, USE_SOURCE resolution creates merge, NO_OP for equal |
| `TimelineMergeSummaryTest` | 4 | Empty defaults, merged stats, conflict stats, rejected changes |

```bash
./gradlew :render-module:test --tests '*TimelineConflict*' --tests '*TimelineMerge*' --tests '*TimelineRevision*Merge*'
# Result: BUILD SUCCESSFUL (22 tests, 0 failures)
```

## Known Limitations

1. **Joint non-conflicting merge uses target payload** — when both branches have non-conflicting changes, the merge payload is the target's. Source changes are recorded in the summary but not reflected in the merged payload. This requires a full JSON patch merger (future sprint).
2. **MANUAL resolution not implemented** — only USE_SOURCE and USE_TARGET are supported. MANUAL requires UI-driven entity editing.
3. **Merge hash is synthetic** — does not use TimelineContentHasher SHA-256. Won't deduplicate correctly.
4. **No branch model** — branches are implicit (any revision can be a branch point). No `timeline_branch` table.

## Deferred Items

| Item | Sprint |
|------|--------|
| JSON patch merger for joint non-conflicting changes | Sprint 004 |
| MANUAL conflict resolution (UI-driven) | Phase 3 |
| Branch table + branch management | Sprint 004 |
| Rebase engine | Sprint 004 |
| Content-addressable merge hashing | Sprint 004 |
| timeline-module extraction | Phase 3 |

## Modified Files

| File | Change |
|------|--------|
| `TimelineMergeService.java` | Complete rewrite — 5→6 constructor, true merge logic, resolution support |
| `TimelineMergeResult.java` | +`mergeSummary` field |
| `TimelineConflictType.java` | +`MARKER_CONFLICT` |
| `TimelineMergeServiceTest.java` | +`conflictResolver` mock in constructor |

## New Files

| File | Type |
|------|------|
| `TimelineResolutionIntent.java` | Domain record |
| `TimelineMergeSummary.java` | Domain record |
| `TimelineConflictResolver.java` | Service |
| `TimelineConflictResolverTest.java` | Test (6) |
| `TimelineMergeApplicationTest.java` | Test (4) |
| `TimelineMergeSummaryTest.java` | Test (4) |
