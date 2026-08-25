# Community Compute and Distributed Runtime Foundation — Amendment 1

TASK_ID=COMMUNITY_COMPUTE_AND_DISTRIBUTED_RUNTIME_FOUNDATION_GOVERNANCE_AMENDMENT_1
MODE=APPEND_FORWARD_DOCS_ONLY_GOVERNANCE
STATUS=ADOPTED_REPOSITORY_PERSISTED
IMPLEMENTATION=DEFERRED
NEW_NUMBERED_ROADMAP=NO
CRITICAL_PATH_CHANGE=NO
PHASE_16_REOPENED=NO
PHASE_17_STARTED=NO
ROADMAP_22_STATUS=IN_PROGRESS
ROADMAP_23_STATUS=NOT_STARTED
CANONICAL_MAIN_INTEGRATION=NOT_YET_PERFORMED

## 1. Scope and precedence

This amendment adopts a bounded foundation for future community/user-contributed
capacity and provider-local distributed runtimes. It does not reopen Roadmap
#22 Phase 16, start Phase 17, implement a runtime, create a numbered roadmap,
change the critical path, or integrate canonical `main`.

Community compute is execution capacity in the existing worker fabric. It is
not a new domain, Provider, Artifact authority, scheduler, task model,
OperationPlan authority, or commercial marketplace. A future implementation
must reuse the existing Native Pull boundary unless a real delegated-backend
lifecycle proves that a separate `ExecutionBackend` is necessary. BOINC,
SheepIt, and similar references do not justify pre-creating backend kinds.

Existing exact/equivalent laws are reused without aliases:

- `WORKER_RUNTIME_IS_EXECUTION_ENDPOINT_NOT_HOST_CAPACITY_AUTHORITY_V1`
- `PHYSICAL_HOST_IS_HOST_CAPACITY_SCOPE_V1`
- `CAPACITY_RESERVATION_AND_OBSERVATION_ARE_DISTINCT_AUTHORITIES_V1`
- `RESOURCE_ACCOUNTING_DOMAIN_V1`
- `TRUST_PERMISSION_SANDBOX_V1`
- `WORKER_RUNTIME_SUPPORT_ADVERTISEMENT_V1`
- `PLACEMENT_AUTHORITY_IS_EXPLICIT_PER_EXECUTION_BACKEND_V1`
- `NO_MULTIPLE_ACTIVE_PLACEMENT_AUTHORITIES_FOR_ONE_EXECUTION_SCOPE_V1`
- `BACKEND_INTERNAL_WORKER_DOES_NOT_BECOME_PLATFORM_WORKER_V1`
- `CACHE_LOCALITY_IS_OPTIMIZATION_NOT_ARTIFACT_AUTHORITY_V1`
- `BYTES_EXIST_IS_NOT_ARTIFACT_COMMITTED_OR_TASK_COMPLETED_V1`
- `STALE_EXECUTION_GENERATION_MUST_NOT_PUBLISH_WINNING_REUSE_OR_COMPLETION_V1`

## 2. Community capacity uses the existing worker fabric

`USER_CONTRIBUTED_COMPUTE_IS_WORKER_CAPACITY_NOT_DOMAIN_AUTHORITY_V1` and
`COMMUNITY_WORKER_IS_A_REAL_WORKER_RUNTIME_NOT_A_FAKE_PROVIDER_V1` are adopted.
A real community Native Pull participant is a real platform `WorkerRuntime`,
with the existing identity, incarnation, heartbeat, trust, lease, reservation,
admission, and fencing contracts. It is never represented as a fake Provider,
fake delegated backend worker, or ownership-derived subtype.

PVE, cloud, platform-owned, tenant-owned, and community-contributed are
orthogonal ownership/deployment sources. They do not create inheritance types
or change `WorkerRuntime`, `PhysicalHost`, `Device`, reservation, or
`ResourceAccountingDomain` semantics. `WorkerRuntime != PhysicalHost`, and
multiple runtimes must never duplicate one physical capacity domain.

`COMMUNITY_COMPUTE_REUSES_NATIVE_PULL_UNLESS_A_REAL_BACKEND_BOUNDARY_IS_PROVEN_V1`
is adopted. A future separate backend requires concrete lifecycle authority,
submission, observation, cancellation, internal placement, retry, and native
management behavior that cannot be represented honestly by Native Pull. No
Community, BOINC, or SheepIt backend is reserved in advance.

## 3. Eligibility, trust, and policy

`COMMUNITY_COMPUTE_ELIGIBILITY_REQUIRES_TRUST_DATA_POLICY_AND_RUNTIME_FEASIBILITY_V1`
is adopted. Eligibility is the server-validated intersection of runtime,
provider, installation/profile, device, and resource feasibility with trust,
isolation/sandbox, tenant/data handling, licensing/content restrictions,
entitlement, and policy. `CAN_RUN` technically does not imply `MAY_RUN` on a
particular worker. This amendment introduces no trust enum or parallel trust
model; Phase 17 remains not started and the existing trust/policy authorities
remain authoritative.

Worker self-report is typed, freshness- and incarnation-bound candidate
evidence only. It never creates schedulable capacity, proves Artifact
existence or locality, grants authorization, or overrides central reservations
and policy. Cache locality remains a bounded optimization only.

## 4. Untrusted results and trusted commit authority

