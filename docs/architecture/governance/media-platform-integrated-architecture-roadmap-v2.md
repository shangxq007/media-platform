# MEDIA_PLATFORM_INTEGRATED_ARCHITECTURE_ROADMAP_V2

**STATUS = ADOPTED (baseline consolidation, FINAL BASELINE CORRECTION applied,
pending independent review)**

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
| REVIEWED_PREDECESSOR | 1749b005dfd45fe54b25e4b7a8166361e4045f66 |
| CORRECTION_TYPE | GOVERNANCE_BASELINE_CORRECTION (C1-C4) |
| ARV2_CORRECTION | 22/22 PASS |

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

### 3.1 Normalized status vocabulary (C1 correction)

ARCHITECTURE_STATUS (design authority):
- PROPOSED / ADOPTED / FROZEN / SUPERSEDED / DEFERRED

IMPLEMENTATION_STATUS (runtime reality):
- NOT_STARTED / FOUNDATION_ONLY / PARTIALLY_IMPLEMENTED / IMPLEMENTED / CLOSED
  (CLOSED is a governance/milestone finalization qualifier, not merely
  "architecture adopted")

MILESTONE_STATUS (governance finalization):
- CLOSED / NOT_STARTED / FUTURE

Conventions:
- CLOSED != merely "architecture adopted"
- FOUNDATION_ONLY != "implemented bounded foundation" (they are distinct;
  where a bounded V1 foundation IS implemented, the document says
  IMPLEMENTED_BOUNDED_V1 or IMPLEMENTED / CLOSED explicitly)
- Recommended model for Operation Model / OperationPlan Transaction /
  Revision Command: ARCH_STATUS = FROZEN, IMPL_STATUS = IMPLEMENTED,
  MILESTONE_STATUS = CLOSED (see §6.2)

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
- ARCHITECTURE_STATUS = FROZEN / CLOSED
- IMPLEMENTATION_STATUS = IMPLEMENTED / CLOSED
- Source: `docs/architecture/governance/operation-model-foundation-v1.md` (CLOSED)
- Repository truth: typed OperationDefinition; typed OperationRequest;
  OperationRequestResolver; OperationInstance; typed OperationParameters;
  deterministic parameter digest; OperationBatch; typed target contracts;
  15 frozen V1 operation definitions; OperationErrorCode typed vocabulary.

**OPERATION_PLAN_TRANSACTION_MODEL_V1**
- ARCHITECTURE_STATUS = FROZEN / CLOSED
- IMPLEMENTATION_STATUS = IMPLEMENTED / CLOSED
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
- ARCHITECTURE_STATUS = FROZEN / CLOSED
- IMPLEMENTATION_STATUS = IMPLEMENTED / CLOSED
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
AFFECTED_EXISTING, AFFECTED_MILESTONES, AFFECTED_CONSTRAINTS,
REQUIRED_EVIDENCE_DELTA, TRACEABILITY_DELTA, SOURCE_DOCUMENTS.

AFFECTED_CONSTRAINTS values: explicit existing Constraint IDs where they
already exist; otherwise GOVERNANCE_ONLY / FUTURE_CONSTRAINT_PROJECTION_REQUIRED
/ NONE / NOT_YET_ASSIGNED. No stable ConstraintIds are invented (the
Constraint Kernel has not assigned them); NOT_YET_ASSIGNED is preferred to
fabricated precision.

TRACEABILITY_DELTA values: NONE / SOURCE_LINK_ADDED / STATUS_CORRECTED /
AUTHORITY_RELATION_REFINED / FUTURE_CONSTRAINT_TRACE_REQUIRED /
EXISTING_DECISION_INTEGRATED / IMPLEMENTATION_STATUS_CORRECTED.

EXACT_FROZEN_DECISION_ID_OR_EXPLICIT_ALIAS_V1 — ADOPTED. Frozen decision IDs
are used verbatim; no silent shortening. Where the V2 document needed an
umbrella decision ID, it is explicitly marked ADD with its composition
relation (see §22.1 alias register; zero unregistered near-synonyms).

