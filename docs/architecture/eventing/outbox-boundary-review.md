# Outbox Boundary Review

**Date:** 2026-07-08
**Status:** COMPLETE
**Authority:** OUTBOX-BOUNDARY-REVIEW.0

---

## Problem Statement

The outbox module (`outbox-event-module`) may contain routing-related responsibilities, making it heavier than a transactional outbox should be. This review evaluates the current design and proposes a cleaner architecture.

---

## Outbox Boundary Principle

> **Outbox 不是路由器。Outbox 是领域事件的事务可靠边界。路由、转换、协议适配和外部投递属于事件中继 / 集成层。**
>
> Outbox is not a router.
> Outbox is the transactional reliability boundary for domain events.
> Routing, transformation, protocol adaptation, and external delivery belong to the event relay / integration layer.

---

## Current Outbox Inventory

### outbox_events Table

| Column | Type | Purpose | Classification |
|--------|------|---------|----------------|
| id | varchar(64) PK | Event ID | ✅ Outbox |
| aggregate_type | varchar(100) | Domain aggregate type | ✅ Outbox |
| aggregate_id | varchar(100) | Domain aggregate ID | ✅ Outbox |
| event_type | varchar(150) | Event type name | ✅ Outbox |
| event_version | int | Schema version | ✅ Outbox |
| payload | text | Event payload JSON | ✅ Outbox |
| status | varchar(50) | Processing status | ✅ Outbox |
| created_at | timestamp | Creation time | ✅ Outbox |
| published_at | timestamp | Publication time | ✅ Outbox |
| retry_count | int | Retry counter | ✅ Outbox |
| next_attempt_at | timestamp | Next retry time | ✅ Outbox |
| idempotency_key | varchar(255) | Idempotency key | ✅ Outbox |
| locked_at | timestamptz | Processing lock | ✅ Outbox |
| locked_by | varchar(100) | Processor ID | ✅ Outbox |
| max_retries | int | Max retry limit | ✅ Outbox |

**Assessment: CLEAN** — No route-specific, protocol-specific, or external target data.

### OutboxEventService

| Method | Purpose | Classification |
|--------|---------|----------------|
| append() | Write event to outbox | ✅ Outbox |
| lockBatch() | Lock pending events for processing | ✅ Outbox |
| markProcessed() | Mark event as dispatched | ✅ Outbox |
| markFailed() | Mark event as failed | ✅ Outbox |
| overview() | Query outbox status | ✅ Outbox |
| recent() | Query recent events | ✅ Outbox |

**Assessment: CLEAN** — Pure outbox CRUD, no routing logic.

### OutboxEventDispatcher

| Responsibility | Classification |
|----------------|----------------|
| Poll pending events | ✅ Outbox Relay |
| Lock batch | ✅ Outbox Relay |
| Deserialize payload | ✅ Outbox Relay |
| Publish to Spring ApplicationEventPublisher | ⚠️ Relay (internal) |
| Retry scheduling | ✅ Outbox Relay |
| Dead letter handling | ✅ Outbox Relay |
| Metrics (dispatched/failed/retried) | ✅ Outbox Relay |

**Assessment: MOSTLY CLEAN** — Dispatches to Spring events only, no external routing.

### OutboxEventRouter

| Responsibility | Classification |
|----------------|----------------|
| Map event type → Java class | ✅ Type Resolution |
| Register event types | ✅ Type Resolution |

**Assessment: CLEAN** — Pure type registry, no routing behavior.

### OutboxEventRegistration

| Responsibility | Classification |
|----------------|----------------|
| Register 22 event types | ✅ Type Registration |

**Assessment: CLEAN** — Static configuration, no routing.

### PlatformJob / PlatformTask (in same module)

| Model | Purpose | Classification |
|-------|---------|----------------|
| PlatformJob | Job coordination (fan-out/fan-in) | ⚠️ Job Orchestration |
| PlatformTask | Individual work unit | ⚠️ Job Orchestration |
| ExecutionBackend | Backend abstraction | ⚠️ Job Orchestration |
| TaskHandler | Task handler SPI | ⚠️ Job Orchestration |

**Assessment: MIXED** — Job orchestration models co-located with event outbox. These are separate concerns.

---

## Current Classification

**MIXED** — The event outbox itself is clean, but the module also contains job orchestration models (PlatformJob, PlatformTask, ExecutionBackend, TaskHandler) that are a separate concern.

