# Outbox Relay SPI

**Date:** 2026-07-08
**Status:** COMPLETE
**Authority:** OUTBOX-RELAY-SPI.0

---

## Background

OUTBOX-BOUNDARY-REVIEW.0 confirmed the outbox is CLEAN.
OUTBOX-MODULE-SEPARATION.0 moved coordination out of outbox.
EVENT-CONTRACT.0 defined the event naming and envelope.

This document defines the internal interfaces for relay and delivery provider boundaries.

---

## Current Dispatch Path

```
Domain Service
  → OutboxEventService.append() [DB write, same transaction]
  → outbox_events table
  → OutboxEventDispatcher.scheduledDispatch() [poll every 3s]
  → OutboxEventRouter.resolve() [event type → Java class]
  → Jackson deserialize payload
  → ApplicationEventPublisher.publishEvent() [Spring in-process]
  → @EventListener consumers (NotificationEventHandler, AuditEventHandler, etc.)
```

**Current limitation:** Dispatch only goes to Spring in-process listeners. No external delivery.

---

## Target Architecture

```
Domain Service
  → OutboxEventService.append() [DB write]
  → outbox_events table
  → OutboxRelay.poll() [relay abstraction]
  → OutboxRelay.dispatch() [to delivery providers]
  → EventDeliveryProvider.deliver() [provider abstraction]
    → SpringEventDeliveryProvider (existing behavior)
    → WebhookDeliveryProvider (future)
    → CamelDeliveryProvider (future)
    → EventMeshDeliveryProvider (future)
    → SearchIndexDeliveryProvider (future)
```

---

## OutboxRelay SPI

### Interface

```java
/**
 * Relay abstraction for dispatching outbox events.
 *
 * Reads pending events from outbox, resolves delivery targets,
 * and delegates to EventDeliveryProvider implementations.
 */
public interface OutboxRelay {

    /**
     * Process a batch of pending outbox events.
     * @return number of events successfully dispatched
     */
    int processBatch(int batchSize);

    /**
     * Process a single outbox event by ID.
     * @return true if successfully dispatched
     */
    boolean processOnce(String outboxEventId);

    /**
     * Retry due failed events.
     * @return number of events retried
     */
    int retryDueEvents();
}
```

### Current Implementation

The existing `OutboxEventDispatcher` already implements this behavior. It should be wrapped or adapted to implement the `OutboxRelay` interface.

**No behavior change required.** The interface formalizes what already exists.

---

## EventDeliveryProvider SPI

### Interface

```java
/**
 * Delivery provider abstraction for sending events to destinations.
 *
 * Each provider handles a specific delivery mechanism:
 * Spring events, webhooks, EventMesh, search index, etc.
 */
public interface EventDeliveryProvider {

    /**
     * Provider name for logging and metrics.
     */
    String name();

    /**
     * Whether this provider can deliver the given event.
     * @param eventType the canonical event type
     * @return true if this provider handles this event type
     */
    boolean supports(String eventType);

    /**
     * Deliver the event to the destination.
     * @param event the deserialized event
     * @param context delivery context (tenant, project, metadata)
     * @throws Exception if delivery fails
     */
    void deliver(Object event, DeliveryContext context) throws Exception;
}
```

### DeliveryContext

```java
/**
 * Context for event delivery, carrying metadata without exposing internals.
 */
public record DeliveryContext(
    String outboxEventId,
    String eventType,
    String aggregateType,
    String aggregateId,
    String tenantId,
    String projectId,
    String correlationId,
    Map<String, Object> metadata
) {}
```

### Provider Implementations

| Provider | Status | Description |
|----------|--------|-------------|
| SpringEventDeliveryProvider | EXISTS (current behavior) | Publishes to Spring ApplicationEventPublisher |
| WebhookDeliveryProvider | FUTURE | Delivers via HTTP webhook |
| CamelDeliveryProvider | FUTURE | Delegates to Apache Camel route |
| EventMeshDeliveryProvider | FUTURE | Publishes to Apache EventMesh |
| SearchIndexDeliveryProvider | FUTURE | Indexes in search engine |
| NotificationDeliveryProvider | FUTURE | Sends notifications |

---

## Event Type → Provider Mapping

### Current (Implicit)

All events go to Spring ApplicationEventPublisher.

### Future (Explicit)

Event type patterns map to providers:

| Pattern | Provider |
|---------|----------|
| `render.*` | SpringEventDeliveryProvider |
| `artifact.*` | SpringEventDeliveryProvider, WebhookDeliveryProvider |
| `product.*` | SpringEventDeliveryProvider, WebhookDeliveryProvider |
| `publish.*` | CamelDeliveryProvider |
| `search.*` | SearchIndexDeliveryProvider |
| `notification.*` | NotificationDeliveryProvider |

**Note:** This mapping belongs to a future EventSubscription model, NOT in the outbox.

---

## Integration with Existing Code

### Current OutboxEventDispatcher

```java
@Component
public class OutboxEventDispatcher {
    // Current: polls outbox, publishes Spring events
    // Future: implements OutboxRelay, delegates to providers
}
```

### Migration Path

