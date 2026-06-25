---
status: blueprint
created: 2026-06-24
scope: platform-wide (coordination layer)
truth_level: target
owner: platform
---

# Platform Coordination Blueprint

> **Reality Check (2026-06-24):** Outbox infrastructure exists (`outbox_events`, `OutboxEventService`, `OutboxEventDispatcher`) with 6 render event types. Notification and audit consumers exist. But no coordination layer exists between domain events and multi-step workflows. Asset enrichment (probe → ASR → OCR → vision → embedding) needs fan-out/fan-in. Search reindex needs barrier. Marketplace listing needs multi-check validation. This blueprint defines the coordination model that bridges events and workflows.

---

## 1. Executive Summary

### The Coordination Gap

Outbox = reliable event delivery. It says "AssetPublished happened" and delivers to consumers. But it doesn't coordinate multi-step processes:

```
AssetPublished event fires
  → Need to: reindex search (step 1)
  → Need to: create marketplace listing (step 2)
  → Need to: update analytics (step 3)
  → When all 3 complete: mark asset as "fully published"
  → If any step fails: retry that step, not the whole flow
  → If step 2 fails after 3 retries: dead-letter, continue steps 1 and 3
```

The outbox delivers events. The coordination layer orchestrates what happens after delivery.

### Design Principles

1. **Event-driven, not workflow-engine-driven** — Domain events trigger coordination. Coordination doesn't replace Temporal.
2. **PostgreSQL-native** — Jobs, tasks, barriers, and retry all live in PostgreSQL. No external queue (Kafka/RabbitMQ) needed for coordination.
3. **Generic job/task model** — One `platform_job` table, not one per domain. Domain-specific logic lives in task handlers.
4. **Bitmask for fast fan-in, task table for granular retry** — Bitmask tracks completion fast. Task rows track individual failures for recovery.
5. **LISTEN/NOTIFY for wake-up, NOT for reliability** — PostgreSQL NOTIFY wakes dispatchers. Outbox guarantees delivery.

---

## 2. Coordination Architecture

### Layered Model

```
┌─────────────────────────────────────────────────┐
│ Domain Services (Timeline, Review, Asset)        │  → publish domain events
├─────────────────────────────────────────────────┤
│ Outbox (outbox_events, OutboxEventDispatcher)    │  → reliable delivery
├─────────────────────────────────────────────────┤
│ Coordination Layer (platform_job, platform_task) │  → orchestrate multi-step
├─────────────────────────────────────────────────┤
│ Task Handlers (Probe, ASR, OCR, Search, Market)  │  → execute individual tasks
├─────────────────────────────────────────────────┤
│ Consumers (Audit, Notification)                  │  → side effects
└─────────────────────────────────────────────────┘
```

### How It Flows

```
AssetPublished event
    │
    ▼
CoordinationConsumer.onAssetPublished()
    │
    ▼
creates platform_job {
  jobType: "ASSET_PUBLISH_POST_PROCESSING",
  requiredMask: PROBE_WORKFLOW = bit 0 | SEARCH_REINDEX = bit 1 | MARKETPLACE_LISTING = bit 2
  completedMask: 0b000
}
    │
    ▼
dispatches platform_task[]:
  task A: type = SEARCH_REINDEX, capability = SEARCH
  task B: type = MARKETPLACE_LISTING, capability = MARKETPLACE
    │
    ├── Task A completes → completedMask |= bit 1 → 0b010
    ├── Task B completes → completedMask |= bit 2 → 0b011
    │
    ▼
completedMask == requiredMask → job COMPLETED
    │
    ▼
If all tasks OK: publish AssetPostProcessed event
If any task FAILED after all complete: move failed task to dead-letter, continue
```

---

## 3. Generic Job Model

### `platform_job` Table

