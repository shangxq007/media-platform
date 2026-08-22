# PRE_ROADMAP_21_MODULE_BOUNDARY_AND_PLANNER_PURITY_HARDENING_CONTRACT_V1

Frozen bounded architecture contract for PRE-ROADMAP-21 bounded implementation.
Each clause is specific, mechanically testable, narrow, provider-neutral, and
compatible with Roadmap #21 (which owns ExecutionRequirement / LogicalExecutionGraph /
PhysicalPlanner / PhysicalExecutionPlan — NOT started by this contract).

## C1 — Logical planning purity

PLANNING_IS_PURE_COMPUTATION_OVER_EXPLICIT_INPUTS_V1.
The logical planner (ExecutionPlannerService or equivalent) MUST NOT read mutable
runtime state (ProductRuntimeService, ProducerRuntimeService, runtime product status,
runtime dependencies, provider readiness, ambient registries, DB-backed runtime
availability) during logical planning.
Mechanical invariant: LOGICAL_PLANNER_RUNTIME_MUTABLE_READ_COUNT = 0.
All runtime facts arrive as an explicit frozen planning input.

## C2 — Frozen Planning Context

Logical planning operates over an immutable Frozen Planning Context containing:
requested operation semantics, declared capability requirements, resolved/frozen
capability implementation facts, requested RenderExtent, deterministic semantic
configuration, immutable source/materialization references, explicit planning policies.
No planner mutation of the context.

## C3 — Runtime resolution boundary

Runtime/capability resolution happens BEFORE logical planning and produces the
Frozen Planning Context. The planner never performs runtime resolution itself.

## C4 — CapabilityRequirement authority

Semantic consumers/OperationDefinitions DECLARE CapabilityRequirement. The resolver
(RESOLVES declared requirements, filters implementations, validates availability,
chooses among eligible implementations) but MUST NOT invent/derive/guess semantic
requirements from productType, task type, provider type, enum mappings, or render
implementation choices.
Mechanical invariant: RESOLVER_INVENTED_CAPABILITY_REQUIREMENT_COUNT = 0.
The existing switch mapping (CapabilityResolutionService.mapToCapability) is retired
CLEAN FORWARD after caller migration (LEGACY_CALL_COUNT=0, LEGACY_DEFINITION_COUNT=0,
COMPATIBILITY_WRAPPER_COUNT=0, DUAL_AUTHORITY_COUNT=0).

## C5 — Capability resolver responsibility

Resolver responsibilities are limited to: resolve declared requirements, filter
implementations, validate availability, select among eligible implementations.
No semantic authority.

## C6 — Module interaction policy

MODULE_INTERACTION_REQUIRES_EXPLICIT_EXPOSED_CONTRACT_V1.
CROSS_MODULE_INTERNAL_TYPE_ACCESS_IS_NOT_A_STABLE_ARCHITECTURE_CONTRACT_V1.
Cross-module interaction uses intentional domain surfaces, application contracts,
ports, or Spring Modulith NamedInterface where semantically appropriate. No ceremony
interfaces; no hiding of good stable domain types.

## C7 — Critical Modulith defects

Only critical pre-#21 cross-module boundary defects are repaired in PRE-#21;
remaining violations are classified (REAL_ARCHITECTURE_DEFECT / WRONG_EXPOSED_SURFACE /
HISTORICAL_DEBT / GOVERNANCE_DEFECT / NON_BLOCKING_HARDENING / FALSE_POSITIVE) with a
burn-down plan. No aesthetic zero-violation target.

## C8 — Application-layer contracts

app/web surfaces depend on modules through explicit application contracts/ports;
legitimate stable application/domain contracts are allowed without forced indirection.

## C9 — Render planning/runtime boundary

LOGICAL_PLANNING_RUNTIME_INFRASTRUCTURE_LEAK_COUNT = 0.
Layering: Logical Planning → Physical Planning → Execution Orchestration →
Provider Adapter → Artifact Materialization. No provider-native fields
(ffmpegCommand/ffmpegArgs/useFFmpeg/ffmpegFilterGraph or equivalent) in canonical
models. FFmpeg is the reference execution adapter, not canonical authority.

## C10 — FrameStream authority / fail-closed

AUTHORITATIVE_FRAMESTREAM_FAIL_CLOSED: authoritative frame output is deterministic
and fail-closed. Result reports REQUESTED_RENDER_EXTENT vs ACHIEVED_RENDER_EXTENT.
Insufficient extent → typed unsupported or typed failure. Never silent fallback,
never partial output as authoritative success.
AUTHORITATIVE_FRAMESTREAM_PARTIAL_SUCCESS_COUNT = 0.

## C11 — Requested vs achieved RenderExtent

RenderExtent (exact half-open [start,end) + rational frame rate) is the planning-time
request semantics; achieved extent is validated against it at execution result time.

## C12 — Error algebra ownership

Semantic failure category owned by domain/orchestration; provider-native code mapped
by adapter; transport DTO/status owned by API.
CapabilityId != SemanticFailureCategory != ProviderNativeCode.
No global mega ErrorCode. GLOBAL_MEGA_ERROR_CODE_AUTHORITY_COUNT = 0.

## C13 — Identity → Artifact/Storage boundary

Identity consumes Artifact/Storage application contracts for import/export only.
Identity must NOT orchestrate storage lifecycle, choose storage providers, own
materialization, or become storage cleanup authority. No bypass of Artifact/Storage
contracts; narrow any infrastructure-package imports found.

## C14 — Intentional pure-domain exposed surfaces

Pure semantic modules (audio, fonttext, media, color-image, graph, algorithm)
may expose stable semantic types intentionally; document exposed surfaces with
consumers and stability expectation; Modulith exposure metadata correct where present.

## C15 — CLEAN FORWARD deletion requirements

For every retired unshipped API: REPLACE CALLERS → PROVE ZERO CALLERS → PROVE ZERO
RETIRED DEFINITIONS/WRAPPERS/DUAL AUTHORITIES → DELETE OLD SURFACE. No compatibility
presumption without concrete external/persisted evidence.

## C16 — TimelineAssetGcService hygiene

Remove the proven-dead DSLContext field/dependency and TIMELINE_SNAPSHOT import
(mechanical evidence: dsl. usages = 0, TIMELINE_SNAPSHOT body refs = 0). No
compatibility retention.

## C17 — Roadmap #21 boundary

PRE-#21 prepares boundaries only. It does NOT implement ExecutionRequirement,
LogicalExecutionGraph, PhysicalPlanner, PhysicalExecutionPlan, graph semantics,
parallel regions, temporal windows, partition/fusion hints, resource/locality
requirements, distributed scheduler, orchestration frameworks, or full cache infra.
