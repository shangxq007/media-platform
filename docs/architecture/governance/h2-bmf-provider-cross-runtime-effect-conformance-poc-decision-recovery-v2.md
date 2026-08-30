# H2 BMF Provider and Cross-Runtime Effect Conformance POC Decision Recovery V2

```text
TASK=BMF_PROVIDER_AND_CROSS_RUNTIME_EFFECT_CONFORMANCE_POC_DECISION_RECOVERY_V2
MODE=FAST_DELIVERY_MODE,CLEAN_FORWARD,FAIL_CLOSED,APPEND_FORWARD_HISTORY,NO_HISTORY_REWRITE,EVIDENCE_DRIVEN,MINIMUM_CHANGE,CHEAP_GATES_FIRST_EXPENSIVE_GATES_AFTER_ARCHITECTURE_FREEZE_V1
SCOPE=DOCS_GOVERNANCE_ONLY
BRANCH=agent/bmf-provider-cross-runtime-effect-conformance-poc-decision-recovery
WORKTREE=/home/user/Documents/workspace/projects/.worktrees/bmf-provider-cross-runtime-effect-conformance-poc-decision-recovery
BASE_SHA=e02579181ba3049ae65ed81080c93a7212f5833d
BASE_TREE=b67136e3a4b4e08688091bad0c4dad30d841978d
ORIGIN_MAIN=e02579181ba3049ae65ed81080c93a7212f5833d
CURRENT_HEAD_PARENT=989ee911341157570220837f326c886c4ab2163b
PRE_CORRECTION_STATUS=FOUR_UNTRACKED_DOCS_GOVERNANCE_DRAFTS_ONLY
STASH_STATE=ONE_UNRELATED_PRE_EXISTING_STASH_ON_agent/roadmap22-executable-task-graph-worker-fabric-decision-recovery_LEFT_UNTOUCHED
APPLICABLE_INSTRUCTIONS=AGENTS.md (repository root; no nested instruction under docs; no conflict with Owner brief)
HISTORY_MUTATION=NONE
CANDIDATE_SHA=RECORDED_BY_POST_COMMIT_RECEIPT_NOT_SELF_REFERENTIAL
CANDIDATE_PARENT=e02579181ba3049ae65ed81080c93a7212f5833d
```

Exact changed paths, all classified `DOCS_GOVERNANCE_ONLY`, are:

- `docs/architecture/governance/h2-bmf-provider-cross-runtime-effect-conformance-poc-decision-recovery-v2.md`
- `docs/architecture/governance/h2-phase19-deferred-capability-reassessment-v1.json`
- `docs/architecture/governance/h2-bmf-clean-forward-disposition-v1.json`
- `docs/architecture/governance/h2-bmf-runtime-dependency-research-v1.json`

## 1. Decision

```text
BMF_POC_DECISION_RECOVERY=PASS
BMF_PROVIDER_AND_CROSS_RUNTIME_EFFECT_CONFORMANCE_POC_CONTRACT=FROZEN
CONTRACT=BMF_PROVIDER_AND_CROSS_RUNTIME_EFFECT_CONFORMANCE_POC_BOUNDED_ARCHITECTURE_CONTRACT_V1
REASSESSMENT=docs/architecture/governance/h2-phase19-deferred-capability-reassessment-v1.json
SELECTED_EFFECT=BOUNDED_DETERMINISTIC_GAUSSIAN_BLUR
DEPENDENCY_PROPOSAL=DIGEST_PINNED_PROVIDER_LOCAL_OCI_PLUS_LOCK_MANIFEST_SBOM_LICENSE_AND_PROBE_FINGERPRINT
GRAPH_PROOF=LOGICAL_EXECUTION_GRAPH_TYPED_INPUT_TO_PRIVATE_BMF_GRAPH_WITH_NO_CANONICAL_NODE_MIRROR
PHYSICAL_EXECUTION_PLAN_EVIDENCE=PHYSICAL_EXECUTION_PLAN_COLLAPSE_OR_DOWNGRADE_CANDIDATE
H1_SHARED_REQUIREMENTS=CROSS_LANE_RECONCILIATION_REQUIRED
BLOCKERS=0
ARCHITECTURE_ESCALATION=EXPLICITLY_BOUNDED
READY_FOR_BMF_POC_IMPLEMENTATION=YES
BMF_POC_IMPLEMENTATION_AUTHORIZED=NO_PENDING_INDEPENDENT_CHATGPT_ACCEPTANCE_AND_B0
BMF_PROVIDER_IMPLEMENTATION_COMPLETE=NO
INDEPENDENT_CHATGPT_REVIEW=REQUIRED_BEFORE_IMPLEMENTATION
INDEPENDENT_CHATGPT_REVIEW_ROUTE_1=READ_ONLY_INSPECTION_TERMINATED_NO_FINAL_DECISION_APIDOCK_QUOTA_INSUFFICIENT
```

`BLOCKERS=0` is scoped only to freezing and reviewing this bounded candidate contract; it does not claim independent acceptance, runtime feasibility, or effect equivalence. `READY_FOR_BMF_POC_IMPLEMENTATION=YES` means the architecture candidate is ready to enter the implementation sequence after acceptance, not that execution is authorized. One independent ChatGPT route performed read-only repository inspection but terminated before a final decision because its ApiDock quota was insufficient. That outcome is neither ACCEPT nor REJECT and grants no implementation authority. After an independent review explicitly accepts the contract, separate Owner authorization may permit B0 as the first proof gate; B1 and later implementation remain unauthorized unless B0 succeeds. GPU work additionally waits for H1 reconciliation and a suitable environment. No BMF build, execution, or conformance test occurred in this decision-recovery lane.

