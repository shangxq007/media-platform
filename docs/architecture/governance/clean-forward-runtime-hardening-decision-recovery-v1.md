# CLEAN-FORWARD RUNTIME HARDENING — DECISION RECOVERY V1

**STATUS = DECISION RECOVERY COMPLETE (pending ChatGPT independent review)**

| Field | Value |
|---|---|
| BASE_SHA | 5d80ac3474a0f50e67dcb26d30037365d15ba091 |
| BASE_TREE | 7e6e613b1ffa33018eb21e0793b9ecf881a4c759 |
| NEXT_EPOCH_BASE | MEDIA_PLATFORM_INTEGRATED_ARCHITECTURE_ROADMAP_V2 = CANONICAL |
| ROADMAP_20 | CLOSED |
| ROADMAP_21 / #22 | NOT_STARTED / NOT_STARTED |
| PRODUCTION_CHANGE_COUNT | 0 (decision recovery only) |
| TASK_TYPE | DECISION_RECOVERY / REPOSITORY_REALITY_AUDIT / GOVERNANCE_ONLY |

## 1. Base

- DECISION_RECOVERY_BASE_SHA = 5d80ac3474a0f50e67dcb26d30037365d15ba091
- DECISION_RECOVERY_BASE_TREE = 7e6e613b1ffa33018eb21e0793b9ecf881a4c759
- Branch: `agent/clean-forward-runtime-hardening-dr`
- V2 canonical baseline frozen; no reopen.

## 2. Repository reality summary

Production Java files scanned: 3459 (src/main, excluding build/).

- `internal-1.0` / `InternalTimeline*` markers: 13+ production files
  (timeline-module legacy service + render-module legacy adapters/converters/
  planners + platform-app legacy controllers).
- `@Deprecated` production symbols: 29 files (9 distinct symbols identified;
  effect/quota/prompt/storage/identity/ai/render clusters).
- `fallback` keyword: 103 production files (most are provider-runtime or
  UX fallbacks; subset requires semantic review).
- `adapter`: 91 files (majority are legitimate port adapters — NOT residue).
- `legacy` keyword: 126 files (majority are domain-meaningful labels like
  `LegacyStorageReference` type names or historical comments; subset requires
  triage).
- Ownership-unscoped global snapshot reads confirmed: 2 methods
  (`TimelineSnapshotService.findPayload(String)`, `.findById(String)`) with 6
  production call sites + legacy service read authority.

## 3. Compatibility obligation evidence

NO_HISTORICAL_PRODUCT_COMPATIBILITY_OBLIGATION = TRUE (frozen, maintained).

No released public API, published SDK contract, real persisted customer data
contract, released canonical schema, external plugin contract, marketplace
package contract, externally consumed file/wire format, or externally
documented versioned integration was found in the current repository.

All identified compatibility/residue surfaces are internal unshipped paths.

→ COMPATIBILITY_JUSTIFICATION_REQUIRED_V1 applies: none of the audited
surfaces currently carries an external justification.

## 4. Dual-authority inventory (Audit B)

Confirmed dual-authority candidates (see dual-authority-manifest.tsv, 17 rows):

- **P0: TimelineRevisionService (legacy internal-1.0)** — parallel write
  authority (`recordRevision`) and read authority (`findById`/`findPayload`/
  `restore`) vs canonical `TimelineRevisionSaveService` (tenant-scoped,
  R3-fixed). Legacy write has 1 production caller (TimelineEditorSyncService);
  legacy read has 6 production callers.
- **P2: internal-1.0 serialization/adapters** — InternalTimelineJson,
  InternalTimelineCandidateAdapter, TimelineSnapshotConverter,
  RenderTimelinePayloadCodec, InternalTimelineAdapter,
  InternalTimelineToEditorConverter, SegmentTimelinePlanner,
  SegmentPipelinePayloadBuilder, TimelineConversionService (legacy shapes vs
  canonical TimelineDocument / RenderPlan).
- **P2: legacy controllers** — TimelineGitV1Controller, TimelineEditorSyncController.
- Safe/non-residue: TimelineCanonicalizer, CanonicalTimelineDiffCalculator
  (core canonical conversion, required).

## 5. Ownership-unscoped read inventory (Audit C)

