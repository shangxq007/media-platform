# Agent C: Platform-App OOM & Repository Failure Classification

**Branch**: fix/pre-v5-readiness-recovery (HEAD: 1643274)
**Date**: 2026-07-16
**Status**: INVESTIGATION COMPLETE

---

## 1. Platform-App OOM Diagnosis

### OOM Type

```
java.lang.OutOfMemoryError: Java heap space
```

**Process**: Gradle Test Worker (GradleWorkerMain), NOT the Gradle daemon.
**Location**: `org.gradle.api.internal.tasks.testing.worker.TestWorker`

### Heap Configuration

| Setting | Value |
|---------|-------|
| gradle.properties | **DOES NOT EXIST** |
| org.gradle.jvmargs | **NOT SET** (Gradle defaults) |
| Test task JVM args | **NOT SET** |
| maxParallelForks | **NOT SET** (default: 1) |
| forkEvery | **NOT SET** (default: 0 = unlimited) |
| Default test worker heap | ~512MB (Gradle 9.1.0 default) |

**No `gradle.properties` file exists at the project root or in any module.**

### Root Cause: SPRING_CONTEXT_EXPLOSION + TEST_WORKER_OOM

The platform-app module has **19 `@SpringBootTest` classes** with **16+ unique ApplicationContext configurations**. Each unique configuration loads a full Spring context in a single test worker process with only ~512MB heap.

**Evidence from logs:**
- HikariPool-1, HikariPool-2, HikariPool-4, HikariPool-5 = 4+ connection pools (HikariPool-3 missing = MvcRouteInventoryTest context failed at Flyway)
- OOM in `ConfigurationPropertyName.java:1008` during property binding (RealHttpSecurityBoundaryTest)
- OOM in `Unsafe.java:1396` during byte allocation (RenderExecutionBoundaryTest)
- OOM in 13+ thread pools: `http-nio-auto-*-Poller`, `HttpClient-*-SelectorManager`, `testcontainers-ryuk`
- 74 tests completed, then OOM killed the process before test reporting finished

**Memory amplification factors per context:**
1. 30+ module beans loaded (platform-app depends on ~27 modules)
2. Embedded Tomcat with NIO connectors (RANDOM_PORT tests)
3. HikariCP connection pool (5 connections each)
4. Flyway migration execution
5. Spring Security filter chain
6. Spring Modulith analysis
7. Temporal, LiteFlow, PF4J framework initialization
8. GraphQL autoconfiguration
9. SpringDoc OpenAPI

**16 classes extend `PostgresTestContainerSupport`**, each potentially starting a PostgreSQL container.

### Context Cache Key Analysis

Spring context cache keys differ by:
- `@ActiveProfiles`: `"test"` vs `{"test","preview"}`
- `@TestPropertySource`: Each class has different property overrides
- `webEnvironment`: `RANDOM_PORT` vs `NONE` vs default

Unique context configurations identified:

| # | Profile | WebEnv | Classes |
|---|---------|--------|---------|
| 1 | test,preview | RANDOM_PORT | RealHttpSecurityBoundaryTest (4 props) |
| 2 | test,preview | RANDOM_PORT | RenderExecutionBoundaryTest (7 props) |
| 3 | test,preview | RANDOM_PORT | RenderJobInstanceProvenanceTest (5 props) |
| 4 | test,preview | RANDOM_PORT | EnabledAdminSecurityTest |
| 5 | test,preview | RANDOM_PORT | RenderJobSelectionTransitionRemainderTest |
| 6 | test,preview | RANDOM_PORT | RenderJobPreselectionTest |
| 7 | test,preview | RANDOM_PORT | MinimalMediaRenderBoundaryTest |
| 8 | test,preview | RANDOM_PORT | StartClaimAndFailureDurabilityTest |
| 9 | test,preview | RANDOM_PORT | RenderJobSelectionTransitionTest |
| 10 | test,preview | RANDOM_PORT | MvcRouteInventoryTest (no TestPropertySource) |
| 11 | test,preview | NONE | ProviderRegistrationValidationTest |
| 12 | test,preview | default | PreviewBootTest |
| 13 | test | default | EffectTaxonomyIntegrationTest |
| 14 | test | default | EffectTaxonomyVerificationTest |
| 15 | test | default | SimpleTaxonomyTest |
| 16 | test | default | RenderPipelineDagIT |

That's **16 unique contexts** in a single test worker with ~512MB heap.

### OOM Classification

```
PRIMARY:    TEST_WORKER_OOM
SECONDARY:  SPRING_CONTEXT_EXPLOSION
TERTIARY:   HEAP_LIMIT_LEGITIMATELY_TOO_LOW
```

