# ROADMAP #20 — Render Architecture Research Review

Status: PASS
Date: 2026-08-19 (all sources accessed on this date)
Branch: agent/roadmap20-renderplan-decision-recovery
Base: 07de009205e0ee50cad06e5a324ce18f5c46b10d

Purpose (task §4.3): validate render-graph architecture patterns, DAG planning
boundaries, incremental render concepts, materialization patterns, caching
identity, node dependency semantics, and execution/resource separation against
high-signal external systems — WITHOUT cargo-culting any external architecture.

Method: web_search + direct fetch (curl + html2text) of primary/authoritative
pages; every claim classified as SOURCE-DERIVED FACT / ARCHITECTURAL INFERENCE /
MEDIA-PLATFORM DECISION; pages that could not be fetched are marked UNVERIFIED
and no content was invented for them. Full evidence table with URLs:
/tmp/ROADMAP20_EVIDENCE/09-research-review-raw.md (uncommitted).

---

## 1. Evidence summary (task §46 format)

| SOURCE | DATE_ACCESSED | CONCEPT | WHY_RELEVANT | WHAT_WE_ADOPT | WHAT_WE_REJECT | WHY |
|---|---|---|---|---|---|---|
| Blender Developer Docs — Dependency Graph (DEG) | 2026-08-19 | DEG updates only what depends on a modified value; evaluation runs on a copy-on-write copy of authored DNA data; render engines read only the evaluated copy | Canonical "evaluate scene dependency graph for render": authored immutable, evaluated derived, incremental invalidation | Separate immutable authored Timeline (our "DNA") from an evaluated/derived plan; render only the projection, never mutate the revision | Blender's per-window graph ownership and in-memory CoW object graph as our representation | We need a serializable provider-neutral plan, not live C++ object graphs |
| Blender code.blender.org — Dependency graph proposal (2016) | 2026-08-19 | DEG holds relations AND evaluated state; per-engine data attaches in the evaluation context; CoW-aware, dedup-friendly | Makes explicit that mature DEGs couple topology with evaluated state and attach per-engine requirements without changing topology | RenderPlan = WHAT kept separate from RenderGraph = topology | Fusing evaluated state into the graph identity | Serializable cross-provider plan needs topology and payload decoupled |
| OpenUSD — Hydra 2.0 Getting Started Guide | 2026-08-19 | Two core abstractions: scene abstraction/transformation pipeline and renderer abstraction/execution pipeline; pull-based scene index (GetPrim/GetChildPrimPaths) + observer change notices (PrimsAdded/Removed/Dirtied with HdDataSourceLocator) + filtering scene indices chained into a scene-index graph; HdRenderer replaces HdRenderDelegate | Strongest industry example of separating data/plan from execution and change-tracking topology | Pull-based view of the render plan + observer-style dirty/invalidation keyed by path locators (future incremental re-render) | C++ plugin/DS machinery and time-sampled GetValue(shutterOffset) API shape | Java service keeps time as a plan parameter, not a per-datasource method |
| NVIDIA Learn OpenUSD — Hydra lesson | 2026-08-19 | Hydra bridges scene description and rendering backend: scene delegate (data), render index (change tracking/management), render delegate (visualization to final image); "decouples the scene data from the rendering backend" | Independent confirmation of the 3-way split: data / change-tracking / backend | Role mapping plan=scene data, graph=change-tracking topology, provider=backend delegate | nothing specific | Separation is industry-standard and reusable |
| Vulkan Reference — VkRenderPass | 2026-08-19 | A render pass = collection of attachments, subpasses, and dependencies between subpasses, describing attachment usage over the passes (superseded by dynamic rendering in 1.4) | Lowest-level definition of a render pass as a LOCAL group; the primitive render graphs compose | Model provider-level "pass" as attachments + usage; keep it a leaf concept, never our canonical plan unit | Baking Vulkan render-pass objects into canonical structures | Violates no-provider-commands-in-canonical-structures |
| Granite — "Render graphs and Vulkan: a deep dive" (H.-K. Arntzen) | 2026-08-19 | Render graphs solve manual sync via global frame knowledge; passes declared, resources declared with usage classes (write-only / read-write / read-only); bake pipeline = Validate → topo flatten (reverse, dedup) → reorder (optimization) → logical→physical resource assignment → subpass merging → barriers; RMW abstraction exists "to avoid cycles" | Most complete public frame-graph reference: declarative passes, derived topology, usage-typed resources, acyclic-by-construction, bake-then-execute | Declare resource usage class per node to derive edges (future); separate explicit bake/compile (topo sort + validate) from execute; RMW-style aliasing keeps graph acyclic | In-process mutable C++ graph with callbacks and GPU stages | Not serializable/portable |
| zeux.io — "Writing an efficient Vulkan renderer" (A. Kapoulkine) | 2026-08-19 | Resource-allocation layer under a render graph: memory suballocation, descriptor management, pipeline barriers, render passes = backend responsibility | Shows which concerns belong to the backend vs the graph | Allocation/barriers/descriptor strategy = provider-internal, invisible to canonical plan | Any leak of allocation/descriptor/barrier detail into canonical structures | Confirms resource allocation is legitimately backend-owned |
| gpuweb/gpuweb issue #64 — "The case for passes" | 2026-08-19 | WebGPU inserts synchronization automatically BETWEEN passes; mutable "Usage" state per resource; "constant usage across a pass" requirement; passes are the sync unit | API-level analog of usage-typed resources in a render graph | Resource usage as the declared contract per node, dependencies derivable from usage (future) | nothing | Declared-usage dependency derivation is portable across backends |
| wgpu CommandEncoder docs | 2026-08-19 | "Wgpu does not have a global view of the frame when recording command buffers"; barrier command buffers inserted BETWEEN submitted buffers, not optimally batched | Documents the cost of lacking a global graph — the exact problem a render graph solves | Global RenderGraph as the single place deriving ordering/transitions | Per-node/submission barrier bookkeeping as source of truth | Graph-first derivation beats bottom-up command recording |
| Unreal Engine — Rendering Dependency Graph (official docs) | 2026-08-19 | RDG = "graph-based scheduling system designed to perform whole-frame optimization"; execution deferred until the whole frame is recorded; "graph is compiled and executed in dependency-sorted order"; automatic async-compute scheduling, memory aliasing, early barriers, "rich validation during pass setup" | Authoritative statement of record-then-compile-then-execute and graph-driven validation | Deferred execution: collect all nodes, validate, topo-sort, then execute | GPU-specific scheduling heuristics (async compute queues) in the canonical layer | The deferred model is provider-neutral; the heuristics are not |
| UE RDG deep documentation (community mirror, R. Loggini) | 2026-08-19 | 3 phases: Setup (declare passes + resources), Compile (autonomous, "non-programmable": cull unreferenced, compute lifetimes, allocate, build transition graph), Execute (run lambdas with real GPU resources; resources were "opaque abstract references" until then); resources transient (aliasable) vs external; dependency levels with a fence per level | Full detail on compile as DERIVED and non-programmable (determinism) and on pass/resource declaration surface | Explicit Setup→Compile→Execute phases; compile = pure deterministic non-authorable derivation (cull, lifetime, topo order) | Shader-parameter-struct resource typing as a Java concept | Phase separation is the durable insight |
| OpenFX Programming Guide (readthedocs) | 2026-08-19 | Host/plugin API: host loads plugins and provides environment; plugin declares clips (inputs/outputs) and implements per-instance render action; unique pluginIdentifier (domain:plugin) for disambiguation/serialization, versioned; host owns node graph, threading, tiling, resource materialization | Canonical DCC node-graph-as-DAG contract: host owns topology + materialization; plugin owns only per-node per-region computation | Provider nodes = declared inputs/outputs + pure per-node render function; platform owns DAG, ordering, caching, materialization; stable string node identifiers for serialization | C suite/property ABI (typed Java provider SPI instead) | Host/plugin separation is exactly platform/provider split |
| Bazel Glossary (action graph / action key) | 2026-08-19 | Action = command with metadata (args, action key, env, declared inputs/outputs); action key = cache key "computed based on action metadata" → deterministic per-action caching/invalidation; action graph built in analysis phase from target graph, used in execution phase; Skyframe = parallel functional incremental evaluation; pluggable spawn strategies (local/remote/dynamic/sandboxed/docker) | Cleanest content-addressable answer to node identity + plan-vs-graph with deterministic caching | Content-addressable node identity (canonical stable hash over provider-neutral inputs/params) as cache/invalidation key; authored plan (target graph) vs derived execution graph (action graph) | Skyframe-style live evaluation framework now | Identity + invalidation semantics are what we need |
| Ninja build manual | 2026-08-19 | Barest dependency-graph description; separate generator program makes decisions up front; build statements = graph, rule statements = how to generate along an edge; outputs implicitly depend on the generating command line; deps log | Minimal executor: graph fully separated from commands and from construction; deterministic rebuild via command-line dependency | Deliberately minimal canonical graph; generation (plan→graph) as a separate deterministic step; node outputs implicitly depend on their parameter hash | mtime-based invalidation as primary identity | Not content-deterministic for cloud/render farms |
| Apache Airflow — DAGs core concepts | 2026-08-19 | Dag declared with dag_id; tasks with task_id; dependencies via >>/<<; Dag instantiates into Dag Run, tasks into Task Instances; logical date + data interval; backfill | Mature scheduler's two-level identity: structural/nominal + per-run instance identity | Two-level identity: stable logical node id (plan revision + node id) + run/render-instance id (revision + render id + frame) | Schedule-time ("logical date") as the determinism axis | Ours is authored-revision-driven, not calendar-driven |
| Ray DAG API docs | 2026-08-19 | .bind() on remote functions generates IR nodes "statically holding the computation graph"; each IR node resolved at execution "with respect to their topological order"; dag_node.execute() is the root; non-reachable nodes ignored | Clean lazy-DAG model: build IR declaratively, resolve by topo order at execution, reachable-from-root pruning | Build graph lazily as immutable IR; execute by topological order from explicit root; prune unreachable nodes | Python .bind() fluent authoring as our API | We author from a revision, not code |