| DECISION_ID | UPDATE_TYPE | ARCH_LAYER | ARCH_STATUS | IMPL_STATUS | AFFECTED_EXISTING | AFFECTED_MILESTONES | AFFECTED_CONSTRAINTS | REQUIRED_EVIDENCE_DELTA | TRACEABILITY_DELTA | SOURCE_DOCUMENTS |
|---|---|---|---|---|---|---|---|---|---|---|
| ONE_CANONICAL_CORE_MANY_ENTITLED_PRODUCT_SURFACES_V1 | ADD | Platform model | ADOPTED | FOUNDATION_ONLY | — | all | FUTURE_CONSTRAINT_PROJECTION_REQUIRED | none (governance) | EXISTING_DECISION_INTEGRATED | this document §1 |
| EXTERNAL_REVISION_BACKEND_FIRST_V1 | REFINE | Canonical Semantics | FROZEN | IMPLEMENTED | JGit mechanics | #13 | NOT_YET_ASSIGNED | none | SOURCE_LINK_ADDED | roadmap-13; core-rebalancing §1 |
| TIMELINE_IS_COMPOSITION_REVISION_AND_MERGE_AUTHORITY_V1 | REFINE | Canonical Semantics | FROZEN | IMPLEMENTED | Timeline Git | #14 | NOT_YET_ASSIGNED | none | SOURCE_LINK_ADDED | roadmap-14; core-rebalancing §1 |
| EFFECT_SEMANTIC_SNAPSHOT_PINNED_BY_TIMELINE_REVISION_V1 | REFINE | Canonical Semantics | FROZEN | IMPLEMENTED | Effect authority | #20 | NOT_YET_ASSIGNED | FCV (done) | EXISTING_DECISION_INTEGRATED | roadmap-20 Option B chain |
| NO_LEGACY_EFFECT_AUTHORITY_AFTER_ROADMAP20_V1 | REFINE | Canonical Semantics | FROZEN | IMPLEMENTED | legacy Effect | #20 | NOT_YET_ASSIGNED | FCV (done) | EXISTING_DECISION_INTEGRATED | roadmap-20 clean-forward |
| CAPABILITY_AUTHORITY_MODEL_V1 | REFINE | Capability | FROZEN | IMPLEMENTED | CapabilityRegistry/PluginRegistry | #16 | NOT_YET_ASSIGNED | none | SOURCE_LINK_ADDED | roadmap-16 |
| EFFECTIVE_CAPABILITY_MODEL_V1 | ADD | Capability | ADOPTED | FOUNDATION_ONLY | workflow capability | #16 | FUTURE_CONSTRAINT_PROJECTION_REQUIRED | future | AUTHORITY_RELATION_REFINED | this document §5.3 |
| OPERATION_MODEL_FOUNDATION_V1 | REFINE | Operation | FROZEN | IMPLEMENTED / CLOSED | operation model | #19/OPM | NOT_YET_ASSIGNED | real-PG evidence (done) | IMPLEMENTATION_STATUS_CORRECTED | operation-model-foundation-v1.md |
| OPERATION_PLAN_TRANSACTION_MODEL_V1 | REFINE | Operation | FROZEN | IMPLEMENTED / CLOSED | plan/apply boundary | OPTM | NOT_YET_ASSIGNED | real-PG evidence (done) | IMPLEMENTATION_STATUS_CORRECTED | operation-plan-transaction-model-v1.md |
| REVISION_COMMAND_MODEL_V1 | ADD | Operation | FROZEN | IMPLEMENTED / CLOSED | revision command boundary | RCM | NOT_YET_ASSIGNED | real-PG evidence (done) | EXISTING_DECISION_INTEGRATED | revision-command-model-v1.md |
| UNIFIED_CONSTRAINT_AND_EVALUATION_ARCHITECTURE_V1 | ADD | Constraint/Evidence | ADOPTED | FOUNDATION_ONLY | — | future | FUTURE_CONSTRAINT_PROJECTION_REQUIRED | future | EXISTING_DECISION_INTEGRATED | this document §7.1 |
| CANONICAL_CONSTRAINT_KERNEL_V1 | ADD | Constraint/Evidence | ADOPTED | NOT_STARTED | — | future | FUTURE_CONSTRAINT_PROJECTION_REQUIRED | future | FUTURE_CONSTRAINT_TRACE_REQUIRED | this document §7.2 |
| EVIDENCE_MODEL_FOUNDATION_V1 | ADD | Constraint/Evidence | ADOPTED | FOUNDATION_ONLY | FCV/gates as projections | future | FUTURE_CONSTRAINT_PROJECTION_REQUIRED | future | FUTURE_CONSTRAINT_TRACE_REQUIRED | this document §7.3 |
| FORMAL_METHODS_PROGRESSIVE_ADOPTION_ROADMAP_V1 | ADD | Formal Methods | ADOPTED | NOT_STARTED | — | future | FUTURE_CONSTRAINT_PROJECTION_REQUIRED | future | EXISTING_DECISION_INTEGRATED | this document §7.5 |
| LEAN_FIRST_FORMAL_SEMANTIC_KERNEL_V1 | ADD | Formal Methods | ADOPTED | NOT_STARTED | — | future | FUTURE_CONSTRAINT_PROJECTION_REQUIRED | future | EXISTING_DECISION_INTEGRATED | this document §7.5 |
| RENDERPLAN_LOGICAL_PLANNING_AUTHORITY_V1 | REFINE | Planning | FROZEN | IMPLEMENTED | RenderPlan/RenderGraph | #20 | NOT_YET_ASSIGNED | FCV (done) | EXISTING_DECISION_INTEGRATED | roadmap-20 contract |
| ROADMAP_ALGEBRAIC_SEMANTIC_OPTIMIZATION_AMENDMENT_V1 | ADD | Planning | ADOPTED | FOUNDATION_ONLY | — | future | FUTURE_CONSTRAINT_PROJECTION_REQUIRED | future | EXISTING_DECISION_INTEGRATED | this document §8.2 |
| COST_OPTIMIZATION_ONLY_OVER_PROVEN_LEGAL_PLAN_SPACE_V1 | ADD | Planning | ADOPTED | NOT_STARTED | — | future | FUTURE_CONSTRAINT_PROJECTION_REQUIRED | future | EXISTING_DECISION_INTEGRATED | this document §8.2 |
| PROVIDER_EXECUTES_NOT_DEFINES_SEMANTICS_V1 | REFINE | Execution | FROZEN | IMPLEMENTED | FFmpeg adapter | #13 | NOT_YET_ASSIGNED | none | SOURCE_LINK_ADDED | core-rebalancing §1 |
| INFINITE_CANVAS_AND_VISUAL_WORKFLOW_AS_PRODUCT_SURFACES_V1 | ADD | Product Surfaces | ADOPTED | NOT_STARTED | — | future | FUTURE_CONSTRAINT_PROJECTION_REQUIRED | future | EXISTING_DECISION_INTEGRATED | this document §10.1 |
| WORKFLOW_OWNS_PROCESS_TIMELINE_OWNS_COMPOSITION_V1 | REFINE | Product Surfaces | FROZEN | PARTIALLY_IMPLEMENTED | workflow/timeline | #19 | NOT_YET_ASSIGNED | none | SOURCE_LINK_ADDED | core-rebalancing §8 |
| GRAPHQL_IS_APPLICATION_QUERY_PROJECTION_AND_COMMAND_TRANSPORT_NOT_DOMAIN_AUTHORITY_V1 | ADD | Product Surfaces | ADOPTED | DEFERRED | — | future | FUTURE_CONSTRAINT_PROJECTION_REQUIRED | future | EXISTING_DECISION_INTEGRATED | this document §10.4 |
| POSTGRES_EXTENSION_IS_INFRASTRUCTURE_CAPABILITY_NOT_DOMAIN_AUTHORITY_V1 | REFINE | Persistence | FROZEN | IMPLEMENTED | PG extensions | GCR-5/6 | NOT_YET_ASSIGNED | none | SOURCE_LINK_ADDED | gcr-5-gcr-6 |
| PROVENANCE_LINEAGE_V1 | ADD | Persistence | ADOPTED | FOUNDATION_ONLY | — | future | FUTURE_CONSTRAINT_PROJECTION_REQUIRED | future | EXISTING_DECISION_INTEGRATED | this document §11.3 |
| LANGUAGE_NEUTRAL_CONTRACT_POLYGLOT_IMPLEMENTATION_SINGLE_SEMANTIC_AUTHORITY_V1 | ADD | Polyglot | ADOPTED | FOUNDATION_ONLY | — | future | FUTURE_CONSTRAINT_PROJECTION_REQUIRED | future | EXISTING_DECISION_INTEGRATED | this document §13.1 |
| EVIDENCE_ACCOUNTING_MUST_MATCH_ACTUAL_TEST_TARGET_AND_EXECUTION_SCOPE_V1 | REFINE | Governance | ADOPTED | IMPLEMENTED (governance) | #20 evidence correction | #20 | FUTURE_CONSTRAINT_PROJECTION_REQUIRED | governance (done) | IMPLEMENTATION_STATUS_CORRECTED | roadmap-20-final-independent-acceptance-evidence-correction |

