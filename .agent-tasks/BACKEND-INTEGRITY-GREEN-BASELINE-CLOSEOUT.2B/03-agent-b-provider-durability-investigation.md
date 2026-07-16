# Agent B: Provider Failure Durability Investigation

## Classification: NEW_INTEGRATION_TEST_REQUIRED

The mock characterization tests prove the CAS SQL pattern but do NOT prove REQUIRES_NEW transactional durability. No existing integration test exercises the real `RenderJobFailureService` bean. A new focused integration test is required.

---

## A. Current Mock Evidence

### How the Mock Works

Both characterization tests (`RenderOrchestratorServiceCharacterizationTest`, `RenderPipelineE2ECharacterizationTest`) mock `RenderJobFailureService` with a `doAnswer` that:

1. Directly calls jOOQ DSL to update `render_job.status` to `"FAILED"` with `error_message`
2. Uses CAS: `WHERE id = jobId AND status IN ('SELECTING_PROVIDER', 'PROVIDER_SELECTED', 'EXECUTING', 'COMPLETING')`
3. If updated > 0, sets `error_message` again (second update without CAS guard)

```java
// From RenderOrchestratorServiceCharacterizationTest.java:110-127
RenderJobFailureService failureService = mock(RenderJobFailureService.class);
doAnswer(inv -> {
    String jobId = inv.getArgument(0);
    String reason = inv.getArgument(1);
    int updated = dsl.update(table("render_job"))
            .set(field("status"), "FAILED")
            .set(field("error_message"), reason)
            .set(field("updated_at"), OffsetDateTime.now())
            .where(field("id").eq(jobId).and(
                    field("status").in("SELECTING_PROVIDER", "PROVIDER_SELECTED", "EXECUTING", "COMPLETING")))
            .execute();
    if (updated > 0) {
        dsl.update(table("render_job"))
                .set(field("error_message"), reason)
                .where(field("id").eq(jobId))
                .execute();
    }
    return null;
}).when(failureService).recordDurableFailure(anyString(), anyString());
```

### What the Mock PROVES

- The CAS SQL pattern correctly targets the 4 expected active states
- The overall flow from Provider exception → `failureService.recordDurableFailure()` → DB `status=FAILED` works
- The error message is correctly persisted
- Provider is called once (via Mockito `verify`)
- The catch block in `RenderJobExecutionService.finishRenderPhaseInternal()` correctly delegates to `failureService`

### What the Mock Does NOT Prove

| Property | Proven by Mock? | Reason |
|----------|----------------|--------|
| REQUIRES_NEW commits independently | ❌ NO | Mock bypasses Spring AOP proxy entirely |
| Outer rollback preserves FAILED | ❌ NO | No separate transaction exists in the mock |
| Real CAS guard against stale overwrite | ⚠️ PARTIAL | Mock's inline SQL matches production, but not through real bean |
| Spring proxy delegates correctly | ❌ NO | Mock is not a Spring-managed bean |
| Error message persistence | ✅ YES | Mock's second update matches production `updateErrorMessage()` |

**Critical gap**: The mock simulates the SQL but not the transactional isolation. If `@Transactional(propagation = Propagation.REQUIRES_NEW)` were misconfigured (e.g., self-invocation, missing proxy), the mock would still pass.

---

## B. Production Code Path Analysis

### 1. RenderJobExecutionService.execute() — Catch Block

File: `render-module/src/main/java/com/example/platform/render/app/RenderJobExecutionService.java`

Multiple failure catch points call `failureService.recordDurableFailure()`:

```java
// Line 372-376: Provider render failure
try {
    renderResult = executeRenderWithOptionalDag(...);
} catch (Exception e) {
    log.error("Render failed for job {}", jobId, e);
    failureService.recordDurableFailure(jobId, "Render failed: " + e.getMessage());
    throw new IllegalStateException("Render failed", e);
}
```

Other catch points:
- Line 202-204: Script resolution failure
- Line 361: Billing reservation failure
- Line 405: Storage failure

### 2. RenderJobFailureService — @Transactional Annotation

File: `render-module/src/main/java/com/example/platform/render/app/RenderJobFailureService.java`

