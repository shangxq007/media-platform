---
status: implementation-report
created: 2026-06-26
scope: render-module
truth_level: current
owner: platform
---

# Foundation F8 — Backend Selection Foundation

## Backend Selection: Planning Only

| Before (F7) | After (F8) |
|------------|-----------|
| ExecutionStep has no backend info | +backendId, +backendType, +backendSelectionReason, +backendResolved |
| No backend integration | `ExecutionPlannerService` uses `ExecutionBackendRegistry` |

## ProductType → TaskCapability Mapping

| ProductType | TaskCapability | Backend |
|------------|---------------|---------|
| TRANSCRIPT | ASR | local-process |
| OCR | OCR | local-process |
| VISION | VISION | local-process |
| EMBEDDING | EMBEDDING | local-process |
| THUMBNAIL | THUMBNAIL | bmf |
| PROXY, TRANSCODE | TRANSCODE | bmf |
| PREVIEW, FINAL_RENDER | MEDIA_PIPELINE | bmf |
| PACKAGE | PACKAGE | bmf |

## Backend Capability Matrix

| Backend | Capabilities |
|---------|-------------|
| `local-process` | PROBE, ASR, OCR, VISION, EMBEDDING |
| `bmf` | MEDIA_PIPELINE, TRANSCODE, FRAME_EXTRACTION, FILTER, THUMBNAIL |

## Selection Algorithm

```
selectBackend(step, productType):
  1. Map productType → TaskCapability
  2. If no mapping → mark UNRESOLVED (step still included in plan)
  3. Resolve backend via ExecutionBackendRegistry.resolve(capability)
  4. If found → step.withBackend(backendId, backendType, reason)
  5. If not → mark UNRESOLVED
```

## Modified

| Component | Change |
|-----------|--------|
| `ExecutionStep` | +backendId, +backendType, +backendSelectionReason, +backendResolved; +withBackend() |
| `ExecutionPlannerService` | +`ExecutionBackendRegistry`; +`selectBackend()`; +`mapProductTypeToCapability()`; enhanced explain() with backend info |

## Explain Output (Enhanced)

```
Plan eplan_1:
  Stage 0 (sequential):
    - MISSING → Producer whisper-asr → [TRANSCRIPT] [backend=local-process reason=local-process supports ASR]
    - MISSING → Producer whisper-asr → [THUMBNAIL] [backend=bmf reason=bmf supports THUMBNAIL]
```

## Unresolved Handling

Steps with no capability mapping or no compatible backend are marked `backendResolved=false` with a reason string. They remain in the plan for visibility. Execution phase will reject unresolved plans later.

## Tests
Compilation passes. All existing tests unaffected.

## Deferred Items
- Multi-backend preference (cost, locality, GPU)
- Backend hint override from step parameters
