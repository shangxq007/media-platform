---
status: analysis
created: 2026-06-24
scope: platform-wide (coordination + workflow)
truth_level: current
owner: platform
---

# Platform Coordination Analysis

> **Analysis Date:** 2026-06-24
> **Method:** Outbox infrastructure audit + coordination gap analysis + queue/messaging assessment
> **Conclusion:** Outbox exists for 6 render event types. No coordination layer exists. LISTEN/NOTIFY is underutilized. PGMQ is unnecessary.

---

## 1. Coordination Gap Analysis

### What Exists

| Capability | Component | Status |
|-----------|-----------|--------|
| Reliable event delivery | `outbox_events` + `OutboxEventDispatcher` | ✅ 6 render event types |
| Notification dispatch | `NotificationEventHandler` | ✅ 6 render events |
| Audit trail | `AuditEventHandler` | ✅ 5 render events |
| Application event bus | Spring `ApplicationEventPublisher` | ✅ In-process |
| Render-internal event bus | `SystemEventBus` | ✅ In-memory (isolated) |

### What's Missing

| Gap | Impact |
|-----|--------|
| **No multi-step coordination** | Asset enrichment runs probe → ASR sequentially. No fan-out across providers. No fan-in after all complete. |
| **No job/task model** | Asset enrichment has no job tracking. If ASR fails, the whole process fails — no per-task retry. |
| **No barrier/aggregation** | When all enrichment tasks complete, there's no trigger for the next step (search reindex, notification). |
| **No task retry** | If ffprobe fails (transient), no automatic retry. Manual re-trigger only. |
| **No task leasing** | If dispatcher crashes mid-task, task is lost. No lease-based recovery. |
| **No LISTEN/NOTIFY usage** | Dispatchers poll on schedule (3s). No real-time wake-up. |
| **No cross-domain workflow** | AssetPublished → search reindex → marketplace listing → notification — no coordination between these domains. |
| **12 un-routed shared events** | Quota, billing, cost events have no outbox dispatch. Not a coordination issue; an outbox gap. |

---

## 2. LISTEN/NOTIFY Assessment

### Current Usage: None

No LISTEN/NOTIFY usage found anywhere in the codebase. All dispatchers and consumers use scheduled polling.

### Recommended Role: Wake-Up Signal

| Scenario | Recommended | Why |
|----------|------------|-----|
| Task ready to dispatch | ✅ Use LISTEN/NOTIFY | NOTIFY on task insert → wake dispatcher immediately (vs 3s poll delay) |
| Job barrier breached | ✅ Use LISTEN/NOTIFY | NOTIFY when job completes → consumers react in near-real-time |
| Reliable delivery | ❌ Don't use | NOTIFY doesn't survive crashes. Outbox does. |
| Queue replacement | ❌ Don't use | NOTIFY is fire-and-forget. No persistence, no retry, no ordering guarantees. |
| Metrics/analytics | ❌ Don't use | Use a dedicated metrics table. NOTIFY can't accumulate or aggregate. |

### Channel Design

```
channel: platform_task_pending → TaskDispatcher wakes up
channel: platform_job_completed → CoordinationConsumer wakes up
channel: platform_task_failed → AlertConsumer wakes up
```

### Failure Mode

If a listener misses a NOTIFY (crashed, disconnected), the dispatcher's scheduled polling (3s) catches up. LISTEN/NOTIFY is an optimization, not a reliability mechanism.

---

## 3. PGMQ Assessment

### What PGMQ Provides

PGMQ is a PostgreSQL extension implementing AMQP-like message queues in PostgreSQL:
- Named queues with FIFO ordering
- Visibility timeout (prevent double-processing)
- Message archiving and deletion
- Partitioned queues by key

### Why We Don't Need It

| Need | PGMQ Approach | Our Approach | Better? |
|------|--------------|-------------|---------|
| Reliable delivery | Enqueue → dequeue → archive | Outbox (write in transaction → poll → markProcessed) | Outbox — simpler, battle-tested |
| Task coordination | Enqueue tasks → workers dequeue | platform_task table with status lifecycle | Task table — richer state model |
| Fan-out/fan-in | Multiple queues + manual barrier logic | platform_job bitmask + barrier check | Job mask — built-in, fast |
| Retry/Dead-letter | Visibility timeout + DLQ queue | task.attempt_count + max_attempts + FAILED status | Task row — more granular |

**Decision: PGMQ adds dependency without solving our coordination problem.** Our problem is NOT message queuing — it's multi-step coordination. The `platform_job` + `platform_task` pattern is purpose-built for coordination. PGMQ would be a workaround.

---

## 4. Recovery Strategy

### Crash Scenarios

