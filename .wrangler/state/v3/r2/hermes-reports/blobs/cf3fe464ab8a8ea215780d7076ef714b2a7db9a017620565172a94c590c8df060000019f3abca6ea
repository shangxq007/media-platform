# Preview Render Contract

**Updated:** 2026-07-07
**Status:** STABLE (execution validation pending)

---

## Endpoints

### Create Render Job (does NOT execute)

```
POST /api/v1/render/jobs
```

Creates a QUEUED job. Does NOT trigger execution.

### Execute Render Job

```
POST /api/v1/render/jobs/{jobId}/execute
```

Triggers synchronous in-process execution.

### Get Render Job Status

```
GET /api/v1/render/jobs/{jobId}
```

### Get Job Artifacts

```
GET /api/v1/render/jobs/{jobId}/artifacts
```

---

## Status Enum

| Status | Type | Description |
|--------|------|-------------|
| QUEUED | Non-terminal | Job created |
| SELECTING_PROVIDER | Non-terminal | Resolving provider |
| EXECUTING | Non-terminal | Currently rendering |
| **COMPLETED** | **Terminal success** | Job finished successfully |
| FAILED | Terminal failure | Job failed |
| CANCELLED | Terminal failure | Cancelled by user |
| REJECTED | Terminal failure | Rejected by policy |

**Important:** Success terminal is `COMPLETED`, not `SUCCEEDED`.

---

## Canonical Execution Flow

```
1. POST /api/v1/render/jobs         → jobId (QUEUED)
2. POST /api/v1/render/jobs/{id}/execute → triggers execution
3. GET /api/v1/render/jobs/{id}     → poll until COMPLETED
4. GET /api/v1/render/jobs/{id}/artifacts → artifact metadata
```

---

## Warnings

- Create-only endpoint is NOT proof of execution
- HTTP 200 from execute is NOT sufficient without status check
- Must verify COMPLETED status and artifact existence
