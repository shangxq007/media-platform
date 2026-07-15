# Hidden Exception Investigation: RenderJob /start Route

## 1. The Hiding Boundary

**File:** `render-module/src/main/java/com/example/platform/render/api/RenderController.java`
**Lines:** 212-226

```java
@PostMapping("/tenants/{tenantId}/projects/{projectId}/render-jobs/{jobId}/start")
public Map<String, String> startRenderJob(@PathVariable String tenantId,
        @PathVariable String projectId,
        @PathVariable String jobId) {
    if (orchestratorPort != null) {
        try {
            renderJobService.getByIdAndProject(tenantId, projectId, jobId);
            String resultJobId = orchestratorPort.executeExistingRenderJob(tenantId, jobId);
            return Map.of("jobId", resultJobId, "status", "STARTED");
        } catch (Exception ex) {                          // ← SWALLOWS ALL EXCEPTIONS
            return Map.of("jobId", jobId, "status", "QUEUED");  // ← LIES: returns QUEUED
        }
    }
    return Map.of("jobId", jobId, "status", "QUEUED");
}
```

**Problem:** The `catch (Exception ex)` block at line 221 catches every possible exception
from the execution pipeline and returns `status=QUEUED` with no logging, no error detail,
and no status code differentiation. The client has no way to distinguish between:
- A genuine job that is queued and waiting
- A job whose execution failed catastrophically
- A job that was already claimed by another concurrent request

The exception is silently discarded — no `log.error()`, no `log.warn()`, nothing.

---

## 2. Execution Call Chain

```
RenderController.startRenderJob()                           [API layer — catch boundary]
  │
  ├─ renderJobService.getByIdAndProject(tenantId, projectId, jobId)
  │    └─ IllegalArgumentException if job not found (pre-check)
  │
  └─ orchestratorPort.executeExistingRenderJob(tenantId, jobId)
       │
       └─ RenderOrchestratorService.executeExistingRenderJob()   [@Transactional]
            │
            └─ executionService.execute(tenantId, jobId)         [@Transactional]
                 │
                 ├─ 1. assertTenantAccess(tenantId)
                 │     └─ IllegalArgumentException("Resource not found for tenant")
                 │        if TenantContext.get() mismatches tenantId
                 │
                 ├─ 2. renderJobRepository.requireJobRecord(jobId)
                 │     └─ IllegalArgumentException("Render job not found: " + jobId)
                 │        if jobId doesn't exist in render_job table
                 │
                 ├─ 3. Tenant mismatch check
                 │     └─ IllegalArgumentException("Render job not found for tenant")
                 │
                 ├─ 4. Status check: if COMPLETED → return jobId (no exception)
                 │
                 ├─ 5. claimService.claimForSelection(jobId)     [REQUIRES_NEW txn]
                 │     ├─ CAS: render_job SET status='SELECTING_PROVIDER'
                 │     │   WHERE id=jobId AND status='QUEUED'
                 │     ├─ returns true  → claim won, continue
                 │     └─ returns false → already claimed, return jobId (no exception)
                 │
                 ├─ 6. IllegalState check: if status not in
                 │     {SELECTING_PROVIDER, EXECUTING}
                 │     └─ IllegalStateException("Render job " + jobId +
                 │        " is in state " + status + ", cannot start")
                 │
                 ├─ 7. resolveRenderScript(...)
                 │     ├─ [on failure] failureService.recordDurableFailure(jobId, ...)
                 │     │   [REQUIRES_NEW txn — commits even if outer rolls back]
                 │     └─ re-throws original exception
                 │        (various: IllegalStateException, IllegalArgumentException,
                 │         NullPointerException, etc.)
                 │
                 ├─ 8. stateMachine.transition(... SELECTING_PROVIDER → PROVIDER_SELECTED)
                 │     └─ IllegalStateException if transition invalid
                 │
                 ├─ 9. stateMachine.transition(... PROVIDER_SELECTED → EXECUTING)
                 │     └─ IllegalStateException if transition invalid
                 │
                 └─ 10. finishRenderPhaseInternal(tenantId, jobId)
                       ├─ Billing reservation failure
                       │   ├─ failJob(... BILLING_FAILED ...)
                       │   └─ IllegalStateException("Billing reservation failed: ...")
                       │
                       ├─ executeRenderWithOptionalDag(...)
                       │   ├─ Provider resolution failure
                       │   │   └─ IllegalStateException("No render provider available...")
                       │   ├─ Pipeline DAG failure
                       │   │   └─ IllegalStateException("Pipeline DAG failed: ...")
                       │   ├─ Provider.render() failure
                       │   │   └─ [on failure] failureService.recordDurableFailure(...)
                       │   │       └─ re-throws IllegalStateException("Render failed", e)
                       │   └─ assertJobNotInTerminalState()
                       │       └─ IllegalStateException("Job has been cancelled: ...")
                       │
                       ├─ Storage failure (uploadJobOutput)
                       │   ├─ failJob(... STORAGE_FAILED ...)
                       │   └─ IllegalStateException("Storage failed", e)
                       │
                       └─ resolveRenderScript null case
                           └─ IllegalStateException("No timeline snapshot or prompt...")
```

