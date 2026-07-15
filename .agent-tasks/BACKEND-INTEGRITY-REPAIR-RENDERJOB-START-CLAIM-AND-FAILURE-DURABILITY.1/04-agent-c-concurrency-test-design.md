# Agent C: Concurrency & Failure Test Design for RenderJob /start Route

**Branch**: `main` @ `3f9a837`
**Target**: `POST /api/v1/tenants/{tenantId}/projects/{projectId}/render-jobs/{jobId}/start`
**Test class**: `StartClaimAndFailureDurabilityTest` (existing) + new focused tests

---

## 1. Architecture Analysis

### 1.1 Request Flow (from code inspection)

```
HTTP POST /start
  → RenderController.startRenderJob()
    → renderJobService.getByIdAndProject(tenantId, projectId, jobId)  [404 check]
    → orchestratorPort.executeExistingRenderJob(tenantId, jobId)
      → RenderOrchestratorService.executeExistingRenderJob()  [@Transactional]
        → RenderJobExecutionService.execute()  [@Transactional]
          → executeInternal(tenantId, jobId, true)
            → assertTenantAccess(tenantId)
            → renderJobRepository.requireJobRecord(jobId)
            → if status == QUEUED:
                → claimService.claimForSelection(jobId)  [REQUIRES_NEW]
                  → renderJobRepository.claimForSelection(jobId)
                    → UPDATE render_job SET status='SELECTING_PROVIDER' WHERE id=? AND status='QUEUED'
                  → returns true (winner) / false (loser)
                → loser: return jobId immediately (no side effects)
                → winner: reload job, continue
            → resolveRenderScript()  [can throw → failureService.recordDurableFailure]
            → effectTimelineInspector.extractFromScript()
            → renderProfileResolver.resolve()
            → renderJobRepository.updateAiScript()
            → stateMachine: SELECTING_PROVIDER → PROVIDER_SELECTED
            → stateMachine: PROVIDER_SELECTED → EXECUTING
            → executeRenderWithOptionalDag()  [can throw → failureService.recordDurableFailure]
            → finishRenderPhaseInternal()  [billing, render, storage, complete]
```

### 1.2 Claim Mechanism (CAS)

**File**: `RenderJobRepository.claimForSelection()` (line 130)

```java
public int claimForSelection(String jobId) {
    return dsl.update(table("render_job"))
            .set(field("status"), "SELECTING_PROVIDER")
            .set(field("updated_at"), java.time.OffsetDateTime.now())
            .where(field("id").eq(jobId).and(field("status").eq("QUEUED")))
            .execute();
}
```

- Returns **1** → this request won the claim (CAS matched QUEUED)
- Returns **0** → another request already transitioned away from QUEUED
- Wrapped in `@Transactional(propagation = Propagation.REQUIRES_NEW)` in `RenderJobClaimService`
- The REQUIRES_NEW ensures the claim commits even if the outer execution transaction rolls back

### 1.3 Durable Failure Mechanism

**File**: `RenderJobFailureService.recordDurableFailure()` (line 30)

```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void recordDurableFailure(String jobId, String reason) {
    int updated = renderJobRepository.markActiveJobFailed(jobId, reason);
    if (updated > 0) {
        renderJobRepository.updateErrorMessage(jobId, reason);
    }
}
```

- CAS: `UPDATE render_job SET status='FAILED' WHERE id=? AND status IN ('SELECTING_PROVIDER','PROVIDER_SELECTED','EXECUTING','COMPLETING')`
- REQUIRES_NEW ensures failure state survives outer transaction rollback

### 1.4 Side Effects Inventory

