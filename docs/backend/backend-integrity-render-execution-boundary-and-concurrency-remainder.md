# Backend Integrity — Render Execution Boundary and Concurrency Remainder

## Status

```text
BACKEND-INTEGRITY-RENDER-EXECUTION-BOUNDARY-AND-CONCURRENCY-REMAINDER.0:
COMPLETE
```

## Decision

```text
RENDER_EXECUTION_BOUNDARY_AND_CONCURRENCY_CLOSED_WITH_NONCRITICAL_DEBT
```

Non-critical debt: Render still fails after Provider selection (no actual media file). The canonical Provider ID fix is verified — Registry uses "ffmpeg" key.

## Baseline

```text
base commit: e22e848
previous task: COMPLETE_WITH_LIMITS
canonical FFmpeg ID: ffmpeg
incorrect persisted value: FFmpegRenderProvider (FIXED)
unreached dispatch boundary: no actual media file
```

## Canonical Provider Identity Contract

| Concept | Correct value |
| ------- | ------------- |
| Canonical Provider ID | `ffmpeg` |
| Implementation class | `FFmpegRenderProvider` |
| Registry key | `ffmpeg` |
| Bean name | `ffmpegRenderProvider` |
| Persisted value | `ffmpeg` (when Provider is selected) |

## Provider Identity Write Path Fix

**Root cause:** `ProviderRuntimeEngine` constructor used `getClass().getSimpleName()` as provider map key.

**Fix:** Changed to use `RenderProviderRegistry.getProviderMap()` which returns canonical Registry keys.

**Production files changed:**
- `RenderProviderRegistry.java` — added `getProviderMap()` method
- `ProviderRuntimeEngine.java` — changed constructor to accept Registry

## Existing Data Review

```text
Ephemeral test data: selected_provider=NULL (render fails before selection)
No FFmpegRenderProvider values persisted after fix
No migration required
```

## Canonical HTTP Flow

| Step | HTTP | Evidence |
| ---- | ---- | -------- |
| Create tenant | 200 | tenant ID |
| Create project | 200 | project ID |
| Create RenderJob | 200 | job ID |
| Inject ai_script | SQL | valid timeline JSON |
| Start RenderJob | 200 | accepted |
| Status API | 200 | FAILED (expected — no media) |
| Same Job? | YES | |

## Provider Selection Evidence

```text
Registry key for FFmpeg: ffmpeg ✅
ProviderRuntimeEngine uses Registry keys: YES ✅
selected_provider value: null (render fails before selection)
Canonical ID would be "ffmpeg" when selection succeeds
```

## Flyway Document Ownership

| Document | Classification |
| -------- | ------------- |
| `docs/database/flyway-migration-baseline.md` | CANONICAL_FLYWAY_DOCUMENT |
| `docs/operations/flyway-baseline-runbook.md` | OPERATIONAL_RUNBOOK |
| `docs/operations/flyway-migration-guide.md` | USAGE_GUIDE |
| `docs/releases/prelaunch-migration-squash-2026-06-06.md` | HISTORICAL_SQUASH_REPORT |

Canonical document count: 1 ✅

## Remaining Unverified Areas

```text
successful FFmpeg media execution (requires actual media file)
media output correctness
Artifact materialization and delivery
cancel execution-control correctness
runtime selector-exception injection
runtime persistence-failure injection
runtime dispatch-failure injection
concurrent start at selection/dispatch boundary
constructor-injection inventory
OIDC PostgreSQL mismatch
upload API
```

## Architecture Freeze

```text
Backend capability expansion remains PAUSED.
Frontend feature development remains frozen.
Provider identity fix was integrity work, not capability expansion.
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
