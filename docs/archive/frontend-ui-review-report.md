# Frontend UI/UX Review Report — Media Platform

**Date:** 2026-05-16
**Scope:** Complete frontend codebase review for UI/UX redesign sprint planning
**Framework:** Vue 3.5 + TypeScript + Tailwind CSS 3.4 + Pinia 2.3 + Vue Router 4.5

---

## 1. Current Page List with Routes

### Editor / User-Facing Pages

| Route | Name | Component | Description |
|-------|------|-----------|-------------|
| `/` | editor | `EditorPage.vue` | Main video editor with timeline, clip library, effects, export |
| `/project/:id` | project | `EditorPage.vue` | Same editor loaded with a specific project |
| `/prompts` | prompts | `PromptManagementPage.vue` | Prompt template management with list/editor/executions/manifest tabs |
| `/prompts/:templateId` | prompt-editor | `PromptManagementPage.vue` | Same page with template editor open |
| `/effect-packs` | effect-packs | `EffectPackEditor.vue` | Effect pack studio with list/create/edit views |

### User-Side Entitlement & Billing Pages

| Route | Name | Component | Description |
|-------|------|-----------|-------------|
| `/me/capabilities` | me-capabilities | `MyCapabilitiesPage.vue` | User capabilities dashboard (plan, features, usage, entitlements) |
| `/me/usage` | me-usage | `MyCapabilitiesPage.vue` | Reuses capabilities page (likely should be separate) |
| `/me/billing` | me-billing | `BillingHistoryPage.vue` | Billing ledger + invoices |
| `/me/credits` | me-credits | `MyCapabilitiesPage.vue` | Reuses capabilities page (should show `CreditBalancePanel`) |
| `/me/plan` | me-plan | `MyCapabilitiesPage.vue` | Reuses capabilities page (should show `CurrentPlanPanel`) |
| `/me/upgrades` | me-upgrades | `MyCapabilitiesPage.vue` | Reuses capabilities page (should show `UpgradeSuggestionPanel`) |

### Workspace Pages (lazy-loaded)

| Route | Name | Component | Description |
|-------|------|-----------|-------------|
| `/workspace/:workspaceId/members` | workspace-members | `WorkspaceMembersPage.vue` | Member list with entitlement grants |
| `/workspace/:workspaceId/roles` | workspace-roles | `RoleManagementPanel.vue` | Role CRUD with permission toggles |
| `/workspace/:workspaceId/entitlements/pool` | workspace-pool | `WorkspaceEntitlementPoolPanel.vue` | Entitlement pool progress bars |
| `/workspace/:workspaceId/entitlements/grants` | workspace-grants | `WorkspaceMemberGrantPanel.vue` | Member grant management |
| `/workspace/:workspaceId/entitlements/groups` | workspace-groups | `WorkspaceGroupGrantPanel.vue` | Group grant management |
| `/workspace/:workspaceId/entitlements/quota` | workspace-quota | `QuotaAllocationEditor.vue` | Quota allocation editing |
| `/workspace/:workspaceId/entitlements/preview` | workspace-preview | `EntitlementDecisionPreview.vue` | Entitlement decision preview |
| `/workspace/:workspaceId/entitlements/debug` | workspace-debug | `AccessDecisionDebugPanel.vue` | Access decision debug tool |

### Admin Pages (all under `/admin` with `AdminLayout.vue` shell)

| Route | Name | Component | Description |
|-------|------|-----------|-------------|
| `/admin` | admin-dashboard | `AdminDashboard.vue` | Stats grid: tenants, users, extensions, outbox, jobs |
| `/admin/tenants` | admin-tenants | `TenantManagement.vue` | Tenant/project/user/API key CRUD tabs |
| `/admin/render-jobs` | admin-render-jobs | `RenderJobManagement.vue` | Job table with filter, retry, cancel + workers |
| `/admin/extensions` | admin-extensions | `ExtensionManagement.vue` | Extension catalog, detail, execute, rollback, audit |
| `/admin/quota-billing` | admin-quota-billing | `QuotaBilling.vue` | Quota bars, usage, billing, revenue, commerce events |
| `/admin/analytics` | admin-analytics | `UserAnalytics.vue` | User profiles, habits, segments |
| `/admin/notifications` | admin-notifications | `NotificationManagement.vue` | Notification list, deliveries, publish event |
| `/admin/audit` | admin-audit | `AuditCompliance.vue` | Audit records table + outbox failed/recent |
| `/admin/config` | admin-config | `ConfigManagement.vue` | Key-value config table |
| `/admin/feature-flags` | admin-feature-flags | `FeatureFlags.vue` | Tier-based flag matrix + Unleash/ OpenFeature status |
| `/admin/routes` | admin-routes | `RouteManagementPage.vue` | Route definition CRUD with preview modal |
| `/admin/entitlements/bundles` | admin-entitlement-bundles | `EntitlementBundleList.vue` | Bundle list + editor modal |
| `/admin/entitlements/overrides` | admin-entitlement-overrides | `TenantOverridePanel.vue` | Tenant override CRUD |
| `/admin/entitlements/grants` | admin-entitlement-grants | `UserGrantPanel.vue` | User grant CRUD |
| `/admin/entitlements/quota` | admin-entitlement-quota | `QuotaPolicyEditor.vue` | Quota policy CRUD |
| `/admin/billing/plans` | admin-billing-plans | `BillingPlanManagementPage.vue` | Plan cards + stub editor modal |
| `/admin/billing/pricing` | admin-billing-pricing | `PricingRuleEditor.vue` | Pricing rule CRUD |
| `/admin/billing/usage` | admin-billing-usage | `UsageLedgerPage.vue` | Raw/rated usage tables with pagination |
| `/admin/billing/credits` | admin-billing-credits | `CreditWalletAdminPanel.vue` | Wallet list + top-up |
| `/admin/billing/quotes` | admin-billing-quotes | `BillingQuotePanel.vue` | Quote generation + list |
| `/admin/billing/invoices` | admin-billing-invoices | `InvoicePreviewPage.vue` | Invoice preview generation + list |

