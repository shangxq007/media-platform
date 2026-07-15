# Failure Injection Investigation: RenderJobExecutionService

**File**: `render-module/src/main/java/com/example/platform/render/app/RenderJobExecutionService.java`  
**Date**: 2026-07-15  
**Purpose**: Identify transaction boundaries, state transitions, and failure injection points for runtime integrity tests.

---

## 1. Transaction Boundaries

### `@Transactional` Methods

| Method | Line | Scope |
|--------|------|-------|
| `execute(String tenantId, String jobId)` | 161 | Full pipeline: load → resolve → select provider → execute → persist → complete |
| `finishRenderPhase(String tenantId, String jobId)` | 234 | Second half: render execution → persist → complete |

### Critical Observation: Single Transaction Encompasses Entire Pipeline

Both `execute()` and `finishRenderPhase()` wrap the **entire execution pipeline** in a single `@Transactional` boundary. This means:

- **All database writes** (status updates, selected_provider, artifact URI, error messages) are committed atomically
- **If render fails mid-execution**, the transaction rolls back, undoing any status transitions that happened before the failure
- **State machine transitions** (in-memory `ConcurrentHashMap`) are NOT rolled back — they persist in the `RenderJobStateMachine` instance regardless of transaction outcome
- **Side effects** (notifications, events, quota consumption) happen within the transaction boundary

### Transaction Scope Map

```
execute() @Transactional ─────────────────────────────────────────────┐
│                                                                      │
│  1. assertTenantAccess()                                             │
│  2. renderJobRepository.requireJobRecord(jobId)         [DB READ]    │
│  3. stateMachine.transition() → SELECTING_PROVIDER      [IN-MEMORY]  │
│  4. updateStatus() → SELECTING_PROVIDER                 [DB WRITE]   │
│  5. resolveRenderScript()                                           │
│     └─ renderJobRepository.findAiScriptById()           [DB READ]   │
│     └─ timelineSnapshotService.findPayload()            [DB READ]   │
│     └─ aiGatewayPort.chat()                             [EXTERNAL]   │
│  6. effectTimelineInspector.extractFromScript()                      │
│  7. renderProfileResolver.resolve()                                  │
│  8. renderJobRepository.updateProfile()                 [DB WRITE]   │
│  9. effectEntitlementPort.validateEffectAccess()        [EXTERNAL]   │
│ 10. renderJobRepository.updateAiScript()                [DB WRITE]   │
│ 11. stateMachine.transition() → PROVIDER_SELECTED       [IN-MEMORY]  │
│ 12. updateStatus() → PROVIDER_SELECTED                 [DB WRITE]   │
│ 13. stateMachine.transition() → EXECUTING              [IN-MEMORY]  │
│ 14. updateStatus() → EXECUTING                         [DB WRITE]   │
│ 15. finishRenderPhaseInternal()                                     │
│     └─ billingEnforcementService.reserveQuota()         [EXTERNAL]  │
│     └─ executeRenderWithOptionalDag()                               │
│        └─ providerRuntimeEngine.resolveProvider()       [EXTERNAL]  │
│        └─ renderJobRepository.updateTraceId()           [DB WRITE]  │
│        └─ renderJobRepository.updateSelectedProvider()  [DB WRITE]  │
│        └─ provider.render()                             [EXTERNAL]  │
│     └─ billingEnforcementService.finalizeCost()         [EXTERNAL]  │
│     └─ stateMachine.transition() → COMPLETING           [IN-MEMORY] │
│     └─ updateStatus() → COMPLETING                      [DB WRITE]  │
│     └─ artifactStorageService.uploadJobOutput()         [EXTERNAL]  │
│     └─ artifactGraphRepository.saveGraph()              [DB WRITE]  │
│     └─ renderJobRepository.updateArtifactUri()          [DB WRITE]  │
│     └─ stateMachine.transition() → COMPLETED            [IN-MEMORY] │
│     └─ updateStatus() → COMPLETED                       [DB WRITE]  │
│     └─ quotaService.consumeQuota()                      [DB WRITE]  │
│     └─ notificationEventPublisher.publish()             [EXTERNAL]  │
│     └─ eventPublisher.publishEvent()                    [EXTERNAL]  │
└──────────────────────────────────────────────────────────────────────┘
```

---

## 2. Where `selected_provider` Is Written

### Location: `executeRenderWithOptionalDag()` — Line 458

```java
renderJobRepository.updateSelectedProvider(jobId, providerName);
```

