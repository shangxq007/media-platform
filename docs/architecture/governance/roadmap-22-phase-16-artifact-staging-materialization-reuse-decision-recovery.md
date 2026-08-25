# Roadmap #22 Phase 16 — Artifact Staging, Materialization, Reuse Decision Recovery

MODE=DECISION_RECOVERY_AND_GOVERNANCE_AMENDMENT
CLEAN_FORWARD=AUTHORITATIVE
ARCHITECTURE_AUTHORITY=CHATGPT
ENGINEERING_CONTROL_PLANE=HERMES

## Topology

BASE_SHA=d761a3523b9d554f0ff79818c3e8c9f7aaef1d9c
BASE_TREE=f6ecb872a11d2352b082c057a9682e18a54772d1
EXPECTED_BRANCH=agent/roadmap22-executable-task-graph-worker-fabric-decision-recovery
CANONICAL_MAIN_SHA=036f21f7f94f61da92faa2e91934675d024d99e8
CANONICAL_MAIN_TREE=7a61effeb2840c428cab2705a9f529159fc4e345

PROJECT_STATE_BOOTSTRAP_REOPEN=NO
GOVERNANCE_MODE=NORMAL_INCREMENTAL_ARCHITECTURE_MAINTENANCE
ROADMAP_RENUMBERING=NO
PHASE_16_IMPLEMENTATION_STARTED=NO

## Repository Reality Findings

### Legacy ArtifactCache

Current class/package:
`render-module/src/main/java/com/example/platform/render/infrastructure/renderplan/ArtifactCache.java`
`com.example.platform.render.infrastructure.renderplan.ArtifactCache`

Current key type: `String inputHash`.
Current value type: `String outputUri`.
Backing implementation: `ConcurrentHashMap<String, String>` process-local map plus unsynchronized `long hits/misses` counters.
Callers: `DagExecutionEngine` constructor injection; `DagExecutionEngine.execute` calls `get(inputHash)` before node execution and `put(inputHash, output)` after execution.
Lifetime: Spring component lifetime / JVM process lifetime only; `clear()` deletes all entries.
Ownership: render infrastructure legacy mechanics, not artifact-module authority and not execution-fabric authority.
Disposition: REUSE_MECHANICS_ONLY now; MIGRATE_REDESIGN during Phase 16 implementation; delete/shadow-count only after all callers migrate.

### DagExecutionEngine

Current class/package:
`render-module/src/main/java/com/example/platform/render/infrastructure/renderplan/DagExecutionEngine.java`

Cache lookup occurs inside topological loop: if `node.cacheable()` and `node.inputHash()!=null`, `artifactCache.get(node.inputHash())` is read.
Cache hit behavior: non-null cache value is placed directly into `nodeOutputs`, node id is added to `cachedNodes`, and execution is skipped with `continue`.
Output representation: `String` output URI in `nodeOutputs`, `rootOutput`, `ExecutionResult.nodeOutputs`.
URI truth issue: current legacy surface treats URI string as reusable output truth; it does not validate Artifact commit, ArtifactId, ContentDigest, tenant authorization, quarantine/corruption, or lifecycle.
Failure behavior: node exceptions are collected in `errors`; execution continues through the loop and final `success` is `errors.isEmpty()`.
Disposition: MIGRATE_REDESIGN; the direct cache-hit skip is not Phase-16 authoritative behavior.

### IncrementalRenderPlan

Current class/package:
`render-module/src/main/java/com/example/platform/render/domain/planning/IncrementalRenderPlan.java`

Fields: `planId`, `mode`, `baseRevision`, `targetRevision`, `fullReRenderRequired`, `SemanticDiffResult diff`, `RenderImpactResult impact`, `PipelineExecutionPlan pipelinePlan`, `List<ReusableArtifact> reuse`, `List<String> executeTaskIds`, `List<String> reuseTaskIds`, `Map<String,String> metadata`.
Current reuse object: `ReusableArtifact(String artifactId, String taskId, String uri, String cacheKey, List<Integer> frameRange, String scope)`.
Current consumers: `IncrementalRenderPlanService`, `IncrementalRenderPlanResponse`, tests, and `PipelineDagExecutorService` via plan task parameters.
Current reuse authority: currently stronger than advisory in parts of the render pipeline; service writes `incrementalMode=reuse`, `skipExecution=true`, and `reuseArtifactUri` onto tasks after URI/hash filtering.
Disposition: MIGRATE_REDESIGN to reuse-candidate projection only. Final skip truth must come from ExecutionReuseKey + ArtifactReuseIndexPort + Artifact validation.

