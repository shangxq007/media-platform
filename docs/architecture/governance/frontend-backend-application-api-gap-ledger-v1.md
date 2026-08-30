---
document_id: FRONTEND_BACKEND_APPLICATION_API_GAP_LEDGER_V1
artifact_type: GAP_LEDGER
authority_class: INFORMATIVE
lifecycle_state: ACTIVE
acceptance_state: ACCEPTED
owner: frontend-platform
retention_class: PROJECT_LIFETIME
status: FROZEN_BASELINE
created: 2026-08-30
---

# FRONTEND_BACKEND_APPLICATION_API_GAP_LEDGER_V1

## Status and decision

**Status:** FROZEN_BASELINE for the accepted F0/F1 tree.

**Decision:** The gaps below are derived from accepted controllers, response
types, and current frontend consumers. They are integration constraints, not
authorization to add endpoints in this task. Temporary frontend behavior is
always a safe projection, explicit unavailable state, or fail-closed action.

The target information architecture is frozen in
[FRONTEND_PRODUCT_INFORMATION_ARCHITECTURE_V1](frontend-product-information-architecture-v1.md).
Inspection/count evidence is in the
[F0/F1 interim report](frontend-product-ia-f0-f1-interim-report-v1.md).

## Context and method

The audit inspected accepted application/controller code under
`identity-access-module`, `entitlement-module`, `operation-module`,
`timeline-module`, `render-module`, `workflow-module`, `observability-module`,
`storage-module`, `worker-fabric-module`, and `platform-app`, then compared it
with registered routes and clients under `frontend/src`.

A gap is recorded only when a target user action needs canonical truth that the
inspected accepted API does not project or command in the required scope. A
frontend component or adapter being absent is not by itself a backend gap.

`Blocking` means the named user action/surface must remain unavailable; it does
not mean F1 IA freeze is blocked. `Nonblocking` means a smaller honest surface
can ship without the projection.

## Summary

| ID | Surface | User action | Classification | Recommended owner lane |
|---|---|---|---|---|
| FB-GAP-001 | Workspace / Project shell | Open a Project within a Workspace deep link | Blocking | Identity / Workspace application |
| FB-GAP-002 | All restricted surfaces/actions | Explain and enforce effective access | Blocking | Capability + Identity/Entitlement/Policy/Quota application |
| FB-GAP-003 | Creative | Query and apply typed Timeline edits | Blocking | Timeline + Operation application |
| FB-GAP-004 | Render / Assets / Operations | List safe artifacts for a scoped render job | Blocking | Render + Artifact application query |
| FB-GAP-005 | Render / Operations | Present typed job state, failures, and allowed actions | Nonblocking | Render / Execution application query |
| FB-GAP-006 | Platform Operations | Inspect coherent execution/runtime/provider operations detail | Blocking | Observability + Execution/Worker Fabric query |
| FB-GAP-007 | Production Management | View and manage production projections | Blocking for Production; nonblocking for shell foundation | Future authorized Production application lane |
| FB-GAP-008 | Render Result | Navigate from output Product to producing job/artifacts/provenance | Blocking for composed result detail | Render Output / Product application query |
| FB-GAP-009 | Workspace Home | Present a coherent recent-work and creation projection without client joins | Nonblocking for Home foundation; blocking for the named cards | Workspace application query |

## Detailed entries

### FB-GAP-001 — Workspace-to-Project scoped resolution

| Field | Value |
|---|---|
| Surface | Workspace/Home and every Project-scoped surface |
| User action | Open `/w/:workspaceId/projects/:projectId/*`, restore a deep link, or switch surfaces without losing scope |
| Required canonical authority | Identity owns Workspace and Project identity, membership, and scope relationships |
| Existing backend owner | `WorkspaceController` exposes create/get/member/group operations; `TenantProjectController` lists Projects by tenant and gets a Project; `MeController` exposes a dashboard Workspace and recent Projects |
| Missing projection/command | A server-authorized resolution/list projection proving the requested Project is available in the requested Workspace and returning safe Workspace/Project context. Existing `ProjectResponse` has `tenantId` but no `workspaceId` relationship. |
| Temporary frontend behavior | Never choose `recentProjects[0]` or `ws-default` as durable context. Resolve only explicit server-known scope; if the Workspace→Project relationship cannot be verified, show a not-available/chooser state and do not load Project data. |
| Blocking/nonblocking | **Blocking** for Project deep links and shared Project shell; Workspace chooser/home can proceed with projections that are actually available. |
| Recommended owner lane | Identity / Workspace application |

Evidence:

- `identity-access-module/.../api/WorkspaceController.java`
- `identity-access-module/.../api/TenantProjectController.java`
- `identity-access-module/.../api/dto/ProjectResponse.java`
- `frontend/src/api/render-jobs.ts` and
  `frontend/src/routes/app/renders/RenderResultsListPage.tsx` currently derive a
  Project from dashboard `recentProjects[0]`.

