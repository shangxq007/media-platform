# MEDIA_PLATFORM_INTEGRATED_ARCHITECTURE_ROADMAP_V2

**STATUS = ADOPTED (baseline consolidation, FINAL MECHANICAL LEDGER
CANONICALIZATION applied, pending independent final ledger review)**

| Field | Value |
|---|---|
| STATUS | ADOPTED |
| ROADMAP_MODEL | CUMULATIVE_NOT_REPLACEMENT |
| EXISTING_28_MILESTONE_NUMBERS | PRESERVED |
| BASE_SHA | 19db3aead6c27e6ddf1e7d3faab62b287a48cef0 |
| BASE_TREE | 027ab1c6249fbb4727b9979fcaae0e5cd5779907 |
| SUPERSEDES | none (cumulative baseline; older decisions remain authoritative unless explicitly superseded) |
| AUTHORITY | ONE_CANONICAL_CORE_MANY_ENTITLED_PRODUCT_SURFACES_V1 |
| ROADMAP_20 | CLOSED / INTEGRATED at main 19db3aea |
| REVIEWED_PREDECESSOR | 102e5298ec2e5510a666579847099dd9260ea03b |
| CORRECTION_TYPE | FINAL MECHANICAL LEDGER CANONICALIZATION (F1-F4) |
| ARV2_FINAL_MECHANICAL_LEDGER_CANONICALIZATION | 32/32 PASS |

This document is the current top-level architecture roadmap authority. It
consolidates repository-adopted architecture decisions up through Roadmap #20
closure. It is a GOVERNANCE baseline, not an implementation plan. Every
integrated decision is classified ADD / REFINE / SUPERSEDE / DEFER, and
architecture status is kept separate from implementation status.

Evidence base: `docs/architecture/governance/` repository records (Roadmap
#13-#20 final publications, Checkpoint A publications, core architecture
rebalancing contracts, capability lifecycle, operation model, semantic
relationship / temporal mapping foundations, Roadmap #20 Option B Effect
authority chain, final independent acceptance evidence correction).

---

## 1. Top-level platform model

**ONE_CANONICAL_CORE_MANY_ENTITLED_PRODUCT_SURFACES_V1 — ADOPTED / FROZEN**

The platform is ONE canonical semantic core with many entitled product
surfaces. The Canonical Core owns semantics; product surfaces do NOT fork
canonical semantics. All surfaces share the same Canonical Domain, Capability,
Operation, Constraint, Planning and Execution contracts.

Differences belong ABOVE the core: entitlement, role/workspace policy, quota,
capability visibility, provider selection, recipe/template defaults,
product-surface exposure.

No FreeTimeline / ProTimeline / EnterpriseTimeline variants exist or are
planned. No plan-specific domain semantics.

## 2. Top-level conceptual pipeline

Integrated architecture pipeline (future target):

```
Canonical Intent
        ↓
Canonical Constraints
        ↓
Canonical Semantics
        ↓
Operation Model
        ↓
Algebraic Contracts
        ↓
Semantic Analysis
        ↓
Constraint Evaluation + Formal Evidence
        ↓
Legal Plan Space
        ↓
Semantic Rewrite
        ↓
Cost Optimizer
        ↓
Physical Planning
        ↓
Execution
```

Current implementation frontier after Roadmap #20 (C1 corrected — both
authoring/command and render/planning sides shown):

```
AUTHORED / COMMAND SIDE:
Canonical Semantics (IMPLEMENTED)
→ Operation Model V1 (IMPLEMENTED / CLOSED)
→ OperationPlan Transaction V1 (IMPLEMENTED / CLOSED)
→ Revision Command Model V1 (IMPLEMENTED / CLOSED)
→ canonical revision state

RENDER / PLANNING SIDE:
Verified Canonical Revision State
→ Logical RenderPlan (IMPLEMENTED, #20)
→ RenderGraph (IMPLEMENTED, #20)
→ [FUTURE LAYERS BEGIN — not implemented by #20]
```

Roadmap #20 does NOT implement the lower future layers (Physical Planner,
Cost Optimizer, Semantic Rewrite, Formal Kernel, Constraint Kernel runtime,
GraphQL, Canvas, Workflow runtime, provider/device scheduler). Those are
future/cross-cutting layers explicitly NOT owned by #20 (§54).

## 3. Architecture status vs implementation status

For every foundation below, ARCHITECTURE_STATUS and IMPLEMENTATION_STATUS are
reported separately. "Frozen/adopted" never implies "implemented".

### 3.1 Normalized status vocabulary (V2-F2/F3 — strict three-axis model)

Three SEMANTICALLY INDEPENDENT status axes. Label spellings MAY overlap
across axes (e.g. NOT_STARTED exists in both IMPLEMENTATION_STATUS and
MILESTONE_STATUS), but their meanings are axis-qualified and never shared as
a semantic value.

ARCHITECTURE_STATUS (design authority):
- PROPOSED / ADOPTED / FROZEN / SUPERSEDED / DEFERRED
- CLOSED is NOT a valid architecture status.

IMPLEMENTATION_STATUS (runtime reality):
- NOT_STARTED / FOUNDATION_ONLY / PARTIALLY_IMPLEMENTED / IMPLEMENTED
- CLOSED is NOT a valid implementation status.
- No parenthetical qualifiers (e.g. "IMPLEMENTED (governance)") are valid
  tokens; qualifiers belong in ARCH_LAYER / SOURCE_DOCUMENTS / prose only.

MILESTONE_STATUS (governance finalization):
- NOT_APPLICABLE / NOT_STARTED / IN_PROGRESS / CLOSED / FUTURE
- CLOSED appears ONLY here.

Conventions:
- For a completed bounded foundation: ARCH = FROZEN, IMPL = IMPLEMENTED,
  MILESTONE = CLOSED.
- FOUNDATION_ONLY != IMPLEMENTED; IMPLEMENTED != "architecture adopted".
- Recommended model for Operation Model / OperationPlan Transaction /
  Revision Command: ARCH_STATUS = FROZEN, IMPL_STATUS = IMPLEMENTED,
  MILESTONE_STATUS = CLOSED (see §6.2).

---

## 4. Layer 1 — Canonical Semantics

### 4.1 Integrated authority principles

| Decision ID | Status | Meaning |
|---|---|---|
| EXTERNAL_REVISION_BACKEND_FIRST_V1 | ADOPTED / IMPLEMENTED | revision mechanics via external backend (JGit) |
| DOMAIN_AUTHORITY_INTERNAL_FROM_DAY_ONE_V1 | ADOPTED / IMPLEMENTED | domain owns semantics, not the backend |
| TIMELINE_IS_COMPOSITION_REVISION_AND_MERGE_AUTHORITY_V1 | ADOPTED / IMPLEMENTED | Timeline is the media composition authority |
| TIMELINE_IS_SOURCE_AGNOSTIC_COMPOSITION_AUTHORITY_V1 | ADOPTED / IMPLEMENTED | composition is source-agnostic |
| MEDIA_STREAM_SOURCE_IS_ONE_TIMELINE_SOURCE_KIND_V1 | ADOPTED / IMPLEMENTED | MediaStream is one Timeline source kind |
| TIMELINE_SOURCE_BINDINGS_PIN_IMMUTABLE_SOURCE_SEMANTICS_V1 | ADOPTED / IMPLEMENTED | source bindings pin immutable source semantics |
| NO_UNIVERSAL_ASSET_GOD_OBJECT_V1 | ADOPTED / IMPLEMENTED | typed asset/artifact identity separation |
| TIMELINE_REVISION_PINS_IMMUTABLE_EFFECT_SEMANTICS_V1 | ADOPTED / IMPLEMENTED | revision pins exact Effect semantics |
| EFFECT_SEMANTIC_SNAPSHOT_IS_PRIMARY_TYPED_AUTHORITY_WIRE_EFFECT_IS_DERIVED_PROJECTION_V1 | ADOPTED / IMPLEMENTED | typed Effect snapshot is authority |
| EFFECT_SNAPSHOT_BINDING_IDENTITY_IS_DISTINCT_FROM_SEMANTIC_COMMITMENT_V1 | ADOPTED / IMPLEMENTED | binding identity ≠ semantic commitment |
| REVISION_GRAPH_IDENTITY_IS_DISTINCT_FROM_SEMANTIC_CONTENT_IDENTITY_V1 | ADOPTED / IMPLEMENTED | graph identity ≠ content identity |
| CANONICAL_SCHEMA_EVOLUTION_V1 | ADOPTED / IMPLEMENTED | canonical schema evolution rules |
| SEMANTIC_RELATIONSHIP_SELECTION_FOUNDATION_V1 | ADOPTED / IMPLEMENTED | typed semantic relationships |
| TEMPORAL_MAPPING_FOUNDATION_V1 | ADOPTED / IMPLEMENTED | exact rational temporal mapping |

### 4.2 Revision backend authority (REFINE — explicit)

JGit is the current revision-graph mechanics backend. JGit does NOT own:
Timeline semantics, canonical schema, semantic equality, diff, merge, or
Effect semantics. media-platform owns the canonical model, canonical
serialization, semantic equality, diff, merge, and migration semantics.
Backend replaceability is preserved. media-platform does NOT plan to rewrite
JGit; replacement happens only if concrete architecture/performance/
operational constraints justify it.

### 4.3 Effect authority — final post-#20 state (REFINE — Option B integrated)

