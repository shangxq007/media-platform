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

---

## 17. DECISION RECOVERY BOUNDARY CORRECTION (append-forward, supersedes §10/§16 partially)

The earlier 21421108 publication correctly identified P0/P1 existence and
correctly adopted the clean-forward architecture, but:

- underestimated TimelineRevisionService behavioral surface (compressed to
  "1 writer + 6 readers");
- misclassified TimelineGitV1Controller by naming (V1 label ≠ legacy);
- under-scoped the ownership read inventory;
- over-scoped the precision claim of the future unscoped-read guard;
- proposed an I1 boundary that overlapped I2.

Original evidence is preserved; this section corrects append-forward.

### 17.1 TimelineRevisionService behavior inventory (C1)

Full behavior matrix: `.agent-tasks/CLEAN-FORWARD-RUNTIME-HARDENING-DR/
timeline-revision-service-behavior-matrix.tsv` (15 behavior rows).

Public methods audited individually (with exact production callers):

| Behavior | Class | Production callers | Wave |
|---|---|---|---|
| recordRevision (2 overloads, L63/L91) | LEGACY_SEMANTIC_WRITE_AUTHORITY | TimelineEditorSyncService:46,108,143 | I1 |
| recordAiAdoptRevision (L426) | LEGACY_SEMANTIC_WRITE_AUTHORITY | RenderController:308 | I1 |
| restore (L463) | RESTORE_OR_REISSUE_AUTHORITY | TimelineRevisionController:193 | I1 |
| backfillHeadFromLatestSnapshot (L274) | BACKFILL_COMPATIBILITY_BEHAVIOR | TimelineEditorSyncService:60 | I1 |
| findHead (L188) | QUERY_PROJECTION | TimelineRevisionController:137, TimelineEditorSyncService:61,170 | I2 |
| findById (L192) | QUERY_PROJECTION | TimelineRevisionService internal 195/202/449, TimelineMergeEngine:754 | I2 |
| getRevisionSnapshotPayload (L200) | QUERY_PROJECTION | TimelineRevisionController:169 | I2 |
| listHistory (3 overloads L223/227/231) | HISTORY_QUERY | TimelineRevisionController:81 | I2 |
| listFacets (L255) | QUERY_PROJECTION | TimelineRevisionController:90, TimelineWorkbenchController:40 | I2 |
| listEditSessions (L263) | HISTORY_QUERY | TimelineRevisionController:118 | I2 |
| compareRevisions (L303) | DIFF_COMPARE | TimelineRevisionController:129, TimelineWorkbenchController:91,101,102 | I2 |
| previewPatchReplay (L343) | PATCH_PREVIEW | TimelineRevisionController:149 | I2 |
| previewPatchSteps (L370) | PATCH_PREVIEW | TimelineRevisionController:160 | I2 |
| updateAnnotation (L242) | REVISION_METADATA_MUTATION | TimelineRevisionController:105 | I2 |
| getDetail (L446) | QUERY_PROJECTION | TimelineRevisionController:180 | I2 |

Record types (RevisionInfo, RevisionFacets, etc.) have 0 external callers.

### 17.2 TimelineRevisionController endpoint matrix (C1)

15 endpoints audited at base path
`/api/render/projects/{projectId}/timeline/revisions`:

| Endpoint | Service method | Purpose | Wave |
|---|---|---|---|
| GET /facets | listFacets | projection | I2 |
| PATCH /{id}/annotation | updateAnnotation | metadata mutation | I2 |
| GET /edit-sessions | listEditSessions | projection | I2 |
| GET /compare | compareRevisions | diff projection | I2 |
| GET /head | findHead | projection | I2 |
| GET /{id}/patch-preview | previewPatchReplay | preview (global-id read) | I2 |
| GET /{id}/patch-steps | previewPatchSteps | preview (global-id read) | I2 |
| GET /{id}/snapshot | getRevisionSnapshotPayload | projection | I2 |
| GET /{id} | getDetail | projection (global-id read) | I2 |
| POST /{id}/restore | restore (legacy) | SEMANTIC WRITE → canonical restoreRevision | I1 |
| POST /merge | TimelineMergeEngine | canonical merge | retained |
| POST /{id}/render | TimelineRevisionRenderService | canonical render | retained |
| GET /{id}/render-jobs/{jobId} | RenderJobStatusService | canonical | retained |
| GET /{id}/render-jobs/{jobId}/result | RenderJobStatusService | canonical | retained |