**Total: 12 admin sub-routes + 7 user-side routes + 4 editor routes + 8 workspace routes = 31 routes**

---

## 2. Current Component Structure (Tree)

```
src/
├── App.vue                          (root: header + nav + upgrade banner + feedback)
├── main.ts                          (app bootstrap, monitoring init)
│
├── router/
│   ├── index.ts                     (route definitions, syncDynamicRoutes, resetRouter)
│   └── guards.ts                    (navigation guard: visibility + entitlement checks)
│
├── stores/
│   ├── project.ts                   (currentProject, projects, renderJobs)
│   ├── timeline.ts                  (tracks, clips, playback, zoom, OTIO export)
│   ├── subtitle.ts                  (tracks, fonts, upload, cue editing)
│   ├── history.ts                   (undo/redo stack, 50 states)
│   └── effectPack.ts                (builtin + custom packs, tier filtering)
│
├── composables/
│   ├── useNavigation.ts             (navigation profile, route visibility)
│   └── useWorkflowStatus.ts         (workflow steps, progress, error handling)
│
├── api/
│   ├── index.ts                     (axios instance, ProjectAPI, RenderAPI, AnalyticsAPI, EntitlementAPI, CostAPI, UsageAlertAPI)
│   ├── me.ts                        (MeEntitlementAPI)
│   ├── navigation.ts                (NavigationClient, RouteManagementClient)
│   ├── prompt.ts                    (PromptAPI)
│   ├── workspace.ts                 (WorkspaceEntitlementAPI)
│   └── admin/
│       ├── analytics.ts             (AdminAnalyticsAPI)
│       ├── audit.ts                 (AuditAPI)
│       ├── billing-admin.ts         (BillingAdminAPI)
│       ├── config.ts                (ConfigAPI)
│       ├── entitlement-admin.ts     (EntitlementAdminAPI)
│       ├── extension.ts             (ExtensionAPI)
│       ├── feature-flags.ts         (FeatureFlagAPI)
│       ├── identity.ts              (IdentityAPI)
│       ├── notification.ts          (NotificationAPI)
│       ├── quota-billing.ts         (QuotaBillingAPI)
│       └── render.ts                (RenderAdminAPI)
│
├── types/
│   ├── index.ts                     (780 lines — all domain types)
│   └── routing.ts                   (route definition types)
│
├── utils/
│   ├── i18n.ts                      (error code → message mapping, en/zh)
│   ├── sentry.ts                    (Sentry init, user, context, sanitization)
│   ├── openreplay.ts                (OpenReplay init, feedback, redaction)
│   ├── otio.ts                      (OTIO export/import)
│   ├── subtitleParser.ts            (SRT/ASS/VTT parsers)
│   └── empty-module.ts              (stub)
│
├── components/
│   ├── admin/
│   │   ├── AdminLayout.vue          (sidebar nav with collapsible groups)
│   │   ├── NavigationPreviewPanel.vue   (modal: preview nav for different contexts)
│   │   ├── RouteDefinitionEditor.vue    (modal: create/edit route form)
│   │   └── RouteDefinitionList.vue      (table: route list with actions)
│   │
│   ├── clip-library/
│   │   └── ClipLibrary.vue          (clip search, filter, upload, drag to timeline)
│   │
│   ├── common/
│   │   ├── DisabledRouteHint.vue    (locked route display + tooltip)
│   │   ├── FormInput.vue            (reusable labeled input)
│   │   ├── MigrationPanel.vue       (timeline schema v1→v2 migration)
│   │   ├── NavigationMenu.vue       (dynamic nav from navigation API)
│   │   ├── OTIOPanel.vue            (OTIO import/export buttons)
│   │   ├── ProjectPanel.vue         (project list, create, save/load timeline)
│   │   ├── SubtitleTimingEditor.vue (cue timing editor with shift controls)
│   │   └── SubtitleUpload.vue       (subtitle + font upload, track list)
│   │
│   ├── effects/
│   │   └── EffectsPanel.vue         (effect browser, category tabs, drag to clip, param config)
│   │
│   ├── export/
│   │   └── ExportPanel.vue          (673 lines — presets, validation, budget, workers, subtitles, submit)
│   │
│   ├── feedback/
│   │   ├── FeedbackButton.vue       (floating button + modal with type/severity/title/description)
│   │   └── MonitoringStatus.vue     (Sentry/OpenReplay status indicator)
│   │
│   ├── prompt/
│   │   ├── PromptExecutionList.vue  (execution list/detail split view)
│   │   ├── PromptManifestPanel.vue  (manifest validation, file scan, stats)
│   │   ├── PromptRiskBadge.vue      (risk level badge with action icon)
│   │   ├── PromptTemplateEditor.vue (edit/versions/render/risk tabs)
│   │   └── PromptTemplateList.vue   (template list with search, status filter)
│   │
│   └── timeline/
│       ├── TimelineEditor.vue       (transport, ruler, tracks, clips, playhead, drag)
│       └── SubtitleTimeline.vue     (subtitle cue visual blocks)
│
└── pages/
    ├── EditorPage.vue               (left panel: clips/effects/subtitles/migration/export + timeline)
    ├── EffectPackEditor.vue         (pack list + full editor with effects/params/tiers)
    ├── PromptManagementPage.vue     (sidebar tabs + content area)
    │
    ├── admin/
    │   ├── AdminDashboard.vue
    │   ├── TenantManagement.vue
    │   ├── RenderJobManagement.vue
    │   ├── ExtensionManagement.vue
    │   ├── QuotaBilling.vue
    │   ├── UserAnalytics.vue
    │   ├── NotificationManagement.vue
    │   ├── AuditCompliance.vue
    │   ├── ConfigManagement.vue
    │   ├── FeatureFlags.vue
    │   ├── RouteManagementPage.vue
    │   ├── EntitlementManagement.vue       (tab container for bundles/overrides/grants)
    │   ├── EntitlementBundleList.vue       (delegates to EntitlementBundleEditor)
    │   ├── EntitlementBundleEditor.vue     (modal editor)
    │   ├── TenantOverridePanel.vue
    │   ├── UserGrantPanel.vue
    │   ├── QuotaPolicyEditor.vue
    │   ├── BillingPlanManagementPage.vue
    │   ├── PricingRuleEditor.vue
    │   ├── UsageLedgerPage.vue
    │   ├── InvoicePreviewPage.vue
    │   ├── CreditWalletAdminPanel.vue
    │   └── BillingQuotePanel.vue
    │
    ├── entitlement/
    │   ├── MyCapabilitiesPage.vue           (plan, usage, features, entitlements, explanations)
    │   ├── BillingHistoryPage.vue           (ledger + invoices tabs)
    │   ├── CreditBalancePanel.vue           (wallet balance + transactions)
    │   ├── CurrentPlanPanel.vue             (plan details card)
    │   ├── EntitlementExplanationPanel.vue  (feature availability explanation)
    │   ├── UpgradeSuggestionPanel.vue       (upgrade options cards)
    │   └── UsageSummaryPanel.vue            (usage bars for minutes/storage/api/exports)
    │
    └── workspace/
        ├── WorkspaceMembersPage.vue            (member list + detail panel)
        ├── RoleManagementPanel.vue             (role CRUD with permission toggles)
        ├── WorkspaceEntitlementPoolPanel.vue   (pool progress bars)
        ├── WorkspaceMemberGrantPanel.vue       (member grant list + create)
        ├── WorkspaceGroupGrantPanel.vue        (group grant list + create)
        ├── QuotaAllocationEditor.vue           (quota allocation inline editing)
        ├── EntitlementDecisionPreview.vue      (decision preview form + result)
        └── AccessDecisionDebugPanel.vue       (debug form + rule evaluation results)
```

