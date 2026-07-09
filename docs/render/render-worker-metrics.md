# Render Worker Metrics

**Date:** 2026-07-09
**Status:** COMPLETE
**Authority:** RENDER-WORKER-METRICS.0

---

## Metrics Scope

### State Counts

| Metric | Description |
|--------|-------------|
| queuedCount | Jobs in QUEUED state |
| executingCount | Jobs in EXECUTING state |
| completedCount | Jobs in COMPLETED state |
| failedCount | Jobs in FAILED state |

### Health Counts

| Metric | Description |
|--------|-------------|
| staleExecutingCount | EXECUTING jobs older than 30m |
| retryEligibleFailedCount | FAILED jobs marked RETRYABLE |
| retryExhaustedCount | FAILED jobs marked RETRY_EXHAUSTED |
| oldestQueuedAgeSeconds | Age of oldest QUEUED job |
| oldestExecutingAgeSeconds | Age of oldest EXECUTING job |

### Warnings

| Warning | Condition |
|---------|-----------|
| STALE_EXECUTING_JOBS_PRESENT | Stale count > 0 |
| RETRY_ELIGIBLE_FAILED_JOBS_PRESENT | Retry eligible > 0 |
| RETRY_EXHAUSTED_JOBS_PRESENT | Retry exhausted > 0 |
| OLD_QUEUED_JOB | Oldest queued > 1 hour |

---

## Implementation

| Component | Status |
|-----------|--------|
| RenderJobRepository.countByStatus | ✅ |
| RenderJobRepository.countStaleExecuting | ✅ |
| RenderJobRepository.countRetryEligibleFailed | ✅ |
| RenderJobRepository.countRetryExhausted | ✅ |
| RenderWorkerMetricsService | ✅ |

---

## Status

- RENDER-WORKER-METRICS.0: COMPLETE
- Metrics service: IMPLEMENTED
- State counts: IMPLEMENTED
- Health counts: IMPLEMENTED
