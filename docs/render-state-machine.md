# RenderJob State Machine

## Overview

The RenderJob state machine centralizes all state transition logic for render jobs, replacing scattered status checks in `RenderOrchestratorService` with a single, auditable state machine.

## States

| State | Description | Terminal? |
|---|---|---|
| `QUEUED` | Job created, awaiting processing | No |
| `AI_PROCESSING` | AI script generation in progress | No |
| `RENDERING` | Render provider executing | No |
| `COMPLETED` | Render finished successfully | Yes |
| `FAILED` | Render failed (AI, Storage, or Provider error) | No (retryable) |
| `CANCELLED` | User cancelled the job | Yes |
| `REJECTED` | Job rejected at submission (quota/permission) | Yes |

## State Diagram

```
                    ┌──────────┐
          ┌────────►│ CANCELLED│ (terminal)
          │         └──────────┘
          │                ▲
          │                │
┌─────┐   │  ┌─────────────┼──────┐   ┌──────────┐
│QUEUED├───┤  │ AI_PROCESSING│      ├──►│COMPLETED │
└─────┘   │  └──────┬──────┘      │   └──────────┘
   │      │         │             │
   │      │         ▼             │
   │      │  ┌──────────────┐     │
   │      │  │  RENDERING   │     │
   │      │  └──────┬───────┘     │
   │      │         │             │
   │      │         ▼             │
   │      │  ┌──────────────┐     │
   │      └──┤   FAILED     ├─────┘
   │         └──────────────┘
   │                │
   │                │ (retry)
   │                └──────► QUEUED
   │
   ├────────► REJECTED (terminal)
   └────────► CANCELLED (terminal)
```

## Valid Transitions

| From | To | Reason |
|---|---|---|
| `QUEUED` | `AI_PROCESSING` | Normal flow: start AI generation |
| `QUEUED` | `CANCELLED` | User cancels before processing |
| `QUEUED` | `REJECTED` | Quota exceeded or permission denied |
| `AI_PROCESSING` | `RENDERING` | AI script generated successfully |
| `AI_PROCESSING` | `FAILED` | AI generation error |
| `AI_PROCESSING` | `CANCELLED` | User cancels during AI processing |
| `RENDERING` | `COMPLETED` | Render finished successfully |
| `RENDERING` | `FAILED` | Render provider or storage error |
| `RENDERING` | `CANCELLED` | User cancels during rendering |
| `FAILED` | `QUEUED` | User retries the failed job |

## Error Codes

When a job transitions to `FAILED` or `REJECTED`, an error code is recorded in the status history:

| Error Code | Description |
|---|---|
| `QUOTA_EXCEEDED` | Tenant quota insufficient, job rejected at submission |
| `AI_GENERATION_FAILED` | AI provider returned an error |
| `NO_RENDER_PROVIDER` | No render provider available for the requested profile |
| `RENDER_FAILED` | Render provider execution error |
| `STORAGE_FAILED` | Blob storage or artifact registration error |
| `STALE_TIMEOUT` | Job timed out (compensated by StaleRenderJobCompensator) |

## Status History

Every state transition is recorded in the `render_job_status_history` table:

```sql
CREATE TABLE render_job_status_history (
    id VARCHAR(64) PRIMARY KEY,
    job_id VARCHAR(64) NOT NULL REFERENCES render_job(id),
    from_status VARCHAR(30),
    to_status VARCHAR(30) NOT NULL,
    reason VARCHAR(255),
    error_code VARCHAR(100),
    occurred_at TIMESTAMP NOT NULL DEFAULT NOW()
);
```

## Failure Compensation

### StaleRenderJobCompensator

A scheduled task that detects jobs stuck in `AI_PROCESSING` or `RENDERING` for too long.

**Configuration:**

```properties
# Enable/disable the compensator (default: true)
render.stale-compensator.enabled=true

# Threshold after which a job is considered stale (default: PT30M = 30 minutes)
render.stale-compensator.threshold=PT30M

# How often the compensator runs (default: PT5M = 5 minutes)
render.stale-compensator.interval=PT5M
```

**Behavior:**
1. Queries for jobs in `AI_PROCESSING` or `RENDERING` status with `created_at` older than the threshold
2. Validates the transition to `FAILED` via `RenderJobStateMachine`
3. Updates the job status to `FAILED` with error message
4. Records a history entry with `reason=stale_timeout` and `errorCode=STALE_TIMEOUT`
5. Publishes a `RenderJobFailedEvent`

## API Endpoints

### Cancel a Job

```
POST /api/v1/render/jobs/{jobId}/cancel?tenantId={tenantId}
```

- Validates the job exists and belongs to the tenant
- Validates the transition via `RenderJobStateMachine`
- Sets status to `CANCELLED`
- Records history entry

**Errors:**
- `404` — Job not found
- `409` — Invalid state transition (e.g., already completed)

### Retry a Failed Job

```
POST /api/v1/render/jobs/{jobId}/retry?tenantId={tenantId}
```

- Validates the job exists and belongs to the tenant
- Validates the transition `FAILED → QUEUED` via `RenderJobStateMachine`
- Clears the error message
- Records history entry

**Errors:**
- `404` — Job not found
- `409` — Job is not in FAILED status

### Get Status History

```
GET /api/v1/render/jobs/{jobId}/status-history?tenantId={tenantId}
```

Returns the full transition history for a job, ordered by time:

```json
[
  {
    "id": "rsh_abc123",
    "jobId": "rj_xyz789",
    "fromStatus": null,
    "toStatus": "QUEUED",
    "reason": "Job created",
    "errorCode": null,
    "occurredAt": "2025-01-15T10:30:00Z"
  },
  {
    "id": "rsh_def456",
    "jobId": "rj_xyz789",
    "fromStatus": "QUEUED",
    "toStatus": "AI_PROCESSING",
    "reason": null,
    "errorCode": null,
    "occurredAt": "2025-01-15T10:30:05Z"
  }
]
```

## Usage in Code

### State Machine Validation

```java
RenderJobStateMachine stateMachine = new RenderJobStateMachine();

// Check if transition is valid
if (stateMachine.canTransition(currentStatus, newStatus)) {
    // proceed
}

// Or validate and throw on invalid transition
stateMachine.validateTransition(currentStatus, newStatus);
// throws PlatformException with CommonErrorCode.CONFLICT if invalid
```

### Recording History

```java
historyRepository.record(jobId, "QUEUED", "AI_PROCESSING", "Started AI", null);
historyRepository.record(jobId, "AI_PROCESSING", "FAILED", "AI error", "AI_GENERATION_FAILED");
```

### Querying History

```java
List<StatusHistoryResponse> history = historyRepository.findByJobId(jobId);
// Returns all transitions ordered by occurred_at ascending
```
