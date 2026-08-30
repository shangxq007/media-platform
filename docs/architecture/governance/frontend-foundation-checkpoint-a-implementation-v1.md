---
document_id: FRONTEND_FOUNDATION_CHECKPOINT_A_IMPLEMENTATION_V1
artifact_type: IMPLEMENTATION_CHECKPOINT_EVIDENCE
authority_class: INFORMATIVE
lifecycle_state: ACTIVE
acceptance_state: CANDIDATE
owner: frontend-platform
retention_class: PROJECT_LIFETIME
status: VALIDATED_CANDIDATE_PENDING_OWNER_ACCEPTANCE
created: 2026-08-30
---

# Frontend Foundation Checkpoint A implementation

## Outcome and boundary

F2 Engineering Foundation, F3 Workspace/Project foundation, F4 creative
shells, F5 management shells, and the bounded F6 clean-forward migration are
implemented in the existing single React/Vite/TanStack application. Shell
existence is not integration or runtime adoption. Missing canonical queries,
access decisions, and commands remain visibly fail-closed.

The base remains `9cd899a3ad6196e04cdfda21430ed61529abf49a`. No backend,
database, migration, OpenCue, Roadmap #23, dependency-version, Git-history,
remote-ref, deployment, or publication change is part of this candidate. The
historical default export from `frontend/src/api/index.ts` remains intact; the
platform client is an additive named boundary.

Applicable instructions are repository-root `AGENTS.md` plus the current Owner
packet. There is no conflict: the Owner explicitly authorizes frontend
production/test/tooling and architecture-document changes while narrowing out
backend and remote operations.

## Engineering foundation inventory

- `surfaceRegistry.ts`: 14 presentation-only definitions with typed ID,
  display name, icon reference, route template/builder, category, scope,
  capability IDs, effective-access key, region policy, compatible references,
  and honest maturity.
- `effectiveAccess.tsx`: all seven required outcomes. Missing data always
  disables; the development adapter is explicitly isolated and fail-closed.
- `platformClient.ts`: Zod-parsed presentation projections and scoped React
  Query keys/hooks. New surfaces contain no scattered raw fetch.
- `references.ts`: safe Project/MediaAsset/Artifact/Timeline/Revision/Render/
  Workflow navigation references, not a canonical backend object.
- `projectContext.tsx`: deep-link identity plus server-query state. It stays
  `BLOCKED` because FB-GAP-001 prevents Workspace-to-Project verification.
- `commandRegistry.ts`: typed commands, project/access/maturity checks,
  shortcuts, conflict detection, and override hook. It cannot bypass server
  authorization.
- `errors.tsx` and `telemetry.ts`: typed async/error categories and a
  no-sensitive-content route/API/access/action/fatal/performance event sink.
- `components/design-system`: Button, IconButton, Input, Search, Tabs, Badge,
  Status, Panel, ResizablePanel, EmptyState, Skeleton, Breadcrumb,
  InspectorSection, PropertyRow, Toast, and CommandPalette.
- `components/app-shell`: WorkspaceHeader, GlobalNavigation,
  ProjectNavigation, SurfaceSwitcher, AssetBrowserHost, InspectorHost,
  CenterWorkspace, BottomPanel, and ActivityPanel. Toggle and resize controls
  are keyboard reachable and named.
- `FoundationPages.tsx`: routed Workspace/Project, creative, operations,
  admin, and developer shells. Canvas state is presentation only; Workflow
  categories are metadata; Agent separates request/plan/preview/authorization/
  result; Review never fabricates merge/diff; Production says
  `NOT_INTEGRATED`.

Server projections stay in React Query. Local state contains only search, tabs,
panel visibility/size, selection-compatible navigation, viewport/zoom, and
transient presentation state. No global store duplicates canonical truth.

## Registry inventory

| ID | Display | Category | Project | Maturity | Route |
|---|---|---|---:|---|---|
| workspace | Workspace | WORKSPACE | no | FOUNDATION | `/w/$workspaceId/home` |
| project-overview | Overview | WORKSPACE | yes | FOUNDATION | `/w/$workspaceId/projects/$projectId/overview` |
| nle | Edit | CREATIVE | yes | FOUNDATION | `/w/$workspaceId/projects/$projectId/edit` |
| canvas | Canvas | CREATIVE | yes | PREVIEW | `/w/$workspaceId/projects/$projectId/canvas` |
| storyboard | Storyboard | CREATIVE | yes | HIDDEN | `/w/$workspaceId/projects/$projectId/storyboard` |
| screenplay | Screenplay | CREATIVE | yes | HIDDEN | `/w/$workspaceId/projects/$projectId/script` |
| agent | Agent | CREATIVE | yes | PREVIEW | `/w/$workspaceId/projects/$projectId/agent` |
| workflow | Workflow | CREATIVE | yes | PREVIEW | `/w/$workspaceId/projects/$projectId/workflow` |
| recipe | Recipe / Template | CREATIVE | yes | HIDDEN | `/w/$workspaceId/projects/$projectId/recipe` |
| review | Review | REVIEW | yes | PREVIEW | `/w/$workspaceId/projects/$projectId/review` |
| production | Production | PRODUCTION | yes | PREVIEW | `/w/$workspaceId/projects/$projectId/production` |
| operations | Operations | OPERATIONS | no | FOUNDATION | `/operations/overview` |
| admin | Admin | ADMIN | no | FOUNDATION | `/admin/organization` |
| developer | Developer | DEVELOPER | no | PREVIEW | `/developer/capabilities` |

