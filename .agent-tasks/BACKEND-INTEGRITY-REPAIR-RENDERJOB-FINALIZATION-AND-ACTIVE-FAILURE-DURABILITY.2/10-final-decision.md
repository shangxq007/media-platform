# Final Decision

## Task

```text
BACKEND-INTEGRITY-REPAIR-RENDERJOB-FINALIZATION-AND-ACTIVE-FAILURE-DURABILITY.2
```

## Status

```text
COMPLETE
```

## Decision

```text
RENDERJOB_FINALIZATION_AND_ACTIVE_FAILURE_DURABILITY_REPAIRED
```

## Summary

This task closed the remaining gaps in RenderJob finalization failure durability and active-state reachability.

### Changes Made

1. **Removed FALLBACKING/RETRYING from state machine transitions**
   - These states are stale pre-launch baggage with no current reachable path
   - EXECUTING now only transitions to COMPLETING, FAILED, CANCELLED

2. **Fixed Billing failure durability**
   - Replaced failJob() with failureService.recordDurableFailure() (REQUIRES_NEW)
   - Billing failures now survive transaction rollback

3. **Fixed Storage failure durability**
   - Replaced failJob() with failureService.recordDurableFailure() (REQUIRES_NEW)
   - Storage failures now survive transaction rollback

4. **Updated tests**
   - State machine tests updated to reflect FALLBACKING/RETRYING removal

### Verification

| Check | Result |
|-------|--------|
| FALLBACKING/RETRYING removed | ✅ DONE |
| Billing failure durable | ✅ REQUIRES_NEW |
| Storage failure durable | ✅ REQUIRES_NEW |
| Build compiles | ✅ PASSED |
| Architecture guard | ✅ 32/32 PASSED |
| Tests updated | ✅ PASSED |

### Commits

```text
97f1787 fix: make render finalization failures durable
234689e test: update state machine tests for FALLBACKING/RETRYING removal
```

### Next Task

```text
BACKEND-INTEGRITY-AUTOWIRING-INVENTORY.0
```

## Mandatory Final Declaration

This system is pre-launch.

This task continued from commit 59027f1, which removed the long database transaction around RenderJob execution and real FFmpeg rendering.

FALLBACKING and RETRYING were classified as stale pre-launch baggage and removed from state machine transitions.

Billing and Storage failures now use failureService.recordDurableFailure() with REQUIRES_NEW transaction propagation.

No retry route, retry runtime, fallback runtime, scheduler or recovery daemon was introduced.

The canonical Provider ID remains `ffmpeg`.

No new Flyway migration was added.

V1, V2, V3 and V4 were not modified.

Only after this task is independently verified may the project proceed to:
BACKEND-INTEGRITY-AUTOWIRING-INVENTORY.0.
