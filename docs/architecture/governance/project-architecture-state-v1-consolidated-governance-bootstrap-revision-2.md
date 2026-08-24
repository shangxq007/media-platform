# PROJECT ARCHITECTURE STATE V1 — CONSOLIDATED GOVERNANCE BOOTSTRAP REVISION 2

STATUS=ADOPTED_FOR_REPOSITORY_PERSISTENCE

BASE_E3_B_SHA=224d3a907ce76c79f868d3615c681aa833cff2b2
BASE_E3_B_TREE=6a868801c001b5ffce64a7440de1e1bf2b917fa0
BASE_PARENT_SHA=a17d637f273b9eab402da1ff3a8a4b55924d8cb4

GOVERNANCE_BASE=224d3a907ce76c79f868d3615c681aa833cff2b2

PURPOSE=
Persist previously conversation-adopted architecture and roadmap extensions
without changing milestone numbering or implementation scope. This is a
GOVERNANCE / DOCUMENTATION publication. It is not Roadmap #22 Phase 15
implementation, not Plugin Runtime V1, not FFmpeg Provider implementation,
not Roadmap #23, not production-code refactoring, not a schema migration,
not permission to merge canonical main.

This record is the adoption/publication record. The structured, mutable
indexes live under `docs/architecture/governance/project-state/`; the frozen
implementation evidence chain is the accepted Epoch-3 commit
224d3a907ce76c79f868d3615c681aa833cff2b2 (E3-B) and its ancestors.

## 1. Authority baseline (repository reality at persistence time)

- canonical main = 036f21f7f94f61da92faa2e91934675d024d99e8
  (tree 7a61effeb2840c428cab2705a9f529159fc4e345) — Roadmap #21 final
  review and canonical integration closure record.
- Roadmap #22 branch = agent/roadmap22-executable-task-graph-worker-fabric-decision-recovery
  - E3-B tip = 224d3a907ce76c79f868d3615c681aa833cff2b2 (tree
    6a868801c001b5ffce64a7440de1e1bf2b917fa0), parent E3-A
    a17d637f273b9eab402da1ff3a8a4b55924d8cb4, E2 base
    964271ebdc03037429d2a6821e33edb62558a8b9.
- Chain is strictly linear: 964271eb -> a17d637f -> 224d3a90.
- main is the merge base of the branch; main remains unmerged with Roadmap #22.
- ChatGPT verdict (accepted): ROADMAP_22_EPOCH_3=CLOSED,
  ARCHITECTURE_BLOCKERS=0, ARCHITECTURE_ESCALATION=NONE,
  ROADMAP_22_CLOSED=NO, ROADMAP_23=NOT_STARTED, MERGE_MAIN=NO.

## 2. Canonical memory principles (V1)

REPOSITORY_IS_CANONICAL_PROJECT_MEMORY_AUTHORITY_V1
CONVERSATION_MEMORY_IS_A_CONVENIENCE_CACHE_NOT_PROJECT_AUTHORITY_V1
DISCUSSION_ADOPTION_IS_DISTINCT_FROM_REPOSITORY_PERSISTENCE_V1
CURRENT_STATE_INDEX_IS_MUTABLE_BUT_HISTORY_IS_GIT_VERSIONED_V1
ARCHITECTURE_DECISIONS_HAVE_STABLE_IDS_V1
ROADMAP_STATUS_MUST_REFERENCE_CONCRETE_REPOSITORY_EVIDENCE_V1
DEFERRED_WORK_MUST_BE_EXPLICITLY_INDEXED_NOT_LEFT_TO_MEMORY_V1
VALIDATION_PROJECTS_ARE_FIRST_CLASS_GOVERNANCE_ARTIFACTS_V1
TECHNOLOGY_CANDIDATE_IS_DISTINCT_FROM_ADOPTED_ARCHITECTURE_DEPENDENCY_V1
REPOSITORY_STATE_MUST_DISTINGUISH_ADOPTED_PERSISTED_DEFERRED_AND_POC_V1

Conversation memory exists and is useful as working context; the repository is
the durable authority. Future sessions read the repository first.

## 3. Structured indexes (mutable, machine-navigable)

- current-state.yaml — compact snapshot: SHAs, roadmap status, next step
- architecture-registry.yaml — stable-ID index, families 9A-9AC (198 IDs)
- roadmap-tracks.yaml — 14 cross-cutting tracks (no milestone renumbering)
- foundation-inventory.yaml — 64 technology/foundation entries, one status each
- product-reference-inventory.yaml — 10 product references (REFERENCE only)
- deferred-items.yaml — 38 explicitly indexed deferred/resolved items
- validation-inventory.yaml — 10 first-class validation projects
- README.md — purpose, precedence, update rules, recovery workflow

## 4. Newly persisted architecture families (summary)

- Project memory governance: repository as canonical memory; authority
  precedence per source-of-truth/authority-precedence.md (L0-L7).
- Capability Validation (9V/9W): V1/V2/V3 validation levels; demos as
  first-class artifacts; FIRST_REAL_MEDIA_CUT and GOLDEN_STORY_01《辛公平上仙》
  as golden validation projects; owner/operator as first real tenant.
- Product/Application track (9U): NLE, Canvas, FLOW, AI/Hybrid Film Studio,
  Agent-first, Automation, Template studios over one semantic core; landing
  sequence P0 design system/workspace shell -> P1 NLE -> P2 FLOW -> P3 Canvas
  -> P4 AI studio. Neither implemented nor started.
- Provider plugin/distribution (9N/9O/9P): plugin = implementation packaging,
  never domain authority; logical platform-plugins/ umbrella; leaf module =
  build/deployment unit; same plugin artifacts support bundled and external
  loading; distribution compositions (coreDistribution/fullDistribution;
  creator/filmStudio/server/worker variants are future possibilities only).
