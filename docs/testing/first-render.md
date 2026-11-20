# First Render E2E scenario

> **Status:** CURRENT scenario definition (2026-11-15). First Render is an automation/E2E scenario,
> **not** a production business type or entry point.

First Render exercises the platform's existing render planning/binding, Execution, Workflow, Artifact
and Storage contracts end to end. It owns no production Controller, orchestration service, database
table, workflow type or idempotency store; the scenario runs through the formal `POST /api/render-jobs`
entry. The retired `/api/first-render/**` probe route is absent from production source and guarded by
[scripts/check-architecture-drift.sh](../../scripts/check-architecture-drift.sh).

Current product paths are named in
[current product execution paths](../architecture/current/current-product-execution-paths.md).

## Runtime and API

Build both official assemblies with Gradle assemble (Java25 / Gradle9.1 / Boot4.0.4). A protected
launcher supplies the datasource and credentials. Worker uses the ffmpeg-worker profile and polls
only media-platform-tasks; API is Temporal client-only. pg18-integration is an isolated test profile
with real OIDC/RBAC and explicit local storage reads, not production admission. Synthetic BWS stubs
are permitted only in test evidence. Never pass real management credentials to Java/JUnit.

The worker also needs an FFmpeg build with `drawtext`, `overlay`, `libx264` and `qtrle`, plus an
explicit font: `FFMPEG_WORKER_FFMPEG_PATH` and `FFMPEG_WORKER_FONT` (see
[runtime contract](../dev/ffmpeg-worker-runtime-contract.md)).

1. Authenticate normally and create the revision through the canonical Operation API
   (`/timeline-operations/initialize` then `/timeline-operations/add-media-clip/apply`); read it back
   from `/api/render/projects/{projectId}/timeline/revisions/{revisionId}/snapshot`. The former
   standalone `/api/render/timeline-snapshots` import route is retired.
2. POST /api/render-jobs with `revisionId`, `extent`, MP4 `outputFormat` and `profile`; an optional
   `Idempotency-Key` header replays the same submission.
3. Query GET /api/tenants/{tenant}/projects/{project}/render-jobs/{job} and /result.
4. /status-history exposes the canonical transitions. /cancel invokes the same authorized Render
   cancellation contract as other render entry points.
5. When status is COMPLETED, use exactly result.finalArtifactId. GET the scoped Artifact /access
   endpoint, then GET its relative accessUrl with normal authorization. This route is an authenticated
   backend download, not a pre-signed grant. Metadata is available from the same scoped Artifact list.
6. Verify Content-Length and SHA-256 ETag against Artifact metadata and inspect the downloaded video.

Both Render workflow starters and cancellation use the same tenant/job-scoped RenderWorkflowIdentity. Transport failures are reported; a queued job with no workflow may still be cancelled safely.

Expected state history is QUEUED → SELECTING_PROVIDER → PROVIDER_SELECTED → EXECUTING → COMPLETING
→ COMPLETED, or an accepted FAILED/CANCELLED terminal state. Output acceptance and completion
projection cannot turn failed/cancelled jobs into success. Do not use SQL to advance state.

## Ownership and consistency

- RenderStateTransitions is the shared Render state-machine/history/typed-status writer. The public
  GraphRenderLifecycle service is Render's transaction boundary for graph binding, accepted final
  output, quota consumption and completion facts. It stores no second copy of job status.
- The single V1 render_graph_execution binds one immutable Render attempt/generation to the exact stored
  plan/graph and final task. Retry creates a new Render Job; a late prior attempt cannot take over.
  Composite foreign keys bind tenant/project/job/final Artifact and native task/attempt/generation.
- Execution's existing assignment/attempt/ownership and wf_completion_event remain the native
  execution success authority. The worker queries current-generation fenced success before
  terminal projection, holding its owner rows through the same Spring transaction.
- Planning's typed final-output declarations select the final task. Publications hash full
  tenant/project/job/task/attempt/generation identity. Intermediate outputs use DERIVED_MEDIA;
  the declared final output uses RENDER_MASTER. No first-row or newest-row heuristic exists.
- ArtifactCommitService remains the sole Artifact commit authority, including durable exact replay.
  Render only references its committed logical identity. Artifact resolves accepted outputs through
  a Render-implemented public query port, never through a Render repository/table import.
- Storage owns object bytes, checksum and secure local reads. The local provider lives in
  storage-module so API/worker reuse the same implementation. Public endpoints never accept physical
  object coordinates or return absolute filesystem paths.
- Temporal owns workflow/activity scheduling, cancellation and retry history. It performs no domain
  SQL. Media execution and terminal projection are separate activities: transient projection faults
  retry without repeating FFmpeg, and the workflow cannot succeed until Render commits completion.

Scoped Render outputs do not adopt a reusable Artifact owned by a different publication/attempt.
The current task still executes and completes through the existing fenced authority. If a semantic
reuse key already has a different winning Artifact, that hint is retained; after exact native
completion the new output may finish without replacing the cache. Pending conflicts and stale
owners still fail closed. Derived cache metadata is not a second task-completion authority.

## Failure and recovery

A lost execute-activity response is recovered only when every graph task has durable current-generation
success and every recovered Artifact belongs to this job. A persisted output binding rejects attempt,
generation or Artifact drift. A completion replay returns exactly the accepted output even after a later job advances native task ownership, without another
quota operation or completion fact. Projection retries continue durably for transient failures;
deterministic conflicts fail and project failure through Render. Cancellation and failure never
consume successful-completion quota. An outbox write fault rolls back final state, accepted reference
and quota together. Late failure cannot overwrite accepted success.

Downloads require tenant/project authorization on every access. Nonexistent scope returns404;
unavailable/corrupt/unsupported content returns a redacted unavailable response. No environment,
path, signed-URL or default-credential fallback exists. Current validated downloads are bounded at
64 MiB and fully checked before the response; the old stdout-truncation path is statically
unreachable. Streaming larger objects is separate future work and is not validated here.

## Migration and deployment boundary

The canonical greenfield migration is a single V1 containing the accepted graph-execution bindings and
scope/native constraints previously validated as V1+V2. The historical accepted snapshot remains
immutable outside this Git workspace. Later owner-approved schema changes require a fresh empty-PG18
run, real jOOQ regeneration and the actual new generated file set; no generated-file count is
permanent. Never point this baseline at a historical populated database, and never rewrite an applied
database's Flyway history.

Schema policy: [Flyway development baseline](../database/flyway-migration-baseline.md). Development
and regeneration: [canonical development baseline](../development/canonical-baseline.md).

## Multitrack NLE with BMF

Run the packaged NLE workbench against an isolated API, Worker, PostgreSQL and Temporal runtime:

```sh
python3 -B scripts/dev/first-render --provider bmf --nle-multitrack \
  --bmf-runtime-root /path/to/verified/bmf-runtime --output /path/to/new/evidence
```

The Chrome scenario uploads two videos and two distinct audio signals, edits the Timeline through
Operations, reloads the saved Revision and submits a formal RenderJob. The evidence correlates the
persisted physical plan and graph digest, Temporal completion, native BMF Worker execution and the
downloaded master Artifact. It checks video pixels and audio frequencies at representative times.

For a configured API and Worker, enable `platform.bmf-provider.enabled` and supply the verified
`platform.bmf-worker.runtime-root` on the Worker. Multitrack render plans select the existing BMF
binding when the multitrack capability is required. The BMF Graph uses native decoder, filter and
encoder nodes, backed by the BMF distribution's bundled libav libraries. When BMF is unavailable,
the normal binding boundary reports the unsupported multitrack plan.
