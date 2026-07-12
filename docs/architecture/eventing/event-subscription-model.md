# Event Subscription Model

**Date:** 2026-07-08
**Status:** DESIGN_ONLY
**Authority:** EVENT-SUBSCRIPTION-MODEL.0

---

## Background

OUTBOX-BOUNDARY-REVIEW.0 confirmed outbox is CLEAN.
OUTBOX-MODULE-SEPARATION.0 moved coordination out of outbox.
EVENT-CONTRACT.0 defined event naming and envelope.
OUTBOX-RELAY-SPI.0 defined relay and delivery provider boundaries.

This document defines the subscription model that determines which events go where.

---

## Current Inventory

| Concept | Location | Responsibility | Maps to EventSubscription |
|---------|----------|---------------|--------------------------|
| @EventListener | notification-module | Consume render events | YES (INTERNAL_LISTENER) |
| @EventListener | audit-compliance-module | Audit trail | YES (AUDIT_LOG) |
| OutboxEventRouter | outbox.app | Event type → class | PARTIAL (type resolution only) |
| OutboxEventRegistration | outbox.app | Register 22 event types | NO (static config) |
| NotificationEventPublisher | notification-module | Write to outbox | NO (producer, not consumer) |

**Current limitation:** All routing is implicit via Spring @EventListener. No configurable subscriptions.

---

## Problem Statement

Relay and delivery boundaries exist, but routing rules have no explicit model. Without EventSubscription, future webhook/Camel/EventMesh work may hardcode routing or leak destination config into outbox.

---

## EventSubscription Concept

### Fields

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| id | string | YES | Subscription ID |
| name | string | YES | Human-readable name |
| description | string | NO | Purpose of subscription |
| eventTypePattern | string | YES | Event type match (exact or wildcard) |
| eventCategory | string | NO | Category filter (LifecycleEvent, DomainFactEvent, etc.) |
| tenantId | string | NO | Tenant scope (null = system-wide) |
| projectId | string | NO | Project scope (null = all projects) |
| subscriberType | string | YES | Intent type |
| deliveryProviderType | string | YES | Implementation type |
| destinationRef | string | YES | Reference to destination config |
| filterExpression | json | NO | Additional filter criteria |
| retryPolicyRef | string | NO | Reference to retry policy |
| enabled | boolean | YES | Active/inactive |
| priority | int | NO | Execution order |
| createdAt | timestamp | YES | Creation time |
| updatedAt | timestamp | YES | Last update |
| createdBy | string | NO | Creator identity |
| version | int | YES | Optimistic locking |
| metadata | json | NO | Non-secret metadata |

### Rules

- EventSubscription is NOT an outbox event
- EventSubscription does NOT create domain facts
- EventSubscription controls routing from router to provider
- EventSubscription references destination by ID/ref, NOT embedded secrets
- EventSubscription must NOT mutate original event
- EventSubscription must be versioned for future user-configurable editing

---

## Event Type Matching

### Exact Match (First-class)

```
eventTypePattern: "render.job.completed"
→ matches only render.job.completed
```

### Prefix/Wildcard Match (Supported)

```
eventTypePattern: "render.job.*"
→ matches render.job.completed, render.job.failed, etc.

eventTypePattern: "artifact.*"
→ matches artifact.created, artifact.content_ready, etc.
```

### Category Match (Future)

```
eventCategory: "LifecycleEvent"
→ matches all lifecycle events
```

### Rules

- Exact match is first-class
- Wildcard uses `*` suffix only
- Regex avoided in MVP
- Event type names follow EVENT-CONTRACT.0 convention
- Route matching NOT stored in outbox_events

---

## Subscriber Types

| subscriberType | Description |
|----------------|-------------|
| INTERNAL_LISTENER | In-process Spring event listener |
| WEBHOOK | External HTTP webhook |
| EVENT_BUS | Event mesh/bus distribution |
| SEARCH_INDEX | Search engine indexing |
| NOTIFICATION | User notification delivery |
| AUDIT_LOG | Audit trail recording |
| CONNECTOR | External system connector |
| AGENT_CALLBACK | AI/agent callback |
| CUSTOM_INTEGRATION | Custom integration |

---

## Delivery Provider Types

| deliveryProviderType | Description |
|---------------------|-------------|
| SPRING_EVENT | Spring ApplicationEventPublisher |
| WEBHOOK | HTTP webhook delivery |
| EVENTMESH | Apache EventMesh |
| CAMEL | Apache Camel route |
| SEARCH | Search engine |
| NOTIFICATION | Notification service |
| AUDIT | Audit logging |
| CONNECTOR | External connector |

### subscriberType → deliveryProviderType Mapping

| subscriberType | Default deliveryProviderType |
|----------------|------------------------------|
| INTERNAL_LISTENER | SPRING_EVENT |
| WEBHOOK | WEBHOOK |
| EVENT_BUS | EVENTMESH |
| SEARCH_INDEX | SEARCH |
| NOTIFICATION | NOTIFICATION |
| AUDIT_LOG | AUDIT |
| CONNECTOR | CAMEL or CONNECTOR |

---

## DestinationRef Concept

### Purpose

Reference destination configuration without storing secrets in outbox.

### Format

```
{type}:{id}
```

### Examples

