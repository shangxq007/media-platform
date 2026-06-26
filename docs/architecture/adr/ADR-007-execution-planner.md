---
status: accepted
created: 2026-06-25
scope: platform-wide
owner: platform
---

# ADR-007: Execution Planner

## Context

The platform has Coordination Runtime (fan-out/fan-in of tasks) and Execution Runtime (where execution happens). Missing is a layer that transforms Artifact DAG into ordered, capability-matched execution stages.

## Decision

Introduce an Execution Planner that:
1. Accepts Artifact DAG as input
2. Topologically sorts artifact nodes
3. Resolves capabilities via ExtensionRegistryService
4. Selects execution backends via ExecutionBackendRegistry
5. Groups parallel-compatible nodes into ExecutionStage objects
6. Produces a Logical Execution Plan

The Execution Planner does NOT produce media graphs (FFmpeg, BMF, MLT). A BackendCompiler SPI translates logical steps to backend-specific formats.

## Consequences

- Artifact DAG remains the authoritative dependency model
- Execution Planner is a pure computation layer (no persistence)
- New BackendCompiler SPI for backend-specific translation
- OpenCue integrates as a backend compiler, not a replacement for planning

## Alternatives Rejected

- Embedding planning in Coordination Runtime: coordination handles task lifecycle, not graph topology
- Making each backend plan independently: loses cross-backend optimization

## Migration

Phase 1: Artifact DAG model + Execution Planner algorithm
Phase 2: BackendCompiler SPI + FFmpegCompiler
Phase 3: BmfCompiler + OpenCueCompiler
