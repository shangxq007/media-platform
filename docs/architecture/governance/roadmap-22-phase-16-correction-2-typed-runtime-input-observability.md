# Roadmap #22 Phase 16 Correction 2 — Typed Runtime Input Binding and Materialization Observability

TASK_ID=ROADMAP_22_PHASE_16_CORRECTION_2_TYPED_RUNTIME_INPUT_BINDING_AND_MATERIALIZATION_OBSERVABILITY
MODE=BOUNDED_CORRECTION_IMPLEMENTATION
CLOSURE_CLAIM=NONE
PHASE_16_CLOSED=NO

## Governed Starting State and Branch Provenance

- Worktree: `/home/user/Documents/workspace/projects/.worktrees/roadmap22-phase16-correction2` only.
- Correction 2 local worktree branch: `agent/roadmap22-phase16-correction2-typed-input-observability`.
- Remote candidate branch tracked by that local branch: `origin/agent/roadmap22-executable-task-graph-worker-fabric-decision-recovery`. The differently named local branch is not represented as a separately published remote branch at this stage.
- Original and unchanged base HEAD: `8e4e589ce3c6b8a90f41852c6f6e31e8b1222f3e`.
- Base tree: `858206a9d23ab9d59fb4000f4bd4fbee377cb62a`.
- Base parent: `7ff41904e5ec01f17aac349fca43e97b308762bf`.
- Correction 1 executor-local branch was `agent/roadmap22-phase16-correction1-runtime-closed-loop`; it is recorded separately from the remote candidate branch `origin/agent/roadmap22-executable-task-graph-worker-fabric-decision-recovery`.
- Correction 1 remote CI run `32845247072` completed successfully for SHA `8e4e589ce3c6b8a90f41852c6f6e31e8b1222f3e`.
- Applicable instruction scope: repository-root `AGENTS.md`; no nested `AGENTS.md` was present and no instruction conflict was found.
- Existing `stash@{0}` was observed and not modified. Task-local executor prompt/log artifacts were removed before commit; no unrelated untracked files were included.
- The bounded executor did not commit, amend, rebase, reset, clean, cherry-pick, fetch, pull, push, merge, mutate canonical `main`, mutate a remote ref, or deploy.
- Correction 2 implementation SHA: `caaa03295af46570c7a08419fcc0a94fee66ddf7`.
- Correction 2 implementation tree: `335d9e9b761fe5a9fccf45e915e9c2fbe8da03a2`.
- Correction 2 implementation parent: `8e4e589ce3c6b8a90f41852c6f6e31e8b1222f3e`.

## Correction 2 Scope

This append-forward correction preserves the Correction 1 runtime closed loop, durable storage-to-Artifact binding, ownership fencing, dependency-preserving reuse pruning, and reuse-key derivation. It changes only typed runtime input materialization and bounded materialization outcome reporting.

### Typed logical runtime inputs

- The provider-neutral execution-plan projection `ExecutableSourceArtifactPin` now retains its canonical `ExecutionInputId` beside the immutable Artifact identity and digest.
- `ExecutableTaskDependency` retains the exact computed consumer `ExecutableInputProjection`; therefore the consumer `ExecutionInputId`, exact producer/consumer step relationship, and source-presence semantics remain present without exposing render implementation types.
- `MaterializedExecutionInput` is the worker-fabric runtime-local value: canonical `ExecutionInputId`, immutable `ArtifactPin`, and `MaterializedArtifact`. It contains no URI, provider, storage object/replica, credentials, generic map, or object bag.
- The existing `RuntimeAdapter` and `ProviderNativeRuntimeBinding` were migrated in place to `List<MaterializedExecutionInput>`. `ExecutionInputId` is exposed through the media-execution-plan execution-domain named interface; taskgraph projections remain the only other execution-plan input contract consumed by worker-fabric. No second SPI and no compatibility overload was added.
- `RuntimeClosedLoopOrchestrator` resolves every source input and computed external consumer input independently, sorts by typed identity only for determinism, and never deduplicates logical inputs by `ArtifactPin`. Two identities bound to the same pin therefore remain two runtime bindings while the worker-local byte cache may reuse one physical file.
- A reused predecessor supplies the exact consumer input identity and its validated reused output pin. Executed predecessors follow the same mapping.
- The orchestrator and exact runtime binding fail before adapter execution for duplicate, unknown, or absent required runtime identities. A missing predecessor output pin, pin/materialized-handle mismatch, or materialization failure also fails before provider execution.
- No runtime semantic role is inferred from list position, local path, Artifact identity, or digest.

