# 策略组合示例

> **最后更新：** 2026-05-19

## 概述

本文档提供 Feature Flag、权益、配额和账单策略如何在访问决策链中组合在一起的具体示例。

## 决策链

平台中的完整决策链：

```
身份验证
  → RBAC（角色 + 权限检查）
  → Feature Flag 评估
  → ABAC（PolicyEvaluationService）
  → 权益（EntitlementDecisionService）
  → 配额（QuotaDecisionService）
  → 账单（BillingDecisionService）  ⚠️ 独立运行，未接入决策链
  → AccessDecision（最终结果）
```

## 示例 1：Feature Flag 控制新的导出预设

**场景**：新的 `gpu_h264` 导出预设正在向 10% 的团队版用户灰度发布。

### Feature Flag 定义

```json
{
  "flagKey": "export.gpu.v2.enabled",
  "name": "GPU H264 导出 V2",
  "description": "启用 GPU 加速的 H264 导出预设",
  "flagType": "BOOLEAN",
  "defaultValue": false,
  "enabled": true,
  "owner": "platform-team",
  "tags": ["export", "gpu", "rollout"],
  "targetingRules": [
    {
      "ruleId": "rule-gpu-v2-team",
      "name": "团队版套餐灰度",
      "priority": 1,
      "enabled": true,
      "tier": "TEAM",
      "percentage": 10
    }
  ]
}
```

### 访问检查流程

1. 用户（团队版套餐）请求 `render.submit`，使用 `preset=gpu_h264`
2. 调用 `AccessDecisionService.check()`
3. `AccessDecisionFeatureFlagService` 评估 `export.gpu.v2.enabled`
   - 用户在 10% 灰度范围内 → flag 为 `enabled=true`
4. `EntitlementDecisionService.evaluate()` 检查：
   - 无覆盖 → 跳过
   - 无工作区成员授权 → 跳过
   - 无工作区池 → 跳过
   - 无用户授权 → 跳过
   - 套餐策略：团队版允许 `gpu_h264` 预设和 GPU → `ALLOW`
5. `QuotaDecisionService.evaluate()` 检查剩余配额 → 在限额内
6. 结果：`AccessDecision(allowed=true, decision="ALLOW")`

### 拒绝情况

如果用户不在 10% 灰度范围内：
1. Feature flag 评估为 `enabled=false`
2. `AccessDecision.disabledByFeatureFlag = true`
3. `AccessDecision.featureFlagReasons = ["Feature flag 'export.gpu.v2.enabled' 被禁用 (NO_MATCHING_RULE)"]`
4. 权益检查仍然通过（团队版允许 GPU）
5. 结果：`AccessDecision(allowed=false, disabledByFeatureFlag=true)`

## 示例 2：权益覆盖为免费用户授予高级功能

**场景**：免费版用户通过覆盖授权临时获得 4K 导出权限。

### 覆盖创建

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

### 访问检查流程

1. 用户（免费版套餐）请求使用 `preset=team_4k` 的导出
2. `EntitlementDecisionService.evaluate()`：
   - 第 1 步：检查覆盖 → 找到 `user-123` 的活跃覆盖
   - 返回 `EntitlementDecision(allowed=true, reasonCode="TENANT_OVERRIDE")`
3. 结果：`AccessDecision(allowed=true, matchedOverrideId="override-abc")`

## 示例 3：配额超限阻止渲染

**场景**：专业版用户已用完每月渲染分钟数。

### 访问检查流程

1. 用户（专业版套餐）请求 `render.submit`，`requestedQuota=30`（分钟）
2. `EntitlementDecisionService.evaluate()`：
   - 套餐策略：专业版允许渲染 → `ALLOW`
3. `QuotaDecisionService.evaluate("user-456", "render.minutes", 30)`：
   - `currentUsage = 295`（每月共 300）
   - `remaining = 300 - 295 = 5`
   - `afterRequest = 5 - 30 = -25` → `allowed = false`
4. 结果：`AccessDecision(allowed=false, decision="QUOTA_EXCEEDED", quotaRemaining=5, upgradeOptions=["升级到团队版以获得更多容量"])`

