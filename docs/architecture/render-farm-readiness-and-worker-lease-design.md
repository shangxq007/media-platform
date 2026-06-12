# Render Farm Readiness and Worker Lease Design

**Created:** 2026-06-12
**Status:** Design document — no runtime implementation
**Based on:** Render Provider SPI formalization, platform fact gathering report

---

## 1. Executive Summary

The platform currently supports remote render dispatch via an in-memory worker pool (`RemoteRenderDispatcher`) with basic heartbeat and stale worker pruning. However, it lacks persistent worker registry, job-level lease/claim semantics, capability-based scheduling, and artifact transfer contracts.

This document designs the render farm architecture and defines a phased implementation roadmap from the current Level 2 (remote dispatch) to Level 4 (capability-based scheduling). It does NOT implement the system — it provides the design for subsequent task execution.

**Current maturity:** Level 2 — Remote worker dispatch (in-memory, no lease)
**Target maturity:** Level 4 — Capability-based scheduling with persistent lease

---

## 2. Current State

### What Exists

| Component | Status | Location |
|-----------|--------|----------|
| RemoteRenderDispatcher (platform side) | In-memory worker pool, HTTP dispatch | `render-module/.../infrastructure/remote/` |
| RemoteRenderService (worker side) | Receives jobs, routes to provider, sends callback | `remote-render-worker/.../app/` |
| WorkerRegistryService (worker side) | In-memory self-registry | `remote-render-worker/.../app/` |
| Heartbeat | Worker → platform, 120s stale threshold | `RemoteRenderDispatcher.heartbeat()` |
| Stale worker pruning | @Scheduled every 30s, removes workers with no heartbeat | `RemoteRenderDispatcher.pruneStaleWorkers()` |
| Stale job compensation | DB-backed, 30min threshold, @Scheduled every 5min | `StaleRenderJobCompensationService` |
| RemoteRenderProvider | `@Component("remote-ffmpeg")`, supports `remote_*` profiles | `render-module/.../infrastructure/remote/` |
| Temporal workflow | In-process only, no remote worker bridge | `workflow-module` |
| Worker API key auth | Optional `X-Worker-Api-Key` header | `remote-render-worker/.../WorkerApiKeyFilter` |

### What Does NOT Exist

| Gap | Impact |
|-----|--------|
| Persistent worker registry | Workers lost on platform restart |
| Job-level lease/claim | Worker crash → job stuck for 30min until stale compensator |
| Capability-based scheduling | No provider capability matching, only least-loaded IDLE |
| Artifact transfer contract | Worker renders to local FS, no shared storage mechanism |
| Worker auto-registration | Manual `registerWorker()` call required |
| Callback idempotency | Fire-and-forget HTTP, no retry/dedup |
| Temporal ↔ remote worker bridge | Two separate execution paths, not unified |
| Worker resource tracking | No CPU/memory/GPU capacity reporting |

---

## 3. Maturity Level

| Level | Description | Current? |
|-------|-------------|----------|
| **0** | Single-process render (in-process provider) | ✅ Completed |
| **1** | Local worker role (same process, queue drain) | ✅ Completed (POC) |
| **2** | Remote worker dispatch (HTTP, in-memory pool) | ✅ **Current** |
| **3** | Lease-based worker pool (persistent, claim/renew/expire) | ❌ Next target |
| **4** | Capability-based scheduling (provider match, resource fit) | ❌ Design target |
| **5** | Multi-zone/GPU/fair-share render farm | ❌ Future |

**Next target:** Level 3 — persistent worker registry + DB lease model.

---

## 4. Worker Registry Model

### RenderWorker Record

```
RenderWorker {
    workerId: String (PK)
    workerType: RENDER | FONT | TEXT_RENDER | BROWSER_RENDER | GPU | SANDBOX
    status: STARTING | IDLE | BUSY | DRAINING | OFFLINE | FAILED
    version: String                    // application version
    imageTag: String                   // Docker image tag
    hostname: String
    zone: String                       // availability zone / datacenter
    providerIds: List<String>          // ["ffmpeg", "mlt", "gpac"]
    capabilities: WorkerCapabilities   // structured capability report
    runtimeRequirements: Map           // {"ffmpeg": "/usr/bin/ffmpeg", "melt": "/usr/bin/melt"}
    maxConcurrentJobs: int
    activeJobCount: int
    cpuCores: int
    memoryMb: int
    gpuCount: int
    gpuType: String                    // "nvidia-a100", null
    diskFreeMb: long
    lastHeartbeatAt: Instant
    registeredAt: Instant
    expiresAt: Instant                 // auto-expire if no heartbeat
    metadata: Map<String, String>      // extensible
}
```

