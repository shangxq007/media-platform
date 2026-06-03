# User Portal

> **Module:** `frontend/src/pages/user/`
> **Last Updated:** 2026-05-19

## Overview

The user portal provides self-service access to projects, capabilities, usage, billing, credits, and feedback.

## Implementation Status

| Component | Status |
|-----------|--------|
| `UserDashboardPage` | ✅ Implemented - Enhanced with Quick Actions, Onboarding Panel, real dashboard API |
| `MyProjectsPage` | ✅ Implemented - Enhanced with real API data loading |
| `MySharedResourcesPage` | ✅ Implemented (New in Prompt 69) |
| `MyCapabilitiesPage` | ✅ Implemented - Enhanced with FF/Entitlement/Quota/Billing/Provider Access |
| `MyUsagePage` | ✅ Implemented |
| `MyBillingPage` | ✅ Implemented |
| `MyCreditsPage` | ✅ Implemented |
| `MyExportsPage` | ✅ Implemented (New in Prompt 69) |
| `MyReportsPage` | ✅ Implemented |
| `MyFeedbackPage` | ✅ Implemented - Enhanced with real API submit/load |
| `MyNotificationsPage` | ✅ Implemented (New in Prompt 69) |
| `MySettingsPage` | ✅ Implemented |
| `UserOnboardingPanel` | ✅ Implemented (New in Prompt 69) |
| `GettingStartedChecklist` | ✅ Implemented via UserOnboardingPanel |
| `BetaFeaturesPanel` | ✅ Implemented |
| `AnalyticsAssistantPage` | ✅ Implemented |
| `EntitlementExplanationPanel` | ✅ Implemented |

## Pages

| Page | Route | Component | Purpose |
|------|-------|-----------|---------|
| Dashboard | `/` | `UserDashboardPage` | Overview with quick actions |
| My Projects | `/me/projects` | `MyProjectsPage` | Project list |
| Capabilities | `/me/capabilities` | `MyCapabilitiesPage` | Feature access by tier |
| Usage | `/me/usage` | `MyUsagePage` | Usage statistics |
| Billing | `/me/billing` | `MyBillingPage` | Billing overview |
| Credits | `/me/credits` | `MyCreditsPage` | Credit wallet |
| Feedback | `/me/feedback` | `MyFeedbackPage` | Submit feedback |
| Settings | `/me/settings` | `MySettingsPage` | User settings |
| Beta Features | `/me/beta` | `BetaFeaturesPanel` | Beta feature access |
| Analytics | `/me/analytics` | `AnalyticsAssistantPage` | NLQ analytics |
| Reports | `/me/reports` | `MyReportsPage` | Saved reports |

## Dashboard

The dashboard provides:
- Quick project creation
- Recent projects list
- Usage summary (render minutes, storage)
- Credit balance
- Active feature flags
- Notification center

## MyCapabilitiesPage

The capabilities page is the primary user-facing view of the entitlement and feature flag system.

### Layout

```
PageHeader: "Capabilities" + Tier Badge + Refresh Button
├── Grid (3 columns)
│   ├── CurrentPlanPanel
│   ├── UsageSummaryPanel
│   └── UpgradeSuggestionPanel
├── PageSection: "Plan Overview"
│   └── Grid (4 MetricCards): Tier, Render Minutes, Concurrent Jobs, Export Formats
├── PageSection: "Feature Flags"
│   └── Grid (2 columns): Flag display name, description, ON/OFF status
├── Grid (2 columns)
│   ├── PageSection: "Entitlement Policy"
│   │   └── Key-value pairs: Max Resolution, Monthly Minutes, Concurrent Jobs,
│   │       GPU Allowed, Remote Worker, Custom Fonts, Watermark
│   └── PageSection: "Export Capabilities"
│       └── Key-value pairs: Formats (as badges), Presets count,
│           Max Resolution, GPU Export, Concurrent Exports
└── UpgradeHint (shown for non-ENTERPRISE tiers)
```

### Data Loading

```typescript
const capabilities = ref<MyCapabilities | null>(null)

async function loadCapabilities() {
  capabilities.value = await MeEntitlementAPI.getMyCapabilities()
}
```

The `MeEntitlementAPI.getMyCapabilities()` calls `GET /api/v1/entitlements/me/capabilities`.

### MyCapabilities Type

```typescript
interface MyCapabilities {
  tier: string                            // "FREE" | "PRO" | "TEAM" | "ENTERPRISE"
  entitlementPolicy: {
    maxResolutionWidth: number
    maxResolutionHeight: number
    monthlyRenderMinutes: number
    maxConcurrentJobs: number
    gpuAllowed: boolean
    remoteWorkerAllowed: boolean
    customFontsAllowed: boolean
    watermark: boolean
  }
  featureFlags: FeatureFlag[]
  exportCapabilities: {
    allowedFormats: string[]       // e.g. ["mp4", "webm", "mov"]
    allowedPresets: string[]       // e.g. ["default_1080p", "pro_1080p"]
    maxResolutionWidth: number
    maxResolutionHeight: number
    gpuExportAllowed: boolean
    maxConcurrentExports: number
  }
}

interface FeatureFlag {
  flagKey: string
  displayName: string
  description: string
  enabled: boolean
  scope: string
  targetTier: string
}
```

### Tier Comparison Display

| Feature | FREE | PRO | TEAM | ENTERPRISE |
|---------|------|-----|------|------------|
| Max Resolution | 720p | 1080p | 4K | 4K |
| Monthly Minutes | 60 | 300 | 1,200 | 6,000 |
| Watermark | Yes | No | No | No |
| GPU | No | No | Yes | Yes |
| Custom Fonts | No | Yes | Yes | Yes |
| Concurrent Jobs | 1 | 3 | 10 | 50 |
| Export Formats | mp4, webm | +mov | +dash, hls | +cmaf |

## Beta Features Panel

Shows features gated by feature flags:
- Only visible when `beta-features` flag is enabled for the user
- Lists beta features with descriptions
- Allows opt-in/opt-out

## CurrentPlanPanel

Displays the user's current subscription plan with:
- Plan name and tier
- Renewal date
- Usage progress bar (render minutes used / total)

## UsageSummaryPanel

Shows usage statistics:
- Render minutes used this month
- Storage used
- Jobs completed
- Credits remaining

## UpgradeSuggestionPanel

Contextual upgrade suggestions based on current tier:
- FREE → PRO: "Unlock 1080p export and remove watermarks"
- PRO → TEAM: "Enable GPU rendering and 4K export"
- TEAM → ENTERPRISE: "Priority queue and dedicated support"

## EntitlementExplanationPanel

Explains why a feature is available or not:
- Shows the matched policy (tier, grant, or override)
- Shows upgrade path if feature is not available
- Links to the capabilities page
