# Media thumbnail vertical slice

The canonical capability is `media.thumbnail`; the registered provider is the capability-independent
family `platform.ffmpeg` with implementation identity `ffmpeg.cpu.frame-extract.v1`. Clients submit
only a tenant/project scope, a Media
asset id, a finite timestamp, a bounded image format/size/quality, and an idempotency
key. Physical paths, URLs, FFmpeg arguments, and Storage credentials are never part
of the HTTP contract.

`media.thumbnail` and `media.cover-image` share one capability-neutral provider
(`FfmpegCpuProvider`) and one platform contribution
(`FrameExtractPlatformProvider` / `FrameExtractPlatformRegistration`, contribution id
`media.ffmpeg.frameextract`). The provider declares the capability list and selects the
thumbnail execution profile from the executing `capabilityId`; the worker dispatch is
`FrameExtractExecutionAdapter`.

`V10__media_thumbnail_tasks.sql` owns durable admission and lifecycle state. The
Temporal `ThumbnailWorkflow` persists admission before the activity, retries bounded
provider work, honors cancellation, and commits an IMAGE Artifact (generic
`ArtifactKind.DERIVED_MEDIA`) related to its subject by a `THUMBNAIL_OF` provenance
edge only after readable output and Storage integrity verification; the legacy
`ArtifactKind.THUMBNAIL` is retained for rows committed before the rebuild. Artifact retrieval checks
tenant/project scope, Artifact availability/kind/media type, replica integrity, and
returns a controlled image response.

The frontend route is `/w/{workspaceId}/projects/{projectId}/thumbnails`; it uses
the existing OIDC-bound API client and displays durable status, failure, preview,
and authorized open/download actions. Real FFmpeg/PostgreSQL/Temporal/browser
acceptance remains a verification boundary; unit and compile checks are recorded in
the task handoff.