| Side Effect | Table/Target | When |
|---|---|---|
| Status transition | `render_job.status` | Claim, each state machine step |
| Status history | `render_job_status_history` | Each transition |
| AI script | `render_job.ai_script` | After script resolution |
| Profile update | `render_job.profile` | If profile changed by resolver |
| Selected provider | `render_job.selected_provider` | After provider selection |
| Trace ID | `render_job.trace_id` | After provider resolution |
| Artifact URI | `render_job.artifact_uri` | After render completes |
| Error message | `render_job.error_message` | On failure |
| Billing reservation | BillingEnforcementService | Before render |
| Billing finalization | BillingEnforcementService | After render |
| Notification events | NotificationEventPublisher | On artifact creation |
| Application events | ApplicationEventPublisher | On completion |
| Artifact graph | ArtifactGraphRepository | After render |
| Storage upload | RenderArtifactStorageService | After render |

---

## 2. Test Design

### 2.1 Test Infrastructure

- **Base class**: `PostgresTestContainerSupport` (real PostgreSQL via Testcontainers)
- **Web environment**: `SpringBootTest.WebEnvironment.RANDOM_PORT`
- **HTTP client**: `java.net.http.HttpClient` (JDK 11+)
- **Concurrency primitives**: `CountDownLatch`, `ExecutorService`, `CyclicBarrier`
- **DB assertions**: `JdbcTemplate` direct queries to `render_job` and `render_job_status_history`
- **Profiles**: `test`, `preview`; security disabled; synthetic render enabled

### 2.2 Test 1: Two Concurrent /start Requests Overlap

**Goal**: Prove that two requests issued simultaneously both reach the server and contend for the same job.

**Design**:

```
1. Create tenant, project, job (status = QUEUED)
2. Inject valid ai_script via JDBC
3. Record pre-state: status, updated_at
4. Create CyclicBarrier(2) + CountDownLatch(2)
5. Submit 2 HTTP POST /start requests on separate threads
6. Both threads wait on CyclicBarrier before sending → guarantees temporal overlap
7. Collect both HTTP response status codes
8. Wait for both threads to complete (CountDownLatch)
9. Query render_job row
```

**Assertions**:
- Both requests return HTTP 200 (the endpoint returns 200 with `{"jobId":..., "status":"STARTED"}` for both — winner executes, loser returns immediately)
- Exactly ONE row in `render_job` (no duplicates)
- Final status is NOT `QUEUED` (the CAS was exercised — at least one request advanced the state)
- Status history contains exactly ONE `QUEUED → SELECTING_PROVIDER` transition

**Key**: The `CyclicBarrier` ensures both HTTP requests arrive at the server at the same instant, maximizing the race window. The CAS at the database level guarantees deterministic winner selection.

**Verification query**:
```sql
SELECT COUNT(*) FROM render_job_status_history
WHERE job_id = ? AND from_status = 'QUEUED' AND to_status = 'SELECTING_PROVIDER'
-- Expected: exactly 1
```

### 2.3 Test 2: Exactly One Claim Winner

**Goal**: Prove the CAS claim produces exactly one winner and one loser, deterministically.

**Design**:

```
1. Create tenant, project, job (status = QUEUED)
2. Inject valid ai_script
3. Snapshot render_job row: status, ai_script, selected_provider, trace_id, artifact_uri
4. Launch 2 concurrent /start requests (CyclicBarrier synchronized)
5. Collect response codes + bodies
6. Wait for completion
7. Query final state
```

**Assertions**:
- `render_job.status` is NOT `QUEUED` (CAS was exercised)
- Status history: exactly 1 `QUEUED → SELECTING_PROVIDER` transition
- Status history: exactly 1 `SELECTING_PROVIDER → PROVIDER_SELECTED` transition
- Status history: exactly 1 `PROVIDER_SELECTED → EXECUTING` transition
- Only ONE thread's claimService.claimForSelection returned true (verified via status history count)

**Rationale**: The `claimForSelection` CAS (`WHERE status = 'QUEUED'`) is atomic at the PostgreSQL level. Two concurrent `UPDATE ... WHERE status = 'QUEUED'` statements serialize — one matches (returns 1), the other doesn't match (returns 0). The `REQUIRES_NEW` propagation ensures the winner's claim commits immediately, so the loser's CAS definitely sees `status != QUEUED`.

