# Error Codes and Audit

> **Last Updated:** 2026-05-19

## Overview

This document provides a comprehensive reference for all error codes and audit events across the ABAC, Feature Flag, Entitlement, Quota, and Billing systems.

## Error Code Format

```
{MODULE}-{HTTP_STATUS}-{SEQUENCE}
```

Error codes are defined in `shared-kernel/src/main/resources/error-codes.json` and loaded via `ErrorCodeRegistry`.

## Error Code Modules

| Module | Prefix | Count | Status |
|--------|--------|-------|--------|
| Common | `COMMON-` | 3 | ✅ |
| Render | `RENDER-` | — | ✅ |
| Subtitle | `SUBTITLE-` | — | ✅ |
| Effect | `EFFECT-` | — | ✅ |
| Timeline | `TIMELINE-` | — | ✅ |
| Migration | `MIGRATION-` | — | ✅ |
| Entitlement | `ENTITLEMENT-` | 7 | ✅ |
| Feature Flag | `FF-` | 13 | ✅ |
| NLQ | `NLQ-` | 11 | ✅ |
| Monitoring | `MONITORING-` | 2 | ✅ |
| Feedback | `FEEDBACK-` | 2 | ✅ |
| Billing | `BILLING-` | 7 | ✅ |
| Navigation | `NAV-` | 8 | ✅ |
| **Total** | | **60+** | |

## Entitlement Error Codes

| Code | HTTP | Description | i18n (zh) |
|------|------|-------------|-----------|
| `ENTITLEMENT-403-001` | 403 | Feature not available for current tier | 当前套餐不支持此功能 |
| `ENTITLEMENT-403-002` | 403 | Provider not allowed for current tier | 当前套餐不允许使用此渲染器 |
| `ENTITLEMENT-403-003` | 403 | Export preset not allowed | 当前套餐不允许使用此导出预设 |
| `ENTITLEMENT-403-004` | 403 | Export format not allowed | 当前套餐不允许使用此导出格式 |
| `ENTITLEMENT-404-001` | 404 | Entitlement grant not found | 未找到权益授权 |
| `ENTITLEMENT-409-001` | 409 | Entitlement already granted | 权益已授权 |
| `ENTITLEMENT-422-001` | 422 | Invalid entitlement request | 无效的权益请求 |

## Feature Flag Error Codes

| Code | HTTP | Description | i18n (zh) |
|------|------|-------------|-----------|
| `FF-404-001` | 404 | Feature flag not found | 未找到功能开关 |
| `FF-404-002` | 404 | Feature flag variant not found | 未找到功能开关变体 |
| `FF-403-001` | 403 | Feature disabled by flag | 功能已被开关禁用 |
| `FF-403-002` | 403 | Feature not available in tier | 当前套餐不支持此功能 |
| `FF-403-003` | 403 | Navigation disabled by flag | 导航已被开关禁用 |
| `FF-403-004` | 403 | Export disabled by flag | 导出已被开关禁用 |
| `FF-403-005` | 403 | Extension runtime flag disabled | 扩展运行时开关已禁用 |
| `FF-403-006` | 403 | GraphQL feature disabled | GraphQL 功能已禁用 |
| `FF-403-007` | 403 | Error code feature disabled | 错误码功能已禁用 |
| `FF-409-001` | 409 | Feature flag already exists | 功能开关已存在 |
| `FF-422-001` | 422 | Invalid flag configuration | 无效的开关配置 |
| `FF-500-001` | 500 | OpenFeature evaluation error | OpenFeature 评估错误 |
| `FF-EVAL-OPENFEATURE-001` | 500 | OpenFeature SDK exception | OpenFeature SDK 异常 |

## Billing Error Codes

| Code | HTTP | Description | i18n (zh) |
|------|------|-------------|-----------|
| `BILLING-403-001` | 403 | Budget limit exceeded | 超出预算限制 |
| `BILLING-403-002` | 403 | Insufficient credit balance | 余额不足 |
| `BILLING-404-001` | 404 | Wallet not found | 未找到钱包 |
| `BILLING-404-002` | 404 | Subscription not found | 未找到订阅 |
| `BILLING-409-001` | 409 | Duplicate transaction | 重复交易 |
| `BILLING-422-001` | 422 | Invalid billing request | 无效的账单请求 |
| `BILLING-500-001` | 500 | Billing engine error | 账单引擎错误 |

## Navigation Error Codes

| Code | HTTP | Description |
|------|------|-------------|
| `NAV-403-SOURCE` | 403 | Route not available for request source |
| `NAV-403-ROLE` | 403 | Route requires specific role |
| `NAV-403-PERM` | 403 | Route requires specific permission |
| `NAV-403-TIER` | 403 | Route requires higher tier |
| `NAV-403-FEAT` | 403 | Route requires specific features |
| `NAV-403-ENT` | 403 | Route requires specific entitlement |
| `NAV-403-FF` | 403 | Route requires feature flag |
| `NAV-403-BETA` | 403 | Route is in beta |
| `NAV-403-ROLLOUT` | 403 | Route rollout flag disabled |
| `NAV-404-HIDDEN` | 404 | Route is hidden |
| `NAV-403-DISABLED` | 403 | Route is disabled |

