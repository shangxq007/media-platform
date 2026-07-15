# Agent B: Quota/Idempotency, Compensation Service, and canRetry Audit

**Branch**: `arch/render-output-commit-protocol` @ `234689e`  
**Scope**: Billing/quota accounting, StaleRenderJobCompensationService, canRetry semantics

---

## 1. Billing/Quota Accounting

### 1.1 RenderBillingRecord Idempotency

**File**: `render-module/.../infrastructure/billing/RenderBillingRecord.java`  
**File**: `render-module/.../infrastructure/billing/RenderBillingRecordRepository.java`

**ID generation**: `RenderBillingRecord.create()` produces `id = "bill-" + jobId` — deterministic, not UUID.

**Save semantics**: `RenderBillingRecordRepository.save()` uses jOOQ `INSERT ... ON CONFLICT(id) DO UPDATE` (upsert on primary key `id`).

```java
dsl.insertInto(table("render_billing_record"))
    .columns(...)
    .values(...)
    .onConflict(field("id"))
    .doUpdate()
    .set(field("actual_cost"), record.actualCost())
    .set(field("usage_seconds"), record.usageSeconds())
    .set(field("provider_id"), record.providerId())
    .set(field("output_size_bytes"), record.outputSizeBytes())
    .set(field("status"), record.status().name())
    .set(field("completed_at"), ...)
    .execute();
```

**Key observations**:
- ✅ Upsert on `id` is idempotent for the same record content — calling `save()` twice with the same finalized state is safe.
- ⚠️ The `onConflict` set does NOT update `estimated_cost`, `tenant_id`, or `created_at` — these are write-once from initial insert. This is correct for a billing record lifecycle.
- ⚠️ `job_id` has a `UNIQUE INDEX ix_billing_job_id` at the DB level, so even if the app tried to insert a second record for the same job, the DB would reject it (assuming the unique index is on `job_id` alone, which it is per migration L2242).
- ⚠️ The `finalizeCost()` method in `BillingEnforcementService` has a fallback: if no existing record is found for a `jobId`, it creates a new one with `RenderBillingRecord.create()`. Since `id = "bill-" + jobId`, this is also deterministic and the upsert handles the race.

**Conclusion**: `RenderBillingRecord` persistence is idempotent. Duplicate `save()` calls for the same `jobId` are safe. The deterministic ID (`bill-{jobId}`) + upsert-on-id pattern means no double-charging from retries.

### 1.2 QuotaUsageRepository.incrementUsage Semantics

**File**: `render-module/.../app/QuotaUsageRepository.java`

```java
public int incrementUsage(String tenantId, String featureCode, int amount) {
    Optional<QuotaUsageRecord> existing = findByTenantAndFeature(tenantId, featureCode);
    if (existing.isPresent()) {
        int newValue = existing.get().usageValue() + amount;
        dsl.update(table("quota_usage"))
            .set(field("usage_value"), newValue)
            .set(field("updated_at"), OffsetDateTime.now())
            .where(field("id").eq(existing.get().id()))
            .execute();
        return newValue;
    } else {
        // INSERT new row
    }
}
```

**Critical issues**:
- ❌ **NOT ATOMIC**: `findByTenantAndFeature` then `update` is a classic read-then-write race. Two concurrent calls can both read the same `usageValue`, add `amount`, and write the same result — one increment is lost.
- ❌ **NOT idempotent**: Each call adds `amount` to the current value. Calling `incrementUsage("t1", "render", 1)` twice will increment by 2, not 1.
- ❌ **No unique constraint on (tenant_id, feature_code)**: The migration creates `ix_quota_usage_tenant_feature` as a plain index (not unique). Multiple rows for the same tenant+feature could exist, though the code's `findByTenantAndFeature` uses `fetchOne()` which would throw `DataAccessException` if multiple rows exist.
- ❌ **No optimistic locking**: No version column, no `WHERE usage_value = old_value` guard.
- ⚠️ **No DB-level atomic increment**: Should use `UPDATE quota_usage SET usage_value = usage_value + ? WHERE tenant_id = ? AND feature_code = ?` for atomicity.

