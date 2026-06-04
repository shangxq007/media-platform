# Policy Composition Examples

> **Last Updated:** 2026-05-19

## Overview

This document provides concrete examples of how Feature Flags, Entitlements, Quota, and Billing policies compose together in the access decision chain.

## Decision Chain

The full decision chain in the platform:

```
Authentication
  → RBAC (Role + Permission check)
  → Feature Flag evaluation
  → ABAC (PolicyEvaluationService)
  → Entitlement (EntitlementDecisionService)
  → Quota (QuotaDecisionService)
  → Billing (BillingDecisionService)  ⚠️ Standalone, not wired into chain
  → AccessDecision (final result)
```

## Example 1: Feature Flag Gates a New Export Preset

**Scenario**: A new `gpu_h264` export preset is being rolled out to 10% of TEAM users.

### Feature Flag Definition

```json
{
  "flagKey": "export.gpu.v2.enabled",
  "name": "GPU H264 Export V2",
  "description": "Enable GPU-accelerated H264 export preset",
  "flagType": "BOOLEAN",
  "defaultValue": false,
  "enabled": true,
  "owner": "platform-team",
  "tags": ["export", "gpu", "rollout"],
  "targetingRules": [
    {
      "ruleId": "rule-gpu-v2-team",
      "name": "TEAM tier rollout",
      "priority": 1,
      "enabled": true,
      "tier": "TEAM",
      "percentage": 10
    }
  ]
}
```

### Access Check Flow

1. User (TEAM tier) requests `render.submit` with `preset=gpu_h264`
2. `AccessDecisionService.check()` is called
3. `AccessDecisionFeatureFlagService` evaluates `export.gpu.v2.enabled`
   - User is in the 10% rollout → flag is `enabled=true`
4. `EntitlementDecisionService.evaluate()` checks:
   - No override → skip
   - No workspace member grant → skip
   - No workspace pool → skip
   - No user grant → skip
   - Tier policy: TEAM tier allows `gpu_h264` preset and GPU → `ALLOW`
5. `QuotaDecisionService.evaluate()` checks remaining quota → within limit
6. Result: `AccessDecision(allowed=true, decision="ALLOW")`

### Denied Case

If the user is NOT in the 10% rollout:
1. Feature flag evaluates to `enabled=false`
2. `AccessDecision.disabledByFeatureFlag = true`
3. `AccessDecision.featureFlagReasons = ["Feature flag 'export.gpu.v2.enabled' is disabled (NO_MATCHING_RULE)"]`
4. Entitlement check still passes (TEAM tier allows GPU)
5. Result: `AccessDecision(allowed=false, disabledByFeatureFlag=true)`

## Example 2: Entitlement Override Grants Premium Feature to Free User

**Scenario**: A FREE tier user is granted temporary access to 4K export via an override.

### Override Creation

```json
{
  "subjectType": "USER",
  "subjectId": "user-123",
  "overrideKind": "FEATURE_GRANT",
  "overridePayload": "{\"featureKey\": \"export.4k\", \"expiresAt\": \"2026-06-01T00:00:00Z\"}",
  "effectiveAt": "2026-05-19T00:00:00Z",
  "expiresAt": "2026-06-01T00:00:00Z",
  "status": "ACTIVE"
}
```

### Access Check Flow

1. User (FREE tier) requests export with `preset=team_4k`
2. `EntitlementDecisionService.evaluate()`:
   - Step 1: Check overrides → finds active override for `user-123`
   - Returns `EntitlementDecision(allowed=true, reasonCode="TENANT_OVERRIDE")`
3. Result: `AccessDecision(allowed=true, matchedOverrideId="override-abc")`

## Example 3: Quota Exceeded Blocks Render

**Scenario**: A PRO user has exhausted their monthly render minutes.

### Access Check Flow

1. User (PRO tier) requests `render.submit` with `requestedQuota=30` (minutes)
2. `EntitlementDecisionService.evaluate()`:
   - Tier policy: PRO allows render → `ALLOW`
3. `QuotaDecisionService.evaluate("user-456", "render.minutes", 30)`:
   - `currentUsage = 295` (out of 300 monthly)
   - `remaining = 300 - 295 = 5`
   - `afterRequest = 5 - 30 = -25` → `allowed = false`
