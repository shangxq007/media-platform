# media.cover-image capability

Platform-owned cover-image capability: one capability id served by a provider family whose provider
identity is independent of any capability, capability-keyed provider selection with no fallback
provider, and no second asset identity. The slice is deliberately minimal — admission, durable task,
sandboxed provider execution, a single canonical Artifact commit and read-back.

## Capability

| Field | Value |
|---|---|
| Capability id | `media.cover-image` |
| Capability contract version | `1.0` |
| Provider family | `platform.ffmpeg` (capability-independent identity; never a capability id) |
| Provider implementation | `ffmpeg.cpu.frame-extract.v1` |
| Provider version | `1.0.0` |
| Declared capabilities | the provider manifest declares a capability **list**; this slice declares `media.cover-image` |
| Provider pin | deployment configuration only (`app.cover-image.pinned-provider`); no provider identity is hardcoded in the registry |
| Operation id | `cover-image:media.cover-image@1` |
| Task queue | `media-platform-tasks` (shared canonical queue; no per-capability queue) |
| Worker name | `cover-image-worker` |
| Provenance | one `COVER_OF` edge, subject → cover, committed through `ArtifactCommitService` |

The cover is **not** a new `ArtifactKind`. It is an existing image Artifact
(`ArtifactMediaType.IMAGE` + `ArtifactKind.DERIVED_MEDIA`) whose cover-ness is recorded in the
provenance graph as `ProvenanceRelationType.COVER_OF` (subject Artifact → cover Artifact). The
Artifact identity stays immutable and is derived deterministically from
`tenant \0 project \0 taskId \0 storageObjectId`, so a retry cannot mint a second identity.

## HTTP surface (API process only)

`POST /api/projects/{projectId}/cover-images` — admits the durable task and starts the Temporal
workflow on `media-platform-tasks`. The API process never executes the provider and never touches
FFmpeg, storage bytes or the sandbox.

`CoverImageController` + `CoverImageService` + `CoverImageTaskStore` are registered by
`PlatformApplication`'s explicit component scan, which includes `com.example.platform.coverimage`;
every worker-only bean of the same package (provider, registry, sandbox backend, materializer, local
object store, commit fence, activities, worker application) is gated on
`platform.runtime.role=WORKER` and is therefore absent from the API process.
`CoverImageApiContextRegistrationTest` boots the real API context and asserts exactly that split.
Admission takes the Temporal client optionally (the default profile runs without a Temporal cluster);
if no client is available the request fails closed with a clear error instead of admitting a task that
could never run.

### Request schema

```json
{
  "subjectArtifactId": "art_...",
  "timestampSeconds": 1.5,
  "imageFormat": "png",
  "width": 640,
  "quality": 80,
  "idempotencyKey": "cover-<your-key>"
}
```

| Field | Type | Required | Rules |
|---|---|---|---|
| `subjectArtifactId` | string | yes | must be an available Artifact inside the requested project scope |
| `timestampSeconds` | number | yes | finite, `>= 0` |
| `imageFormat` | string | yes | `png` or `jpeg` |
| `width` | integer | no | `16..8192` |
| `quality` | integer | no | `1..100` |
| `idempotencyKey` | string | yes | unique per `(tenant, project)`; decides the durable task row |

### Response schema

`201 Created`

```json
{
  "taskId": "cimg_...",
  "status": "ADMITTED",
  "artifactId": null,
  "failureCode": null
}
```

| Field | Notes |
|---|---|
| `taskId` | deterministic from `(tenant, project, idempotencyKey)` |
| `status` | `ADMITTED` \| `RUNNING` \| `COMMITTING` \| `COMPLETED` \| `FAILED` \| `CANCELLED` |
| `artifactId` | present only once the cover Artifact is committed |
| `failureCode` | present when the task failed closed |

`GET /api/projects/{projectId}/cover-images/{taskId}` returns the same `Result` body once the task
row exists. A repeated `POST` with the same idempotency key returns the existing row instead of
admitting a second task.

## Provider

`CpuFrameExtractCoverImageProvider` implements provider family `platform.ffmpeg`, implementation
`ffmpeg.cpu.frame-extract.v1`, and declares its manifest as **provider identity + capability
declaration list** (`capabilities()`, each entry `capabilityId` + capability contract version) plus
the `ffmpeg` toolchain, accepted input formats, `png`/`jpeg` outputs, timestamp/width/byte/timeout
limits, `sandbox-bwrap` trust and runtime requirements.

`CoverImageCapabilityRegistry` resolves **capability → provider** over every registered provider's
declared list:

- a provider that declares several capabilities is indexed under each of them — it is never skipped
  for a capability it declares, and one capability may be served by several providers;
- selection is deterministic and fail-closed: the deployment-pinned provider when it declares the
  capability, otherwise the single declaring provider; an ambiguous capability with no pin, a
  capability with no provider, a duplicate provider identity, or a pin naming an unregistered (or
  non-declaring) provider all fail at construction;