---

## 3. Current Style Organization

### Tailwind Configuration (`tailwind.config.js`)

**Custom colors (5 tokens):**
- `timeline-bg: '#1a1a2e'` — dark navy timeline background
- `track-bg: '#16213e'` — slightly lighter track background
- `clip-video: '#4361ee'` — blue for video clips
- `clip-audio: '#2ec4b6'` — teal for audio clips
- `clip-text: '#ff6b6b'` — red/pink for text clips
- `panel-bg: '#0f3460'` — panel background (used in sidebar, headers)

**No custom fonts, spacing, border-radius, or shadow tokens defined.**

### Global CSS (`style.css`)

- Tailwind directives (`@tailwind base/components/utilities`)
- Global reset: `* { margin: 0; padding: 0; box-sizing: border-box }`
- Full-height app: `html, body, #app { height: 100%; width: 100%; overflow: hidden }`
- Custom scrollbar: 8px wide, `#1a1a2e` track, `#4361ee` thumb, 4px radius

### Styling Approach Per Area

| Area | Approach |
|------|----------|
| **All pages** | Tailwind utility classes exclusively (no CSS modules, no scoped styles except `DisabledRouteHint.vue` which has a `<style scoped>` for fade transition) |
| **Admin pages** | Consistent pattern: `bg-gray-800`, `border-gray-700`, `text-white`, `rounded-lg`, `p-4/p-6` |
| **Editor** | Heavy use of custom color tokens (`bg-timeline-bg`, `bg-track-bg`, `bg-panel-bg`, `bg-clip-video`, etc.) |
| **Modals** | Fixed overlay `bg-black/50` or `bg-black/60`, centered card with `bg-gray-800 border-gray-700 rounded-xl shadow-2xl` |
| **Tables** | `bg-gray-800 border border-gray-700 rounded-lg overflow-hidden`, `text-xs`, `hover:bg-gray-700/30` |
| **Status badges** | Consistent pattern: colored background + text (e.g., `bg-green-600/20 text-green-300`) |
| **Forms** | `bg-gray-700`/`bg-gray-800` inputs with `border-gray-600 rounded px-2 py-1.5 text-sm text-white` |
| **Buttons** | Action: `bg-blue-600 hover:bg-blue-500`; Danger: `bg-red-900/40 text-red-300`; Neutral: `bg-gray-700 hover:bg-gray-600` |