| DestinationRef | Description |
|----------------|-------------|
| `webhook_endpoint:wh_xxx` | Webhook endpoint config |
| `eventmesh_topic:topic_xxx` | EventMesh topic config |
| `search_index:idx_xxx` | Search index config |
| `notification_channel:ch_xxx` | Notification channel |
| `connector:conn_xxx` | Connector config |
| `audit_sink:audit_xxx` | Audit sink config |

### Rules

- Points to config stored outside outbox
- Must NOT contain raw secret values
- Must NOT contain full target URL if private
- DeliveryProvider resolves DestinationRef
- Tenant/project access checked during resolution
- Must be stable and auditable

---

## Filter Model

### Levels

| Level | Priority | Description |
|-------|----------|-------------|
| Event type filter | Required | eventTypePattern match |
| Tenant/project filter | Recommended | Scope filtering |
| Aggregate filter | Optional | aggregateType match |
| Payload field filter | Future | Allowlisted payload fields |
| Custom expression | Deferred | Advanced filtering |

### Example filterExpression

```json
{
  "aggregateType": "RenderJob",
  "payload": {
    "artifactType": "FINAL_RENDER"
  }
}
```

### Rules

- MVP avoids arbitrary expression language
- No unsafe code execution
- Payload filters use allowlisted fields
- Filtering is deterministic
- Filtering must NOT mutate event payload

---

## Retry Policy Model

### Conceptual RetryPolicy Fields

| Field | Type | Description |
|-------|------|-------------|
| id | string | Policy ID |
| name | string | Policy name |
| maxAttempts | int | Maximum retry attempts |
| initialDelaySeconds | int | Initial delay |
| maxDelaySeconds | int | Maximum delay |
| backoffMultiplier | float | Exponential backoff factor |
| jitterEnabled | boolean | Add jitter |
| retryableErrorCodes | list | Codes that trigger retry |
| deadLetterEnabled | boolean | Dead letter on exhaustion |
| timeoutSeconds | int | Per-attempt timeout |

### Rules

- Retry policy belongs to subscription/delivery, NOT outbox event fact
- Outbox row tracks retryCount for relay state
- Policy may differ by delivery provider
- Permanent vs retryable failure must be distinguishable

---

## DeliveryAttempt Future Model

### Conceptual Fields

| Field | Type | Description |
|-------|------|-------------|
| id | string | Attempt ID |
| outboxEventId | string | Source outbox event |
| subscriptionId | string | Target subscription |
| deliveryProviderType | string | Provider used |
| destinationRef | string | Destination reference |
| attemptNumber | int | Attempt sequence |
| status | string | PENDING/SUCCESS/FAILED/DEAD_LETTER |
| startedAt | timestamp | Start time |
| completedAt | timestamp | Completion time |
| nextRetryAt | timestamp | Next retry time |
| errorCode | string | Error classification |
| errorMessageSummary | string | Safe error summary |

### When Needed

- When one event fans out to multiple destinations
- When per-subscription retry tracking is required
- When delivery audit trail is needed

---

## Security and Tenancy Rules

| Rule | Description |
|------|-------------|
| Tenant scoping | Project-scoped subscription must not receive other project events |
| Project scoping | Tenant-scoped subscription may receive all project events if authorized |
| Global subscriptions | System-wide subscriptions must be explicit and internal-only |
| Destination resolution | Must enforce ownership |
| Secrets | Stored in secret manager, not in event/subscription/outbox |
| Logging | No secrets or raw credentials in logs |
| Artifact paths | Not exposed through event payloads |

---

## CloudEvents / AsyncAPI Relationship

### CloudEvents

- EventSubscription routes platform event envelopes
- DeliveryProvider may serialize as CloudEvents externally
- CloudEvents `type` → `eventType`
- CloudEvents `subject` → aggregate path
- Tenant/project/correlation → CloudEvents extensions

### AsyncAPI

- EventSubscription model drives future AsyncAPI channels
- Internal-only listeners do NOT need AsyncAPI docs
- Future channels: render.job.completed, artifact.created, product.ready, etc.

---

## Camel / EventMesh / APISIX Relationship

| Tool | Role | Status |
|------|------|--------|
| Apache Camel | EventRouter/DeliveryProvider implementation | Candidate |
| Apache EventMesh | EventBus delivery provider | Future extension |
| Apache APISIX | Gateway layer (NOT relay) | Candidate/Deferred |

---

## Migration Roadmap

| Phase | Task | Description |
|-------|------|-------------|
| 1 | EVENT-SUBSCRIPTION-MODEL.0 | ✅ This document |
| 2 | EVENT-SUBSCRIPTION-SPI.0 | Add internal interfaces |
| 3 | WEBHOOK-DELIVERY-PROVIDER.0 | Implement webhook provider |
| 4 | DELIVERY-ATTEMPT-MODEL.0 | Add delivery attempt tracking |
| 5 | OUTBOX-LIGHTWEIGHT-REDESIGN.0 | Schema refinements if needed |
| 6 | CAMEL-OUTBOX-RELAY-POC.0 | Camel relay evaluation |
| 7 | ASYNCAPI-RENDER-EVENTS.0 | Event documentation |

---

## Open Questions

1. Should subscription config be DB-backed or config-backed in MVP?
2. Do we need per-subscription DeliveryAttempt table immediately?
3. Should filters support payload fields in MVP?
4. Should tenant/project subscriptions be user-configurable?
5. What event types should be externally deliverable first?
6. How should webhook secrets be stored?
7. How should delivery idempotency be enforced?
