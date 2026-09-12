# Render job browsing

The normal Operations entry is `/operations/renders`; `/render-jobs` redirects there.
This read-only slice consumes the authorized discovery and job reads accepted in
backend commit `c51a3ee601105fd988347ed70cfc1255fc7cad49`.

## Read flow

1. `GET /api/me/dashboard` supplies the current tenant only; recent Projects are not grants.
2. `GET /api/identity/tenants/{tenantId}/projects` returns the actor-authorized subset.
3. The user explicitly selects a Project. `GET /api/identity/projects/{projectId}` re-resolves it before reading jobs.
4. `GET /api/tenants/{tenantId}/projects/{projectId}/render-jobs` lists returned jobs; `/{jobId}` reads selected details.

All requests use the existing authenticated transport, preserve HTTP status, consume
AbortSignal and validate returned IDs and scope. The five-field job projection is
`id`, `projectId`, `timelineSnapshotId`, `profile`, `status`. All nine exact backend
statuses are supported; unknown aliases or malformed responses fail closed.

Search (ID/profile/snapshot), status filters and deterministic ID ordering apply only
to returned records. Counts are returned-record counts, with no pagination or complete
inventory claim. Refresh is explicit. There are no render, retry, cancel, provider or
Artifact actions; progress, timestamps, failure explanations and Artifact availability
are not inferred from status or other domains.

## Read lifetime and recovery

Queries belong to the authentication/Workspace binding, source, mounted browser,
Project and job. Project changes, route teardown and session retirement cancel and
remove owned queries. Late responses cannot repopulate retired views. Selection is
scoped through the shared SelectionProvider as `RENDER_JOB`.

401 retires the session. 403 clears discovery, selection, list and detail; recovery
requires fresh authorized discovery and explicit Project selection. Project 404 prevents
job reads; detail 404 removes the unavailable row and selection. Other read errors show
a safe message and explicit retry without displaying raw response bodies. No job data
is persisted in browser storage. Project and filter choices reset when leaving the view.

## Implementation and verification

- `frontend/src/api/render-jobs.ts`: authorized Render reader; the existing workspace hook remains for separate legacy Product routes.
- `frontend/src/pages/RenderJobDashboard.tsx`: Operations page, filters and scoped inspector.
- `frontend/src/components/render-jobs/JobDetail.tsx`: actual job fields only.
- `npm run test:render`: transport, lifecycle, failure/recovery and route coverage.

Local acceptance uses the real authenticated backend with isolated synthetic database
records. It does not establish production OIDC or actual render/provider execution.
