# 管理控制台

> **模块：** `frontend/src/pages/admin/`
> **最后更新：** 2026-05-19

## 概述

管理控制台提供平台管理能力，包括 feature flag 管理、策略管理、权益管理和系统监控。

## 实现状态

| 组件 | 状态 |
|------|------|
| `AdminDashboard` | ✅ 已实现 |
| `FeatureFlagManagementPage` | ✅ 已实现 |
| `FeatureFlagEditor` | ✅ 已实现 |
| `FeatureFlagRuleEditor` | ✅ 已实现 |
| `FeatureFlagEvaluationPreview` | ✅ 已实现 |
| `FeatureFlagEvaluationLog` | ✅ 已实现 |
| `PolicyManagementPage` | ✅ 已实现 |
| `PolicyRuleEditor` | ✅ 已实现 |
| `PolicySimulationPanel` | ✅ 已实现 |
| `EntitlementManagementPage` | ✅ 已实现 |
| `ExtensionManagement` | ✅ 已实现 |
| `RouteManagementPage` | ✅ 已实现 |
| `MonitoringFeedbackPage` | ✅ 已实现 |
| `AuditLogPage` | ✅ 已实现 |
| `DatasetCatalogPage` | ✅ 已实现 |
| `QueryAuditPage` | ✅ 已实现 |
| `FeedbackAdminPage` | ✅ 已实现 |
| `TenantManagement` | ✅ 已实现 |
| `BillingPlanManagementPage` | ✅ 已实现 |
| `QuotaPolicyEditor` | ✅ 已实现 |
| `ConfigManagement` | ✅ 已实现 |
| `NotificationManagement` | ✅ 已实现 |
| `RenderJobManagement` | ✅ 已实现 |
| `UserAnalytics` | ✅ 已实现 |
| `CreditWalletAdminPanel` | ✅ 已实现 |
| `InvoicePreviewPage` | ✅ 已实现 |
| `BillingQuotePanel` | ✅ 已实现 |
| `TenantOverridePanel` | ✅ 已实现 |
| `UserGrantPanel` | ✅ 已实现 |
| `ExtensionQuotaInfo` | ✅ 已实现 |
| `AccessDecisionDebugPanel` | ✅ 已实现 |
| `EntitlementDecisionPreview` | ✅ 已实现 |
| `QuotaAllocationEditor` | ✅ 已实现 |
| `RoleManagementPanel` | ✅ 已实现 |
| `WorkspaceEntitlementPoolPanel` | ✅ 已实现 |
| `WorkspaceGroupGrantPanel` | ✅ 已实现 |
| `WorkspaceMemberGrantPanel` | ✅ 已实现 |
| `WorkspaceMembersPage` | ✅ 已实现 |
| `AdminLayout` | ✅ 已实现 |

## 页面列表

| 页面 | 路由 | 组件 | 用途 |
|------|------|------|------|
| 仪表盘 | `/admin` | `AdminDashboard` | 管理概览 |
| Feature Flags | `/admin/feature-flags` | `FeatureFlagManagementPage` | 管理 feature flags |
| FF 编辑器 | （模态框） | `FeatureFlagEditor` | 编辑 flag 规则 |
| FF 规则编辑器 | （模态框） | `FeatureFlagRuleEditor` | 编辑定向规则 |
| FF 评估 | （选项卡） | `FeatureFlagEvaluationPreview` | 预览评估结果 |
| FF 日志 | （选项卡） | `FeatureFlagEvaluationLog` | 评估审计日志 |
| 策略 | `/admin/policies` | `PolicyManagementPage` | ABAC 策略管理 |
| 策略编辑器 | （模态框） | `PolicyRuleEditor` | 编辑策略规则 |
| 策略模拟 | （选项卡） | `PolicySimulationPanel` | 模拟决策 |
| 权益 | `/admin/entitlements` | `EntitlementManagementPage` | 权益管理 |
| 扩展 | `/admin/extensions` | `ExtensionManagement` | 扩展管理 |
| 路由 | `/admin/routes` | `RouteManagementPage` | 导航配置 |
| 监控 | `/admin/monitoring` | `MonitoringFeedbackPage` | 监控状态 |
| 审计日志 | `/admin/audit` | `AuditLogPage` | 审计追踪 |
| 数据集目录 | `/admin/analytics/datasets` | `DatasetCatalogPage` | NLQ 数据集 |
| 查询审计 | `/admin/analytics/query-audit` | `QueryAuditPage` | NLQ 审计日志 |
| 反馈管理 | `/admin/feedback` | `FeedbackAdminPage` | 用户反馈 |
| 租户 | `/admin/tenants` | `TenantManagement` | 租户管理 |
| 计费方案 | `/admin/billing/plans` | `BillingPlanManagementPage` | 方案管理 |
| 配额策略 | `/admin/quota` | `QuotaPolicyEditor` | 配额配置 |
| 配置 | `/admin/config` | `ConfigManagement` | 系统配置 |
| 通知 | `/admin/notifications` | `NotificationManagement` | 通知管理 |
| 渲染任务 | `/admin/render-jobs` | `RenderJobManagement` | 渲染任务管理 |
| 用户分析 | `/admin/analytics` | `UserAnalytics` | 用户分析 |
| 用量分类账 | `/admin/billing/ledger` | `UsageLedgerPage` | 用量计费分类账 |

## 访问控制

管理控制台对普通用户隐藏。访问需要：
- 用户租户中的管理员角色
- 为用户启用的 `admin-console` feature flag

## Feature Flag 管理

管理员可以：
- 创建、编辑、删除 feature flags
- 配置定向规则（租户、工作区、用户、角色、群组、套餐、百分比灰度）
- 设置 flag 激活的时间窗口
- 配置 A/B 测试变体
- 预览特定用户/租户的 flag 评估结果
- 查看评估审计日志
- 启用/禁用/归档 flags

## 策略管理（ABAC）

`PolicyManagementPage` 提供 ABAC 策略 CRUD：

### 策略列表
- 按名称或代码搜索
- 按状态筛选（全部、活跃、草稿、已归档）
- 显示策略名称、代码、状态、版本数、规则数
- 操作：编辑、添加规则、归档

### 策略编辑器（模态框）
- 名称、代码、描述
- 状态（草稿、活跃、已归档）
- 版本数显示

### 策略规则编辑器（模态框）
- 规则名称、优先级、效果（ALLOW、DENY、REQUIRE_REVIEW、DEGRADE、WARN）
- 带属性和操作符的条件
- Feature flag 条件（flag 键、操作符、期望值）

### 策略模拟面板

`PolicySimulationPanel` 允许管理员：
- 输入假设的访问请求（用户、租户、工作区、资源、动作）
- 查看完整的决策链
- 识别匹配或拒绝的规则
- 在应用之前测试策略变更

## 权益管理

管理员可以：
- 查看和管理每个租户/工作区/用户的权益授权
- 创建、撤销和延期授权
- 管理权益捆绑包
- 管理租户覆盖
- 查看工作区权益池
- 查看工作区配额分配

## 访问决策调试

`AccessDecisionDebugPanel` 允许管理员：
- 输入访问检查请求（租户、工作区、用户、功能、预设、提供商）
- 查看完整的决策链及每步结果
- 查看匹配的策略、feature flags、配额和账单决策
- 确定允许/拒绝的确切原因

## 权益决策预览

`EntitlementDecisionPreview` 显示：
- 给定主体和功能对应的权益决策
- 匹配的策略链
- 如果被拒绝则显示升级选项