| Decision ID | Status |
|---|---|
| EFFECT_SEMANTIC_SNAPSHOT_PINNED_BY_TIMELINE_REVISION_V1 | ADOPTED / IMPLEMENTED |
| EFFECT_DEFINITION_SEMANTICS_ARE_EXACTLY_PINNED_IN_EFFECT_SNAPSHOT_V1 | ADOPTED / IMPLEMENTED |
| EFFECT_DEFINITION_VERSION_CONTENT_IS_IMMUTABLE_V1 | ADOPTED / IMPLEMENTED |
| EFFECT_STACK_ORDER_IS_AUTHORED_ORDERED_SEMANTICS_V1 | ADOPTED / IMPLEMENTED |
| RENDER_CONSUMES_VERIFIED_EFFECT_SNAPSHOT_NOT_CALLER_EFFECT_LISTS_V1 | ADOPTED / IMPLEMENTED |
| EFFECT_SNAPSHOT_MINTING_IS_DOMAIN_AUTHORITY_ONLY_V1 | ADOPTED / IMPLEMENTED |
| NO_LEGACY_EFFECT_AUTHORITY_AFTER_ROADMAP20_V1 | ADOPTED / IMPLEMENTED |
| ONE_CANONICAL_EFFECT_AUTHORITY_PATH_V1 | ADOPTED / IMPLEMENTED |

Roadmap #20 exact implementation: `08696bd22457026a00a613c9e830db6e7bbf7c5d`.
Detailed correction history lives in the #20 publication chain; the main
baseline links/traces instead of repeating it.

### 4.4 Clean-forward policy

- NO_HISTORICAL_PRODUCT_COMPATIBILITY_OBLIGATION = TRUE
- GIT_HISTORY_APPEND_FORWARD_DOMAIN_MODEL_CLEAN_FORWARD_V1
- NO_UNSHIPPED_COMPATIBILITY_PATHS_V1
- DELETE_OBSOLETE_AUTHORITY_INSTEAD_OF_DEPRECATING_IT_V1

Git/governance history is immutable evidence. Unshipped runtime/domain
compatibility is NOT a product requirement. Future compatibility becomes
mandatory only for actually published canonical formats/contracts.

---

## 5. Layer 2 — Capability

### 5.1 Capability identity (REFINE — Roadmap #16)

CapabilityId: typed, namespaced, stable. Platform reserved namespaces:
`media.*`, `timeline.*`, `audio.*`, `video.*`, `subtitle.*`, `render.*`.
Vendor namespaces: reverse-DNS (`com.vendor.*`). No hardcoded TLD allowlists.
CapabilityImplementation has stable implementation identity and version; one
plugin may provide multiple implementations of one capability.

### 5.2 Capability authority model (REFINE)

- CapabilityRegistry / CapabilityRegistryPort = capability-facing architecture
  authority.
- PluginRegistryPort = plugin package/container concern.
- Future capability consumers must not depend directly on PluginRegistryPort.
- Separate axes: Capability Contract Lifecycle vs Capability Runtime
  Availability vs Plugin Lifecycle. Do not collapse them.

### 5.3 Effective capability model (ADD — integrated)

```
effective access
= capability exists
∩ runtime available
∩ entitlement
∩ policy permission
∩ quota
```

Agents/MCP/Skills/Recipes receive a principal-filtered effective capability
view. No surface may bypass entitlement/policy by querying raw providers
directly.

### 5.4 Capability future hooks (DEFER / FOUNDATION_ONLY)

- CAPABILITY_CONFORMANCE_INTROSPECTION_V1 — ADOPTED / FOUNDATION_ONLY
- SEMANTIC_EXTENSION_CONTRACT_V1 — ADOPTED / FOUNDATION_ONLY
- TRUST_PERMISSION_SANDBOX_V1 — ADOPTED / FOUNDATION_ONLY

---

## 6. Layer 3 — Operation

### 6.1 Operation model (REFINE)

- ONE_SEMANTIC_OPERATION_MODEL_MANY_FRONTENDS_V1 — FROZEN / IMPLEMENTED (V1)
- DSL_IS_A_TYPED_COMPOSITION_LANGUAGE_NOT_A_DOMAIN_AUTHORITY_V1 — FROZEN
- IR_BEFORE_SYNTAX_V1 — FROZEN / IMPLEMENTED (V1 IR)

UI, Java/API, GraphQL, MCP, Agents, Skills, Recipes, DSL, Canvas all lower to
one canonical Operation semantic model.

### 6.2 Implemented Operation foundations (C1 correction — repository truth)

The following Operation-layer foundations are CLOSED / IMPLEMENTED in
repository governance, NOT future work. Correct status model:

**OPERATION_MODEL_FOUNDATION_V1**
- ARCHITECTURE_STATUS = FROZEN
- IMPLEMENTATION_STATUS = IMPLEMENTED
- MILESTONE_STATUS = CLOSED
- Source: `docs/architecture/governance/operation-model-foundation-v1.md` (CLOSED)
- Repository truth: typed OperationDefinition; typed OperationRequest;
  OperationRequestResolver; OperationInstance; typed OperationParameters;
  deterministic parameter digest; OperationBatch; typed target contracts;
  15 frozen V1 operation definitions; OperationErrorCode typed vocabulary.

**OPERATION_PLAN_TRANSACTION_MODEL_V1**
- ARCHITECTURE_STATUS = FROZEN
- IMPLEMENTATION_STATUS = IMPLEMENTED
- MILESTONE_STATUS = CLOSED
- Source: `docs/architecture/governance/operation-plan-transaction-model-v1.md`
  + `operation-plan-final-evidence-verification-v1.md` (both CLOSED)
- Repository truth: OperationPlanner implemented (15 frozen ops incl. delete
  sync/group consequences); immutable OperationPlan; OperationPlanDigest;
  OperationPlanPreview; AuthorizationDecision; ApplyContext;
  TargetRevisionRef; typed PlannedChange; PostgreSQL-enforced CAS
  (conditional UPDATE on timeline_revision_ref); durable idempotency;
  semantic NO_OP; atomic application transaction (jOOQ: CAS + revision
  insert + durable result); real PostgreSQL concurrency/integrity evidence.

**REVISION_COMMAND_MODEL_V1**
- ARCHITECTURE_STATUS = FROZEN
- IMPLEMENTATION_STATUS = IMPLEMENTED
- MILESTONE_STATUS = CLOSED
- Source: `docs/architecture/governance/revision-command-model-v1.md` (CLOSED)
- Repository truth: CREATE_REF, DELETE_REF, RESTORE, MERGE; RevisionCommandPlan;
  RevisionCommandPlanDigest; RevisionGraphService (merge-base: unique /
  AMBIGUOUS_MERGE_BASE / NO_COMMON_ANCESTOR); RevisionCommandApplyService;
  ordered parent edges; project-safe revision-number allocation (counter);
  command-domain separation (apply_command.command_domain);
  RevisionCommandConcurrencyIT 12 PASS (real PostgreSQL 16).

The canonical transaction model:

```
request → resolve → plan → validate → preview → authorize → atomic apply → new revision / NO_OP
```

is already MATERIALLY IMPLEMENTED for the bounded V1 OperationPlan model
(IMPLEMENTED_BOUNDED_V1). Distinguish:

- IMPLEMENTED_BOUNDED_V1 = the Operation Model / OperationPlan Transaction /
  Revision Command foundations above (closed, real PG evidence)
- FUTURE_GENERALIZATION / FUTURE_SURFACE_COVERAGE = widening the same boundary
  to every frontend (GraphQL/Canvas/Agents/DSL), full constraint-kernel
  integration, and unified cross-layer mutation — NOT "runtime from zero"

### 6.3 Current implementation frontier (C1 correction)

The frontier no longer jumps from Canonical Semantics directly to RenderPlan.
Both truths are represented:

AUTHORED / COMMAND SIDE:
```
Canonical Semantics
→ Operation Model V1 (IMPLEMENTED)
→ OperationPlan Transaction V1 (IMPLEMENTED)
→ Revision Command Model V1 (IMPLEMENTED)
→ canonical revision state
```

RENDER / PLANNING SIDE:
```
Verified Canonical Revision State
→ Logical RenderPlan (IMPLEMENTED, #20)
→ RenderGraph (IMPLEMENTED, #20)
→ [future physical / optimization layers — NOT STARTED]
```

RenderPlan is not assumed to be downstream of every Operation request in all
cases; the authored/command side and the render/planning side are separate but
connected paths.

### 6.4 Operation IR (REFINE — future)

Typed Operation IR / OperationPlan must be frozen BEFORE human DSL syntax
becomes authoritative. Operation IR must be: language-neutral at the contract
level, typed, deterministic where required, capability-aware, scope-aware,
compatible with preview/authorization, independent from persistence,
independent from FFmpeg/provider command syntax. DSL does not own domain
semantics. (V1 IR exists; full DSL-surface coverage is future.)

---

## 7. Layer 4 — Constraint / Evaluation / Evidence / Formal Methods

### 7.1 Constraint / evaluation architecture (ADD — architecture adopted)

- UNIFIED_CONSTRAINT_AND_EVALUATION_ARCHITECTURE_V1 — ADOPTED / FOUNDATION_ONLY
- ONE_CANONICAL_CONSTRAINT_MODEL_MANY_EVALUATION_SURFACES_V1 — ADOPTED
- CONSTRAINTS_HAVE_ONE_CANONICAL_MEANING_MANY_ENFORCEMENT_PROJECTIONS_V1 — ADOPTED
- DESIGN_IS_AN_EVALUABLE_CONTRACT_BUNDLE_V1 — ADOPTED
- FEATURE_IS_A_MULTI_LAYER_CONTRACT_REALIZATION_V1 — ADOPTED
- COLLABORATION_CONTRACTS_ARE_PROJECTIONS_OF_ARCHITECTURE_CONSTRAINTS_V1 — ADOPTED
- LANGUAGE_IMPLEMENTATIONS_ARE_CONTRACT_PROJECTIONS_NOT_SEMANTIC_AUTHORITIES_V1 — ADOPTED