### No Design System Tokens

- **No CSS custom properties** (no `--color-primary`, `--spacing-md`, etc.)
- **No component library** (no Vuetify, Element, Naive UI, etc.)
- **No Tailwind plugin** for component abstraction
- **No design token layer** — colors are raw hex values scattered across Tailwind classes
- **No spacing scale customization** — relies entirely on Tailwind defaults
- **No typography plugin** — uses default font stack throughout

---

## 4. Current UI Main Problems

### 4.1 Inconsistent Layout Patterns

- **Admin pages** use `flex-1 overflow-y-auto p-6` while **user-side pages** use `flex-1 overflow-y-auto p-6 space-y-6` — minor but inconsistent spacing convention
- **Editor page** uses a completely different layout (sidebar + main area) with no shared layout component
- **PromptManagementPage** has its own sidebar layout (w-56) independent of the admin layout
- **EffectPackEditor** uses `bg-gray-900` background while admin pages use the layout's `bg-gray-900` — double-wrapping background colors
- **MyCapabilitiesPage** uses a 3-column grid for sub-panels, but other pages use 2-column grids — no consistent grid system

### 4.2 Visual Hierarchy Issues

- **No consistent heading scale**: Some pages use `text-xl font-bold`, others use `text-lg font-semibold`, `text-base font-semibold` — no standardized hierarchy
- **Card vs. flat design inconsistency**: Admin pages use bordered cards (`bg-gray-800 border border-gray-700 rounded-lg p-4`) while the editor uses flat panels with border-r separators
- **Tab styling varies**: Admin sub-tabs use `text-blue-400 border-b-2 border-blue-400` while prompt tabs use `bg-blue-600 text-white` filled style
- **Modal inconsistency**: `RouteDefinitionEditor` uses `rounded-xl shadow-2xl max-w-2xl`, `NavigationPreviewPanel` uses `rounded-xl shadow-2xl max-w-3xl`, `BillingPlanManagementPage` editor uses no shadow at all — no shared modal component

### 4.3 Color Inconsistencies

- **Video clip color** is `#4361ee` (blue) in Tailwind config, but some status indicators use hardcoded `bg-blue-600` — clip color and action color are the same hue
- **Success green** is used inconsistently: `text-green-400`, `text-green-300`, `bg-green-600/20`, `bg-green-900/10`, `bg-green-900/20` — at least 5 variations
- **Warning yellow** has similar fragmentation: `text-yellow-400`, `bg-yellow-600/20`, `bg-yellow-900/30`, `border-yellow-700/50`
- **No dark/light mode** — everything is hardcoded dark theme with no CSS variable abstraction

### 4.4 Overcrowded Interfaces

- **ExportPanel.vue (673 lines)** is the single most overloaded component — it contains tier badge, budget bar, anomaly warnings, worker status, preset selection, cost estimate, unavailable presets list, upgrade options, format/frame rate/encoder selects, subtitle mode, effects compatibility, detailed validation, export button, job history — all in a scrollable sidebar panel
- **FeatureFlags.vue (300 lines)** combines tier-based flag matrix table + Unleash status + code snippets + dynamic flag documentation
- **MyCapabilitiesPage** crams plan details, usage bars, feature flags, entitlement policy, export capabilities, and feature availability into a single scrollable page with a 3-column grid

### 4.5 Missing Visual Feedback

- **No loading skeletons** — only text "Loading..." strings
- **No empty state illustrations** — just text like "No configurations in namespace"
- **No toast/notification system** — errors appear inline as red text blocks
- **No progress indicators** for long operations (only `validatingExport`, `submitting` booleans with text changes)
- **No confirmation dialogs** for destructive actions (delete, archive, void) — actions execute immediately

### 4.6 Icon Usage Inconsistency

- **Emoji icons** used throughout admin sidebar (`📊`, `🏢`, `🎬`, `🔌`, etc.) — not scalable, not accessible, inconsistent rendering across platforms
- **Unicode symbols** for actions: `×`, `→`, `←`, `▶`, `⏸`, `↶`, `↷`, `✕`
- **SVG icons** only appear in `FeedbackButton.vue` (chat bubble) — no icon library
- **No icon system** — mix of emoji, unicode, raw SVG, and text

---

## 5. Current Interaction Main Problems

### 5.1 Navigation Issues

