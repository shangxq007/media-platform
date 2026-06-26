---
status: implementation-report
created: 2026-06-26
scope: render-module
truth_level: current
owner: platform
---

# Foundation F13 — BMF Backend Compiler Foundation

## Implemented

| Component | Purpose |
|-----------|---------|
| `BmfBackendCompiler` | Implements `BackendCompiler` — backendType="bmf", supports MEDIA_PIPELINE/TRANSCODE/THUMBNAIL/FRAME_EXTRACTION/FILTER |

## ProductType → BMF Graph Mapping

| ProductType | Graph Type |
|------------|-----------|
| THUMBNAIL | THUMBNAIL |
| PROXY, TRANSCODE | TRANSCODE |
| PREVIEW, FINAL_RENDER | MEDIA_PIPELINE |
| PACKAGE | MEDIA_PIPELINE |

## Execution Flow

```
ExecutionPlan → BackendCompilerRuntimeService.compile()
    → BmfBackendCompiler → BackendExecutionSpec
    → ExecutionPipelineService.execute()
    → ExecutionBackendRegistry.resolve(THUMBNAIL) → BmfExecutionBackend
    → ExecutionResult → ProductRuntime
```

## Auto-discovery via Spring `List<BackendCompiler>`. No hardcoding.

## Boundaries

- Compiler never resolves storage — only copies product IDs
- Compiler never registers products — ProductRuntime owns lifecycle
- Compiler never executes — Pipeline handles execution
- Planner remains backend-independent (unchanged)

## Two Compilers Active

| Compiler | Backend | Product Types |
|----------|---------|---------------|
| `LocalProcessBackendCompiler` | local-process | ASR, OCR, VISION, EMBEDDING |
| `BmfBackendCompiler` | bmf | THUMBNAIL, PROXY, PREVIEW, FINAL_RENDER |

## Tests
Compilation passes. All existing tests unaffected.

## Deferred Items
- BMF graph optimizer
- OpenCue backend compiler
