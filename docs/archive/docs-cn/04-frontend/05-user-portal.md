# 用户门户

> **模块：** `frontend/src/pages/user/`
> **最后更新：** 2026-05-19

## 概述

用户门户提供项目、能力、用量、账单、积分和反馈的自助访问功能。

## 实现状态

| 组件 | 状态 |
|------|------|
| `UserDashboardPage` | ✅ 已实现 |
| `MyProjectsPage` | ✅ 已实现 |
| `MyCapabilitiesPage` | ✅ 已实现 |
| `MyUsagePage` | ✅ 已实现 |
| `MyBillingPage` | ✅ 已实现 |
| `MyCreditsPage` | ✅ 已实现 |
| `MyFeedbackPage` | ✅ 已实现 |
| `MySettingsPage` | ✅ 已实现 |
| `BetaFeaturesPanel` | ✅ 已实现 |
| `AnalyticsAssistantPage` | ✅ 已实现 |
| `MyReportsPage` | ✅ 已实现 |
| `EntitlementExplanationPanel` | ✅ 已实现 |

## 页面列表

| 页面 | 路由 | 组件 | 用途 |
|------|------|------|------|
| 仪表盘 | `/` | `UserDashboardPage` | 概览与快捷操作 |
| 我的项目 | `/me/projects` | `MyProjectsPage` | 项目列表 |
| 能力 | `/me/capabilities` | `MyCapabilitiesPage` | 按套餐显示功能权限 |
| 用量 | `/me/usage` | `MyUsagePage` | 用量统计 |
| 账单 | `/me/billing` | `MyBillingPage` | 账单概览 |
| 积分 | `/me/credits` | `MyCreditsPage` | 积分钱包 |
| 反馈 | `/me/feedback` | `MyFeedbackPage` | 提交反馈 |
| 设置 | `/me/settings` | `MySettingsPage` | 用户设置 |
| 测试功能 | `/me/beta` | `BetaFeaturesPanel` | 测试功能访问 |
| 分析 | `/me/analytics` | `AnalyticsAssistantPage` | 自然语言查询分析 |
| 报告 | `/me/reports` | `MyReportsPage` | 已保存报告 |

## 仪表盘

仪表盘提供：
- 快速创建项目
- 最近项目列表
- 用量摘要（渲染分钟数、存储）
- 积分余额
- 当前激活的 feature flag
- 通知中心

## MyCapabilitiesPage

能力页面是权益和 feature flag 系统的主要用户端视图。

### 布局

```
PageHeader: "能力" + 套餐徽章 + 刷新按钮
├── 网格（3 列）
│   ├── CurrentPlanPanel（当前套餐面板）
│   ├── UsageSummaryPanel（用量摘要面板）
│   └── UpgradeSuggestionPanel（升级建议面板）
├── PageSection: "套餐概览"
│   └── 网格（4 个 MetricCard）：套餐、渲染分钟数、并发任务、导出格式
├── PageSection: "Feature Flags"
│   └── 网格（2 列）：Flag 显示名称、描述、开/关状态
├── 网格（2 列）
│   ├── PageSection: "权益策略"
│   │   └── 键值对：最大分辨率、每月分钟数、并发任务、
│   │       允许 GPU、远程 Worker、自定义字体、水印
│   └── PageSection: "导出能力"
│       └── 键值对：格式（徽章形式）、预设数量、
│           最大分辨率、GPU 导出、并发导出
└── 升级提示（非企业版套餐显示）
```

### 数据加载

```typescript
const capabilities = ref<MyCapabilities | null>(null)

async function loadCapabilities() {
  capabilities.value = await MeEntitlementAPI.getMyCapabilities()
}
```

`MeEntitlementAPI.getMyCapabilities()` 调用 `GET /api/v1/entitlements/me/capabilities`。

### MyCapabilities 类型

```typescript
interface MyCapabilities {
  tier: string                            // "FREE" | "PRO" | "TEAM" | "ENTERPRISE"
  entitlementPolicy: {
    maxResolutionWidth: number
    maxResolutionHeight: number
    monthlyRenderMinutes: number
    maxConcurrentJobs: number
    gpuAllowed: boolean
    remoteWorkerAllowed: boolean
    customFontsAllowed: boolean
    watermark: boolean
  }
  featureFlags: FeatureFlag[]
  exportCapabilities: {
    allowedFormats: string[]       // 例如 ["mp4", "webm", "mov"]
    allowedPresets: string[]       // 例如 ["default_1080p", "pro_1080p"]
    maxResolutionWidth: number
    maxResolutionHeight: number
    gpuExportAllowed: boolean
    maxConcurrentExports: number
  }
}

interface FeatureFlag {
  flagKey: string
  displayName: string
  description: string
  enabled: boolean
  scope: string
  targetTier: string
}
```

### 套餐对比展示

| 功能 | 免费版 | 专业版 | 团队版 | 企业版 |
|------|--------|--------|--------|--------|
| 最大分辨率 | 720p | 1080p | 4K | 4K |
| 每月分钟数 | 60 | 300 | 1,200 | 6,000 |
| 水印 | 有 | 无 | 无 | 无 |
| GPU | 否 | 否 | 是 | 是 |
| 自定义字体 | 否 | 是 | 是 | 是 |
| 并发任务 | 1 | 3 | 10 | 50 |
| 导出格式 | mp4, webm | +mov | +dash, hls | +cmaf |

## 测试功能面板

显示由 feature flag 控制的功能：
- 仅当用户的 `beta-features` 标志启用时可见
- 列出测试功能及其描述
- 允许选择加入/退出

## CurrentPlanPanel

显示用户当前订阅套餐：
- 套餐名称和等级
- 续费日期
- 用量进度条（已用渲染分钟数 / 总量）

## UsageSummaryPanel

显示用量统计：
- 本月已用渲染分钟数
- 已用存储空间
- 已完成任务数
- 剩余积分

## UpgradeSuggestionPanel

基于当前套餐提供上下文升级建议：
- 免费版 → 专业版："解锁 1080p 导出并去除水印"
- 专业版 → 团队版："启用 GPU 渲染和 4K 导出"
- 团队版 → 企业版："优先队列和专属支持"

## EntitlementExplanationPanel

解释某功能可用或不可用的原因：
- 显示匹配的策略（套餐、授权或覆盖）
- 如果功能不可用则显示升级路径
- 链接到能力页面