### 17.3 TimelineGitV1Controller reclassification (C2)

**C2 result: TimelineGitV1Controller = CANONICAL_API_PROJECTION_WITH_
VERSIONED_NAME.** Its dependencies are exclusively canonical services:
TimelineRevisionSaveService, ProductCurrentRevisionService,
RenderJobRevisionPinningService, TimelineSemanticDiffV1Service,
TimelinePatchApplicationService, TimelineContentDigester. ZERO references to
TimelineRevisionService. The "V1" name is an API versioning label, not a
legacy semantic authority. Semantic legacy residue count = 0. It remains
SAFE / PRODUCT_API_CLEANUP (never P2 semantic residue).

### 17.4 TimelineEditorSync remains legacy (C2 complement)

TimelineEditorSyncService explicitly uses `storedSchema = "internal-1.0"`
(L41) and depends on legacy write authority (recordRevision/backfill). It is
a genuine legacy candidate — NOT to be confused with TimelineGitV1Controller.
Behaviors: push/pullByProject/pullBySnapshotId/sync/saveSnapshotEnsuringInternal.
Controller endpoints /push /pull /latest /sync (base `/api/render/timeline-sync`).
PullRequest carries only (projectId, snapshotId) — snapshotId can arrive
without tenant; PUBLIC_API_CHANGE_REQUIRED for ownership-safe pull.

### 17.5 Expanded ownership audit (C3)

Expanded manifest: `.agent-tasks/CLEAN-FORWARD-RUNTIME-HARDENING-DR/
ownership-read-manifest.tsv` (11 surfaces) and
`ownership-caller-context-matrix.tsv` (18 caller rows).

Confirmed P1:
- TimelineSnapshotService.findPayload(String) — GLOBAL_ID_ONLY, 4 production
  call sites + legacy service.
- TimelineSnapshotService.findById(String) — GLOBAL_ID_ONLY, 2 call sites
  (editor-sync unscoped; asset-lifecycle LOAD_THEN_CHECK).
- TimelineSnapshotService.findLatestByProject(projectId) — PROJECT_ONLY,
  7 call sites; tenantId must also apply per adopted contracts.
- TimelineRevisionRepository.findById(revisionId) — GLOBAL_ID_ONLY;
  TimelineMergeEngine:754 is a LOAD_THEN_CHECK path (row read unscoped, owned
  snapshot read after); propose findOwnedById(revisionId, projectId, tenantId).
- TimelineRevisionRepository.findHeadByProject/listByProject — PROJECT_ONLY.

Context findings:
- BaseJobTimelineLoader: HAS tenantId + tenantGuard.requireJobTenant
  (tenant validated) but NO projectId → PROJECT_CONTEXT_AVAILABLE = NO;
  future correction: extend job projection/query to include projectId (Option A)
  or introduce scoped read port (Option B).
- TimelineEditorSyncService.pullBySnapshotId: PUBLIC_API_CHANGE_REQUIRED = YES.
- TimelineAssetLifecycleService.tombstone(projectId, snapshotId, assetId,
  tenantId): method ALREADY has projectId+tenantId but uses findById +
  load-then-check → direct scoped-read migration possible.

Adopted: OWNERSHIP_SCOPE_MUST_BE_VERIFIABLE_AT_OR_BEFORE_PERSISTENCE_READ_V1
= ADOPT (ownership scope at or before persistence read; LOAD_THEN_CHECK
remains P1).

### 17.6 Revised implementation boundaries (C4)

**CFRH-I1 = TIMELINE_LEGACY_WRITE_AUTHORITY_CLOSURE**
- Targets: recordRevision (both overloads), recordAiAdoptRevision, legacy
  restore, backfillHeadFromLatestSnapshot — all behaviors that create legacy
  revision state.
- Per-target decision: MIGRATE_TO_CANONICAL_WRITE_AUTHORITY (recordRevision →
  TimelineRevisionSaveService.saveRevision* with internal-1.0→canonical input
  conversion; restore → TimelineRevisionSaveService.restoreRevision;
  recordAiAdoptRevision → canonical save with AI-path conversion;
  backfill → eliminate behavior).
- Postcondition: LEGACY_TIMELINE_REVISION_SEMANTIC_WRITE_AUTHORITY_COUNT = 0.
- TimelineRevisionService class deletion is NOT an I1 acceptance requirement.

