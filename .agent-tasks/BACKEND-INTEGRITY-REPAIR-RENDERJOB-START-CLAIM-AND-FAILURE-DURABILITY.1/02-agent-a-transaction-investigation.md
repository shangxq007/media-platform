# Agent A: Transaction Topology Investigation — RenderJob /start → execute() Path

## Files Read

| File | Branch | Path |
|------|--------|------|
| RenderJobExecutionService.java | main (fb809d0) | `render-module/src/main/java/com/example/platform/render/app/RenderJobExecutionService.java` |
| RenderJobExecutionService.java | WIP (c40de00) | same path |
| RenderJobClaimService.java | WIP (7143a80) | `render-module/src/main/java/com/example/platform/render/app/RenderJobClaimService.java` |
| RenderJobFailureService.java | WIP (7143a80) | `render-module/src/main/java/com/example/platform/render/app/RenderJobFailureService.java` |
| RenderJobRepository.java | both (identical) | `render-module/src/main/java/com/example/platform/render/infrastructure/RenderJobRepository.java` |
| FFmpegRenderProvider.java | WIP (7143a80) | `render-module/src/main/java/com/example/platform/render/infrastructure/ffmpeg/FFmpegRenderProvider.java` |
| RenderProvider.java | WIP (7143a80) | `render-module/src/main/java/com/example/platform/render/infrastructure/RenderProvider.java` |
| ProcessToolRunner.java | both | `extension-module/src/main/java/com/example/platform/extension/app/ProcessToolRunner.java` |
| RenderController.java | both | `render-module/src/main/java/com/example/platform/render/api/RenderController.java` |
| RenderOrchestratorService.java | both | `render-module/src/main/java/com/example/platform/render/app/RenderOrchestratorService.java` |

---

## Branch State Analysis

### Critical Finding: WIP Branch Has Broken Wiring

The WIP branch (7143a80) has a **compilation error** in `RenderJobExecutionService.java`:

- **Blob c40de00** (WIP): Method body references `claimService.claimForSelection(jobId)` (line 181) and `failureService.recordDurableFailure(...)` (lines 197, 302), but **no field declarations or constructor parameters exist** for these services. The class will not compile.
- **Blob fb809d0** (main): Has complete wiring — field declarations, constructor parameters, field assignments, and method body references. 9 total references to claimService/failureService.

### Branch Divergence

```
2fd01ea  docs: add concurrency and failure path findings
  ├── a6f46c4  wip: preserve blocked render job claim repair     ← WIP: adds ClaimService, FailureService, Repository methods + test
  │   └── 7143a80  wip: preserve parent-task claim/failure wiring  ← WIP: adds method body calls BUT NOT field/constructor wiring
  ├── c237b23  fix: repair render job execution bean graph        ← MAIN: adds complete wiring (fields + constructor + body)
  │   ├── 355e706  test: prove render controller instance provenance
  │   └── 3f9a837  fix: surface render start failures accurately  ← MAIN HEAD
```

**Commit c237b23 on main** properly added:
1. Field declarations for `claimService` and `failureService`
2. Constructor parameters
3. Constructor field assignments

**Commit 7143a80 on WIP** added the method body calls but forgot the field/constructor wiring — the code references undeclared variables.

---

## 1. Is `execute()` Transactional?

**YES.** `RenderJobExecutionService.execute()` is annotated with `@Transactional` (no attributes → default `REQUIRED`, isolation default, default timeout).

```java
@Transactional
public String execute(String tenantId, String jobId) { ... }
```

Similarly, `finishRenderPhase()` is also `@Transactional`:
```java
@Transactional
public String finishRenderPhase(String tenantId, String jobId) { ... }
```

Both methods use default propagation (`REQUIRED`), so `finishRenderPhase()` joins the existing transaction if called within `execute()`, or creates a new one if called standalone.

---

## 2. Does the Outer Transaction Hold the `render_job` Row?

**YES, but timing matters.**

### Call sequence inside `execute()`:

