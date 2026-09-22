# Media thumbnail vertical slice

The canonical capability is `media.thumbnail`; the registered provider identity is
`ffmpeg.cpu.frame-extract.v1`. Clients submit only a tenant/project scope, a Media
asset id, a finite timestamp, a bounded image format/size/quality, and an idempotency
key. Physical paths, URLs, FFmpeg arguments, and Storage credentials are never part
of the HTTP contract.

`V10__media_thumbnail_tasks.sql` owns durable admission and lifecycle state. The
Temporal `ThumbnailWorkflow` persists admission before the activity, retries bounded
provider work, honors cancellation, and commits an IMAGE/THUMBNAIL Artifact only
after readable output and Storage integrity verification. Artifact retrieval checks
tenant/project scope, Artifact availability/kind/media type, replica integrity, and
returns a controlled image response.

The frontend route is `/w/{workspaceId}/projects/{projectId}/thumbnails`; it uses
the existing OIDC-bound API client and displays durable status, failure, preview,
and authorized open/download actions. Real FFmpeg/PostgreSQL/Temporal/browser
acceptance remains a verification boundary; unit and compile checks are recorded in
the task handoff.
