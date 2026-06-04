# 错误代码与审计

> **最后更新：** 2026-05-19

## 概述

本文档提供 ABAC、Feature Flag、权益、配额和账单系统中所有错误代码和审计事件的完整参考。

## 错误代码格式

```
{模块}-{HTTP 状态码}-{序号}
```

错误代码定义在 `shared-kernel/src/main/resources/error-codes.json` 中，通过 `ErrorCodeRegistry` 加载。

## 错误代码模块

| 模块 | 前缀 | 数量 | 状态 |
|------|------|------|------|
| 通用 | `COMMON-` | 3 | ✅ |
| 渲染 | `RENDER-` | — | ✅ |
| 字幕 | `SUBTITLE-` | — | ✅ |
| 特效 | `EFFECT-` | — | ✅ |
| 时间轴 | `TIMELINE-` | — | ✅ |
| 迁移 | `MIGRATION-` | — | ✅ |
| 权益 | `ENTITLEMENT-` | 7 | ✅ |
| Feature Flag | `FF-` | 13 | ✅ |
| NLQ | `NLQ-` | 11 | ✅ |
| 监控 | `MONITORING-` | 2 | ✅ |
| 反馈 | `FEEDBACK-` | 2 | ✅ |
| 账单 | `BILLING-` | 7 | ✅ |
| 导航 | `NAV-` | 8 | ✅ |
| **总计** | | **60+** | |

## 权益错误代码

| 代码 | HTTP | 描述 | i18n (zh) |
|------|------|------|-----------|
| `ENTITLEMENT-403-001` | 403 | 当前套餐不支持此功能 | 当前套餐不支持此功能 |
| `ENTITLEMENT-403-002` | 403 | 当前套餐不允许使用该提供商 | 当前套餐不允许使用此渲染器 |
| `ENTITLEMENT-403-003` | 403 | 不允许该导出预设 | 当前套餐不允许使用此导出预设 |
| `ENTITLEMENT-403-004` | 403 | 不允许该导出格式 | 当前套餐不允许使用此导出格式 |
| `ENTITLEMENT-404-001` | 404 | 未找到权益授权 | 未找到权益授权 |
| `ENTITLEMENT-409-001` | 409 | 权益已授权 | 权益已授权 |
| `ENTITLEMENT-422-001` | 422 | 无效的权益请求 | 无效的权益请求 |

## Feature Flag 错误代码

| 代码 | HTTP | 描述 | i18n (zh) |
|------|------|------|-----------|
| `FF-404-001` | 404 | 未找到 feature flag | 未找到功能开关 |
| `FF-404-002` | 404 | 未找到 feature flag 变体 | 未找到功能开关变体 |
| `FF-403-001` | 403 | 功能被 flag 禁用 | 功能已被开关禁用 |
| `FF-403-002` | 403 | 套餐中功能不可用 | 当前套餐不支持此功能 |
| `FF-403-003` | 403 | 导航被 flag 禁用 | 导航已被开关禁用 |
| `FF-403-004` | 403 | 导出被 flag 禁用 | 导出已被开关禁用 |
| `FF-403-005` | 403 | 扩展运行时 flag 已禁用 | 扩展运行时开关已禁用 |
| `FF-403-006` | 403 | GraphQL 功能已禁用 | GraphQL 功能已禁用 |
| `FF-403-007` | 403 | 错误码功能已禁用 | 错误码功能已禁用 |
| `FF-409-001` | 409 | 功能开关已存在 | 功能开关已存在 |
| `FF-422-001` | 422 | 无效的开关配置 | 无效的开关配置 |
| `FF-500-001` | 500 | OpenFeature 评估错误 | OpenFeature 评估错误 |
| `FF-EVAL-OPENFEATURE-001` | 500 | OpenFeature SDK 异常 | OpenFeature SDK 异常 |

## 账单错误代码

| 代码 | HTTP | 描述 | i18n (zh) |
|------|------|------|-----------|
| `BILLING-403-001` | 403 | 超出预算限制 | 超出预算限制 |
| `BILLING-403-002` | 403 | 余额不足 | 余额不足 |
| `BILLING-404-001` | 404 | 未找到钱包 | 未找到钱包 |
| `BILLING-404-002` | 404 | 未找到订阅 | 未找到订阅 |
| `BILLING-409-001` | 409 | 重复交易 | 重复交易 |
| `BILLING-422-001` | 422 | 无效的账单请求 | 无效的账单请求 |
| `BILLING-500-001` | 500 | 账单引擎错误 | 账单引擎错误 |

## 导航错误代码