- the pin is deployment configuration (`app.cover-image.pinned-provider`), not a code constant.

There is no fallback provider and no implicit default provider. `invoke(capabilityId, …)` passes the
capability being executed to the provider, and a provider fails closed
(`UNSUPPORTED_CAPABILITY`) when asked for a capability it does not declare.

Execution stays inside `CoverImageExecutionBackend`, which runs the pinned FFmpeg binary inside a
bubblewrap profile (`--ro-bind / / --dev /dev --proc /proc --tmpfs /tmp --unshare-all
--die-with-parent`) with no shell involved and no capability other than `COVER_IMAGE`.

## Platform registration (first-class platform capability)

`media.cover-image` is registered with the platform provider/capability registry, not only held as a
slice-local constant. `CoverImagePlatformProvider` declares the platform metadata and
`CoverImagePlatformRegistration` registers it at platform start-up.

| Element | Value |
|---|---|
| Provider family (`ProviderId`) | `platform.ffmpeg` (capability-independent, model A) |
| Provider implementation (`ProviderImplementationId`) | `ffmpeg.cpu.frame-extract.v1` |
| Provider version / execution-contract version | `1.0.0` / `1.0` |
| Capability (`ProviderCapabilityProfile` / `ProviderExecutionContract`) | `media.cover-image` @ `1.0`, contract range `[1.0, 1.0]` |
| Contribution (`PluginDescriptor`) identity | `media.coverimage.ffmpeg@1.0.0` (hyphen-free contribution id, mirroring `media.transcode.ffmpeg`; not a provider identity) |
| Declared platform boundary | `ExecutableTask` in, `ProviderExecutionOutput` out (the platform's P1 provider-facing boundary) |
| Registered through | `PluginRegistrationPort.registerRuntime(PluginDescriptor)` — the same canonical seam the PF4J provider host uses |
| Role | platform (API) process only; the cover worker has no capability registry and registers nothing |
| Composition catalog | `media.cover-image` is listed by the composition capability catalog (`/composition/capabilities`) on the platform Artifact contract (subject Artifact → cover Artifact) |

Every identity value is derived from `CoverImageContracts`, so the slice still has exactly one source
of truth for the capability id, contract version, provider family and implementation id.

Failure modes stay closed: an invalid descriptor (the platform's own validator) or a duplicate
contribution identity aborts start-up instead of degrading, and the registration retires exactly its
own lease on shutdown. The `CapabilityRegistryPort.findCapabilityImplementations` /
`PluginRegistryPort.findCapabilityCandidates` lookups therefore expose `media.cover-image` for this
provider family.

### What the registration does not claim

The runtime that executes the capability today is the cover worker (Temporal activity → bubblewrap +
FFmpeg sandbox → `ArtifactCommitService`). Routing the capability through the platform
operation-invocation seam is backlog item C2, so this registration declares the provider-boundary
shape (`ExecutableTask` / `ProviderExecutionOutput`) without claiming that the platform executes it
yet: the composition catalog lists the capability but projects it `UNAVAILABLE` until that seam
exists — the same treatment `media.transcode` receives for its un-materialized output. The worker
runtime-support requirement (`WorkerRuntimeSupportRequirement`) is likewise not declared here,
because it belongs to that dispatch path.

## Commit path

`CoverImageCommitService` is one transactional fence:

1. **Idempotent replay** (increment 4 fix): before the commit fence it resolves the cover through
   the canonical `ArtifactCommitService.findByIdempotencyKey(tenant, "cover-image:" + taskId)` and,
   when the canonical adapter delegates replay to the caller's durable record, through the task row
   itself (`COMPLETED` + `artifact_id`). A retry returns the already-committed Artifact id and never
   re-commits.
2. Storage publication through `StorageOutputPort` with `IssuanceIdempotencyKey("cover-image:" + taskId)`.
3. A single `ArtifactCommitService.commit(...)` call carrying the pinned digest and one `COVER_OF`
   provenance declaration. No Artifact row, replica row or `artifact_relation` row is ever written
   directly, and `ArtifactCommitService` is not bypassed.
4. Task completion with the returned `ArtifactId`.

The canonical jOOQ adapter is fail-closed: a re-commit of an existing Artifact identity returns
`ARTIFACT-409-001` ("Artifact already exists"). That fail-closed behaviour is exactly why replay
must happen in the caller (step 1) and is intentionally left unchanged.

## Runtime requirements

| Requirement | Value |
|---|---|
| Process | dedicated worker process, `web-application-type: none`, no controller/security scan |
| Profiles | `temporal,cover-image-worker` (worker profile applied **last**, per `CoverImageWorkerApplication.WORKER_PROFILES`) |
| Queue set | exactly `{media-platform-tasks}`; `workflow-process` must be absent |
| Binaries | `ffmpeg`, `ffprobe` (probe) and `bwrap` on the pinned paths |
| Storage (subject read) | a registered `StorageProvider` bean — the worker composes `LocalObjectStoreStorageProvider` (`app.cover-image.storage.provider-id`, `app.cover-image.storage.root`); its object store holds the subject bytes the materializer reads |
| Storage (cover write) | `StorageOutputPort` (unchanged canonical publication); the commit staging root is `app.cover-image.commit-staging-root`, which defaults to `app.storage.local-root` and **must** be the root the output port resolves relative paths under |
| Execution backends | the worker composes `RuntimeExecutionBackends` from its own `ExecutionBackend` beans, so `TaskCapability.COVER_IMAGE` resolves to the cover sandbox backend (the API-side PF4J composition is not part of the worker) |
| Provider selection | `app.cover-image.pinned-provider` (deployment configuration; required when more than one provider declares `media.cover-image`) |
| Temporal | 1.26.2 or compatible; namespace resolved from `TEMPORAL_NAMESPACE` |
| Database | `platform-app` migrations, including `V22__cover_image_tasks.sql` |

### Worker-role wiring constraints

- The worker does **not** scan `com.example.platform.config` (API/PF4J composition) and imports the
  shared clock configuration explicitly.
- The activity establishes the ambient tenant scope for its own execution
  (`TenantContext.set(tenant)` … restore), because storage publication asserts it.
- Sandbox work/staging roots must live **outside** the host `/tmp`: the bubblewrap profile mounts a
  private tmpfs on `/tmp`, so any host path below it is invisible to the sandboxed provider.
- The canonical `COVER_OF` edge identity is the bounded digest introduced by the platform fix
  `2f75b088` — `sha256(child ‖ 0x00 ‖ parent)` rendered as 64 lowercase hex characters — so it fits
  `artifact_relation.id varchar(64)` for real identities (`art-<uuid>` subjects at 40 characters,
  `art-cover-<uuid>` covers at 46). The digest is deterministic (re-commits stay idempotent),
  direction-sensitive, and the endpoints remain the authoritative facts, stored verbatim in
  `source_artifact_id` / `target_artifact_id`. The previous `child + "-" + parent` concatenation
  overflowed the column and is gone.

### Profile precedence

Spring Boot replaces a list property with the value from the highest-precedence source that defines
it, so the **last** profile in the active list wins. `application-cover-image-worker.yml` therefore
only removes the base `application-temporal.yml` worker list when the worker profile is applied
after `temporal`.

`CoverImageWorkerApplication.WORKER_PROFILES = [temporal, cover-image-worker]` declares exactly that
order, and `main()` applies it, so the worker profile is last and its single-queue list wins: a cover
worker polls `media-platform-tasks` only (asserted by `CoverImageWorkerQueueSetTest`, which also keeps
a negative control showing the base profile alone would poll `workflow-process`).

The deployment keeps `SPRING_CONFIG_ADDITIONAL_LOCATION` pointing at
`infra/docker/cover-image-worker-override.yml` as defence in depth: additional config data outranks the
packaged profile resources, so the queue set stays exactly `{media-platform-tasks}` even if the active
profile order is changed by an operator.

## Acceptance

- `CoverImageCapabilityTest` — vocabulary, capability-independent provider identity, capability-list
  declaration validation, and registry fail-closed behaviour (empty/foreign capability, duplicate
  provider identity, invalid pin, multi-provider ambiguity).
- `CoverImageApiContextRegistrationTest` — boots the real `PlatformApplication` context and asserts the
  API registers exactly `CoverImageController` + `CoverImageService` + `CoverImageTaskStore` from the
  cover package, that the documented route is present, and that every worker-only cover bean is absent.
- `CoverImageProviderCapabilityShapeTest` — model-A shape: one provider declaring two capabilities is
  indexed under both, capability-scoped dispatch passes the executing capability to the provider,
  dispatch never crosses providers, and a provider fails closed for an undeclared capability.
- `CoverImageWorkerQueueSetTest` — reflects the real `WorkerFactory.workers` map and asserts exactly
  `{media-platform-tasks}` with `workflow-process` absent (plus a negative control on the base
  profile).
- `CoverImageWorkerApplicationTemporalRoleContextTest` — boots the real worker context and scans the
  bean graph: no `@RestController`/`@Controller` bean, web disabled, one `WorkerFactory` on the
  canonical queue.
- `CoverImageCommitServiceIdempotencyTest` — DB-backed (Testcontainers) replay proof: the same
  Artifact is returned and the commit fence is never reached again.
- `JooqArtifactCommitServiceCoverOfTest` — DB-backed canonical `COVER_OF` edge, fail-closed
  conflicting digest and fail-closed identity re-commit.

Local runtime stack: `docker-compose.cover-acceptance.yml` (Temporal 1.26.2 + MinIO + Postgres) with
`infra/docker/Dockerfile.cover-image-worker` and
`infra/runtime/verify-cover-image-worker-image.sh`.
