# Lead Synthesis - Final

## Based on Agent A Investigation + Direct Code Analysis

### Confirmed Root Cause

**Classification: FALSE_QUEUED_CATCH_FOUND + STALE_NULL_CONSTRUCTOR_FOUND**

### Issue 1: Stale Null Constructor (RenderController.java:62-63)

```java
public RenderController(RenderJobService renderJobService) {
    this(renderJobService, null, null, null, null, null, null, null, null, null, null, null, null);
}
```

**Problem:** Permits orchestratorPort=null, which is invalid for production.

**Fix:** REMOVE_STALE_NULL_CONSTRUCTOR

### Issue 2: False QUEUED Response (RenderController.java:212-226)

```java
@PostMapping("/tenants/{tenantId}/projects/{projectId}/render-jobs/{jobId}/start")
public Map<String, String> startRenderJob(...) {
    if (orchestratorPort != null) {
        try {
            ...
            return Map.of("jobId", resultJobId, "status", "STARTED");
        } catch (Exception ex) {                          // ← SWALLOWS ALL
            return Map.of("jobId", jobId, "status", "QUEUED");  // ← LIE
        }
    }
    return Map.of("jobId", jobId, "status", "QUEUED");    // ← LIE
}
```

**Problem:** 
1. Catches ALL exceptions silently (no logging)
2. Returns QUEUED regardless of actual DB state
3. Client cannot distinguish genuine QUEUED from FAILED

**Fix:** REMOVE_CONTROLLER_CATCH_AND_USE_GLOBAL_HANDLER

### Issue 3: State Corruption

When exception occurs:
1. Claim (REQUIRES_NEW) has already committed: QUEUED → SELECTING_PROVIDER
2. Failure record (REQUIRES_NEW) may have committed: → FAILED
3. Outer transaction rolls back
4. Client sees: QUEUED
5. DB state: SELECTING_PROVIDER or FAILED

**This is a lie.**

### Exception Classification

**Most likely exception:** IllegalStateException from:
- Script resolution failure
- Provider resolution failure  
- Render execution failure
- State machine transition violation

**Exception class:** IllegalStateException or IllegalArgumentException

**Root cause:** The execution pipeline throws domain exceptions that should be handled by Spring's @ExceptionHandler, but the catch-all intercepts them first.

### Required Production Code Changes

1. **Remove stale constructor** (line 62-63)
2. **Remove catch-all** (line 221)
3. **Add logging** for any remaining exception handling
4. **Let Spring's @ExceptionHandler handle typed exceptions**

### Error Handling Strategy

**Before:**
```java
catch (Exception ex) {
    return Map.of("jobId", jobId, "status", "QUEUED");  // WRONG
}
```

**After:**
```java
// No catch - let exceptions propagate to @ExceptionHandler
// IllegalArgumentException → 404
// IllegalStateException → 409
// Other → 500 (via global handler)
```

### Required Tests

1. Controller constructor invariant (no null orchestratorPort)
2. Real HTTP provenance (Spring-managed Controller)
3. Unknown exception → proper HTTP status (not QUEUED)
4. Database state consistency (response matches DB)

### Non-Goals

- Do NOT repair concurrent start behavior
- Do NOT repair durable failure verification
- Do NOT add new routes
- Do NOT add Flyway migration

### Implementation Plan

**Agent D (sole writer):**
1. Remove stale constructor
2. Remove catch-all
3. Add Objects.requireNonNull for orchestratorPort
4. Update tests

**Agent E (verifier):**
1. Fresh worktree
2. Verify constructor audit
3. Verify real HTTP test
4. Verify exception handling

## Status

Ready for Agent D implementation.