### Routing Leakage Assessment

| Question | Answer |
|----------|--------|
| Does outbox store route-specific data? | NO |
| Does outbox decide where events go? | NO (dispatches to Spring events only) |
| Does outbox transform payloads? | NO |
| Does outbox contain retry policies per destination? | NO (global max-retries) |
| Does outbox contain connector-specific behavior? | NO |
| Does outbox couple domain transactions to external integration? | NO |
| Does outbox have multiple responsibilities? | YES (event outbox + job orchestration) |

**Conclusion:** The event outbox is CLEAN. The module is MIXED due to job orchestration co-location.

---

## Target Eventing Architecture

```
Domain Service
    ↓
Domain Event (DomainEvent record)
    ↓
Transactional Outbox (outbox_events table)
    ↓
Outbox Relay (OutboxEventDispatcher)
    ↓
Event Router / Integration Runtime (future)
    ↓
Delivery Providers (future)
    ↓
External Systems
```

### Layer Responsibilities

| Layer | Responsibility | Current State |
|-------|---------------|---------------|
| Domain Event | Platform-owned event fact | ✅ Exists (22 types) |
| Transactional Outbox | DB-backed reliability boundary | ✅ Exists (clean) |
| Outbox Relay | Poll, lock, dispatch | ✅ Exists (Spring events) |
| Event Router | Subscription matching, filtering | ❌ Not implemented |
| Delivery Provider | Webhook, search, notification | ⚠️ Partial (Spring @EventListener) |
| Gateway | External API ingress | ❌ Not implemented |

---

## Proposed Domain Event Categories

| Category | Events | Status |
|----------|--------|--------|
| render.* | job.created, job.status.changed, job.completed, job.failed | ✅ Registered |
| artifact.* | created | ✅ Registered |
| timeline.* | revision.created, merged, restored | ✅ Registered |
| review.* | created, approved, rejected, changes_requested, comment.added, thread.resolved | ✅ Registered |
| asset.* | registered, metadata.updated, enriched, submitted.review, approved, published, archived | ✅ Registered |
| media.* | input.registered | ❌ Not implemented |
| provider.* | selected, completed | ❌ Not implemented |
| worker.* | job.started, job.failed | ❌ Not implemented |
| connector.* | delivery.completed, delivery.failed | ❌ Not implemented |

---

## Minimal Outbox Contract

The current `outbox_events` table already implements a clean minimal contract:

```sql
CREATE TABLE outbox_events (
    id              VARCHAR(64) PRIMARY KEY,
    aggregate_type  VARCHAR(100) NOT NULL,
    aggregate_id    VARCHAR(100) NOT NULL,
    event_type      VARCHAR(150) NOT NULL,
    event_version   INT NOT NULL,
    payload         TEXT NOT NULL,
    status          VARCHAR(50) NOT NULL,
    created_at      TIMESTAMP NOT NULL,
    published_at    TIMESTAMP,
    retry_count     INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP,
    idempotency_key VARCHAR(255),
    locked_at       TIMESTAMPTZ,
    locked_by       VARCHAR(100),
    max_retries     INTEGER NOT NULL DEFAULT 3
);
```

**Recommended additions (future):**

| Column | Purpose | Priority |
|--------|---------|----------|
| tenant_id | Multi-tenant filtering | P1 |
| project_id | Project-scoped filtering | P1 |
| correlation_id | Request correlation | P2 |
| causation_id | Event chain tracking | P2 |

**Should NOT be stored in outbox:**

- Target URL / endpoint
- Protocol-specific config
- Provider-specific config
- Connector credentials
- External route IDs
- APISIX route IDs
- Camel route IDs
- EventMesh topic

---

## Event Subscription / Route Rule Model (Future)

```sql
CREATE TABLE event_subscriptions (
    id                  VARCHAR(64) PRIMARY KEY,
    name                VARCHAR(200) NOT NULL,
    event_type_pattern  VARCHAR(200) NOT NULL,  -- e.g. "render.*", "artifact.created"
    tenant_id           VARCHAR(100),           -- optional filter
    subscriber_type     VARCHAR(50) NOT NULL,   -- e.g. "webhook", "search_index", "notification"
    destination_ref     VARCHAR(500),           -- reference to delivery config
    filter_json         TEXT,                   -- additional filter criteria
    enabled             BOOLEAN NOT NULL DEFAULT TRUE,
    retry_policy_ref    VARCHAR(100),           -- reference to retry policy
    created_at          TIMESTAMP NOT NULL,
    updated_at          TIMESTAMP NOT NULL
);
```

