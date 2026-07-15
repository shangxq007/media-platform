# Agent A: Provider Failure Durability & Timeline Error Contract

## Summary

| # | Test | Root Cause Classification | Decision |
|---|------|--------------------------|----------|
| 1 | `RenderOrchestratorServiceCharacterizationTest#executeExistingRenderJobHandlesProviderFailure` | `MOCK_DOES_NOT_SIMULATE_CAS` | `PROVIDER_FAILURE_TEST_DEFECT` |
| 2 | `RenderPipelineE2ECharacterizationTest#scenarioK_providerFailureHandling` | `MOCK_DOES_NOT_SIMULATE_CAS` | `PROVIDER_FAILURE_TEST_DEFECT` |
| 3 | `RenderPipelineE2ECharacterizationTest#scenarioI_jobStatusLifecycle` | `MISSING_HISTORY_FOR_CLAIM_TRANSITION` | `PROVIDER_FAILURE_TEST_DEFECT` (production co-defect) |
| 4 | `TimelineRevisionRenderServiceTest#R6.1: missing input Product fails closed` | `URI_FALLBACK_INSTEAD_OF_FAIL_CLOSED` | `TIMELINE_ERROR_PRODUCTION_CONTRACT_DEFECT` |
| 5 | `TimelineRevisionRenderServiceTest#R6.1: input Product not READY fails closed` | `URI_FALLBACK_INSTEAD_OF_FAIL_CLOSED` | `TIMELINE_ERROR_PRODUCTION_CONTRACT_DEFECT` |

---

## Part A: Provider Failure Durability

### A1. Test 1 — `executeExistingRenderJobHandlesProviderFailure`

**Error**: `expected: <FAILED> but was: <EXECUTING>`

#### Code Path Trace

1. Test inserts job with status `QUEUED` (line 270).
2. Calls `service.executeExistingRenderJob("tenant-4", "rj-4")` (line 281).
3. Enters `RenderOrchestratorService.executeExistingRenderJob()` (line 59) — annotated `@Transactional` but the service is manually constructed in the test without Spring proxies, so `@Transactional` has **no effect**.
4. Delegates to `RenderJobExecutionService.execute()` (line 167):
   - Reads job from DB, status = `QUEUED`
   - Calls `claimService.claimForSelection(jobId)` (line 186) — **MOCK** with `.thenAnswer()` that does direct jOOQ update: `QUEUED → SELECTING_PROVIDER` in DB. Status now `SELECTING_PROVIDER`.
   - Reloads job (line 192), status = `SELECTING_PROVIDER`
   - Resolves render script via `timelineSnapshotService.findPayload()` mock — succeeds
   - `updateStatus(SELECTING_PROVIDER, PROVIDER_SELECTED)` (line 222) — DB: `PROVIDER_SELECTED`
   - `updateStatus(PROVIDER_SELECTED, EXECUTING)` (line 227) — DB: `EXECUTING`
   - Calls `finishRenderPhaseInternal()` (line 238)
5. In `finishRenderPhaseInternal()` (line 321):
   - Reloads job, status = `EXECUTING`
   - Calls `executeRenderWithOptionalDag()` → `provider.render()` → **throws `RuntimeException("FFmpeg crashed")`** (test mock, line 273–274)
   - Catch block (line 372–376): calls `failureService.recordDurableFailure(jobId, "Render failed: FFmpeg crashed")`
   - **`failureService` is `mock(RenderJobFailureService.class)` (line 108) — no stubbing. Mockito returns void by default. No DB update occurs.**
   - Throws `IllegalStateException("Render failed", e)`
6. Exception propagates out. Test catches it (line 280).
7. Test reads DB (line 283–286): status is `EXECUTING`, not `FAILED`.

#### Root Cause

**`MOCK_DOES_NOT_SIMULATE_CAS`** — The `RenderJobFailureService` is mocked but has no stub for `recordDurableFailure()`. The mock returns void without executing the actual failure-recording logic.