### FB-GAP-002 — Five-factor effective-access projection

| Field | Value |
|---|---|
| Surface | Every restricted surface and command; Developer Capabilities is the explicit diagnostic consumer |
| User action | Discover a surface, understand why an action is unavailable, or attempt a protected command |
| Required canonical authority | Effective access = capability existence ∩ runtime availability ∩ entitlement ∩ policy ∩ quota, evaluated by backend owners |
| Existing backend owner | `EntitlementController` exposes `/api/entitlements/me/capabilities`, access checks, and export validation; quota, policy, capability/runtime data exist in separate owners |
| Missing projection/command | A typed principal/scope/resource effective-access catalog with distinct factor outcomes, safe reason codes, freshness, and action/capability IDs. The current capabilities response is tier/policy-shaped and does not separate the five factors. |
| Temporary frontend behavior | `CapabilitiesPage` remains an explicit unavailable state. Other actions fail closed on missing/unknown access and still rely on command authorization. No plan/tier or local runtime branching. |
| Blocking/nonblocking | **Blocking** for any action whose availability cannot otherwise be safely returned by its canonical command/query. |
| Recommended owner lane | Capability application plus Identity/Entitlement/Policy/Quota composition |

Evidence: `entitlement-module/.../api/EntitlementController.java`,
`frontend/src/shared/CapabilitiesPage.tsx`, and the zero-count plan-name guard in
`frontend/scripts/frontend-architecture-guard.mjs`.

### FB-GAP-003 — Canonical Timeline query and Operation command boundary

| Field | Value |
|---|---|
| Surface | NLE, Canvas, Storyboard, Screenplay, Agent Studio, and any editing surface |
| User action | Load an editable canonical Timeline and add/move/trim/delete media or apply higher-level edits |
| Required canonical authority | Timeline owns composition/revision; Operation/application services validate and apply semantic mutations against explicit immutable base identity |
| Existing backend owner | Timeline revision/read/compare/restore/merge controllers and Operation domain contracts exist; `TimelineSnapshotController` still accepts editor JSON and `RenderController` exposes several legacy/internal JSON timeline endpoints |
| Missing projection/command | A tenant/Workspace/Project-scoped typed authoring projection and application command accepting canonical media/stream/artifact references, exact `MediaTime`/temporal mapping, Operation intent, and explicit base revision/content hash, returning preview/rejection/new revision. |
| Temporary frontend behavior | Reuse only interaction/presentation mechanics. Keep create/edit submission unavailable; never accept raw URI, persist local Timeline JSON as canonical truth, or call generic patch/sync endpoints from a surface. |
| Blocking/nonblocking | **Blocking** for durable creative editing; read-only revision/review projections may proceed where real APIs are adequate. |
| Recommended owner lane | Timeline + Operation application |

Evidence: `platform-app/.../TimelineSnapshotController.java`,
`platform-app/.../TimelineRevisionController.java`,
`render-module/.../RenderController.java`, `frontend/src/pages/SmokeEditorPage.tsx`,
and `frontend/src/timeline/**`.

### FB-GAP-004 — Scoped redacted artifact summary list

| Field | Value |
|---|---|
| Surface | Project Assets, render result, Render Jobs, and Operations Artifacts |
| User action | List outputs for a render job and request access to one Artifact |
| Required canonical authority | Artifact owns identity/integrity; Render owns job association; access service owns ephemeral delivery |
| Existing backend owner | `RenderController` has an unscoped `/render/jobs/{jobId}/artifacts` list returning `ArtifactInfoResponse` and a scoped `/tenants/{tenantId}/projects/{projectId}/render-jobs/{jobId}/artifacts/{artifactId}/access` descriptor |
| Missing projection/command | A tenant/project-scoped redacted artifact list exposing `ArtifactId`, safe media/format/readiness metadata, and access availability, with no storage URI/bucket/object key/path. |
| Temporary frontend behavior | Render and result pages state that the projection is unavailable. They may request on-demand scoped access only after a safe Artifact summary supplies an `ArtifactId`. |
| Blocking/nonblocking | **Blocking** for artifact lists and artifact actions; render job summary remains usable without artifacts. |
| Recommended owner lane | Render + Artifact application query |

Evidence: `render-module/.../api/RenderController.java` mappings at the artifact
list/access methods, `frontend/src/pages/RenderJobDashboard.tsx`, and
`frontend/src/contracts/app/artifact.ts`.

### FB-GAP-005 — Typed render/execution action and failure projection

