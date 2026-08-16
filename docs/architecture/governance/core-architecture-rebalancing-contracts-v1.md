# CORE ARCHITECTURE REBALANCING CONTRACTS V1

**STATUS = FROZEN**

| Field | Value |
|-------|-------|
| STATUS | FROZEN |
| BASE_REPOSITORY_REALITY | CORE_ARCHITECTURE_REBALANCING_REVIEW_V1 |
| CHATGPT_REVIEW | PASS_WITH_MODIFICATIONS |
| FROZEN_AT_SHA | 1dda8300a252926ee15659c39fce3d0c556237f9 (GCR-4 base) |
| FROZEN_AT_TREE | f37c496c5cf38d8f4b2be60f66a711de2ab99bb7 |
| FROZEN_BY | CHATGPT_CORE_ARCHITECTURE_REBALANCING_REVIEW = PASS_WITH_MODIFICATIONS |
| NEXT_AUTHORIZED | GCR-1 (after GCR-4 closes) |

Authority order (highest first):

1. ChatGPT frozen architecture decisions (this document)
2. accepted Roadmap #13–#19 canonical contracts
3. repository reality
4. CORE_ARCHITECTURE_REBALANCING_REVIEW_V1 report
5. older historical architecture records

---

## 1. Timeline / Operation / Render ownership

**CORE_TIMELINE_OPERATION_RENDER_OWNERSHIP_REBALANCING_V1 — FROZEN**

- Timeline = canonical media composition authority.
- Operation = platform-wide semantic mutation model.
- Render = downstream render planning/execution concern.
- Timeline and Operation are NOT Render subdomains.
- Roadmap #20 prerequisite: physical authority rebalancing MUST be completed
  before Roadmap #20.
- GCR-1 priority: P0. GCR-1_BLOCKS_ROADMAP_20 = YES.
- Frozen implementation target topology (unless GCR-1 encounters a real
  dependency-cycle blocker requiring architecture escalation):

```
Media / Audio / FontText / ColorImage / Artifact
                    ↓
              timeline-module
                    ↓
             operation-module
                    ↓
               render-module
                    ↓
              execution fabric
```

## 2. Operation version authority

**CORE_OPERATION_VERSION_CONTRACT_MUST_NOT_DEPEND_ON_EXTENSION_DOMAIN_V1 — FROZEN**

**OPERATION_DEFINITION_VERSION_IS_OPERATION_DOMAIN_VALUE_V1 — FROZEN**

- Target type: `OperationDefinitionVersion`, owned by operation-module.
- Meaning: the semantic contract version of an OperationDefinition.
- It is NOT: plugin ContractVersion, capability version, software
  ReleaseVersion, schema version.
- Do NOT move this to a generic shared version god type without new evidence.

## 3. Artifact authority

**ARTIFACT_REFERENCE_AUTHORITY_RECONCILIATION_V1 — FROZEN**

**ARTIFACT_IDENTITY_AND_STORAGE_LOCATION_ARE_DISTINCT_V1 — FROZEN**

**CONTENT_DIGEST_IS_INTEGRITY_NOT_COMPETING_ARTIFACT_IDENTITY_V1 — FROZEN**

**ARTIFACT_CATALOG_IS_PROJECTION_NOT_ARTIFACT_AUTHORITY_V1 — FROZEN**

- Final target concepts: Artifact, ArtifactId, ContentDigest,
  ArtifactReplicaBinding / typed StorageReference.
- ArtifactId = canonical immutable identity.
- ContentDigest = integrity assertion.
- Storage location = infrastructure/storage binding.
- permissions/policy = separate authority.
- shared-kernel ArtifactRef: DISPOSITION = DELETE after caller migration.
- ArtifactCatalogEntry: projection/query model only.
- GCR-2 priority: P0. BLOCKS_ROADMAP_20 = YES.

## 4. Database structural integrity

**DATABASE_ENFORCES_LOCAL_STRUCTURAL_INTEGRITY_WHERE_ONE_SCHEMA_OWNS_BOTH_SIDES_V1 — FROZEN**

- For same bounded-context structural relations use PostgreSQL FK, UNIQUE,
  CHECK, NOT NULL where semantically appropriate.
- Cross-domain semantic references may remain application-validated if FK
  would create invalid bounded-context coupling.
- Implemented in GCR-5/6.

## 5. Single canonical Flyway V1

**SINGLE_CANONICAL_FLYWAY_V1_BEFORE_FIRST_EXTERNAL_RELEASE_V1 — FROZEN**