### 2.4 Test 3: Loser Performs Zero Side Effects

**Goal**: Prove the losing request produces zero mutations beyond the claim check.

**Design**:

```
1. Create tenant, project, job (status = QUEUED)
2. Inject valid ai_script
3. Snapshot ALL side-effect tables before /start:
   - render_job row (all columns)
   - render_job_status_history count
   - (If possible) capture event listener invocations via spy/mock
4. Launch 2 concurrent /start requests
5. Wait for both to complete
6. Query post-state
```

**Assertions**:
- Status history has exactly 1 entry per transition (not 2)
- `render_job.ai_script` is updated exactly once (not overwritten by loser)
- `render_job.selected_provider` is set exactly once
- `render_job.trace_id` is set exactly once
- `render_job.artifact_uri` is set exactly once (or job fails — still exactly one attempt)

**Detailed side-effect matrix**:

| Side Effect | Winner | Loser |
|---|---|---|
| claimForSelection returns | true | false |
| Status QUEUED→SELECTING_PROVIDER | YES (CAS) | NO (short-circuits) |
| resolveRenderScript() called | YES | NO |
| updateAiScript() | YES | NO |
| effectTimelineInspector | YES | NO |
| stateMachine transitions | YES | NO |
| provider.render() | YES | NO |
| Billing reservation | YES | NO |
| Artifact storage | YES | NO |
| Status history entries | Multiple | Zero |

**Verification queries**:
```sql
-- Exactly 1 claim transition
SELECT COUNT(*) FROM render_job_status_history
WHERE job_id = ? AND to_status = 'SELECTING_PROVIDER';
-- Expected: 1

-- Status history total should reflect single execution path
SELECT COUNT(*) FROM render_job_status_history WHERE job_id = ?;
-- Expected: 3-5 (SELECTING_PROVIDER, PROVIDER_SELECTED, EXECUTING, COMPLETING, COMPLETED)
-- NOT double that
```

### 2.5 Test 4: Failure Injection — Script Resolution Boundary

**Goal**: Prove that when `resolveRenderScript()` throws, the durable failure is recorded and survives outer transaction rollback.

**Design**:

```
1. Create tenant, project, job (status = QUEUED)
2. Do NOT inject ai_script (leave it NULL or inject invalid JSON)
3. Issue /start
4. Expect HTTP 500 (IllegalStateException from execution failure)
5. Query render_job.status
```

**Assertions**:
- `render_job.status` = `FAILED` (durable failure committed via REQUIRES_NEW)
- `render_job.error_message` contains `"Script resolution failed"` (or similar)
- Status history contains `QUEUED → SELECTING_PROVIDER` transition (the claim succeeded)
- The FAILED status persists on reload (not rolled back)

**Failure path in code** (RenderJobExecutionService line 229-233):
```java
try {
    aiScript = resolveRenderScript(jobId, snapshotId, null, projectId);
} catch (Exception e) {
    failureService.recordDurableFailure(jobId, "Script resolution failed: " + e.getMessage());
    throw e;
}
```

The `recordDurableFailure` runs in REQUIRES_NEW, so it commits even though the outer transaction (which re-throws) rolls back.

### 2.6 Test 5: Failure Injection — Provider Selection Boundary

**Goal**: Prove that when no provider can be resolved, the durable failure is recorded.

**Design**:

```
1. Create tenant, project, job (status = QUEUED, profile = "nonexistent_profile")
2. Inject valid ai_script (so script resolution succeeds)
3. Issue /start
4. Expect HTTP 500
5. Query render_job
```

**Assertions**:
- `render_job.status` = `FAILED`
- `render_job.error_message` contains provider-related failure message
- Status history shows: QUEUED → SELECTING_PROVIDER → (potentially) PROVIDER_SELECTED → FAILED
- The FAILED status is durable (survives reload)

