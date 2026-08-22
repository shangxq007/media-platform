# ROADMAP #21 — MEDIA-EXECUTION-PLAN-MODULE TYPE DISPOSITION LEDGER

STATUS=CANDIDATE / PENDING_CHATGPT_ARCHITECTURE_REVIEW
CORRECTION_BASE=c49cd338a1fe376c38b896c5e2a2cf7bc8c020fe
MODE=GOVERNANCE_CORRECTION_ONLY
LEDGER_ROW_COUNT=53 / SOURCE_FILE_COUNT=53 / LEDGER_COUNT_MATCH=PASS

Classification vocabulary (allowed):
REUSE_AS_CANONICAL / REUSE_MECHANICS_ONLY / MIGRATE_REDESIGN /
DELETE_SHADOW / DEFER_TO_22_PLUS

| # | FILE | TYPE | CLASSIFICATION | TARGET_TYPE_OR_OWNER | RATIONALE |
|---|---|---|---|---|---|
| 1 | domain/ExecutionPlanId.java | record | REUSE_AS_CANONICAL | #21 identity | plan identity, distinct from semantic content digest |
| 2 | domain/ExecutionPlanSchemaVersion.java | record | REUSE_AS_CANONICAL | #21 versioning | schema versioning mechanics |
| 3 | domain/ExecutionEdgeId.java | record | REUSE_AS_CANONICAL | #21 logical edge id | DAG skeleton identity |
| 4 | domain/ExecutionInputBinding.java | record | REUSE_AS_CANONICAL | #21 node input binding | typed binding mechanics, no old semantic authority |
| 5 | domain/ExecutionInputId.java | record | REUSE_AS_CANONICAL | #21 node input id | typed input identity |
| 6 | domain/ExecutionInputRole.java | enum | REUSE_AS_CANONICAL | #21 input role | typed input role mechanics |
| 7 | domain/ExecutionOutputDeclaration.java | record | REUSE_AS_CANONICAL | #21 node output decl | typed output declaration mechanics |
| 8 | domain/ExecutionOutputId.java | record | REUSE_AS_CANONICAL | #21 node output id | typed output identity |
| 9 | domain/ExecutionOutputRole.java | enum | REUSE_AS_CANONICAL | #21 output role | typed output role mechanics |
| 10 | domain/ExecutionStepId.java | record | REUSE_AS_CANONICAL | #21 node id | typed step/node identity |
| 11 | domain/ExecutionCreationContext.java | record | REUSE_AS_CANONICAL | #21 provenance-only metadata | correlation/createdAt/trace — PROVENANCE_ONLY, excluded from semantic digest (CR-02) |
| 12 | domain/ExecutionCapabilityRequirement.java | record | DELETE_SHADOW | extension CapabilityRequirement | second CapabilityRequirement model — single authority is extension-module CapabilityRequirement (SHADOW_EXECUTION_CAPABILITY_REQUIREMENT_COUNT=0) |
| 13 | domain/operation/MediaOperation.java | interface | DELETE_SHADOW | #20 RenderNode.operationKey | second Operation WHAT hierarchy (SHADOW_EXECUTION_OPERATION_AUTHORITY_COUNT=0) |
| 14 | domain/operation/AnalysisOperation.java | record | DELETE_SHADOW | #20 RenderNode semantics | Operation WHAT hierarchy shadow |
| 15 | domain/operation/AudioMixOperation.java | record | DELETE_SHADOW | #20 RenderNode semantics | Operation WHAT hierarchy shadow |
| 16 | domain/operation/ComposeOperation.java | record | DELETE_SHADOW | #20 RenderNode semantics | Operation WHAT hierarchy shadow |
| 17 | domain/operation/CropOperation.java | record | DELETE_SHADOW | #20 RenderNode semantics | Operation WHAT hierarchy shadow |
| 18 | domain/operation/DecodeOperation.java | record | DELETE_SHADOW | #20 RenderNode semantics | Operation WHAT hierarchy shadow |
| 19 | domain/operation/GeneratedMediaOperation.java | record | DELETE_SHADOW | #20 RenderNode semantics | Operation WHAT hierarchy shadow |
| 20 | domain/operation/IntegrityVerificationOperation.java | record | DELETE_SHADOW | #20 RenderNode semantics | Operation WHAT hierarchy shadow |
| 21 | domain/operation/MediaInspectionOperation.java | record | DELETE_SHADOW | #20 RenderNode semantics | Operation WHAT hierarchy shadow |
| 22 | domain/operation/PackageOperation.java | record | DELETE_SHADOW | #20 RenderNode semantics | Operation WHAT hierarchy shadow |
| 23 | domain/operation/ScaleOperation.java | record | DELETE_SHADOW | #20 RenderNode semantics | Operation WHAT hierarchy shadow |
| 24 | domain/operation/SubtitleBurnInOperation.java | record | DELETE_SHADOW | #20 RenderNode semantics | Operation WHAT hierarchy shadow |
| 25 | domain/operation/ThumbnailOperation.java | record | DELETE_SHADOW | #20 RenderNode semantics | Operation WHAT hierarchy shadow |
| 26 | domain/operation/TranscodeOperation.java | record | DELETE_SHADOW | #20 RenderNode semantics | Operation WHAT hierarchy shadow |
| 27 | domain/operation/TrimOperation.java | record | DELETE_SHADOW | #20 RenderNode semantics | Operation WHAT hierarchy shadow |
| 28 | domain/operation/WaveformOperation.java | record | DELETE_SHADOW | #20 RenderNode semantics | Operation WHAT hierarchy shadow |
| 29 | domain/GpuRequirement.java | enum | DELETE_SHADOW | RenderExecutionRequirement.gpu | #20 RenderNode.executionRequirements.gpu is authoritative (ROADMAP21_INVENTED_RESOURCE_REQUIREMENT_COUNT=0) |
| 30 | domain/compiler/TimelineToExecutionPlanCompiler.java | record | DELETE_SHADOW | (none — no direct compiler after #21) | direct Timeline→ExecutionPlan compiler forbidden (DIRECT_TIMELINE_TO_EXECUTION_PLAN_COMPILER_COUNT=0); #21 derives from RenderPlan+RenderGraph |
| 31 | domain/compiler/MediaBackendCompiler.java | record | DELETE_SHADOW | #22 runtime realization | backend compilation is #22; FROZEN placeholder |
| 32 | domain/ExecutionPlanErrorCode.java | record | DELETE_SHADOW | #21 typed planning failure enum | second failure-code authority — no dual error algebra |
| 33 | domain/ExecutionResourceRequirement.java | record | DEFER_TO_22_PLUS | #22 resource allocation | invents CPU/memory/network/storage requirements with NO #20 upstream declaration (ROADMAP21_INVENTED_RESOURCE_REQUIREMENT_COUNT=0); excluded from #21 digest |
| 34 | domain/CpuClass.java | enum | DEFER_TO_22_PLUS | #22 resource allocation | no upstream #20 declaration; excluded from #21 digest |
| 35 | domain/MemoryClass.java | enum | DEFER_TO_22_PLUS | #22 resource allocation | no upstream #20 declaration; excluded from #21 digest |
| 36 | domain/NetworkRequirement.java | enum | DEFER_TO_22_PLUS | #22 resource allocation | no upstream #20 declaration; excluded from #21 digest |
| 37 | domain/TemporaryStorageClass.java | enum | DEFER_TO_22_PLUS | #22 resource allocation | no upstream #20 declaration; excluded from #21 digest |
| 38 | domain/ExecutionStepFailurePolicy.java | enum | DEFER_TO_22_PLUS | #22 runtime failure policy | FAIL_PLAN/ALLOW_OPTIONAL_OUTPUT_FAILURE/REQUIRE_MANUAL_REVIEW = runtime behavior, NOT #21 planning failure algebra (ROADMAP21_RUNTIME_FAILURE_POLICY_COUNT=0) |
| 39 | domain/provider/ExecutionProvider.java | interface | DEFER_TO_22_PLUS | #22 provider execution | FROZEN sealed Stub-only — #22 boundary, stays unwired |
| 40 | domain/cache/ExecutionCacheKey.java | record | DEFER_TO_22_PLUS | #22 runtime cache mechanics | cache-key concept belongs to #22; #21 declares digest/cacheability metadata only |
| 41 | domain/ExecutionStepKind.java | enum | MIGRATE_REDESIGN | derived from #20 RenderNodeKind + operationKey | EXECUTION_STEP_KIND_INDEPENDENT_AUTHORITY=NO — no independent extensible second work taxonomy; logical node retains exact source RenderNodeKind/operationKey (EXECUTION_STEP_KIND_INDEPENDENT_AUTHORITY_COUNT=0) |
| 42 | domain/ExecutionDependency.java | record | MIGRATE_REDESIGN | exact RenderDependencyEdge/RenderDependency variants | typed dependency preservation — no generic authority (RENDER_DEPENDENCY_VARIANT_LOSS_COUNT=0) |
| 43 | domain/ExecutionDependencyType.java | enum | MIGRATE_REDESIGN | exact RenderDependencyEdge/RenderDependency variants | DATA/CONTROL/VALIDATION incompatible with LOGICAL_DEPENDENCY_SEMANTIC_LOSS=NO (GENERIC_EXECUTION_DEPENDENCY_AUTHORITY_COUNT=0) |
| 44 | domain/ExecutionDeterminism.java | enum | MIGRATE_REDESIGN | 1:1 derived from RenderExecutionRequirement.RenderDeterminismClass | EXECUTION_DETERMINISM_INDEPENDENT_AUTHORITY=NO — no UNKNOWN invention; mechanical derivation only (EXECUTION_DETERMINISM_INDEPENDENT_AUTHORITY_COUNT=0) |
| 45 | domain/MediaExecutionPlan.java | record | MIGRATE_REDESIGN | #21 LogicalExecutionGraph + PhysicalExecutionPlan | canonical plan carrier rebuilt per C6/C16 — old fields not retained via wrappers |
| 46 | domain/MediaExecutionStep.java | record | MIGRATE_REDESIGN | #21 LogicalExecutionNode / PhysicalPlanUnit | rebuilt per frozen contract; NO MediaOperation/ExecutionCapabilityRequirement/independent StepKind/invented resource/independent determinism/failure policy inside node |
| 47 | domain/ExecutionPlanDigest.java | record | MIGRATE_REDESIGN | LogicalExecutionGraphDigest + PhysicalExecutionPlanDigest | C16 two-layer digests; no generic digest as second semantic identity |
| 48 | domain/builder/MediaExecutionPlanBuilder.java | class | MIGRATE_REDESIGN | #21 graph/plan builder | structurally coupled to old MediaExecutionPlan/Step semantics — rebuilt |
| 49 | domain/validation/MediaExecutionPlanValidator.java | class | MIGRATE_REDESIGN | #21 graph/plan validator | validation invariants per C9 — rebuilt against #21 model |
| 50 | domain/projection/MediaExecutionGraphProjection.java | class | MIGRATE_REDESIGN | #21 projection utilities | coupled to old plan semantics — rebuilt |
| 51 | domain/serialization/ExecutionPlanCanonicalSerializer.java | class | REUSE_MECHANICS_ONLY | #21 canonical serialization mechanics | hashing/serialization mechanics only — no old semantic authority |
| 52 | domain/serialization/ExecutionPlanDigestCalculator.java | class | REUSE_MECHANICS_ONLY | #21 digest mechanics | hashing mechanics only — layer digests owned by Logical/Physical digest types |
| 53 | domain/ExecutionPlanDomainException.java | class | MIGRATE_REDESIGN | #21 typed planning failure carrier | failure carrier aligned to #21 failure algebra |

AGGREGATE (computed from table):
REUSE_AS_CANONICAL_COUNT=11 (rows 1-11)
REUSE_MECHANICS_ONLY_COUNT=2 (rows 51-52)
MIGRATE_REDESIGN_COUNT=13 (rows 41-50, 53)
DELETE_SHADOW_COUNT=21 (rows 12-32)
DEFER_TO_22_PLUS_COUNT=6 (rows 33-40)
SUM=11+2+13+21+6=53
UNCLASSIFIED_COUNT=0
DUPLICATE_ROW_COUNT=0

AUTHORITY SUMMARY:
RESOURCE_REQUIREMENT_AUTHORITY=#20 RenderNode.executionRequirements only; module resource types DEFER_TO_22_PLUS (ROADMAP21_INVENTED_RESOURCE_REQUIREMENT_COUNT=0)
EXECUTION_STEP_KIND_AUTHORITY=#20 RenderNodeKind + operationKey (EXECUTION_STEP_KIND_INDEPENDENT_AUTHORITY=NO)
DEPENDENCY_AUTHORITY=#20 RenderDependencyEdge/RenderDependency variants (LOGICAL_DEPENDENCY_SEMANTIC_LOSS=NO)
DETERMINISM_AUTHORITY=RenderExecutionRequirement.RenderDeterminismClass (1:1 derived; no UNKNOWN invention)
FAILURE_POLICY_AUTHORITY=#21 typed planning failure algebra (planning) vs #22 runtime failure policy (execution) — separate authorities
DIGEST_AUTHORITY=LogicalExecutionGraphDigest + PhysicalExecutionPlanDigest (C16); ExecutionPlanId = identity, distinct from content digest; provenance excluded from semantic digest