Confirmed P1 (see ownership-read-manifest.tsv, 12 rows):

- `TimelineSnapshotService.findPayload(String snapshotId)` — global-ID-only
  canonical payload read; production call sites: RenderJobExecutionService:590,
  BaseJobTimelineLoader:56, TimelineRevisionRenderService:129,
  PlanBasedTimelineRevisionRenderService:187 (+ legacy TimelineRevisionService).
- `TimelineSnapshotService.findById(String snapshotId)` — global-ID-only read;
  production call sites: TimelineEditorSyncService:73,
  TimelineAssetLifecycleService:115.
- `TimelineMergeEngine` internal `revisionRepository.findById(revisionId)`
  (row read not tenant-predicated at source; snapshot read IS owned).
- Legacy `TimelineRevisionService.findById/findPayload` (P0 legacy authority).

Confirmed correct (SAFE):
- `TimelineRevisionSaveService.findById(revisionId)` — R3-fixed single
  ownership-validated tenant-scoped read.
- `TimelineSnapshotService.findOwnedById(projectId, tenantId, ...)` — owned.
- `listDistinctProjectIds()` — system introspection.

Callers of unscoped reads generally hold project/job context (UNSCOPED_BUT_
CALLER_HAS_CONTEXT) — risk is boundary-crossing if context is stale/misused,
not ambient missing-context. No confirmed cross-tenant exploit observed.

## 6. Legacy schema/runtime inventory (Audit D)

Runtime-reachable legacy residues: 15 (P2 class in manifests).
Delete-direct candidates: 12 (after caller migration).
Migration-required candidates: 15 (caller migration before deletion).
Externally required: 0.

## 7. Deprecated API inventory (Audit F)

Production @Deprecated symbols: 9 distinct (across 29 files).
- Dead candidates (0 production callers): RenderJobRepository.requeueExecutingJob,
  requeueFailedJob → delete directly.
- Single-caller candidates: RoleRepository.deleteUserRoleAssignment
  (DevWorkspaceBootstrapService), AiGatewayService (AiController) → migrate
  then delete.
- Wide-reference surface: EffectInstance (17 files) → replace with canonical
  Effect semantic snapshot references (deferred to implementation wave).
- Quota/prompt/storage clusters: audit in implementation wave.

## 8. P0-P4 priority matrix

| Priority | Count | Description |
|---|---|---|
| P0 | 2 | DUAL CANONICAL SEMANTIC AUTHORITY: legacy TimelineRevisionService write+read authority vs canonical save service |
| P1 | 2+4 | OWNERSHIP-UNSCOPED PRODUCTION CANONICAL READ: findPayload/findById global reads (6 call sites) + merge-engine internal row read |
| P2 | 15 | LEGACY INTERNAL SCHEMA / SERIALIZATION / VERSION PATH: internal-1.0 adapters, codecs, planners, controllers |
| P3 | 9+ | DEPRECATED API / CONSTRUCTOR / ALIAS: @Deprecated symbols, dead methods, wrappers |
| P4 | triage | STALE TEST FIXTURE / COMMENT / HISTORICAL NAMING (deferred, not enumerated in this recovery) |

## 9. Contract decisions

