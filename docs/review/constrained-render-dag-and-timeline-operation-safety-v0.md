# Constrained Render DAG and Timeline Operation Safety v0

## 1. Purpose

Define and enforce architecture rules that keep Timeline operations, Workflow DAGs, Render Execution DAGs, Provider Binding, and Artifact DAG from becoming a general graph optimization or NP-hard scheduling system.

## 2. Inventory Summary

The platform has 15 graph-like structures across 8 categories. See `/tmp/p2a1-constrained-dag-inventory.md` for full inventory.

Key findings:
- 8 structures classified SAFE
- 5 structures classified RESTRICTED
- 2 structures classified DEFERRED
- 0 structures classified FORBIDDEN (forbidden patterns are rules, not structures)

## 3. Risk Classification

See `/tmp/p2a1-graph-risk-classification.md` for full classification.

Summary:
- **SAFE**: TimelineSnapshot, ProviderBindingPlan, RenderExecutionPlan, ExecutionEnvironment, ProductDependency, MergePreview, WorkflowDryRunPlanner, TimelineDiff/Patch
- **RESTRICTED**: WorkflowDefinition, TemplateApplication, CompositeTemplate, Plugin manifest, Capability Graph
- **DEFERRED**: Artifact DAG, TimelineVersionGraph

## 4. Why General Graph Optimization Is Dangerous

If arbitrary dependencies, arbitrary provider choices, arbitrary plugin-generated execution nodes, arbitrary resource constraints, and global optimization are allowed, the platform can drift into hard computational problems:

- Resource-constrained project scheduling (NP-hard)
- Global provider assignment optimization (NP-hard)
- Graph partitioning (NP-hard)
- Optimal cache reuse selection (NP-complete)
- Optimal partial render region selection (NP-hard)
- Multi-objective scheduling (NP-hard)

The platform must avoid these by constraining graph structures to be typed, bounded, acyclic, deterministic, and platform-generated.

## 5. NP-Hard / NP-Complete Risk Areas

| Area | Risk | Mitigation |
|------|------|-----------|
| Provider assignment | NP-hard if global | Per-node greedy scoring |
| Partial render region | NP-hard if optimal | Deferred, not implemented |
| Cache reuse selection | NP-complete if optimal | Deferred, hash-based lookup |
| Resource scheduling | NP-hard if constrained | Not implemented |
| Graph partitioning | NP-hard if optimal | Not applicable, separate graph types |

## 6. Timeline Structure Constraints

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

Constraints:
- No arbitrary clip-to-clip dependency
- No clip cycles
- No template cycles
- No runtime timeline graph rewrites
- No user-submitted dependency graphs
- No plugin-generated arbitrary timeline graph

## 7. Workflow DAG Constraints

WorkflowDefinition is a restricted semantic DAG:

