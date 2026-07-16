# Agent C: Provider Failure PostgreSQL Durability Proof Audit

**Date:** 2026-07-16
**Commit:** eb8521f (docs: complete green test baseline recovery evidence)
**Classification:** `TEST_DOUBLE_ONLY_FIXED`

---

## A. Current Test Coverage

### A1. RenderOrchestratorServiceCharacterizationTest — failureService Mock

**File:** `render-module/src/test/java/com/example/platform/render/app/RenderOrchestratorServiceCharacterizationTest.java`

```java
RenderJobFailureService failureService = mock(RenderJobFailureService.class);
// Mock recordDurableFailure to update DB status (simulates REQUIRES_NEW durable failure)
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
    // ... also updates error_message unconditionally
    return null;
}).when(failureService).recordDurableFailure(anyString(), anyString());
```

**Key observation:** `failureService` is a Mockito mock. The `doAnswer` stub runs raw jOOQ updates directly against the test PostgreSQL container, bypassing the real `RenderJobFailureService` class entirely.

### A2. RenderPipelineE2ECharacterizationTest — failureService Mock

**File:** `render-module/src/test/java/com/example/platform/render/app/RenderPipelineE2ECharacterizationTest.java`

Identical pattern: `RenderJobFailureService failureService = mock(RenderJobFailureService.class)` with the same `doAnswer` stub that performs raw jOOQ updates.

### A3. Both Tests Also Mock RenderJobClaimService

```java
RenderJobClaimService claimService = mock(RenderJobClaimService.class);
when(claimService.claimForSelection(anyString())).thenAnswer(inv -> {
    String jobId = inv.getArgument(0);
    int updated = dsl.update(table("render_job"))
            .set(field("status"), "SELECTING_PROVIDER")
            .where(field("id").eq(jobId).and(field("status").eq("QUEUED")))
            .execute();
    return updated > 0;
});
```

Same pattern: raw jOOQ, bypasses real `@Transactional(propagation = Propagation.REQUIRES_NEW)`.

---

## B. Production Code Path Analysis

### B1. RenderJobExecutionService.execute() — Failure Handling

In `RenderJobExecutionService` (line 372-376):
```java
} catch (Exception e) {
    log.error("Render failed for job {}", jobId, e);
    failureService.recordDurableFailure(jobId, "Render failed: " + e.getMessage());
    throw new IllegalStateException("Render failed", e);
}
```

The pattern is: **catch → call failureService → re-throw**. The outer `@Transactional` will rollback because the exception propagates. The durability guarantee depends entirely on `failureService.recordDurableFailure()` committing in its own transaction.

### B2. RenderJobFailureService.recordDurableFailure() — The Real Implementation

