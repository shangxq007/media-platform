# Agent B: Architecture Document Stale Reference & Retry Contract Audit

## Date
2026-07-15

## Repository
`media-platform` at commit `1acab6b` on branch `fix/pre-v5-readiness-recovery`

## Scope
1. Find every UNIQUE(render_job_id, output_type) in target docs
2. Inspect RenderJobService.retry() implementation
3. Identify stale references and contract violations

---

## 1. UNIQUE(render_job_id, output_type) Inventory

### Search Results

| # | File | Line | Context | Status |
|---|------|------|---------|--------|
| 1 | `docs/architecture/target/render-output-commit-target-state.md` | 89 | `UNIQUE(render_output_commit.render_job_id)` | ✅ CORRECT — already updated from old form |
| 2 | `docs/architecture/target/render-output-commit-protocol-errata.md` | 19 | `BEFORE: "New table: render_output" with UNIQUE(render_job_id, output_type)` | ✅ OK — historical "BEFORE" reference documenting the change |
| 3 | `docs/architecture/target/render-output-commit-protocol-closeout.md` | 19 | `BEFORE: UNIQUE(render_job_id, output_type) — allows multiple commits per RenderJob` | ✅ OK — historical "BEFORE" reference documenting the change |

**Finding:** `UNIQUE(render_job_id, output_type)` does NOT appear as a live constraint in any target doc. The target-state.md was already corrected to `UNIQUE(render_output_commit.render_job_id)`. The errata and closeout docs correctly reference it as a historical "BEFORE" state.

### Correct Target Constraint (from schema proposal + ADR-026)

```sql
-- render_output_commit: one commit per RenderJob
UNIQUE(render_output_commit.render_job_id)

-- render_output_item: one item per output role per commit
UNIQUE(render_output_item.output_commit_id, output_role)
```

---

## 2. Target-State Terminology Staleness

### Stale `render_output` References in target-state.md

The target-state.md uses `render_output` (old single-table name) in the sequence steps and blob lifecycle, while the corrected schema uses `render_output_commit` + `render_output_item` (two-table split):

| Line | Current Text | Should Reference |
|------|-------------|-----------------|
| 30 | `render_output INSERT [short transaction]` | `render_output_commit INSERT` |
| 31 | `INSERT INTO render_output (status=PENDING)` | `INSERT INTO render_output_commit (status=PENDING)` |
| 41 | `render_output: PENDING → COMMITTED` | `render_output_commit: PENDING → COMMITTED` |
| 46 | `Billing consume (idempotent via render_output.id)` | `render_output_commit.id` |
| 51 | `render_output: → FAILED` | `render_output_commit: → FAILED` |
| 76 | `after render_output COMMITTED` | `after render_output_commit COMMITTED` |
| 106 | `Ownership: render_output record` | `render_output_commit record` |
| 107 | `Only after render_output COMMITTED` | `Only after render_output_commit COMMITTED` |
| 115 | `render_output INSERT: ON CONFLICT DO NOTHING` | `render_output_commit INSERT: ON CONFLICT DO NOTHING` |

**Severity:** MEDIUM — these are prose references in an architecture doc, not SQL or code. The constraint section (lines 85-99) and schema proposal are correct. But the sequence description uses the old single-table name, which could confuse implementors.

---

## 3. RenderJobService.retry() Contract Audit

### Current Implementation

**File:** `render-module/src/main/java/com/example/platform/render/app/RenderJobService.java` (lines 108-118)

```java
@Transactional
public RenderJobResponse retry(String jobId, String tenantId) {
    assertTenantAccess(tenantId);
    RenderJobResponse job = getById(jobId);
    RenderJobStatus currentStatus = RenderJobStatus.valueOf(job.status());
    stateMachine.validateTransition(currentStatus, RenderJobStatus.QUEUED);

    renderJobRepository.updateStatusAndClearError(jobId, RenderJobStatus.QUEUED.name());
    historyRepository.record(jobId, job.status(), RenderJobStatus.QUEUED.name(), "User retry", null);
    return getById(jobId);
}
```

**Behavior:** Resets a FAILED RenderJob back to QUEUED (in-place status mutation + error clear).

### Target Contract (from ADR-026 + errata + verification contract)

| Source | Contract Statement |
|--------|-------------------|
| ADR-026 errata | "FAILED RenderJob: terminal, immutable" |
| ADR-026 errata | "Retry: creates new RenderJob" |
| ADR-026 errata | "Reset: FORBIDDEN" |
| ADR-026 §canRetry | "Target: REMOVE_FROM_CURRENT_CONTRACT. Reason: Always false, misleading, no retry runtime exists" |
| Verification Test M | "retry route = 404, no retry execution" |
| Verification Test N | "retry = 404" |

