# 08 — Agent E Independent Verification

**Commit:** `97f1787` — `fix: make render finalization failures durable`
**Branch:** `fix/renderjob-finalization-active-failure-durability`
**Verified:** 2026-07-15
**Verifier:** Agent E (independent subagent)

---

## 1. FALLBACKING/RETRYING removed from state machine ⚠️ PARTIAL

### What changed (diff):

```java
// RenderJobStateMachine.java — VALID_TRANSITIONS map
// REMOVED from EXECUTING outgoing transitions:
-  RenderJobStatus.FALLBACKING,
-  RenderJobStatus.RETRYING,
```

`EXECUTING` now transitions to only `{COMPLETING, FAILED, CANCELLED}` — FALLBACKING and RETRYING are no longer reachable as target states.

### What remains:

- `FALLBACKING` and `RETRYING` **entries still exist** in `VALID_TRANSITIONS` as source states (lines 54–63):
  ```java
  Map.entry(RenderJobStatus.FALLBACKING, Set.of(
      RenderJobStatus.EXECUTING,
      RenderJobStatus.FAILED,
      RenderJobStatus.CANCELLED
  )),
  Map.entry(RenderJobStatus.RETRYING, Set.of(
      RenderJobStatus.EXECUTING,
      RenderJobStatus.FAILED,
      RenderJobStatus.CANCELLED
  )),
  ```
- `RenderJobStatus.java` enum still defines `FALLBACKING` and `RETRYING` members (lines 52, 58).
- `isProviderState()` still references both (line 119).

These are **unreachable dead code** since no transition leads to them, but they were not cleaned up.

### ⚠️ CRITICAL: Test not updated

`RenderJobStateMachineErrorModelTest$ValidTransitions` was **NOT** updated. Two tests now **FAIL**:

| Test | Result |
|------|--------|
| `EXECUTING → FALLBACKING is valid` | ❌ FAIL — `expected: <true> but was: <false>` |
| `EXECUTING → RETRYING is valid` | ❌ FAIL — `expected: <true> but was: <false>` |

**Evidence:** `render-module/build/test-results/test/TEST-com.example.platform.render.domain.RenderJobStateMachineErrorModelTest$ValidTransitions.xml`

```
tests="12" skipped="0" failures="2"
```

**Root cause:** The implementation removed the transitions but did not update or invert the corresponding test assertions. These tests should either be deleted or changed to assert the transitions are **invalid**.

---

## 2. Billing failure uses REQUIRES_NEW ✅ PASS

### Diff evidence:

```java
// RenderJobExecutionService.java — finishRenderPhaseInternal() billing path
// BEFORE:
-  stateMachine.transition(jobId, RenderJobStatus.EXECUTING, RenderJobStatus.FAILED,
-          "Billing reservation failed: " + reservation.error(), "RenderJobExecutionService");
-  failJob(jobId, projectId, RenderJobStatus.EXECUTING, "BILLING_FAILED",
-          "Billing reservation failed: " + reservation.error());

// AFTER:
+  failureService.recordDurableFailure(jobId, "Billing reservation failed: " + reservation.error());
```

### Implementation verification:

```java
// RenderJobFailureService.java (lines 29-30)
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void recordDurableFailure(String jobId, String reason) {
```

Uses `@Transactional(propagation = Propagation.REQUIRES_NEW)` — commits in an independent transaction that survives outer rollback.

### CAS verification:

```java
// RenderJobRepository.markActiveJobFailed() (line 164-172)
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

Atomic CAS — only transitions active states to FAILED. Correct.

---

## 3. Storage failure uses REQUIRES_NEW ✅ PASS

### Diff evidence:

```java
// RenderJobExecutionService.java — storage upload path
// BEFORE:
-  stateMachine.transition(jobId, RenderJobStatus.COMPLETING, RenderJobStatus.FAILED,
-          "Storage failed: " + e.getMessage(), "RenderJobExecutionService");
-  failJob(jobId, projectId, RenderJobStatus.COMPLETING, "STORAGE_FAILED", "Storage failed: " + e.getMessage());