`UNTRUSTED_WORKER_CANNOT_AUTHOR_ARTIFACT_OR_EXECUTION_COMPLETION_V1` and
`VOLUNTEER_RESULT_REQUIRES_TRUSTED_VALIDATION_BEFORE_AUTHORITATIVE_COMMIT_V1`
are adopted. An untrusted worker may return evidence and bytes. Trusted
platform staging/ingestion, digest and policy validation, Artifact commit,
attempt/ownership-generation fencing, task completion, and reuse publication
remain mandatory platform authority.

`VOLUNTEER_RESULT_VALIDATION_IS_WORKLOAD_AWARE_NOT_UNIVERSAL_DIGEST_CONSENSUS_V1`
is adopted. Validation depends on the workload's deterministic guarantees,
semantic tolerances, safety profile, provenance, and available independent
checks. A digest may prove byte identity where appropriate; it does not prove
universal semantic correctness. No generic consensus rule, universal
redundant-compute policy, or platform-wide quorum mechanism is adopted.

## 5. Accounting is not a reward marketplace

`CONTRIBUTED_RESOURCE_ACCOUNTING_IS_DISTINCT_FROM_COMMERCIAL_REWARD_V1` is
adopted. Resource accounting may measure admitted/reserved/observed use and
contribution evidence without defining price, reward, credit, settlement, or
entitlement. No GPU-hour rate, token, credit ledger, exchange, or marketplace
is adopted. Any future incentive policy belongs to separately authorized
commercial policy and may not redefine capacity or execution authority.

## 6. Specialized delegated render backends

`FLAMENCO_IS_SPECIALIZED_BLENDER_EXECUTION_BACKEND_CANDIDATE_V1` is adopted as
a deferred POC direction. Flamenco is a specialized Blender delegated
`ExecutionBackend` candidate and a peer candidate to OpenCue; it is neither
mandated nor a blocker for Roadmap #22 Phase 21.

`DELEGATED_RENDER_FARM_BACKEND_DOES_NOT_REOWN_EXECUTABLE_TASK_SEMANTICS_V1` is
adopted. The platform continues to own `ExecutableTask`, Provider binding,
Artifact authority, global backend selection, attempt/generation fences, and
authoritative completion. After bounded delegation, Flamenco may own its
internal job/worker topology and placement. A future POC must use the path
workspace output -> trusted ingestion/staging -> Artifact commit rather than
turning backend-local output into platform truth.

## 7. Provider-local distributed AI runtimes

`PROVIDER_LOCAL_DISTRIBUTED_RUNTIME_DOES_NOT_BECOME_PLATFORM_PLACEMENT_AUTHORITY_V1`,
`PROVIDER_INTERNAL_NODE_TOPOLOGY_DOES_NOT_IMPLY_PLATFORM_WORKER_IDENTITY_V1`,
and `PROVIDER_LOCAL_PARALLELISM_REMAINS_BEHIND_PROVIDER_EXECUTION_CONTRACT_V1`
are adopted. Exo- or Petals-style internal nodes, meshes, sharding, collective
execution, and local placement stay behind one provider execution contract.
They do not become platform `WorkerRuntime`/`PhysicalHost` identities, mutate
the provider-bound ExecutableTask graph, or acquire global placement authority.

`PUBLIC_PETALS_STYLE_SWARM_IS_NON_SENSITIVE_BY_DEFAULT_V1` is adopted. A
public or otherwise untrusted swarm denies sensitive, private, tenant-secret,
licensed-restricted, and policy-restricted data by default. A future private
swarm remains subject to explicit trust, tenant/data, entitlement, isolation,
and policy validation; private deployment alone is not authorization.

## 8. No distributed-compute god abstraction

`NO_UNIVERSAL_DISTRIBUTED_COMPUTE_GOD_ABSTRACTION_V1` is adopted. These three
boundaries remain distinct:

1. a community Native Pull participant is a platform `WorkerRuntime`;
2. a delegated backend worker remains internal to its `ExecutionBackend`;
3. a provider-local distributed runtime node remains internal to its Provider
   execution contract.

No universal Worker/Node/Farm/Swarm/Cluster abstraction may merge their
identity, placement, lifecycle, resource, task, retry, or authority models.

## 9. Deferred execution windows

The first community-compute POC is deferred until after Roadmap #22 Phase 20
and requires: Phase 19's real FFmpeg Native Pull vertical slice and runtime
support advertisement; sufficient Phase 20 resource accounting; sandbox and
isolation; trust/data policy; and the authoritative Artifact path. Its bounded
shape is one to three external CPU machines, public or synthetic inputs, an
explicitly restricted workload allowlist, and deterministic FFmpeg work. It
does not target public-internet scale.

Flamenco is deferred to Phase 21 or later and requires a real Blender
consumer. Exo is deferred to the first real distributed-AI consumer. Petals
is deferred to the first distributed-LLM need with an explicit public/private
swarm decision and sensitive data denied by default. Each is nonblocking now.

BOINC, SheepIt, Ramanujan, and Apache Mesos remain research references only;
they create no implementation backlog, dependency, protocol authority, or
critical-path gate.

## 10. Governance result

This amendment is repository-persisted architecture with deferred
implementation. Roadmap #22 remains `IN_PROGRESS`; Roadmap #23 remains
`NOT_STARTED`; Phase 16 remains `CLOSED`; Phase 17 remains not started; known
architecture, implementation, and governance blockers remain zero. The next
gate is
`CHATGPT_COMMUNITY_COMPUTE_AND_DISTRIBUTED_RUNTIME_FOUNDATION_GOVERNANCE_AMENDMENT_1_FINAL_REVIEW`.
