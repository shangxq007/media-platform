# 业务逻辑模块

> **最后更新：** 2026-05-18

## billing-module

**状态：** ✅ 已实现

成本计量、预算管理和对账。

| 功能 | 状态 | 说明 |
|------|------|------|
| 计量 | ✅ | 按租户使用量追踪 |
| 预算守护 | ✅ | 预算限制与告警 |
| 预留 | ✅ | 渲染前成本预留 |
| 对账 | ✅ | 发票导入、匹配 |
| 异常检测 | ✅ | 8 条规则，分级缓解 |

**依赖：** `shared-kernel`

## quota-billing-module

**状态：** ✅ 已实现

配额管理，支持桶和阈值事件。

| 功能 | 状态 | 说明 |
|------|------|------|
| 配额桶 | ✅ | 按租户+功能 |
| 阈值事件 | ✅ | 可配置阈值 |
| QuotaDecisionService | ✅ | 运行时配额检查 |

**依赖：** 无

## entitlement-module

**状态：** ✅ 已实现

基于层级的访问控制系统。**功能访问的最终真相来源**。

| 功能 | 状态 | 说明 |
|------|------|------|
| 5 级策略 | ✅ | FREE/PRO/TEAM/ENTERPRISE/EXPERIMENTAL |
| 权益授权 | ✅ | 用户/组/租户授权 |
| 权益覆盖 | ✅ | 租户级自定义策略 |
| 工作空间池 | ✅ | 共享权益池 |
| 导出验证 | ✅ | 基于层级的导出格式/预设检查 |
| 提供者访问策略 | ✅ | 基于层级的提供者访问 |
| 决策优先级链 | ✅ | 10 级优先级链 |

**依赖：** `shared-kernel`

**REST API：** `/api/v1/entitlements/*`、`/api/v1/admin/entitlements/*`

## payment-module

**状态：** ⚠️ 部分实现

支付提供者集成。

| 功能 | 状态 | 说明 |
|------|------|------|
| 领域模型 | ✅ | PaymentAttempt、CheckoutSession |
| NoopStripePaymentProvider | 🔧 存根 | 空操作实现 |
| NoopHyperswitchPaymentProvider | 🔧 存根 | 空操作实现 |
| 真实支付集成 | 📋 未来 | Stripe/Hyperswitch 待完成 |

**依赖：** `shared-kernel`

## commerce-module

**状态：** ✅ 已实现

商务域，包含结账和订单管理。

| 功能 | 状态 | 说明 |
|------|------|------|
| 产品目录 | ✅ | CommerceProduct、CommercePrice |
| 结账会话 | ✅ | CheckoutSession 管理 |
| 采购订单 | ✅ | PurchaseOrder 生命周期 |
| 提供者映射 | ✅ | SKU 到外部提供者映射 |
| NoopMedusaCatalogAdapter | 🔧 存根 | 空操作目录适配器 |

**依赖：** `shared-kernel`

## policy-governance-module

**状态：** ✅ 已实现

Feature Flag、策略评估和 ABAC 访问控制。

| 功能 | 状态 | 说明 |
|------|------|------|
| FeatureFlagService | ✅ | CRUD、批量评估、缓存 |
| LocalFeatureFlagProvider | ✅ | 内存实现，支持目标 + 灰度 |
| OpenFeatureFlagEvaluator | ✅ | OpenFeature SDK 包装（预留） |
| PolicyEvaluationService | ✅ | ABAC 策略规则评估 |
| AccessDecisionService | ✅ | 8 步决策流 |
| NavigationDecisionService | ✅ | 带 Feature Flag 控制的路由访问 |
| FeatureFlagAuditService | ✅ | 15 种审计事件类型 |
| 13 个 FF- 错误码 | ✅ | FF-404-001 至 FF-403-004 |

**依赖：** 无（提供 `feature-flags` 命名接口）

**REST API：** `/api/v1/feature-flags/*`、`/api/v1/admin/policies/*`

## audit-compliance-module

**状态：** ✅ 已实现

审计追踪与合规。

| 功能 | 状态 | 说明 |
|------|------|------|
| 审计记录存储 | ✅ | 所有操作记录 |
| 事件驱动审计 | ✅ | 通过 @EventListener 消费事件 |
| 异常检测 | ✅ | 行为异常检测 |
| UX 守护 | ✅ | 分级缓解措施 |

**依赖：** `shared-kernel`

## notification-module

**状态：** ✅ 已实现

多渠道通知投递。

| 功能 | 状态 | 说明 |
|------|------|------|
| 模板管理 | ✅ | NotificationTemplate CRUD |
| 多渠道投递 | ✅ | 邮件、短信、推送（可扩展） |
| 事件驱动 | ✅ | 通过 @EventListener 消费事件 |
| 投递追踪 | ✅ | NotificationDelivery 记录 |

**依赖：** `shared-kernel`

## observability-module

**状态：** ✅ 已实现

健康监控与熔断器。

| 功能 | 状态 | 说明 |
|------|------|------|
| 健康检查 | ✅ | 自定义健康指标 |
| 熔断器 | ✅ | 提供者熔断器 |
| SLA 指标 | ✅ | 按提供者 SLA 追踪 |
| ObservabilityController | ✅ | `/api/v1/observability/overview` |

**依赖：** `shared-kernel`

## user-analytics-module

**状态：** ✅ 已实现

用户行为分析和分群。

| 功能 | 状态 | 说明 |
|------|------|------|
| 行为事件 | ✅ | 事件摄入 |
| 用户画像 | ✅ | 画像聚合 |
| 用户分群 | ✅ | 分群计算 |
| 用户习惯 | ✅ | 习惯分析 |

**依赖：** `shared-kernel`

## compatibility-migration-module

**状态：** ✅ 已实现

9 个 Schema 族的迁移支持。

| 功能 | 状态 | 说明 |
|------|------|------|
| Schema 族迁移 | ✅ | 9 个族 |
| 迁移验证 | ✅ | 迁移前检查 |

**依赖：** `shared-kernel`
