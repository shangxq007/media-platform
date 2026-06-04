# Frontend Feature Flag Management

> **Last Updated:** 2026-05-16

---

## Overview

The Feature Flag Management UI provides a complete CRUD interface for managing feature flags, targeting rules, and evaluation preview. It is part of the admin console and restricted to admin users.

---

## FeatureFlagManagementPage

### Flag List

Displays all feature flags with:
- Flag key, name, description
- Type badge (BOOLEAN, STRING, NUMBER, JSON)
- Status badge (Active/Disabled)
- Owner
- Tags

### Search & Filter

- **Search**: By flag key, name, owner (case-insensitive)
- **Type Filter**: ALL, BOOLEAN, STRING, NUMBER, JSON
- **Status Filter**: ALL, ACTIVE, DISABLED

### Actions

| Action | Description |
|--------|-------------|
| Create | Opens editor for new flag |
| Edit | Opens editor with existing flag data |
| Enable | Enables a disabled flag |
| Disable | Disables an enabled flag |
| Archive | Archives a flag (soft delete) |

---

## FeatureFlagEditor

### Form Fields

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| flagKey | text | Yes | Unique identifier |
| name | text | Yes | Display name |
| description | text | No | Description |
| type | select | Yes | BOOLEAN/STRING/NUMBER/JSON |
| defaultValue | text | Yes | Default value |
| owner | text | No | Owner identifier |
| tags | text | No | Comma-separated tags |
| enabled | toggle | Yes | Initial enabled state |

### Variants

- Add/remove variants
- Each variant has: key, value, description
- Used for A/B testing and multi-variant flags

### Targeting Rules

- Add/remove targeting rules
- Opens `FeatureFlagRuleEditor` modal
- Rules displayed with priority, name, percentage

---

## FeatureFlagRuleEditor

### Form Fields

| Field | Type | Description |
|-------|------|-------------|
| name | text | Rule display name |
| priority | number | Evaluation order (lower = first) |
| percentage | number | Rollout percentage (0-100) |
| variantKey | text | Variant to serve when matched |
| startAt | datetime | Rule start time (optional) |
| endAt | datetime | Rule end time (optional) |

### Conditions

Each condition has:
- **Attribute**: tenant, workspace, user, role, group, tier, region, requestSource, environment
- **Operator**: EQUALS, IN, NOT_IN, GT, LT, GTE, LTE, CONTAINS
- **Value**: Condition value

### Validation

- Percentage must be 0-100
- Priority must be positive
- At least one condition or percentage required
- startAt must be before endAt

---

## FeatureFlagEvaluationPreview

### Context Input

| Field | Description |
|-------|-------------|
| tenant | Tenant ID |
| workspace | Workspace ID |
| user | User ID |
| role | User role |
| group | User group |
| tier | User tier |
| region | Geographic region |
| requestSource | Request source (api, web, mobile) |
| environment | Environment (prod, staging, dev) |

### Evaluation Result

Displays:
- **flagKey**: Evaluated flag
- **enabled**: Boolean result
- **variant**: Matched variant
- **matchedRule**: ID of matched rule
- **reason**: Reason code (RULE_MATCHED, FLAG_DISABLED, etc.)
- **steps**: Evaluation step-by-step trace

---

## API Integration

All operations use `FeatureFlagAPI`:

```typescript
// CRUD
FeatureFlagAPI.listFeatureFlags()
FeatureFlagAPI.getFeatureFlag(flagKey)
FeatureFlagAPI.createFeatureFlag(flag)
FeatureFlagAPI.updateFeatureFlag(flagKey, updates)
FeatureFlagAPI.archiveFeatureFlag(flagKey)
FeatureFlagAPI.enableFeatureFlag(flagKey)
FeatureFlagAPI.disableFeatureFlag(flagKey)

// Evaluation
FeatureFlagAPI.evaluateFeatureFlag(flagKey, context)
FeatureFlagAPI.getEvaluationLogs(params)
FeatureFlagAPI.getFeatureFlagSummary()
```

---

## State Management

The UI uses Vue 3 Composition API with reactive state:
- `loading`: Loading indicator
- `flags`: Flag list
- `error`: Error message
- `activeTab`: Current tab (flags/preview/logs)
- `showEditor`: Editor visibility
- `editingFlag`: Flag being edited
- `searchQuery`: Search filter
- `filterType`: Type filter
- `filterStatus`: Status filter
