# App Shell and Navigation

> **Module:** `frontend/src/components/ui/`, `frontend/src/router/`, `frontend/src/navigation/`
> **Last Updated:** 2026-05-20

## Overview

The application uses a slot-based shell layout (`AppShell`) with a header, sidebar, content area, and footer. The `AppShell` component delegates all visual structure to slots, making it reusable across admin and user contexts.

## AppShell Layout Structure

```
┌─────────────────────────────────────────────────────┐
│ AppShell (layout-shell)                             │
│ ┌──────────┐ ┌───────────────────────────────────┐  │
│ │ Sidebar  │ │ layout-main                       │  │
│ │ (slot)   │ │ ┌───────────────────────────────┐ │  │
│ │          │ │ │ AppHeader (slot: header)      │ │  │
│ │          │ │ ├───────────────────────────────┤ │  │
│ │          │ │ │ layout-content (slot: default)│ │  │
│ │          │ │ │                               │ │  │
│ │          │ │ │  <RouterView />               │ │  │
│ │          │ │ │                               │ │  │
│ │          │ │ └───────────────────────────────┘ │  │
│ │          │ │ ┌───────────────────────────────┐ │  │
│ │          │ │ │ Footer (slot: footer)         │ │  │
│ │          │ │ └───────────────────────────────┘ │  │
│ └──────────┘ └───────────────────────────────────┘  │
└─────────────────────────────────────────────────────┘
```

The `AppShell` component (`frontend/src/components/ui/AppShell.vue`) is a minimal wrapper:

```html
<div class="layout-shell">
  <slot name="sidebar" />
  <div class="layout-main">
    <slot name="header" />
    <div class="layout-content theme-scrollbar">
      <slot />        <!-- default slot = page content -->
    </div>
    <slot name="footer" />
  </div>
</div>
```

The `AppShell` is instantiated in `App.vue` with the following slot assignments:

| Slot | Component | Purpose |
|------|-----------|---------|
| `header` | `AppHeader` | Top navigation bar |
| `default` | `<RouterView />` | Main page content |
| `sidebar` | (none in App.vue) | Sidebar is rendered inside admin/user page components |

### AppHeader Component

`AppHeader` (`frontend/src/components/ui/AppHeader.vue`) is the top bar inside the shell. It defines these named slots:

| Slot | Default Content | Used By |
|------|----------------|---------|
| `logo` | Logo text/icon | `App.vue` sets `logo-icon="🎬"` and `logo-text="Media Platform"` |
| `workspace` | — | Workspace badge in `App.vue` |
| `monitoring` | — | Monitoring button in `App.vue` |
| `notifications` | — | `NotificationBell` component in `App.vue` |
| `feedback` | — | Help & Feedback button in `App.vue` |
| `userMenu` | — | Theme toggle + `AvatarMenu` in `App.vue` |
| `actions` | — | Unused (available for extensions) |

The header emits `toggleSidebar` for the hamburger menu button.

## AvatarMenu Component

`UserAccountMenu` (`frontend/src/components/navigation/UserAccountMenu.vue`) renders a dropdown-style navigation menu with all user-side routes.

### Menu Items

| Route Key | Path | Label | Icon | Divider Before |
|-----------|------|-------|------|----------------|
| `me-dashboard` | `/me` | Dashboard | 📊 | No |
| `me-projects` | `/me/projects` | Projects | 📁 | No |
| `me-shared-resources` | `/me/shared-resources` | Shared With Me | 🔗 | No |
| `editor` | `/` | Editor | ✂️ | No |
| `me-exports` | `/me/exports` | Exports | 📤 | Yes |
| `me-capabilities` | `/me/capabilities` | Capabilities | 🛡️ | Yes |
| `me-usage` | `/me/usage` | Usage | 📈 | No |
| `me-billing` | `/me/billing` | Billing | 💳 | No |
| `me-credits` | `/me/credits` | Credits | 💰 | No |
| `me-reports` | `/me/reports` | Reports | 📊 | No |
| `me-feedback` | `/me/feedback` | Feedback | 💬 | Yes |
| `me-notification-settings` | `/me/notification-settings` | Notification Settings | 🔔 | No |
| `me-settings` | `/me/settings` | Settings | ⚙️ | Yes |
| `admin-dashboard` | `/admin` | Admin Console | 🔐 | Yes (admin only) |

### Behavior

- Active route highlighting via `isActive(path)` which checks `route.path === path || route.path.startsWith(path + '/')`
- Admin-only items are conditionally shown based on the `isAdmin` prop
- Clicking an item navigates via `router.push()` and emits `close` to dismiss the menu
- Dividers are rendered before items with `dividerBefore: true`
- ARIA attributes: `role="menu"`, `role="menuitem"`, `aria-current="page"` for active items

## NotificationBell Component

`NotificationBell` (`frontend/src/components/notifications/NotificationBell.vue`) provides a bell icon with unread count badge in the header.

### Functionality

