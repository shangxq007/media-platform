# ROADMAP #20 — Graph Backend Adoption Review

Status: PASS
Date: 2026-08-19
Branch: agent/roadmap20-renderplan-decision-recovery
Base: 07de009205e0ee50cad06e5a324ce18f5c46b10d

Question this review answers (per ROADMAP_20 spec §4.2):
"What minimal graph mechanics does Roadmap #20 actually require?"
NOT "what graph library is popular".

---

## 1. What Roadmap #20 actually requires

| Requirement | Needed for #20 first slice? | Minimum mechanics |
|---|---|---|
| typed node identity | YES | typed Comparable node id (deterministic ordering) |
| typed edges | YES | typed edge record + dependency semantics (data/temporal distinctions only where used) |
| DAG invariant | YES | validation: all endpoints exist, no self-edge, acyclicity |
| cycle detection | YES | deterministic algorithm |
| topological order | YES | deterministic algorithm |
| deterministic traversal | YES | stable ordering (typed identity), no HashMap iteration dependence |
| dependency queries | YES | predecessors/successors + typed dependency lookup |
| immutable graph snapshot | YES | immutable node/edge collections; record snapshot |
| graph validation | YES | structural validation + render-semantic validation (fail closed) |
| graph hashing/fingerprinting | YES | canonical serialization + SHA-256 (reuse repo digest pattern) |
| subgraph extraction | HOOK | bounded descendants/ancestors queries exist in kernel; full subgraph extraction deferred |
| future partitioning hooks | HOOK | stable node identity + typed dependencies make partitioning a later pure function |
| future incremental recomputation hooks | HOOK | same identity stability (C22) |

No requirement needs a graph database, an external graph library, or
distributed graph infrastructure.

## 2. Options evaluated

1. plain immutable typed graph structures (hand-rolled) — rejected as the
   DEFAULT: repository already owns a tested, deterministic graph kernel;
   hand-rolling a third+ implementation would repeat the render-module
   pattern of four ad-hoc topological sorts.
2. JGraphT — rejected: heavy dependency, generic flexibility beyond need;
   repo convention favors minimal zero-risk dependencies; determinism and
   immutability must be enforced by us either way.
3. Guava graph — rejected: same external-dependency cost; API less suited to
   immutable snapshots; no cycle/topo algorithms.
4. another mature Java graph library — rejected for the same reasons.
5. custom bounded DAG mechanics — partially ADOPTED as the thin typed layer
   (RenderGraph) ON TOP of the kernel, NOT as a reimplementation of
   topology algorithms.
6. existing repository graph utilities — ADOPTED: platform-algorithms:graph
   (com.example.platform.graph) already provides the mechanics;
   media-execution-plan-module already delegates to it (precedent).
7. database-backed graph mechanics — rejected: RenderGraph is derived
   execution state, rebuildable per request; PostgreSQL remains the
   canonical relational store for authored state only (C32).
8. no external graph backend yet — this is the essence of the decision:
   the "backend" is the in-repo kernel; no external backend at all.

## 3. Repository evidence

- platform-algorithms:graph exists and is deterministic by contract:
  "All methods are deterministic: same graph + same natural node order →
  same result. No side effects, no mutable state."
  (GraphAlgorithms package; Kahn's in-degree cycle detection; sealed
  TopologicalOrderResult Ordered/CycleDetected; CycleDetectionResult;
  ReachabilityResult; DirectedGraphView/BidirectionalGraphView with
  nodes/successors/predecessors/roots/sinks; GraphViews factories
  directedFromAdjacency/bidirectionalFromAdjacency/directedFromEdges;
  descendantsBounded/ancestorsBounded.)
- media-execution-plan-module: MediaExecutionPlanValidator delegates cycle
  detection + topological order to GraphAlgorithms; MediaExecutionGraphProjection
  builds kernel views — in-repo precedent for typed-graph-on-kernel.
- workflow-module: UserWorkflowDefinitionValidator consumes the kernel.
- render-module does NOT use the kernel and carries four+ ad-hoc
  implementations (RenderPlanIr.topologicalSort DFS, DagExecutionEngine,
  PipelineDagTopology, RenderPlanPolicyGuard DFS, WorkflowCycleDetector Kahn,
  WorkflowStepOrderResolver) — evidence of exactly the duplication the
  canonical graph must avoid.
- Revision DAG mechanics are a DIFFERENT concern: timeline_revision_parent
  ordered edge table + RevisionGraphService (readParents/isAncestor/
  findBestMergeBase) is revision-history authority and stays where it is;
  RenderGraph never becomes the revision DAG.

## 4. Decision

GRAPH_BACKEND_DECISION =
ADOPT_REPOSITORY_GRAPH_KERNEL (platform-algorithms:graph) AS THE MECHANICAL
BACKEND; RenderGraph is a THIN typed projection layer (typed node identity +
typed dependencies + fail-closed validation + deterministic canonicalization
+ fingerprint) over DirectedGraphView; NO external graph library, NO graph
database, NO new graph module.

GRAPH_BACKEND_RATIONALE =
The kernel already provides every mechanical primitive the first slice
needs (typed generics, deterministic cycle detection, topological order,
dependency queries, immutability via factory-built views, sealed results)
and is already consumed by sibling modules, so adopting it costs zero new
dependency risk and consolidates the graph-mechanics authority. JGraphT /
Guava / graph DBs add flexibility the derived, rebuildable RenderGraph does
not need and would make canonicalization/determinism OUR problem anyway
with a bigger surface. Revision-DAG mechanics are explicitly excluded:
they remain RevisionGraphService's concern over the ordered parent-edge
table. The kernel's determinism contract keys on the node type's natural
order (toString-based default comparator), so RenderNodeId must be a record
(or otherwise deterministic Comparable with stable toString) — a bounded
constraint the identity contract (C6) already satisfies; a custom-comparator
overload is a future kernel extension hook, not a #20 requirement.

## 5. Explicit rejections (spec §4.2)

- Neo4j — rejected (no semantic authority; derived state; no distributed need)
- Apache AGE — rejected (same)
- generic graph DB — rejected (same)
- JGit — rejected (already replaced by PostgreSQL revision model; never a
  render-graph authority; spec §38)
- Jujutsu — rejected (no repository presence, no requirement)
- Dolt — rejected (no requirement)

Revision DAG mechanics and Render DAG mechanics are different concerns:
revision DAG = durable history authority (timeline_revision_parent +
RevisionGraphService); Render DAG = derived execution dependency projection,
rebuildable from an immutable revision.

## 6. Kernel-vs-layer responsibilities

Kernel (platform-algorithms:graph) owns: adjacency mechanics, cycle
detection, topological order, reachability, deterministic traversal given
node order. It is domain-ignored; it never becomes semantic authority.

RenderGraph layer owns: typed RenderNodeId identity, typed dependency
variants, node/edge construction from RenderPlan, structural + semantic
validation (fail closed), deterministic canonicalization (C8), fingerprint
(C7), source-state consistency (C4), dependency queries over typed edges.
It adds semantics; it does not reimplement mechanics.

Validation precedent: MediaExecutionPlanValidator (module-local validation
delegating to the kernel) and TimelineMediaSemanticsValidator (O(V+E) typed
validation with stable error codes) are the in-repo patterns to follow.

## 7. Verdict

ROADMAP20_GRAPH_BACKEND_ADOPTION_REVIEW = PASS
GRAPH_BACKEND_DECISION = ADOPT_REPOSITORY_GRAPH_KERNEL
GRAPH_BACKEND_RATIONALE = see §4.
