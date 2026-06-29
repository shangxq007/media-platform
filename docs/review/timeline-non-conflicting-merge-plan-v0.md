# P2V.7 — Timeline Non-conflicting Merge Plan v0

## 1. Purpose

Introduce a pure, side-effect-free Timeline Non-conflicting Merge Plan. The planner classifies operations from a base/ours/theirs merge preview into safe-to-apply-later, manual-review conflict, unsupported, blocked, and duplicate buckets. It does not apply patches, create a merged snapshot, or persist anything.

## 2. Package Placement

```
render-module/src/main/java/.../domain/timeline/diff/merge/plan/
render-module/src/test/java/.../domain/timeline/diff/merge/plan/
```

Placed under the existing `merge` package alongside `merge/preview/` (P2V.4) to maintain consistent package organization.

## 3. Merge Plan Request Model

`TimelineMergePlanRequest` — record with:
- `TimelineMergePlanRequestId id` — required, rejects blank
- `CanonicalTimelineSnapshot base` — required
- `CanonicalTimelineSnapshot ours` — required
- `CanonicalTimelineSnapshot theirs` — required
- `TimelineMergePlanPolicy policy` — defaults to CONSERVATIVE if null
- `Map<String, String> safeMetadata` — safe metadata only

## 4. Merge Plan Result Model

`TimelineNonConflictingMergePlan` — record with:
- `TimelineMergePlanId id`
- `TimelineMergePlanStatus status`
- `TimelineMergePreviewResult previewResult`
- `List<TimelineMergePlanOperation> operations`
- `TimelineMergePlanSummary summary`
- `List<TimelineMergePlanIssue> issues`
- `Map<String, String> safeMetadata`

Does **not** contain:
- Merged snapshot
- TimelineCommit
- Persistence metadata
- RenderJob
- Product

## 5. Operation Classification Model

`TimelineMergePlanOperation` — record with:
- `TimelineMergePlanOperationStatus status` — SAFE_TO_APPLY_LATER, CONFLICT_REQUIRES_MANUAL_REVIEW, UNSUPPORTED, BLOCKED, SKIPPED_DUPLICATE
- `TimelineMergePlanOperationSource source` — OURS, THEIRS, BOTH_IDENTICAL, SYSTEM
- `TimelineChangeOperation operation`
- `String path`
- `List<TimelineConflict> relatedConflicts`
- `List<TimelineMergePlanIssue> issues`
- `Map<String, String> safeMetadata`

## 6. Plan Status Model

`TimelineMergePlanStatus`:
- `READY` — all operations safe to apply later
- `MANUAL_REVIEW_REQUIRED` — conflicts detected
- `BLOCKED` — forbidden paths or BLOCK_ON_ANY_CONFLICT policy
- `UNSUPPORTED` — unsupported change types
- `INVALID_INPUT` — missing base/ours/theirs or bad request
- `FAILED` — internal error

## 7. Policy Model

`TimelineMergePlanPolicy`:
- `CONSERVATIVE` — classify any divergent same-path change as conflict (default)
- `ALLOW_DIFFERENT_PATHS` — allow non-conflicting different paths
- `ALLOW_IDENTICAL_SAME_PATH_CHANGES` — treat identical same-path changes as SKIPPED_DUPLICATE
- `BLOCK_ON_ANY_CONFLICT` — return BLOCKED status when any conflicts exist

## 8. Summary Model

`TimelineMergePlanSummary` — record with:
- `oursOperationCount`, `theirsOperationCount`
- `safeOperationCount`, `conflictOperationCount`, `unsupportedOperationCount`, `blockedOperationCount`, `skippedDuplicateCount`
- `conflictCount`
- `boolean manualReviewRequired`
- `boolean canAutoApplyInFuture` — true only when no conflicts/unsupported/blocked operations

## 9. Planner Behavior

