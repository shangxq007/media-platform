# 01 · 架构原则

> [← 分卷索引](README.md) | 下一卷：[02-技术栈与依赖](02-dependencies.md)

---

## 产品定位

**媒体平台**是 AI 驱动的视频制作与**服务端渲染编排**系统：多租户项目、时间线编辑、权益与计费、多阶段渲染流水线、成片交付与运维可观测性。

---

## 模块化单体（Modular Monolith）

- **运行时：** 单一 Spring Boot 进程（`platform-app`），非微服务拆分。
- **边界：** Spring Modulith + Gradle 多模块；跨模块通过 **Port 接口**、**领域事件**、**Outbox** 通信。
- **前端：** React 19 SPA，构建产物可嵌入 `platform-app` 静态资源或独立 Vite 开发服。

```
┌────────────────────────────────────────────────────────────────────┐
│  Browser (React 19 + Zustand)  ──REST/GraphQL──►  platform-app :8080   │
└────────────────────────────────────────────────────────────────────┘
         │                    │                    │
         ▼                    ▼                    ▼
   PostgreSQL            Object Storage         Temporal (可选)
   Flyway 迁移           RustFS / R2 / S3       Task Queue: media-platform-tasks
   H2 (仅 dev 默认)      段缓存 / 成片            LiteFlow / 本地编排 (默认)
```

更细的模块关系图与渲染数据流见 [architecture.md](../architecture.md)。

---

## 设计取舍（当前）

| 选择 | 原因 |
|------|------|
| Internal Timeline 1.0 为渲染真源 | 语义 Diff、段级缓存、AI Patch、增量计划 |
| 编辑器 schema 2.0 为前端真源 | Pinia 时间线；提交时服务端规范化为 1.0 |
| `ai-module` 仅 SPI；Spring AI 在 `platform-app` | 避免 AI 实现污染领域模块 |
| LiteLLM 作推荐 AI 网关 | 单 OpenAI 兼容端点 + 多厂商 model 名 |
| Vault 存交付/云凭证引用 | 库内不存明文密钥（生产目标） |

---

## 逻辑分层（单进程内）

| 层 | 职责 | 代表 |
|----|------|------|
| API | REST、OpenAPI、租户路径 | `RenderController`、`platform-app` |
| 应用服务 | 编排、校验、状态机 | `RenderOrchestratorService`、`AiTimelineEditService` |
| 领域 | Timeline、RenderJob、Entitlement | `render-module/domain` |
| 基础设施 | Provider、S3、Vault、Temporal | `storage-module`、`workflow-module` |
| 组合根 | 装配、Profile、静态资源 | `platform-app` |

---

## 关键数据流（摘要）

**渲染（增量路径）：**

```
ExportPanel → POST incremental/submit → RenderOrchestrator
  → Internal 1.0 规范化 → IncrementalRenderPlan → ProviderRouter → 工件 / 缓存
  → [可选] Delivery → Vault 凭证 → SFTP/WebDAV/S3
```

**AI 时间线编辑：**

```
ai-edit → ai-module 路由 → LiteLLM/Stub → Patch 或全量 JSON
  → metadata.platform.ai.* / aiProposals（人工确认）
```

详见 [04-核心实现](04-implementation.md)。
