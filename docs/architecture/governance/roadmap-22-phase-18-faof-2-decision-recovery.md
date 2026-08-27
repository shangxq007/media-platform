# ROADMAP #22 PHASE 18 — FAOF-2 DECISION RECOVERY

STATUS=FROZEN_CANDIDATE_PENDING_INDEPENDENT_REVIEW
TASK_ID=ROADMAP_22_PHASE_18_FAOF_2_DECISION_RECOVERY
BASE_SHA=bb4c683d11f6fb866c64f5d68ca81be79985bfdb
BASE_TREE=0f13af78021e57efdc5bc474bec46ae63d4fc9e9
PHASE18_IMPLEMENTATION_AUTHORIZATION=NO
PHASE19_AUTHORIZATION=NO
ROADMAP23_AUTHORIZATION=NO

# ROADMAP_22_PHASE_18_FAOF_2_BOUNDED_ARCHITECTURE_CONTRACT_V1

## 1. Purpose and frozen boundary

FAOF-2 is a tooling-only validation of already-owned, pure structural laws. It
creates no canonical domain authority, runtime authority, provider authority,
scheduler, semantic rewrite authority, JVM dependency, or production build
dependency. Production contracts and canonical Java/domain semantics remain
authoritative. This Decision Recovery freezes a candidate law inventory and
implementation/evidence contract only; it implements no Lean4, Coq, or
production code.

The source boundary is `platform-algorithms/README.md`: algorithm consumers
depend on pure domain-agnostic algorithm modules, while those modules must not
depend on domain, infrastructure, Spring, cloud SDKs, or frameworks.

## 2. Authority hierarchy and disagreement policy

Precedence, highest to lowest, is:

1. frozen architecture/canonical contracts and explicit approved corrections;
2. canonical production semantic model;
3. Java pure algorithm implementation;
4. executable Java property, differential, and behavioral-parity tests;
5. Lean4 primary model/specification/proof;
6. Coq complementary model/specification/proof.

`FORMAL_TOOLING_MUST_NOT_DEFINE_SEMANTICS_V1`: Lean4 and Coq validate an
explicit mapping to higher-authority semantics; neither becomes authoritative
merely by proving its own model. A theorem, counterexample, Java test, or
cross-prover disagreement fails closed. The affected implementation stops and
is classified as one of `FORMAL_MODEL_DEFECT`, `JAVA_IMPLEMENTATION_DEFECT`,
`PROPERTY_TEST_DEFECT`, or `FROZEN_ARCHITECTURE_CONTRACT_DEFECT`. The last
classification requires a separately approved architecture correction; no
silent semantic rewrite is permitted.

## 3. Recovered FAOF-1 evidence

Graph kernel authority is
`platform-algorithms/graph/src/main/java/com/example/platform/graph/api/GraphAlgorithms.java`.
It is documented as pure, deterministic, side-effect-free, and mutable-state
free. Existing executable evidence is:

- `platform-algorithms/graph/src/test/java/com/example/platform/graph/PropertyTest.java`;
- `platform-algorithms/graph/src/test/java/com/example/platform/graph/DeterminismTest.java`;
- `platform-algorithms/graph/src/test/java/com/example/platform/graph/DifferentialTest.java`;
- `platform-algorithms/graph/src/test/java/com/example/platform/graph/BehavioralParityTest.java`.

Roadmap #21 structural authority is
`docs/architecture/governance/roadmap-21-execution-graph-planning-contract-v1.md`,
clauses C6-C17, with implementation publication
`roadmap-21-execution-graph-planning-bounded-implementation-publication.md`.
The publication records FAOF-1 as deterministic DFS cycle detection plus
canonical digest mechanics, both pure and without Lean4/Coq runtime dependency.

## 4. Selected formal-law traceability ledger

Every formal theorem must have this tuple:
`FormalLawId; ProductionContractId; JavaSymbol; TestSymbol; LeanTheorem;
CoqTheorem(optional); Assumptions; Status`.