```java
@Service  // ← Separate Spring Bean
public class RenderJobFailureService {
    private final RenderJobRepository renderJobRepository;

    public RenderJobFailureService(RenderJobRepository renderJobRepository) {
        this.renderJobRepository = renderJobRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)  // ← Independent transaction
    public void recordDurableFailure(String jobId, String reason) {
        int updated = renderJobRepository.markActiveJobFailed(jobId, reason);
        if (updated > 0) {
            renderJobRepository.updateErrorMessage(jobId, reason);
            log.info("Durable failure recorded for job {}: {}", jobId, reason);
        } else {
            log.warn("Could not record durable failure for job {} (not in EXECUTING state)", jobId);
        }
    }
}
```

### 3. Key Questions Answered

| Question | Answer |
|----------|--------|
| Is failureService a separate Spring Bean? | ✅ YES — `@Service` annotation, separate class |
| Self-invocation risk? | ❌ NO — Injected into `RenderJobExecutionService` via constructor (line 88, 127, 155) |
| Exact CAS SQL? | `UPDATE render_job SET status='FAILED', error_message=?, updated_at=NOW() WHERE id=? AND status IN ('SELECTING_PROVIDER', 'PROVIDER_SELECTED', 'EXECUTING', 'COMPLETING')` |
| States it accepts? | SELECTING_PROVIDER, PROVIDER_SELECTED, EXECUTING, COMPLETING |
| States it rejects? | QUEUED, FAILED, COMPLETED, REJECTED, CANCELLED (returns 0) |

### 4. RenderJobRepository.markActiveJobFailed() — CAS SQL

File: `render-module/src/main/java/com/example/platform/render/infrastructure/RenderJobRepository.java` (lines 164-172)

```java
public int markActiveJobFailed(String jobId, String reason) {
    return dsl.update(table("render_job"))
            .set(field("status"), "FAILED")
            .set(field("error_message"), reason)
            .set(field("updated_at"), java.time.OffsetDateTime.now())
            .where(field("id").eq(jobId).and(
                    field("status").in("SELECTING_PROVIDER", "PROVIDER_SELECTED", "EXECUTING", "COMPLETING")))
            .execute();
}
```

### 5. Bean Wiring Summary

```
RenderJobExecutionService (@Service)
  ├── RenderJobRepository (@Repository) — real jOOQ
  ├── RenderJobClaimService (@Service) — @Transactional(REQUIRES_NEW)
  └── RenderJobFailureService (@Service) — @Transactional(REQUIRES_NEW)
        └── RenderJobRepository (@Repository) — real jOOQ
```

**No self-invocation risk**: `failureService` is a distinct bean injected via constructor. Spring AOP proxy will correctly intercept `@Transactional(REQUIRES_NEW)`.

---

## C. Existing Integration Test Coverage

### RenderJobRepositoryTest

File: `render-module/src/test/java/com/example/platform/render/infrastructure/RenderJobRepositoryTest.java`

- Extends `PostgresTestContainerSupport` ✅
- Tests basic CRUD: `create`, `findById`, `listByTenant`, `updateStatus`
- **Does NOT test** `markActiveJobFailed()` ❌
- **Does NOT test** `claimForSelection()` ❌
- **Does NOT test** CAS guard behavior ❌

### Vs0VerticalSliceIntegrationTest

File: `render-module/src/test/java/com/example/platform/render/integration/Vs0VerticalSliceIntegrationTest.java`

- Extends `PostgresTestContainerSupport` ✅
- Tests domain pipeline: timeline edit → caption → FFmpeg plan → provider binding → execution plan
- **Does NOT test** failure path ❌
- **Does NOT test** `RenderJobFailureService` ❌
- **Does NOT test** `RenderJobExecutionService` ❌

### Characterization Tests

- `RenderOrchestratorServiceCharacterizationTest`: Mocks `failureService` — proves SQL pattern, not transactional durability
- `RenderPipelineE2ECharacterizationTest`: Same mock approach

### Coverage Gap Summary

| Component | Unit Test | Integration Test | Transactional Test |
|-----------|-----------|------------------|-------------------|
| `RenderJobRepository.markActiveJobFailed()` | ❌ | ❌ | ❌ |
| `RenderJobRepository.claimForSelection()` | ❌ | ❌ | ❌ |
| `RenderJobFailureService.recordDurableFailure()` | ❌ (mocked) | ❌ | ❌ |
| `RenderJobClaimService.claimForSelection()` | ❌ (mocked) | ❌ | ❌ |
| Provider failure → FAILED flow | ✅ (mock) | ❌ | ❌ |