1. `assertTenantAccess(tenantId)` — no DB access
2. `renderJobRepository.requireJobRecord(jobId)` — **SELECT** (no row lock in PostgreSQL READ COMMITTED)
3. Read fields from `Record` object (in-memory)
4. Status checks
5. **If QUEUED**: `claimService.claimForSelection(jobId)` — REQUIRES_NEW, see Q3
6. `renderJobRepository.requireJobRecord(jobId)` — **SELECT** again (reload after claim)
7. `resolveRenderScript(...)` — reads `findAiScriptById`, `timelineSnapshotService.findPayload`
8. `renderJobRepository.updateAiScript(jobId, aiScript)` — **UPDATE** → **takes row-level lock**
9. `stateMachine.transition(...)` + `updateStatus(...)` — **UPDATE** → **holds row-level lock**
10. `updateStatus(...)` (PROVIDER_SELECTED → EXECUTING) — **UPDATE** → **holds row-level lock**
11. `finishRenderPhaseInternal(...)` → `executeRenderWithOptionalDag(...)` → `provider.render(...)` — FFmpeg execution (see Q4)
12. Further status updates, artifact storage, completion

**The outer transaction takes its first row-level lock at step 8** (`updateAiScript`). From that point until commit, the `render_job` row is locked in the outer transaction.

### Lock duration

The lock is held from step 8 through:
- Script resolution (step 7 is done, but this is where it gets complex — wait, step 7 is before step 8)
- State transitions (steps 9-10)
- FFmpeg execution (step 11) — **potentially minutes**
- Billing finalization, artifact storage, more state transitions
- Final commit

**FINDING: The outer transaction holds the `render_job` row lock for the entire duration of FFmpeg execution plus all post-render operations.**

---

## 3. Does REQUIRES_NEW Wait for Lock?

### RenderJobClaimService (REQUIRES_NEW)

```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public boolean claimForSelection(String jobId) {
    int claimed = renderJobRepository.claimForSelection(jobId);
    ...
}
```

The `claimForSelection()` repository method:
```java
public int claimForSelection(String jobId) {
    return dsl.update(table("render_job"))
            .set(field("status"), "SELECTING_PROVIDER")
            .set(field("updated_at"), OffsetDateTime.now())
            .where(field("id").eq(jobId).and(field("status").eq("QUEUED")))
            .execute();
}
```

### Analysis: No deadlock, but potential contention

**In the normal `/start` flow:**

1. Outer `execute()` calls `requireJobRecord(jobId)` — **plain SELECT, no lock**
2. `claimService.claimForSelection(jobId)` — REQUIRES_NEW creates a new connection/transaction
3. REQUIRES_NEW executes: `UPDATE render_job SET status='SELECTING_PROVIDER' WHERE id=? AND status='QUEUED'`
4. REQUIRES_NEW **commits** — row lock released
5. Control returns to outer transaction
6. Outer transaction continues with more operations

**No deadlock** because at step 2, the outer transaction has only done a plain SELECT (no row lock). The REQUIRES_NEW transaction can freely acquire the row lock, update, and commit.

**However**, if the outer transaction had already written to the `render_job` row (e.g., via `updateStatus` or `updateAiScript`) BEFORE calling `claimService.claimForSelection()`, a deadlock would occur:
- Outer transaction holds row lock (from its UPDATE)
- REQUIRES_NEW tries to UPDATE the same row → **blocks waiting for outer lock**
- Outer transaction waits for REQUIRES_NEW to return → **deadlock**

**In the current code, the claim happens at step 5 (before any writes), so no deadlock occurs.** This ordering is essential and fragile — any code change that moves a write before the claim call would introduce a deadlock.

### RenderJobFailureService (REQUIRES_NEW)

```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void recordDurableFailure(String jobId, String reason) {
    int updated = renderJobRepository.markActiveJobFailed(jobId, reason);
    ...
}
```

**Deadlock risk: HIGH.** `recordDurableFailure()` is called in catch blocks:

```java
// Call site 1: after script resolution failure (line ~197)
} catch (Exception e) {
    failureService.recordDurableFailure(jobId, "Script resolution failed: " + e.getMessage());
    throw e;
}
```

At this point, the outer transaction has NOT yet written to `render_job` (step 7 is before step 8 in the flow), so **no deadlock at call site 1**.

```java
// Call site 2: after render execution failure (line ~302)
} catch (Exception e) {
    log.error("Render failed for job {}", jobId, e);
    failureService.recordDurableFailure(jobId, "Render failed: " + e.getMessage());
    throw new IllegalStateException("Render failed", e);
}
```

At this point, the outer transaction HAS written to `render_job` multiple times (steps 8-10, plus status transitions inside `finishRenderPhaseInternal`). The outer transaction **holds the row lock**.

**When `recordDurableFailure()` (REQUIRES_NEW) tries to UPDATE the same row:**
- REQUIRES_NEW needs the row lock
- Outer transaction holds the row lock
- REQUIRES_NEW **blocks** waiting for the outer lock
- Outer transaction is in a catch block and will throw (rolling back)
- **No deadlock** — the outer transaction will eventually roll back, releasing the lock, and REQUIRES_NEW will proceed

**BUT**: The timing is tricky. If the outer transaction's rollback is slow (e.g., large uncommitted changes), the REQUIRES_NEW connection will be blocked for that duration. Also, during this blocking period, the connection pool has TWO connections held (one for outer, one for REQUIRES_NEW), increasing pool pressure.

**FINDING: REQUIRES_NEW at call site 2 will BLOCK (not deadlock) until the outer transaction rolls back. This is correct behavior — the failure will be recorded durably — but it temporarily holds an extra connection from the pool.**

---

## 4. Is FFmpeg Inside a Transaction?

**YES — FFmpeg runs inside the outer `@Transactional` boundary.**

### Call chain:

```
RenderController.startRenderJob()          [no @Transactional]
  → RenderOrchestratorService.executeExistingRenderJob()  [no @Transactional]
    → RenderJobExecutionService.execute()   [@Transactional — outer tx starts here]
      → finishRenderPhaseInternal()
        → executeRenderWithOptionalDag()
          → provider.render(jobId, aiScript, profile)  [FFmpegRenderProvider]
            → processToolRunner.execute(request)  [external process: ffmpeg]
```

### FFmpegRenderProvider.render():

```java
@Component  // No @Transactional
public class FFmpegRenderProvider implements RenderProvider {
    @Override
    public RenderResult render(String jobId, String aiScript, String profile) {
        ...
        ToolExecutionRequest request = ToolExecutionRequest.withTimeout("ffmpeg", args, 600_000);
        ToolExecutionResult result = processToolRunner.execute(request);  // BLOCKS for up to 600 seconds
        ...
    }
}
```

- `FFmpegRenderProvider` is `@Component` with **no `@Transactional`** annotation
- `ProcessToolRunner` is an interface with **no `@Transactional`** — implementations spawn external processes
- FFmpeg runs as an external OS process via `Runtime.exec()` or similar
- The Spring transaction context is **propagated** (but unused by FFmpeg itself)

### Consequences:

1. **Database connection held during FFmpeg execution**: The outer `@Transactional` connection remains open and checked out from the pool for the entire FFmpeg duration (up to 600 seconds per the timeout).

2. **Row locks held**: Any `render_job` row locks taken before FFmpeg execution remain held until the outer transaction commits.

3. **Connection pool exhaustion risk**: Under concurrent load, multiple render jobs could exhaust the connection pool while FFmpeg processes are running.

4. **Transaction timeout**: If the database has a transaction timeout shorter than FFmpeg execution time, the transaction will be rolled back while FFmpeg is still running, leading to inconsistent state.

5. **No mid-execution state persistence**: If the JVM crashes during FFmpeg execution, all status transitions (QUEUED → SELECTING_PROVIDER → PROVIDER_SELECTED → EXECUTING) are lost because the outer transaction never committed.