**Failure path** (RenderJobExecutionService line 335-339):
```java
} catch (Exception e) {
    log.error("Render failed for job {}", jobId, e);
    failureService.recordDurableFailure(jobId, "Render failed: " + e.getMessage());
    throw new IllegalStateException("Render failed", e);
}
```

### 2.7 Test 6: Failure Injection — Render Execution Boundary

**Goal**: Prove that when the provider.render() call fails, the durable failure is recorded.

**Design**:

```
1. Create tenant, project, job (status = QUEUED)
2. Inject ai_script that references a nonexistent media file
   (e.g., file:///nonexistent/path/video.mp4)
3. Issue /start
4. Expect HTTP 500
5. Query render_job
```

**Assertions**:
- `render_job.status` = `FAILED`
- `render_job.error_message` contains render failure message
- Status history shows the full path up to the failure point
- Durable failure survives reload

### 2.8 Test 7: Concurrent Start with Failure — Durable Failure Under Contention

**Goal**: Prove that when the winner fails, the failure is durable, and the loser still produces zero side effects.

**Design**:

```
1. Create tenant, project, job (status = QUEUED)
2. Do NOT inject ai_script (guarantees script resolution failure)
3. Launch 2 concurrent /start requests (CyclicBarrier synchronized)
4. Wait for completion
5. Query render_job
```

**Assertions**:
- `render_job.status` = `FAILED` (durable failure from winner)
- `render_job.error_message` is set
- Status history: exactly 1 claim transition, exactly 1 failure recording
- Loser produced no additional status history entries
- Error message is NOT duplicated

### 2.9 Test 8: Non-QUEUED Job Rejects Concurrent Start

**Goal**: Prove that starting an already-EXECUTING or COMPLETED job is idempotent or rejected.

**Design**:

```
1. Create tenant, project, job
2. Manually set status to EXECUTING via JDBC
3. Issue /start
4. Expect HTTP 500 (IllegalStateException: "is in state EXECUTING, cannot start")
5. Verify status unchanged
```

**Assertions**:
- HTTP 500 with error message about invalid state
- `render_job.status` unchanged (still EXECUTING)
- No new status history entries

### 2.10 Test 9: High-Contention Stress (N=10 Concurrent Requests)

**Goal**: Prove the CAS holds under higher contention.

**Design**:

```
1. Create tenant, project, job (status = QUEUED)
2. Inject valid ai_script
3. Launch N=10 concurrent /start requests (CyclicBarrier synchronized)
4. Collect all response codes
5. Wait for completion
6. Query final state
```

**Assertions**:
- Exactly 1 request won the claim (verified via status history)
- Exactly 1 set of status transitions in history
- `render_job.status` is terminal (COMPLETED or FAILED)
- All 10 requests returned HTTP 200 (the endpoint returns 200 for both winner and loser)

---

## 3. Determinism Techniques

### 3.1 Eliminating Timing Dependencies

| Technique | Usage |
|---|---|
| `CyclicBarrier(n)` | All threads block until N are ready, then release simultaneously |
| `CountDownLatch(1)` start gate | Single gate to release all threads at once |
| `CountDownLatch(n)` completion | Wait for all threads to finish |
| `Thread.sleep()` avoidance | Do NOT use sleep for synchronization — use latches/barriers |
| PostgreSQL CAS | The database serializes the UPDATE WHERE — deterministic winner |

### 3.2 Avoiding Flaky Assertions

- **Never assert which thread won** — the winner is non-deterministic. Assert only that exactly ONE won.
- **Never assert HTTP status codes depend on timing** — both requests return 200.
- **Use status history counts** as the single source of truth for claim/uniqueness.
- **Use `render_job` column values** as the single source of truth for side effects.

### 3.3 Database as Oracle