### Context

The write happens **after** provider resolution succeeds but **before** the actual render call:

```java
// Line 441-460
ProviderRuntimeEngine.ProviderResolutionResult resolutionResult =
        providerRuntimeEngine.resolveProvider(resolutionRequest);

if (!resolutionResult.isSuccess()) {
    throw new IllegalStateException("No render provider available for profile: " + profile ...);
}

RenderProvider provider = resolutionResult.selectedProvider();
String providerName = resolutionResult.selectedProviderName();

// Store trace ID and selected Provider in job for observability
renderJobRepository.updateTraceId(jobId, resolutionResult.traceId());
renderJobRepository.updateSelectedProvider(jobId, providerName);  // <-- HERE

return provider.render(jobId, aiScript, profile);
```

### Implication for Failure Injection

- If `resolveProvider()` throws an exception, `selected_provider` is **never written** (transaction rolls back)
- If `resolveProvider()` returns `isSuccess() == false`, an `IllegalStateException` is thrown, `selected_provider` is **never written**
- If `provider.render()` throws after `updateSelectedProvider()`, the transaction rolls back and `selected_provider` is **reverted**
- The `selected_provider` write is **not committed** until the entire `@Transactional` method completes successfully

---

## 3. State Transitions

### Transition Map (from `RenderJobStateMachine`)

```
QUEUED ──────────→ SELECTING_PROVIDER ──────────→ PROVIDER_SELECTED
  │                      │                              │
  ├→ CANCELLED           ├→ FAILED                      ├→ EXECUTING
  └→ REJECTED            └→ CANCELLED                   │   │
                                                        │   ├→ COMPLETING ──→ COMPLETED
                                                        │   │       │
                                                        │   │       └→ FAILED
                                                        │   │
                                                        │   ├→ FAILED
                                                        │   ├→ FALLBACKING ──→ EXECUTING
                                                        │   ├→ RETRYING ──→ EXECUTING
                                                        │   └→ CANCELLED
                                                        │
                                                        └→ CANCELLED
```

### Transition Points in Code

| From | To | Line | Trigger |
|------|----|------|---------|
| currentStatus | SELECTING_PROVIDER | 182-184 | Start of `execute()` |
| SELECTING_PROVIDER | PROVIDER_SELECTED | 209-211 | After script resolution |
| PROVIDER_SELECTED | EXECUTING | 214-216 | Before render execution |
| (conditional) | EXECUTING | 267-271 | Resume in `finishRenderPhaseInternal()` |
| EXECUTING | FAILED | 280-284 | Billing reservation failure |
| EXECUTING | FAILED | 296-299 | Render execution failure |
| EXECUTING | COMPLETING | 316-318 | After successful render |
| COMPLETING | FAILED | 329-332 | Storage upload failure |
| COMPLETING | COMPLETED | 373-375 | After successful artifact persistence |

### Key Observation: Dual State Tracking

The system has **two parallel state tracking mechanisms**:

1. **`RenderJobStateMachine`** (in-memory `ConcurrentHashMap`): Tracks state transitions for validation and tracing. **Not persisted to DB.** Survives only within the JVM lifetime.

2. **`renderJobRepository.updateStatus()`** (DB): Persists the actual job status to the `render_job` table. This is the source of truth for job state.

The `updateStatus()` method (line 553-560) calls:
- `stateMachine.validateTransition()` — validates the transition is legal
- `renderJobRepository.updateStatus()` — writes to DB
- `historyRepository.record()` — writes to `render_job_status_history`
- `notificationEventPublisher.publish()` — fires event

---

## 4. Failure Injection Points

### 4.1 Selector Exception (Provider Resolution Failure)

**Location**: `executeRenderWithOptionalDag()` — Lines 441-447

**Injection Strategy**:
- Mock `ProviderRuntimeEngine` to throw exception during `resolveProvider()`
- Mock `ProviderRuntimeEngine` to return `isSuccess() == false` (null selectedProvider)
- Mock `CapabilityNegotiationService` to throw during negotiation
- Mock `ProviderHealthMonitor` to report all providers unhealthy (triggers fallback to all candidates, then `selectProvider()` may return null if empty)

**Expected Behavior**:
- Exception propagates up to `finishRenderPhaseInternal()` catch block (line 294-300)
- State transitions to FAILED via `failJob()` (line 298)
- Error code: `RENDER_FAILED`
- Transaction rolls back: `selected_provider` NOT persisted, status reverts to EXECUTING

