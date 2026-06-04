# Admin Console

> **Module:** `frontend/src/pages/admin/`
> **Last Updated:** 2026-05-20

## Overview

The admin console provides platform administration capabilities including notification management, feature flag management, policy management, entitlement management, and system monitoring.

## Implementation Status

| Component | Status |
|-----------|--------|
| `AdminDashboard` | ✅ Implemented |
| `FeatureFlagManagementPage` | ✅ Implemented |
| `FeatureFlagEditor` | ✅ Implemented |
| `FeatureFlagRuleEditor` | ✅ Implemented |
| `FeatureFlagEvaluationPreview` | ✅ Implemented |
| `FeatureFlagEvaluationLog` | ✅ Implemented |
| `PolicyManagementPage` | ✅ Implemented |
| `PolicyRuleEditor` | ✅ Implemented |
| `PolicySimulationPanel` | ✅ Implemented |
| `EntitlementManagementPage` | ✅ Implemented |
| `ExtensionManagement` | ✅ Implemented |
| `RouteManagementPage` | ✅ Implemented |
| `MonitoringFeedbackPage` | ✅ Implemented |
| `AuditLogPage` | ✅ Implemented |
| `DatasetCatalogPage` | ✅ Implemented |
| `QueryAuditPage` | ✅ Implemented |
| `FeedbackAdminPage` | ✅ Implemented |
| `TenantManagement` | ✅ Implemented |
| `BillingPlanManagementPage` | ✅ Implemented |
| `QuotaPolicyEditor` | ✅ Implemented |
| `ConfigManagement` | ✅ Implemented |
| `NotificationManagement` | ✅ Implemented |
| `NotificationAdminPage` | ✅ Implemented |
| `NotificationEventDefinitionPage` | ✅ Implemented |
| `NotificationDeliveryLogPage` | ✅ Implemented |
| `RenderJobManagement` | ✅ Implemented |
| `UserAnalytics` | ✅ Implemented |
| `CreditWalletAdminPanel` | ✅ Implemented |
| `InvoicePreviewPage` | ✅ Implemented |
| `BillingQuotePanel` | ✅ Implemented |
| `TenantOverridePanel` | ✅ Implemented |
| `UserGrantPanel` | ✅ Implemented |
| `ExtensionQuotaInfo` | ✅ Implemented |
| `AccessDecisionDebugPanel` | ✅ Implemented |
| `EntitlementDecisionPreview` | ✅ Implemented |
| `QuotaAllocationEditor` | ✅ Implemented |
| `RoleManagementPanel` | ✅ Implemented |
| `WorkspaceEntitlementPoolPanel` | ✅ Implemented |
| `WorkspaceGroupGrantPanel` | ✅ Implemented |
| `WorkspaceMemberGrantPanel` | ✅ Implemented |
| `WorkspaceMembersPage` | ✅ Implemented |
| `AdminLayout` | ✅ Implemented |

## Pages

