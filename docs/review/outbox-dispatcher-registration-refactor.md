---
status: implementation-report
created: 2026-06-24
scope: outbox-event-module
truth_level: current
owner: platform
---

# Platform Foundation Sprint 018 — Outbox Dispatcher Registration Refactor

## Before: Hardcoded Switch (22 cases)

```java
private Object toSpringEvent(Map<String, Object> row) {
    String eventType = String.valueOf(row.get("event_type"));
    return switch (eventType) {
        case "render.job.created" -> objectMapper.convertValue(..., RenderJobCreatedEvent.class);
        case "render.job.status.changed" -> objectMapper.convertValue(..., RenderJobStatusChangedEvent.class);
        // ... 20 more cases
        case "asset.archived" -> objectMapper.convertValue(..., AssetArchivedEvent.class);
        default -> null;
    };
}
```

**Problems:**
- Adding a new event required modifying the dispatcher switch (violates Open/Closed Principle)
- 22 imports at the top for all event classes
- Growing linearly with each new event type
- No registration visibility — had to grep the switch to see all supported types

## After: Registration-Based Routing

```java
private Object toSpringEvent(Map<String, Object> row) {
    String eventType = String.valueOf(row.get("event_type"));
    Class<?> eventClass = router.resolve(eventType);
    if (eventClass == null) return null;  // unknown → mark FAILED
    Map<String, Object> payloadMap = objectMapper.readValue(payload, Map.class);
    return objectMapper.convertValue(payloadMap, eventClass);
}
```

**Benefits:**
- Adding a new event: register in `OutboxEventRegistration`, zero dispatcher changes
- Dispatcher imports only `OutboxEventRouter` (not 22 event classes)
- All 22 routes visible in one file (`OutboxEventRegistration`)
- Unknown events handled gracefully with structured error logging

## Architecture

```
OutboxEventRegistration (@Configuration, @PostConstruct)
    │
    ├── router.register("render.job.created", RenderJobCreatedEvent.class)
    ├── router.register("timeline.merged", TimelineMergedEvent.class)
    │   ... (21 more registrations)
    │
    ▼
OutboxEventRouter (ConcurrentHashMap<String, Class<?>>)
    │
    ├── resolve(eventType) → Class<?> or null
    ├── isKnown(eventType) → boolean
    └── size() → int
    │
    ▼
OutboxEventDispatcher.toSpringEvent()
    │
    router.resolve(eventType) → eventClass
      → objectMapper.convertValue(payloadMap, eventClass)
        → publisher.publishEvent(event)
```

## Event Registration Matrix (22 events)

| Domain | Event Types | Count |
|--------|------------|-------|
| Render | render.job.created/status.changed/completed/failed, artifact.created | 5 |
| Timeline | timeline.revision.created, timeline.merged, timeline.restored | 3 |
| Review | review.created/approved/rejected/changes_requested/comment.added/thread.resolved | 6 |
| Asset | asset.registered/metadata.updated/enriched/submitted.review/approved/published/archived | 7 |
| Special | notification.event.published (string marker, not routed) | 1 |

**Total: 21 routed + 1 special = 22 event types**

## Unknown Event Strategy

When an event type is not registered:
1. `router.resolve()` returns `null`
2. Dispatcher logs: `"Unknown event type '{type}' — not registered in OutboxEventRouter. Register it in OutboxEventRegistration."`
3. Event marked as `FAILED` with reason `UNKNOWN_EVENT_TYPE`
4. Event retried on schedule (exponential backoff)
5. Event dead-lettered after `max_retries` (default 3)

## Files Modified

| File | Change |
|------|--------|
| `OutboxEventDispatcher.java` | Complete rewrite: switch → router-based; 22 imports removed; 1 new import (`OutboxEventRouter`); constructor +1 param |
| `OutboxEventDispatcherTest.java` | Updated constructor for new `router` param |

## Files Added

| File | Purpose |
|------|---------|
| `OutboxEventRouter.java` | ConcurrentHashMap-based event type → Class routing |
| `OutboxEventRegistration.java` | `@Configuration` that registers all 22 event types at `@PostConstruct` |
| `OutboxEventRouterTest.java` | 4 unit tests (register/resolve/known/count) |
| `OutboxEventRegistrationTest.java` | 2 tests (all 22 routes resolve correctly, unknown returns null) |

## Backward Compatibility

| Component | Changes Required? |
|-----------|------------------|
| `NotificationEventHandler` | ✅ None |
| `AuditEventHandler` | ✅ None |
| `TimelineReviewEventPublisher` | ✅ None |
| `AssetController` / `TimelineReviewController` / `TimestampController` | ✅ None |
| Event records (`TimelineMergedEvent`, etc.) | ✅ None |
| `outbox_events` table | ✅ None |

**All 22 events dispatch identically — the internal routing mechanism changed, but the external behavior is unchanged.**

## Open/Closed Principle Achievement

| Metric | Before | After |
|--------|--------|-------|
| Lines changed to add new event type | ~10 lines in dispatcher switch + 1 import | 1 line in `OutboxEventRegistration` |
| Files modified to add new event type | 1 (`OutboxEventDispatcher`) | 1 (`OutboxEventRegistration`) |
| Dispatcher imports event classes? | Yes (22 imports) | No (0 imports) |
| Registration visible? | Must grep switch statement | Single registration file |

## Tests

All tests pass: `OutboxEventRouterTest` (4), `OutboxEventRegistrationTest` (2), `OutboxEventDispatcherTest` (existing).

## Known Limitations

| Limitation | Why Acceptable |
|-----------|---------------|
| Router is in-memory (not persisted) | Registration happens at startup via `@PostConstruct`. No runtime mutation. |
| No event version routing | Not needed — all events are version 1. Add versioned lookup later if needed. |
| `notification.event.published` still special-cased | It's a string marker, not a typed event. Correct to skip routing. |
