---
status: accepted
created: 2026-06-26
scope: platform-wide
owner: platform
---

# ADR-015: Execution Environment SPI

## Context
ADR-014 defined Execution Environment as separate from Execution Backend. SPIs are needed to make environments first-class extension points.

## Decision
Three new SPIs:
1. ExecutionEnvironment — submit, cancel, status for environments
2. EnvironmentCompiler — translates BackendExecutionSpec → EnvironmentExecutionSpec
3. EnvironmentRuntimeService — discovers environments + compilers via Spring

## Consequences
- Backends unchanged — no OpenCue code in BMF/FFmpeg
- New layer: BackendCompiler → EnvironmentCompiler → Environment → Backend
- OpenCue implements ExecutionEnvironment SPI (not ExecutionBackend SPI)
- Worker model remains future work (owned by environment)
