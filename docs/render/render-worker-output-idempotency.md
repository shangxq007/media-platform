# Render Worker Output Idempotency

**Date:** 2026-07-09
**Status:** COMPLETE
**Authority:** RENDER-WORKER-OUTPUT-IDEMPOTENCY.0

---

## Idempotency Contract

| Rule | Status |
|------|--------|
| One RenderJob → one canonical output Product | ✅ |
| RenderJob.outputProductId is canonical | ✅ |
| Re-execution does not create duplicate Product | ✅ |
| Completion requires output verification | ✅ |

---

## Crash Window Handling

```
Worker renders → Output created → Worker crashes
  ↓
Recovery marks FAILED → Retry requeues → Worker reclaims
  ↓
Worker checks execution metadata → Output already exists
  ↓
Skip re-execution → Mark COMPLETED
```

---

## Output States

| State | Behavior |
|-------|----------|
| No output | Render normally |
| Valid existing output | Reuse/adopt |
| Invalid existing output | Fail safely |
| Completed job | Skip execution |
| Failed with output | Retry may adopt |

---

## Implementation

| Component | Status |
|-----------|--------|
| RenderWorkerExecutionService idempotency guard | ✅ |
| pipeline_execution_json check | ✅ |
| COMPLETED job skip | ✅ |

---

## Status

- RENDER-WORKER-OUTPUT-IDEMPOTENCY.0: COMPLETE
- Output idempotency: IMPLEMENTED
- Crash window handling: IMPLEMENTED
