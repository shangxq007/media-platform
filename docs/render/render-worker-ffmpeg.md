# FFmpeg Render Worker

**Date:** 2026-07-09
**Status:** PARTIAL
**Authority:** RENDER-WORKER-FFMPEG.0

---

## Worker Scope

### Included

- ✅ Minimal worker profile (`application-ffmpeg-worker.yml`)
- ✅ Worker runner class (`FFmpegWorkerRunner`)
- ✅ Worker can execute one RenderJob by ID
- ✅ Worker uses Product-backed inputs
- ✅ Worker uses StorageRuntime materialization
- ✅ Worker runs FFmpeg baseline provider
- ✅ Worker stores output through Product/Artifact path

### Excluded

- ❌ OpenCue (NOT STARTED)
- ❌ OpenDAL (not introduced)
- ❌ Artifact DAG (postponed)
- ❌ Distributed scheduling
- ❌ Autoscaling
- ❌ External queue

---

## Worker Execution Model

**Model:** CLI once mode (execute one job by ID)

**Usage:**
```bash
java -jar platform-app.jar --spring.profiles.active=ffmpeg-worker \
     --render.worker.job-id=rj_xxx
```

**Environment variables:**
- `RENDER_WORKER_JOB_ID` — Job ID to execute
- `RENDER_WORKER_ENABLED` — Enable worker mode

---

## Current Status

| Component | Status |
|-----------|--------|
| Worker profile | ✅ CREATED |
| Worker runner | ✅ CREATED (placeholder) |
| Dockerfile | ⚠️ EXISTING (render-worker-javacv.Dockerfile) |
| Full execution | ⚠️ PLACEHOLDER |

---

## Next Steps

- Implement full RenderJob execution in FFmpegWorkerRunner
- Create Dockerfile for FFmpeg worker
- Validate Product-backed worker smoke

---

## Status

- RENDER-WORKER-FFMPEG.0: PARTIAL
- Worker boundary: ESTABLISHED
- Full execution: PLACEHOLDER
