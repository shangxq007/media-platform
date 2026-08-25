# Roadmap #22 Phase 15 — PlanLowerer / RuntimeAdapter Generic Boundary

Mode: BOUNDED_IMPLEMENTATION

Architecture authority: CHATGPT
Engineering control plane: HERMES

PHASE_15_BASE_SHA=5b80f0d5c209d99330c0ef32eab2899812cd38a9
PHASE_15_BASE_TREE=e85d6f87b38ff909cae0ae30cb5a5075d3ae3979
PHASE_15_PARENT_AFTER_PRE_REPAIR=78990af25cd4d084c744347e4f00270bacbe999d
PHASE_15_PRE_REPAIR_RECORD=docs/architecture/governance/roadmap-22-pre-phase15-cip2-drift-gate-repair-v1.md

## Scope

Phase 15 establishes the generic provider-native lowering/runtime SPI that bridges already-provider-bound executable semantics into typed provider-native plans and runtime invocation commands.

It does not implement FFmpeg-specific lowering, Artifact staging/materialization, sandbox/isolation, remote AI providers, Apache Camel, Webhook runtime, OpenCue, or Roadmap #23 scheduling/optimization.

## Authority Boundary

ProviderBoundExecutableTaskGraph -> ExecutableTask -> PlanLowerer -> ProviderNativeExecutionPlan -> InvocationSpec(s) -> RuntimeAdapter -> typed ExecutionCommand / provider API request.

The following authorities remain upstream and are not redefined:

- Roadmap #20 RenderPlan / RenderGraph WHAT authority.
- Roadmap #21 provider-neutral execution structure authority.
- Roadmap #22 Epochs 1-3 provider binding, worker/runtime, assignment, attempt, generation, backend, observation, and completion-fence authority.

## Types Introduced

Production package:

`worker-fabric-module/src/main/java/com/example/platform/workerfabric/domain/providernative/`

- ProviderNativeExecutionPlan
- PlanLowerer
- StaticProviderExecutionContext
- InvocationSpec
- InvocationKind
- ProcessInvocationSpec
- BackendSubmissionInvocationSpec
- ExecutionCommand
- RuntimeAdapter
- RuntimeExecutionContext
- RuntimeExecutionBundle
- ProviderNativeFailureCode
- ProviderNativeExecutionFailure

## Types Reused

- ExecutableTask / ExecutableTaskId from media-execution-plan-module.
- ProviderBindingPin and exact ProviderExecutionContractVersion / ProviderVersion / ProviderImplementationId / ProviderCapabilityProfileVersionOrDigest from media-execution-plan-module.
- ExecutionAttemptId and ExecutionOwnershipGeneration from worker-fabric-module.

## Clean Forward Disposition

REUSE_AS_CANONICAL:
- ExecutableTask
- ExecutableTaskId
- ProviderBindingPin
- ProviderExecutionContractVersion
- ExecutionAttemptId
- ExecutionOwnershipGeneration

REUSE_MECHANICS_ONLY:
- Existing render-module command builders and provider-specific local render adapters remain legacy/provider-specific mechanics, not generic Phase-15 authority.

MIGRATE_REDESIGN:
- Generic provider-native lowering/runtime contracts are introduced in worker-fabric-module, not render-module or media-execution-plan-module.

DELETE_SHADOW:
- No existing generic Phase-15 shadow authority was found that could be safely deleted.

DEFER_TO_PHASE_16_PLUS:
- Artifact staging/materialization, runtime filesystem/path resolution, output temporary materialization, output checksum/validation, Artifact commit.

DEFER_TO_PHASE_19:
- FFmpegPlanLowerer, FFmpegExecutionGraph, FFmpegCommandCompiler, provider-ffmpeg.jar, concrete FFmpeg provider plugin.

## Tests and Guards

New tests:

- ProviderNativeLoweringRuntimeAdapterTest
- ProviderNativeArchitectureGuardTest

Covered invariants include deterministic lowering, ProviderBindingPin preservation, provider mismatch typed failure, one ExecutableTask lowering boundary, legal same-task multi-membership lowering, required Artifact boundary fail-closed behavior, unsupported semantics typed failure, runtime adapter plan-type mismatch, shared platform attempt/generation scope for multiple native commands, no shell-string command authority, and RuntimeAdapter non-rebinding.

Architecture guards cover:

- media-execution-plan does not depend on worker-fabric.
- ExecutableTask does not gain provider-native command fields.
- PlanLowerer package does not import mutable runtime-state authorities.
- Generic provider-native plan does not expose shellCommand/commandLine string authority.
- RuntimeAdapter does not depend on domain repositories for mutation.
- No concrete FFmpeg provider production implementation in Phase 15.
- No new direct Render/Timeline to provider-native compiler.
- No cross-task lowering/fusion API.
- No Provider rebind/fallback/latest selection in RuntimeAdapter code.
- Native command does not own independent lease/assignment/attempt lifecycle authority.

## Deferred Work

- Phase 16: Artifact staging/materialization and Artifact commit integration.
- Phase 17+: sandbox/isolation and runtime filesystem/materialization mechanics.
- Phase 19: first real FFmpeg provider plugin and provider-specific lowering.
- Roadmap #23: distributed/global scheduling and optimization.

## Publication

Implementation SHA/tree are recorded in the final Phase-15 report after commit freeze. This record is part of the implementation commit and does not rewrite prior governance records.