## 2. Repository reality and authority evidence

The evidence supports a provider-local BMF lowering boundary:

- `render-module/src/main/java/com/example/platform/render/domain/renderplan/RenderGraph.java:6-17` defines a typed render DAG projection. `RenderNode.java:8-27` carries operation key, Artifact references, capability/output/execution/materialization requirements, and exact time semantics without a provider field.
- `media-execution-plan-module/src/main/java/com/example/platform/execution/planning/LogicalExecutionGraph.java:15-31` states that each logical node retains exact typed source semantics; lines 37-69 carry typed operation, Artifact, capability, execution, output, materialization, sample-window, and coverage fields. This is sufficient typed canonical input for provider lowering.
- `media-execution-plan-module/src/main/java/com/example/platform/execution/planning/PhysicalExecutionPlan.java:14-27` explicitly freezes `ONE_LOGICAL_NODE_TO_ONE_PHYSICAL_PLAN_UNIT`; its unit at lines 39-67 is a provider-neutral typed IO projection.
- `media-execution-plan-module/src/main/java/com/example/platform/execution/planning/PhysicalPlannerV1.java:135-142` loops once per logical node, and lines 216-238 create one unit by copying that node's identity/operation/requirements. No fusion, partition optimization, provider selection, runtime placement, or device binding occurs. `LogicalPhysicalPlanner.java:16-26` independently documents the 1:1 logical-to-physical chain.
- Current provider reality differs from the preferred long-term input: `ffmpeg-provider-module/src/main/java/com/example/platform/ffmpeg/FfmpegCpuTranscodeLowerer.java:13-30` consumes an `ExecutableTask`, requires one membership, then obtains its `PhysicalPlanUnit`; lines 31-63 validate and lower it. A BMF POC may initially use the same narrow adapter, but no new canonical projection is authorized.
- `media-execution-plan-module/src/main/java/com/example/platform/execution/taskgraph/ProviderBoundExecutableTaskGraph.java:190-200` exposes coarse tasks and provider-local/cross-task dependencies; `ExecutionArtifactBoundary.java:12-30,79-87` carries typed immutable cross-task/provider/runtime materialization semantics.
- `media-execution-plan-module/src/main/java/com/example/platform/execution/composition/ProviderLocalCompositionEvaluator.java:20-27` is pure provider-local legality authority and freezes mandatory Artifact boundaries; lines 68-91 make unsupported/unknown provider composition fail closed.
- Provider family and implementation identity are already distinct: `ProviderId.java:5-17`, `ProviderImplementationId.java:5-20`, and `ProviderBindingPin.java:10-20`. The current FFmpeg example uses family `ffmpeg` and implementation `ffmpeg.cpu.native-pull.v1` at `ffmpeg-provider-module/src/main/java/com/example/platform/ffmpeg/FfmpegCpuProvider.java:24-40`.
- Worker Fabric already owns mutable runtime matching. `WorkerRuntimeDescriptor.java:6-22` identifies runtime lifecycle/host relation; `WorkerRuntimeSupportAdvertisement.java:8-29` is non-authoritative candidate support evidence; `WorkerRuntimeSupportRequirement.java:6-16` binds the server-owned requirement to an exact provider pin. `RuntimeEligibilityEvaluator.java:13-37` combines probe, runtime support, worker/host, device, reservation/resource, and sandbox evidence and emits `UNKNOWN_FAIL_CLOSED`.
- Artifact authority is already immutable: `artifact-module/src/main/java/com/example/platform/artifact/domain/Artifact.java:10-28`. `worker-fabric-module/src/main/java/com/example/platform/workerfabric/reuse/DirectStorageArtifactMaterializer.java:36-57` verifies availability and content digest before materialization.
- The repository contains no Java effect-conformance authority defining the required equivalence ladder/tolerance envelope. The closest reusable test-support path is sandbox conformance mechanics, `test-conformance-support/src/test/java/com/example/platform/testsupport/Phase17SandboxConformance.java`; B5 must add the bounded effect foundation under separate implementation authorization.

Legacy conflicts are evidence, not current authority. `docs/architecture/bmf-integration.md:27-45,58-73,90-94` makes BMF an ExecutionBackend, puts graph specification in task payload, and assigns BMF execution location. `docs/architecture/adr/ADR-006-bmf-integration.md:14-26` repeats the hybrid provider/backend decision. The same legacy authority appears in live architecture surfaces: `docs/architecture/platform-roadmap.md:40,80,116`, `docs/architecture/platform-kernel.md:140-159,194-196`, `docs/architecture/eventing/outbox-module-separation.md:44`, `docs/architecture/adr/ADR-005-extension-decision-guide.md:39`, `docs/architecture/execution-environment.md:69`, `docs/architecture/extension-decision-guide.md:84,169`, and `docs/architecture/runtime-governance.md:50,224`. Those documents are `MIGRATE_REDESIGN`, not present authority.

The materialized Render scan also exposes more than the original direct BMF classes. `BackendCompilerRuntimeService.java:11-30`, `BackendCompiler.java:6-12`, and `BackendExecutionSpec.java:6-18` establish a Render-local backend compiler/spec authority; `LocalProcessExecutionSpec.java:6-22`, `RemotionExecutionSpec.java:6-23`, `LocalProcessBackendCompiler.java:10-15`, and `infrastructure/exection/RemotionBackendCompiler.java:9-15` specialize that shadow. `ArtifactDependencyGraph.java:6-25`, `LogicalCapabilityGraph.java:6-23`, `ProviderBindingPlan.java:6-30`, and `RenderExecutionPlan.java:8-35` create parallel compile DAG/binding/execution-plan authorities. `TimelineRenderExecutionMode.java:9-22`, `TimelineRenderExecutionProperties.java:19-40`, `PlanBasedTimelineRevisionRenderService.java:35-62`, `CaptionTemplateRenderService.java:25-46`, and `RemotionLocalExecutionRunner.java:9-21` route those plans through local execution surfaces. They are `DELETE_SHADOW` candidates for a separately authorized migration; this lane neither edits nor deletes them.

