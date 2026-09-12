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

#### Frontend recent-project discovery clarification (UXW1-001; 2026-09-08)

This append-forward clarification belongs to `FRONTEND_WAVE2_NEXT_PLANNED_FEATURE_IMPLEMENTATION_V1`; it does not change the historical backend findings above or authorize a backend lane. Linked recent-work composition gap: FB-GAP-009. No new backend requirement ID, endpoint, server DTO or Operation key is established.

| Field | Bounded consumer requirement |
|---|---|
| User scenario / consumer | Find a recently projected Project by name/description, filter opaque projected status, sort names locally and inspect its projected summary in `frontend/src/product/projects/ProjectBrowser.tsx` on the existing Workspace Projects route. Opening/creating a canonical Project is excluded. |
| Query semantics | Read a bounded recent-project snapshot. Local filtering/sorting applies only to returned items, not a full Project inventory or server search. Inspection is local presentation of the same item, not a canonical detail fetch. |
| Identity / scope | A future authenticated application source must bind principal, tenant, session generation, explicit Workspace and request identity. Workspace membership and destination-resource authorization remain server-owned; neither a recent-item entry nor a local fixture grants them. Logout, changed principal/tenant/session/Workspace and replaced source retire pending work. |
| Proposed input / output (UNAGREED) | Frontend consumption proposal: scoped request identity; existing ProjectSummary-shaped safe id/name/description/status/createdAt, optional matching tenant, explicit complete/limited recent-snapshot metadata and echoed request/scope. The implementation source is the exact frontend proposal, not an accepted server DTO. No raw transport, storage coordinates or secrets. |
| Permission / availability / errors | Consume established EffectiveAccess status/reason/factors, preserving unknown versus denied versus unavailable/unsupported/failure. A simulated projection is explanatory only. Wrong-scope, duplicate, malformed or stale successful responses fail closed; failed refresh cannot masquerade as empty or retain sensitive stale detail. Server explanations and item strings remain opaque content. |
| Pagination / concurrency / persistence | No invented cursor, total or global completeness. Any future traversal needs separately agreed query semantics. Abort and request-generation checks suppress late results even if a source ignores cancellation. No mutation, client canonical persistence or localStorage Project database. |
| Current frontend / mock boundary | The bounded Projects consumer accepts an explicitly injected application source, with an explicit localhost-only simulated fixture for frontend validation. Ordinary unconfigured behavior is unavailable; no real adapter is silently inferred from the older dashboard wrapper. WorkspaceHome and its existing query remain unchanged. This intentional Projects-route transition replaces legacy session-unscoped dashboard cards; it does not claim that the dashboard endpoint itself is absent or newly disproved. |
| Future limited integration acceptance | Pin the accepted frontend tree and one separately accepted backend build/adapter. Use one principal, tenant, Workspace and two controlled recent Projects plus denied/wrong-scope fixtures. Query and inspect one result; verify exact scope, limited-result labeling, failure clearing, logout/session-change cancellation and no Project-open/canonical mutation. No notification, Timeline or whole-platform readiness prerequisite. |

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
| User action | Discover/filter/sort Render work, inspect source-supplied progress/times/failure/task/attempt/version relationships, and inspect safe Artifact metadata availability without assuming Artifact read permission |
| Required canonical authority | Render owns job lifecycle; Execution owns attempts/runtime; policy/access owners authorize actions |
| Existing backend owner | Scoped render create/get/list/execution endpoints exist. `RenderJobResponse` is exactly `id`, `projectId`, `timelineSnapshotId`, `profile`, and string `status`; other status-history/metrics endpoints are separate. |
| Missing projection/command | A separately agreed Project-scoped query for coherent Render/source/task/attempt/progress/failure/version/time/completeness relationships, plus independent Artifact-target access. Action commands, provider/runtime detail, and canonical writes remain separate and unavailable. |
| Temporary frontend behavior | A strict FRONTEND UNAGREED read projection shows only supplied safe fields and explicit links. Artifact collection availability does not authorize item metadata: only explicit inspectable metadataAccess emits name/id/type/availability/version/taskId; denied/unknown/unavailable/stale items emit a generic state-only placeholder. Restricted backend responses MUST trim protected fields; DOM omission is not a confidentiality boundary. Unknown literal status remains visible but never implies success/failure/action. Invalid progress is not clamped; no progress/ETA/order/history is synthesized. |
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
| Temporary frontend behavior | Keep the surface `PREVIEW`. The V6 frontend consumer defaults unavailable and accepts only an explicitly injected principal/tenant/session/Workspace/Project-bound source; its isolated fixture is opt-in and read-only. It displays only strict supplied Scene/Shot fields and explicit relationships, never local task/shot truth or Timeline/Workflow mutation. |
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


## Wave2 notification integration observations — append-forward, source-only

Task `FRONTEND_WAVE2_FOCUSED_REVIEW_UX_CONVERGENCE_V1` authorizes this bounded
addition. Historical F0/F1 entries and their status remain unchanged. These
entries compare frontend implementation tree `43038f740997d0ebb3a8c67f293da7edab563fdb`
with backend Git-object reference `86d6aef94fd5e58da552e97c11473cff6eca734e`
(the observed local origin/main, not a claim of newest remote or parallel backend candidate).
No active backend worktree, notification endpoint, provider test, browser permission
or notification credential was accessed. Full source/blob/line provenance is in
the external task package `inventory/NOTIFICATION_CAPABILITY_INVENTORY.md`.

