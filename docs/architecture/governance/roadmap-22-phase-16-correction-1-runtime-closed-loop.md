# Roadmap #22 Phase 16 Correction 1 — Runtime Closed Loop, Durable Output, and Observability

TASK_ID=ROADMAP_22_PHASE_16_CORRECTION_1_RUNTIME_CLOSED_LOOP_DURABLE_OUTPUT_AND_OBSERVABILITY
MODE=BOUNDED_CORRECTION_IMPLEMENTATION
DISPOSITION=FAIL_CORRECTABLE
CLOSURE_CLAIM=NONE

## Governed Starting State

- Worktree: `roadmap22-phase16-correction1` linked worktree only.
- Branch: `agent/roadmap22-phase16-correction1-runtime-closed-loop`.
- Original and unchanged HEAD: `de82be9e5d35e30343c7139a8b3cb8218d46289f`.
- Original tree: `f5fc8df7b50c631946971ce552b96cf5d251e46c`.
- Original parent: `a174c1dfeb0e4d843e8abb5bdeb3eb2ec8d38b4b`.
- Correction implementation SHA: `b2825ca02b09d2a9a9606a6f18ed14688ce36934`.
- Correction implementation tree: `44227f03ce309a798e379cae05aa66d2953f70c0`.
- Correction implementation parent: `de82be9e5d35e30343c7139a8b3cb8218d46289f`.
- Correction publication/FCV SHA and tree are the later docs-only descendant and are intentionally recorded after that commit is created.
- Applicable instruction scope: repository-root `AGENTS.md`; no nested `AGENTS.md` and no instruction conflict were found.
- Task-local executor prompt/log artifacts were removed before the implementation commit; no unrelated untracked files were included.
- Existing `stash@{0}` was observed and not modified.
- No commit, amend, rebase, reset, clean, cherry-pick, fetch, pull, push, merge, remote-ref mutation, canonical-main mutation, or deployment was performed.

## FAIL_CORRECTABLE Reason

The accepted Phase 16 components existed at the original SHA but were isolated mechanics rather than a generic production runtime path. No production orchestrator derived graph reuse keys, resolved and validated tenant-scoped reuse, passed typed validated hits to dependency-preserving pruning, materialized miss inputs, invoked the accepted Phase 15 lowerer/runtime adapter, staged provider output, published it durably, committed Artifact authority, fenced completion, and activated reuse in one ordered flow.

Two concrete gaps prevented safe wiring:

1. `WriteSessionResult` returned only `StorageReplicaId`; it did not return the authoritative `StorageObjectId` required by `ArtifactCommitRequest`. A caller therefore could only invent or separately supply an object identity.
2. The accepted `RuntimeAdapter` translated a native plan to commands but had no existing method for receiving runtime-local materialized inputs and returning provider output. Adding a separate execution SPI would have duplicated the frozen Phase 15 boundary.

Metrics had also been intentionally deferred because there were no real orchestration call sites.

## Bounded Implementation Scope

### Existing runtime chain

`RuntimeClosedLoopOrchestrator` now performs this ordered path:

`ProviderBoundExecutableTaskGraph -> ExecutionReuseKeyDeriver -> ArtifactReuseResolver -> typed VALIDATED_HIT IDs -> DependencyPreservingReusePruner -> ArtifactMaterializerPort -> ProviderNativeRuntimeBinding(PlanLowerer + RuntimeAdapter) -> ProviderExecutionOutput -> OutputStagingArea -> ArtifactOutputCommitOrchestrator -> FencedReuseCompletionOrchestrator`.

- Reuse decisions are produced internally for the tenant and derived key. Only `ValidatedReuseDecision.Outcome.VALIDATED_HIT` populates the set passed to the existing pruner.
- Index exceptions become safe misses. Artifact authority validation remains tenant-scoped, `AVAILABLE`-state and digest exact. Stale/corrupt metadata is evicted from the index only; no Artifact or storage bytes are deleted.
- Root source pins plus reused/executed predecessor output pins are materialized through the accepted `ArtifactMaterializerPort`. The exact provider binding receives only `MaterializedArtifact` local paths/pins/lengths.
- `ProviderNativeRuntimeBinding` pairs the existing `PlanLowerer` and `RuntimeAdapter` for the same native plan type and exact `ProviderBindingPin`; it performs no provider selection, fallback, or rebinding.
- `RuntimeAdapter` was minimally completed in place with execution of the already-adapted `RuntimeExecutionBundle`, accepting only runtime-local materialized handles and returning `ProviderExecutionOutput`. No parallel runtime SPI was introduced.

### Durable publication and Artifact binding

`WriteSessionResult` now contains typed `StorageObjectId` and `StorageReplicaId`. Both concrete providers were migrated together:

- `InMemoryStorageProvider` returns deterministic completed identities and retains completed-session idempotence.
- `AbstractOpenDalProvider` returns the deterministic generated object identity on first and repeated completion and does not reopen or undo an already completed session.

`ArtifactOutputCommitOrchestrator` no longer accepts a caller-created `ArtifactCommitRequest` beside staged bytes. It accepts storage-free `ArtifactCommitMetadata` plus `DurableOutputTarget`, then:

1. calls `StorageProvider.beginWrite` with the staged digest and byte length;
2. streams the staged file through `StorageProvider.write`;
3. calls `StorageProvider.completeWrite` and captures authoritative typed object/replica identity;
4. constructs `ArtifactCommitRequest` internally from that completed identity and measured staged digest/length;
5. calls `ArtifactCommitService` and validates the returned Artifact and replica binding against durable publication.

