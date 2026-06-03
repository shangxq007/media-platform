# Frontend User Portal

> **Last Updated:** 2026-05-16

---

## Overview

The user-facing portal provides a personalized dashboard and account management pages. Feature flags control which modules and features are visible to each user.

---

## Pages

### UserDashboardPage

The main dashboard showing:
- Capability summary (tier, quotas, limits)
- Usage metrics (render minutes, storage, API calls, exports)
- Feature flags enabled for the user
- Recent projects and exports

**Feature Flag Integration**: The dashboard reads `capabilities.featureFlags` from the API and displays enabled beta features.

### MyCapabilitiesPage

Displays the user's entitlement capabilities:
- Current tier and plan details
- Export formats and presets
- Provider access
- Feature flags section showing all flags and their states

**Feature Flag Section**: Lists all feature flags with display name, description, and enabled/disabled status.

### MyUsagePage

Shows resource consumption against quotas:
- Render minutes (used / limit)
- Storage (used / limit)
- API calls (used / limit)
- Exports (used / limit)
- GPU minutes
- Prompt executions
- Extension executions

Period selector: Current Month, Last Month, Custom Range.

### MyBillingPage

Billing and subscription management:
- Current plan card with pricing
- Billing history entries
- Invoices list
- Plan features

### MyFeedbackPage

User feedback submission:
- Submit feedback dialog (BUG, FEATURE, GENERAL)
- Severity levels (low, medium, high, critical)
- Status tracking (OPEN, IN_PROGRESS, RESOLVED, CLOSED)

### BetaFeaturesPanel

Opt-in panel for experimental features:
- Lists available beta features
- Toggle enable/disable per feature
- Risk badges (Low, Medium, High)
- Shows scope and tier information
- Filters out INTERNAL tier features

---

## Navigation

The user portal navigation includes:
- Dashboard
- My Projects
- My Usage
- My Capabilities
- My Billing
- My Settings
- My Feedback
- Beta Features (controlled by `nav.beta_features.enabled`)

---

## Feature Flag Display

### Badge Components

| Component | Purpose |
|-----------|---------|
| `FeatureBadge` | Shows tier/scope badge (beta, PRO, etc.) |
| `RiskBadge` | Shows risk level (Low, Medium, High) |
| `StatusBadge` | Shows enabled/disabled status |
| `FeatureFlagIndicator` | Inline flag status indicator |

### Disabled State

When a feature is disabled by feature flag:
- `DisabledFeatureState` component renders a message
- Uses i18n error messages from `error-codes.json`
- Both English and Chinese messages supported

---

## API Integration

All pages use `MeEntitlementAPI`:

```typescript
MeEntitlementAPI.getMyCapabilities()  // Capabilities + feature flags
MeEntitlementAPI.getUsageSummary()    // Usage metrics
MeEntitlementAPI.getCurrentPlan()     // Billing plan
MeEntitlementAPI.getBillingHistory()  // Ledger entries
MeEntitlementAPI.getInvoices()        // Invoices
```

---

## i18n

All user-facing text supports English and Chinese locales. Feature flag error messages use the `FF-` error code prefix with translations from the shared `error-codes.json`.