Maturity is presentation communication only. It never grants access or claims
backend/runtime availability.

## Runtime routes and migration

The root `/` is an honest Workspace chooser/unavailable entry, never a
synthetic default. The old editor remains preserved at `/legacy/editor` because
there is no truthful way to derive its required Workspace and Project IDs.

Implemented routes (36):

```text
/
/w/$workspaceId/home
/w/$workspaceId/projects
/w/$workspaceId/projects/$projectId/overview
/w/$workspaceId/projects/$projectId/edit
/w/$workspaceId/projects/$projectId/canvas
/w/$workspaceId/projects/$projectId/storyboard
/w/$workspaceId/projects/$projectId/script
/w/$workspaceId/projects/$projectId/workflow
/w/$workspaceId/projects/$projectId/recipe
/w/$workspaceId/projects/$projectId/agent
/w/$workspaceId/projects/$projectId/review
/w/$workspaceId/projects/$projectId/production
/operations/overview
/operations/renders
/operations/storage
/admin/organization
/admin/members
/admin/workspaces
/admin/roles
/admin/security
/admin/billing
/admin/entitlements
/admin/usage
/admin/quota
/admin/policies
/admin/audit
/developer/capabilities
/developer/plugins
/developer/providers
/developer/integrations
/developer/mcp
/developer/api-keys
/developer/webhooks
/developer/agents
/developer/recipes
```

Preserved legacy routes (14):

```text
/legacy/editor
/render-jobs
/capabilities
/smoke-editor
/observability
/dev/timeline-git
/app/renders/$productId
/admin/storage-health
/app/renders
/admin/render-jobs
/dev/preview
/dev/diagnostics
/dev/storage-delivery-profiles
/dev/ingest/preflight-policy
```

Foundation pages are lazy imported as a route chunk. Runtime tests check every
inventory entry against the route tree, render a creative deep link, restore
the exact IDs, and verify SurfaceSwitcher links preserve both identities.

## F6 bounded consumer migration and CLEAN FORWARD

### DELETE_SHADOW resolution

The pre-edit source/import scan covered `frontend/src` and `frontend/scripts`
and counted import specifiers plus exported-symbol references. Six targets had
zero external imports. `demoProjectFactory.ts` had one type-only import from
`demoTimelineFactory.ts`; the latter had no consumer, so the two-file island
had zero entry edges from the rest of the frontend. No route registered any of
the components. All seven files were therefore deleted:

| Deleted path | Mechanical consumer proof | Decision |
|---|---|---|
| `frontend/src/config/navigation.ts` | zero imports; its `userNavItems`, `quickActionIcons`, and synthetic `DEFAULT_WORKSPACE_ID` had zero outside references | delete unreachable navigation shadow |
| `frontend/src/pages/UserRenderHistoryPage.tsx` | zero imports/routes; H4 already names it dead/unregistered | delete duplicate page |
| `frontend/src/pages/UserRenderResultDetailPage.tsx` | zero imports/routes; H4 already names it dead/unregistered | delete duplicate page |
| `frontend/src/render-job/RenderJobsPage.tsx` | zero imports/routes; only raw `fetch('/api/v1/render-jobs')` consumer was the file itself | delete unscoped dead page |
| `frontend/src/style.css` | zero imports; `main.tsx` imports `styles/index.css` | delete duplicate style root |
| `frontend/src/utils/demoProjectFactory.ts` | one type-only edge from the otherwise unconsumed demo Timeline island; zero outside consumers | delete island member |
| `frontend/src/utils/demoTimelineFactory.ts` | zero imports/consumers outside its own file | delete island entry |

The path ledger removes those seven rows rather than retaining records for
nonexistent files. It adds the typed diagnostics client and its contract test.
Its final universe is exactly 212 paths: `REUSE=117`, `MIGRATE=74`,
`DEFER=21`, `DELETE_SHADOW=0`, `UNCLASSIFIED=0`, with zero missing, stale, or
duplicate ledger paths.

### Legacy-route evaluation

No legacy route was retired in F6. Every route remains imported and registered
in `routeTree.tsx`; `routeTree.test.tsx` mechanically checks every member of
`legacyRouteInventory`. The H4 disposition ledger explicitly classifies all 14
as product, operator, or developer compatibility routes. The H4 reachability
guard additionally requires the seven product-facing routes. The remaining
route-specific evidence is:

| Preserved route(s) | Compatibility evidence and reason retirement is unsafe |
|---|---|
| `/legacy/editor` | The replacement editor is Project-scoped; no accepted Workspace→Project relationship can supply IDs. A redirect would synthesize identity. |
| `/render-jobs` | H4-required product Render dashboard; `/operations/renders` is only a projection-gap foundation and is not behaviorally equivalent. |
| `/capabilities` | H4-required fail-closed capability gap page; `/developer/capabilities` is a management foundation, not the same consumer contract. |
| `/smoke-editor` | H4-required fail-closed authoring gap; the Project editor requires identities and canonical authoring contracts that do not exist. |
| `/observability` | H4-required operator compatibility surface; Operations overview does not provide its projection. |
| `/dev/timeline-git` | Internal links remain in the admin render/storage pages; there is no canonical developer replacement. |
| `/app/renders`, `/app/renders/$productId` | H4-required list/detail pair with component tests and internal navigation. No scoped canonical detail route can translate `productId` without fabricated Project scope. |
| `/admin/storage-health`, `/admin/render-jobs` | H4 operator routes with cross-links from legacy admin pages; Operations placeholders are not equivalent projections. |
| `/dev/preview` | Registered developer diagnostic with no canonical replacement. |
| `/dev/diagnostics` | Hub is linked from both diagnostics detail pages and remains their compatibility parent. |
| `/dev/storage-delivery-profiles` | Linked from the diagnostics hub and backed by an exact read-only dev controller. |
| `/dev/ingest/preflight-policy` | Linked from the diagnostics hub and backed by an exact read-only dev controller. |

This is compatibility preservation, not evidence of adoption by the new
surface families. `OLD_ROUTE_COUNT=14` is a non-increasing ceiling; the guard
rejects a fifteenth legacy registration while allowing a later evidenced
retirement.

### Typed consumer convergence

The two live developer diagnostics consumers now use TanStack Query plus a
single typed dev application client built on `api/core/api-client.ts` and the
existing endpoint builders. Zod schemas match the implemented Java response
records, including the object-shaped `StorageDeliveryProfileId.value` rather
than the former handwritten string assumption. Contract tests cover both
responses and reject the old profile-ID shape. No backend path changed.

The deleted render shadow removed one unscoped native fetch, and the two live
diagnostics migrations removed the remaining scattered fetches. The guard
ratchets scattered native fetch from 3 to 0 and raw-storage field residue from
4 to 1. The remaining `sourceUrl` is a legacy presentation type in
`types/index.ts`; changing it requires a separately accepted media projection,
so F6 records it instead of fabricating storage identity.

## Four-axis statuses

| Surface | ARCHITECTURE | IMPLEMENTATION | INTEGRATION | RUNTIME_ADOPTION |
|---|---|---|---|---|
| Workspace | FROZEN | FOUNDATION_ROUTED | PARTIAL; `/me/dashboard` only | NOT_DEMONSTRATED |
| NLE | FROZEN | FOUNDATION_ROUTED | BLOCKED (FB-GAP-001/002/003) | LEGACY_PROTOTYPE_ONLY |
| Canvas | FROZEN | FOUNDATION_ROUTED | BLOCKED (FB-GAP-001/002/003) | NOT_DEMONSTRATED |
| Workflow | FROZEN | FOUNDATION_ROUTED | API_EXISTS_NOT_SURFACE_WIRED; scope/access blocked | NOT_DEMONSTRATED |
| Recipe / Template | FROZEN | HIDDEN_FOUNDATION_ROUTED | API_EXISTS_NOT_SURFACE_WIRED; scope/access blocked | NOT_DEMONSTRATED |
| Agent | FROZEN | FOUNDATION_ROUTED | BLOCKED (FB-GAP-002/003) | NOT_DEMONSTRATED |
| Review | FROZEN | FOUNDATION_ROUTED | PARTIAL_API_NOT_WIRED; scope/merge blocked | NOT_DEMONSTRATED |
| Production | FROZEN | FOUNDATION_ROUTED | BLOCKED (FB-GAP-007) | NONE |
| Operations | FROZEN | FOUNDATION_ROUTED | PARTIAL; FB-GAP-004/005/006/008 | LEGACY_INTERNAL_ONLY |
| Admin | FROZEN | FOUNDATION_ROUTED | PARTIAL_API_NOT_WIRED | LEGACY_INTERNAL_ONLY |
| Developer | FROZEN | FOUNDATION_ROUTED | PARTIAL; FB-GAP-002 | LEGACY_INTERNAL_ONLY |

## Compact surface design records

### Workspace

- PURPOSE: Workspace discovery, recent Projects, and creation entry.
- PRIMARY_USERS: authenticated collaborators.
- PRIMARY_OBJECTS: Workspace and safe Project references.
- PRIMARY_ACTIONS: search Projects, inspect recent work, request creation.
- CANONICAL_AUTHORITIES_CONSUMED: matching authenticated `/me/dashboard` Workspace.
- NAVIGATION_ENTRY: `/w/$workspaceId/home`, `/w/$workspaceId/projects`.
- SHELL_LAYOUT: header/global navigation, center dashboard, activity region.
- EMPTY_STATE: exact no-Projects or unsupported-card message.
- ERROR_STATE: typed Workspace query error without existence leak.
- EFFECTIVE_ACCESS_BEHAVIOR: Create disables on missing access.
- RESPONSIVE_POLICY: desktop/tablet/mobile card reflow.
- NOT_OWNED_SEMANTICS: Project relationship, Assets, Render, activity, pins, recipes, access.

