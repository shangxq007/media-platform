# Roadmap #22 Phase 16 — Artifact Staging, Materialization, and Reuse Closure

TASK_ID=ROADMAP_22_PHASE_16_CLOSURE_AND_CROSS_CUTTING_GOVERNANCE_PERSISTENCE
MODE=APPEND_FORWARD_DOCS_ONLY_GOVERNANCE
RECORD_KIND=IMMUTABLE_APPEND_FORWARD_CLOSURE
ROADMAP_22_PHASE_16=CLOSED
PHASE_16_ARTIFACT_STAGING_MATERIALIZATION_REUSE=CLOSED
CHATGPT_ROADMAP_22_PHASE_16_CORRECTION_3_FINAL_REVIEW=PASS

## 1. Closure verdict

Phase 16 is closed on the accepted Correction 3 publication candidate. The
authoritative final review found zero architecture, implementation, or
governance blockers and required no escalation.

- `ARCHITECTURE_BLOCKERS=0`
- `IMPLEMENTATION_BLOCKERS=0`
- `GOVERNANCE_BLOCKERS=0`
- `ARCHITECTURE_ESCALATION=NONE`
- `ROADMAP_22=IN_PROGRESS`
- `ROADMAP_23=NOT_STARTED`
- `CANONICAL_MAIN_MERGE=NO`
- `PHASE_17_STARTED=NO`

This record closes Phase 16 only. It does not start Phase 17, Phase 18, Phase
19, Roadmap #23, a new numbered milestone, integration to canonical `main`,
release, or deployment.

## 2. Governed repository state

- Accepted Phase 16 final candidate:
  `aa95b5d81e8df11ae03854b874f778f3cd4760c1`.
- Accepted candidate tree:
  `9ac5e2e9812c766c85f19e845bea39cec77f3aca`.
- Accepted candidate parent / Correction 3 implementation:
  `e526776170e140f19927d7e4ce838fd1fcc7b775`.
- Remote candidate branch:
  `agent/roadmap22-executable-task-graph-worker-fabric-decision-recovery`.
- Canonical `main` remains unchanged at
  `036f21f7f94f61da92faa2e91934675d024d99e8`, tree
  `7a61effeb2840c428cab2705a9f529159fc4e345`.
- Canonical-main integration has not been performed.

## 3. Exact append-forward evidence chain

The full accepted Phase 16 chain is preserved exactly:

1. Frozen decision-recovery base:
   `0fd00e8557471e112ce6796f6c85ff13e6a2d979`.
2. Initial FCV descendant:
   `de82be9e5d35e30343c7139a8b3cb8218d46289f`.
3. Correction 1 implementation:
   `b2825ca02b09d2a9a9606a6f18ed14688ce36934`;
   publication: `8e4e589ce3c6b8a90f41852c6f6e31e8b1222f3e`.
4. Correction 2 implementation:
   `caaa03295af46570c7a08419fcc0a94fee66ddf7`;
   publication: `0331a90bb1f94c86f2b0d38765135b67d1c61c6a`.
5. Correction 3 implementation:
   `e526776170e140f19927d7e4ce838fd1fcc7b775`;
   publication: `aa95b5d81e8df11ae03854b874f778f3cd4760c1`.
6. Final accepted CI run: `32868936892`, `completed/success` for the final
   publication candidate.

This closure task creates no new CI claim and makes no claim that its own
cross-cutting governance final review has already occurred. Its next gate is
`CHATGPT_ROADMAP_22_PHASE_16_CLOSURE_AND_CROSS_CUTTING_GOVERNANCE_FINAL_REVIEW`.

## 4. Frozen accepted Phase 16 contract

### Reuse identity, validation, and pruning

- `ExecutionReuseKey` V1 is deterministic, versioned, and derived over the
  accepted provider-bound executable task graph.
- Root/source inputs contribute exact `ArtifactId + ContentDigest` pins.
- Computed inter-task inputs propagate predecessor reuse identity through the
  frozen Merkle contract: predecessor reuse key, exact output identity, and
  exact dependency/boundary semantics. A future output Artifact pin is not
  invented to derive a pre-execution key.
