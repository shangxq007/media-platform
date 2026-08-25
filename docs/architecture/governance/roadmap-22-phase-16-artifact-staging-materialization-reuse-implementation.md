# Roadmap #22 Phase 16 — Artifact Staging, Materialization, and Reuse Implementation

TASK_ID=ROADMAP_22_PHASE_16_ARTIFACT_STAGING_MATERIALIZATION_AND_REUSE_BOUNDED_IMPLEMENTATION
MODE=BOUNDED_IMPLEMENTATION
CLEAN_FORWARD=AUTHORITATIVE
ARCHITECTURE_AUTHORITY=CHATGPT
ENGINEERING_CONTROL_PLANE=HERMES

## Governed Topology

- Worktree: `/home/user/Documents/workspace/projects/media-platform/.worktrees/roadmap22-decision-recovery`
- Branch: `agent/roadmap22-executable-task-graph-worker-fabric-decision-recovery`
- Executor base/HEAD: `0fd00e8557471e112ce6796f6c85ff13e6a2d979`
- Base tree: `965c577c88d35f93f5af59d2fd23e2d7ef2b35c5`
- Expected parent: `14ca7c550a844bb7912e33b2e2d47bbce0065870`
- Stash observed before work: `stash@{0}: phase15-pre-cip2-drift-gate-repair-safety` (untouched)
- Applicable instruction scope: repository-root `AGENTS.md`; no nested instruction file or conflict was found.
- Pre-existing untracked task inputs preserved: `.hermes-phase16-implementation-brief.md`, the P16-0 legacy ledger, and the Phase 16 guard script.

The bounded executor did not commit, push, amend, rebase, merge, or otherwise mutate Git history. Hermes owns candidate freezing and final verification/commit.

## Implemented Production Slices

### Pure execution identity and pruning (`media-execution-plan-module`)

- `ExecutionReuseKey` is an immutable V1 value containing an explicit version, canonical serialization, and matching SHA-256 stable digest.
- `ExecutionReuseKeyDeriver` derives keys in deterministic topological order over the accepted provider-bound executable task graph.
- Local task participation reuses the existing injective canonical codec, covering executable task identity, provider binding pins, memberships, operations, temporal/sample semantics, outputs, materialization requirements, and exact root/source `ArtifactId + ContentDigest` pins already present in the model.
- Computed predecessor participation is Merkle-derived from predecessor `ExecutionReuseKey` version/digest plus the exact producer output declaration, dependency semantics, and optional execution Artifact boundary. It never requires a future output Artifact pin.
- `DependencyPreservingReusePruner` performs a pure backward dependency closure from requested tasks and stops traversal only at caller-supplied validated reuse tasks. It has no cache, database, filesystem, scheduler, worker, or clock input.

### Runtime reuse, materialization, staging, and fencing (`worker-fabric-module`)

- `ArtifactReuseIndexPort` maps a tenant-scoped `ExecutionReuseKey` to `ReusableArtifactRecord` containing an Artifact pin, never a URI.
- `JooqArtifactReuseIndex` is the production PostgreSQL adapter. It validates the current task/attempt/generation before staging, validates that the Artifact row is tenant-matched, `AVAILABLE`, and digest-exact, and stores the entry as invisible `PENDING` metadata.
- A reuse entry becomes visible as `WINNING` only after a matching canonical completion event and `SUCCEEDED` attempt exist for the same task/attempt/generation. Crash-abandoned pending rows remain safe misses and can be purged by age.
- Index lookup is tenant-scoped and returns only `WINNING` rows. Index eviction/pending cleanup deletes index metadata only.
- `ArtifactReuseResolver` always follows index lookup with tenant-scoped `ArtifactQueryService` authority validation. Missing/deleting entries are stale, digest mismatch/quarantine/failure is corrupt, cross-tenant records fail unauthorized, and only exact `AVAILABLE` Artifacts become `VALIDATED_HIT`.
- `ArtifactMaterializerPort` and `MaterializedArtifact` expose only an Artifact pin, normalized local file, and byte length.
- `DirectStorageArtifactMaterializer` queries Artifact-owned replica bindings and reads through the backend-neutral `StorageProvider` SPI. It imports no OpenDAL, S3, RustFS, or R2 type.
- `WorkerLocalMaterializationCache` uses SHA-256 digest-safe paths below a bounded root, verifies every hit/download, treats corrupt local content as a miss, uses same-directory temp files plus atomic replace, serializes same-digest concurrent materialization, and evicts local files only.
- `OutputStagingArea` writes provider output into a bounded staging root while calculating exact length and SHA-256 digest.
- `ArtifactOutputCommitOrchestrator` rejects commit requests/results that do not match staged length/digest and delegates the authoritative commit to `ArtifactCommitService`.
- `FencedReuseCompletionOrchestrator` stages pending reuse metadata, delegates canonical task completion fencing, and activates reuse only after authoritative completion.

### PostgreSQL schema

`wf_artifact_reuse_index` was added to the canonical V1 schema with:

- tenant + key version + key digest primary identity;
- canonical key serialization for reload verification;
- ArtifactId + ContentDigest pin metadata;
- exact task/attempt/generation publication provenance;
- `PENDING`/`WINNING` visibility state;
- Artifact, execution-attempt, and completion-event foreign keys/checks;
- indexes for Artifact association and pending-row cleanup.

### Render clean-forward

