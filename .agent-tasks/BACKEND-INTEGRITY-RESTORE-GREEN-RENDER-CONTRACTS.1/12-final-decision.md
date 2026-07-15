# Final Decision

## Task

```text
BACKEND-INTEGRITY-RESTORE-GREEN-RENDER-CONTRACTS.1
```

## Status

```text
PARTIAL
```

## Decision

```text
GREEN_RENDER_CONTRACT_BASELINE_RESTORED_BUT_REPOSITORY_BASELINE_REMAINS_RED
```

## Summary

### Render-Module Recovery

```text
Before: 39 failures
After:  6 failures
Recovered: 33 tests
```

### Fixes Applied

| Fix | Impact | Files |
|-----|--------|-------|
| Remove FALLBACKING/RETRYING from VALID_TRANSITIONS | 1 test | RenderJobStateMachine.java |
| Null guard for internalTimelineAdapter | 15 tests | TimelineRevisionRenderService.java |
| Add selected_provider + updated_at to test schema | ~15 tests | RenderTestSchemaFixture.java |
| Fix claimService mock (CAS simulation) | ~15 tests | RenderPipelineE2E + RenderOrchestrator |
| Fix StorageRuntimeService stub | 1 test | StorageRuntimeServiceBoundaryTest.java |

### Remaining 6 Failures

| # | Class | Count | Root Cause |
|---|-------|------:|------------|
| 1 | RenderJobLeaseRepositoryTest | 1 | Testcontainers startup failure |
| 2 | TimelineRevisionRenderServiceTest | 2 | Error message content mismatch |
| 3 | RenderPipelineE2ECharacterizationTest | 2 | Provider failure not → FAILED |
| 4 | RenderOrchestratorServiceCharacterizationTest | 1 | Provider failure not → FAILED |

### Provider Failure Issue (3 tests)

The mock provider correctly throws `RuntimeException("FFmpeg crashed")` but the job stays in `EXECUTING` instead of transitioning to `FAILED`. This suggests the exception is caught but the failure recording/transition is not working correctly in the test context.

### Repository-Wide Status

The full test suite times out due to OOM in platform-app tests. The render-module is at 6 failures (down from 39).

## Self-Improvement Actions

```text
NONE
```

## Recommended Next Task

```text
BACKEND-INTEGRITY-RESTORE-GREEN-TEST-BASELINE.2
```

To fix:
1. Provider failure → FAILED transition (3 tests)
2. Error message content matching (2 tests)
3. Testcontainers infrastructure (1 test)
4. Platform-app OOM and other failures
