# Observability Baseline — media-platform

**Date:** 2026-07-01
**Status:** PLANNED
**Authority:** OBS.0

---

## Scope

| Domain | Events | Status |
|--------|--------|--------|
| Application logs | Startup, errors, requests | PLANNED |
| Render job logs | Job lifecycle, FFmpeg execution | PLANNED |
| Provider binding traces | Eligibility, selection, dispatch | PLANNED |
| ProductRuntime lifecycle | Create, update, finalize | PLANNED |
| StorageRuntime materialization | Upload, download, verify | PLANNED |
| Agent run events | Task start, complete, fail | PLANNED |
| Verification agent results | PVE smoke, cloud checks | PLANNED |
| PVE preview deployment | Deploy, health, smoke | PLANNED |
| Cloud Verification / Lambda | Endpoint checks, latency | PLANNED |
| OpenCue worker events | Dispatch, frame status | PLANNED |
| OpenCV/media analysis | Future worker events | PLANNED |

## Tool Status

| Tool | Status | Role |
|------|--------|------|
| OpenTelemetry SDK | PLANNED | Vendor-neutral telemetry standard |
| OpenTelemetry Collector | PLANNED | Unified collector pipeline |
| Fluent Bit | PLANNED | Lightweight log forwarder |
| Fluentd | DEFERRED | Log aggregator (evaluate later) |
| OpenSearch/Loki/S3 | EVALUATE_LATER | Log storage/search |

---

## Trace/Span Naming Baseline

### Span Namespaces

```
media.timeline.*
media.caption.*
media.provider_binding.*
media.render.*
media.product.*
media.storage.*
media.worker.*
media.verification.*
media.agent.*
media.external.*
media.frontend.*
```

### Example Spans

| Span | Description |
|------|-------------|
| `media.timeline.apply_edit_command` | Apply timeline edit |
| `media.caption.map_to_ass` | Map caption to ASS format |
| `media.provider_binding.compile_plan` | Compile provider binding plan |
| `media.render.ffmpeg_libass.plan` | FFmpeg/libass render plan |
| `media.product.create_preview_artifact` | Create preview artifact |
| `media.storage.materialize_artifact` | Materialize artifact to storage |
| `media.worker.opencue.dispatch` | Dispatch to OpenCue worker |
| `media.verification.pve.smoke_test` | PVE smoke test |
| `media.verification.cloud.endpoint_check` | Cloud endpoint check |
| `media.agent.run_task` | Agent task execution |
| `media.external.model_provider.health_check` | Model provider health |

---

## Log Event Schema

### Required Fields

| Field | Required | Description |
|-------|----------|-------------|
| timestamp | YES | ISO 8601 |
| level | YES | INFO/WARN/ERROR |
| service | YES | Service name |
| environment | YES | dev/preview/prod |
| event_name | YES | Event identifier |
| correlation_id | YES | Request/task correlation |
| trace_id | optional | OpenTelemetry trace ID |
| span_id | optional | OpenTelemetry span ID |
| job_id | optional | Render job ID |
| task_id | optional | TASK_ID |
| agent_run_id | optional | Agent run ID |
| project_id | optional | Project ID |
| tenant_id | optional | Tenant ID |
| product_id | optional | Product ID |
| artifact_id | optional | Artifact ID |
| provider | optional | Provider name |
| execution_environment | optional | FFmpeg/OpenCue/etc |
| result | YES | SUCCESS/FAILURE/BLOCKED |
| duration_ms | optional | Duration |
| error_code | optional | Error code |
| message | YES | Human-readable message |

### Rules

- Logs must not contain secrets
- Logs must not contain raw tokens
- Logs must not expose local filesystem paths in public reports
- Large payloads referenced by artifact ID or report link
- Media analysis uses structured result artifacts, not log blobs

---

## Metrics Baseline

| Metric | Type | Description |
|--------|------|-------------|
| `media.render.jobs.started` | counter | Render jobs started |
| `media.render.jobs.completed` | counter | Render jobs completed |
| `media.render.jobs.failed` | counter | Render jobs failed |
| `media.render.duration_ms` | histogram | Render duration |
| `media.provider_binding.selected_provider` | counter | Provider selection |
| `media.storage.materialization.duration_ms` | histogram | Materialization duration |
| `media.product.created` | counter | Products created |
| `media.verification.checks.passed` | counter | Verification passed |
| `media.verification.checks.failed` | counter | Verification failed |
| `media.agent.tasks.completed` | counter | Agent tasks completed |
| `media.agent.tasks.failed` | counter | Agent tasks failed |
| `media.external.endpoint.latency_ms` | histogram | External endpoint latency |
| `media.external.endpoint.failures` | counter | External endpoint failures |

**Rules:**
- Avoid high-cardinality labels
- No secrets or raw URLs with tokens as labels
- Metrics are PLANNED, not implemented in OBS.0

---

## Correlation Model

| Field | Links To |
|-------|----------|
| TASK_ID | agent_run_id, branch, commit, PR |
| agent_run_id | TASK_ID, branch, commit |
| render_job_id | product_id, artifact_id, provider |
| product_id | render_job_id, artifact_id, storage |
| artifact_id | product_id, storage, report path |
| verification_id | environment, target, commit |
| trace_id | all spans in a trace |
| report path | dashboard URL, R2 URL |

---

## Frontend Observability (PLANNED)

Do not instrument old frontend now. Future rewrite should include:
- Route change events
- Editor interaction events
- Preview render events
- Caption panel events
- Error boundary events

---

## Fluent Bit vs Fluentd

| Tool | Role | Status |
|------|------|--------|
| **Fluent Bit** | Lightweight node/container/worker log forwarder | PLANNED |
| **Fluentd** | Log aggregator (evaluate after OBS.0) | DEFERRED |

Re-evaluate Fluentd only if:
- Complex log routing required
- Multiple sinks need buffering/routing
- Fluent Bit + OTel Collector insufficient
- Plugin ecosystem requirement appears

---

*Generated by Hermes Agent — OBS.0*