| Page | Route | Component | Purpose |
|------|-------|-----------|---------|
| Dashboard | `/admin` | `AdminDashboard` | Admin overview |
| Feature Flags | `/admin/feature-flags` | `FeatureFlagManagementPage` | Manage feature flags |
| FF Editor | (modal) | `FeatureFlagEditor` | Edit flag rules |
| FF Rule Editor | (modal) | `FeatureFlagRuleEditor` | Edit targeting rules |
| FF Evaluation | (tab) | `FeatureFlagEvaluationPreview` | Preview evaluations |
| FF Logs | (tab) | `FeatureFlagEvaluationLog` | Evaluation audit log |
| Policies | `/admin/policies` | `PolicyManagementPage` | ABAC policy management |
| Policy Editor | (modal) | `PolicyRuleEditor` | Edit policy rules |
| Policy Simulation | (tab) | `PolicySimulationPanel` | Simulate decisions |
| Entitlements | `/admin/entitlements` | `EntitlementManagementPage` | Entitlement management |
| Extensions | `/admin/extensions` | `ExtensionManagement` | Extension management |
| Routes | `/admin/routes` | `RouteManagementPage` | Navigation config |
| Monitoring | `/admin/monitoring` | `MonitoringFeedbackPage` | Monitoring status |
| Audit Logs | `/admin/audit` | `AuditLogPage` | Audit trail |
| Dataset Catalog | `/admin/analytics/datasets` | `DatasetCatalogPage` | NLQ datasets |
| Query Audit | `/admin/analytics/query-audit` | `QueryAuditPage` | NLQ audit logs |
| Feedback Admin | `/admin/feedback` | `FeedbackAdminPage` | User feedback |
| Tenants | `/admin/tenants` | `TenantManagement` | Tenant management |
| Billing Plans | `/admin/billing/plans` | `BillingPlanManagementPage` | Plan management |
| Quota Policies | `/admin/quota` | `QuotaPolicyEditor` | Quota configuration |
| Config | `/admin/config` | `ConfigManagement` | System configuration |
| Notifications | `/admin/notifications` | `NotificationManagement` | Notification management |
| Notification Overview | `/admin/notifications/overview` | `NotificationAdminPage` | Notification system health |
| Notification Events | `/admin/notifications/events` | `NotificationEventDefinitionPage` | Event definition CRUD |
| Notification Deliveries | `/admin/notifications/deliveries` | `NotificationDeliveryLogPage` | Delivery log viewer |
| Render Jobs | `/admin/render-jobs` | `RenderJobManagement` | Render job management |
| User Analytics | `/admin/analytics` | `UserAnalytics` | User analytics |
| Usage Ledger | `/admin/billing/ledger` | `UsageLedgerPage` | Usage billing ledger |

## Access Control

The admin console is hidden from normal users. Access requires:
- Admin role in the user's tenant
- Feature flag `admin-console` enabled for the user

## Notification Management

The admin console provides three notification management pages:

### NotificationManagement (Legacy)

`NotificationManagement.vue` at `/admin/notifications` provides the legacy notification management interface with:
- Tenant-scoped notification listing
- Publish event form (event type + JSON payload)
- Delivery viewing per notification
- Retry notification functionality

### NotificationAdminPage

`NotificationAdminPage.vue` at `/admin/notifications/overview` provides the full admin overview with four tabs:

#### Overview Tab
- **Metric cards**: Active Events, Critical Events, Total Deliveries, Active Providers
- **Provider issues alert**: Warning banner when providers are DOWN
- **Recent deliveries**: Last 5 delivery records with status

#### Event Definitions Tab
- Table of all event definitions with columns: Event Key, Name, Category, Severity, Channels, Status
- Count summary: "X event definitions (Y active, Z archived)"
- "Manage Events" button linking to `/admin/notifications/events`

#### Delivery Logs Tab
- Table of recent deliveries with columns: ID, Event, Channel, Status, Destination, Retries, Created
- "View Full Logs" button linking to `/admin/notifications/deliveries`

#### Providers Tab
- Grid of provider cards showing: Provider name, Channel, Status (ACTIVE/DEGRADED/DOWN)
- Per-provider metrics: Success Rate (%), Average Latency (ms), Last Success timestamp
- Last failure warning (if applicable)

### NotificationEventDefinitionPage

`NotificationEventDefinitionPage.vue` at `/admin/notifications/events` provides full CRUD for event definitions:

#### Create/Edit Form
Fields:
- Event Key (disabled during edit)
- Name
- Description
- Category (SYSTEM, SECURITY, BILLING, COLLABORATION, RENDER, EXPORT)
- Severity (LOW, MEDIUM, HIGH, CRITICAL)
- Visibility (PUBLIC, INTERNAL, ADMIN_ONLY)
- Feature Flag Key (optional)
- Supported Channels (multi-select: IN_APP, EMAIL, SMS, WEBHOOK, CHAT, PUSH)
- Toggles: User Configurable, Critical, Default Enabled