4. Result: `AccessDecision(allowed=false, decision="QUOTA_EXCEEDED", quotaRemaining=5, upgradeOptions=["Upgrade to TEAM for more capacity"])`

## Example 4: Combined Feature Flag + Entitlement + Billing

**Scenario**: An enterprise user tries to use a premium AI feature that requires both a feature flag and sufficient budget.

### Policy Evaluation

```java
// 1. Feature Flag check
FeatureFlagDecision ffDecision = featureFlagService.evaluate(
    new FeatureFlagEvaluationRequest("ai.premium.models",
        new FeatureFlagContext(tenantId, workspaceId, userId, ...),
        false)
);
// Result: enabled=true (flag is active for ENTERPRISE tier)

// 2. Entitlement check
EntitlementDecision entDecision = entitlementDecisionService.evaluate(
    new AccessCheckRequest(tenantId, workspaceId, userId, ..., "ai.premium.models", ...)
);
// Result: allowed=true (ENTERPRISE tier includes AI premium)

// 3. Budget check
BudgetCheckResult budgetResult = budgetGuardService.checkBudget(tenantId, 0.50);
// Result: allowed=true (sufficient budget remaining)

// 4. Quota check
QuotaDecision quotaDecision = quotaDecisionService.evaluate(userId, "ai.premium.models", 1);
// Result: allowed=true (within monthly AI execution quota)

// 5. Final decision
// AccessDecision(allowed=true, decision="ALLOW")
```

## Example 5: ABAC Policy with Feature Flag Condition

**Scenario**: A policy rule denies access to a feature when a specific feature flag is disabled.

### Policy Rule Definition

```json
{
  "ruleId": "rule-deny-when-ff-off",
  "name": "Deny when feature flag is off",
  "effect": "DENY",
  "priority": 10,
  "status": "ACTIVE",
  "conditions": "{\"featureFlag\": {\"flagKey\": \"export.gpu.v2.enabled\", \"operator\": \"eq\", \"expectedValue\": false}}"
}
```

### Evaluation

The `PolicyEvaluationService` evaluates this rule by:
1. Parsing the `featureFlag` condition from the rule's JSON conditions
2. Evaluating the feature flag via `FeatureFlagService.evaluate()`
3. If the flag is `false` (matching `expectedValue: false`) → condition is met → `DENY`
4. If the flag is `true` → condition is not met → continue to next rule

## Example 6: Navigation Policy Hides Admin Route

**Scenario**: The admin console should be hidden from users without the admin role.

### Navigation Policy

```java
new NavigationPolicy(
    "policy-hide-admin",       // policyKey
    "admin-dashboard",         // routeKey
    "ROLE_BASED",              // policyType
    "role=ADMIN",              // condition
    "HIDE",                    // effect
    "NAV-403-ROLE",            // reasonCode
    "Admin access required",   // reasonMessage
    List.of(),                 // upgradeOptions
    100,                       // priority
    true                       // enabled
)
```

### Evaluation

1. User without ADMIN role navigates to `/admin`
2. `NavigationDecisionService.evaluateRoute()` checks required roles
3. User doesn't have ADMIN → `visible=false`, reason: `NAV-403-ROLE`
4. Route is hidden from navigation menu

## Error Code Reference for Policy Decisions

| Code | Condition | HTTP |
|------|-----------|------|
| `PERMISSION_DENIED` | RBAC check fails | 403 |
| `POLICY_NOT_MATCHED` | No ABAC rule matches | 403 |
| `FEATURE_FLAG_DISABLED` | Feature flag blocks access | 403 |
| `ENTITLEMENT_DENIED` | Tier/grant/override denies | 403 |
| `QUOTA_EXCEEDED` | Quota limit reached | 409 |
| `BUDGET_EXCEEDED` | Budget limit reached | 403 |
| `NAV-403-TIER` | Navigation requires higher tier | 403 |
| `NAV-403-FF` | Navigation requires feature flag | 403 |
| `NAV-404-HIDDEN` | Route is hidden | 404 |
