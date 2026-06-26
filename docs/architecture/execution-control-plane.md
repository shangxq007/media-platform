---
status: blueprint
created: 2026-06-26
scope: platform-wide
truth_level: target
owner: platform
---

# Execution Control Plane

## Architecture

```
ExecutionJob → ExecutionControlService → EnvironmentRuntimeService → ExecutionEnvironment → submit/status
                    ↓
            ExecutionJobRegistry (in-memory, platform-owned)
```

## Responsibilities

| Component | Owns |
|-----------|------|
| `ExecutionControlService` | submit, cancel, status, complete, fail, listJobs |
| `ExecutionJobRegistry` | In-memory job tracking (no persistence) |
| `ExecutionEnvironment` | Executes — never updates platform state |

## Lifecycle Ownership

Platform: owns lifecycle semantics (ExecutionStatus transitions).
Environment: reports execution state. Never updates platform state directly.

## Events

- `ExecutionJobSubmittedEvent` — jobId, environmentId, backendId
- `ExecutionJobCompletedEvent` — jobId, environmentId
- `ExecutionJobFailedEvent` — jobId, reason

## Integration

`ExecutionControlService` uses `EnvironmentRuntimeService.resolve()` to find environments. Environment never resolves jobs.

## Future

- Persistence (job history)
- Queue management
- Retry orchestration
- Worker registration