### 7.2 Canonical constraint kernel (ADD — FOUNDATION_ONLY / NOT_STARTED)

- CANONICAL_CONSTRAINT_KERNEL_V1 — ADOPTED / FOUNDATION_ONLY
- CONSTRAINT_PROJECTION_FOUNDATION_V1 — ADOPTED / FOUNDATION_ONLY

Minimal canonical concepts: ConstraintId, Version, Kind, Authority, Scope,
Invariant, Precondition, Postcondition, Dependency, AllowedTransformation,
ForbiddenTransformation, FailurePolicy, EvidenceRequirement,
EvaluationResult.

Non-goals: NOT a generic rule engine, NOT a workflow engine, NOT a policy god
object.

### 7.3 Evidence model (ADD — architecture adopted)

- EVIDENCE_MODEL_FOUNDATION_V1 — ADOPTED / FOUNDATION_ONLY
- EVIDENCE_IS_TYPED_AND_TRACEABLE_V1 — ADOPTED
- REQUIRED_EVIDENCE_DEPENDS_ON_CONSTRAINT_KIND_AND_RISK_V1 — ADOPTED
- EVIDENCE_STRENGTH_LADDER_V1 — ADOPTED
- CONSTRAINT_TRACEABILITY_GRAPH_V1 — ADOPTED
- UNKNOWN_IS_FIRST_CLASS_NOT_IMPLICIT_PASS_V1 — ADOPTED

Evidence states: PASS / FAIL / UNKNOWN / NA / DEFERRED. UNKNOWN != PASS.

### 7.4 Existing gates as early projections (REFINE — explicit)

Existing mechanisms (JUnit tests, property tests, architecture guards,
Gradle gates, drift gates, Modulith, FCV, transaction tests, ownership tests,
corruption tests, canonical acceptance matrices, independent review,
governance publication) are EARLY PROJECTIONS of the future
Constraint/Evidence architecture. They are source material for future
consolidation; they do NOT yet form a unified Constraint Kernel.

### 7.5 Formal methods (ADD — roadmap adopted)

- FORMAL_METHODS_PROGRESSIVE_ADOPTION_ROADMAP_V1 — ADOPTED / NOT_STARTED
- FORMALIZATION_DOES_NOT_INTERRUPT_ROADMAP_20_V1 — satisfied historically
- FORMALIZATION_HOOKS_MUST_PRECEDE_SEMANTIC_REWRITE_AND_COST_OPTIMIZATION_V1 — ADOPTED
- LEAN_FIRST_FORMAL_SEMANTIC_KERNEL_V1 — ADOPTED / NOT_STARTED
- FORMAL_TOOLS_PROVIDE_EVIDENCE_NOT_RUNTIME_DOMAIN_AUTHORITY_V1 — ADOPTED
- PRODUCTION_IMPLEMENTATIONS_REMAIN_POLYGLOT_FORMAL_SEMANTICS_REMAIN_SINGLE_V1 — ADOPTED
- FORMAL_SEMANTIC_COMPLEXITY_IS_CONCENTRATED_IN_PLATFORM_KERNEL_V1 — ADOPTED

Formal adoption stages:

```
F0 language-neutral law/evidence/equivalence hooks — NEXT-ERA FOUNDATION / NOT FULLY IMPLEMENTED
F1 Lean Semantic Kernel POC — NOT STARTED
F2 executable Lean oracle + differential CI — NOT STARTED
F3 Law Registry + formal evidence gate — NOT STARTED
F4 LegalPlanSpace proof boundary before cost optimization — NOT STARTED
```

No Lean implementation exists. No claim otherwise is made.

### 7.6 Formalization readiness (REFINE)

FORMALIZATION_READINESS_CHECK_V1: FR1-FR12 (authority explicit, semantics
explicit, deterministic, immutable-or-mutation-semantics explicit, observable
behavior explicit, equality explicit, identity/equality separated, dependency
explicit, ordering explicit, UNKNOWN/failure behavior explicit, provider
concerns excluded, canonical representation deterministic).

Meaning: MODEL_CAN_BE_FORMALIZED_WITHOUT_REDESIGN. Roadmap #20 RenderPlan is
recorded as formalization-READY, not formally proved.

---

## 8. Layer 5 — Planning

### 8.1 Roadmap #20 planning authority (REFINE)

```
Authored Canonical State
        ↓
Verified Semantic Snapshot
        ↓
Logical RenderPlan
        ↓
RenderGraph
        ↓
future Physical Planning
        ↓
Execution
```

RenderPlan is DERIVED, DETERMINISTIC, PROVIDER-NEUTRAL, LOGICAL planning state
— NOT authored state, NOT canonical authoring authority, NOT revision
authority, NOT Effect authority, NOT provider authority.

Logical WHAT closes: immutable source pins, decode ranges, Effect semantics,
Audio semantics, Text/font semantics, output requirements, capability
requirements, dependency graph, execution/materialization requirements.

### 8.2 Algebraic semantic optimization (ADD — architecture adopted)

- ROADMAP_ALGEBRAIC_SEMANTIC_OPTIMIZATION_AMENDMENT_V1 — ADOPTED
- OPERATION_ALGEBRAIC_CONTRACT_V1 — ADOPTED / FOUNDATION_ONLY
- SEMANTIC_ANALYSIS_FOUNDATION_V1 — ADOPTED / FOUNDATION_ONLY
- SEMANTIC_REWRITE_SYSTEM_V1 — ADOPTED / NOT_STARTED
- COST_OPTIMIZATION_ONLY_OVER_PROVEN_LEGAL_PLAN_SPACE_V1 — ADOPTED
- UNKNOWN_SEMANTIC_PROPERTIES_DEFAULT_TO_NO_OPTIMIZATION_V1 — ADOPTED

No reorder without equivalence evidence.

### 8.3 Equivalence levels (ADD — explicit)

SEMANTIC_EXACT / BIT_EXACT / NUMERICALLY_EQUIVALENT / PERCEPTUALLY_EQUIVALENT
/ NOT_EQUIVALENT. Do NOT conflate into a boolean equality flag. Future
optimizer legality depends on explicit equivalence class/evidence.

### 8.4 Future planning pipeline (REFINE)

```
OperationPlan → Semantic Analysis → Legal Transformation Space
→ Rewrite / Normalization → Legal Alternatives → Cost Optimizer → Physical Plan
```

Cost optimization cannot invent legal transformations; legal space must
already be proven/validated.

---

## 9. Layer 6 — Execution

### 9.1 Execution authority (REFINE)

Execution remains below planning. Physical planning / execution may choose
provider, worker, device, codec implementation, GPU/CPU, resource placement,
QoS, cost, privacy, determinism profile — these do NOT redefine canonical
media semantics.

### 9.2 Provider authority (REFINE — explicit)

Provider implementations EXECUTE canonical semantics; providers do not define
them. FFmpeg remains a one-way execution adapter / implementation backend —
NOT a domain model, DSL, or canonical operation authority.

### 9.3 Future execution intent (DEFER)

RenderExtent, Execution Requirements, QoS intent, resource intent, cost
intent, privacy intent, determinism intent, runtime isolation, provider
probing, worker capability probing — future refinements around #20-#22; not
implemented in this consolidation.

### 9.4 User-contributed / distributed compute (ADD — future)

User-contributed compute is future execution FABRIC — NOT domain authority,
NOT revision authority, NOT planning-legality authority. It must execute
already-authorized plans under trust/sandbox/resource policy.

---

## 10. Layer 7 — Product Surfaces

### 10.1 Infinite Canvas (ADD — architecture adopted)

INFINITE_CANVAS_AND_VISUAL_WORKFLOW_AS_PRODUCT_SURFACES_V1: Infinite Canvas is
a workspace/application presentation surface, NOT canonical media authority.
Separate: WorkspaceCanvas presentation state / SemanticReference / validated
SemanticRelationship. Do not mix layout edges with domain relationships.

### 10.2 Workflow (REFINE)

WORKFLOW OWNS PROCESS; TIMELINE OWNS MEDIA COMPOSITION. Workflow: long-lived
orchestration, wait, retry, external interaction, agent invocation, render
invocation, multiple revisions/artifacts. Recipe: semantic composition
lowering toward OperationPlan. Do NOT merge Recipe and Workflow into one
abstraction.

### 10.3 Agents / MCP / Skills / Recipes

May discover/compose effective capabilities; must lower into common
application semantics; must not directly mutate canonical persistence; must
not bypass authorization, entitlement, or the OperationPlan transaction
boundary.

### 10.4 GraphQL (ADD — DEFERRED)

- GRAPHQL_IS_APPLICATION_QUERY_PROJECTION_AND_COMMAND_TRANSPORT_NOT_DOMAIN_AUTHORITY_V1
- GRAPHQL_SCHEMA_IS_A_PROJECTION_NOT_THE_CANONICAL_SCHEMA_V1
- GRAPHQL_MUTATIONS_LOWER_TO_APPLICATION_COMMANDS_AND_OPERATION_PLAN_V1
- GRAPHQL_SUBSCRIPTIONS_PROJECT_TYPED_EVENTS_V1
- GRAPHQL_AND_MCP_ARE_PEER_ADAPTERS_V1
- GRAPHQL_IS_CONTROL_METADATA_PLANE_NOT_MEDIA_DATA_PLANE_V1

Implementation DEFERRED. No GraphQL Federation now.

### 10.5 DSL (DEFER — future)

Future controlled typed composition layer: selection, scope resolution,
capability requirements, typed parameters, composition, conditions,
templates. Avoid general-purpose imperative language design. No direct
storage mutation. No FFmpeg command DSL.

