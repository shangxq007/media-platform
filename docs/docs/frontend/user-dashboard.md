# User Dashboard

> **Module:** `frontend/src/pages/user/`, `platform-app/src/main/java/com/example/platform/web/MeController.java`
> **Last Updated:** 2026-05-20

## Overview

The user dashboard is the primary entry point for authenticated users at `/me`. It aggregates workspace info, capabilities, usage, billing, projects, shared resources, exports, reports, feedback, notifications, and quick actions — all gated by AccessDecision / NavigationDecision / Feature Flag / Entitlement / Quota / Billing.

## User Dashboard Page

`UserDashboardPage.vue` is the main dashboard component at route `/me` (`me-dashboard`).

### Dashboard Data Structure

The dashboard loads data from `GET /api/v1/me/dashboard` which returns:

```typescript
interface DashboardData {
  tenantId: string | null
  userId: string                    // Masked (e.g., "us***er")
  timestamp: string
  workspace: {
    id?: string
    name?: string
    status?: string
    role: string
  }
  capabilities: {
    tier: string
    monthlyRenderMinutes?: number
    maxConcurrentJobs?: number
    gpuAllowed?: boolean
    remoteWorkerAllowed?: boolean
    customFontsAllowed?: boolean
    watermark?: boolean
    allowedExportFormats?: string[]
    allowedPresets?: string[]
    exportFormats?: string[]
    exportPresets?: string[]
    maxExportResolutionWidth?: number
    maxExportResolutionHeight?: number
    gpuExportAllowed?: boolean
    maxConcurrentExports?: number
  }
  featureFlags: Array<{
    flagKey: string
    displayName: string
    enabled: boolean
    description: string
  }>
  recentProjects: Project[]
  quickActions: Array<{
    key: string
    label: string
    icon: string
    path: string
    enabled: boolean
    visible: boolean
    disabledReason?: string
  }>
  usage: {
    period: string
    renderMinutesUsed?: number
    renderMinutesLimit?: number
    storageGbUsed?: number
    storageGbLimit?: number
    apiCallsUsed?: number
    apiCallsLimit?: number
    exportsUsed?: number
    exportsLimit?: number
  }
  onboarding: {
    hasProjects: boolean
    hasCompletedProfile: boolean
    hasInvitedTeamMembers: boolean
    hasCompletedFirstExport: boolean
    hasSetBilling: boolean
  }
}
```

### Quick Actions

Quick actions are permission-gated:

| Action | Key | Permission Required | Always Enabled |
|--------|-----|---------------------|----------------|
| New Project | `new_project` | None | Yes |
| Upload Media | `upload_media` | None | Yes |
| Try Demo | `try_demo` | None | Yes |
| Invite Team | `invite_team` | ADMIN or MEMBER_MANAGE | No |
| View Reports | `view_reports` | None | Yes |
| Manage Billing | `manage_billing` | ADMIN or MEMBER_MANAGE | No |

Admin-only actions show `disabledReason: "ADMIN_PERMISSION_REQUIRED"` when the user lacks permissions.

### Onboarding Panel

The `UserOnboardingPanel` / `GettingStartedChecklist` component displays a 5-step checklist:

1. Create a project
2. Complete profile
3. Invite team members
4. Complete first export
5. Set up billing

Progress is tracked via the `onboarding` section of the dashboard API response.

## Personal Space Navigation

The user portal provides these pages accessible from the sidebar or `UserAccountMenu`:

| Page | Route | Description |
|------|-------|-------------|
| Dashboard | `/me` | Main dashboard with quick actions, usage, onboarding |
| Projects | `/me/projects` | Project list with pagination |
| Shared Resources | `/me/shared-resources` | Resources shared with the user |
| Exports | `/me/exports` | Render job / export history |
| Capabilities | `/me/capabilities` | Entitlement/FF/Quota/Billing/Provider Access overview |
| Usage | `/me/usage` | Quota usage bars with percentage indicators |
| Billing | `/me/billing` | Subscription plan, billing ledger, invoices |
| Credits | `/me/credits` | Credit wallet balance, transaction history |
| Reports | `/me/reports` | User-generated reports |
| Analytics | `/me/analytics` | NLQ analytics assistant |
| Feedback | `/me/feedback` | Submit and view feedback |
| Notifications | `/me/notifications` | In-app notification inbox |
| Notification Settings | `/me/notification-settings` | Event subscriptions, channel bindings, preferences |
| Settings | `/me/settings` | Account settings |

### Data Loading

All pages load data from the `MeEntitlementAPI` client (`frontend/src/api/me.ts`) which wraps `/api/v1/me/*` endpoints. The MeController (`MeController.java`) provides these endpoints:

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/me/dashboard` | Full dashboard data |
| GET | `/api/v1/me/projects` | Paginated project list |
| GET | `/api/v1/me/shared-resources` | Shared resources |
| GET | `/api/v1/me/exports` | Export/render job list |
| GET | `/api/v1/me/reports` | Reports list |
| GET | `/api/v1/me/notifications` | Notifications list |
| POST | `/api/v1/me/notifications/{id}/read` | Mark notification as read |
| GET | `/api/v1/me/feedback` | Feedback list |
| POST | `/api/v1/me/feedback` | Submit feedback |

### Tenant Isolation

All `/api/v1/me/*` endpoints use `TenantContext.get()` to scope data to the current tenant. Projects are filtered by `tenantId` via `projectRepository.findByTenantId(tenantId)`.

### User ID Masking

The `userId` in the dashboard response is masked (e.g., `us***er`) to prevent information leakage. The masking function shows the first 2 and last 2 characters.

## Notification Settings Page

`NotificationSettingsPage.vue` at route `/me/notification-settings` provides a tabbed interface with three sections:

1. **Event Subscriptions** — toggle notification events on/off
2. **Channel Bindings** — configure email, SMS, webhook destinations
3. **Preferences** — quiet hours, digest mode, critical override

See `notification-settings.md` for full details.

## My Notifications Page

`MyNotificationsPage.vue` at route `/me/notifications` provides the full notification inbox.

### Features

- **Filter tabs**: All / Unread toggle
- **Mark as read**: Individual or "Mark all read"
- **Load more**: Pagination support
- **Relative timestamps**: "Just now", "5m ago", "2h ago", "3d ago"
- **Type badges**: SUCCESS, WARNING, ERROR, INFO with icons
- **Unread indicator**: Blue dot + highlighted background for unread items
- **Resource links**: Shows resource type/id badges (e.g., `PROJECT:abc123`)
- **Navigation**: Clicking an item navigates to the notification's link URL

### API Integration

```typescript
// Load inbox
MeEntitlementAPI.getNotificationInbox(page, size)
// Mark single as read
MeEntitlementAPI.markInboxNotificationRead(id)
// Mark all as read
MeEntitlementAPI.markAllInboxNotificationsRead()
```

### Error Handling

All pages use `ErrorState` component with error codes:
- `COMMON-401-001` — Authentication required
- `COMMON-403-001` — Insufficient permission
- `COMMON-500-001` — Internal server error
- `NOTIFICATION-500-001` — Notification-specific errors