**PRE_RELEASE_SCHEMA_CHANGE_REWRITES_CANONICAL_V1_NOT_INCREMENTAL_MIGRATIONS_V1 — FROZEN**

**PRE_RELEASE_DATABASE_BOOTSTRAP_HAS_ONE_CANONICAL_SCHEMA_AUTHORITY_V1 — FROZEN**

- Final pre-release target:

```
FLYWAY_SCRIPT_COUNT = 1
FLYWAY_CANONICAL_SCRIPT = V1__initial_schema.sql
PRE_RELEASE_INCREMENTAL_MIGRATION_COUNT = 0
```

- Delete eventual V2+, .bak, orphan migrations, duplicate version files,
  invalid compose bootstrap references (in GCR-5/6, NOT GCR-4).

## 6. Operational time

**OPERATIONAL_INSTANT_IS_POSTGRES_TIMESTAMPTZ_V1 — FROZEN**

- Media semantic time: typed Rational / MediaTime.
- Operational absolute time: Java Instant ↔ PostgreSQL timestamptz.
- LocalDateTime: only genuine human-local calendar semantics.
- Migration happens in GCR-5/6.

## 7. jOOQ

**JOOQ_IS_GENERATED_SCHEMA_PROJECTION_NOT_SCHEMA_AUTHORITY_V1 — FROZEN**

- Canonical DB authority: V1__initial_schema.sql.
- jOOQ classes must be regenerated from canonical schema.
- jOOQ generated definitions must never constrain or silently override
  canonical schema semantics.

## 8. Workflow capability boundary

**WORKFLOW_DEPENDS_ON_CAPABILITY_REQUIREMENTS_NOT_PROVIDER_IDENTITIES_V1 — FROZEN**

**WORKFLOW_DEFINITION_STORES_CAPABILITY_REQUIREMENT_V1 — FROZEN**

**WORKFLOW_RUNTIME_RESOLVES_CAPABILITY_THROUGH_EFFECTIVE_CAPABILITY_VIEW_V1 — FROZEN**

**PROVIDER_IDENTITY_IS_EXECUTION_BINDING_AND_PROVENANCE_NOT_WORKFLOW_SEMANTICS_V1 — FROZEN**

- Target flow:

```
WorkflowDefinition
→ CapabilityRequirement
→ principal-filtered Effective Capability View
→ CapabilityRegistryPort
→ CapabilityImplementation
→ Provider / Worker
```

- Forbidden: WorkflowDefinition → provider ID; workflow activity →
  hardcoded ProviderRef("provider-1").
- Provider belongs to runtime binding/provenance.
- GCR-7 may begin in parallel only after core authority boundaries are
  stable; does not block Roadmap #20 if its contract remains frozen.

## 9. Temporal

**TEMPORAL_RUNTIME_ID_IS_INFRASTRUCTURE_BINDING_NOT_WORKFLOWRUN_IDENTITY_V1 — FROZEN**

- Canonical WorkflowRun identity is separate from Temporal workflow ID /
  run ID.
- Temporal remains replaceable durable orchestration HOW.

## 10. Runtime ports

**EXECUTION_RUNTIMES_INTERACT_THROUGH_PLATFORM_OWNED_PORTS_NOT_EACH_OTHERS_DOMAIN_APIS_V1 — FROZEN**

- Examples: Workflow → AgentRuntimePort → Embabel; Workflow →
  ExternalIntegrationPort → Camel; Workflow/Render → RenderExecutionPort →
  OpenCue.
- Exact Port names may evolve. The permanent rule: no execution-runtime mesh.

## 11. Domain concept creation

**NEW_DOMAIN_CONCEPT_REQUIRES_DISTINCT_AUTHORITY_LIFECYCLE_OR_INVARIANTS_V1 — FROZEN**

- A first-class domain aggregate/entity requires legitimate distinct:
  authority, identity, lifecycle, versioning, invariants, persistence,
  transaction boundary.
- Otherwise prefer: value object, projection, manifest, reference,
  configuration, adapter.

## 12. RenderPlan single authority

**ONE_CANONICAL_RENDERPLAN_AUTHORITY_V1 — FROZEN**

**RENDERPLAN_IS_NOT_OPERATIONPLAN_V1 — FROZEN**

- Before Roadmap #20: inventory all existing RenderPlan-like types, classify,
  rename/delete obsolete ambiguity.
- Roadmap #20 must create/evolve exactly ONE canonical RenderPlan authority.
- Do NOT allow a fourth ambiguous RenderPlan type.

## 13. Timed text status

