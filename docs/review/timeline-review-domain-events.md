---
status: implementation-report
created: 2026-06-24
scope: shared-kernel + render-module + platform-app + outbox-event-module
truth_level: current
owner: platform
---

# Platform Foundation Sprint 014 — Timeline & Review Domain Events

## Event Gap Analysis

### Before Sprint 014

Outbox infrastructure supported only 6 render event types. Timeline (revision, merge, restore) and Review (created, approved, rejected, changes, comments, threads) had zero event representation.

### After Sprint 014

9 new domain events spanning two domains, dispatched via existing outbox infrastructure.

## Event Inventory

### Timeline Domain Events (3)

| Event | eventType | Trigger |
|-------|-----------|---------|
| `TimelineRevisionCreatedEvent` | `timeline.revision.created` | `TimelineRevisionController` (via `TimelineReviewEventPublisher`) |
| `TimelineMergedEvent` | `timeline.merged` | `TimelineRevisionController.merge()` |
| `TimelineRestoredEvent` | `timeline.restored` | (event record created; publish point TBD) |

### Review Domain Events (6)

| Event | eventType | Trigger |
|-------|-----------|---------|
| `ReviewCreatedEvent` | `review.created` | `TimelineReviewController.createReview()` |
| `ReviewApprovedEvent` | `review.approved` | `TimelineReviewController.approve()` |
| `ReviewRejectedEvent` | `review.rejected` | (event record created) |
| `ReviewChangesRequestedEvent` | `review.changes_requested` | (event record created) |
| `ReviewCommentAddedEvent` | `review.comment.added` | (event record created) |
| `ReviewThreadResolvedEvent` | `review.thread.resolved` | (event record created) |

## New Files

| File | Type | Purpose |
|------|------|---------|
| `TimelineRevisionCreatedEvent.java` | Event record | Revision created |
| `TimelineMergedEvent.java` | Event record | Merge completed |
| `TimelineRestoredEvent.java` | Event record | Restore completed |
| `ReviewCreatedEvent.java` | Event record | Review created |
| `ReviewApprovedEvent.java` | Event record | Review approved |
| `ReviewRejectedEvent.java` | Event record | Review rejected |
| `ReviewChangesRequestedEvent.java` | Event record | Changes requested |
| `ReviewCommentAddedEvent.java` | Event record | Comment added |
| `ReviewThreadResolvedEvent.java` | Event record | Thread resolved |
| `TimelineReviewEventPublisher.java` | Service | Publishes events via Spring ApplicationEventPublisher |

## Modified Files

| File | Change |
|------|--------|
| `OutboxEventDispatcher.java` | +9 case branches for timeline + review event types |
| `TimelineRevisionController.java` | +`eventPublisher` dependency; event publish on merge completion |
| `TimelineReviewController.java` | +`eventPublisher` dependency; event publish on create + approve |
| `TimelineMergeControllerTest.java` | +`eventPublisher` mock |
| `TimelineReviewControllerTest.java` | +`eventPublisher` mock + restored test methods |

## Event Flow

```
Controller action (merge/approve/create)
    ↓
TimelineReviewEventPublisher.publish(event)
    ↓
Spring ApplicationEventPublisher
    ↓
OutboxEventDispatcher (reads from outbox_events)
    ↓
@EventListener consumers (notification, audit)
```

## Tests

All existing tests pass (TimelineMergeControllerTest, TimelineReviewControllerTest, TimelineConflictDetectorTest).

## Known Limitations

| Limitation | Status |
|-----------|--------|
| Trigger coverage incomplete | Merge and create/approve wired. Restore, reject, changes, comment, thread not yet connected to controllers. |
| Notification handler not wired | Event records exist. No `@EventListener` for timeline/review events in NotificationEventHandler yet. |
| Audit handler not wired | Event records exist. No `@EventListener` for timeline/review events in AuditEventHandler yet. |
| No outbox_event rows in tests | Events published via Spring ApplicationEventPublisher, not directly to outbox. Integration test needed. |

## Deferred Items

| Item | Sprint |
|------|--------|
| Notification handler for timeline/review events | Sprint 015 |
| Audit handler for timeline/review events | Sprint 015 |
| Asset domain events | Sprint 015 |
| platform_job / platform_task | Sprint 016 |
| LISTEN/NOTIFY wake-up | Sprint 017 |
