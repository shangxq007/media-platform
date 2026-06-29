# Artifact DAG Indefinite Deferral and Extension Boundary v0 (P2A.2)

## 1. Purpose

Formally reclassify Artifact DAG from a deferred optimization layer to an indefinitely deferred extension layer. Remove Artifact DAG from current roadmap dependency. Strengthen architecture boundaries to prevent future agents or developers from treating Artifact DAG as a near-term or mid-term implementation target.

Chinese intent: Artifact DAG 无限期推迟，只保留扩展层。直到真的遇到性能、复用、成本、局部渲染问题，再重新评估是否落地实现。

## 2. Previous P2A.0 Decision

P2A.0 established Artifact DAG as a deferred render optimization layer with runtime modes:
- DISABLED (default)
- DRY_RUN (non-blocking analysis)
- EXPERIMENTAL (internal-only)
- REQUIRED (future-only)

P2A.0 said "Artifact DAG remains in the blueprint as a future optimization." This language still implies scheduled roadmap work.

## 3. Updated P2A.2 Decision

Artifact DAG is **indefinitely deferred** and retained only as an **extension layer**.

It is:
- Not on the current roadmap
- Not a dependency for any current work item
- Not required for rendering, Timeline Git, effects/transitions, Provider Binding, Render Execution Plan, OpenCue, ProductRuntime, StorageRuntime, Product API, or E2E validation

It may be reconsidered only after measured production bottlenecks prove a need.

## 4. Why Indefinite Deferral Is Correct

Current active priorities do not require Artifact DAG:
1. Visual Capability Contract for Effects and Transitions
2. FFmpeg/libass baseline effect and transition support
3. Provider visual consistency matrix
4. API Scenario Runner and E2E Validation Harness
5. OpenCue PVE Smoke Harness
6. OpenCue ExecutionEnvironment Adapter
7. Explicit Render → Product E2E
8. Product-facing Timeline Version / Render API Contract
9. Temporary Timeline Debug Console / Agent-driven API Validation

None of these work items have a dependency on Artifact DAG. The platform's render pipeline works without Artifact DAG. Provider binding works without Artifact DAG. Timeline Git works without Artifact DAG.

## 5. What Remains Supported

- ArtifactDagMode enum (DISABLED, DRY_RUN, EXPERIMENTAL, REQUIRED)
- ArtifactDagEvaluationResult, ArtifactDagEvaluationStatus, ArtifactDagBoundaryPolicy
- ArtifactGraph, ArtifactNode, ArtifactDependencyGraph domain types
- ArtifactGraphCompiler, CapabilityGraphCompiler services
- ArtifactGraphRepository infrastructure
- All existing tests
- Dry-run analysis (non-blocking)
- Internal audit metadata recording

## 6. What Is Removed From Current Roadmap

- Artifact DAG as near-term roadmap item
- Artifact DAG as mid-term dependency
- Incremental render as planned work
- Partial render region calculation as planned work
- Cache reuse optimization as planned work
- Artifact lineage persistence as planned work

## 7. Extension-Layer Meaning

"Extension layer" means:
- Code exists and is preserved
- Vocabulary exists and is preserved
- Tests exist and pass
- But the feature is not enabled by default
- And the feature is not required for any current product flow
- And the feature is not on the implementation roadmap
- And the feature is only activated when explicitly measured production need triggers re-evaluation

## 8. DISABLED / DRY_RUN / EXPERIMENTAL Meaning After This Decision

| Mode | Meaning After P2A.2 |
|------|---------------------|
| DISABLED | Default. Artifact DAG is not built, not used, does not block render. |
| DRY_RUN | Available for internal analysis. Non-blocking. Does not affect provider binding or render execution. |
| EXPERIMENTAL | Internal-only, feature-flagged. Not default, not public, not product-required. |
| REQUIRED | Future-only. Not enabled by default. Reserved for when measured production need triggers re-evaluation. |

## 9. Relationship to Timeline Git

Timeline Git, checkout, rollback, merge preview, non-conflicting merge plan, branch, and commit semantics are all independent of Artifact DAG. No Timeline Git operation requires Artifact DAG. Timeline Git is the active near-term priority.

## 10. Relationship to Effects and Transitions

Visual Capability Contract for Effects and Transitions, FFmpeg baseline effect/transition support, and provider visual consistency matrix are independent of Artifact DAG. Effects and transitions are active near-term priorities.

## 11. Relationship to Provider Binding