```java
@Service
public class RenderJobFailureService {
    private final RenderJobRepository renderJobRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
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

**Critical design:** `@Transactional(propagation = Propagation.REQUIRES_NEW)` means this method runs in a separate database transaction that commits independently of the caller's transaction. This is the mechanism that makes the failure record durable even when the outer transaction rolls back.

### B3. RenderJobRepository.markActiveJobFailed() — CAS SQL

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

**CAS guard:** Only transitions to FAILED if current status is one of the four active states. This prevents re-failing an already-failed or completed job.

### B4. RenderJobClaimService — Same REQUIRES_NEW Pattern

```java
@Service
public class RenderJobClaimService {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean claimForSelection(String jobId) {
        int claimed = renderJobRepository.claimForSelection(jobId);
        // ...
    }
}
```

Both claim and failure use REQUIRES_NEW for transactional independence.

---

## C. Existing Integration Test Coverage

### C1. Tests Using PostgresTestContainerSupport in render-module

| Test Class | Tests failureService? | Tests ClaimService? | Tests markActiveJobFailed? |
|---|---|---|---|
| RenderOrchestratorServiceCharacterizationTest | **MOCKED** | **MOCKED** | No |
| RenderPipelineE2ECharacterizationTest | **MOCKED** | **MOCKED** | No |
| RenderJobRepositoryTest | N/A | N/A | **NO** |
| Vs0VerticalSliceIntegrationTest | N/A | N/A | No |
| StaleRenderJobCompensatorTest | N/A | N/A | No |
| StaleRenderJobCompensationServiceTest | N/A | N/A | No |
| RenderJobStatusHistoryRepositoryTest | N/A | N/A | No |

### C2. RenderJobRepositoryTest — No markActiveJobFailed Test

The `RenderJobRepositoryTest` (which uses real PostgreSQL) tests:
- `createAndFindById`
- `findByIdReturnsEmptyForMissing`
- `findByIdAndProjectAndTenant` (positive + negative)
- `listByTenant`
- `listByProjectAndTenant`
- `updateStatus`
- `updateStatusAndClearError`
- `existsByIdAndTenant`
- `findTenantIdById`
- `findProjectTenantId`
- `listAll`

**Missing:** No test for `markActiveJobFailed()`, `markExecutingJobFailed()`, or the CAS guard logic.

### C3. No RenderJobFailureServiceTest Exists

Searched the entire repository — no dedicated test for `RenderJobFailureService`.

### C4. No RenderJobClaimServiceTest Exists

Searched the entire repository — no dedicated test for `RenderJobClaimService`.

### C5. No Integration Test Exercises Real Failure Path

No test anywhere in the codebase:
1. Uses the real `RenderJobFailureService` (not mocked)
2. Verifies that `REQUIRES_NEW` actually commits independently
3. Tests the CAS guard on `markActiveJobFailed`

---

## D. Durability Proof Assessment

### D1. What the Mock Proves

The characterization tests' mock stub proves:

1. **Catch block invokes failureService** — when Provider throws, `RenderJobExecutionService` catches the exception and calls `failureService.recordDurableFailure()`
2. **DB update succeeds** — the mock's inline jOOQ code correctly sets status to FAILED and writes the error message
3. **CAS guard logic works** — the mock's WHERE clause only matches active states (SELECTING_PROVIDER, PROVIDER_SELECTED, EXECUTING, COMPLETING)
4. **End-to-end flow produces FAILED status** — after the catch → mock → DB update chain, the test verifies the job row has status=FAILED and error_message contains the provider error

### D2. What the Mock Does NOT Prove

The mock does NOT prove:

1. **REQUIRES_NEW transaction isolation** — the mock's jOOQ code runs in the same thread/transaction as the test, not in a Spring-managed REQUIRES_NEW transaction. There is no transaction boundary.

2. **Survival of outer rollback** — the critical durability scenario is: outer `@Transactional` rolls back (because exception propagates), but the failure record survives because it was committed in REQUIRES_NEW. The mock cannot test this because there is no outer transaction to roll back.

3. **Spring AOP proxy behavior** — `@Transactional(propagation = Propagation.REQUIRES_NEW)` only works through a Spring proxy. The mock bypasses the proxy entirely. Common pitfalls:
   - Self-invocation (calling from within the same class) bypasses the proxy
   - Missing `@EnableTransactionManagement` would silently skip REQUIRES_NEW
   - The method must be `public` and called on the proxy, not `this`

4. **Real CAS with PostgreSQL row-level locking** — the mock uses raw jOOQ without transaction isolation. With real PostgreSQL + REQUIRES_NEW, concurrent failures could race on the same job row. The CAS guard is designed for this, but the mock cannot validate it.

5. **The markActiveJobFailed → updateErrorMessage two-step** — the real `recordDurableFailure` calls `markActiveJobFailed()` (which sets both status and error_message in one CAS update) and then calls `updateErrorMessage()` again separately. This redundancy exists for robustness but the mock doesn't exercise this exact sequence.

### D3. The Core Gap

The characterization tests are **service-level tests with test doubles for the two critical durability components** (`RenderJobFailureService` and `RenderJobClaimService`). They test the orchestration logic (catch → call failureService → re-throw) but delegate the actual durability mechanism to mocked behavior.

This is a classic "test double integration gap":
- The service being tested (`RenderJobExecutionService`) calls a real Spring bean (`RenderJobFailureService`)
- But the bean is replaced with a mock that mimics the observable outcome (DB row updated) without exercising the actual mechanism (REQUIRES_NEW transaction)

---

## E. Classification

### `TEST_DOUBLE_ONLY_FIXED`

**Rationale:**
- The characterization tests prove the failure **routing** (catch → failureService → DB update)
- The mock stub performs the same SQL as the real code, proving the **SQL is correct**
- But the critical property — **transactional durability via REQUIRES_NEW** — is entirely untested
- No dedicated integration test for `RenderJobFailureService.recordDurableFailure()` exists
- No test for `RenderJobRepository.markActiveJobFailed()` exists (even in the repository-level tests)
- No test verifies that failure survives outer rollback

### What Would Constitute PRODUCTION_DURABLE_FAILURE_PROVEN

An integration test that:
1. Creates a job in EXECUTING state in real PostgreSQL
2. Starts an outer transaction that will roll back (e.g., by throwing after the failure call)
3. Calls the **real** `RenderJobFailureService.recordDurableFailure()` (not mocked)
4. Verifies the outer transaction rolled back (status changes from the outer transaction are gone)
5. Verifies the failure record persisted (status=FAILED, error_message set)

This would prove REQUIRES_NEW commits independently of the outer transaction.

---

## F. Specific Gaps and Recommendations

### F1. Missing: RenderJobFailureService Integration Test

**Priority: HIGH**

Create `RenderJobFailureServiceIntegrationTest` that:
- Uses real PostgreSQL (PostgresTestContainerSupport)
- Instantiates the real `RenderJobFailureService` with real `RenderJobRepository`
- Wraps in a Spring-like transaction that rolls back after calling `recordDurableFailure`
- Verifies the failure record persists despite the rollback

### F2. Missing: RenderJobRepository.markActiveJobFailed Test

**Priority: MEDIUM**

Add to `RenderJobRepositoryTest`:
- Test that `markActiveJobFailed()` transitions EXECUTING → FAILED
- Test that `markActiveJobFailed()` transitions SELECTING_PROVIDER → FAILED
- Test that `markActiveJobFailed()` returns 0 for QUEUED (CAS guard)
- Test that `markActiveJobFailed()` returns 0 for COMPLETED (CAS guard)

### F3. Missing: RenderJobClaimService Integration Test

**Priority: MEDIUM**

Same pattern as F1 — verify the REQUIRES_NEW claim survives outer rollback.

---

## G. Risk Assessment

The production code is correctly designed (`REQUIRES_NEW` + CAS). The risk is not that the code is wrong, but that **a regression could silently break the durability guarantee without any test catching it**. For example:
- Removing `@Transactional(propagation = Propagation.REQUIRES_NEW)` from `RenderJobFailureService`
- Changing the method to `private` (bypasses Spring AOP proxy)
- Calling `this.recordDurableFailure()` from within the same class (self-invocation bypass)

All of these would silently downgrade to REQUIRED propagation, meaning the failure record would roll back with the outer transaction — exactly the scenario the REQUIRES_NEW was designed to prevent. No existing test would detect this regression.