```sql
create table platform_job (
    id varchar(64) primary key,
    job_type varchar(64) not null,          -- "asset.enrichment", "asset.publish.post_process"
    aggregate_type varchar(32) not null,    -- "ASSET", "TIMELINE", "REVIEW"
    aggregate_id varchar(64) not null,      -- "asset_789"
    tenant_id varchar(64),
    project_id varchar(64),
    status varchar(32) not null default 'PENDING',
    required_mask int not null default 0,   -- bitmask: which tasks must complete
    completed_mask int not null default 0,  -- bitmask: which tasks are done
    failed_mask int not null default 0,     -- bitmask: which tasks failed
    payload_json text,                      -- context for task handlers
    metadata_json text,                     -- result aggregation
    total_task_count int not null default 0,
    completed_task_count int not null default 0,
    failed_task_count int not null default 0,
    created_at timestamp not null,
    updated_at timestamp,
    completed_at timestamp
);
```

### Status Transitions

```
PENDING → IN_PROGRESS → COMPLETED
                     → FAILED (if critical tasks fail)
                     → PARTIAL (non-critical tasks failed)
```

### Job Types

| jobType | Trigger | Tasks |
|---------|---------|-------|
| `asset.enrichment` | Asset registered | probe, asr, ocr, vision, embedding |
| `asset.publish.post_process` | Asset published | search_reindex, marketplace_listing, analytics_update |
| `marketplace.listing.validation` | Listing submitted | license_check, quality_review, governance_approval |
| `timeline.snapshot.analysis` | Timeline snapshot created | diff_computation, impact_analysis, cache_warm |

---

## 4. Generic Task Model

### `platform_task` Table

```sql
create table platform_task (
    id varchar(64) primary key,
    job_id varchar(64) not null references platform_job(id),
    task_type varchar(64) not null,         -- "probe", "asr", "search_reindex"
    capability varchar(64) not null,        -- "PROBE", "ASR", "SEARCH", "MARKETPLACE"
    provider varchar(64),                   -- "ffprobe", "whisper-v3", "pg-fts"
    status varchar(32) not null default 'PENDING',
    attempt_count int not null default 0,
    max_attempts int not null default 3,
    result_ref varchar(256),                -- reference to task output
    result_json text,
    error_message text,
    bit_position int not null,              -- which bit in the job mask (0-31)
    started_at timestamp,
    completed_at timestamp,
    constraint uq_job_bit unique(job_id, bit_position)
);

create index ix_pt_job_status on platform_task(job_id, status);
create index ix_pt_capability_status on platform_task(capability, status);
```

### Why Task is the Source of Truth

- **Task has `bit_position`** — The bitmask on `platform_job` is a fast-summary optimization. The authoritative state is in the task rows.
- **Task has `attempt_count` and `error_message`** — Individual task retry and failure tracking lives here. The job's `failed_mask` is derived from task status.
- **Task has `result_ref`** — Each task produces a result. The job's `metadata_json` aggregates results from all tasks.

### Task Responsibilities

| Field | Purpose |
|-------|---------|
| `bit_position` | Map task to a bit in the job mask (0-31, allowing up to 32 parallel tasks) |
| `capability` | Route task to the correct handler (PROBE, ASR, SEARCH) |
| `provider` | Optional provider override (e.g., "whisper-v3" vs default) |
| `result_ref` | Reference to the task's output (e.g., transcript ID, search index version) |

---

## 5. Bitmask Design

### Why Bitmask

```
Problem: "Are all 5 enrichment tasks complete?"
Solution 1: SELECT COUNT(*) WHERE status = 'COMPLETED' → 5 queries, race conditions
Solution 2: completedMask & requiredMask == requiredMask → 1 fast integer comparison
```

### Bit Assignment

| Bit | Asset Enrichment | Asset Publish Post-Process |
|-----|-----------------|---------------------------|
| 0 | PROBE | SEARCH_REINDEX |
| 1 | ASR | MARKETPLACE_LISTING |
| 2 | OCR | ANALYTICS_UPDATE |
| 3 | VISION | NOTIFICATION_DISPATCH |
| 4 | EMBEDDING | (reserved) |

### Bitmask Update Protocol

```
Task completes:
  UPDATE platform_task SET status = 'COMPLETED'
  UPDATE platform_job SET completed_mask = completed_mask | (1 << task.bit_position)
  IF completed_mask == required_mask → UPDATE platform_job SET status = 'COMPLETED'

Task fails (after max attempts):
  UPDATE platform_task SET status = 'FAILED'
  UPDATE platform_job SET failed_mask = failed_mask | (1 << task.bit_position)
  IF task IS critical → UPDATE platform_job SET status = 'FAILED'
  ELSE → job continues with remaining tasks
```