---

## D. Integration Test Design

### Test Class: `RenderJobFailureDurabilityIntegrationTest`

**Goal**: Prove that when a Provider throws, the job status is durably persisted as FAILED in real PostgreSQL, surviving outer transaction rollback.

### Approach

Use a lightweight Spring context (not full `@SpringBootTest`) with:
- Real `PostgreSQLContainer` from `PostgresTestContainerSupport`
- Real `RenderJobRepository` with real jOOQ `DSLContext`
- Real `RenderJobFailureService` wrapped in Spring AOP proxy
- `PlatformTransactionManager` for outer transaction simulation
- `TransactionTemplate` to control outer transaction boundaries

### Class Structure

```java
package com.example.platform.render.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.render.app.RenderJobFailureService;
import com.example.platform.render.infrastructure.RenderJobRepository;
import com.example.platform.render.testsupport.RenderTestSchemaFixture;
import com.example.platform.shared.test.PostgresTestContainerSupport;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;

/**
 * Integration test proving Provider failure durability with real PostgreSQL.
 *
 * <p>Validates:
 * 1. Real PostgreSQL persists FAILED status
 * 2. REQUIRES_NEW commits independently of outer transaction
 * 3. Outer rollback doesn't remove FAILED
 * 4. CAS uses correct states (rejects terminal states)
 * 5. Provider exception flow end-to-end
 */
@SpringBootTest(
    classes = RenderJobFailureDurabilityIntegrationTest.TestConfig.class,
    properties = {
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=never"
    }
)
class RenderJobFailureDurabilityIntegrationTest extends PostgresTestContainerSupport {

    @Autowired private RenderJobRepository renderJobRepository;
    @Autowired private RenderJobFailureService failureService;
    @Autowired private PlatformTransactionManager transactionManager;

    private static DSLContext dsl;

    @BeforeAll
    static void setUpSchema() {
        dsl = DSL.using(createDataSource(), org.jooq.SQLDialect.POSTGRES);
        RenderTestSchemaFixture.createSchema(dsl);
    }

    @BeforeEach
    void cleanUp() {
        RenderTestSchemaFixture.truncate(dsl);
    }

    // --- Test Methods ---

    @Test
    @DisplayName("1. recordDurableFailure persists FAILED to real PostgreSQL")
    void recordDurableFailurePersistsToRealPostgres() {
        // Given: a job in EXECUTING state
        insertJob("rj-dur-1", "EXECUTING");

        // When: we record a durable failure
        failureService.recordDurableFailure("rj-dur-1", "FFmpeg crashed");

        // Then: PostgreSQL has FAILED status
        var row = loadJob("rj-dur-1");
        assertEquals("FAILED", row.status);
        assertEquals("FFmpeg crashed", row.errorMessage);
    }

    @Test
    @DisplayName("2. REQUIRES_NEW commits independently — outer rollback preserves FAILED")
    void requiresNewSurvivesOuterRollback() {
        // Given: a job in EXECUTING state
        insertJob("rj-dur-2", "EXECUTING");

        // When: outer transaction rolls back after failureService succeeds
        TransactionTemplate outerTx = new TransactionTemplate(transactionManager);
        outerTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);

        assertThrows(RuntimeException.class, () -> outerTx.execute(status -> {
            // failureService uses REQUIRES_NEW — commits in separate transaction
            failureService.recordDurableFailure("rj-dur-2", "Provider timeout");

            // Simulate outer transaction failure
            throw new RuntimeException("Outer transaction fails");
        }));

        // Then: FAILED survives because REQUIRES_NEW committed independently
        var row = loadJob("rj-dur-2");
        assertEquals("FAILED", row.status,
                "FAILED must survive outer rollback — REQUIRES_NEW committed independently");
        assertEquals("Provider timeout", row.errorMessage);
    }

    @Test
    @DisplayName("3. CAS rejects terminal state COMPLETED — no stale overwrite")
    void casRejectsCompletedState() {
        // Given: a job already COMPLETED
        insertJob("rj-dur-3", "COMPLETED");

        // When: we try to record failure (simulating stale concurrent request)
        failureService.recordDurableFailure("rj-dur-3", "Stale failure");

        // Then: COMPLETED is preserved, not overwritten
        var row = loadJob("rj-dur-3");
        assertEquals("COMPLETED", row.status,
                "CAS must reject failure for COMPLETED job");
        assertNull(row.errorMessage,
                "Error message must not be set for COMPLETED job");
    }

    @Test
    @DisplayName("4. CAS rejects terminal state FAILED — no overwrite of existing failure")
    void casRejectsAlreadyFailedState() {
        // Given: a job already FAILED
        insertJob("rj-dur-4", "FAILED");
        renderJobRepository.updateStatusWithError("rj-dur-4", "FAILED", "Original failure");

        // When: we try to record another failure
        failureService.recordDurableFailure("rj-dur-4", "Second failure");

        // Then: original failure message is preserved
        var row = loadJob("rj-dur-4");
        assertEquals("FAILED", row.status);
        assertEquals("Original failure", row.errorMessage,
                "CAS must reject overwrite of already-FAILED job");
    }

    @Test
    @DisplayName("5. CAS accepts SELECTING_PROVIDER — active state transition")
    void casAcceptsSelectingProviderState() {
        insertJob("rj-dur-5", "SELECTING_PROVIDER");

        failureService.recordDurableFailure("rj-dur-5", "Selection failed");

        var row = loadJob("rj-dur-5");
        assertEquals("FAILED", row.status);
    }

    @Test
    @DisplayName("6. CAS accepts PROVIDER_SELECTED — active state transition")
    void casAcceptsProviderSelectedState() {
        insertJob("rj-dur-6", "PROVIDER_SELECTED");

        failureService.recordDurableFailure("rj-dur-6", "Provider rejected");

        var row = loadJob("rj-dur-6");
        assertEquals("FAILED", row.status);
    }

    @Test
    @DisplayName("7. CAS accepts COMPLETING — active state transition")
    void casAcceptsCompletingState() {
        insertJob("rj-dur-7", "COMPLETING");

        failureService.recordDurableFailure("rj-dur-7", "Storage write failed");

        var row = loadJob("rj-dur-7");
        assertEquals("FAILED", row.status);
    }

    @Test
    @DisplayName("8. CAS rejects QUEUED — job not yet claimed")
    void casRejectsQueuedState() {
        insertJob("rj-dur-8", "QUEUED");

        failureService.recordDurableFailure("rj-dur-8", "Should not persist");

        var row = loadJob("rj-dur-8");
        assertEquals("QUEUED", row.status,
                "CAS must reject failure for QUEUED job — not yet claimed");
    }

    // --- Helpers ---

    private void insertJob(String jobId, String status) {
        dsl.insertInto(DSL.table("render_job"))
                .columns(DSL.field("id"), DSL.field("project_id"), DSL.field("tenant_id"),
                        DSL.field("timeline_snapshot_id"), DSL.field("profile"),
                        DSL.field("status"), DSL.field("created_at"))
                .values(jobId, "proj-1", "tenant-1", "snap-1", "default_1080p",
                        status, OffsetDateTime.now())
                .execute();
    }

    private JobRow loadJob(String jobId) {
        var r = dsl.select(DSL.field("status"), DSL.field("error_message"))
                .from(DSL.table("render_job"))
                .where(DSL.field("id").eq(jobId))
                .fetchOne();
        return new JobRow(
                r.get(DSL.field("status"), String.class),
                r.get(DSL.field("error_message"), String.class));
    }

    private record JobRow(String status, String errorMessage) {}

    // --- Spring Test Configuration ---

    @Configuration
    @EnableTransactionManagement
    static class TestConfig {

        @Bean
        public javax.sql.DataSource testDataSource() {
            return createDataSource();
        }

        @Bean
        public DSLContext dslContext(javax.sql.DataSource ds) {
            return DSL.using(ds, org.jooq.SQLDialect.POSTGRES);
        }

        @Bean
        public org.springframework.jdbc.datasource.DataSourceTransactionManager transactionManager(
                javax.sql.DataSource ds) {
            return new org.springframework.jdbc.datasource.DataSourceTransactionManager(ds);
        }

        @Bean
        public RenderJobRepository renderJobRepository(DSLContext dsl) {
            return new RenderJobRepository(dsl);
        }

        @Bean
        public RenderJobFailureService failureService(RenderJobRepository repo) {
            return new RenderJobFailureService(repo);
        }
    }
}
```

