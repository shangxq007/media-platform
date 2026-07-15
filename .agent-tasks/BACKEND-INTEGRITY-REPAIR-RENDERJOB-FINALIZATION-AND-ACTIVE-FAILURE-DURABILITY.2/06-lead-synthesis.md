# Lead Synthesis

## Summary of Findings

### 1. Active-State Model

```text
Reachable states: QUEUED, SELECTING_PROVIDER, PROVIDER_SELECTED, EXECUTING, COMPLETING, COMPLETED, FAILED, CANCELLED
Stale states: FALLBACKING, RETRYING
Unclear: REJECTED
```

**Decision:** FALLBACKING and RETRYING are stale pre-launch baggage. Remove from state machine transitions where safe, mark as deprecated.

### 2. Finalization Transaction Issues

```text
❌ Billing failure not durable (failJob in same transaction)
❌ Storage failure not durable (failJob in same transaction)
❌ Product/finalization failure not durable
❌ No explicit completion transaction after execute() lost @Transactional
```

**Decision:** Replace all failJob() calls with failureService.recordDurableFailure() (REQUIRES_NEW). Add explicit @Transactional for completion writes.

### 3. Transaction Topology Target

```text
execute() [NO @Transactional]
  → claimService.claimForSelection() [REQUIRES_NEW] ✅
  → resolveRenderScript() [NO transaction]
  → provider selection [NO transaction]
  → persist selected_provider [short transaction]
  → provider.render() [NO transaction - FFmpeg outside]
  → Billing reservation [NO transaction]
  → Storage/Artifact [NO transaction]
  → completion writes [@Transactional - bounded]
  → failure recording [REQUIRES_NEW] ✅
```

## Implementation Plan

### Step 1: Remove FALLBACKING/RETRYING from State Machine

Remove transitions to FALLBACKING and RETRYING from RenderJobStateMachine. Keep enum constants but mark deprecated.

### Step 2: Fix Billing Failure Durability

Replace:
```java
failJob(jobId, projectId, RenderJobStatus.EXECUTING, "BILLING_FAILED", ...);
```
With:
```java
failureService.recordDurableFailure(jobId, "Billing failed: " + reservation.error());
```

### Step 3: Fix Storage Failure Durability

Replace:
```java
failJob(jobId, projectId, RenderJobStatus.COMPLETING, "STORAGE_FAILED", ...);
```
With:
```java
failureService.recordDurableFailure(jobId, "Storage failed: " + e.getMessage());
```

### Step 4: Add Explicit Completion Transaction

Create @Transactional method for completion writes:
```java
@Transactional
protected void recordCompletion(String jobId, String projectId, ...) {
    stateMachine.transition(...);
    updateStatus(...);
}
```

### Step 5: Add Concurrency Tests

Implement Test C (overlapping concurrent start) with CyclicBarrier.

## Allowed Files

```text
render-module/src/main/java/com/example/platform/render/domain/RenderJobStateMachine.java
render-module/src/main/java/com/example/platform/render/app/RenderJobExecutionService.java
render-module/src/main/java/com/example/platform/render/infrastructure/RenderJobRepository.java
platform-app/src/test/java/com/example/platform/StartClaimAndFailureDurabilityTest.java
```

## Status

```text
SYNTHESIS COMPLETE
Ready for Agent D implementation
```