**Test Approach**:
```java
// Mock ProviderRuntimeEngine to throw
when(providerRuntimeEngine.resolveProvider(any()))
    .thenThrow(new RuntimeException("Provider resolution failed"));

// Or mock to return failure result
when(providerRuntimeEngine.resolveProvider(any()))
    .thenReturn(new ProviderResolutionResult(
        traceId, null, null, List.of(), ..., false));
```

### 4.2 Persistence Failure (DB Write Failure)

**Location**: Multiple points within `execute()` and `finishRenderPhaseInternal()`

**Injection Strategy**:
- Mock `RenderJobRepository` to throw on `updateSelectedProvider()` (line 458)
- Mock `RenderJobRepository` to throw on `updateStatus()` (line 556)
- Mock `RenderJobStatusHistoryRepository` to throw on `record()` (line 557)
- Mock `ArtifactGraphRepository` to throw on `saveGraph()` (line 365)
- Mock `RenderJobRepository` to throw on `updateArtifactUri()` (line 370)

**Expected Behavior**:
- If `updateSelectedProvider()` fails: exception propagates, transaction rolls back, job reverts to EXECUTING state in DB
- If `updateStatus()` fails: exception propagates, transaction rolls back
- If `historyRepository.record()` fails: exception propagates, transaction rolls back (this is within `updateStatus()` helper)
- If `artifactGraphRepository.saveGraph()` fails: exception propagates, transaction rolls back, job reverts to COMPLETING state in DB

**Test Approach**:
```java
// Mock repository to throw on specific method
when(renderJobRepository.updateSelectedProvider(anyString(), anyString()))
    .thenThrow(new RuntimeException("DB connection lost"));

// Or mock DSLContext to simulate DB failure
```

### 4.3 Render Failure (Provider Execution Failure)

**Location**: `executeRenderWithOptionalDag()` — Line 460

**Injection Strategy**:
- Mock `RenderProvider.render()` to throw exception
- Mock `RenderProvider.render()` to return invalid/null result
- Mock `PipelineDagExecutorService` to return `dag.success() == false` (line 413)
- Mock `PipelineDagExecutorService.execute()` to throw exception

**Expected Behavior**:
- Exception propagates to `finishRenderPhaseInternal()` catch block (line 294-300)
- State transitions to FAILED via `failJob()` (line 298)
- Error code: `RENDER_FAILED`
- Transaction rolls back: all DB writes within the transaction are reverted
- Note: `selected_provider` WAS written before `provider.render()` (line 458), but since it's in the same transaction, it gets rolled back

**Test Approach**:
```java
// Mock provider to throw
when(provider.render(anyString(), anyString(), anyString()))
    .thenThrow(new RuntimeException("FFmpeg process crashed"));

// Or mock DAG executor to fail
when(pipelineDagExecutorService.execute(any(), any(), any(), any(), any()))
    .thenReturn(new DagExecutionResult(false, null, null, "DAG node failed"));
```

---

## 5. Critical Architectural Observations

### 5.1 Transaction Boundary Risk: Long-Running Transaction

The `@Transactional` boundary on `execute()` encompasses:
- Multiple DB reads/writes
- External API calls (AI gateway, billing service, provider execution, artifact storage)
- Potentially long-running render operations (minutes to hours)

This is a **long-running transaction** anti-pattern. If the render takes 30 minutes, the DB connection is held open for 30 minutes. This can lead to:
- Connection pool exhaustion
- Lock contention on `render_job` row
- Timeout issues with DB connection pool settings

### 5.2 State Machine vs DB State Divergence

The `RenderJobStateMachine` is in-memory and not transactional. If:
- A transition is recorded in-memory but the transaction rolls back
- The JVM restarts mid-execution
- Multiple instances of `RenderJobExecutionService` run (horizontal scaling)

The in-memory state machine will be out of sync with the DB. The DB is the source of truth, but the state machine's `validateTransition()` calls may reject valid transitions if its in-memory state is stale.

### 5.3 Side Effects Within Transaction

The following side effects happen **within** the `@Transactional` boundary:
- `notificationEventPublisher.publish()` — fires events before commit
- `eventPublisher.publishEvent()` — fires Spring events before commit
- `quotaService.consumeQuota()` — writes quota before commit

If the transaction rolls back after these side effects, the external systems have already received the events/notifications. This is a classic **dual-write problem**.

### 5.4 `failJob()` Is Called But Exception Is Also Thrown

