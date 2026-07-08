# DEV-RESUME.0 — Current Development Status

**Updated:** 2026-07-07
**Branch:** integration/vs1
**Commit:** 11769df

---

## Current Status

| Component | Classification |
|-----------|---------------|
| Control Plane | **READY** |
| Execution Plane | **PARTIAL** |
| FFmpeg Runtime | **BOOTSTRAPPED** (temporary) |
| FFmpeg Provider Config | **ENABLED** |
| Worker Strategy | in-process submit fallback |
| Artifact Metadata | **PENDING VALIDATION** |
| Artifact Output File | **NOT VERIFIED** |
| Preview Render E2E | **NOT COMPLETE** |
| OpenCue Integration | **NOT STARTED** |

## Important Clarification

**The FFmpeg runtime in platform-api is a temporary preview bootstrap strategy.** It exists to validate the minimal render execution and artifact output path before introducing OpenCue-based distributed worker scheduling. It must not be treated as the final production render architecture.

Long term:
- platform-api = control plane only
- render-worker-ffmpeg = dedicated execution image
- OpenCue = distributed scheduler

## Key Facts

- Terminal success status: **COMPLETED** (not SUCCEEDED)
- Create-only endpoint does NOT execute jobs
- Canonical execution: `POST /api/v1/render/jobs/{id}/execute`
- Smoke script: `scripts/smoke/render-execution-smoke.sh`

## Next Tasks

1. **RENDER-EXECUTION-PREFLIGHT-FIX.0** — Fix false positives, terminal status
2. **ARTIFACT-OUTPUT-VALIDATION.0** — Prove real artifact output
3. **ARTIFACT-ACCESS.0** — Define safe artifact access
4. **OPENCUE-WORKER-EXECUTION.0** — Move to OpenCue scheduling
5. **FFMPEG-RUNTIME-SPLIT.0** — Remove FFmpeg from platform-api

## Open-source Capability Reference

See [Open-source Capability Extension Blueprint](../architecture/blueprint/open-source-capability-extension-blueprint.md) for candidate tool evaluation and technology selection.

## Spring MVC Route Registration Blocker (2026-07-08)

REAL-MEDIA-INPUT.0 is PARTIAL. Upload/content endpoints return 404 despite being in compiled JAR.
See [Debug](../render/spring-mvc-route-registration-debug.md)

Do not continue REAL-MEDIA-INPUT.0 feature expansion.
Recommended next task: SPRING-BOOT-CLASSLOADER-DEEP-DIAG.0
