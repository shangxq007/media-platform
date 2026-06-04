# Feature Flag Management UI

> **Module:** `frontend/src/pages/admin/`
> **Last Updated:** 2026-05-19

## Overview

The feature flag management UI allows admins to create, configure, and monitor feature flags that control feature access across the platform. It consists of 5 components with tab-based navigation.

## Implementation Status

| Component | Status |
|-----------|--------|
| `FeatureFlagManagementPage` | ✅ Implemented |
| `FeatureFlagEditor` | ✅ Implemented |
| `FeatureFlagRuleEditor` | ✅ Implemented |
| `FeatureFlagEvaluationPreview` | ✅ Implemented |
| `FeatureFlagEvaluationLog` | ✅ Implemented |
| `FeatureFlags` (list) | ✅ Implemented |
| `FeatureFlagIndicator` | ✅ Implemented |
| `useFeatureFlag` composable | ✅ Implemented |
| `BetaFeaturesPanel` (user portal) | ✅ Implemented |

## Pages

| Page | Component | Purpose |
|------|-----------|---------|
| Flag List | `FeatureFlagManagementPage` | List, search, filter, create, edit, toggle, archive flags |
| Flag Editor | `FeatureFlagEditor` | Create/edit flag definition with variants and targeting rules |
| Rule Editor | `FeatureFlagRuleEditor` | Configure targeting rules with conditions, percentage, time windows |
| Evaluation Preview | `FeatureFlagEvaluationPreview` | Preview flag evaluation for specific context |
| Evaluation Log | `FeatureFlagEvaluationLog` | View evaluation audit trail |

## FeatureFlagManagementPage

The main flag management page with three tabs:

### Flags Tab
- **Search**: by key, name, or owner
- **Filter by type**: ALL, BOOLEAN, STRING, NUMBER, JSON
- **Filter by status**: ALL, ACTIVE, DISABLED
- **Table columns**: Key, Name, Type, Status, Owner, Rules count, Modified date, **Actions**
- **Actions**: Edit, Enable/Disable, Archive
- **Empty state**: with "New Flag" action button

### Evaluation Preview Tab
- Select a flag from dropdown
- Enter evaluation context (tenant, workspace, user, role, tier, region, etc.)
- Display evaluation result: enabled/disabled, matched rule, variant

### Evaluation Logs Tab
- View recent flag evaluation audit events
- Filter by flag key
- Show timestamp, actor, flag key, result, reason

## FeatureFlagEditor

Full-screen modal for creating/editing feature flags:

### Basic Fields
- Flag Key (disabled for edits)
- Name, Description
- Type (BOOLEAN, STRING, NUMBER, JSON)
- Default Value
- Owner
- Tags (comma-separated)
- Enabled toggle

### Variants Section
- Add/remove variants (key + value pairs)
- Used for A/B testing

### Targeting Rules Section
- List existing rules with priority, name, percentage, conditions count
- Add/remove/edit rules
- Opens `FeatureFlagRuleEditor` modal

### API Integration
```typescript
// Create
await FeatureFlagAPI.createFeatureFlag(payload)

// Update
await FeatureFlagAPI.updateFeatureFlag(flagKey, payload)

// Toggle
await FeatureFlagAPI.enableFeatureFlag(flagKey)
await FeatureFlagAPI.disableFeatureFlag(flagKey)

// Archive
await FeatureFlagAPI.archiveFeatureFlag(flagKey)
```

## FeatureFlagRuleEditor

Modal for creating/editing targeting rules:

### Rule Fields
- Rule Name
- Priority (lower = evaluated first)
- Percentage (0-100, slider control)
- Variant Assignment (optional)
- Start At / End At (datetime picker for time windows)

### Condition Builder
- **Attributes**: tenant, workspace, user, role, group, tier, region, requestSource, environment
- **Operators**: EQUALS, IN, NOT_IN, GT, LT, GTE, LTE, CONTAINS
- **Value**: text input (comma-separated for IN/NOT_IN)
- Add/remove conditions dynamically

### Targeting Rule Types

| Rule Type | Description | Example |
|-----------|-------------|---------|
| Tenant | Match specific tenant ID | `tenant-123` → enabled |
| Workspace | Match specific workspace ID | `workspace-456` → enabled |
| User | Match specific user ID | `user-789` → enabled |
| Role | Match specific role | `ADMIN` → enabled |
| Tier | Match specific tier | `ENTERPRISE` → enabled |
| Percentage | Hash-based percentage rollout | 10% of users → enabled |
| Time Window | Active during time range | 2026-06-01 to 2026-06-30 |
| Region | Match specific region | `us-east-1` → enabled |

## FeatureFlagEvaluationPreview

Interactive evaluation simulator:

1. Select a flag from the loaded flags list
2. Fill in context fields (tenant, workspace, user, role, tier, etc.)
3. Submit evaluation request
4. View result: enabled/disabled, matched rule, variant, reason code

## API Client

The `FeatureFlagAPI` client (`src/api/admin/feature-flags.ts`) provides:

```typescript
interface FeatureFlagAPI {
  listFeatureFlags(): Promise<FeatureFlagDefinition[]>
  getFeatureFlag(flagKey: string): Promise<FeatureFlagDefinition>
  createFeatureFlag(flag: FeatureFlagDefinition): Promise<FeatureFlagDefinition>
  updateFeatureFlag(flagKey: string, flag: FeatureFlagDefinition): Promise<FeatureFlagDefinition>
  deleteFeatureFlag(flagKey: string): Promise<void>
  archiveFeatureFlag(flagKey: string): Promise<void>
  enableFeatureFlag(flagKey: string): Promise<void>
  disableFeatureFlag(flagKey: string): Promise<void>
  evaluateFlag(flagKey: string, context: FeatureFlagEvaluationContext): Promise<FeatureFlagEvaluationResult>
  getEvaluationLogs(flagKey: string): Promise<FeatureFlagAuditEvent[]>
}
```

## Domain Types

```typescript
interface FeatureFlagDefinition {
  flagKey: string
  name: string
  description: string
  type: 'BOOLEAN' | 'STRING' | 'NUMBER' | 'JSON'
  defaultValue: string
  variants: FeatureFlagVariant[]
  targetingRules: FeatureFlagTargetingRule[]
  enabled: boolean
  owner: string
  tags: string[]
  createdAt: string
  updatedAt: string
  archived: boolean
}

interface FeatureFlagTargetingRule {
  ruleId?: string
  priority: number
  name: string
  percentage: number
  conditions: FeatureFlagCondition[]
  variantKey?: string
  startAt?: string
  endAt?: string
}

interface FeatureFlagCondition {
  attribute: string
  operator: string
  value: string
}

interface FeatureFlagVariant {
  key: string
  value: string
  description?: string
}

interface FeatureFlagEvaluationContext {
  tenant: string
  workspace: string
  user: string
  role: string
  group: string
  tier: string
  region: string
  requestSource: string
  environment: string
}

interface FeatureFlagEvaluationResult {
  flagKey: string
  enabled: boolean
  variant: string
  reasonCode: string
  matchedRule: string
}
```

## User-Facing Beta Features

The `BetaFeaturesPanel` in the user portal shows:
- Features gated by feature flags with `beta` tag
- Opt-in/opt-out toggle
- Feature description and status

The `useFeatureFlag` composable provides:
```typescript
const { isEnabled, loading, error } = useFeatureFlag('flag-key')
```

## DisabledFeatureState Component

When a feature is disabled by flag, the `DisabledFeatureState` component shows:
- A lock icon
- "Feature disabled" message
- Optional upgrade hint