`TimelineNonConflictingMergePlanner`:
1. Validates request (null checks, missing snapshots)
2. Calls `TimelineMergePreviewService.preview()` with DIFF_AND_CONFLICTS mode
3. Maps preview status for non-analysis cases (INVALID_INPUT, BLOCKED, UNSUPPORTED, FAILED)
4. Extracts oursDiff and theirsDiff from conflict analysis
5. Groups operations by path
6. Classifies each path's operations:
   - Both sides, identical → SAFE_TO_APPLY_LATER or SKIPPED_DUPLICATE (per policy)
   - Both sides, divergent → CONFLICT_REQUIRES_MANUAL_REVIEW
   - One side only, no conflict path → SAFE_TO_APPLY_LATER
   - One side, related to conflict → CONFLICT_REQUIRES_MANUAL_REVIEW
   - Forbidden path → BLOCKED
7. Sorts operations deterministically
8. Builds summary
9. Returns plan

## 10. Non-conflicting Operation Rules

- Different path, no conflict → SAFE_TO_APPLY_LATER
- One-sided operation not related to any conflict → SAFE_TO_APPLY_LATER
- Both sides identical change → SAFE_TO_APPLY_LATER (or SKIPPED_DUPLICATE with ALLOW_IDENTICAL policy)

## 11. Conflicting Operation Rules

- Same path divergent operation → CONFLICT_REQUIRES_MANUAL_REVIEW
- Remove-vs-modify conflict → CONFLICT_REQUIRES_MANUAL_REVIEW (detected by existing conflict detector)
- Operation related to a detected conflict → CONFLICT_REQUIRES_MANUAL_REVIEW

## 12. Duplicate Operation Rules

- Same path identical operation → SKIPPED_DUPLICATE (with ALLOW_IDENTICAL_SAME_PATH_CHANGES policy)
- Same path identical operation → SAFE_TO_APPLY_LATER (with other policies)
- Future merge engine should avoid applying identical same-path operation twice

## 13. Deterministic Ordering

Operations sorted by:
1. Status priority: BLOCKED(0) → CONFLICT_REQUIRES_MANUAL_REVIEW(1) → UNSUPPORTED(2) → SAFE_TO_APPLY_LATER(3) → SKIPPED_DUPLICATE(4)
2. Source priority: OURS(0) → THEIRS(1) → BOTH_IDENTICAL(2) → SYSTEM(3)
3. TimelineChangeType enum ordinal
4. Path lexicographic order

## 14. Relationship to Merge Preview

P2V.7 uses `TimelineMergePreviewService` (P2V.4) to obtain conflict analysis. The preview service delegates to `TimelineMergeConflictDetector` (P2V.3) for three-way conflict detection.

## 15. Relationship to Patch Application

P2V.7 does **not** use `TimelinePatchApplier` (P2V.2). Operations are classified only — not applied.

## 16. Relationship to Future Merge Engine

`SAFE_TO_APPLY_LATER` means a future merge engine may apply the operation. It does **not** mean P2V.7 applies it.

## 17. What is Intentionally Not Implemented

- Merge engine (applying operations)
- Patch application
- Merged snapshot creation
- TimelineCommit persistence
- TimelineBranch persistence
- TimelineBranchPointer persistence
- Timeline Git storage
- Automatic conflict resolution
- Manual conflict resolution
- Database tables / Flyway migrations
- Repositories
- Public APIs / Controllers / Queues / Workers
- Render media / RenderJob creation
- Product creation
- StorageRuntime / ProductRuntime calls
- FFmpeg / Remotion calls
- vedit / pyvedit / OpenTimelineIO
- Artifact DAG incremental render
- Global graph optimization

## 18. Persistence Boundary

No persistence of any kind. All types are pure in-memory domain models.

## 19. Render/Product/Storage Boundaries

- No render pipeline calls
- No Product creation
- No StorageRuntime calls
- No ProductRuntime calls
- No signed URLs or storage paths in results

## 20. Artifact DAG Boundary

No Artifact DAG usage. Artifact DAG is deferred and disabled by default.

## 21. Provider/Global Optimization Boundary

- No provider binding
- No provider name/type/backend exposure
- No global optimization
- No NP-hard/NP-complete algorithms
- No backtracking or exponential search

## 22. vedit / OTIO / Remotion Boundaries

- No vedit dependency
- No pyvedit dependency
- No OpenTimelineIO dependency
- No Remotion dependency
- No npm/npx/pnpm/yarn execution

## 23. Follow-up Tasks

- P2V.8: Timeline Merge Engine (apply non-conflicting operations)
- P2V.9: Timeline Conflict Resolution
- P2V.10: Timeline Git Persistence
