# RenderJob Lifecycle Events

**Date:** 2026-07-09
**Status:** COMPLETE
**Authority:** RENDER-JOB-EVENTS.1

---

## Event Table

**Table:** `render_job_lifecycle_events`

| Column | Type | Description |
|--------|------|-------------|
| id | VARCHAR(64) | Event ID |
| tenant_id | VARCHAR(64) | Tenant |
| project_id | VARCHAR(128) | Project |
| render_job_id | VARCHAR(64) | RenderJob |
| event_type | VARCHAR(64) | Event type |
| status_from | VARCHAR(32) | Previous status |
| status_to | VARCHAR(32) | New status |
| worker_id | VARCHAR(128) | Worker ID |
| attempt | INT | Attempt count |
| output_product_id | VARCHAR(64) | Output Product |
| reason_code | VARCHAR(64) | Reason code |
| reason | VARCHAR(512) | Reason message |
| retryable | BOOLEAN | Is retryable |
| duration_ms | BIGINT | Duration |
| event_time | TIMESTAMP | Event time |
| created_at | TIMESTAMP | DB insert time |

---

## Event Types

| Event | Description |
|-------|-------------|
| JOB_CLAIMED | Worker claimed job |
| CLAIM_LOST | Claim lost to another worker |
| EXECUTION_STARTED | Execution begins |
| EXECUTION_COMPLETED | Output verified |
| EXECUTION_FAILED | Safe failure |
| JOB_RECOVERED_STALE | Stale recovery |
| JOB_REQUEUED_FOR_RETRY | Retry requeue |
| RETRY_EXHAUSTED | Max attempts reached |
| OUTPUT_REUSED | Existing output reused |
| OUTPUT_ADOPTED | Output adopted to job |
| DUPLICATE_OUTPUT_PREVENTED | Duplicate prevented |

---

## Indexes

| Index | Columns |
|-------|---------|
| idx_lifecycle_events_job | render_job_id, event_time |
| idx_lifecycle_events_project | project_id, render_job_id, event_time |
| idx_lifecycle_events_tenant | tenant_id, project_id, event_time |
| idx_lifecycle_events_type | event_type, event_time |
| idx_lifecycle_events_worker | worker_id, event_time |

---

## Status

- RENDER-JOB-EVENTS.1: COMPLETE
- Event table: CREATED
- Repository: CREATED
- Service persistence: INTEGRATED