These are four linked consumer requirements, not eight separate work items.
The corresponding detailed rows are in
`frontend/governance/BACKEND_ENABLEMENT_REQUESTS.tsv`.
No entry authorizes backend or notification UI implementation; all are
**nonblocking for this focused read-only Review acceptance**. Existing
FB-GAP-002 governs shared authorization expectations. Missing frontend pages
alone are not classified as missing backend capabilities.

### FB-GAP-010 — Read a durable user inbox, unread count, and mark one/all notifications read

| Field | Source-confirmed observation / integration requirement |
|---|---|
| Consumer record | UXW2-NTF-001 |
| Existing contract and exact gap | Axios /api/v1 wrappers expect paginated items,total,page,size,unreadCount; reference /api/me/notifications/inbox accepts limit and returns List; read-all service has no inspected Controller mapping; single-read returns ordinary error map on missing ID; legacy /me/notifications is a stub |
| Desired bounded alignment | One accepted scoped inbox request/envelope/read-state contract; align existing frontend wrapper rather than invent pagination totals |
| Permissions / failure behavior | Authenticated principal-owned inbox with explicit tenant isolation, exact read outcomes and supported traversal/limit; reject missing/denied without fake success |
| Mock boundary | This task adds an explicit in-memory simulated inbox consumer/fixture; All/Unread/detail, count and completeness, confirmed read operations, partial/failure/denied/unknown and asynchronous context handling are frontend acceptance scenarios only. No server persistence, real authorization, delivery or cross-device synchronization. Ordinary path remains unavailable/unknown and does not call known-mismatched notification endpoints. |
| Integration acceptance | Future contract/TCP tests bind exact client/server paths including prefix, envelope, params, unread/read-all behavior, missing/denied outcomes and cross-user/tenant isolation |
| Blocking disposition | OPEN — BLOCKS_REAL_NOTIFICATION_INBOX_INTEGRATION; explicit frontend simulation does not resolve this gap. No backend lane assignment or implementation authorization. |

#### Notification Inbox frontend consumer continuation

Consumer: `frontend/src/product/notifications/NotificationInbox.tsx`, mounted once in the shared `components/app-shell/AppShell.tsx`. Proposed consumption boundary: `product/notifications/types.ts`; explicit unavailable adapter and opt-in in-memory fixture are separate. This is **not an accepted server contract**.

The consumer separates opaque ID/content and confirmed read state from optional authoritative inbox-wide unread counts, complete/limited list snapshots, supported single/read-all operations, data origin, and safe related targets. Unknown counts never mean zero; loaded unread items never establish an inbox-wide total. No cursor or pagination behavior is inferred. Read-all requires an explicitly inbox-wide contract, not loaded-page updates. Result validation includes exact principal/tenant/session/access context, request ID, filter/operation and notification ID; malformed success and error maps fail closed. Shared media Selection and project routes are not inbox ownership.

Real integration acceptance remains outstanding:

- Align the complete API prefix and response envelope with the actual adapter; legacy `/me/notifications` remains unestablished as a durable inbox.
- Establish traversal/completeness, count availability and authoritative scope without invented totals/cursors; prove inbox-wide read-all has an actual usable HTTP endpoint.
- Persist exact single/read-all outcomes; distinguish partial/failure/denied/not-found and successful-HTTP error maps; refresh from authoritative projection and do not retry mutations implicitly.
- Supply an authenticated principal-owned authorized projection and observable logout/session/principal/tenant changes; reject non-disclosing cross-user/tenant access. Existing FB-GAP-002 still governs permission requirements; no new permission keys or local authentication system.
- Declare optional Workspace scoping explicitly. Safe supported targets require tenant/resource/Workspace authorization and missing/deleted/denied semantics at the destination; notification receipt never grants business action authority.
- Run real client/server contract and TCP integration tests plus persisted readback and cross-context races. Fixture tests/browser observations cannot satisfy these checks.

UXW2-NTF-002..004 / FB-GAP-011..013 remain future settings/admin/delivery work, unchanged by this consumer task.

### FB-GAP-011 — Configure notification subscriptions, preferences, and channel bindings

| Field | Source-confirmed observation / integration requirement |
|---|---|
| Consumer record | UXW2-NTF-002 |
| Existing contract and exact gap | Binding create returns three fields not full binding; subscription update returns three fields and defaults can contain null; batch returns List not results/errors; preference GET/PUT omit declared identities/full fields and Partial updates can default flags to true |
| Desired bounded alignment | Align existing preference/subscription/binding DTOs and update/partial-failure semantics before any UI integration |
| Permissions / failure behavior | Principal-scoped current-user settings; backend controls critical-event rules and channel validation; defined non-disclosing denied/validation/missing outcomes; public DTO must not leak destination secrets |
| Mock boundary | No settings UI or transport calls; design/contract candidate remains candidate |
| Integration acceptance | Future contract tests verify every request/response/nullability/update-preservation rule, batch failure semantics, critical restrictions, destination redaction and ownership; no live delivery in frontend mocks |
| Blocking disposition | BLOCKS_NOTIFICATION_SETTINGS; nonblocking for focused Review; no backend lane assignment or implementation authorization |

