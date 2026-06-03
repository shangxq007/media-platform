# ABAC Feature Flag Integration

> **Module:** `policy-governance-module`
> **Last Updated:** 2026-05-16

---

## Overview

The media platform uses Feature Flags as an input to Attribute-Based Access Control (ABAC) decisions. Feature flag state is treated as a dynamic attribute that can be evaluated alongside traditional attributes (tenant, user, role, tier, etc.) to make authorization decisions.

---

## ABAC Model

```
┌─────────────────────────────────────────────────────────┐
│                    ABAC Decision Flow                    │
│                                                         │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │  Subject     │  │  Resource    │  │  Environment │  │
│  │  Attributes  │  │  Attributes  │  │  Attributes  │  │
│  │              │  │              │  │              │  │
│  │  - userId    │  │  - type      │  │  - time      │  │
│  │  - role      │  │  - owner     │  │  - source    │  │
│  │  - tier      │  │  - status    │  │  - region    │  │
│  │  - tenantId  │  │              │  │              │  │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘  │
│         │                 │                  │          │
│         └────────────┬────┴──────────────────┘          │
│                      ▼                                  │
│         ┌────────────────────────────┐                  │
│         │   Feature Flag Attributes  │                  │
│         │                            │                  │
│         │  - flagKey.enabled: bool   │                  │
│         │  - flagKey.variant: string │                  │
│         │  - flagKey.matchedRule     │                  │
│         └────────────┬───────────────┘                  │
│                      ▼                                  │
│         ┌────────────────────────────┐                  │
│         │    Policy Evaluation       │                  │
│         │                            │                  │
│         │  Rule 1: FF + Role check   │                  │
│         │  Rule 2: FF + Tier check   │                  │
│         │  Rule 3: Tenant match      │                  │
│         │  Rule N: Default Deny      │                  │
│         └────────────┬───────────────┘                  │
│                      ▼                                  │
│         ┌────────────────────────────┐                  │
│         │    AccessDecision          │                  │
│         │                            │                  │
│         │  effect: ALLOW | DENY      │                  │
│         │  matchedFeatureFlags: {}   │                  │
│         └────────────────────────────┘                  │
└─────────────────────────────────────────────────────────┘
```

---

## Feature Flag as ABAC Input

Feature flags serve as **dynamic subject attributes** that can change at runtime without code deployment:

| Traditional Attribute | Feature Flag Attribute |
|----------------------|----------------------|
| `tier` | `export.gpu.v2.enabled` |
| `role` | `nav.beta_features.enabled` |
| `tenantId` | `graphql.queryAggregation.enabled` |
| `region` | `monitoring.openReplay.enabled` |

---

## Decision Flow

1. **Context Collection**: Gather subject, resource, and environment attributes
2. **Feature Flag Evaluation**: Evaluate all feature flags referenced in policy rules
3. **Attribute Enrichment**: Add feature flag results to the evaluation context
4. **Rule Evaluation**: Evaluate policy rules against enriched attributes
5. **Decision**: Return `ALLOW` or `DENY` with matched flags

---

## AccessDecision Integration

`PolicyEvaluationService` serves as the AccessDecisionService:

```java
PolicyDecision decision = service.evaluate(context);
// decision.effect() → ALLOW or DENY
// decision.matchedFeatureFlags() → { "flagKey": boolean }
// decision.matchedRuleId() → which rule was matched
```

### Integration Points

| Service | Usage |
|---------|-------|
| `AccessDecisionService` | General access control (API, resources) |
| `NavigationDecisionService` | Route visibility (UI navigation) |
| `ExportDecisionService` | Export format/feature availability |
| `ExtensionDecisionService` | Extension runtime enable/disable |

---

## Multi-Attribute Rules

Rules can combine feature flag checks with traditional attributes:

```json
{
  "featureFlag": {
    "flagKey": "export.gpu.v2.enabled",
    "operator": "eq",
    "expectedValue": true
  },
  "role": "ADMIN",
  "tier": "enterprise"
}
```

All conditions must match for the rule to apply.

---

## Temporal Attributes

Feature flag rules can include time-based conditions via `startAt` and `endAt`:

```java
FeatureFlagTargetingRule rule = new FeatureFlagTargetingRule(
    "rule-1", "flag-1", 10, true,
    "tenant-1", null, null, null, null, null,
    null, null, null, null,
    Instant.parse("2026-01-01T00:00:00Z"),
    Instant.parse("2026-12-31T23:59:59Z")
);
```

---

## Audit Trail

Every ABAC decision involving feature flags is audited:

- `POLICY_EVALUATED_WITH_FEATURE_FLAG`: Policy used FF as condition
- `ACCESS_DENIED_BY_FEATURE_FLAG`: Access denied due to FF state
- `FLAG_EVALUATED`: Individual flag evaluation result

---

## Relationship with ABAC

Traditional ABAC uses static attributes (role, tier, department). Feature flags add **runtime-dynamic attributes** that can be changed without deployment:

| Aspect | Traditional ABAC | FF-Enhanced ABAC |
|--------|-----------------|-------------------|
| Attribute source | Database/Identity provider | Feature flag provider |
| Change deployment | Code change + redeploy | Toggle flag at runtime |
| Granularity | Per-user/group | Per-user/tenant/percentage |
| Time-based | Static rules | Time-bounded rules |
| Rollout | All-or-nothing | Percentage-based gradual |
