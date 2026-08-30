---
document_id: FRONTEND_PRODUCT_INFORMATION_ARCHITECTURE_V1
artifact_type: ARCHITECTURE_DECISION
authority_class: NORMATIVE
lifecycle_state: ACTIVE
acceptance_state: ACCEPTED
owner: frontend-platform
retention_class: PROJECT_LIFETIME
status: FROZEN
created: 2026-08-30
---

# FRONTEND_PRODUCT_INFORMATION_ARCHITECTURE_V1

## Status and decision

**Status:** FROZEN for F1.

**Decision:** The product has six surface families over one canonical backend
core. The existing React/Vite/TanStack application remains the implementation
foundation. Routes, shell, data adapters, and presentation state migrate cleanly
forward; the frontend never becomes semantic authority. This decision does not
authorize F2 implementation or any backend change.

No stop-condition architecture conflict was found. The accepted backend does
not yet project every relationship or command required by the target IA; those
are explicit fail-closed integration gaps in the
[application API gap ledger](frontend-backend-application-api-gap-ledger-v1.md),
not permission for the frontend to invent the missing truth.

## Context and accepted-tree inspection

The inspection baseline is commit
`9cd899a3ad6196e04cdfda21430ed61529abf49a`, tree
`d2b44a2d5a7c3a6e9d182dca0b03beb9e3b0b0e2`, on
`agent/frontend-product-ia-engineering-foundation-v1`. The repository has one
Vite-built React application in `frontend/`. It uses a code-defined TanStack
Router tree rather than file routes. The accepted router has 14 URL-bearing
routes plus one root layout node. Current surfaces are disconnected prototypes,
operator/dev panels, and two product render projections rather than a coherent
workspace/project shell.

The exact F0 findings and commands are in the
[F0/F1 interim report](frontend-product-ia-f0-f1-interim-report-v1.md). Every
tracked frontend path has one disposition in the
[machine-readable path ledger](frontend-product-path-classification-v1.tsv).

## Non-negotiable authority boundary

1. Media, Artifact, Timeline, Revision, Operation/OperationPlan,
   Render/Execution, Capability/Workflow, Identity/Entitlement/Policy/Quota/
   Billing, Notification, and Observability semantics have one canonical core.
2. Organization, Workspace, Project, application surface, and Timeline are
   distinct scopes or concepts. Opening a Project in another surface changes a
   projection, not the Project or Timeline identity.
3. Surface changes preserve canonical state and selected canonical references.
4. The frontend is not authority for compatibility, `CAN_RUN`, worker
   eligibility, capacity/reservation, access/commercial truth, merge truth,
   artifact integrity, render completion, or workflow execution truth.
5. Product identity is a safe canonical identifier such as `ArtifactId`; a
   bucket, object key, URI, filesystem path, provider coordinate, or expiring
   access URL is never identity or durable route state.
6. Effective access is server authoritative:
   `capability ∩ runtime ∩ entitlement ∩ policy ∩ quota`. Unknown or incomplete
   input fails closed. Visibility is only presentation and never authorization.
7. Editing gestures lower into canonical Operation/application commands. Local
   UI command objects may manage interaction and undo previews, but cannot
   persist Timeline truth or emit generic database patches.
8. Review records discussion and decisions; Timeline owns revision/merge truth.
   Production Management is a projection and owns neither Timeline nor
   Workflow. Operations is a control/query plane over authoritative services.

## Scope and role model

| Scope | Product meaning | Route/context rule | Authority rule |
|---|---|---|---|
| Organization | The authenticated tenant/organization boundary | `/admin/*` is for the current authorized organization; no generic super-admin is assumed | Identity and policy services authorize every organization action |
| Workspace | Collaboration, membership, grouping, and workspace preferences | Required by `/w/$workspaceId/*` | A workspace ID must be resolved and authorized by the server |
| Project | Durable creative/production container | Required by `/w/$workspaceId/projects/$projectId/*` | The server must validate the Project and its relationship/access in the Workspace |
| Resource | Artifact, media, revision, review, execution, workflow, task, or similar canonical reference | Kept in typed route params, search params, or selection state only when safe and stable | Resource owner validates existence, scope, action, and current state |