---

## 3. Exception Classes That Can Be Thrown

### 3.1 `IllegalArgumentException`
| Source | Message | When |
|--------|---------|------|
| `assertTenantAccess()` | "Resource not found for tenant" | TenantContext mismatch |
| `requireJobRecord()` | "Render job not found: {jobId}" | Job ID doesn't exist |
| Tenant mismatch check | "Render job not found for tenant" | Job belongs to different tenant |

**Spring MVC handler:** `RenderController.handleNotFound()` → HTTP 404 ProblemDetail
BUT: the `catch (Exception ex)` in `startRenderJob` intercepts BEFORE the
`@ExceptionHandler` can fire, because the exception is caught inside the method body.

### 3.2 `IllegalStateException`
| Source | Message | When |
|--------|---------|------|
| Status guard | "Render job {id} is in state {status}, cannot start" | Job in unexpected state |
| State machine | (transition violation) | Invalid status transition |
| Script resolution | "No timeline snapshot or prompt available..." | No snapshot, no prompt |
| Billing | "Billing reservation failed: ..." | Quota exceeded |
| Provider | "No render provider available for profile: ..." | No matching provider |
| Pipeline DAG | "Pipeline DAG failed: ..." | DAG execution error |
| Render | "Render failed" (wraps cause) | Provider.render() throws |
| Storage | "Storage failed" (wraps cause) | Artifact upload fails |
| Cancel check | "Job has been cancelled: {jobId}" | Job was cancelled mid-flight |

**Spring MVC handler:** `RenderController.handleConflict()` → HTTP 409 ProblemDetail
BUT: same interception issue — caught by `catch (Exception ex)` first.

### 3.3 Wrapped/Internal Exceptions
The `executeRenderWithOptionalDag()` method can propagate any exception thrown by:
- `provider.render(jobId, aiScript, profile)` — provider-specific exceptions
- `pipelineDagExecutorService.execute(...)` — DAG execution exceptions
- `timelineSpecResolver.resolve(aiScript)` — parsing exceptions
- `effectTimelineInspector.extractFromScript(aiScript)` — parsing exceptions

These are wrapped in `IllegalStateException("Render failed", e)` at line 309.

---

## 4. Failure Boundary and State Impact

### 4.1 The Critical Problem: Silent State Corruption

When an exception is caught at line 221 of `RenderController`:

**Before the exception:**
- `RenderJobClaimService.claimForSelection()` has ALREADY committed the claim
  in a REQUIRES_NEW transaction (QUEUED → SELECTING_PROVIDER)
- `RenderJobFailureService.recordDurableFailure()` may have ALREADY committed
  a failure record in a REQUIRES_NEW transaction (→ FAILED)

