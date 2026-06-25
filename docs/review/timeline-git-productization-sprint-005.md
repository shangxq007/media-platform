---
status: implementation-report
created: 2026-06-24
scope: render-module + platform-app + V1 baseline
truth_level: current
owner: platform
---

# Timeline Git Productization Sprint 005 — Review Workflow + Comment + Approval Foundation

## Review Foundation Audit

**Result: No existing review, comment, or approval models found.** `ConflictResolver` (subsystem decisions), `CollaborationEngine` (OT skeleton), and `Workspace` (conflict strategies) exist in different domains and were NOT reused — they serve different purposes.

**Decision:** Build new timeline-specific review/comment/approval layer from scratch within `render-module`.

## Implemented Domain Models

| Record | File | Purpose |
|--------|------|---------|
| `TimelineReview` | `domain/timeline/internal/TimelineReview.java` | Review entity with OPEN→APPROVED→CHANGES_REQUESTED→MERGED→CLOSED lifecycle |
| `ReviewThread` | `domain/timeline/internal/ReviewThread.java` | Threaded discussion anchored to entity (OPEN/RESOLVED) |
| `TimelineComment` | `domain/timeline/internal/TimelineComment.java` | Entity-anchored comment (CLIP:hero, TRACK:audio_main) — NEVER timecode-only |
| `ReviewDecision` | `domain/timeline/internal/ReviewDecision.java` | Reviewer decision: APPROVE/REQUEST_CHANGES/REJECT |

## Database Changes (4 new tables)

| Table | Columns | Purpose |
|-------|---------|---------|
| `timeline_review` | id, project_id, tenant_id, revision_id, author_user_id, title, description, status, created_at, updated_at | Review lifecycle tracking |
| `review_thread` | id, review_id, entity_ref, diff_id, status, created_at | Threaded anchored discussions |
| `timeline_comment` | id, review_id, thread_id, revision_id, entity_ref, author_user_id, content, created_at | Entity-anchored comments |
| `review_decision` | id, review_id, reviewer_user_id, decision, created_at | APPROVE/REQUEST_CHANGES/REJECT records |

## New Services (3)

| Service | File | Key Methods |
|---------|------|-------------|
| `TimelineReviewService` | `app/timeline/TimelineReviewService.java` | `createReview()`, `getReview()`, `listReviews()`, `approve()`, `requestChanges()`, `reject()`, `closeReview()`, `checkMergeGuard()` |
| `TimelineCommentService` | `app/timeline/TimelineCommentService.java` | `addComment()`, `listComments()`, `resolveThread()`, `reopenThread()` |
| `ReviewDecisionService` | `app/timeline/ReviewDecisionService.java` | `recordDecision()`, `listDecisions()` |

## New Repository (1)

| Repository | File | Purpose |
|-----------|------|---------|
| `TimelineReviewRepository` | `app/timeline/TimelineReviewRepository.java` | jOOQ CRUD for all 4 tables |

## REST API (10 endpoints)

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `POST /reviews` | POST | Create review |
| `GET /reviews` | GET | List project reviews |
| `GET /reviews/{reviewId}` | GET | Review detail with comments, threads, decisions, merge guard |
| `POST /reviews/{reviewId}/comments` | POST | Add entity-anchored comment (auto-creates thread) |
| `GET /reviews/{reviewId}/comments` | GET | List comments |
| `POST /reviews/{reviewId}/comments/{threadId}/resolve` | POST | Resolve thread |
| `POST /reviews/{reviewId}/comments/{threadId}/reopen` | POST | Reopen thread |
| `POST /reviews/{reviewId}/approve` | POST | Approve + record decision |
| `POST /reviews/{reviewId}/request-changes` | POST | Request changes + record decision |
| `POST /reviews/{reviewId}/reject` | POST | Reject + record decision |
| `GET /reviews/{reviewId}/merge-guard` | GET | Check merge gate |

## Merge Guard

```
canMerge = review.status == APPROVED 
           AND no pending REQUEST_CHANGES decisions
           AND (has at least one APPROVE decision OR review.status == APPROVED)

BLOCKED when:
  - Review is OPEN without approval
  - Review has pending change requests
  - Review is CLOSED/CHANGES_REQUESTED
```

Merge engine is unchanged. Guard runs at API level before merge.

## Frontend Contract DTOs

| DTO | Fields |
|-----|--------|
| `ReviewResponse` | reviewId, projectId, revisionId, authorUserId, title, description, status, timestamps |
| `ReviewDetailResponse` | review + comments[] + threads[] + decisions[] + mergeGuard |
| `CommentResponse` | commentId, reviewId, threadId, revisionId, entityRef, authorUserId, content, createdAt |
| `ThreadResponse` | threadId, reviewId, entityRef, diffId, status, createdAt |
| `DecisionResponse` | decisionId, reviewId, reviewerUserId, decision, createdAt |
| `MergeGuardDto` | canMerge, reason |

## Tests Run (All Passing)

| Test Class | Tests | Scenarios |
|-----------|-------|-----------|
| `TimelineReviewControllerTest` | 6 | Create review, add comment, approve, request changes, merge guard, resolve thread |

## Known Limitations

| Limitation | Status |
|-----------|--------|
| No Branch model | Deferred — P4 |
| No Rebase | Deferred — P5 |
| No notification system | Deferred — no event bus yet |
| No real-time collaboration | Deferred — no WebSocket updates |
| No Marketplace review | Deferred — P3 |
| No Asset review | Deferred — P2 |
| Self-review not blocked | Reviewer == author not yet enforced |
| Multi-reviewer not configurable | Always single-reviewer mode in Phase 1 |

## Deferred Items

| Item | Sprint |
|------|--------|
| Branch | P4 |
| Rebase | P5 |
| Asset Ingestion | P1 blueprint |
| Asset Search | P2 |
| Marketplace | P3 |
| OpenCue/OpenAssetIO/OpenLineage/KG | Deferred |

## Validation

- [x] No new module
- [x] No V2 migration
- [x] V1 baseline extended (4 tables)
- [x] No Branch/Rebase
- [x] No OpenCue/OpenAssetIO/OpenLineage/KG
- [x] No Spring AI runtime
- [x] No H2
- [x] ProductionSafetyValidator unchanged
- [x] All tests passing
