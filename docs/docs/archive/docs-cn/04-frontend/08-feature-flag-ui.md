# Feature Flag 管理界面

> **模块：** `frontend/src/pages/admin/`
> **最后更新：** 2026-05-19

## 概述

Feature Flag 管理界面允许管理员创建、配置和监控控制平台功能访问的 feature flags。它由 5 个组件组成，采用选项卡式导航。

## 实现状态

| 组件 | 状态 |
|------|------|
| `FeatureFlagManagementPage` | ✅ 已实现 |
| `FeatureFlagEditor` | ✅ 已实现 |
| `FeatureFlagRuleEditor` | ✅ 已实现 |
| `FeatureFlagEvaluationPreview` | ✅ 已实现 |
| `FeatureFlagEvaluationLog` | ✅ 已实现 |
| `FeatureFlags`（列表） | ✅ 已实现 |
| `FeatureFlagIndicator` | ✅ 已实现 |
| `useFeatureFlag` 组合式函数 | ✅ 已实现 |
| `BetaFeaturesPanel`（用户门户） | ✅ 已实现 |

## 页面列表

| 页面 | 组件 | 用途 |
|------|------|------|
| Flag 列表 | `FeatureFlagManagementPage` | 列出、搜索、筛选、创建、编辑、切换、归档 flags |
| Flag 编辑器 | `FeatureFlagEditor` | 创建/编辑 flag 定义，含变体和定向规则 |
| 规则编辑器 | `FeatureFlagRuleEditor` | 配置定向规则，含条件、百分比、时间窗口 |
| 评估预览 | `FeatureFlagEvaluationPreview` | 预览特定上下文的 flag 评估结果 |
| 评估日志 | `FeatureFlagEvaluationLog` | 查看评估审计追踪 |

## FeatureFlagManagementPage

主 flag 管理页面，包含三个选项卡：

### Flags 选项卡
- **搜索**：按键、名称或所有者搜索
- **按类型筛选**：全部、BOOLEAN、STRING、NUMBER、JSON
- **按状态筛选**：全部、活跃、已禁用
- **表格列**：键、名称、类型、状态、所有者、规则数、修改日期、**操作**
- **操作**：编辑、启用/禁用、归档
- **空状态**：带"新建 Flag"操作按钮

### 评估预览选项卡
- 从下拉列表中选择 flag
- 输入评估上下文（租户、工作区、用户、角色、套餐、区域等）
- 显示评估结果：启用/禁用、匹配的规则、变体

### 评估日志选项卡
- 查看最近的 flag 评估审计事件
- 按 flag 键筛选
- 显示时间戳、操作者、flag 键、结果、原因

## FeatureFlagEditor

全屏模态框，用于创建/编辑 feature flags：

### 基础字段
- Flag 键（编辑时禁用）
- 名称、描述
- 类型（BOOLEAN、STRING、NUMBER、JSON）
- 默认值
- 所有者
- 标签（逗号分隔）
- 启用开关

### 变体部分
- 添加/删除变体（键值对）
- 用于 A/B 测试

### 定向规则部分
- 列出已有规则，含优先级、名称、百分比、条件数
- 添加/删除/编辑规则
- 打开 `FeatureFlagRuleEditor` 模态框

### API 集成
```typescript
// 创建
await FeatureFlagAPI.createFeatureFlag(payload)

// 更新
await FeatureFlagAPI.updateFeatureFlag(flagKey, payload)

// 切换
await FeatureFlagAPI.enableFeatureFlag(flagKey)
await FeatureFlagAPI.disableFeatureFlag(flagKey)

// 归档
await FeatureFlagAPI.archiveFeatureFlag(flagKey)
```

## FeatureFlagRuleEditor

用于创建/编辑定向规则的模态框：

### 规则字段
- 规则名称
- 优先级（越低越先评估）
- 百分比（0-100，滑块控制）
- 变体分配（可选）
- 开始时间 / 结束时间（日期时间选择器，用于时间窗口）

