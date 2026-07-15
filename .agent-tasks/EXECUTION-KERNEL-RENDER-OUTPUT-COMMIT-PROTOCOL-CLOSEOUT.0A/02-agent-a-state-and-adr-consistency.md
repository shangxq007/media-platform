# Agent A: Canonical RenderJob State and ADR Consistency

## Current RenderJob States (Source Enum)

| State | Source Enum | DB Mapping | Current Writer | Runtime Reachable | Target Retained | Reason |
|-------|-----------|-----------|----------------|-------------------|-----------------|--------|
| QUEUED | ✅ | ✅ | createRenderJob | ✅ | ✅ | Primary entry |
| SELECTING_PROVIDER | ✅ | ✅ | claimForSelection | ✅ | ✅ | Claim state |
| PROVIDER_SELECTED | ✅ | ✅ | stateMachine.transition | ✅ | ✅ | Post-selection |
| EXECUTING | ✅ | ✅ | stateMachine.transition | ✅ | ✅ | Render state |
| FALLBACKING | ✅ | ✅ | NONE | ❌ | ❌ | STALE_BAGGAGE |
| RETRYING | ✅ | ✅ | NONE | ❌ | ❌ | STALE_BAGGAGE |
| COMPLETING | ✅ | ✅ | stateMachine.transition | ✅ | ✅ | Publication state |
| COMPLETED | ✅ | ✅ | stateMachine.transition | ✅ | ✅ | Terminal |
| FAILED | ✅ | ✅ | markActiveJobFailed | ✅ | ✅ | Terminal |
| CANCELLED | ✅ | ✅ | cancelJob | ✅ | ✅ | Terminal |
| REJECTED | ✅ | ✅ | (policy) | ❓ | ✅ | Terminal |

## FALLBACKING Analysis

```text
Enum: EXISTS in RenderJobStatus.java
State Machine: Transition from EXECUTING REMOVED (commit 97f1787)
Production Writer: NONE
Runtime Reachable: NO
Compensation Reference: YES (StaleRenderJobCompensationService checks it)
```

**Classification: FALLBACKING_STALE**

FALLBACKING exists in the enum but has no production writer. The transition from EXECUTING was removed. It is stale pre-launch baggage.

## RETRYING Analysis

```text
Enum: EXISTS in RenderJobStatus.java
State Machine: Transition from EXECUTING REMOVED (commit 97f1787)
Production Writer: NONE
Runtime Reachable: NO
Compensation Reference: YES (StaleRenderJobCompensationService checks it)
```

**Classification: RETRYING_STALE**

RETRYING exists in the enum but has no production writer. The transition from EXECUTING was removed. It is stale pre-launch baggage.

## Canonical Target State Set

```text
QUEUED
SELECTING_PROVIDER
PROVIDER_SELECTED
EXECUTING
COMPLETING
COMPLETED
FAILED
CANCELLED
```

**FALLBACKING: EXCLUDED from target architecture**
**RETRYING: EXCLUDED from target architecture**
**Future retry: Creates a new RenderJob**

## COMPLETING Semantics

```text
COMPLETING = Provider execution succeeded AND platform is performing canonical Render Output Commit Protocol
```

It must not mean retry, fallback, cleanup, or arbitrary post-processing.

## CAS Requirements

```text
Every canonical RenderJob transition must use expected-state CAS or equivalent durable version check.

Current CAS coverage:
- QUEUED → SELECTING_PROVIDER: ✅ (claimForSelection)
- SELECTING_PROVIDER → PROVIDER_SELECTED: ❌ (no CAS)
- PROVIDER_SELECTED → EXECUTING: ❌ (no CAS)
- EXECUTING → COMPLETING: ❌ (no CAS)
- COMPLETING → COMPLETED: ❌ (no CAS)
```

Later transitions need CAS implementation.

## Classification

```text
FALLBACKING_STALE: YES
RETRYING_STALE: YES
TARGET_STATE_SET_VALID: YES (8 states)
```
