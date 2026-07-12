# FFmpeg Worker Polling Mode

**Date:** 2026-07-09
**Status:** COMPLETE
**Authority:** RENDER-WORKER-POLLING.0

---

## Worker Modes

| Mode | Description | Command |
|------|-------------|---------|
| `once` | Execute one job by ID | `--render.worker.job-id=rj_xxx` |
| `poll` | Long-running polling | `--render.worker.mode=poll` |

---

## Polling Contract

| Setting | Default | Description |
|---------|---------|-------------|
| `render.worker.mode` | `once` | Worker mode |
| `render.worker.poll-interval` | `5s` | Poll interval |
| `render.worker.max-concurrent-jobs` | `1` | Max concurrency |

---

## Polling Flow

1. **Start** — Worker starts in poll mode
2. **Find** — Look for oldest QUEUED RenderJob
3. **Claim** — Atomic update QUEUED → EXECUTING
4. **Execute** — `RenderWorkerExecutionService.executeOnce(jobId)`
5. **Complete/Fail** — Mark COMPLETED or FAILED
6. **Continue** — Loop back to step 2

---

## Docker Commands

### Once Mode
```bash
docker run --rm \
  -e DATABASE_URL=jdbc:postgresql://... \
  -e RENDER_WORKER_JOB_ID=rj_xxx \
  ghcr.io/shangxq007/platform-ffmpeg-worker:latest
```

### Poll Mode
```bash
docker run --rm \
  -e DATABASE_URL=jdbc:postgresql://... \
  -e RENDER_WORKER_MODE=poll \
  -e RENDER_WORKER_POLL_INTERVAL=5s \
  ghcr.io/shangxq007/platform-ffmpeg-worker:latest
```

---

## Implementation

| Component | Status |
|-----------|--------|
| FFmpegWorkerRunner | ✅ Supports once + poll |
| RenderJobRepository | ✅ findQueuedJobs + claimJob |
| Atomic claim | ✅ QUEUED → EXECUTING |

---

## Status

- RENDER-WORKER-POLLING.0: COMPLETE
- Polling mode: IMPLEMENTED
- CLI once mode: PRESERVED
