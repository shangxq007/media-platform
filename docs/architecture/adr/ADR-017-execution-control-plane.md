---
status: accepted
created: 2026-06-26
scope: platform-wide
owner: platform
---

# ADR-017: Execution Control Plane

## Context
C6 introduced lifecycle states. C5 introduced job model. Execution environments own submit/status/cancel, coupling lifecycle to environments.

## Decision
Introduce Execution Control Plane as the single platform entry for job management:
1. `ExecutionControlService` — submit, cancel, status, complete, fail, listJobs
2. `ExecutionJobRegistry` — in-memory job tracking (no persistence)
3. Platform owns lifecycle; environments report state
4. Environment never updates platform state directly

## Consequences
- Single entry for all job operations
- Platform-owned lifecycle semantics enforced
- Events emitted for lifecycle transitions
- In-memory registry (no persistence — phase 1)
