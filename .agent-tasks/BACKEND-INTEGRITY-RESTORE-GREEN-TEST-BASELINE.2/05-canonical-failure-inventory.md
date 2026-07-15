# Canonical Failure Inventory

## Baseline State (1643274)

```
Render module: 2749 tests, 6 failures, 0 errors, 17 skipped
Platform-app:  317 tests, 23 failures, 0 errors, 20 skipped (2nd run, after OOM recovery)
Other modules: ALL GREEN
Repository total failures: 29 (6 render + 23 platform-app)
```

## Failure Inventory

| # | Cluster | Module | Test methods | Count | Root cause | Repair type | Owner |
|---|---------|--------|-------------|-------|------------|-------------|-------|
| 1 | Provider-failure mock | render-module | RenderOrchestratorServiceCharacterizationTest#executeExistingRenderJobHandlesProviderFailure, RenderPipelineE2ECharacterizationTest#scenarioK_providerFailureHandling | 2 | MOCK_DOES_NOT_SIMULATE_CAS: failureService mock has no stub for recordDurableFailure() | PROVIDER_FAILURE_DURABILITY_FIX (test) | Agent D |
| 2 | Job status history | render-module | RenderPipelineE2ECharacterizationTest#scenarioI_jobStatusLifecycle | 1 | MISSING_HISTORY_FOR_CLAIM_TRANSITION: QUEUED→SELECTING_PROVIDER claim bypasses history recording | TIMELINE_ERROR_TEST_FIX (test) | Agent D |
| 3 | Timeline fail-closed | render-module | TimelineRevisionRenderServiceTest#R6.1: missing input Product fails closed, R6.1: input Product not READY fails closed | 2 | URI_FALLBACK_INSTEAD_OF_FAIL_CLOSED: production code has URI fallback instead of failing closed | PROVIDER_FAILURE_DURABILITY_FIX (production) | Agent D |
| 4 | Testcontainers | render-module | RenderJobRepositoryTest#initializationError | 1 | TESTCONTAINERS_PODMAN_COMPATIBILITY: Broken pipe on Podman socket (transient) | TESTCONTAINERS_LIFECYCLE_FIX | Agent D |
| 5 | OOM | platform-app | Gradle Test Executor (OOM cascade: ~15+ tests) | ~15 | SPRING_CONTEXT_EXPLOSION + TEST_WORKER_OOM: 16+ Spring contexts in 512MB heap | JUSTIFIED_TEST_HEAP_FIX | Agent D |
| 6 | Mockito final class | platform-app | TimelineReviewControllerTest (3), TimelineRevisionRenderJobStatusControllerTest (14) | 17 | Mockito cannot mock final/sealed classes on Java 25 without ByteBuddy agent | OOM_LEAK_FIX (build config) | Agent D |
| 7 | Missing test DB | platform-app | MvcRouteInventoryTest#captureRouteInventory | 1 | MISSING_TESTCONTAINERS_BASE_CLASS: no PostgreSQL on localhost:5432, no Testcontainers | TEST_ISOLATION_FIX | Agent D |
| 8 | Testcontainers | platform-app | ProviderRegistrationValidationTest#initializationError | 1 | TESTCONTAINERS_PODMAN_COMPATIBILITY: Broken pipe (same as #4) | TESTCONTAINERS_LIFECYCLE_FIX | Agent D |
| 9 | Assertion drift | platform-app | ResponseInvarianceTest$ForbiddenFieldsTests#testForbiddenFieldsListSize | 1 | Stale test expectation: expects 27 fields, actual 29 | OTHER_NAMED_REPOSITORY_FIX | Agent D |
| 10 | Assertion drift | platform-app | StorageDeliveryProfileTest#test8CanonicalProfileIds | 1 | Stale test expectation: expects 8 profiles, actual 9 | OTHER_NAMED_REPOSITORY_FIX | Agent D |
| 11 | Assertion drift | platform-app | StorageDeliveryProfileDiagnosticsServiceTest#testNoSensitiveFields | 1 | Test assertion mismatch | OTHER_NAMED_REPOSITORY_FIX | Agent D |
| 12 | NoClassDefFound | platform-app | TimelineMergeControllerTest (2) | 2 | NoClassDefFoundError for render domain internal types | OTHER_NAMED_REPOSITORY_FIX | Agent D |
| 13 | Modulith debt | platform-app | ModularityTest#modularityViolationsWithinBudget | 1 | MODULITH_BOUNDARY_DEBT: 123 violations (render→outbox, web→render) | OTHER_NAMED_REPOSITORY_FIX | Agent D |

## Totals

```
Render module failures: 6 (clusters 1-4)
Platform-app failures: 23 (clusters 5-13)
Repository total: 29
OTHER: 0
UNKNOWN: 0
```

## Dependency Order

```
Fix #5 (OOM heap) first → resolves ~15 cascade failures
Fix #6 (Mockito agent) → resolves 17 failures
Fix #7 (test DB) → resolves 1 failure
Fix #1-3 (render tests) → resolves 5 failures
Fix #4,8 (Testcontainers) → resolves 2 failures (may self-resolve)
Fix #9-13 (assertion drift) → resolves 6 failures
```