**FINDING: FFmpeg is inside the transaction. This is the fundamental design issue — long-running external process execution should not be wrapped in a database transaction.**

---

## 5. What Is the Actual Exception?

### On the WIP Branch (7143a80): Compilation Error

The WIP branch **will not compile**. `RenderJobExecutionService.java` references `claimService` and `failureService` in the method body but never declares them as fields or constructor parameters.

**Expected compilation error:**
```
error: cannot find symbol
  symbol:   variable claimService
  location: class RenderJobExecutionService
```

This was caused by commit 7143a80 which added the method body calls but omitted the field declarations and constructor wiring. The parent commit (6504985) also lacked these declarations.

### On the Main Branch (3f9a837): No Compilation Error — Transaction Risks

The main branch has correct wiring. If executed:

1. **No deadlock on claim**: The claim happens before any outer-transaction writes to the row.
2. **Connection pool contention**: FFmpeg execution (up to 600s) holds a DB connection.
3. **Potential PessimisticLockingFailureException**: If multiple requests try to claim the same job concurrently, the REQUIRES_NEW UPDATE will serialize correctly (one wins, others get 0 rows updated and return false).
4. **Potential CannotCreateTransactionException**: If the connection pool is exhausted (all connections held by running FFmpeg jobs), new requests cannot obtain a connection for the REQUIRES_NEW transaction.
5. **Potential TransactionTimedOutException**: If the DB or Spring transaction timeout is shorter than FFmpeg execution time.

### Most Likely Runtime Exception Scenario

Under concurrent load with multiple active render jobs:

```
1. Request A starts execute() — claims job, enters FFmpeg execution (holds connection)
2. Request B starts execute() — claims different job, enters FFmpeg execution (holds connection)
3. ... N requests hold N connections for FFmpeg execution ...
4. Request N+1 starts execute() — needs connection for outer @Transactional
5. Pool exhausted → CannotCreateTransactionException: "Could not open JPA EntityManager for transaction"
   OR
   Request N+1 tries claimService.claimForSelection() (REQUIRES_NEW) — needs SECOND connection
   Pool exhausted → CannotCreateTransactionException
```

---

## Transaction Topology Diagram