### FB-GAP-012 — Inspect/configure notification event definitions and delivery records; prepare explicit administrative notification input

| Field | Source-confirmed observation / integration requirement |
|---|---|
| Consumer record | UXW2-NTF-003 |
| Existing contract and exact gap | Frontend event-definitions/delivery-logs/providers differ from backend events/deliveries/provider-status; list/object/metrics envelopes differ; detail ignores notificationId; publishEvent sends type/tenantId but backend requires eventType/subjectId |
| Desired bounded alignment | Accepted admin event/delivery queries and exact target/publish input contract; no speculative announcement workflow |
| Permissions / failure behavior | Server-enforced administrator and tenant/resource scope; explicit filtered IDs and pagination capabilities; preserve typed forbidden/not-found/unsupported failures; existing FB-GAP-002 applies |
| Mock boundary | No admin notification UI or calls added; absent UI alone is not an endpoint gap |
| Integration acceptance | Future contract and authorization tests resolve route/payload/envelope differences, exact-ID detail filtering, non-disclosing cross-tenant denial and explicit target validation; separate authorization for announcements/targeting |
| Blocking disposition | BLOCKS_NOTIFICATION_ADMIN_INTEGRATION; nonblocking for focused Review; no backend lane assignment or implementation authorization |

### FB-GAP-013 — Receive a real email/SMS/webhook notification or verify/test/retry a bound channel

| Field | Source-confirmed observation / integration requirement |
|---|---|
| Consumer record | UXW2-NTF-004 |
| Existing contract and exact gap | Email/SMS/Webhook providers return SENT without external transport; binding verify writes VERIFIED without challenge; test only checks/audits and Controller says TEST_SENT; retry only returns RETRY_QUEUED; Novu transport code exists but live integration unestablished |
| Desired bounded alignment | Truthful simulated/accepted/sent/delivered/failed boundaries and separately authorized real delivery/verification/retry integration |
| Permissions / failure behavior | Authenticated scoped verified destination and explicit delivery/test authority; no browser/Agent escalation; real failures cannot become empty or success; no secret exposure |
| Mock boundary | Local provider SENT and persisted MOCK are simulation, not external delivery; this task sends nothing |
| Integration acceptance | Future backend-owned controlled integration validates actual transport/challenge/attempt and readback, authorization, failure/retry semantics, destination safety and simulated labeling; actual external tests require separate consent |
| Blocking disposition | BLOCKS_REAL_NOTIFICATION_DELIVERY; nonblocking for focused Review; no backend lane assignment or implementation authorization |


## V2 bounded Operations Render consumer — FB-GAP-005 / FB-GAP-006 clarification

This task implements only explicitly Project-scoped read-only summary discovery under the existing Operations route. Existing scoped render list/detail APIs are not claimed missing. Consumer: product/render-browser/RenderBrowser.tsx; existing RenderJobSummary five-field schema remains the projected model. No render lifecycle or execution model is created.

- Proposed adapter input: principal/tenant/session/project scope and request identity; output: matching scope/request plus bounded summaries (id, projectId, timelineSnapshotId, profile, status) and explicit complete/limited metadata. This is UNAGREED frontend consumption vocabulary, not an accepted backend DTO, endpoint or Operation.
- Existing EffectiveAccess governs availability/reasons. Real-origin must consume SERVER projection; unknown/denied/unsupported/unavailable/error remain distinct and no content or fixture fallback follows real failure. Every item must belong to the requested project; no guessed/default Project, global client join, timestamps-as-identity or inferred actions.
- Cancel, unmount, principal/tenant/session/project/source/access change retire old pending responses, even valid successful old replies. Search/filter/sort/inspect apply only to the returned bounded snapshot; no invented pagination/total, polling, persistence or canonical command.
- Ordinary unconfigured route is unavailable. Explicit localhost simulation exercises frontend behavior only. Storage and legacy render consumers unchanged. Artifacts, retry/cancel-job, output access, provider/runtime details and global Operations remain excluded under existing FB-GAP-004/006/008.
- Future one-path integration: pin frontend final tree/build and separately agreed backend version/adapter; controlled principal and explicit tenant/project, valid summaries plus denied/error/late old-response cases. Permit only the agreed read query and local inspection; zero writes/artifact access. No other frontend/backend program readiness prerequisite.

No new backend capability requirement ID is created. The existing FB-GAP-005 is linked in BACKEND_ENABLEMENT_REQUESTS.tsv for this consumer; full real adapter alignment remains unestablished.

### V7 Render observability continuation — FB-GAP-005 remains open

V7 extends the existing `product/render-browser` and `/operations/renders` consumer in place. It does not add a Render center, route, permission registry, endpoint, backend DTO, provider/worker call, or requirement ID. The inspected real `RenderJobSummary` still supplies only `id`, `projectId`, `timelineSnapshotId`, `profile`, and a known status schema; it cannot truthfully represent the requested coherent observability view. The smallest richer interface therefore remains explicitly **FRONTEND UNAGREED**.