### Pruning

Current static pruning: `IncrementalRenderPlanService` uses semantic diff/impact, dirty task ids/types, segment dirty ids, and downstream-clean checks to mark tasks execute/reuse.
RenderExtent/segment pruning: `SegmentPlanFilter.restrictToTargetSegments` forces non-target segment tasks to reuse with optional `reuseArtifactUri` and `skipExecution=true`.
Execution-graph pruning: current implementation is PipelineExecutionPlan-level task mode rewriting, not Roadmap 21/22 dependency-preserving execution graph pruning.
Cache/reuse influence: reuse URI maps and content-hash filtering influence task reuse/skip today.
Disposition: MIGRATE_REDESIGN. Frozen order is semantic/static pruning first, then reuse candidate generation, then lookup/validation, then dependency-preserving execution pruning.

### Artifact / Storage / OpenDAL Reality

Artifact authority exists in `artifact-module`. `Artifact` pins `ArtifactId`, `tenantId`, `ContentDigest`, byte length, media type, kind, state, schema version, and createdAt.
Artifact commit current state: `JooqArtifactCommitService` is documented as sole production Artifact write authority and commits Artifact row + replica row + provenance edges in one transaction. `RenderArtifactStorageService` uploads bytes, computes SHA-256 digest, then calls `ArtifactCommitService.commit`.
Artifact pin validation current state: `TimelineArtifactPinValidator` / `ArtifactPinService` protect revision pins with `ArtifactId + ContentDigest`; artifact query tests prove cross-tenant empty results for artifact, replica, provenance, and content digest lookup.
Storage current state: `StorageProvider`/OpenDAL are storage mechanics. `AbstractOpenDalProvider` confines OpenDAL classes to storage provider internals and exposes storage contract types.
OpenDAL status: ADOPTED as storage access/provider mechanics; not Artifact authority and not cache authority.

## Legacy Surface Classification

| Surface | Classification | Decision |
| --- | --- | --- |
| ArtifactCache ConcurrentHashMap inputHash -> outputUri | REUSE_MECHANICS_ONLY / MIGRATE_REDESIGN | Do not promote. Replace with ExecutionReuseKey -> ArtifactPin via ArtifactReuseIndexPort. |
| DagExecutionEngine direct cache-hit skip | MIGRATE_REDESIGN | Cache hit cannot skip without Artifact commit/digest/tenant validation. |
| IncrementalRenderPlan reusable artifact URI list | MIGRATE_REDESIGN | Recast as candidate projection. |
| RenderCacheUriResolver | REUSE_MECHANICS_ONLY | URI resolution is data-plane mechanics, not reuse truth. |
| RenderCacheReuseValidator | REUSE_MECHANICS_ONLY | Hash validation is useful mechanics but insufficient authority. |
| SegmentPlanFilter forced reuse | MIGRATE_REDESIGN | Must become dependency-preserving validated reuse pruning. |
| Artifact module commit/pin/query authority | REUSE_AS_CANONICAL | Authoritative Artifact state remains here. |
| OpenDAL storage provider | REUSE_MECHANICS_ONLY | Storage access mechanics only. |

CLEAN_FORWARD_COMPATIBILITY_REQUIRED=NO
COMPATIBILITY_EVIDENCE=No external/persisted compatibility contract was found requiring preservation of hash-to-URI ArtifactCache authority.

## Adopted Cache and Artifact Reuse Foundation

CACHE_AND_ARTIFACT_REUSE_FOUNDATION=ADOPTED

This is a cross-cutting foundation, not a new numbered roadmap milestone. It spans process-local caches, host/worker materialization caches, cluster shared execution-reuse index, optional distributed byte cache, optional P2P distribution, validation, policy, observability, prefetch hints, eviction, and cacheability.

Do not create one universal runtime Cache service.

## Frozen Principles

