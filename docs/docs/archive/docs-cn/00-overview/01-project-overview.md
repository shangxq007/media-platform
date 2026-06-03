# 项目总览

> **模块：** 全部
> **最后更新：** 2026-05-18

## 什么是 Media Platform？

Media Platform 是一个全面的 **AI 驱动的视频制作与渲染编排平台**。它提供端到端的媒体处理能力，包括多提供者渲染管线、Prompt 工程管理、成本控制、权益管理、异常检测、监控和动态扩展支持。

## 技术栈

| 层级 | 技术 | 版本 |
|------|------|------|
| 语言 | Java | 25 |
| 框架 | Spring Boot | 4.0.4 |
| 模块化 | Spring Modulith | 2.0.4 |
| AI | Spring AI | 2.0.0-M3 |
| 工作流 | Temporal | 1.33.0 |
| 规则引擎 | LiteFlow | 2.15.3.2 |
| 数据库 | PostgreSQL | 16 |
| 迁移 | Flyway | BOM 管理 |
| ORM | jOOQ | 3.19.18 |
| API 文档 | springdoc OpenAPI | 3.0.2 |
| 插件系统 | PF4J | 3.15.0 |
| 前端 | Vue 3 + Vite | — |
| 前端测试 | Vitest | — |
| 构建 | Gradle | 9.1 |

## 核心能力

### ✅ 已实现

- **渲染管线** — 多阶段管线（特效 → 转码 → 封装），6 个提供者实现
- **Prompt 工程** — 模板生命周期管理与安全治理
- **成本控制** — 计量、预算、预留和异常检测
- **权益管理** — 5 级策略系统（FREE/PRO/TEAM/ENTERPRISE/EXPERIMENTAL）
- **Feature Flag** — 基于 OpenFeature 的特性开关系统，支持目标规则和百分比灰度
- **GraphQL** — 只读查询聚合层，支持 DataLoader 批量加载
- **NLQ** — 自然语言查询分析助手
- **动态扩展** — 运行时插件加载，支持沙箱执行和回滚
- **监控** — Sentry + OpenReplay 集成，支持会话回放
- **问题数据** — 自动检测、隔离、自动修复和隔离区
- **前端视频编辑器** — 基于时间线的视频编辑器，支持导出、特效、字幕
- **审计追踪** — 跨所有模块的全面审计日志

### ⚠️ 部分实现

- **AI 模块** — 基础设施就绪（ChatProvider SPI、ModelRouter），但使用 `StubChatProvider`
- **支付模块** — 领域模型存在，但所有提供者均为 Noop 存根

### 🔧 存根 / Mock

- `StubChatProvider` — 返回硬编码响应
- `NoopStripePaymentProvider` — 空操作支付处理
- `NoopHyperswitchPaymentProvider` — 空操作支付处理
- `NoopKillBillBillingEngine` — 仅返回投影状态
- `LocalFeatureFlagProvider` — 仅内存实现，不持久化

### 📋 未来规划

- 真实 GLM/Claude/GPT 模型集成
- 真实 Stripe/Hyperswitch 支付集成
- Spring Security + JWT 认证
- 多租户数据隔离强制执行
- OpenTelemetry 集成
- 远程渲染 Worker GPU 加速
- OTIO（OpenTimelineIO）完整集成

## 项目结构

```
media-platform-workspace/
├── media-platform/              # 主应用仓库
│   ├── platform-app/            # Spring Boot 应用入口
│   ├── shared-kernel/           # 共享类型、事件、错误码
│   ├── render-module/           # 渲染编排与提供者
│   ├── workflow-module/         # Temporal + LiteFlow 工作流
│   ├── ai-module/               # AI 模型集成（存根）
│   ├── prompt-module/           # Prompt 模板管理
│   ├── extension-module/        # 动态扩展（PF4J）
│   ├── sandbox-runtime-module/  # 沙箱执行
│   ├── billing-module/          # 成本计量与预算
│   ├── entitlement-module/      # 基于层级的访问控制
│   ├── policy-governance-module/# Feature Flag 与策略评估
│   ├── federation-query-module/ # GraphQL 与 NLQ
│   ├── [20+ 其他模块]/          # 参见 02-modules/
│   ├── frontend/                # Vue 3 视频编辑器
│   ├── docs/                    # 历史文档（参见 archive/）
│   └── scripts/                 # 验证脚本
├── docs/                        # 文档（含中英文）
├── prompts/                     # 执行 Prompt 与 MANIFEST
└── scripts/                     # 工作区级脚本
```

## 模块数量：30

详见 `02-modules/` 获取模块细分，`01-architecture/03-module-architecture.md` 获取依赖关系图。
