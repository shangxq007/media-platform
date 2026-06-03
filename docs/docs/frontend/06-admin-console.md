# Admin Console

> **Module:** `frontend/src/pages/admin/`
> **Last Updated:** 2026-05-19

## Overview

The admin console provides platform administration capabilities including feature flag management, policy management, entitlement management, and system monitoring.

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
| Render Jobs | `/admin/render-jobs` | `RenderJobManagement` | Render job management |
| User Analytics | `/admin/analytics` | `UserAnalytics` | User analytics |
| Usage Ledger | `/admin/billing/ledger` | `UsageLedgerPage` | Usage billing ledger |

## Access Control

The admin console is hidden from normal users. Access requires:
- Admin role in the user's tenant
- Feature flag `admin-console` enabled for the user

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