### WorkerCapabilities Record

```
WorkerCapabilities {
    renderCapabilities: Set<String>    // ["videoTranscode", "subtitleBurnIn", ...]
    fontCapabilities: Set<String>      // ["fontFile", "cjk", "shaping", ...]
    subtitleCapabilities: Set<String>  // ["srt", "ass", "burnIn", ...]
    maxResolution: String              // "3840x2160"
    maxDurationSeconds: long
    supportedFormats: Set<String>      // ["mp4", "webm", "hls"]
    gpuAcceleration: boolean
    distributedExecution: boolean
}
```

### Key Rules

1. Worker self-reports capabilities on startup via `/register` endpoint
2. Platform validates claimed capabilities against known provider implementations
3. Worker claiming `STUB`/`SKELETON` provider capabilities is rejected
4. Worker must re-register on restart (new `registeredAt`)
5. Platform prunes workers with no heartbeat for >120s
6. Worker status transitions: `STARTING → IDLE → BUSY → IDLE` or `→ DRAINING → OFFLINE`

---

## 5. Job Lease Model

### RenderJobLease Record

```
RenderJobLease {
    leaseId: String (PK)
    jobId: String (FK → render_job.id)
    tenantId: String
    workerId: String (FK → render_worker.worker_id)
    providerId: String                 // "ffmpeg", "mlt"
    status: CLAIMED | RUNNING | RENEWED | RELEASED | EXPIRED | FAILED
    leaseVersion: long                 // optimistic lock
    claimedAt: Instant
    leaseUntil: Instant                // lease expiry time
    renewedAt: Instant
    releasedAt: Instant
    attempt: int                       // 1, 2, 3...
    heartbeatTokenHash: String         // HMAC(workerId + jobId + secret)
    failureReason: String
    failureErrorCode: String
    createdByScheduler: String         // "db-lease-scheduler", "temporal"
}
```

### Key Operations

| Operation | Description | Atomicity |
|-----------|-------------|-----------|
| `claimNextJob(workerId, capabilities)` | Find eligible QUEUED job, create CLAIMED lease | `SELECT ... FOR UPDATE SKIP LOCKED` |
| `renewLease(leaseId, workerId, token)` | Extend `leaseUntil` | Version check + workerId match |
| `releaseLease(leaseId, workerId, result)` | Mark RELEASED, update job status | Version check |
| `failLease(leaseId, workerId, error)` | Mark FAILED, requeue or dead-letter | Version check |
| `expireStaleLeases(now)` | Find leases with `leaseUntil < now` | `UPDATE ... WHERE status IN (CLAIMED,RUNNING,RENEWED) AND lease_until < ?` |
| `requeueExpiredJobs(now)` | Move expired lease jobs back to QUEUED | After expireStaleLeases |

### Concurrency Control

- **Claim:** `SELECT ... FROM render_job WHERE status = 'QUEUED' AND ... FOR UPDATE SKIP LOCKED LIMIT 1` — prevents double-claim
- **Renew/Release/Fail:** `UPDATE ... WHERE lease_id = ? AND worker_id = ? AND lease_version = ?` — optimistic lock, returns 0 rows if stale
- **One active lease per job:** Unique constraint on `(job_id) WHERE status IN (CLAIMED, RUNNING, RENEWED)`
- **Lease duration:** Default 10 minutes, renewable in 5-minute increments
- **Max attempts:** 3 (configurable), then dead-letter

---

## 6. Capability-based Scheduling

### Scheduling Input

```
SchedulingRequest {
    jobId: String
    tenantId: String
    requiredRenderCapabilities: Set<String>
    requiredFontCapabilities: Set<String>
    requiredSubtitleCapabilities: Set<String>
    preferredProviderIds: List<String>
    blockedProviderIds: List<String>
    maxResolution: String
    maxDurationSeconds: long
    outputFormat: String
    priority: int                      // 0 = highest
    queueMode: String                  // "production", "experiment", "manual"
}
```

### Scheduling Steps