### NLE

- PURPOSE: Project preview, Timeline viewport, revision, selection, inspector, render entry.
- PRIMARY_USERS: editors and creators.
- PRIMARY_OBJECTS: Project, Timeline, Revision, MediaAsset, Artifact, Render refs.
- PRIMARY_ACTIONS: preview and request canonical Operation/render.
- CANONICAL_AUTHORITIES_CONSUMED: none wired; future Timeline/Operation/Render.
- NAVIGATION_ENTRY: `/w/$workspaceId/projects/$projectId/edit`.
- SHELL_LAYOUT: assets, preview/Timeline center, inspector, bottom panel.
- EMPTY_STATE: no canonical editable revision loaded.
- ERROR_STATE: Project scope or application query error.
- EFFECTIVE_ACCESS_BEHAVIOR: edit/render disabled on unknown; server reauthorizes.
- RESPONSIVE_POLICY: desktop-first; narrow read/navigation warning.
- NOT_OWNED_SEMANTICS: Timeline persistence, Operation validation, merge, completion/runtime choice.

### Canvas

- PURPOSE: spatial presentation of safe canonical references.
- PRIMARY_USERS: creators and planners.
- PRIMARY_OBJECTS: separate SemanticReference and SemanticRelationship projections.
- PRIMARY_ACTIONS: pan/zoom/select locally; request canonical relationship command.
- CANONICAL_AUTHORITIES_CONSUMED: none wired; future reference/Operation APIs.
- NAVIGATION_ENTRY: `/w/$workspaceId/projects/$projectId/canvas`.
- SHELL_LAYOUT: resizable assets/inspector around infinite center.
- EMPTY_STATE: presentation-only reference card.
- ERROR_STATE: blocked Project/query state.
- EFFECTIVE_ACCESS_BEHAVIOR: semantic action disabled on unknown.
- RESPONSIVE_POLICY: desktop-first with narrow unsupported notice.
- NOT_OWNED_SEMANTICS: media, relationships, Timeline, Workflow; drawing edges creates no truth.

### Workflow

- PURPOSE: process composition presentation over canonical Workflow definitions.
- PRIMARY_USERS: creators, automation authors, operators.
- PRIMARY_OBJECTS: WorkflowRef and version/execution projections.
- PRIMARY_ACTIONS: arrange seven node categories; validate/invoke later.
- CANONICAL_AUTHORITIES_CONSUMED: accepted Workflow APIs exist but are not wired.
- NAVIGATION_ENTRY: `/w/$workspaceId/projects/$projectId/workflow`.
- SHELL_LAYOUT: node palette, graph center, validation inspector.
- EMPTY_STATE: no server-projected definition selected.
- ERROR_STATE: Project/access/query blocked.
- EFFECTIVE_ACCESS_BEHAVIOR: invoke disabled without exact server access.
- RESPONSIVE_POLICY: desktop-first with safe narrow navigation/read state.
- NOT_OWNED_SEMANTICS: process execution, Timeline composition, runtime eligibility.

### Recipe / Template

- PURPOSE: reserve a Project-scoped recipe/template editor without inventing execution semantics.
- PRIMARY_USERS: automation authors and creators.
- PRIMARY_OBJECTS: WorkflowRef and future Recipe/Template references.
- PRIMARY_ACTIONS: inspect/edit only after canonical projections and commands exist.
- CANONICAL_AUTHORITIES_CONSUMED: none wired; future Workflow/Recipe application API.
- NAVIGATION_ENTRY: `/w/$workspaceId/projects/$projectId/recipe` (HIDDEN).
- SHELL_LAYOUT: shared creative shell with unavailable center.
- EMPTY_STATE: explicit surface-not-integrated state.
- ERROR_STATE: Project/access/query blocked.
- EFFECTIVE_ACCESS_BEHAVIOR: hidden and fail-closed on unknown.
- RESPONSIVE_POLICY: desktop-first; navigation remains available on narrow screens.
- NOT_OWNED_SEMANTICS: Workflow process truth, execution, entitlements, runtime eligibility.

### Agent

- PURPOSE: separate request, resolved plan, preview, authorization, and result.
- PRIMARY_USERS: creators and assisted-workflow users.
- PRIMARY_OBJECTS: safe references and proposed Operation intents.
- PRIMARY_ACTIONS: converse, inspect plan/preview, authorize, inspect result.
- CANONICAL_AUTHORITIES_CONSUMED: none wired; future Agent/Operation APIs.
- NAVIGATION_ENTRY: `/w/$workspaceId/projects/$projectId/agent`.
- SHELL_LAYOUT: conversation, context, plan, preview, authorization, result.
- EMPTY_STATE: no conversation or server plan.
- ERROR_STATE: scope/access/application result error.
- EFFECTIVE_ACCESS_BEHAVIOR: authorization disabled on unknown.
- RESPONSIVE_POLICY: desktop-first panels reflow; content remains navigable.
- NOT_OWNED_SEMANTICS: direct mutation, authorization truth, Operation result, runtime/provider choice.