**Conclusion**: `incrementUsage` is NOT safe for concurrent access and is NOT idempotent. Under concurrent `reserveQuota` calls for the same tenant, quota can be over-counted (lost update) or double-counted (idempotent retries).

### 1.3 BillingLedgerJdbcRepository Insertion Semantics

**File**: `billing-module/.../infrastructure/BillingLedgerJdbcRepository.java`

```java
public void saveEntry(BillingLedgerEntry entry) {
    jdbc.update("INSERT INTO billing_ledger_entry (id, ...) VALUES (?, ...)", ...);
}
```

**Key observations**:
- ❌ **Plain INSERT, no upsert**: No `ON CONFLICT` / `ON DUPLICATE KEY` handling. If `saveEntry()` is called twice with the same `entryId`, it will throw a primary key violation (assuming `id` is the PK, which it is per migration L832).
- ❌ **No idempotency key**: The entry ID is generated externally (in `BillingLedgerEntry`), not derived from a stable key. If the caller generates a new UUID each time, duplicate billing entries will be created.
- ⚠️ **No uniqueness constraint on (reference_type, reference_id)**: Only an index exists (`ix_billing_ledger_ref`), not a unique index. Duplicate ledger entries for the same reference are possible.

**Conclusion**: `BillingLedgerJdbcRepository.saveEntry()` is NOT idempotent. A retry-safe billing system needs either upsert semantics or a caller-generated idempotency key that the repository checks.

### 1.4 reserveQuota vs consumeQuota Meaning

**File**: `render-module/.../infrastructure/billing/BillingEnforcementService.java`

**`reserveQuota(tenantId, jobId, estimatedCost)`**:
- Calls `renderQuotaService.consumeQuota(tenantId, "render", 1)` — immediately increments quota usage by 1.
- Creates a `RenderBillingRecord` with `ESTIMATED` status and the estimated cost.
- Returns `ReservationResult` with the billing record ID.
- **Semantics**: "reserve" is a misnomer — it actually **consumes** quota immediately. There is no separate "reserve then commit" two-phase pattern. The quota is consumed at reservation time, not at finalization time.

**`finalizeCost(tenantId, jobId, providerId, actualDuration, outputSizeBytes)`**:
- Calculates actual cost.
- Updates the existing billing record to `FINALIZED` status with actual costs.
- Records usage via `UsageMeteringService.recordUsage()` with idempotency keys (`job-{jobId}-seconds`, `job-{jobId}-bytes`).
- **Semantics**: "finalize" updates the billing record with actuals but does NOT adjust quota. Quota was already consumed at reservation time.

**Double consumption issue**:
- `BillingEnforcementService.reserveQuota()` calls `renderQuotaService.consumeQuota()` at line 144.
- `RenderJobExecutionService` at line 450 ALSO calls `quotaService.consumeQuota(tenantId, "render", 1)` on job completion.
- ❌ This means quota is consumed TWICE per job: once at reservation and once at completion. This is a double-counting bug.

**Conclusion**: "reserve" and "consume" are semantically identical — both increment quota. The naming suggests a two-phase pattern that doesn't exist. The double-call in `RenderJobExecutionService` causes double-counting.

### 1.5 Existing Uniqueness Constraints

| Table | Column(s) | Constraint Type | Notes |
|---|---|---|---|
| `render_billing_record` | `id` | PRIMARY KEY | Deterministic: `"bill-" + jobId` |
| `render_billing_record` | `job_id` | UNIQUE INDEX (`ix_billing_job_id`) | One billing record per job |
| `render_billing_record` | `tenant_id` | INDEX (not unique) | — |
| `billing_ledger_entry` | `id` | PRIMARY KEY | Caller-generated, not deterministic |
| `billing_ledger_entry` | `reference_type, reference_id` | INDEX (not unique) | No duplicate protection |
| `quota_usage` | `id` | PRIMARY KEY | `"qtu-" + randomId`, not deterministic |
| `quota_usage` | `tenant_id, feature_code` | INDEX (not unique) | No duplicate row protection |

**Gaps**:
- `quota_usage` should have `UNIQUE(tenant_id, feature_code)` or the application should use `INSERT ... ON CONFLICT` to prevent duplicate rows.
- `billing_ledger_entry` should have `UNIQUE(reference_type, reference_id)` if idempotency is required.