| Crash At | Recovery |
|----------|----------|
| Dispatcher picks task, crashes before execution | Task has `status = IN_PROGRESS` + `started_at`. Periodic sweep resets IN_PROGRESS tasks with expired lease. |
| Task handler executes, crashes mid-work | `attempt_count++`. If < `max_attempts`, retry. Task result is lost; re-execute. |
| PostgreSQL crashes | WAL recovery restores all job/task rows. Dispatchers restart with polling. |
| Job stuck (all tasks complete but barrier not triggered) | Periodic reconciliation: SELECT jobs WHERE status = 'IN_PROGRESS' AND all tasks COMPLETED → re-evaluate barrier. |

### Lease Design

```
LEASE_DURATION = 5 minutes (configurable)

Dispatch:
  UPDATE platform_task
  SET status = 'IN_PROGRESS', started_at = NOW(), attempt_count = attempt_count + 1
  WHERE id = :taskId AND status = 'PENDING'
    AND (started_at IS NULL OR started_at < NOW() - INTERVAL '5 minutes')

Lease check (periodic, every 1 minute):
  UPDATE platform_task
  SET status = 'PENDING', started_at = NULL
  WHERE status = 'IN_PROGRESS'
    AND started_at < NOW() - INTERVAL '5 minutes'
    AND attempt_count < max_attempts

Dead-letter (lease check variant):
  UPDATE platform_task
  SET status = 'FAILED'
  WHERE status = 'IN_PROGRESS'
    AND started_at < NOW() - INTERVAL '5 minutes'
    AND attempt_count >= max_attempts
```

---

## 5. Bitmask vs Explicit State

### Comparison

| Criterion | Bitmask Only | Task Table Only | Bitmask + Task Table |
|-----------|-------------|-----------------|---------------------|
| Completion check speed | Fast (1 int compare) | Slow (COUNT query) | Fast (mask compare) |
| Individual task state | No | Yes | Yes |
| Retry per task | No | Yes | Yes |
| Max parallel tasks | 32 (int bit width) | Unlimited | 32 (mask) + unlimited via multiple jobs |
| Crash recovery | Lost (mask is derived) | Recoverable | Recoverable (tasks are source of truth) |
| Complexity | Low | Medium | Medium |

### Design Decision

**Use bitmask for fast barrier check. Use task table for granular state.**

The bitmask is updated atomically with the task status:

```
-- Single transaction
BEGIN;
UPDATE platform_task SET status = 'COMPLETED', completed_at = NOW()
  WHERE id = :taskId;
UPDATE platform_job SET completed_mask = completed_mask | :bitMask
  WHERE id = :jobId;
-- If completedMask == requiredMask, update job status
COMMIT;
```

The task table is the authoritative source. The bitmask is a cached summary for fast checks.

---

## 6. Future Evolution — What Changes

| Component | Phase 1-3 (This Blueprint) | Phase 4+ (If Scale Demands) |
|-----------|---------------------------|---------------------------|
| **Job/Task model** | `platform_job` + `platform_task` in PostgreSQL | Temporal for DAG-style workflows with long waits |
| **Wake-up signal** | PostgreSQL LISTEN/NOTIFY | Kafka for cross-service pub/sub |
| **Reliable delivery** | Outbox (stays forever) | Outbox + Kafka adapter for external consumers |
| **Task dispatch** | Polling with lease (PostgreSQL `UPDATE ... WHERE`) | Temporal activity workers |
| **Retry policy** | `attempt_count` + `max_attempts` in task row | Temporal retry policies (exponential, custom) |
| **Render farm** | Not applicable | OpenCue (50+ machines, frame-level scheduling) |

### When Each Technology Becomes Necessary

| Technology | Trigger Condition |
|-----------|------------------|
| **Temporal** | Workflows span multiple services with human-in-the-loop steps OR workflows need Saga compensation |
| **Kafka** | Need to fan-out events to 100+ external consumers OR need exactly-once cross-service semantics |
| **OpenCue** | Render farm with 50+ machines, 1000+ concurrent jobs, frame-level scheduling |
| **ElasticSearch** | Full-text search across transcripts, OCR text, entity names (100K+ assets) |
| **Vector DB** | Semantic/visual similarity search at scale (1M+ embeddings) |

---

## 7. Roadmap

| Priority | Capability | Phase |
|----------|-----------|-------|
| P0 | Coordination architecture (this blueprint) | Now |
| P1 | Timeline + Review domain events (wire outbox) | Sprint 012 |
| P2 | Asset domain events (wire outbox) | Sprint 013 |
| P3 | platform_job + platform_task tables + JobCoordinator + TaskDispatcher | Sprint 014 |
| P4 | LISTEN/NOTIFY wake-up optimization | Sprint 015 |
| P5 | Search consumer, marketplace consumer | Sprint 016 |
| P6 | External event bus | 2027+ |

---

## 8. Related Documents

| Document | Relationship |
|----------|-------------|
| [Platform Coordination Blueprint](../architecture/blueprint/platform-coordination-blueprint.md) | Main blueprint |
| [Domain Event & Outbox Blueprint](../architecture/blueprint/domain-event-outbox-blueprint.md) | Event layer |
| [Domain Event & Outbox Audit](domain-event-outbox-audit.md) | Existing infrastructure audit |
