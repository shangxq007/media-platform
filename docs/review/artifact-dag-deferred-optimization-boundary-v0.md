# Artifact DAG Deferred Optimization Boundary v0 (P2A.0)

## 1. Purpose

Mark Artifact DAG as a deferred render optimization layer with safe runtime modes. Prevent future code or agents from treating Artifact DAG as a required near-term render path. Artifact DAG remains in the blueprint as a future optimization for incremental render, cache reuse, artifact lineage, partial recomputation, and performance bottleneck handling.

## 2. Inventory Summary

Artifact DAG exists as:
- **Domain vocabulary**: ArtifactGraph, ArtifactNode, ArtifactDependencyGraph, ArtifactDAGImpact
- **Compile services**: ArtifactGraphCompiler, CapabilityGraphCompiler
- **Execution infrastructure**: DagExecutionEngine, ArtifactCache, PipelineDagExecutorService
- **Repository**: ArtifactGraphRepository (jOOQ)
- **API DTOs**: IncrementalReuseArtifactDto, ArtifactInfoResponse, PublicArtifactResponse
- **Tests**: ArtifactGraphCompilerTest, CapabilityGraphCompilerTest, PipelineDagExecutorServiceTest, and others

Full inventory: `/tmp/p2a0-artifact-dag-inventory.md`

## 3. Existing Implementation Status

| Component | Status | Blocking? |
|-----------|--------|-----------|
| ArtifactGraph domain | Implemented | No |
| ArtifactNode domain | Implemented | No |
| ArtifactDependencyGraph | Implemented | No |
| ArtifactGraphCompiler | Implemented | No (throws on null input) |
| CapabilityGraphCompiler | Implemented | No (throws on null input) |
| DagExecutionEngine | Implemented | No (collects errors) |
| ArtifactCache | Implemented | No |
| PipelineDagExecutorService | Implemented | No (returns failed result) |
| ArtifactGraphRepository | Implemented | No |
| ProviderBindingPlan integration | Not implemented | N/A |
| RenderExecutionPlan integration | Not implemented | N/A |
| Public API | Not implemented | N/A |
| Incremental render | Not implemented | N/A |

## 4. Decision

Artifact Dependency DAG is a future performance optimization and artifact-lineage layer.

It is **not required** for:
- Timeline Git
- Rollback
- Branch switching
- Merge preview
- Template workflow MVP
- Explicit full render
- Caption Template MVP render path
- FFmpeg/libass production baseline

It **must not**:
- Block render when disabled
- Be required by near-term product flows
- Drive provider binding or render execution planning by default
- Expose public API

It **may** exist as internal domain vocabulary and optional dry-run analysis.

## 5. Runtime Modes

| Mode | Description | Default? | Affects Render? |
|------|-------------|----------|-----------------|
| `DISABLED` | Do not build or use Artifact DAG | **Yes** | No |
| `DRY_RUN` | Build for analysis only | No | No |
| `EXPERIMENTAL` | Internal-only, feature-flagged | No | No |
| `REQUIRED` | Future-only, full evaluation | No | Yes (when enabled) |

## 6. Default Behavior

Artifact DAG default mode is `DISABLED`. When disabled:
- Artifact DAG is not built
- Artifact DAG is not used
- Render continues normally
- Audit/metadata may record `artifactDagMode=DISABLED` and `artifactDagImpact=NOT_COMPUTED`
- No provider/render decision changes

## 7. DISABLED Semantics

```
Do not build Artifact DAG.
Do not use Artifact DAG.
Do not block render.
Do not change provider binding.
Do not change render execution plan.
Audit/metadata may record artifactDagMode=DISABLED and artifactDagImpact=NOT_COMPUTED.
```

## 8. DRY_RUN Semantics

```
Artifact DAG may be built for internal analysis only.
Result may be logged or recorded in internal audit metadata if already supported.
Must not affect provider binding.
Must not affect render execution plan.
Must not block render.
Must not expose public API.
```

## 9. EXPERIMENTAL Semantics

```
Internal-only.
Feature-flagged if implemented.
Not default.
Not public.
Not product-required.
May be used only in tests or controlled experiments.
Must not become default path.
```

## 10. REQUIRED Future-only Semantics

```
Future-only.
Must not be enabled in this task.
If enum value is present, tests must prove it is not default.
When Artifact DAG is ready for production, this mode enables full evaluation.
```

## 11. Render Path Boundary

Artifact DAG must not block render when disabled. The compile path (ArtifactGraphCompiler → CapabilityGraphCompiler) is called in the timeline compilation flow, but failure in DRY_RUN mode is non-blocking. The main render path (FFmpeg/libass baseline) continues regardless of Artifact DAG status.

## 12. Provider Binding Boundary

Artifact DAG must not drive ProviderBindingPlan by default. The CapabilityGraphCompiler produces a LogicalCapabilityGraph which maps to capability requirements, but this does not feed into provider binding in the current implementation. Only REQUIRED mode (future) allows Artifact DAG to influence provider binding.

## 13. Execution Plan Boundary

Artifact DAG must not drive RenderExecutionPlan by default. The DagExecutionEngine executes RenderPlans with topological sort, but the Artifact DAG evaluation result does not alter the execution plan in DISABLED or DRY_RUN modes. Only REQUIRED mode (future) allows Artifact DAG to influence execution plans.

## 14. ProductRuntime Boundary

Artifact DAG does not call ProductRuntime. Artifact DAG is a compile-time planning graph, not a runtime execution system.

## 15. StorageRuntime Boundary

Artifact DAG does not call StorageRuntime directly. The ArtifactGraphRepository persists artifact nodes and graphs, but this is infrastructure-level persistence, not StorageRuntime materialization/checksum/StorageReference operations.

## 16. Timeline Git Relationship

Timeline Git / diff / patch / merge-preview line is the near-term priority. Artifact DAG is recognized as an optimization layer between Timeline semantics and execution planning, not a required MVP layer. Timeline rollback, branch switching, audit, and merge preview are prioritized over partial render optimization.

## 17. What is Intentionally Not Implemented

- Incremental render
- Artifact-level cache invalidation
- Partial render region calculation
- Artifact DAG persistence (beyond existing repository)
- Artifact DAG public API
- Artifact DAG provider dispatch
- Making Artifact DAG required
- Making Artifact DAG drive ProviderBindingPlan
- Making Artifact DAG drive RenderExecutionPlan

## 18. Future Trigger Conditions

Artifact DAG may become required when:
- Incremental render becomes a performance requirement
- Cache reuse across render jobs becomes critical
- Partial recomputation is needed for large timelines
- Artifact lineage tracking is needed for provenance

Until then, Artifact DAG remains a deferred optimization layer.

## 19. Follow-up Tasks

- P2V.5: Timeline Merge Engine
- P2V.6: Timeline Conflict Resolution
- P2V.7: Timeline Git Persistence
- P2A.1: Constrained Render DAG and Timeline Operation Safety Rules (see ADR-024)
- P2A.2: Incremental Render Planning (future, when triggered)

> Render DAG and timeline-related graph structures are constrained media-domain DAGs, not arbitrary user-programmable graphs or global optimization systems. Provider binding uses deterministic eligibility and priority rather than global combinatorial optimization. See ADR-024.
