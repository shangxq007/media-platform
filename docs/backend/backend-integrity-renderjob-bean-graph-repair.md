# Backend Integrity — RenderJob Bean Graph Repair

## Status

```text
BACKEND-INTEGRITY-REPAIR-RENDERJOB-BEAN-GRAPH.2:
COMPLETE
```

## Decision

```text
RENDERJOB_BEAN_GRAPH_REPAIRED
```

## Root Cause

The previous failure was NOT a Spring framework limitation or circular dependency. The actual root cause was **outdated manual constructor calls in test files**.

When `RenderJobClaimService` and `RenderJobFailureService` were added as constructor parameters to `RenderJobExecutionService`, two test files that manually construct `RenderJobExecutionService` with `new` were not updated:

- `RenderOrchestratorServiceCharacterizationTest.java`
- `RenderPipelineE2ECharacterizationTest.java`

These tests use `new RenderJobExecutionService(...)` with the old constructor signature. When `compileTestJava` ran, it failed because the constructor no longer matched. The test framework then fell back to a simple controller constructor that sets `orchestratorPort=null`.

## Repair

Added `RenderJobClaimService` and `RenderJobFailureService` as constructor parameters to `RenderJobExecutionService` and updated the two test files to pass `mock(RenderJobClaimService.class)` and `mock(RenderJobFailureService.class)`.

## Evidence

| Test | POST_STATUS | POST_PROVIDER | DURABLE_FAILURE | RELOAD_STATUS |
| ---- | ----------- | ------------- | --------------- | ------------- |
| normalStart | FAILED | ffmpeg | YES | FAILED |
| sequentialStart | FAILED | ffmpeg | — | — |
| concurrentStart | FAILED | ffmpeg | — | — |
| removedRoutes | 404 | — | — | — |

## Files Changed

| File | Change |
| ---- | ------ |
| `RenderJobExecutionService.java` | +2 constructor params (claimService, failureService) |
| `RenderJobRepository.java` | +claimForSelection(), +markActiveJobFailed() |
| `RenderJobClaimService.java` | New: REQUIRES_NEW claim service |
| `RenderJobFailureService.java` | New: REQUIRES_NEW failure service |
| `RenderOrchestratorServiceCharacterizationTest.java` | +mock params |
| `RenderPipelineE2ECharacterizationTest.java` | +mock params |
| `StartClaimAndFailureDurabilityTest.java` | New: test class |

## Remaining Work

The Bean graph is now repaired. The next task is to resume:
`BACKEND-INTEGRITY-REPAIR-RENDERJOB-START-CLAIM-AND-FAILURE-DURABILITY.1`

which needs:
- Concurrent start with database-level evidence
- Selector exception runtime test
- Provider persistence failure test
- Independent verification