Failure ordering is explicit:

- Before successful `completeWrite`, a storage failure calls `abortWrite`; Artifact commit, completion, and reuse publication are not called.
- After successful durable publication, Artifact failure throws `ArtifactCommitAfterDurablePublicationException` carrying `DurableStoragePublication` reconciliation evidence; completion and reuse are not called.
- After durable Artifact commit, stale ownership/generation throws `NonAuthoritativeRuntimeCompletionException` carrying durable commit evidence; no completion or winning reuse entry is created.
- `FencedReuseCompletionOrchestrator` always rechecks canonical completion before activation, including idempotent winning retries, then calls activation idempotently.
- Local staged-file cleanup is best effort and cannot mask durable orphan or fencing evidence.

## Observability

`Phase16RuntimeMetrics` is called at the real production orchestration boundaries for:

- `media.worker_fabric.phase16.reuse.lookup`;
- `media.worker_fabric.phase16.materialization`;
- `media.worker_fabric.phase16.staging`;
- `media.worker_fabric.phase16.durable.publish`;
- `media.worker_fabric.phase16.artifact.commit`;
- `media.worker_fabric.phase16.reuse.publication`.

The only metric label is a bounded `outcome` enum. Tenant, Artifact, execution key, task, attempt, worker, local path, storage object, storage replica, and provider-specific location are not labels.

## Tests and Guards Added

`RuntimeClosedLoopConformanceTest` uses a bounded recording `RuntimeAdapter` only for the external executable. All orchestration, graph/key derivation, resolver, pruning, direct storage materializer/cache, staging, StorageProvider contract, in-memory Artifact commit semantics, completion fencing, and reuse index state transitions execute as code. It covers:

- first miss and durable publication;
- equivalent hit skipping runtime;
- source and provider semantic changes causing misses;
- worker-local cache loss and index loss/failure causing safe rematerialized execution;
- stale and corrupt Artifact authority state failing closed;
- storage failure before Artifact/completion/reuse;
- Artifact failure after durable storage with orphan evidence;
- stale generation after publication;
- completion followed by idempotent reuse activation;
- shared-dependency DAG pruning with one validated shared producer feeding two consumers.

`RuntimeClosedLoopArchitectureGuardTest` and the extended `phase16-clean-forward-guards.py` add bounded checks for:

- staged direct Artifact commit without completed durable binding;
- provider-native direct storage or Artifact authority;
- arbitrary/unvalidated reuse pruning inputs;
- bypass of durable commit and fenced completion/reuse orchestration;
- high-cardinality Phase 16 metric labels.

Existing Phase 16 clean-forward counters and Phase 15 provider-native guards were retained.

## Executed Verification Evidence

The following commands were actually executed in this worktree:

- Direct JDK 25 production compilation of changed storage and worker-fabric sources, including the closed-loop orchestrator: PASS.
- Direct JDK 25 production compilation of `AbstractOpenDalProvider`: PASS.
- Direct JDK 25 focused test compilation: PASS.
- Manual JUnit Platform execution with the resolved Byte Buddy agent of closed-loop conformance, closed-loop architecture guards, existing Artifact reuse/materialization tests, and existing Phase 15 lowering/runtime and architecture guards: **49 found, 49 started, 49 successful, 0 failed, 0 skipped**.
- Manual JUnit Platform execution of `InMemoryProviderTest` and `OpenDalConformanceTest`: **24 found, 24 started, 24 successful, 0 failed, 0 skipped**.
- `python3 scripts/phase16-clean-forward-guards.py`: PASS, including all existing counters and four new runtime-loop counters at zero.
- `scripts/check-architecture-drift.sh`: PASS.
- Final affected-module Gradle suite `:media-execution-plan-module:test :artifact-module:test :storage-module:test :storage-provider-opendal:test :worker-fabric-module:test --no-daemon --console=plain`: **BUILD SUCCESSFUL**, 63 actionable tasks, 0 failures.
- Exact `:platform-app:test --tests com.example.platform.ModularityTest.modularityViolationsWithinBudget`: **BUILD SUCCESSFUL**.

Machine-readable count arithmetic is recorded in `docs/architecture/governance/roadmap-22-phase-16-correction-1-test-evidence.json`: 49 + 24 = **73 total, 73 passed, 0 failures, 0 errors, 0 skipped, 0 aborted**.

An authoritative Gradle attempt was also made with a writable copied Gradle distribution. It was environment-blocked before task execution because Gradle could not create its file-lock contention service: `Could not determine a usable wildcard IP for this machine`. No Gradle test success is claimed.

## Explicit Non-Scope and Remaining Gates

- No FFmpeg provider, OpenCue execution, remote provider, Phase 17–19 implementation, Roadmap #23, Redis, cloud/serverless runtime, or unrelated roadmap amendment was added.
- `LEGACY_RENDER_INFRASTRUCTURE_FFMPEG=TRANSITIONAL_LEGACY` remains unchanged, and the Phase 19 obligation is not altered.
- PostgreSQL `JooqArtifactReuseIndex` integration execution was not rerun because this environment has no authorized/available Docker/Testcontainers path; its accepted contract was not redesigned.
- Full authoritative Gradle module suites and PostgreSQL integration remain external verification gates.
- This correction record reports bounded implementation and evidence only. It makes no Phase 16 closure, release, integration, or deployment claim.
