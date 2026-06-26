---
status: blueprint
created: 2026-06-26
scope: platform-wide
truth_level: target
owner: platform
---

# Execution Lifecycle Model

## States

| State | Description |
|-------|-------------|
| CREATED | Job created, not yet submitted |
| SUBMITTED | Environment accepted the job |
| QUEUED | Waiting for available worker |
| RUNNING | Actively executing |
| PAUSED | Temporarily suspended |
| COMPLETED | Finished successfully |
| FAILED | Finished with error |
| CANCELLED | Explicitly stopped |
| TIMED_OUT | Exceeded execution deadline |

## Allowed Transitions

```
CREATED → SUBMITTED → QUEUED → RUNNING → COMPLETED
                  ↓         ↓         ↓
              CANCELLED  PAUSED    FAILED
                  ↓         ↓         ↓
              (terminal)  RUNNING  TIMED_OUT
```

## Model Extensions

### ExecutionJob
- +status (ExecutionStatus)
- +createdAt (Instant)
- +startedAt (Instant)
- +finishedAt (Instant)
- +failureReason (String)
- +withStatus() → returns new instance with updated timestamps

### ExecutionTask
- +status (ExecutionStatus)
- +attempt (int)
- +workerId (String)

## Platform vs Environment

Platform: owns lifecycle semantics, defines allowed transitions
Environment: reports state, executes tasks