### 22.1 Decision ID alias / composition register

| V2_ID | RELATION | CANONICAL_SOURCE_DECISION_IDS | STATUS |
|---|---|---|---|
| (none) | — | — | zero aliases required |

The traceability table above uses exact frozen decision IDs verbatim. No
shorthand aliases are introduced. (Previous V2 draft shorthands
`GRAPHQL_IS_PROJECTION_AND_COMMAND_TRANSPORT_V1` and
`POSTGRES_EXTENSION_IS_INFRASTRUCTURE_NOT_DOMAIN_AUTHORITY_V1` were corrected
to the exact frozen IDs; no ambiguous near-synonyms remain.)

## 23. Supersession register

No prior adopted decision is SUPERSEDED by this consolidation. All entries
are ADD or REFINE. (GCR-1..GCR-6 decisions remain authoritative; Checkpoint A
frozen requirements remain authoritative; Roadmap #13-#20 frozen contracts
remain authoritative.)

## 24. Contradiction review

| Authority pair | Check | Resolution |
|---|---|---|
| Timeline vs Effect authority | consistent | Effect pinned BY Timeline revision; distinct identities |
| CapabilityRegistry vs PluginRegistry | consistent | separate authority axes (§5.2) |
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

UNRESOLVED_CONTRADICTIONS = 0 (18 pairs checked, all consistent).
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