### Design Decision: Bitmask + Task Table

| Approach | Pros | Cons |
|----------|------|------|
| **Bitmask only** | Fast completion check (1 integer compare) | No individual task state, no retry tracking, limited to 32 tasks |
| **Task table only** | Full task state, unlimited tasks, detailed retry | Slower completion check (COUNT + conditions) |
| **Bitmask + Task table** | Fast check via bitmask, detailed state via tasks | Must keep them in sync |

**Recommendation: Bitmask + Task table.** Bitmask is a fast summary. Task table is the source of truth. The job coordinator updates both atomically.

---

## 6. Barrier / Aggregation Model

### Design Decision: Inline in Job, Not Separate Table

A barrier is the condition `completedMask == requiredMask`. This is already expressed in the `platform_job` row:

```
Barrier Condition:    completedMask & requiredMask == requiredMask
Aggregation Trigger:  When last task completes AND this condition is true
Join Point:           The job coordinator checks this after every task completion
```

No separate barrier/aggregation table needed. The job itself IS the barrier.

### Fan-Out → Fan-In

```
Fan-Out:
  JobCoordinator creates platform_task[] rows (one per enrichment type)
  TaskDispatcher picks up PENDING tasks by capability

Fan-In:
  Each task completion updates platform_job.completedMask
  When completedMask == requiredMask → barrier breached
  JobCoordinator aggregates task results → metadataJson
  JobCoordinator publishes completion event
```

---

## 7. LISTEN / NOTIFY Assessment

### Role: Wake-Up Signal, Not Reliable Queue

```
PostgreSQL LISTEN/NOTIFY is a WAKE-UP signal.
It tells dispatchers "there's work to do."
It does NOT guarantee delivery — the outbox does that.
```

### Scenarios

| Scenario | Use LISTEN/NOTIFY? | Why |
|----------|-------------------|-----|
| **Task ready to dispatch** | ✅ Yes | Task inserted → `NOTIFY platform_task_pending` → dispatcher wakes up |
| **Job completed** | ✅ Yes | Barrier breached → `NOTIFY platform_job_completed` → consumers wake up |
| **Reliable event delivery** | ❌ No | Use outbox_events. NOTIFY doesn't survive crashes. |
| **Cross-service communication** | ❌ No | Use domain events via outbox. NOTIFY is process-internal. |
| **Metrics/counters** | ❌ No | Poll or use dedicated metrics table. NOTIFY can't accumulate. |

### Architecture

```
Task insert → NOTIFY platform_task_pending
    ↓
TaskDispatcher.listen() → wakes up → SELECT * FROM platform_task WHERE status = 'PENDING'
    ↓
Dispatches to correct capability handler
```

The NOTIFY is a hint. The actual work retrieval is a `SELECT`. This is crash-safe — if the dispatcher crashes after NOTIFY, it polls on restart.

---

## 8. PGMQ Assessment

### What PGMQ Provides

PGMQ (Postgres Message Queue) is a PostgreSQL extension that implements a message queue in PostgreSQL:
- `pgmq.create(queue_name)` — create a named queue
- `pgmq.send(queue_name, message)` — enqueue
- `pgmq.read(queue_name, vt, limit)` — dequeue with visibility timeout
- `pgmq.archive(queue_name, msg_id)` — archive processed message
- `pgmq.delete(queue_name, msg_id)` — delete processed message

### Comparison

| Capability | platform_task Table | PGMQ | Recommendation |
|-----------|-------------------|------|----------------|
| **Queue semantic** | Task rows with status | Named queues with vt | Use task table — status is richer than enqueue/dequeue |
| **Visibility timeout** | `started_at` + timeout check | Built-in `vt` | Task table with custom timeout logic |
| **Retry + dead-letter** | `attempt_count` + `max_attempts` + `FAILED` status | Must build on top | Task table — built-in retry model |
| **Ordering** | Indexed by status | FIFO by default | Either works |
| **Fan-out/fan-in** | Job mask + barrier check | Not supported | Task table — native barrier support |
| **Queryability** | Full SQL on task state | Limited to queue ops | Task table — join with jobs, aggregate, etc. |
| **External dependency** | None (PostgreSQL table) | Requires pgmq extension | Task table — no extension needed |

