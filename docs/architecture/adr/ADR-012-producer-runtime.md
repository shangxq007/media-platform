---
status: accepted
created: 2026-06-25
scope: platform-wide
owner: platform
---

# ADR-012: Producer Runtime

## Context

Current processing components (Whisper, OCR, Vision, Creative Planner, TimelineMutation, Marketplace Builder) are described using different concepts. Execution Planner needs a unified Producer abstraction to plan Producer Graph instead of Task Graph.

## Decision

Unify every processing component under a single Producer abstraction:

1. `Producer` — producerId, type (AI/CREATIVE/MUTATION/MEDIA/PROJECTION/PACKAGING), capabilities, input/output types
2. `Producer Graph` — connects Products via Producer edges
3. Execution Planner plans Producer Graph (not PlatformTask directly)
4. Producer contract: Input Products → Producer → Output Products

## Consequences

- All processing uniform — Execution Planner sees Producers, not providers/tasks/backends
- Provider Runtime becomes one Producer implementation strategy
- Producer Graph replaces ad-hoc dependency tracking
- 7 architecture rules formalize Producer responsibilities

## Rejected Alternatives

1. Keep separate concepts per domain: Execution Planner needs unified model
2. Embed Producer logic in Product Runtime: conflates data with processing
3. Use PlatformTask as the scheduling primitive: tasks are execution, not planning

## Migration

Phase 1: Producer domain model
Phase 2: Execution Planner integration
Phase 3: Producer Graph