1. **Provider status filter:** Remove STUB, SKELETON, DEPRECATED, MOCK providers
2. **Provider eligibility filter:** Apply `ProviderEligibility.isEligible()` rules
3. **Capability match:** Check required render/font/subtitle capabilities
4. **Tenant entitlement filter:** Apply `ProviderAccessPolicy` tier restrictions
5. **Worker availability filter:** Only IDLE/BUSY workers with `activeJobCount < maxConcurrentJobs`
6. **Worker capability match:** Worker must support the selected provider
7. **Resource fit:** Worker must have sufficient CPU/memory/GPU/disk
8. **Ranking:** Score by load (prefer less loaded), priority, zone locality, provider preference
9. **Claim:** Atomically claim the best worker + job combination

### Ranking Formula

```
score = workerLoadScore(providerLoad)
      + priorityBonus(job.priority)
      + localityBonus(worker.zone, job.zone)
      + providerPreferenceBonus(worker.providerIds, job.preferredProviderIds)
      - pocPenalty(provider.status == POC)
```

---

## 7. Artifact Contract

### Worker Output Report

```
RenderJobCompletionReport {
    jobId: String
    tenantId: String
    workerId: String
    providerId: String
    attempt: int
    status: COMPLETED | FAILED

    // Artifact references (not file bodies)
    outputArtifactUri: String          // "s3://bucket/tenant/project/job/output.mp4"
    manifestUri: String                // "s3://bucket/tenant/project/job/manifest.json"
    logsUri: String                    // "s3://bucket/tenant/project/job/logs.txt"
    thumbnailUri: String?              // optional
    previewUri: String?                // optional

    // Metadata
    durationMs: long
    checksumSha256: String
    mediaInfo: Map<String, Object>     // ffprobe metadata
    warnings: List<String>
    error: String?                     // if FAILED
    errorCode: String?
    generatedAt: Instant
}
```

### Key Rules

1. Worker uploads artifacts to object storage (S3-compatible)
2. Worker sends completion report via callback (HTTP POST to platform)
3. Platform records artifact references in `render_job` + `artifact` tables
4. Platform does NOT receive file bodies via callback — only URIs
5. Artifact write is idempotent by `(jobId, attempt)` — overwrites on retry
6. Failed job logs are also uploaded and recorded
7. Temp artifacts cleaned up after configurable TTL (default 7 days)

---

## 8. Retry and Stale Compensation

### Retry Policy

| Scenario | Action | Max Attempts |
|----------|--------|-------------|
| Provider error (e.g., FFmpeg crash) | Requeue to different worker/provider | 3 |
| Worker crash (heartbeat lost) | Requeue after lease expires | 3 |
| Artifact upload failure | Requeue (worker-side retry first) | 2 |
| Validation failure (bad output) | Dead-letter (non-retryable) | 1 |
| Quota exceeded | Dead-letter (non-retryable) | 1 |
| Lease expired (worker too slow) | Requeue with longer timeout | 2 |

### Stale Compensation Flow

```
Every 5 minutes:
1. Find leases WHERE status IN (CLAIMED, RUNNING, RENEWED) AND lease_until < now
2. Mark lease as EXPIRED
3. If attempt < maxAttempts: set job status = QUEUED (requeue)
4. If attempt >= maxAttempts: set job status = FAILED + insert into dead_letter
5. Publish RenderJobFailedEvent
6. Record status history
```

### Dead Letter Table

```
render_job_dead_letter {
    id: String (PK)
    job_id: String
    tenant_id: String
    last_worker_id: String
    last_provider_id: String
    last_attempt: int
    failure_reason: String
    failure_error_code: String
    original_payload_json: Text
    created_at: Timestamp
    resolved_at: Timestamp?
    resolved_by: String?
    resolution_notes: String?
}
```

---

## 9. Worker Deployment Strategy

### Recommended: Same Codebase + Multi-Image Target

| Image | Purpose | Base |
|-------|---------|------|
| `media-platform-api` | Main API server | `eclipse-temurin:25-jre` |
| `media-platform-render-worker-ffmpeg` | FFmpeg render worker | `eclipse-temurin:25-jre` + FFmpeg |
| `media-platform-render-worker-mlt` | MLT render worker | `eclipse-temurin:25-jre` + MLT/melt |
| `media-platform-font-worker` | Font processing | `eclipse-temurin:25-jre` + fonttools/HarfBuzz |
| `media-platform-text-render-worker` | Text/Skia rendering | `eclipse-temurin:25-jre` + Skia |
| `media-platform-browser-render-worker` | Remotion/browser | `node:22` + Chrome |
| `media-platform-gpu-worker` | GPU rendering (future) | `nvidia/cuda:12.x` + JDK |

### Why NOT Other Options