All assertions query the database directly via `JdbcTemplate`, not through the application layer. This avoids false positives from caching or entity manager state.

```java
// Claim uniqueness verification
int claimCount = jdbc.queryForObject(
    "SELECT COUNT(*) FROM render_job_status_history " +
    "WHERE job_id = ? AND from_status = 'QUEUED' AND to_status = 'SELECTING_PROVIDER'",
    Integer.class, jobId);
assertEquals(1, claimCount, "Exactly one claim transition must exist");
```

---

## 4. Failure Injection Strategy

### 4.1 Script Resolution Failure

| Method | Trigger | Expected Outcome |
|---|---|---|
| NULL ai_script | Don't inject script | resolveRenderScript fails if no snapshot |
| Invalid JSON | Inject `{{{bad json` | TimelineScriptParser throws |
| Missing media | Inject script with nonexistent file ref | May fail at render time |

### 4.2 Provider Selection Failure

| Method | Trigger | Expected Outcome |
|---|---|---|
| Invalid profile | Set profile to `"nonexistent"` | No provider matches, IllegalStateException |
| All providers disabled | Disable all providers in test properties | Same as above |

### 4.3 Render Execution Failure

| Method | Trigger | Expected Outcome |
|---|---|---|
| Missing input file | Script references `/nonexistent/file.mp4` | FFmpeg/render fails |
| Malformed script | Valid JSON but semantically broken | Provider throws |
| Billing rejection | Enable billing enforcement, zero quota | BillingEnforcementService rejects |

---

## 5. Test Implementation Outline

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "preview"})
@TestPropertySource(properties = {
    "app.security.enabled=false",
    "app.identity.api-key-auth-enabled=false",
    "render.providers.ffmpeg.enabled=true",
    "render.providers.gstreamer.enabled=false",
    "render.execution.mode=local",
    "render.synthetic.enabled=true"
})
class RenderJobStartConcurrencyAndFailureTest extends PostgresTestContainerSupport {

    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;

    HttpClient client;
    String baseUrl;
    ObjectMapper mapper = new ObjectMapper();

    // ---- Test 1: Concurrent overlap ----
    @Test
    void concurrentStart_requestsOverlap() {
        // CyclicBarrier(2) ensures simultaneous arrival
        // Assert: both return 200, exactly 1 claim in history
    }

    // ---- Test 2: Single claim winner ----
    @Test
    void concurrentStart_exactlyOneClaimWinner() {
        // CyclicBarrier(2) synchronized
        // Assert: status_history QUEUED→SELECTING_PROVIDER count == 1
    }

    // ---- Test 3: Loser zero side effects ----
    @Test
    void concurrentStart_loserPerformsZeroSideEffects() {
        // Snapshot history count before
        // Launch 2 concurrent requests
        // Assert: history count delta == single execution path (not doubled)
    }

    // ---- Test 4: Script resolution failure ----
    @Test
    void start_scriptResolutionFailure_durableFailure() {
        // NULL ai_script, no valid snapshot
        // Assert: status=FAILED, error_message contains "Script resolution"
    }

    // ---- Test 5: Provider selection failure ----
    @Test
    void start_providerSelectionFailure_durableFailure() {
        // Invalid profile
        // Assert: status=FAILED, error_message contains provider failure
    }

    // ---- Test 6: Render execution failure ----
    @Test
    void start_renderExecutionFailure_durableFailure() {
        // Script referencing nonexistent file
        // Assert: status=FAILED, error_message contains render failure
    }

    // ---- Test 7: Concurrent start with failure ----
    @Test
    void concurrentStart_winnerFails_loserZeroSideEffects() {
        // No ai_script → script resolution failure
        // 2 concurrent requests
        // Assert: status=FAILED, exactly 1 failure record, loser had no side effects
    }

    // ---- Test 8: Non-QUEUED rejection ----
    @Test
    void start_alreadyExecuting_rejected() {
        // Set status=EXECUTING via JDBC
        // Assert: HTTP 500, status unchanged
    }