### Review

- PURPOSE: Overview, Visual Changes, Semantic Changes, Conversation, Checks.
- PRIMARY_USERS: reviewers, editors, approvers.
- PRIMARY_OBJECTS: Project, Revision, review/comment/decision refs.
- PRIMARY_ACTIONS: inspect, discuss, decide, request canonical merge later.
- CANONICAL_AUTHORITIES_CONSUMED: review/revision APIs exist but are not wired.
- NAVIGATION_ENTRY: `/w/$workspaceId/projects/$projectId/review`.
- SHELL_LAYOUT: responsive tabbed center plus inspector/activity.
- EMPTY_STATE: exact projection-specific unavailable message.
- ERROR_STATE: scoped review/revision query error.
- EFFECTIVE_ACCESS_BEHAVIOR: merge disabled without access/contract.
- RESPONSIVE_POLICY: tabs scroll and cards reflow on narrow layouts.
- NOT_OWNED_SEMANTICS: Timeline merge truth and fabricated diff.

### Production

- PURPOSE: future shot/asset/task/milestone projection.
- PRIMARY_USERS: producers, coordinators, supervisors.
- PRIMARY_OBJECTS: future Shot/Scene/Task IDs linked to canonical refs.
- PRIMARY_ACTIONS: search/filter/sort and resource links when integrated.
- CANONICAL_AUTHORITIES_CONSUMED: none; FB-GAP-007.
- NAVIGATION_ENTRY: `/w/$workspaceId/projects/$projectId/production`.
- SHELL_LAYOUT: dense toolbar/list and review/render status.
- EMPTY_STATE: explicit `NOT_INTEGRATED`.
- ERROR_STATE: typed unavailable/query error, never fake data.
- EFFECTIVE_ACCESS_BEHAVIOR: durable actions disabled.
- RESPONSIVE_POLICY: desktop/tablet list; narrow stacked fallback.
- NOT_OWNED_SEMANTICS: Timeline, Workflow, Shot/Task lifecycle, assignments, milestones.

### Operations

- PURPOSE: query/control navigation over authoritative runtime services.
- PRIMARY_USERS: authorized operators and support engineers.
- PRIMARY_OBJECTS: Render, Execution, Worker, Runtime, Device, Provider, Artifact refs.
- PRIMARY_ACTIONS: search/filter/sort and open backed projections.
- CANONICAL_AUTHORITIES_CONSUMED: narrow Render/Storage APIs, not globally composed.
- NAVIGATION_ENTRY: `/operations/overview`, `/operations/renders`, `/operations/storage`.
- SHELL_LAYOUT: cards/table toolbar with justified child routes.
- EMPTY_STATE: explicit scoped-projection requirement.
- ERROR_STATE: typed API error; no unscoped client joins.
- EFFECTIVE_ACCESS_BEHAVIOR: discovery/action requires server access.
- RESPONSIVE_POLICY: cards/lists reflow from desktop to narrow.
- NOT_OWNED_SEMANTICS: eligibility, capacity, provider choice, completion, integrity.

### Admin

- PURPOSE: current-organization administration foundation.
- PRIMARY_USERS: server-authorized organization administrators.
- PRIMARY_OBJECTS: organization, members, Workspaces, roles, security, access/commercial refs.
- PRIMARY_ACTIONS: inspect/configure after typed contracts and access exist.
- CANONICAL_AUTHORITIES_CONSUMED: existing APIs not composed into these routes.
- NAVIGATION_ENTRY: `/admin/organization` plus ten section routes.
- SHELL_LAYOUT: responsive section navigation and maturity panel.
- EMPTY_STATE: `FOUNDATION / NOT_INTEGRATED`.
- ERROR_STATE: future typed auth/policy/API failure.
- EFFECTIVE_ACCESS_BEHAVIOR: configure disabled; no UI-role/plan inference.
- RESPONSIVE_POLICY: desktop/tablet/mobile navigation reflow.
- NOT_OWNED_SEMANTICS: universal super-admin, identity/policy/entitlement/quota/billing truth.

### Developer

- PURPOSE: capability/plugin/provider/integration/MCP/key/webhook/agent/recipe foundation.
- PRIMARY_USERS: server-authorized developers/integrators.
- PRIMARY_OBJECTS: safe capability/integration refs; never retained secrets.
- PRIMARY_ACTIONS: inspect/configure through future application commands.
- CANONICAL_AUTHORITIES_CONSUMED: legacy diagnostics only; new routes are placeholders.
- NAVIGATION_ENTRY: `/developer/capabilities` plus eight section routes.
- SHELL_LAYOUT: section navigation and maturity panel.
- EMPTY_STATE: `FOUNDATION / NOT_INTEGRATED`.
- ERROR_STATE: future typed access/API error.
- EFFECTIVE_ACCESS_BEHAVIOR: discovery/configuration fails closed; keys never re-reveal secrets.
- RESPONSIVE_POLICY: desktop/tablet/mobile section reflow.
- NOT_OWNED_SEMANTICS: capability/runtime truth, provider selection, secrets, commercial access.

