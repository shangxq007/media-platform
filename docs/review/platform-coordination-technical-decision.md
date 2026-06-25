---
status: technical-decision
created: 2026-06-24
scope: platform-wide (coordination + event architecture)
truth_level: confirmed
owner: platform
supersedes: platform-coordination-blueprint.md (design), platform-coordination-analysis.md (audit)
---

# Platform Coordination — Technical Decision Report

> **Decision Date:** 2026-06-24
> **Scope:** Event delivery, task coordination, workflow orchestration
> **Decision:** PostgreSQL-backed lightweight coordination
> **Rationale:** All state in PostgreSQL ACID. No external queue/client required. Migration path to Temporal/Kafka when scale demands.

---

## 1. Executive Summary

The platform needs to coordinate multi-step workflows (asset enrichment, search reindex, marketplace listing, review checks) across multiple services. We evaluated 8 technologies and chose a PostgreSQL-native approach.

### Why Not External Technologies

| Technology | Why Rejected (Now) |
|-----------|-------------------|
| **PGMQ** | Message queue doesn't solve coordination (fan-out/fan-in/barrier). Task table is purpose-built. |
| **LiteFlow** | Rule engine, not a coordination engine. May integrate later for business rules. |
| **Temporal** | Complex DAG workflows need separate Temporal server + workers. Overkill for current coordination needs. |
| **Kafka / RabbitMQ** | External message bus adds cluster management overhead. < 10 consumers don't justify it. |

### What We Chose

```
PostgreSQL-backed Lightweight Coordination:
  outbox_events       — domain events (immutable, append-only, at-least-once)
  platform_job        — coordination (fan-out/fan-in, barrier)
  platform_task       — task state (lease, retry, dead-letter)
  LISTEN/NOTIFY       — wake-up signal (optimization, not reliability)
  Spring consumers    — audit, notification, search, marketplace
```

---

## 2. Decision Matrix

| Criterion | Outbox Only | Outbox + Job/Task + NOTIFY | PGMQ | LiteFlow | Temporal | Kafka |
|-----------|------------|---------------------------|------|---------|----------|-------|
| **Reliability** | ✅ | ✅ | ✅ | ⚠️ Rule-dependent | ✅ | ✅ |
| **Observability** | ✅ Tables | ✅ Tables (5) | ⚠️ Queue ops | ⚠️ Logs | ✅ UI | ✅ UI |
| **Re-drive/Retry** | ⚠️ Manual | ✅ Lease + backoff | ✅ VT | ❌ No built-in | ✅ Policies | ✅ Offset |
| **Fan-out/Fan-in** | ❌ | ✅ Bitmask | ❌ | ❌ | ✅ Children | ❌ |
| **Ops Complexity** | Low | Low | Medium | Medium | High | High |
| **Migration Path** | Stay | → Temporal | Rip out | Rip out | Already there | Already there |

---

## 3. Role Assignments

### Outbox (outbox_events) — Domain Event Truth

```
Used for: "This happened" — business facts.
Examples: AssetPublished, ReviewApproved, TimelineMerged
Behavior: Append-only after publish. At-least-once delivery. Idempotent.
Consumers: Audit, Notification, Coordination trigger.
```

### platform_job / platform_task — Coordination Truth

```
Used for: "This needs coordination" — multi-step orchestration.
Examples: AssetEnrichmentJob, PublishPostProcessJob
Behavior: Fan-out into parallel tasks. Barrier on completion. Retry per task.
Producers: CoordinationConsumer (triggered by domain events).
Consumers: Task handlers (Probe, ASR, Search, Marketplace).
```

### LISTEN/NOTIFY — Wake-Up Signal

```
Used for: "There's work to do" — near-real-time dispatcher wake-up.
NOT reliability mechanism. Outbox guarantees delivery.
Complements 3s scheduled polling with < 100ms reaction time.
```

### Bitmask — Fast Barrier Check

```
Used for: "Are all tasks done?" — 1 integer comparison.
Derived from platform_task status. Not authoritative.
Authoritative state is in platform_task rows.
```

---

## 4. What Each Technology Does NOT Do

| Technology | Does NOT |
|-----------|---------|
| **Outbox** | Fan-out, fan-in, barrier, retry, lease, task state |
| **platform_job/task** | External message bus, event sourcing, Saga compensation |
| **LISTEN/NOTIFY** | Reliable delivery, persistence, queue semantics |
| **Bitmask** | Task retry tracking, detailed failure info |

---

## 5. Migration Path

### When to Introduce Temporal

```
Trigger conditions (any one):
  1. Workflows span multiple services with day-long waits
  2. Workflows need human-in-the-loop steps (approval, review)
  3. Workflows need Saga compensation (undo steps 1-2 if step 3 fails)
  4. Workflows have complex branching (conditional parallel branches)

Migration:
  platform_job/task → Temporal workflow + activities
  Outbox stays → events trigger Temporal workflows
  Task handlers → Temporal activity implementations
```

### When to Introduce Kafka

```
Trigger conditions (any one):
  1. External consumers need events (100+ webhook subscribers)
  2. Cross-service pub/sub at scale (> 10 consumers)
  3. Event retention for replay/audit exceeds PostgreSQL capacity

Migration:
  Outbox → Kafka adapter (read outbox_events, publish to Kafka topic)
  Spring consumers → Kafka consumer groups
  Outbox stays → internal event source (Kafka is derived)
```

### When to Introduce LiteFlow

```
Trigger conditions:
  1. Business rules become complex (multi-condition routing)
  2. Policy chains need visual editing (no-code policy builder)
  3. Rules change frequently (need hot-reload without deployment)

Integration:
  LiteFlow chains → evaluated by CoordinationConsumer before creating tasks
  LiteFlow does NOT replace platform_job/task
```

---

## 6. Truth Ownership Summary

| Truth Domain | Table | Immutable? | Rebuildable? |
|-------------|-------|-----------|-------------|
| **Business** | asset, timeline_snapshot, timeline_revision | No | No |
| **Event** | outbox_events | Yes (append-only) | No (historical record) |
| **Coordination** | platform_job, platform_task | No (status lifecycle) | Yes (derived from events) |
| **Notification** | notification_event, notification_delivery | Yes | Yes (from events) |
| **Audit** | audit_records | Yes | Yes (from events) |
| **Search** | indexes | No | Yes (from events + enrichment data) |

---

## 7. Related Documents

| Document | Relationship |
|----------|-------------|
| [Platform Coordination Blueprint](../architecture/blueprint/platform-coordination-blueprint.md) | Full coordination design |
| [Domain Event & Outbox Blueprint](../architecture/blueprint/domain-event-outbox-blueprint.md) | Event layer architecture |
| [Platform Coordination Analysis](platform-coordination-analysis.md) | Coordination gap analysis |
| [Domain Event & Outbox Audit](domain-event-outbox-audit.md) | Existing infrastructure audit |
| [Reference Architecture Map](../architecture/blueprint/reference-architecture-map.md) | §44-45 Coordination & Event references |