Implementation identity also leaks into otherwise useful semantic or boundary code: `MediaPipelineProvider.java:6-29` accepts opaque pipeline definitions and names BMF suitability; `EffectBackendKind.java:3-23` and `EnhancedEffectDescriptor.java:8-24` attach implementation kind to effect descriptors; `TimelineRenderJobMapper.java:44-55` hard-codes `bmf`; and `infrastructure/ExecutionMode.java:3-7` creates a Render-local location mode. The inventory therefore uses `DELETE_SHADOW` for duplicate execution/provider authorities and `MIGRATE_REDESIGN` where provider identity is mixed into retained semantics or fail-closed policy.

## 3. Frozen architecture laws

The following laws are exact and normative:

1. `MEDIA_PLATFORM_OWNS_WHAT`
2. `SELECTED_PROVIDER_OWNS_PROVIDER_LOCAL_HOW`
3. `EXECUTION_BACKEND_OWNS_WHERE_AND_WHEN`
4. `BMF_GRAPH_IS_A_PROVIDER_LOCAL_LOWERED_EXECUTION_GRAPH_V2`
5. `NO_PROVIDER_LOCAL_GRAPH_MIRRORING_V1`
6. `ONE_GRAPH_PER_AUTHORITY_BOUNDARY_V1`
7. `LOGICAL_EXECUTION_GRAPH_IS_LAST_PROVIDER_NEUTRAL_EXECUTION_GRAPH_V1`
8. `EXECUTABLE_TASK_GRAPH_IS_CROSS_RUNTIME_BOUNDARY_GRAPH_V1`
9. `ProviderId != ProviderImplementationId`
10. `PROVIDER_RUNTIME_DEPENDENCY_SET_IS_IMPLEMENTATION_LOCAL_V1`
11. `NO_GLOBAL_NATIVE_TOOL_VERSION_AUTHORITY_V1`
12. `CONFORMANCE_NOT_VERSION_UNIFICATION_IS_THE_CROSS_PROVIDER_CONTRACT_V1`
13. `CROSS_RUNTIME_EFFECT_CONFORMANCE_FOUNDATION_V1`
14. `CROSS_PROVIDER_FUSION_DEFAULT=NO`
15. `WORKFLOW OWNS PROCESS`
16. `TIMELINE OWNS MEDIA COMPOSITION`
17. `PARALLELIZE_ACROSS_AUTHORITIES_NOT_WITHIN_UNFROZEN_AUTHORITY_V1`

`BMF_OWNS_ALL_HOW` is explicitly rejected. BMF owns only BMF-provider-local HOW. It does not own Timeline/Recipe/Workflow/Render semantics, Artifact authority, provider selection, cross-provider transfer, WorkerRuntime matching, placement, retry, attempts, distributed scheduling, or completion.

## 4. BMF_PROVIDER_AND_CROSS_RUNTIME_EFFECT_CONFORMANCE_POC_BOUNDED_ARCHITECTURE_CONTRACT_V1

**C1 — Platform WHAT.** The media platform owns canonical media meaning: Timeline composition, RenderGraph render WHAT, typed effect semantics, LogicalExecutionGraph operation/dependency semantics, Artifact requirements, and conformance decisions. Canonical effect input is `BlurEffect`, never `ff_filter(name,args)` or `bmf_module(name,args)`.

**C2 — Provider HOW.** Once a provider/implementation is selected, it owns replaceable local lowering and execution mechanics inside the already-authorized semantic envelope. BMF/FFmpeg module/filter names may occur only below the provider boundary.

**C3 — Backend WHERE/WHEN.** ExecutionBackend and Worker Fabric own placement, runtime selection, availability, reservation, device eligibility, attempts, retry, fencing, scheduling mechanics, and completion. The BMF scheduler may schedule within one admitted provider execution only; it is not distributed backend authority.

**C4 — Long-term graph layers.** `RenderGraph` is provider-neutral render WHAT. `LogicalExecutionGraph` is the final provider-neutral execution/dependency graph. `ExecutableTaskGraph` is coarse provider dispatch, Artifact materialization, retry/attempt observation, cross-runtime boundary, observability, and backend handoff. Beneath ETG, BMF graph, FFmpeg filtergraph, and GStreamer pipeline are private implementation state.

**C5 — One authority, one graph.** Each graph exists only within its authority boundary. BMF node/module/edge topology is not copied into RenderGraph, LogicalExecutionGraph, PhysicalExecutionPlan, or ExecutableTaskGraph.

**C6 — No BMF semantic identity.** The platform never assigns canonical operation identity, canonical semantic digest, cross-provider identity, cache identity, or Artifact identity to a BMF node/module/edge. One canonical operation may lower to many BMF nodes, and those many nodes may remain one platform execution unit.

**C7 — Canonical lowerer input (Q1).** The BMF lowerer consumes one or more typed canonical `LogicalExecutionGraph.LogicalExecutionNode` operations plus exact typed input, output, dependency, temporal, capability, execution, and materialization requirements assembled in a provider-neutral lowering request at the provider boundary. It never consumes raw Render objects or a caller-supplied BMF graph specification.

