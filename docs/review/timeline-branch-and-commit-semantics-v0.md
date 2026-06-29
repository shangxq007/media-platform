# Timeline Branch and Commit Semantics v0 (P2V.5)

## 1. Purpose

Introduce pure domain vocabulary and in-memory semantics for Timeline branch and commit operations. Establish how future rollback, checkout, branch switching, and merge preview relate to TimelineRevision history.

## 2. Package Placement

```
render-module/src/main/java/.../domain/timeline/version/
render-module/src/test/java/.../domain/timeline/version/
```

Separate from `diff` package (which contains `TimelineCommit`, `TimelineCommitId`, `TimelineCommitParent` for diff/merge context). The `version` package has its own commit/branch types for Git-like version control semantics.

## 3. Branch Model

`TimelineBranch` — immutable branch record:
- `TimelineBranchId id` — rejects blank
- `TimelineBranchName name` — safe name validation (rejects whitespace, shell metacharacters, path traversal, storage/provider keywords)
- `TimelineRevisionRef headRevision` — semantic reference to head revision
- `Map<String, String> safeMetadata` — safe metadata only

Branch points to a head revision ref. Branch does not contain full history. Branch is not persistence.

## 4. Branch Pointer Model

`TimelineBranchPointer` — branch head pointer:
- `TimelineBranchId branchId`
- `TimelineRevisionRef headRevision`
- `TimelineCommitId headCommitId` (optional)
- `Map<String, String> safeMetadata`

Moving pointer is semantic planning only via `moveTo()` — returns new pointer, no mutation.

## 5. Commit Model

`TimelineCommit` — immutable commit:
- `TimelineCommitId id` — rejects blank
- `TimelineRevisionRef revisionRef` — required
- `TimelineCommitType type` — INITIAL, EDIT, PATCH_APPLICATION, ROLLBACK, BRANCH_POINT, MERGE_PREVIEW, MANUAL_MERGE, SYSTEM
- `List<TimelineCommitParent> parents` — max 2 parents
- `TimelineCommitMetadata metadata` — safe metadata only
- `Map<String, String> safeMetadata`

## 6. Commit Parent Rules

- Initial commit may have zero parents (`isRoot()`)
- Normal edit commit has one primary parent
- Manual merge commit may have two parents (future vocabulary)
- No more than two parents
- At most one primary parent (`primaryParent = true`)
- No automatic merge engine

`TimelineCommitParent`:
- `TimelineCommitId parentCommitId` — rejects null
- `boolean primaryParent`
- `Map<String, String> safeMetadata`

## 7. Revision Ref Model

`TimelineRevisionRef` — semantic reference:
- Rejects blank
- Rejects storage/provider keywords (bucket, objectKey, signedUrl, providerName, backendName, executionEnvironment, autoDispatch, rawCommand, processEnvironment)
- No storage paths, no provider/backend fields, no signed URLs, no command strings

## 8. Checkout Plan Semantics

`TimelineCheckoutPlan` — plan reading/editing context:
- `TimelineCheckoutStatus status` — READY, INVALID_TARGET, BRANCH_NOT_FOUND, REVISION_NOT_FOUND, BLOCKED
- `TimelineBranchName branchName` (optional)
- `TimelineRevisionRef targetRevision`
- `List<TimelineBranchOperationIssue> issues`
- `Map<String, String> safeMetadata`

Does not render, create Product, persist, or modify branch pointer.

## 9. Rollback Plan Semantics

`TimelineRollbackPlan` — plan non-destructive rollback:
- `TimelineRollbackStatus status` — READY, INVALID_TARGET, TARGET_NOT_ANCESTOR, NO_OP, BLOCKED
- `TimelineRevisionRef currentRevision`
- `TimelineRevisionRef targetRevision`
- `TimelineCommitType plannedCommitType` — always ROLLBACK
- `List<TimelineBranchOperationIssue> issues`
- `Map<String, String> safeMetadata`

Rollback creates future revision/commit semantics. Does not delete history, hard reset, apply persistence, or render.

## 10. Branch Switch Plan Semantics

`TimelineBranchSwitchPlan` — plan switching editing context:
- `TimelineBranchSwitchStatus status` — READY, SOURCE_BRANCH_NOT_FOUND, TARGET_BRANCH_NOT_FOUND, UNSAVED_CHANGES_REQUIRE_DECISION, BLOCKED
- `TimelineBranchName sourceBranch`
- `TimelineBranchName targetBranch`
- `TimelineRevisionRef targetRevision`
- `List<TimelineBranchOperationIssue> issues`
- `Map<String, String> safeMetadata`

Switch changes editing context only. Does not render, create Product, or mutate branch pointer.

## 11. Non-destructive Rollback Policy

Rollback creates a new revision pointing to a previous state. History is never deleted. Branch pointer moves forward, not backward. Rollback plan is planning-only — no automatic application.

## 12. Immutable History Policy

Timeline history is immutable. Commits are never deleted or rewritten. Rollback adds new commits. Branch switching changes context, not history.

## 13. Relationship to TimelineDiff

`TimelineDiff` (in `diff` package) computes semantic differences between timeline snapshots. `TimelineCommit` (in `version` package) records version history. Diff is computed; commits are recorded.

## 14. Relationship to TimelinePatch

`TimelinePatch` applies changes to a timeline snapshot. A commit of type `PATCH_APPLICATION` records that a patch was applied. The commit is the record; the patch is the operation.

## 15. Relationship to MergePreview

`TimelineMergePreviewService` analyzes conflicts between branches without merging. A commit of type `MERGE_PREVIEW` records that a preview was performed. Preview is read-only; merge is future work.

## 16. Relationship to Future Timeline Git Persistence

This task establishes domain vocabulary only. Future persistence will store commits, branches, and pointers. The domain model is persistence-agnostic.

## 17. What is Intentionally Not Implemented

- Timeline Git persistence (database tables, repositories)
- Public APIs for branch/commit operations
- Automatic merge engine
- Automatic conflict resolution
- Media rendering
- Product creation
- StorageRuntime calls
- ProductRuntime calls
- Artifact DAG integration
- Global graph optimization

## 18. vedit Boundary

No vedit dependency. vedit is a POC/benchmark tool, not a production dependency.

## 19. OTIO Boundary

No OpenTimelineIO runtime dependency.

## 20. Provider/Storage/Remotion/Product Safety Boundaries

- No providerName/providerType/backendName exposure
- No bucket/objectKey/signedUrl/local path exposure
- No StorageRuntime calls
- No ProductRuntime calls
- No render pipeline calls
- No Remotion references
- No FFmpeg commands

## 21. Follow-up Tasks

- P2V.6: Timeline Checkout and Rollback Application Service (complete)
- P2V.7: Timeline Non-conflicting Merge Plan (completed)
- P2V.8: Timeline Merge Engine
- P2V.9: Timeline Conflict Resolution
- P2V.10: Timeline Git Persistence
