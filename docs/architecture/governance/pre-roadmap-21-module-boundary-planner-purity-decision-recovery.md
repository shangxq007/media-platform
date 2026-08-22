# PRE-ROADMAP-21 MODULE BOUNDARY AND PLANNER PURITY HARDENING — DECISION RECOVERY

## 1. EXACT REPOSITORY BASE

BASE_SHA=fda0ba2dfd3b846050d561eebf35d30ea48cba98
BASE_TREE=49a1464124735bab4e251ea12ac041718128ffcf
BRANCH=agent/pre21-module-boundary-planner-purity-dr
WORKTREE=.worktrees/pre21-module-boundary-planner-purity-dr
REPOSITORY_STATUS=CLEAN

## 2. PLANNER DEPENDENCY INVENTORY

ExecutionPlannerService (render-module/src/main/java/com/example/platform/render/app/planner/ExecutionPlannerService.java, 91 lines):
- Constructor deps: ProducerRuntimeService producerRuntime, ProductRuntimeService productRuntime, CapabilityResolutionService capabilityResolver
- Public methods: plan(String targetProductId, String targetProductType, ...), explain(ExecutionPlan)

Direct mutable-runtime dependencies:
- ProductRuntimeService (runtime product status/dependency state)
- ProducerRuntimeService (runtime producer state)

CapabilityResolutionService (render-module/app/planner/CapabilityResolutionService.java, 86 lines):
- Constructor deps: CapabilityCatalogService catalog, ExecutionBackendRegistry backendRegistry
- resolve(String productType) — productType → TaskCapability switch mapping

## 3. RUNTIME MUTABLE READ INVENTORY (TOPIC A)

| # | Location | Dependency | State read | Why mutable | Layer | Disposition |
|---|---|---|---|---|---|---|
| 1 | ExecutionPlannerService:31 | productRuntime | product status (READY check) | runtime DB-backed product status | logical planner | MIGRATE to explicit input |
| 2 | ExecutionPlannerService:39 | capabilityResolver | resolve(productType) | runtime registry lookup | logical planner | MIGRATE to frozen resolution input |
| 3 | ExecutionPlannerService:48 | productRuntime | findDependencies | runtime dependency graph | logical planner | MIGRATE to explicit input |
| 4 | ExecutionPlannerService:50-52 | productRuntime | upstream product status | runtime READY state | logical planner | MIGRATE to explicit input |
| 5 | ExecutionPlannerService:76 | productRuntime | product status in explain() | runtime state | logical planner | MIGRATE or freeze |

LOGICAL_PLANNER_RUNTIME_MUTABLE_READ_COUNT (current) = 5
Target: LOGICAL_PLANNER_RUNTIME_MUTABLE_READ_COUNT = 0

Architecture premise (PLANNING_IS_PURE_COMPUTATION_OVER_EXPLICIT_INPUTS_V1) is VALID; repository does not yet conform → bounded implementation correction can be planned. NOT an architecture premise failure.

## 4. CAPABILITYREQUIREMENT AUTHORITY INVENTORY (TOPIC B)

Violation confirmed: CapabilityResolutionService.mapToCapability(String productType) is a switch that INVENTS semantic requirements from product type:
- TRANSCRIPT→ASR, OCR→OCR, VISION→VISION, EMBEDDING→EMBEDDING, THUMBNAIL→THUMBNAIL, PROXY/TRANSCODE→TRANSCODE, PREVIEW/FINAL_RENDER→MEDIA_PIPELINE, PACKAGE→PACKAGE
- TaskCapability enum lives in outbox module (com.example.platform.outbox.coordination.TaskCapability) — an execution-coordination enum used as capability authority

Correct semantic carriers already exist:
- extension-module/domain/CapabilityRequirement (CapabilityId + ContractVersionRange + required/optional)
- media-execution-plan-module/execution/domain/ExecutionCapabilityRequirement (capabilityId + minimumVersion + requiredFeatures)
- render-module/domain/template/TemplateCapabilityRequirement
- render-module/domain/renderplan/RenderCapabilityVocabulary (render-plan capability vocabulary)

Authority chain violation: ExecutionPlannerService → capabilityResolver.resolve(productType) bypasses the semantic requirement chain:
Semantic Consumer/OperationDefinition → CapabilityRequirement → Registry → Resolution → Implementation
Current: productType → (switch invention) → TaskCapability → registry

