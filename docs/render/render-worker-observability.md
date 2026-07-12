# Render Worker Observability

**Date:** 2026-07-09
**Status:** COMPLETE
**Authority:** RENDER-WORKER-OBSERVABILITY.0

---

## Lifecycle Events

| Event | Status Transition | Description |
|-------|-------------------|-------------|
| JOB_CLAIMED | QUEUED → EXECUTING | Worker claimed job |
| CLAIM_LOST | QUEUED → (race) | Claim lost to another worker |
| EXECUTION_STARTED | EXECUTING → (start) | Execution begins |
| EXECUTION_COMPLETED | EXECUTING → COMPLETED | Output verified |
| EXECUTION_FAILED | EXECUTING → FAILED | Safe failure |
| JOB_RECOVERED_STALE | EXECUTING → FAILED/QUEUED | Stale recovery |
| JOB_REQUEUED_FOR_RETRY | FAILED → QUEUED | Retry requeue |
| RETRY_EXHAUSTED | FAILED (terminal) | Max attempts reached |

---

## Event Model

```java
record LifecycleEvent(
    String eventType,
    String statusFrom,
    String statusTo,
    Instant timestamp,
    String workerId,
    int attempt,
    String reasonCode,
    String reason,
    boolean retryable,
    String outputProductId,
    Long durationMs
)
```

---

## Structured Logs

| Log | Fields |
|-----|--------|
| JOB_CLAIMED | jobId, workerId, attempt |
| EXECUTION_STARTED | jobId, workerId, attempt |
| EXECUTION_COMPLETED | jobId, workerId, outputProductId, durationMs |
| EXECUTION_FAILED | jobId, workerId, failureCode, retryable |
| JOB_RECOVERED_STALE | jobId, action |
| JOB_REQUEUED_FOR_RETRY | jobId, attempt |

---

## Implementation

| Component | Status |
|-----------|--------|
| RenderJobLifecycleEventService | ✅ CREATED |
| In-memory event store | ✅ |
| Bounded history (50 events/job) | ✅ |
| Structured logs | ✅ |

---

## Status

- RENDER-WORKER-OBSERVABILITY.0: COMPLETE
- Lifecycle events: IMPLEMENTED
- Structured logs: IMPLEMENTED