| Option | Rejection Reason |
|--------|-----------------|
| Same image + `app.role` flag | Works short-term but doesn't scale to different runtime dependencies |
| Dedicated worker repo/service | Over-engineering for current modular monolith |
| Kubernetes Job per render | Too much operational overhead, no existing K8s Job infrastructure |

### Phase 1 Approach

- Keep current `remote-render-worker` module as-is
- Add `app.worker.type=render-ffmpeg` configuration flag
- Worker self-reports type on registration
- No image changes needed yet

---

## 10. Queue / Workflow Options

| Option | Complexity | Reliability | Observability | Fit for Monolith | Recommendation |
|--------|-----------|-------------|---------------|-----------------|----------------|
| **DB lease queue** | Low | High | High | Excellent | **Phase 1 — Recommended** |
| **Temporal** | Medium | Very High | Very High | Good | Phase 3 — for long-running orchestration |
| **Redis/SQS** | Medium | High | Medium | Medium | Phase 4 — if scale requires |
| **K8s Jobs** | High | High | Medium | Poor | Not recommended for current phase |

### Phase 1: DB Lease Queue

- Use existing `render_job` table + new `render_job_lease` table
- `SELECT ... FOR UPDATE SKIP LOCKED` for claim
- Platform-side scheduler polls for available jobs
- Worker heartbeat via HTTP callback
- Stale compensation via existing `StaleRenderJobCompensationService`

### Phase 2: Temporal Bridge (Optional)

- Temporal workflow starts on job submit
- Temporal activity dispatches to remote worker via lease
- Worker reports back via callback → Temporal activity completes
- Temporal handles retry/retry policy

### Phase 3: Broker (Only if Scale Requires)

- Redis Streams or SQS for job queue
- Worker pulls from queue
- Platform pushes to queue on submit
- Only if DB lease queue becomes bottleneck

---

## 11. Security Model

| Concern | Design |
|---------|--------|
| **Worker authentication** | API key (`X-Worker-Api-Key`) + optional mTLS |
| **Lease token** | HMAC(workerId + jobId + secret) — prevents lease forgery |
| **Tenant isolation** | Worker receives only jobId + tenantId; must not access other tenants' data |
| **Storage credentials** | Worker receives scoped storage credentials per job (not platform-wide) |
| **Signed URL redaction** | Callbacks must not include signed URLs in logs |
| **Provider status eligibility** | Platform enforces STUB/SKELETON/DEPRECATED/MOCK never dispatched |
| **Worker capability validation** | Platform validates worker self-reported capabilities against known providers |
| **Sandbox isolation** | Worker process isolation (future: container per render) |

---

## 12. Observability Model

### Required Fields on All Operations

| Field | Source | Notes |
|-------|--------|-------|
| `jobId` | Render job | Primary correlation key |
| `leaseId` | Lease record | Tracks claim lifecycle |
| `workerId` | Worker registry | Identifies which worker |
| `providerId` | Provider selection | Which render backend |
| `attempt` | Lease record | Retry tracking |
| `tenantId` | Render job | Multi-tenant isolation |
| `traceId` | MDC | Distributed tracing |
| `stage` | Operation | "claim", "execute", "heartbeat", "release", "expire" |
| `errorCode` | Error model | Structured error codes |

### Metrics to Track

| Metric | Type | Labels |
|--------|------|--------|
| `render.lease.claimed` | Counter | provider, worker |
| `render.lease.released` | Counter | provider, status |
| `render.lease.expired` | Counter | provider |
| `render.worker.active_jobs` | Gauge | worker |
| `render.worker.heartbeat_age_seconds` | Gauge | worker |
| `render.queue.depth` | Gauge | status |
| `render.queue.wait_time_seconds` | Histogram | provider |
| `render.artifact.upload_duration_ms` | Histogram | provider |

---

## 13. Database Model Proposal

**PROPOSAL ONLY — no migration to be created.**

### render_worker

