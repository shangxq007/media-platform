# Roadmap #22 Phase 17 — Sandbox / Isolation Bounded Implementation

TASK_ID=ROADMAP_22_PHASE_17_SANDBOX_ISOLATION_BOUNDED_IMPLEMENTATION
STATUS=FROZEN_CANDIDATE_PENDING_FCV
DECISION_RECOVERY_SHA=b81d1227087d3dd4316948b0b05a7e1ea28515e1
DECISION_RECOVERY_TREE=0123f41e58374dd912c693513510306bb93f6852
PHASE_17_IMPLEMENTATION_STARTED=YES
PHASE_17_CLOSED=NO

## Canonical implementation

Phase 17 introduces the narrow, technology-neutral `:sandbox-isolation-module` and `com.example.platform.sandbox` API. It owns execution-safety contracts and mechanism adapters only. Worker Fabric retains WorkerRuntime eligibility, assignment/attempt/ownership generation, completion fencing, Artifact staging/commit orchestration, and runtime completion authority.

Implemented bounded contracts cover process execution, explicit filesystem exposure, default-denied network policy, exact environment construction, opaque/scoped secrets, privilege/device denial, granular resource enforcement evidence, typed failures, bounded capture, cancellation, process-tree/container cleanup, and execution observations.

Real enforcement adapters:

- rootless Podman/Docker-compatible container isolation for hardened container workloads;
- Bubblewrap host-binary isolation for existing trusted platform tooling, using private user/mount/PID/network/device namespaces, read-only runtime/workspace exposure, and explicit writable temp/output overlays;
- best-effort local process mechanics remain non-authoritative and cannot satisfy mandatory hard-containment requirements.

Technology implements the contract and does not define semantic authority.

## Authority preservation

PROCESS_EXIT_SUCCESS != OUTPUT_BYTES_EXIST

OUTPUT_BYTES_EXIST != ARTIFACT_COMMITTED

ARTIFACT_COMMITTED != EXECUTABLE_TASK_COMPLETED

ArtifactMaterializer/Artifact domain remains Artifact authority. Existing execution ownership generation and CompletionFence remain completion authority. Capacity, Reservation, ObservedUsage and sandbox enforcement limits remain distinct. Provider compatibility remains outside sandbox authority. `media-execution-plan-module` does not depend on Worker Fabric.

## Clean Forward

Historical extension sandbox and Commons Exec shadow authorities were migrated or deleted without compatibility wrappers, aliases, dual writes, legacy fallback, or parallel V1/V2 authority. The accepted 131-row ledger is closed at:

- REUSE_AS_CANONICAL=8
- REUSE_MECHANICS_ONLY=45
- MIGRATE_REDESIGN=0
- DELETE_SHADOW=16
- DEFER=62
- UNCLASSIFIED=0
- DUPLICATE=0

The ledger guard and its sixteen RED mutations execute through `scripts/check-architecture-drift.sh`.

## Authoritative local evidence

- Real rootless Podman and Bubblewrap conformance: PASS, installed engines exercised, no skipped real-host isolation tests.
- Affected modules: PASS.
- Exact Modulith gate: PASS.
- Media-execution-plan tests: PASS.
- Architecture drift, Phase 16 Clean Forward and Phase 17 guards: PASS.
- Full serial repository suite: `./gradlew test --rerun-tasks --max-workers=1 --no-daemon --console=plain`; 8,092 tests, 0 failures, 0 errors, 43 skipped; 185 tasks executed; PASS.
- PFIRR1 remediation: PASS.
- bootJar: PASS.
- root Docker image smoke: PASS.
- frontend lint/test/build: PASS (warnings only; zero lint errors, 3/3 tests).

## Deferred boundaries

Phase 18 and Phase 19 have not started. Roadmap #23 has not started. Community Compute remains ADOPTED_DEFERRED. No FFmpeg provider, accelerator provider, OpenCue POC, RemoteProvider, global scheduler/optimizer, volunteer worker, reward system, or community execution was implemented.

ARCHITECTURE_BLOCKERS=0
IMPLEMENTATION_BLOCKERS=0
GOVERNANCE_BLOCKERS=0
ARCHITECTURE_ESCALATION=NONE
READY_FOR_PHASE_17_FCV=YES
NEXT_ACTION=CHATGPT_ROADMAP_22_PHASE_17_SANDBOX_ISOLATION_BOUNDED_IMPLEMENTATION_FCV_REVIEW
