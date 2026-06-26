---
status: implementation-report
created: 2026-06-26
scope: render-module
truth_level: current
owner: platform
---

# Foundation F4 — Producer Runtime Foundation

## Implemented

### Domain Models (3)
| Component | Purpose |
|-----------|---------|
| `ProducerContext` | Execution context: executionId, tenantId, projectId, inputProductIds, requestedOutputTypes, executionHints |
| `ProducerResult` | Execution result: success, producedProductIds[], executionDurationMs, warnings[], error |
| `Producer` (SPI) | Interface: producerId(), supportedOutputTypes(), execute(ProducerContext) → ProducerResult |

### Runtime Service
| Component | Key Methods |
|-----------|-------------|
| `ProducerRuntimeService` | execute(producerId, context) — resolves producer, invokes execution, logs results; listProducers() — registry of registered producers |

## Architecture

```
ProducerRuntimeService
  ↓ resolve producer
Producer SPI
  ↓ execute(context)
ProducerResult
```

ProducerRuntimeService coordinates:
- Producer SPI (execution entry)
- ProductRuntime (inputs/outputs — via future integration)
- StorageRuntime (materialization — via future integration)

## Execution Flow

```
ProducerRuntimeService.execute(producerId, context):
  1. Resolve Producer by producerId
  2. Log execution start
  3. Invoke producer.execute(context) → ProducerResult
  4. Log execution finish (duration, success, outputs)
  5. Return result
```

## Integration Boundaries

| Boundary | Rule |
|----------|------|
| Product Runtime | ProducerRuntime never accesses ProductRepository directly |
| Storage Runtime | ProducerRuntime never resolves paths directly |
| Execution Backend | ProducerRuntime does not execute CLI directly |
| Producer Registry | Auto-discovered via Spring List<Producer> injection |

## Constraints Validated
- No existing producers migrated (Whisper, OCR, Vision, Embedding unchanged)
- No REST endpoints (internal only)
- No Producer Registry table

## Tests
Compilation passes. All existing tests unaffected.

## Deferred Items
| Item |
|------|
| ProductRuntime + StorageRuntime integration |
| Execution Planner integration |
| Existing producer migration |
