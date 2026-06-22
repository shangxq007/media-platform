---
status: blueprint
last_verified: 2026-06-22
scope: all
truth_level: target
owner: platform
---

# Module Blueprint: Render Pipeline

> **Reality Check (2026-06-22):** Render module has 600 production files, 138 test files. 7+ active providers (FFmpeg, GStreamer, MLT, Remotion, GPAC, OFX, Natron). State machine with 10+ states. Incremental render with DAG-based pipeline execution. Cache with content-addressable hashing. Render farm with worker lease management. Significantly more advanced than this blueprint describes.

## 1. Purpose

The Render Pipeline module is responsible for orchestrating media rendering workflows, managing render jobs, and coordinating with various render providers (FFmpeg, JavaCV, OFX, Natron, etc.).

## 2. Responsibilities

- Create and manage render job lifecycle
- Coordinate with render providers for actual media processing
- Manage render queues and scheduling
- Track render progress and status
- Handle render artifacts and storage
- Support multi-provider orchestration
- Manage render presets and profiles

## 3. Non-Responsibilities

- Actual media encoding/decoding (delegated to providers)
- Storage management (delegated to storage-module)
- Billing calculations (delegated to billing-module)
- User authentication (delegated to identity-access-module)

## 4. Public Ports / APIs

### REST API
- `POST /api/v1/render/jobs` - Create render job
- `GET /api/v1/render/jobs/{id}` - Get job status
- `GET /api/v1/render/jobs` - List jobs
- `POST /api/v1/render/jobs/{id}/cancel` - Cancel job

### Internal APIs
- `RenderJobService` - Core job management
- `RenderProviderRegistry` - Provider management
- `RenderQueueManager` - Queue orchestration

## 5. Domain Model

### RenderJob
- id, project_id, timeline_snapshot_id
- profile, status, created_at
- tenant_id, trace_id
- pipeline_plan_json, pipeline_execution_json

### RenderProvider
- provider_code, provider_type
- health_status, capabilities
- priority, cost_estimate

### RenderQueue
- job_id, tenant_id
- status, priority
- created_at, updated_at

## 6. Events Published

- `RenderJobCreated` - When job is created
- `RenderJobStarted` - When job processing begins
- `RenderJobCompleted` - When job finishes successfully
- `RenderJobFailed` - When job fails
- `RenderJobCancelled` - When job is cancelled

## 7. Events Consumed

- `TimelineSnapshotCreated` - From editing module
- `ProjectCreated` - From project module

## 8. Dependencies Allowed

- `storage-module` - For artifact storage
- `shared-kernel` - For common types
- `identity-access-module` - For tenant context

## 9. Dependencies Forbidden

- Direct database access from other modules
- Direct provider implementation (must use registry)
- Direct billing calculations

## 10. Extension Points

- `RenderProvider` interface - For adding new render engines
- `RenderPipeline` interface - For custom pipeline strategies
- `RenderScheduler` interface - For custom scheduling algorithms

## 11. Security / Tenant Rules

- All render jobs are tenant-scoped
- Jobs can only access resources within their tenant
- Provider access controlled by entitlements
- Rate limiting per tenant

## 12. Persistence Ownership

- `render_job` table
- `render_job_status_history` table
- `render_worker` table
- `render_job_lease` table
- `render_job_queue` table

## 13. Observability

- Metrics: job count, processing time, queue depth
- Traces: job lifecycle, provider invocation
- Logs: job status changes, errors

## 14. Current Status

**Status: Significantly Implemented** (600 production files, 138 test files)

### Implemented
- Job lifecycle management with 10+ state machine states
- 7+ render providers: FFmpeg, GStreamer, MLT, Remotion, GPAC, OFX, Natron
- Incremental render with DAG-based pipeline execution
- Content-addressable render cache with S3-backed remote storage
- Render farm with worker lease management and heartbeat
- Subtitle pipeline (SRT, WebVTT, ASS, burn-in via libass)
- Font management with security scanning
- OTIO (OpenTimelineIO) timeline support
- AAF format conversion
- Billing integration via BillingDecisionEngine and BillingEnforcementService
- Quota integration via QuotaBucketSummary (in-memory)

### Partially Implemented
- Multi-provider orchestration (basic selection policy, no cost optimization)
- Advanced scheduling (DAG execution exists, no priority-based scheduling)
- Auto-scaling (K8s HPA exists, no application-level auto-scaling)

### Not Implemented
- Real-time progress tracking (WebSocket/SSE)
- Cost optimization (cost estimation exists in billing-module, not integrated into provider selection)

## 15. Gap to Blueprint

| Blueprint Feature | Current Status | Gap |
|-------------------|----------------|-----|
| Multi-provider orchestration | 7+ providers with selection policy | **Low** — basic orchestration exists |
| Advanced scheduling | DAG-based pipeline execution | **Medium** — no priority-based scheduling |
| Cost optimization | CostEstimationService exists, not integrated | **Medium** — needs provider selection integration |
| Real-time progress | Polling only | **Medium** — no WebSocket/SSE |
| Provider health monitoring | RenderProviderHealthCheck exists | **Low** — basic health checks exist |
| Auto-scaling workers | K8s HPA 1-10 replicas | **Medium** — infrastructure-level only |
