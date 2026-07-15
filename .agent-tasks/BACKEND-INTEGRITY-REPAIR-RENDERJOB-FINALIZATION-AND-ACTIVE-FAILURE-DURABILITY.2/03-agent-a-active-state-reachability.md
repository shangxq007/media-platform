# Agent A — Active-State Reachability Investigation

## All RenderJob States

| State | Terminal | Active | Entry Writer | Current Reachable | Recommendation |
|-------|----------|--------|--------------|-------------------|----------------|
| QUEUED | No | No | createRenderJob | ✅ YES | KEEP |
| SELECTING_PROVIDER | No | Yes | claimForSelection | ✅ YES (from /start) | KEEP |
| PROVIDER_SELECTED | No | Yes | stateMachine.transition | ✅ YES (after script resolution) | KEEP |
| EXECUTING | No | Yes | stateMachine.transition | ✅ YES (after provider selected) | KEEP |
| FALLBACKING | No | Yes | stateMachine.transition | ❌ NO (no production code enters this) | STALE_BAGGAGE |
| RETRYING | No | Yes | stateMachine.transition | ❌ NO (no production code enters this) | STALE_BAGGAGE |
| COMPLETING | No | Yes | stateMachine.transition | ✅ YES (after render complete) | KEEP |
| COMPLETED | Yes | No | stateMachine.transition | ✅ YES | KEEP |
| FAILED | Yes | No | markActiveJobFailed | ✅ YES | KEEP |
| CANCELLED | Yes | No | cancelJob | ✅ YES | KEEP |
| REJECTED | Yes | No | (policy/quota) | ❓ UNCLEAR | INVESTIGATE |

## FALLBACKING Analysis

```text
Declaration: RenderJobStatus.FALLBACKING(false, false)
State Machine Entry: From EXECUTING
State Machine Exit: To EXECUTING, FAILED, CANCELLED
Production Code Entry: NONE FOUND
Reachable from /start: NO
```

**Classification: STALE_PRE_LAUNCH_BAGGAGE**

No production code ever transitions to FALLBACKING. The fallback provider concept exists in documentation but is not implemented. This is pre-launch baggage that should be deprecated.

## RETRYING Analysis

```text
Declaration: RenderJobStatus.RETRYING(false, false)
State Machine Entry: From EXECUTING
State Machine Exit: To EXECUTING, FAILED, CANCELLED
Production Code Entry: NONE FOUND
Reachable from /start: NO
```

**Classification: STALE_PRE_LAUNCH_BAGGAGE**

No production code ever transitions to RETRYING. The retry concept exists in documentation but is not implemented. This is pre-launch baggage that should be deprecated.

## markActiveJobFailed Coverage

```sql
UPDATE render_job SET status='FAILED', error_message=?, updated_at=now()
WHERE id=? AND status IN ('SELECTING_PROVIDER', 'PROVIDER_SELECTED', 'EXECUTING', 'COMPLETING')
```

**Missing states:** FALLBACKING, RETRYING

Since FALLBACKING and RETRYING are not reachable, this is acceptable. But if they were reachable, failures in those states would not be durably recorded.

## Recommendation

1. **Remove FALLBACKING and RETRYING from state machine transitions** where safe
2. **Mark as deprecated/unreachable** in enum documentation
3. **Do NOT implement retry/fallback behavior** — this is pre-launch, no compatibility needed
4. **Keep markActiveJobFailed coverage** for SELECTING_PROVIDER, PROVIDER_SELECTED, EXECUTING, COMPLETING

## Classification

```text
FALLBACKING_STALE_BAGGAGE: YES
RETRYING_STALE_BAGGAGE: YES
ACTIVE_STATE_MODEL_VALID: PARTIAL (stale states exist)
STATE_REMOVAL_RECOMMENDED: YES (for FALLBACKING, RETRYING)
```