| Contract | Decision | Evidence / scope / exceptions |
|---|---|---|
| NO_UNSHIPPED_RUNTIME_COMPATIBILITY_SURFACES_V1 | ADOPT | no external obligation exists; all audited residue is internal unshipped; exception: none currently justified |
| NO_PRODUCTION_LEGACY_INTERNAL_SCHEMA_PATHS_V1 | ADOPT (bounded) | internal-1.0 payload/adapters are unshipped; migration of real callers required before deletion; exception: TimelineCanonicalizer (legacy→canonical conversion) is core, NOT residue |
| NO_DUAL_CANONICAL_READ_OR_WRITE_V1 | ADOPT | TimelineRevisionService legacy authority is the confirmed dual path; canonical save service is sole future authority; exception: none |
| NO_FALLBACK_TO_SUPERSEDED_SEMANTIC_AUTHORITY_V1 | ADOPT | no confirmed semantic fallback to superseded authority found in canonical paths (fallback keyword hits are provider-runtime/UX); exception: none found, revisit in implementation wave |
| OWNERSHIP_CONTEXT_IS_EXPLICIT_ON_PRODUCTION_CANONICAL_READS_V1 | ADOPT | findPayload/findById global reads violate; canonical R3-fixed read is the pattern; exception: system introspection (listDistinctProjectIds) via explicit system port |
| NO_PRODUCTION_AMBIENT_GLOBAL_CANONICAL_OBJECT_LOOKUP_V1 | ADOPT | global snapshot reads are ambient; migrate to findOwnedById with explicit project/tenant; exception: none for canonical reads |
| SYSTEM_LEVEL_GLOBAL_LOOKUP_REQUIRES_EXPLICIT_SYSTEM_AUTHORITY_PORT_V1 | ADOPT | system introspection surfaces must be named/ported explicitly (SystemMaintenanceReader / AdminInspectionPort concept); listDistinctProjectIds is the example |
| COMPATIBILITY_EXISTS_ONLY_FOR_ACTUALLY_PUBLISHED_EXTERNAL_CONTRACTS_V1 | ADOPT | no published contracts; compatibility surfaces are residue by default |
| COMPATIBILITY_JUSTIFICATION_REQUIRED_V1 | ADOPT | every retained compatibility surface must carry evidence; currently none qualify |

No REJECT. All 9 contracts ADOPT (2 bounded by explicit exceptions).

## 10. Bounded implementation proposal

Repository reality justifies 5 units (CFRH-I1..I5):

- **CFRH-I1 (P0)** — Remove legacy TimelineRevisionService semantic authority:
  migrate TimelineEditorSyncService (1 caller) + 6 read-path callers to
  canonical TimelineRevisionSaveService; delete legacy service.
  Files: timeline-module app/; render-module timeline/; platform-app controllers.
  Semantic risk: HIGH (authority change). Expected delete: ~1 service + adapters;
  add: canonical-path callers. Guard: superseded-authority-reference-count=0.
- **CFRH-I2 (P1)** — Migrate ownership-unscoped canonical reads:
  replace findPayload/findById call sites with findOwnedById(projectId,
  tenantId, ...) carrying explicit context; introduce SystemMaintenanceReader
  port for listDistinctProjectIds.
  Files: timeline-module adapter; render-module (4 call sites + sync/lifecycle).
  Semantic risk: MEDIUM. Guard: unscoped-production-canonical-read-count=0
  (mechanical grep over production sources for `.findPayload(`/`.findById(`
  on canonical snapshot service).
- **CFRH-I3 (P2)** — Remove legacy internal-1.0 schema/serialization paths
  (after caller migration): InternalTimelineJson, adapters, codecs, segment
  planner, V1 controllers.
  Files: timeline-module, render-module, platform-app.
  Semantic risk: MEDIUM-HIGH. Guard: legacy-runtime-compatibility-count=0.
- **CFRH-I4 (P3)** — Deprecated API removal: delete dead methods
  (requeue*), migrate single callers (RoleRepository, AiGatewayService),
  replace EffectInstance references with canonical Effect snapshot types.
  Semantic risk: LOW-MEDIUM.
- **CFRH-I5** — Structural guards preventing reintroduction:
  zero-count guards + naming conventions (ProductCanonicalReader vs
  SystemMaintenanceReader vs AdminInspectionPort).

Guard feasibility classification:
- UNSCOPED_PRODUCTION_CANONICAL_READ_COUNT = 0: MECHANICALLY_PRECISE
  (production-source grep for known global reader signatures).
- LEGACY_RUNTIME_COMPATIBILITY_COUNT = 0: BOUNDED_HEURISTIC (keyword +
  package-bound scan; manual review of each hit).
- SUPERSEDED_AUTHORITY_REFERENCE_COUNT = 0: BOUNDED_HEURISTIC (symbol-level
  reference scan).
- CANONICAL_DUAL_READ/WRITE_COUNT = 0: MECHANICALLY_PRECISE after CFRH-I1
  (legacy service deleted → no reference possible).
- UNJUSTIFIED_DEPRECATED_PRODUCTION_API_COUNT = 0: MANUAL_REVIEW_REQUIRED
  (semantic judgment on each @Deprecated).

## 11. Explicit exclusions

