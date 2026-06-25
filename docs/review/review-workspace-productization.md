---
status: implementation-report
created: 2026-06-25
scope: platform-app
truth_level: current
owner: platform
---

# Productization Sprint 032 — Review Workspace Productization

## Review Capability Audit

### Existing APIs (Reused)

| API | Source |
|-----|--------|
| Reviews | `TimelineReviewController` |
| Comments | `TimelineCommentService` |
| Threads | `TimelineCommentService` |
| Decisions | `ReviewDecisionService` |
| Merge Guard | `TimelineReviewService.checkMergeGuard()` |

### Workspace Gaps (Addressed)

| Gap | API | Reuses |
|-----|-----|--------|
| Full review header | `GET /reviews/{reviewId}/workspace` | reviewRepo + commentService + decisionService + mergeGuard |
| Thread list for sidebar | `GET /reviews/{reviewId}/threads` | commentService.listThreads() + listComments() |
| Comment list | `GET /reviews/{reviewId}/comments` (filterable by threadId) | commentService.listComments() |
| Entity anchor summary | `GET /reviews/{reviewId}/anchors` | Aggregated from threads + comments |
| Decision overview | `GET /reviews/{reviewId}/decisions` | decisionService.listDecisions() |
| Merge guard for button | `GET /reviews/{reviewId}/merge-guard` | mergeGuard + threads + decisions |

## New Controller: `ReviewWorkspaceController` — 6 aggregation endpoints

| Endpoint | Purpose | Key DTO |
|----------|---------|---------|
| `GET /api/v1/reviews/{reviewId}/workspace` | Full review header | `ReviewWorkspaceDto` |
| `GET /api/v1/reviews/{reviewId}/threads` | Thread list for sidebar | `ReviewThreadDto` |
| `GET /api/v1/reviews/{reviewId}/comments?threadId=` | Comment list (filterable) | `ReviewCommentDto` |
| `GET /api/v1/reviews/{reviewId}/anchors` | Entity anchor statistics | `EntityAnchorDto` |
| `GET /api/v1/reviews/{reviewId}/decisions` | Decision summary | `DecisionSummaryDto` |
| `GET /api/v1/reviews/{reviewId}/merge-guard` | Merge guard button state | `MergeGuardDto` |

## DTO Model (6 new)

| DTO | Fields |
|-----|--------|
| `ReviewWorkspaceDto` | reviewId, revisionId, title, status, authorId, timestamps, approvals, rejections, changeRequests, openThreads, resolvedThreads, commentCount, mergeAllowed, mergeReason |
| `ReviewThreadDto` | threadId, entityRef, status, commentCount, lastActivity |
| `ReviewCommentDto` | commentId, threadId, entityRef, authorId, content, createdAt |
| `EntityAnchorDto` | entityRef, threadCount, commentCount, openIssues |
| `DecisionSummaryDto` | approveCount, rejectCount, changeRequestCount, lastDecision, lastDecisionBy, lastDecisionAt |
| `MergeGuardDto` | allowed, reason, pendingThreads, pendingChangeRequests, requiredApprovals, currentApprovals |

## Observability

```
Review workspace loaded: review=arev_1 threads=5 comments=12 latency=8ms
```

## No Changes To

- Merge Engine, Review Workflow, Outbox/Coordination Runtime
- Database schema, Domain models
- Existing TimelineWorkbenchController or TimelineReviewController

## Known Limitations

| Limitation | Status |
|-----------|--------|
| Comment pagination not implemented | Returns all comments. Paginate when > 100 per review. |
| Thread filter by anchor type not supported | All threads returned. Filter by entityRef pattern in future. |
| Required approvals always 1 | Multi-reviewer approval config deferred. |

## Deferred Items

| Item |
|------|
| Multi-reviewer approval workflow |
| Real-time collaborator presence |
| Branch review workspace |
