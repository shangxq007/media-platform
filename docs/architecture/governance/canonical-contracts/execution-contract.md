# Execution Contract

**Contract ID:** execution
**Authority Status:** GOVERNANCE_CANONICAL
**Implementation Alignment:** ALIGNED
**Frozen Rules:** F-007, F-008

## EX-001: ExecutionJob
ExecutionJob **MUST** represent a unit of work dispatched to an ExecutionEnvironment.

## EX-002: Task
Task **MUST** represent an atomic work unit within an ExecutionJob.

## EX-003: Command
Command **MUST** represent a concrete executable instruction.

## EX-004: ExecutionEnvironment
OpenCue **MUST** be treated as an ExecutionEnvironment (F-007).

## EX-005: ExecutionBackend
FFmpeg, Remotion, GPAC, and Blender **MUST** be treated as ExecutionBackends (F-008).

## EX-006: Worker Boundary
Worker execution **MUST** be isolated from platform control plane.

## Change Authority
- ADR_ACCEPTANCE
