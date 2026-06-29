# ADR-024 — Constrained Render DAG and Timeline Operations

## Status

Accepted

## Context

The media platform uses multiple graph-shaped structures: Timeline, WorkflowDefinition, RenderExecutionPlan, ArtifactDependencyGraph, LogicalCapabilityGraph, ProviderBindingPlan, and ProductDependency lineage. These structures serve different purposes — editing, planning, execution, caching, and provenance.

If these graph structures are unconstrained, the platform risks drifting into hard computational problems:

- Resource-constrained project scheduling (NP-hard)
- Global provider assignment optimization (NP-hard)
- Graph partitioning (NP-hard)
- Optimal cache reuse selection (NP-complete)
- Optimal partial render region selection (NP-hard)
- Multi-objective scheduling (NP-hard)

The platform must use DAGs, but they must be constrained, typed, bounded, platform-generated, acyclic, deterministic, and linearly or near-linearly verifiable.

## Decision

Media platform graph structures must remain constrained, typed, bounded, acyclic, deterministic, and platform-generated where execution is involved. The platform must not accept arbitrary user/plugin execution graphs or perform global combinatorial optimization by default.

## Consequences

- All execution graphs are platform-generated from typed compilers
- Provider binding uses deterministic eligibility and priority, not global optimization
- Artifact DAG remains deferred and cannot drive default render execution
- Plugins and templates produce intent (declarative definitions), not arbitrary execution DAG nodes
- Any operation requiring exponential search, backtracking, or global optimal solving must be rejected, degraded to dry-run, or marked future work

## Allowed Operations

| Operation | Complexity | Notes |
|-----------|-----------|-------|
| TimelineSnapshot validation | O(n log n) | Flat structure validation |
| TimelineDiff calculation | O(n log n) | Pairwise comparison |
| TimelinePatch application | O(n log n) | Linear operation sequence |
| TimelineMergeConflictAnalysis | O(n log n) | Pairwise operation comparison |
| TimelineMergePreview | O(n log n) | Wraps conflict analysis |
| TimelineBranch pointer switch | O(1) | Pointer change |
| TimelineRollback plan | O(n) | Revision lookup |
| Workflow semantic DAG validation | O(V + E) | Kahn's cycle detection |
| Topological sort on bounded DAG | O(V + E) | Kahn's algorithm |
| Provider eligibility filtering | O(nodes × providers) | Per-node greedy scoring |
| Simple priority-based provider selection | O(nodes × providers) | Deterministic scoring |
| Artifact DAG disabled decision | O(1) | Mode check |
| Artifact DAG dry-run analysis | O(V + E) | Non-blocking |

## Restricted Operations

Each restricted category has explicit boundaries:

| Category | Boundary |
|----------|----------|
| Workflow DAG | Finite step types, known dependencies, acyclic validation, no runtime mutation |
| Template composition | Declarative only, no arbitrary execution graph generation |
| Provider binding | Eligibility + deterministic priority, no global optimization |
| Merge preview | Side-effect-free analysis only |
| Patch application | Pure in-memory, no persistence |
| Artifact DAG | Deferred, default DISABLED, cannot drive default render |
| Render DAG | Platform-generated only, typed node classes |
| Plugin extensions | Declarative intent, not execution graph nodes |

## Forbidden Operations

| Pattern | Reason |
|---------|--------|
| Arbitrary graph dependencies | Unbounded complexity |
| Cycles | Invalid DAG semantics |
| Runtime graph self-modification | Non-deterministic |
| User-submitted execution DAG | Security and correctness risk |
| Plugin-inserted execution DAG nodes | Bypasses platform governance |
| Template-generated arbitrary Render DAG | Unbounded graph rewrite |
| Automatic global provider optimization | NP-hard |
| Automatic optimal partial render region selection | NP-hard |
| Automatic cross-branch merge with execution graph rewrite | Unbounded complexity |
| Arbitrary script nodes | Security risk |
| Recursive workflow/render nodes | Unbounded expansion |
| Unbounded fan-in/fan-out | Resource exhaustion |
| Artifact DAG as required default render path | Deferred optimization |