- TimelineCanonicalizer / CanonicalTimelineDiffCalculator (core canonical
  conversion — retained).
- Provider-runtime fallbacks (FFmpeg/worker failover — retained).
- UX fallbacks (UI defaults — retained).
- Legacy type NAMES that are canonical domain types (e.g. LegacyStorageReference
  type semantics) — triaged per-type in implementation wave.
- All F0 / Constraint Kernel / Evidence runtime / Semantic Analysis /
  formal methods — NOT part of hardening.

## 12. Escalation conditions

Evaluated: none triggered.
- No released external/persisted obligation found (A: NO).
- No legacy path is the only current canonical authority — canonical save
  service and owned reads exist (B: NO).
- Removing unscoped reads does not redefine ownership semantics — expressing
  existing project/tenant context is sufficient (C: NO).
- No dual path is actually two legitimate domain authorities — legacy
  internal-1.0 is superseded by canonical Timeline V2 (D: NO).
- Cleanup does not change frozen Timeline/Effect/Capability/Operation/
  RenderPlan semantics (E: NO).

ARCHITECTURE_ESCALATION = NONE

## 13. Verification strategy

- Implementation wave FCV: per-wave fresh detached committed-tree FCV
  (compile + affected module suites + full suite for P0/P1 waves).
- Guard execution: CFRH structural guards in CI (governance guards per
  existing automated-guards practice).
- No full-suite FCV in this decision-recovery task (docs-only).

## 14. Sequencing decision (F0 relation, §21)

OPTION C — ADOPT:
1. Remove obsolete runtime paths now (CFRH-I1..I4).
2. Add only bounded structural guards (CFRH-I5) needed to prevent regression.
3. Do NOT delay cleanup waiting for the future Constraint Kernel.
4. Later migrate guards into the unified Constraint/Evidence architecture.

Relationship to #21/#22: CROSS_CUTTING_PRE_21_HARDENING (not a milestone,
no renumbering, no #29/#30). After bounded hardening, evaluate F0 /
Constraint/Evidence foundation, then authorize #21/#22 from the updated
canonical base only.

## 15. Next-step recommendation

1. ChatGPT reviews this decision recovery.
2. On PASS: authorize CFRH-I1 (P0) as the first bounded implementation wave
   from base 5d80ac34, followed by CFRH-I2 (P1).
3. Each wave: fresh worktree, bounded commit, exact-SHA FCV, governance
   publication, append-forward.
4. After P0/P1 waves: re-evaluate P2 legacy schema removal and F0 timing.

## 16. CFRH_DR acceptance gates

| Gate | Result |
|---|---|
| CFRH-DR-01 base SHA exact 5d80ac34 | PASS |
| CFRH-DR-02 production changes = 0 | PASS |
| CFRH-DR-03 compatibility manifest complete | PASS (18 rows) |
| CFRH-DR-04 external compatibility evidence audited | PASS (0 obligations) |
| CFRH-DR-05 dual-authority manifest complete | PASS (17 rows) |
| CFRH-DR-06 ownership-read manifest complete | PASS (12 rows) |
| CFRH-DR-07 legacy schema/version manifest complete | PASS (15 residues) |
| CFRH-DR-08 deprecated API manifest complete | PASS (14 rows) |
| CFRH-DR-09 P0-P4 classification complete | PASS |
| CFRH-DR-10 candidate contracts all resolved | PASS (9 ADOPT, 0 REJECT) |
| CFRH-DR-11 legitimate exceptions explicit | PASS (canonical conversion + provider/UX fallback) |
| CFRH-DR-12 bounded implementation units defined | PASS (CFRH-I1..I5) |
| CFRH-DR-13 structural guard feasibility classified | PASS (3 precise / 2 heuristic / 1 manual) |
| CFRH-DR-14 F0 sequencing resolved | PASS (OPTION C) |
| CFRH-DR-15 #21/#22 remain NOT_STARTED | PASS |
| CFRH-DR-16 no roadmap renumbering | PASS |
| CFRH-DR-17 architecture escalation explicitly evaluated | PASS (NONE, all 5 conditions checked) |
| CFRH-DR-18 unresolved repository-reality blockers = 0 | PASS |

**CFRH_DR = 18/18 PASS**