- **5 routes reuse `MyCapabilitiesPage.vue`** (`/me/capabilities`, `/me/usage`, `/me/credits`, `/me/plan`, `/me/upgrades`) — the component shows the same content regardless of which sub-route is active. The dedicated panel components (`CreditBalancePanel`, `CurrentPlanPanel`, `UpgradeSuggestionPanel`, `UsageSummaryPanel`) exist but are only used as sub-widgets within `MyCapabilitiesPage`, not as standalone pages
- **Workspace pages must know `workspaceId`** but don't show breadcrumbs or workspace context — users can't tell which workspace they're in
- **No breadcrumb navigation** anywhere in the app
- **Admin sidebar doesn't show the current page title** — users must remember which section they're in
- **Back button in PromptManagementPage** says "← Back to Editor" — hardcoded, not dynamic

### 5.2 Form UX Issues

- **No form validation feedback** — forms rely on HTML5 validation (`required` attribute not even used) and disabled states, but show no inline error messages
- **No dirty state tracking** — navigating away from an edited form doesn't warn about unsaved changes
- **Create forms are inline** (e.g., `TenantManagement.vue` shows the form below tabs) rather than in modals or dedicated pages — they clutter the view
- **No bulk operations** — every CRUD action is single-item only
- **Date inputs use native `<input type="date">`** with no consistent date picker styling
- **JSON input fields** (e.g., `renderVariables`, `publishPayload`, `executeParams`) have no syntax highlighting, validation, or formatting

### 5.3 Data Table Issues

- **No sortable columns** — all tables render in API return order
- **No column resizing** — fixed widths cause truncation (`truncate max-w-xs`)
- **No row selection feedback** in most tables (only `WorkspaceMembersPage` highlights selected rows)
- **Pagination is manual** — only `UsageLedgerPage` has prev/next pagination; most tables show all data
- **No filtering beyond status dropdowns** — no text search, date range, or advanced filters
- **Table actions use emoji buttons** (`👁`, `✏️`, `🔴`, `🟢`) with `title` attributes — not discoverable, not accessible

### 5.4 Modal UX Issues

- **Modals use `fixed inset-0 z-50`** but z-index is not managed — stacking multiple modals could conflict
- **No escape key handling** for modals — only click-outside and X button close
- **No focus trapping** in modals — keyboard users can tab out
- **`RouteDefinitionEditor`** and **`NavigationPreviewPanel`** have different padding, header styles, and footer button patterns

### 5.5 Timeline Interaction Issues

- **Drag-and-drop for clips** has no visual ghost/placeholder during drag
- **No clip resize handles** — clips can only be moved, not resized from the timeline
- **No multi-select** for clips or tracks
- **Zoom control** uses `+`/`-` buttons with percentage display — no pinch-to-zoom or scroll-to-zoom
- **Playhead** requires clicking on the timeline to seek — no draggable playhead
- **No keyboard shortcuts** documented or implemented for timeline operations (undo/redo buttons exist but no Ctrl+Z/Y)
- **Effect drag from EffectsPanel to clips** — no visual indicator showing valid drop targets

---

## 6. Reusable Component Gaps

The following components are needed but don't exist as reusable elements:

| Missing Component | Impact |
|---|---|
| **`<DataTable>`** | Every admin page rebuilds tables from scratch with inconsistent markup, no sorting, no pagination |
| **`<Modal>` / `<Dialog>`** | Modals are rebuilt per-page with different sizing, header styles, and close behaviors |
| **`<ConfirmDialog>`** | No confirmation for destructive actions (delete, archive, void, unload) |
| **`<Toast>` / `<Notification>`** | No global notification system — errors shown inline, no success feedback |
| **`Breadcrumb`** | No breadcrumb trail anywhere |
| **`<EmptyState>`** | Empty states are plain text strings, no consistent illustration or action prompt |
| **`LoadingSkeleton`** | Loading states are text-only "Loading..." with no skeleton screens |
| **`<Pagination>`** | Only one page has pagination; most don't need it yet but the pattern isn't established |
| **`<TabGroup>`** | Tab implementations vary across admin sub-pages, prompt page, EffectPackEditor |
| **`<StatusBadge>`** | Color-coded badges are duplicated as inline `:class` bindings everywhere |
| **`<ProgressBar>`** | Progress bars for quotas/usage are inline divs with duplicated color logic |
| **`<SearchInput>`** | Search fields are plain `<input>` elements with no search icon or clear button |
| **`<Dropdown>` / `<Select>`** | Native `<select>` elements used everywhere — unstyled, inconsistent cross-browser |
| **`<Toggle>` / `<Switch>`** | Binary settings use checkboxes, no toggle switch component |
| **`UserAvatar`** | No avatar component — user names shown as text only |
| **`<CopyButton>`** | API keys, IDs shown with `font-mono` but no copy-to-clipboard |
| **`Icon`** | No icon component — emoji, unicode, and raw SVG mixed |
| **`<Tooltip>`** | Only `DisabledRouteHint` has a tooltip; most actions rely on `title` attribute |
| **`<CodeBlock>`** | JSON/config displayed in `<pre>` tags with no syntax highlighting or copy |
| **`<FileUpload>`** | File upload patterns are duplicated in ClipLibrary, SubtitleUpload, PromptManifestPanel |
| **`<BadgeCount>`** | Tab labels show counts as text `({{ bundles.length }})` but no badge component |

