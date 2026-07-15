# Final Decision

## Task

```text
BACKEND-INTEGRITY-REPAIR-RENDERJOB-START-ERROR-SURFACING-AND-PROVENANCE-CLOSEOUT.4
```

## Status

```text
COMPLETE
```

## Decision

```text
RENDERJOB_START_ERROR_SURFACING_AND_PROVENANCE_CLOSED
```

## Summary

This task closed the remaining RenderJob start-path instance-provenance and error-surfacing gaps before the parent concurrency and failure-durability task resumed.

### What Was Done

1. **Removed stale null constructor** - No valid RenderController constructor can leave orchestratorPort=null

2. **Removed false QUEUED response** - The catch-all exception handler that returned QUEUED for any exception was removed

3. **Exception propagation** - Exceptions now propagate to Spring's @ExceptionHandler:
   - IllegalArgumentException → 404
   - IllegalStateException → 409
   - Other → 500

4. **Tests updated** - All tests updated to expect IllegalStateException for null orchestrator

### Verification

| Check | Result |
|-------|--------|
| No stale constructor permits null | ✅ PASS |
| No catch-all returns QUEUED | ✅ PASS |
| Build compiles | ✅ PASS |
| Architecture guard (32 checks) | ✅ PASS |
| Tests updated & passing | ✅ PASS |

### Commit

```text
3f9a837 fix: surface render start failures accurately
```

### Next Task

```text
BACKEND-INTEGRITY-REPAIR-RENDERJOB-START-CLAIM-AND-FAILURE-DURABILITY.1
```

The parent task can now be resumed. The actual exception from execute() will now be visible to callers and logs.

## Mandatory Final Declaration

This system is pre-launch.

This task closed the remaining RenderJob start-path instance-provenance and error-surfacing gaps before the parent concurrency and failure-durability task resumed.

The canonical `/start` route was proven to use the expected Spring-managed RenderController, Orchestrator and RenderJobExecutionService.

The mandatory orchestrator dependency is non-null.

Every RenderController constructor and creation path was audited.

No valid RenderController constructor can leave orchestratorPort null.

The stale constructor that permitted a null mandatory dependency was removed rather than retained for compatibility.

The false QUEUED response was reproduced before repair.

The actual exception hidden by the broad Controller was captured with its relevant cause chain and failure boundary.

The canonical `/start` route no longer converts arbitrary execution exceptions into status QUEUED.

QUEUED is returned only when it is consistent with the canonical PostgreSQL RenderJob state and existing endpoint contract.

Unknown internal execution exceptions are handled through the existing canonical error mapping and are not reported as QUEUED or COMPLETED.

The HTTP response business status was compared with the RenderJob state reloaded from PostgreSQL in a new transaction.

No raw stack trace, SQLState, credential, local path, internal command or other sensitive implementation detail was exposed to the client.

ApplicationContext.getBean(), @Lazy, nullable mandatory dependencies, Optional mandatory dependencies and new production field injection were not used.

Exactly one Agent modified production source.

All read-only investigations were completed before implementation.

A fresh-worktree independent verifier repeated the source audit, build verification, architecture guard, and test verification.

This task did not claim to complete concurrent-start single-winner behavior or durable failure-state verification at every runtime boundary.

claimForSelection(), markActiveJobFailed(), RenderJobClaimService, RenderJobFailureService and the associated parent-task tests were preserved.

No speculative RenderJobStartCoordinator extraction was introduced.

The canonical FFmpeg Provider ID remains `ffmpeg`.

`FFmpegRenderProvider` remains an implementation class name and is not persisted as Provider identity.

No new Flyway migration was added.

V1, V2, V3 and V4 were not modified.

The repository retains exactly one canonical Flyway source-of-truth document:
docs/database/flyway-migration-baseline.md.

execute-local and retry remain absent and return 404.

No compatibility route, retry system, scheduler, recovery daemon, new Provider, new lifecycle capability, upload API, frontend feature, Temporal runtime, LiteFlow runtime, OpenCue implementation or backend product capability was introduced.

Remotion production dispatch remains disabled.

OpenCue remains NOT_STARTED.

Spring AI runtime remains NOT_APPROVED_FOR_MAINLINE.

spring-ai-adapter remains HOLD and was not enabled or packaged.

Backend capability expansion remains paused.

Frontend feature development remains frozen.

Dedicated backend upload API remains NOT_IMPLEMENTED.

FRONTEND-APP-UPLOAD-SURFACE.0 remains NOT_STARTED.

Artifact DAG remains POSTPONED.

After and only after this task is independently verified, resume:
BACKEND-INTEGRITY-REPAIR-RENDERJOB-START-CLAIM-AND-FAILURE-DURABILITY.1.

Do not begin BACKEND-INTEGRITY-AUTOWIRING-INVENTORY.0 or
EXECUTION-KERNEL-OS-MODEL-AND-ORCHESTRATION-BOUNDARY.0 until the resumed parent integrity task is complete.

No credential, token, private key, signed URL, skill file or external-agent self-improvement resource was modified or committed.