Roles are assignments within these scopes. They are not hardcoded UI personas.
Navigation may present creator, reviewer, producer, operator, administrator, or
developer affordances from server projections, but every command is separately
authorized. Platform-wide super-admin behavior is out of scope unless a later
canonical contract explicitly defines it.

## Six surface families

### 1. Workspace / Home

Purpose: enter a Workspace, find Projects and Assets, start creation, see Reviews
and Production work, and reach search, activity, notifications, help, and
profile. Workspace is a collaboration scope, not a synonym for Project.

Primary destinations: Home, Projects, Assets, Create, Reviews, Production.

### 2. Creative

Purpose: project-scoped application surfaces over the same canonical Project
and Timeline: NLE, Infinite Canvas, Storyboard, Screenplay, Agent Studio,
Workflow, and Template/Recipe Editor.

Shared regions: project context, assets, selection, inspector, preview,
history/revision, render, comments, command palette, effective capabilities,
and surface switcher. Each surface may have distinct presentation state, but
all persistent edits use canonical application commands.

### 3. Review & Collaboration

Purpose: inspect Overview, Visual Changes, Semantic Changes, Conversation,
Checks, and Decision for canonical revisions/resources. A merge control is
hidden or disabled unless a canonical Timeline merge contract and server merge
guard are both available; review state alone never permits merge.

### 4. Production Management

Purpose: project/workspace projections for sequences, scenes, shots, assets,
tasks, assignments, milestones, reviews, deliverables, dependencies, people,
and workload. These records reference canonical resources. They do not mutate
Timeline or Workflow state by implication.

### 5. Platform Operations

Purpose: query and control renders, executions, tasks, workers/runtimes,
devices, providers, runtime dependencies, reservations, artifacts, storage,
incidents, and metrics. Detail tabs are Summary, Graph, Tasks, Artifacts,
Runtime, Logs, Metrics, and Provenance only when their real typed APIs exist.

### 6. Organization / Admin / Developer

Purpose: administer members, groups, workspaces, roles, identity, security,
audit, billing, subscription, entitlements, usage, quotas, and policies; and
configure capabilities, plugins, providers, integrations, MCP, API keys,
webhooks, agents, recipes, templates, and marketplace entries. Developer and
operator diagnostics stay visibly separated from user product surfaces.

## Navigation freeze

| Navigation level | Persistent entries | Context behavior |
|---|---|---|
| Global | Workspace switcher, global search, activity, notifications, help, profile | Changing Workspace clears invalid Project/resource selections after server validation |
| Workspace | Home, Projects, Assets, Create, Reviews, Production | Keeps `workspaceId`; Project is optional |
| Project | Overview, NLE/Edit, Canvas, Storyboard, Script, Agent, Workflow, Review, Production | Keeps `workspaceId`, `projectId`, and compatible selected references |
| Operations | Overview, Renders, Executions, Workers, Devices, Providers, Artifacts, Storage, Incidents | Independent operations shell; resource deep links may return to a Project |
| Admin | Organization, Members, Workspaces, Roles, Security, Billing, Entitlements, Usage, Quota, Policies, Audit | Current authorized organization only |
| Developer | Capabilities, Plugins, Providers, Integrations, MCP, API Keys, Webhooks, Agents, Recipes | Hidden unless server presentation/effective-access projection permits discovery; commands still reauthorize |

The canonical home `/` resolves to authentication/onboarding, a server-known
last Workspace, or an explicit Workspace chooser. It does not synthesize a
default Workspace or choose `recentProjects[0]` as durable context.

## Semantic typed route map

TanStack Router path parameters use `$parameter` in code. The corresponding
external semantic notation is `:parameter`. These are the frozen target route
families; implementation may add typed resource detail children without
changing their scope laws.