---

## 11. Layer 8 — Persistence / Extensibility

### 11.1 PostgreSQL baseline (REFINE)

POSTGRES_EXTENSION_IS_INFRASTRUCTURE_CAPABILITY_NOT_DOMAIN_AUTHORITY_V1.
PostgreSQL remains canonical relational store.

- Baseline: pg_stat_statements (recommended/current), pg_trgm (early),
  auto_explain (threshold diagnostics), HypoPG (index planning diagnostics)
- Conditional: btree_gist, ltree
- Likely future: pgvector (semantic search)
- Possible later: TimescaleDB (operational telemetry)
- Deferred: Apache AGE, PostGIS, Citus

### 11.2 Storage authority boundaries (REFINE)

- Revision graph mechanics: JGit
- Canonical relational metadata: PostgreSQL
- Media bytes: Artifact / Storage data plane
- Embeddings: derived/rebuildable projections
- Telemetry: operational projection

No extension/store becomes Timeline/Media/Capability domain authority.

### 11.3 Provenance / events (ADD — architecture adopted)

- PROVENANCE_LINEAGE_V1 — explanatory, reproducibility, lineage; NOT canonical
  authority.
- TYPED_DOMAIN_EVENTS_V1 — reactive automation/integration projection; NOT
  event sourcing as canonical domain authority.

---

## 12. Layer 9 — Governance / Delivery

### 12.1 Delivery model (REFINE)

- ChatGPT = architecture/roadmap authority + independent review
- Hermes = engineering control-plane + repository/milestone owner
- DeepSeek Harness / selected coding model = bounded implementation executor
- FAST DELIVERY MODE

Do NOT restore the old complex multi-role model yet.

### 12.2 Non-negotiable delivery safeguards (REFINE — retained)

Frozen architecture contracts, architecture escalation conditions, controlled/
isolated worktrees where practical, Hermes diff review, authoritative gates,
candidate freeze, FCV, publication discipline, no unauthorized merge/push,
mandatory independent epoch reviews.

### 12.3 Evidence identity (REFINE — integrated permanently)

- FCV_MUST_RUN_FROM_CLEAN_CHECKOUT_OF_COMMITTED_TREE_V1
- EVIDENCE_BUILD_INPUT_MUST_BE_IDENTICAL_TO_COMMITTED_ARTIFACT_V1
- COMMIT_IDENTITY_IS_DISTINCT_FROM_WORKTREE_STATE_V1
- BUILD_INPUT_IDENTITY_MUST_BE_EXPLICIT_V1

Distinction frozen: COMMIT IDENTITY != WORKTREE STATE != BUILD INPUT IDENTITY
!= EVIDENCE IDENTITY. (Roadmap #20 source-completeness incident cited as
motivation, not repeated in detail.)

### 12.4 Governance evidence honesty (REFINE — post-#20 lesson)

- UNKNOWN_IS_FIRST_CLASS_NOT_IMPLICIT_PASS_V1
- EVIDENCE_ACCOUNTING_MUST_MATCH_ACTUAL_TEST_TARGET_AND_EXECUTION_SCOPE_V1
  (a passing exception test does not prove the claimed boundary unless the
  fixture reaches that boundary)
- Cross-publication metrics must not be presented as comparable unless the
  counting method is established.

---

## 13. Polyglot architecture & encoding

### 13.1 Polyglot (ADD — architecture adopted)

LANGUAGE_NEUTRAL_CONTRACT_POLYGLOT_IMPLEMENTATION_SINGLE_SEMANTIC_AUTHORITY_V1:
Java is the current reference/canonical implementation. Future Rust, Python,
C++, WASM, remote analyzers/solvers may implement bounded analyzers/providers.
One semantic authority, language-neutral contracts, external analyzer
proposes, canonical core verifies. POLYGLOT_READY != MICROSERVICE_FIRST.

### 13.2 Canonical vs interchange encoding (ADD — explicit)

CANONICAL ENCODING = deterministic semantic identity / hashing / history.
INTERCHANGE ENCODING = API/transport/language interoperability. Do not
require one encoding to serve both concerns.

---

## 14. Current milestone state

ROADMAP_MILESTONE_IDENTITY_IS_INDIVIDUALLY_PRESERVED_V1 — ADOPTED. Every
milestone #1-#28 has its own row. No renumbering; no #29/#30. Unknown
historical identities are recorded as UNKNOWN (UNKNOWN_IS_FIRST_CLASS_NOT_
IMPLICIT_PASS_V1), never guessed.

| MILESTONE | CANONICAL_NAME | STATUS | ARCH_LAYER_OR_ERA | IMPLEMENTATION_TRUTH | SOURCE / EVIDENCE |
|---|---|---|---|---|---|
| #1 | HISTORICAL_NAME_NOT_RECOVERED | CLOSED / HISTORICAL | foundation era (pre-2026-05 baseline) | UNKNOWN (pre-governance-record) | foundation-era commits (2b868fe0...) |
| #2 | HISTORICAL_NAME_NOT_RECOVERED | CLOSED / HISTORICAL | foundation era | UNKNOWN (pre-governance-record) | foundation-era commits |
| #3 | HISTORICAL_NAME_NOT_RECOVERED | CLOSED / HISTORICAL | foundation era | UNKNOWN (pre-governance-record) | foundation-era commits |
| #4 | HISTORICAL_NAME_NOT_RECOVERED | CLOSED / HISTORICAL | foundation era | UNKNOWN (pre-governance-record) | foundation-era commits |
| #5 | HISTORICAL_NAME_NOT_RECOVERED | CLOSED / HISTORICAL | foundation era | UNKNOWN (pre-governance-record) | foundation-era commits |
| #6 | HISTORICAL_NAME_NOT_RECOVERED | CLOSED / HISTORICAL | foundation era | UNKNOWN (pre-governance-record) | foundation-era commits |
| #7 | HISTORICAL_NAME_NOT_RECOVERED | CLOSED / HISTORICAL | foundation era | UNKNOWN (pre-governance-record) | foundation-era commits |
| #8 | HISTORICAL_NAME_NOT_RECOVERED | CLOSED / HISTORICAL | foundation era | UNKNOWN (pre-governance-record) | foundation-era commits |
| #9 | HISTORICAL_NAME_NOT_RECOVERED | CLOSED / HISTORICAL | foundation era | UNKNOWN (pre-governance-record) | foundation-era commits |
| #10 | HISTORICAL_NAME_NOT_RECOVERED | CLOSED / HISTORICAL | foundation era | UNKNOWN (pre-governance-record) | foundation-era commits |
| #11 | HISTORICAL_NAME_NOT_RECOVERED | CLOSED / HISTORICAL | foundation era | UNKNOWN (pre-governance-record) | foundation-era commits |
| #12 | HISTORICAL_NAME_NOT_RECOVERED | CLOSED / HISTORICAL | foundation era | UNKNOWN (pre-governance-record) | foundation-era commits |
| #13 | MEDIA_CANONICAL_MODEL_V2 | CLOSED | Canonical Semantics | IMPLEMENTED | roadmap-13-media-canonical-model-v2.md (CLOSED) |
| #14 | TIMELINE_V2 | CLOSED | Canonical Semantics | IMPLEMENTED | roadmap-14-timeline-v2.md (CLOSED) |
| #15 | AUDIO_V2 | CLOSED | Canonical Semantics | IMPLEMENTED | roadmap-15-audio-v2.md (CLOSED) |
| #16 | CAPABILITY_VERSION_LIFECYCLE | CLOSED | Capability | IMPLEMENTED | roadmap-16-capability-version-lifecycle.md (CLOSED) |
| #17 | OTIO_V2_ASSET_SOURCE_BOUNDARY | CLOSED | Canonical Semantics | IMPLEMENTED | roadmap-17-otio-v2-asset-source-boundary.md (CLOSED) |
| #18 | COLOR_IMAGE_FOUNDATION (+ CIP2 durable persistence chain) | CLOSED | Canonical Semantics / Persistence | IMPLEMENTED | roadmap-18-color-image-foundation.md + CIP2 chain (CLOSED) |
| #19 | FONT_TEXT_FOUNDATION (+ completion: operations/diff-merge; timedtext presentation foundation) | CLOSED | Canonical Semantics / Operation | IMPLEMENTED | roadmap-19 font-text + completion publications (CLOSED, blockers 0) |
| #20 | RENDERPLAN_RENDERGRAPH_V1 (+ Option B Effect authority) | CLOSED / INTEGRATED at main 19db3aea | Planning / Canonical Semantics | IMPLEMENTED | roadmap-20 publications + final evidence correction (CLOSED) |
| #21 | UNKNOWN (no repository name evidence; referenced only as NOT STARTED) | NOT STARTED | future epoch | NOT STARTED | roadmap-20 records (#21/#22 started: NO) |
| #22 | WORKER_FABRIC / PHYSICAL_PLANNING (repository evidence: "Worker Fabric", "Physical planning: CapabilityImplementation profiles, candidate enumeration, ExecutionIsland, provider/worker/device locality, physical binding, cost/latency inputs") | NOT STARTED | Execution / Physical Planning | NOT STARTED | roadmap-20-post-decision-recovery-mandatory-refinement.md §12; roadmap-13/14/15 delta lists |
| #23 | DISTRIBUTED_SCHEDULING (repository evidence: "Distributed scheduling: queue pressure, worker utilization, cross-worker placement, deadline/resource scheduling") | NOT STARTED | Execution | NOT STARTED | roadmap-20-post-decision-recovery-mandatory-refinement.md §12 |
| #24 | UNKNOWN (referenced as #24 in delta lists; name not recovered) | NOT STARTED | future epoch | NOT STARTED | roadmap-15/16 delta lists |
| #25 | HISTORICAL_NAME_NOT_RECOVERED | NOT STARTED | future epoch | NOT STARTED | no repository record found |
| #26 | HISTORICAL_NAME_NOT_RECOVERED | NOT STARTED | future epoch | NOT STARTED | no repository record found |
| #27 | HISTORICAL_NAME_NOT_RECOVERED | NOT STARTED | future epoch | NOT STARTED | no repository record found |
| #28 | HISTORICAL_NAME_NOT_RECOVERED | NOT STARTED | future epoch | NOT STARTED | no repository record found |