Disposition: CAPABILITY_MAPPING_AUTHORITY_CORRECTION_BEFORE_21 = REQUIRED
- Semantic requirement declaration must move to the semantic owner (Operation/consumer)
- Resolver may resolve/filter/validate declared requirements; must NOT invent them
- RESOLVER_INVENTED_CAPABILITY_REQUIREMENT_COUNT target = 0
- CLEAN FORWARD: remove switch mapping API after caller migration (unshipped — no external compatibility burden)

## 5. MODULE-CROSSING INVENTORY (TOPIC C)

Observed high-traffic module crossings (platform-app controllers → module internals):
- 23 controllers → render-module (largest; render is primary application domain — mostly legitimate application contracts, some may be internal leakage)
- 8 controllers → timeline-module
- 17 controllers → shared-kernel (web infrastructure)
- 1-3 each → artifact, identity, ingest, storage, workflow, entitlement, billing, policy, prompt, commerce, audit, app, security, outbox, web

Classification method for PRE-#21 implementation: per-crossing audit (intentional domain surface / application contract / port / internal leakage / historical debt / test-only / generated / false positive). No NamedInterface declarations found in repository. No active Modulith verification task (see §6).

Frozen policy: MODULE_INTERACTION_REQUIRES_EXPLICIT_EXPOSED_CONTRACT_V1; CROSS_MODULE_INTERNAL_TYPE_ACCESS_IS_NOT_A_STABLE_ARCHITECTURE_CONTRACT_V1.

## 6. MODULITH VIOLATION CLASSIFICATION (TOPIC D)

Observed: platform-app/docs/architecture/maps/generated/modulith/ contains 91 generated files (components.puml + per-module adoc/puml) TRACKED in git, but:
- No active Spring Modulith dependency/verification task in any build file
- No NamedInterface declarations
- The generated files are a historical/static snapshot (no regenerating task found in build.gradle.kts)

Classification: the generated Modulith map is a HISTORICAL_DEBT artifact (stale snapshot, no active gate). PRE-#21 decision: either re-activate a real Modulith verification task (with NamedInterface declarations for intentional surfaces) or remove the stale generated snapshot. Do NOT optimize toward violation-count=0; require classification + critical-boundary fixes + burn-down plan.

## 7. RENDER PLANNING / RUNTIME BOUNDARY MAP (TOPIC E)

Current architecture:
- ExecutionPlannerService (render-module/app/planner) — logical planning but reads runtime state (see §3)
- CapabilityResolutionService (render-module/app/planner) — runtime resolution via catalog + backend registry
- CapabilityCatalogService (render-module/app/capability) — producer discovery
- RenderProvider (render-module/infrastructure) — provider adapter interface (render(jobId, aiScript, profile))
- RenderPlanCanonicalCodec / DefaultRenderPlanner (render-module/domain/renderplan) — canonical render-plan domain
- RenderExtent (render-module/domain/renderplan) — exact half-open [start,end) + rational frame rate (C9)

Target layering: Logical Planning → Physical Planning → Execution Orchestration → Provider Adapter → Artifact Materialization.
LOGICAL_PLANNING_RUNTIME_INFRASTRUCTURE_LEAK_COUNT current > 0 (planner runtime reads). Target = 0.
No provider-native fields observed in canonical models (no ffmpegCommand/ffmpegArgs/useFFmpeg in canonical render-plan types — to be re-verified mechanically during implementation).

## 8. PLATFORM-APP INTERNAL DEPENDENCY INVENTORY (TOPIC F)

Controllers import across 20+ modules (see §5). Largest surfaces: render (23), timeline (8), shared (17).
PLATFORM_APP_INTERNAL_DEPENDENCY_INVENTORY = REQUIRED as bounded implementation deliverable:
- per-controller import audit
- classify stable application/domain contract vs internal leakage
- no forced indirection for intentional stable domain types
- app/web surface → explicit application contract/port → module

## 9. IDENTITY → ARTIFACT/STORAGE DECISION EVIDENCE (TOPIC G)

Observed identity-access-module dependencies on artifact/storage:
- ProjectImportService: ArtifactCatalogService, ArtifactLifecycleService, ArtifactCatalogEntry, BlobStorage, PutObjectCommand, StorageObjectRef, ChecksumFormat
- ProjectImportExecuteService / ProjectExportZipReader / HttpImportAssetDownloader: ChecksumFormat, ChecksummingInputStream
- ArtifactCatalogProjectAssetListingAdapter (infrastructure/export): ArtifactCatalogService, ArtifactCatalogEntry

Use cases: project import/export (asset catalog listing, blob put, checksum verification) — application-level use of artifact/storage contracts for identity-owned portability features.

