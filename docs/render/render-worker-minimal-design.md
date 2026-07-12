# Render Worker Minimal Design

**Status:** BOOTSTRAPPED (Temporary Preview Path)
**Updated:** 2026-07-07

---

## Current Implementation

FFmpeg is temporarily packaged into the platform-api preview image to validate the minimal execution and artifact-output loop before OpenCue integration.

### Why FFmpeg in platform-api (temporary)

- Reduce validation variables before OpenCue integration
- Prove: API submit → execution → provider → FFmpeg → output → artifact metadata
- Avoid debugging OpenCue scheduling + worker image + volumes + provider + artifact all at once
- Establish single-node local executor baseline

### What this is NOT

- NOT final production architecture
- NOT OpenCue replacement
- NOT production render worker
- NOT end-to-end preview render success

### What this IS

- Temporary preview bootstrap path
- Single-node local executor baseline
- Pre-OpenCue validation strategy
- Dev/preview smoke support path

---

## Long-Term Architecture

```
platform-api (control plane)
  ↓ submits
OpenCue (scheduler)
  ↓ dispatches
render-worker-ffmpeg (execution plane)
  ↓ produces
Artifact storage
```

### Future Split: FFMPEG-RUNTIME-SPLIT.0

After OpenCue worker execution is stable:
- Remove/disable FFmpeg from platform-api production runtime
- Create dedicated render-worker-ffmpeg image
- Schedule via OpenCue
- Keep platform-api FFmpeg only under dev/preview/CI smoke profiles

---

## Execution Flow (Current)

```
POST /api/v1/render/jobs/{id}/execute
  → RenderOrchestratorService.executeExistingRenderJob()
  → RenderProviderRouter.route()
  → FFmpegRenderProvider.render()
  → ProcessToolRunner.execute()
  → Output file
  → Artifact metadata
```

---

## Classification

| Component | Status |
|-----------|--------|
| FFmpeg in image | ✅ Installed |
| Provider enabled | ✅ Config enabled |
| Worker | In-process submit fallback |
| Artifact | PENDING VALIDATION |
| OpenCue | NOT STARTED |

**Execution Plane: PARTIAL**
