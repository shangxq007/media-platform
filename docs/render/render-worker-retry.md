# Render Worker Retry Policy

**Date:** 2026-07-09
**Status:** COMPLETE
**Authority:** RENDER-WORKER-RETRY.0

---

## Retry Policy

| Setting | Default | Description |
|---------|---------|-------------|
| `render.worker.retry.enabled` | `true` | Enable retry |
| `render.worker.retry.max-attempts` | `3` | Max attempts |

---

## Failure Classification

### Retryable

| Failure | Reason |
|---------|--------|
| timeout | Transient |
| connection | Transient |
| interrupted | Transient |
| killed | Transient |
| temporary | Transient |

### Non-retryable

| Failure | Reason |
|---------|--------|
| validation | Deterministic |
| not found | Deterministic |
| invalid | Deterministic |
| unsupported | Deterministic |

---

## Retry Flow

```
EXECUTING → FAILED [RETRYABLE]
  ↓
Retry Service scans
  ↓
FAILED → QUEUED (requeue)
  ↓
Atomic claim → EXECUTING
  ↓
Re-execute
  ↓
COMPLETED or FAILED (exhausted)
```

---

## Implementation

| Component | Status |
|-----------|--------|
| RenderJobRepository.findRetryEligibleFailedJobs | ✅ |
| RenderJobRepository.requeueFailedJob | ✅ |
| RenderWorkerRetryService | ✅ |
| FFmpegWorkerRunner integration | ✅ |

---

## Status

- RENDER-WORKER-RETRY.0: COMPLETE
- Bounded retry: IMPLEMENTED
- Failure classification: IMPLEMENTED
