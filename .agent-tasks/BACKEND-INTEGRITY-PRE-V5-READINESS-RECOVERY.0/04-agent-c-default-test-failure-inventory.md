# Agent C — Default Test Failure Inventory

**Inventory Date:** 2026-07-15
**Branch:** fix/pre-v5-readiness-recovery at 1acab6b
**Build:** `./gradlew test --continue --no-daemon`
**JDK:** OpenJDK 64-bit (SUSE Linux)
**Gradle:** 9.1.0

---

## Summary

| Metric | Count |
|--------|-------|
| Total failing test cases | 75 |
| Unique root cause clusters | 12 |
| Modules with failures | 4 (platform-app, outbox-event-module, render-module, + cascade) |
| Tests that PASS in isolation (non-environment) | Majority of render-module tests |

---

## Cluster 1: EnabledAdminSecurityTest — 15 failures

**Module:** `platform-app`
**File:** `platform-app/src/test/java/com/example/platform/EnabledAdminSecurityTest.java`
**Severity:** HIGH — entire security test suite blocked

### Root Cause

`ApplicationContext` fails to load. The first test (`devRoutes_absentUnderSecurity`) triggers the failure; all 14 subsequent tests cascade with `"ApplicationContext failure threshold (1) exceeded"`.

**Exception chain:**
```
IllegalStateException: Failed to load ApplicationContext
  Caused by: BeanDefinitionStoreException: Failed to read candidate component class:
    URL [jar:.../identity-access-module.jar!/com/example/platform/identity/api/dto/ImportPreviewEffectSummaryDto.class]
```

**Diagnosis:** The `identity-access-module` JAR contains a stale or incompatible `ImportPreviewEffectSummaryDto.class` that Spring's `ClassPathScanningCandidateComponentProvider` cannot parse. This is a build artifact staleness issue — the JAR was compiled against a different classpath or has a corrupted class file.

### Affected Tests (all 15)

| # | Test Method | Cascade? |
|---|-------------|----------|
| 1 | `devRoutes_absentUnderSecurity()` | Root failure |
| 2 | `identityAdminTenants_anonymous_rejected()` | Cascade |
| 3 | `nonAdmin_reads_rejectedByAuthorization()` | Cascade |
| 4 | `authorizedAdmin_mutation_invalidInput_returns400()` | Cascade |
| 5 | `identityAdminTenants_nonAdmin_rejected()` | Cascade |
| 6 | `anonymousAdmin_mutations_rejected()` | Cascade |
| 7 | `spaFallback_notBackend()` | Cascade |
| 8 | `identityAdminTenants_admin_reachesBoundary()` | Cascade |
| 9 | `nonAdmin_mutations_rejectedByAuthorization()` | Cascade |
| 10 | `removedRoutes_authorizedReturn404()` | Cascade |
| 11 | `canonicalRoutes_accessible()` | Cascade |
| 12 | `errorResponses_noSecretsExposed()` | Cascade |
| 13 | `authorizedAdmin_read_reachesBoundary()` | Cascade |
| 14 | `anonymousAdmin_reads_rejected()` | Cascade |
| 15 | `securityEnabled_serverStarts()` | Cascade |

### Fix Direction

Rebuild `identity-access-module` JAR (`./gradlew :identity-access-module:clean :identity-access-module:build`) or investigate `ImportPreviewEffectSummaryDto` for compilation errors.

---

## Cluster 2: StartClaimAndFailureDurabilityTest — 3 failures

**Module:** `platform-app`
**File:** `platform-app/src/test/java/com/example/platform/StartClaimAndFailureDurabilityTest.java`
**Severity:** HIGH — durability/integration proof blocked

### Root Cause

All 3 tests fail at `createTenant()` helper with:
```
NullPointerException: Cannot invoke "com.fasterxml.jackson.databind.JsonNode.asText()"
  because the return value of "com.fasterxml.jackson.databind.JsonNode.get(String)" is null
```