That proposed receipt echoes authenticated `principalId`, `tenantId`, `sessionId`, explicit `projectId`, and request identity. A successful result supplies a bounded/complete current snapshot with version, freshness, source update time, and source-supported status filters; Render/source identity, optional name/version/source times/progress; optional related task; optional explicit ordinal attempt records with parent/retry links; safe failure summary/code/time; and optional Artifact metadata states/records with explicit task links. Unknown source status is opaque and never classified as success/failure or action availability. A percentage is derived only from finite nonnegative values with a positive total, equal supplied units, and value within total; otherwise the UI shows invalid or partial supplied progress without clamping.

Real adapters are ready only when the host explicitly binds its separately agreed read key to the matching `SERVER` EffectiveAccess entry. Generic `surface.operations.view`, a hardcoded frontend proposal label, development provenance, missing/malformed identity, or mismatched access binding fail closed before request. Ordinary route behavior is unavailable and sends no request. Simulation is possible only through an explicitly injected, complete identity-scoped test fixture; the former URL fixture switch is removed. Failed real reads never fall back to fixture data.

Receipts reject mismatched ownership, foreign Project items, duplicate Render/attempt/Artifact IDs or attempt ordinals, invalid source times, unsafe credential/private-path/raw-trace failure text, excess bounds, orphan/self/future attempt links, and Artifact/attempt links to a missing or different task. Invalid relationship and general invalid results are distinct. Source/access/principal/tenant/session/Project/adapter/Selection-owner replacement, cancel, unmount, and generation change abort and retire old reads; successful late replies cannot restore content. StrictMode re-registration remains usable while explicit retirement clears ready or pending data.

Only local ID/name search, source-supported literal status filter, stable name/ID sort, focusable reset, read-only detail and current-snapshot inspection are implemented. Filters and reasonable list position survive refresh/retry; shared dialog close returns focus. Missing, partial, empty, bounded, denied, unavailable, unknown, stale, inspectable-metadata, detail-not-found, no-match, and empty-snapshot states remain distinct. Frontend fetch time is separately labelled and never substitutes for source time. No polling, timer, animation, fake total/pagination, generated attempt history, arbitrary URL/storage/signed URL, Artifact download/open, render submit/cancel/retry/rerender, delete/publish, Workflow/Timeline write, notification, billing, quota, or canonical mutation exists.

Artifact collection `artifacts.state=available` only permits collection presentation; every item still requires explicit `metadataAccess=inspectable` after outer EffectiveAccess, host binding, scope/request identity, schema and relationship checks. Only then may its supplied name, id, type, availability, version and taskId appear. Item denied/unknown/unavailable/stale states produce generic localized state-only placeholders, with no protected values in visible text, title, aria-label, data attributes, links or hidden DOM. Internal React keys may retain item identity. Missing or invalid metadataAccess and invalid task relationships remain invalid responses; no default-to-inspectable conversion exists. A task ID may independently appear in an authorized task/attempt section; that does not authorize its disclosure in a restricted Artifact subtree.

Refresh clears the prior snapshot before reading. Same-ID inspectable-to-denied/unknown/unavailable/stale transitions remove old metadata, including after reopening detail; unavailable/stale/error/cancelled reads cannot fill from cache. Late aborted or superseded success cannot restore it; only a new explicit valid inspectable receipt can. Principal/account identity (principalId), tenant, session, Project, access, binding, adapter and Selection-owner changes clear details and abort/retire old reads. The proposed scope has no separate accountId field.

Restricted backends **MUST trim protected metadata from responses** according to authenticated item access. Frontend DOM omission is **not a confidentiality boundary**: this unagreed defensive consumer schema can still accept protected fields on restricted items, so those bytes would remain observable in transport/runtime. A real backend contract must agree a trimmed response and its frontend adapter representation before integration. This correction establishes neither that contract nor real authorization enforcement.

Artifact metadata inspection never grants Artifact content read. A future open action requires both an existing typed application route/type and an independently verified current target-access receipt; neither is established here. One future bounded integration may pin one frontend tree/build and one separately agreed backend Project-read adapter, use controlled authorized and denied identities plus current/bounded/stale/error projections, and validate zero writes. Include mixed inspectable/restricted items in an available collection, all four restricted states, same-ID allow-to-restricted transitions, late old allow after denial, valid new allow, refresh failure/cancel, scope/owner/access retirement, backend response trimming, invalid item access/relationships and independent task metadata. Independently test any subsequently authorized Artifact destination. This is not a full-platform readiness prerequisite and does not authorize backend work.


### V3 / V8 Workflow local-sketch consumption note — existing UXW1-002 / FB-GAP-001/002

This task completes the plan's local arrangement of seven node categories, not backend Workflow definition editing or execution. No new endpoint, permission key, DTO or requirement ID is accepted. Existing Workflow application APIs remain unwired; FB-GAP-002 still governs any eventual real access. FB-GAP-003 remains Timeline-specific and is NOT assigned Workflow authority by this note.