DECISION: KEEP (application-contract use). Identity consumes Artifact/Storage application contracts for import/export; no evidence of identity orchestrating storage lifecycle, choosing providers, owning materialization, or becoming cleanup authority. Verify during implementation that identity uses only artifact/storage public application contracts (not infrastructure internals); narrow any infrastructure-package imports.

## 10. FRAMESTREAM PATH INVENTORY (TOPIC I)

Observed:
- RenderExtent (render-module/domain/renderplan) — exact requested extent semantics (C9), used by DefaultRenderPlanner, RenderRequest, RenderPlanCanonicalCodec
- RenderProvider.render(jobId, aiScript, profile) → RenderResult (render-module/infrastructure) — provider adapter output
- VideoFrameGenNode (render-module/policy/liteflow) — frame generation node (LiteFlow policy path)
- No type named FrameStream exists in the repository

PRE-#21 requirement: AUTHORITATIVE_FRAMESTREAM_FAIL_CLOSED — inventory every render-frame output path; distinguish authoritative vs debug/preview/diagnostic; result must report REQUESTED_RENDER_EXTENT vs ACHIEVED_RENDER_EXTENT; insufficient extent → typed unsupported/failure; never silent partial success; debug output explicitly marked and not conformance evidence. Current RenderResult structure and extent validation must be audited mechanically during implementation.

## 11. ERROR ALGEBRA OWNERSHIP INVENTORY (TOPIC J)

Observed error constructs (module-owned, no single mega code):
- artifact-module: ArtifactErrorCode/ArtifactDomainException (domain-owned)
- media-execution-plan-module: ExecutionPlanErrorCode/ExecutionPlanDomainException
- storage-module: StorageError (contract/error)
- timeline-module: TimelineError, PatchErrorCode, RevisionCommandErrorCode, TimelineDiffErrors
- operation-module: OperationErrorCode, PlanErrorCode
- workflow-module: WorkflowExecutionErrorCode, UserWorkflowErrorCode
- render-module: IrErrorCode, failure-reason records (LocalExecutionPlanFailureReason, RenderExecutionPlanFailureReason, ProviderBindingFailureReason, FailureClassificationEngine)
- provider mapping: OpenDalErrorMapper (storage-provider-opendal)
- API mapping: GraphQLExceptionMapper / GraphQLErrorResolver (federation-query), GlobalExceptionHandler (platform-app)
- shared-kernel: CommonErrorCode, ErrorCodeRegistry, ConfigurableErrorCode (10 external consumers)

Assessment: ownership is largely correct (domain owns semantic categories; provider adapter maps native errors; API maps transport). PRE-#21 must verify: (a) ErrorCodeRegistry is a registration utility, not a global mega authority; (b) provider-native codes never flow directly into canonical semantic categories; (c) CapabilityId != SemanticFailureCategory != ProviderNativeCode remains distinct. GLOBAL_MEGA_ERROR_CODE_AUTHORITY_COUNT target = 0.

## 12. TIMELINEASSETGC DEAD DEPENDENCY VERIFICATION (TOPIC K)

Mechanical evidence (TimelineAssetGcService, render-module/app/timeline):
- private final DSLContext dsl; field: PRESENT
- dsl. usages in body: 0 (zero)
- TIMELINE_SNAPSHOT import: PRESENT
- TIMELINE_SNAPSHOT references in body: 0 (zero)

VERDICT: DEAD — DSLContext field/dependency and TIMELINE_SNAPSHOT import are unused post-I2 (private jOOQ scan removed in I2-F). Disposition: CLEAN FORWARD bounded hygiene (remove unused dependency/import, no compatibility retention).

## 13. BOUNDED IMPLEMENTATION WAVES

WAVE 1 — Planner Purity Boundary:
- Introduce/finalize frozen planning input contract (explicit immutable inputs: requested operation semantics, declared capability requirements, frozen resolution facts, frozen planning context)
- Migrate ExecutionPlannerService off ProductRuntimeService/ProducerRuntimeService mutable reads
- Prove LOGICAL_PLANNER_RUNTIME_MUTABLE_READ_COUNT = 0

WAVE 2 — CapabilityRequirement Authority:
- Move semantic requirement declaration to correct semantic owner (Operation/consumer)
- Replace resolver-invented switch mapping (CapabilityResolutionService.mapToCapability)
- Migrate callers to declared-requirement resolution
- Delete wrong unshipped mapping API; prove LEGACY_CALL_COUNT=0, LEGACY_DEFINITION_COUNT=0, COMPATIBILITY_WRAPPER_COUNT=0, DUAL_AUTHORITY_COUNT=0
- RESOLVER_INVENTED_CAPABILITY_REQUIREMENT_COUNT = 0

