# Frontend Admin Console

> **Last Updated:** 2026-05-16

---

## Overview

The admin console provides platform management capabilities. Access is restricted to admin-tier users. Feature flag `admin.enabled` controls visibility of admin sections.

---

## Access Control

The admin console is hidden from normal users:
- Non-admin users see the user dashboard instead
- Admin-only sections are not rendered for regular users
- Feature flag `admin.feature_flags.enabled` controls feature flag management access

---

## Admin Pages

### FeatureFlagManagementPage

Central hub for feature flag administration:

**Tabs:**
- **Flags**: List, search, filter, create, edit, archive
- **Preview**: Evaluate flags with custom context
- **Logs**: View evaluation audit trail

**Capabilities:**
- Create new flags (BOOLEAN, STRING, NUMBER, JSON)
- Edit existing flags
- Enable/disable/archive flags
- Add targeting rules
- Search by key, name, owner
- Filter by type and status

### FeatureFlagEditor

Form for creating/editing feature flags:
- Flag key, name, description
- Type selector (BOOLEAN, STRING, NUMBER, JSON)
- Default value
- Enabled toggle
- Owner and tags
- Variants management
- Targeting rules management

### FeatureFlagRuleEditor

Form for creating/editing targeting rules:
- Rule name and priority
- Percentage rollout (0-100)
- Conditions (attribute, operator, value)
- Variant key
- Time range (startAt, endAt)

**Condition Attributes:** tenant, workspace, user, role, group, tier, region, requestSource, environment

**Condition Operators:** EQUALS, IN, NOT_IN, GT, LT, GTE, LTE, CONTAINS

### FeatureFlagEvaluationPreview

Interactive evaluation tool:
- Select a flag from dropdown
- Set context fields (tenant, workspace, user, role, group, tier, region, requestSource, environment)
- Evaluate and see result
- View evaluation steps
- Reset context

### FeatureFlagEvaluationLog

Audit log viewer:
- Filter by flag key, tenant, workspace, user
- Filter by result (enabled/disabled)
- Paginated entries

### EntitlementManagementPage

Manage entitlement bundles, tenant overrides, and user grants.

### ExtensionQuotaInfo

Display extension quota information with risk levels:
- Execution quota and usage
- Cost estimation
- Risk level badge (LOW, MEDIUM, HIGH, CRITICAL)

### MonitoringFeedbackPage

Platform monitoring and feedback:
- Sentry status
- OpenReplay status (controlled by `monitoring.openReplay.enabled` enabled)
- Feedback summary metrics
- Problematic data summary

---

## Feature Flag Control

Admin console visibility is controlled by:

| Flag Key | Controls |
|----------|----------|
| `admin.enabled` | Overall admin console access |
| `admin.feature_flags.enabled` | Feature flag management page |
| `nav.admin.enabled` | Admin navigation entries |
| `admin.entitlements.enabled` | Entitlement management |

---

## Route Configuration

Admin routes support `requiredFeatureFlags`:

```typescript
{
  path: '/admin/feature-flags',
  name: 'FeatureFlagManagement',
  requiredFeatureFlags: ['admin.feature_flags.enabled', 'nav.admin.enabled']
}
```

Routes are only accessible when all required feature flags are enabled for the user.