- Provider lowering/native IR (9K): ProviderBoundETG -> ExecutableTask ->
  PlanLowerer -> ProviderNativeExecutionPlan -> InvocationSpec[] ->
  RuntimeAdapter -> ExecutionCommand/API request -> ExecutionBackend.
  FFmpeg planned typed lowering: FFmpegPlanLowerer -> FFmpegExecutionGraph ->
  FFmpegCommandCompiler -> typed ProcessInvocation. Phase 15, NOT_STARTED.
- Provider optimization/formal boundaries (9L): legality before optimization;
  three optimizer scopes (Semantic Rewrite, Provider Local Lowering, Global
  Execution #23); formal verification targets shared semantic laws
  (executable laws / Lean4 / Coq), concurrent lease/reservation/generation
  protocols prefer TLA+/PlusCal.
- Provider multi-version strategy (9M): binary version != runtime
  availability; binding pins exact execution contract + implementation
  version; historical executions never rebind; lifecycle candidate states
  ACTIVE/DRAINING/PINNED_ONLY/RETIRED/UNAVAILABLE are NOT-YET-IMPLEMENTED.
- Commercial dogfooding (9Q/9V): commercial foundations stay ACTIVE_PLANNED;
  real external commercialization DEFERRED; self-dogfooding ADOPTED with
  production authorization paths; usage metering must work before external
  commercialization; cost != price != charge != revenue.
- Manual subscription renewal (9R): platform owns subscription lifecycle;
  MANUAL is the primary V1 renewal; auto-renewal, off-session charge, dunning,
  payment retry, complex mandates deferred.
- Hyperswitch/OpenMeter preferred POC (9S): payment orchestration = Hyperswitch
  (bounded provider implementation exists in payment-module), usage metering
  + initial billing mechanics = OpenMeter (NOT integrated); both are providers
  behind platform ports, never commercial domain authorities; Kill Bill /
  Flexprice / Lago remain deferred candidates, NOT rejected.
- Product/competitor references (9X): Premiere, Firefly, Creative Cloud,
  Runway, LTX Studio, Higgsfield, Flow Studio, ComfyUI, Blender, Omniverse —
  reference inputs, never architecture dependencies.
- Open media/scene/native track (9Y): OTIO = interchange adapter (implemented
  boundary), OIIO/EXR/OCIO/ACES = planned/POC, USD/Hydra/Storm/MaterialX/
  OpenPBR/OSL/OpenVDB/NanoVDB/OpenFX/Open RV/Gaffer = reference or deferred;
  JavaCPP/JNI/Panama FFM = native binding mechanics only; no foundation
  library becomes canonical domain authority.
- Deferred-items governance (14): 38 items explicitly indexed with revisit
  triggers; TIMELINE_ARTIFACT_PIN_EXISTENCE_VALIDATION RESOLVED with code and
  gate evidence.
- Validation inventory (15): 10 validation projects with purpose, status,
  required foundations, success criteria, metrics, next triggers.
- Content distribution/publication (16): PublicationPackage concept
  (MasterArtifact, Renditions, Thumbnail, TitleCandidates, Description,
  SubtitleTracks, Chapters, Tags, Provenance, AI disclosure metadata);
  EXPORT_PACKAGE -> MANUAL_PUBLISH initial policy; platform adapters future.
  PublicationPackage is NOT canonical media authority.

## 5. Roadmap #22 phase model (persisted, NOT started)

Closed: Decision Recovery, Epoch 1, Epoch 2, Epoch 3.
Remaining phases (start only after this governance task is reviewed):
Phase 15 PlanLowerer/RuntimeAdapter; Phase 16 artifact staging/materialization
+ fenced completion; Phase 17 sandbox/isolation; Phase 18 FAOF-2 Lean4+Coq;
Phase 19 FFmpeg CPU Native Pull (first real Provider plugin); Phase 20 Intel
VAAPI/QSV; Phase 21 OpenCue POC; Phase 22 remote backend conformance; Phase 23
NVIDIA/cloud Native Pull; Phase 24 candidate freeze/FCV/publication/closure.

Real FFmpeg work is intended to become platform-plugins/provider/ffmpeg ->
provider-ffmpeg.jar, the first real consumer proving Provider plugin
architecture. After that, bounded PLUGIN_RUNTIME_V1 may begin (explicit
PluginDescriptor, typed contribution discovery, plugins/*.jar loading, basic
classloader boundary, compatibility/version validation, registration,
coreDistribution, fullDistribution). No marketplace/hot-reload/remote
catalog/complex dependency solver for Plugin Runtime V1.

## 6. Status ledger (persisted truth)

ROADMAP_21=CLOSED
ROADMAP_22=IN_PROGRESS
ROADMAP_22_EPOCH_3=CLOSED
ROADMAP_23=NOT_STARTED
NEXT_ROADMAP_22_PHASE=15
PHASE_15_STARTED=NO
PLUGIN_RUNTIME_V1=NOT_STARTED
FFMPEG_PROVIDER_PLUGIN=NOT_STARTED
COMMERCIAL_STACK_PREFERRED_POC=HYPERSWITCH_PLUS_OPENMETER
REAL_AUTO_RENEWAL=DEFERRED
FIRST_REAL_MEDIA_CUT=NOT_YET_VALIDATED (product-track; FRMC technical milestone CLOSED 2026-08-15)
GOLDEN_STORY_01=NOT_YET_VALIDATED
MERGE_MAIN=NO

## 7. Supersession

This record is the bootstrap revision 2 adoption/publication record. It
supersedes no frozen contracts. It adds repository persistence for
conversation-adopted decisions. Any future correction to this record is a new
governance record; this file is immutable historical evidence.