- Deleted the process-local `ArtifactCache` hash-to-URI authority.
- Removed cache lookup/publication and direct cache-hit skipping from `DagExecutionEngine`.
- Removed `skipExecution`, `reuseArtifactUri`, synthetic `reuse://` output, and direct incremental skip behavior from the multi-provider and pipeline DAG execution paths.
- `ReusableArtifact` is now explicitly advisory candidate metadata and no longer contains a URI.
- Incremental and segment planning mark `incrementalMode=reuse-candidate`; every task remains in `executeTaskIds` until worker-fabric validates reuse and performs dependency-preserving pruning.
- Prior execution state contributes task/cache-key candidate metadata only. Legacy hash/URI filtering no longer decides execution.

## Legacy Ledger Disposition

| Legacy surface | Final bounded disposition |
| --- | --- |
| `ArtifactCache` `ConcurrentHashMap<inputHash, outputUri>` | Deleted |
| `DagExecutionEngine` direct cache-hit skip | Deleted |
| `IncrementalRenderPlan` URI-bearing reuse truth | Recast to URI-free advisory candidates |
| `SegmentPlanFilter` forced reuse/skip | Recast to candidate marking only |
| Multi-provider/pipeline direct incremental skip | Deleted |
| `RenderCacheUriResolver` upload/cache-location mechanics | Retained only for existing data-plane publisher/upload callers; removed from reuse planning |
| `RenderCacheReuseValidator` local hash mechanics | Retained as standalone mechanics, removed from reuse authority/planning |

## Verification Evidence

### TDD / focused execution

The initial Gradle red run was attempted before production implementation:

`./gradlew :media-execution-plan-module:test --tests com.example.platform.execution.taskgraph.ProviderBoundExecutableTaskGraphTest --rerun-tasks`

Result: environment-blocked before compilation because the managed sandbox made the default Gradle cache read-only. Retrying with `GRADLE_USER_HOME=/tmp/roadmap22-gradle-home --no-daemon` was also blocked before compilation because Gradle could not create its file-lock contention socket (`Could not determine a usable wildcard IP for this machine`).

As a bounded substitute, changed production sources and tests were compiled directly with JDK 25 against the repository's existing Gradle-resolved classpath. Results:

- Changed `media-execution-plan-module` production sources: PASS.
- Changed `worker-fabric-module` production sources including the jOOQ adapter: PASS.
- Changed focused media/worker tests: PASS compilation.
- Changed `render-module` production sources: PASS.
- Entire `render-module/src/test/java` source set: PASS compilation.

The compiled tests were then executed through JUnit Platform Launcher with the repository-resolved Byte Buddy agent:

`ManualJunitRunner ProviderBoundExecutableTaskGraphTest ArtifactReuseMaterializationTest MultiProviderPipelineIncrementalTest SegmentPlanFilterTest RenderArtifactRegistryPipelineStagesTest`

Result after fixes: 31 tests found, 31 started, 31 successful, 0 failed, 0 skipped.

After adding staged-output Artifact commit gating:

`ManualJunitRunner ArtifactReuseMaterializationTest`

Final focused rerun result: 41 tests found, 41 started, 41 successful, 0 failed, 0 skipped.

Relevant Artifact authority and StorageProvider tests were also executed through the same runner: 24 tests found, 24 successful, 0 failed, 0 skipped. `OpenDalConformanceTest` executed separately with native access enabled: 17 tests found, 17 successful, 0 failed, 0 skipped.

Machine-readable arithmetic/evidence: `docs/architecture/governance/roadmap-22-phase-16-focused-test-evidence.json` (82 total, 82 passed, 0 failures, 0 errors, 0 skipped).

### PostgreSQL integration

`ArtifactReuseIndexPostgresTest` was added and compiles. It covers pending invisibility, activation, tenant isolation, idempotence, stale/wrong generation, pending cleanup, index eviction, and preservation of the Artifact row.

Direct execution attempt result: environment-blocked before test discovery because Testcontainers could not find an accessible Docker environment. No H2 substitution was used.

### Architecture gates

`python3 scripts/phase16-clean-forward-guards.py`

Result: PASS; all 16 reported Phase 16 counters were zero.

`scripts/check-architecture-drift.sh`

Result: PASS, including the integrated Phase 16 guard invocation.

## Deferred / Remaining Integration

- The current bounded slice provides production ports/orchestrators and persistence, but the accepted provider-native runtime has not yet been wired end-to-end to construct materializers, invoke provider execution, and feed staged outputs. That integration remains within Phase 16 and must preserve these ports; it is not a new provider implementation.
- PostgreSQL integration execution and authoritative Gradle module gates remain for Hermes in an environment with Gradle lock sockets and Docker/Testcontainers access.
- Cache/materialization metrics vocabulary is frozen but runtime metric emission is deferred until the provider-native orchestration wiring supplies real call sites. No distributed-cache optimization was introduced.
- Caffeine, Redis, Alluxio, JuiceFS, Dragonfly, FFmpeg modernization/Phase 19, OpenCue, Remote Provider, FAOF-2, Phase 17, Roadmap 23, and new modules remain unimplemented/deferred.

## Handoff Status

ARCHITECTURE_GUARDS=PASS
DIRECT_JAVA_COMPILATION=PASS
FOCUSED_JUNIT=PASS_82_OF_82
POSTGRESQL_TEST_EXECUTION=ENVIRONMENT_BLOCKED
GRADLE_MODULE_GATES=ENVIRONMENT_BLOCKED
READY_FOR_HERMES_FCV=CONDITIONAL_ON_FINAL_RERUN_AND_AUTHORITATIVE_GRADLE_POSTGRES_GATES
