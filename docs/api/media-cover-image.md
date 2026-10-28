# Cover image: Artifact-sourced capability request

`media.cover-image@1.0` generates an image from a committed video Artifact. Generating an image and
choosing it as a project's official cover are separate actions; this endpoint performs generation.
It is a thin authenticated intent adapter over the existing #21 planning, #22 binding, native Worker
Fabric, Execution and Artifact owners. It creates no RenderJob or purpose-specific task lifecycle.
Operation may eventually express this intent for Media Assets; no new Operation type or framework is
introduced here. [Execution principles](../architecture/current/execution-architecture-principles.md).

## HTTP contract

`POST /api/projects/{projectId}/cover-images` resolves the tenant from the authenticated context.
The actor needs source Artifact read permission and the existing project `WRITE` permission.
Artifact tombstone/GC permission is not required and is not granted by this endpoint.

```json
{"subjectArtifactId":"source-artifact","timestampSeconds":0.25,"imageFormat":"png","width":160,"idempotencyKey":"request-1"}
```

The source must be an AVAILABLE VIDEO Artifact in that tenant/project. Timestamp is finite and
nonnegative; encoding is `png` or `jpeg`; optional width is16–8192 and JPEG quality1–100.
An unspecified JPEG quality is pinned to85 before planning/fingerprinting. Parameters use the shared
`MediaFrameExtractParametersV1`; callers cannot supply URLs, paths, providers or command arguments.

The response is an Execution Handle: `executionId`, positive `generation`, `state`, nullable
`artifactId`/`failureCode`, and `retryBlocked`. Status is queried at `GET .../{executionId}`.
The existing Execution lifecycle is ADMITTED → RUNNING → COMPLETED/FAILED/CANCELLED, with RETRYING
for explicit failed-execution retry. No Cover/Thumbnail COMMITTING state or task table remains.

`POST .../{executionId}/cancel` and `POST .../{executionId}/retry` require `{"generation":1}`.
Stale generations and conflicting idempotency parameters return409. Retry is allowed only from
FAILED; uncertain physical cleanup sets `retryBlocked` and requires reconciliation. Cancellation
cannot override a completed output. Temporal schedules the work; it does not own these domain states.

## Identity, persistence and publication

Idempotency binds tenant, project, capability and user key. The persisted canonical request pins the
source Artifact/digest and normalized frame/image parameters. Equivalent replay returns the accepted
Execution; different parameters with the same key are rejected. Existing
`platform_execution_admission`/`platform_execution_result` persist request, generation and result
association across restarts. Durable typed outbox dispatch references one Temporal workflow per
execution/generation. Outbox failures remain diagnostics requiring recovery, never successful outputs.

The registered native FFmpeg contribution is shared with graph execution. Provider selection remains
with #22 and immutable ProviderBindingPin inputs; there is no slice registry or fallback provider.
Publication holds the logical cancellation fence and current native attempt fence in one transaction
with Artifact commit, native completion, exact reservation release and successful quota settlement.
A failed transaction exposes no formal Artifact result; durable storage bytes may remain for exact
reconciliation and are never blindly deleted. Cleanup uncertainty retains the native recovery hold.

The result is an IMAGE/DERIVED_MEDIA Artifact with a `COVER_OF` edge from derived image to source,
including request/result digests and native attempt provenance. Tenant/project/source kind/task/native
attempt/generation bind its immutable publication identity. No synthetic cover ID is used as a
RenderJob ID. Cover generation currently incurs no Thumbnail quota operation.

## Authorized bytes

`GET /api/tenants/{tenant}/projects/{project}/artifacts/{artifact}/access` returns a relative authenticated
`.../content` route. The content endpoint verifies project access, AVAILABLE commit state, registered
replica, actual length/digest and media signature before returning bytes (current limit64MiB).
It emits no signed URL, host path, storage key or credentials. Existing Render-scoped download remains.
[Thumbnail](media-thumbnail.md) uses the same owners and access contract.

Real PNG/JPEG generation, HTTP bytes, ffprobe/hash, idempotency, failure/retry, cancellation and negative
access checks run through `scripts/dev/first-render` in newly owned local PG18/Temporal resources.
The evidence identifies exact source bytes; Fake/unit results never replace this real acceptance.
Protected production runners and Neon/BWS permissions remain `DEPLOYMENT_ISOLATION_NOT_VERIFIED`.