Checkpoint A (inter-milestone governance epoch): combined media semantic
closure + revision write-surface + pin atomicity — CLOSED
(CHECKPOINT_A_INDEPENDENT_FINAL_REVIEW = PASS, material blockers 0;
checkpoint-a-independent-final-pass.md).

Note: #1-#12 are the pre-governance-record foundation era. Their exact
per-number canonical names are not recoverable from repository governance
records; the names are therefore recorded as HISTORICAL_NAME_NOT_RECOVERED
rather than guessed. #21, #24-#28 similarly have no recovered individual
names; #22 and #23 have explicit repository evidence quoted above.

## 15. Roadmap #20 closure record

- ROADMAP20_FINAL_IMPLEMENTATION_SHA = 08696bd22457026a00a613c9e830db6e7bbf7c5d
- ROADMAP20_FINAL_MAINLINE_TIP = 19db3aead6c27e6ddf1e7d3faab62b287a48cef0

Roadmap #20 established: Logical RenderPlan, RenderGraph, deterministic
canonical plan representation, provider-neutral logical planning, verified
Timeline/Effect semantic boundary, immutable Effect semantic snapshot pinning,
exact historical restore verification, ownership-scoped canonical reads,
immutable writer authorities, HEAD CAS final publication boundary,
clean-forward Effect authority.

## 16. Cross-layer dependency graph

```
Canonical Semantics
    ↓
Capability Identity
    ↓
Operation Model
    ↓
Constraint/Evidence
    ↓
Semantic Analysis / Algebra
    ↓
LegalPlanSpace
    ↓
Logical/Physical Planning
    ↓
Execution

Side relationships:
Product Surfaces → Operation
Entitlement/Policy → Effective Capability + Authorization
Formal Methods → Evidence for semantic legality
Persistence → supports all, owns no semantic authority beyond its bounded store contract
```

## 17. Next-epoch entry conditions (post-#20)

1. #20 CLOSED — DONE (this baseline)
2. Integrated Architecture Roadmap V2 consolidation published — this document
3. Explicit next-epoch base SHA recorded (after independent review PASS and
   mainline fast-forward)
4. Then authorize next roadmap implementation

Do NOT pre-authorize #21/#22 in this consolidation.

## 18. Deferred foundations

Constraint Kernel runtime, Evidence runtime, Formal Methods F1-F4, Semantic
Analysis, Semantic Rewrite, Cost Optimizer, Physical Planner, GraphQL, Canvas
runtime, Workflow surface evolution, polyglot runtime, user-contributed
compute, provider/device scheduler, marketplaces.

Note (C1 correction): Operation Model V1, OperationPlan Transaction V1 and
Revision Command Model V1 are IMPLEMENTED / CLOSED (see §6.2) and are NOT
deferred. What remains future in the Operation layer is only
FUTURE_GENERALIZATION / FUTURE_SURFACE_COVERAGE (widening the implemented
bounded V1 boundary to every frontend and integrating the constraint/evidence
kernel) — not "runtime from zero".

## 19. Post-#20 architecture sequencing (planned, NOT milestone authorization)

Corrected frontier begins ABOVE the implemented Operation / OperationPlan /
Revision Command foundation. This is architecture sequencing for the next
epoch — it is NOT milestone authorization and does NOT pre-authorize #21/#22.

```
implemented canonical semantics
→ implemented Operation Model V1
→ implemented OperationPlan Transaction V1
→ implemented Revision Command Model V1
→ implemented Logical RenderPlan / RenderGraph V1
→ F0 language-neutral semantic law / evidence / equivalence hooks
→ Constraint / Evidence foundation realization
→ Semantic Analysis
→ Formal Semantic Kernel POC
→ executable formal oracle / differential CI
→ Law Registry / formal evidence gate
→ LegalPlanSpace
→ Semantic Rewrite
→ Cost Optimizer
→ Physical Planning
→ Execution intelligence
```

The platform does NOT need to "create from zero" the Operation Model
Foundation, the OperationPlan transaction boundary, or an OperationPlan
runtime — those exist (bounded V1). The next work starts ABOVE them.

## 20. Constraint Kernel migration strategy

Do not replace existing tests/gates. Future migration: existing
invariant/test/gate → assign stable ConstraintId → typed Evidence →
EvaluationResult → Traceability → shared Constraint Kernel. Incremental
migration only; no big-bang rewrite.

## 21. Product roadmap implication

The project has moved from the DOMAIN FOUNDATION ERA toward the SEMANTIC
COMPUTING / PLANNING ERA. Product layer remains above the same canonical
core. Traditional NLE, Infinite Canvas, Agent-first Editor, Workflow Studio,
MCP/API, GraphQL, Template Editor are PEER product surfaces; none owns
canonical media semantics.

---

## 22. Decision traceability table (architecture-level)

ARCHITECTURE_CHANGE_TRACEABILITY_FIELDS_V1 — ADOPTED. Required fields:
DECISION_ID, UPDATE_TYPE, ARCH_LAYER, ARCH_STATUS, IMPL_STATUS,
MILESTONE_STATUS, AFFECTED_EXISTING, AFFECTED_MILESTONES,
AFFECTED_CONSTRAINTS, REQUIRED_EVIDENCE_DELTA, TRACEABILITY_DELTA,
SOURCE_DOCUMENTS.

AFFECTED_CONSTRAINTS values: explicit existing Constraint IDs where they
already exist; otherwise GOVERNANCE_ONLY / FUTURE_CONSTRAINT_PROJECTION_REQUIRED
/ NONE / NOT_YET_ASSIGNED. No stable ConstraintIds are invented (the
Constraint Kernel has not assigned them); NOT_YET_ASSIGNED is preferred to
fabricated precision.

TRACEABILITY_DELTA values: NONE / SOURCE_LINK_ADDED / STATUS_CORRECTED /
AUTHORITY_RELATION_REFINED / FUTURE_CONSTRAINT_TRACE_REQUIRED /
EXISTING_DECISION_INTEGRATED / IMPLEMENTATION_STATUS_CORRECTED.

EXACT_FROZEN_DECISION_ID_OR_EXPLICIT_COMPOSITION_V1 — ADOPTED (V2-F1/F4).
Every DECISION_ID is exactly one of:
- EXACT_EXISTING_FROZEN_ID (exact Decision ID provably existed in tracked
  repository governance before V2; mechanical provenance recorded in §22.1:
  source path + source commit SHA, ancestor of base main 19db3aea, exact
  string present at that commit),
