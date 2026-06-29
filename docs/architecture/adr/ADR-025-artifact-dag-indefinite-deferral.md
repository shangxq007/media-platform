# ADR-025: Artifact DAG Indefinite Deferral

## Status

Accepted

## Context

P2A.0 established Artifact DAG as a deferred optimization layer with runtime modes (DISABLED, DRY_RUN, EXPERIMENTAL, REQUIRED). P2A.1 constrained render DAG and timeline operations.

However, the existing deferral language still positions Artifact DAG as a near-term or mid-term roadmap dependency — referenced in pipeline diagrams, execution planner inputs, and provider binding flows. This creates ambiguity: future agents or developers may treat Artifact DAG as a scheduled implementation target rather than an indefinitely deferred extension layer.

The platform's current active priorities are:
1. Visual Capability Contract for Effects and Transitions
2. FFmpeg/libass baseline effect and transition support
3. Provider visual consistency matrix
4. API Scenario Runner and E2E Validation Harness
5. OpenCue PVE Smoke Harness
6. OpenCue ExecutionEnvironment Adapter
7. Explicit Render → Product E2E
8. Product-facing Timeline Version / Render API Contract
9. Temporary Timeline Debug Console / Agent-driven API validation

None of these require Artifact DAG.

## Decision

Artifact DAG is indefinitely deferred and retained only as an extension layer and future optimization vocabulary.

It is not a dependency for current rendering, productization, OpenCue execution, Timeline Git, effect/transition support, Provider Binding, Render Execution Plan, ProductRuntime, StorageRuntime, Product API, or E2E validation.

Production implementation may be reconsidered only after measured production bottlenecks demonstrate a clear need.

## Consequences

### Positive

- Removes ambiguity about Artifact DAG's place in the roadmap
- Prevents future agents from treating Artifact DAG as a required implementation target
- Preserves existing code and vocabulary for future use
- Clarifies that current priorities (effects, transitions, E2E, OpenCue) are independent of Artifact DAG

### Negative

- If production bottlenecks emerge sooner than expected, the deferral decision must be explicitly reversed
- Blueprint documents that show Artifact DAG in future pipeline diagrams need annotation

### Neutral

- Existing Artifact DAG code (ArtifactGraph, ArtifactNode, ArtifactGraphCompiler, etc.) is preserved
- P2A.0 mode vocabulary (ArtifactDagMode, ArtifactDagEvaluationResult, etc.) is preserved
- Existing tests remain valid

## What Remains

- ArtifactDagMode enum with DISABLED, DRY_RUN, EXPERIMENTAL, REQUIRED
- ArtifactDagEvaluationResult, ArtifactDagEvaluationStatus, ArtifactDagBoundaryPolicy
- ArtifactGraph, ArtifactNode, ArtifactDependencyGraph domain types
- ArtifactGraphCompiler, CapabilityGraphCompiler services
- ArtifactGraphRepository infrastructure
- All existing tests (ArtifactDagModeTest, ConstrainedGraphSafetyRulesTest)

## What Is Removed From Roadmap Dependency

- Artifact DAG is removed from near-term roadmap
- Artifact DAG is removed from mid-term dependency chain
- Artifact DAG is no longer a prerequisite for any current work item
- Incremental render is indefinitely deferred
- Partial render region calculation is indefinitely deferred
- Cache reuse optimization is indefinitely deferred

## Trigger Conditions for Future Re-evaluation

Artifact DAG may be revisited only when one or more of the following are measured:

- Repeated full renders are too slow for real workloads
- Render cost becomes unacceptable
- Cache reuse has measurable ROI
- Partial render requirements become unavoidable
- Artifact lineage is needed for audit/debug/compliance
- OpenCue workload volume justifies artifact-level scheduling
- Large timeline edits require safe sub-render reuse
- Provider output reuse becomes a proven requirement

## Relationship to Timeline Git

Timeline Git, checkout, rollback, merge preview, non-conflicting merge plan, branch, and commit semantics are all independent of Artifact DAG. No Timeline Git operation requires Artifact DAG.

## Relationship to Effects and Transitions

Visual Capability Contract, FFmpeg baseline effect/transition support, and provider visual consistency matrix are independent of Artifact DAG.

## Relationship to Provider Binding

ProviderBindingPlan is determined by capability eligibility, safety policy, provider status, and deterministic priority. Artifact DAG does not drive ProviderBindingPlan by default. Only REQUIRED mode (future-only, not enabled) allows Artifact DAG to influence provider binding.

## Relationship to Render Execution Plan

RenderExecutionPlan is generated from the compile pipeline without Artifact DAG input. Artifact DAG does not drive RenderExecutionPlan by default.

## Relationship to OpenCue

OpenCue ExecutionEnvironment adapter and PVE smoke harness are independent of Artifact DAG. Future artifact-level OpenCue scheduling is conditional on measured production need.

## Relationship to ProductRuntime

ProductRuntime handles product lifecycle, metadata, dependency, and query. Artifact DAG does not call ProductRuntime.

## Relationship to StorageRuntime

StorageRuntime handles storage/materialization/checksums/StorageReference. Artifact DAG does not call StorageRuntime directly.

## Relationship to Product API

Product-facing Timeline Version / Render API Contract is independent of Artifact DAG.

## Safety Rules

1. Artifact DAG is indefinitely deferred
2. Artifact DAG is an extension layer, not a roadmap dependency
3. Artifact DAG is not required for MVP, internal demo, OpenCue E2E, Product API, Timeline Git, checkout, rollback, merge preview, non-conflicting merge plan, effect/transition rendering, or explicit render
4. Artifact DAG must not block rendering
5. Artifact DAG must not drive ProviderBindingPlan by default
6. Artifact DAG must not drive RenderExecutionPlan by default
7. Artifact DAG must not be exposed through public APIs
8. Artifact DAG may remain as DISABLED / DRY_RUN / EXPERIMENTAL only
9. Artifact DAG production implementation resumes only after measured production pain
10. Render impact may remain semantic and coarse-grained; it must not require artifact-level impact or partial render region calculation

## Non-goals

- Implementing incremental render
- Implementing partial render
- Implementing cache key generation
- Implementing artifact cache reuse
- Implementing artifact lineage persistence
- Making Artifact DAG required
- Wiring Artifact DAG into ProviderBindingPlan
- Wiring Artifact DAG into RenderExecutionPlan
- Wiring Artifact DAG into OpenCue
- Wiring Artifact DAG into ProductRuntime
- Wiring Artifact DAG into StorageRuntime

## Future Work

- P3A.x — Artifact DAG Re-evaluation, only after measured production bottleneck
- P3A.x — Artifact Cache Key Design, if needed
- P3A.x — Artifact Lineage Persistence, if needed
- P3A.x — Incremental Render POC, if needed
- P3A.x — Partial Render Region Calculation, if needed

All P3A.x items are conditional, not committed roadmap.