The real `RenderJobFailureService.recordDurableFailure()` (file: `RenderJobFailureService.java`, line 29–38) uses:
- `@Transactional(propagation = Propagation.REQUIRES_NEW)` — independent transaction
- Calls `renderJobRepository.markActiveJobFailed(jobId, reason)` — CAS update: `WHERE status IN ('SELECTING_PROVIDER', 'PROVIDER_SELECTED', 'EXECUTING', 'COMPLETING') → FAILED`
- Then calls `renderJobRepository.updateErrorMessage(jobId, reason)`

The production code path is correct. The test's mock prevents it from working.

#### Evidence

- **Test file**: `render-module/src/test/java/com/example/platform/render/app/RenderOrchestratorServiceCharacterizationTest.java`
  - Line 108: `RenderJobFailureService failureService = mock(RenderJobFailureService.class);`
  - No `.when(failureService.recordDurableFailure(...)).thenAnswer(...)` stub
- **Production file**: `render-module/src/main/java/com/example/platform/render/app/RenderJobExecutionService.java`
  - Line 374: `failureService.recordDurableFailure(jobId, "Render failed: " + e.getMessage());`
- **Production file**: `render-module/src/main/java/com/example/platform/render/app/RenderJobFailureService.java`
  - Lines 29–38: `@Transactional(propagation = Propagation.REQUIRES_NEW)` + `markActiveJobFailed()`
- **Repository file**: `render-module/src/main/java/com/example/platform/render/infrastructure/RenderJobRepository.java`
  - Lines 164–172: `markActiveJobFailed()` — CAS `WHERE status IN ('SELECTING_PROVIDER', 'PROVIDER_SELECTED', 'EXECUTING', 'COMPLETING')`

#### Recommended Repair

Add a `thenAnswer` stub to the `failureService` mock that performs the actual DB update (matching the pattern already used for `claimService`):

```java
when(failureService.recordDurableFailure(anyString(), anyString())).thenAnswer(inv -> {
    String jobId = inv.getArgument(0);
    String reason = inv.getArgument(1);
    int updated = dsl.update(table("render_job"))
            .set(field("status"), "FAILED")
            .set(field("error_message"), reason)
            .where(field("id").eq(jobId).and(
                    field("status").in("SELECTING_PROVIDER", "PROVIDER_SELECTED", "EXECUTING", "COMPLETING")))
            .execute();
    return null; // void method
});
```

---

### A2. Test 2 — `scenarioK_providerFailureHandling`

**Error**: `expected: <FAILED> but was: <EXECUTING>`

#### Code Path Trace

1. Test submits job via `service.submitRenderJob(request)` (line 654).
2. `RenderOrchestratorService.submitRenderJob()` (line 49):
   - `submissionService.submit(request)` — creates job with status `QUEUED`
   - `executionService.execute(request.tenantId(), jobId)` — enters execution
3. `RenderJobExecutionService.execute()`:
   - `claimService.claimForSelection()` — MOCK, updates DB: `QUEUED → SELECTING_PROVIDER`
   - `resolveRenderScript()` — succeeds via snapshot mock
   - `updateStatus(SELECTING_PROVIDER, PROVIDER_SELECTED)` — DB: `PROVIDER_SELECTED`
   - `updateStatus(PROVIDER_SELECTED, EXECUTING)` — DB: `EXECUTING`
   - `finishRenderPhaseInternal()` → `provider.render()` → **throws `RuntimeException("FFmpeg crashed")`**
   - Catch: `failureService.recordDurableFailure(jobId, ...)` — **MOCK, does nothing**
   - Throws `IllegalStateException`
4. Test reads DB: status is `EXECUTING`.

#### Root Cause

**Identical to Test 1**: `MOCK_DOES_NOT_SIMULATE_CAS`. Same mock setup at line 111:

```java
RenderJobFailureService failureService = mock(RenderJobFailureService.class);
```

No stub for `recordDurableFailure()`.