**C8 — Existing projection decision (Q2).** LogicalExecutionGraph/typed canonical operations are the desired semantic source. Initial implementation may use a narrow read-only adapter from the existing `PhysicalPlanUnit` carried by ETG because current ETG and FFmpeg lowering already do so. The adapter must prove a lossless link back to the exact logical nodes; it cannot add semantics or become another canonical graph/projection.

**C9 — Private graph object (Q3).** A provider-private immutable `BmfLoweredExecutionGraph` (conceptual name) contains BMF nodes/modules/edges, private filter/module parameters, provider asset references, and local scheduling/fusion decisions. It exists only inside the BMF provider implementation/runtime bundle and has no platform serialization contract.

**C10 — Allowed visible result (Q4).** The platform may observe only typed lowering acceptance/rejection, the bound provider implementation/runtime fingerprint, opaque provider execution correlation, declared inputs/outputs, immutable output Artifacts, typed status/failure, and conformance evidence. It may not observe or route on BMF topology.

**C11 — Diagnostics (Q5).** Diagnostics are typed, bounded, and non-authoritative: canonical operation/node attribution, provider implementation ID, runtime fingerprint reference, failure code, failed semantic condition, metric/equivalence outcome, sanitized BMF module label if useful, and evidence Artifact references. Diagnostics never promote private BMF strings or graph identities into semantic authority.

**C12 — Artifact boundaries (Q6).** Cross-provider and cross-runtime edges default to immutable typed Artifact materialization using `ExecutionArtifactBoundary` and content-digest verification. Direct transfer/fusion is forbidden unless semantic equivalence, legal transfer, isolation, lifecycle, failure, and execution contracts are all proven. UNKNOWN yields materialization or rejection, never implicit memory sharing.

**C13 — Runtime dependencies (Q7).** Each ProviderImplementation declares its complete implementation-local dependency set and compatibility predicates. Dependencies are locked, built, SBOMed, license-reviewed, probed, fingerprinted, and matched against WorkerRuntime. No host `/usr/bin/ffmpeg`, ambient host library, runtime download, or unpinned package/branch is accepted. The provider is not a package manager.

**C14 — WorkerRuntime/device observations (Q8).** H2 supplies provider-specific required evidence: image/lock/linkage/module probe and, for GPU, driver/CUDA/device ABI/CV-CUDA/blur_gpu loadability. H1 owns shared WorkerRuntime, host/device identity and incarnation, availability, resource/capacity, reservation, and placement. H2 consumes those typed observations and records `CROSS_LANE_RECONCILIATION_REQUIRED`; it creates no competing canonical model.

**C15 — CPU/GPU identity (Q9).** Stable family identity is `bmf`. Separate stable implementation identities are `bmf.cpu.container.v1` and `bmf.cuda.container.v1`. A CPU result and a GPU result are different implementation provenance under one provider family and one canonical BlurEffect contract.

**C16 — Semantic digest authority (Q10).** Canonical semantic digests include the exact typed BlurEffect contract/revision, normalized parameters, canonical input pins, color/precision policy, temporal/spatial requirements, and platform-owned dependencies/outputs. They exclude BMF/FFmpeg versions, graph topology, image/lock digest, command/module names, compiler flags, runtime/worker/host/device/driver identity, probe output, attempt/retry, and placement. Private fusion does not change canonical digest.

**C17 — Non-authoritative BMF details (Q11).** BMF graph/module/node names and counts, FFmpeg filter names/arguments, Python/C++ bindings, queue/thread choices, local scheduling, buffering, CUDA kernels, memory layout, module templates, and dynamic graph decisions are implementation detail. Templates/modules are execution assets, never platform DSL, Recipe, Workflow, Timeline, or semantic contract authority.

**C18 — Optimization/fusion bounds (Q12).** `CROSS_PROVIDER_FUSION_DEFAULT=NO`. BMF-private fusion/dynamic graph behavior is allowed only within one provider implementation, one admitted ETG task/authority boundary, and one proven semantic/tolerance envelope; it cannot cross mandatory Artifact boundaries, alter output/metadata/determinism, weaken attribution, or change canonical digest. Any unproven case fails closed.

**C19 — Conformance evidence storage (Q13).** Corpus inputs, reference outputs, provider outputs, normalized comparison data, reports, SBOM/license references, lock/runtime fingerprints, hardware provenance, and diagnostic renderings are immutable Artifacts. A typed `ConformanceDecision` record references their Artifact IDs/content digests, contract/corpus/tolerance revisions, implementation bindings, repetitions, metrics, decision, reviewer, and append-forward supersession link. The report stores references, not ambient filesystem paths.

**C20 — UNKNOWN policy (Q14).** Equivalence `UNKNOWN`, missing/stale probe, missing fingerprint, missing corpus result, unregistered tolerance, unsupported conversion, nondeterminism, or ambiguous metadata fails closed wherever equivalence is required. It cannot select a provider, authorize direct fusion/transfer, accept conformance, or publish a reusable equivalent Artifact.

**C21 — No legacy Render FFmpeg authority (Q15).** Render and Timeline expose only typed canonical effects/requirements. No `FFmpegCommandFactory`, filtergraph string, BMF graph spec, executable path, provider module name, or provider runtime probe returns to Render authority. Legacy source/docs are clean-forward evidence only; new provider work lives behind Provider/ETG/Worker Fabric boundaries and must include zero-awareness architecture guards.