Current consumer (V8): route-owned disposable category cards with bilingual creation guidance, shared Selection inspection, correctable validated title editing, pointer/keyboard placement, and target/lifetime-bound single-card deletion and confirmed reset. Existing SelectionScope workspaceId/projectId/surfaceId and owner lifetime delimit local state. Project context/tenant changes and the existing workflow.invoke EffectiveAccess projection retire the Workflow shell owner; missing or denied canonical access never grants invocation, and local title/position changes are not server writes. Empty means an empty locally authored sketch; its explicit local card limit is not a server limited-result projection. There is no Workflow loading/retry/failure network simulation or request envelope. Inherited shell dashboard/auth bootstrap remains separate.

V8 attempt-03 adds the Owner-authorized subscribeOidcSessionRetirement export in frontend/src/auth/oidcClient.ts. Existing native UserManager loaded/unloaded/access-token-expired/signed-in/signed-out/session-changed notifications and signOutOidc initiation synchronously replace the Workflow shell/Selection owner; retained editors and confirmations cannot affect the next sketch. Unconfigured subscription is a no-op and cleanup removes all listeners. No async user hydration can restore drafts: late UserLoaded and same-principal renewal conservatively retire again, and signout failure does not restore prior content. No tokens/full User objects enter keys, DOM or logs; storage hints are not authority. Mock-native tests establish this frontend boundary, not real authentication or remote changes the current SDK configuration does not emit. Auth settings and session monitoring are unchanged; real backend identity/access/version integration remains unestablished.

UXW1-002 remains a proposed optional presentation persistence dependency; V8 does not implement it or create process truth. Scoped Workflow persistence and frontend read/write/version contracts remain unestablished; reuse FB-GAP-001/002, while FB-GAP-003 stays Timeline-specific. Any future representation would need exact layout owner/resource/version, authorized safe references, typed non-disclosing failure and concurrency handling, with local fields separate from canonical process semantics.

At most one future integration considered: explicitly pin frontend tree/build, backend version and accepted Workflow-version read adapter, controlled authenticated identity/Project/version and data. Read one authorized definition/version into a local arrangement projection; allowed requests are only that agreed read, no writes. Denied/unknown/error must clear unavailable content; owner/version/permission changes retire requests and reject stale results; complete/limited meaning must be server-agreed, not inferred. No integration performed now and no whole-platform readiness gate introduced.

### V4 Commands shortcut remapping — no backend dependency for the bounded flow

The existing IA remapping requirement is implemented as local shell UI only. Current consumer is AppShell plus the existing commandRegistry; the one editable command is navigation.command-palette.open. Its session binding contains no canonical reference, request envelope, loaded resource or permission grant. No adapter, fixture fallback, HTTP call, persistence or integration is introduced for remapping.

Existing UXW1-002 discusses optional Project-scoped layout/presentation persistence; it is not silently broadened into an account/global shortcut-preference contract. That record now explicitly separates the V4 local flow. New backend requirement IDs: 0. Persisted shortcuts remain out of scope and unavailable; no useful real-integration scenario is required for a synchronous local keyboard setting. Existing FB-GAP-002/003 and canonical command availability remain unchanged.

If persistence is separately proposed later, ownership/scope, controlled principal, allowed preference fields, authorized reads/writes, non-disclosing denied/validation/conflict failures, stale-context retirement and readback acceptance would need an agreed contract first. This is not such a contract, and whole-platform readiness is not a prerequisite for the current local interaction.


## V5 Canvas local-history disposition — no real integration

FRONTEND_WAVE2_NEXT_PLANNED_FEATURE_IMPLEMENTATION_V5 reuses UXW1-002 (optional scoped presentation layout persistence). Undo/redo of current Canvas titles/positions is local in-memory presentation recovery, bounded to 50 edits; no network, persistence, permission grant, new backend requirement ID or agreed endpoint/DTO. Camera, Selection and canonical revisions/Operations are not history targets. Local history clears with owner/context retirement or unmount.

UXW1-002 still requires any eventual persisted layout to be owned by the authenticated workspace/project, versioned separately from canonical references and checked for access/concurrency. Denied/unknown/unavailable, malformed and stale-context/version outcomes must not restore another owner's data or imply save success. Future acceptance must pin frontend build/tree and backend version, use controlled authorized and denied identities, restore only approved local labels/positions, reject stale or cross-context layouts, and read back actual persisted state. This is a proposal, not a contract or V5 integration. No dependency on completion of EP19 or the entire backend program is imposed.


## V6 Project Scene/Shot read-only consumer — FB-GAP-007 remains open

The V6 consumer uses the existing FB-GAP-007 Production Management gap and existing FB-GAP-001/002/004/008 boundaries for Project relationship, effective access, resource projection, and safe referenced-resource inspection. It creates no new backend gap ID, endpoint, permission key, DTO, canonical Scene/Shot authority, or write operation.

The smallest **FRONTEND UNAGREED** interface contains an echoed `principalId`, `tenantId`, `sessionId`, `workspaceId`, `projectId`, request identity, supplied Scene and Shot stable IDs plus optional name/description/status/version, explicit Scene-ID/Shot-ID relationships, Project-scoped safe shared references, and `complete` or `bounded` snapshot metadata with a required projection version. Unknown, denied, unavailable, unsupported, invalid, error, and version-stale results disclose no data. Duplicate/ambiguous IDs, duplicate or orphaned relationships, Shots without exactly one supplied Scene relationship, foreign Project data, unsafe reference kinds, mismatched receipts, and excess bounds fail closed.