- CACHE_IS_DERIVED_OPTIMIZATION_NOT_CANONICAL_AUTHORITY_V1
- CACHE_LOSS_AFFECTS_PERFORMANCE_NOT_CORRECTNESS_V1
- CACHE_SCOPE_MUST_BE_EXPLICIT_V1
- NO_UNIVERSAL_CACHE_GOD_ABSTRACTION_V1
- EXECUTION_REUSE_AND_ARTIFACT_MATERIALIZATION_ARE_DISTINCT_CACHE_CONCERNS_V1
- CACHE_KEY_IS_VERSIONED_AND_DETERMINISTIC_V1
- EXECUTION_REUSE_KEY_IS_VERSIONED_DETERMINISTIC_DERIVED_IDENTITY_V1
- REUSE_KEY_EXCLUDES_MUTABLE_RUNTIME_STATE_V1
- CACHE_VALUE_IS_ARTIFACT_PIN_NOT_STORAGE_URI_V1
- CACHE_HIT_REQUIRES_ARTIFACT_COMMIT_AND_DIGEST_VALIDATION_V1
- CACHE_INDEX_IS_NOT_ARTIFACT_EXISTENCE_AUTHORITY_V1
- CACHE_ENTRY_MUST_BE_VERIFIABLE_BEFORE_AUTHORITATIVE_REUSE_V1
- INCREMENTAL_PLAN_IDENTIFIES_REUSE_CANDIDATES_NOT_REUSE_TRUTH_V1
- PRUNING_PRECEDES_RUNTIME_REUSE_RESOLUTION_V1
- VALIDATED_REUSE_MAY_DRIVE_DEPENDENCY_PRESERVING_EXECUTION_PRUNING_V1
- REUSE_INDEX_EVICTION_DOES_NOT_DELETE_ARTIFACT_AUTHORITY_V1
- WORKER_MATERIALIZATION_CACHE_IS_DISTINCT_FROM_EXECUTION_ARTIFACT_REUSE_INDEX_V1
- CACHE_POLICY_IS_PLATFORM_OWNED_AND_BACKEND_INDEPENDENT_V1
- CACHE_POLICY_IS_INTENT_NOT_BACKEND_CONFIGURATION_V1
- CACHE_BACKEND_IS_REPLACEABLE_INFRASTRUCTURE_V1
- CACHE_HIERARCHY_MAY_SHORT_CIRCUIT_LOOKUP_BUT_NOT_AUTHORITY_VALIDATION_V1
- DISTRIBUTED_CACHE_IS_ACCELERATION_NOT_EXECUTION_OR_ARTIFACT_AUTHORITY_V1
- DISTRIBUTED_CACHE_LOCK_IS_NOT_EXECUTION_OWNERSHIP_AUTHORITY_V1
- CACHEABILITY_FAILS_CLOSED_FOR_NONDETERMINISTIC_EXECUTION_V1
- CACHE_OBSERVABILITY_IS_REQUIRED_BEFORE_DISTRIBUTED_CACHE_OPTIMIZATION_V1
- OPENDAL_IS_STORAGE_ACCESS_MECHANICS_NOT_ARTIFACT_CACHE_AUTHORITY_V1
- STORAGE_PROVIDER_AND_ARTIFACT_MATERIALIZER_ARE_DISTINCT_PORTS_V1
- ARTIFACT_MATERIALIZATION_MAY_USE_STORAGE_PROVIDER_OR_DISTRIBUTED_CACHE_V1
- PROVIDER_MUST_NOT_DEPEND_DIRECTLY_ON_STORAGE_OR_CACHE_BACKEND_V1
- ARTIFACT_MATERIALIZATION_IS_A_REPLACEABLE_DATA_PLANE_CAPABILITY_V1
- PROVIDER_CONSUMES_MATERIALIZED_ARTIFACT_NOT_STORAGE_BACKEND_V1
- LARGE_ARTIFACT_CACHE_IS_TRANSPARENT_TO_PROVIDER_EXECUTION_V1
- SEMANTIC_CACHE_INVALIDATION_PREFERS_NEW_DERIVED_KEY_OVER_MUTABLE_INVALIDATION_V1
- BYTES_EXIST_IS_NOT_ARTIFACT_COMMITTED_OR_TASK_COMPLETED_V1
- DISTRIBUTED_CACHE_LOCK_IS_NOT_EXECUTION_OWNERSHIP_AUTHORITY_V1

