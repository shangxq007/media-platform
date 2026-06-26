---
status: implementation-report
created: 2026-06-26
scope: render-module
truth_level: current
owner: platform
---

# Foundation F10 — Backend Compiler Runtime Foundation

## Implemented

### Runtime Service
`BackendCompilerRuntimeService` — auto-discovers `BackendCompiler` beans via Spring injection.

| Method | Purpose |
|--------|---------|
| `compile(plan)` | Selects compiler by backendType, calls `compiler.compile(plan)` → `BackendExecutionSpec` |
| `listCompilers()` | Returns registered backend types |
| `supports(backendType)` | Checks if compiler exists for type |

## Architecture Flow

```
ExecutionPlan → BackendCompilerRuntimeService.compile(plan)
    → resolve compiler by backendType
    → BackendCompiler.compile(plan) → BackendExecutionSpec
```

## Compiler Discovery

Auto-discovered via Spring `List<BackendCompiler>` constructor injection. No registry table. No registry runtime. Consistent with `ProducerRuntimeService` pattern.

## Compiler Selection

First compiler matching `backendType` that `supports(plan)`. No optimizer. No priorities.

## Boundaries

- Planner never calls compiler directly — only returns ExecutionPlan
- Compiler never executes, never accesses storage, never calls Producer
- CompilerRuntimeService is the single orchestration entry

## Tests

Compilation passes. All existing tests unaffected.

## Deferred Items

- LocalProcess compiler implementation
- BMF compiler implementation
- OpenCue compiler implementation