---

## 2. StaleRenderJobCompensationService

### 2.1 Scheduler Annotation

**File**: `render-module/.../app/StaleRenderJobCompensator.java`

```java
@Component
public class StaleRenderJobCompensator {
    @Scheduled(fixedDelayString = "${render.stale-compensator.interval:PT5M}")
    public void compensateStaleJobs() {
```

- `@Scheduled(fixedDelayString)` — delay between end of previous invocation and start of next. Default `PT5M` (5 minutes).
- Configurable via `render.stale-compensator.interval`.
- Can be disabled via `render.stale-compensator.enabled` (default: `true`).

### 2.2 Startup Listener

**File**: `render-module/.../app/StaleRenderJobStartupListener.java`

```java
@Component
@ConditionalOnProperty(prefix = "render.stale-compensator", name = "startup-enabled",
                       havingValue = "true", matchIfMissing = true)
public class StaleRenderJobStartupListener {
    @EventListener(ApplicationReadyEvent.class)
    public void compensateOnStartup() {
```

- Runs once on `ApplicationReadyEvent`.
- Skipped if `render.execution.mode=temporal` AND `skipOnTemporal=true` (default: true).
- Startup compensation uses `CompensationRequest.startup()` which has different semantics:
  - `includeQueued`: configurable via `render.stale-compensator.startup-include-queued` (default: false)
  - For `local` execution mode: no cutoff time (all in-flight jobs are considered stale).
  - For non-local: cutoff is `now - 2 minutes`.

### 2.3 Current Reachable States in Compensation

**File**: `render-module/.../app/StaleRenderJobCompensationService.java`

The `compensate()` method targets:

```java
List<String> activeStatuses = new ArrayList<>(List.of(
    RenderJobStatus.SELECTING_PROVIDER.name(),
    RenderJobStatus.EXECUTING.name()));
if (request.includeQueued()) {
    activeStatuses.add(RenderJobStatus.QUEUED.name());
}
```

**Reachable from scheduled compensation** (default, `includeQueued=false`):
- `SELECTING_PROVIDER` → `FAILED`
- `EXECUTING` → `FAILED`

**Reachable from startup compensation** (`includeQueued` configurable, default false):
- Same as above, plus `QUEUED` if `includeQueued=true`.

**NOT targeted by compensation**:
- `PROVIDER_SELECTED` — jobs in this state will NOT be compensated.
- `FALLBACKING` — jobs in this state will NOT be compensated.
- `RETRYING` — jobs in this state will NOT be compensated.
- `COMPLETING` — jobs in this state will NOT be compensated.

⚠️ **Gap**: `PROVIDER_SELECTED`, `FALLBACKING`, `RETRYING`, and `COMPLETING` are non-terminal active states that can get stuck but are invisible to the compensation service. A job stuck in `PROVIDER_SELECTED` after a crash will never be cleaned up.

**Transition validation**: The service uses `stateMachine.validateTransition(currentStatus, RenderJobStatus.FAILED)` which checks `RenderJobStateMachine.VALID_TRANSITIONS`. All of `SELECTING_PROVIDER → FAILED`, `EXECUTING → FAILED`, and `QUEUED → FAILED` are valid transitions per the state machine.

### 2.4 Concurrency Guard

- ❌ **No concurrency guard**: There is no `@SchedulerLock`, ` ShedLock`, distributed lock, or `SELECT ... FOR UPDATE` in the compensation service. If multiple instances of the application run (e.g., in a cluster), they will all execute the compensation simultaneously.
- ❌ **No optimistic locking on state updates**: The `UPDATE render_job SET status = 'FAILED' WHERE id = ?` has no `AND status = currentStatus` guard. Two concurrent compensators could both update the same job.
- The `RenderJobStateMachine` instance is per-service (created in constructor), not shared, so its in-memory `currentStates` map is not a coordination mechanism.

**Conclusion**: The compensation service has no concurrency protection. In a multi-instance deployment, duplicate compensation and lost updates are possible.

---

## 3. canRetry Semantics

### 3.1 RenderJobStatus.canRetry

**File**: `render-module/.../domain/RenderJobStatus.java`

