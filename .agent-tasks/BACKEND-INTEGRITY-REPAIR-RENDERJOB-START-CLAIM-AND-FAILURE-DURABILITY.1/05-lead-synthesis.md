# Lead Synthesis

## Based on Agent A/B/C Investigations

### Root Cause

```text
LONG_TRANSACTION_ACROSS_PROVIDER: CONFIRMED
```

The `execute()` method's `@Transactional` spans the entire execution including FFmpeg. This means:
- Database connection held during FFmpeg execution (potentially minutes)
- Row locks held during this time
- If FFmpeg fails, the outer transaction rolls back

### Transaction Topology (Current)

```text
Controller (no @Transactional)
  → OrchestratorService [@Transactional]
    → ExecutionService.execute() [@Transactional]
      → claimService.claimForSelection() [REQUIRES_NEW] ✅ Short, commits
      → reload job ✅
      → resolveRenderScript() ← Inside outer tx
      → stateMachine.transition() ← Inside outer tx
      → provider.render() ← FFmpeg INSIDE outer tx ❌ PROBLEM
      → completion ← Inside outer tx
    → COMMIT outer tx
```

### Transaction Topology (Target)

```text
Controller (no @Transactional)
  → OrchestratorService [no @Transactional on facade]
    → ExecutionService [no class-level @Transactional]
      → claimAndSelect() [@Transactional] — short: claim + reload + script + selection
        → claimService.claimForSelection() [REQUIRES_NEW]
        → reload job
        → resolveRenderScript()
        → select provider
        → persist selected_provider
      → provider.render() ← NO transaction ✅
      → recordCompletion() [@Transactional] — short: mark complete
      OR
      → failureService.recordDurableFailure() [REQUIRES_NEW] — independent
```

### Key Changes Required

1. **Remove class-level @Transactional from execute()**
2. **Create short transaction for claim+selection**
3. **FFmpeg runs outside any database transaction**
4. **Completion is a separate short transaction**
5. **Failure recording remains REQUIRES_NEW** (already correct)

### Claim Mechanism (Already Correct)

```sql
UPDATE render_job SET status='SELECTING_PROVIDER', updated_at=now()
WHERE id=? AND status='QUEUED'
```

- Atomic CAS
- Returns 1 for winner, 0 for loser
- Loser exits immediately (returns jobId)

### Failure Coverage (Already Correct)

```sql
UPDATE render_job SET status='FAILED', error_message=?, updated_at=now()
WHERE id=? AND status IN ('SELECTING_PROVIDER', 'PROVIDER_SELECTED', 'EXECUTING', 'COMPLETING')
```

Covers all 4 active states that can fail.

### Implementation Plan

**Agent D (Claude Code) must:**

1. Refactor `execute()` into smaller transactional methods
2. Ensure claim+selection is one short transaction
3. Ensure FFmpeg is outside transaction
4. Ensure completion is separate transaction
5. Keep failure recording as REQUIRES_NEW
6. Add concurrency tests with CountDownLatch
7. Verify single-winner semantics

### Allowed Files

```text
render-module/src/main/java/com/example/platform/render/app/RenderJobExecutionService.java
render-module/src/main/java/com/example/platform/render/app/RenderJobClaimService.java
render-module/src/main/java/com/example/platform/render/app/RenderJobFailureService.java
render-module/src/main/java/com/example/platform/render/infrastructure/RenderJobRepository.java
platform-app/src/test/java/com/example/platform/StartClaimAndFailureDurabilityTest.java
```

### Forbidden Changes

```text
- No new routes
- No new migrations
- No V5
- No Temporal/LiteFlow/OpenCue
- No retry/scheduler infrastructure
- No frontend changes
- No @Lazy, getBean, field injection
```

### Acceptance Tests

1. Normal start: claim winner=1, render=1
2. Sequential duplicate: logical execution=1
3. Concurrent overlap: claim attempts=2, winners=1, losers=1, render=1
4. Script failure: final=FAILED, survives reload
5. Render failure: final=FAILED, survives reload
6. Error response: no stack trace/SQL leak

## Status

```text
SYNTHESIS COMPLETE
Ready for Agent D (Claude Code) implementation
```
