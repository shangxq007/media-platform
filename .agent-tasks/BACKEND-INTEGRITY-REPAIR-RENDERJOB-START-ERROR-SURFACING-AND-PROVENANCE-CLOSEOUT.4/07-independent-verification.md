# 07 — Independent Verification

**Commit:** 3f9a837  
**Date:** 2026-07-15  
**Verified by:** Agent E (independent subagent)

---

## 1. No stale constructor permits orchestratorPort=null ✅ PASS

**Before:** A single-arg convenience constructor existed:
```java
public RenderController(RenderJobService renderJobService) {
    this(renderJobService, null, null, null, null, null, null, null, null, null, null, null, null);
}
```

**After:** Removed. Only the 13-arg `@Autowired` constructor remains (lines 62-89). The `orchestratorPort` parameter is annotated `@Autowired(required = false)`, so Spring may still inject `null` when no bean exists, but there is no convenience constructor that silently passes `null` for all dependencies. All test code explicitly instantiates with the 13-arg constructor, making the `null` orchestratorPort visible at every call site.

**Static check:** `grep` for `public RenderController(RenderJobService \w+\)` — 0 matches in production code. ✅

---

## 2. No catch-all returns QUEUED for exceptions ✅ PASS

**Before:** `startRenderJob` (lines 209-218) had:
```java
if (orchestratorPort != null) {
    try {
        renderJobService.getByIdAndProject(...);
        String resultJobId = orchestratorPort.executeExistingRenderJob(...);
        return Map.of("jobId", resultJobId, "status", "STARTED");
    } catch (Exception ex) {
        return Map.of("jobId", jobId, "status", "QUEUED");  // SWALLOWED
    }
}
return Map.of("jobId", jobId, "status", "QUEUED");  // SILENT FAILURE
```

**After:** Replaced with fail-closed logic:
```java
if (orchestratorPort == null) {
    throw new IllegalStateException("Render orchestrator is not available");
}
renderJobService.getByIdAndProject(tenantId, projectId, jobId);
String resultJobId = orchestratorPort.executeExistingRenderJob(tenantId, jobId);
return Map.of("jobId", resultJobId, "status", "STARTED");
```

No `try/catch` wrapping. Exceptions from `orchestratorPort.executeExistingRenderJob` or `getByIdAndProject` propagate to Spring's `@ExceptionHandler` infrastructure. The `IllegalStateException` handler (line 450) returns HTTP 409 CONFLICT. The `IllegalArgumentException` handler (line 443) returns HTTP 404 NOT_FOUND.

**Remaining QUEUED reference:** Line 180 in `submitIncrementalRenderJob` — this is the **happy-path** return after `orchestratorPort.submitRenderJob(request)` succeeds. Correct behavior.

**Remaining catch blocks:** Three `catch` blocks exist in `RenderController.java`, none in the `startRenderJob` path:
- Line 126: `_diagnosticInit` bytecode hash computation — non-REST diagnostic, logs warning
- Line 511: `uploadPreviewMedia` Product creation — logs warning, continues without Product (non-critical side-effect)
- Line 517: `uploadPreviewMedia` IOException — re-throws as `IllegalStateException` (fail-closed)

None of these are in the render-job-start flow or return QUEUED.

---

## 3. Build compiles ✅ PASS

```
:render-module:compileJava  →  BUILD SUCCESSFUL in 5s
```

---

## 4. Architecture guard passes ✅ PASS

```
scripts/check-architecture-drift.sh
Checks: 32
Failed: 0
✅ All architecture drift checks passed
```

All 32 checks pass: required classes, runtime profile switching, storage exposure, report-only evaluator, upload rejection, persistence, deferred status, HOLD module governance, admin routes, SPA fallback.

---

## 5. Tests updated correctly ✅ PASS

### render-module unit tests
```
:render-module:test  →  BUILD SUCCESSFUL
```
Covers:
- `RenderControllerTest` — basic CRUD delegation (3 tests)
- `RenderControllerContractTest` — full API contract including:
  - `startDelegatesToOrchestrator` — happy path returns STARTED
  - `startThrowsWhenNoOrchestrator` — `assertThrows(IllegalStateException.class, ...)`
  - `artifactsReturnEmptyWhenNoOrchestrator` — graceful degradation
- `VS1SmokeIntegrationTest` — integration-level:
  - `missingOrchestratorFallback` renamed → `assertThrows(IllegalStateException.class, ...)` on `submitIncrementalRenderJob`

### platform-app tests
```
:platform-app:test  →  BUILD SUCCESSFUL
```
Covers `RenderControllerTest` (4 tests) — all use 13-arg constructor.

### Key test changes verified from diff:

| File | Before | After |
|------|--------|-------|
| `RenderControllerContractTest` | `startReturnsQueuedWhenNoOrchestrator` — asserts `QUEUED` | `startThrowsWhenNoOrchestrator` — `assertThrows(IllegalStateException)` |
| `VS1SmokeIntegrationTest` | `missingOrchestratorFallback` — asserts `QUEUED` | Same method, now `assertThrows(IllegalStateException)` on `submitIncrementalRenderJob` |
| All 3 test files | `new RenderController(service)` (1-arg) | `new RenderController(service, null, List.of(), null, ...)` (13-arg) |

---

## Summary

| Check | Result |
|-------|--------|
| No stale 1-arg constructor | ✅ PASS |
| No catch-all returning QUEUED | ✅ PASS |
| Build compiles | ✅ PASS |
| Architecture guard (32 checks) | ✅ PASS |
| Tests updated & passing | ✅ PASS |

**Commit 3f9a837 is verified correct.** The changes are minimal, targeted, and fail-closed: null orchestrator throws `IllegalStateException` (409), exceptions propagate to `@ExceptionHandler`, and all existing tests have been updated to match the new contract.
