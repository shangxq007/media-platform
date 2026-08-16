# GCR-4 GREENFIELD COMPATIBILITY-MIGRATION-MODULE DELETION — PUBLICATION V1

| Field | Value |
|-------|-------|
| STATUS | PUBLISHED |
| BASE_SHA | 1dda8300a252926ee15659c39fce3d0c556237f9 |
| BASE_TREE | f37c496c5cf38d8f4b2be60f66a711de2ab99bb7 |
| BRANCH | agent/gcr4-compatibility-module-deletion |
| CANDIDATE_SHA | 3738b084a03a2b887239d39d1aba9f2083c051a1 |
| CANDIDATE_TREE | efb086ea4cad0f523a17d141a2ff4183ef377ade |
| FCV | GCR4_COMPATIBILITY_DELETION_FINAL_FCV = PASS |
| NEXT_AUTHORIZED_ACTION | GCR-1 |

## Purpose

Wave 1 of the post-Roadmap-19 Greenfield Core Canonicalization. Two
responsibilities only: (A) delete the unshipped speculative
compatibility-migration-module and all residue whose sole purpose was that
obsolete compatibility system; (B) publish the frozen
CORE_ARCHITECTURE_REBALANCING_CONTRACTS_V1 governance record.

## Reality reconfirmation (pre-deletion)

| Counter | Value |
|---------|-------|
| PRODUCTION_CALLER_COUNT | 0 |
| REAL_SHIPPED_COMPATIBILITY_REQUIREMENT_COUNT | 0 |
| CONTROLLER_COUNT | 1 (MigrationController @RestController /api/internal/migrations) |
| CONTROLLER_ENDPOINT_COUNT | 2 (/dry-run, /run) |
| RUNTIME_BEAN_COUNT | 7 (2 @Service + 1 @RestController + 4 @Component) |
| ADAPTER_COUNT | 5 (4 implementations + 1 interface; WasmMigrationAdapter dead — not injected, throws UnsupportedOperationException) |
| DATABASE_DEPENDENCY_COUNT | 0 |
| FLYWAY_DEPENDENCY_COUNT | 0 |
| CONFIG_PROPERTY_COUNT | 0 |
| TEST_COUNT | 1 (MigrationServiceTest) |
| BUILD_DEPENDENT_MODULE_COUNT | 1 (platform-app) |
| WASM_ADAPTER_COUNT | 1 |
| JAVA_MIGRATION_ADAPTER_COUNT | 1 |
| JSON_PATCH_ADAPTER_COUNT | 1 |
| SCRIPT_ADAPTER_COUNT | 1 |

Zero genuine shipped/external compatibility requirement → deletion authorized;
no ARCHITECTURE_ESCALATION required.

## Deletion scope

- Module removed: compatibility-migration-module (21 tracked files: 19
  production java + 1 test java + build.gradle.kts).
- Build references removed: settings.gradle.kts include (1), platform-app
  build.gradle.kts implementation dep (1).
- Test reference updated: ArtifactAuthorityTest retirement-candidate list (1).
- Active architecture docs corrected (11 files): system-architecture,
  module-architecture, business-modules, project-status, current-module-status,
  code-derived-system-overview, system-blueprint, current-known-gaps,
  backend-first-stabilization-plan, platform-fact-gathering-report,
  platform-architecture-assessment.
- Architecture maps: LikeC4 media-platform.likec4 PMPR description updated;
  architecture-map-drift-guard.py PMPR lists updated (also classified the 4
  R18/R19 pure-domain modules previously flagged unclassified — pre-existing
  guard debt, not GCR-4 scope);
  generated Modulith maps regenerated (module-compatibility.adoc/puml removed,
  43 modules × adoc+puml + components.puml).
- Historical records preserved unchanged: docs/archive/*, docs/review/*,
  docs/zh/*, governance decision records (mainline-readiness DI-018,
  first-production-release-zero-debt-policy DI-018 — both documented the
  REMOVE_BEFORE_FIRST_PRODUCTION_RELEASE decision this wave executes).

## Zero-residue counters (active tree, post-deletion)

| Counter | Value |
|---------|-------|
| COMPATIBILITY_MIGRATION_MODULE_COUNT | 0 |
| COMPATIBILITY_MIGRATION_PRODUCTION_TYPE_COUNT | 0 |
| COMPATIBILITY_MIGRATION_RUNTIME_BEAN_COUNT | 0 |
| COMPATIBILITY_MIGRATION_CONTROLLER_COUNT | 0 |
| COMPATIBILITY_MIGRATION_ENDPOINT_COUNT | 0 |
| COMPATIBILITY_MIGRATION_BUILD_REFERENCE_COUNT | 0 |
| SPECULATIVE_COMPATIBILITY_RUNTIME_COUNT | 0 |
| COMPATIBILITY_WRAPPER_COUNT | 0 |
| DEPRECATED_ALIAS_COUNT | 0 |
| DUAL_READ_COUNT | 0 |
| DUAL_WRITE_COUNT | 0 |
| SEMANTIC_FALLBACK_COUNT | 0 |
| ACTIVE_PARALLEL_AUTHORITY_COUNT | 0 |

## Contract publication

- File: docs/architecture/governance/core-architecture-rebalancing-contracts-v1.md
- STATUS = FROZEN; CHATGPT_REVIEW = PASS_WITH_MODIFICATIONS.
- Frozen contracts (final wording as published): ownership rebalancing,
  operation version authority, artifact authority (4), DB structural
  integrity, single canonical Flyway V1 (3), operational time, jOOQ,
  workflow capability boundary (4), temporal, runtime ports, domain concept
  creation, RenderPlan single authority (2), timed-text status,
  schema-evolution boundary (2). See the contract file for exact wording.

## Scope control

| Counter | Value |
|---------|-------|
| GCR1_IMPLEMENTATION_CHANGE_COUNT | 0 |
| GCR2_IMPLEMENTATION_CHANGE_COUNT | 0 |
| FLYWAY_CANONICALIZATION_CHANGE_COUNT | 0 |
| WORKFLOW_CANONICALIZATION_CHANGE_COUNT | 0 |
| TIMED_TEXT_IMPLEMENTATION_CHANGE_COUNT | 0 |
| ROADMAP20_IMPLEMENTATION_CHANGE_COUNT | 0 |

## Validation

See evidence package /tmp/GCR4_EVIDENCE/ (manifest SHA256 in evidence
package). Full suite XML, drift, Modulith, bootJar, PFIRR1, CI-equivalent,
credential scan results recorded in the final GCR-4 report.

## FCV

GCR4_FINAL_FCV = PASS (20/20 items, see final report).

## Final state

- GCR-4 = CLOSED
- COMPATIBILITY_MIGRATION_MODULE_DISPOSITION = DELETED
- CORE_ARCHITECTURE_REBALANCING_CONTRACTS_FROZEN = YES
- GCR-1_START_AUTHORIZED = YES
- GCR-2_START_AUTHORIZED = NO; GCR-5/6_START_AUTHORIZED = NO;
  TIMED_TEXT_START_AUTHORIZED = NO; CHECKPOINT_A = NOT_READY;
  ROADMAP_20_START_AUTHORIZED = NO