## 2. Honest unverified entries (no content invented)

- Frostbite "FrameGraph: Extensible Rendering Architecture in Frostbite"
  (Y. O'Donnell, GDC 2017): primary text not directly fetchable (EA 404, GDC
  vault login-gated). Its core concepts are corroborated by the Granite deep
  dive (explicitly inspired by it) and Unreal RDG docs (which cite O'Donnell);
  no primary-text claims made.
- Foundry Nuke node graph: only positioning docs fetchable; the DAG dependency
  model for DCC compositing is covered via OpenFX (which Nuke hosts).
- Prefect task caching: page JS-rendered/unfetchable; no cache-key claims made.
- bgfx: located but not deep-read (immediate-mode renderer, not a render-graph
  system); deprioritized.

## 3. Research questions answered

RQ1 Plan vs graph separation: YES in all mature systems — Blender data masks
(per-engine requirements over a stable DEG) [SOURCE-DERIVED FACT]; UE RDG
Setup(what) vs Compile(derived topology) [SOURCE-DERIVED FACT]; Bazel target
graph vs action graph [SOURCE-DERIVED FACT]; Hydra scene index vs render index
vs renderer [SOURCE-DERIVED FACT].
→ Contract: RenderPlan and RenderGraph are separate artifacts (C3).

RQ2 Node identity for caching/incremental: two families — content-addressable
(Bazel action key [SOURCE-DERIVED FACT]) and structural/nominal (Ninja output
path + command line [SOURCE-DERIVED FACT]; OpenFX pluginIdentifier [SOURCE-DERIVED
FACT]; Airflow dag_id+task_id with run instance [SOURCE-DERIVED FACT]; Hydra
SdfPath + HdDataSourceLocator [SOURCE-DERIVED FACT]).
→ Contract: deterministic semantic node identity (C6) + plan fingerprint (C7) +
cache key = content-derived (C21) + execution-attempt identity separate (C6).

RQ3 Execution vs resource allocation/backend: Hydra render delegate owns
allocation [SOURCE-DERIVED FACT]; Granite bake produces logical→physical
mapping, pass callbacks execute [SOURCE-DERIVED FACT]; UE RDG resources are
opaque until Execute [SOURCE-DERIVED FACT]; OpenFX host owns materialization,
plugin owns per-region computation [SOURCE-DERIVED FACT]; Bazel spawn
strategies plug the same graph into different executors [SOURCE-DERIVED FACT].
→ Contract: provider neutrality (C18), typed execution-requirement hooks (C19),
capability-not-provider requirements (C17).

RQ4 Determinism of construction/traversal: Granite deterministic topo flatten
with reordering as an OPTIMIZATION over the canonical order [SOURCE-DERIVED
FACT]; UE RDG compile is autonomous/"non-programmable" with culling [SOURCE-DERIVED
FACT]; Bazel action key deterministic function of metadata [SOURCE-DERIVED FACT];
Ninja up-front decisions + command-line dependency [SOURCE-DERIVED FACT]; Ray
topological resolution + root reachability pruning [SOURCE-DERIVED FACT]; Airflow
deterministic run identity via schedule + logical date [SOURCE-DERIVED FACT].
→ Contract: canonicalization rules (C8), plan determinism (C20), root-reachability
pruning noted as a compile-phase option (C16/C23).

RQ5 Minimal graph API surface (synthesized): add node/pass; declare resource
usage per node; add edge/dependency; validate (acyclic + usage sanity); topo
order; query deps/traverse. NOT required in canonical layer: barrier/transition
emission, allocation/aliasing, queue scheduling [ARCHITECTURAL INFERENCE].
→ Contract: kernel (platform-algorithms:graph) already provides cycle detection,
topological order, reachability, dependency queries; typed node identity +
validation + canonicalization live in the thin RenderGraph layer (C30).

## 4. What we adopt vs reject — consolidated

ADOPT:
1. Plan ≠ graph (C3) — Blender/UE/Bazel/Hydra.
2. Content-addressable node identity over provider-neutral inputs (C6, C7, C21)
   — Bazel action key; Ninja command-line dependency.
3. Two-level identity: logical node id + run-instance id (C6) — Airflow.
4. Build→Compile→Execute phase discipline; compile = pure, deterministic,
   non-authorable (validate + topo + cull) (C16, C23) — UE RDG, Granite, Ray.
5. Providers = leaf executors, not topology authors (C18, C19) — OpenFX
   host/plugin; Hydra render delegate; Bazel spawn strategies.
6. Determinism = pure function of immutable inputs + stable ordering + topo
   resolution; no mtime/calendar/thread-order inputs (C8, C20) — Granite, UE,
   Bazel, Ninja, Ray.
7. Declared resource-usage per node as a FUTURE edge-derivation refinement
   (usage classes read/write/RMW) — Granite, WebGPU. NOT in the V1 slice: the
   contract's typed dependency variants (C5) are more explicit for the first
   slice; usage-class derivation is a documented future optimization hook.

REJECT:
1. Live in-memory object graphs / CoW C++ machinery as our representation
   (Blender DEG).
2. Fusing evaluated state into graph identity (Blender DEG coupling).
3. Provider commands/handles in canonical structures (Vulkan render passes,
   wgpu barriers, CUDA identifiers).
4. GPU scheduling heuristics (async compute, queue management) in the canonical
   layer (UE RDG).
5. mtime/calendar-driven invalidation (Ninja mtime; Airflow logical date).
6. Skyframe-style live evaluation framework (Bazel) for #20.
7. Shader-parameter-struct-level resource typing as a Java concept (UE RDG).

## 5. Verdict

ROADMAP20_RENDER_ARCHITECTURE_RESEARCH_REVIEW = PASS
- Research validates the contract's separation, identity, determinism,
  provider-neutrality, and minimal-mechanics choices.
- No external architecture is adopted wholesale; every adoption maps to a
  frozen contract decision (C-numbers above); every rejection is justified.
- SOURCE-DERIVED FACT / ARCHITECTURAL INFERENCE / MEDIA-PLATFORM DECISION are
  distinguished throughout; unverifiable sources are marked, never invented.