```java
QUEUED(false, false),
SELECTING_PROVIDER(false, false),
PROVIDER_SELECTED(false, false),
EXECUTING(false, false),
FALLBACKING(false, false),
RETRYING(false, false),
COMPLETING(false, false),
COMPLETED(true, false),
FAILED(true, false),
CANCELLED(true, false),
REJECTED(true, false);
```

**All states have `canRetry = false`**. Every `RenderJobStatus` enum value is constructed with `canRetry=false`.

The test at `RenderJobStateMachineErrorModelTest:290` explicitly validates this:
```java
assertFalse(RenderJobStatus.FAILED.isCanRetry(),
    "canRetry is false but FAILED→QUEUED is a valid transition");
```

This test **acknowledges the contradiction**: `FAILED → QUEUED` IS a valid state transition (per `VALID_TRANSITIONS` map, line 69), but `FAILED.isCanRetry()` returns `false`.

### 3.2 All Usages of canRetry (RenderJobStatus)

| Location | Usage | Impact |
|---|---|---|
| `RenderJobStatus.isCanRetry()` | Getter | Always returns `false` for all states |
| `RenderJobStateMachineErrorModelTest` | Assertion | Validates that `canRetry` is always `false` |

**No code path reads `RenderJobStatus.isCanRetry()` to make a decision**. It exists as a field but is never consulted by the retry logic.

### 3.3 Retry Logic Actually Uses Different Mechanism

The actual retry decision in `RenderJobLeaseService` (farm/lease layer):
```java
boolean canRetry = retryable && lease.attempt() < lease.maxAttempts();
```
This is a **local variable** named `canRetry`, computed from `retryable` (method parameter) and attempt count. It does NOT use `RenderJobStatus.isCanRetry()`.

The actual retry decision in `PlatformCoordinationService` (outbox/task layer):
```java
if (task.canRetry()) {  // PlatformTask.canRetry()
    // attemptCount < maxAttempts && status != COMPLETED
}
```
This uses `PlatformTask.canRetry()` which is a completely separate method on a different class.

### 3.4 Summary: canRetry is Dead Code on RenderJobStatus

- `RenderJobStatus.canRetry` field is **always false** for every state.
- No production code reads `RenderJobStatus.isCanRetry()`.
- Retry decisions are made at the **lease layer** (`RenderJobLeaseService`) and **task coordination layer** (`PlatformCoordinationService`), not at the job status level.
- The state machine DOES allow `FAILED → QUEUED` transition, enabling retry, but the decision is not driven by `canRetry`.
- **No external contract depends on `RenderJobStatus.isCanRetry()`** — it's a dead field with a contradicting test that documents the contradiction rather than fixing it.

---

## 4. Key Findings Summary

### Critical Issues
1. **Double quota consumption**: `BillingEnforcementService.reserveQuota()` and `RenderJobExecutionService` both call `consumeQuota()`, doubling quota usage per job.
2. **`incrementUsage` race condition**: Read-then-write is non-atomic; concurrent calls lose increments.
3. **No concurrency guard on compensation**: Multi-instance deployments will double-compensate.
4. **Compensation misses states**: `PROVIDER_SELECTED`, `FALLBACKING`, `RETRYING`, `COMPLETING` are invisible to stale job detection.

### Design Issues
5. **`reserveQuota` is a misnomer**: It immediately consumes quota — there's no reserve/commit two-phase pattern.
6. **`BillingLedgerJdbcRepository` is not idempotent**: Plain INSERT, no upsert, no duplicate detection.
7. **`quota_usage` table lacks unique constraint**: `(tenant_id, feature_code)` should be unique.
8. **`RenderJobStatus.canRetry` is dead code**: Always false, never read, contradicts valid transitions.

### Positive Findings
9. **`RenderBillingRecord` is idempotent**: Deterministic ID + upsert on `id` prevents double billing records.
10. **`UsageMeteringService.recordUsage` has idempotency keys**: Uses `ConcurrentHashMap`-based idempotency check (though this is in-memory only, not persistent).
11. **`StaleRenderJobCompensator` is well-configurable**: Threshold, interval, enable/disable, startup behavior all configurable.