**TIMEDTEXT_RENDER_BOUNDARY_FROZEN = NO**

- Roadmap #19 TextElement / FontText does NOT equal canonical TimedText.
- Title/Graphic Text: Timeline TextElement.
- Subtitle/Caption: future independent canonical TimedTextTrack /
  SubtitleTrack.
- Before Roadmap #20: TIMED_TEXT_PRESENTATION_FOUNDATION_V1 must at least
  complete repository reality recovery + canonical boundary contract freeze.

## 14. Schema evolution boundary (GCR-4 publication)

**PRE_RELEASE_COMPATIBILITY_RUNTIME_IS_FORBIDDEN_V1 — FROZEN**

**FUTURE_CANONICAL_SCHEMA_EVOLUTION_IS_TRIGGERED_BY_REAL_RELEASED_COMPATIBILITY_REQUIREMENTS_V1 — FROZEN**

- PRE_RELEASE: one canonical schema, zero compatibility migration runtime,
  rewrite canonical V1 directly.
- POST-FIRST-REAL-RELEASE: CANONICAL_SCHEMA_EVOLUTION_V1 may introduce
  explicit versioned migration mechanisms when real persisted/external
  compatibility exists.
- No replacement schema-evolution infrastructure is implemented at GCR-4.

---

## Checkpoint A — frozen requirements

| Requirement | Target |
|-------------|--------|
| CORE_AUTHORITY_STABLE | YES |
| TIMELINE_OPERATION_RENDER_OWNERSHIP_CANONICAL | YES |
| ARTIFACT_REFERENCE_AUTHORITY_SINGLE | YES |
| DATABASE_CANONICAL_V1_SINGLE | YES |
| FLYWAY_SCRIPT_COUNT | 1 |
| PRE_RELEASE_INCREMENTAL_MIGRATION_COUNT | 0 |
| COMPATIBILITY_RESIDUE | 0 |
| DB_OPERATIONAL_TIME_CANONICAL | YES |
| DB_LOCAL_STRUCTURAL_INTEGRITY | YES |
| OPERATIONPLAN_EXECUTION_BOUNDARY_FROZEN | YES |
| WORKFLOW_RECONCILIATION_DIRECTION_FROZEN | YES |
| TIMEDTEXT_RENDER_BOUNDARY_FROZEN | YES |
| ONE_CANONICAL_RENDERPLAN_PRECONDITION | YES |
| ROADMAP_20_INPUT_CONTRACTS_READY | YES |

Current: CHECKPOINT_A = NOT_READY.

## GCR order (frozen)

```
GCR-4 compatibility migration deletion + contract publication
  ↓
GCR-1 Timeline/Operation/Render ownership extraction
      + OperationDefinitionVersion + RenderPlan ambiguity cleanup
  ↓
GCR-2 Artifact authority canonicalization
  ↓
GCR-5/GCR-6 single canonical Flyway V1 + local DB integrity
          + operational timestamps + jOOQ regeneration + bootstrap cleanup
  ↓
TIMED_TEXT_PRESENTATION_FOUNDATION_V1 (reality recovery + boundary freeze)
  ↓
CHECKPOINT A
  ↓
ROADMAP #20
```

GCR-7 Workflow may begin in parallel only after core authority boundaries are
stable enough and must not interfere with the P0 critical path.

## Zero-residue targets (future gates)

```
RENDER_OWNED_TIMELINE_AUTHORITY_COUNT = 0
RENDER_OWNED_OPERATION_AUTHORITY_COUNT = 0
SHARED_LEGACY_ARTIFACT_REF_COUNT = 0
CORE_OPERATION_EXTENSION_VERSION_DEPENDENCY_COUNT = 0
SPECULATIVE_COMPATIBILITY_RUNTIME_COUNT = 0
FLYWAY_SCRIPT_COUNT = 1
PRE_RELEASE_INCREMENTAL_MIGRATION_COUNT = 0
LOCAL_STRUCTURAL_RELATION_WITHOUT_REQUIRED_FK_COUNT = 0
WORKFLOW_HARDCODED_PROVIDER_IDENTITY_COUNT = 0
WORKFLOW_DIRECT_PLUGINRUNTIME_CAPABILITY_EXECUTION_COUNT = 0
ACTIVE_PARALLEL_AUTHORITY_COUNT = 0
COMPATIBILITY_CODE_COUNT = 0
```

---

*Governance record. GCR-4 wave (compatibility-migration-module deletion) is the
first executing wave under this contract set. Contracts are FROZEN; future
GCR waves implement them without re-litigating the authority decisions.*