// AFTER:
+  failureService.recordDurableFailure(jobId, "Storage failed: " + e.getMessage());
```

Same `RenderJobFailureService.recordDurableFailure()` method with `REQUIRES_NEW` propagation. Same CAS guard. ✅

### Additional change: RenderOrchestratorService.submitRenderJob()

```java
// BEFORE:
-  return executionService.execute(request.tenantId(), jobId);

// AFTER:
+  return executionService.executeAfterSubmit(request.tenantId(), jobId);
```

`executeAfterSubmit()` is annotated `@Transactional` and skips the `REQUIRES_NEW` claim since the row hasn't been committed yet. Correct behavior for same-transaction submit path.

### Additional change: Test mocks → real implementations

```java
// RenderOrchestratorServiceCharacterizationTest + RenderPipelineE2ECharacterizationTest
// BEFORE:
-  mock(RenderJobClaimService.class), mock(RenderJobFailureService.class));

// AFTER:
+  new RenderJobClaimService(renderJobRepository),
+  new RenderJobFailureService(renderJobRepository));
```

Tests now use real `REQUIRES_NEW`-annotated services instead of mocks, ensuring the actual transaction propagation is exercised. ✅

---

## 4. Build compiles ✅ PASS

```
$ ./gradlew :render-module:compileJava :platform-app:compileJava

BUILD SUCCESSFUL in 5s
35 actionable tasks: 35 up-to-date
```

No compilation errors. All modified files compile cleanly.

---

## 5. Architecture guard passes ✅ PASS

```
$ bash scripts/check-architecture-drift.sh

=== Summary ===
Checks: 32
Failed: 0
✅ All architecture drift checks passed
```

All 32 drift checks pass: required classes, runtime profile switching, storage exposure, report-only evaluator, upload rejection, persistence, deferred status, HOLD module governance, admin routes, SPA fallback.

---

## Summary

| # | Check | Result |
|---|-------|--------|
| 1 | FALLBACKING/RETRYING removed from state machine | ⚠️ PARTIAL — transitions removed, states still exist as dead code, **2 tests broken** |
| 2 | Billing failure uses REQUIRES_NEW | ✅ PASS |
| 3 | Storage failure uses REQUIRES_NEW | ✅ PASS |
| 4 | Build compiles | ✅ PASS |
| 5 | Architecture guard passes | ✅ PASS (32/32) |

### Required follow-up

**BLOCKING:** `RenderJobStateMachineErrorModelTest$ValidTransitions` must be updated:
- `EXECUTING → FALLBACKING is valid` → change to assert **invalid** or delete
- `EXECUTING → RETRYING is valid` → change to assert **invalid** or delete

**NON-BLOCKING:** Dead code cleanup candidates:
- Remove `FALLBACKING`/`RETRYING` entries from `VALID_TRANSITIONS` map (lines 54–63)
- Remove `FALLBACKING`/`RETRYING` enum values from `RenderJobStatus` (lines 52, 58)
- Remove `isProviderState()` references (line 119)
- Remove Javadoc references (lines 15–16)

### Test failure classification

| Test suite | Failure count | Cause | Related to commit? |
|-----------|---------------|-------|---------------------|
| `RenderJobStateMachineErrorModelTest$ValidTransitions` | 2 | Stale assertions after transition removal | **YES — introduced by this commit** |
| FFmpeg/smoke tests (multiple) | ~35 | `libx264` encoder not available in env | No — pre-existing environment issue |
| `RenderJobRepositoryTest` | 1 | TestContainers Postgres startup failure | No — Docker/network issue |
| `TimelineSpecTest`, `BasicTimelineEditingModelTest`, etc. | ~4 | IllegalArgumentException assertions | No — pre-existing |

**Total test failures caused by this commit: 2** (both in `RenderJobStateMachineErrorModelTest$ValidTransitions`).