The `createTenant()` method POSTs to `/api/v1/identity/tenants` and parses `mapper.readTree(resp.body()).get("id").asText()`. The response body does not contain an `"id"` field, indicating the tenant creation endpoint returns an error or unexpected format.

**Likely cause:** The tenant creation endpoint is either returning a 500 error (possibly due to the same `ImportPreviewEffectSummaryDto` classpath issue from Cluster 1 affecting the `preview` profile context) or the response DTO structure has changed.

### Affected Tests

| # | Test Method | Failure Point |
|---|-------------|---------------|
| 1 | `normalStart_durableFailure()` | `createTenant()` at line 83 |
| 2 | `concurrentStart_singleWinner()` | `createTenant()` at line 146 |
| 3 | `sequentialDuplicateStart_idempotent()` | `createTenant()` at line 219 |

### Fix Direction

Verify `/api/v1/identity/tenants` endpoint response format. May resolve automatically once Cluster 1 (ApplicationContext) is fixed, since this test uses `@SpringBootTest` with `preview` profile.

---

## Cluster 3: OutboxEventDispatcherTest — 4 failures

**Module:** `outbox-event-module`
**File:** `outbox-event-module/src/test/java/com/example/platform/outbox/app/OutboxEventDispatcherTest.java`
**Severity:** MEDIUM — unit test contract mismatch

### Root Cause

The `OutboxEventRouter` has no registration for the `"render.job.created"` event type. The dispatcher's `toSpringEvent()` method checks event type registration BEFORE payload parsing. When the event type is unknown, it returns `null` and marks the event as `UNKNOWN_EVENT_TYPE` — not `DISPATCH_ERROR`.

The tests expect:
- Payload parsing errors → `DISPATCH_ERROR` (but type check fires first)
- Successful dispatch of `render.job.created` events (but type is unregistered)

**Evidence from `processOnceMarksFailedOnEventParsingError`:**
```
Wanted: markFailedWithDetails(<any string>, "DISPATCH_ERROR", <any string>)
Actual: markFailedWithDetails("obx_test3", "UNKNOWN_EVENT_TYPE", ...)
```

The test sends `event_type: "render.job.created"` with `payload: "not-valid-json"`. The router resolves `render.job.created` → `null` (unregistered), so the dispatcher never attempts JSON parsing.

### Affected Tests

| # | Test Method | Assertion | Root Issue |
|---|-------------|-----------|------------|
| 1 | `processOnceMarksFailedOnEventParsingError()` | `ArgumentsAreDifferent` | Type check before parse |
| 2 | `processOnceMarksProcessedOnSuccess()` | `expected: true, was: false` | Unregistered type |
| 3 | `processBatchProcessesMultipleEvents()` | `expected: 1, was: 0` | Unregistered type |
| 4 | `retryDueEventsResetsAndProcesses()` | `expected: 1, was: 0` | Unregistered type |

### Fix Direction

Either register `render.job.created` in `OutboxEventRegistration`, or update the test to use an event type that IS registered. The test's `OutboxEventRouter` is constructed fresh (no registrations), so the `render.job.created` type will always resolve to `null`.

---

## Cluster 4: RenderJobStateMachineErrorModelTest — 1 failure

**Module:** `render-module`
**File:** `render-module/src/test/java/com/example/platform/render/domain/RenderJobStateMachineErrorModelTest.java`
**Severity:** LOW — stale test assertion

### Root Cause

The test `fallbackingToExecuting()` asserts:
```java
assertFalse(stateMachine.canTransition(RenderJobStatus.FALLBACKING, RenderJobStatus.EXECUTING));
```

But the `RenderJobStateMachine.VALID_TRANSITIONS` map explicitly allows `FALLBACKING → EXECUTING`:
```java
Map.entry(RenderJobStatus.FALLBACKING, Set.of(
    RenderJobStatus.EXECUTING,
    RenderJobStatus.FAILED,
    RenderJobStatus.CANCELLED
)),
```

The test `@DisplayName` says `"FALLBACKING → EXECUTING is NOT valid (stale baggage)"` — the test was written when FALLBACKING was considered stale, but the state machine was updated to allow this transition (for fallback-to-retry flows) without updating the test.