| Formal law ID | Existing contract and Java referent | Existing executable evidence | Lean4 primary | Coq complementary | Disposition |
|---|---|---|---|---|---|
| FAOF2-GRAPH-001 | GraphAlgorithms.topologicalOrder; valid finite DAG complete ordering | PropertyTest P1/P3; DifferentialTest random DAG | `topological_complete` | no duplicate | FORMALIZE_IN_LEAN_PRIMARY |
| FAOF2-GRAPH-002 | GraphAlgorithms.topologicalOrder; every edge precedes successor | PropertyTest P2; DifferentialTest disconnected/diamond | `topological_respects_edges` | no duplicate | FORMALIZE_IN_LEAN_PRIMARY |
| FAOF2-GRAPH-003 | GraphAlgorithms.detectCycles and TopologicalOrderResult.CycleDetected | PropertyTest P4; DifferentialTest direct/multihop cycles; BehavioralParity cycle tests | `cycle_rejects_complete_order` | `cycle_rejects_complete_order` | Lean primary plus Coq complementary slice |
| FAOF2-GRAPH-004 | GraphAlgorithms.topologicalOrder TreeSet ordering; same graph plus natural node order | PropertyTest P5; DeterminismTest insertion/repetition; BehavioralParity stable order | `stable_topological_order` | no duplicate | FORMALIZE_IN_LEAN_PRIMARY |
| FAOF2-GRAPH-005 | GraphAlgorithms.reachableFrom/reachability source inclusion | PropertyTest P6; DifferentialTest diamond reachability | `reachability_reflexive_for_sources` | no duplicate | FORMALIZE_IN_LEAN_PRIMARY |
| FAOF2-GRAPH-006 | GraphAlgorithms.descendantsBounded/ancestorsBounded | PropertyTest P7 | `bounded_traversal_respects_depth` | no duplicate | FORMALIZE_IN_LEAN_PRIMARY |
| FAOF2-PLAN-001 | Roadmap21 C6/C8/C9; LogicalExecutionGraphBuilder | Roadmap21ContractBehaviorTest and Roadmap21PlanningGuardTest | `projection_preserves_node_and_dependency_identity` | `projection_preserves_node_and_dependency_identity` | Lean primary plus Coq complementary slice |
| FAOF2-PLAN-002 | Roadmap21 C14/C15; PhysicalPlannerV1 one logical node to one unit | Roadmap21ContractBehaviorTest and Roadmap21PlanningGuardTest | `one_logical_node_one_physical_unit` | no duplicate | FORMALIZE_IN_LEAN_PRIMARY |
| FAOF2-PLAN-003 | Roadmap21 C12/C13; graph-closed pruning only outside requested extent | Roadmap21GraphClosureTest T-C1..T-C13 | no theorem in initial POC | no theorem | PROPERTY_TEST_ONLY, with finite conformance vectors |
| FAOF2-PLAN-004 | Roadmap21 C16/C17 separate layer digests and deterministic frozen inputs | Roadmap21IoAndCanonicalTest; Roadmap21PlanningGuardTest | `layer_digest_input_separation` | no duplicate | DEFER pending exact canonical encoding model |

## 5. Candidate inventory exclusions and deferrals

G1-G7 map to FAOF2-GRAPH-001 through FAOF2-GRAPH-006 above. G8 purity/non-
mutation is already a Java contract assertion and remains `PROPERTY_TEST_ONLY`;
no independent immutable-input formal model is selected in this POC.

Temporal mapping, exact time/sample-window algebra, partition coverage/overlap,
and full digest serialization are not selected as initial theorems. Existing
contracts preserve their authority, but the recovered code/test evidence is not
a bounded formal model yet. RenderExtent graph-closure/pruning stays
property-test plus shared-vector conformance only. Provider/runtime/resource,
completion, scheduling, optimization, fusion, semantic rewrite, FAOF-3, and
FAOF-4 are `OUT_OF_SCOPE_NOT_AN_EXISTING_FROZEN_LAW` for this phase.

## 6. Model fidelity and conformance

A proof is not proof of Java conformity by itself. Each selected law requires a
versioned finite witness-vector format containing canonical node identities,
edges, source set or depth when relevant, expected result class/order, and the
source Java test symbol. Java tests consume the vector; Lean4 consumes its
mirrored finite graph representation; Coq consumes only its complementary
slice. The vector corpus, theorem names, Java test names, and SHA are recorded
in the traceability ledger. Divergent result is a fail-closed conflict under
clause 2. No general code generator is authorized.

## 7. Proof versus executable-test boundary

