# Render Worker Output Cleanup

**Date:** 2026-07-09
**Status:** COMPLETE
**Authority:** RENDER-WORKER-OUTPUT-CLEANUP.0

---

## Cleanup Policy

| Setting | Default | Description |
|---------|---------|-------------|
| `render.worker.output-cleanup.enabled` | `false` | Enable cleanup |
| `render.worker.output-cleanup.dry-run` | `true` | Dry-run mode |
| `render.worker.output-cleanup.max-age` | `24h` | Max file age |
| `render.worker.output-cleanup.batch-size` | `100` | Batch size |

---

## Output Taxonomy

| Category | Cleanup Eligible |
|----------|------------------|
| Canonical completed output | ❌ NEVER |
| Canonical RAW_MEDIA input | ❌ NEVER |
| Valid idempotent reusable output | ❌ NEVER |
| Worker temp output (old) | ✅ YES |
| Partial output (old) | ✅ YES |
| Abandoned staging (old) | ✅ YES |
| Unknown orphan | ❌ REPORT ONLY |

---

## Safety Rules

| Rule | Status |
|------|--------|
| Never delete outside temp-root | ✅ |
| Never follow symlinks | ✅ |
| Never delete completed Product/Artifact | ✅ |
| Never delete RAW_MEDIA | ✅ |
| Never delete active EXECUTING outputs | ✅ |
| Bounded batch cleanup | ✅ |
| Dry-run support | ✅ |

---

## Implementation

| Component | Status |
|-----------|--------|
| RenderWorkerOutputCleanupService | ✅ CREATED |
| Dry-run mode | ✅ |
| Bounded cleanup | ✅ |
| Empty directory cleanup | ✅ |

---

## Status

- RENDER-WORKER-OUTPUT-CLEANUP.0: COMPLETE
- Cleanup service: IMPLEMENTED
- Safety rules: ENFORCED