## 26. ARV2 correction checklist (V2-C1..C4 closure)

| Check | Result |
|---|---|
| ARV2-C01 Operation Model IMPLEMENTED/CLOSED truth | PASS (§6.2) |
| ARV2-C02 OperationPlan Transaction IMPLEMENTED/CLOSED truth | PASS (§6.2) |
| ARV2-C03 Revision Command Model integrated | PASS (§6.2) |
| ARV2-C04 no false post-#20 Operation-foundation future claim | PASS (§18, §19) |
| ARV2-C05 current implementation frontier corrected | PASS (§6.3) |
| ARV2-C06 AFFECTED_CONSTRAINTS column present | PASS (§22) |
| ARV2-C07 TRACEABILITY_DELTA column present | PASS (§22) |
| ARV2-C08 all traceability rows populated | PASS (§22, 25 rows) |
| ARV2-C09 #1 through #28 each individually present | PASS (§14, 28 rows) |
| ARV2-C10 no milestone renumbering | PASS (§14) |
| ARV2-C11 no #29/#30 | PASS (§14) |
| ARV2-C12 unknown milestone data explicitly UNKNOWN | PASS (§14 note) |
| ARV2-C13 exact frozen decision IDs used | PASS (§22, §22.1) |
| ARV2-C14 any aliases explicitly registered | PASS (§22.1, zero aliases) |
| ARV2-C15 supersession register explicit | PASS (§23) |
| ARV2-C16 unresolved contradictions = 0 | PASS (§24, 18 pairs) |
| ARV2-C17 architecture status vs implementation status separated | PASS (§3, §6.2, §8) |
| ARV2-C18 #21/#22 still NOT STARTED | PASS (§14) |
| ARV2-C19 next epoch authorization still NO | PASS (§17, §19) |
| ARV2-C20 docs-only diff | PASS (§15 validation) |
| ARV2-C21 main unchanged | PASS (§15 validation) |
| ARV2-C22 append-forward history preserved | PASS (§15 validation) |

**ARV2_CORRECTION = 22/22 PASS**