**After the catch:**
- The outer `@Transactional` on `RenderOrchestratorService.executeExistingRenderJob()`
  ROLLS BACK (Spring's default for unhandled exceptions in @Transactional)
- But the claim (REQUIRES_NEW) survives the rollback
- But the failure record (REQUIRES_NEW) survives the rollback

**Client sees:** `{ "jobId": "...", "status": "QUEUED" }`

**Database state:** Job is in `SELECTING_PROVIDER` (claimed) or `FAILED` (if failure
was recorded) — but the client was told `QUEUED`. This is a **lie**.

### 4.2 Race Condition Scenario

Two concurrent /start requests for the same job:

```
Request A                          Request B
────────                           ────────
  read: QUEUED
                                   read: QUEUED
  claimForSelection → true (CAS)
                                   claimForSelection → false (CAS)
  continue execution...
  throws IllegalStateException
  catch → returns QUEUED ←
                                   catch → returns QUEUED ←
```

Both clients see `status=QUEUED`. Neither knows the job was already claimed
by A, and A's failure is invisible.

### 4.3 Failure Recording Timing

In `RenderJobExecutionService.execute()`:

```java
// Line 200-205: Script resolution failure
try {
    aiScript = resolveRenderScript(...);
} catch (Exception e) {
    failureService.recordDurableFailure(jobId, "Script resolution failed: " + e.getMessage());
    throw e;  // ← re-thrown, caught by controller → client sees QUEUED
}
```

The durable failure IS recorded in the database (REQUIRES_NEW), but the client
receives `status=QUEUED` — contradicting the database state.

Similarly in `finishRenderPhaseInternal()`:

```java
// Line 306-310: Render failure
} catch (Exception e) {
    log.error("Render failed for job {}", jobId, e);
    failureService.recordDurableFailure(jobId, "Render failed: " + e.getMessage());
    throw new IllegalStateException("Render failed", e);  // ← caught → QUEUED
}
```

---

## 5. Most Likely Exception in Practice

Given the WIP branch `wip/renderjob-start-claim-failure-durability` adds
claim/failure wiring, the most likely exception scenario when
`orchestratorPort.executeExistingRenderJob()` is called:

### Primary: `IllegalStateException` from script resolution or provider resolution

When a RenderJob is created but has:
- No valid timeline snapshot (`timeline_snapshot_id` is null or references missing snapshot)
- An ai_script that cannot be parsed/resolved
- No matching render provider for the configured profile

The execution path hits one of:
1. `IllegalStateException("No timeline snapshot or prompt available for render")` at line 553
2. `IllegalStateException("No render provider available for profile: ...")` at line 455
3. `IllegalStateException("Render failed", cause)` at line 309

### Secondary: `IllegalArgumentException` from missing job or tenant mismatch

If the job was deleted between the pre-check (`getByIdAndProject`) and the
execution call, or if TenantContext changes between calls.

---

## 6. Summary of Findings

| Aspect | Detail |
|--------|--------|
| **Hiding mechanism** | `catch (Exception ex)` at RenderController line 221 |
| **Lie returned** | `Map.of("jobId", jobId, "status", "QUEUED")` |
| **No logging** | Exception is caught and discarded with zero logging |
| **Most likely exception** | `IllegalStateException` — from script resolution, provider resolution, or render failure |
| **Cause chain** | Provider/render failure → `IllegalStateException("Render failed", originalException)` |
| **State corruption** | Claim (REQUIRES_NEW) survives rollback; client told QUEUED but DB shows SELECTING_PROVIDER or FAILED |
| **Failure boundary** | Controller method body catches before `@ExceptionHandler` can produce proper HTTP error responses |
| **Durable failure recording** | `RenderJobFailureService.recordDurableFailure()` commits in REQUIRES_NEW but client never sees the failure |
| **Concurrency risk** | Multiple concurrent /start requests all see QUEUED; no idempotency signal |

---

## 7. Recommended Fix Direction

1. **Log the exception** — at minimum, `log.error("Render job start failed", ex)`
2. **Return differentiated status** — distinguish QUEUED (genuine) from FAILED (execution error)
3. **Surface error detail** — include error message in response or return proper HTTP status
4. **Check pre-existing claim** — before calling execute, check if job is already in
   SELECTING_PROVIDER/FAILED state
5. **Remove blanket catch** — let Spring's `@ExceptionHandler` handle typed exceptions
   with proper HTTP status codes (404, 409, 500)