In the catch blocks (lines 294-300, 329-333), the code:
1. Calls `failJob()` which writes FAILED status to DB
2. Throws `IllegalStateException`

Since `failJob()` writes to DB within the same transaction, and then the exception propagates which triggers rollback, the FAILED status write is **also rolled back**. The job ends up in whatever state it was before the transaction started (likely EXECUTING).

This means **failed jobs may not actually be marked as FAILED in the database** after a transaction rollback.

---

## 6. Recommended Test Scenarios

### Scenario 1: Selector Exception (Happy Path Failure)
1. Submit job → QUEUED
2. Execute job → transitions to SELECTING_PROVIDER
3. Inject: `ProviderRuntimeEngine.resolveProvider()` throws
4. Verify: Job status in DB is EXECUTING (rolled back from SELECTING_PROVIDER)
5. Verify: `selected_provider` is NULL in DB
6. Verify: State machine in-memory shows EXECUTING (not rolled back)

### Scenario 2: Persistence Failure (DB Unavailable)
1. Submit job → QUEUED
2. Execute job → transitions to SELECTING_PROVIDER → PROVIDER_SELECTED
3. Inject: `renderJobRepository.updateSelectedProvider()` throws
4. Verify: Job status in DB is EXECUTING (rolled back)
5. Verify: `selected_provider` is NULL in DB
6. Verify: No render was attempted

### Scenario 3: Render Failure (Provider Crash)
1. Submit job → QUEUED
2. Execute job → transitions through SELECTING_PROVIDER → PROVIDER_SELECTED → EXECUTING
3. Provider resolution succeeds, `selected_provider` written
4. Inject: `provider.render()` throws
5. Verify: Job status in DB is EXECUTING (rolled back from FAILED write)
6. Verify: `selected_provider` is NULL in DB (rolled back)
7. Verify: No artifact graph exists

### Scenario 4: Storage Failure (Post-Render)
1. Submit job → QUEUED
2. Execute job → full render succeeds
3. Inject: `artifactStorageService.uploadJobOutput()` throws
4. Verify: Job status in DB is EXECUTING (rolled back from COMPLETING/FAILED)
5. Verify: No artifact graph exists
6. Verify: Render output is orphaned on local filesystem

### Scenario 5: Billing Failure (Pre-Render)
1. Submit job → QUEUED
2. Execute job → transitions to EXECUTING
3. Inject: `billingEnforcementService.reserveQuota()` fails
4. Verify: Job status in DB is EXECUTING (rolled back from FAILED)
5. Verify: No render was attempted

---

## 7. Key Dependencies for Mocking

| Component | Interface/Class | Injection Point |
|-----------|----------------|-----------------|
| Provider Runtime | `ProviderRuntimeEngine` | `resolveProvider()` |
| Provider | `RenderProvider` | `render()` |
| Billing | `BillingEnforcementService` | `reserveQuota()`, `finalizeCost()` |
| Storage | `RenderArtifactStorageService` | `uploadJobOutput()` |
| Artifact Graph | `ArtifactGraphRepository` | `saveGraph()` |
| Status History | `RenderJobStatusHistoryRepository` | `record()` |
| Job Repository | `RenderJobRepository` | `updateStatus()`, `updateSelectedProvider()`, `updateTraceId()` |
| Quota | `RenderQuotaService` | `consumeQuota()` |
| Notifications | `NotificationEventPublisher` | `publish()` |
| Events | `ApplicationEventPublisher` | `publishEvent()` |

---

## 8. Summary

The `RenderJobExecutionService.execute()` method has a **single `@Transactional` boundary** that encompasses the entire render pipeline. This creates several failure injection opportunities but also reveals architectural risks:

- **Selector exception**: Inject at `ProviderRuntimeEngine.resolveProvider()` → job stays in EXECUTING, no selected_provider persisted
- **Persistence failure**: Inject at `RenderJobRepository.updateSelectedProvider()` → job stays in EXECUTING, no selected_provider persisted
- **Render failure**: Inject at `RenderProvider.render()` → job stays in EXECUTING, selected_provider rolled back
- **Storage failure**: Inject at `RenderArtifactStorageService.uploadJobOutput()` → job stays in EXECUTING, all artifacts rolled back

**Critical finding**: The `failJob()` method writes FAILED status to DB, but since it's within the same `@Transactional` boundary as the exception, the FAILED status is rolled back along with everything else. Failed jobs may end up in a non-terminal state (EXECUTING) in the DB, requiring external intervention (retry, manual cleanup) to resolve.