**C22 — Effect Semantic Contract.** `BlurEffect` V1 defines deterministic Gaussian convolution over canonical frames. Required fields are horizontal/vertical sigma or an exact frozen derivation from the existing radius input, integer support radius, kernel truncation/construction/normalization and coefficient rounding, edge mode, channel set, alpha rule, working color space, precision, temporal coverage, and metadata invariants. B0/B2 must reconcile the current `GaussianBlurEffect(radiusPixels)` at `VideoEffectSemantics.java:161-174` without silently inventing a backend-specific meaning.

**C23 — Reference Implementation.** Reference CPU is an independent deterministic scalar kernel over decoded canonical frame fixtures. It cannot call BMF, BMF-internal FFmpeg, the BMF lowering code, a provider-private module, or a standalone FFmpeg effect path. Decode/canonicalization may use a separately pinned fixture preparation step whose output frames are themselves immutable corpus Artifacts.

**C24 — Working Color Space.** The test domain is canonical linear-light RGB float32. Corpus preparation must pin source transfer function, matrix coefficients, full/limited range interpretation, chroma siting/subsampling reconstruction, RGB primaries/white point, chromatic adaptation if any, alpha representation, conversion algorithm, and rounding. Comparisons occur after every implementation output is converted to this same canonical domain.

**C25 — Precision Contract.** Reference accumulation order, coefficient representation, float32 input/output rounding, intermediate precision, clamping, NaN/Inf handling, channel ordering, and edge sampling are frozen per contract revision. CPU/GPU may use different internal precision only if the output satisfies the registered envelope and determinism contract.

**C26 — Equivalence Class.** The ladder is exactly `EXACT`, `CANONICAL`, `SEMANTIC`, `OBSERVATIONAL`, `TOLERANCE_BOUNDED`, `NON_EQUIVALENT`, `UNKNOWN`. Decisions state the highest proven class; absence of proof is `UNKNOWN`, not approximate success.

**C27 — Tolerance Envelope.** No final numeric tolerance is asserted here. Before acceptance, B5 pre-registers a versioned per-metric envelope from a clean reference corpus, without looking at held-out acceptance outcomes. Required metric structure includes pixel maximum error, pixel mean error or RMSE, a perceptual metric as diagnostic only, alpha error/behavior where applicable, metadata invariants separately, deterministic repetition equality/stability, and CPU/GPU hardware provenance. Every threshold has units, domain, aggregation, frame/corpus rule, exceptional-value rule, and pass conjunction. Tolerance loosening requires append-forward contract revision and independent review; prior decisions are not rewritten.

**C28 — Conformance Corpus.** The corpus is immutable, content-addressed, licensed, and versioned. It covers constant/impulse/step/ramp/checker/noise/natural-image frames, odd/even dimensions, boundaries/corners, small frames relative to radius, zero/small/large admitted blur values, primaries/transfer/range/chroma conversions, alpha cases if admitted, and metadata fixtures. Negative/mutation controls must prove channel swap, wrong transfer, wrong edge mode, changed kernel/radius, and nondeterminism are detected.

**C29 — Conformance Decision.** The initial matrix is exactly Reference CPU vs BMF CPU vs BMF GPU. BMF GPU is `NOT_RUN` when no suitable reconciled environment exists; it is never inferred from CPU. Acceptance requires every mandatory corpus case/metric/invariant/repetition to satisfy its pre-registered policy with complete fingerprints and evidence; otherwise result is `NON_EQUIVALENT` or `UNKNOWN` according to observed proof.

**C30 — Provider runtime identity.** Runtime/version/build/dependency facts are compatibility and provenance, not canonical media semantics. Image digest + lock-manifest digest + normalized probe output form the ProviderImplementation runtime fingerprint; GPU additionally binds normalized driver/runtime/device ABI evidence. A fingerprint mismatch is typed incompatibility evidence.

**C31 — Dependency distribution.** Prefer digest-pinned provider-local multi-stage OCI images. CPU contains only the minimum blur path. GPU adds only pinned CUDA/CV-CUDA/blur_gpu closure and exact device ABI compatibility. Images may not discover or install dependencies during task execution.

**C32 — BMF release candidate.** B0 tests BMF v0.2.0 at commit `c39146c636c6b2b68ffaf741095ce737bf123254`, tree `f072467431ad2d5d571eeda04510b93d25156a3a`, commit time `2025-06-27T14:12:12+08:00`.[1] Observed upstream HEAD `5c8d302a468085b853613f026876c39d920ee20e` is not selected.

**C33 — POC scope.** The bounded candidate scope is one deterministic Gaussian blur only; this clause is not execution authorization. No concat, transition, subtitle, audio, codec matrix, full scheduler, OpenCue, distributed scheduling, complete GPU matrix, BMFLite, Roadmap #23, or FAOF-3. A simple spatial overlay is B6 conditional only after blur closes.

**C34 — Selected-effect rationale.** Phase 19 retained typed video-effect semantics (`docs/architecture/governance/roadmap-22-phase-19-legacy-render-ffmpeg-functional-capability-ledger-v1.json:721-737`), and Timeline already names Gaussian blur (`VideoEffectSemantics.java:161-174`). The selected BMF tree exposes CPU FFmpeg filter composition and a separate CV-CUDA-backed `blur_gpu`, enabling one strong Reference CPU/BMF CPU/BMF GPU conformance question without broad parity.[1]

**C35 — PhysicalExecutionPlan disposition.** Current PhysicalExecutionPlan is mechanically a 1:1 provider-neutral projection, not physical placement. BMF can conceptually consume the exact typed LogicalExecutionGraph operations and requirements directly. Therefore `PHYSICAL_EXECUTION_PLAN_COLLAPSE_OR_DOWNGRADE_CANDIDATE` is recorded as evidence only. H1/H2 later reconciles whether it remains an IO view, is downgraded, or collapses; this report does not redesign/delete it or mutate Roadmap #21.

