# Frontend Entitlement Management

> Doc index: [docs/README.md](./README.md).

## Overview

The frontend provides pages for users, workspace admins, and platform admins to manage entitlements, view capabilities, track usage, manage billing, and configure the platform. All pages are protected by the navigation decision service and require appropriate roles/permissions.

## User-Side Pages

### Capabilities Page

Displays the current user's entitlement capabilities.

**Route**: `/account/capabilities` (or accessible via user menu)

**API**: `GET /api/v1/entitlements/me/capabilities`

Displays:
- Current tier
- Entitlement policy (resolution, concurrent jobs, etc.)
- Export capabilities (formats, presets)
- Provider access
- Feature flags
- Upgrade suggestions

```typescript
const capabilities = await fetch('/api/v1/entitlements/me/capabilities', {
  headers: { 'X-Tenant-ID': tenantId, 'X-User-ID': userId },
});
```

### Usage Page

Displays current quota usage and remaining limits.

**Route**: `/account/usage`

**API**: `GET /api/v1/tenants/{tenantId}/quota` and `GET /api/v1/tenants/{tenantId}/usage`

Displays:
- Render minutes used / remaining
- GPU minutes used / remaining
- Storage used / remaining
- Daily render job count
- Warning indicators at 80% threshold

### Billing Page

Displays subscription, invoices, and payment methods.

**Route**: `/account/billing`

**APIs**:
- `GET /api/v1/billing/subscriptions/current`
- `GET /api/v1/billing/ledger?tenantId={tenantId}`
- `GET /api/v1/billing/subjects/{subjectId}`

Displays:
- Current subscription plan and status
- Billing period
- Included quota and usage
- Invoice history
- Payment method (stub)

### Credits Page

Displays credit wallet balance and transactions.

**Route**: `/account/credits`

**APIs**:
- `GET /api/v1/billing/wallets?tenantId={tenantId}` (via CreditWalletService)
- Transaction history

Displays:
- Current balance
- Transaction history (credits, debits, reservations, finalizations)
- Top-up button (stub)

### Plan Page

Displays current plan details and available plans.

**Route**: `/account/plan`

**APIs**:
- `GET /api/v1/entitlements/me/capabilities`
- `GET /api/v1/admin/billing/plans` (public plans)

Displays:
- Current plan features
- Usage vs limits
- Available upgrade plans
- Feature comparison

### Upgrades Page

Displays upgrade options when a feature is not available.

**Route**: `/upgrade?feature={featureKey}`

Displays:
- Current tier
- Required tier for the feature
- Upgrade pricing
- Feature comparison
- CTA to upgrade

## Workspace Admin Pages

### Members Page

Manage workspace members and their roles.

**Route**: `/workspaces/{workspaceId}/members`

**APIs**:
- `GET /api/v1/workspaces/{workspaceId}/members`
- `POST /api/v1/workspaces/{workspaceId}/members`
- `PUT /api/v1/workspaces/{workspaceId}/members/{memberId}/roles`

### Roles Page

Manage workspace roles and permissions.

**Route**: `/workspaces/{workspaceId}/roles`

**APIs**:
- Role assignment/removal
- Permission management

### Pool Page

Manage workspace entitlement pools.

**Route**: `/workspaces/{workspaceId}/pool`

**APIs**:
- `GET /api/v1/workspaces/{workspaceId}/entitlements/pool`
- `POST /api/v1/workspaces/{workspaceId}/entitlements/pool/allocate`
- `POST /api/v1/workspaces/{workspaceId}/entitlements/pool/reclaim`

Displays:
- Pool list with total/used/remaining quota
- Member allocations
- Allocate/reclaim controls

### Grants Page

Manage entitlement grants for workspace members.

**Route**: `/workspaces/{workspaceId}/grants`

**APIs**:
- `GET /api/v1/admin/entitlements/grants?subjectId={workspaceId}`
- `POST /api/v1/admin/entitlements/grants`
- `POST /api/v1/admin/entitlements/grants/{grantId}/revoke`
- `POST /api/v1/admin/entitlements/grants/{grantId}/extend`

### Quota Page

View and manage workspace quota allocations.

**Route**: `/workspaces/{workspaceId}/quota`