**Not**: GRADLE_DAEMON_OOM (daemon is single-use, forked per build)
**Not**: REAL_MEMORY_LEAK (system has 125GB RAM, only ~12GB used)
**Not**: CONTAINER_LEAK (containers are per-test-class, not accumulating)
**Not**: UNBOUNDED_STATIC_CACHE (no evidence of static caches)
**Not**: THREAD_OR_EXECUTOR_LEAK (threads are per-context, GC'd with context)
**Not**: TEST_CONTEXT_CACHE_KEY_DRIFT (contexts are different by design)

---

## 2. ModularityTest Failure

### Test: `ModularityTest#modularityViolationsWithinBudget`

**Exception**: `org.opentest4j.AssertionFailedError`
**Message**: `Unexpected Modulith violations (messages=123)`

### Violation Categories

The 123 violations fall into these categories:

| Category | Source → Target | Count (approx) |
|----------|----------------|-----------------|
| render → outbox (coordination) | render app/domain/infrastructure → outbox.coordination.* | ~45 |
| render → storage::infrastructure | render app → S3ObjectMaterializer, S3ObjectWriter | ~8 |
| render → outbox::app | render app → OutboxEventService | ~2 |
| web → render (domain types) | web controllers → render domain types | ~60 |
| web → outbox::app | web controllers → OutboxEventService | ~1 |
| root → ingest (non-exposed) | PlatformBeanConfiguration → ingest config types | ~3 |

### Allowed Violations (pre-existing budget)

The test allows only 2 violations:
```java
ALLOWED_VIOLATIONS = List.of(
    "identity' depends on named interface(s) 'artifact",
    "identity' depends on named interface(s) 'storage"
);
```

**Net unexpected violations: 123 - 0 (none match allowed patterns) = 123**

### Classification

```
MODULE:  platform-app
CLASS:   ModularityTest
METHOD:  modularityViolationsWithinBudget
ROOT_CAUSE: MODULITH_BOUNDARY_DEBT — render→outbox and web→render coupling
PRE_EXISTING: YES (regression from render-module refactoring)
```

This is a **design debt issue**, not a code bug. The render module was refactored to depend on outbox coordination infrastructure and storage infrastructure, and the web layer directly references render domain types. The test has zero tolerance for new violations beyond the 2 identity-module exceptions.

---

## 3. MvcRouteInventoryTest Failure

### Test: `MvcRouteInventoryTest#captureRouteInventory`

**Exception**: `java.lang.IllegalStateException: Failed to load ApplicationContext`
**Root Cause Chain**:
```
IllegalStateException
  → BeanCreationException: Error creating bean with name 'flyway'
    → FlywaySqlUnableToConnectToDbException: Unable to obtain connection
      → PSQLException: FATAL: password authentication failed for user "test"
```

### Root Cause

`MvcRouteInventoryTest` does **NOT** extend `PostgresTestContainerSupport`. It loads the full ApplicationContext with `@SpringBootTest(RANDOM_PORT)` and `@ActiveProfiles({"test","preview"})`, which picks up `application-test.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/test
    username: test
    password: test
```

No PostgreSQL is running on localhost:5432. The `FlywayConfiguration` bean tries to run migrations at context startup and fails.

### Classification

```
MODULE:  platform-app
CLASS:   MvcRouteInventoryTest
METHOD:  captureRouteInventory
ROOT_CAUSE: MISSING_TESTCONTAINERS_BASE_CLASS
FIX: Extend PostgresTestContainerSupport OR mock the DataSource
```

This is **NOT related to OOM** — it fails independently because of missing database.

---

## 4. ProviderRegistrationValidationTest Failure

### Test: `ProviderRegistrationValidationTest#initializationError`

**Exception**: `org.testcontainers.containers.ContainerLaunchException: Container startup failed for image postgres:15-alpine`
**Root Cause Chain**:
```
ContainerLaunchException
  → RetryCountExceededException
    → ContainerLaunchException: Could not create/start container
      → RuntimeException: java.io.IOException: Broken pipe
```

### Root Cause

The Testcontainers PostgreSQL container fails to start via Podman. The `Broken pipe` error occurs when writing to the Podman Unix socket (`/run/user/1000/podman/podman.sock`). This is a Podman/Testcontainers compatibility issue.

**Environment**: Podman 5.4.2 (not Docker), `DOCKER_HOST=unix:///run/user/1000/podman/podman.sock`

### Classification

```
MODULE:  platform-app
CLASS:   ProviderRegistrationValidationTest
METHOD:  initializationError
ROOT_CAUSE: TESTCONTAINERS_PODMAN_COMPATIBILITY
FIX: Verify Podman socket is running; may need podman machine start or socket configuration
```

This is the **same root cause** as the render-module `RenderJobRepositoryTest` failure.

---

## 5. Full Suite Failure Inventory

### Modules with FAILURES

| Module | Failing Tests | Failure Count |
|--------|--------------|---------------|
| platform-app | Gradle Test Executor (OOM), ModularityTest, MvcRouteInventoryTest, ProviderRegistrationValidationTest, RealHttpSecurityBoundaryTest (OOM cascades), RenderExecutionBoundaryTest (OOM cascades), RenderJobInstanceProvenanceTest (OOM cascades) | 4 explicit + OOM cascade |
| render-module | RenderJobRepositoryTest, RenderPipelineE2ECharacterizationTest, RenderOrchestratorServiceCharacterizationTest, TimelineRevisionRenderServiceTest | 6 failures |

### Modules with ALL TESTS PASSING

ai-module, artifact-catalog-module, audit-compliance-module, billing-module, cloud-resource-module, commerce-module, compatibility-migration-module, config-module, datasource-module, delivery-module, entitlement-module, extension-module, federation-query-module, identity-access-module, notification-module, observability-module, outbox-event-module, payment-module, policy-governance-module, prompt-module, quota-billing-module, sandbox-runtime-module, scheduler-module, secrets-config-module, shared-kernel, social-publish-module, storage-module, user-analytics-module, workflow-module

### Modules with NO TEST SOURCE

config-module (NO-SOURCE)

### Modules NOT IN BUILD (hold/excluded)

spring-ai-adapter, remote-render-worker, sandbox-worker, product-layer-module

---

## 6. Render-Module Failures (For Reference — Owned by Agents A/B)

### 6.1 RenderJobRepositoryTest#initializationError

```
EXCEPTION: ContainerLaunchException: postgres:15-alpine
ROOT_CAUSE: TESTCONTAINERS_PODMAN_COMPATIBILITY (same as ProviderRegistrationValidationTest)
```

### 6.2 RenderPipelineE2ECharacterizationTest (2 failures)

**scenarioI_jobStatusLifecycle** (line 587):
```
AssertionFailedError: expected: <true> but was: <false>
```
Job status lifecycle assertion fails — status tracking doesn't match expected behavior.

**scenarioK_providerFailureHandling** (line 661):
```
AssertionFailedError: expected: <FAILED> but was: <EXECUTING>
```
When a provider throws an exception, the job status should transition to FAILED but remains EXECUTING. This is a **failure durability bug** — the provider failure is not propagated to the job status.

### 6.3 RenderOrchestratorServiceCharacterizationTest (1 failure)

**executeExistingRenderJobHandlesProviderFailure** (line 287):
```
AssertionFailedError: expected: <FAILED> but was: <EXECUTING>
```
Same root cause as scenarioK — provider failure not propagating to FAILED status.

### 6.4 TimelineRevisionRenderServiceTest (2 failures)

**r61MissingInputProductFailsClosed** (line 322):
```
AssertionFailedError: Error must indicate resolution failure: Cannot resolve media URI: asset://ast_smoke_001
  expected: <true> but was: <false>
```

**inputProductNotReadyFailsClosed** (same pattern):
```
AssertionFailedError: Error must indicate resolution failure: Cannot resolve media URI: asset://ast_smoke_001
  expected: <true> but was: <false>
```

Both fail because the error message doesn't contain the expected "resolution failure" indicator text when an input product is missing or not READY. The exception is thrown but the message format doesn't match the assertion.

---

## 7. Required Decisions

### OOM_ROOT_CAUSE_IDENTIFIED: ✅ YES

The OOM is caused by loading 16+ unique Spring ApplicationContexts in a single Gradle test worker process with default ~512MB heap.

### OOM_REQUIRES_CODE_FIX: ❌ NO

No application code is defective. The OOM is a test infrastructure configuration issue.

### OOM_REQUIRES_TEST_INFRASTRUCTURE_FIX: ✅ YES

**Primary fix**: Add `gradle.properties` at project root:
```properties
org.gradle.jvmargs=-Xmx2g -XX:+HeapDumpOnOutOfMemoryError
```

**Secondary fix options** (if 2GB is still insufficient):
1. Add `tasks.withType<Test> { maxHeapSize = "2g" }` to root `build.gradle.kts`
2. Use `forkEvery = 10` to fork a new JVM every 10 tests
3. Reduce unique context configurations by consolidating `@TestPropertySource` properties

### OOM_REQUIRES_JUSTIFIED_HEAP_CHANGE: ✅ YES

The default ~512MB is legitimately too low for a 30+ module Spring Boot application running 16+ integration test contexts. 2GB is justified given:
- 27+ module dependencies loaded per context
- Embedded Tomcat per RANDOM_PORT context
- 5 HikariCP connections per context
- Flyway migrations per context

### ALL_REPOSITORY_FAILURES_CLASSIFIED: ✅ YES

| # | Module | Class | Method | Exception | Root Cause | Minimal Repair |
|---|--------|-------|--------|-----------|------------|----------------|
| 1 | platform-app | Gradle Test Executor | N/A | TestSuiteExecutionException (OOM) | TEST_WORKER_OOM + SPRING_CONTEXT_EXPLOSION | Add gradle.properties with -Xmx2g |
| 2 | platform-app | ModularityTest | modularityViolationsWithinBudget | AssertionFailedError (123 violations) | MODULITH_BOUNDARY_DEBT | Expand ALLOWED_VIOLATIONS or fix boundaries |
| 3 | platform-app | MvcRouteInventoryTest | captureRouteInventory | IllegalStateException (Flyway → PSQLException) | MISSING_TESTCONTAINERS_BASE_CLASS | Extend PostgresTestContainerSupport |
| 4 | platform-app | ProviderRegistrationValidationTest | initializationError | ContainerLaunchException (Broken pipe) | TESTCONTAINERS_PODMAN_COMPATIBILITY | Verify Podman socket; test container runtime |
| 5 | render-module | RenderJobRepositoryTest | initializationError | ContainerLaunchException (Broken pipe) | TESTCONTAINERS_PODMAN_COMPATIBILITY | Same as #4 |
| 6 | render-module | RenderPipelineE2ECharacterizationTest | scenarioI_jobStatusLifecycle | AssertionFailedError (true/false) | JOB_STATUS_ASSERTION_MISMATCH | Owned by Agent A/B |
| 7 | render-module | RenderPipelineE2ECharacterizationTest | scenarioK_providerFailureHandling | AssertionFailedError (FAILED≠EXECUTING) | PROVIDER_FAILURE_NOT_PROPAGATED | Owned by Agent A/B |
| 8 | render-module | RenderOrchestratorServiceCharacterizationTest | executeExistingRenderJobHandlesProviderFailure | AssertionFailedError (FAILED≠EXECUTING) | PROVIDER_FAILURE_NOT_PROPAGATED | Owned by Agent A/B |
| 9 | render-module | TimelineRevisionRenderServiceTest | r61MissingInputProductFailsClosed | AssertionFailedError (message mismatch) | ERROR_MESSAGE_FORMAT_MISMATCH | Owned by Agent A/B |
| 10 | render-module | TimelineRevisionRenderServiceTest | inputProductNotReadyFailsClosed | AssertionFailedError (message mismatch) | ERROR_MESSAGE_FORMAT_MISMATCH | Owned by Agent A/B |

**Zero 'OTHER' or 'UNKNOWN' classifications remain.**

---

## 8. Cascade Failure Analysis

The OOM in the test worker causes cascade failures:

1. **RealHttpSecurityBoundaryTest** (18 tests) — All fail with `IllegalStateException: Failed to load ApplicationContext` because the OOM prevents context creation. First failure shows `OutOfMemoryError at ConfigurationPropertyName.java:1008`.

2. **RenderExecutionBoundaryTest** (6 tests) — All fail with `IllegalStateException` because context loading fails. First failure shows `OutOfMemoryError at Unsafe.java:1396`.

3. **RenderJobInstanceProvenanceTest** (3 tests) — All fail with `IllegalStateException`. First failure shows `OutOfMemoryError`.

These are **not independent failures** — they are OOM cascade victims. Fixing the heap will resolve all of them.

---

## 9. Recommendations

### Immediate (unblocks platform-app green)

1. **Create `gradle.properties`** at project root:
   ```properties
   org.gradle.jvmargs=-Xmx2g -XX:+HeapDumpOnOutOfMemoryError
   ```

2. **Fix MvcRouteInventoryTest**: Extend `PostgresTestContainerSupport` or add `@MockBean` for DataSource.

3. **Verify Podman socket**: Ensure `podman machine` is running and socket is accessible for Testcontainers.

### Short-term (reduces context explosion)

4. **Consolidate `@TestPropertySource`**: Many test classes duplicate the same property overrides. Extract a shared `@TestPropertySource` annotation or use a common base class.

5. **Use `@ContextConfiguration` sharing**: Ensure tests with identical configuration reuse cached contexts.

### Medium-term (modulith debt)

6. **Update `ALLOWED_VIOLATIONS`** in ModularityTest to document the render→outbox and web→render coupling as known debt, OR fix the boundaries.