WAVE 3 — Critical Module Boundary Corrections:
- Re-activate or remove stale Modulith snapshot; add NamedInterface for intentional surfaces
- Repair only critical pre-#21 cross-module defects (from §5/§6 classification)
- Classify remaining debt into burn-down plan

WAVE 4 — FrameStream Fail-Closed / RenderExtent Exactness:
- Authoritative result semantics with REQUESTED vs ACHIEVED extent
- Typed unsupported/failure for insufficient extent; explicit debug/non-authoritative marking
- AUTHORITATIVE_FRAMESTREAM_PARTIAL_SUCCESS_COUNT = 0

WAVE 5 — Error Algebra Ownership:
- Verify semantic/provider/API ownership boundaries; no provider-native code into canonical categories; ErrorCodeRegistry not mega authority

WAVE 6 — Small Hygiene / Guards:
- TimelineAssetGcService dead DSLContext + TIMELINE_SNAPSHOT import removal (proven dead §12)
- Structural guards + architecture tests

## 14. STRUCTURAL GUARDS

LOGICAL_PLANNER_RUNTIME_MUTABLE_READ_COUNT = 0
LOGICAL_PLANNING_RUNTIME_INFRASTRUCTURE_LEAK_COUNT = 0
RESOLVER_INVENTED_CAPABILITY_REQUIREMENT_COUNT = 0
CRITICAL_CROSS_MODULE_INTERNAL_ACCESS_COUNT = 0
AUTHORITATIVE_FRAMESTREAM_SILENT_PARTIAL_COUNT = 0
AUTHORITATIVE_FRAMESTREAM_UNCHECKED_RENDER_EXTENT_COUNT = 0
GLOBAL_MEGA_ERROR_CODE_AUTHORITY_COUNT = 0
CLEAN FORWARD: LEGACY_CALL_COUNT=0 / LEGACY_DEFINITION_COUNT=0 / COMPATIBILITY_WRAPPER_COUNT=0 / DUAL_AUTHORITY_COUNT=0 per retired API

## 15. RED MUTATION PLAN

| Mutation | Expected detector | Failure signal |
|---|---|---|
| Reintroduce mutable runtime service into logical planner | planner purity guard | guard FAIL |
| Reintroduce productType→capability switch in resolver | capability authority guard | guard FAIL |
| Reintroduce forbidden cross-module internal type access | module boundary guard | guard FAIL |
| Bypass requested/achieved RenderExtent validation | FrameStream extent guard | guard FAIL |
| Return partial FrameStream as authoritative success | FrameStream guard | guard FAIL |
| Route provider-native error into canonical semantic category | error algebra guard | guard FAIL |
| Reintroduce retired legacy API definition/wrapper | CLEAN FORWARD guard | guard FAIL |

Restore every mutation; working tree returns clean. Execution during WAVE implementation/FCV only.

## 16. AUTHORITATIVE TEST / GATE PLAN

Actual repository tasks (verified in build.gradle.kts): verifyGcr2ArtifactAuthority, pfirr1RemediationCheck, verifyC1Cnm1RedGates, jooqFoundationCheck, verifyTimelineEffectTransitionCanonicalization, :render-module:verifyC20RenderPlanBoundaryGuard, :platform-app:bootJar, full ./gradlew test.
Cfrhi1LegacyWriteAuthorityGuardTest / Cfrhi2SystemAuthorityGuardTest / Cfrhi2FinalReadAuthorityGuardTest (timeline-module).
No active Modulith task (see §6 — WAVE 3 decides re-activation).
FCV from clean committed candidate tree; pre-change tests never validate post-change code.

## 17. BLOCKERS

BLOCKERS=0

## 18. ARCHITECTURE ESCALATION STATUS

ARCHITECTURE_ESCALATION=NONE
(All observed gaps are repository implementation gaps against valid frozen premises; no frozen premise contradicted.)

## 19. READINESS

READY_FOR_PRE21_BOUNDED_IMPLEMENTATION=YES
Mechanical reason: CFRH-I2 closed on canonical main (origin/main=fda0ba2d, 14/14 milestone SHAs reachable); all eleven PRE-#21 topics inspected with concrete repository evidence; two confirmed defects (planner runtime reads §3, resolver-invented capability mapping §4) and one confirmed dead dependency (§12) are bounded corrections against valid frozen architecture; no architecture premise failure found; worktree clean.