#### Evidence

- **Test file**: `render-module/src/test/java/com/example/platform/render/app/RenderPipelineE2ECharacterizationTest.java`
  - Line 111: `RenderJobFailureService failureService = mock(RenderJobFailureService.class);`

#### Recommended Repair

Same as Test 1: add `thenAnswer` stub for `failureService.recordDurableFailure()` that performs the CAS DB update.

---

### A3. Test 3 — `scenarioI_jobStatusLifecycle`

**Error**: `expected: <true> but was: <false>`

#### Code Path Trace

1. Test submits job via `service.submitRenderJob(request)`.
2. `submitRenderJob()` → `submissionService.submit()` → `createQueuedJob()`:
   - Inserts job with status `QUEUED`
   - Records history: `null → QUEUED` via `historyRepository.record()` (line 215)
3. `executionService.execute()` (called from `submitRenderJob`):
   - `claimService.claimForSelection(jobId)` — MOCK, does direct jOOQ update: `QUEUED → SELECTING_PROVIDER`
   - **No history record for this transition.**
   - `updateStatus(SELECTING_PROVIDER, PROVIDER_SELECTED)` — records history: `SELECTING_PROVIDER → PROVIDER_SELECTED`
   - `updateStatus(PROVIDER_SELECTED, EXECUTING)` — records history: `PROVIDER_SELECTED → EXECUTING`
   - `finishRenderPhaseInternal()` → succeeds
   - `updateStatus(EXECUTING, COMPLETING)` — records history
   - `updateStatus(COMPLETING, COMPLETED)` — records history

4. Test assertion (lines 587–589):
```java
assertTrue(history.stream().anyMatch(r ->
    "QUEUED".equals(r.get(field("from_status"), String.class))
        && "SELECTING_PROVIDER".equals(r.get(field("to_status"), String.class))));
```

History table contains:
- `null → QUEUED` (from submission)
- `SELECTING_PROVIDER → PROVIDER_SELECTED`
- `PROVIDER_SELECTED → EXECUTING`
- `EXECUTING → COMPLETING`
- `COMPLETING → COMPLETED`

**Missing**: `QUEUED → SELECTING_PROVIDER`

#### Root Cause

**`MISSING_HISTORY_FOR_CLAIM_TRANSITION`** — The QUEUED → SELECTING_PROVIDER transition is performed by:
- Test mock `claimService.claimForSelection()` — direct jOOQ update, no history
- Real `RenderJobClaimService.claimForSelection()` (`RenderJobClaimService.java`, line 31) — calls `renderJobRepository.claimForSelection()` which is a bare CAS update (`RenderJobRepository.java`, line 130–136). **Neither the claim service nor the repository records history.**

In the `execute()` method, line 186, the claim is done without calling `updateStatus()` or `historyRepository.record()`.

Similarly, in `executeAfterSubmit()` line 266, `renderJobRepository.updateStatus(jobId, "SELECTING_PROVIDER")` is called directly without history recording.

This is a **production co-defect**: the claim transition bypasses history recording. However, the test was written expecting a behavior the production code doesn't implement, making this primarily a test defect — the test should either:
- Accept that `QUEUED → SELECTING_PROVIDER` is not in history, or
- The production code should be fixed to record this transition.

#### Evidence

- **Test file**: `RenderPipelineE2ECharacterizationTest.java`, lines 587–589
- **Production file**: `RenderJobExecutionService.java`, line 186: `claimService.claimForSelection(jobId)` — no history
- **Production file**: `RenderJobClaimService.java`, lines 30–40: no history recording
- **Production file**: `RenderJobRepository.java`, lines 130–136: `claimForSelection()` — bare CAS, no history

#### Recommended Repair

**Option A (minimal — adjust test)**: Change the test assertion to check for `SELECTING_PROVIDER → PROVIDER_SELECTED` instead, which IS recorded:

```java
assertTrue(history.stream().anyMatch(r ->
    "SELECTING_PROVIDER".equals(r.get(field("from_status"), String.class))
        && "PROVIDER_SELECTED".equals(r.get(field("to_status"), String.class))));
```

**Option B (fix production)**: Add history recording to the claim path. In `RenderJobExecutionService.execute()`, after the claim succeeds:

```java
if (claimed) {
    historyRepository.record(jobId, "QUEUED", "SELECTING_PROVIDER", "Claimed for selection", null);
}
```

This would also require the same in `executeAfterSubmit()` line 266.

---

## Part B: Timeline Error Contract

### B4. Test 4 — `R6.1: missing input Product fails closed`

**Error**: `Error must indicate resolution failure: Cannot resolve media URI: asset://ast_smoke_001 ==> expected: <true> but was: <false>`

#### Code Path Trace

1. No Product registered for asset `ast_smoke_001`.
2. `TimelineRevisionRenderService.render()` line 170:
   ```java
   var resolverResult = inputProductResolver.resolveWithBindings(mappingResult.sourceAssetIds(), productBindings);
   ```
3. `TimelineInputProductResolver.resolveWithBindings()` (line 64–131):
   - `productRuntime.findByAsset("ast_smoke_001")` returns empty list
   - `matched` is null
   - Returns `TimelineInputProductResolverResult.failure(sourceAssetIds, "No READY RAW_MEDIA Product found for asset: ast_smoke_001")`
