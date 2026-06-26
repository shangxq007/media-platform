---
status: implementation-report
created: 2026-06-26
scope: render-module
truth_level: current
owner: platform
---

# Foundation F9 — Backend Execution Model Foundation

## Implemented

### Domain Models (6)
| Component | Purpose |
|-----------|---------|
| `BackendExecutionSpec` | Backend-specific spec: executionSpecId, backendId/Type, producerId, inputs, outputs, hints, workingDirectory |
| `ExecutionInput` | productId, storageReferenceId, representationKind, materializationPolicy |
| `ExecutionOutput` | expectedProductType, representationKind, temporary |
| `ExecutionResourceHints` | cpu, memoryMb, gpu, diskMb, priority, timeoutSec |
| `ExecutionHint` | key/value extensibility hints |
| `BackendCompiler` | SPI: backendType(), supports(plan), compile(plan) → BackendExecutionSpec |

## Architecture Boundaries

```
Execution Plan (Logical)
    ↓
Backend Compiler (SPI — no implementation)
    ↓
Backend Execution Spec (Backend-specific)
    ↓
ExecutionBackend.execute(ExecutionRequest) → ExecutionResult
```

Planner → produces ExecutionPlan (logical). Compiler → translates to BackendExecutionSpec (backend-specific). ExecutionBackend → executes BackendExecutionSpec.

## Key Design Decisions

- ExecutionBackend must consume `BackendExecutionSpec`, not `ExecutionPlan` directly
- Planner remains backend-independent — never produces backend-specific specs
- BackendCompiler is SPI only — no implementation, no registration, no runtime
- ExecutionInput carries materializationPolicy (LOCAL_CACHE) but no local path
- ExecutionOutput carries expected type but never allocates storage

## Tests
Compilation passes. All existing tests unaffected.

## Deferred Items
- BackendCompiler implementations (FFmpeg, BMF, OpenCue, Remotion)
- Integration with ExecutionBackend
