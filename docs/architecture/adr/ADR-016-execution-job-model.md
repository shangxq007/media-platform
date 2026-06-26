---
status: accepted
created: 2026-06-26
scope: platform-wide
owner: platform
---

# ADR-016: Execution Job Model

## Context
C4 introduced Local Execution Environment using EnvironmentExecutionSpec. Future environments (OpenCue, Kubernetes, Ray) require a Job-oriented model with task decomposition and retry policies.

## Decision
Introduce ExecutionJob as the common execution contract:
1. `ExecutionJob` — jobId, environmentId, backendId, priority, resourceRequirements, tasks[]
2. `ExecutionTask` — taskId, backendSpec, dependencies, retryPolicy
3. `ExecutionCommand` — one executable unit (PROCESS, BMF_GRAPH, future OpenCue layer)
4. `EnvironmentCompiler.compile()` now returns `ExecutionJob` (not EnvironmentExecutionSpec)
5. `ExecutionEnvironment.submit()` accepts `ExecutionJob` (not EnvironmentExecutionSpec)

## Consequences
- All environments share the same Job contract
- OpenCue maps ExecutionJob → OpenCue Job with layers
- Kubernetes maps ExecutionJob → K8s Job with pods
- Local maps ExecutionJob → single-task synchronous execution
