# Roadmap #22 Phase 17 — Sandbox / Isolation Decision Recovery

TASK_ID=ROADMAP_22_PHASE_17_SANDBOX_ISOLATION_DECISION_RECOVERY
STATUS=FROZEN_CANDIDATE_REQUIRING_CHATGPT_REVIEW
BASE_SHA=d2cc856939fe0a73d6f1ef799078a0a5e7c5b179
BASE_TREE=d2e68f5af848cb49a5db1ea33cd8629ad5b250e0
PHASE_17_IMPLEMENTATION_STARTED=NO

## Scope and non-goals

Sandbox/Isolation is cross-domain execution-safety infrastructure. It is not Media, Timeline, RenderPlan, ExecutionPlan, Provider, WorkerRuntime, Artifact, Workflow, scheduler, optimizer, or resource-accounting authority. Technology is implementation mechanics, not architecture authority. This Decision Recovery does not implement Phase 17, Community Compute, delegated backends, accelerators, FFmpeg, or Roadmap #23.

## Frozen authority table

| Concern | Authority | Sandbox role | Explicit non-authority |
|---|---|---|---|
| Task semantics | media-execution-plan / ExecutableTask | consume immutable task runtime request | task/DAG/plan identity |
| Process integration | RuntimeAdapter | calls bounded sandbox execution boundary | second RuntimeAdapter / Provider |
| Provider semantics | ProviderBindingPin / ProviderExecutionContract | consume provider runtime invocation | compatibility/capability authority |
| Worker identities | worker-fabric WorkerRuntime/PhysicalHost/Device | consume authorized runtime context | identity/lifecycle authority |
| Artifact | ArtifactMaterializer / Artifact domain | emit candidate bytes/observations only | Artifact commit / AVAILABLE |
| Completion | ownership generation and completion authority | report observation only | task completion/reuse WINNING |
| Resource | Capacity/Reservation/ObservedUsage | enforce derived limits | capacity/reservation accounting |
| Policy | existing trust/tenant/data/entitlement authorities | evaluate sandbox feasibility input | universal security god object |

Laws: SANDBOX_IS_EXECUTION_SAFETY_INFRASTRUCTURE_NOT_DOMAIN_AUTHORITY_V1; SANDBOX_IS_NOT_RUNTIME_ADAPTER_V1; SANDBOX_IS_NOT_PROVIDER_AUTHORITY_V1; SANDBOX_IS_NOT_WORKER_AUTHORITY_V1; SANDBOX_CANNOT_AUTHOR_ARTIFACT_COMMIT_V1; SANDBOX_CANNOT_AUTHOR_EXECUTION_COMPLETION_V1; SANDBOX_RESOURCE_LIMIT_IS_ENFORCEMENT_NOT_RESOURCE_ACCOUNTING_AUTHORITY_V1; SANDBOX_TRUST_POLICY_IS_DISTINCT_FROM_PROVIDER_COMPATIBILITY_V1; SANDBOX_TECHNOLOGY_IS_IMPLEMENTATION_NOT_ARCHITECTURE_AUTHORITY_V1; SANDBOX_FAILS_CLOSED_V1; NO_UNIVERSAL_SECURITY_RUNTIME_GOD_OBJECT_V1.

## Repository reality and Clean Forward ledger

The canonical, mechanically parsed ledger is:

`docs/architecture/governance/automated-guards/phase17-sandbox-isolation-clean-forward-ledger.tsv`

Each row identifies one exact tracked repository surface and contains: ROW_ID, EXACT_PATH, SYMBOL_OR_COMPONENT, CURRENT_CALLERS, CURRENT_DEPENDENCY_DIRECTION, CURRENT_RUNTIME_ROLE, CURRENT_AUTHORITY_CLAIM, PHASE_17_RELEVANCE, DISPOSITION, and RATIONALE. It covers existing extension/plugin sandbox production and tests; sandbox-worker source, tests, configuration and deployment; direct production ProcessBuilder paths; worker-fabric sandbox eligibility and Artifact/fencing authorities; OpenCue source/tests/docs; Kubernetes/GitOps sandbox surfaces; and generated persistence projections. No path placeholder or undeclared glob is permitted.