#### Event List Table
Columns: Event Key, Name, Category, Severity, Channels, Configurable, Actions
Actions: Edit, Archive

#### Archived Events
Separate table for archived events (read-only)

### NotificationDeliveryLogPage

`NotificationDeliveryLogPage.vue` at `/admin/notifications/deliveries` provides a filterable, paginated delivery log:

#### Filters
- Status (PENDING, SENT, DELIVERED, FAILED, BOUNCED)
- Channel (IN_APP, EMAIL, SMS, WEBHOOK, CHAT, PUSH)
- Event Key (text search)
- Tenant ID (text search)

#### Table
Columns: ID, Notification ID, Event, Channel, Status, Destination (masked), Retries, Created, Sent, Actions

Failed deliveries show a "Retry" action button. Failed rows are highlighted with `bg-danger-500/5`.

#### Pagination
- Page size: 20
- Shows "Showing X–Y of Z" with Previous/Next navigation

### Admin API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/tenants/{tenantId}/notifications` | List tenant notifications |
| GET | `/tenants/{tenantId}/notifications/{id}` | Get notification detail |
| GET | `/tenants/{tenantId}/notifications/{id}/deliveries` | Get deliveries |
| POST | `/tenants/{tenantId}/notifications/{id}/retry` | Retry notification |
| POST | `/notifications/events` | Publish event |
| GET | `/notifications/deliveries` | List all deliveries |
| GET | `/notifications/mock-sent` | List mock-sent notifications |
| GET | `/admin/notifications/events` | List event definitions |
| POST | `/admin/notifications/events` | Create event definition |
| PUT | `/admin/notifications/events/{key}` | Update event definition |
| POST | `/admin/notifications/events/{key}/archive` | Archive event definition |
| GET | `/admin/notifications/deliveries` | List delivery logs |
| POST | `/admin/notifications/deliveries/{id}/retry` | Retry delivery |
| GET | `/admin/notifications/subscriptions` | List subscriptions (by userId) |
| GET | `/admin/notifications/provider-status` | Provider status (Novu + local) |

## Feature Flag Management

Admins can:
- Create, edit, delete feature flags
- Configure targeting rules (tenant, workspace, user, role, group, tier, percentage rollout)
- Set time windows for flag activation
- Configure A/B test variants
- Preview flag evaluation for specific users/tenants
- View evaluation audit logs
- Enable/disable/archive flags

## Policy Management (ABAC)

The `PolicyManagementPage` provides ABAC policy CRUD:

### Policy List
- Search by name or code
- Filter by status (ALL, ACTIVE, DRAFT, ARCHIVED)
- Shows policy name, code, status, version count, rules count
- Actions: Edit, Add Rule, Archive

### Policy Editor (Modal)
- Name, Code, Description
- Status (DRAFT, ACTIVE, ARCHIVED)
- Version count display

### Policy Rule Editor (Modal)
- Rule name, priority, effect (ALLOW, DENY, REQUIRE_REVIEW, DEGRADE, WARN)
- Conditions with attributes and operators
- Feature flag conditions (flag key, operator, expected value)

### Policy Simulation Panel
The `PolicySimulationPanel` allows admins to:
- Input a hypothetical access request (user, tenant, workspace, resource, action)
- See the full decision chain
- Identify which rule matched or denied
- Test policy changes before applying

## Entitlement Management

Admins can:
- View and manage entitlement grants per tenant/workspace/user
- Create, revoke, and extend grants
- Manage entitlement bundles
- Manage tenant overrides
- View workspace entitlement pools
- View workspace quota allocations

## Access Decision Debug

The `AccessDecisionDebugPanel` allows admins to:
- Input an access check request (tenant, workspace, user, feature, preset, provider)
- See the full decision chain with each step's result
- View matched policies, feature flags, quota, and billing decisions
- Identify the exact reason for allow/deny

## Entitlement Decision Preview

The `EntitlementDecisionPreview` shows:
- The entitlement decision for a given subject and feature
- The matched policy chain
- Upgrade options if denied
