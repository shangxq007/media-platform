# Roadmap 22 Phase 16 Correction 3 — Concurrency and Output Guards

## Status and authority

- Task: `ROADMAP_22_PHASE_16_CORRECTION_3_CONCURRENT_REUSE_PUBLICATION_AND_OUTPUT_CARDINALITY_GUARDS`.
- Local executor branch: `agent/roadmap22-phase16-correction3-concurrency-output-guard`.
- Remote tracking/candidate branch remains distinct: `agent/roadmap22-executable-task-graph-worker-fabric-decision-recovery`.
- Frozen base HEAD: `0331a90bb1f94c86f2b0d38765135b67d1c61c6a`; tree `325123ca9bd4cf14d13f2493e60a2d857f2776f3`; parent `caaa03295af46570c7a08419fcc0a94fee66ddf7`.
- Correction 2 CI run `32858857398` was `completed/success` for `0331a90b`.
- This record makes no Phase 16 closure, Correction 3 remote CI, integration, release, or deployment claim.

The root `AGENTS.md` governs the whole worktree. No nested instruction file applies and no instruction conflict was found. Task-local executor prompt/log artifacts were removed before commit; the existing stash was not modified.

## A. PostgreSQL first-publication contention

`JooqArtifactReuseIndex.stageWinningPublication` retains the existing owner and committed-Artifact validation, then performs one PostgreSQL insert with:

`ON CONFLICT (tenant_id, reuse_key_version, reuse_key_digest) DO NOTHING`.

An inserted row returns `STAGED_PENDING`. A constraint conflict waits through PostgreSQL transaction/unique-index semantics, then re-reads the key with `FOR UPDATE` and classifies the durable row:

- same pending publication: `PENDING_IDEMPOTENT`;
- same winning publication: `WINNER_IDEMPOTENT`;
- differing publication: `CONFLICT_REJECTED`.

No generic exception catch, version field, generic compare-and-set framework, JVM lock, Redis, distributed lock, or advisory lock was added. Genuine database failures continue to propagate. `currentOwner`, `committedArtifactMatches`, `samePublication`, and completion/activation fencing remain in place.

`ArtifactReuseIndexPostgresTest` now uses two real `JooqArtifactReuseIndex` instances over the Testcontainers PostgreSQL datasource. Two executor threads synchronize on a `CyclicBarrier`, stage the exact same publication, and assert zero exceptions, the exact typed result pair `STAGED_PENDING` plus `PENDING_IDEMPOTENT`, and one matching database row. Existing pending/winning idempotence now also checks that a different valid publication remains `CONFLICT_REJECTED` in both states.

## B. Exactly one platform-authoritative output in V1

The neutral `ExecutableTask.authoritativeOutputIds()` projection derives distinct output identities only from task-owned `POST_EXECUTION` `BoundaryAction` targets. It does not count task memberships or all raw `OutputDeclaration` values. Several materialization actions for the same producer output therefore remain one authoritative output, including a shared producer feeding several consumers.

`RuntimeClosedLoopOrchestrator` validates every task has cardinality exactly one before reuse-key derivation, reuse resolution, input resolution/materialization, provider-native lowering/adaptation/execution, staging, durable storage, Artifact commit, completion, or reuse publication. Zero or multiple outputs fail with `ProviderNativeExecutionFailure` code `UNSUPPORTED_AUTHORITATIVE_OUTPUT_CARDINALITY` and bounded diagnostics.

Production closed-loop tests use concrete task/output/boundary semantics, the production orchestrator and `ProviderNativeRuntimeBinding`, and a recording `RuntimeAdapter` only at the external executable boundary. Zero-output and multi-output cases assert:

- provider executions: 0;
- materializations and staging: 0;
- storage writes and durable publications: 0;
- Artifact commits: 0;
- completions: 0;
- reuse winners/publications: 0.

The one-output miss/hit, typed runtime inputs, metrics, durable write/Artifact commit, fencing, and reuse behavior remain covered. Multiple runtime output targets, `ProviderExecutionOutputs`, first-output selection, synthesized artifacts, and singular-metadata loops remain deferred.

## Guard coverage

`RuntimeClosedLoopArchitectureGuardTest` checks the validation order, typed failure, neutral projection, bounded PostgreSQL conflict statement, and absence of exception-catching/CAS or multi-output runtime surfaces. `phase16-clean-forward-guards.py` strips Java comments before reporting these additional counters:

- `UNSUPPORTED_MULTI_OUTPUT_SILENT_FIRST_OUTPUT_COUNT=0`;
- `GENERIC_REUSE_INDEX_CAS_FRAMEWORK_COUNT=0`;
- `MULTI_OUTPUT_RUNTIME_IMPLEMENTATION_COUNT=0`.

All previously accepted Phase 16 counters remain zero.

## Changed-path scope

- Production execution-plan semantics: `ExecutableTask.java`.
- Production worker runtime/failure/reuse infrastructure: `ProviderNativeFailureCode.java`, `RuntimeClosedLoopOrchestrator.java`, `JooqArtifactReuseIndex.java`.
- Bounded tests/fixtures: `ProviderBoundExecutableTaskGraphTest.java`, `ArtifactReuseIndexPostgresTest.java`, `RuntimeClosedLoopConformanceTest.java`, `RuntimeClosedLoopArchitectureGuardTest.java`, `RuntimeClosedLoopGraphFixture.java`.
- Guard/evidence documentation: `scripts/phase16-clean-forward-guards.py`, this record, and `roadmap-22-phase-16-correction-3-test-evidence.json`.

No module, migration, build file, application configuration, provider implementation, remote integration, or canonical-main state was changed.

## Local verification

The machine-readable summary is in `roadmap-22-phase-16-correction-3-test-evidence.json`.

- `git diff --check`: PASS.
- `python3 scripts/phase16-clean-forward-guards.py`: PASS; all existing and three new counters are zero.
- `scripts/check-architecture-drift.sh`: PASS; 42 top-level checks, 0 failed, including the Phase 16 clean-forward gate.
- `./gradlew :worker-fabric-module:test --tests com.example.platform.workerfabric.domain.ArtifactReuseIndexPostgresTest :worker-fabric-module:test --tests com.example.platform.workerfabric.reuse.RuntimeClosedLoopConformanceTest --no-daemon --console=plain`: **BUILD SUCCESSFUL**. This ran the real PostgreSQL/Testcontainers index test and runtime closed-loop conformance.
- `./gradlew :media-execution-plan-module:test :artifact-module:test :storage-module:test :storage-provider-opendal:test :worker-fabric-module:test :platform-app:test --tests com.example.platform.ModularityTest.modularityViolationsWithinBudget --no-daemon --console=plain`: **BUILD SUCCESSFUL**.
- `./gradlew check pfirr1RemediationCheck :platform-app:bootJar --rerun-tasks --no-daemon --console=plain`: **BUILD SUCCESSFUL** in 21m 13s; 199 actionable tasks executed.

Correction 3 remote CI remains pending until the final docs-publication SHA is pushed.
