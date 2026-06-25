---
status: implementation-report
created: 2026-06-25
scope: outbox-event-module + render-module
truth_level: current
owner: platform
---

# Media Runtime Sprint 037 — Execution Backend Abstraction

## Execution Audit

| Component | Before | After |
|-----------|--------|-------|
| `ProbeTaskHandler` | Called ffprobe directly via `FfprobeMetadataProvider` | Delegates to `ExecutionBackend` via `ExecutionBackendRegistry` |
| `ProcessBuilder` | Embedded in `FfprobeMetadataProvider` | Extracted to `LocalProcessExecutionBackend` |
| Future backends (BMF, OpenCue, K8s) | No abstraction | `ExecutionBackend` SPI ready |

## Execution Architecture

```
Platform Task → TaskDispatcher → ProbeTaskHandler
    ↓ (delegates)
ExecutionBackendRegistry.resolve(PROBE) → LocalProcessExecutionBackend
    ↓ (executes)
ProcessBuilder → ffprobe CLI → stdout/stderr/exitCode
    ↓ (returns)
ExecutionResult → ProbeTaskHandler processes result
```

## New Components (3 + SPI)

| Component | Role |
|-----------|------|
| `ExecutionBackend` (SPI) | Interface: `backendId()`, `supports(TaskCapability)`, `execute(ExecutionRequest)` |
| `ExecutionRequest` | Immutable record: jobId, taskId, capability, args, timeout, env, workingDir, payload |
| `ExecutionResult` | Immutable record: success, exitCode, stdout, stderr, durationMs, errorCode, errorMessage |
| `LocalProcessExecutionBackend` | Wraps ProcessBuilder — supports PROBE capability |
| `ExecutionBackendRegistry` | Auto-registers backends by TaskCapability via Spring injection |

## Refactored Component

| Component | Change |
|-----------|--------|
| `ProbeTaskHandler` | No longer calls `FfprobeMetadataProvider` directly. Uses `ExecutionBackendRegistry.resolve(PROBE)` → `ExecutionBackend.execute(request)`. Handler owns business logic; backend owns execution. |

## Future Backend Design (SPI only)

| Backend | Purpose | When |
|---------|---------|------|
| `LocalProcessExecutionBackend` | ✅ Current: ProcessBuilder for ffprobe/CLI tools | Now |
| `BmfExecutionBackend` | BMF framework for media processing | P3 |
| `OpenCueExecutionBackend` | Render farm job submission | P4 |
| `KubernetesExecutionBackend` | K8s Job resource | P5 |

**No backend implementation beyond LocalProcess in this sprint.**

## Tests

All existing coordination tests pass (PlatformJob, PlatformTask, TaskHandlerRegistry, TaskDispatcher). Probe tests pass.

## Known Limitations

| Limitation | Status |
|-----------|--------|
| `FfprobeMetadataProvider` still exists | Both provider (semantic analysis) and backend (execution) coexist. Provider will be refactored to use backend in future sprint. |
| Only PROBE capability registered | Future: add ASR, OCR, VISION, EMBEDDING backends when capabilities are implemented. |
| No async execution | `execute()` is synchronous. Async execution (future with OpenCue) requires different API. |

## Deferred Items

| Item | Sprint |
|------|--------|
| Whisper integration | Sprint 038 |
| BMF backend | P3 |
| OpenCue backend | P4 |
| Async execution API | P4 |