No worker-fabric dependency on a render implementation, media-execution-plan dependency on worker-fabric, provider-owned storage/cache authority, reuse-key redesign, Artifact authority change, durable write-binding change, or `PlanLowerer` impurity was introduced.

### Real materialization dispositions

`WorkerLocalMaterializationCache` and `ArtifactMaterializerPort` now return `ArtifactMaterializationResult`, which carries the local handle and one successful bounded disposition:

- `LOCAL_CACHE_HIT`: an existing regular local file passed exact digest validation; the storage source was not opened.
- `STORAGE_MATERIALIZED`: no valid local file existed; storage bytes were acquired, validated by exact digest, and atomically published locally.
- `CORRUPTION_RECOVERED`: an existing regular local file failed digest validation, was removed, storage bytes were reacquired, the exact digest passed, and the replacement was atomically published.
- `FAILURE`: emitted by the production orchestrator only when materialization throws or otherwise fails before producing a successful result.

`DirectStorageArtifactMaterializer` still authorizes tenant-scoped Artifact existence, `AVAILABLE` state, and the exact pin before invoking the local cache. Storage replica access is now lazy, so a post-authorization local hit performs no physical storage read. `Phase16RuntimeMetrics` records only the bounded `outcome` tag and adds no tenant, Artifact, digest, task, input, attempt, generation, provider, path, or storage label.

## Production-Path Tests and Guards

`RuntimeClosedLoopConformanceTest` continues to fake only the external `RuntimeAdapter` executable. Graph projection, reuse resolution/pruning, Artifact authorization, direct storage materializer, worker-local cache, metrics, staging, durable publication, Artifact commit, completion fencing, and reuse publication use production code. Added scenarios prove:

1. distinct logical source inputs map exact `input-a -> source-x` and `input-b -> source-y` identities/pins;
2. foreground and mask identities bound to the same exact pin remain two runtime values and one local path, with one physical storage read;
3. two computed inputs retain `input-c-1` and `input-c-2` while two validated reused predecessors resolve to the same exact pin and one local handle;
4. a reused predecessor feeds the exact downstream consumer ID, reused pin, and regular local file;
5. empty cache, second authorized acquisition, manual corruption/reacquisition, and storage read failure emit exactly `STORAGE_MATERIALIZED`, `LOCAL_CACHE_HIT`, `CORRUPTION_RECOVERED`, and `FAILURE` respectively, without direct metric calls;
6. all previous closed-loop, durable publication, ownership fencing, cache loss, index loss, idempotence, and shared-dependency reuse cases remain in the same conformance suite.

`ProviderNativeLoweringRuntimeAdapterTest` additionally proves absent, unknown, and duplicate logical runtime IDs are rejected before the fake adapter executes.

`RuntimeClosedLoopArchitectureGuardTest` and `scripts/phase16-clean-forward-guards.py` preserve every prior guard and report zero for:

- old `RuntimeAdapter` `List<MaterializedArtifact>` signatures;
- compatibility overloads;
- untyped runtime input collections;
- input-role inference from list index;
- logical runtime input deduplication by `ArtifactPin`.

## Verification Actually Executed

- `./gradlew :media-execution-plan-module:test :artifact-module:test :storage-module:test :storage-provider-opendal:test :worker-fabric-module:test --no-daemon --console=plain`: **BUILD SUCCESSFUL**.
- `./gradlew :media-execution-plan-module:test :worker-fabric-module:test :platform-app:test --tests com.example.platform.ModularityTest.modularityViolationsWithinBudget --no-daemon --console=plain`: **BUILD SUCCESSFUL**.
- `./gradlew check pfirr1RemediationCheck :platform-app:bootJar --rerun-tasks --no-daemon --console=plain`: **BUILD SUCCESSFUL** in 22m 1s; 199 actionable tasks executed.
- `python3 scripts/phase16-clean-forward-guards.py`: PASS; every prior and new reported counter was zero.
- `scripts/check-architecture-drift.sh`: PASS; all architecture drift checks, including the embedded Phase 16 clean-forward guard, passed.

Machine-readable focused-test arithmetic is recorded in `roadmap-22-phase-16-correction-2-test-evidence.json`. The authoritative Gradle gates above supersede the executor’s earlier sandbox-only direct-launcher attempts.

## Remaining Gates and Publication Status

- Correction 2 remote CI is pending publication. Hermes must not claim remote CI until the final Correction 2 publication SHA is pushed and GitHub reports completed/success.
- This record does not claim that Correction 2 is published, integrated, released, deployed, or that Phase 16 is closed.