```
┌─────────────────────────────────────────────────────────────┐
│  HTTP POST /tenants/{tid}/projects/{pid}/render-jobs/{jid}/start  │
│  (no transaction)                                                     │
└──────────────┬──────────────────────────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────────────────────────┐
│  RenderOrchestratorService.executeExistingRenderJob()       │
│  (no @Transactional)                                         │
└──────────────┬──────────────────────────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────────────────────────┐
│  RenderJobExecutionService.execute()                        │
│  @Transactional (REQUIRED, default isolation)                │
│  ┌───────────────────────────────────────────────────────┐  │
│  │ Connection A acquired from pool                        │  │
│  │                                                        │  │
│  │ 1. SELECT render_job (no lock)                         │  │
│  │                                                        │  │
│  │ 2. ┌─────────────────────────────────────────────┐    │  │
│  │    │ claimService.claimForSelection() [REQUIRES_NEW] │    │  │
│  │    │ Connection B acquired from pool               │    │  │
│  │    │ UPDATE render_job SET status='SELECTING_...'  │    │  │
│  │    │ → row lock on Connection B                    │    │  │
│  │    │ COMMIT (Connection B)                         │    │  │
│  │    │ Connection B returned to pool                 │    │  │
│  │    └─────────────────────────────────────────────┘    │  │
│  │                                                        │  │
│  │ 3. SELECT render_job (reload, no lock)                 │  │
│  │ 4. resolveRenderScript()                               │  │
│  │ 5. UPDATE render_job SET ai_script=...                 │  │
│  │    → ROW LOCK TAKEN on Connection A                    │  │
│  │ 6. UPDATE render_job SET status='PROVIDER_SELECTED'    │  │
│  │ 7. UPDATE render_job SET status='EXECUTING'            │  │
│  │                                                        │  │
│  │ 8. ┌─────────────────────────────────────────────┐    │  │
│  │    │ FFmpegRenderProvider.render()                │    │  │
│  │    │ ProcessToolRunner.execute(ffmpeg, 600s)      │    │  │
│  │    │ → External OS process                        │    │  │
│  │    │ → Connection A IDLE but OPEN                 │    │  │
│  │    │ → Row lock HELD                              │    │  │
│  │    │ → ⏱️ Up to 600 seconds                       │    │  │
│  │    └─────────────────────────────────────────────┘    │  │
│  │                                                        │  │
│  │ 9. Billing finalization                                │  │
│  │ 10. UPDATE render_job SET status='COMPLETING'          │  │
│  │ 11. Storage upload                                     │  │
│  │ 12. Artifact graph save                                │  │
│  │ 13. UPDATE render_job SET status='COMPLETED'           │  │
│  │                                                        │  │
│  │ COMMIT (Connection A returned to pool)                 │  │
│  └───────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

### Failure Path (Render Execution Fails)

```
│  │ 8. FFmpegRenderProvider.render() THROWS                 │  │
│  │    ┌─────────────────────────────────────────────┐      │  │
│  │    │ failureService.recordDurableFailure()       │      │  │
│  │    │ [REQUIRES_NEW]                               │      │  │
│  │    │ Connection B acquired from pool              │      │  │
│  │    │ UPDATE render_job SET status='FAILED'        │      │  │
│  │    │   → BLOCKS waiting for Connection A's lock   │      │  │
│  │    │   → Outer tx will rollback → releases lock   │      │  │
│  │    │   → Connection B proceeds, commits           │      │  │
│  │    │ Connection B returned to pool                │      │  │
│  │    └─────────────────────────────────────────────┘      │  │
│  │                                                          │  │
│  │ throw new IllegalStateException("Render failed")         │  │
│  │ → OUTER TRANSACTION ROLLS BACK                           │  │
│  │ → All outer-tx status changes LOST                       │  │
│  │ → BUT recordDurableFailure COMMITTED (survived!)         │  │
│  └─────────────────────────────────────────────────────────┘  │
```

---

## Summary of Findings

| # | Question | Answer | Risk |
|---|----------|--------|------|
| 1 | Is execute() transactional? | **YES** — `@Transactional` (REQUIRED, default) | ⚠️ Long tx duration |
| 2 | Does outer tx hold render_job row? | **YES** — from `updateAiScript()` through commit | ⚠️ Row lock held during FFmpeg |
| 3 | Does REQUIRES_NEW wait for lock? | **No deadlock for claim** (pre-write). **Blocks for failure** (post-write, outer will rollback) | ✅ Correct but fragile ordering |
| 4 | Is FFmpeg inside a transaction? | **YES** — 600s timeout, connection held idle | 🔴 **Critical design issue** |
| 5 | What is the actual exception? | WIP: **compilation error** (missing field declarations). Main: **connection pool exhaustion** under concurrent load | 🔴 WIP won't compile |

### Critical Design Issue

The `@Transactional` boundary on `execute()` wraps the entire FFmpeg execution. This means:
- A database connection is held for up to 600+ seconds per render job
- Row locks are held for the same duration
- Connection pool exhaustion is inevitable under concurrent load
- The REQUIRES_NEW pattern for claim/failure is correct in isolation but operates within a fundamentally flawed transaction boundary

### The Correct Pattern (for future work)

The transaction boundary should be split:
1. **Short tx**: Claim the job (QUEUED → SELECTING_PROVIDER) — commit
2. **Short tx**: Resolve script, set status to EXECUTING — commit
3. **No tx**: Execute FFmpeg (external process, no DB connection needed)
4. **Short tx**: Record results, transition to COMPLETED — commit

Each step commits independently, so failures are durable without REQUIRES_NEW gymnastics.
