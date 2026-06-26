---
status: implementation-report
created: 2026-06-26
scope: render-module
truth_level: current
owner: platform
---

# Foundation F14 — Backend Execution Spec Specialization

## Type Hierarchy

```
BackendExecutionSpec (interface)
    ├── LocalProcessExecutionSpec (CLI: executable, arguments, environment, workingDirectory)
    └── BmfExecutionSpec (Graph: graphDefinition, graphInputs, graphOutputs, graphOptions)
```

## Implemented

| Component | Purpose |
|-----------|---------|
| `BackendExecutionSpec` | Converted to interface — common fields |
| `LocalProcessExecutionSpec` | Record implementing BackendExecutionSpec — executable, arguments, environment, workingDirectory, stdin/stdout fields |
| `BmfExecutionSpec` | Record implementing BackendExecutionSpec — graphDefinition, graphInputs, graphOutputs, graphOptions |

## Compiler Updates

| Compiler | Returns |
|----------|---------|
| `LocalProcessBackendCompiler` | `LocalProcessExecutionSpec.of(backendId, producer, inputs, outputs, executable, args)` |
| `BmfBackendCompiler` | `BmfExecutionSpec.of(backendId, producer, graphDefinition, inputs, outputs)` |

## Runtime Compatibility

- `BackendCompilerRuntimeService` works polymorphically — treats specs as `BackendExecutionSpec`
- `ExecutionPipelineService` unchanged
- `BackendCompiler` SPI return type still `BackendExecutionSpec`

## Tests

Compilation passes. All existing tests unaffected. Polymorphism validated.

## Deferred Items

- OpenCueExecutionSpec
- RemotionExecutionSpec
- MltExecutionSpec
