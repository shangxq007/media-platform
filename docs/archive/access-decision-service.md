# Access Decision Service

> **Module:** `policy-governance-module`
> **Last Updated:** 2026-05-16

---

## Overview

The AccessDecisionService (`PolicyEvaluationService`) evaluates access requests against a set of policy rules. It supports Feature Flag conditions as a first-class citizen in the policy evaluation engine, enabling dynamic, runtime-configurable authorization.

---

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                AccessDecisionService                     │
│                (PolicyEvaluationService)                 │
│                                                         │
│  ┌──────────────────────────────────────────────────┐   │
│  │ PolicyContext                                     │   │
│  │  - userId, role, tenantId, workspaceId           │   │
│  │  - resourceType, requestSource, attributes       │   │
│  └──────────────────────┬───────────────────────────┘   │
│                         │                               │
│                         ▼                               │
│  ┌──────────────────────────────────────────────────┐   │
│  │ Rule Evaluation (by priority)                    │   │
│  │                                                  │   │
│  │  1. Parse conditions JSON                        │   │
│  │  2. Check traditional attributes                 │   │
│  │  3. Evaluate feature flag conditions             │   │
│  │  4. Return first matching rule's effect          │   │
│  └──────────────────────┬───────────────────────────┘   │
│                         │                               │
│                         ▼                               │
│  ┌──────────────────────────────────────────────────┐   │
│  │ PolicyDecision                                   │   │
│  │  - effect: ALLOW | DENY                          │   │
│  │  - reason: string                                │   │
│  │  - matchedRuleId: string                         │   │
│  │  - matchedFeatureFlags: Map<string, boolean>     │   │
│  └──────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────┘
```

---

## PolicyContext

```java
public record PolicyContext(
    String userId,
    String role,
    String tenantId,
    String workspaceId,
    String resourceType,
    String requestSource,
    Map<String, Object> attributes
)
```

---

## PolicyRule

```java
public record PolicyRule(
    String id,
    String name,
    PolicyEffect effect,
    String conditions,    // JSON string
    int priority,
    String status         // "ACTIVE" or "INACTIVE"
)
```

---

## Decision Chain

1. **Collect Rules**: Get all ACTIVE rules sorted by priority
2. **Evaluate Each Rule**:
   - Parse conditions JSON
   - Check traditional attribute conditions (tenantId, workspaceId, userId, role)
   - If conditions contain `"featureFlag"`, delegate to FeatureFlagService
3. **Return Decision**: First matching rule's effect, or Default Deny
4. **Audit**: Record FLAG_EVALUATED event for each flag evaluation

---

## Feature Flag Condition Evaluation

### Step 1: Extract Conditions

```java
List<PolicyFeatureFlagCondition> conditions = extractFeatureFlagConditions(conditionsJson);
```

Parses JSON like:
```json
{
  "featureFlag": {
    "flagKey": "export.gpu.v2.enabled",
    "operator": "eq",
    "expectedValue": true
  }
}
```

### Step 2: Build FeatureFlagContext

```java
FeatureFlagContext ffContext = new FeatureFlagContext(
    context.tenantId(), context.workspaceId(), context.userId(),
    List.of(context.role()), List.of(), null,
    context.requestSource(), null, null, null,
    context.attributes()
);
```

### Step 3: Evaluate Each Flag

```java
FeatureFlagEvaluationResult result = featureFlagService.evaluate(
    new FeatureFlagEvaluationRequest(flagKey, ffContext, false));
```

### Step 4: Compare Result

```java
boolean conditionMet = evaluateFlagCondition(ffCondition, decision.enabled());
// eq: flagEnabled == expectedValue
// ne: flagEnabled != expectedValue
```

---

## PolicyEffect

| Effect | Description |
|--------|-------------|
| `ALLOW` | Access granted |
| `DENY` | Access denied |
| `REQUIRE_REVIEW` | Requires manual review |
| `DEGRADE` | Grant access with reduced quality |
| `WARN` | Grant access with warning |

---

## PolicyDecision

```java
public record PolicyDecision(
    PolicyEffect effect,
    String reason,
    String matchedRuleId,
    Map<String, Boolean> matchedFeatureFlags
)
```

The `matchedFeatureFlags` map provides full traceability of all evaluated flags.

---

## Default Deny

When no rule matches:
```java
return new PolicyDecision(
    PolicyEffect.DENY,
    "No matching policy rule found",
    "none",
    Map.copyOf(evaluatedFlags)
);
```

---

## Integration with Feature Flag

```java
// Production
PolicyEvaluationService service = new PolicyEvaluationService(
    featureFlagService, auditService);

// Without feature flags (backward compatible)
PolicyEvaluationService service = new PolicyEvaluationService();
```

---

## Audit Events

| Event | When |
|-------|------|
| `FLAG_EVALUATED` | Each flag evaluated during policy check |
| `POLICY_EVALUATED_WITH_FEATURE_FLAG` | Policy used FF as condition |
| `ACCESS_DENIED_BY_FEATURE_FLAG` | Access denied due to FF state |

---

## Example: GPU Export Access Control

**Policy Rule:**
```json
{
  "id": "gpu-export-access",
  "name": "GPU Export Access",
  "effect": "ALLOW",
  "conditions": "{\"featureFlag\":{\"flagKey\":\"export.gpu.v2.enabled\",\"operator\":\"eq\",\"expectedValue\":true}}",
  "priority": 5,
  "status": "ACTIVE"
}
```

**Evaluation:**
1. User requests GPU export
2. Policy engine evaluates `export.gpu.v2.enabled` flag
3. If flag is enabled → ALLOW
4. If flag is disabled → rule doesn't match → next rule or Default Deny