| Field | Value |
|---|---|
| Surface | Project render status and Platform Operations render/execution detail |
| User action | Understand current state/failure, see which canonical actions are allowed, and inspect execution linkage |
| Required canonical authority | Render owns job lifecycle; Execution owns attempts/runtime; policy/access owners authorize actions |
| Existing backend owner | Scoped render create/get/list/execution endpoints exist. `RenderJobResponse` is exactly `id`, `projectId`, `timelineSnapshotId`, `profile`, and string `status`; other status-history/metrics endpoints are separate. |
| Missing projection/command | A typed detail projection with canonical status, typed failure/reason, allowed actions, attempt/execution identity, and safe provider/runtime/provenance references where real. |
| Temporary frontend behavior | Show only parsed fields actually returned. Unknown status/action fails closed. Do not infer retry/cancel/completion or worker/provider eligibility. |
| Blocking/nonblocking | **Nonblocking** for basic list/detail status; blocks richer action controls and Operations tabs. |
| Recommended owner lane | Render / Execution application query |

Evidence: `render-module/src/main/java/com/example/platform/render/app/dto/RenderJobResponse.java`,
`render-module/.../api/RenderController.java`, and
`frontend/src/contracts/app/render-job.ts`.

### FB-GAP-006 — Coherent Platform Operations projections

| Field | Value |
|---|---|
| Surface | Operations Overview, Executions, Workers, Devices, Providers, Storage, Incidents, Metrics, Logs, and Provenance |
| User action | Filter and inspect runtime/execution state across the operations shell |
| Required canonical authority | Observability is a query/projection owner; Render, Execution, Worker Fabric, Storage, Artifact, Provider, reservation/capacity, and incident owners retain their semantics |
| Existing backend owner | `ObservabilityController` returns a generic `Map<String,Object>` overview; render admin pages call project-specific metrics/orphan endpoints; remote workers and storage providers have isolated endpoints |
| Missing projection/command | Typed, authorized, paginated operations summaries/details with stable IDs, cross-links, typed states/failures, attempt/runtime/provider identity layers, log/metric availability, and safe provenance. No generic provider-internal graph. |
| Temporary frontend behavior | Migrate existing panels under `/operations` only as honest narrow projections. Hide detail tabs without real APIs. No reconstruction of global operations truth from unscoped job lists or client joins. |
| Blocking/nonblocking | **Blocking** for the complete operations family; narrow Render and Storage panels are nonblocking where accepted APIs are sufficient. |
| Recommended owner lane | Observability + Execution/Worker Fabric application query |

Evidence: `observability-module/.../api/ObservabilityController.java`,
`platform-app/.../remote/RemoteWorkerController.java`,
`storage-module/.../api/StorageController.java`,
`frontend/src/pages/ObservabilityDashboard.tsx`, and current admin pages.

### FB-GAP-007 — Production Management projections

| Field | Value |
|---|---|
| Surface | Workspace/Project Production Management |
| User action | View or update sequences/scenes/shots, tasks, assignments, milestones, deliverables, dependencies, and workload while linking canonical resources/workflows |
| Required canonical authority | A future explicitly authorized production application owner; Project, Timeline, Workflow, Asset/Artifact, Identity, and Review retain their own truth |
| Existing backend owner | Project dashboard, asset/product, review, workflow, and identity APIs expose separate canonical data. No inspected accepted application controller exposes the required production-management aggregate/projection routes. |
| Missing projection/command | Typed production query/command contracts with stable production identities, explicit canonical references, lifecycle, authorization, pagination, dependencies, and workload rules. |
| Temporary frontend behavior | Reserve the semantic routes and keep the surface `PREVIEW`/`HIDDEN` with a precise unavailable state. Do not build local task/shot truth or imply Timeline/Workflow mutation. |
| Blocking/nonblocking | **Blocking** for Production Management functionality; **nonblocking** for route/shell foundation. |
| Recommended owner lane | Future authorized Production application lane; not this F0/F1 task |

Evidence: the exact accepted-controller search for route segments
`production|shots|scenes|sequences|milestones|deliverables|assignments|workload`
returned zero. A broader `shots` search produced only the explicit false
positive `/timeline-snapshots`.

### FB-GAP-008 — Render output Product/job/artifact linkage

| Field | Value |
|---|---|
| Surface | User render result detail and Operations provenance |
| User action | Open an output Product and navigate to its producing RenderJob, Artifact, execution, and provenance |
| Required canonical authority | Render output commit/application query owns the linkage; Product and Artifact retain their identities |
| Existing backend owner | `ProductController.ProductDto` returns product identity/type/status/representation/asset/producer/version/time. `RenderJobResponse` returns no output Product/Artifact linkage. Current frontend explicitly says job linkage is not implemented. |
| Missing projection/command | A scoped render-output result/provenance projection connecting canonical ProductId, ArtifactId, RenderJobId, Execution/attempt identity, readiness, integrity/provenance summaries, and permitted access actions. |
| Temporary frontend behavior | Show Product fields only. Do not join by labels, timestamps, storage paths, or guessed IDs; keep render status/artifacts/provenance unavailable. |
| Blocking/nonblocking | **Blocking** for composed result detail/provenance; Product detail alone is nonblocking. |
| Recommended owner lane | Render Output / Product application query |

