# ROADMAP #22 PHASE 18 — FAOF-2 DECISION RECOVERY CORRECTION 1

STATUS=FROZEN_CANDIDATE_PENDING_INDEPENDENT_REVIEW
TASK_ID=ROADMAP_22_PHASE_18_FAOF_2_DECISION_RECOVERY_CORRECTION_1
PRIOR_CANDIDATE_SHA=96f0a28a1a257708ae22e45329a4f3c48f6c137a
PRIOR_CANDIDATE_TREE=5ff61ffdb80e9dca1130ba11218605d28b11108a
CANONICAL_MAIN_REQUIRED=bb4c683d11f6fb866c64f5d68ca81be79985bfdb
CANONICAL_MAIN_INTEGRATION=FORBIDDEN
PHASE18_IMPLEMENTATION_AUTHORIZATION=NO
PHASE19_AUTHORIZATION=NO
EXTERNAL_LIBRARY_INTEGRATION=NO

# ROADMAP_22_PHASE_18_FAOF_2_DECISION_RECOVERY_CORRECTION_1_CONTRACT_V1

## 1. Correction purpose

The prior candidate correctly kept formal tooling subordinate to production
semantics, but its graph laws assumed a deterministic generic topological order
without establishing a language-neutral node-order authority. Repository reality
shows `GraphAlgorithms.defaultComparator()` uses `Object.toString()` and
`topologicalOrder()` uses a `TreeSet` with that comparator, while
`DirectedGraphView<N>` requires only stable `equals`/`hashCode`. Distinct nodes
can therefore compare equal and be silently collapsed. This correction freezes
platform-owned graph semantics and corrects only affected law assumptions. It
does not formalize accidental Java collection behavior.

## 2. Adopted authority decisions

- `GRAPH_ALGORITHM_SEMANTICS_ARE_PLATFORM_OWNED_MECHANICS_MAY_BE_LIBRARY_BACKED_V1`
- `GRAPH_ORDER_CONTRACT_IS_EXPLICIT_AND_LANGUAGE_NEUTRAL_V1`
- `OBJECT_TOSTRING_IS_NOT_GRAPH_NODE_IDENTITY_OR_ORDER_AUTHORITY_V1`
- `EXTERNAL_GRAPH_ALGORITHM_ENGINE_IS_REPLACEABLE_MECHANICS_NOT_SEMANTIC_AUTHORITY_V1`
- `FORMAL_PROOF_PROVES_PLATFORM_LAWS_NOT_LIBRARY_INTERNALS_V1`
- `FORMAL_PROOF_PROVES_WHAT_CONFORMANCE_TESTS_VALIDATE_HOW_V1`

Platform owns node identity, ordering requirements, dependency and cycle
semantics, result/failure semantics, formal laws, and conformance evidence.
Algorithm backend code owns traversal/data-structure mechanics only.

## 3. Graph consumer inventory

| Production consumer | Node type | Use | Ordering/digest observability |
|---|---|---|---|
| `render-module/.../graph/RenderGraphBuilder` | `RenderNodeId` | cycle and topological validation | graph fingerprint independently canonicalizes node IDs and edges; topological output is projection/diagnostic mechanics |
| `media-execution-plan-module/.../ExecutableTaskMembership` | `String` step ID | membership positions from dependency order | position is persisted semantic membership order; string ID is the existing canonical key |
| `media-execution-plan-module/.../ProviderBoundExecutableTaskGraph` | `ExecutableTaskId` | canonical task dependency order | externally returned and consumed by `RuntimeClosedLoopOrchestrator`; task ID is SHA-256 semantic identity |
| `media-execution-plan-module/.../ExecutionReuseKeyDeriver` | `ExecutableTaskId` | predecessor-before-consumer derivation | execution reuse keys are canonical derived evidence; deterministic order is required |
| `workflow-module/.../UserWorkflowDefinitionValidator` | `String` node ID | cycle/connectivity/reachability validation | no topological output consumed; identity and displayed diagnostics use node IDs |

No production consumer uses an arbitrary object node type as canonical graph
identity. Consumer modules are `render-module`, `media-execution-plan-module`,
and `workflow-module`; each declares dependency on `:platform-algorithms:graph`.

## 4. Node identity and ordering ledger

