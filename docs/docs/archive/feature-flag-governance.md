# Feature Flag Governance

> **Module:** `policy-governance-module`
> **Last Updated:** 2026-05-16

---

## Overview

The Feature Flag system provides runtime control over feature availability across the media platform. It is implemented in the `policy-governance-module` and supports two provider backends: a local in-memory provider and OpenFeature (with Unleash as the remote provider).

---

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                   policy-governance-module               │
│                                                         │
│  ┌─────────────────┐    ┌──────────────────────────┐   │
│  │ FeatureFlagService│──▶│ FeatureFlagEvaluator      │   │
│  │ (Spring @Service) │   │ (Interface API)           │   │
│  └────────┬──────────┘   └──────────────────────────┘   │
│           │                                             │
│  ┌────────┴──────────┐   ┌──────────────────────────┐   │
│  │ LocalFeatureFlag  │   │ OpenFeatureFlagEvaluator  │   │
│  │ Provider          │   │ (OpenFeature SDK Client)  │   │
│  └───────────────────┘   └──────────────────────────┘   │
│           │                         │                   │
│  ┌────────┴─────────────────────────┴───────────────┐   │
│  │              OpenFeatureContextMapper             │   │
│  │         (FeatureFlagContext → EvaluationContext)  │   │
│  └──────────────────────────────────────────────────┘   │
│                                                         │
│  ┌──────────────────────────────────────────────────┐   │
│  │           FeatureFlagAuditService                 │   │
│  │   (15 audit event types, AuditPort integration)   │   │
│  └──────────────────────────────────────────────────┘   │
│                                                         │
│  ┌──────────────────────────────────────────────────┐   │
│  │           FeatureFlagController                   │   │
│  │   (REST API: CRUD, evaluate, batch-evaluate)     │   │
│  └──────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────┘
```

---

## Provider Types

### Local Provider (`LocalFeatureFlagProvider`)

- In-memory `ConcurrentHashMap` storage
- No external dependencies
- Suitable for development, testing, and fallback
- Rules evaluated in-process with priority ordering

### OpenFeature Provider (`OpenFeatureFlagEvaluator`)

- Uses OpenFeature Java SDK (`dev.openfeature:sdk:1.20.2`)
- Supports Unleash remote provider (`dev.openfeature.contrib.providers:unleash:0.1.3-alpha`)
- Configuration via `app.features.unleash.*` properties
- Automatic fallback to `InMemoryProvider` when Unleash is disabled

### Provider Selection

Controlled by `AppFeaturesProperties.Unleash.enabled`:
- `false` (default): Uses `LocalFeatureFlagProvider`
- `true`: Uses `OpenFeatureFlagEvaluator` with Unleash

---

## Flag Types

| Type | Description | Default Value |
|------|-------------|---------------|
| `BOOLEAN` | On/off toggle | `true`/`false` |
| `STRING` | String value selection | Any string |
| `NUMBER` | Numeric value | Any number |
| `JSON` | Structured JSON object | Any JSON |

---

## Targeting Rules

Rules are evaluated in priority order (lower number = higher priority). Each rule supports the following matching criteria:

| Criterion | Field | Match Type |
|-----------|-------|------------|
| Tenant ID | `tenantId` | Exact match |
| Workspace ID | `workspaceId` | Exact match |
| User ID | `userId` | Exact match |
| Role | `role` | Contains in context roles list |
| Group | `group` | Contains in context groups list |
| Tier | `tier` | Exact match |
| Region | `region` | Exact match |
| Request Source | `requestSource` | Exact match |
| Environment | `environment` | Exact match |
| Percentage | `percentage` | Hash-based deterministic rollout |

### Rule Properties

- **ruleId**: Unique identifier
- **priority**: Evaluation order (lower = first)
- **enabled**: Whether the rule is active
- **startAt/endAt**: Optional time bounds (ISO-8601)
- **percentage**: Optional percentage rollout (0-100)

---

## Percentage Rollout

Percentage rollout uses deterministic hashing:

```java
String hashKey = userId != null ? userId
    : tenantId != null ? tenantId : UUID.randomUUID().toString();
