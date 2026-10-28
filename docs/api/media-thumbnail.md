# Thumbnail: thin Artifact capability intent

`media.thumbnail@1.0` uses the same [planning, Execution and Artifact contract](media-cover-image.md)
as cover generation. It has no dedicated task table, repository, workflow state machine, worker,
provider registry or commit service. The sole native FFmpeg contribution is selected by #22.

`POST /api/tenants/{tenantId}/projects/{projectId}/thumbnails` requires canonical authentication,
project WRITE and source Artifact read access. The existing transport field `sourceAssetId` carries
an Artifact ID; it does not refer to another asset identity authority.

```json
{"sourceAssetId":"source-artifact","timestampSeconds":0.25,"imageFormat":"jpeg","width":160,"quality":80,"idempotencyKey":"request-1"}
```

The source must be an AVAILABLE VIDEO Artifact in this tenant/project. Timestamp is finite and
nonnegative, format is PNG/JPEG, width16–4096, JPEG quality1–100 (omitted defaults to85).
The response is the formal Execution Handle, with `executionId`, `generation`, `state`, committed
`artifactId`, `failureCode` and `retryBlocked`. `GET .../{executionId}` queries it.
`POST .../{executionId}/cancel` or `/retry` requires the current `{"generation":1}`; stale control
requests return409. Retry never substitutes another input or bypasses native attempt fencing.

Only COMPLETED exposes the exact committed result. `GET .../{executionId}/image` authorizes the
execution's scope and delegates to Artifact's verified project-scoped byte reader. Artifact's own
project `/access` and `/content` routes are also available; neither accepts filesystem coordinates.
The image is IMAGE/DERIVED_MEDIA with `THUMBNAIL_OF` provenance from derived image to source.
Successful publication settles the existing monthly `media.thumbnail` policy exactly once using
`execution:<executionId>:completion`. Failure/cancellation consumes no completion quota.

The UI route `/w/{workspaceId}/projects/{projectId}/thumbnails` consumes Execution Handles and current
generations. Image bytes use the existing authenticated transport and a short-lived browser Blob URL,
revoked on replacement/unmount; bearer credentials never appear in an image/download URL. Browser
rendering itself is a separate acceptance scope from real backend HTTP/media checks.

Durable request/result association and idempotency use the existing Execution tables. No historical
pre-release task DTO/table compatibility is retained. Real local backend checks are part of
`scripts/dev/first-render`; see its source-bound records for generated/downloaded bytes, hashes,
ffprobe, provenance, native completion and negative cases. Production isolation remains unverified.
