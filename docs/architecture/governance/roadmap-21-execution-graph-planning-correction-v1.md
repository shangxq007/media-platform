# ROADMAP #21 — DECISION RECOVERY — ARCHITECTURE CORRECTION V1

STATUS=CANDIDATE / PENDING_CHATGPT_ARCHITECTURE_REVIEW
CORRECTION_BASE=35cfe5362108012fd83211b50d323273b1ce6aaf (immutable review evidence)
MODE=GOVERNANCE_CORRECTION_ONLY

Supersedes the prior Decision Recovery where in conflict (same branch lineage).

---

## CR-01 MODULE AUTHORITY / CLEAN FORWARD

ROADMAP21_OWNER_MODULE=media-execution-plan-module

render-module remains Roadmap #20 upstream authority. The existing
media-execution-plan-module semantic surface is NOT deferred as a whole; it
becomes the canonical #21 implementation home.

### Full production-type disposition inventory (53 types, mechanical)

| Type | Classification | Rationale |
|---|---|---|
| ExecutionPlanId / ExecutionPlanSchemaVersion / ExecutionPlanDigest / ExecutionStepId / ExecutionStepKind / ExecutionEdgeId / ExecutionDependency / ExecutionDependencyType / ExecutionInputBinding / ExecutionInputId / ExecutionInputRole / ExecutionOutputDeclaration / ExecutionOutputId / ExecutionOutputRole / ExecutionDeterminism / ExecutionStepFailurePolicy / ExecutionCreationContext | REUSE_AS_CANONICAL | DAG skeleton + identity + digest semantics = #21 Logical/Physical graph foundation |
| MediaExecutionPlan / MediaExecutionStep / MediaExecutionGraphProjection | MIGRATE_RENAME | canonical plan carrier for #21 (renamed/aligned to #21 LogicalExecutionGraph + PhysicalExecutionPlan contract in implementation phase) |
| MediaExecutionPlanBuilder / ExecutionPlanCanonicalSerializer / ExecutionPlanDigestCalculator / MediaExecutionPlanValidator | REUSE_AS_CANONICAL | construction/serialization/digest/validation mechanics |
| CpuClass / MemoryClass / NetworkRequirement / TemporaryStorageClass / ExecutionResourceRequirement | REUSE_AS_CANONICAL (declarative only) | typed declarative resource facts carried forward for #22 allocation; NEVER read as runtime availability |
| GpuRequirement | DELETE (shadow) | duplicate of RenderExecutionRequirement.gpu declared at #20 RenderNode — single authority is RenderNode.executionRequirements |
| ExecutionCapabilityRequirement | DELETE (shadow) | second CapabilityRequirement model — single authority is extension-module CapabilityRequirement (SHADOW_EXECUTION_CAPABILITY_REQUIREMENT_COUNT=0) |
| MediaOperation + AnalysisOperation/AudioMixOperation/ComposeOperation/CropOperation/DecodeOperation/GeneratedMediaOperation/IntegrityVerificationOperation/MediaInspectionOperation/PackageOperation/ScaleOperation/SubtitleBurnInOperation/ThumbnailOperation/TranscodeOperation/TrimOperation/WaveformOperation | DELETE (shadow) | second Operation WHAT hierarchy — single authority is #20 RenderNode operationKey/component semantics (SHADOW_EXECUTION_OPERATION_AUTHORITY_COUNT=0) |
| TimelineToExecutionPlanCompiler | DELETE (shadow) | direct Timeline→ExecutionPlan compiler forbidden after #21 (DIRECT_TIMELINE_TO_EXECUTION_PLAN_COMPILER_COUNT=0); #21 derives from RenderPlan+RenderGraph |
| ExecutionPlanErrorCode | DELETE (shadow) | second failure-code authority — #21 uses its module-local typed planning failure enum (no dual error algebra) |
| ExecutionPlanDomainException | MIGRATE_RENAME | typed failure carrier aligned to #21 failure algebra |
| ExecutionCacheKey | DEFER_NONCONFLICTING_ONLY | cache-key concept belongs to #22 runtime cache mechanics; #21 declares digest/cacheability metadata only |
| MediaBackendCompiler | DELETE (shadow/FROZEN) | backend compilation is #22 runtime realization |
| ExecutionProvider (FROZEN sealed, Stub-only) | DEFER_NONCONFLICTING_ONLY | #22 provider execution boundary; stays frozen-unwired |

### Phase 0 CLEAN FORWARD rules (implementation phase)

- no direct Timeline→ExecutionPlan compiler after #21
- no second CapabilityRequirement model
- no second Operation WHAT hierarchy
- no duplicate execution-plan authority
- no compatibility wrappers, aliases, fallbacks
- migrate tests to canonical model
- zero external callers before delete; zero retired definitions at closure

Required future guards:

SHADOW_EXECUTION_CAPABILITY_REQUIREMENT_COUNT=0
SHADOW_EXECUTION_OPERATION_AUTHORITY_COUNT=0
DIRECT_TIMELINE_TO_EXECUTION_PLAN_COMPILER_COUNT=0
EXECUTION_PLAN_DUAL_AUTHORITY_COUNT=0
EXECUTION_PLAN_COMPATIBILITY_WRAPPER_COUNT=0

---

## CR-02 EXECUTION REQUIREMENT SINGLE AUTHORITY

ExecutionRequirement is a PURE DERIVED NORMALIZED PROJECTION of RenderPlan +
validated RenderGraph declarations. It MUST NOT independently redeclare
RenderExtent, CapabilityRequirement, RenderExecutionRequirement,
RenderOutputRequirement, RenderMaterializationRequirement, or sample-window
semantics.

Canonical source chain:

- RenderPlan.request.extent → extent authority
- RenderNode.capabilityRequirements → capability authority
- RenderNode.executionRequirements → declared execution-intent authority
- ExecutionRequirement → normalized derived execution-planning projection only

No CapabilityId-only downgrade where full CapabilityRequirement semantics are
required. Any correlation/request/job identity not affecting execution
semantics: PROVENANCE_ONLY, EXCLUDED_FROM_SEMANTIC_DIGEST.

RenderPlan/RenderGraph consistency validation: graph.planFingerprint ==
plan.fingerprint. No separate duplicate capability/extent authority inputs.

EXECUTION_REQUIREMENT_DUAL_AUTHORITY=NO

---

## CR-03 LOGICAL GRAPH V1

RENDER_NODE_TO_LOGICAL_NODE=1_TO_1 (N_TO_M_LOGICAL_DECOMPOSITION=DEFERRED)

Logical node retains exact source RenderNode reference + typed declared
requirements (capability/execution/output/materialization/sample-window —
as references to the #20 declarations, not re-declarations).

Logical dependencies preserve exact RenderDependencyEdge / RenderDependency
semantic variants. NO flattening into a weaker generic DATA edge authority.
PLANNER_INVENTED_BARRIER_COUNT=0 (no invented BARRIER semantics).

Temporal information projects existing exact MediaTime / RenderSampleWindow /
TemporalMapping semantics only.

LOGICAL_DEPENDENCY_SEMANTIC_LOSS=NO

---

## CR-04 PHYSICAL PLAN / EQUIVALENCE

ONE_LOGICAL_NODE_TO_ONE_PHYSICAL_PLAN_UNIT for bounded V1.

PARTITION_BASELINE=REQUIRED_V1
FUSION=DEFERRED
TEMPORAL_CHUNKING=DEFERRED
SEMANTIC_REWRITE=DEFERRED
GENERAL_COST_OPTIMIZATION=DEFERRED

Separate digests:

- LogicalExecutionGraphDigest
- PhysicalExecutionPlanDigest

Different layer digests are NOT semantic-equivalence proof. Future equivalence
proof (when fusion lands):

LogicalSemanticDigest(logical) == LogicalSemanticDigest(projectPhysicalToLogicalSemantics(physical))

---

## CR-05 ROADMAP #22 / #23 BOUNDARY

#22 = EXECUTABLE_TASK_GRAPH_AND_WORKER_FABRIC_RUNTIME
#22 owns: runtime realization primitives, task lifecycle,
provider/worker/device binding, probing, isolation, retry/lease/heartbeat,
runtime cache mechanics, bounded/local dispatch.

#23 = DISTRIBUTED_SCHEDULING (unchanged)
#23 owns: cross-worker/global placement policy, queue pressure, worker
utilization optimization, deadline/resource scheduling, distributed locality
optimization.

Wording that grants the same scheduling/resource-placement authority to both
#22 and #23 is removed (ROADMAP22_23_SCOPE_OVERLAP=0): #22 = bounded/local
dispatch only; #23 = cross-worker/global placement policy.

---

## CR-06 CONTRACT CONSISTENCY

C1..C25 updated accordingly (see contract document revision). Mandatory final
state:

UNRESOLVED_DECISION_COUNT=0
ROADMAP21_OWNER_MODULE=media-execution-plan-module
EXECUTION_REQUIREMENT_DUAL_AUTHORITY=NO
LOGICAL_NODE_LOWERING_V1=1_TO_1
LOGICAL_DEPENDENCY_SEMANTIC_LOSS=NO
FUSION_V1=DEFERRED (no semantic projection proof required for V1 since fusion is deferred)
MEDIA_EXECUTION_PLAN_MODULE_DEFERRED_AS_WHOLE=NO
ROADMAP22_23_SCOPE_OVERLAP=0

---

## SCOPE CONTROL

PRODUCTION_CODE_CHANGED=NO
TEST_CODE_CHANGED=NO
SCHEMA_CHANGED=NO
IMPLEMENTATION_STARTED=NO
CANONICAL_MAIN_MERGE=NO

BLOCKERS=0
ARCHITECTURE_ESCALATION=NONE
ROADMAP_21_DECISION_RECOVERY=READY_FOR_CHATGPT_REVIEW
ROADMAP_21_IMPLEMENTATION=NO_GO