---

## 7. Admin Page Problems

### 7.1 AdminLayout.vue

- **Sidebar with 23 nav items** across 3 groups — no search or way to find items quickly
- **Sidebar collapse** reduces to icon-only but emoji icons at 14px width are barely recognizable
- **No "Back to Editor" indicator** in expanded state — just a small arrow link at the bottom
- **No user info** in the sidebar — no avatar, name, or role displayed
- **Active route detection** uses `startsWith` which can incorrectly highlight parent routes (e.g., `/admin` matches `/admin/tenants`)

### 7.2 Data Management Patterns

- **TenantManagement.vue**: 4 sub-tabs (tenants, projects, users, API keys) with a create form that changes fields based on active tab — confusing UX since the form title/fields change context
- **QuotaBilling.vue**: Tenant ID is a free-text input with no dropdown/selector — users must know the exact ID
- **UserAnalytics.vue**: Same tenant ID free-text input problem
- **ConfigManagement.vue**: Namespace is a free-text input — no discovery of available namespaces
- **NotificationManagement.vue**: Same pattern — tenant ID as free-text

### 7.3 Missing Admin Features

- **No global search** across admin sections
- **No admin activity log** showing what changes were made and by whom
- **No batch operations** (bulk delete, bulk enable/disable)
- **No export/download** for any admin data tables
- **No settings page** for admin user preferences (theme, density, etc.)

### 7.4 Admin Visual Issues

- **Stat cards** on AdminDashboard use `grid-cols-4` which doesn't fit well on smaller screens
- **RenderJobManagement** uses `grid-cols-4` for stats + `grid-cols-3` for worker cards — no responsive breakpoints
- **FeatureFlags** flag matrix table has 5 tier columns + 2 fixed columns — overflows on narrow screens with no horizontal scroll wrapper
- **BillingPlanManagementPage** editor modal is a stub with "Plan editor form would go here" placeholder text

---

## 8. Video Editor Page Problems

### 8.1 EditorPage.vue

- **Left panel is 256px fixed width** (`w-64`) — too narrow for the ExportPanel content (673 lines of controls)
- **Panel tabs** use `flex-1` equal width — "migration" gets same space as "clips" even though it's rarely used
- **No panel resize** — user can't drag to resize the sidebar
- **Migration banner** appears inside the left panel, pushing content down — should be a dismissible global alert
- **No fullscreen/presentation mode** for the timeline

### 8.2 TimelineEditor.vue

- **Fixed pixel-per-second (50px/s)** — doesn't adapt to zoom level properly (zoom is a multiplier but ruler step calculation is awkward)
- **Track height is fixed at 60px** — no way to resize individual tracks
- **No track reordering** — tracks are fixed in the order they were created
- **No clip snapping** — clips can be placed at any position, no snap-to-grid or snap-to-playhead
- **No waveform visualization** for audio tracks — audio clips look identical to video/text
- **No thumbnails** for video clips in the timeline (only in the clip library)
- **Playhead** is a thin red line — hard to see against dark background
- **Undo/redo** only available as small buttons in the transport bar — no keyboard shortcut
- **No timeline minimap** for long timelines

### 8.3 ClipLibrary.vue

- **No clip preview** on hover — just a tiny 32x24px thumbnail
- **No clip metadata display** in the library (resolution, codec, frame rate)
- **File upload** accepts `video/*,audio/*` but the UI says "Upload Files" with no format guidance
- **No drag preview** when dragging clips to timeline
- **No folder/project organization** — flat list of all clips

### 8.4 EffectsPanel.vue

- **Tier gating** is client-side only — the `currentTier` is hardcoded to `'FREE'` with no way to change it
- **No effect search** — users must scroll through all effects in a category
- **No effect favorites or recent** list
- **Parameter editing** is basic — no sliders for numeric values, no color picker for color params
- **No effect preview** — users can't see what an effect does before applying

### 8.5 ExportPanel.vue

- **Overloaded with 16+ distinct UI sections** in a single scrollable panel
- **Preset list** shows ALL presets including unavailable ones with no visual grouping
- **Budget bar** is always visible even when there's no budget concern — visual noise
- **Worker status** section is always visible — most users won't need this
- **No export queue visualization** — recent jobs shown as text list with no progress bars
- **No estimated file size** or render time display (only cost estimate)

---

## 9. User-Side Page Problems

### 9.1 Route-to-Component Mismatch

- **5 different routes** (`/me/capabilities`, `/me/usage`, `/me/credits`, `/me/plan`, `/me/upgrades`) all render `MyCapabilitiesPage.vue` with identical content — the sub-navigation to these pages exists in the header but the pages don't differentiate
- **`CreditBalancePanel`**, **`CurrentPlanPanel`**, **`UpgradeSuggestionPanel`**, **`UsageSummaryPanel`** exist as standalone components but are only used as sub-widgets inside `MyCapabilitiesPage`, not as dedicated pages

### 9.2 MyCapabilitiesPage.vue

