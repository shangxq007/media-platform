# 媒体平台 (Media Platform)

> **状态:** 生产就绪审查完成 (Prompt 13-53)  
> **最后更新:** 2026-05-14  
> **版本:** 0.1.0

## 🎯 项目概述

**媒体平台** 是一个全面的 AI 驱动的视频制作与渲染编排平台，基于 Spring Boot 4.0.4、Spring Modulith 2.0.4 和 Java 25 构建。提供端到端的媒体处理能力，包括多提供者渲染流水线、提示词工程管理、成本控制、权益管理、异常检测、监控反馈和动态扩展支持。

### 核心能力

- **渲染流水线** - 多阶段流水线（特效 → 转码 → 打包），支持 6 个渲染提供者
- **提示词工程** - 模板生命周期管理与安全治理
- **成本控制** - 计量、预算、预留和异常检测
- **权益管理** - 5 层策略系统（免费/专业/团队/企业/实验）
- **监控反馈** - Sentry + OpenReplay 集成，支持会话回放
- **问题数据** - 自动检测、隔离、自动修复和隔离区管理
- **动态扩展** - 运行时插件加载，支持沙箱执行和回滚

---

## 🚀 快速开始

### 前置条件

- Java 25+
- Node.js 22+ / npm
- Docker & Docker Compose（用于基础设施服务）

### 本地开发

```bash
# 1. 克隆仓库
git clone <repository-url>
cd media-platform-workspace/media-platform

# 2. 启动后端（终端 1）
./gradlew :platform-app:bootRun
# API 地址: http://localhost:8080
# Swagger UI: http://localhost:8080/swagger-ui.html

# 3. 启动前端（终端 2）
cd frontend
npm install
npm run dev
# 前端地址: http://localhost:3000

# 4. 运行所有测试
./gradlew test

# 5. 运行基础设施验证
bash scripts/infra-validate.sh
```

### Docker 部署

```bash
# 构建所有服务
docker compose build

# 启动所有服务
docker compose up -d

# 查看日志
docker compose logs -f platform-app

# 停止所有服务
docker compose down
```

### 健康检查

```bash
curl http://localhost:8080/actuator/health
# 预期: {"status":"UP"}
```

---

## 📋 模块参考

### 核心基础设施 (✅ 完成)

| 模块 | 用途 | 关键特性 |
|------|------|----------|
| `shared-kernel` | 共享工具 | 事件、错误码、租户上下文、日志 |
| `platform-app` | 应用入口 | Spring Boot 应用、OpenAPI 配置、安全配置 |
| `config-module` | 配置管理 | 带版本控制的 CRUD |
| `secrets-config-module` | 密钥管理 | 密钥引用管理 |
| `datasource-module` | 数据源联邦 | DSL 上下文注册表、联邦查询 |
| `identity-access-module` | 身份与访问 | API 密钥、用户、租户、项目 |
| `scheduler-module` | 任务调度 | Cron 任务、手动触发、死信队列支持 |
| `outbox-event-module` | 事务发件箱 | 带重试的事件发布 |

### 媒体处理 (✅ 完成)

| 模块 | 用途 | 关键特性 |
|------|------|----------|
| `render-module` | 渲染编排 | 6 个提供者、流水线、配额 |
| `workflow-module` | 工作流引擎 | Temporal + LiteFlow 编排 |
| `ai-module` | AI 集成 | ChatProvider SPI、模型路由 |
| `remote-render-worker` | 远程执行 | 工作者注册、任务分发 |
| `artifact-catalog-module` | 工件跟踪 | 输出元数据、存储 URI |
| `storage-module` | 存储管理 | 多提供者存储目录 |

### 渲染提供者实现 (✅ 完成)

| 提供者 | 类型 | 能力 |
|--------|------|------|
| JavaCV | 渲染 | 转码、水印、字幕烧录 |
| OFX | 渲染 | 特效、过渡、滤镜 |
| GPAC | 打包 | DASH/HLS、CMAF、MP4 faststart |
| MLT | 渲染 | XML 生成、melt 命令 |
| GStreamer | 渲染 | 流水线处理、字幕叠加 |
| FFMPEG | 渲染 | 通用转码 |

### GPU 支持 (✅ 完成)

| 预设 | 编码器 | 层级访问 |
|------|--------|----------|
| GPU_H264 | NVENC H.264 | 团队+ |
| GPU_H265 | NVENC HEVC | 团队+ |
| GPU_VP9 | VAAPI VP9 | 团队+ |

### 业务逻辑 (✅ 完成)