V6 correction: no fixed real-server permission/query literal is imposed. A real host must explicitly bind its separately agreed read-projection key to the matching `SERVER` EffectiveAccess entry; a mismatched binding, development provenance, or generic `surface.production.view` visibility remains unknown/fail-closed. Fixture access uses a separately labelled test-only unagreed key. All five required identity/scope fields reject empty values and whitespace/control characters without trimming or reinterpretation before the adapter can be called.

The current implementation performs only local search/status filter/stable sort/reset and read-only detail/reference inspection. Reset remains a stable focus target whenever populated or empty filter results are shown. Missing, supplied-empty, empty snapshot, no-match, no-related-Shot, and bounded snapshot states remain distinct. It infers no duration, progress, ownership, order, assignment, count beyond the loaded snapshot, external URL, storage path, or permission. Source, identity, access, InteractionStore owner lifetime, context, generation, cancellation, unmount, and projection-version changes retire old content; only a fresh owner after `pageshow` rebinds. No request result can create a canonical or persistent write, and this is not physical bfcache qualification.

One proposed future integration only: pin one frontend tree/build and one separately agreed frontend/backend controlled Project-read adapter; use controlled authenticated authorized and denied identities, exact Project relationships, error and version-stale responses, and zero writes. Validate strict envelopes, access nondisclosure, cancellation/context retirement, and safe referenced-resource checks. This is not actual integration and does not authorize a backend lane or depend on whole-platform completion.


## V8 continuation — Workflow session continuity contract remains conditional

The local Workflow correction reuses UXW1-002 and FB-GAP-001/002. Installed oidc-client-ts 3.5.0 emits UserLoaded during normal refresh-token and iframe renewal; it is not itself an identity change. Continuity now compares the SDK issuer/audience/subject and optional issuer-scoped `sid`, optional consistent tenant claims, and normalized OAuth scope while the SDK user has a known valid access expiry. Tokens and token timestamps are not identities. A supplied stable `sid` enables preservation; absent/invalid session identity fails closed for draft preservation and is not a new mandatory login field. No fallback to subject-only identity, storage hints, navigation or test authorization exists.

The existing server tenant/Workspace/Project context, semantic EffectiveAccess decision/factors and Selection owner remain separate retirement boundaries. An observation timestamp or explanation-only refresh of the same effective decision preserves the draft. Project status remains provisional BLOCKED on the ordinary route; canonical invocation remains disabled. This does not establish a session-bound server Project/access projection or fix the existing query cache identity gap.

Subscribe-before-hydration and notification/lifecycle generations prevent late initial or loaded reads from affecting newer editing. UserLoaded checks the current SDK user before processing a change; event payload identity is not applied. An old notification preserves an already established current session but cannot hide a pending genuine change. Definitive notifications/signout initiation retire synchronously, including redirect failure. The SDK supplies no operation generation: an old operation that overwrites SDK storage before emitting is indistinguishable from a new sign-in, and payloadless invalidation cannot identify an old owner. Those cases fail closed; no universal stale-auth-operation protection is claimed.

Real integration still needs verified IdP `sid` stability and tenant semantics plus reactive, session-bound Project/access invalidation under existing FB-GAP-001/002. Remote logout detection is limited to existing SDK configuration; automatic silent renew is preserved. No new endpoint, permission key, backend lane, persistence or authorization contract was added. Hermes owns final engineering/browser gates; simulated SDK tests are not real authentication acceptance.


### FB-GAP-003 / UXW1-004 — V9 navigation consumer clarification

V9 completes bounded frontend navigation/inspection on an explicitly injected isolated verification projection; it does not close the real Timeline read gap. Existing `TimelineQueryGateway` exposes `getHead`, `listRevisions` (limit 50), `getRevision`, and `compare`; revision detail/change counts carry no track/clip geometry. HEAD maps response productId to Project AND Timeline; independent Timeline identity resolution remains unproven. Existing typed Add Media Clip Operation preview/apply is retained; this consumer never invokes it or invents a geometry endpoint/DTO/permission.

Ordinary track/clip source is unavailable, distinct from empty. The local proposal echoes principal/tenant/session/Workspace/Project/request and queried Project/Timeline/Revision/optional HEAD digest, with complete/bounded loaded tracks, clip-to-track relationships, optional supplied names/types/version/logical source references and exact source/timeline ranges. Only an explicit test-only binding plus simulated SERVER EffectiveAccess decision enables the isolated host. That fixture is not authorization. Duplicate IDs, mismatched receipts/relationships, malformed or out-of-bounds rational times fail closed. Unknown time basis disables dependent actions. Media references have no open/download/preview link. No playback, canonical editing, remote state or persistence exists.

Future separately authorized integration: pin one frontend tree/build and one backend build plus an agreed adapter for exactly one controlled Project/Timeline/Revision. Backend must resolve authenticated principal/tenant/session and Workspace relationship, independent Timeline/revision identity, read permission/factors, immutable version/content digest, canonical exact unit/bounds and authored interval behavior (the inspected MediaClip.TimeRange.contains includes endpoints; render half-open extents are separate). Return correctly trimmed authorized metadata and typed non-disclosing restricted/unavailable/error/stale states with complete/bounded scope. Validate allowed/denied identities, unknown basis, adjacent/zero-length/large rational boundaries, revision switch, normal renewal vs true identity/access retirement, cancellation and late old receipts. Read back zero canonical/Operation/media-access writes. A backend geometry response and a SERVER-like fixture label are not substitutes for this acceptance; no real adapter is currently implemented. Reuse FB-GAP-001/002 for Project and access/session semantics; no second ledger or whole-platform integration prerequisite.


