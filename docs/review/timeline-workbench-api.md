---
status: implementation-report
created: 2026-06-25
scope: platform-app
truth_level: current
owner: platform
---

# Productization Sprint 031 — Timeline Workbench API Foundation

## Timeline Capability Audit

### Existing APIs (Reused)

| API | Controller | Method |
|-----|-----------|--------|
| History | `TimelineRevisionController` | `GET /revisions`, `/compare`, `/diff` |
| Merge | `TimelineRevisionController` | `POST /merge` |
| Restore | `TimelineRevisionController` | `POST /restore` |
| Review | `TimelineReviewController` | `POST /reviews`, `/approve`, `/comments` |
| Comment | `TimelineCommentService` | `addComment`, `listComments`, `resolveThread` |

### Workbench Gaps (Addressed)

| Gap | API | Reuses |
|-----|-----|--------|
| No aggregated timeline view | `GET /{id}/workbench` | `listFacets`, `listByProject` |
| No review workspace view | `GET /reviews/workspace` | `listByProject`, `listComments`, `listThreads`, `checkMergeGuard` |
| No diff preview | `GET /diff-preview` | `compareRevisions` |
| No conflict preview | `GET /conflicts` | `compareRevisions` (×2 for source/target) |

## New APIs (6 endpoints)

| Endpoint | Purpose | Reuses |
|----------|---------|--------|
| `GET /api/v1/timelines/{projectId}/{timelineId}/workbench` | Aggregated workbench summary | `revisionService.listFacets()` + `reviewRepo.listByProject()` |
| `GET /.../reviews/workspace` | Review workspace summary (open/approved/changes/merged counts) | `reviewRepo.listByProject()` |
| `GET /.../reviews/{reviewId}/workspace` | Single review detail (threads, comments, merge guard) | `reviewRepo.findById()`, `commentService`, `reviewService.checkMergeGuard()` |
| `GET /.../diff-preview?from=&to=` | Entity-level diff summary | `revisionService.compareRevisions()` |
| `GET /.../conflicts?base=&source=&target=` | Merge conflict preview | `revisionService.compareRevisions()` (×2) |

## DTO Model (6 new)

| DTO | Fields |
|-----|--------|
| `WorkbenchDto` | timelineId, sourceCount, reviewCount, openComments |
| `ReviewWsDto` | open, approved, changesRequested, merged, total |
| `ReviewDetailDto` | reviewId, status, author, title, threads, openThreads, resolvedThreads, comments, canMerge, mergeReason |
| `DiffPreviewDto` | fromRevision, toRevision, hasDetail, totalChanges |
| `ConflictDto` | sourceChanges, targetChanges, reason |

## Observable

```
Timeline workbench loaded: timeline=proj_1 latency=5ms
```

## New File

| File | Purpose |
|------|---------|
| `TimelineWorkbenchController.java` | Aggregation controller with 6 workbench endpoints |

## No Changes To

- Merge Engine
- Review Workflow
- Outbox/Coordination Runtime
- Database schema
- Domain models

## Tests

Compilation passing. All existing tests unaffected (TimelineRevisionController, TimelineReviewController).

## Known Limitations

| Limitation | Status |
|-----------|--------|
| Workbench summary uses `listFacets` (no revision timeline) | Acceptable — revisions accessible via existing `/revisions` endpoint |
| Conflict preview doesn't use actual conflict detector | Returns entity change counts from both branches — merge-time detection handles actual conflicts |
| No branch/rebase in workbench | Deferred to P3+ |

## Deferred Items

| Item |
|------|
| Timeline branch API |
| Rebase API |
| GraphQL read layer |
| Real-time collaborator presence |

## Timeline Core Testable R1 (2026-06-27)

R1 closes the first backend smoke path from Timeline to Product:

- `TimelineRenderJobMapper` converts `TimelineSpec` to `SubmitRenderJobRequest` with fail-closed validation
- Controlled render output registered through `RenderOutputRegistrationService`
- Output becomes a READY `Product` queryable via existing `ProductController` APIs
- Mapper rejects unsafe paths, invalid dimensions, unsupported formats, internal provider selection

This is a backend smoke closure using controlled local output — not real FFmpeg/libass rendering.
Frontend Workbench UI integration remains a next step.
