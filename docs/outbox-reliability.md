# Outbox Reliability Guide

## Overview

The outbox-event-module implements the [Transactional Outbox](https://microservices.io/patterns/data/transactional-outbox.html) pattern with full reliability guarantees: exactly-once processing semantics via idempotency keys, exponential backoff retry, dead letter handling, and concurrency-safe dispatch via `SELECT FOR UPDATE`.

## Status Machine

```
                    ┌──────────┐
                    │ PENDING  │
                    └────┬─────┘
                         │ lockForProcessing()
                         ▼
                    ┌────────────┐
            ┌──────│ PROCESSING │──────┐
            │      └────────────┘      │
            │ success                  │ failure
            ▼                          ▼
     ┌────────────┐           ┌──────────────┐
     │ PROCESSED  │           │   FAILED     │
     └────────────┘           └──────┬───────┘
                                     │ retryCount < maxRetries
                                     │ (exponential backoff)
                                     │
                              resetDueFailedEvents()
                                     │
                                     ▼
                               ┌──────────┐
                               │ PENDING  │ (re-queued)
                               └──────────┘
                                     │
                                     │ retryCount >= maxRetries
                                     ▼
                               ┌─────────────┐
                               │ DEAD_LETTER │
                               └─────────────┘
```

### Status Descriptions

| Status | Meaning |
|--------|---------|
| `PENDING` | Event is waiting to be dispatched. |
| `PROCESSING` | Event is currently being processed (row is locked). |
| `PROCESSED` | Event was successfully dispatched. Terminal state. |
| `FAILED` | Event failed dispatch; will be retried after `next_attempt_at`. |
| `DEAD_LETTER` | Event exceeded `maxRetries`; no more attempts. Terminal state. |

## Retry Strategy

### Exponential Backoff

When an event fails, the next attempt is scheduled using exponential backoff:

```
nextAttemptAt = now + (baseDelay × 2^retryCount)
```

With the default `baseDelay = 1000ms`:

| Retry Count | Backoff |
|-------------|---------|
| 1 | 2 seconds |
| 2 | 4 seconds |
| 3 | 8 seconds |

The default `maxRetries` is 3. After 3 failures, the event moves to `DEAD_LETTER`.

### Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `app.outbox.max-retries` | 3 | Maximum retry attempts per event |
| `app.outbox.dispatch-interval-ms` | 3000 | Interval for scheduled dispatch (ms) |

## Idempotency Guarantees

### Append-Level Idempotency

When `appendEvent()` is called with an `idempotencyKey`:

1. **Key exists + PROCESSED** → returns existing event ID (no duplicate)
2. **Key exists + PENDING/FAILED** → updates payload, resets to PENDING, returns existing ID
3. **Key exists + PROCESSING/DEAD_LETTER** → returns existing ID (no modification)
4. **Key does not exist** → creates new event

### Handler-Level Idempotency

`OutboxBackedNotificationEventPublisher` generates deterministic idempotency keys for each event type:

| Event Type | Idempotency Key Pattern |
|------------|------------------------|
| `render.job.created` | `render.job.created:{renderJobId}` |
| `render.job.status.changed` | `render.job.status.changed:{renderJobId}:{oldStatus}:{newStatus}` |
| `render.job.completed` | `render.job.completed:{renderJobId}` |
| `render.job.failed` | `render.job.failed:{renderJobId}` |
| `artifact.created` | `artifact.created:{artifactId}` |

This ensures that even if the publisher is called multiple times with the same logical event, only one outbox event is created/dispatched.

## Concurrency Safety

### SELECT FOR UPDATE

`lockForProcessing()` uses `SELECT ... FOR UPDATE` to lock the row before dispatch. This prevents:

- Two dispatcher instances from processing the same event
- A scheduled dispatch and a manual API call from colliding

The lock is released when the event transitions to PROCESSED, FAILED, or DEAD_LETTER.

### Lock Fields

| Field | Purpose |
|-------|---------|
| `locked_at` | Timestamp when the event was locked |
| `locked_by` | UUID of the processor instance |

## Dead Letter Handling

### Automatic

Events automatically move to `DEAD_LETTER` when `retryCount >= maxRetries`.

### Manual

Use the API to manually dead-letter an event:

```bash
POST /api/v1/outbox/dead-letter/{outboxId}?reason=Business+reason
```

### Recovery

Dead-lettered events can be re-queued by directly updating the database:

```sql
UPDATE outbox_events
SET status = 'PENDING', retry_count = 0, next_attempt_at = NULL,
    last_error_code = NULL, last_error_message = NULL
WHERE id = :outboxId AND status = 'DEAD_LETTER';
```

## Event Type Routing

The dispatcher routes events based on `event_type`:

| Event Type | Spring Event Class |
|------------|-------------------|
| `render.job.created` | `RenderJobCreatedEvent` |
| `render.job.status.changed` | `RenderJobStatusChangedEvent` |
| `render.job.completed` | `RenderJobCompletedEvent` |
| `render.job.failed` | `RenderJobFailedEvent` |
| `artifact.created` | `ArtifactCreatedEvent` |
| `notification.event.published` | String marker (for notification delivery) |
| Unknown types | Dead-lettered with `UNKNOWN_EVENT_TYPE` error |

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/outbox/overview` | Status counts (pending, processing, processed, failed, deadLetter) |
| `GET` | `/api/v1/outbox/recent?limit=20` | Recent events in descending time order |
| `POST` | `/api/v1/outbox/process-once/{outboxId}` | Manually process a single event |
| `POST` | `/api/v1/outbox/process-batch?limit=100` | Manually trigger batch processing |
| `GET` | `/api/v1/outbox/failed?limit=50` | List failed events |
| `POST` | `/api/v1/outbox/retry/{outboxId}` | Manually retry a failed event |
| `POST` | `/api/v1/outbox/dead-letter/{outboxId}?reason=...` | Move event to dead letter |

## Database Schema

### Columns

| Column | Type | Description |
|--------|------|-------------|
| `id` | varchar(64) PK | Event identifier (prefixed `obx_`) |
| `aggregate_type` | varchar(100) | Aggregate root type |
| `aggregate_id` | varchar(100) | Aggregate root ID |
| `event_type` | varchar(150) | Event type discriminator |
| `event_version` | int | Schema version of the event |
| `payload` | text | JSON payload |
| `status` | varchar(50) | One of: PENDING, PROCESSING, PROCESSED, FAILED, DEAD_LETTER |
| `retry_count` | int | Current retry count |
| `max_retries` | int | Maximum retries (default 3) |
| `next_attempt_at` | timestamp | Next retry time (exponential backoff) |
| `idempotency_key` | varchar(255) | Idempotency key for deduplication |
| `last_error_code` | varchar(100) | Error code from last failure |
| `last_error_message` | text | Error message from last failure |
| `locked_at` | timestamp | When the event was locked for processing |
| `locked_by` | varchar(255) | Processor instance UUID |
| `created_at` | timestamp | Event creation time |
| `published_at` | timestamp | When the event was successfully dispatched |

### Key Indexes

| Index | Columns | Purpose |
|-------|---------|---------|
| `ix_outbox_events_status_created_at` | (status, created_at) | Dispatch polling |
| `ix_outbox_events_status_next_attempt` | (status, next_attempt_at) | Retry scheduling |
| `ix_outbox_events_idempotency_key` | (idempotency_key) | Idempotency lookups |
| `ix_outbox_events_locked_at` | (locked_at) | Stale lock recovery |

## Recovery After Crash

If a processor crashes while an event is in `PROCESSING` state:

1. The event remains locked with `locked_at` set
2. A monitoring query can find stale locks:
   ```sql
   SELECT * FROM outbox_events
   WHERE status = 'PROCESSING'
     AND locked_at < NOW() - INTERVAL '5 minutes';
   ```
3. Reset stale locks:
   ```sql
   UPDATE outbox_events
   SET status = 'PENDING', locked_at = NULL, locked_by = NULL
   WHERE status = 'PROCESSING'
     AND locked_at < NOW() - INTERVAL '5 minutes';
   ```
