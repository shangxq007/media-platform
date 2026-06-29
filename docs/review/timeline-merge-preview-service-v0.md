# Timeline Merge Preview Service v0 (P2V.4)

## 1. Purpose

A pure, side-effect-free Timeline Merge Preview Service that wraps three-way conflict analysis into a safe preview result with status, summary, readiness, and issues. Future UI/API/application services can expose this preview to users before any merge is attempted.

## 2. Package Placement

```
render-module/src/main/java/.../domain/timeline/diff/merge/preview/
render-module/src/test/java/.../domain/timeline/diff/merge/preview/
```

## 3. Preview Request Model

```java
record TimelineMergePreviewRequest(
    TimelineMergePreviewRequestId id,
    CanonicalTimelineSnapshot base,
    CanonicalTimelineSnapshot ours,
    CanonicalTimelineSnapshot theirs,
    TimelineMergePreviewMode mode,
    TimelineMergePreviewPolicy policy,
    Map<String, String> safeMetadata
)
```

- `id` is required (rejects blank/null).
- `base`, `ours`, `theirs` are required snapshots.
- `mode` defaults to `DIFF_AND_CONFLICTS` if null.
- `policy` defaults to `CONSERVATIVE` if null.
- `safeMetadata` must not contain provider/storage keywords.

## 4. Preview Result Model

```java
record TimelineMergePreviewResult(
    TimelineMergePreviewStatus status,
    TimelineMergePreviewSummary summary,
    TimelineMergeConflictAnalysis conflictAnalysis,
    List<TimelineMergePreviewIssue> issues,
    Map<String, String> safeMetadata
)
```

Factory methods: `mergeReady`, `manualReview`, `blocked`, `invalidInput`, `unsupported`, `failed`.

## 5. Preview Statuses

| Status | Description |
|--------|-------------|
| `MERGE_READY` | No conflicts detected; safe to merge |
| `MANUAL_REVIEW_REQUIRED` | Conflicts detected; human review needed |
| `BLOCKED` | Forbidden metadata/paths detected |
| `INVALID_INPUT` | Missing base/ours/theirs or null request |
| `UNSUPPORTED` | Unsupported preview mode or conflict type |
| `FAILED` | Internal error during preview computation |

## 6. Preview Modes

| Mode | Description |
|------|-------------|
| `DIFF_AND_CONFLICTS` | Full conflict analysis with diffs (default) |
| `CONFLICTS_ONLY` | Conflicts and readiness, no extra diff detail |
| `READINESS_ONLY` | Status and summary only; no conflict analysis |

## 7. Preview Policies

| Policy | Description |
|--------|-------------|
| `CONSERVATIVE` | Delegates readiness to detector as-is (default) |
| `ALLOW_IDENTICAL_SAME_PATH_CHANGES` | Vocabulary-only in P2V.4; same as CONSERVATIVE |
| `BLOCK_ON_ANY_CONFLICT` | Downgrades to MANUAL_REVIEW_REQUIRED when any conflict exists |

Policies are vocabulary-only in P2V.4. The service delegates readiness to the conflict detector and does not alter conflict resolution.

## 8. Conflict Detector Integration

```java
TimelineMergePreviewService(TimelineMergeConflictDetector conflictDetector)
```

The service delegates to `conflictDetector.analyze(base, ours, theirs)` and maps the result to preview status/summary/issues.

## 9. Status Mapping

| `TimelineMergeReadinessStatus` | `TimelineMergePreviewStatus` |
|-------------------------------|------------------------------|
| `MERGE_READY` | `MERGE_READY` |
| `MANUAL_REVIEW_REQUIRED` | `MANUAL_REVIEW_REQUIRED` |
| `BLOCKED` | `BLOCKED` |
| `UNSUPPORTED` | `UNSUPPORTED` |
| `INVALID_INPUT` | `INVALID_INPUT` |
| (any exception) | `FAILED` |

## 10. Summary Model

```java
record TimelineMergePreviewSummary(
    String baseRevisionId,
    String oursRevisionId,
    String theirsRevisionId,
    int oursOperationCount,
    int theirsOperationCount,
    int conflictCount,
    int blockingConflictCount,
    boolean mergeReady,
    boolean manualReviewRequired,
    Map<String, String> safeMetadata
)
```

## 11. Safety and Error Handling

- Exceptions are caught and converted to `FAILED` status with a safe issue message.
- No stack traces, exception class names, or internal details leak into issues.
- Forbidden metadata keys (`bucket`, `objectKey`, `signedUrl`, `providerName`, etc.) trigger `BLOCKED` status.
- Input snapshots are never mutated.

## 12. What is Intentionally Not Implemented

- Automatic merge
- Conflict resolution
- Patch application inside preview
- Timeline Git persistence
- TimelineBranch / TimelineRollback / TimelineCheckout
- Merkle-DAG
- Database migrations, repositories, public APIs, controllers, queues, workers
- StorageRuntime / ProductRuntime calls
- Render pipeline / FFmpeg / Remotion execution

## 13. Difference from Merge Engine

The preview service does not merge. It only analyzes and reports. A merge engine would produce a merged timeline; this service produces a preview of what conflicts exist.

## 14. Difference from Conflict Detector

The conflict detector is a low-level domain service that computes diffs and detects conflicts. The preview service wraps it with request validation, forbidden-keyword checking, mode/policy support, and safe error handling.

## 15. Difference from Patch Application

Patch application (P2V.2) applies a single patch to a base snapshot. The preview service analyzes two competing snapshots against a common base without applying anything.

## 16. vedit Boundary

No vedit dependency. vedit is a POC/benchmark tool, not a production dependency.

## 17. OTIO Boundary

No OpenTimelineIO runtime dependency.

## 18. Provider/Storage/Remotion Safety Boundaries

- No providerName/providerType/backendName exposure
- No bucket/objectKey/signedUrl/local path exposure
- No StorageRuntime calls
- No ProductRuntime calls
- No render pipeline calls
- No Remotion references
- No FFmpeg commands

## 19. Follow-up Tasks

- P2V.5: Timeline Branch and Commit Semantics (completed)
- P2V.6: Timeline Merge Engine
- P2V.7: Timeline Non-conflicting Merge Plan (completed)
- P2V.8: Timeline Conflict Resolution
- P2V.9: Timeline Git Persistence