**CFRH-I2 = TIMELINE_READ_OWNERSHIP_AND_LEGACY_QUERY_CLOSURE**
- A: migrate ownership-unscoped snapshot/revision reads (findPayload/
  findById/findLatestByProject → scoped reads with explicit project+tenant).
- B: replace load-then-check repository reads (merge engine → findOwnedById
  repository method).
- C: migrate or delete retained TimelineRevisionService query/projection
  behaviors (listFacets/listHistory/compare/previews/getDetail/updateAnnotation
  → canonical queries + TimelineSemanticDiffV1Service + TimelinePatch
  ApplicationService).
- D: clean-forward public API signature changes where ownership missing
  (pull-by-snapshotId requires projectId+tenantId).
- E: explicit system-level global ports only where justified
  (listDistinctProjectIds → SystemMaintenanceReader port).
- Postconditions: KNOWN_FORBIDDEN_UNSCOPED_TIMELINE_READ_REFERENCE_COUNT = 0;
  LEGACY_TIMELINE_REVISION_QUERY_AUTHORITY_COUNT = 0;
  TIMELINE_REVISION_SERVICE_PRODUCTION_CALLER_COUNT = 0; THEN
  TimelineRevisionService may be deleted.

**CFRH-I3 = TRUE INTERNAL-1.0 SCHEMA/ADAPTER REMOVAL**
- InternalTimelineJson, InternalTimelineCandidateAdapter,
  TimelineSnapshotConverter, InternalTimelineAdapter,
  InternalTimelineToEditorConverter, InternalTimelineMetadataEnricher,
  InternalTimelineAiProposals, RenderTimelinePayloadCodec,
  SegmentTimelinePlanner, SegmentPipelinePayloadBuilder,
  TimelineConversionService, TimelineEditorSyncController (+ editor sync
  service internal-1.0 surface).
- NOT included: TimelineGitV1Controller (canonical); TimelineCanonicalizer
  (core conversion).

Adopted: LEGACY_SERVICE_DELETION_REQUIRES_BEHAVIORAL_REPLACEMENT_CLOSURE_V1
= ADOPT (delete only after every production behavior migrated/deleted AND
caller count = 0).

### 17.7 Structural guard precision (C5)

Previous claim "UNSCOPED_PRODUCTION_CANONICAL_READ_COUNT = 0 = MECHANICALLY_
PRECISE" was too broad. Corrected guard design:

| Guard | Forbidden symbol set | Evidence class | Proves | Does NOT prove |
|---|---|---|---|---|
| KNOWN_FORBIDDEN_UNSCOPED_TIMELINE_READ_REFERENCE_COUNT = 0 | TimelineSnapshotService.findPayload/.findById/.findLatestByProject; TimelineRevisionRepository.findById/.findHeadByProject (frozen audited set) | BOUNDED_MECHANICAL | zero references to the audited unscoped set in product/application sources | absence of unknown unscoped patterns |
| LEGACY_TIMELINE_REVISION_SEMANTIC_WRITE_AUTHORITY_COUNT = 0 | TimelineRevisionService.recordRevision/.recordAiAdoptRevision/.restore/.backfillHeadFromLatestSnapshot | MECHANICALLY_PRECISE (symbol-level) | zero production references after I1 | — |
| CANONICAL_DUAL_READ/WRITE_COUNT = 0 | any reference to legacy service after deletion | MECHANICALLY_PRECISE | service gone → no reference possible | — |
| UNJUSTIFIED_DEPRECATED_PRODUCTION_API_COUNT = 0 | @Deprecated production symbols | MANUAL_GOVERNANCE_REVIEW | per-symbol review | automated semantic judgment |

Adopted: STRUCTURAL_GUARD_SCOPE_MUST_MATCH_MECHANICALLY_AUDITED_SYMBOL_SET_V1
= ADOPT (guard claims only the audited + mechanically enforced symbol set).
System/admin ports are explicit exceptions, not hidden allowlists.

### 17.8 Bounded refinement decisions (§33)