4. Back in `render()`, line 171: `resolverResult.valid()` is `false`
5. **Falls through to URI-based fallback** (line 182–203):
   - Extracts `mediaUri = "asset://ast_smoke_001"` from clip's `assetRef.storageUri()`
   - `localPath = null` (doesn't start with `"localFsStorageProvider://"` or `"/"`)
   - Throws `IllegalStateException("Cannot resolve media URI: asset://ast_smoke_001")`

#### Test Expectation (line 322–323)

```java
assertTrue(ex.getMessage().contains("Input product resolution failed"),
        "Error must indicate resolution failure: " + ex.getMessage());
```

Expected substring: `"Input product resolution failed"`
Actual message: `"Cannot resolve media URI: asset://ast_smoke_001"`

#### Root Cause

**`URI_FALLBACK_INSTEAD_OF_FAIL_CLOSED`** — The production code at `TimelineRevisionRenderService.java` lines 182–203 implements a URI-based fallback when product resolution fails. The architecture contract (per `TimelineInputProductResolver.java` line 27: *"Fail-closed: any invalid source asset ID or missing Product mapping produces a failure result. No silent fallback to direct file paths."*) requires fail-closed behavior.

The else branch (line 182) should immediately throw instead of attempting URI fallback:

```java
} else {
    throw new IllegalStateException(
        "Input product resolution failed for assets: " + mappingResult.sourceAssetIds()
        + ": " + resolverResult.failureReason());
}
```

#### Evidence

- **Test file**: `render-module/src/test/java/com/example/platform/render/app/timeline/TimelineRevisionRenderServiceTest.java`
  - Lines 302–324: test setup and assertion
  - Line 322: `assertTrue(ex.getMessage().contains("Input product resolution failed"), ...)`
- **Production file**: `render-module/src/main/java/com/example/platform/render/app/timeline/TimelineRevisionRenderService.java`
  - Lines 171–203: resolver result handling with URI fallback
  - Line 199: `throw new IllegalStateException("Cannot resolve media URI: " + mediaUri);`
- **Resolver file**: `render-module/src/main/java/com/example/platform/render/app/timeline/TimelineInputProductResolver.java`
  - Line 27: *"Fail-closed: any invalid source asset ID or missing Product mapping produces a failure result. No silent fallback to direct file paths."*
- **Fixture file**: `render-module/src/test/java/com/example/platform/render/testsupport/TimelineCoreSmokeFixture.java`
  - Line 26: `ASSET_URI = "asset://ast_smoke_001"` — non-resolvable URI scheme

---

### B5. Test 5 — `R6.1: input Product not READY fails closed`

**Error**: `Error must indicate resolution failure: Cannot resolve media URI: asset://ast_smoke_001 ==> expected: <true> but was: <false>`

#### Code Path Trace

1. Product registered with status `REGISTERED` (not `READY`) at test line 330–340.
2. `TimelineInputProductResolver.resolveWithBindings()`:
   - `productRuntime.findByAsset("ast_smoke_001")` finds the product
   - `.filter(p -> p.status() == ProductStatus.READY)` filters it out (status is `REGISTERED`)
   - `matched` is null
   - Returns failure: `"No READY RAW_MEDIA Product found for asset: ast_smoke_001"`
3. Back in `render()`, `resolverResult.valid()` is `false`
4. Falls through to URI-based fallback → same path as Test 4
5. Throws `IllegalStateException("Cannot resolve media URI: asset://ast_smoke_001")`

#### Test Expectation (line 358–359)

```java
assertTrue(ex.getMessage().contains("Input product resolution failed"),
        "Error must indicate resolution failure: " + ex.getMessage());
```

#### Root Cause

**Identical to Test 4**: `URI_FALLBACK_INSTEAD_OF_FAIL_CLOSED`. Same code path, same fallback logic.

#### Recommended Repair (Tests 4 & 5)

In `TimelineRevisionRenderService.render()`, replace the else branch (lines 182–203) with a fail-closed throw:

```java
} else {
    log.warn("Input product resolution failed, failing closed: {}", resolverResult.failureReason());
    throw new IllegalStateException(
            "Input product resolution failed for assets: " + mappingResult.sourceAssetIds()
            + ": " + resolverResult.failureReason());
}
```

This removes the entire URI-based fallback block (lines 183–203) and immediately fails when product resolution fails. The `mediaResolutionMode = "URI_BACKED_PREVIEW"` path is eliminated.

**Note**: This change may affect other tests or code paths that rely on the URI fallback for bootstrap/preview scenarios. Verify that no other caller depends on `URI_BACKED_PREVIEW` mode before applying.

---

## Stable Error Contract

### Provider Failure Contract

| State | Event | Expected Outcome |
|-------|-------|-----------------|
| EXECUTING | Provider throws | `failureService.recordDurableFailure()` called in catch block |
| | | REQUIRES_NEW transaction commits `FAILED` + `error_message` |
| | | Outer transaction rolls back (exception re-thrown) |
| | | DB shows `FAILED` with error message containing provider exception text |
| SELECTING_PROVIDER, PROVIDER_SELECTED, COMPLETING | Provider throws | Same — `markActiveJobFailed()` CAS accepts all active states |

The contract is correct in production code. Tests must simulate it properly.

### Timeline Error Contract

| Condition | Expected Error Message |
|-----------|----------------------|
| No Product for source asset | `IllegalStateException` containing `"Input product resolution failed"` |
| Product exists but not READY | `IllegalStateException` containing `"Input product resolution failed"` |
| Product READY but no StorageReference | `IllegalStateException` containing `"Input materialization failed"` |

The stable contract is: **message substring match** on `"Input product resolution failed"` for resolution failures and `"Input materialization failed"` for materialization failures. Full-string equality is NOT required.

---

## Decisions Required

1. **`PROVIDER_FAILURE_TEST_DEFECT`** — Tests 1 and 2 need `failureService` mock stubs that simulate the CAS DB update. Production code is correct.

2. **`PROVIDER_FAILURE_TEST_DEFECT`** (with production co-defect) — Test 3 expects a history record for `QUEUED → SELECTING_PROVIDER` that the production code never creates. Either adjust the test assertion or add history recording to the claim path.

3. **`TIMELINE_ERROR_PRODUCTION_CONTRACT_DEFECT`** — Tests 4 and 5 fail because `TimelineRevisionRenderService.render()` falls through to a URI-based fallback instead of failing closed when product resolution fails. The else branch at line 182 should throw immediately.
