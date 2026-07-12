# Render Execution Plane Full-Stack Closeout

**Date:** 2026-07-09
**Status:** COMPLETE
**Milestone:** RENDER-EXECUTION-PLANE-FULLSTACK.0

---

## Milestone Status

**Render Execution Plane Full-Stack: COMPLETE**

---

## Stable Capabilities

| Capability | Backend | Worker | Frontend | Status |
|------------|---------|--------|----------|--------|
| Render by Revision | ✅ | — | ✅ | STABLE |
| Product-backed RAW_MEDIA | ✅ | ✅ | — | STABLE |
| StorageRuntime contract | ✅ | ✅ | — | STABLE |
| FFmpeg Worker Once | — | ✅ | — | STABLE |
| FFmpeg Worker Poll | — | ✅ | — | STABLE |
| Atomic Claim | ✅ | ✅ | — | STABLE |
| Basic Multi-worker Safety | ✅ | ✅ | — | STABLE |
| Stale EXECUTING Recovery | ✅ | ✅ | — | STABLE |
| Bounded Retry | ✅ | ✅ | — | STABLE |
| Failure Classification | ✅ | ✅ | — | STABLE |
| Output Idempotency | ✅ | ✅ | — | STABLE |
| Lifecycle Observability | ✅ | ✅ | ✅ | STABLE |
| Persistent Events | ✅ | — | ✅ | STABLE |
| Metrics Summary | ✅ | — | ✅ | STABLE |
| Event Retention | ✅ | — | — | STABLE |
| RenderJob Status Panel | — | — | ✅ | STABLE |
| Worker Health Panel | — | — | ✅ | STABLE |

---

## Experimental Capabilities

| Capability | Status | Notes |
|------------|--------|-------|
| Merge | MERGE_EXPERIMENTAL | DB blocker fixed, not MVP |

---

## Deferred Capabilities

