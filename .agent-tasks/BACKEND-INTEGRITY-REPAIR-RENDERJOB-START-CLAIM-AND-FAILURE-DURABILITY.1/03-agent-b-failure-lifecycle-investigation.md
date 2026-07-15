# Agent B: RenderJob Failure Lifecycle Investigation

## Files Read

| File | Path |
|------|------|
| RenderJobExecutionService.java | `render-module/src/main/java/com/example/platform/render/app/RenderJobExecutionService.java` |
| RenderJobFailureService.java | `render-module/src/main/java/com/example/platform/render/app/RenderJobFailureService.java` |
| RenderJobRepository.java | `render-module/src/main/java/com/example/platform/render/infrastructure/RenderJobRepository.java` |
| RenderJobStatus.java | `render-module/src/main/java/com/example/platform/render/domain/RenderJobStatus.java` |
| RenderJobStateMachine.java | `render-module/src/main/java/com/example/platform/render/domain/RenderJobStateMachine.java` |
| RenderJobClaimService.java | `render-module/src/main/java/com/example/platform/render/app/RenderJobClaimService.java` |

---

## 1. All RenderJob States

From `RenderJobStatus.java`:

| State | Terminal? | Active? (`isActive()`) | isProviderState? |
|-------|-----------|------------------------|------------------|
| QUEUED | No | No | No |
| SELECTING_PROVIDER | No | **Yes** | Yes |
| PROVIDER_SELECTED | No | **Yes** | Yes |
| EXECUTING | No | **Yes** | Yes |
| FALLBACKING | No | **Yes** | Yes |
| RETRYING | No | **Yes** | Yes |
| COMPLETING | No | **Yes** | No |
| COMPLETED | Yes | No | No |
| FAILED | Yes | No | No |
| CANCELLED | Yes | No | No |
| REJECTED | Yes | No | No |

`isActive()` = `!terminal && this != QUEUED` — so **6 active states**: SELECTING_PROVIDER, PROVIDER_SELECTED, EXECUTING, FALLBACKING, RETRYING, COMPLETING.

---

## 2. markActiveJobFailed() Coverage Analysis

From `RenderJobRepository.java` L164-172:

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

### Coverage Table

| Active State | In `markActiveJobFailed()` WHERE? | StateMachine allows → FAILED? | Covered? |
|---|---|---|---|
| SELECTING_PROVIDER | ✅ Yes | ✅ Yes | ✅ **COVERED** |
| PROVIDER_SELECTED | ✅ Yes | ✅ Yes | ✅ **COVERED** |
| EXECUTING | ✅ Yes | ✅ Yes | ✅ **COVERED** |
| FALLBACKING | ❌ **NO** | ✅ Yes | ❌ **GAP** |
| RETRYING | ❌ **NO** | ✅ Yes | ❌ **GAP** |
| COMPLETING | ✅ Yes | ✅ Yes | ✅ **COVERED** |

**FINDING: `markActiveJobFailed()` does NOT cover FALLBACKING or RETRYING.** These are active states (`isActive()=true`), the state machine permits them to transition to FAILED, but the SQL WHERE clause excludes them. If a job is in FALLBACKING or RETRYING and `recordDurableFailure()` is called, `markActiveJobFailed()` returns 0 and the failure is **silently dropped** with only a warn log.

---

## 3. Failure Paths Per Active State

### Execution Pipeline State Flow (happy path)

```
QUEUED → (claimForSelection CAS) → SELECTING_PROVIDER → PROVIDER_SELECTED → EXECUTING → COMPLETING → COMPLETED
```

### Where Failures Occur in `executeInternal()` / `finishRenderPhaseInternal()`

| State at Failure | Failure Trigger | Failure Method Used | Durable (REQUIRES_NEW)? |
|---|---|---|---|
| SELECTING_PROVIDER | Script resolution exception (L231-233) | `failureService.recordDurableFailure()` | ✅ Yes |
| PROVIDER_SELECTED | *(immediately transitions to EXECUTING — no failure point)* | N/A | N/A |
| EXECUTING | Render execution exception (L335-338) | `failureService.recordDurableFailure()` | ✅ Yes |
| EXECUTING | Billing reservation failure (L321-325) | `stateMachine.transition()` + `failJob()` | ❌ **No — same transaction** |
| COMPLETING | Storage upload failure (L368-371) | `stateMachine.transition()` + `failJob()` | ❌ **No — same transaction** |
| FALLBACKING | *(defined in state machine but no code path in ExecutionService)* | N/A | N/A |
| RETRYING | *(defined in state machine but no code path in ExecutionService)* | N/A | N/A |

### Two Distinct Failure Mechanisms

**Path A: Durable failure (`RenderJobFailureService.recordDurableFailure`)**
- Uses `@Transactional(propagation = Propagation.REQUIRES_NEW)`
- Calls `markActiveJobFailed()` → atomic CAS on active states
- **Survives outer transaction rollback**
- Only called for: script resolution failure, render execution failure

**Path B: Non-durable failure (`failJob` helper at L586-590)**
- Runs in the **same transaction** as the outer `execute()`
- Uses `updateStatus()` → `renderJobRepository.updateStatus()` + state machine + history
- **Does NOT survive outer transaction rollback** — if the transaction rolls back, the failure state is lost and the job reverts to its pre-failure state
- Called for: billing reservation failure, storage upload failure