| 代码 | HTTP | 描述 |
|------|------|-------------|
| `NAV-403-SOURCE` | 403 | 路由对请求来源不可用 |
| `NAV-403-ROLE` | 403 | 路由需要特定角色 |
| `NAV-403-PERM` | 403 | 路由需要特定权限 |
| `NAV-403-TIER` | 403 | 路由需要更高等级套餐 |
| `NAV-403-FEAT` | 403 | 路由需要特定功能 |
| `NAV-403-ENT` | 403 | 路由需要特定权益 |
| `NAV-403-FF` | 403 | 路由需要 feature flag |
| `NAV-403-BETA` | 403 | 路由处于测试阶段 |
| `NAV-403-ROLLOUT` | 403 | 路由灰度发布 flag 已禁用 |
| `NAV-404-HIDDEN` | 404 | 路由已隐藏 |
| `NAV-403-DISABLED` | 403 | 路由已禁用 |

## 策略决策错误代码

| 代码 | HTTP | 描述 |
|------|------|-------------|
| `PERMISSION_DENIED` | 403 | RBAC 权限检查失败 |
| `POLICY_NOT_MATCHED` | 403 | 无 ABAC 规则匹配 |
| `FEATURE_FLAG_DISABLED` | 403 | Feature flag 阻止访问 |
| `ENTITLEMENT_DENIED` | 403 | 套餐/授权/覆盖拒绝 |
| `QUOTA_EXCEEDED` | 409 | 配额超限 |
| `BUDGET_EXCEEDED` | 403 | 预算超限 |

## 异常层次结构

```
RuntimeException
└── PlatformException
    ├── errorCode: ErrorCode (ConfigurableErrorCode | SimpleErrorCode)
    ├── details: Map<String, Object>
    └── locale: String (默认: "en")
```

## 全局异常处理器

| 异常类型 | HTTP 状态码 | 错误代码 |
|----------|-------------|----------|
| `PlatformException` | 来自 errorCode | 来自异常 |
| `IllegalArgumentException` | 400 | `COMMON-400-001` |
| `IllegalStateException` | 409 | `COMMON-409-001` |
| `Exception` | 500 | `COMMON-500-001` |

## 国际化支持

- 后端：`ErrorCodeRegistry` 从 `error-codes.json` 加载消息
- 前端：`useI18nError()` 组合式函数提供 `t(errorCode)`
- 区域检测：`Accept-Language` 请求头
- 支持语言：英语（en）、中文（zh）

## 审计事件参考

### Feature Flag 审计事件（15+ 种类型）

| 事件类型 | 触发条件 | 捕获的详细信息 |
|----------|----------|----------------|
| `FLAG_CREATED` | 创建新 flag | flagKey, name, flagType, enabled |
| `FLAG_UPDATED` | 修改 flag | flagKey, nameChanged, enabledChanged, typeChanged |
| `FLAG_ENABLED` | 启用 flag | flagKey |
| `FLAG_DISABLED` | 禁用 flag | flagKey |
| `FLAG_ARCHIVED` | 归档 flag | flagKey |
| `FLAG_EVALUATED` | 评估 flag | flagKey, enabled, providerType, reasonCode |
| `FLAG_EVALUATION_FAILED` | 评估出错 | flagKey, errorCode, errorMessage |
| `RULE_CREATED` | 添加定向规则 | flagKey, ruleId, priority, enabled |
| `RULE_UPDATED` | 修改定向规则 | flagKey, ruleId, priority |
| `RULE_DELETED` | 删除定向规则 | flagKey, ruleId |
| `ROLLOUT_CHANGED` | 更改百分比 | flagKey, oldPercentage, newPercentage |
| `VARIANT_CHANGED` | 更改变体 | flagKey, oldVariant, newVariant |
| `POLICY_EVALUATED_WITH_FEATURE_FLAG` | 策略 + FF 评估 | flagKey, flagEnabled, actor, tenant, workspace, user, matchedRule, variant, reason, traceId, requestSource |
| `ACCESS_DENIED_BY_FEATURE_FLAG` | FF 拒绝访问 | flagKey, actor, tenant, workspace, user, matchedRule, variant, reason, traceId, requestSource |
| `NAVIGATION_DISABLED_BY_FEATURE_FLAG` | FF 禁用导航 | flagKey, actor, tenant, workspace, user, matchedRule, variant, reason, traceId, requestSource |

### 权益审计事件

| 事件类型 | 触发条件 | 捕获的详细信息 |
|----------|----------|----------------|
| `entitlement.granted` | 创建授权 | subjectId, featureKey, bundleKey |
| `entitlement.revoked` | 撤销授权 | grantId, reason |
| `entitlement.extended` | 延长授权 | grantId, newExpiresAt |

### 审计存储

- **内存中**：`FeatureFlagAuditService` 存储最多 10,000 条最近事件
- **持久化**：事件转发到 `AuditPort.record()` 进行数据库持久化
- **查询**：`getRecentEvents(limit)` 和 `getEventsByFlag(flagKey)` 用于查询内存事件

## 错误响应格式

所有后端错误返回 JSON：

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

## 可配置错误代码

错误代码从 `error-codes.json` 加载：

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

`ConfigurableErrorCode` 记录实现了 `ErrorCode` 接口，并提供 `message(locale)` 方法支持国际化。