TOTAL_ROWS=131
REUSE_AS_CANONICAL_COUNT=8
REUSE_MECHANICS_ONLY_COUNT=21
MIGRATE_REDESIGN_COUNT=37
DELETE_SHADOW_COUNT=0
DEFER_COUNT=65
UNCLASSIFIED_COUNT=0
DUPLICATE_ROW_COUNT=0

The counts above are derived from the TSV by `docs/architecture/governance/automated-guards/check-phase17-sandbox-ledger.py`; they are not hand-maintained independently.

## Typed contract proposal

Phase 17 implementation requires bounded worker-fabric-owned runtime contracts equivalent to: IsolationRequirement (immutable runtime requirement, non-semantic, no digest participation); SandboxCapabilityAdvertisement (mutable ephemeral capability evidence); SandboxFeasibilityDecision (ephemeral policy/capability decision); SandboxExecutionRequest/Handle/Observation/Result (ephemeral attempt-correlated runtime records); bounded Filesystem, Network, Process, Privilege, Environment, Secret, ResourceLimit and DeviceExposure policies. Exact names are implementation-time decisions; no universal context object is authorized.

Immutable policy/requirements remain separate from mutable runtime capability advertisement. CAN_RUN technical feasibility remains distinct from MAY_RUN trust/tenant/data permission.

## Process, filesystem, network and secrets

Process: RuntimeAdapter -> sandbox boundary -> concrete process/provider runtime. Process tree containment, cancellation, timeout, forced termination, orphan cleanup, bounded stdout/stderr capture, signal/result mapping are required. Exit 0 is only an observation, never authoritative completion.

Filesystem: immutable Artifact inputs are materialized explicitly and read-only by default; writable workspace/temp/output areas are bounded and platform-controlled; no implicit host path exposure; traversal, unsafe absolute paths, mounts, symlink/hardlink/special file escape must fail closed. Output bytes enter platform staging, then existing Artifact commit path.

Network: default no-network. Explicit bounded egress is a requirement/policy decision; no implicit ingress, DNS, proxy, endpoint or host network access. Native Pull, delegated backend and RemoteProvider can have distinct requirements without sandbox choosing backend authority.

Secrets: runtime scoped only; never canonical state or semantic digest; redacted from logs; short lived where feasible; injection mechanism (environment/file/FD/helper) is implementation-specific and selected only when justified. Cleanup/revocation is a lifecycle obligation.

Environment: explicit PATH/HOME/TMPDIR/workdir/umask/LANG/LC_*/TZ policy; no silent host environment inheritance. Environment control supports determinism but does not own semantic determinism.

## Resources, privilege and devices

Authorized reservation -> sandbox enforcement limit -> observed usage. Sandbox is not Capacity, Reservation or ObservedUsage authority. Enforcement hooks cover CPU, memory, pids/threads, disk/temp, file descriptors, I/O where feasible, timeout and future device grants.

Default device exposure is NONE. Explicit bounded DeviceId grant is consumed from worker/provider context; sandbox does not invent Device identity. Phase 20 accelerator support is out of scope.

Rootless/unprivileged execution is preferred where feasible. Privileged execution, host PID/IPC/network namespace, host sockets, ambient capabilities and no-new-privileges relaxation are denied by default unless an explicitly authorized bounded implementation case exists. Platform-neutral properties are canonical; Linux namespaces/cgroups/seccomp are mechanisms.

## Artifact, fencing and failure algebra

Lifecycle: ExecutableTask -> assignment/ownership -> Sandbox launch -> process -> candidate bytes -> staging validation -> Artifact commit -> authoritative completion. Stale attempt/generation cannot authoritatively commit/complete; cancellation and late observations are fenced; cleanup failure cannot overwrite committed authority.