## API gaps, UX, and performance

FB-GAP-001 through FB-GAP-008 remain active. FB-GAP-009 is the only new gap:
a typed Workspace Home composition for recent Assets/activity/renders,
pins/favorites, and recipe availability. It is nonblocking for Home foundation.

All icon controls and panel toggles are named; focus-visible, skip link,
landmarks, announced state, arrow-key separators, Escape palette close,
reduced-motion, and non-color status are present. Workspace/Admin/Review
reflow; creative surfaces are desktop-first with a narrow warning.

Pages are route-lazy. Before `AVAILABLE`, set measured budgets for route JS,
LCP, interaction latency, long tasks, memory, virtualization thresholds, log
pagination, and authoritative cursor page size. Design-system convergence in
F6 should migrate legacy controls into this one primitive set and add new
catalog items only for a routed, accessibility-specified consumer.

## Guards and path classification

Guards use semantic ceilings, not file cardinality. Authority counts remain
zero. Legacy raw-storage product fields are 1 maximum and scattered native
fetch is 0 maximum after F6; neither can increase. Direct storage URI
use, duplicate canonical DTO authority, unclassified domain models, commercial
authority, and runtime eligibility are zero. RED tests prove all ceilings.

The candidate ledger covers 212 frontend paths exactly once:
`REUSE=117`, `MIGRATE=74`, `DELETE_SHADOW=0`, `DEFER=21`,
`UNCLASSIFIED=0`. The guard also fails on missing, stale, or duplicate ledger
paths and on reintroduced shadow imports/components/schemas.

## Changed-path scope

Frontend changes:

```text
frontend/scripts/frontend-architecture-guard.mjs
frontend/scripts/frontend-architecture-guard.test.mjs
frontend/scripts/verify-h4-route-reachability.mjs
frontend/src/api/index.ts
frontend/src/app/RootLayout.tsx
frontend/src/app/routeTree.tsx
frontend/src/app/routeTree.test.tsx
frontend/src/components/app-shell/AppShell.tsx
frontend/src/components/app-shell/AppShell.test.tsx
frontend/src/components/design-system/index.tsx
frontend/src/foundation/commandRegistry.ts
frontend/src/foundation/commandRegistry.test.ts
frontend/src/foundation/effectiveAccess.tsx
frontend/src/foundation/effectiveAccess.test.tsx
frontend/src/foundation/errors.tsx
frontend/src/foundation/errors.test.tsx
frontend/src/foundation/platformClient.ts
frontend/src/foundation/projectContext.tsx
frontend/src/foundation/references.ts
frontend/src/foundation/references.test.ts
frontend/src/foundation/surfaceRegistry.ts
frontend/src/foundation/surfaceRegistry.test.ts
frontend/src/foundation/telemetry.ts
frontend/src/styles/foundation.css
frontend/src/styles/index.css
frontend/src/surfaces/FoundationPages.tsx
```

Governance changes are this record, FB-GAP-009, the path ledger, and README
index. F0/F1 artifacts were pre-existing prerequisite work and are preserved.
No backend production/test/build/configuration/migration path changed.

F6 adds or changes these bounded paths:

```text
docs/architecture/governance/frontend-foundation-checkpoint-a-implementation-v1.md
docs/architecture/governance/frontend-product-path-classification-v1.tsv
frontend/scripts/frontend-architecture-guard.mjs
frontend/scripts/frontend-architecture-guard.test.mjs
frontend/src/api/dev/diagnostics.client.test.ts
frontend/src/api/dev/diagnostics.client.ts
frontend/src/api/dev/index.ts
frontend/src/pages/DevIngestPreflightPolicyDiagnosticsPage.tsx
frontend/src/pages/DevStorageDeliveryProfileDiagnosticsPage.tsx
```

F6 deletes exactly:

```text
frontend/src/config/navigation.ts
frontend/src/pages/UserRenderHistoryPage.tsx
frontend/src/pages/UserRenderResultDetailPage.tsx
frontend/src/render-job/RenderJobsPage.tsx
frontend/src/style.css
frontend/src/utils/demoProjectFactory.ts
frontend/src/utils/demoTimelineFactory.ts
```

## F2-F5 validation evidence