## Complexity Budgets

| Operation | Maximum Target |
|-----------|---------------|
| Timeline validation | O(n log n) |
| Timeline diff | O(n log n) |
| Timeline patch application | O(n log n) |
| Timeline merge conflict analysis | O(n log n) |
| Timeline merge preview | O(n log n) |
| Workflow DAG validation | O(V + E) |
| Workflow topological sort | O(V + E) |
| Render DAG validation | O(V + E) |
| Render DAG topological sort | O(V + E) |
| Provider eligibility filtering | O(nodes × providers), providers bounded |
| Artifact DAG dry-run | O(V + E) if enabled |

Forbidden complexity patterns: exponential search, unbounded backtracking, global optimal solver, unbounded graph rewrite, recursive runtime expansion, unbounded plugin-generated fan-out, user-defined arbitrary execution graph.

## Provider Binding Rule

Provider binding uses deterministic eligibility and priority:

- Capability eligibility filtering
- Safety policy enforcement
- Provider status check
- Deterministic priority scoring (status + priority + tool availability)
- Explicit feature flags

It must not use global optimal provider assignment, backtracking search, multi-provider global cost minimization, cross-node combinatorial provider optimization, or automatic provider graph partitioning.

## Plugin/Template Rule

Plugins may provide: TemplateDefinition, WorkflowDefinition, validators, schemas, declarative operation definitions.

Plugins must not provide: arbitrary Render DAG nodes, arbitrary shell commands, provider binding overrides, storage internals, execution environment internals, global graph rewrite rules.

TemplateApplication may produce semantic timeline changes, not arbitrary execution graph changes.

## Artifact DAG Deferred Rule

Artifact DAG is a future optimization layer for incremental render, cache reuse, artifact lineage, and partial recomputation. It is not the default render path, not a required provider binding input, not a required render execution input, not a public API, and not required for Timeline Git, rollback, branch switching, or merge preview.

Default mode is DISABLED. DRY_RUN is non-blocking. EXPERIMENTAL is internal-only. REQUIRED is future-only.

## Render DAG Generation Rule

Render DAG must be generated by platform compilers only. Allowed node classes are finite and typed: INGEST, NORMALIZE, APPLY_TEMPLATE, COMPILE_TIMELINE, RENDER_SEGMENT, COMPOSE, PACKAGE, REGISTER_PRODUCT, DELIVER.

Forbidden: user-submitted Render DAG, plugin-submitted Render DAG, provider-submitted global graph rewrite, recursive render node, cycle, unbounded fan-in/fan-out, dynamic graph rewrite during execution, arbitrary shell/script execution node.

## Timeline Operation Rule

Timeline is a structured editing model, not a general graph:

```
Timeline
  ├── ordered tracks
  │     └── ordered clips
  ├── captions ordered by time
  ├── watermarks / overlays
  ├── template applications
  └── restricted workflow references
```

Forbidden: arbitrary clip-to-clip dependency, clip cycles, template cycles, runtime timeline graph rewrites, user-submitted dependency graphs, plugin-generated arbitrary timeline graph.

## Workflow DAG Rule

WorkflowDefinition is a restricted semantic DAG with bounded step types, known dependencies, acyclic validation, topological ordering, deterministic dry-run, and O(V + E) validation.

Forbidden: arbitrary script nodes, recursive nodes, runtime graph mutation, dynamic upstream dependency creation, unbounded fan-out, unbounded fan-in, general computation graph semantics.

## Future Work

These are future-only and must not be implemented now:

- Optimal partial render region selection
- Optimal cache reuse across graph
- Optimal provider combination
- Optimal render segmentation
- Optimal cost/quality/speed multi-objective solver
- Global graph partitioning
- Resource-constrained schedule optimization

If encountered, system must classify as: DEFERRED, UNSUPPORTED, DRY_RUN_ONLY, or MANUAL_REVIEW_REQUIRED.