| Family | TanStack Router target | Semantic meaning |
|---|---|---|
| Workspace | `/w/$workspaceId/home` | Workspace home |
| Workspace | `/w/$workspaceId/projects` | Project index |
| Workspace | `/w/$workspaceId/assets` | Workspace-authorized asset browser |
| Workspace | `/w/$workspaceId/reviews` | Workspace review queue |
| Workspace | `/w/$workspaceId/production` | Workspace production overview |
| Project | `/w/$workspaceId/projects/$projectId/overview` | Project overview |
| Creative | `/w/$workspaceId/projects/$projectId/edit` | NLE |
| Creative | `/w/$workspaceId/projects/$projectId/canvas` | Infinite Canvas |
| Creative | `/w/$workspaceId/projects/$projectId/storyboard` | Storyboard |
| Creative | `/w/$workspaceId/projects/$projectId/script` | Screenplay |
| Creative | `/w/$workspaceId/projects/$projectId/agent` | Agent Studio |
| Creative | `/w/$workspaceId/projects/$projectId/workflow` | Workflow |
| Creative | `/w/$workspaceId/projects/$projectId/recipe` | Recipe/Template Editor foundation |
| Review | `/w/$workspaceId/projects/$projectId/review` | Project review workspace |
| Production | `/w/$workspaceId/projects/$projectId/production` | Project production management |
| Operations | `/operations/overview` | Operations overview |
| Operations | `/operations/renders` | Render operations |
| Operations | `/operations/executions` | Execution operations |
| Operations | `/operations/workers` | Workers and runtimes |
| Operations | `/operations/devices` | Devices |
| Operations | `/operations/providers` | Providers |
| Operations | `/operations/artifacts` | Artifact operations projection |
| Operations | `/operations/storage` | Storage health/projection |
| Operations | `/operations/incidents` | Incidents |
| Admin | `/admin/organization` | Current organization |
| Admin | `/admin/members` | Members and groups |
| Admin | `/admin/workspaces` | Workspace administration |
| Admin | `/admin/roles` | Role assignments/policies |
| Admin | `/admin/security` | Identity/security |
| Admin | `/admin/billing` | Billing/subscription |
| Admin | `/admin/entitlements` | Entitlements |
| Admin | `/admin/usage` | Usage |
| Admin | `/admin/quota` | Quota |
| Admin | `/admin/policies` | Policies |
| Admin | `/admin/audit` | Audit |
| Developer | `/developer/capabilities` | Capability catalog/projections |
| Developer | `/developer/plugins` | Plugins |
| Developer | `/developer/providers` | Provider integrations, not provider selection authority |
| Developer | `/developer/integrations` | Integrations |
| Developer | `/developer/mcp` | MCP configuration |
| Developer | `/developer/api-keys` | API keys |
| Developer | `/developer/webhooks` | Webhooks |
| Developer | `/developer/agents` | Agent definitions/configuration |
| Developer | `/developer/recipes` | Recipes/templates |

Optional public/external routes (for example a review share token) are not
created speculatively. They belong to a `PUBLIC/EXTERNAL` logical domain only
after an accepted scoped-token/public-access contract exists.

## Presentation-only surface registry

The registry is typed configuration consumed by routing, navigation, shell
composition, analytics naming, and lazy loading. It cannot grant access or
declare backend truth.

```ts
type SurfaceMaturity = 'FOUNDATION' | 'PREVIEW' | 'AVAILABLE' | 'HIDDEN'
type SurfaceCategory =
  | 'WORKSPACE'
  | 'CREATIVE'
  | 'REVIEW'
  | 'PRODUCTION'
  | 'OPERATIONS'
  | 'ADMIN'
  | 'DEVELOPER'

interface SurfaceDefinition {
  id: string
  route: string
  category: SurfaceCategory
  projectScoped: boolean
  requiredBackendCapabilities: readonly string[]
  requiredEffectiveAccess: readonly string[]
  layout: 'WORKSPACE' | 'PROJECT' | 'OPERATIONS' | 'ADMIN' | 'DEVELOPER'
  maturity: SurfaceMaturity
}
```

`requiredBackendCapabilities` and `requiredEffectiveAccess` are presentation
inputs from server projections. `maturity` controls product communication and
default discovery only. None of these fields authorizes a loader or command.

## Shared shell regions

The shell is composed from small logical modules, not a forced monorepo split:

- global rail/header: Workspace switcher, search, activity, notifications,
  help, profile;
- contextual navigation: Workspace or Project destinations;
- project header: Project identity, surface switcher, revision/history state,
  collaborators, render entry;
- surface canvas: the active routed surface;
- contextual panels: asset browser, selection, inspector, comments, checks;
- status region: connectivity, save/application-command state, effective
  access reason, render/execution state;
