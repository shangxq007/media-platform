# Roadmap #22 Phase 16 — Decision Recovery Correction 1

MODE=APPEND_FORWARD_DOCS_ONLY_CORRECTION
CLEAN_FORWARD=AUTHORITATIVE
ARCHITECTURE_AUTHORITY=CHATGPT
ENGINEERING_CONTROL_PLANE=HERMES

ORIGINAL_DECISION_RECOVERY_SHA=14ca7c550a844bb7912e33b2e2d47bbce0065870
ORIGINAL_DECISION_RECOVERY_TREE=00e05ec3ada69f70e6157a25018ae3883a1aeebf

CHATGPT_ROADMAP_22_PHASE_16_DECISION_RECOVERY_FINAL_REVIEW=FAIL_CORRECTABLE
CORRECTABLE_FINDINGS=2
F1=PHASE_16_MODULE_OWNERSHIP_NOT_FROZEN
F2=INTER_TASK_DEPENDENCY_DERIVED_EXECUTION_REUSE_KEY_NOT_FROZEN

ARCHITECTURE_REOPEN=NO
ARCHITECTURE_ESCALATION=NONE
PHASE_16_IMPLEMENTATION_STARTED=NO
ROADMAP_23=NOT_STARTED

## Module Ownership Contract

MODULE_OWNERSHIP_FROZEN=YES

media-execution-plan-module owns:
- ExecutionReuseKey V1 value / derived identity.
- deterministic reuse-key canonicalization.
- dependency-derived / Merkle reuse-key mechanics.
- pure dependency-preserving execution-pruning mechanics.

Rationale: these are pure immutable execution semantics/mechanics over ExecutableTask and provider-bound execution structure. They must not depend on mutable Worker runtime state.

worker-fabric-module owns:
- ArtifactReuseIndexPort.
- PostgreSQL ArtifactReuseIndex adapter.
- reuse lookup orchestration.
- ValidatedReuseDecision.
- ArtifactMaterializerPort.
- materialization orchestration.
- Worker-local materialization cache.
- runtime staging orchestration.
- ExecutionAttempt / ExecutionOwnershipGeneration fencing orchestration.

artifact-module retains:
- Artifact domain authority.
- ArtifactId.
- ContentDigest.
- ArtifactState.
- Artifact query / validation.
- Artifact commit.
- lifecycle authority.

storage-module retains StorageProvider contract/mechanics.
storage-provider-opendal retains concrete OpenDAL adapter mechanics.
render-module retains IncrementalRenderPlan candidate production, semantic diff/impact/pruning inputs, and no final reusable Artifact truth.

Dependency direction preserved:
worker-fabric-module -> media-execution-plan-module
Never:
media-execution-plan-module -> worker-fabric-module

NEW_MODULE_CREATED=NO

If implementation proves this ownership creates a real dependency cycle, stop and escalate before creating a module.

## Dependency-Derived ExecutionReuseKey Contract

MERKLE_DEPENDENCY_REUSE_KEY=FROZEN

Current repository reality: ExecutionIoProjection.InputBinding.sourceArtifact is nullable for ordinary computed inter-node/inter-task dependencies, and ExecutionArtifactBoundary intentionally carries no pre-invented output Artifact identity. Therefore ExecutionReuseKey must not require an already-materialized ArtifactId/ContentDigest for every computed predecessor.

Frozen principles:
- EXECUTION_REUSE_KEY_DEPENDENCY_PROPAGATION_IS_MERKLE_DERIVED_V1
- ROOT_SOURCE_INPUT_REUSE_KEY_USES_EXACT_ARTIFACT_PIN_V1
- COMPUTED_INTER_TASK_INPUT_REUSE_KEY_USES_PREDECESSOR_REUSE_IDENTITY_V1
- MATERIALIZED_ARTIFACT_DIGEST_VALIDATES_REUSE_BUT_DOES_NOT_DEFINE_PRE_EXECUTION_DEPENDENCY_IDENTITY_V1

Algorithmic contract:
- Root/source input contribution = exact ArtifactId + ContentDigest pin.
- Computed inter-task input contribution = producer ExecutionReuseKey + exact producer output declaration/identity + exact dependency/boundary semantics.
- Compute task reuse keys in deterministic topological order.
- ExecutableTask-local provider-local fused memberships remain one task identity; do not recursively treat internal same-task memberships as separate scheduler tasks.

Required property: if producer semantic identity or producer ProviderBindingPin changes, the producer ExecutionReuseKey changes and every dependent ExecutionReuseKey changes deterministically.

This enables validated downstream reuse to drive dependency-preserving pruning of unneeded upstream tasks without first executing or materializing those upstream tasks.

FUTURE_ARTIFACT_PIN_REQUIRED_TO_COMPUTE_INTER_TASK_KEY=NO

## Key Identity vs Artifact Validation

ExecutionReuseKey identity answers whether this is the same fully pinned computation.
Artifact validation answers whether a committed reusable result Artifact still exists and remains authorized.

Reuse validation path:
ExecutionReuseKey -> ReusableArtifactRecord / ArtifactPin -> Artifact authority lookup -> tenant authorization -> ArtifactState AVAILABLE -> exact ContentDigest match -> corruption/quarantine/lifecycle checks -> ValidatedReuseDecision.

Storage URI must not participate in reuse identity and must not replace Artifact authority validation.

## Stale Generation Clarification

A stale ExecutionAttempt / ExecutionOwnershipGeneration must not:
- publish authoritative execution output association.
- update ArtifactReuseIndex as the winning task result.
- mark ExecutableTask completed.

If immutable bytes were already written/registered before stale ownership is discovered, they may become unreferenced/orphaned Artifact/storage cleanup concerns, but must not become the authoritative winning task result.

Artifact authority remains in artifact-module. Worker-fabric orchestrates fencing; it does not own Artifact domain authority.

## Status

ROADMAP_22_PHASE_16_DECISION_RECOVERY=READY_FOR_CHATGPT_CORRECTION_1_FINAL_REVIEW
ARCHITECTURE_BLOCKERS=0
ARCHITECTURE_ESCALATION=NONE
NEXT_ACTION=CHATGPT_ROADMAP_22_PHASE_16_DECISION_RECOVERY_CORRECTION_1_FINAL_REVIEW
