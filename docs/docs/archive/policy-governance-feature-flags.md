# Policy Governance Feature Flags

> **Module:** `policy-governance-module`
> **Last Updated:** 2026-05-16

---

## Overview

The policy-governance-module integrates Feature Flags as a first-class condition type in the policy evaluation engine. This allows policies to grant or deny access based on feature flag state, enabling dynamic, runtime-configurable authorization.

---

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                 PolicyEvaluationService                  │
│                                                         │
│  ┌──────────────────────────────────────────────────┐   │
│  │ PolicyRule.conditions (JSON string)              │   │
│  │                                                  │   │
│  │  {                                               │   │
│  │    "featureFlag": {                              │   │
│  │      "flagKey": "export.gpu.v2.enabled",         │   │
│  │      "operator": "eq",                           │   │
│  │      "expectedValue": true                       │   │
│  │    }                                             │   │
│  │  }                                               │   │
│  └──────────────────────────────────────────────────┘   │
│           │                                             │
│           ▼                                             │
│  ┌──────────────────────────────────────────────────┐   │
│  │ extractFeatureFlagConditions()                   │   │
│  │ → List<PolicyFeatureFlagCondition>               │   │
│  └──────────────────────────────────────────────────┘   │
│           │                                             │
│           ▼                                             │
│  ┌──────────────────────────────────────────────────┐   │
│  │ evaluateFeatureFlagConditions()                  │   │
│  │  1. Build FeatureFlagContext from PolicyContext  │   │
│  │  2. Call FeatureFlagService.evaluate()           │   │
│  │  3. Compare result with expected value           │   │
│  │  4. Record audit event                           │   │
│  └──────────────────────────────────────────────────┘   │
│           │                                             │
│           ▼                                             │
│  ┌──────────────────────────────────────────────────┐   │
│  │ PolicyDecision {                                 │   │
│  │   effect: ALLOW | DENY,                          │   │
│  │   matchedRuleId: string,                         │   │
│  │   matchedFeatureFlags: { "flagKey": boolean }    │   │
│  │ }                                                │   │
│  └──────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────┘
```

---

## Policy Condition Format

Feature Flag conditions are expressed as JSON in the `PolicyRule.conditions` field:

```json
{
  "featureFlag": {
    "flagKey": "export.gpu.v2.enabled",
    "operator": "eq",
    "expectedValue": true
  }
}
```

### Fields

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `flagKey` | string | Yes | Feature flag identifier (supports `featureFlag.` prefix) |
| `operator` | string | No | `eq` (default), `ne` |
| `expectedValue` | boolean/string | No | Expected flag state (`true`/`false`) |

### Flag Key Prefix

The `featureFlag.` prefix is automatically stripped:
- `"featureFlag.export.gpu.v2.enabled"` → evaluates `"export.gpu.v2.enabled"`

---

## PolicyFeatureFlagCondition Record

```java
public record PolicyFeatureFlagCondition(
    String flagKey,
    String operator,
    Object expectedValue
)
```

---

## Decision Flow

1. PolicyEvaluationService iterates rules by priority
2. For each rule, `matchesRule()` checks conditions JSON
3. If conditions contain `"featureFlag"` key, delegates to `evaluateFeatureFlagConditions()`
4. Builds `FeatureFlagContext` from `PolicyContext` (tenantId, workspaceId, userId, role, requestSource, attributes)
5. Calls `FeatureFlagService.evaluate()` for each flag condition
6. Evaluates condition: `flagEnabled == expectedValue` (for `eq`) or `flagEnabled != expectedValue` (for `ne`)
7. Records audit event via `FeatureFlagAuditService.auditEvaluated()`
8. Returns `PolicyDecision` with `matchedFeatureFlags` map

---

## PolicyDecision Record

```java
public record PolicyDecision(
    PolicyEffect effect,
    String reason,
    String matchedRuleId,
    Map<String, Boolean> matchedFeatureFlags
)
```

The `matchedFeatureFlags` map contains all evaluated flags and their states, providing full traceability.

---

## Default Deny Rule

The policy engine includes a built-in default deny rule:

```java
PolicyRule defaultDeny = new PolicyRule(
    "rule-default-deny", "Default Deny",
    PolicyEffect.DENY, "{}", 999, "ACTIVE");
```

- Priority 999 (lowest)
- Empty conditions `{}` matches all requests
- Returns `DENY` when no other rule matches

---

## Integration with Feature Flag

The `PolicyEvaluationService` constructor accepts optional `FeatureFlagService` and `FeatureFlagAuditService`:

```java
// Production usage
new PolicyEvaluationService(featureFlagService, auditService);

// Backward-compatible (no feature flags)
new PolicyEvaluationService();
```

When `featureFlagService` is null, feature flag conditions are skipped.

---

## Audit Integration

Every feature flag evaluation within policy context emits:
- `FLAG_EVALUATED` audit event with the decision
- Includes `userId` as the actor
- Full context: `tenantId`, `workspaceId`, `matchedRule`, `variant`, `reason`

---

## Error Handling

- If `featureFlagService` is null: feature flag conditions are skipped (rule does not match)
- If flag evaluation throws: exception propagates (logged by caller)
- If flag key has `featureFlag.` prefix: automatically stripped before evaluation
- If operator is null: defaults to `eq`