- **3-column grid** for sub-panels doesn't stack gracefully — no responsive handling
- **Feature flags** shown as a 2-column grid within a card — no grouping by category
- **Entitlement policy** shown as a flat key-value list — no visual grouping (compute, export, media, etc.)
- **Feature availability** section requires clicking a preset button then loading an explanation — two-step process with no visual connection between the button and result

### 9.3 BillingHistoryPage.vue

- **No date range filtering** — shows all ledger entries
- **Pagination** only shows "Showing X of Y" with no page controls for the ledger (only invoices lack pagination entirely)
- **No download/print** for invoices
- **No payment method management**

### 9.4 Workspace Pages

- **All workspace pages are lazy-loaded** (dynamic import) but there's no loading spinner during import
- **No workspace navigation** — users must use browser back or manually type URLs to switch between workspace sections
- **No workspace context header** showing which workspace is active
- **WorkspaceMemberGrantPanel** and **WorkspaceGroupGrantPanel** have identical layout patterns but are separate components with duplicated markup
- **RoleManagementPanel** is embedded in the workspace but has no connection to the admin role/permission system

### 9.5 PromptManagementPage

- **Sidebar width (224px / w-56)** is narrower than admin sidebar (208px / w-52 when expanded) — inconsistent
- **Only 3 tabs** (Templates, Executions, Manifest) — no user settings or help
- **"Create" flow** uses `showCreateForm` boolean but the `PromptTemplateList` emits `@create` which sets the flag — the create form replaces the list view with no visual transition
- **No prompt search** across all tabs simultaneously

---

## 10. Mobile / Responsive Issues

### 10.1 No Responsive Design

- **Zero responsive breakpoints** used anywhere in the codebase — no `sm:`, `md:`, `lg:`, `xl:` Tailwind prefixes
- **Fixed widths everywhere**: `w-64` (editor sidebar), `w-56` (prompt sidebar), `w-52` (admin sidebar), `w-48` (tenant ID inputs), `w-80` (workspace detail panel)
- **Fixed grid columns**: `grid-cols-4`, `grid-cols-3`, `grid-cols-2` — all unresponsive
- **Horizontal overflow** is the primary "mobile strategy" — `overflow-x-auto` on tables and timeline
- **No touch-friendly targets** — buttons as small as `text-[10px] px-1 py-0.5` (roughly 20px tall)
- **No touch event handlers** — timeline drag uses `mousedown`/`mousemove` with no `touchstart`/`touchmove`

### 10.2 Viewport Issues

- **`html, body, #app { overflow: hidden }`** — the entire app is locked to viewport height with no scrolling at the page level
- **Modals** use `fixed inset-0` which can have issues on mobile Safari with the address bar
- **No `<meta name="viewport">`** tag handling in the HTML (not visible in source but likely in index.html)

### 10.3 Editor Mobile Unusability

- **Timeline** requires precise mouse clicking and dragging — impossible on touch
- **Transport controls** are inline with zoom and undo — too small for touch
- **Left panel** at 256px would take 60%+ of a 375px phone screen
- **No mobile-specific layout** or simplified view

---

## 11. Accessibility Issues

### 11.1 Semantic HTML

- **No landmark elements** — no `<main>`, `<nav>`, `<aside>`, `<header>` semantic tags used correctly (some `<header>` and `<main>` exist in App.vue but not consistently)
- **No heading hierarchy** — `h1` used on pages but not consistently; `h2`, `h3` used without proper nesting
- **Tables** use `<table>` correctly but lack `<caption>`, `scope` attributes, and proper `<th>` labeling
- **Forms** use `<label>` elements but not always with proper `for`/`id` associations (some use parent-child nesting)

### 11.2 Keyboard Navigation

- **No skip links** — keyboard users must tab through all nav items
- **Custom dropdowns** (select elements) rely on native behavior — no custom keyboard handling
- **Timeline** is entirely mouse-driven — no keyboard access to play, seek, or manipulate clips
- **Modals** don't trap focus — tabbing moves to background elements
- **No ARIA attributes** on interactive elements — `role`, `aria-label`, `aria-expanded`, `aria-current` are absent
- **Emoji buttons** in RouteDefinitionList (`👁`, `✏️`, `🔴`, `🟢`) have `title` but no `aria-label`

### 11.3 Screen Reader

- **No `alt` text** on any images (video thumbnails use `<img>` with no alt)
- **Dynamic content changes** (loading, error states) have no `aria-live` regions
- **Color-only status indicators** — status badges rely solely on color (green/red/yellow) with no text prefix or pattern
- **Form errors** are displayed as red text with no `aria-invalid` or `aria-describedby`

### 11.4 Color Contrast

- **Light text on dark backgrounds** generally passes, but `text-gray-500` on `bg-gray-800` may fail WCAG AA for small text
- **`text-[10px]` and `text-[8px]`** sizes used throughout — even if contrast passes, readability is poor
- **`opacity-40`** and **`opacity-50`** used on interactive elements (disabled effects, locked clips) — reduces contrast further

---

## 12. Redesign Scope for This Sprint

### High Priority (Must Have)