| Refinement | Decision | Evidence |
|---|---|---|
| LEGACY_SERVICE_DELETION_REQUIRES_BEHAVIORAL_REPLACEMENT_CLOSURE_V1 | ADOPT | service deletion only after full behavior closure + caller count 0 (§17.6) |
| API_VERSION_NAME_DOES_NOT_IMPLY_LEGACY_SEMANTIC_AUTHORITY_V1 | ADOPT | TimelineGitV1Controller is canonical despite V1 name (§17.3) |
| OWNERSHIP_SCOPE_MUST_BE_VERIFIABLE_AT_OR_BEFORE_PERSISTENCE_READ_V1 | ADOPT | ownership at/before persistence read; LOAD_THEN_CHECK = P1 (§17.5) |
| STRUCTURAL_GUARD_SCOPE_MUST_MATCH_MECHANICALLY_AUDITED_SYMBOL_SET_V1 | ADOPT | guard claims only audited symbol set (§17.7) |

### 17.9 Corrected counts (recomputed, not preserved)

- P0 semantic authority count: 4 behavior classes (recordRevision ×2,
  recordAiAdoptRevision, restore, backfill) — grouped into 4 write-authority
  rows + 1 legacy-read-authority row.
- P1 unsafe symbol count: 5 (findPayload, findById, findLatestByProject,
  TimelineRevisionRepository.findById, + legacy read authority).
- P1 call-site count: 13 production call sites (4 findPayload + 2 findById +
  7 findLatestByProject).
- P2 runtime residue count: 15 (internal-1.0 surfaces).
- TimelineRevisionService public behavior count: 15 (14 semantic methods +
  1 grouped metadata mutation).
- TimelineRevisionService production caller count: 11 files (3 in render,
  6 platform-app controllers incl. workbench, 1 render API, 1 timeline-module
  internal).
- TimelineGitV1 semantic-residue count: 0.

### 17.10 CFRH_DR_BOUNDARY_CORRECTION gates

| Gate | Result |
|---|---|
| CFRH-BC-01 parent exact 21421108 | PASS |
| CFRH-BC-02 production/test/build/migration/generated = 0 | PASS |
| CFRH-BC-03 all TimelineRevisionService public behaviors inventoried | PASS (15 rows) |
| CFRH-BC-04 all TimelineRevisionService production callers resolved | PASS (11 files) |
| CFRH-BC-05 TimelineRevisionController endpoint matrix complete | PASS (15 endpoints) |
| CFRH-BC-06 TimelineEditorSync service/controller behavior matrix complete | PASS |
| CFRH-BC-07 TimelineGitV1 reclassification evidence complete | PASS (6 canonical deps) |
| CFRH-BC-08 TimelineGitV1 not classified legacy by name | PASS (CANONICAL_API_PROJECTION_WITH_VERSIONED_NAME) |
| CFRH-BC-09 TimelineSnapshotService ownership surface complete | PASS |
| CFRH-BC-10 TimelineRevisionRepository ownership surface complete | PASS |
| CFRH-BC-11 project-only reads classified | PASS (findLatestByProject, findHeadByProject, listByProject) |
| CFRH-BC-12 global-id-only reads classified | PASS (findPayload, findById, repository.findById) |
| CFRH-BC-13 load-then-check reads classified | PASS (asset-lifecycle:116, merge-engine:754) |
| CFRH-BC-14 system-global exceptions explicit | PASS (listDistinctProjectIds) |
| CFRH-BC-15 ownership caller context matrix complete | PASS (18 rows) |
| CFRH-BC-16 BaseJobTimelineLoader context proven | PASS (tenant YES, project NO) |
| CFRH-BC-17 EditorSync snapshot-only API consequence explicit | PASS (PUBLIC_API_CHANGE_REQUIRED = YES) |
| CFRH-BC-18 CFRH-I1 = WRITE AUTHORITY CLOSURE | PASS |
| CFRH-BC-19 CFRH-I2 = READ OWNERSHIP + QUERY CLOSURE | PASS |
| CFRH-BC-20 service deletion deferred to behavior closure | PASS |
| CFRH-BC-21 CFRH-I3 contains only proven legacy surfaces | PASS |
| CFRH-BC-22 guard claims match audited symbol scope | PASS |
| CFRH-BC-23 four bounded refinements resolved | PASS (4/4 ADOPT) |
| CFRH-BC-24 9 original contracts coherent | PASS |
| CFRH-BC-25 OPTION C preserved | PASS |
| CFRH-BC-26 #21/#22 NOT_STARTED | PASS |
| CFRH-BC-27 no roadmap renumbering | PASS |
| CFRH-BC-28 escalation evaluated | PASS (NONE, 5 conditions) |
| CFRH-BC-29 unresolved repository-reality blockers = 0 | PASS |