| Node type | Identity / equality evidence | Explicit order evidence | Contract disposition |
|---|---|---|---|
| `RenderNodeId` | immutable record; deterministic revision-context-free canonical value | `Comparable` by `value`; `toString()==value` is explicitly bounded to this type | explicit canonical ID order required where deterministic order is consumed |
| `ExecutableTaskId` | immutable SHA-256 semantic ID record | `Comparable` by lowercase SHA-256 value | explicit canonical ID order required for ETG/reuse derivation |
| workflow node `String` | workflow node ID / edge endpoints | existing validator sorts strings for diagnostics | canonical string-ID order sufficient where ordered diagnostics are observable |
| membership step-ID `String` | `ExecutionStepId.value()` converted to string topology | membership validation uses `canonicalPosition` then step value | canonical string-ID order sufficient |
| generic `N` in `DirectedGraphView<N>` | only stable equals/hashCode promised | no canonical key, no comparator, no toString uniqueness | generic topological order may only be validity order unless caller supplies platform order |

`Object.toString`, identity hash, memory identity, unordered container iteration,
wall clock, random ordering UUID, mutable runtime state, and provider/runtime
state are prohibited canonical tie-breakers.

## 5. Corrected language-neutral graph order contract

`DETERMINISTIC_GRAPH_ORDER_IS_EXPLICIT_CONTRACT_NOT_LANGUAGE_CONTAINER_BEHAVIOR_V1`.

For a consumer requiring deterministic topological tie-breaking, its graph
projection must supply a platform-defined strict total order over distinct
canonical node identities, or an equivalent frozen canonical stable ordering
key. A Java `Comparator` implements this contract; it never defines it.

Generic `DirectedGraphView<N>` does not itself assert a universal semantic
order. A consumer requiring only dependency validity may accept any successful
topological permutation; a consumer whose output is externally observable or
feeds canonical derivation must supply the explicit platform order.

## 6. Corrected formal laws

`FAOF2-GRAPH-001`: for every valid finite DAG whose distinct semantic nodes have
stable canonical identities, successful topological order retains every node
exactly once and respects all dependencies. If canonical tie-breaking is
required, the theorem assumes a frozen strict total order over those identities.

`FAOF2-GRAPH-002`: every edge `u -> v` is respected by every successful
topological result. It is independent of tie-breaking.

`FAOF2-GRAPH-003`: cyclic input cannot yield a successful complete topological
result and must align with the platform result/failure contract.

`FAOF2-GRAPH-004`: semantically identical finite DAGs under the same frozen
platform node order yield the same ordered result independently of construction
order, enumeration order, runtime object identity, or Java container behavior.
It applies only to consumers for which canonical deterministic order is
required.

`DISTINCT_NODE_EQUAL_TOSTRING_RED_WITNESS` is mandatory for future conformance:
two unequal nodes with equal `toString()` must either both survive through an
explicit canonical order or be rejected by a separately frozen input identity
contract. Silent collapse is forbidden.

## 7. Current custom backend and JGraphT reuse decision

`CURRENT_CUSTOM_GRAPH_ALGORITHMS` provides Kahn/TreeSet topological order, cycle
detection, reachability, and bounded traversals. It is not semantic authority.

`PLATFORM_GRAPH_ALGORITHM_BACKEND_REUSE_DECISION_V1`:
`JGRAPHT_PRIMARY_POC_CANDIDATE_PENDING_BOUNDED_CONFORMANCE_EVALUATION`.

Repository/API feasibility assessment:

| Criterion | Finding |
|---|---|
| Directed DAG/topological ordering | fit: JGraphT documents a topological iterator and user-supplied queue mechanism for tie-breaking; platform must supply its own canonical comparator/queue policy |
| Cycle detection | fit subject to mapping library detection to platform result/failure types |
| Reachability/transitive traversal | fit as backend traversal mechanics; platform retains result semantics |
| Bounded ancestor/descendant | bounded composition is feasible, but must be proven against platform depth law; no library behavior is assumed authoritative |
| Type encapsulation | fit: library types can remain inside `platform-algorithms:graph`; no external types leak to domains/APIs/formal contracts |
| Failure/result preservation | fit only through platform-owned adapters/result types; no library exception/result leakage |
| Determinism | conditional: only with explicit platform ordering, never library/hash iteration default |
| Build/JDK | POC only; future exact JGraphT version/JDK25 compatibility must be pinned and tested before adoption |
| License | candidate is dual-licensed EPL-2.0 or LGPL-2.1-or-later; future dependency adoption requires repository license review |
| Replaceability/testability | fit through internal encapsulation and backend conformance vectors; no generic backend SPI is authorized now |

