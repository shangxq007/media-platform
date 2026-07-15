# Agent B — Finalization Transaction Investigation

## Post-Render Execution Path

### Current Flow (after execute() lost @Transactional)

```text
execute() [NO @Transactional]
  → claimAndSelect() [NO explicit transaction - relies on caller]
  → provider.render() [NO transaction]
  → finishRenderPhaseInternal() [NO explicit transaction]
    → Billing reservation
    → executeRenderWithOptionalDag()
    → Billing finalization
    → registerRenderOutput()
    → stateMachine.transition(COMPLETING → COMPLETED)
```

## Stage Analysis

### 1. Billing Reservation

```text
Method: billingEnforcementService.reserveQuota()
Transaction: NONE (runs in caller context)
External I/O: YES (billing API)
State before: EXECUTING
Failure effect: IllegalStateException thrown
Durable state now: ❌ FAILURE NOT DURABLE
Required repair: Wrap in explicit transaction or use REQUIRES_NEW failure recording
```

### 2. Render Execution

```text
Method: executeRenderWithOptionalDag()
Transaction: NONE (correct - FFmpeg outside)
External I/O: YES (FFmpeg process)
State before: EXECUTING
Failure effect: failureService.recordDurableFailure() + throw
Durable state now: ✅ DURABLE (REQUIRES_NEW)
```

### 3. Billing Finalization

```text
Method: billingEnforcementService.finalizeCost()
Transaction: NONE
External I/O: YES (billing API)
State before: EXECUTING
Failure effect: Logged but NOT failed (intentional)
Durable state now: ⚠️ NON-CRITICAL (logged only)
```

### 4. Storage/Artifact Registration

```text
Method: artifactStorageService.uploadJobOutput()
Transaction: NONE (runs in caller context)
External I/O: YES (R2/S3 storage)
State before: COMPLETING
Failure effect: failJob() called
Durable state now: ❌ FAILURE MAY NOT BE DURABLE
Required repair: Use REQUIRES_NEW failure recording
```

### 5. Product Publication

```text
Method: registerRenderOutput() → productRuntimeService
Transaction: NONE (runs in caller context)
External I/O: YES (Product/Artifact creation)
State before: COMPLETING
Failure effect: IllegalStateException thrown
Durable state now: ❌ FAILURE MAY NOT BE DURABLE
Required repair: Use REQUIRES_NEW failure recording
```

### 6. Final Completion

```text
Method: stateMachine.transition(COMPLETING → COMPLETED)
Transaction: NONE (runs in caller context)
External I/O: NO
State before: COMPLETING
Failure effect: IllegalStateException thrown
Durable state now: ❌ FAILURE MAY NOT BE DURABLE
Required repair: Use explicit bounded transaction
```

## Critical Issues

### Issue 1: Billing Failure Not Durable

```java
if (!reservation.success()) {
    // failJob() uses same transaction as caller
    failJob(jobId, projectId, RenderJobStatus.EXECUTING, "BILLING_FAILED", ...);
    throw new IllegalStateException("Billing reservation failed: ...");
}
```

If the outer caller rolls back, the FAILED state is lost.

### Issue 2: Storage Failure Not Durable

```java
} catch (Exception e) {
    // failJob() uses same transaction as caller
    failJob(jobId, projectId, RenderJobStatus.COMPLETING, "STORAGE_FAILED", ...);
    throw new IllegalStateException("Storage failed", e);
}
```

Same issue - failJob() is in the same transaction.

### Issue 3: No Explicit Completion Transaction

After execute() lost its @Transactional, the completion writes (COMPLETING → COMPLETED) have no explicit transaction boundary. This means:
- Multiple writes may not be atomic
- Partial completion is possible
- State may be inconsistent

## Required Repairs

1. **Billing failure**: Use failureService.recordDurableFailure() (REQUIRES_NEW)
2. **Storage failure**: Use failureService.recordDurableFailure() (REQUIRES_NEW)
3. **Product/finalization failure**: Use failureService.recordDurableFailure() (REQUIRES_NEW)
4. **Completion writes**: Wrap in explicit @Transactional method

## Classification

```text
BILLING_FAILURE_ROLLBACKS_FAILED_STATE: YES
STORAGE_FAILURE_ROLLBACKS_FAILED_STATE: YES
ARTIFACT_FAILURE_ROLLBACKS_FAILED_STATE: YES
COMPLETION_TRANSACTION_MISSING: YES
FINALIZATION_MODEL_VALID: NO
```