**FINDING: Billing and storage failures use the non-durable path.** If the outer transaction rolls back after these failures, the job will remain in EXECUTING/COMPLETING with no error recorded — a ghost active job.

---

## 4. Durable Reload Behavior (Resume on Re-execute)

From `executeInternal()` L200-226:

```java
if (RenderJobStatus.COMPLETED.name().equals(status)) {
    return jobId;  // already done
}

if ("QUEUED".equals(status)) {
    // claim CAS...
} else if (!"SELECTING_PROVIDER".equals(status) && !"EXECUTING".equals(status)) {
    throw new IllegalStateException("Render job " + jobId + " is in state " + status + ", cannot start");
}
```

### Resume Table

| Job State on Reload | `/start` Behavior | Can Resume? |
|---|---|---|
| QUEUED | Triggers CAS claim | ✅ Yes |
| SELECTING_PROVIDER | Proceeds to script resolution | ✅ Yes |
| PROVIDER_SELECTED | **Throws IllegalStateException** | ❌ **Cannot resume** |
| EXECUTING | Proceeds to finishRenderPhase | ✅ Yes |
| FALLBACKING | **Throws IllegalStateException** | ❌ **Cannot resume** |
| RETRYING | **Throws IllegalStateException** | ❌ **Cannot resume** |
| COMPLETING | **Throws IllegalStateException** | ❌ **Cannot resume** |

**FINDING: Only QUEUED, SELECTING_PROVIDER, and EXECUTING can be resumed via `/start`.** Jobs stuck in PROVIDER_SELECTED, COMPLETING, FALLBACKING, or RETRYING cannot be recovered by re-executing — they throw and remain stuck.

---

## 5. Selected Provider Persistence

- `renderJobRepository.updateSelectedProvider(jobId, providerName)` is called at L497 inside `executeRenderWithOptionalDag()`
- This happens **after** the state transitions to EXECUTING (L254-257), during the actual render phase
- If failure occurs in SELECTING_PROVIDER (script resolution), selected_provider is **never set**
- If failure occurs in PROVIDER_SELECTED (theoretically, though no explicit failure point exists), selected_provider is **never set**
- `markActiveJobFailed()` does **not clear** selected_provider — it only sets status, error_message, updated_at
- After durable failure, selected_provider persists as whatever it was (null if never set, or the provider name if failure occurred during/after EXECUTING)

**FINDING: selected_provider is not cleared on failure.** If a job fails during EXECUTING after provider selection, the selected_provider column retains the provider name. This is actually useful for debugging — it tells you which provider was active when the job failed.

---

## 6. Javadoc/Documentation Mismatches

| Location | Says | Actual Behavior |
|---|---|---|
| `RenderJobFailureService` L26 | "EXECUTING → FAILED" | Covers SELECTING_PROVIDER, PROVIDER_SELECTED, EXECUTING, COMPLETING |
| `RenderJobRepository.markActiveJobFailed` L161 | "SELECTING_PROVIDER or EXECUTING → FAILED" | Actually covers all 4 states in the WHERE clause |

Both Javadocs are **outdated** — the implementation was updated to cover PROVIDER_SELECTED and COMPLETING but the documentation was not.

---

## 7. Lifecycle Events

Events published during failure:

| Event | Published by Durable Path? | Published by Non-Durable Path? |
|---|---|---|
| `RenderJobStatusChangedEvent` | ❌ No (markActiveJobFailed is raw SQL) | ✅ Yes (via updateStatus) |
| `RenderJobFailedEvent` | ❌ No | ✅ Yes (via failJob) |
| Status history record | ❌ No | ✅ Yes (via historyRepository.record) |

**FINDING: Durable failure path (`recordDurableFailure`) does NOT publish `RenderJobStatusChangedEvent`, `RenderJobFailedEvent`, or status history records.** It only does a raw SQL update + error_message update. Listeners (notifications, billing, metrics) are not notified of durable failures.

---

## 8. Summary of Gaps

### Critical Gaps

1. **markActiveJobFailed() misses FALLBACKING and RETRYING** — If a job is in these states and a durable failure is triggered, the failure is silently dropped (returns 0, logs warn only).

2. **Billing and storage failures are non-durable** — Uses `failJob()` in the same transaction. If the outer transaction rolls back, the failure is lost and the job remains in an active state.

3. **Resume via `/start` only handles SELECTING_PROVIDER and EXECUTING** — Jobs stuck in PROVIDER_SELECTED, COMPLETING, FALLBACKING, or RETRYING cannot be recovered. They throw `IllegalStateException` and remain stuck forever.

### Moderate Gaps

4. **Durable failure path skips lifecycle events** — No `RenderJobFailedEvent`, no `RenderJobStatusChangedEvent`, no status history record. Downstream systems (notifications, billing, metrics) are not informed.

5. **Javadoc mismatches** — Both `RenderJobFailureService` and `markActiveJobFailed` have outdated documentation.

### Non-Issues

6. **selected_provider persistence after failure** — Retained for debugging. Not a bug, but worth documenting as intentional.