Evidence: `platform-app/.../web/assets/ProductController.java`,
`render-module/.../app/dto/RenderJobResponse.java`, and
`frontend/src/routes/app/renders/RenderResultDetailPage.tsx`.

### FB-GAP-009 — Workspace Home composed recent-work projection

| Field | Value |
|---|---|
| Surface | Workspace Home |
| User action | See recent Assets, activity, render/jobs, pinned/favorite Projects, and available templates/recipes without silently selecting a Project |
| Required canonical authority | Workspace/Identity owns authenticated scope; Project, Media/Artifact, Render, Activity, and Recipe owners retain their records and access decisions |
| Existing backend owner | `/api/v1/me/dashboard` returns the authenticated Workspace and recent Projects; Project dashboard/activity and render/Product queries exist only in narrower scopes |
| Missing projection/command | A typed authorized Workspace Home projection (or separately scoped typed queries) for recent safe references, server-supplied availability, pins/favorites, and cursors. It must not expose storage coordinates or require the client to join by labels/timestamps. |
| Temporary frontend behavior | Render real recent Projects from the accepted dashboard only when its Workspace ID exactly matches the route. Show explicit unavailable/unsupported states for the other cards; never select `recentProjects[0]` as context or invent statistics. |
| Blocking/nonblocking | **Nonblocking** for Workspace Home and Projects foundation; **blocking** for the named richer cards/actions. |
| Recommended owner lane | Workspace application query, composed from existing owners under separate backend authorization |

This gap was exposed by the F2/F3 routed Home implementation. It is additive;
FB-GAP-001 still governs Workspace-to-Project resolution, FB-GAP-002 governs
creation access, FB-GAP-004 governs safe Artifact lists, and FB-GAP-006 governs
coherent Operations projections.

## Existing APIs that prevent fabricated gaps

The audit found accepted contracts that F2 should consume before requesting new
backend work:

- tenant-scoped Project create/list and Project detail;
- Workspace detail, membership, groups, roles, and entitlement pool/grants;
- project dashboard/activity/pending/health (subject to proper scope handling);
- Product detail/project list/dependencies;
- Timeline revision list/detail/compare/snapshot/restore/merge/render;
- review list/detail/comments/decisions/merge guard;
- scoped render job create/get/list/execution and scoped Artifact access;
- workflow definition/version/validate/publish and workflow execution/cancel/
  approval;
- identity users/API keys, billing, entitlement, quota, policy, audit,
  notification, worker, storage, and observability endpoints of varying maturity.

Surface/client absence around these APIs is F2 frontend work, not a new backend
gap. API adequacy and authorization still require contract tests before a
surface becomes `AVAILABLE`.

## Safety boundary

This ledger adds no endpoint and changes no backend semantics. It does not
authorize raw coordinates, frontend authority, generic super-admin, OpenCue,
Roadmap #23, production-management domain creation, or remote operations.

Any local, unfrozen, or noncanonical H6/H7 API is classified
`NOT_INTEGRATED` and `BLOCKED_BY_BACKEND_CANONICALIZATION`. Production UI must
remain disabled; at most, a clearly isolated development-only adapter may
inspect it. Such an API is never treated as canonical and never produces fake
success.

## Exact validation evidence

The gap evidence was collected with read-only `rg`/`sed` inspection at the
accepted SHA/tree. Key exact findings were:

- `RenderJobResponse` has 5 fields;
- current registered URL route count is 14;
- the precise production-route-segment controller search returned 0;
- the legacy raw-coordinate source search returned 4 matches in 2 paths;
- the existing frontend architecture guard returned all 11 authority counts at
  0.

The complete commands and final exit statuses are retained in the interim
report.

## Known limits

- This is a source/API inspection, not a deployed OpenAPI compatibility run.
- Generic `Map<String,Object>` and handwritten frontend DTOs require contract
  verification; the ledger does not claim their runtime payloads are stable.
- Security annotations/interceptors were not exhaustively threat-modeled; every
  F2 command still requires backend authorization verification.
- Gap priority does not authorize the recommended owner lane to start.

## Recommended next step

F2 should consume the existing scoped APIs through one typed `platform-client`,
then open separately authorized backend tasks only for ledger entries that block
the selected vertical slice. Start with FB-GAP-001 and FB-GAP-002 because they
govern the shared shell and all later surfaces.