```text
HEAD=9cd899a3ad6196e04cdfda21430ed61529abf49a
FINAL_CANDIDATE_SHA=NOT_CREATED_COMMIT_FORBIDDEN_BY_OWNER

cd frontend && npm run architecture:guard:test: EXIT=0; TESTS=23; PASSED=23; FAILED=0
cd frontend && npm run architecture:guard: EXIT=0; GOVERNED_TYPESCRIPT_FILES=163
AUTHORITY_COUNTS=all 11 zero
FRONTEND_RAW_STORAGE_PRODUCT_FIELD_COUNT=4; MAXIMUM=4
FRONTEND_DIRECT_STORAGE_URI_USE_COUNT=0; MAXIMUM=0
FRONTEND_SCATTERED_NATIVE_FETCH_COUNT=3; MAXIMUM=3
FRONTEND_DUPLICATE_CANONICAL_DTO_AUTHORITY_COUNT=0; MAXIMUM=0
FRONTEND_UNCLASSIFIED_DOMAIN_MODEL_COUNT=0; MAXIMUM=0
FRONTEND_COMMERCIAL_AUTHORITY_COUNT=0; MAXIMUM=0
FRONTEND_RUNTIME_ELIGIBILITY_AUTHORITY_COUNT=0; MAXIMUM=0

cd frontend && npm run lint: EXIT=0; ERRORS=0; WARNINGS=50
LINT_ATTRIBUTION=all 50 warnings are in unchanged pre-existing paths; candidate paths emitted none
cd frontend && npm run typecheck: EXIT=0
cd frontend && npm test -- --reporter=json --outputFile=/tmp/frontend-foundation-final-vitest.json: EXIT=0
VITEST_FILES=11; TOTAL=32; PASSED=32; FAILED=0; PENDING=0; TODO=0

cd frontend && npm run build: EXIT=0; MODULES=350
FOUNDATION_ROUTE_CHUNK=35.27 kB (gzip 9.87 kB)
MAIN_CHUNK=597.81 kB (gzip 174.18 kB); Vite emitted the existing >500 kB advisory
BUILD_OUTPUT_CLEANUP=the configured platform-app static output was restored exactly to HEAD after verification

cd frontend && npm run h4:routes: EXIT=0; REQUIRED_ROUTE_SUBSET=21
cd frontend && npm run h4:clean-forward: EXIT=0
git diff --check: EXIT=0

FRONTEND_PATH_LEDGER_TOTAL=217; REUSE=115; MIGRATE=74; DELETE_SHADOW=7; DEFER=21; UNCLASSIFIED=0
FRONTEND_PATH_LEDGER_DUPLICATES=0; CANDIDATE_LEDGER_DIFF=0
IMPLEMENTED_ROUTE_COUNT=36; PRESERVED_LEGACY_ROUTE_COUNT=14; RUNTIME_REGISTERED_TOTAL=50
SURFACE_REGISTRY_COUNT=14; REGISTRY_CONFLICTS=0
BACKEND_OR_OTHER_UNAUTHORIZED_DIRTY_PATH_COUNT=0
```

The Vite build target is intentionally configured under `platform-app` in the
accepted tree. The build gate therefore replaces hashed static output. Only
that exact generated directory was restored from the accepted HEAD and its
three new hashed files removed after the successful build; final backend status
is clean.

## F6 final CLEAN FORWARD evidence

F6 did not run the production build because the Owner reserved that gate and
its configured backend-static cleanup for Hermes. No independent verification
or Owner acceptance is claimed.

```text
HEAD=9cd899a3ad6196e04cdfda21430ed61529abf49a
FINAL_CANDIDATE_SHA=NOT_CREATED_COMMIT_FORBIDDEN_BY_OWNER

cd frontend && npm run architecture:guard:test: EXIT=0; TESTS=28; PASSED=28; FAILED=0
cd frontend && npm run architecture:guard: EXIT=0; GOVERNED_TYPESCRIPT_FILES=158
AUTHORITY_COUNTS=all 11 zero
OLD_IMPORT_COUNT=0
OLD_ROUTE_COUNT=14; MAXIMUM=14
OLD_COMPONENT_USAGE_COUNT=0
OLD_SCHEMA_USAGE_COUNT=0
LEGACY_RAW_STORAGE_USAGE_COUNT=1; MAXIMUM=1
PLAN_NAME_AUTHORITY_BRANCH_COUNT=0
SCATTERED_RAW_FETCH_CALL_COUNT=0; MAXIMUM=0
UNCLASSIFIED_FRONTEND_PATHS=0
DELETE_SHADOW_PATH_RESIDUE_COUNT=0
PATH_LEDGER_STALE_PATH_COUNT=0
PATH_LEDGER_DUPLICATE_PATH_COUNT=0

cd frontend && npm run lint: EXIT=0; ERRORS=0; WARNINGS=49
LINT_ATTRIBUTION=all warnings are in pre-existing paths; F6 paths emitted none
cd frontend && npm run typecheck: EXIT=0
cd frontend && npm test -- --reporter=json --outputFile=/tmp/frontend-f6-vitest.json: EXIT=0
VITEST_FILES=12; SUITES=24; TOTAL=35; PASSED=35; FAILED=0; PENDING=0; TODO=0
cd frontend && npm run h4:routes: EXIT=0; REQUIRED_ROUTE_SUBSET=21
cd frontend && npm run h4:clean-forward: EXIT=0
H4_RETIRED_PATH_RESIDUE_COUNT=0; H4_RETIRED_SYMBOL_USAGE_COUNT=0
H4_ACTIVE_RAW_STORAGE_ASSUMPTION_COUNT=0; H4_RETIRED_RENDER_STATUS_ALIAS_COUNT=0
FRONTEND_ACTIVE_UNSCOPED_RENDER_API_COUNT=0
FRONTEND_SYNTHETIC_WORKSPACE_SCOPE_COUNT=0
FRONTEND_STALE_RENDER_STATUS_SHADOW_COUNT=0
H4_DEFAULT_API_COMPATIBILITY_ENTRYPOINT=PRESERVED
git diff --check: EXIT=0

FRONTEND_PATH_LEDGER_TOTAL=212; REUSE=117; MIGRATE=74; DELETE_SHADOW=0; DEFER=21; UNCLASSIFIED=0
FRONTEND_PATH_LEDGER_DUPLICATES=0; FRONTEND_PATH_LEDGER_STALE=0; CANDIDATE_LEDGER_DIFF=0
IMPLEMENTED_ROUTE_COUNT=36; PRESERVED_LEGACY_ROUTE_COUNT=14; RUNTIME_REGISTERED_TOTAL=50
BACKEND_OR_OTHER_UNAUTHORIZED_F6_PATH_COUNT=0
PRODUCTION_BUILD=NOT_RUN_BY_OWNER_INSTRUCTION
```