### Test Methods Summary

| # | Test | What It Proves |
|---|------|----------------|
| 1 | `recordDurableFailurePersistsToRealPostgres` | Real PostgreSQL persists FAILED |
| 2 | `requiresNewSurvivesOuterRollback` | REQUIRES_NEW commits independently; outer rollback doesn't remove FAILED |
| 3 | `casRejectsCompletedState` | CAS rejects terminal state COMPLETED |
| 4 | `casRejectsAlreadyFailedState` | CAS rejects already-FAILED (no stale overwrite) |
| 5 | `casAcceptsSelectingProviderState` | CAS accepts SELECTING_PROVIDER |
| 6 | `casAcceptsProviderSelectedState` | CAS accepts PROVIDER_SELECTED |
| 7 | `casAcceptsCompletingState` | CAS accepts COMPLETING |
| 8 | `casRejectsQueuedState` | CAS rejects QUEUED (not yet claimed) |

### Why This Design

- **Real PostgreSQL**: Extends `PostgresTestContainerSupport` — no mocks for persistence
- **Real Repository**: `RenderJobRepository` wired with real jOOQ `DSLContext`
- **Real FailureService**: `RenderJobFailureService` is a Spring-managed bean with real `@Transactional` proxy
- **Real TransactionManager**: `DataSourceTransactionManager` enables `TransactionTemplate` for outer transaction simulation
- **No Provider mock needed**: The test directly calls `failureService.recordDurableFailure()` — the Provider is upstream of this call
- **Focused scope**: Tests the transactional durability contract, not the full execution pipeline

