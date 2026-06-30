# Failure Status Mapping Example

## OpenCue Job States

| OpenCue State | Description | Platform Mapping |
|---------------|-------------|------------------|
| PENDING | Job submitted, waiting for dispatch | SUBMITTED |
| RUNNING | At least one frame running | RUNNING |
| SUCCEEDED | All frames completed successfully | COMPLETED |
| FINISHED | Alias for SUCCEEDED | COMPLETED |
| DEAD | At least one frame failed | FAILED |
| SETUP_FAILED | Job setup phase failed | FAILED |
| CHECKPOINTED | Job checkpointed | RUNNING |

## OpenCue Frame States

| Frame State | Description | Exit Code |
|-------------|-------------|-----------|
| PENDING | Waiting for dispatch | N/A |
| RUNNING | Executing on RQD | N/A |
| SUCCEEDED | Completed successfully | 0 |
| DEAD | Completed with error | non-zero |

## Mapping Example

### Scenario: 10-frame job, frames 3 and 7 fail

```sql
-- Query frame states
SELECT f.int_number, f.str_state, f.int_exit_status
FROM frame f
WHERE f.pk_job = (SELECT pk_job FROM job WHERE str_name='render-abc123')
ORDER BY f.int_number;
```

Expected result:

| Frame | State | Exit Code |
|-------|-------|-----------|
| 1 | SUCCEEDED | 0 |
| 2 | SUCCEEDED | 0 |
| 3 | DEAD | 1 |
| 4 | SUCCEEDED | 0 |
| 5 | SUCCEEDED | 0 |
| 6 | SUCCEEDED | 0 |
| 7 | DEAD | 137 |
| 8 | SUCCEEDED | 0 |
| 9 | SUCCEEDED | 0 |
| 10 | SUCCEEDED | 0 |

### Platform Status Mapping

```java
public ExecutionStatus mapJobStatus(String opencueJobState, List<FrameStatus> frames) {
    long succeeded = frames.stream().filter(f -> f.state == SUCCEEDED).count();
    long dead = frames.stream().filter(f -> f.state == DEAD).count();

    if (dead > 0) {
        return ExecutionStatus.PARTIAL_FAILURE;
    }
    if (succeeded == frames.size()) {
        return ExecutionStatus.COMPLETED;
    }
    return ExecutionStatus.RUNNING;
}
```

## Failure Visibility

### What Is Observable

1. **Job-level**: DEAD state visible in DB
2. **Frame-level**: DEAD state + exit code visible in DB
3. **Layer-level**: Layer name associated with failed frame
4. **Timing**: dt_started, dt_finished for each frame
5. **Logs**: Worker logs available via container or log aggregation

### What Is Not Observable (from CJSL alone)

1. **Stderr content**: Must query worker logs
2. **Resource exhaustion**: Must query worker metrics
3. **Timeout**: Not configured in basic CJSL
4. **Retry count**: Not configured in basic CJSL

## Partial Success Handling

When a job has both SUCCEEDED and DEAD frames:

```java
public RenderResult handlePartialSuccess(String jobId) {
    List<FrameStatus> frames = queryFrameStatuses(jobId);
    List<FrameStatus> succeeded = filter(frames, SUCCEEDED);
    List<FrameStatus> dead = filter(frames, DEAD);

    // Register successful outputs
    for (FrameStatus frame : succeeded) {
        registerOutput(jobId, frame);
    }

    // Report failures
    for (FrameStatus frame : dead) {
        reportFailure(jobId, frame.exitCode, frame.layerName);
    }

    return RenderResult.partial(succeeded.size(), dead.size());
}
```

## Retry Boundary

### Current State (P2O.0g)

- No retry mechanism implemented
- Failed frames remain DEAD
- Platform must decide: retry whole job or accept partial

### Future Work

- Frame-level retry via Cuebot API (if available)
- Job-level retry with modified frame range
- Platform-side retry policy configuration

## Safety Rules

1. Never hide failure status from caller
2. Never retry silently without policy
3. Never expose exit codes in public API (internal detail)
4. Never expose worker paths in public API
5. Always log failure details for debugging
6. Always preserve successful output metadata even on partial failure
