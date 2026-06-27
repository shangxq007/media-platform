---
status: accepted
created: 2026-06-26
scope: platform-wide
owner: platform
---

# ADR-016: Execution Lifecycle

## Context
C5 introduced ExecutionJob/Task/Command models without lifecycle semantics. Future environments need shared state tracking.

## Decision
1. `ExecutionStatus` enum with 9 states: CREATED→SUBMITTED→QUEUED→RUNNING→COMPLETED/FAILED/CANCELLED/TIMED_OUT
2. `ExecutionJob` extended with status, timestamps, failureReason + `withStatus()` transition method
3. `ExecutionTask` extended with status, attempt, workerId
4. Platform owns lifecycle semantics; environments report state

## Consequences
- All environments share the same state machine
- OpenCue maps OpenCue Job states to ExecutionStatus
- Kubernetes maps K8s Pod phases to ExecutionStatus