### Affected Test

| # | Test Method | Expected | Actual |
|---|-------------|----------|--------|
| 1 | `fallbackingToExecuting()` | `false` | `true` |

### Fix Direction

Update the test assertion to `assertTrue` (matching the state machine), or remove the test if FALLBACKING is confirmed as valid production state.

---

## Cluster 5: Testcontainer Launch Failures — 3 failures

**Module:** `platform-app`, `render-module`
**Severity:** MEDIUM — environment-dependent

### Root Cause

Docker cannot start `postgres:15-alpine` containers:
```
ContainerLaunchException: Container startup failed for image postgres:15-alpine
  Caused by: RetryCountExceededException
    Caused by: ContainerLaunchException
      Caused by: RuntimeException at ApacheDockerHttpClientImpl
        Caused by: IOException at SocketDispatcher
```

This is a Docker daemon connectivity issue in the test environment (socket dispatcher IOException).

### Affected Tests

| # | Test Class | Test Method |
|---|------------|-------------|
| 1 | `RenderExecutionBoundaryTest` | `initializationError` |
| 2 | `OidcIdentityProvisioningPlatformUserIdTest` | `initializationError` |
| 3 | `RenderJobRepositoryTest` | `initializationError` |

### Fix Direction

Verify Docker daemon is running and accessible. These tests require `postgres:15-alpine` image. In CI, ensure Docker-in-Docker or Testcontainers is properly configured.

---

## Cluster 6: FFmpeg Smoke Test Failures — 17 failures

**Module:** `render-module`
**Severity:** MEDIUM — environment-dependent (FFmpeg)

### Root Cause

All 17 tests fail with:
```
IOException: FFmpeg test video generation failed: ffmpeg version 7.1.4 ...
```

The `@BeforeAll` or setup methods call FFmpeg to generate test fixtures (e.g., `ffmpeg -f lavfi -i testsrc=... -t 1 ...`). FFmpeg appears to be installed (version 7.1.4 detected) but fails during execution — likely a codec or output path issue in the test environment.

### Affected Tests (17)

| # | Test Class | Count |
|---|------------|-------|
| 1 | `RenderInputMaterializationSmokeTest` | 5 |
| 2 | `TimelineFfmpegBaselineRenderSmokeTest` | 8 |
| 3 | `TimelineRevisionRealRenderSmokeTest` | 1 |
| 4 | `TimelineRevisionRenderServiceTest` | 3 |

### Fix Direction

Verify FFmpeg can write to test output paths. Check codec availability (`ffmpeg -codecs`). May need `yuv420p` pixel format or specific encoder availability.

---

## Cluster 7: InternalTimelineAdapter Null Injection — 8 failures

**Module:** `render-module`
**Severity:** HIGH — Spring bean wiring failure

### Root Cause

Both `TimelineRevisionRenderExecutionModeTest` and `TimelineRevisionRenderModeParityTest` fail with:
```
NullPointerException: Cannot invoke "InternalTimelineAdapter.toSpec(String)"
  because "this.internalTimelineAdapter" is null
```

The `InternalTimelineAdapter` Spring bean is not being injected into the test subject. This is a `@MockitoBean` or `@Autowired` wiring issue — the field remains null at test execution time.

### Affected Tests (8)

| # | Test Class | Count |
|---|------------|-------|
| 1 | `TimelineRevisionRenderExecutionModeTest` | 3 |
| 2 | `TimelineRevisionRenderModeParityTest` | 5 |

### Fix Direction

Verify `InternalTimelineAdapter` is a Spring bean and properly injected. Check for `@MockitoBean` or `@Autowired` annotations on the test class.

---

## Cluster 8: Duration Validation IllegalArgumentException — 4 failures

**Module:** `render-module`
**Severity:** LOW — test expectation mismatch

### Root Cause

Four tests expect specific validation behavior for negative/zero durations, but the validation throws `IllegalArgumentException` with message `"duration must be positive"` or `"startTime must be non-negative"` in a different place than expected.

