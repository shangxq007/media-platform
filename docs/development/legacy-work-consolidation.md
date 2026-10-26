# Legacy work: current disposition and boundaries

This record describes the bounded OWN01–OWN07 consolidation from main509e6aca. It is not another
engineering policy or a new domain authority. Historical sources remain read-only; names and old
commits alone do not authorize restoring them. Exact source hashes, per-commit diffs, test identities,
commands and media are retained in the external task evidence.

| Group / requirement | Disposition | Current implementation / reason |
| --- | --- | --- |
| OWN01 authoritative commit identity, digest/length/replica/scope | COVERED | ArtifactOutputCommitOrchestrator validates the actual Artifact commit result before RuntimeClosedLoop emits native evidence; GraphRenderLifecycle and immutable DB scope/attempt/generation constraints own final acceptance. Actual receipt recovery and wrong-scope/replay PG cases remain required. |
| OWN01 exact completion replay and stale attempts | COVERED | JooqExecutionAuthorityBoundary compares persisted task/attempt/generation/Artifact before duplicate no-op and fences new completion against current lease/owner. Exact duplicate replay does not reopen ownership. |
| OWN01 old parallel Comparator/PersistedCompletionIdentity/richer shadow-evidence protocol | REJECTED | Existing Artifact-owned durable receipt and native/Render owners remain authoritative; no second commit or terminal model is restored. The internal artifact-id value is not authentication for arbitrary callers. |
| OWN02 complete immutable candidates, canonical ordering and Binding pin | COVERED | Catalog projection supplies all typed values; #22 binds once, immutable BoundGraphInputs and exact graph digest support deterministic worker re-derivation. |
| OWN02 worker-source snapshot TTL/version, binary digest and multi-worker association | DEFERRED_BY_DEPENDENCY | OD1 requires Owner disposition: source uniqueness, multiple-runtime table identity, freshness-at-admission policy and supplemental evidence associated with BoundGraphReference. Existing wf_provider_candidate_snapshot is unused persistence, not proof this behavior is implemented. Restore only after that specific contract is approved; do not turn worker availability into a competing Provider selector. |
| OWN03 authoritative execution project publication | COVERED | MediaTaskPublicationSettings binds the execution project rather than an untrusted deployment default; old mismatch-reject behavior is not restored. |
| OWN03 unused CapabilityDefinition and old kernel/constitution authority edits | REJECTED | No current consumer requires that unused abstraction; retain current typed capability vocabulary and canonical Capability/Provider owners. |
| OWN04 typed cancellation to actual child | ADOPTED | A cancellation-capable backend contract extends the existing execution SPI; the frame-extract provider uses it for probe/render and the sandbox observes cancellation until the bounded child is reaped. No concrete backend-class cast or second runtime registry. |
| OWN04 nonzero native exit | COVERED | Canonical sandbox already reports PROCESS_CRASHED. The new real-process regression retains that behavior; it is not presented as a newly fixed defect. |
| OWN04 task admission/cancel/commit races | ADOPTED | Real PG uniqueness/CAS/row-lock scenarios replace the old AtomicBoolean-only concurrency model. Scope, conflict and terminal winner assertions exercise production SQL. |
| OWN04 Artifact/Storage publication failure | COVERED | Storage owns durable write intent and exact retry; Artifact/task commit share the existing transaction. Old default-noop discard and blind backend deletion are rejected because they can erase still-referenced bytes and destroy retry evidence. |
| OWN04 obsolete thumbnail-kind shortcut | REJECTED | Current output is DERIVED_MEDIA with THUMBNAIL_OF provenance; the reader no longer accepts a legacy kind without the current relation model. The generic public ArtifactKind enum is not rewritten by this task. |
| OWN04 canonical quota admission | ADOPTED | The real HTTP regression found that ThumbnailService used an unregistered quota key. The existing QuotaPolicyService now supplies a bounded monthly media.thumbnail policy; the sole transactional QuotaUsageAuthority still owns consumption and idempotency. |
| OWN04 current Render-produced source interoperability | ADOPTED | Scoped Artifact catalog lookup precedes a registered StorageProvider verified read. Active receipt-based producers retain their own scoped read protocol. Failed native reads never fall back to another protocol. Both are active infrastructure adapters, not restored historical domain authorities. |
| OWN04 backend/worker/capability duplicates and five evidence-only commits | COVERED | Unified FrameExtractExecutionAdapter/FfmpegCpuProvider and fail-closed RuntimeExecutionBackends already replace old slice registry/runtime wrappers. Evidence-only commits remain history, not active product code. No BMF farm integration is introduced. |
| OWN05 expiry, disconnect, generation, host/runtime registration and current grants | COVERED | Existing registration authority, readiness freshness/generation checks, grant filters and real PG regressions remain authoritative. |
| OWN05 readiness observation failure before refresh | ADOPTED | Both independent existing readiness probes are observed before any durable registration write; failure cannot partially refresh host/runtime state. The old G2 five-fact HostReadiness replacement model is not reintroduced. |
| OWN05 fixed process incarnation and overlapping restart | DEFERRED_BY_DEPENDENCY | OD2 requires an explicit Registration decision. Existing bounded-V1 constant does not establish separate process identity; no claim of overlapping-process restart isolation until approved instance identity/retirement fencing or an enforced no-overlap executor gate is verified. |
| OWN06 ledger column semantics | ADOPTED | Existing test policy now defines actual task scope, database access, daemon dependency and evidence-backed shared resources. One current changed database task loses its historical pure-suite promotion and remains single-fork. |
| OWN06 old PG15/public-schema/topology and historical count claims | REJECTED | The six remaining worker-fabric PostgreSQL classes now migrate and truncate only their own generated schema; all seven run through the controlled real-PG18 entry. Old ledger instance values are not copied as current facts. Historical receipt/counts remain historical; current acceptance uses fresh source-bound JUnit identities. Suspended source cannot regain canonical parallel promotion or the benchmark overlay silently. |
| OWN07 thin architecture probe | COVERED | Formal /api/first-render delegates to #20/#21/#22, immutable BoundGraphInputStore, Temporal and the generic runtime; Render/Artifact/Execution remain separate owners. Request/response/admission adapters have no new state/cache or direct internal Repository access. |
| OWN07 actual Timeline implementation coupling and stale lifecycle comment | ADOPTED | The two existing coordinators now depend on already-published TimelineRevisionCommands/Queries, use only immutable revision reads, and do not mutate Timeline or bypass Operation. No new facade/port/plan engine was added. Dispatch failure documentation now matches the public Render lifecycle call and preserved suppressed error. |

