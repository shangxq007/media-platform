---
status: implementation-report
created: 2026-06-24
scope: outbox-event-module + V1 baseline
truth_level: current
owner: platform
---

# Platform Foundation Sprint 019 — Generic Coordination Layer

## Motivation

### Why Outbox Doesn't Coordinate

The outbox delivers events reliably. It says "AssetPublished happened" and delivers to consumers. But it does NOT:
- Fan-out one event into multiple parallel tasks
- Wait for all tasks to complete (barrier/fan-in)
- Retry individual tasks with backoff
- Track per-task lease and recovery

### What Coordination Adds

```  
AssetPublished event
    ↓  
CoordinationConsumer creates platform_job (type: POST_PUBLISH)
    ↓  
Creates platform_task[SEARCH_REINDEX], platform_task[MARKETPLACE_PREPARE]
    ↓  
TaskDispatcher leases tasks → handlers execute
    ↓  
Barrier: both tasks complete → job COMPLETED → publish completion event
```  

## Schema Changes (2 new tables)

### `platform_job`

| Column | Type | Purpose |
|--------|------|---------|
| id | VARCHAR(64) PK | Job identifier |
| job_type | VARCHAR(64) | ASSET_ENRICHMENT, SEARCH_REINDEX, etc. |
| aggregate_type/aggregate_id | VARCHAR | What this job operates on |
| status | VARCHAR(32) | PENDING→RUNNING→COMPLETED/FAILED |
| required_mask | INT | Bitmask of required tasks |
| completed_mask | INT | Bitmask of completed tasks |
| failed_mask | INT | Bitmask of failed tasks |
| payload_json | TEXT | Context for task handlers |
| metadata_json | TEXT | Aggregated results |

### `platform_task`

| Column | Type | Purpose |
|--------|------|---------|
| id | VARCHAR(64) PK | Task identifier |
| job_id | VARCHAR(64) FK | Parent job |
| task_type/capability | VARCHAR | PROBE, ASR, REINDEX, etc. |
| status | VARCHAR(32) | PENDING→LEASED→RUNNING→COMPLETED/FAILED |
| attempt_count | INT | Retry tracking |
| max_attempts | INT | Default 3 |
| bit_position | INT | Which bit in the job mask |
| error_message | TEXT | Failure details |

## Domain Models (6 new)

| Model | Purpose |
|-------|---------|
| `PlatformJob` | Job record with `isBarrierSatisfied()`, `hasFailed()`, `withStatus()` |
| `PlatformTask` | Task record with `canRetry()`, `isLeasable()`, `markLeased/markCompleted/markFailed()` |
| `JobStatus` | PENDING, RUNNING, COMPLETED, FAILED, CANCELLED |
| `TaskStatus` | PENDING, LEASED, RUNNING, COMPLETED, FAILED |
| `JobType` | ASSET_ENRICHMENT, SEARCH_REINDEX, etc. |
| `TaskCapability` | PROBE, ASR, OCR, VISION, EMBEDDING, REINDEX, PACKAGE, VALIDATE |

## Services & Repositories (3 new)

| Component | Role |
|-----------|------|
| `PlatformJobRepository` | jOOQ CRUD: create, findById, updateMask, markCompleted, markFailed |
| `PlatformTaskRepository` | jOOQ CRUD: create, findById, listByJob, lease (optimistic lock), complete, fail |
| `PlatformCoordinationService` | Orchestration: createJob, createTask, leaseAndRun, completeTask (updates barrier), failTask (with retry check) |

## Bitmask Barrier

```java  
// Task completion updates the job mask atomically  
completeTask(taskId, resultRef):  
  complete task → | bitPosition to completedMask  
  evaluateBarrier: (completedMask & requiredMask) == requiredMask → job COMPLETED  

failTask(taskId):  
  if canRetry() → retry  
  else → | bitPosition to failedMask → job FAILED  
```  

**Design:** Bitmask is a fast summary (1 integer compare). Task rows are the authoritative state.

## Lease Protocol

```java  
lease(taskId):  
  UPDATE platform_task  
  SET status = 'LEASED', started_at = NOW()  
  WHERE id = :taskId AND status = 'PENDING'  
  // Returns true if exactly 1 row updated (optimistic lock)  
```  

No distributed lock needed. PostgreSQL's atomic `UPDATE ... WHERE` handles mutual exclusion.

## Retry Foundation

- `attemptCount` tracks attempts per task
- `maxAttempts` (default 3) caps retries
- `canRetry()` → `attemptCount < maxAttempts`
- `markFailed()` sets status to FAILED — job coordinator decides retry or dead-letter

## Tests (5 tests, all passing)

| Test | Scenario |
|------|----------|
| `PlatformJobTest.barrierSatisfied` | 0b111 required, 0b111 completed → satisfied |
| `PlatformJobTest.barrierNotSatisfied` | 0b111 required, 0b010 completed → not satisfied |
| `PlatformTaskTest.canRetry` | 1/3 attempts → true |
| `PlatformTaskTest.cannotRetry` | 3/3 attempts → false |
| `PlatformTaskTest.isLeasable` | PENDING status → true |

## Known Limitations

| Limitation | Status |
|-----------|--------|
| No LISTEN/NOTIFY wake-up | Uses scheduled polling (3s) |
| No task dispatcher | Task dispatch deferred to Sprint 020 |
| No Search consumer | Deferred to Sprint 021 |
| No Marketplace consumer | Deferred to Sprint 022 |
| `attempt_count` not auto-incremented on lease | Application-level increment deferred |

## Deferred Items

| Item | Sprint |
|------|--------|
| LISTEN/NOTIFY wake-up | Sprint 020 |
| Task dispatcher (polling worker) | Sprint 020 |
| Search consumer | Sprint 021 |
| Marketplace consumer | Sprint 022 |
| Temporal evaluation | Future |