- Reuse-index lookup is candidate metadata only. Authoritative reuse requires
  an exact tenant-scoped, `AVAILABLE`, digest-valid Artifact decision.
- Only a `ValidatedReuseDecision` may stop dependency traversal. Static
  pruning precedes runtime reuse resolution, and dependency-preserving
  pruning retains every producer still required by a non-reused consumer.
- Reuse-index loss, pending-row cleanup, or cache loss can reduce performance
  but cannot alter correctness or delete Artifact authority.

### Typed inputs and byte materialization

- Typed logical inputs survive plan projection, dependency mapping,
  materialization, provider-native binding, and execution.
- Logical input identity is never inferred from list position, local path,
  Artifact identity, or digest.
- Two logical inputs bound to the same Artifact pin remain two semantic input
  bindings. Worker-local byte deduplication may reuse one verified local file,
  but byte deduplication is not semantic input deduplication.
- Materialization is behind `ArtifactMaterializerPort`; the local cache is a
  digest-validating, bounded worker-local optimization. Providers consume
  typed materialized inputs and do not own storage, cache, URI, or Artifact
  authority.

### Durable output publication and completion

- Provider output first enters a bounded staging area and is verified by
  exact length and digest.
- Durable storage publication precedes authoritative Artifact commit.
- An `AVAILABLE` Artifact with the exact durable binding exists before task
  completion can become authoritative.
- Stale attempt/ownership generation cannot complete the task, publish the
  authoritative output association, or win reuse publication. Bytes already
  durably written may remain orphan cleanup evidence only.
- Reuse publication is staged as non-visible pending metadata. A winner exists
  only after authoritative fenced completion, and activation is idempotent.
- PostgreSQL first-publication contention has typed outcomes; it uses the
  unique-key transaction boundary and does not introduce JVM, Redis,
  advisory, generic distributed-lock, or generic compare-and-set authority.

### Output cardinality

- Phase 16 V1 supports exactly one platform-authoritative output per
  `ExecutableTask`.
- Zero or multiple authoritative outputs fail closed before reuse-key
  derivation, materialization, provider invocation, staging, storage
  publication, Artifact commit, completion, or reuse publication.
- Output cardinality is derived from task-owned post-execution boundary
  targets, not memberships or raw materialization actions.
- Multi-output execution, Artifact groups, and first-output selection are not
  silently implemented.
- The future commit law is: all required authoritative outputs must be
  durably published and committed as Artifacts before authoritative task
  completion and reuse publication; partial output sets never win.

## 5. Explicit deferred scope retained

Phase 16 closure does not adopt or implement:

- Redis reuse-index acceleration or Caffeine expansion;
- Alluxio, JuiceFS, or Dragonfly distributed materialization acceleration;
- new cache-policy implementation, a generic cache abstraction, distributed
  locks, or cache-based execution ownership;
- full multi-output execution, Artifact groups, or streaming output commit;
- double hashing beyond the accepted validation path;
- FFmpeg provider-plugin migration;
- OpenCue integration;
- remote/global scheduling, global optimizer policy, or Roadmap #23 work.

The existing metric/need-based revisit triggers remain the only authority for
the cache and distributed-data-plane candidates.

## 6. FFmpeg transitional obligation

`LEGACY_RENDER_INFRASTRUCTURE_FFMPEG=TRANSITIONAL_LEGACY` remains unchanged.
Phase 16 did not turn the legacy FFmpeg adapter into the first real Provider
runtime. Roadmap #22 Phase 19 must still deliver the real FFmpeg Native Pull
vertical slice in Provider-plugin form, including typed lowering/runtime
binding and the adopted WorkerRuntime support-advertisement gate. No Phase 19
implementation is claimed here.

## 7. Closure references

- `roadmap-22-phase-16-artifact-staging-materialization-reuse-decision-recovery.md`
- `roadmap-22-phase-16-decision-recovery-correction-1.md`
- `roadmap-22-phase-16-artifact-staging-materialization-reuse-implementation.md`
- `roadmap-22-phase-16-correction-1-runtime-closed-loop.md`
- `roadmap-22-phase-16-correction-2-typed-runtime-input-observability.md`
- `roadmap-22-phase-16-correction-3-concurrency-output-guards.md`