Theorem-worthy laws are the selected finite pure graph and pure structural
projection laws in clause 4. API nullability, exception plumbing, typed result
carriers, full RenderExtent arithmetic, serialization implementation detail,
and all runtime/provider behavior remain executable property, differential,
integration, or architecture-guard responsibilities. Differential tests retain
behavioral parity against the historic MEP validator; formal proofs do not
replace them.

## 8. Lean4 primary POC contract

Lean4 is the primary theorem-proving POC. Phase18 implementation may introduce
only an isolated tooling tree `formal/lean4/`, outside Gradle source sets and
without JVM or production runtime dependency. The implementation proposal must
pin an exact Lean4 toolchain version and exact Mathlib revision in a checked-in
tooling manifest; unpinned `latest` is prohibited. Mathlib is permitted only
as a tooling-only pinned dependency. Theorem names must begin `Faof2.` and map
one-to-one to the ledger. CI runs the pinned toolchain command, emits a
machine-readable theorem manifest, and records exact Git SHA/toolchain IDs.

## 9. Coq complementary POC contract

Coq is a complementary portability/friction/consistency POC, not a duplicate
of the Lean corpus. Its isolated tooling tree is `formal/coq/`; it has no
production or JVM dependency. It receives only FAOF2-GRAPH-003 and
FAOF2-PLAN-001 unless implementation review approves a smaller equivalent
slice. The implementation proposal must pin an exact Coq version and package
lock/manifest. CI emits theorem names, counts, command, exact SHA, and pinned
version. No proof implementation is added by this Decision Recovery.

## 10. Tooling dependency and proof-hole policy

Dependency direction is production semantics -> executable tests and
traceability fixtures -> formal tooling evidence. Formal trees must not be a
Java domain module, provider module, Worker Fabric runtime module, or
application dependency. Formal tooling changes require a future
`FORMAL_VERIFICATION_IMPACT` classifier category and its dedicated formal CI
job; this Decision Recovery is governance impact only and does not modify the
classifier.

PASS permits no `sorry`, `admit`, `admitted`, `axiom`, `opaque` escape hatch,
or unsafe proof bypass in selected theorem files, except an explicitly listed
trusted standard-library axiom whose identity and rationale is machine-recorded
and independently approved. Any unproved placeholder makes formal validation
FAIL, never PASS.

## 11. CI, reproducibility, and evidence

For Decision Recovery, the change-impact classifier must select governance and
architecture validation, not backend/frontend/bootJar/image publication. Later
formal-tool impact runs only the dedicated formal job plus relevant
fixture/conformance checks unless another changed input independently requires
application CI. Unknown remains full-CI fail-closed.

A formal POC PASS evidence set must contain exact Git SHA; Lean and Coq pinned
versions; package lock/manifest hashes; theorem names and counts; exact command
lines; machine-readable result; CI job identity; witness-vector manifest; zero
proof-hole result; Java conformance/differential result; and, where Coq applies,
cross-prover agreement. Retention of CI artifacts is allowed only as evidence,
not semantic authority.

## 12. Hard stop boundary

FAOF-2 proves existing structural/legal laws only. It must not introduce a
global optimizer, cross-provider cost optimization, distributed scheduling,
provider scoring/selection, semantic fusion, semantic rewrite engine, runtime
Constraint Kernel expansion, executable provider selection, or Roadmap #23
`WHICH_IS_BEST` logic. Optimization never creates semantic compatibility.
FAOF-3 and Roadmap #23 remain not started; FAOF-4 remains deferred.

## 13. Implementation and closure contract

No Phase18 implementation begins until independent ChatGPT review accepts this
frozen candidate. Before Phase18 can close, the implementation must provide:

1. accepted frozen Phase18 architecture contract and law ledger;
2. complete theorem-to-code traceability ledger;
3. pinned Lean4 primary POC result;
4. pinned Coq complementary POC result;
5. executable witness-vector/conformance and differential evidence;
6. dedicated formal CI gate and formal-impact classifier policy;
7. proof of zero production-runtime dependency;
8. proof of zero Phase19/provider/runtime and FAOF-3/#23 scope drift;
9. candidate freeze, FCV, and independent ChatGPT review.

CURRENT_DECISION=FROZEN_CANDIDATE_PENDING_INDEPENDENT_REVIEW
NEXT_ACTION=CHATGPT_ROADMAP_22_PHASE_18_FAOF_2_DECISION_RECOVERY_FINAL_REVIEW
