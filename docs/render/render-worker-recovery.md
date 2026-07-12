# FFmpeg Worker Recovery

**Date:** 2026-07-09
**Status:** COMPLETE
**Authority:** RENDER-WORKER-RECOVERY.0

---

## Recovery Policy

**Default action:** FAIL
**Stale timeout:** 30m (configurable)
**Atomic guard:** Yes (status = EXECUTING)

---

## Stale Detection

| Field | Condition |
|-------|-----------|
| status | EXECUTING |
| updated_at | < cutoff (now - stale_timeout) |

---

## Recovery Actions

| Action | Behavior |
|--------|----------|
| FAIL | Mark stale EXECUTING → FAILED |
| REQUEUE | Mark stale EXECUTING → QUEUED |

---

## Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `render.worker.recovery.enabled` | `true` | Enable recovery |
| `render.worker.recovery.stale-timeout` | `30m` | Stale timeout |
| `render.worker.recovery.action` | `FAIL` | Recovery action |

---

## Polling Integration

1. **Startup** — Run recovery before first poll
2. **Each loop** — Run recovery when no jobs found
3. **Atomic** — Only recover jobs still in EXECUTING

---

## Implementation

| Component | Status |
|-----------|--------|
| RenderJobRepository.findStaleExecutingJobs | ✅ |
| RenderJobRepository.markExecutingJobFailed | ✅ |
| RenderJobRepository.requeueExecutingJob | ✅ |
| RenderWorkerRecoveryService | ✅ |
| FFmpegWorkerRunner integration | ✅ |

---

## Status

- RENDER-WORKER-RECOVERY.0: COMPLETE
- Stale EXECUTING recovery: IMPLEMENTED
- Default action: FAIL