No JGraphT dependency, adapter, Gradle edit, or custom algorithm deletion occurs in
this correction. If a future POC passes conformance, preferred clean-forward
migration is: migrate consumers, preserve platform contracts/results, turn tests
into backend conformance, prove zero custom-mechanics callers, then delete
redundant custom generic mechanics. No triple custom/library/wrapper authority.

## 8. Existing test backend-conformance classification

| Existing evidence | Classification |
|---|---|
| `PropertyTest` P1-P7/P9 | `REUSABLE_BACKEND_CONFORMANCE` and `FORMAL_WITNESS_SOURCE` after explicit identity/order fixtures |
| `DeterminismTest` | `REUSABLE_BACKEND_CONFORMANCE` after replacing implicit toString assumption with supplied canonical order |
| `DifferentialTest` | `REUSABLE_BACKEND_CONFORMANCE` for topology/cycle/reachability; not proof of external implementation parity |
| `BehavioralParityTest` | `LEGACY_PARITY_ONLY`; it asserts self-contained invariants, not live differential comparison with historic MEP validator |
| `GraphAlgorithmsTest`, `DirectedGraphViewTest`, `BidirectionalGraphViewTest`, `ScaleTest` | `JAVA_IMPLEMENTATION_SPECIFIC` or `REQUIRES_REDESIGN` as applicable |
| Roadmap21 graph closure/planning/digest tests | `REUSABLE_BACKEND_CONFORMANCE` only at platform projection boundary; formal witness source where finite fixtures map exactly |

## 9. Formal-model and conformance correction

Lean4/Coq prove finite platform laws, not TreeSet, PriorityQueue, JGraphT,
JVM comparator, Java collection, Kahn, or DFS internals. The same platform-law
witness vectors feed formal models and Java backend conformance. Formal model
assumptions must be no stronger than production semantic contract assumptions.

## 10. Performance and optimization evidence foundation

The following are persisted as governance directions only:
`PERFORMANCE_AND_OPTIMIZATION_EVIDENCE_FOUNDATION_V1`,
`MEASURE_BEFORE_OPTIMIZE_V1`,
`OPTIMIZATION_OPERATES_OVER_PROVEN_FEASIBLE_SPACE_V1`,
`EQUIVALENCE_IS_TYPED_AND_EVIDENCE_GRADED_V1`,
`UNKNOWN_EQUIVALENCE_FAILS_CLOSED_V1`,
`FORMAL_LAWS_AUTHORIZE_SEMANTIC_REWRITE_V1`,
`DATA_DRIVEN_COST_MODEL_NEVER_OVERRIDES_SEMANTIC_LEGALITY_V1`,
`CONSERVATIVE_SEMANTIC_ENVELOPE_PROGRESSIVE_OPTIMIZATION_V1`,
`SEMANTIC_SEARCH_SPACE_IS_ARCHITECTURE_GOVERNED_V1`,
`OPTIMIZATION_EXPLORES_WITHIN_BUT_NEVER_REDEFINES_SEMANTIC_SPACE_V1`, and
`NEW_EVIDENCE_MAY_TRIGGER_EXPLICIT_SEMANTIC_SPACE_EXPANSION_V1`.

They do not authorize FAOF-3, FAOF-4, Roadmap #23, optimizer, solver, e-graph,
or semantic rewrite implementation.

## 11. CI correction and historical failure

Semantic scope is governance correction. Actual CI impact is derived by the
platform classifier, not manually assigned. Because this correction updates an
existing `scripts/ci/**` governance-contract test, actual impact is
`CI_INFRASTRUCTURE`, full CI is required, and runtime image publication remains
false because no runtime/container/build input changes.

Prior candidate CI `33048517380` failed at
`RateLimitFilterTest.shouldExtractClientIpFromXForwardedFor` (570 tests, one
failure, 22 skipped). This correction does not repair or classify it as flaky.
If the same test fails on this exact correction SHA, stop with
`RECURRING_UNRELATED_STANDARD_CI_VALIDATION_FAILURE`.

## 12. State and stop boundary

Phase18 remains started only as Decision Recovery. Its state is
`CORRECTION_1_FROZEN_CANDIDATE_PENDING_INDEPENDENT_REVIEW`; implementation is
unauthorized. Phase19 and Roadmap23 remain not started. Canonical main remains
unchanged pending independent review.

NEXT_ACTION=CHATGPT_ROADMAP_22_PHASE_18_FAOF_2_DECISION_RECOVERY_CORRECTION_1_FINAL_REVIEW