**C36 — Cross-authority parallelism.** Work may parallelize across already-frozen independent authorities (for example dependency research and corpus design). Within an unfrozen semantic authority, contract, implementation, and tolerance calibration remain serialized so competing definitions cannot emerge.

**C37 — Legal closure.** Apache-2.0 covers BMF source, not the whole distributed runtime.[10] FFmpeg configured with `--enable-gpl --enable-nonfree`, codec libraries, CUDA/CV-CUDA, and all transitive assets require an SBOM and independent distribution/license review before GO.[5]

**C38 — Fail-closed build proof.** The upstream Dockerfiles are not the POC recipe. B0 freezes and verifies all sources/artifacts and proves no ambient linkage. Failure to reproduce, review licensing, generate the fingerprint, or execute the minimum isolated blur path is NO-GO.

**C39 — Review and change control.** Independent architecture review is mandatory before implementation. Contract, corpus, tolerance, dependency lock, or authority changes are append-forward revisions with explicit review; no history rewrite, silent tolerance loosening, or scope expansion is allowed.

### 4.1 Explicit answer index for the 15 provider-contract questions

| Question | Frozen answer |
|---|---|
| 1. Canonical input to BMF lowerer | C7: typed logical operation nodes plus exact typed IO/dependency/requirement request. |
| 2. LEG, typed operations, or projection | C8: LEG/typed operations are the desired source; narrow existing PhysicalPlanUnit adapter is transitional only. |
| 3. Private graph object | C9: provider-private `BmfLoweredExecutionGraph`; no public serialization. |
| 4. Allowed visible result | C10: typed status/Artifacts/fingerprint/opaque correlation only. |
| 5. Diagnostics | C11: typed, bounded, attributed, sanitized, non-authoritative evidence. |
| 6. Artifact boundaries | C12: immutable typed materialization by default; direct transfer/fusion requires proof. |
| 7. Runtime dependencies | C13: complete implementation-local locked/probed/fingerprinted set; no ambient host tools. |
| 8. WorkerRuntime/device observations | C14: H1 shared authority; H2 provider-specific requirements and diagnostics. |
| 9. CPU vs GPU identity | C15: `bmf.cpu.container.v1` and `bmf.cuda.container.v1` under family `bmf`. |
| 10. Semantic digest authority/exclusions | C16: typed media semantics included; runtime/provider-local HOW excluded. |
| 11. Non-authoritative BMF details | C17: topology, names, scheduling, kernels, templates, dynamic mechanics. |
| 12. Optimization/fusion bounds | C18: provider-private, single envelope/task, no boundary/digest/semantic changes. |
| 13. Conformance evidence storage/reference | C19: immutable Artifact evidence plus typed content-addressed decision record. |
| 14. UNKNOWN fail-close | C20: never accepts equivalence, fusion, selection, or reusable equivalence. |
| 15. Avoiding legacy Render FFmpeg authority | C21: typed provider-neutral Render only plus zero-awareness guards and clean-forward retirement. |

## 5. BMF version and runtime dependency evidence

The machine-readable research record is `docs/architecture/governance/h2-bmf-runtime-dependency-research-v1.json`. Independent `git ls-remote`, exact-tag clone, `git rev-parse`, and local file inspection selected BMF v0.2.0 at commit `c39146c636c6b2b68ffaf741095ce737bf123254` and tree `f072467431ad2d5d571eeda04510b93d25156a3a`.[1] The tag has no mechanically proven signature or attestation; the commit/tree are the evidence identity and tag movement would fail B0. The research clone `/tmp/BMF_H2_RESEARCH_V2` is evidence only, never a runtime input. A deterministic local `git archive --format=tar HEAD` produced SHA-256 `d5448311aa4d44724fbad43a1ecc218223191b0972c66bae0000e209e69f8eb6`; this is a locally generated evidence digest, not an upstream-published attestation.

`setup.py` identifies BabitMF 0.2.0, Python >=3.6, and numpy>=1.19.5.[2]
Non-Windows CMake requires C++17; defaults enable local dependencies, CUDA, Python, FFmpeg, and tests; CUDA raises the CMake minimum to 3.17 and requests CUDA toolkit 11.[3]
The official Ubuntu 20.04 Dockerfiles build FFmpeg from source and install Python packages without a complete hash lock.[8][9]
`scripts/build_ffmpeg.sh` selects FFmpeg 4.4, enables GPL/nonfree configuration, and uses mutable shallow x264/x265/fdk-aac branches; it also records CUDA 12.2, nv-codec-headers n10.0.26.2, and optional TensorRT 8.6.1.6/CV-CUDA 0.3.1-beta inputs.[5]
`cmake/ffmpeg.cmake` can fall back to `/opt/conda`, `/usr`, or pkg-config.[7]
`dependencies.cmake` pins nlohmann/json v3.11.2 and stduuid v1.2.3, while `build.sh` may download a breakpad release asset.[6][4]
BMF source is Apache-2.0, but that license does not prove the distribution closure of FFmpeg, codecs, CUDA/CV-CUDA, or transitive assets.[10]

The FFmpeg 4.4 tar.bz2 downloaded from `https://ffmpeg.org/releases/ffmpeg-4.4.tar.bz2` was mechanically observed as `11489948` bytes with SHA-256 `42093549751b582cf0f338a21a3664f52e0a9fbe0d238d3c992005e493607d0e`.
This records the downloaded source evidence and does not claim an upstream-published checksum attestation.
Together, the observed mutable sources, ambient fallback paths, and incomplete dependency locks make the upstream Dockerfiles unacceptable as the POC recipe.[5][7][8]