### Recommendation: **Do NOT adopt PGMQ. Use platform_task table.**

PGMQ is a message queue. Our coordination problem is NOT queueing — it's coordination (fan-out, fan-in, barrier, retry, aggregation). The `platform_task` table with bitmask-based completion tracking solves this more elegantly than a message queue.

---

## 9. Recovery Model

### Crash Scenarios

| Scenario | Recovery Strategy |
|----------|------------------|
| **Dispatcher crashes mid-task** | Task has `started_at` + `status = IN_PROGRESS`. On restart, scan for IN_PROGRESS tasks with `started_at` > timeout → reset to PENDING. |
| **Task handler crashes** | Task `attempt_count++`. If `< max_attempts`, retry. If `== max_attempts`, mark FAILED, update job `failed_mask`. |
| **PostgreSQL crashes** | All state is in PostgreSQL WAL. On recovery, `platform_task` and `platform_job` rows are intact. Dispatchers poll from last known state. |
| **Job stuck in IN_PROGRESS** | Periodic sweep: SELECT jobs WHERE status = 'IN_PROGRESS' AND updated_at < NOW() - INTERVAL '1 hour'. Check if all tasks are COMPLETED/FAILED → re-evaluate barrier. |

### Lease-Based Task Dispatch

```
Task dispatch (optimistic lock):
  UPDATE platform_task
  SET status = 'IN_PROGRESS', started_at = NOW()
  WHERE id = :taskId AND status = 'PENDING' AND (started_at IS NULL OR started_at < NOW() - INTERVAL '5 minutes')

If rows_updated == 0 → another dispatcher took it → skip
If rows_updated == 1 → we own this task → execute
```

### Retry Strategy

```
attempt 1: immediate retry
attempt 2: delay 5s
attempt 3: delay 30s
attempt 4+ (if max_attempts > 3): delay 5min, exponential backoff
max_attempts reached: status = 'FAILED' → dead-letter
```

### Dead-Letter Recovery

```
Recovery API:
  POST /api/v1/jobs/{jobId}/tasks/{taskId}/retry  → reset status to PENDING, reset attempt_count
  POST /api/v1/jobs/{jobId}/retry-failed          → retry all FAILED tasks
  POST /api/v1/jobs/{jobId}/skip-failed           → mark FAILED tasks as SKIPPED, re-evaluate barrier
```

---

## 10. Domain Event Integration

### Which Is Which

| Layer | Examples | Persistence | Reliability |
|-------|----------|-------------|-------------|
| **Domain Event** | AssetPublished, ReviewApproved, TimelineMerged | `outbox_events` table | At-least-once via outbox |
| **Internal Workflow State** | platform_job row, platform_task rows, bitmask transitions | `platform_job`, `platform_task` tables | PostgreSQL (ACID) |

### Flow

```
1. Domain service publishes AssetPublishedEvent (via OutboxEventService)
2. CoordinationConsumer.onAssetPublished()
     → creates platform_job (type: ASSET_PUBLISH_POST_PROCESS)
     → creates platform_task[] (search_reindex, marketplace_listing, analytics)
3. TaskDispatcher picks up PENDING tasks → routes to capability handlers
4. Task handlers execute → update platform_task status + platform_job mask
5. Barrier breached (completedMask == requiredMask)
6. JobCoordinator publishes AssetPostProcessCompletedEvent (via OutboxEventService)
7. Consumers (Audit, Notification) react to completion event
```

### Boundary

```
Domain Events (outbox):
  ✓ AssetPublished
  ✓ AssetPostProcessCompleted
  ✗ TaskStarted (internal)
  ✗ TaskRetried (internal)

Internal Workflow State (platform_job/task):
  ✓ Task STARTED/COMPLETED/FAILED
  ✓ Job mask transitions
  ✗ Business meaning (delegate to domain events)
```

---

## 11. Future Evolution

### What Gets Replaced, What Stays