Required failure categories: SANDBOX_UNAVAILABLE; SANDBOX_CAPABILITY_UNSUPPORTED; SANDBOX_POLICY_UNSATISFIABLE; SANDBOX_SETUP_FAILED; PROCESS_LAUNCH_FAILED; PROCESS_TERMINATED_BY_LIMIT; PROCESS_TIMEOUT; PROCESS_CRASHED; FILESYSTEM_POLICY_VIOLATION; NETWORK_POLICY_VIOLATION; SECRET_INJECTION_FAILED; PRIVILEGE_SETUP_FAILED; DEVICE_EXPOSURE_FAILED; OUTPUT_STAGING_FAILED; SANDBOX_CLEANUP_FAILED; SANDBOX_RUNTIME_LOST. They are infrastructure/runtime failures, not Provider incompatibility.

## Zero guard plan

Implementation must enforce zero counts for sandbox domain/media/timeline/renderplan/execution-plan/provider/worker/device identity authority; Artifact commit; self-authored completion; second fencing/resource authority; secret canonical state/digest; unbounded host paths/network/privilege/device exposure; fail-open host execution; second RuntimeAdapter; global scheduler/optimizer; Community Compute implementation; compatibility wrappers; legacy dual authority; unclassified ledger rows.

## Technology classification

Docker: ADOPTED build/test mechanics only. Podman: POC_CANDIDATE local rootless execution mechanism. Linux namespaces, cgroups v2, seccomp, no-new-privileges and systemd scopes: PLANNED Linux enforcement mechanisms, not semantic contract. containerd/LXC: REFERENCE_ONLY pending concrete deployment need. No technology dependency is adopted by this Decision Recovery.

## Community Compute and Roadmap #23 boundaries

Community Compute remains ADOPTED_DEFERRED. No enrollment, trust taxonomy, discovery, reward, consensus, scheduling or marketplace is authorized. Its future workers may consume Phase 17 safety primitives only. Phase 17 exposes isolation capability/feasibility evidence only; it creates no global placement, optimizer, fleet scheduler or resource arbitration.

## Implementation proposal and evidence plan

I0 ledger/deletion preparation; I1 typed requirements/capabilities/failures; I2 process lifecycle; I3 filesystem/workspace/staging; I4 environment/secrets; I5 network; I6 resource/privilege/device hooks; I7 RuntimeAdapter integration; I8 Artifact/fencing proof; I9 one real local isolation adapter; I10 shadow deletion; I11 conformance/guards; I12 freeze/FCV.

Required future evidence: child containment/timeout/cancel/orphan; read-only inputs/traversal/workspace cleanup; deny/allow network; secret scope/redaction/no persistence; resource limit typed mapping; unprivileged/device grant behavior; exit-zero/stale/partial/cancel Artifact fencing; module boundaries and real isolation end-to-end path.

## Governance Correction 1

The independent review accepted the architecture semantics and identified three governance defects: the former summary declared ten rows while its visible table contained nine; the entries were aggregated and not mechanically traceable; and the two project-state next-gate surfaces disagreed. Correction 1 preserves all accepted semantics, replaces the summary with the 131-row exact-path TSV ledger, adds a fail-closed parser/validator with six required RED mutations, and normalizes both next-gate surfaces. The reviewed commit `63f0b49590a3b6cd6aa072d31d5483904d32e668` remains immutable in history.

## Escalation and final decision

Escalate if Artifact or completion authority must move into sandbox, sandbox must own Provider/Worker/Device identity or Reservation, #23 scheduling is required, existing externally published compatibility blocks clean-forward, Linux-only mechanics become canonical semantics, or Community Compute implementation is required.

ARCHITECTURE_ESCALATION=NONE
ARCHITECTURE_BLOCKERS=0
IMPLEMENTATION_BLOCKERS=0
GOVERNANCE_BLOCKERS=0
READY_FOR_ROADMAP_22_PHASE_17_SANDBOX_ISOLATION_IMPLEMENTATION=YES
NEXT_ACTION=CHATGPT_ROADMAP_22_PHASE_17_SANDBOX_ISOLATION_DECISION_RECOVERY_FINAL_REVIEW
