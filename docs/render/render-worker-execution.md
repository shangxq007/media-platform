# FFmpeg Worker Execution

**Date:** 2026-07-09
**Status:** COMPLETE
**Authority:** RENDER-WORKER-EXECUTION.0

---

## CLI Once Contract

**Command:**
```bash
java -jar platform-app.jar --spring.profiles.active=ffmpeg-worker \
     --render.worker.job-id=rj_xxx
```

**Environment variables:**
- `RENDER_WORKER_JOB_ID` — Job ID to execute
- `RENDER_WORKER_ENABLED` — Enable worker mode

---

## Worker Execution Flow

1. **Start** — Application starts with `ffmpeg-worker` profile
2. **Load** — `FFmpegWorkerRunner` reads `render.worker.job-id`
3. **Validate** — Job exists and is executable (not COMPLETED/CANCELLED)
4. **Execute** — `RenderWorkerExecutionService.executeOnce(jobId)`
5. **Delegate** — `RenderJobExecutionService.execute(tenantId, jobId)`
6. **Result** — Exit 0 on success, exit 1 on failure

---

## Implementation

| Component | Status |
|-----------|--------|
| FFmpegWorkerRunner | ✅ IMPLEMENTED |
| RenderWorkerExecutionService | ✅ CREATED |
| Worker profile | ✅ EXISTS |
| CLI once mode | ✅ WORKING |

---

## Status

- RENDER-WORKER-EXECUTION.0: COMPLETE
- Worker execution: IMPLEMENTED
- CLI once mode: WORKING
