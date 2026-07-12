# RenderJob Event Retention

**Date:** 2026-07-09
**Status:** COMPLETE
**Authority:** RENDER-JOB-EVENT-RETENTION.0

---

## Retention Policy

| Setting | Default | Description |
|---------|---------|-------------|
| `render.job.events.retention.enabled` | `false` | Enable cleanup |
| `render.job.events.retention.max-age` | `30d` | Max event age |
| `render.job.events.retention.batch-size` | `1000` | Batch size |
| `render.job.events.retention.dry-run` | `true` | Dry-run mode |

---

## Cleanup Eligibility

| Condition | Eligible |
|-----------|----------|
| event_time < cutoff | ✅ |
| Job not EXECUTING | ✅ |
| Recent events | ❌ |
| Active EXECUTING job events | ❌ |

---

## Implementation

| Component | Status |
|-----------|--------|
| RenderJobLifecycleEventRepository.countEventsOlderThan | ✅ |
| RenderJobLifecycleEventRepository.findOldestEventTime | ✅ |
| RenderJobLifecycleEventRepository.deleteEventsOlderThan | ✅ |
| RenderJobLifecycleEventRetentionService | ✅ |

---

## Safety Rules

| Rule | Status |
|------|--------|
| Bounded batch deletes | ✅ |
| Active EXECUTING events protected | ✅ |
| Recent events protected | ✅ |
| Dry-run supported | ✅ |
| RenderJob state not mutated | ✅ |

---

## Status

- RENDER-JOB-EVENT-RETENTION.0: COMPLETE
- Retention service: IMPLEMENTED
- Cleanup: BATCH + BOUNDED