1. **Now:** Define interfaces (this task)
2. **Next:** Wrap OutboxEventDispatcher as SpringEventDeliveryProvider
3. **Later:** Add WebhookDeliveryProvider behind interface
4. **Future:** Add CamelDeliveryProvider for relay POC

**No behavior change in this task.**

---

## Placement in Module Structure

```
outbox-event-module/
  com.example.platform.outbox/
    app/
      OutboxEventService        ← persists events
      OutboxEventDispatcher     ← current dispatcher (will implement OutboxRelay)
      OutboxEventRouter         ← type resolution
      OutboxEventRegistration   ← type registration
      OutboxRelay               ← NEW interface
      EventDeliveryProvider     ← NEW interface
      DeliveryContext           ← NEW record
      SpringEventDeliveryProvider ← NEW adapter (wraps existing behavior)
    coordination/
      PlatformCoordinationService  ← separated job/task orchestration
```

---

## Rules

1. Outbox must NOT own delivery routing.
2. Outbox must NOT know about destination URLs.
3. Relay reads from outbox and delegates to providers.
4. Providers handle protocol-specific delivery.
5. Event type → provider mapping belongs to subscription model, not outbox.
6. No route-specific data in outbox_events table.

---

## Tool Placement

| Tool | Layer | Status |
|------|-------|--------|
| Apache Camel | EventDeliveryProvider implementation | Candidate |
| Apache EventMesh | EventDeliveryProvider implementation | Future extension |
| Apache APISIX | Gateway (separate from relay) | Candidate/Deferred |
| CloudEvents | External envelope mapping | Recommended |
| AsyncAPI | Event documentation | Candidate |

---

## Summary

- OutboxRelay SPI: DEFINED
- EventDeliveryProvider SPI: DEFINED
- DeliveryContext: DEFINED
- Current behavior preserved: YES
- External dependencies added: NO
- Schema changes: NO

---

## EventRouter Responsibilities

### What EventRouter Does

- Match event against subscriptions/rules
- Fan out to zero or more delivery providers
- Apply lightweight filters
- Choose delivery target based on subscription config
- Handle subscription-level routing

### What EventRouter Does NOT Do

- Write domain state
- Own transactional outbox
- Mutate original event payload
- Persist routing config inside outbox row

### Current State

EventRouter is not implemented yet. Current dispatch goes directly to Spring events.

### Future Implementation

When EventSubscription model is introduced, EventRouter will sit between OutboxRelay and EventDeliveryProvider.

---

## Default Delivery Path

### Current

```
outbox_events → OutboxEventDispatcher → Spring ApplicationEventPublisher
```

All events go to Spring in-process listeners. No external delivery.

### Preserved Behavior

- OutboxEventDispatcher polls every 3 seconds
- Events locked, deserialized, published
- Marked PROCESSED on success
- Marked FAILED on failure
- Retry after max-retries → DEAD_LETTER

---

## What Must NOT Go Into Outbox

| Item | Reason |
|------|--------|
| Destination URL | Belongs to delivery provider config |
| Connector credentials | Belongs to provider secrets |
| Camel route ID | Belongs to integration runtime |
| APISIX route ID | Belongs to gateway config |
| EventMesh broker config | Belongs to event bus config |
| Route-specific retry policy | Belongs to subscription model |
| External protocol config | Belongs to provider |

---

## Risks

| Risk | Mitigation |
|------|------------|
| Duplicate delivery | Idempotency key exists in outbox |
| Retry policy split | Current: per-event global. Future: per-subscription |
| Over-engineering | Conservative: interfaces only, no runtime change |
| Premature Camel adoption | Document as candidate, don't implement |
| Losing transaction reliability | Keep outbox as DB-backed boundary |
| Route leakage returning | Explicit rules in this document |

---

## Open Questions

1. Do we need event claiming/locking beyond current implementation?
2. Is retry policy per event or per subscription?
3. Do we need a delivery attempt table for audit?
4. Should CloudEvents be external envelope only?
5. When should AsyncAPI spec be generated?
6. Are current outbox schema fields sufficient for relay?

---

## Phase A — Current Dispatch Answers

| Question | Answer |
|----------|--------|
| 1. Who writes outbox events? | Domain services via `OutboxEventService.append()` in same DB transaction |
| 2. Who reads pending outbox events? | `OutboxEventDispatcher.scheduledDispatch()` polls every 3s |
| 3. Who marks events published/failed? | `OutboxEventService.markProcessed()` / `markFailed()` |
| 4. Does dispatcher publish Spring events only? | YES — `ApplicationEventPublisher.publishEvent()` |
| 5. Are there existing retry semantics? | YES — global max-retries, exponential backoff, DEAD_LETTER |
| 6. Are there existing transaction boundaries? | YES — append is in domain transaction, dispatch is separate |
| 7. Are there existing scheduler/poller classes? | YES — `OutboxEventDispatcher`, `DispatchBooster` |
| 8. Are any delivery targets hardcoded? | NO — all events go to Spring `ApplicationEventPublisher` |
| 9. Are any event type routes hardcoded? | NO — `OutboxEventRouter` uses registration-based mapping |
| 10. Module cycles or unwanted dependencies? | NO — after OUTBOX-MODULE-SEPARATION.0, outbox is clean |