1. **Polling**: On mount, fetches unread count immediately, then polls every 60 seconds via `setInterval`
2. **Badge**: Shows count (capped at `99+`) when `unreadCount > 0`
3. **Dropdown**: Toggles `NotificationDropdown` component in an animated dropdown
4. **Accessibility**: `aria-label`, `aria-expanded`, `aria-haspopup`, `aria-hidden` on badge
5. **Keyboard**: Escape key closes dropdown and returns focus to button
6. **Click outside**: Closes dropdown when clicking outside the bell element

### API Integration

```typescript
// Fetches unread count
MeEntitlementAPI.getMyNotifications(0, 1, 'UNREAD')
// Returns: { notifications, total, page, size, unreadCount }
```

## UserSidebar Integration

The `UserSidebar` (`frontend/src/components/user/UserSidebar.vue`) is rendered inside user portal pages (not inside `AppShell` directly). It provides a 220px-wide vertical navigation with these sections:

| Section | Items |
|---------|-------|
| **Main** | Dashboard, Projects, Shared, Exports, Editor, Prompts, Effect Packs |
| **Account** | Capabilities, Usage, Billing, Credits |
| **Reports** | Reports, Analytics, Feedback, Notifications, Settings |
| **Admin** | Admin link at bottom |

The sidebar highlights the active route with `bg-primary-500/10` and `text-primary-500`.

## Fallback Navigation

When the backend `NavigationRegistryService` is unavailable, the frontend falls back to a static route list defined in `frontend/src/navigation/fallbackRoutes.ts`.

### Fallback Route Lists

`FALLBACK_USER_ROUTES` contains 13 routes with `routeKey`, `path`, `title`, `icon`, `menuGroup`, and `order`. `FALLBACK_ADMIN_ROUTES` contains 1 route.

### Navigation Mapper

`frontend/src/navigation/navigationMapper.ts` provides:

- `mapToNavItems(decisions)` — converts `RouteVisibilityDecision[]` to `NavItem[]`
- `mapToMenuGroups(decisions)` — groups nav items by `menuGroup` (main, content, account, support), filtering out invisible items and admin routes

### Fallback Banner

When `isUsingFallback` is true, a warning banner is displayed:
```
⚠️ Using local navigation — backend navigation unavailable
```

## Disabled Route Pages

Three system pages handle disabled/hidden routes:

| Page | Component | Route | Purpose |
|------|-----------|-------|---------|
| Forbidden | `ForbiddenPage` | `/forbidden` | Route exists but is hidden (404-equivalent) |
| Route Disabled | `RouteDisabledPage` | `/route-disabled` | Route exists but is disabled (permission/entitlement/FF blocked) |
| Upgrade Required | `UpgradeRequiredPage` | `/upgrade-required` | Route requires higher tier |

These pages receive route information via query parameters:
- `routeKey` — the original route key
- `reasonCode` — why the route was blocked
- `pageName` — human-readable page name
- `requiredUpgrade` — target tier for upgrade
- `requiredPermission` — missing permission
- `requiredEntitlement` — missing entitlement
- `message` — user-friendly explanation

## Route Guard Logic

The `navigationGuard` (`frontend/src/router/guards.ts`) runs on every route transition via `router.beforeEach()`.

### Guard Flow

```
1. Call fetchNavigation() to load route visibility decisions
   └─ On failure: call next() (allow navigation)

2. Admin routes (routeKey starts with "admin-"): allow immediately

3. System pages (forbidden, route-disabled, upgrade-required): allow immediately

4. Look up route decision via getRouteDecision(routeKey)

5. If decision.visible === false:
   └─ Redirect to /forbidden with reasonCode and message

6. If decision.enabled == false:
   ├─ reasonCode = NAV-403-TIER + requiredUpgrade:
   │  └─ Redirect to /upgrade-required with upgrade details
   ├─ reasonCode = NAV-403-FEAT:
   │  └─ Redirect to /route-disabled with feature flag info
   ├─ Has requiredUpgrade:
   │  └─ Redirect to /upgrade-required
   └─ Otherwise:
      └─ Redirect to /route-disabled with full context

7. If using fallback: log info message

8. Call next() (allow navigation)
```

### Navigation Decision Sources

Route decisions come from the backend `NavigationRegistryService` which evaluates:

- **Feature flags**: If a route's required feature flag is disabled → `NAV-403-FEAT`
- **Entitlements**: If the user's tier doesn't include the route → `NAV-403-TIER`
- **Permissions**: If the user lacks required permissions → `NAV-403-PERM`
- **Route configuration**: Routes can be hidden or disabled in the navigation config

### Dynamic Route Registration

`syncDynamicRoutes(componentKeys)` allows registering routes at runtime from a backend-provided component map. It:

1. Checks if the route already exists (`router.hasRoute`)
2. Looks up the component loader from `componentMap`
3. Adds the route via `router.addRoute`

`resetRouter()` clears all dynamic routes and re-adds only the static routes.
