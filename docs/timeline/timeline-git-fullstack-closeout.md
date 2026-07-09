# Timeline Git Full-Stack Closeout

**Date:** 2026-07-09
**Status:** COMPLETE
**Milestone:** TIMELINE-GIT-FULLSTACK.0

---

## Milestone Status

**Timeline Git Full-Stack: COMPLETE**

---

## Stable Capabilities

| Capability | Backend | Worker | Frontend | Status |
|------------|---------|--------|----------|--------|
| Revision Model | ✅ | — | — | STABLE |
| Revision API | ✅ | — | — | STABLE |
| Snapshot | ✅ | — | ✅ | STABLE |
| Semantic Diff | ✅ | — | ✅ | STABLE |
| Restore | ✅ | — | ✅ | STABLE |
| Render by Revision | ✅ | ✅ | ✅ | STABLE |
| Provenance | ✅ | — | ✅ | STABLE |
| Product-backed RAW_MEDIA | ✅ | ✅ | — | STABLE |
| StorageRuntime | ✅ | ✅ | — | STABLE |
| FFmpeg Worker Once | — | ✅ | — | STABLE |
| FFmpeg Worker Poll | — | ✅ | — | STABLE |
| Frontend Console | — | — | ✅ | STABLE |

---

## Experimental Capabilities

| Capability | Status | Notes |
|------------|--------|-------|
| Merge | MERGE_EXPERIMENTAL | DB blocker fixed, not MVP |
| Merge UX | NOT IMPLEMENTED | No stable frontend |

---

## Deferred Capabilities

| Capability | Status | Notes |
|------------|--------|-------|
| Branch | NOT IMPLEMENTED | — |
| Patch Application | NOT IMPLEMENTED | — |
| Timeline Patch DSL | NOT INTRODUCED | — |
| ANTLR | NOT INTRODUCED | — |
| CRDT/Multiplayer | NOT IMPLEMENTED | — |
| Artifact DAG | POSTPONED | — |
| OpenCue | NOT STARTED | — |
| OpenDAL | DEFERRED | Future StorageRuntime adapter |
| OpenAssetIO | DEFERRED | — |
| Full Video Editor | NOT IMPLEMENTED | — |

---

## Canonical Data Flow

```
User/API → TimelineRevision
  ↓
TimelineAssetRef.productId → RAW_MEDIA Product
  ↓
storageReferenceId → StorageRuntime materialization
  ↓
RenderJob (QUEUED → EXECUTING → COMPLETED)
  ↓
FFmpeg Worker (once/poll) → atomic claim
  ↓
Product/Artifact output
  ↓
Frontend /dev/timeline-git display
```

---

## Frontend Handoff

**Route:** `/dev/timeline-git`

**Capabilities:**
- Revision list with head marker
- Revision detail panel
- Snapshot JSON viewer
- Semantic diff viewer
- Render selected revision
- Restore with confirmation
- Provenance display
- Merge experimental label

**Limitations:**
- Not full editor
- No patch apply
- No branch UI
- No stable merge UX

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

**Features:**
- Atomic claim: QUEUED → EXECUTING
- Max concurrency: 1
- Graceful shutdown: supported
- Product-backed input: supported

---

## Storage Handoff

**Contract:** `docs/storage/storage-runtime-contract.md`

**Key Points:**
- storageReferenceId is internal stable reference
- RAW_MEDIA Product requires storageReferenceId
- Worker materializes via StorageRuntime
- No raw paths in public API
- OpenDAL not introduced (future adapter only)

---

## Merge Status

**Classification:** MERGE_EXPERIMENTAL
**Included in MVP:** NO
**DB Blocker:** FIXED (SHA-256 hash)
**Stable UX:** NO

---

## Regression Checklist

### Backend
- [x] List revisions
- [x] Get head
- [x] Get snapshot
- [x] Compare revisions
- [x] Render selected revision
- [x] Restore revision
- [x] Provenance

### Worker
- [x] Once mode
- [x] Poll mode
- [x] Atomic claim
- [x] Product-backed input
- [x] Artifact output

### Frontend
- [x] /dev/timeline-git
- [x] Revision list
- [x] Detail/Snapshot/Diff
- [x] Render/Restore
- [x] Provenance

### Storage
- [x] RAW_MEDIA Product storageReferenceId
- [x] Materialization
- [x] Output artifact access
- [x] No raw path exposure

---

## Next-Phase Roadmap

### Option A: Frontend Polish
- FRONTEND-RENDER-JOB-STATUS.0
- FRONTEND-TIMELINE-DIFF-VISUALIZATION.0
- FRONTEND-TIMELINE-RESTORE-UX.0

### Option B: Worker Hardening
- RENDER-WORKER-RECOVERY.0
- RENDER-WORKER-CLAIMING-HARDENING.0
- RENDER-WORKER-CI.0

### Option C: Storage/Provider
- STORAGE-RUNTIME-PROVIDER-MATRIX.0
- STORAGE-R2-PREVIEW-HARDENING.0

### Option D: Merge (if needed)
- TIMELINE-MERGE-MVP-PLANNING.0

### Option E: OpenCue (much later)
- OPENCUE-WORKER-EXECUTION.0

**Recommendation:** Do not start OpenCue until worker recovery/claiming is stable.

---

## Key Commits

| Commit | Description |
|--------|-------------|
| ccd34b0 | Timeline Git MVP contract |
| 3352730 | RAW_MEDIA Product materialization |
| ce7d070 | FFmpeg worker execution |
| 662382c | FFmpeg worker image |
| 53cb1dc | StorageRuntime contract |
| 6348290 | FFmpeg worker polling |
| b9e2e84 | Merge DB fix |
| fa76511 | Frontend Timeline Git console |

---

## Agent Handoff Rules

**Do NOT:**
- Reintroduce Artifact DAG
- Promote merge to MVP
- Introduce OpenCue/OpenDAL unless task says
- Treat /dev/timeline-git as full editor
- Expose raw paths/secrets
- Expose generated FFmpeg/ASS as canonical content

**DO:**
- Use inputProductIds/storageReferenceId as worker input contract
- Use existing StorageRuntime for materialization
- Keep merge as MERGE_EXPERIMENTAL
- Keep Artifact DAG as POSTPONED
- Keep OpenCue as NOT STARTED