### FB-GAP-014 — Publication workspace and separately authorized single-channel integration

**PUBLICATION LIFECYCLE / ATTEMPT / EXTERNAL-OUTCOME GRAPH NOT ESTABLISHED.** The narrower authorized local-record read contract is established in the V11 subsection below. Existing social reads include: `SocialPublishController` exposes platform, post-list and post-detail reads and `frontend/src/api/publish.ts` calls them. Their existence does not establish deployment readiness, user-to-Project authorization, a post-to-Project/account/Artifact binding, or the DOM-PUBLICATION-001 plan → attempt → external-outcome graph. Core `SocialPost`, `ConnectedPlatform` and `PostStatus` remain real authority; provider IDs remain separate external references rather than replacements for their core IDs. Historical ObservationSet metrics remain deferred. This gap is separate from Timeline FB-GAP-003, Render FB-GAP-005 and Production Scene/Shot FB-GAP-007.

V10 uses only an explicit isolated fixture host. It binds principal/tenant/session/Workspace/Project/source/owner, a fixture-only graph adapter and independent EffectiveAccess entries for collection listing, each plan's permitted content, and each Artifact's permitted metadata. These `test-only.publication.*` keys and TypeScript projections are not server contract authority. A source self-allowed flag grants nothing. Listing does not grant copy/artifact/media/link access. Unknown access fails closed; restricted relationships omit values and do not disclose existence or counts. Backend response trimming is still required and browser omission is not a confidentiality boundary. All external URLs are omitted. Artifact refs remain inert because no supported destination plus access predicate is established.

The fixture proposal echoes the exact identity scope/request and Project snapshot query (`kind=project-publication-snapshot`, `limit=200`), stable snapshot version and `complete|bounded|partial`. IDs must be unique per supplied fixture collection; plan→account/artifact, attempt→plan/account and external→attempt/plan/account links must exist explicitly when those relations are supplied. Each relation now distinguishes supplied, known-empty, unavailable, restricted and pending-core-contract; an unavailable relation cannot be represented as authoritative `[]`. Generic display/filter status is normalized to the bounded `draft|scheduled|publishing|published|failed|cancelled|unknown` vocabulary; raw or novel provider strings never create filter options or control semantics. No name/order/time matching. Search and filters operate on permitted supplied content only; no server totals, absent-object conclusions from omissions, or hidden paging are inferred. Read error/restricted/invalid clears data and detail; cancellation and generations reject old replies. Same-context refresh/view switch preserves local query/date/zone/scroll and valid selection, but closes details for revalidation without automatic revival. Native identity retirement blocks the old explicit binding until a new host scope/owner is supplied; equal trustworthy SDK renewal and semantic access observations preserve browsing. Simulated lifecycle evidence does not establish real backend/IdP/access integration.

V11 NON-SOCKET preparation adds a separate `external-publication-observation-v2` parser and external adapter under task evidence, not the ordinary route. Every observation carries explicit provider/instance/account/post references and remains `PENDING_CORE_CONTRACT` until a platform-owned Project/account/post binding exists. It never becomes a plan, attempt, external outcome, actual publication time, Artifact relation or known-empty collection by prefixing/hashing external IDs. Owner-local single-user pilot access is explicitly narrower than platform EffectiveAccess. Unknown provider states, including provider `ERROR`, become safe `unknown` observations rather than rejecting the whole read or asserting generic publication failure. Adapter/frontend intervals are half-open `[startInclusive,endExclusive)`; the prepared pinned-provider request alone translates the exclusive end to an inclusive last millisecond. Pure synthetic mapping evidence is not HTTP, account, transport or publication evidence; all socket/browser/real-provider checks remain not run.

#### V11 authorized local-record read slice

The corrected backend source now defines one narrower canonical read: `GET /api/social/platforms?projectId`, `GET /api/social/posts?projectId&connectedAccountId&bindingVersion&start&end&limit`, and `GET /api/social/posts/{id}?projectId&connectedAccountId&bindingVersion`. The account query returns only active actor-owned accounts with an exact current-version Project post binding. The ranged list is constrained by tenant, canonical actor, Project, exact account ID/current binding version and platform consistency; unbound or stale-binding rows are excluded. Its range is `start <= scheduledAt < end`, its coverage is always `BOUNDED_PARTIAL`, and `scheduledAt` means planned publish time only. The detail route applies the same scope predicates and is the only way this contract can return a record without a plan time.

The frontend ordinary Publication route now consumes only that source through the existing authenticated transport. It first selects from the secured Project account result, then sends the exact selected account and UTC half-open month to the ranged list; it sends the current `bindingVersion` and does not send legacy `page/size`, status or provider parameters. Opening a row performs the corrected scoped detail read. Strict response parsing rejects unknown legacy/status/provider fields and incoherent optional content/time pairs; no fallback protocol or fixture is selected after an unavailable/error response. The explicit fixture provider remains verification injection only.