## Known limits after F6

- Project links restore IDs and shell context, but data/commands remain blocked
  by FB-GAP-001. This is not an integrated Project experience.
- Home uses only the exact matching dashboard Workspace/recent Projects; other
  cards are unavailable states.
- No creative surface persists edits. Canvas/Workflow/Agent structures are
  presentation metadata.
- Review/Workflow/operations/admin APIs need typed adapters and contract tests.
- Runtime adoption is not inferred from source/build success.
- The F2-F5 build measured the existing main route chunk at 597.81 kB minified
  and observed Vite's 500 kB advisory. F6 did not rerun the Owner-reserved
  production build, so no new bundle measurement is claimed.
- The 14 compatibility routes remain registered. Retirement requires exact
  replacement projections, link migration, and identity-preserving routing.
- Backend gap work and remaining canonical runtime adoption remain separately
  authorized; F6 did not claim either.

## Hermes final gate and rendered UX evidence

Hermes independently reran the complete candidate gate sequence after F6. The
production build was then executed and its configured backend-static output was
restored byte-for-byte from `HEAD`; no backend or other unauthorized dirty path
remained.

```text
npm run architecture:guard:test: EXIT=0; TESTS=28; PASSED=28; FAILED=0
npm run architecture:guard: EXIT=0; GOVERNED_TYPESCRIPT_FILES=158
npm run lint: EXIT=0; ERRORS=0; WARNINGS=49
npm run typecheck: EXIT=0
npm test -- --reporter=json --outputFile=/tmp/frontend-final-vitest.json:
  EXIT=0; FILES=12; TOTAL=35; PASSED=35; FAILED=0
npm run h4:routes: EXIT=0; REQUIRED_ROUTE_SUBSET=21
npm run h4:clean-forward: EXIT=0
npm run build: EXIT=0; MODULES=354
FOUNDATION_ROUTE_CHUNK=35.27 kB (gzip 9.87 kB)
MAIN_CHUNK=601.29 kB (gzip 175.32 kB); Vite >500 kB advisory remains
STATIC_DIRTY_COUNT_AFTER_RESTORE=0
UNAUTHORIZED_DIRTY_COUNT_AFTER_RESTORE=0
git diff --check: EXIT=0
```

Rendered evidence root:

`/home/user/Documents/workspace/audit-runs/FRONTEND_PRODUCT_INFORMATION_ARCHITECTURE_AND_ENGINEERING_FOUNDATION_V1/evidence/screenshots`

The evidence contains nine required desktop route captures, four narrow-width
captures, two contact sheets, 13 extracted body-text records, and `SHA256SUMS`.
`SCREENSHOT_PNG_COUNT=15`, `RUNTIME_BODY_LOADING_RESIDUE=0`, and the manifest
SHA-256 is `a0ed55e4130dcf1af90e7fd0615b9df41e18d6407511a40d1cc5ffe44a76fd34`.

UX review:

- All nine required routes rendered; none was blank, stuck in the lazy-loading
  fallback, or visibly crashed.
- Workspace, Project, creative, Review, Production, Operations, and Admin share
  a restrained dark shell, stable global navigation, status vocabulary, and
  consistent panel hierarchy.
- Project Overview is the densest coherent summary. NLE, Canvas, Workflow, and
  Review render the intended shell regions and explicit blocked/unavailable
  content rather than fabricated canonical data or commands.
- Production explicitly renders `NOT_INTEGRATED`. Operations and Admin display
  maturity/projection limitations rather than invented metrics or permissions.
- At 390 px, Workspace and Admin remain readable; Review content remains
  reachable. NLE shows the desktop-workspace recommendation and preserves read
  and navigation content. Dense project/surface tab rows require horizontal
  scrolling/cropping at this width, consistent with the declared desktop-first
  creative policy but still a follow-up keyboard/overflow usability concern.
- These renders prove runtime reachability and honest state presentation, not
  canonical backend integration, accessibility conformance, or feature
  completeness.