ProviderBindingPlan is determined by capability eligibility, safety policy, provider status, and deterministic priority. Artifact DAG does not drive ProviderBindingPlan by default. Only REQUIRED mode (future-only, not enabled) allows Artifact DAG to influence provider binding.

## 12. Relationship to Render Execution Plan

RenderExecutionPlan is generated from the compile pipeline without Artifact DAG input. Artifact DAG does not drive RenderExecutionPlan by default. Only REQUIRED mode (future-only, not enabled) allows Artifact DAG to influence execution plans.

## 13. Relationship to OpenCue

OpenCue ExecutionEnvironment adapter and PVE smoke harness are independent of Artifact DAG. OpenCue is an active near-term priority. Future artifact-level OpenCue scheduling is conditional on measured production need.

## 14. Relationship to ProductRuntime / StorageRuntime

ProductRuntime handles product lifecycle, metadata, dependency, and query. Artifact DAG does not call ProductRuntime. StorageRuntime handles storage/materialization/checksums/StorageReference. Artifact DAG does not call StorageRuntime directly.

## 15. Relationship to Product API

Product-facing Timeline Version / Render API Contract is independent of Artifact DAG. Product API is an active near-term priority.

## 16. Relationship to E2E Validation

API Scenario Runner and E2E Validation Harness are independent of Artifact DAG. E2E validation is an active near-term priority.

## 17. Future Trigger Conditions

Artifact DAG may be revisited only when one or more of the following are measured:

- Repeated full renders are too slow for real workloads
- Render cost becomes unacceptable
- Cache reuse has measurable ROI
- Partial render requirements become unavoidable
- Artifact lineage is needed for audit/debug/compliance
- OpenCue workload volume justifies artifact-level scheduling
- Large timeline edits require safe sub-render reuse
- Provider output reuse becomes a proven requirement

Do not implement any of these now.

## 18. Explicitly Forbidden Assumptions

- Artifact DAG is next phase
- Artifact DAG is near-term optimization
- Artifact DAG is scheduled implementation
- Artifact DAG is required future layer
- Artifact DAG is required for MVP
- Artifact DAG is required for internal demo
- Artifact DAG is required for OpenCue E2E
- Artifact DAG is required for Product API
- Artifact DAG is required for Timeline Git
- Artifact DAG is required for effects/transitions
- Artifact DAG is required for Provider Binding
- Artifact DAG is required for Render Execution Plan
- Artifact DAG is a dependency for any current work item

## 19. Required Wording Updates

Replace weak wording like:
- "Artifact DAG is future work"
- "Artifact DAG will be added later"
- "Artifact DAG is a later optimization phase"
- "Artifact DAG may drive incremental render in future"

With stronger wording:
- "Artifact DAG is indefinitely deferred and retained only as an extension layer. It is not on the current roadmap and must not be required for current rendering, Timeline Git, effects/transitions, Provider Binding, Render Execution Plan, OpenCue, Product API, or E2E validation. It may be reconsidered only after measured production bottlenecks prove a need."

## 20. Follow-up Roadmap

### Current Roadmap (Active)

| ID | Work Item |
|----|-----------|
| P2A.2 | Artifact DAG Indefinite Deferral and Extension Boundary |
| P2R.0 | Visual Capability Contract for Effects and Transitions | ✅ Implemented |
| P2R.1 | FFmpeg Baseline Effect Plan |
| P2R.2 | FFmpeg Baseline Transition Plan |
| P2R.3 | Provider Visual Consistency Matrix |
| P2X.0 | API Scenario Runner and E2E Validation Harness |
| P2X.1 | OpenCue PVE Smoke Harness |
| P2X.2 | OpenCue ExecutionEnvironment Adapter |
| P2X.3 | Explicit Render to Product E2E Scenario |
| P2P.0 | Product-facing Timeline Version / Render API Contract |
| P2D.0 | Temporary Timeline Debug Console / Agent-driven Validation |

### Future Extension (Conditional, Not Committed)

| ID | Work Item | Condition |
|----|-----------|-----------|
| P3A.x | Artifact DAG Re-evaluation | Only after measured production bottleneck |
| P3A.x | Artifact Cache Key Design | If needed |
| P3A.x | Artifact Lineage Persistence | If needed |
| P3A.x | Incremental Render POC | If needed |
| P3A.x | Partial Render Region Calculation | If needed |

P3A.x items are conditional, not committed roadmap.