- command palette and non-conflicting keyboard layer;
- global live regions for accessible errors, progress, and notifications.

Shell regions receive safe presentation models and canonical IDs. They do not
cache storage coordinates or infer completion/access from hidden UI elements.

## Cross-surface navigation and selection laws

1. A surface switch keeps the exact `workspaceId` and `projectId`; it never
   creates or clones Project, Timeline, or revision state.
2. Canonical selection is a typed safe reference such as
   `{ kind: 'ARTIFACT', id: ArtifactId }`. Surface-local selection may add view
   state but cannot replace canonical identity.
3. A reference is retained only if the destination declares it compatible and
   the server projection still resolves it in scope. Otherwise the destination
   opens with no selection and a non-destructive explanation.
4. Deep links restore route scope first, then server data, then compatible
   selection. Unknown/unauthorized scope fails closed without leaking whether a
   resource exists.
5. Revision identity is explicit. A route never silently substitutes "latest"
   while presenting an older revision as selected.
6. Back/forward navigation restores presentation state from typed URL search
   params where safe; ephemeral secrets, signed URLs, raw JSON, and large
   editor state never enter URLs.

## Data and state boundaries

| State class | Owner/tooling direction | Permitted examples | Forbidden examples |
|---|---|---|---|
| Route state | TanStack Router typed params/search | Workspace, Project, safe resource ID, tab, filter, cursor | credentials, signed URL, storage URI, canonical document blob |
| Server state | TanStack Query over platform-client adapters | canonical projections, effective access, revisions, jobs, reviews | locally fabricated success or authorization |
| Shared presentation state | small Zustand stores only when cross-component and route-independent | panel visibility, zoom, viewport, compatible selection | canonical Timeline, billing truth, worker eligibility |
| Component state | React local state | hover, draft form field, dialog step | durable domain state |
| Mutation state | application command lifecycle | idempotency key, optimistic visual placeholder, pending/rejected/applied state | direct DB patch, premature canonical success |

Server query keys include scope and canonical identity. Cache invalidation follows
the returned application result/events, not guessed domain coupling. Transport
DTOs are parsed at the platform-client boundary (Zod or generated types when a
future accepted generator is adopted); domain-shaped frontend "single sources
of truth" are prohibited.

## Effective access behavior

Each potentially restricted surface or action consumes a typed, server-issued
effective-access entry with separate capability existence, runtime
availability, entitlement, policy, and quota outcomes plus safe reason codes.
The frontend may:

- hide an undiscoverable surface;
- show a disabled control with the server reason;
- show an upgrade/request-access path supplied by the server;
- retry/refetch an unknown or stale projection.

The frontend may not combine plan names, feature flags, runtime probes, or local
quota arithmetic into access. Missing, malformed, stale-for-command, or unknown
access data disables the action, and the backend still authorizes the command.

## Commands and keyboard architecture

- One command registry owns command IDs, labels, default shortcuts, contexts,
  discoverability, and handlers.
- Commands are semantic user intents (`timeline.clip.move`,
  `review.comment.add`, `render.submit`) that call application adapters. They
  are not database verbs or generic patches.
- Surface adapters may translate pointer/keyboard gestures into typed command
  input. Canonical Operation commands include explicit base revision/content
  identity and surface the server's stale-base/rejection result.
- Text-entry, IME, screen-reader, and browser/OS shortcuts take precedence.
  Shortcuts are remappable, discoverable, and never the only way to act.
- Destructive or externally visible actions require confirmation proportional
  to impact; repeat execution uses idempotency where the backend supports it.
- Undo/redo for durable edits follows canonical revision/Operation semantics.
  Local undo is limited to unapplied presentation drafts.

## Experience policies

### Accessibility

Target WCAG 2.2 AA. All routed surfaces require landmarks, a unique page title,
visible focus, keyboard reachability, semantic labels, logical focus restoration,
minimum target sizes, reduced-motion support, non-color status cues, captions/
transcripts where media requires them, and announced async/error states. Canvas
and Timeline surfaces provide a structured list/table alternative for essential
selection and editing operations.

### Responsive behavior