| Capability | Status | Notes |
|------------|--------|-------|
| Artifact DAG | POSTPONED | — |
| OpenCue | NOT STARTED | — |
| OpenDAL | DEFERRED | Future StorageRuntime adapter |
| OpenAssetIO | DEFERRED | — |
| Branch | NOT IMPLEMENTED | — |
| Patch Application | NOT IMPLEMENTED | — |
| Timeline Patch DSL | NOT INTRODUCED | — |
| ANTLR | NOT INTRODUCED | — |
| CRDT/Multiplayer | NOT IMPLEMENTED | — |
| Admin Console | NOT IMPLEMENTED | /admin/* future |
| User App | NOT IMPLEMENTED | /app/* future |
| Tailwind | NOT SELECTED | — |
| StyleX | SCOPED POC | — |
| Astryx | SCOPED POC | — |

---

## Canonical Render Execution Flow

```
User selects TimelineRevision
  ↓
Render API creates RenderJob (QUEUED)
  ↓
FFmpeg Worker polling finds QUEUED job
  ↓
Atomic claim: QUEUED → EXECUTING
  ↓
StorageRuntime materializes Product-backed RAW_MEDIA inputs
  ↓
FFmpeg renders output
  ↓
Output idempotency guard checks existing output
  ↓
StorageRuntime stores output
  ↓
Product/Artifact output created/reused
  ↓
Output verified → EXECUTING → COMPLETED
  ↓
Frontend displays status/events/metrics
```

---

## Failure/Retry/Recovery Flow

```
EXECUTING fails safely
  ↓
Failure classified (retryable/deterministic)
  ↓
RenderJob → FAILED
  ↓
If retryable + within bounded attempts:
  FAILED → QUEUED (retry)
  ↓
Worker claims again
  ↓
If stale EXECUTING:
  Recovery marks FAILED or requeues
  ↓
If crash after output created:
  Output idempotency guard detects existing output
  Skip re-execution → COMPLETED
```

---

## Frontend Surface Strategy

| Surface | Route | Status |
|---------|-------|--------|
| Dev Console | /dev/* | IMPLEMENTED |
| Admin Console | /admin/* | FUTURE |
| User App | /app/* | FUTURE |

**Current:** `/dev/timeline-git` (internal diagnostics)

---

## Frontend Technology Decision

| Technology | Status |
|------------|--------|
| React 19 | ✅ BASELINE |
| TanStack Router | ✅ BASELINE |
| TanStack Query | ✅ BASELINE |
| Zustand | ✅ BASELINE |
| Zod | ✅ BASELINE |
| TypeScript | ✅ BASELINE |
| Tailwind | NOT SELECTED |
| StyleX | SCOPED POC |
| Astryx | SCOPED POC |

---

## Worker Handoff

**Image:** `ghcr.io/shangxq007/platform-ffmpeg-worker:latest`

**Commands:**
```bash
# Once mode
docker run --rm -e RENDER_WORKER_JOB_ID=rj_xxx ghcr.io/shangxq007/platform-ffmpeg-worker:latest

# Poll mode
docker run --rm -e RENDER_WORKER_MODE=poll -e RENDER_WORKER_POLL_INTERVAL=5s ghcr.io/shangxq007/platform-ffmpeg-worker:latest
```

**State Machine:**
- QUEUED → EXECUTING (atomic claim)
- EXECUTING → COMPLETED (output verified)
- EXECUTING → FAILED (safe failure)
- Stale EXECUTING → FAILED/QUEUED (recovery)
- FAILED [RETRYABLE] → QUEUED (bounded retry)

---

## Observability Handoff

| Component | Status |
|-----------|--------|
| Persistent event table | ✅ render_job_lifecycle_events |
| Event types | ✅ 13+ types |
| Metrics | ✅ State/Health/Warnings |
| Retention | ✅ 30d default |
| In-memory store | ✅ CACHE only |

---

## Next-Phase Roadmap

### Option A: Frontend Design System POC
- FRONTEND-DESIGN-SYSTEM-POC.0
- Evaluate Astryx/StyleX
- Compare existing local styling

### Option B: Admin Console
- FRONTEND-ADMIN-RENDER-JOBS.0
- FRONTEND-ADMIN-PROJECTS.0

### Option C: User App
- FRONTEND-USER-RENDER-HISTORY.0
- FRONTEND-USER-PROJECT-WORKSPACE.0

### Option D: Worker Hardening
- RENDER-WORKER-OUTPUT-CLEANUP.0
- RENDER-WORKER-CI.0

### Option E: Storage/Provider
- STORAGE-RUNTIME-PROVIDER-MATRIX.0
- STORAGE-R2-PREVIEW-HARDENING.0

### Option F: OpenCue (much later)
- OPENCUE-WORKER-EXECUTION.0_LATER

---

## Key Commits

| Commit | Description |
|--------|-------------|
| e433bd6 | Timeline Git full-stack closeout |
| fa76511 | Timeline Git frontend console |
| 6348290 | FFmpeg worker polling |
| c6f1054 | Stale EXECUTING recovery |
| 9a25a45 | Claim hardening |
| d934400 | Bounded retry |
| 0020a23 | Lifecycle observability |
| 1dc4691 | Output idempotency |
| 48145f0 | Persistent events |
| 44e9056 | Metrics summary |
| 43ee5ca | Event retention |
| bc02b34 | RenderJob status panel |

---

## Agent Handoff Rules

**Do NOT:**
- Reintroduce Artifact DAG
- Promote merge to MVP
- Introduce OpenCue/OpenDAL unless task says
- Expose dev diagnostics to normal users
- Globally introduce Tailwind/StyleX/Astryx
- Expose raw paths/secrets
- Expose generated FFmpeg/ASS as canonical content

**DO:**
- Use inputProductIds/storageReferenceId as worker input contract
- Use existing StorageRuntime for materialization
- Keep merge as MERGE_EXPERIMENTAL
- Keep Artifact DAG as POSTPONED
- Keep OpenCue as NOT STARTED
- Treat /dev/timeline-git as internal console
