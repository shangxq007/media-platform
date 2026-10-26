# First Render through platform owner contracts

First Render exercises the platform's existing Render planning/binding, Execution, Workflow,
Artifact and Storage contracts. It is not a second render product or a parallel lifecycle.

## Runtime and API

Build both official assemblies with Gradle assemble (Java25 / Gradle9.1 / Boot4.0.4). A protected
launcher supplies the datasource and credentials. Worker uses the ffmpeg-worker profile and polls
only media-platform-tasks; API is Temporal client-only. pg18-integration is an isolated test profile
with real OIDC/RBAC and explicit local storage reads, not production admission. Synthetic BWS stubs
are permitted only in test evidence. Never pass real management credentials to Java/JUnit.

1. Authenticate normally and POST a timeline snapshot via /api/render/timeline-snapshots.
2. POST /api/first-render/plans with its revisionId, extent, MP4 outputFormat and profile.
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
- V2 render_graph_execution binds one immutable Render attempt/generation to the exact stored
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
- Storage owns object bytes, checksum and secure local reads. The local provider moved from the
  application cover-image package to storage-module so API/worker reuse the same implementation.
  Public endpoints never accept physical object coordinates or return absolute filesystem paths.
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
64 MiB, fully checked before response; larger-object streaming is separate future work.

## Migration and deployment boundary

The canonical greenfield migration is now a single V1, containing the accepted graph execution
bindings and scope/native constraints previously validated as V1+V2. The historical accepted
snapshot remains immutable outside this Git workspace. Old two-step and new one-step empty-PG18
schemas and all447 generated jOOQ sources were compared. Never point this baseline at a historical
populated database or rewrite an applied Flyway history. See ../development/canonical-baseline.md.

Dedicated protected runner, cross-UID/executor/cgroup containment and real Neon/BWS scopes remain
separate deployment gates (DEPLOYMENT_ISOLATION_NOT_VERIFIED). This local PG18/FFmpeg product test
requires no real cloud credentials and does not prove production readiness. Historical audit packets
and dirty main workspaces remain untouched; integration requires separate authorization.