```sql
CREATE TABLE render_worker (
    worker_id VARCHAR(64) PRIMARY KEY,
    worker_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    version VARCHAR(64),
    image_tag VARCHAR(128),
    hostname VARCHAR(256),
    zone VARCHAR(64),
    provider_ids TEXT,              -- JSON array
    capabilities_json TEXT,         -- JSON object
    runtime_requirements_json TEXT, -- JSON object
    max_concurrent_jobs INT NOT NULL DEFAULT 1,
    active_job_count INT NOT NULL DEFAULT 0,
    cpu_cores INT,
    memory_mb INT,
    gpu_count INT DEFAULT 0,
    gpu_type VARCHAR(64),
    disk_free_mb BIGINT,
    last_heartbeat_at TIMESTAMP NOT NULL,
    registered_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP,
    metadata_json TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

### render_job_lease

```sql
CREATE TABLE render_job_lease (
    lease_id VARCHAR(64) PRIMARY KEY,
    job_id VARCHAR(64) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    worker_id VARCHAR(64) NOT NULL,
    provider_id VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    lease_version BIGINT NOT NULL DEFAULT 1,
    claimed_at TIMESTAMP NOT NULL,
    lease_until TIMESTAMP NOT NULL,
    renewed_at TIMESTAMP,
    released_at TIMESTAMP,
    attempt INT NOT NULL DEFAULT 1,
    heartbeat_token_hash VARCHAR(128),
    failure_reason TEXT,
    failure_error_code VARCHAR(64),
    created_by_scheduler VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_lease_job FOREIGN KEY (job_id) REFERENCES render_job(id),
    CONSTRAINT fk_lease_worker FOREIGN KEY (worker_id) REFERENCES render_worker(worker_id)
);

CREATE UNIQUE INDEX ux_lease_active_job ON render_job_lease(job_id)
    WHERE status IN ('CLAIMED', 'RUNNING', 'RENEWED');
CREATE INDEX ix_lease_worker_status ON render_job_lease(worker_id, status);
CREATE INDEX ix_lease_until ON render_job_lease(lease_until) WHERE status IN ('CLAIMED', 'RUNNING', 'RENEWED');
```

### render_job_dead_letter

```sql
CREATE TABLE render_job_dead_letter (
    id VARCHAR(64) PRIMARY KEY,
    job_id VARCHAR(64) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    last_worker_id VARCHAR(64),
    last_provider_id VARCHAR(64),
    last_attempt INT NOT NULL,
    failure_reason TEXT,
    failure_error_code VARCHAR(64),
    original_payload_json TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP,
    resolved_by VARCHAR(128),
    resolution_notes TEXT
);