OD1 and OD2 are explicit Owner decision dependencies, not unspecified future work. Their exact
alternatives, affected contracts and costs are in the external consolidated decision report. Those
items must not be reported complete or silently implemented while approval is pending.

## Seven thumbnail commits

015eb121 contains the original runtime/cancellation/compensation patch: typed cancellation is adapted,
current fail-closed backend registration is covered, and unsafe/no-op discard plus hardcoded library
path policy are rejected. b09e476c's cancelled-admission-before-quota rule is already in current
ThumbnailService. 2731d8df,3537787e,aecfb4db,734ca94e,0a1995aa are evidence/boundary/identity publications,
not five additional functional changes. All seven exact original IDs remain archived; no cherry-pick
or whole-branch merge is used.

## Verification and retention

Required regression is real PG18, full target-stack compilation/packaging, selected source-bound
non-database and safety suites, independent API/Worker First Render, actual thumbnail publication and
HTTP download, failure/cancel/authorization cases, and exact owned resource cleanup. A method name,
source keyword, Boolean-only race model or old log is not runtime evidence. The native test fixture
uses an actual executable, not a symlink, and asserts a started child marker before cancellation.

The current task does not delete original archive/G2/integration/Candidate/bundle. After final
ownership closure, Owner may authorize retiring redundant working copies; preserve content-addressed
history and the minimal accepted evidence. Concrete retention recommendations belong in the final
task report, not a perpetual alternative development branch.

[Default principles](../engineering/functional-development-and-module-principles.md),
[execution supplement](../architecture/current/execution-architecture-principles.md) and
[Flyway policy](../database/flyway-migration-baseline.md) remain the instruction hierarchy.
`DEPLOYMENT_ISOLATION_NOT_VERIFIED`: local behavior does not certify a protected runner or cloud scope.