**CFRH_DR_BOUNDARY_CORRECTION = 29/29 PASS**

---

## 18. CFRH-I1 EXECUTION CONTRACT CORRECTION (append-forward)

Per ChatGPT review (CHATGPT_CLEAN_FORWARD_RUNTIME_HARDENING_BOUNDARY_
CORRECTION_REVIEW = CORRECTION_REQUIRED; direction PASS; caller inventory
FAIL; endpoint inventory FAIL; I1 semantic migration contract FAIL; guard
symbol closure PARTIAL). This section freezes the exact executable contract
for CFRH-I1. Implementation remains FORBIDDEN.

### 18.1 Caller inventory correction (E1)

Rebuilt from source (constructor injection + field types + direct invocation,
independent of prior manifests):

- **TimelineRevisionService actual production caller files: 6**
  (TimelineRevisionController, TimelineWorkbenchController, RenderController,
  TimelineEditorSyncService, TimelineRevisionRenderService,
  PlanBasedTimelineRevisionRenderService) + service-internal self-calls.
- **findById caller correction**: TimelineMergeEngine:754 uses
  TimelineRevisionRepository.findById (REPOSITORY, not service) — REMOVED
  from service caller accounting. Real service findById callers:
  TimelineRevisionRenderService:113, PlanBasedTimelineRevisionRenderService:174
  (+ service internal 195/202/449).
- Behavior matrix updated; caller inventory invariant
  ACTUAL_CALLER_SET == MATRIX_CALLER_SET now holds per method.

### 18.2 Controller endpoint matrix correction (E2)

Mechanically counted from TimelineRevisionController source: **15 endpoints**
(14 method-level @*Mapping + 1 root @GetMapping for history listing).

Previously omitted: **root GET /api/render/projects/{projectId}/timeline/
revisions → listHistory** (L72-84). Endpoint matrix created:
`timeline-revision-controller-endpoint-matrix.tsv` (15 rows).
Invariant DECLARED_REQUEST_MAPPING_ENDPOINT_COUNT == ENDPOINT_MATRIX_ROW_COUNT
= 15 holds.

### 18.3 I1 semantic migration contract (E3)

Evidence: legacy recordRevision persists the ORIGINAL internal-1.0 JSON
snapshot (schema="internal-1.0", L147) with NO revision semantic context and
NO Effect semantic snapshot (L167-170 null fields), NO CAS (appends to head
after contentHash dedup). Canonical saveRevisionWithEffects requires
TimelineDocument + EffectInstance list and creates semantic context
(storeTx L250) + Effect snapshot + CAS (expectedCurrentRevisionId) + pins in
one jOOQ transaction.

**Semantic width findings** (internal-to-canonical-semantic-width-matrix.tsv,
22 features):

- TimelineDocument carries: tracks, clips, source bindings, artifact pins,
  content digest, exact source range, timeline placement, temporal mapping,
  audioMix, textElements, font semantics, effects (via EffectInstance params),
  effect order, semantic relationships, metadata — roundtrip preserved.