### 条件构建器
- **属性**：tenant、workspace、user、role、group、tier、region、requestSource、environment
- **操作符**：EQUALS、IN、NOT_IN、GT、LT、GTE、LTE、CONTAINS
- **值**：文本输入（IN/NOT_IN 用逗号分隔）
- 动态添加/删除条件

### 定向规则类型

| 规则类型 | 描述 | 示例 |
|----------|------|------|
| 租户 | 匹配特定租户 ID | `tenant-123` → 启用 |
| 工作区 | 匹配特定工作区 ID | `workspace-456` → 启用 |
| 用户 | 匹配特定用户 ID | `user-789` → 启用 |
| 角色 | 匹配特定角色 | `ADMIN` → 启用 |
| 套餐 | 匹配特定套餐 | `ENTERPRISE` → 启用 |
| 百分比 | 基于哈希的百分比灰度 | 10% 用户 → 启用 |
| 时间窗口 | 在时间范围内激活 | 2026-06-01 至 2026-06-30 |
| 区域 | 匹配特定区域 | `us-east-1` → 启用 |

## FeatureFlagEvaluationPreview

交互式评估模拟器：

1. 从已加载的 flags 列表中选择一个 flag
2. 填写上下文字段（租户、工作区、用户、角色、套餐等）
3. 提交评估请求
4. 查看结果：启用/禁用、匹配的规则、变体、原因代码

## API 客户端

`FeatureFlagAPI` 客户端（`src/api/admin/feature-flags.ts`）提供：

```typescript
interface FeatureFlagAPI {
  listFeatureFlags(): Promise<FeatureFlagDefinition[]>
  getFeatureFlag(flagKey: string): Promise<FeatureFlagDefinition>
  createFeatureFlag(flag: FeatureFlagDefinition): Promise<FeatureFlagDefinition>
  updateFeatureFlag(flagKey: string, flag: FeatureFlagDefinition): Promise<FeatureFlagDefinition>
  deleteFeatureFlag(flagKey: string): Promise<void>
  archiveFeatureFlag(flagKey: string): Promise<void>
  enableFeatureFlag(flagKey: string): Promise<void>
  disableFeatureFlag(flagKey: string): Promise<void>
  evaluateFlag(flagKey: string, context: FeatureFlagEvaluationContext): Promise<FeatureFlagEvaluationResult>
  getEvaluationLogs(flagKey: string): Promise<FeatureFlagAuditEvent[]>
}
```

## 领域类型

```typescript
interface FeatureFlagDefinition {
  flagKey: string
  name: string
  description: string
  type: 'BOOLEAN' | 'STRING' | 'NUMBER' | 'JSON'
  defaultValue: string
  variants: FeatureFlagVariant[]
  targetingRules: FeatureFlagTargetingRule[]
  enabled: boolean
  owner: string
  tags: string[]
  createdAt: string
  updatedAt: string
  archived: boolean
}

interface FeatureFlagTargetingRule {
  ruleId?: string
  priority: number
  name: string
  percentage: number
  conditions: FeatureFlagCondition[]
  variantKey?: string
  startAt?: string
  endAt?: string
}

interface FeatureFlagCondition {
  attribute: string
  operator: string
  value: string
}

interface FeatureFlagVariant {
  key: string
  value: string
  description?: string
}

interface FeatureFlagEvaluationContext {
  tenant: string
  workspace: string
  user: string
  role: string
  group: string
  tier: string
  region: string
  requestSource: string
  environment: string
}

interface FeatureFlagEvaluationResult {
  flagKey: string
  enabled: boolean
  variant: string
  reasonCode: string
  matchedRule: string
}
```

## 用户端测试功能

用户门户中的 `BetaFeaturesPanel` 显示：
- 由带有 `beta` 标签的 feature flag 控制的功能
- 选择加入/退出开关
- 功能描述和状态

`useFeatureFlag` 组合式函数提供：
```typescript
const { isEnabled, loading, error } = useFeatureFlag('flag-key')
```

## DisabledFeatureState 组件

当功能被 flag 禁用时，`DisabledFeatureState` 组件显示：
- 锁定图标
- "功能已禁用"消息
- 可选的升级提示