## Policy Decision Error Codes

| Code | HTTP | Description |
|------|------|-------------|
| `PERMISSION_DENIED` | 403 | RBAC permission check failed |
| `POLICY_NOT_MATCHED` | 403 | No ABAC policy rule matched |
| `FEATURE_FLAG_DISABLED` | 403 | Access blocked by feature flag |
| `ENTITLEMENT_DENIED` | 403 | Access blocked by entitlement |
| `QUOTA_EXCEEDED` | 409 | Quota limit exceeded |
| `BUDGET_EXCEEDED` | 403 | Budget limit exceeded |

## Exception Hierarchy

```
RuntimeException
└── PlatformException
    ├── errorCode: ErrorCode (ConfigurableErrorCode | SimpleErrorCode)
    ├── details: Map<String, Object>
    └── locale: String (default: "en")
```

## Global Exception Handler

| Exception Type | HTTP Status | Error Code |
|----------------|-------------|------------|
| `PlatformException` | From errorCode | From exception |
| `IllegalArgumentException` | 400 | `COMMON-400-001` |
| `IllegalStateException` | 409 | `COMMON-409-001` |
| `Exception` | 500 | `COMMON-500-001` |

## i18n Support

- Backend: `ErrorCodeRegistry` loads messages from `error-codes.json`
- Frontend: `useI18nError()` composable provides `t(errorCode)`
- Locale detection: `Accept-Language` header
- Supported languages: English (en), Chinese (zh)

## Audit Event Reference

### Feature Flag Audit Events (15+ types)

| Event Type | Trigger | Details Captured |
|------------|---------|-----------------|
| `FLAG_CREATED` | New flag created | flagKey, name, flagType, enabled |
| `FLAG_UPDATED` | Flag modified | flagKey, nameChanged, enabledChanged, typeChanged |
| `FLAG_ENABLED` | Flag enabled | flagKey |
| `FLAG_DISABLED` | Flag disabled | flagKey |
| `FLAG_ARCHIVED` | Flag archived | flagKey |
| `FLAG_EVALUATED` | Flag evaluated | flagKey, enabled, providerType, reasonCode |
| `FLAG_EVALUATION_FAILED` | Evaluation error | flagKey, errorCode, errorMessage |
| `RULE_CREATED` | Targeting rule added | flagKey, ruleId, priority, enabled |
| `RULE_UPDATED` | Targeting rule modified | flagKey, ruleId, priority |
| `RULE_DELETED` | Targeting rule removed | flagKey, ruleId |
| `ROLLOUT_CHANGED` | Percentage changed | flagKey, oldPercentage, newPercentage |
| `VARIANT_CHANGED` | Variant changed | flagKey, oldVariant, newVariant |
| `POLICY_EVALUATED_WITH_FEATURE_FLAG` | Policy + FF evaluation | flagKey, flagEnabled, actor, tenant, workspace, user, matchedRule, variant, reason, traceId, requestSource |
| `ACCESS_DENIED_BY_FEATURE_FLAG` | Access denied by FF | flagKey, actor, tenant, workspace, user, matchedRule, variant, reason, traceId, requestSource |
| `NAVIGATION_DISABLED_BY_FEATURE_FLAG` | Nav disabled by FF | flagKey, actor, tenant, workspace, user, matchedRule, variant, reason, traceId, requestSource |

### Entitlement Audit Events

| Event Type | Trigger | Details Captured |
|------------|---------|-----------------|
| `entitlement.granted` | Grant created | subjectId, featureKey, bundleKey |
| `entitlement.revoked` | Grant revoked | grantId, reason |
| `entitlement.extended` | Grant extended | grantId, newExpiresAt |

### Audit Storage

- **In-memory**: `FeatureFlagAuditService` stores up to 10,000 recent events
- **Persistent**: Events forwarded to `AuditPort.record()` for database persistence
- **Query**: `getRecentEvents(limit)` and `getEventsByFlag(flagKey)` for in-memory events

## Error Response Format

All backend errors return JSON:

```json
{
  "errorCode": "ENTITLEMENT-403-001",
  "message": "Feature not available for current tier",
  "details": {
    "featureKey": "export.4k",
    "currentTier": "FREE",
    "requiredTier": "TEAM"
  },
  "timestamp": "2026-05-19T10:00:00Z"
}
```

## Configurable Error Codes

Error codes are loaded from `error-codes.json`:

```json
{
  "ENTITLEMENT-403-001": {
    "numericCode": 403001,
    "messages": {
      "en": "Feature not available for current tier",
      "zh": "当前套餐不支持此功能"
    },
    "module": "entitlement",
    "status": 403
  }
}
```

The `ConfigurableErrorCode` record implements the `ErrorCode` interface and provides `message(locale)` for i18n support.