1. **Design token system** — Define CSS custom properties for colors, spacing, typography, shadows, radii. Replace all raw hex values and inconsistent Tailwind color usage.
2. **Component library foundation** — Build `DataTable`, `Modal`, `ConfirmDialog`, `Toast`, `EmptyState`, `LoadingSkeleton`, `StatusBadge`, `SearchInput`, `Pagination`.
3. **Responsive layout framework** — Add responsive breakpoints to all layouts. Make sidebar/panel layouts collapse properly on medium screens.
4. **Fix route-to-component mapping** — Each `/me/*` route should render its dedicated panel component, not all render `MyCapabilitiesPage`.
5. **Consistent heading/typography scale** — Define and apply standardized heading sizes across all pages.
6. **Icon system** — Replace all emoji and unicode icons with a proper icon library (e.g., Heroicons, Lucide).
7. **Replace native `<select>` elements** — Build or integrate a styled select/dropdown component.
8. **AdminLayout improvements** — Fix active route detection, add user info, add workspace context for workspace pages.

### Medium Priority (Should Have)

9. **ExportPanel decomposition** — Split into sub-components: `PresetSelector`, `BudgetIndicator`, `WorkerStatus`, `SubtitleSettings`, `ValidationSummary`.
10. **Form validation framework** — Add inline error messages, dirty state tracking, and unsaved changes warnings.
11. **Confirmation dialogs** — Add `ConfirmDialog` for all destructive actions (delete, archive, void, unload).
12. **Breadcrumb navigation** — Add to admin pages and workspace pages.
13. **Notification/toast system** — Global toast for success/error feedback instead of inline text.
14. **Loading states** — Replace "Loading..." text with skeleton screens.
15. **Tab component** — Standardize tab styling across admin sub-pages, prompt page, and EffectPackEditor.
16. **Empty states** — Design and implement consistent empty state components with illustrations and action prompts.

### Lower Priority (Nice to Have)

17. **Keyboard shortcuts** — Add Ctrl+Z/Y for undo/redo, Escape for modals, Space for play/pause.
18. **Timeline improvements** — Clip resize handles, snapping, waveform visualization, minimap.
19. **Workspace navigation** — Add a workspace-level sidebar or tab navigation.
20. **FeatureFlags table** — Add horizontal scroll wrapper and responsive column handling.
21. **Admin search** — Global search across admin sections.
22. **Monitoring page** — Consolidate monitoring status into a dedicated admin page.

---

## 13. Out of Scope Items

The following are explicitly **out of scope** for this UI/UX redesign sprint:

1. **Touch/mobile-specific interactions** — The editor is a desktop-first professional tool; mobile touch support for the timeline is a separate initiative
2. **Full WCAG 2.1 AA compliance** — Accessibility improvements should be incremental; full audit and remediation is a dedicated project
3. **Internationalization (i18n) framework** — The i18n utility exists for error codes but page-level translation is not in scope
4. **Dark/light mode toggle** — The app is dark-theme only; adding a light mode requires broader design work
5. **Real-time collaboration features** — Multi-user editing, presence indicators, conflict resolution
6. **Advanced timeline features** — Multi-track selection, nested sequences, keyframe animation
7. **Plugin/extension marketplace UI** — Extension management exists but a public marketplace is future work
8. **Onboarding/tutorial flows** — First-time user experience, tooltips, guided tours
9. **Performance optimization** — Virtual scrolling for large tables, lazy loading for timeline clips, code splitting improvements (already partially done with route-level lazy loading)
10. **Backend API changes** — The redesign assumes existing API contracts; any API changes needed to support new UI features are separate work
11. **Billing/payment flow integration** — The billing pages display data but actual payment processing UI (Stripe checkout, etc.) is not in scope
12. **Prompt execution engine UI** — The prompt management UI exists but a visual prompt builder/flowchart editor is future work

---

## Appendix: File Statistics

| Category | File Count | Total Lines (approx) |
|----------|-----------|---------------------|
| Pages | 35 | ~5,800 |
| Components | 21 | ~4,200 |
| Stores | 5 | ~370 |
| API clients | 16 | ~1,350 |
| Types | 2 | ~900 |
| Utils | 6 | ~730 |
| Router | 2 | ~200 |
| Config | 7 | ~120 |
| **Total** | **94** | **~13,670** |

### Largest Files (by line count)

| File | Lines | Issue |
|------|-------|-------|
| `ExportPanel.vue` | 673 | Severely overloaded, needs decomposition |
| `EffectPackEditor.vue` | 604 | List + editor views in one file |
| `types/index.ts` | 780 | All types in one file, should be split by domain |
| `FeatureFlags.vue` | 300 | Two distinct feature sets in one page |
| `RouteDefinitionEditor.vue` | 282 | Complex form, could be split into sections |
| `TenantManagement.vue` | 266 | 4 entity types in one page |
| `NavigationPreviewPanel.vue` | 198 | Preview + form + results in one modal |
| `BillingHistoryPage.vue` | 125 | Ledger + invoices in one page |
| `PromptTemplateEditor.vue` | 336 | Edit + versions + render + risk in one component |
| `ExtensionManagement.vue` | 214 | List + detail + execute + rollback + audit |
