---
status: implementation-report
created: 2026-06-26
scope: render-module
truth_level: current
owner: platform
---

# Capability C8 — OpenCue Execution Environment (Phase 1)

## Implemented

| Component | Role |
|-----------|------|
| `OpenCueJobSpec` | Platform model: jobName, owner, priority, tags, environmentVariables, resourceRequirements, layers[] |
| `OpenCueJobSpec.OpenCueLayerSpec` | layerName, commands[], frameCount, frameEnvironment |
| `OpenCueProperties` | Configuration: server (localhost), grpcPort (8443), timeoutSec (300), enabled (false) |
| `OpenCueExecutionEnvironment` | Implements `ExecutionEnvironment` — envId="opencue", submit/cancel/status (Phase 1 stub) |
| `OpenCueEnvironmentCompiler` | Implements `EnvironmentCompiler` — compiles BackendExecutionSpec → ExecutionJob |

## Architecture

```
ExecutionJob → ExecutionControlService → EnvironmentRuntimeService.resolve("opencue")
    → OpenCueExecutionEnvironment.submit(job) → OpenCueJobSpec → OpenCue REST/gRPC
```

## Phase 1 Mappings

| Platform | OpenCue |
|----------|---------|
| ExecutionJob | OpenCue Job |
| ExecutionTask | OpenCue Layer (single) |
| ExecutionCommand | Frame Command |
| ExecutionStatus | OpenCue Job status |

No frame splitting. One task → one layer. No workers.

## Configuration

```yaml
opencue:
  server: localhost
  grpc-port: 8443
  timeout-sec: 300
  enabled: false
```

## Auto-Discovery

Both `OpenCueExecutionEnvironment` and `OpenCueEnvironmentCompiler` are `@Component` beans, auto-discovered by `EnvironmentRuntimeService`. Disabled by default (`opencue.enabled=false`).

## Tests

Compilation passes. All existing tests unaffected.

## Deferred (Phase 2+)

| Item |
|------|
| OpenCue REST/gRPC client (real submit) |
| Frame-level dispatch (multi-frame layers) |
| Worker registration + heartbeat |
| Retry/lease integration |
| BMF graph → OpenCue layer mapping |