CREATE INDEX dl_job_id ON render_job_dead_letter(job_id);
CREATE INDEX dl_tenant ON render_job_dead_letter(tenant_id);
```

---

## 14. API / Internal Port Proposal

### WorkerRegistryPort

```java
public interface WorkerRegistryPort {
    void registerWorker(RenderWorker worker);
    void deregisterWorker(String workerId);
    void updateHeartbeat(String workerId);
    Optional<RenderWorker> findWorker(String workerId);
    List<RenderWorker> findAvailableWorkers(String providerId);
    void updateWorkerStatus(String workerId, WorkerStatus status);
    void pruneStaleWorkers(Instant threshold);
}
```

### RenderJobLeasePort

```java
public interface RenderJobLeasePort {
    Optional<RenderJobLease> claimNextJob(String workerId, WorkerCapabilities capabilities);
    boolean renewLease(String leaseId, String workerId, String token);
    boolean releaseLease(String leaseId, String workerId, RenderJobCompletionReport report);
    boolean failLease(String leaseId, String workerId, String errorCode, String reason);
    void expireStaleLeases(Instant now);
    void requeueExpiredJobs(Instant now);
}
```

### RenderSchedulerPort

```java
public interface RenderSchedulerPort {
    Optional<RenderJobLease> scheduleJob(SchedulingRequest request);
    int scheduleBatch(List<SchedulingRequest> requests);
    QueueStatus getQueueStatus();
}
```

### RenderArtifactCompletionPort

```java
public interface RenderArtifactCompletionPort {
    void recordCompletion(RenderJobCompletionReport report);
    void recordFailure(RenderJobCompletionReport report);
    Optional<RenderJobCompletionReport> getCompletionReport(String jobId);
}
```

---

## 15. Implementation Roadmap

### Phase 1: Foundation (DB Lease + Worker Registry) ✅ COMPLETED

| Deliverable | Status | Notes |
|-------------|--------|-------|
| `render_worker` + `render_job_lease` tables (Flyway V7) | ✅ Done | |
| `RenderWorkerRepository` | ✅ Done | register, heartbeat, status transitions, stale detection |
| `RenderJobLeaseRepository` | ✅ Done | create, find, renew, release, fail, expire |
| `RenderWorkerRegistryService` | ✅ Done | register, heartbeat, drain, offline, prune |
| `RenderJobLeaseService` | ✅ Done | claim, renew, release, fail, expire |
| `StaleRenderJobLeaseCompensationService` | ✅ Done | @Scheduled stale lease expiration |
| Tests (40+) | ✅ Done | Repository + service tests for all operations |

### Phase 2: Capability Scheduling + Artifact Contract

| Deliverable | Effort | Dependencies |
|-------------|--------|-------------|
| `RenderSchedulerPort` with capability matching | Medium | Phase 1 |
| Provider status/eligibility integration in scheduler | Small | ProviderEligibility |
| Tenant entitlement integration in scheduler | Medium | ProviderAccessPolicy |
| `RenderArtifactCompletionPort` implementation | Medium | Storage module |
| Worker completion callback with artifact URIs | Medium | Storage module |
| Failed job logs upload | Small | Storage module |
| Dead letter table + admin retry API | Medium | RenderJobLease |
| Worker resource tracking (CPU/mem/GPU) | Medium | Worker heartbeat |
| Integration tests for full claim→execute→complete cycle | Medium | All above |

### Phase 3: Temporal Bridge + Multi-Image Workers

| Deliverable | Effort | Dependencies |
|-------------|--------|-------------|
| Temporal activity bridge to lease-based dispatch | Medium | Phase 2 |
| Multi-image Docker build targets | Medium | Docker infra |
| Worker type-based scheduling | Small | WorkerRegistryPort |
| Provider health aggregation | Medium | Worker heartbeat |
| End-to-end render farm smoke test | Medium | All above |

### Phase 4: Advanced Scheduling (Future)

| Deliverable | Effort | Dependencies |
|-------------|--------|-------------|
| Fair-share scheduling (tenant quotas) | Large | Phase 3 |
| Multi-zone scheduling | Medium | Phase 3 |
| GPU worker support | Large | Phase 3 |
| Autoscaling hooks | Medium | K8s metrics |
| Worker pool dashboard | Medium | All above |

---

## 16. Non-goals

- ❌ Implement full distributed scheduler now
- ❌ Introduce message broker (Redis/SQS/Kafka)
- ❌ Kubernetes Job per render
- ❌ GPU/CUDA worker
- ❌ BMF runtime
- ❌ New render provider runtime
- ❌ Microservice extraction
- ❌ Modify frontend
- ❌ Modify GitOps
- ❌ Create Flyway migration (proposal only)

---

## 17. Open Decisions

| # | Decision | Options | Recommendation |
|---|----------|---------|----------------|
| 1 | **Lease claim SQL dialect** | PostgreSQL SKIP LOCKED vs H2 compatibility | PostgreSQL SKIP LOCKED (prod), H2 emulation (test) |
| 2 | **Worker auto-registration endpoint** | REST vs gRPC | REST (consistent with existing worker API) |
| 3 | **Storage credential scoping** | Pre-signed URLs vs scoped IAM vs STS | Pre-signed URLs (simplest, existing pattern) |
| 4 | **Temporal adoption scope** | Lease dispatch only vs full workflow | Lease dispatch only (preserve DB lease as primary) |
| 5 | **Worker image strategy** | Same image + flag vs multi-image | Multi-image (different runtime deps) |
| 6 | **Heartbeat frequency** | 10s vs 30s vs 60s | 30s (balance between freshness and load) |
| 7 | **Lease duration** | 5min vs 10min vs 30min | 10min default, configurable per provider |
| 8 | **Max retry attempts** | 3 vs 5 | 3 (configurable) |
| 9 | **Dead letter retention** | 7 days vs 30 days | 30 days, admin purge |
| 10 | **Worker capability self-report vs platform probe** | Trust worker vs probe | Trust worker + platform validation |

---

## 18. Code References

| Concept | Current File | Design Extension |
|---------|-------------|-----------------|
| RemoteRenderDispatcher | `render-module/.../infrastructure/remote/RemoteRenderDispatcher.java` | Replaced by `WorkerRegistryPort` + `RenderJobLeasePort` |
| RemoteRenderService | `remote-render-worker/.../app/RemoteRenderService.java` | Extended with lease claim/release |
| StaleRenderJobCompensationService | `render-module/.../app/StaleRenderJobCompensationService.java` | Integrated with lease expiration |
| RenderProviderRouter | `render-module/.../infrastructure/RenderProviderRouter.java` | Extended with capability scheduling |
| ProviderEligibility | `render-module/.../infrastructure/ProviderEligibility.java` | Used by scheduler |
| RenderJobRepository | `render-module/.../infrastructure/RenderJobRepository.java` | Extended with lease-aware queries |
