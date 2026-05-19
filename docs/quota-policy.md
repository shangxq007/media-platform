# Quota Policy

> Doc index: [docs/README.md](./README.md).

## Overview

The quota system enforces usage limits at runtime. Quotas are defined by `QuotaPolicy` and `QuotaProfile`, tracked in `QuotaBucket`, and evaluated by `QuotaDecisionService`. Quotas are checked as part of the access control flow — after entitlement passes but before the operation executes.

## Models

### QuotaPolicy

Defines a usage limit for a feature within a tier.

```java
public record QuotaPolicy(
    String policyId,
    String tier,
    String featureCode,           // e.g., "render.minutes", "storage.bytes"
    long limitValue,
    String period,                // "DAILY", "MONTHLY", "ANNUAL"
    long warningThresholdPercent  // e.g., 80 means warn at 80% usage
)
```

Methods:
- `isExceeded(currentUsage)`: Returns true if `currentUsage >= limitValue`
- `isWarning(currentUsage)`: Returns true if usage exceeds the warning threshold
- `remaining(currentUsage)`: Returns `max(0, limitValue - currentUsage)`

Source: `entitlement-module/.../domain/QuotaPolicy.java`

### QuotaProfile

A comprehensive quota profile that aggregates multiple quota types.

```java
public record QuotaProfile(
    String id,
    String profileKey,           // e.g., "pro_quota", "enterprise_quota"
    String name,
    String description,
    long monthlyRenderMinutes,
    int dailyRenderJobs,
    int concurrentRenderJobs,
    long storageBytes,
    long gpuMinutes,
    long remoteWorkerJobs,
    long promptExecutions,
    long extensionExecutions,
    int apiCallsPerMinute,
    int mcpCallsPerMinute,
    Instant createdAt,
    Instant updatedAt
)
```

Source: `entitlement-module/.../domain/QuotaProfile.java`

### QuotaBucket

Tracks actual usage against a limit for a specific tenant and feature.

```java
public record QuotaBucket(
    String id,
    String tenantId,
    String featureCode,
    long limit,
    String period,
    long currentUsage,
    Instant createdAt,
    Instant updatedAt
)
```

Methods:
- `withUsage(newUsage)`: Returns a new bucket with updated usage
- `usageRatio()`: Returns `currentUsage / limit` as a double
- `isExceeded()`: Returns true if `currentUsage >= limit`

Source: `quota-billing-module/.../domain/QuotaBucket.java`

### QuotaDecision

Result of a quota evaluation.

```java
public record QuotaDecision(
    String subjectId,
    String quotaCode,
    boolean allowed,
    double limitValue,
    double usedValue
)
```

## Supported Quota Types

| Feature Code | Description | Unit |
|-------------|-------------|------|
| `render.minutes` | Monthly render minutes | minutes |
| `render.jobs.daily` | Daily render job count | jobs |
| `render.concurrent` | Concurrent render jobs | jobs |
| `storage.bytes` | Storage usage | bytes |
| `render.gpu_minutes` | GPU render minutes | minutes |
| `render.remote_jobs` | Remote worker jobs | jobs |
| `prompt.executions` | Prompt executions | executions |
| `extension.executions` | Extension executions | executions |
| `api.calls_per_minute` | API rate limit | calls/min |
| `mcp.calls_per_minute` | MCP rate limit | calls/min |

## How Quotas Are Checked at Runtime

### Access Decision Flow

When `AccessDecisionService.check()` processes a request with a non-zero `requestedQuota`:

```
1. EntitlementDecisionService.evaluate() -> allowed?
2. If allowed && requestedQuota > 0:
   QuotaDecisionService.evaluate(subjectId, featureCode, requestedQuota)
3. If quota exceeded -> AccessDecision(QUOTA_EXCEEDED)
4. If quota passes -> AccessDecision(ALLOW, quotaRemaining)
```

### QuotaDecisionService

```java
public QuotaDecision evaluate(String subjectId, String featureCode, long requestedAmount) {
    long currentUsage = quotaUsageService.getUsage(subjectId, featureCode);
    long remaining = quotaPolicyService.remaining(featureCode, currentUsage);
    long afterRequest = remaining - requestedAmount;
    boolean allowed = afterRequest >= 0;
    return new QuotaDecision(subjectId, featureCode, allowed, remaining, currentUsage);
}
```

With profile:
```java
public QuotaDecision evaluateWithProfile(String subjectId, String featureCode,
        QuotaProfile profile, long requestedAmount) {
    long limit = quotaPolicyService.resolveLimitFromProfile(profile, featureCode);
    long currentUsage = quotaUsageService.getUsage(subjectId, featureCode);
    long remaining = Math.max(0, limit - currentUsage);
    boolean allowed = requestedAmount <= remaining;
    return new QuotaDecision(subjectId, featureCode, allowed, remaining, currentUsage);
}
```

## Integration Points

### RenderJob

When a render job is submitted:
1. `AccessCheckRequest` is built with `featureKey = "render.minutes"` and `requestedQuota = estimatedDurationMinutes`
2. `AccessDecisionService.check()` validates entitlement then quota
3. If quota exceeded, the job is rejected with `QUOTA_EXCEEDED`
4. If allowed, `QuotaDecisionService.recordUsage()` increments usage after job completion

### Export

Export validation checks quota as part of `EntitlementPolicyService.validateExport()`:
- Estimates render minutes from `estimatedDurationSeconds`
- Checks against `monthlyRenderMinutes` quota
- Returns `recommendedPreset` and `upgradeOptions` if quota exceeded

### PromptExecution

Prompt executions are counted against `prompt.executions` quota:
- Checked before sending to AI provider
- Incremented after successful execution

### ExtensionExecution

Extension executions are counted against `extension.executions` quota:
- Checked before sandbox execution
- Incremented after completion

## Quota Exceeded Handling

When a quota is exceeded:

1. The `AccessDecision` contains:
   - `allowed: false`
   - `decision: "QUOTA_EXCEEDED"`
   - `reasonCode: "QUOTA_POLICY"`
   - `quotaRemaining`: remaining quota before the request
   - `upgradeOptions`: e.g., `["Request a quota increase or reduce usage"]`

2. The API returns a structured error response with:
   - Current usage and limit
   - Recommended alternatives
   - Upgrade options (tier or quota increase)

3. At the warning threshold (default 80%), a warning is included in the response but access is still granted.

## Quota Reset

```
POST /api/v1/tenants/{tenantId}/quota/reset
```

Resets all quota buckets for a tenant. Typically called at the start of a new billing period.

## Quota API Reference

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/tenants/{tenantId}/quota` | Get quota buckets for tenant |
| GET | `/api/v1/tenants/{tenantId}/usage` | Get usage for tenant |
| POST | `/api/v1/tenants/{tenantId}/quota/reset` | Reset quota for tenant |
| GET | `/api/v1/quota/billing/overview` | Billing overview (legacy) |

## Error Codes

| Code | Description |
|------|-------------|
| `QUOTA-403-001` | Quota exceeded |
| `QUOTA-403-002` | Rate limit exceeded |
| `QUOTA-422-001` | Invalid quota request |