---

## E. Spring Context Requirements

### Can we use @SpringBootTest with Testcontainers?

**Yes, but with a focused configuration.** The existing `Vs0VerticalSliceIntegrationTest` uses manual bean construction without Spring context. For transaction testing, we need Spring's `PlatformTransactionManager`.

### Recommended Approach

Use `@SpringBootTest` with an inner `@Configuration` class that provides only the beans needed:
- `DataSource` (from Testcontainers)
- `DSLContext` (jOOQ)
- `DataSourceTransactionManager` (Spring TX)
- `RenderJobRepository`
- `RenderJobFailureService`

This avoids bootstrapping the full application context (which would require all module dependencies, Flyway migrations, etc.) while still providing real Spring AOP proxy for `@Transactional`.

### Vs0VerticalSliceIntegrationTest as Model

The existing test uses manual construction without Spring context:
```java
@BeforeAll
static void setUpDatabase() {
    dataSource = createDataSource();
    dsl = DSL.using(dataSource, org.jooq.SQLDialect.POSTGRES);
    RenderTestSchemaFixture.createSchema(dsl);
}
```

For our test, we need Spring context for transaction management, so we use `@SpringBootTest` with a minimal `@Configuration`. The `RenderTestSchemaFixture.createSchema(dsl)` approach works for both.

---

## F. Summary

### What's Needed

1. **New test class**: `RenderJobFailureDurabilityIntegrationTest`
2. **Location**: `render-module/src/test/java/com/example/platform/render/integration/`
3. **8 test methods** covering: persistence, REQUIRES_NEW isolation, CAS state guards
4. **Real PostgreSQL** via `PostgresTestContainerSupport`
5. **Real Spring transaction management** via `@SpringBootTest` with focused `@Configuration`

### Classification Rationale

- **NOT** `EXISTING_REAL_DURABILITY_PROOF_SUFFICIENT`: No existing test exercises the real `RenderJobFailureService` bean or proves REQUIRES_NEW isolation
- **NOT** `PRODUCTION_TRANSACTION_FIX_REQUIRED`: The production code is correctly structured (`@Service` bean, `@Transactional(REQUIRES_NEW)`, constructor injection, no self-invocation risk)
- **IS** `NEW_INTEGRATION_TEST_REQUIRED`: A focused integration test with real PostgreSQL and real Spring transaction management is needed to prove the durability contract