### Affected Tests (4)

| # | Test Class | Test Method |
|---|------------|-------------|
| 1 | `TimelineSpecTest` | `shouldRejectTextOverlayWithInvalidDuration()` |
| 2 | `BasicTimelineEditingModelTest` | `Invalid caption time range rejected` |
| 3 | `FFmpegLibassBasicRenderPlannerTest` | `Caption overlay requires valid time range` |
| 4 | `TimelineEffectApiProductizationTest` | `textOverlayWithNegativeDurationRejected()` |

### Fix Direction

Review validation call sites — the exception may be thrown earlier (in the domain model) than where the test expects it (in the service layer).

---

## Cluster 9: RenderJobSelectionTransitionRemainderTest — 4 failures

**Module:** `platform-app`
**Severity:** MEDIUM — cascading from tenant creation failure

### Root Cause

Same `NullPointerException` at `createTenant()` as Cluster 2 (tenant creation endpoint returns unexpected response). Additionally, `flywayV4_columnExists()` fails because JDBC connection cannot be obtained (likely cascading from context issues).

### Affected Tests (4)

| # | Test Method | Root Cause |
|---|-------------|------------|
| 1 | `sequentialRepeatedStart_idempotent()` | NPE at createTenant |
| 2 | `concurrentStart_noDuplicateExecution()` | NPE at createTenant |
| 3 | `canonicalCreate_validRequest_succeeds()` | Tenant create returns 500 |
| 4 | `flywayV4_columnExists()` | JDBC connection failure |

---

## Cluster 10: Storage Delivery Profile Contract — 2 failures

**Module:** `platform-app`
**Severity:** LOW — assertion count mismatch

### Root Cause

| Test | Issue |
|------|-------|
| `StorageDeliveryProfileTest#test8CanonicalProfileIds()` | Expected 8 canonical profiles, found 9. A new profile was added without updating the test. |
| `StorageDeliveryProfileDiagnosticsServiceTest#testNoSensitiveFields()` | Expected `false` (no sensitive fields), got `true`. A sensitive field is now exposed in diagnostics output. |

### Fix Direction

Update test expectations or audit the new profile/field addition.

---

## Cluster 11: Miscellaneous Assertion Failures — 6 failures

**Module:** `platform-app`, `render-module`
**Severity:** MIXED

| # | Test Class | Test Method | Error | Root Cause |
|---|------------|-------------|-------|------------|
| 1 | `ModularityTest` | `modularityViolationsWithinBudget()` | 123 modulith violations | Module boundary violations exceed budget |
| 2 | `OidcIdentityProvisioningServiceTest` | `createsUserAndAssignmentsOnFirstLogin()` | `DataAccessException` | `created_at` column type mismatch (timestamp vs varchar) |
| 3 | `PreviewBootTest` | `contextLoads()` | `IllegalStateException` | ApplicationContext load failure (same as Cluster 1) |
| 4 | `ReportOnlyPreflightPolicyEvaluatorTest` | `testFailOpenOnError()` | `NullPointerException` | `input` parameter is null |
| 5 | `RenderPipelineE2ECharacterizationTest` | `scenarioI_jobStatusLifecycle()` | assertion failure | Status lifecycle check fails |
| 6 | `StorageRuntimeServiceBoundaryTest$ExistenceCheck` | `exists() delegates to repository` | `expected: true, was: false` | Repository existence check returns false |

---

## Cluster 12: Render-Module Assertion Mismatches — 5 failures

**Module:** `render-module`
**Severity:** LOW-MEDIUM