Successful DTOs carry endpoint decision `AUTHORIZED_LOCAL_PROJECT_ACCOUNT_READ` while the absent global five-factor catalog remains `UNKNOWN_FAIL_CLOSED`; the frontend does not mint a `SERVER` EffectiveAccess entry from that endpoint receipt. Content or planned time may be `NOT_PROVIDED`. Artifact references are supplied only with `artifactRelationState=AVAILABLE`; state-only RESTRICTED and NOT_PROVIDED remain distinct. Attempt, provider outcome/status/diagnostic, retry and actual timestamp facts are not in the contract and remain `NOT_PROVIDED / unknown` rather than an invented graph or authoritative empty relation. Empty ranged results never claim complete Project history, and the UI explicitly states that unscheduled records are outside every ranged list.

The original no-socket preparation established frontend-to-source implementation only; it did not prove live authenticated HTTP/security execution or backend build closure. Subsequent accepted V11/backend records and the task-owned 2026-09-12 read verification below are separate evidence. No social mutation, provider call, Artifact hydration, scheduler, analytics or public packaging is implied by this read-only slice.

Time proposal: `timeField=scheduledAt|publishedAt|unscheduled|unknown` explicitly chooses the calendar bucket; required timestamp must be an absolute offset/Z instant. Current UI precision is milliseconds (1–3 fractional digits), offset `-00:00` is indeterminate, offsetless/date-only/missing/invalid timestamps are not scheduled days. No fallback clock/date inference. Explicit unscheduled stays outside calendar. Scheduled, actual published, attempted and fetched timestamps are distinct and omitted means not supplied. Gregorian date intervals are inclusive start/exclusive end in the chosen display zone; conversion uses Intl and enumerated civil dates across leap/year/month/midnight/DST boundaries, not 24-hour local-day arithmetic. Display timezone does not mutate source scheduling. Invalid time leaves the item inspectable but disables its calendar placement.

**Future minimum real integration — separately authorized, not performed:** pin one frontend tree/build and backend build, adapter/provider version/dependency (including any Postiz API/adapter version), controlled principal/session/Workspace/Project and exactly one publishable OutputArtifact plus one account and one platform content version. Backend verifies independently scoped identity/access/metadata, creates a stable publication intent, chooses a single scheduler owner, and returns actual PublicationAttempt/ExternalPublication outcomes: success/failure/unknown. Require idempotency keys and duplicate defense across intent/scheduler/adapter retries; no dual scheduler or browser scheduling authority. Agree paging/completeness/cursors, source/timezone/offset/DST semantics, raw and mapped statuses, explicit attempt/external links, safe summaries, credential expiry/revocation, non-disclosing errors and request cancellation/lifecycle boundaries. Postiz must be a replaceable backend adapter, not a frontend private API/database dependency; OAuth/credentials never enter this consumer.

Local evidence scenarios are `frontend/src/product/publication/model.test.ts`, `PublicationWorkspace.test.tsx` and V10 route/native-renewal test in `frontend/src/app/routeTree.test.tsx`. Future allowed/denied identities, stable-intent duplicate retry, scheduler result readback, cross-account attempts, late replies, revoked metadata and credential expiry must be verified against the pinned backend. Current read-only frontend tests must show zero write/transport/media-link effects. Real single-channel publishing requires separate authorization; V10 does not wait for backend/EP19, claim integration or authorize connect/save/schedule/cancel/publish/retry-send/approval/bulk/metrics operations.


#### Publication browsing improvement — 2026-09-12 (frontend only)

Current source authority is `PublicationAccountResponse` / `PublicationPostResponse` / `PublicationPostListResponse` in Social, consumed by the strict `PublicationReadAPI` and `platformClient.publication`. Read parameters include the exact positive account `bindingVersion`; replies do **not** supply lifecycle `PostStatus`, attempts, external results, safe failure summaries, publishedAt, totals or cursors. Availability filters operate on the loaded records, not a server-wide search. The verified local-record marker and an Artifact reference never imply successful publishing.

Missing capabilities remain owned by Social's published read contract: expose authorized lifecycle/attempt/external-result projections and redacted failure summaries if those user inspections are needed; define pagination/completeness if browsing beyond the bounded 200-row UTC interval is needed. These are proposals, not backend agreement. The frontend shows these facts as not provided, uses no raw diagnostics/external URL, and introduces no publishing or attempt-retry operation. Every retry button only retries an existing read. Project Open remains blocked by FB-GAP-001. No silent-revocation event or global authorization revision is supplied.

The previously observed `PostSchedulerService` JDBC `Instant` binding failure is an independent backend defect. This frontend slice neither changes the scheduler nor treats its behavior as publication success/failure evidence.

This slice's read interaction verification uses a new task-owned database, accepted backend image `22297d62b234` (872e0b08 backend), real JWT/RBAC and the three existing Social GET endpoints. It verifies authorized local records, Artifact references and backend-denied reads; it does not close Project Open, production OIDC, provider publication, attempt/outcome contracts, scheduler or silent-revocation gaps. The separate injected HTTP503 scenario verifies only frontend read-retry behavior.
