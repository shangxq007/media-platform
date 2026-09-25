# media.cover-image capability

Platform-owned cover-image capability: one capability id, one pinned provider, one execution path,
no fallback provider and no second asset identity. The slice is deliberately minimal — admission,
durable task, sandboxed provider execution, a single canonical Artifact commit and read-back.

## Capability

| Field | Value |
|---|---|
| Capability id | `media.cover-image` |
| Provider id (pinned) | `platform-ffmpeg-cover-image` |
| Provider version | `1.0.0` |
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

`platform-ffmpeg-cover-image` (`CpuFrameExtractCoverImageProvider`) declares its manifest
(capability id, provider id/version, `ffmpeg` toolchain, accepted input formats, `png` output,
timestamp/width/byte/timeout limits, `sandbox-bwrap` trust and runtime requirements). The registry
`CoverImageCapabilityRegistry` fails closed when the pinned provider is absent or when two providers
claim one id — there is no default and no fallback provider.

Execution stays inside `CoverImageExecutionBackend`, which runs the pinned FFmpeg binary inside a
bubblewrap profile (`--ro-bind / / --dev /dev --proc /proc --tmpfs /tmp --unshare-all
--die-with-parent`) with no shell involved and no capability other than `COVER_IMAGE`.

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
| Profiles | `dev,temporal,cover-image-worker` (worker profile applied last) |
| Queue set | exactly `{media-platform-tasks}`; `workflow-process` must be absent |
| Binaries | `ffmpeg`, `ffprobe` (probe) and `bwrap` on the pinned paths |
| Storage | a registered `StorageProvider` bean in the worker role (needed by the digest-verified materializer) |
| Temporal | 1.26.2 or compatible; namespace resolved from `TEMPORAL_NAMESPACE` |
| Database | `platform-app` migrations, including `V22__cover_image_tasks.sql` |

### Profile precedence

Spring Boot replaces a list property with the value from the highest-precedence source that defines
it, so the **last** profile in the active list wins. `application-cover-image-worker.yml` therefore
only removes the base `application-temporal.yml` worker list when the worker profile is applied
after `temporal`.

`CoverImageWorkerApplication` currently declares `.profiles("cover-image-worker", "temporal")`.
Additional profiles are prepended to the active list, so that ordering puts `cover-image-worker`
before `temporal` and lets the base list win — a worker started that way would additionally poll
`workflow-process`. Until that ordering is corrected in the worker main class, the deployment must
pin the queue set with `SPRING_CONFIG_ADDITIONAL_LOCATION` (see
`infra/docker/cover-image-worker-override.yml`), whose config data takes precedence over the packaged
profile resources.

## Acceptance

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