Allowed:
- Finite step types (12 WorkflowStepType values)
- Known dependencies
- Acyclic validation (Kahn's algorithm O(V+E))
- Topological ordering (deterministic)
- Deterministic dry-run

Forbidden:
- Arbitrary script nodes
- Recursive nodes
- Runtime graph mutation
- Dynamic upstream dependency creation
- Unbounded fan-out
- Unbounded fan-in
- General computation graph semantics

## 8. Render Execution DAG Constraints

Render DAG must be platform-generated and typed:

Allowed node classes (finite, typed):
- INGEST, NORMALIZE, APPLY_TEMPLATE, COMPILE_TIMELINE
- RENDER_SEGMENT, COMPOSE, PACKAGE, REGISTER_PRODUCT, DELIVER

Forbidden:
- User-submitted Render DAG
- Plugin-submitted Render DAG
- Provider-submitted global graph rewrite
- Recursive render node
- Cycle
- Unbounded fan-in/fan-out
- Dynamic graph rewrite during execution
- Arbitrary shell/script execution node

14 safety constraints enforced by RenderPlanPolicyGuard.

## 9. Provider Binding Constraints

Provider binding is deterministic filtering, not global optimization:

Uses:
- Capability eligibility
- Safety policy
- Provider status
- Deterministic priority (status score + priority score + tool penalty)
- Explicit feature flags

Must not use:
- Global optimal provider assignment
- Backtracking search
- Multi-provider global cost minimization
- Cross-node combinatorial provider optimization
- Automatic provider graph partitioning

Complexity: O(nodes × providers), providers bounded.

## 10. Artifact DAG Constraints

Artifact DAG is **indefinitely deferred** (P2A.2) and retained only as an extension layer:

- Default mode: DISABLED
- DRY_RUN: non-blocking analysis only
- EXPERIMENTAL: internal-only
- REQUIRED: future-only, not enabled
- Not on current roadmap; not a dependency for any current work item

Boundaries:
- Must not drive ProviderBindingPlan by default
- Must not drive RenderExecutionPlan by default
- Must not be required render path
- Must not expose public API

## 11. Plugin/Template Constraints

Plugins may provide: TemplateDefinition, WorkflowDefinition, validators, schemas, declarative operation definitions.

Plugins must not provide: arbitrary Render DAG nodes, arbitrary shell commands, provider binding overrides, storage internals, execution environment internals, global graph rewrite rules.

TemplateApplication may produce semantic timeline changes, not arbitrary execution graph changes.

## 12. Complexity Budgets

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
| Provider eligibility filtering | O(nodes × providers) |
| Artifact DAG dry-run | O(V + E) |

## 13. Allowed Operations

- TimelineSnapshot validation
- TimelineDiff calculation
- TimelinePatch application
- TimelineMergeConflictAnalysis
- TimelineMergePreview
- TimelineBranch pointer switch
- TimelineRollback plan
- Workflow semantic DAG validation
- Topological sort on bounded DAG
- Provider eligibility filtering
- Simple priority-based provider selection
- Artifact DAG disabled decision
- Artifact DAG dry-run analysis if non-blocking

## 14. Restricted Operations

- Workflow DAG (finite step types, known deps, acyclic)
- Template composition (declarative only)
- Provider binding (eligibility + deterministic priority)
- Merge preview (side-effect-free analysis only)
- Patch application (pure in-memory)
- Artifact DAG (deferred, default DISABLED)
- Render DAG (platform-generated only)
- Plugin extensions (declarative intent)

## 15. Forbidden Operations

- Arbitrary graph dependencies
- Cycles
- Runtime graph self-modification
- User-submitted execution DAG
- Plugin-inserted execution DAG nodes
- Template-generated arbitrary Render DAG
- Automatic global provider optimization
- Automatic optimal partial render region selection
- Automatic cross-branch merge with execution graph rewrite
- Arbitrary script nodes
- Recursive workflow/render nodes
- Unbounded fan-in/fan-out
- Artifact DAG as required default render path

## 16. Validation Strategy

- Workflow: Kahn's algorithm for cycle detection, deterministic topological sort
- Render DAG: Acyclicity check (constraint #12), deterministic step IDs (constraint #13)
- Provider binding: Per-node greedy scoring, mode-based eligibility
- Timeline: Diff calculator deterministic ordering, patch application input immutability
- Artifact DAG: Mode-based gate (DISABLED default)

## 17. What Is Intentionally Not Implemented

- General graph solver
- Global optimization engine
- Resource-constrained scheduling
- Optimal partial render region selection
- Optimal cache reuse across graph
- Optimal provider combination
- Automatic provider graph partitioning
- User-programmable execution graph system
- Plugin-generated arbitrary execution DAG

## 18. Forbidden Visual Capabilities (P2R.0)

P2R.0 added explicit forbidden visual capabilities:
- Arbitrary FFmpeg filtergraph
- Arbitrary shader
- Arbitrary script effect
- Arbitrary OFX plugin
- Natron node graph
- Blender compositor graph
- Remotion component execution
- User-defined Render DAG
- Plugin-inserted Render node
- Provider-specific raw command
- Shader transition
- Arbitrary transition plugin
- User-defined transition graph
- Provider-specific transition graph

See [Visual Capability Contract](visual-capability-contract-effects-transitions-v0.md).

## 19. Follow-up Tasks

- Monitor Workflow DAG complexity as step types grow
- Enforce fan-out/fan-in bounds when Workflow engine is implemented
- Validate Render DAG node type set remains finite
- Review provider binding if multi-provider PRODUCTION mode is enabled
- Review Artifact DAG boundaries when REQUIRED mode is considered

> P2V.5 introduced pure Timeline Branch and Commit Semantics. Branch, commit, pointer, checkout, rollback, and branch-switch plans are side-effect-free domain concepts. They do not persist Timeline Git history, render media, create Products, call StorageRuntime/ProductRuntime, invoke Artifact DAG, or implement merge/conflict resolution.

> P2V.6 introduced pure Timeline Checkout, Rollback, and Branch Switch application services. They produce safe planning/result objects for editing context changes and non-destructive rollback intent. They do not persist Timeline Git history, render media, create Products, call StorageRuntime/ProductRuntime, invoke Artifact DAG, or implement merge/conflict resolution.

> P2V.7 introduced a pure Timeline Non-conflicting Merge Plan. It classifies operations from base/ours/theirs merge preview into safe-to-apply-later, manual-review conflict, unsupported, blocked, and duplicate buckets. It does not apply patches, create a merged snapshot, persist Timeline Git history, render media, create Products, call StorageRuntime/ProductRuntime, invoke Artifact DAG, use provider binding, or implement conflict resolution.

> P2R.0 introduced a platform-owned Visual Capability Contract for effects and transitions. Effects and transitions are represented as bounded semantic capabilities with explicit status, provider consistency, fallback behavior, and safety rules. This does not implement effect/transition rendering, does not expose raw provider commands or arbitrary filtergraphs, does not execute Remotion, does not use Artifact DAG, and does not add public APIs.

> P2R.1 introduced a pure FFmpeg Baseline Effect Plan. It maps semantic timeline effect references to bounded internal FFmpeg baseline effect operations, with typed parameter validation and safety boundaries. It does not execute FFmpeg, does not generate public raw filtergraphs, does not create RenderJob/Product, does not call StorageRuntime/ProductRuntime, does not use OpenCue, and does not use Artifact DAG.