| Component | Phase 1-3 (This Blueprint) | Phase 4+ (Future) |
|-----------|---------------------------|-------------------|
| **Job/Task coordination** | `platform_job` + `platform_task` tables | Temporal for complex DAG workflows |
| **Fan-out/fan-in** | Bitmask + barrier check | Temporal child workflows |
| **Retry/Recovery** | PostgreSQL lease + exponential backoff | Temporal retry policies |
| **Notification (wake-up)** | LISTEN/NOTIFY | Kafka for cross-service communication |
| **Reliable delivery** | Outbox (stays forever) | Outbox + Kafka adapter |
| **Render farm scheduling** | Not applicable to coordination | OpenCue for 50+ machine farms |

### When to Introduce Temporal

Temporal should be introduced when:
1. Workflows span **multiple services** with **long-running steps** (hours/days)
2. Workflows need **human-in-the-loop** approvals with **arbitrary wait times**
3. Workflows have **complex branching** (conditional paths, parallel branches with joins)
4. Workflows need **compensation** (Saga pattern: if step 3 fails, undo steps 1 and 2)

Until then, `platform_job` + `platform_task` + outbox is sufficient.

---

## 12. Implementation Roadmap

| Phase | Capability | Timeline |
|-------|-----------|----------|
| **Phase 1** | Coordination Blueprint (this document) | 2026-06 |
| **Phase 2** | Timeline + Review domain events (wire existing outbox) | Sprint 012 |
| **Phase 3** | Asset domain events (wire existing outbox) | Sprint 013 |
| **Phase 4** | `platform_job` + `platform_task` tables + JobCoordinator + TaskDispatcher | Sprint 014+ |
| **Phase 5** | Search consumer, notification consumer, audit consumer | Sprint 015+ |
| **Phase 6** | LISTEN/NOTIFY-based dispatcher wake-up | Sprint 016+ |
| **Phase 7** | Temporal for complex workflows (if needed) | 2027+ |
| **Phase 8** | Kafka/RabbitMQ for external event bus | 2027+ |
| **Phase 9** | OpenCue for render farm scheduling | 2028+ |

---

## 13. Related Documents

| Document | Relationship |
|----------|-------------|
| [Domain Event & Outbox Blueprint](domain-event-outbox-blueprint.md) | Event layer — coordination sits above this |
| [Platform Coordination Analysis](../../review/platform-coordination-analysis.md) | Detailed audit + gap analysis |
| [OTIO Render Platform Blueprint](otio-render-platform-blueprint.md) | Platform architecture overview |
| [Asset Ecosystem Blueprint](asset-ecosystem-blueprint.md) | Asset enrichment workflow design |
| [Reference Architecture Map](reference-architecture-map.md) | External coordination references |
| [Platform Coordination Technical Decision](../../review/platform-coordination-technical-decision.md) | Decision record |

---

## 14. Technical Decision Summary

### Why PostgreSQL-Native Coordination

| Technology | Assessed | Decision | Rationale |
|-----------|---------|----------|-----------|
| **Outbox (outbox_events)** | ✅ Used | Domain event source of truth | Reliable at-least-once delivery, crash-safe, idempotent |
| **platform_job / platform_task** | ✅ New | Coordination source of truth | Fan-out/fan-in, barrier, retry, lease — all in PostgreSQL ACID |
| **LISTEN/NOTIFY** | ✅ Optimization | Wake-up signal only | Fast dispatch wake-up. NOT reliable delivery. |
| **Bitmask** | ✅ Used | Fast barrier check cache | 1-int compare for completion. Task table is the authoritative state. |
| **PGMQ** | ❌ Rejected | Not needed | Message queue semantic doesn't solve coordination (fan-out/fan-in/barrier). Task table is purpose-built. |
| **LiteFlow** | ❌ Rejected | Not needed now | Local policy/routing engine. Not a coordination engine. May integrate later for business rules. |
| **Temporal** | ❌ Deferred | Not needed now | Complex DAG workflows with days-long waits. Trigger: 2027+ when we have human-in-the-loop + Saga patterns. |
| **Kafka / RabbitMQ** | ❌ Deferred | Not needed now | External event bus for 100+ consumers. Trigger: when outbox consumers exceed internal Spring event capacity. |