| 模块 | 用途 | 关键特性 |
|------|------|----------|
| `billing-module` | 成本管理 | 计量、预算、预留、对账 |
| `quota-billing-module` | 配额管理 | 桶、阈值事件 |
| `entitlement-module` | 访问控制 | 功能检查、层级策略 |
| `commerce-module` | 商务 | 结账、收入、采购订单 |
| `audit-compliance-module` | 审计与合规 | 审计跟踪、异常检测、用户体验保护 |
| `policy-governance-module` | 策略管理 | 功能标志、策略评估 |
| `compatibility-migration-module` | 模式迁移 | 9 个模式族 |
| `notification-module` | 通知 | 多渠道、模板 |
| `observability-module` | 监控 | 健康检查、断路器、SLA 指标 |
| `user-analytics-module` | 分析 | 行为事件、画像、分群 |

### 提示词工程 (✅ 完成)

| 模块 | 用途 | 关键特性 |
|------|------|----------|
| `prompt-module` | 模板管理 | CRUD、版本控制、渲染、安全 |
| `ai-module` | AI 模型集成 | ChatProvider SPI、模型路由 |

### 扩展与安全 (✅ 完成)

| 模块 | 用途 | 关键特性 |
|------|------|----------|
| `extension-module` | 动态扩展 | PF4J 插件、工具注册表、沙箱 |
| `sandbox-runtime-module` | 沙箱执行 | Wasm/容器占位符 |

### 前端 (✅ 完成)

| 组件 | 用途 |
|------|------|
| `TimelineEditor.vue` | 视频时间线编辑 |
| `ExportPanel.vue` | 导出（传统 / 增量+AI 时间线编辑、增量计划预览、作业轮询） |
| `AiTimelineEditPanel.vue` | AI 自然语言改时间线（对接 `/timeline/ai-edit`） |
| `IncrementalRenderPanel.vue` | 增量渲染计划与提交（语义 Diff、段级复用） |
| `EffectsPanel.vue` | 特效包管理 |
| `SubtitleTimeline.vue` | 字幕时间线编辑 |
| `PromptManagementPage.vue` | 提示词模板管理 |
| `PromptTemplateList.vue` | 模板列表（搜索/过滤） |
| `PromptTemplateEditor.vue` | 模板编辑器（渲染预览） |
| `FeedbackButton.vue` | 用户反馈弹窗 |
| `MonitoringStatus.vue` | Sentry/OpenReplay 状态显示 |

---

## ⚠️ 状态图例

| 符号 | 含义 |
|------|------|
| ✅ 完成 | 完全实现并测试通过 |
| ⚠️ 部分 | 核心功能已实现，部分存根保留 |
| 🔧 存根/模拟 | 基础设施就绪，真实实现待完成 |
| 📋 未来 | 已规划但尚未实现 |
| 🔴 阻塞 | 生产部署前必须修复 |

---

## 🔴 关键阻塞项（生产部署前必须修复）

1. **无身份认证** - 无用户认证层。仅支持服务间 API 密钥认证。
2. **无租户隔离** - TenantContext 存在但未在数据层强制执行。
3. **支付存根** - 支付提供者为 Noop。需要真实的 Stripe/Hyperswitch 集成。
4. **AI 默认存根** - 未启用 `litellm` profile 时为 StubChatProvider；生产请部署 LiteLLM 并配置 `app.ai.routing`（见 [ai-gateway-architecture.md](ai-gateway-architecture.md)）。

---

## 📊 质量门禁

| 门禁 | 状态 |
|------|------|
| `./gradlew clean test` | ✅ 121 个测试通过 |
| `./gradlew :platform-app:bootJar` | ✅ 构建成功 |
| `docker compose config` | ✅ 配置有效 |
| `vite build` | ✅ 122 个模块 |
| `scripts/infra-validate.sh` | ✅ 11 项检查通过 |

---

## 📖 文档索引

### 中文文档