The selected proposal adds a provider-local lock manifest and digest-pinned multi-stage OCI build: base digest; BMF commit/tree; the mechanically observed FFmpeg 4.4 source checksum initially for compatibility; every codec/dependency commit or tar checksum; compiler/CMake/Python wheel hashes; enabled modules and flags. It generates SBOM, license report, dynamic-link inventory, image digest, lock digest, and normalized probe. No success is claimed until B0 produces and independently reviews that evidence.

## Sources

[1] https://github.com/BabitMF/bmf/tree/c39146c636c6b2b68ffaf741095ce737bf123254 — BMF v0.2.0 selected commit tree
[2] https://github.com/BabitMF/bmf/blob/c39146c636c6b2b68ffaf741095ce737bf123254/setup.py — `setup.py`
[3] https://github.com/BabitMF/bmf/blob/c39146c636c6b2b68ffaf741095ce737bf123254/CMakeLists.txt — `CMakeLists.txt`
[4] https://github.com/BabitMF/bmf/blob/c39146c636c6b2b68ffaf741095ce737bf123254/build.sh — `build.sh`
[5] https://github.com/BabitMF/bmf/blob/c39146c636c6b2b68ffaf741095ce737bf123254/scripts/build_ffmpeg.sh — `scripts/build_ffmpeg.sh`
[6] https://github.com/BabitMF/bmf/blob/c39146c636c6b2b68ffaf741095ce737bf123254/cmake/dependencies.cmake — `dependencies.cmake`
[7] https://github.com/BabitMF/bmf/blob/c39146c636c6b2b68ffaf741095ce737bf123254/cmake/ffmpeg.cmake — `ffmpeg.cmake`
[8] https://github.com/BabitMF/bmf/blob/c39146c636c6b2b68ffaf741095ce737bf123254/docker/Dockerfile — `docker/Dockerfile`
[9] https://github.com/BabitMF/bmf/blob/c39146c636c6b2b68ffaf741095ce737bf123254/docker/Dockerfile.cpu — `docker/Dockerfile.cpu`
[10] https://github.com/BabitMF/bmf/blob/c39146c636c6b2b68ffaf741095ce737bf123254/LICENSE — `LICENSE`

## 6. Phase 19 deferred-capability reassessment

`docs/architecture/governance/h2-phase19-deferred-capability-reassessment-v1.json` mechanically preserves the exact 14 deferred capability keys from the historical ledger. It classifies each exactly once: direct FFmpeg primitive candidate 3; BMF provider-local graph candidate 3; shared multi-provider semantic capability 3; requires new canonical contract 4; defer further 1; total 14; unclassified 0. This is hypothesis/evidence for later lanes, not an implementation checklist. The historical ledger is unchanged.

## 7. Clean-forward disposition

`docs/architecture/governance/h2-bmf-clean-forward-disposition-v1.json` materializes a bounded 65-path universe at the base SHA. Its declared roots are live repository architecture/render docs, `render-module/src/main/java`, and the already-adopted exact media execution/provider/worker/sandbox/artifact/timeline/outbox/conformance paths. Exact pattern families cover direct BMF/FFmpeg/filtergraph awareness, Render-local backend compilers/execution specs, local runners/modes, old provider pipeline definitions, old effect backend enums/descriptors, and old Render-local compile DAGs/plans. The union contains 38 pattern-matched paths plus 27 exact adopted paths, with zero excluded rows.

Every path in that explicitly declared universe is an inventory row exactly once: total 65; `REUSE_AS_CANONICAL` 19; `REUSE_MECHANICS_ONLY` 6; `MIGRATE_REDESIGN` 16; `DELETE_SHADOW` 23; `DEFER` 1; `UNCLASSIFIED` 0. This is mechanical completeness for the materialized targeted universe, not a claim that every incidental repository BMF/FFmpeg mention or every path outside the declared matchers has been semantically adjudicated.

In particular, all live architecture documents caught by the targeted legacy-authority matcher are `MIGRATE_REDESIGN`. Duplicate Render-local backend/spec/compiler, graph/binding/plan, provider pipeline, mode, and runner authorities are `DELETE_SHADOW`; effect descriptors and fail-closed mapper policy that mix implementation identity into retained meaning are `MIGRATE_REDESIGN`. Canonical RenderGraph, LogicalExecutionGraph, provider-bound ETG, Artifact, and WorkerRuntime rows remain preserved. This task does not migrate or delete any inventoried source path.

## 8. H1 shared requirements and bounded escalation

ARCHITECTURE_ESCALATION is `EXPLICITLY_BOUNDED`, not `NONE`, because:

1. H1 must reconcile the shared WorkerRuntime/host/device/driver/runtime ABI/resource/capacity/availability/reservation model with BMF CPU/GPU provider-specific fingerprint and probe requirements.
2. B0 must prove the complete locked runtime is reproducible, isolated, legally reviewed, and minimally executable.
3. H1/H2 later decides the PhysicalExecutionPlan collapse/downgrade evidence without changing Roadmap #21 here.

These are named gates, not contract-freeze blockers. H2 does not define device identity, GPU capacity, placement, or a second runtime registry.

## 9. Bounded implementation plan — do not execute in this lane

**B0 — Reproducible build/runtime proof.** Freeze the lock; build the minimum CPU image in a controlled environment; prove exact image/linkage/probe fingerprint, SBOM/license closure, reproducibility, isolation from host tools, and one minimum blur path. NO-GO if any proof fails.