## Phase 16 Bounded Architecture Contract V1

ROADMAP_22_PHASE_16_ARTIFACT_STAGING_MATERIALIZATION_REUSE_BOUNDED_ARCHITECTURE_CONTRACT_V1=FROZEN

P16-C1: Phase 16 owns artifact staging, artifact materialization, execution-result reuse, local worker cache, validation, output commit, incremental reuse, and cache-aware execution pruning around the accepted Phase-15 provider-native boundary.
P16-C2: ExecutionReuseKey V1 is a versioned deterministic derived computation identity, not canonical domain identity.
P16-C3: Reuse-key participants must include the schema/version, ExecutableTask semantic identity, provider binding/implementation/contract/profile identity, immutable input ArtifactId + ContentDigest pins, operation/output/materialization semantics, and any deterministic seeds/model/content pins when applicable. It must exclude ExecutionAttemptId, ExecutionOwnershipGeneration, TaskLease, WorkerRuntimeId, PhysicalHostId, DeviceId, reservations, queue depth, utilization, probe freshness, wall clock, createdAt, trace/correlation ids, and mutable cache state.
P16-C4: ArtifactReuseIndexPort maps ExecutionReuseKey to ReusableArtifactRecord / ArtifactPin metadata, not raw URI.
P16-C5: Reuse hit requires Artifact existence, COMMITTED/available state, tenant authorization, ContentDigest match, non-quarantined/non-corrupt state, and lifecycle/retention permission before execution can be skipped.
P16-C6: Tenant scope fails closed. Default reuse is within tenant; cross-project/workspace reuse requires explicit policy; cross-tenant logical reuse is forbidden unless a future explicit authorization model is adopted.
P16-C7: IncrementalRenderPlan identifies candidates only; it is not reusable Artifact truth.
P16-C8: Static semantic pruning precedes runtime reuse resolution.
P16-C9: Validated reuse may drive dependency-preserving execution pruning only when dependency correctness and remaining consumers are preserved.
P16-C10: ArtifactMaterializerPort converts ArtifactPin to runtime-consumable MaterializedArtifact.
P16-C11: DirectOpenDalMaterializer may use StorageProvider/OpenDAL behind the materializer port, without exposing OpenDAL to Provider SPI.
P16-C12: Worker-local materialization cache is a host/worker byte cache keyed by Artifact content identity, bounded and rebuildable.
P16-C13: Corrupt/stale local entries become misses; no partial write appears as valid hit.
P16-C14: Eviction never deletes durable Artifact authority.
P16-C15: Output staging is temporary bytes before Artifact commit; staged bytes are not completion.
P16-C16: ContentDigest calculation/validation gates Artifact commit and reuse.
P16-C17: Artifact authoritative commit remains in artifact authority.
P16-C18: ExecutionAttempt/generation/completion fencing must prevent stale generations from committing authoritative completion.
P16-C19: Providers consume materialized artifacts and typed execution outputs, never concrete storage/cache backend APIs.
P16-C20: Cacheability fails closed for nondeterministic execution; supported conceptual states are CACHEABLE, CACHEABLE_WHEN_FULLY_PINNED, NOT_CACHEABLE.
P16-C21: Reuse decisions must be explainable: task skipped because validated Artifact was reused for a specific ExecutionReuseKey.
P16-C22: Cache/reuse/materialization observability is required before distributed-cache optimization.
P16-C23: Alluxio/JuiceFS/Dragonfly/Redis/Caffeine are deferred/candidate mechanics with evidence triggers, not Phase-16 correctness dependencies.
P16-C24: Legacy ArtifactCache hash-to-URI authority must be migrated away and guarded to zero before implementation closure.
P16-C25: IncrementalRenderPlan legacy URI truth must be migrated to candidate-only semantics before implementation closure.

## Implementation Phasing

P16-0: Clean-forward legacy ArtifactCache / IncrementalRenderPlan disposition.
P16-1: ExecutionReuseKey V1.
P16-2: ArtifactReuseIndexPort.
P16-3: PostgreSQL persistent reuse-index implementation.
P16-4: Artifact validation and reusable Artifact decision.
P16-5: ArtifactMaterializerPort.
P16-6: DirectOpenDalMaterializer.
P16-7: Worker-local NVMe materialization cache.
P16-8: IncrementalRenderPlan migration to reuse-candidate projection.
P16-9: ValidatedReuseDecision.
P16-10: Dependency-preserving cache-aware execution pruning.
P16-11: Output staging / temporary materialization.
P16-12: ContentDigest + Artifact commit.
P16-13: Attempt/generation/completion fencing.
P16-14: Cache/materialization observability.
P16-15: Clean-forward guards and full validation.

