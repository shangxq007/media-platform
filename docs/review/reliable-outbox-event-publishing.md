---
status: implementation-report
created: 2026-06-24
scope: render-module + platform-app + outbox-event-module
truth_level: current
owner: platform
---

# Platform Foundation Sprint 016 — Reliable Outbox Event Publishing

## Before (Sprint 015)

```
Controller Action
    ↓
TimelineReviewEventPublisher.publish(event)
    ↓
ApplicationEventPublisher.publishEvent()  ← direct Spring event
    ↓              ↓
Notification    Audit
```

**Problems:**
- Events bypass `outbox_events` table entirely
- If a listener crashes, the event is lost (no retry)
- No idempotency (duplicate events on retry)
- No dead-letter for failed processing
- Architecture inconsistency: render uses outbox, timeline/review don't

## After (Sprint 016)

```
Controller Action
    ↓
TimelineReviewEventPublisher.publish(event)
    ↓
OutboxEventService.appendEvent()  ← writes to outbox_events table
    ↓
outbox_events table (immutable, retry, dead-letter, idempotent)
    ↓
OutboxEventDispatcher (polls every 3s)
    ↓
ApplicationEventPublisher.publishEvent()  ← Spring event
    ↓              ↓
Notification    Audit
```

**Improvements:**
- All events written to `outbox_events` first — single source of truth
- At-least-once delivery with exponential backoff retry
- Idempotency via `idempotency_key`
- Dead-letter for permanently failed events
- Unified architecture: render + timeline + review all use outbox

## Event Coverage Matrix (9/9 events)

| Event | Outbox route | Publish Point |
|-------|-------------|---------------|
| `TimelineRevisionCreatedEvent` | `timeline.revision.created` | ⚠️ Record + dispatcher ready; publish point in TimelineEditorSyncController pending |
| `TimelineMergedEvent` | `timeline.merged` | ✅ `POST /merge` |
| `TimelineRestoredEvent` | `timeline.restored` | ✅ `POST /restore` |
| `ReviewCreatedEvent` | `review.created` | ✅ `POST /reviews` |
| `ReviewApprovedEvent` | `review.approved` | ✅ `POST /approve` |
| `ReviewRejectedEvent` | `review.rejected` | ✅ `POST /reject` |
| `ReviewChangesRequestedEvent` | `review.changes_requested` | ✅ `POST /request-changes` |
| `ReviewCommentAddedEvent` | `review.comment.added` | ✅ `POST /comments` (wired this sprint) |
| `ReviewThreadResolvedEvent` | `review.thread.resolved` | ✅ `POST /comments/{tid}/resolve` |

**8/9 complete. 1 pending (RevisionCreated — requires service-level integration in recordRevision()).**

## Modified Files

| File | Change |
|------|--------|
| `render-module/build.gradle.kts` | +`api(project(":outbox-event-module"))` dependency |
| `TimelineReviewEventPublisher.java` | Complete rewrite: `ApplicationEventPublisher` → `OutboxEventService.appendEvent()` for all 9 event types |
| `TimelineReviewController.java` | +`ReviewCommentAddedEvent` publish in `addComment()` |

## Event Flow (Unified)

```
All domains (Render, Timeline, Review) now share one event path:

Business Action → OutboxEventService.append() → outbox_events table
    ↓ (OutboxEventDispatcher, 3s poll)
Spring ApplicationEventPublisher
    ↓                         ↓
NotificationEventHandler    AuditEventHandler
(onXxxEvent)                (onXxxEvent)
    ↓                         ↓
notification_event table     audit_records table
notification_delivery
```

## Reliability Improvements

| Aspect | Before (Sprint 015) | After (Sprint 016) |
|--------|--------------------|--------------------|
| **Persistence** | In-memory Spring event | `outbox_events` table (PostgreSQL ACID) |
| **Crash recovery** | Event lost if listener crashes | Dispatcher retries on restart |
| **Retry** | None | Exponential backoff (0s→5s→30s→5min) |
| **Dead-letter** | None | `status = 'DEAD_LETTER'` after max_attempts |
| **Idempotency** | None | `idempotency_key` prevents duplicates |
| **Observability** | Logs only | `outbox_events` table — queryable, auditable |

## Dispatcher Integration (Verified)

The `OutboxEventDispatcher` already routes all 9 timeline/review event types (added in Sprint 014). No dispatcher changes needed — the existing routing works with the new outbox-backed publishing.

```
OutboxEventDispatcher.toSpringEvent():
  case "timeline.merged" → TimelineMergedEvent
  case "review.approved" → ReviewApprovedEvent
  ... (7 more cases)
```

## Notification Validation

Notification handlers (`NotificationEventHandler`) remain unchanged. They consume Spring events from the dispatcher, which now sources from `outbox_events` instead of direct controller calls. Same listener methods, more reliable delivery.

## Audit Validation

Audit handlers (`AuditEventHandler`) remain unchanged. Same pattern — consume Spring events, record to `audit_records`. Now with outbox reliability guarantees.

## Tests

All existing tests pass (TimelineMergeControllerTest, TimelineReviewControllerTest, TimelineConflictDetectorTest).

## Known Limitations

| Limitation | Status |
|-----------|--------|
| `TimelineRevisionCreatedEvent` publish point | Pending — requires integration in `TimelineEditorSyncController` or `TimelineRevisionService.recordRevision()` |
| No outbox integration tests | Unit tests pass. Full outbox write → dispatch → consume E2E test not implemented. |
| Outbox dispatcher disabled in preview | `app.outbox.dispatcher-enabled: false` in preview environments — events written but not dispatched |

## Deferred Items

| Item | Sprint |
|------|--------|
| Asset domain events | Sprint 017 |
| Dispatcher registration refactor | Sprint 018 |
| platform_job / platform_task | Sprint 019 |
| LISTEN/NOTIFY wake-up | Sprint 020 |