Workspace, review, admin, and operations lists work from narrow mobile layouts
through desktop. Creative surfaces are desktop-optimized but must preserve read,
review, comment, and safe fallback navigation on small screens. Panels collapse
to drawers; the canonical selection and unsaved application-command state are
not lost during layout changes.

### Performance

Routes and heavy surfaces are lazy loaded. Large assets, timelines, tables, and
logs use pagination/virtualization from authoritative cursors. Media preview and
thumbnails use safe access descriptors and appropriate cancellation. Query keys
are scoped; prefetching never crosses authorization boundaries. Performance
budgets are measured per surface before `AVAILABLE`, including route JS, LCP,
interaction latency, long tasks, and memory for long editing sessions.

### Observability

Emit stable surface/command IDs, route template, correlation/trace ID, latency,
and safe outcome category. Do not log media content, raw Timeline JSON, signed
URLs, storage coordinates, tokens, free-form sensitive text, or entitlement
secrets. Client errors link to backend correlation IDs without inventing cause.

### Errors and empty states

Every query has loading, empty, error, unauthorized/unknown, and stale states.
Empty states distinguish "none exist" from "not projected" and "not allowed."
Contract parse failures fail closed and show a safe retry/correlation path.
Optimistic UI is visually pending until canonical acknowledgement; rejection
restores the previous projection without claiming success.

## Logical engineering architecture

Keep one deployable Vite app until concrete deployment, ownership, or scale
evidence justifies physical apps. Organize logical boundaries so they can be
extracted later without changing semantics:

- apps/logical domains: `WORKSPACE`, `OPERATIONS`, `ADMIN`, and
  `PUBLIC/EXTERNAL_IF_NEEDED`;
- surfaces: `nle`, `canvas`, `storyboard`, `screenplay`, `agent`, `workflow`,
  `review`, `production`;
- shared logical modules: `design-system`, `app-shell`, `surface-registry`,
  `project-context`, `platform-client`, `effective-access`, `asset-browser`,
  `inspector`, `media-viewer`, `timeline-ui`, `graph-ui`, and `review-ui`.

Dependencies point from apps/surfaces into shared presentation/application
adapters. `platform-client` owns transport parsing and auth/correlation wiring.
No shared UI package defines canonical backend entities.

## Major-surface design and status records

Status axes are independent:

- `ARCHITECTURE_STATUS`: information architecture and authority boundary;
- `IMPLEMENTATION_STATUS`: routed UI/component existence;
- `INTEGRATION_STATUS`: accepted API coverage and contract wiring;
- `RUNTIME_ADOPTION_STATUS`: demonstrated use in the deployed runtime.

| Major surface | Compact design record | ARCHITECTURE_STATUS | IMPLEMENTATION_STATUS | INTEGRATION_STATUS | RUNTIME_ADOPTION_STATUS |
|---|---|---|---|---|---|
| Workspace/Home | Workspace-scoped discovery and entry shell; Project is a child reference, not synonymous context | FROZEN | FRAGMENTED (unregistered nav/client concepts) | BLOCKED for explicit Workspace→Project resolution; partial `/me/dashboard` | NOT_DEMONSTRATED |
| Creative | Shared Project shell with NLE/Canvas/Storyboard/Script/Agent/Workflow projections; edits lower to Operation/application commands | FROZEN | NLE prototype and reusable Timeline mechanics; other surfaces absent | BLOCKED for canonical authoring; workflow backend APIs exist but no surface adapter | PROTOTYPE_ONLY |
| Review & Collaboration | Revision-scoped visual/semantic comparison, conversation, checks, decision; merge delegates to Timeline | FROZEN | No registered review route | PARTIAL: review, comments, decisions, merge guard, revision compare exist; composed surface not wired | NOT_DEMONSTRATED |
| Production Management | Projection over canonical Project/resources/workflows with references and workload views | FROZEN | ABSENT | BLOCKED for production-management projections; no frontend fabrication permitted | NONE |
| Platform Operations | Dedicated shell for render/execution/runtime/storage/artifact/incident projections with real tabs only | FROZEN | Scattered render, storage, observability, and dev panels | PARTIAL; typed unified operations projections missing | INTERNAL_LIMITED |
| Organization/Admin/Developer | Current-organization administration plus separately signposted developer configuration | FROZEN | Handwritten clients and a few registered admin/dev panels | PARTIAL; several accepted endpoints exist, clients/routes require scope and contract migration | INTERNAL_LIMITED |