## Clean-Forward Guard Plan

Implementation closure should prove meaningful zero counts for legacy hash-to-URI authority, raw storage URI reuse identity, direct skip without validation, provider direct OpenDAL/S3/RustFS/R2 dependencies, cache as artifact existence authority, cache eviction deleting Artifact, PlanLowerer mutable cache reads, Redis execution authority, distributed-cache execution lock authority, and cross-tenant reuse without authorization. Guards must combine source-structure checks with behavior tests for validation/fencing; grep-only counters are insufficient for correctness claims.

## Technology Status

OpenDAL: ADOPTED as storage access/provider mechanics, not cache authority.
BAZEL_REMOTE_CACHE_CAS_MODEL: REFERENCE_ONLY architecture reference for action/execution key -> result metadata -> content-addressed blobs.
Alluxio: POC_CANDIDATE for future distributed large-Artifact materialization/cache layer.
JuiceFS: POC_CANDIDATE for future cached POSIX/distributed filesystem materialization option.
Dragonfly: POC_CANDIDATE for future P2P immutable large-object fan-out distribution.
Redis: DEFERRED candidate for cluster hot metadata/reuse-index acceleration only; not byte store and not truth.
Caffeine: DEFERRED candidate for process-local hot metadata/reuse lookup acceleration; not Phase-16 closure dependency.

## Distributed Cache Entry Triggers

ALLUXIO_POC_TRIGGER: multiple workers, repeated remote reads, object-store bandwidth utilization, duplicate remote download bytes, low worker cache hit ratio, projected cluster hit benefit, or materialization latency.
JUICEFS_POC_TRIGGER: strong POSIX namespace requirement, providers/tools needing transparent filesystem semantics, or cached filesystem mount behavior.
DRAGONFLY_POC_TRIGGER: high concurrent fan-out of same immutable Artifact, large Artifact/model sizes, or object-store egress saturation.
REDIS_POC_TRIGGER: multiple app instances, reuse-index lookup QPS, PostgreSQL reuse-index latency, hot-key concentration, and proven DB bottleneck.

## Observability Vocabulary

Metrics vocabulary: cache_lookup_total, cache_hit_total, cache_miss_total, cache_stale_total, cache_corrupt_total, cache_bytes_read, cache_bytes_written, cache_eviction_total, cache_evicted_bytes, cache_validation_failure_total, materialization_remote_bytes, materialization_local_bytes, materialization_latency, worker_cache_used_bytes, worker_cache_capacity_bytes, artifact_reuse_hit_total, artifact_reuse_miss_total.

Avoid high-cardinality metric labels such as ArtifactId, ExecutableTaskId, or ExecutionReuseKey. Use traces/logs for identity detail.

## Validation Target

CACHE_ARTIFACT_REUSE_MATERIALIZATION_CONFORMANCE is added as the future validation project for cache loss safety, validation before reuse, URI non-identity, semantic/digest invalidation, mutable runtime exclusion, stale/corrupt miss behavior, eviction safety, tenant fail-closed reuse, dependency correctness, stale generation fencing, and provider independence from storage/cache backend.

## Decision Recovery Result

ROADMAP_22_PHASE_16_DECISION_RECOVERY=PASS
CACHE_AND_ARTIFACT_REUSE_FOUNDATION=ADOPTED
ROADMAP_22_PHASE_16_ARTIFACT_STAGING_MATERIALIZATION_REUSE_BOUNDED_ARCHITECTURE_CONTRACT_V1=FROZEN
READY_FOR_PHASE_16_IMPLEMENTATION=YES_AFTER_CHATGPT_FINAL_REVIEW
ARCHITECTURE_BLOCKERS=0
ARCHITECTURE_ESCALATION=NONE
NEXT_ACTION=CHATGPT_ROADMAP_22_PHASE_16_DECISION_RECOVERY_FINAL_REVIEW