### Key Distinction: Domain Event vs Platform Task

```
Domain Event (outbox_events):
  "This happened."
  Examples: AssetPublished, ReviewApproved, TimelineMerged
  Delivered via outbox (at-least-once)
  Consumed by: Audit, Notification, coordination triggers

Platform Task (platform_task):
  "This needs to be done."
  Examples: RunASR, ReindexSearch, CreateMarketplaceListing
  Executed via task handlers (leased execution, retry, dead-letter)
  Produced by: JobCoordinator in response to domain events
```

### Why `platform_job` / `platform_task` (Generic Naming)

**Problem:** Domain-specific tables (`asset_enrichment_job`, `asset_enrichment_task`) create schema sprawl. Each new workflow requires new tables, new repositories, new controllers.

**Solution:** One generic `platform_job` + `platform_task` table pair. Domain-specific logic lives in task handlers, not in the schema.

Future reuse:
- Asset Enrichment (probe → ASR → OCR → vision → embedding)
- Search Reindex (full-text update, vector reindex, cache invalidation)
- Marketplace Listing (license check, quality review, governance approval)
- Review Publish Checks (merge guard, approval count, conflict check)
- Webhook Delivery (retry, timeout, signature verification)
- Render Preflight (cache check, asset resolution, quota verification)
- Template Packaging (validate, bundle, version, publish)

### Truth Ownership

| Truth Domain | Table(s) | Owner | Mutability |
|-------------|----------|-------|-----------|
| **Business** | asset, timeline_snapshot, timeline_revision | Domain services | Mutable (business operations) |
| **Event** | outbox_events | OutboxEventService | Append-only (immutable after publish) |
| **Coordination** | platform_job, platform_task | JobCoordinator, TaskDispatcher | Status lifecycle (PENDING → IN_PROGRESS → COMPLETED/FAILED) |
| **Notification** | notification_event, notification_delivery | NotificationEventHandler | Append-only |
| **Audit** | audit_records | AuditEventHandler | Append-only |
| **Search** | search indexes + rebuildable projections | SearchIndexConsumer | Rebuildable from domain events |

---

## 15. Re-drive and Recovery Model

### Worker Crash

```
Scenario: Task handler picks up task, starts ASR processing, process crashes.

Recovery:
  Task has status = IN_PROGRESS + started_at = T.
  Periodic lease sweep (every 60s):
    UPDATE platform_task
    SET status = 'PENDING', started_at = NULL
    WHERE status = 'IN_PROGRESS'
      AND started_at < NOW() - INTERVAL '5 minutes'
      AND attempt_count < max_attempts
  Result: Task re-dispatched to another handler. attempt_count++.
```

### Dispatcher Crash

```
Scenario: TaskDispatcher crashes while holding in-memory task queue.

Recovery:
  All state is in PostgreSQL (platform_task rows).
  On restart, dispatcher polls: SELECT * FROM platform_task WHERE status = 'PENDING'.
  No in-memory state lost.
```

### Partial Task Failure

```
Scenario: Job has 5 tasks. 3 complete, 1 fails (transient), 1 fails (permanent).

Recovery:
  Transient failure: attempt_count++. Retry with exponential backoff.
  Permanent failure (max_attempts reached):
    SET status = 'FAILED'.
    UPDATE platform_job SET failed_mask = failed_mask | bitMask.
    IF task IS critical → job FAILED.
    IF task IS non-critical → job continues. Partial completion possible.
    Failed task available for manual retry via API.
```

### Stuck Job Reconciliation

```
Scenario: All tasks show COMPLETED/FAILED but job status is still IN_PROGRESS.

Recovery:
  Periodic reconciliation (every 5 minutes):
    SELECT j.* FROM platform_job j
    WHERE j.status = 'IN_PROGRESS'
      AND (
        SELECT COUNT(*) FROM platform_task t
        WHERE t.job_id = j.id AND t.status NOT IN ('COMPLETED', 'FAILED', 'SKIPPED')
      ) = 0
    → re-evaluate barrier condition → update job status → publish completion event.
```