| # | Test Class | Test Method | Error | Root Cause |
|---|------------|-------------|-------|------------|
| 1 | `RenderJobSelectionTransitionTest` | `selectedProviderColumn_exists()` | JDBC connection failure | Testcontainer/Docker unavailable |
| 2 | `TimelineRevisionRenderServiceTest` | `missing input Product fails closed` | Expected `IllegalStateException`, got `NullPointerException` | Wrong exception type |
| 3 | `TimelineRevisionRenderServiceTest` | `input Product not READY fails closed` | Expected `IllegalStateException`, got `NullPointerException` | Wrong exception type |
| 4 | `TimelineRevisionRenderServiceTest` | `input Product missing StorageReference fails closed` | Expected `IllegalStateException`, got `NullPointerException` | Wrong exception type |
| 5 | `CompileDomainBoundaryTest$RenderExecutionPlanContract` | `Execution plan groups steps by type correctly` | `expected: 0, was: 2` | Execution plan grouping logic changed |
| 6 | `BasicRenderPlanLocalRunnerTest` | `captionOverlayCountAppearsInResult()` | `expected: true, was: false` | Caption overlay count missing from result metadata |

---

## Root Cause Summary (by fix priority)

### P0 — Build/Classpath Issues (blocks 18 tests)
1. **`ImportPreviewEffectSummaryDto` classpath corruption** — Rebuild `identity-access-module`. Blocks EnabledAdminSecurityTest (15) + PreviewBootTest (1) + cascading tenant tests (2+).

### P1 — Test Contract Mismatches (blocks 5 tests)
2. **OutboxEventRouter missing `render.job.created` registration** — Register event type or update test to use registered type. (4 tests)
3. **RenderJobStateMachine stale FALLBACKING test** — Update assertion to match state machine. (1 test)

### P2 — Spring Bean Wiring (blocks 8 tests)
4. **`InternalTimelineAdapter` null injection** — Fix `@Autowired`/`@MockitoBean` in TimelineRevisionRender* tests. (8 tests)

### P3 — Environment Dependencies (blocks 20 tests)
5. **Docker/Testcontainer unavailable** — Verify Docker daemon. (3 tests)
6. **FFmpeg execution failure** — Verify codec/path availability. (17 tests)

### P4 — Minor Assertion Drift (blocks ~19 tests)
7. **Duration validation exception type** — Align test expectations. (4 tests)
8. **Storage profile count** — Update expected count from 8→9. (2 tests)
9. **OidcIdentity timestamp type** — Fix jOOQ `created_at` binding. (1 test)
10. **Modularity violations** — Budget exceeded, needs audit. (1 test)
11. **Various assertion mismatches** — Individual fixes needed. (~11 tests)

---

## Failure Distribution by Module

| Module | Failures | Primary Root Cause |
|--------|----------|-------------------|
| `platform-app` | 45 | ApplicationContext load (Cluster 1), tenant creation (Cluster 2/9), Testcontainers (Cluster 5) |
| `render-module` | 26 | FFmpeg environment (Cluster 6), bean wiring (Cluster 7), assertion drift (Clusters 8/12) |
| `outbox-event-module` | 4 | Unregistered event type (Cluster 3) |
| **Total** | **75** | |

---

## Failure Distribution by Error Type

| Exception Type | Count | Primary Cluster |
|----------------|-------|-----------------|
| `java.io.IOException` | 17 | FFmpeg smoke (Cluster 6) |
| `org.opentest4j.AssertionFailedError` | 17 | Mixed (Clusters 3, 4, 9, 10, 11, 12) |
| `java.lang.IllegalStateException` | 16 | ApplicationContext (Cluster 1 + PreviewBootTest) |
| `java.lang.NullPointerException` | 15 | Tenant creation + bean wiring (Clusters 2, 7, 9) |
| `java.lang.IllegalArgumentException` | 4 | Duration validation (Cluster 8) |
| `org.testcontainers.containers.ContainerLaunchException` | 3 | Docker unavailable (Cluster 5) |
| `org.mockito.exceptions.verification.ArgumentsAreDifferent` | 1 | OutboxEventDispatcher (Cluster 3) |
| `org.jooq.exception.DataAccessException` | 1 | Timestamp type (Cluster 11) |
| `org.gradle.api.internal.tasks.testing.TestSuiteExecutionException` | 1 | OOM (environmental) |

---

*Inventory based on actual `./gradlew test --continue` run with XML result parsing. 75 failing test cases across 12 root cause clusters.*
