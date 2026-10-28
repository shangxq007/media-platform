# Legacy work disposition and current owners

This bounded OWN01–OWN07 task starts from main509e6aca. It adopts the Owner's OD1–OD3 decisions;
historical archives, G2, the seven thumbnail commits and evidence remain read-only. The task branch
and its final source-bound acceptance/review determine integration; this document grants no merge
permission and does not declare production readiness.

| Work | Disposition | Current owner / evidence obligation |
| --- | --- | --- |
| OWN01 commit scope, durable exact replay, digest/replica and completion fencing | COVERED / ADOPTED | ArtifactCommitService and its durable receipt remain sole commit authority; native completion and GraphRenderLifecycle remain native/Render terminal authorities. NativeOutputPublicationTransaction now encloses Artifact/native completion and exact release under native ownership, nested inside the logical Execution cancellation fence for capability requests. PG rollback/stale-runtime and real HTTP publication verify the boundaries. |
| OWN01 old Comparator/PersistedCompletionIdentity/shadow commit/terminal protocol | REJECTED | No second commit evidence store, terminal state machine or completion authority is restored. Existing receipt and typed native output facts are authoritative. |
| OWN02 catalog, complete canonical candidates, immutable graph inputs and Binding pin | COVERED / ADOPTED | ProviderPluginCatalog → #22 Binding → immutable BoundGraphInputs. Worker rejects absent exact pins and changed contribution metadata; canonical planning/binding negative tests cover ambiguity, identity drift and malformed inputs. |
| OWN02 old Worker Snapshot selection authority/store/DTO/table | REJECTED | No formal consumer/FK depended on wf_provider_candidate_snapshot. It is removed from the pre-release V1; no compatibility DTO or replacement snapshot table is retained. |
| OWN02 distributed candidate source/version/TTL/plugin-binary evidence | DEFERRED_BY_DEPENDENCY | Resume only with a concrete multi-worker source-uniqueness and Runtime/Registration lifecycle contract, snapshot key/version/expiry policy, immutable plugin-binary evidence and an approved Binding evidence attachment. Extend catalog/#22 facts rather than introducing another selector. Single-worker E2E proves none of these distributed properties. |
| OWN03 execution-project publication | COVERED | The authoritative execution project determines publication and access. Deployment overrides and old mismatch semantics cannot replace it. |
| OWN03 unused CapabilityDefinition/old constitution authority changes | REJECTED | No current consumer needs those abstractions. Typed capability requests and existing Capability/Provider owners remain. |
| OWN04 lightweight cover/thumbnail generation | ADOPTED | Two thin HTTP intents share #21/#22, native worker runtime, existing platform_execution_admission/result and Artifact commit. cover_image_task/media_thumbnail_task, dedicated stores/services/workflows/commit scaffolding, duplicate provider registration/SPIs and obsolete task DTOs are retired after real replacement generation/download/retry/cancel proof. No media_task or fake RenderJob is introduced. |
| OWN04 source, provenance, output identity, quota and download | ADOPTED | AVAILABLE source Artifact/digest pins; IMAGE/DERIVED_MEDIA output with COVER_OF/THUMBNAIL_OF child→source edges; scoped idempotency and logical/native generation association. Thumbnail uses the existing quota owner exactly once; Cover does not incur Thumbnail quota. Project Artifact access verifies scope, commit, replica and actual bytes. Existing Render access remains. |
| OWN04 cancellation, uncertain process cleanup and filesystem isolation | ADOPTED | The native sandbox consumes per-execution cancellation without mutable shared bindings. Incomplete cleanup overrides cancellation/timeout and blocks retry/publication while retaining recovery hold. Tests include a real bounded surviving child, exact termination, native staging root/symlink/exclusive-file guards, scoped reuse rejection, and real HTTP cancel/failure/retry. Old wrapper tests are mapped to the replacement owners, not counted as current runtime evidence. |
| OWN05 registration/lease/grant/expiry/readiness invariants | COVERED / ADOPTED | Existing persisted Registration/connection authority serializes takeover. Each production loop has one fresh stable-per-instance UUID; a retired row cannot regain the runtime through a late tick. Old grants are fenced; a local process cannot consume another incarnation's grant. Concurrent/late/expired/disconnected PG cases verify this. The old G2 five-fact readiness model is not imported. |
| OWN06 test scope and ledger semantics | ADOPTED / REJECTED | Class-owned PG18 schemas, fixed controlled test entry, exact owned resources, XML/arithmetic/source identities and truthful ledger meanings remain. PG15/public-schema assumptions, repeated policies and unsupported shared-resource claims are rejected. Existing outbox/claim repair stays with its owner. |
| OWN07 First Render architecture probe | COVERED / ADOPTED | `/api/first-render` remains a thin planning/binding/dispatch adapter. Render creates job plus preparation stage/deadline atomically; synchronous Snapshot/Planning/Binding/save failure uses Render's public lifecycle, and its existing compensation recovers expired preparation after process loss. Begin disarms recovery; late failure cannot overwrite accepted execution. Real service-chain PG failures and actual independent API/WORKER video E2E verify this. No probe-specific status, store, completion authority or cleaner is added. |

Operation is cross-domain intent, including possible Media Asset operations, while Timeline changes
still require Operation. No cross-domain Operation framework or new formal operation type is added.
Generating an image is distinct from selecting an official cover.

Current code and tests supersede the old slice implementation; historical review results are retained
as evidence of those exact earlier sources. In particular, old Cover503 and fixed local-ffmpeg-v1
incarnation are not accepted final behavior. Use the final task report for exact fresh PG/jOOQ/build,
image/video HTTP, failure/cancel/retry, guard, source identity and independent review results. Old
counts157/297/447 do not describe the new code automatically.

Archives/G2/thumbnail history remain preservation material. Their physical deletion requires separate
Owner approval. Multi-worker source evidence and protected runner/UID/cgroup/Neon/BWS deployment
remain explicit dependencies; `DEPLOYMENT_ISOLATION_NOT_VERIFIED` continues.