---

## Candidate Tool Placement

| Tool | Layer | Role | Status |
|------|-------|------|--------|
| **Apache Camel** | Event Relay / Integration Runtime | Connector runtime, outbox polling, webhook dispatch | Candidate / POC recommended |
| **Apache APISIX** | Gateway Layer | External API gateway, webhook ingress, rate limiting | Candidate / Deferred |
| **Apache EventMesh** | Event Bus | Future cross-service event mesh | Future extension |
| **CloudEvents** | Event Contract | Canonical event envelope | Recommended for evaluation |
| **AsyncAPI** | Documentation | Event contract documentation | Candidate |

### Apache Camel

**Recommended role:** Event Relay / Integration Runtime

**Candidate tasks:**
- CAMEL-OUTBOX-RELAY-POC.0
- CAMEL-CONNECTOR-RUNTIME-SPIKE.0

**Should NOT replace:**
- Transactional outbox
- Domain events
- RenderJob lifecycle
- Product/Artifact model

### Apache APISIX

**Recommended role:** External API/Webhook Gateway

**Candidate tasks:**
- APISIX-GATEWAY-EVALUATION.0
- WEBHOOK-GATEWAY-POC.0

**Should NOT replace:**
- Outbox
- Event relay
- Internal event routing
- Business authorization

### Apache EventMesh

**Recommended role:** Future EventBusProvider

**Candidate tasks:**
- EVENTMESH-PROVIDER-SPIKE.0

**Should NOT replace:**
- Transactional outbox
- Event creation inside DB transaction

### CloudEvents

**Recommended role:** Canonical event envelope candidate

**Candidate task:**
- CLOUDEVENTS-EVENT-CONTRACT.0

### AsyncAPI

**Recommended role:** Event contract documentation

**Candidate task:**
- ASYNCAPI-RENDER-EVENTS.0

---

## Migration Plan

| Phase | Task | Description |
|-------|------|-------------|
| 1 | OUTBOX-BOUNDARY-REVIEW.0 | ✅ This document |
| 2 | OUTBOX-LIGHTWEIGHT-REDESIGN.0 | Design minimal schema additions (tenant_id, correlation_id) |
| 3 | EVENT-CONTRACT.0 | Define canonical event names and envelope |
| 4 | OUTBOX-RELAY-SPI.0 | Introduce relay abstraction |
| 5 | EVENT-SUBSCRIPTION-MODEL.0 | Move route config out of outbox |
| 6 | CAMEL-OUTBOX-RELAY-POC.0 | Use Camel for relay POC |
| 7 | WEBHOOK-DELIVERY-PROVIDER.0 | Implement webhook delivery |
| 8 | EVENTMESH-PROVIDER-SPIKE.0 | Evaluate after more services exist |
| 9 | APISIX-GATEWAY-EVALUATION.0 | Evaluate after API contracts stabilize |

**Migration principle:** Do not break existing event publication. Mark routing-related fields as legacy before removal.

---

## Risks

| Risk | Mitigation |
|------|------------|
| Over-engineering | Start with minimal schema additions |
| Introducing Camel too early | POC only, not production dependency |
| Losing transactional reliability | Keep outbox as DB-backed boundary |
| Coupling outbox to external systems | Use provider abstraction |
| Breaking existing consumers | Preserve Spring @EventListener path |
| Duplicate event delivery | Idempotency key already exists |

---

## Decisions Proposed

1. **Keep transactional outbox as platform-owned reliability boundary.**
2. **The current outbox is CLEAN** — no routing leakage found.
3. **The outbox module is MIXED** — job orchestration models should be separated.
4. **Move routing out of outbox** when external delivery is introduced.
5. **Introduce relay/router/provider concepts** before adding external integrations.
6. **Evaluate Camel as relay/integration runtime**, not core domain.
7. **Evaluate EventMesh as future event bus**, not transaction boundary.
8. **Evaluate APISIX as gateway**, not eventing layer.

---

## Open Questions

1. Should PlatformJob/PlatformTask be moved to a separate module?
2. Which events need tenant_id filtering?
3. Should CloudEvents be internal envelope or external only?
4. Is existing retry policy sufficient (global max-retries)?
5. What idempotency guarantees are needed for external delivery?
6. Should we add correlation_id/causation_id now or later?
