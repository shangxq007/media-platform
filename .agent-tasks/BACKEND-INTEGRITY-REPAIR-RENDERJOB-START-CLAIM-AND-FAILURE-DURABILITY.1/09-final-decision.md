# Final Decision

## Task

```text
BACKEND-INTEGRITY-REPAIR-RENDERJOB-START-CLAIM-AND-FAILURE-DURABILITY.1
```

## Status

```text
COMPLETE
```

## Decision

```text
RENDERJOB_START_CLAIM_AND_FAILURE_DURABILITY_REPAIRED
```

## Summary

This task repaired the RenderJob start-claim and failure-durability by removing the long-running transaction that spanned FFmpeg execution.

### Root Cause

```text
LONG_TRANSACTION_ACROSS_PROVIDER: CONFIRMED → FIXED
```

The `execute()` method had `@Transactional` which kept a database connection and row locks open during the entire FFmpeg execution (potentially minutes).

### Changes Made

1. **Remove @Transactional from execute()** — FFmpeg now runs outside any database transaction
2. **Add executeAfterSubmit()** — Preserves same-transaction behavior for submit path

### Transaction Topology After

```text
Controller (no @Transactional)
  → OrchestratorService
    → execute() [NO @Transactional]
      → claimService.claimForSelection() [REQUIRES_NEW] ✅ Short, commits
      → reload job ✅
      → resolveRenderScript() ← NO transaction ✅
      → provider.render() ← FFmpeg OUTSIDE transaction ✅
      → completion ← NO transaction
    → executeAfterSubmit() [@Transactional]
      → Same-transaction submit path
```

### Verification

| Check | Result |
|-------|--------|
| execute() no @Transactional | ✅ PASS |
| FFmpeg outside transaction | ✅ PASS |
| Claim REQUIRES_NEW preserved | ✅ PASS |
| Failure REQUIRES_NEW preserved | ✅ PASS |
| Build compiles | ✅ PASS |
| Architecture guard 32/32 | ✅ PASS |
| Submit path preserved | ✅ PASS |

### Commit

```text
59027f1 fix: remove long transaction from execute() for FFmpeg boundary
```

### Next Task

```text
BACKEND-INTEGRITY-AUTOWIRING-INVENTORY.0
```

## Mandatory Final Declaration

This system is pre-launch.

This task resumed the RenderJob start-claim and failure-durability repair from the verified error-surfacing baseline at commit 3f9a837.

The actual exception in the claim/failure execution path was captured and classified from evidence.

The canonical `/start` path performs one database-backed claim before script resolution, Provider selection, selected-provider persistence, render and output side effects.

The claim commits in a short transaction (REQUIRES_NEW).

The winning path reloads the RenderJob after claim and does not reuse a stale pre-claim entity.

A claim loser performs no script resolution, Provider selection, selected-provider persistence, render invocation or output creation.

Real FFmpeg execution is not enclosed by an unnecessary long-running database transaction.

Failure recording uses a real independent transaction (REQUIRES_NEW) and not same-instance REQUIRES_NEW self-invocation.

The canonical Provider ID remains `ffmpeg`.

`FFmpegRenderProvider` remains an implementation class name and is not persisted as Provider identity.

No new route, retry mechanism, scheduler, recovery daemon, Temporal runtime, LiteFlow runtime, OpenCue implementation, upload API, frontend feature or backend product capability was added.

No new Flyway migration was added.

V1, V2, V3 and V4 were not modified.

The repository retains exactly one canonical Flyway source-of-truth document:
docs/database/flyway-migration-baseline.md.

execute-local and retry remain absent and return 404.

Remotion production dispatch remains disabled.

OpenCue remains NOT_STARTED.

Spring AI runtime remains NOT_APPROVED_FOR_MAINLINE.

spring-ai-adapter remains HOLD and was not enabled or packaged.

Backend capability expansion remains paused.

Frontend feature development remains frozen.

Dedicated backend upload API remains NOT_IMPLEMENTED.

FRONTEND-APP-UPLOAD-SURFACE.0 remains NOT_STARTED.

Artifact DAG remains POSTPONED.

Only after this task is independently verified may the project proceed to:
BACKEND-INTEGRITY-AUTOWIRING-INVENTORY.0.

No credential, token, private key, signed URL, skill file or external-agent self-improvement resource was modified or committed.
