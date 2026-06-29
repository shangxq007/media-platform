# P2V.6 — Timeline Checkout and Rollback Application Service v0

## 1. Purpose

Pure, side-effect-free application services for timeline checkout, rollback planning, and branch switching. These services produce safe planning/result objects for editing context changes and non-destructive rollback intent.

## 2. Package Placement

```
render-module/src/main/java/.../domain/timeline/version/application/
render-module/src/test/java/.../domain/timeline/version/application/
```

## 3. Checkout Request/Result Model

**TimelineCheckoutRequest** — record with `TimelineCheckoutRequestId`, `TimelineCheckoutTarget`, `safeMetadata`.

**TimelineCheckoutTarget** — record with `TimelineCheckoutTargetType` (BRANCH, REVISION, COMMIT), optional `branchName`, `revisionRef`, `commitId`.

**TimelineCheckoutResult** — record with `TimelineCheckoutResultStatus`, `checkoutPlan`, `target`, optional `snapshot`, `issues`, `safeMetadata`.

**TimelineCheckoutResultStatus** — READY, BRANCH_NOT_FOUND, REVISION_NOT_FOUND, COMMIT_NOT_FOUND, INVALID_TARGET, BLOCKED, FAILED.

## 4. Checkout Target Model

Three target types:
- **BRANCH** — resolves branch head revision via lookup
- **REVISION** — resolves snapshot directly by revision ref
- **COMMIT** — resolves commit, then uses commit's revision ref

## 5. Rollback Request/Result Model

**TimelineRollbackRequest** — record with `TimelineRollbackRequestId`, `currentRevision`, `targetRevision`, optional `branchName`, `requireTargetAncestor`, `safeMetadata`.

**TimelineRollbackResult** — record with `TimelineRollbackResultStatus`, `rollbackPlan`, `intent`, `issues`, `safeMetadata`.

**TimelineRollbackResultStatus** — READY, NO_OP, TARGET_NOT_FOUND, TARGET_NOT_ANCESTOR, INVALID_REQUEST, BLOCKED, FAILED.

## 6. Rollback Intent Model

**TimelineRollbackIntent** — record with `plannedCommitType` (defaults to ROLLBACK), `currentRevision`, `targetRevision`, `message`, `safeMetadata`.

## 7. Branch Switch Request/Result Model

**TimelineBranchSwitchRequest** — record with `TimelineBranchSwitchRequestId`, `sourceBranch`, `targetBranch`, `hasUnsavedChanges`, `safeMetadata`.

**TimelineBranchSwitchResult** — record with `TimelineBranchSwitchResultStatus`, `switchPlan`, optional `targetBranch`, optional `targetSnapshot`, `issues`, `safeMetadata`.

**TimelineBranchSwitchResultStatus** — READY, SOURCE_BRANCH_NOT_FOUND, TARGET_BRANCH_NOT_FOUND, UNSAVED_CHANGES_REQUIRE_DECISION, INVALID_REQUEST, BLOCKED, FAILED.

## 8. Version Lookup Port

**TimelineVersionLookup** — pure interface with:
- `findBranch(TimelineBranchName)` → `Optional<TimelineBranch>`
- `findSnapshot(TimelineRevisionRef)` → `Optional<CanonicalTimelineSnapshot>`
- `findCommit(TimelineCommitId)` → `Optional<TimelineCommit>`

No database implementation. No StorageRuntime. No ProductRuntime. Tests use in-memory fake lookup.

## 9. Checkout Service Behavior

`TimelineCheckoutService.checkout(request)`:
1. Validates request and target
2. Resolves by branch/revision/commit
3. Builds checkout plan via P2V.5 planner
4. Returns snapshot if found in memory
5. Does not persist, render, or create Product

## 10. Rollback Service Behavior

`TimelineRollbackService.planRollback(request)`:
1. Validates request
2. Checks target snapshot exists via lookup
3. Returns NO_OP if current == target
4. Returns TARGET_NOT_ANCESTOR if `requireTargetAncestor` is true (ancestry not available yet)
5. Builds rollback plan via P2V.5 planner
6. Builds rollback intent with ROLLBACK commit type
7. Does not persist, apply patch, render, or create Product

## 11. Branch Switch Service Behavior

`TimelineBranchSwitchService.planSwitch(request)`:
1. Validates request
2. Returns SOURCE_BRANCH_NOT_FOUND if source missing
3. Returns TARGET_BRANCH_NOT_FOUND if target missing
4. Returns UNSAVED_CHANGES_REQUIRE_DECISION if `hasUnsavedChanges` is true
5. Builds switch plan via P2V.5 planner
6. Resolves target snapshot if available
7. Does not mutate branch pointer, persist, render, or create Product

## 12. Side-effect-free Policy

All three services are pure and side-effect-free:
- No database writes
- No storage operations
- No render calls
- No Product creation
- No branch pointer mutation
- Deterministic across double-run with same lookup state

## 13. Non-destructive Rollback Policy

Rollback creates a new commit/revision intent (type ROLLBACK). It does NOT:
- Delete history
- Hard reset branch pointer
- Persist a commit
- Apply a patch automatically

## 14. Unsaved Changes Policy

Branch switch with `hasUnsavedChanges=true` returns UNSAVED_CHANGES_REQUIRE_DECISION. No stash implementation. Explicit decision required.

## 15. Relationship to P2V.5

These services use `TimelineBranchSemanticsPlanner` from P2V.5 to build plans. They add application-level request/result types and lookup-based resolution on top of P2V.5's pure planning.

## 16. Relationship to TimelineDiff/Patch/MergePreview

These services do NOT invoke diff calculation, patch application, or merge preview. They are orthogonal: checkout/rollback/switch plan editing context changes; diff/patch/merge handle content changes.

## 17. What is Intentionally Not Implemented

- Timeline Git persistence
- Public APIs
- Render integration
- Product creation
- StorageRuntime/ProductRuntime calls
- Merge engine
- Automatic conflict resolution
- Artifact DAG integration
- Global graph optimization
- vedit/pyvedit/OpenTimelineIO/Remotion

## 18. Persistence Boundary

No database tables, repositories, Flyway migrations, or persistence of any kind.

## 19. Render/Product/Storage Boundaries

- No render calls (FFmpeg, Remotion, or any render pipeline)
- No Product creation or ProductRuntime calls
- No StorageRuntime calls or storage materialization
- No signed URLs or storage paths in results

## 20. vedit / OTIO / Remotion Boundaries

No references to vedit, pyvedit, OpenTimelineIO, Remotion, or any external rendering system.

## 21. Follow-up Tasks

- P2V.7: Timeline Git Persistence (when persistence layer is ready)
- P2V.8: Timeline Merge Engine
- P2V.9: Timeline Conflict Resolution
