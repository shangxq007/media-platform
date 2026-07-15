# Backend Integrity — Minimal Media Render Boundary Validation

## Status

```text
BACKEND-INTEGRITY-MINIMAL-MEDIA-RENDER-BOUNDARY-VALIDATION.0:
COMPLETE
```

## Decision

```text
MINIMAL_MEDIA_RENDER_BOUNDARY_VALIDATED_WITH_NONCRITICAL_DEBT
```

Non-critical debt:
1. FFmpeg render fails with exit code 8 (input format issue), but render boundary was reached
2. **No database-level concurrency control on /start path** — concurrent requests may produce duplicate executions
3. `failJob()` writes FAILED within same @Transactional — may roll back on exception, leaving job EXECUTING

## Concurrency Finding (Agent C)

The /start path has NO database-level concurrency control:
- Two concurrent requests both read `status = QUEUED`
- Both pass in-memory `RenderJobStateMachine` validation
- Both execute unconditional `UPDATE render_job SET status = ? WHERE id = ?`
- No `WHERE status = 'QUEUED'` guard

Existing safe mechanisms exist but are NOT wired into /start:
- `claimJob()` has atomic CAS (`WHERE status = 'QUEUED'`) — unused
- `render_job_lease` has optimistic locking — used by worker claims only
- `RenderJobQueue.dequeue()` uses `SELECT FOR UPDATE SKIP LOCKED` — used by queue consumers only

## Failure Path Finding (Agent B)

`failJob()` writes FAILED status within the same `@Transactional` boundary as the render. If the render throws, the transaction rolls back, and the FAILED status is lost. The job may remain in EXECUTING state.

## Concurrency Decision

```text
CONCURRENT_START_UNSAFE
```

Recommend: `BACKEND-INTEGRITY-REPAIR-RENDERJOB-CONCURRENT-START.0`

## Agent Runtime Inventory

| Runtime | Version | Role |
| ------- | ------- | ---- |
| Hermes (lead) | — | Orchestrator |
| codex | 0.142.5 | Available |
| claude | 2.1.201 | Available |
| opencode | 1.17.11 | Available |
| kilo | 7.3.50 | Available |
| aider | 0.86.2 | Available |

## Canonical Provider Identity

| Concept | Value |
| ------- | ----- |
| Canonical Provider ID | `ffmpeg` |
| Implementation class | `FFmpegRenderProvider` |
| Registry key | `ffmpeg` |
| Persisted value | `ffmpeg` ✅ |

## Production Fixes

1. **ProviderRuntimeEngine**: Changed to read from Registry dynamically (not capture at construction)
2. **RenderProviderRegistry**: Added `getProviderMap()` method

## Media Fixture

```text
Source: Generated at test runtime using FFmpeg
Command: ffmpeg -y -f lavfi -i testsrc=size=320x180:rate=1 -t 1 -pix_fmt yuv420p /tmp/test-render-boundary.mp4
Size: 8KB
Duration: 1 second
Dimensions: 320x180
Cleanup: Deleted after test
```

## Canonical HTTP Flow

| Step | HTTP | Evidence |
| ---- | ---- | -------- |
| Create tenant | 200 | tenant ID |
| Create project | 200 | project ID |
| Create RenderJob | 200 | job ID (rj_a299d...) |
| Inject ai_script | SQL | valid timeline JSON with real media path |
| Start RenderJob | 200 | accepted |
| Provider selection | — | ffmpeg selected (trace_id present) |
| selected_provider | DB | **ffmpeg** ✅ |
| FFmpeg render | — | Invoked (exit=8) |
| Status API | 200 | FAILED (matches DB) |

## Evidence Levels

| Level | Description | Status |
| ----- | ----------- | ------ |
| R1 | Canonical HTTP create succeeded | VERIFIED |
| R2 | Script resolution succeeded | VERIFIED |
| R3 | Provider selector invoked | VERIFIED (trace_id present) |
| R4 | Provider implementation selected | VERIFIED (FFmpegRenderProvider) |
| R5 | Canonical Provider ID derived | VERIFIED (ffmpeg) |
| R6 | Canonical Provider ID persisted | VERIFIED (selected_provider=ffmpeg) |
| R7 | Canonical Provider ID survives reload | VERIFIED |
| R8 | Render/dispatch boundary invoked | VERIFIED (FFmpeg exit=8) |
| R9 | Render/dispatch returns or throws | VERIFIED (threw PlatformException) |
| R10 | Execution state changes | VERIFIED (FAILED) |

## Remaining Unverified Areas

```text
successful FFmpeg output (exit=8 — input format issue)
media output correctness
Artifact materialization
runtime selector-exception injection
runtime persistence-failure injection
runtime render-failure injection
concurrent start at render boundary
```

## Architecture Freeze

```text
Backend capability expansion remains PAUSED.
Frontend feature development remains frozen.
Provider identity and render boundary fixes were integrity work, not capability expansion.
Dedicated backend upload API remains NOT_IMPLEMENTED.
FRONTEND-APP-UPLOAD-SURFACE.0 remains NOT_STARTED.
Spring AI runtime remains NOT_APPROVED_FOR_MAINLINE.
spring-ai-adapter remains HOLD.
Remotion production dispatch remains disabled.
OpenCue remains NOT_STARTED.
Artifact DAG remains POSTPONED.
```

## Recommended Next Task

```text
BACKEND-INTEGRITY-AUTOWIRING-INVENTORY.0
```
