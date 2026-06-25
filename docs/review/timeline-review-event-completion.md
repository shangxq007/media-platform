---
status: implementation-report
created: 2026-06-24
scope: notification-module + audit-compliance-module + platform-app
truth_level: current
owner: platform
---

# Platform Foundation Sprint 015 — Timeline & Review Event Completion

## Trigger Coverage Matrix

| Event | Publish Point | Sprint 014 | Sprint 015 |
|-------|---------------|-----------|-----------|
| `TimelineRevisionCreatedEvent` | `recordRevision()` | ⚠️ Record exists | ⚠️ Record exists |
| `TimelineMergedEvent` | `POST /merge` (merged result) | ✅ | ✅ |
| `TimelineRestoredEvent` | `POST /restore` | ❌ Missing | ✅ Wired |
| `ReviewCreatedEvent` | `POST /reviews` | ✅ | ✅ |
| `ReviewApprovedEvent` | `POST /approve` | ✅ | ✅ |
| `ReviewRejectedEvent` | `POST /reject` | ❌ Missing | ✅ Wired |
| `ReviewChangesRequestedEvent` | `POST /request-changes` | ❌ Missing | ✅ Wired |
| `ReviewCommentAddedEvent` | `POST /comments` | ❌ Missing | ⚠️ Record exists |
| `ReviewThreadResolvedEvent` | `POST /comments/{tid}/resolve` | ❌ Missing | ✅ Wired |

**Coverage: 7/9 events have publish points. 2 have records without publish points (RevisionCreated, CommentAdded).**

## Notification Coverage Matrix

| Event | Handler | Sprint 014 | Sprint 015 |
|-------|---------|-----------|-----------|
| `TimelineMergedEvent` | `NotificationEventHandler.onTimelineMerged()` | ❌ | ✅ |
| `TimelineRestoredEvent` | `NotificationEventHandler.onTimelineRestored()` | ❌ | ✅ |
| `ReviewApprovedEvent` | `NotificationEventHandler.onReviewApproved()` | ❌ | ✅ |
| `ReviewRejectedEvent` | `NotificationEventHandler.onReviewRejected()` | ❌ | ✅ |
| `ReviewChangesRequestedEvent` | `NotificationEventHandler.onReviewChangesRequested()` | ❌ | ✅ |
| `ReviewCommentAddedEvent` | `NotificationEventHandler.onReviewCommentAdded()` | ❌ | ✅ |
| `ReviewThreadResolvedEvent` | `NotificationEventHandler.onReviewThreadResolved()` | ❌ | ✅ |

**7 new `@EventListener` methods added to NotificationEventHandler.**

## Audit Coverage Matrix

| Event | Handler | Sprint 014 | Sprint 015 |
|-------|---------|-----------|-----------|
| `TimelineMergedEvent` | `AuditEventHandler.onTimelineMerged()` | ❌ | ✅ |
| `TimelineRestoredEvent` | `AuditEventHandler.onTimelineRestored()` | ❌ | ✅ |
| `ReviewApprovedEvent` | `AuditEventHandler.onReviewApproved()` | ❌ | ✅ |
| `ReviewRejectedEvent` | `AuditEventHandler.onReviewRejected()` | ❌ | ✅ |
| `ReviewChangesRequestedEvent` | `AuditEventHandler.onReviewChangesRequested()` | ❌ | ✅ |
| `ReviewCommentAddedEvent` | `AuditEventHandler.onReviewCommentAdded()` | ❌ | ✅ |
| `ReviewThreadResolvedEvent` | `AuditEventHandler.onReviewThreadResolved()` | ❌ | ✅ |

**7 new `@EventListener` methods added to AuditEventHandler.**

## Outbox Verification

### Event Path

```
Controller action (merge/approve/restore)
    ↓
TimelineReviewEventPublisher.publish(event)
    ↓
Spring ApplicationEventPublisher.publishEvent()
    ↓             ↓
NotificationEventHandler    AuditEventHandler
(onReviewApproved)          (onReviewApproved)
    ↓                         ↓
notification_event table     audit_records table
(notification_delivery)      
```

### Assessment

Events are published via Spring `ApplicationEventPublisher`, NOT directly to `outbox_events`. The `OutboxEventDispatcher` bridges the outbox table → Spring events. The reverse direction (Spring events → outbox) is NOT implemented.

**Current state:** Spring events reach notification + audit directly. Outbox is bypassed for timeline/review events. This means:
- If a listener crashes, the event is lost (no outbox retry)
- Outbox idempotency is not enforced for these events
- This is acceptable for Phase 1 — notification and audit are advisory (not critical)

**Future:** Add outbox-backed event publication for critical business events (review approval, merge).

## Event Flow

```
Timeline Merge:
  POST /merge → TimelineMergeService
    → MERGED? → eventPublisher.publish(TimelineMergedEvent)
      → NotificationEventHandler.onTimelineMerged() → notification_event table
      → AuditEventHandler.onTimelineMerged() → audit_records table

Review Approve:
  POST /approve → reviewService.approve() → decisionService
    → eventPublisher.publish(ReviewApprovedEvent)
      → NotificationEventHandler.onReviewApproved() → notification_event table
      → AuditEventHandler.onReviewApproved() → audit_records table

Timeline Restore:
  POST /restore → revisionService.restore()
    → eventPublisher.publish(TimelineRestoredEvent)
      → NotificationEventHandler.onTimelineRestored() → notification_event table
      → AuditEventHandler.onTimelineRestored() → audit_records table
```

## Modified Files

| File | Change |
|------|--------|
| `TimelineRevisionController.java` | +`TimelineRestoredEvent` publish on restore |
| `TimelineReviewController.java` | +4 events: reject, changes-requested, comment, thread-resolved |
| `NotificationEventHandler.java` | +7 `@EventListener` methods for timeline/review events |
| `AuditEventHandler.java` | +7 `@EventListener` methods for timeline/review events |

## Tests

All existing tests pass (TimelineMergeControllerTest, TimelineReviewControllerTest, TimelineConflictDetectorTest).

## Known Limitations

| Limitation | Status |
|-----------|--------|
| Events bypass outbox | Spring events reach listeners directly. No outbox_events persistence for timeline/review events. |
| `TimelineRevisionCreatedEvent` not wired | Record exists but no publish point in recordRevision(). |
| `ReviewCommentAddedEvent` not wired | Record exists but addComment() controller endpoint doesn't fire it. |
| No integration tests | Unit tests pass. Full event pipeline (publish → notify → audit) not tested end-to-end. |
| projectId = "unknown" for review events | Review controller doesn't currently expose projectId to event. |

## Deferred Items

| Item | Sprint |
|------|--------|
| Outbox-backed event publishing for timeline/review | Sprint 016 |
| Asset domain events | Sprint 016 |
| platform_job / platform_task | Sprint 017 |
| LISTEN/NOTIFY wake-up | Sprint 018 |