## 示例 4：Feature Flag + 权益 + 账单组合

**场景**：企业版用户尝试使用需要 feature flag 和充足预算的高级 AI 功能。

### 策略评估

```java
// 1. Feature Flag 检查
FeatureFlagDecision ffDecision = featureFlagService.evaluate(
    new FeatureFlagEvaluationRequest("ai.premium.models",
        new FeatureFlagContext(tenantId, workspaceId, userId, ...),
        false)
);
// 结果：enabled=true（flag 对企业版套餐活跃）

// 2. 权益检查
EntitlementDecision entDecision = entitlementDecisionService.evaluate(
    new AccessCheckRequest(tenantId, workspaceId, userId, ..., "ai.premium.models", ...)
);
// 结果：allowed=true（企业版套餐包含 AI 高级功能）

// 3. 预算检查
BudgetCheckResult budgetResult = budgetGuardService.checkBudget(tenantId, 0.50);
// 结果：allowed=true（预算充足）

// 4. 配额检查
QuotaDecision quotaDecision = quotaDecisionService.evaluate(userId, "ai.premium.models", 1);
// 结果：allowed=true（在每月 AI 执行配额内）

// 5. 最终决策
// AccessDecision(allowed=true, decision="ALLOW")
```

## 示例 5：带 Feature Flag 条件的 ABAC 策略

**场景**：当特定 feature flag 被禁用时，策略规则拒绝访问某功能。

### 策略规则定义

```json
{
  "ruleId": "rule-deny-when-ff-off",
  "name": "flag 关闭时拒绝",
  "effect": "DENY",
  "priority": 10,
  "status": "ACTIVE",
  "conditions": "{\"featureFlag\": {\"flagKey\": \"export.gpu.v2.enabled\", \"operator\": \"eq\", \"expectedValue\": false}}"
}
```

### 评估过程

`PolicyEvaluationService` 通过以下方式评估此规则：
1. 从规则的 JSON 条件中解析 `featureFlag` 条件
2. 通过 `FeatureFlagService.evaluate()` 评估 feature flag
3. 如果 flag 为 `false`（匹配 `expectedValue: false`）→ 条件满足 → `DENY`
4. 如果 flag 为 `true` → 条件不满足 → 继续下一条规则

## 示例 6：导航策略隐藏管理路由

**场景**：管理控制台应隐藏，不让没有管理员角色的用户看到。

### 导航策略

```java
new NavigationPolicy(
    "policy-hide-admin",       // policyKey
    "admin-dashboard",         // routeKey
    "ROLE_BASED",              // policyType
    "role=ADMIN",              // condition
    "HIDE",                    // effect
    "NAV-403-ROLE",            // reasonCode
    "需要管理员访问权限",         // reasonMessage
    List.of(),                 // upgradeOptions
    100,                       // priority
    true                       // enabled
)
```

### 评估过程

1. 没有管理员角色的用户导航到 `/admin`
2. `NavigationDecisionService.evaluateRoute()` 检查所需角色
3. 用户没有 ADMIN → `visible=false`，原因：`NAV-403-ROLE`
4. 路由从导航菜单中隐藏

## 策略决策错误代码参考

| 代码 | 条件 | HTTP |
|------|------|------|
| `PERMISSION_DENIED` | RBAC 检查失败 | 403 |
| `POLICY_NOT_MATCHED` | 无 ABAC 规则匹配 | 403 |
| `FEATURE_FLAG_DISABLED` | Feature flag 阻止访问 | 403 |
| `ENTITLEMENT_DENIED` | 套餐/授权/覆盖拒绝 | 403 |
| `QUOTA_EXCEEDED` | 配额超限 | 409 |
| `BUDGET_EXCEEDED` | 预算超限 | 403 |
| `NAV-403-TIER` | 导航需要更高等级套餐 | 403 |
| `NAV-403-FF` | 导航需要 feature flag | 403 |
| `NAV-404-HIDDEN` | 路由已隐藏 | 404 |