int hash = Math.abs(hashKey.hashCode() % 100);
return hash < percentage;
```

Properties:
- **Deterministic**: Same user always gets the same result
- **Consistent**: Uses userId > tenantId > random as hash key
- **Distributed**: Even distribution across percentage range

---

## Context Model

```java
public record FeatureFlagContext(
    String tenantId,
    String workspaceId,
    String userId,
    List<String> roles,
    List<String> groups,
    String tier,
    String requestSource,
    String environment,
    String region,
    String riskLevel,
    Map<String, Object> attributes
)
```

---

## Audit Events

All Feature Flag operations emit audit events via `FeatureFlagAuditService`:

| Event Type | Trigger |
|------------|---------|
| `FLAG_CREATED` | New flag created |
| `FLAG_UPDATED` | Flag modified |
| `FLAG_ENABLED` | Flag enabled |
| `FLAG_DISABLED` | Flag disabled |
| `FLAG_ARCHIVED` | Flag archived |
| `RULE_CREATED` | Targeting rule added |
| `RULE_UPDATED` | Targeting rule modified |
| `RULE_DELETED` | Targeting rule removed |
| `FLAG_EVALUATED` | Flag evaluated successfully |
| `FLAG_EVALUATION_FAILED` | Flag evaluation error |
| `ROLLOUT_CHANGED` | Percentage rollout modified |
| `VARIANT_CHANGED` | Variant assignment changed |
| `POLICY_EVALUATED_WITH_FEATURE_FLAG` | Policy used FF as condition |
| `ACCESS_DENIED_BY_FEATURE_FLAG` | Access denied due to FF |
| `NAVIGATION_DISABLED_BY_FEATURE_FLAG` | Route hidden due to FF |

All audit events include: `tenantId`, `workspaceId`, `userId`, `actorId`, `flagKey`, `operation`, `before`, `after`, `matchedRule`, `variant`, `reason`, `traceId`, `requestSource`.

---

## Error Codes

All Feature Flag error codes use the `FF-` prefix:

| Code | Status | Description |
|------|--------|-------------|
| `FF-404-001` | 404 | Feature flag not found |
| `FF-400-002` | 400 | Feature flag is disabled |
| `FF-500-001` | 500 | Feature flag evaluation failed |
| `FF-503-001` | 503 | Feature flag provider unavailable |
| `FF-400-003` | 400 | Feature flag context is invalid |
| `FF-400-004` | 400 | Feature flag targeting rule is invalid |
| `FF-400-005` | 400 | Feature flag variant is invalid |
| `FF-400-006` | 400 | Feature flag rollout percentage is invalid |
| `FF-403-001` | 403 | Feature flag access denied |
| `FF-403-002` | 403 | Feature flag operation not allowed |
| `FF-500-002` | 500 | OpenFeature initialization failed |
| `FF-403-003` | 403 | Policy denied by feature flag |
| `FF-403-004` | 403 | Navigation disabled by feature flag |

All error codes include both English (`en`) and Chinese (`zh`) messages.

---

## REST API Endpoints

### Admin Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/admin/feature-flags` | Create flag |
| `GET` | `/api/v1/admin/feature-flags` | List all flags |
| `GET` | `/api/v1/admin/feature-flags/{flagKey}` | Get flag by key |
| `PUT` | `/api/v1/admin/feature-flags/{flagKey}` | Update flag |
| `POST` | `/api/v1/admin/feature-flags/{flagKey}/archive` | Archive flag |
| `POST` | `/api/v1/admin/feature-flags/{flagKey}/enable` | Enable flag |
| `POST` | `/api/v1/admin/feature-flags/{flagKey}/disable` | Disable flag |
| `POST` | `/api/v1/admin/feature-flags/{flagKey}/rules` | Add targeting rule |
| `GET` | `/api/v1/admin/feature-flags/{flagKey}/evaluations` | Get evaluation audit log |

### User Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/me/feature-flags` | Get flags for current context |
| `POST` | `/api/v1/feature-flags/evaluate` | Evaluate a flag |
| `POST` | `/api/v1/feature-flags/batch-evaluate` | Batch evaluate flags |

---

## Integration Points

- **AccessDecisionService**: Uses Feature Flags as policy conditions
- **NavigationDecisionService**: Controls route visibility via Feature Flags
- **PolicyEvaluationService**: Evaluates `featureFlag` conditions in policy rules
- **Extension Runtime**: Controls extension execution via runtime flags
- **Export Pipeline**: Controls export formats and GPU acceleration
- **GraphQL Layer**: Controls query aggregation and admin dashboard
- **Monitoring**: Controls OpenReplay and Sentry session replay