**B1 — Provider skeleton and typed registration.** Add family `bmf`, distinct CPU/GPU implementation descriptors, exact capability/execution/runtime requirements, probe/fingerprint types, and plugin registration. No blur execution yet; unknown/mismatch paths fail closed.

**B2 — One canonical blur to private BMF lowering.** Refine/freeze BlurEffect V1, construct the provider-neutral lowering request, implement CPU private BMF graph lowering, and add guards against BMF topology in canonical graphs/digests.

**B3 — BMF CPU Artifact closed loop.** Run only through Worker Fabric/sandbox, materialize exact inputs, produce one authoritative output, durably commit immutable Artifact evidence, and preserve typed failures/fencing/completion.

**B4 — GPU only if environment permits.** After H1 reconciliation, exact GPU fingerprint/probe eligibility, and CPU closure, add `bmf.cuda.container.v1`. Otherwise record `NOT_RUN`; do not emulate or infer GPU conformance.

**B5 — Reference CPU/BMF CPU/BMF GPU conformance corpus.** Freeze Reference CPU, corpus, conversions, precision, pre-registered tolerance policy, metrics, repetitions, provenance, evidence Artifacts, mutation/negative controls, and typed decisions. No post-result tolerance tuning.

**B6 — Conditional second effect.** Only after blur closes cleanly and independent review approves, consider one simple spatial overlay. It is outside this candidate scope and requires separate authorization.

**B7 — H1 reconciliation.** Close shared runtime/hardware/resource authority and PhysicalExecutionPlan evidence. Do not create competing canonical models.

No scope expansion occurs before the first effect closes.

## 10. Validation strategy and decision-recovery evidence

This documentation lane uses cheap gates only:

1. mechanically recheck exact branch/worktree/HEAD/tree/origin-main, clean bytes, stash state, and instruction scope before editing;
2. inspect architecture/source paths cited above and the exact historical 14-key ledger set;
3. parse all three new JSON files;
4. prove Phase 19 key-set equality, exactly one allowed category per row, total/category arithmetic, and unclassified zero;
5. reproduce the targeted clean-forward pattern union, prove its equality with the inventory minus the 27 declared adopted exact paths, and prove path existence, uniqueness, allowed dispositions, arithmetic, and materialized-universe unclassified zero;
6. prove all required laws, Q1-Q15, authorization fields, numbered inline citations, ten immutable source URLs, exact provenance/checksum values, and four exact deliverable names exist;
7. prove the diff contains only the four requested `docs/architecture/governance` deliverables and no forbidden production/test/build/config/workflow/runtime file;
8. recheck final worktree state without committing.

During implementation, use targeted architecture boundary guards, provider-local graph privacy guards, Render concrete FFmpeg/BMF zero-awareness guards, runtime fingerprint/linkage probes, targeted conformance tests, and mutation/negative controls. Independent architecture review precedes implementation. Broader suites run only after actual code changes justify them. This decision-recovery lane does not run Gradle or the full test suite.

Executed cheap-gate receipt:

- `pwd; git branch --show-current; git rev-parse HEAD; git rev-parse HEAD^{tree}; git rev-parse origin/main; git status --short; git stash list; rg --files -g AGENTS.md` — PASS: exact worktree/branch/base/tree/origin; pre-correction state was exactly four untracked governance drafts; one unrelated stash observed and untouched; only root `AGENTS.md` applies in this worktree.
- `jq empty <three-new-json-paths>` — PASS: three valid JSON documents.
- `jq -n --slurpfile old <phase19-ledger> --slurpfile new <reassessment> <key-set-and-count-check>` — PASS: old/new deferred key sets equal; missing 0; extra 0; rows 14; unique keys 14; invalid categories 0; category arithmetic 14; unclassified 0.
- Targeted `rg`/basename scans plus `jq` set comparison and an existence loop over `.inventory[].path` — PASS: pattern union 38; adopted exact paths 27; targeted universe 65; inventory rows 65; unique paths 65; excluded rows 0; invalid dispositions 0; all paths exist; disposition arithmetic 65; unclassified 0.
- `rg` exact-law/decision/citation/provenance scans plus sequential C-clause and Q-table checks — PASS: 17 frozen laws present; C1-C39 contiguous; 15 question rows; all required authorization fields, checksum/provenance values, numbered citations [1]-[10], and 10 immutable commit-pinned upstream URLs present.
- Final identity/scope/JSON/whitespace shell gate over `git status --porcelain=v1`, `git rev-parse`, `jq empty`, and `rg '[[:blank:]]+$'` — PASS: HEAD/tree/origin unchanged; exactly four untracked `docs/architecture/governance/h2-*` deliverables; no other path; JSON valid; no trailing whitespace.
- Gradle/full tests — NOT RUN by explicit bounded-lane instruction.

## 11. Final bounded conclusion

The repository can support a BMF provider without making BMF a second media semantic authority or execution backend. The safe POC is one typed Gaussian blur lowered from final provider-neutral operation semantics to a private BMF graph, with immutable Artifact boundaries, split CPU/GPU implementation identity, locked provider-local runtimes, H1-owned WorkerRuntime/device authority, and empirically calibrated fail-closed cross-runtime conformance. This candidate is ready for an independent acceptance decision; it does not authorize BMF implementation or B0 execution, and it does not assert BMF build success, runtime success, GPU availability, or conformance success. The remaining review gate is explicit independent ChatGPT ACCEPT, followed by separately authorized and successful B0 before B1 or later implementation.