Surface existence never upgrades any other axis. A surface becomes `AVAILABLE`
only after its architecture is frozen, implementation is complete, integration
is contract-verified, and runtime adoption/readiness evidence exists.

## Initial registry maturity direction

| Surface set | Maturity until F2 evidence |
|---|---|
| Workspace shell, Projects, project Overview, NLE shell, Operations shell, Admin shell | FOUNDATION |
| Assets, Reviews, Production, Canvas, Storyboard, Script, Agent, Workflow, render/execution detail, Developer | PREVIEW or HIDDEN according to real projections/effective access |
| Public/external | HIDDEN; no speculative route |
| Any action lacking effective access or canonical command | HIDDEN or fail-closed disabled regardless of surface maturity |

## CLEAN FORWARD and architecture guards

F2 and later work must add mechanical guards for:

1. every registered product route belongs to one typed `SurfaceDefinition`;
2. no duplicate route IDs/templates and no synthetic default Workspace/Project;
3. no product-source storage-coordinate lexemes or use of expiring access URLs
   as identity;
4. no plan-name/tier branching, local `CAN_RUN`, provider selection, capacity,
   quota, merge, or render-completion authority;
5. no direct canonical mutations from pages/components; mutations pass through
   typed application command adapters;
6. no domain-shaped frontend "single source of truth" modules;
7. query keys include required scope and canonical IDs;
8. developer/operator routes cannot appear in user navigation by default;
9. the path disposition ledger is updated with no `UNCLASSIFIED` paths;
10. legacy routes are removed after link migration, with no permanent dual
    route/navigation authority.

The existing `frontend/scripts/frontend-architecture-guard.mjs` is reused and
extended rather than replaced. Generated output, if kept tracked, is validated
as output and never inspected as source authority.

## Safety boundary

This document changes no production, test, build, backend, database, runtime,
or API behavior. It does not authorize new endpoints, OpenCue work, Roadmap #23,
remote operations, or a framework/monorepo migration. Missing contracts remain
backend-owner work and must not be approximated in frontend code.

## Exact validation evidence

At authoring time:

- `git rev-parse HEAD` returned
  `9cd899a3ad6196e04cdfda21430ed61529abf49a`;
- `git rev-parse HEAD^{tree}` returned
  `d2b44a2d5a7c3a6e9d182dca0b03beb9e3b0b0e2`;
- the route-count search over `frontend/src/app/routeTree.tsx` returned 14;
- `npm run architecture:guard` returned 152 governed TypeScript files and all
  11 forbidden-authority counts at zero;
- the path ledger reconciled 198/198 tracked frontend paths with dispositions
  `REUSE=96`, `MIGRATE=74`, `DELETE_SHADOW=7`, `DEFER=21`,
  `UNCLASSIFIED=0`.

The authorized F2/F3 continuation appends 19 new frontend foundation paths to
that accepted-tree baseline. The current ledger therefore reconciles 217/217
tracked-or-current-scope frontend paths with `REUSE=115`, `MIGRATE=74`,
`DELETE_SHADOW=7`, `DEFER=21`, and `UNCLASSIFIED=0`.

Final diff/status validation is recorded in the interim report after all F0/F1
documents are complete.

## Known limits

- The accepted Project response is tenant-scoped and does not expose an
  explicit Workspace relationship; the target nested route therefore requires
  a server-authorized relationship projection before Project surfaces load.
- The current effective-capability endpoint does not express the five-factor
  effective-access intersection as separate typed outcomes.
- Canonical Timeline authoring/application commands and a scoped redacted
  artifact list are missing for key creative/render flows.
- Production Management is an IA reservation, not a claim that its API or UI
  exists.
- Runtime adoption was not tested or inferred from source presence in this
  documentation-only run.

## Recommended next step

After explicit F2 authorization, implement the typed route/surface registry,
shared shell, project-context fail-closed loader, and platform-client boundary
first. Migrate one thin vertical slice (Workspace → Projects → Project Overview)
before creative mutation surfaces. Backend-owner gaps proceed in their own
authorized lanes; F2 must not add endpoints or frontend semantic substitutes.