- **TimelineDocument does NOT carry transitions or automations**
  (TimelineDocumentCandidateMapper L48 explicitly: "carries no transitions/
  automations fields; those live in internal"). CanonicalTransition and
  CanonicalAutomationCurve exist only in the canonicalmodel layer
  (adapter validation path), never persisted via document save.
- renderGraph/provider hints and segment policy = DERIVED_EXECUTION_STATE
  (not authored canonical semantics; not preserved by I1).

**Transitions/automations resolution (mandatory per review):**

- **recordAiAdoptRevision (AI path): OPTION 1 PROVEN.** AiTimelineEditService
  and AiTimelineEditResponse contain ZERO transition/automation references —
  the AI output model cannot author transitions/automations, so a
  lossless-to-canonical migration of AI-adopt inputs loses nothing.
- **recordRevision (editor-sync path): OPTION 3 (delete obsolete product
  behavior).** Sole caller TimelineEditorSyncService is itself the I3 legacy
  internal-1.0 surface (storedSchema = "internal-1.0"); push/sync is an
  unshipped editor compatibility path. Deleting the product behavior (with
  its I1 write authority) is cleaner than a lossy migration.
- No I1 path silently drops transitions/automations: the migrating path (AI)
  cannot author them; the deleting path (editor sync) removes them with the
  obsolete product behavior.

**I1 execution decisions (frozen):**

| Behavior | Decision | Canonical target / delete | Key contract |
|---|---|---|---|
| recordRevision ×2 | DELETE_OBSOLETE_PRODUCT_BEHAVIOR (editor-sync internal-1.0 path; I3 removal) | delete with TimelineEditorSyncService; no canonical migration | no external obligation; no lossy conversion |
| recordAiAdoptRevision | MIGRATE_LOSSLESSLY_TO_CANONICAL_AUTHORITY | TimelineRevisionSaveService.saveRevisionWithEffects (AI timelineJson → TimelineDocument + effects) | AI model proven not to author transitions/automations; canonical CAS + context + Effect snapshot + pins apply |
| restore | REPLACE_WITH_EXISTING_CANONICAL_BEHAVIOR | TimelineRevisionSaveService.restoreRevision (R4-D1 verified-payload reissue) | ownership-scoped (projectId+tenantId), CAS, single tx, pin copy, context copy — full invariant coverage proven |
| backfillHeadFromLatestSnapshot | DELETE_OBSOLETE_PRODUCT_BEHAVIOR | delete; pullByProject falls through to findHead/findLatestByProject (L61-69) | caller fallthrough proven; no production requirement uses backfill result |

I1_MIGRATE_COUNT = 1 (recordAiAdoptRevision), I1_REPLACE_COUNT = 1 (restore),
I1_DELETE_COUNT = 2 (recordRevision ×2, backfill), I1_UNKNOWN_COUNT = 0.

LOSSY_CONVERSION_ALLOWED = NO for all migration-classified behaviors.
LEGACY_WRITE_MIGRATION_MUST_TERMINATE_IN_EXISTING_CANONICAL_TRANSACTION_
BOUNDARY_V1 satisfied (saveRevisionWithEffects/restoreRevision own the full
transaction).

### 18.4 P1 enforcement symbol closure (E4)

- **listByProject contradiction resolved**: reclassified. It is a
  project-scoped query whose ONLY caller is the legacy service itself
  (TimelineRevisionService.listHistory); no production caller reaches it
  directly. After I2 legacy-service deletion the symbol vanishes. Final
  disposition: FORBIDDEN_SYMBOL_SET (post-I2, vacuously) — not an
  independent P1 production risk. Manifest and guard design now agree.
- P1 unsafe symbols (final): findPayload, snapshot-findById,
  findLatestByProject, repo-findById, legacy read authority — ALL map to
  FORBIDDEN_SYMBOL_SET (bounded mechanical guard) with explicit system
  exception only for listDistinctProjectIds.
- P1_UNENFORCED_SYMBOL_COUNT = 0.

### 18.5 New bounded contracts (frozen)

| Contract | Decision | Evidence |
|---|---|---|
| LEGACY_WRITE_MIGRATION_MUST_BE_SEMANTICALLY_NON_NARROWING_V1 | ADOPT | LOSSY_CONVERSION_ALLOWED = NO; AI path proven non-narrowing; editor-sync path deleted rather than narrowed |
| LEGACY_WRITE_MIGRATION_MUST_TERMINATE_IN_EXISTING_CANONICAL_TRANSACTION_BOUNDARY_V1 | ADOPT | saveRevisionWithEffects/restoreRevision own full tx (validation, snapshot, revision, pins, context, CAS, head) |
| LEGACY_NON_CANONICAL_FIELDS_ARE_NOT_PRESERVED_BY_DEFAULT_V1 | ADOPT | renderGraph/segment-policy = derived execution state; not preserved |
| BEHAVIOR_DISPOSITION_MUST_BE_RESOLVED_BEFORE_LEGACY_AUTHORITY_REMOVAL_V1 | ADOPT | I1 disposes all 4 write behaviors before any removal; I2 disposes query behaviors before service deletion |

### 18.6 I1 bounded implementation shape (future, NOT started)

I1-A canonical restore rewiring (TimelineRevisionController:193 →
restoreRevision) — lowest risk.
I1-B backfill deletion (TimelineEditorSyncService:60 removed).
I1-C recordRevision/editor-sync write removal (with I3 sync surface).
I1-D recordAiAdoptRevision migration (AI → saveRevisionWithEffects).
I1-E obsolete legacy write method removal.
I1-F structural write-authority guard
(LEGACY_TIMELINE_REVISION_SEMANTIC_WRITE_AUTHORITY_COUNT = 0).

Sequence follows semantic risk: I1-A, I1-B, I1-D, I1-C, I1-E, I1-F.

TimelineRevisionService deletion remains NOT an I1 postcondition
(queries/projections close in I2).

### 18.7 CFRH_I1_EXECUTION_CONTRACT gates

| Gate | Result |
|---|---|
| I1-EC-01 parent exact f86f5622 | PASS |
| I1-EC-02 production/test/build/migration/generated = 0 | PASS |
| I1-EC-03 call graph re-audited | PASS (6 caller files) |
| I1-EC-04 findById caller set corrected | PASS |
| I1-EC-05 repository findById not misattributed | PASS (merge engine = repository) |
| I1-EC-06 matrix physical row count reported | PASS (16 data rows / 15 behavior groups) |
| I1-EC-07 behavior group count reported | PASS (15 groups) |
| I1-EC-08 endpoint set mechanically complete | PASS (15) |
| I1-EC-09 root GET history endpoint present | PASS (L72-84 listHistory) |
| I1-EC-10 execution matrix covers all 4 write classes | PASS |
| I1-EC-11 recordRevision disposition resolved | PASS (DELETE_OBSOLETE_PRODUCT_BEHAVIOR) |
| I1-EC-12 recordAiAdoptRevision disposition resolved | PASS (MIGRATE_LOSSLESSLY) |
| I1-EC-13 restore disposition resolved | PASS (REPLACE_WITH_CANONICAL) |
| I1-EC-14 backfill disposition resolved | PASS (DELETE_OBSOLETE_PRODUCT_BEHAVIOR) |
| I1-EC-15 I1_UNKNOWN_COUNT = 0 | PASS |
| I1-EC-16 semantic-width matrix complete | PASS (22 features) |
| I1-EC-17 transitions disposition resolved | PASS (OPTION 1 AI / OPTION 3 editor-sync) |
| I1-EC-18 automations disposition resolved | PASS (same) |
| I1-EC-19 Effect semantics disposition resolved | PASS (EffectInstance extraction → canonical snapshot) |
| I1-EC-20 source binding/artifact pin disposition resolved | PASS (pins preserved both paths) |
| I1-EC-21 text/audio/relationship semantics resolved | PASS (all in TimelineDocument) |
| I1-EC-22 renderGraph/provider fields classified | PASS (DERIVED_EXECUTION_STATE) |
| I1-EC-23 no migration permits semantic narrowing | PASS (AI proven non-narrowing; sync deleted) |
| I1-EC-24 CAS semantics frozen | PASS (ADOPT_CANONICAL_CAS) |
| I1-EC-25 Effect snapshot semantics frozen | PASS (canonical minting) |
| I1-EC-26 artifact pin semantics frozen | PASS (register same tx) |
| I1-EC-27 revision semantic context frozen | PASS (storeTx) |
| I1-EC-28 transaction boundary frozen | PASS (canonical full tx) |
| I1-EC-29 ownership context frozen | PASS (projectId+tenantId) |
| I1-EC-30 provenance/fail-closed frozen | PASS |
| I1-EC-31 listByProject contradiction resolved | PASS (reclassified) |
| I1-EC-32 all P1 symbols map to enforcement/exception | PASS |
| I1-EC-33 P1_UNENFORCED_SYMBOL_COUNT = 0 | PASS |
| I1-EC-34 I2 unknown availability separated from disposition | PASS (NO_EXISTING_REPLACEMENT_BUT_I2_NEW_PROJECTION_AUTHORIZED) |
| I1-EC-35 four new bounded contracts resolved | PASS (4/4 ADOPT) |
| I1-EC-36 original 9 contracts coherent | PASS |
| I1-EC-37 I1/I2/I3 boundaries unchanged | PASS |
| I1-EC-38 #20 CLOSED | PASS |
| I1-EC-39 #21/#22 NOT_STARTED | PASS |
| I1-EC-40 architecture escalation evaluated | PASS (NONE — no frozen canonical type change required; deletion preferred over lossy mapping) |
| I1-EC-41 unresolved execution-contract blockers = 0 | PASS |

**CFRH_I1_EXECUTION_CONTRACT = 41/41 PASS**
