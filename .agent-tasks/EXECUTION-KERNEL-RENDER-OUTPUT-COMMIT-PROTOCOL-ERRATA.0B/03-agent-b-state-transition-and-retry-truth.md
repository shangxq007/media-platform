# Agent B: State Transition and Retry Truth

## FALLBACKING Analysis

| Property | Value |
|----------|-------|
| Enum | EXISTS in RenderJobStatus.java |
| Inbound edge | NONE (no state transitions TO FALLBACKING) |
| Outbound edge | EXISTS in VALID_TRANSITIONS (→ EXECUTING, FAILED, CANCELLED) |
| Production writer | NONE |
| Runtime reachable | NO |

**Classification: FALLBACKING_UNREACHABLE_STALE_REFERENCE**

## RETRYING Analysis

| Property | Value |
|----------|-------|
| Enum | EXISTS in RenderJobStatus.java |
| Inbound edge | NONE (no state transitions TO RETRYING) |
| Outbound edge | EXISTS in VALID_TRANSITIONS (→ EXECUTING, FAILED, CANCELLED) |
| Production writer | NONE |
| Runtime reachable | NO |

**Classification: RETRYING_UNREACHABLE_STALE_REFERENCE**

## FAILED Reset Path

```text
FAILED → QUEUED transition EXISTS in VALID_TRANSITIONS
But no production code performs this reset.
Frozen target: Retry creates new RenderJob, not reset.
```

**Classification: FAILED_RESET_PATH_ABSENT_IN_PRODUCTION**

## Retry Semantics

```text
Frozen: Retry creates a new RenderJob
Forbidden: Resetting FAILED RenderJob to QUEUED/SELECTING_PROVIDER
```

## StaleRenderJobCompensationService

```text
Scheduler: @Scheduled(fixedDelay = 300000) — every 5 minutes
Startup: @EventListener(ApplicationReadyEvent)
States scanned: SELECTING_PROVIDER, EXECUTING (optionally QUEUED)
Multi-instance safety: NO guard
Expansion authorized: NO — target is DEFAULT_DISABLE_UNTIL_PROTOCOL_IMPLEMENTED
```

**Classification: COMPENSATION_EXPANSION_NOT_AUTHORIZED**