    // ---- Test 9: High contention ----
    @Test
    void concurrentStart_highContention_singleWinner() {
        // CyclicBarrier(10), 10 concurrent requests
        // Assert: exactly 1 claim, exactly 1 execution path
    }
}
```

---

## 6. Claim Mechanism Deep-Dive (for test authors)

### 6.1 Why REQUIRES_NEW Matters

Without REQUIRES_NEW, the claim would be part of the outer transaction. If the outer transaction rolls back (e.g., render failure), the claim would also roll back, allowing another request to re-claim the same job. REQUIRES_NEW ensures:

1. Claim commits immediately in its own transaction
2. Outer failure does NOT undo the claim
3. Job is permanently marked as "claimed" (SELECTING_PROVIDER) even if execution fails
4. The durable failure service then transitions SELECTING_PROVIDER → FAILED

### 6.2 CAS Atomicity

The `WHERE status = 'QUEUED'` clause makes the UPDATE atomic:
- PostgreSQL serializes concurrent UPDATEs to the same row
- First UPDATE matches (status WAS QUEUED) → returns 1
- Second UPDATE doesn't match (status is now SELECTING_PROVIDER) → returns 0
- No application-level lock needed — the database guarantees exactly one winner

### 6.3 Loser Short-Circuit Path

```
RenderJobExecutionService.executeInternal():
  if ("QUEUED".equals(status)) {
      if (requiresClaim) {
          boolean claimed = claimService.claimForSelection(jobId);
          if (!claimed) {
              log.info("Render job {} already claimed by another request", jobId);
              return jobId;  // ← SHORT CIRCUIT: no side effects after this point
          }
          // Winner continues from here...
      }
  }
```

The loser hits `return jobId` immediately — no script resolution, no provider selection, no render, no storage, no events. The only "work" the loser does is:
1. Load the job record (`requireJobRecord`)
2. Execute the CAS claim (returns 0)
3. Return

---

## 7. Expected Evidence Format

Each test should write evidence to a file (e.g., `/tmp/concurrency-failure-test-evidence.txt`):

```
TEST: concurrentStart_exactlyOneClaimWinner
JOB_ID: rj-abc123
PRE_STATUS: QUEUED
THREAD_0_HTTP: 200
THREAD_1_HTTP: 200
FINAL_STATUS: COMPLETED (or FAILED)
CLAIM_TRANSITION_COUNT: 1
TOTAL_HISTORY_COUNT: 5
CANONICAL_PROVIDER: ffmpeg
DURABLE_FAILURE: (YES|NO)
VERDICT: PASS
```

---

## 8. Risks and Mitigations

| Risk | Mitigation |
|---|---|
| Thread.sleep flakiness | Use CyclicBarrier/CountDownLatch exclusively |
| Both requests return before DB commit | Add short poll loop (max 10s) for status to leave QUEUED |
| Render succeeds unexpectedly (test infra) | Use invalid media path to guarantee failure, OR assert on either COMPLETED or FAILED |
| PostgreSQL connection pool exhaustion | Testcontainers defaults are fine for 2-10 threads |
| Spring transaction propagation timing | REQUIRES_NEW is well-tested in PostgreSQL; CAS is deterministic |

---

## 9. Relationship to Existing Tests

The existing `StartClaimAndFailureDurabilityTest` covers:
- ✅ Normal start with durable failure (basic)
- ✅ Concurrent start — single winner (HTTP-level, basic)
- ✅ Sequential duplicate start — idempotent

This design adds:
- **Deeper concurrency assertions** (status history counts, side-effect matrices)
- **Failure injection at all three boundaries** (script, provider, render)
- **Loser zero-side-effect proof** (pre/post snapshot comparison)
- **High-contention stress** (N=10)
- **Concurrent failure durability** (winner fails under contention)