- NEW_V2_UMBRELLA_ID (first established by this V2 baseline; GROUPS /
  COMPOSES / SUMMARIZES already-authoritative decisions/principles; all
  source authorities explicitly listed; MILESTONE_STATUS = NOT_APPLICABLE —
  umbrella status is NOT the summarized milestone's status), or
- NEW_V2_ADOPTED_DECISION_ID (exact Decision ID not provably present in
  repository governance before V2 and not merely an umbrella; this V2
  baseline is the first repository adoption of that exact ID).

Silent near-synonyms, silent aliases, retroactive fake frozen IDs and
duplicate semantic authorities are forbidden.
PREEXISTING_DECISION_CLASSIFICATION_REQUIRES_PRE_V2_REPOSITORY_EVIDENCE_V1 —
ADOPTED: an EXACT_EXISTING_FROZEN_ID classification is valid only with
machine-checkable pre-V2 repository evidence.

| DECISION_ID | UPDATE_TYPE | ARCH_LAYER | ARCH_STATUS | IMPL_STATUS | MILESTONE_STATUS | AFFECTED_EXISTING | AFFECTED_MILESTONES | AFFECTED_CONSTRAINTS | REQUIRED_EVIDENCE_DELTA | TRACEABILITY_DELTA | SOURCE_DOCUMENTS |
|---|---|---|---|---|---|---|---|---|---|---|---|
| ONE_CANONICAL_CORE_MANY_ENTITLED_PRODUCT_SURFACES_V1 | ADD | Platform model | ADOPTED | FOUNDATION_ONLY | NOT_APPLICABLE | — | all | FUTURE_CONSTRAINT_PROJECTION_REQUIRED | none (governance) | EXISTING_DECISION_INTEGRATED | this document §1 |
| EXTERNAL_REVISION_BACKEND_FIRST_V1 | REFINE | Canonical Semantics | FROZEN | IMPLEMENTED | CLOSED | JGit mechanics | #13 | NOT_YET_ASSIGNED | none | SOURCE_LINK_ADDED | roadmap-13; core-rebalancing §1 |
| TIMELINE_IS_COMPOSITION_REVISION_AND_MERGE_AUTHORITY_V1 | REFINE | Canonical Semantics | FROZEN | IMPLEMENTED | CLOSED | Timeline Git | #14 | NOT_YET_ASSIGNED | none | SOURCE_LINK_ADDED | roadmap-14; core-rebalancing §1 |
| EFFECT_SEMANTIC_SNAPSHOT_PINNED_BY_TIMELINE_REVISION_V1 | REFINE | Canonical Semantics | FROZEN | IMPLEMENTED | CLOSED | Effect authority | #20 | NOT_YET_ASSIGNED | FCV (done) | EXISTING_DECISION_INTEGRATED | roadmap-20 Option B chain |
| NO_LEGACY_EFFECT_AUTHORITY_AFTER_ROADMAP20_V1 | REFINE | Canonical Semantics | FROZEN | IMPLEMENTED | CLOSED | legacy Effect | #20 | NOT_YET_ASSIGNED | FCV (done) | EXISTING_DECISION_INTEGRATED | roadmap-20 clean-forward |
| CAPABILITY_AUTHORITY_MODEL_V1 | ADD | Capability | ADOPTED | IMPLEMENTED | NOT_APPLICABLE | CapabilityRegistryPort/PluginRegistryPort | #16 | NOT_YET_ASSIGNED | none | AUTHORITY_RELATION_REFINED | this document §5.2; roadmap-16 (C16-CORR-3); §22.1 |
| EFFECTIVE_CAPABILITY_MODEL_V1 | ADD | Capability | ADOPTED | FOUNDATION_ONLY | NOT_APPLICABLE | workflow capability | #16 | FUTURE_CONSTRAINT_PROJECTION_REQUIRED | future | AUTHORITY_RELATION_REFINED | this document §5.3; §22.1 |
| OPERATION_MODEL_FOUNDATION_V1 | REFINE | Operation | FROZEN | IMPLEMENTED | CLOSED | operation model | #19/OPM | NOT_YET_ASSIGNED | real-PG evidence (done) | IMPLEMENTATION_STATUS_CORRECTED | operation-model-foundation-v1.md |
| OPERATION_PLAN_TRANSACTION_MODEL_V1 | REFINE | Operation | FROZEN | IMPLEMENTED | CLOSED | plan/apply boundary | OPTM | NOT_YET_ASSIGNED | real-PG evidence (done) | IMPLEMENTATION_STATUS_CORRECTED | operation-plan-transaction-model-v1.md |
| REVISION_COMMAND_MODEL_V1 | ADD | Operation | FROZEN | IMPLEMENTED | CLOSED | revision command boundary | RCM | NOT_YET_ASSIGNED | real-PG evidence (done) | EXISTING_DECISION_INTEGRATED | revision-command-model-v1.md |
| UNIFIED_CONSTRAINT_AND_EVALUATION_ARCHITECTURE_V1 | ADD | Constraint/Evidence | ADOPTED | FOUNDATION_ONLY | NOT_APPLICABLE | — | future | FUTURE_CONSTRAINT_PROJECTION_REQUIRED | future | EXISTING_DECISION_INTEGRATED | this document §7.1 |
| CANONICAL_CONSTRAINT_KERNEL_V1 | ADD | Constraint/Evidence | ADOPTED | NOT_STARTED | NOT_APPLICABLE | — | future | FUTURE_CONSTRAINT_PROJECTION_REQUIRED | future | FUTURE_CONSTRAINT_TRACE_REQUIRED | this document §7.2 |
| EVIDENCE_MODEL_FOUNDATION_V1 | ADD | Constraint/Evidence | ADOPTED | FOUNDATION_ONLY | NOT_APPLICABLE | FCV/gates as projections | future | FUTURE_CONSTRAINT_PROJECTION_REQUIRED | future | FUTURE_CONSTRAINT_TRACE_REQUIRED | this document §7.3 |
| FORMAL_METHODS_PROGRESSIVE_ADOPTION_ROADMAP_V1 | ADD | Formal Methods | ADOPTED | NOT_STARTED | NOT_APPLICABLE | — | future | FUTURE_CONSTRAINT_PROJECTION_REQUIRED | future | EXISTING_DECISION_INTEGRATED | this document §7.5 |
| LEAN_FIRST_FORMAL_SEMANTIC_KERNEL_V1 | ADD | Formal Methods | ADOPTED | NOT_STARTED | NOT_APPLICABLE | — | future | FUTURE_CONSTRAINT_PROJECTION_REQUIRED | future | EXISTING_DECISION_INTEGRATED | this document §7.5 |
| RENDERPLAN_LOGICAL_PLANNING_AUTHORITY_V1 | ADD | Planning | ADOPTED | IMPLEMENTED | NOT_APPLICABLE | RenderPlan/RenderGraph | #20 | NOT_YET_ASSIGNED | FCV (done) | EXISTING_DECISION_INTEGRATED | this document §8.1; §22.1; roadmap-20 contract |
| ROADMAP_ALGEBRAIC_SEMANTIC_OPTIMIZATION_AMENDMENT_V1 | ADD | Planning | ADOPTED | FOUNDATION_ONLY | NOT_APPLICABLE | — | future | FUTURE_CONSTRAINT_PROJECTION_REQUIRED | future | EXISTING_DECISION_INTEGRATED | this document §8.2 |
| COST_OPTIMIZATION_ONLY_OVER_PROVEN_LEGAL_PLAN_SPACE_V1 | ADD | Planning | ADOPTED | NOT_STARTED | NOT_APPLICABLE | — | future | FUTURE_CONSTRAINT_PROJECTION_REQUIRED | future | EXISTING_DECISION_INTEGRATED | roadmap-20-effect-snapshot-binding-vs-semantic-identity-correction; this document §8.2 |
| PROVIDER_EXECUTES_NOT_DEFINES_SEMANTICS_V1 | ADD | Execution | ADOPTED | IMPLEMENTED | NOT_APPLICABLE | FFmpeg adapter | #13 | NOT_YET_ASSIGNED | none | SOURCE_LINK_ADDED | this document §9.2; §22.1; core-rebalancing §10 |
| INFINITE_CANVAS_AND_VISUAL_WORKFLOW_AS_PRODUCT_SURFACES_V1 | ADD | Product Surfaces | ADOPTED | NOT_STARTED | NOT_APPLICABLE | — | future | FUTURE_CONSTRAINT_PROJECTION_REQUIRED | future | EXISTING_DECISION_INTEGRATED | this document §10.1 |
| WORKFLOW_OWNS_PROCESS_TIMELINE_OWNS_COMPOSITION_V1 | ADD | Product Surfaces | ADOPTED | PARTIALLY_IMPLEMENTED | NOT_APPLICABLE | workflow/timeline | #19 | NOT_YET_ASSIGNED | none | AUTHORITY_RELATION_REFINED | this document §10.2; §22.1; core-rebalancing §8 |
| GRAPHQL_IS_APPLICATION_QUERY_PROJECTION_AND_COMMAND_TRANSPORT_NOT_DOMAIN_AUTHORITY_V1 | ADD | Product Surfaces | ADOPTED | NOT_STARTED | FUTURE | — | future | FUTURE_CONSTRAINT_PROJECTION_REQUIRED | future | EXISTING_DECISION_INTEGRATED | this document §10.4 |
| POSTGRES_EXTENSION_IS_INFRASTRUCTURE_CAPABILITY_NOT_DOMAIN_AUTHORITY_V1 | REFINE | Persistence | FROZEN | IMPLEMENTED | CLOSED | PG extensions | GCR-5/6 | NOT_YET_ASSIGNED | none | SOURCE_LINK_ADDED | gcr-5-gcr-6 |
| PROVENANCE_LINEAGE_V1 | ADD | Persistence | ADOPTED | FOUNDATION_ONLY | NOT_APPLICABLE | — | future | FUTURE_CONSTRAINT_PROJECTION_REQUIRED | future | EXISTING_DECISION_INTEGRATED | roadmap-20-logical-what-closure-correction-r4; this document §11.3 |
| LANGUAGE_NEUTRAL_CONTRACT_POLYGLOT_IMPLEMENTATION_SINGLE_SEMANTIC_AUTHORITY_V1 | ADD | Polyglot | ADOPTED | FOUNDATION_ONLY | NOT_APPLICABLE | — | future | FUTURE_CONSTRAINT_PROJECTION_REQUIRED | future | EXISTING_DECISION_INTEGRATED | this document §13.1 |
| EVIDENCE_ACCOUNTING_MUST_MATCH_ACTUAL_TEST_TARGET_AND_EXECUTION_SCOPE_V1 | ADD | Governance | ADOPTED | IMPLEMENTED | NOT_APPLICABLE | #20 evidence correction | #20 | FUTURE_CONSTRAINT_PROJECTION_REQUIRED | governance (done) | IMPLEMENTATION_STATUS_CORRECTED | roadmap-20-final-independent-acceptance-evidence-correction; §22.1 |

Note on status columns (V2-F2): ARCH_STATUS and IMPL_STATUS contain only their
own axis values; MILESTONE_STATUS is a separate column. CLOSED appears only
under MILESTONE_STATUS.

### 22.1 Decision ID authority / composition register (V2-F1/F4 — mechanical provenance)

Classification: every traceability DECISION_ID is exactly one of
EXACT_EXISTING_FROZEN_ID / NEW_V2_UMBRELLA_ID / NEW_V2_ADOPTED_DECISION_ID.
No INVALID_OR_AMBIGUOUS entries. PRE_V2_PROOF = VERIFIED only when the exact
string exists in a tracked file at a source commit that is an ancestor of
base main 19db3aea (mechanically checked via `git merge-base --is-ancestor`
and `git show <sha>:<path>`).

| V2_DECISION_ID | CLASSIFICATION | PRE_V2_PROOF | SOURCE_COMMIT_SHA | SOURCE_PATH | RELATION / SOURCE_IDS | STATUS |
|---|---|---|---|---|---|---|
| ONE_CANONICAL_CORE_MANY_ENTITLED_PRODUCT_SURFACES_V1 | NEW_V2_UMBRELLA_ID | NOT_APPLICABLE | V2_INTRODUCTION (1749b005) | this V2 baseline | GROUPS: platform-model principles across #13-#20 | ADOPTED |
| EXTERNAL_REVISION_BACKEND_FIRST_V1 | NEW_V2_ADOPTED_DECISION_ID | NO_PRE_V2_EVIDENCE | V2_INTRODUCTION (1749b005) | this V2 baseline | exact ID first adopted by V2; semantics trace to roadmap-13/core-rebalancing §1 | ADOPTED |
| TIMELINE_IS_COMPOSITION_REVISION_AND_MERGE_AUTHORITY_V1 | NEW_V2_ADOPTED_DECISION_ID | NO_PRE_V2_EVIDENCE | V2_INTRODUCTION (1749b005) | this V2 baseline | exact ID first adopted by V2; semantics trace to roadmap-14/core-rebalancing §1 | ADOPTED |
| EFFECT_SEMANTIC_SNAPSHOT_PINNED_BY_TIMELINE_REVISION_V1 | EXACT_EXISTING_FROZEN_ID | VERIFIED | 8fcc44dfdc875b047b8639bc50069f28a8314698 | roadmap-20-effect-authority-binding-decision-recovery.md | — | FROZEN |
| NO_LEGACY_EFFECT_AUTHORITY_AFTER_ROADMAP20_V1 | NEW_V2_ADOPTED_DECISION_ID | NO_PRE_V2_EVIDENCE | V2_INTRODUCTION (1749b005) | this V2 baseline | exact ID first adopted by V2; semantics trace to roadmap-20 clean-forward chain | ADOPTED |
| CAPABILITY_AUTHORITY_MODEL_V1 | NEW_V2_UMBRELLA_ID | NOT_APPLICABLE | V2_INTRODUCTION (1749b005) | this V2 baseline | SUMMARIZES: CAPABILITY_VERSION_LIFECYCLE_BOUNDED_ARCHITECTURE_CONTRACT_V1 (C1-C20+R1-R4); CapabilityRegistryPort = capability-facing authority (C16-CORR-3); PluginRegistryPort = plugin container concern | ADOPTED |
| EFFECTIVE_CAPABILITY_MODEL_V1 | NEW_V2_UMBRELLA_ID | NOT_APPLICABLE | V2_INTRODUCTION (1749b005) | this V2 baseline | COMPOSES: effective access = capability exists ∩ runtime available ∩ entitlement ∩ policy permission ∩ quota; WORKFLOW_RUNTIME_RESOLVES_CAPABILITY_THROUGH_EFFECTIVE_CAPABILITY_VIEW_V1 | ADOPTED |
| OPERATION_MODEL_FOUNDATION_V1 | EXACT_EXISTING_FROZEN_ID | VERIFIED | 0c0eda94345df4fd229852ab7e377381b55d594e | operation-model-foundation-v1.md | — | FROZEN |
| OPERATION_PLAN_TRANSACTION_MODEL_V1 | EXACT_EXISTING_FROZEN_ID | VERIFIED | 056f8a964afe0bca89019ad0c75eb3f05a56a865 | operation-plan-transaction-model-v1.md | — | FROZEN |
| REVISION_COMMAND_MODEL_V1 | EXACT_EXISTING_FROZEN_ID | VERIFIED | 69099b42d916bc3116edfccde3b969b91a1fc8db | revision-command-model-v1.md | — | FROZEN |
| UNIFIED_CONSTRAINT_AND_EVALUATION_ARCHITECTURE_V1 | NEW_V2_ADOPTED_DECISION_ID | NO_PRE_V2_EVIDENCE | V2_INTRODUCTION (1749b005) | this V2 baseline | exact ID first adopted by V2; architecture direction adopted in V2 §7.1 | ADOPTED |
| CANONICAL_CONSTRAINT_KERNEL_V1 | NEW_V2_ADOPTED_DECISION_ID | NO_PRE_V2_EVIDENCE | V2_INTRODUCTION (1749b005) | this V2 baseline | exact ID first adopted by V2; architecture direction adopted in V2 §7.2 | ADOPTED |
| EVIDENCE_MODEL_FOUNDATION_V1 | NEW_V2_ADOPTED_DECISION_ID | NO_PRE_V2_EVIDENCE | V2_INTRODUCTION (1749b005) | this V2 baseline | exact ID first adopted by V2; architecture direction adopted in V2 §7.3 | ADOPTED |
| FORMAL_METHODS_PROGRESSIVE_ADOPTION_ROADMAP_V1 | NEW_V2_ADOPTED_DECISION_ID | NO_PRE_V2_EVIDENCE | V2_INTRODUCTION (1749b005) | this V2 baseline | exact ID first adopted by V2; architecture direction adopted in V2 §7.5 | ADOPTED |
| LEAN_FIRST_FORMAL_SEMANTIC_KERNEL_V1 | NEW_V2_ADOPTED_DECISION_ID | NO_PRE_V2_EVIDENCE | V2_INTRODUCTION (1749b005) | this V2 baseline | exact ID first adopted by V2; architecture direction adopted in V2 §7.5 | ADOPTED |
| RENDERPLAN_LOGICAL_PLANNING_AUTHORITY_V1 | NEW_V2_UMBRELLA_ID | NOT_APPLICABLE | V2_INTRODUCTION (1749b005) | this V2 baseline | COMPOSES: ONE_CANONICAL_RENDERPLAN_AUTHORITY_V1; RENDERPLAN_IS_NOT_OPERATIONPLAN_V1; #20 RenderPlan/RenderGraph frozen contracts | ADOPTED |
| ROADMAP_ALGEBRAIC_SEMANTIC_OPTIMIZATION_AMENDMENT_V1 | NEW_V2_ADOPTED_DECISION_ID | NO_PRE_V2_EVIDENCE | V2_INTRODUCTION (1749b005) | this V2 baseline | exact ID first adopted by V2; architecture direction adopted in V2 §8.2 | ADOPTED |
| COST_OPTIMIZATION_ONLY_OVER_PROVEN_LEGAL_PLAN_SPACE_V1 | EXACT_EXISTING_FROZEN_ID | VERIFIED | bf7a2702480d9ad73ead986e05602e974b6f022a | roadmap-20-effect-snapshot-binding-vs-semantic-identity-correction.md | — | ADOPTED |
| PROVIDER_EXECUTES_NOT_DEFINES_SEMANTICS_V1 | NEW_V2_UMBRELLA_ID | NOT_APPLICABLE | V2_INTRODUCTION (1749b005) | this V2 baseline | COMPOSES: PROVIDER_IDENTITY_IS_EXECUTION_BINDING_AND_PROVENANCE_NOT_WORKFLOW_SEMANTICS_V1; EXECUTION_RUNTIMES_INTERACT_THROUGH_PLATFORM_OWNED_PORTS_NOT_EACH_OTHERS_DOMAIN_APIS_V1; FFmpeg = one-way adapter | ADOPTED |
| INFINITE_CANVAS_AND_VISUAL_WORKFLOW_AS_PRODUCT_SURFACES_V1 | NEW_V2_ADOPTED_DECISION_ID | NO_PRE_V2_EVIDENCE | V2_INTRODUCTION (1749b005) | this V2 baseline | exact ID first adopted by V2; architecture direction adopted in V2 §10.1 | ADOPTED |
| WORKFLOW_OWNS_PROCESS_TIMELINE_OWNS_COMPOSITION_V1 | NEW_V2_UMBRELLA_ID | NOT_APPLICABLE | V2_INTRODUCTION (1749b005) | this V2 baseline | SUMMARIZES: WORKFLOW_DEPENDS_ON_CAPABILITY_REQUIREMENTS_NOT_PROVIDER_IDENTITIES_V1; WORKFLOW_DEFINITION_STORES_CAPABILITY_REQUIREMENT_V1; WORKFLOW_RUNTIME_RESOLVES_CAPABILITY_THROUGH_EFFECTIVE_CAPABILITY_VIEW_V1; TEMPORAL_RUNTIME_ID_IS_INFRASTRUCTURE_BINDING_NOT_WORKFLOWRUN_IDENTITY_V1; Timeline = composition authority | ADOPTED |
| GRAPHQL_IS_APPLICATION_QUERY_PROJECTION_AND_COMMAND_TRANSPORT_NOT_DOMAIN_AUTHORITY_V1 | NEW_V2_ADOPTED_DECISION_ID | NO_PRE_V2_EVIDENCE | V2_INTRODUCTION (1749b005) | this V2 baseline | exact ID first adopted by V2; architecture direction adopted in V2 §10.4 | ADOPTED |
| POSTGRES_EXTENSION_IS_INFRASTRUCTURE_CAPABILITY_NOT_DOMAIN_AUTHORITY_V1 | NEW_V2_ADOPTED_DECISION_ID | NO_PRE_V2_EVIDENCE | V2_INTRODUCTION (1749b005) | this V2 baseline | exact ID first adopted by V2; semantics trace to gcr-5-gcr-6-database-canonicalization | ADOPTED |
| PROVENANCE_LINEAGE_V1 | EXACT_EXISTING_FROZEN_ID | VERIFIED | 7a2b6a3a9430fd7cac5f866125f4c815ad8d8740 | roadmap-20-logical-what-closure-correction-r4.md | — | ADOPTED |
| LANGUAGE_NEUTRAL_CONTRACT_POLYGLOT_IMPLEMENTATION_SINGLE_SEMANTIC_AUTHORITY_V1 | NEW_V2_ADOPTED_DECISION_ID | NO_PRE_V2_EVIDENCE | V2_INTRODUCTION (1749b005) | this V2 baseline | exact ID first adopted by V2; architecture direction adopted in V2 §13.1 | ADOPTED |
| EVIDENCE_ACCOUNTING_MUST_MATCH_ACTUAL_TEST_TARGET_AND_EXECUTION_SCOPE_V1 | NEW_V2_UMBRELLA_ID | NOT_APPLICABLE | V2_INTRODUCTION (1749b005) | this V2 baseline | SUMMARIZES: #20 final evidence correction principle (G1-G3 accounting lessons; test-target boundary discipline) — exact ID first coined in this V2 baseline | ADOPTED |

Metrics (mechanically derived from the 26 actual table rows):
TRACEABILITY_ROW_COUNT = 26
EXACT_EXISTING_FROZEN_ID_COUNT = 6
NEW_V2_UMBRELLA_ID_COUNT = 7
NEW_V2_ADOPTED_DECISION_ID_COUNT = 13
6 + 7 + 13 = 26
INVALID_OR_AMBIGUOUS_ID_COUNT = 0
UNREGISTERED_ALIAS_COUNT = 0
NEAR_SYNONYM_WITHOUT_RELATION_COUNT = 0
PRE_V2_PROVENANCE_UNKNOWN_COUNT = 0 (all 13 non-exact IDs are provably
NO_PRE_V2_EVIDENCE, none left as UNKNOWN)

Provenance audit note (V2-F4): the 6 EXACT_EXISTING_FROZEN_ID rows above each
carry a SOURCE_COMMIT_SHA verified as an ancestor of base main 19db3aea with
the exact Decision ID string present in the listed SOURCE_PATH at that commit
(`git merge-base --is-ancestor` PASS + `git show` string match). The 13
NEW_V2_ADOPTED_DECISION_ID rows were mechanically shown to have NO pre-V2
commit containing the exact ID (full-history `git log --all -S <id>`), so
they are truthfully classified as first adopted by this V2 baseline rather
than fabricated as pre-existing frozen IDs. The 7 NEW_V2_UMBRELLA_ID rows are
V2-created umbrella identities with explicit COMPOSES/GROUPS/SUMMARIZES
relations to already-authoritative decisions; their MILESTONE_STATUS is
NOT_APPLICABLE (umbrella status is not the summarized milestone's status).

## 23. Supersession register

No prior adopted decision is SUPERSEDED by this consolidation. All entries
are ADD or REFINE. (GCR-1..GCR-6 decisions remain authoritative; Checkpoint A
frozen requirements remain authoritative; Roadmap #13-#20 frozen contracts
remain authoritative.)

## 24. Contradiction review

| Authority pair | Check | Resolution |
|---|---|---|
| Timeline vs Effect authority | consistent | Effect pinned BY Timeline revision; distinct identities |
| CapabilityRegistry vs PluginRegistry | consistent | separate authority axes (§5.2); CAPABILITY_AUTHORITY_MODEL_V1 = declared umbrella over CAPABILITY_VERSION_LIFECYCLE_BOUNDED_ARCHITECTURE_CONTRACT_V1 (§22.1) |
| Capability authority (umbrella vs source) | consistent | umbrella explicitly COMPOSES/SUMMARIZES exact frozen sources; no duplicate authority (§22.1) |
| RenderPlan authority (umbrella vs frozen) | consistent | umbrella explicitly COMPOSES ONE_CANONICAL_RENDERPLAN_AUTHORITY_V1 + RENDERPLAN_IS_NOT_OPERATIONPLAN_V1; no duplicate authority (§22.1) |
| Provider authority (umbrella vs frozen) | consistent | umbrella explicitly COMPOSES PROVIDER_IDENTITY_IS_EXECUTION_BINDING... + EXECUTION_RUNTIMES_INTERACT...; no duplicate authority (§22.1) |
| Workflow authority (umbrella vs frozen) | consistent | umbrella explicitly SUMMARIZES exact workflow contracts; no duplicate authority (§22.1) |
| Evidence-accounting authority | consistent | umbrella explicitly SUMMARIZES #20 evidence-correction principle, first coined in V2 (§22.1) |
| Operation status axes | consistent | three independent axes; CLOSED only in MILESTONE_STATUS (§3.1, §6.2, §22) |
| Operation vs Revision Command | consistent | Revision Command is a bounded command surface over the Operation/plan boundary (§6.2) |
| OperationPlan vs canonical revision authority | consistent | OperationPlan produces new revisions through the canonical writer; plan is not revision authority |
| Workflow vs Timeline | consistent | process vs composition (§10.2) |
| Recipe vs Workflow | consistent | recipe lowers toward OperationPlan; workflow is long-lived orchestration (§10.2) |
| DSL vs domain authority | consistent | DSL never domain authority (§6.3, §10.5) |
| GraphQL vs canonical schema | consistent | projection only (§10.4) |
| Constraint Kernel vs generic rule engine | consistent | kernel is NOT a rule engine (§7.2) |
| Formal tools vs runtime semantic authority | consistent | evidence only, not runtime domain authority (§7.5) |
| RenderPlan vs canonical authored state | consistent | derived logical planning state (§8.1) |
| Logical vs Physical planning | consistent | separated layers; physical below logical (§8, §9) |
| Provider vs semantic authority | consistent | providers execute, do not define (§9.2) |
| PostgreSQL / extension vs domain authority | consistent | infrastructure capability only (§11.1) |
| Revision backend vs semantic authority | consistent | JGit mechanics only (§4.2) |
| Canvas presentation edges vs semantic relationships | consistent | layout edges never domain relationships (§10.1) |
| Provenance vs canonical authority | consistent | explanatory/lineage only, not canonical authority (§11.3) |
| Events vs event-sourcing authority | consistent | typed projection, not event sourcing as domain authority (§11.3) |

UNRESOLVED_CONTRADICTIONS = 0 (24 pairs checked, all consistent; no duplicate
authority created by any ID mapping).
Classifications: all REFINE/ADD; no SUPERSEDE needed.

## 25. Roadmap completion model (layer status, no fake percentage)

| Layer | Status |
|---|---|
| Canonical Semantics | MATURE / MOSTLY IMPLEMENTED |
| Capability | FOUNDATION IMPLEMENTED |
| Operation | V1 IMPLEMENTED (Operation Model + OperationPlan Transaction + Revision Command, CLOSED) |
| Constraint/Evidence | ARCHITECTURE ADOPTED |
| Logical Planning | V1 IMPLEMENTED (#20) |
| Physical Planning | NOT STARTED |
| Formal Methods | ROADMAP ADOPTED |
| Product Surfaces | PARTIAL |
| Execution Intelligence | FUTURE |

Percentages, if desired, belong in non-authoritative planning analysis only.

## 26. ARV2 FINAL MECHANICAL LEDGER CANONICALIZATION checklist (F1-F4 closure)

| Check | Result |
|---|---|
| ML-01 parse §22 traceability, count actual rows | PASS (26) |
| ML-02 parse §22.1 register | PASS (26) |
| ML-03 §22 ID set == §22.1 ID set | PASS |
| ML-04 no duplicate IDs in §22 | PASS |
| ML-05 no duplicate IDs in §22.1 | PASS |
| ML-06 every §22 ID has exactly one classification | PASS |
| ML-07 classification ∈ {EXACT_EXISTING_FROZEN_ID, NEW_V2_UMBRELLA_ID, NEW_V2_ADOPTED_DECISION_ID} | PASS |
| ML-08 classification counts sum to actual rows | PASS (6+7+13=26) |
| ML-09 no hardcoded 25/18/7 assertion remains | PASS (metrics recomputed to 26/6/7/13) |
| ML-10 every ARCH_STATUS ∈ architecture enum | PASS |
| ML-11 every IMPL_STATUS ∈ implementation enum | PASS |
| ML-12 every MILESTONE_STATUS ∈ milestone enum | PASS |
| ML-13 CLOSED in normalized ledger only in MILESTONE_STATUS | PASS |
| ML-14 `IMPLEMENTED (governance)` count in IMPL_STATUS = 0 | PASS |
| ML-15 every NEW_V2_UMBRELLA_ID has explicit COMPOSES/GROUPS/SUMMARIZES | PASS (7/7) |
| ML-16 every NEW_V2_UMBRELLA_ID has explicit source authorities | PASS (7/7) |
| ML-17 every NEW_V2_UMBRELLA_ID MILESTONE_STATUS = NOT_APPLICABLE | PASS (7/7) |
| ML-18 every EXACT_EXISTING_FROZEN_ID PRE_V2_PROOF = VERIFIED | PASS (6/6) |
| ML-19 every EXACT_EXISTING_FROZEN_ID SOURCE_COMMIT_SHA ancestor of 19db3aea | PASS (6/6) |
| ML-20 exact ID present in SOURCE_PATH at SOURCE_COMMIT_SHA | PASS (6/6) |
| ML-21 no EXACT uses V2-created source as proof | PASS |
| ML-22 INVALID_OR_AMBIGUOUS count = 0 | PASS |
| ML-23 UNREGISTERED_ALIAS count = 0 | PASS |
| ML-24 NEAR_SYNONYM_WITHOUT_RELATION count = 0 | PASS |
| ML-25 Roadmap row count = 28 | PASS |
| ML-26 Roadmap #20 = CLOSED | PASS |
| ML-27 Roadmap #21 = NOT_STARTED | PASS |
| ML-28 Roadmap #22 = NOT_STARTED | PASS |
| ML-29 Operation Model: FROZEN / IMPLEMENTED / CLOSED | PASS |
| ML-30 OperationPlan: FROZEN / IMPLEMENTED / CLOSED | PASS |
| ML-31 Revision Command: FROZEN / IMPLEMENTED / CLOSED | PASS |
| ML-32 unresolved contradiction count = 0 | PASS (24 pairs) |

**ARV2_FINAL_MECHANICAL_LEDGER_CANONICALIZATION = 32/32 PASS**