Displays:
- Per-member quota allocations
- Usage tracking
- Quota profile assignments

## Platform Admin Pages

### Bundles Page

Manage entitlement bundles.

**Route**: `/admin/entitlements/bundles`

**APIs**:
- `GET /api/v1/admin/entitlements/bundles`
- `POST /api/v1/admin/entitlements/bundles`
- `PUT /api/v1/admin/entitlements/bundles/{bundleKey}`
- `POST /api/v1/admin/entitlements/bundles/{bundleKey}/archive`

Displays:
- Bundle list with feature counts
- Bundle creation/editing form
- Bundle status management

### Overrides Page

Manage tenant-level entitlement overrides.

**Route**: `/admin/entitlements/overrides`

**APIs**:
- `GET /api/v1/admin/entitlements/overrides`
- `POST /api/v1/admin/entitlements/overrides`
- `PUT /api/v1/admin/entitlements/overrides/{id}`
- `POST /api/v1/admin/entitlements/overrides/{id}/disable`
- `POST /api/v1/admin/entitlements/overrides/{id}/archive`

### Grants Page

Manage all entitlement grants.

**Route**: `/admin/entitlements/grants`

**APIs**:
- `GET /api/v1/admin/entitlements/grants?subjectId={id}`
- `POST /api/v1/admin/entitlements/grants`
- `POST /api/v1/admin/entitlements/grants/{grantId}/revoke`
- `POST /api/v1/admin/entitlements/grants/{grantId}/extend`

### Billing Plans Page

Manage subscription plans.

**Route**: `/admin/billing/plans`

**APIs**:
- `POST /api/v1/admin/billing/plans`
- `GET /api/v1/admin/billing/plans`

### Pricing Page

Manage pricing rules, custom pricing, and discount policies.

**Route**: `/admin/billing/pricing`

**APIs**:
- `POST /api/v1/admin/billing/pricing-rules`
- `GET /api/v1/admin/billing/pricing-rules`
- `POST /api/v1/admin/billing/custom-pricing`
- `POST /api/v1/admin/billing/discount-policies`
- `GET /api/v1/admin/billing/discount-policies`
- `POST /api/v1/admin/billing/pricing-preview`

## Export Panel Integration

The Export Panel validates export requests before submission:

```typescript
// In ExportPanel.vue
const validateAndExport = async () => {
  const validation = await exportValidationAPI.validate({
    preset: selectedPreset.value,
    outputFormat: selectedFormat.value,
    estimatedDurationSeconds: estimatedDuration.value,
  });

  if (!validation.allowed) {
    // Show upgrade modal
    showUpgradeModal({
      reason: validation.userFriendlyMessage,
      upgradeOptions: validation.upgradeOptions,
      recommendedPreset: validation.legacyValidation.recommendedPreset,
    });
    return;
  }

  // Proceed with export
  submitExportJob();
};
```

## Prompt UI Integration

The Prompt Management UI checks entitlements before executing prompts:

```typescript
// Check prompt execution entitlement
const checkPromptAccess = async () => {
  const decision = await accessCheckAPI.check({
    featureKey: 'prompt.executions',
    requestedQuota: estimatedTokens,
  });

  if (!decision.allowed) {
    showUpgradePrompt(decision.upgradeOptions);
    return false;
  }
  return true;
};
```

## Extension UI Integration

The Extension Management UI checks entitlements before installing/executing extensions:

```typescript
const checkExtensionAccess = async (extensionId: string) => {
  const decision = await accessCheckAPI.check({
    featureKey: 'extension.executions',
    requestedQuota: 1,
  });

  if (!decision.allowed) {
    showUpgradePrompt(decision.upgradeOptions);
    return false;
  }
  return true;
};
```

## Navigation Guard

All entitlement management pages are protected by the navigation decision service. The frontend router checks route visibility before rendering:

```typescript
router.beforeEach(async (to, _from, next) => {
  const profile = await navigationStore.fetchProfile();
  const route = profile.routes.find(r => r.routeKey === to.name);

  if (!route?.visible) {
    next('/404');
  } else if (!route?.enabled) {
    next({ name: 'upgrade', query: { feature: to.name, ...route.upgradeOptions } });
  } else {
    next();
  }
});
```