| 文档 | 用途 |
|------|------|
| [docs/zh/platform-guide/README.md](docs/zh/platform-guide/README.md) | **平台指南（分卷）** — 架构 / 依赖 / 实现 / 部署 / 集成 / 路线图 |
| [docs/zh/platform-guide.md](docs/zh/platform-guide.md) | 旧版单页入口（已重定向至分卷） |
| [docs/zh/README.md](docs/zh/README.md) | 项目总览（本文档） |
| [docs/zh/module-reference.md](docs/zh/module-reference.md) | 各模块详细说明 |
| [docs/zh/development-guidelines.md](docs/zh/development-guidelines.md) | 开发注意事项 |
| [docs/zh/usage-guide.md](docs/zh/usage-guide.md) | 使用方式 |
| [docs/zh/architecture.md](docs/zh/architecture.md) | 项目架构 |
| [docs/zh/prompt-platform.md](docs/zh/prompt-platform.md) | 提示词工程平台说明 |
| [docs/zh/problematic-data.md](docs/zh/problematic-data.md) | 问题数据处理说明 |
| [docs/zh/dynamic-extension.md](docs/zh/dynamic-extension.md) | 动态扩展系统说明 |
| [docs/zh/monitoring-feedback.md](docs/zh/monitoring-feedback.md) | 监控与反馈系统说明 |
| [docs/zh/deployment.md](docs/zh/deployment.md) | 部署清单与回滚方案 |
| [docs/zh/faq.md](docs/zh/faq.md) | 常见问题与端到端演示 |
| [docs/zh/incremental-rendering.md](docs/zh/incremental-rendering.md) | Internal Timeline 1.0 与增量/段级/S3 缓存 |
| [docs/zh/ai-timeline-editing.md](docs/zh/ai-timeline-editing.md) | AI 时间线编辑 API、多轮改稿与 metadata |
| [docs/zh/timeline-version-control.md](docs/zh/timeline-version-control.md) | 时间线领域版控（修订链、冲突、History、patch 预览） |
| [docs/zh/ai-gateway-architecture.md](docs/zh/ai-gateway-architecture.md) | LiteLLM / Spring AI 路由与多厂商配置 |
| [docs/zh/vault-and-rustfs-setup.md](docs/zh/vault-and-rustfs-setup.md) | Vault、RustFS、Temporal、Cloudflare R2 |
| [docs/zh/authentik-oidc-resource-server.md](docs/zh/authentik-oidc-resource-server.md) | Authentik 自托管、OIDC Resource Server 选型与部署 |
| [docs/zh/authentik-property-mapping-and-migration.md](docs/zh/authentik-property-mapping-and-migration.md) | Authentik Property Mapping 与 user-1 迁移 |

### 英文文档

所有英文文档保留在 `docs/` 目录下，中文文档为翻译版本。

---

## 🏗️ 项目结构

```
media-platform/
├── shared-kernel/           # 共享工具、事件、错误码
├── platform-app/            # 应用入口
├── render-module/           # 渲染编排，6 个提供者
├── workflow-module/         # Temporal + LiteFlow 工作流
├── ai-module/               # AI ChatProvider SPI、可配置路由
├── remote-render-worker/    # 远程工作者（GPU 支持）
├── prompt-module/           # 提示词模板管理
├── extension-module/        # 动态扩展、工具注册表
├── sandbox-runtime-module/  # 沙箱执行（占位符）
├── billing-module/          # 成本计量、预算、对账
├── entitlement-module/      # 层级访问控制
├── audit-compliance-module/ # 审计跟踪、异常检测
├── policy-governance-module/# 功能标志、策略评估
├── notification-module/     # 多渠道通知
├── observability-module/    # 健康监控、断路器
├── user-analytics-module/   # 行为分析
├── scheduler-module/        # 任务调度
├── commerce-module/         # 商务（存根支付）
├── payment-module/          # 支付网关（存根）
├── compatibility-migration-module/ # 模式迁移
├── identity-access-module/  # API 密钥、用户、租户
├── config-module/           # 配置管理
├── secrets-config-module/   # 密钥管理
├── datasource-module/       # 联邦查询
├── outbox-event-module/     # 事务发件箱
├── storage-module/          # 存储目录
├── artifact-catalog-module/ # 工件跟踪
├── cloud-resource-module/   # 云资源
├── frontend/                # Vue.js 视频编辑器
├── docs/                    # 英文文档
├── docs/zh/                 # 中文文档
├── scripts/                 # 验证脚本
└── prompts/                 # 执行提示词 + MANIFEST
```

---

## 🔧 开发约定

### 代码风格
- Java 25 record 用于不可变数据结构
- Spring Modulith 用于模块边界
- 可配置错误码，支持 i18n（中/英）
- 所有异常使用 `PlatformException` 并包含结构化详情

### 模块边界
- 每个模块有 `package-info.java` 标注 `@ApplicationModule`
- 跨模块通信通过 `port/` 包中的端口接口
- 事件通过 Spring `ApplicationEventPublisher` 发布

### 测试
- 单元测试：集成用 `@SpringBootTest`，纯单元用 JUnit
- PostgreSQL 用于集成测试（Testcontainers）
- 测试覆盖：40+ 测试文件中约 200+ 个测试

### 错误处理
- 所有错误使用可配置的 `errorCode` + `message` + `details`
- 错误码定义在 `shared-kernel/src/main/resources/error-codes.json`
- 51 个错误码覆盖所有模块，支持中/英翻译

---

## 📝 许可证

内部项目。保留所有权利。

---

*由 Kilo Code 生成 (Prompt 55)。完整执行历史见 [prompts/MANIFEST.md](prompts/MANIFEST.md)。*
