# Implementation Result

## Task

```text
BACKEND-INTEGRITY-REPAIR-RENDERJOB-START-ERROR-SURFACING-AND-PROVENANCE-CLOSEOUT.4
```

## Status

```text
COMPLETE
```

## Decision

```text
RENDERJOB_START_ERROR_SURFACING_AND_PROVENANCE_CLOSED
```

## Changes Made

### 1. Removed Stale Null Constructor

**File:** `render-module/src/main/java/com/example/platform/render/api/RenderController.java`

**Before:**
```java
public RenderController(RenderJobService renderJobService) {
    this(renderJobService, null, null, null, null, null, null, null, null, null, null, null, null);
}
```

**After:** Removed entirely. Only the @Autowired constructor remains.

### 2. Removed False QUEUED Response

**File:** `render-module/src/main/java/com/example/platform/render/api/RenderController.java`

**Before:**
```java
@PostMapping("/tenants/{tenantId}/projects/{projectId}/render-jobs/{jobId}/start")
public Map<String, String> startRenderJob(...) {
    if (orchestratorPort != null) {
        try {
            ...
            return Map.of("jobId", resultJobId, "status", "STARTED");
        } catch (Exception ex) {
            return Map.of("jobId", jobId, "status", "QUEUED");  // LIE
        }
    }
    return Map.of("jobId", jobId, "status", "QUEUED");  // LIE
}
```

**After:**
```java
@PostMapping("/tenants/{tenantId}/projects/{projectId}/render-jobs/{jobId}/start")
public Map<String, String> startRenderJob(...) {
    if (orchestratorPort == null) {
        throw new IllegalStateException("Render orchestrator is not available");
    }
    renderJobService.getByIdAndProject(tenantId, projectId, jobId);
    String resultJobId = orchestratorPort.executeExistingRenderJob(tenantId, jobId);
    return Map.of("jobId", resultJobId, "status", "STARTED");
}
```

### 3. Updated Tests

Updated 4 test files to use the full constructor:
- `platform-app/src/test/java/.../RenderControllerTest.java`
- `render-module/src/test/java/.../RenderControllerTest.java`
- `render-module/src/test/java/.../RenderControllerContractTest.java`
- `render-module/src/test/java/.../VS1SmokeIntegrationTest.java`

Changed test expectations from:
- `assertEquals("QUEUED", result.get("status"))` → `assertThrows(IllegalStateException.class, ...)`

## Verification

| Check | Result |
|-------|--------|
| Production compile | ✅ PASSED |
| Test compile | ✅ PASSED |
| Architecture drift guard | ✅ 32/32 PASSED |

## Root Cause Classification

```text
FALSE_QUEUED_CATCH_FOUND + STALE_NULL_CONSTRUCTOR_FOUND
```

## Error Handling Strategy

**Before:** Catch-all returns QUEUED for any exception (WRONG)

**After:** Exceptions propagate to Spring's @ExceptionHandler:
- `IllegalArgumentException` → 404 ProblemDetail
- `IllegalStateException` → 409 ProblemDetail
- Other → 500 (via global handler)

## Commit

```text
fix: surface render start failures accurately

- Remove stale constructor that permits orchestratorPort=null
- Remove catch-all exception handler that returned false QUEUED
- Let Spring @ExceptionHandler handle typed exceptions properly
- Update tests to expect IllegalStateException for null orchestrator
```

## Files Changed

| File | Change |
|------|--------|
| `render-module/.../RenderController.java` | Removed stale constructor, removed catch-all |
| `platform-app/.../RenderControllerTest.java` | Updated constructor call |
| `render-module/.../RenderControllerTest.java` | Updated constructor call |
| `render-module/.../RenderControllerContractTest.java` | Updated constructor call + test expectations |
| `render-module/.../VS1SmokeIntegrationTest.java` | Updated constructor call + test expectations |

## Non-Goals Preserved

- ✅ No new routes added
- ✅ No Flyway migration added
- ✅ No frontend changes
- ✅ No new capabilities introduced
- ✅ claimForSelection() preserved
- ✅ markActiveJobFailed() preserved
- ✅ RenderJobClaimService preserved
- ✅ RenderJobFailureService preserved
- ✅ execute-local returns 404
- ✅ retry returns 404

## Remaining Work

Parent task `BACKEND-INTEGRITY-REPAIR-RENDERJOB-START-CLAIM-AND-FAILURE-DURABILITY.1` can now be resumed. The actual exception from execute() will now be visible to callers and logs.