### Contract Violation Summary

| Aspect | Current Code | Target Contract | Violation |
|--------|-------------|----------------|-----------|
| FAILED immutability | Mutable (reset to QUEUED) | Terminal, immutable | ⚠️ VIOLATION |
| Retry mechanism | In-place status reset | Creates new RenderJob | ⚠️ VIOLATION |
| API exposure | Service method exists | Route returns 404 | ⚠️ PARTIAL — route returns 404 (verified by tests), but service method still callable |
| canRetry field | All statuses = false | Should be removed | ⚠️ FIELD EXISTS but all values are false |

### Route Status

Tests confirm the retry API endpoint returns 404:
- `StartClaimAndFailureDurabilityTest.java:261-265` — `retry = 404` ✅
- `MinimalMediaRenderBoundaryTest.java:190-194` — `retry = 404` ✅
- `RenderExecutionBoundaryTest.java:178` — `retry = 404` ✅
- `RenderJobPreselectionTest.java:187` — `retry = 404` ✅

**But:** The `RenderJobService.retry()` method still exists and is callable from the service layer. The `FakeRenderJobService.retry()` also mirrors this behavior. If any internal code path calls `retry()`, it will reset FAILED → QUEUED in violation of the target contract.

### State Machine: FAILED → QUEUED Transition

```java
// RenderJobStateMachine.java line 69
Map.entry(RenderJobStatus.FAILED, Set.of(RenderJobStatus.QUEUED)),
```

The state machine explicitly allows FAILED → QUEUED. The test at `RenderJobStateMachineErrorModelTest.java:110` verifies this:
```java
assertTrue(stateMachine.canTransition(RenderJobStatus.FAILED, RenderJobStatus.QUEUED));
```

And the test at line 290 explicitly notes the inconsistency:
```java
assertFalse(RenderJobStatus.FAILED.isCanRetry(), "canRetry is false but FAILED→QUEUED is a valid transition");
```

---

## 4. FALLBACKING/RETRYING Stale States

### In Code

| Location | Status |
|----------|--------|
| `RenderJobStatus.java:52` | `FALLBACKING(false, false)` — enum value exists |
| `RenderJobStatus.java:58` | `RETRYING(false, false)` — enum value exists |
| `RenderJobStatus.java:119` | `isProviderState()` includes FALLBACKING/RETRYING |
| `RenderJobStateMachine.java:54-62` | Transition entries for FALLBACKING and RETRYING exist |

### In Architecture Docs

| Source | Statement |
|--------|-----------|
| ADR-026 | "FALLBACKING: EXCLUDED (stale pre-launch baggage)" |
| ADR-026 | "RETRYING: EXCLUDED (stale pre-launch baggage)" |
| Closeout doc | "FALLBACKING: EXCLUDED (stale pre-launch baggage)" |

### Violation

FALLBACKING and RETRYING are declared "EXCLUDED" in architecture docs but remain in the code enum and state machine. Tests verify they're unreachable (`assertFalse(canTransition(EXECUTING, FALLBACKING))`) but the enum values and transition entries remain as dead code.

---

## 5. Summary of Findings

### UNIQUE(render_job_id, output_type)
- **No stale live references found.** The target-state.md was already corrected. Errata/closeout correctly document the historical change.

### Target-state.md Terminology
- **9 stale `render_output` references** in sequence/blob sections should be `render_output_commit`.

### RenderJobService.retry() Contract
- **Method exists and resets FAILED → QUEUED** — violates "FAILED: terminal, immutable" and "Reset: FORBIDDEN"
- **Route correctly returns 404** — but service method is still callable internally
- **FAILED → QUEUED transition** is in the state machine, contradicting the target contract
- **canRetry field** exists but is always false; architecture says remove it

### FALLBACKING/RETRYING
- **Enum values and transitions remain** in code despite being declared "EXCLUDED" in architecture

---

## 6. Recommended Actions

| Priority | Action | Scope |
|----------|--------|-------|
| P0 | Update `render-output-commit-target-state.md` sequence steps to use `render_output_commit` | Doc |
| P1 | Remove `RenderJobService.retry()` or make it throw explicit unsupported | Code |
| P1 | Remove FAILED → QUEUED from state machine (or gate behind feature flag) | Code |
| P2 | Remove FALLBACKING/RETRYING from RenderJobStatus enum and state machine | Code |
| P2 | Remove canRetry field from RenderJobStatus enum | Code |
| P3 | Remove FakeRenderJobService.retry() override | Code |
