# 项目架构、运维、开发与智能渲染路线图

> **版本**：基于代码 0.2.0-SNAPSHOT + 已有文档综合生成
> **生成日期**：2026-05-28
> **状态标记说明**：
> - ✅ 已实现
> - ⚠️ 部分实现
> - 🔧 存根/模拟
> - 📋 规划中
> - 🔴 生产阻断
> - ❓ 需要环境验证

---

## 目录

1. [项目总览](#1-项目总览)
2. [功能说明](#2-功能说明)
3. [系统架构](#3-系统架构)
4. [技术栈](#4-技术栈)
5. [API / Endpoint 概览](#5-api--endpoint-概览)
6. [配置说明](#6-配置说明)
7. [数据库 / Migration](#7-数据库--migration)
8. [审计与安全](#8-审计与安全)
9. [Render / Worker](#9-render--worker)
10. [部署](#10-部署)
11. [运维 Runbook](#11-运维-runbook)
12. [开发注意事项](#12-开发注意事项)
13. [验收清单](#13-验收清单)
14. [差异与风险](#14-差异与风险)
15. [未来方向：智能渲染与自然语言编辑](future-roadmap-otio-llm.md)

---

## 1. 项目总览

### 系统目标

AI 视频生产与渲染编排平台，提供从素材上传、时间线编辑、AI 辅助编辑、多提供商渲染、交付分发的完整视频处理链路。

### 当前能力

| 领域 | 状态 | 说明 |
|------|------|------|
| 渲染管道 | ✅ | DAG 管道 + 13 个提供商 |
| 时间线编辑 | ✅ | 前端编辑器 + 版本控制 + 增量渲染 |
| 权限治理 | ✅ | Entitlement/Quota/Feature Flag/ABAC |
| 审计合规 | ✅ | 18 种分类 + 告警 + SSRF 防护 |
| 插件平台 | ✅ | PF4J + 沙箱执行 |
| 商业化 | ⚠️ | 域模型完整，支付 Noop |
| AI 集成 | 🔧 | StubChatProvider，SPI 就绪 |
| 部署运维 | ✅ | K8s + GitOps + 出口代理 + CI/CD |
| MCP 工具 | ✅ | 媒体探测/导入/导出/渲染计划 |

### 非目标

- 实时视频通话/直播
- 3D 建模/动画（Blender 集成仅为渲染）
- 专业 VFX 合成（Natron/Blender 为可选 Worker）
- 多区域部署（规划中）

### 用户角色

| 角色 | 描述 |
|------|------|
| 普通用户 | 编辑时间线、提交渲染、查看导出 |
| 工作区管理员 | 成员管理、角色分配、权益池 |
| 平台管理员 | 租户管理、系统配置、审计查看 |
| 外部系统 | API Key 认证、Worker 回调 |
| 服务账户 | 自动化任务 |

### 核心工作流

```
素材上传 → 时间线编辑 → 效果/字幕 → 提交渲染 → 多提供商执行 → 产物预览 → 交付分发
    ↓              ↓            ↓           ↓              ↓
  存储抽象      版本控制     效果包管理   配额检查       审计记录
                增量渲染     Prompt 工程   成本计量       通知投递
```

---

## 2. 功能说明

### 用户 / 租户 / Workspace

- ✅ 多租户（Tenant/Project/User/Workspace/WorkspaceMember/WorkspaceGroup）
- ✅ 角色权限（Role/Permission/RolePermission/UserRoleAssignment/GroupRoleAssignment）
- ✅ API Key 认证（默认关闭）
- ✅ OIDC JIT 用户预配
- ✅ 工作区权益池 + 成员授权 + 配额分配

### Project / Asset / Render Job

- ✅ 项目 CRUD
- ✅ 素材上传 + 媒体探测 + 完整性扫描
- ✅ 渲染作业 CRUD + 状态历史 + 陈旧补偿
- ✅ 产物目录 + 预览 + 关系追踪
- ✅ 增量渲染 + 渲染缓存
- ✅ 效果包管理（内置 + 自定义）
- ✅ 字幕系统（SRT/ASS/VTT 解析 + 烧录）

### Storage

- ✅ 存储抽象层（S3/MinIO/本地）
- ✅ AWS S3 / Cloudflare R2 兼容
- ✅ 孤立扫描 + 清除
- ⚠️ S3 默认关闭，需 staging 验证

### Delivery

- ✅ 交付目的地（SFTP/SMB）
- ✅ 交付策略 + 作业 + 重试
- ✅ Worker 模式
- ⚠️ Temporal Activity 默认关闭

### Audit

- ✅ 18 种审计分类
- ✅ 审计记录 NOT NULL + CHECK 约束
- ✅ 拒绝 burst 检测 + 安全告警
- ✅ Webhook/SLF4J/Noop 发布器
- ✅ SSRF 防护 + 敏感字段脱敏
- ✅ 前端审计日志查看器 + CSV 导出

### Admin

- ✅ 30+ 管理页面
- ✅ 租户/用户/渲染/交付/插件/配置/Feature Flag/策略/路由/监控/审计/计费管理

### Sandbox

- ✅ 三种模式（disabled/external/in-process）
- ✅ 外部 Worker HTTP 适配器
- ✅ K8s NetworkPolicy 隔离
- ✅ 审计日志（不记录代码内容）
- 🔴 生产禁止 in-process 模式

### Settings

- ✅ 用户设置（通知偏好 API、外观 localStorage）
- ✅ 系统配置（版本化 KV）
- ✅ 密钥引用管理
- ⚠️ Profile 保存/账户删除禁用

### Billing

- ✅ 计量/预算/信用钱包/发票/订阅
- ✅ 定价规则/折扣政策
- ✅ 商务产品/购物车/结账
- 🔧 Stripe/Hyperswitch Noop

### Entitlement

- ✅ 5 层策略（FREE/PRO/TEAM/ENTERPRISE/EXPERIMENTAL）
- ✅ Feature Bundle/Grant/Override
- ✅ 工作区权益池/成员授权/配额分配

### Report / GraphQL

- ✅ NLQ 分析助手
- ✅ GraphQL 聚合（默认关闭）
- ✅ 分析报告

### Alert / Webhook

- ✅ 拒绝 burst 检测
- ✅ Webhook 安全告警（SSRF 防护）
- ✅ 敏感字段自动脱敏

### Egress Smoke

- ✅ 出口代理 smoke 测试服务
- ✅ JVM 代理属性配置
- ✅ 就绪验证脚本
- ❓ 需 staging 验证

---

## 3. 系统架构

### C4 Context

```
┌─────────────────────────────────────────────────────────────────┐
│                        Media Platform                            │
│                                                                  │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐       │
│  │  用户    │  │  管理员  │  │ 外部系统 │  │ Worker   │       │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘       │
│       │              │              │              │             │
│       ▼              ▼              ▼              ▼             │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │                   platform-api (8080)                    │    │
│  │  REST /api/v1/*  │  GraphQL  │  OpenAPI  │  MCP         │    │
│  └───────────────────────┬─────────────────────────────────┘    │
│                          │                                       │
│  ┌───────────┬───────────┼───────────┬───────────┐              │
│  │           │           │           │           │              │
│  ▼           ▼           ▼           ▼           ▼              │
│ Postgres   Storage    Sandbox     Egress      Temporal          │
│            (S3/本地)   Worker      Proxy       (可选)           │
│                         (8091)     (3128)                        │
│                                                                  │
│  ┌──────────────┐  ┌──────────────┐                              │
│  │ render-worker│  │ 监控/追踪    │                              │
│  │   (8090)     │  │ Sentry/OR    │                              │
│  └──────────────┘  └──────────────┘                              │
└─────────────────────────────────────────────────────────────────┘
```

### Container Diagram

```
flowchart TD
    User["用户浏览器"] -->|"HTTPS"| Ingress["K8s Ingress (nginx)"]
    Ingress -->|"proxy"| API["platform-api\nport 8080"]
    API -->|"REST"| Frontend["Vue 3 SPA\n(static resources)"]
    API -->|"JWT验证"| OIDC["OIDC Provider\n(Authentik)"]
    API -->|"读写"| DB["PostgreSQL"]
    API -->|"渲染任务"| RW["remote-render-worker\nport 8090"]
    API -->|"代码执行"| SW["sandbox-worker\nport 8091"]
    API -->|"外部HTTP"| EP["egress-proxy\nport 3128"]
    RW -->|"读写存储"| S3["S3/MinIO"]
    RW -->|"状态回调"| API
    EP -->|"HTTPS CONNECT"| Ext["外部服务"]
    API -->|"指标"| Prom["Prometheus"]
    API -->|"追踪"| OTLP["OTLP Collector"]
    API -->|"错误"| Sentry["Sentry"]
    API -->|"回放"| OR["OpenReplay"]
```

### Render Flow

```
用户提交渲染请求
    → RenderController.createJob()
    → 配额检查 (QuotaModule)
    → 渲染作业持久化 (render_job 表)
    → 审计记录 (AuditModule)
    → Outbox 事件发布
    → 本地执行 / Temporal 工作流
        → DAG 管道
            → 效果处理 (EffectPack)
            → 转码 (FFmpeg/JavaCV/GStreamer/MLT)
            → 封装 (Bento4/GPAC/Shaka)
        → 产物存储 (StorageModule)
        → 产物元数据 (ArtifactCatalogModule)
    → 状态更新 + 通知
    → 交付 (DeliveryModule)
        → SFTP/SMB 分发
```

### Audit Flow

```
业务操作
    → AuditService.record()
    → audit_records 表写入
    → 分类检查 (AuditCategory enum)
    → 拒绝 burst 检测
        → 触发告警
        → SecurityAlertPort.publish()
            → Slf4j / Noop / Webhook
    → Outbox 事件 (可选)
        → 审计合规监听器
        → 前端审计日志展示
```

### Egress Flow

```
应用 Pod 需要访问外部服务
    → NetworkPolicy 检查
    → 仅允许到 egress-proxy:3128
    → HTTP_PROXY/HTTPS_PROXY 环境变量
    → (可选) JVM 代理属性
    → Squid 代理
        → ACL 检查 (allowed-domains)
        → 阻断元数据 IP / 私有网络
        → HTTPS CONNECT 隧道
    → 外部服务响应
    → 返回应用 Pod
```

---

## 4. 技术栈

| 层 | 技术 | 用途 | 代码证据 | 风险 / 备注 |
|---|---|---|---|---|
| 语言 | Java 25 | 后端运行时 | `build.gradle.kts:22` | 非常新的 JDK |
| 框架 | Spring Boot 4.0.4 | 核心框架 | `build.gradle.kts:7` | — |
| 模块化 | Spring Modulith 2.0.4 | 模块边界 | `build.gradle.kts:36` | — |
| AI | Spring AI 2.0.0-M3 | AI 客户端 | `build.gradle.kts:30` | **Milestone 版本** |
| 工作流 | Temporal SDK | 持久化工作流 | `application-temporal.yml` | 默认关闭 |
| 规则引擎 | LiteFlow 2.15.3.2 | 本地规则链 | `render-module/build.gradle.kts` | — |
| ORM | jOOQ 3.19.18 | 类型安全 SQL | `build.gradle.kts:8` | — |
| DB 迁移 | Flyway | Schema 迁移 | `application.yml:28-29` | — |
| Dev DB | H2 | 开发/测试 | `application.yml:22` | — |
| Prod DB | PostgreSQL 16 | 生产 | `application-prod.yml:17-19` | — |
| API 文档 | springdoc OpenAPI 3 | Swagger UI | `application.yml:30-40` | — |
| 插件 | PF4J | 运行时插件 | `extension-module/build.gradle.kts` | — |
| 前端 | Vue 3.5.13 | UI 框架 | `frontend/package.json` | — |
| 构建 | Vite 6 | 构建工具 | `frontend/vite.config.ts` | — |
| 语言 | TypeScript 5.7 | 类型安全 | `frontend/tsconfig.json` | — |
| 路由 | Vue Router 4.5 | SPA 路由 | `frontend/src/router/` | — |
| 状态 | Pinia 2.3 | 状态管理 | `frontend/src/stores/` | — |
| HTTP | Axios 1.7 | REST 客户端 | `frontend/src/api/` | — |
| 认证 | oidc-client-ts 3.1 | OIDC | `frontend/src/auth/` | — |
| 前端测试 | Vitest 3 + happy-dom | 测试 | `frontend/package.json` | — |
| 后端测试 | JUnit 5 + Testcontainers | 测试 | `build.gradle.kts:39` | — |
| 容器 | Docker + BuildKit | 镜像构建 | `Dockerfile` | — |
| 编排 | Kubernetes | 容器编排 | `k8s/` | — |
| CD | ArgoCD | GitOps CD | `gitops/argocd/` | — |
| 定制 | Kustomize | 环境差异化 | `k8s/overlays/` | — |
| CI | GitHub Actions | CI | `.github/workflows/ci.yml` | — |
| 仓库 | GHCR | 镜像存储 | `ci.yml:29` | — |
| 监控 | Prometheus + OTLP | 指标 | `application.yml:42-77` | — |
| 追踪 | Sentry | 错误追踪 | `sentry.*` 配置 | 默认关闭 |
| 回放 | OpenReplay | 会话回放 | `openreplay.*` 配置 | 默认关闭 |
| 代理 | Squid 6.6 | 出口代理 | `k8s/base/deployment-egress-proxy.yaml` | — |
| 网络 | NetworkPolicy | 零信任 | `k8s/base/networkpolicy-*.yaml` | 需 CNI 支持 |
| 渲染 | FFmpeg | 视频处理 | `application.yml:157` | — |
| 渲染 | JavaCV | 视觉处理 | `render-module/build.gradle.kts` | — |
| 渲染 | GStreamer | 多媒体管道 | `application.yml:160` | — |
| 渲染 | MLT | 非线性编辑 | `application.yml` | — |
| 渲染 | Blender | 3D 渲染 | `application.yml:321` | — |
| OTIO | 前端工具函数 | 时间线互转 | `frontend/src/utils/otio.ts` | **无 Java OTIO 库** |
| GraphQL | GraphQL | 查询聚合 | `application.yml:15-16` | 默认关闭 |

---

## 5. API / Endpoint 概览

| Method | Path | Auth | Module | Purpose |
|--------|------|------|--------|---------|
| POST | `/api/v1/render/jobs` | JWT+API Key | render | 创建渲染作业 |
| GET | `/api/v1/render/jobs` | JWT | render | 列表渲染作业 |
| GET | `/api/v1/render/jobs/{id}` | JWT | render | 获取渲染作业 |
| POST | `/api/v1/render/jobs/{id}/execute` | JWT+API Key | render | 执行渲染 |
| GET | `/api/v1/render/presets` | JWT | render | 渲染预设列表 |
| GET | `/api/v1/mcp/render/jobs` | JWT | render | MCP 渲染工具 |
| GET | `/api/v1/mcp/media/tools` | JWT | render | MCP 媒体工具 |
| POST | `/api/v1/mcp/media/tools/probe` | JWT | render | 媒体探测 |
| POST | `/api/v1/mcp/media/tools/import` | JWT | render | 时间线导入 |
| POST | `/api/v1/mcp/media/tools/export` | JWT | render | 时间线导出 |
| GET | `/api/v1/me` | JWT | identity | 用户仪表板 |
| GET | `/api/v1/me/projects` | JWT | identity | 项目列表 |
| GET | `/api/v1/me/shared-resources` | JWT | sharing | 共享资源 |
| GET | `/api/v1/me/exports` | JWT | render | 导出历史 |
| GET | `/api/v1/me/reports` | JWT | federation | 分析报告 |
| GET | `/api/v1/me/notifications` | JWT | notification | 通知列表 |
| GET | `/api/v1/me/usage` | JWT | billing | 使用量 |
| GET | `/api/v1/billing/me` | JWT | billing | 计费概览 |
| GET | `/api/v1/billing/me/credits` | JWT | billing | 信用余额 |
| GET | `/api/v1/prompts` | JWT | prompt | Prompt 模板列表 |
| POST | `/api/v1/prompts` | JWT | prompt | 创建模板 |
| POST | `/api/v1/prompts/{id}/render` | JWT | prompt | 渲染 Prompt |
| GET | `/api/v1/effect-packs` | JWT | render | 效果包列表 |
| POST | `/api/v1/effect-packs` | JWT | render | 创建效果包 |
| GET | `/api/v1/asset-governance` | JWT | storage | 资产治理 |
| GET | `/api/v1/media/assets/integrity` | JWT | storage | 资产完整性 |
| GET | `/api/v1/render/projects/{id}/timeline/revisions` | JWT | render | 时间线修订 |
| POST | `/api/v1/render/timeline-sync` | JWT | render | 时间线同步 |
| GET | `/api/v1/remote-worker` | API Key | render | Worker 注册 |
| POST | `/api/v1/remote-worker/heartbeat` | API Key | render | Worker 心跳 |
| POST | `/api/v1/remote-worker/callback` | API Key | render | Worker 回调 |
| GET | `/healthz` | None | observability | 存活探针 |
| GET | `/readyz` | None | observability | 就绪探针 |
| GET | `/metrics/summary` | None | observability | 指标摘要 |
| GET | `/actuator/health` | None | observability | 健康检查 |
| GET | `/actuator/prometheus` | None | observability | Prometheus |
| GET | `/swagger-ui.html` | None | platform-app | Swagger UI |
| GET | `/v3/api-docs` | None | platform-app | OpenAPI 文档 |
| GET | `/api/v1` | JWT | navigation | 导航可见性 |
| GET | `/api/v1/admin/navigation` | JWT (Admin) | platform-app | 路由管理 |
| GET | `/api/v1/admin/platform` | JWT (Admin) | platform-app | 部署就绪 |
| GET | `/api/v1/admin/shared-resources` | JWT (Admin) | sharing | 共享资源管理 |
| GET | `/api/v1/admin/tenants/{id}/ai` | JWT (Admin) | ai | LiteLLM 密钥管理 |
| POST | `/api/v1/dev/auth` | None (条件) | identity | Dev JWT 签发 |
| GET | `/api/v1/render/worker-queue` | JWT (Admin) | render | Worker 队列快照 |

---

## 6. 配置说明

### 安全配置

| Key | 默认 | Profile | Env Var | 安全说明 |
|-----|------|---------|---------|----------|
| `app.security.enabled` | `true` | dev: `false`, prod: `true` | — | 主安全开关 |
| `app.security.oauth2.enabled` | `false` | prod: `true` | `APP_SECURITY_OAUTH2_ENABLED` | OAuth2 开关 |
| `app.security.oauth2.issuer-uri` | — | — | `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI` | OIDC Issuer |
| `app.security.jwt.secret-key` | — | — | `APP_JWT_SECRET` | JWT 密钥（生产必须配置） |
| `app.security.cors.allowed-origin-patterns` | `http://localhost:*` | prod: `${APP_CORS_ALLOWED_ORIGIN}` | — | CORS 白名单 |
| `app.security.dev-auth-endpoint` | `false` | — | — | 开发认证端点（生产必须 false） |
| `app.security.oidc-dev-bootstrap.enabled` | `false` | — | — | OIDC 开发引导（生产必须 false） |

### 沙箱配置

| Key | 默认 | 说明 |
|-----|------|------|
| `app.sandbox.enabled` | `false` | 主开关 |
| `app.sandbox.execution-mode` | `disabled` | disabled/external/in-process |
| `app.sandbox.allow-in-process-eval` | `false` | 生产必须 false |
| `app.sandbox.allowed-languages` | `[]` | dev: `python` |
| `app.sandbox.max-execution-seconds` | `5` | 最大执行时间 |
| `app.sandbox.max-output-bytes` | `1048576` | 最大输出 1MB |
| `app.sandbox.worker.base-url` | — | dev: `http://sandbox-worker:8091` |

### 渲染配置

| Key | 默认 | 说明 |
|-----|------|------|
| `render.execution.mode` | `local` | local/temporal |
| `render.pipeline.dag.enabled` | `true` | DAG 管道 |
| `render.stale-compensator.enabled` | `true` | 陈旧补偿器 |
| `render.providers.ffmpeg.enabled` | `true` | FFmpeg |
| `render.providers.javacv.enabled` | `true` | JavaCV |
| `render.providers.natron.enabled` | `false` | Natron（需单独 profile） |
| `render.providers.shotstack.enabled` | `false` | Shotstack |

### 审计配置

| Key | 默认 | 说明 |
|-----|------|------|
| `app.audit.alerts.publisher.type` | `slf4j` | slf4j/noop/webhook |
| `app.audit.alerts.denied-burst.enabled` | `true` | 拒绝 burst 检测 |
| `app.audit.alerts.denied-burst.threshold` | `5` | 阈值 |
| `app.audit.alerts.denied-burst.window-seconds` | `600` | 窗口（秒） |
| `app.audit.alerts.webhook.allow-private-network` | `false` | Webhook SSRF 防护 |

### 出口代理配置

| Key | 默认 | 说明 |
|-----|------|------|
| `egress.proxy.smoke.enabled` | `false` | Smoke 测试开关 |
| `egress.proxy.smoke.url` | — | Smoke 目标 URL |
| `egress.proxy.jvm.enabled` | `false` | JVM 代理属性 |
| `egress.proxy.jvm.host` | `egress-proxy` | 代理主机 |
| `egress.proxy.jvm.port` | `3128` | 代理端口 |

### AI 配置

| Key | 默认 | 说明 |
|-----|------|------|
| `app.ai.default-provider` | `stubChatProvider` | 默认 AI 提供商 |
| `app.ai.providers.openai.enabled` | `false` | OpenAI 开关 |
| `spring.ai.openai.api-key` | — | OpenAI API Key |
| `LITELLM_API_KEY` | `sk-litellm` | LiteLLM 密钥 |
| `LITELLM_BASE_URL` | `http://127.0.0.1:4000/v1` | LiteLLM 地址 |

---

## 7. 数据库 / Migration

### Flyway Migration

| 版本 | 文件 | 描述 |
|------|------|------|
| V1 | `V1__initial_schema.sql` (1893 行) | 初始完整 schema，10 个域，70+ 张表 |
| V2 | `V2__backfill_audit_record_categories.sql` (159 行) | 审计记录分类回填（16 条规则） |
| V3 | `V3__enforce_audit_record_category_constraints.sql` (50 行) | 分类 NOT NULL + CHECK 约束 |

### 关键表（按域）

| 域 | 关键表 |
|----|--------|
| 核心 | `render_job`, `outbox_events`, `audit_records`, `config_item`, `storage_object` |
| 身份 | `tenant`, `project`, `user`, `api_key`, `workspace`, `workspace_member`, `role`, `permission` |
| 渲染 | `artifact`, `artifact_relation`, `render_job_status_history`, `timeline_snapshot`, `timeline_revision`, `effect_pack` |
| 商务 | `commerce_product`, `checkout_session`, `purchase_order`, `payment_attempt`, `subscription_contract`, `billing_invoice`, `credit_wallet` |
| 权益 | `feature_definition`, `feature_bundle`, `entitlement_grant`, `entitlement_override`, `quota_profile`, `quota_usage` |
| 通知 | `notification_event_definition`, `notification_template`, `notification_delivery_log` |
| 交付 | `delivery_destination`, `delivery_policy`, `delivery_job` |
| 扩展 | `extension_definition`, `extension_invocation`, `extension_routing_rule`, `extension_rollback_point` |
| 审计 | `audit_records`（18 种分类 + NOT NULL + CHECK 约束） |
| AI | `prompt_template`, `prompt_template_version`, `prompt_execution_run`, `tenant_litellm_virtual_key` |
| 分析 | `user_behavior_event`, `user_profile`, `user_segment`, `user_habits` |
| NLQ | `nlq_report_definition`, `nlq_query_history`, `nlq_report_execution` |
| 社交 | `social_connected_platform`, `social_post`, `social_post_analytics` |
| 沙箱 | `sandbox_execution_job` |
| 问题数据 | `problematic_data_record`, `quarantined_*` |

### 迁移注意事项

- Greenfield 环境：V1 包含完整 schema，直接应用
- 审计分类约束：V2 → V3 必须按顺序执行
- 生产环境：`spring.flyway.enabled=true`（prod profile）
- 开发环境：`spring.flyway.enabled=false`（使用 H2 内存库）

---

## 8. 审计与安全

### Audit Record Lifecycle

```
业务操作 → AuditService.record() → 分类判定 → audit_records 表写入
    → 拒绝 burst 检测 → 触发告警 → SecurityAlertPort.publish()
    → Outbox 事件（可选）→ 审计监听器 → 前端展示
```

### Admin Audit

- 前端 `AuditLogPage.vue`：筛选（分类/操作/演员/结果/租户/日期范围）、分页、详情（脱敏）、CSV 导出
- `AuditCompliance.vue`：Outbox 监控

### Audit Alert

- 拒绝 burst：5 次/600 秒，冷却 1800 秒
- 告警 JSON：rule/severity/category/action/actor/resource/result/attributes
- 发布失败不阻塞审计记录

### Denied Burst

- 可配置阈值/窗口/冷却/最大演员数
- 告警属性包含 deniedCount/windowSeconds/cooldownSeconds/sampleActions

### Webhook SSRF 防护

- 始终阻断：localhost, 127.x, ::1, 0.0.0.0, 169.254.169.254, 169.254.0.0/16, 私有 IPv4
- 允许列表：`allowed-hosts`（精确）+ `allowed-domain-suffixes`（后缀）
- 元数据 IP 始终阻断（即使 `allow-private-network=true`）

### CSV Export Safety

- 敏感字段脱敏（`[REDACTED]`）
- CSV 注入防护（需确认实现）

### Sensitive Field Redaction

authorization, cookie, token, apiKey, secret, password, signedUrl, virtualKey, litellmKey, bearer 等

### Sandbox Isolation

- 生产：`disabled` 或 `external`
- `in-process` 永不在生产启用
- K8s NetworkPolicy：sandbox-worker 拒绝所有出口
- 审计日志不记录代码内容

### Egress Control

- 零信任 NetworkPolicy
- 出口代理（Squid）+ 域名白名单
- HTTP_PROXY/HTTPS_PROXY/NO_PROXY 环境变量
- JVM 代理属性（Java 客户端兼容性）
- Smoke 测试验证

### Security Headers

- X-Content-Type-Options: nosniff
- Referrer-Policy
- X-Frame-Options
- Permissions-Policy
- CSP（通过 Spring Security）

---

## 9. Render / Worker

### Render Job Lifecycle

```
CREATED → QUEUED → RUNNING → COMPLETED / FAILED
    ↓         ↓        ↓           ↓
  配额检查   Worker   进度更新    产物存储
  审计记录   分配     状态历史    通知投递
```

### Worker Responsibilities

- 远程渲染 Worker（`remote-render-worker`）：port 8090，独立进程
- 注册/心跳/作业回调
- 执行渲染（调用 ffmpeg/JavaCV/GStreamer 等）
- 产物上传到存储
- 状态回调到 platform-api

### Artifact Storage

- `artifact` 表 + `artifact_relation` 表
- 存储 URI 引用（S3/本地）
- 前端预览 + 下载

### Retry

- 渲染作业：陈旧补偿器（30 分钟阈值）
- 交付作业：最大 3 次重试
- Outbox 事件：3 秒间隔 + 30 秒重试间隔

### Delivery

- SFTP（JSch）/ SMB（SMBJ）
- 策略路由
- Worker 模式 + Temporal Activity 模式

### Local Testing Path

```bash
docker compose -f docker-compose.dev.yml up -d
# db + app + render-worker + sandbox-worker
```

### Known Gaps

- GPU 渲染调度未实现
- 实际渲染依赖外部工具安装
- PopcornFX 文档提及但代码未找到
- 多区域部署未实现

---

## 10. 部署

### Docker Images

| 镜像 | Dockerfile | 基础镜像 | 端口 | 非 root 用户 |
|------|-----------|---------|------|-------------|
| platform-api | `platform/Dockerfile` | temurin:25-jre | 8080 | spring (10001) |
| render-worker | `remote-render-worker/Dockerfile` | temurin:25-jre | 8090 | worker (10002) |
| sandbox-worker | `sandbox-worker/Dockerfile` | temurin:25-jre | 8091 | sandbox (10003) |
| egress-proxy | — | ubuntu/squid:6.6 | 3128 | — |

### K8s Resources

**Base** (20 个资源):
- Namespace, ConfigMap, Secret, PVC
- Deployment × 4 (api, render-worker, sandbox-worker, egress-proxy)
- Service × 4
- Ingress, HPA × 2, NetworkPolicy × 4

**Staging Overlay**: namespace=media-platform-staging, replicas=1, 资源减半, DEBUG 日志

**Production Overlay**: namespace=media-platform, api replicas=2, INFO 日志

### GitOps

- `gitops/staging/` → ArgoCD auto-sync
- `gitops/production/` → ArgoCD manual sync
- CI 自动创建 GitOps PR
- Production promotion 需要手动 workflow_dispatch + 严格验证

### ArgoCD

- Staging: `syncPolicy.automated` (prune + selfHeal)
- Production: 手动同步（无自动化）
- repoURL 需替换为实际 Git 仓库

### CI Image Build

- Tag: `git-<12-char-sha>`
- 推送到 GHCR: `ghcr.io/<owner>/platform-{api,render-worker,sandbox-worker}`
- 不可变 tag 规则（无 :latest/:dev）

### Immutable Tag

- CI 生成 `git-<shortSHA>`
- 三镜像使用相同 tag
- 就绪验证脚本强制执行

### Production Promotion

1. Staging 验证通过
2. 手动触发 workflow_dispatch (environment=production)
3. 严格验证（readiness + egress smoke strict）
4. 创建 PR + 二次审批
5. ArgoCD 手动同步

### Rollback

- ArgoCD: revert PR
- kubectl: `rollout undo deployment/api`
- 紧急: `kubectl set image` 指定旧 tag

---

## 11. 运维 Runbook

### Local Startup

```bash
cd platform
docker compose -f docker-compose.dev.yml up -d
./gradlew :platform-app:bootRun
cd frontend && npm run dev
```

### Staging Rollout

```bash
# 渲染 manifests
REGISTRY=ghcr.io/yourorg IMAGE_TAG=git-abc123 \
  ./scripts/update-gitops-manifests.sh staging
# 验证
./scripts/validate-production-readiness.sh gitops/staging
./scripts/verify-egress-smoke-config.sh gitops/staging
# 创建 PR → 合并 → ArgoCD auto-sync
```

### Production Promotion

```bash
# GitHub Actions → workflow_dispatch → environment=production, imageTag=git-xyz
# 自动运行严格验证 → 创建 PR → 审批 → 合并 → ArgoCD manual sync
```

### Readiness Validation

```bash
./scripts/validate-production-readiness.sh gitops/production
# 检查: image safety, security, resources, probes, network, egress
```

### Egress Smoke Rollout

参见 `platform/docs/egress-smoke-rollout.md`

1. 选择 smoke URL
2. 更新 allowed-domains.txt
3. 启用 smoke 测试
4. 部署到 staging
5. 验证 smoke health
6. 验证 S3/OIDC/webhook/provider
7. 推广到 production

### Audit Export

- 前端 `AuditLogPage.vue` → CSV 导出
- API: `GET /api/v1/admin/audit/export`

### Alert Webhook

- 配置 `AUDIT_ALERTS_PUBLISHER_TYPE=webhook`
- 配置 `AUDIT_ALERTS_WEBHOOK_URL`
- 确保域名在 Squid allowed-domains 中

### Sandbox Worker

- 独立部署：`sandbox-worker` deployment
- 端口 8091
- 无出口网络
- 仅 platform-api 可访问

### Troubleshooting

| 症状 | 可能原因 | 排查 |
|------|----------|------|
| Pod CrashLoopBackOff | 配置错误 / 依赖不可达 | `kubectl logs`, `kubectl describe` |
| 渲染作业卡住 | 陈旧补偿器未触发 | 检查 `render.stale-compensator` 配置 |
| 外部请求失败 | 出口代理/JVM 代理配置 | 检查 smoke test, Squid 日志 |
| OIDC 登录失败 | Issuer URI / 客户端配置 | 检查 `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI` |
| 沙箱执行失败 | Worker 不可用 | 检查 sandbox-worker pod 状态 |

---

## 12. 开发注意事项

### 添加新 API

1. Controller 放在 `platform-app` 模块
2. 路径前缀 `/api/v1/*`
3. 返回 `ProblemDetail` 错误格式
4. 添加审计记录
5. 添加 OpenAPI 注解
6. 前端 API client 在 `frontend/src/api/` 中添加

### 添加新 Admin Endpoint

1. 路径前缀 `/api/v1/admin/*`
2. 前端 admin API client 在 `frontend/src/api/admin/` 添加
3. 添加管理页面 + 路由配置

### 添加审计

1. 使用 `AuditCategory` 枚举
2. 记录 actor/action/resource/result
3. 敏感字段自动脱敏

### 添加外部 HTTP Call

1. 域名加入 Squid allowed-domains
2. NetworkPolicy 允许到 egress-proxy:3128
3. 考虑 JVM 代理属性
4. Webhook URL 需 SSRF 验证

### 添加配置

1. `@ConfigurationProperties`
2. `application.yml` 默认值
3. K8s ConfigMap 条目

### 添加 Migration

1. `platform-app/src/main/resources/db/migration/V{n}__description.sql`
2. 向后兼容变更
3. staging 先验证

### 添加前端页面

1. 组件放在 `frontend/src/pages/`
2. 路由在 `router/index.ts` 添加
3. API client 在 `src/api/` 添加
4. 添加测试

### 添加 K8s Env

1. `k8s/base/configmap.yaml`
2. overlay 按需覆盖
3. `application.yml` 默认值

### 添加测试

1. 后端：`src/test/` 下 `*Test.java`
2. 前端：`src/**/*.spec.ts`
3. CI 自动运行

### 禁止事项

1. ❌ 生产开启 `sandbox.execution-mode=in-process`
2. ❌ 生产开启 `dev-auth-endpoint=true`
3. ❌ 使用 `:latest` 或 `:dev` 镜像 tag
4. ❌ Git 中提交真实 secret
5. ❌ 应用 NetworkPolicy 允许 `0.0.0.0/0`
6. ❌ 任何 NetworkPolicy 允许 `169.254.169.254`
7. ❌ sandbox-worker 访问出口网络
8. ❌ egress-proxy 接受 sandbox-worker 入口
9. ❌ 审计日志记录代码内容
10. ❌ LLM 输出 raw shell 命令

---

## 13. 验收清单

### Local

- [ ] `docker compose -f docker-compose.dev.yml up -d` 正常启动
- [ ] `./gradlew :platform-app:bootRun` 正常启动
- [ ] `npm run dev` 前端正常访问
- [ ] H2 控制台可访问

### CI

- [ ] `./gradlew test` 全部通过
- [ ] `./gradlew :platform-app:bootJar` 构建成功
- [ ] `docker build` 成功
- [ ] `npm run lint` 无错误
- [ ] `npx vitest run` 全部通过
- [ ] `npm run build` 成功

### Staging

- [ ] K8s 资源渲染正确
- [ ] 就绪验证通过
- [ ] 出口代理 smoke 通过
- [ ] OIDC 登录正常
- [ ] 渲染作业端到端正常
- [ ] 审计记录正常

### Production

- [ ] 镜像 tag 为不可变 SHA
- [ ] 无 :latest/:dev tag
- [ ] 就绪验证严格模式通过
- [ ] allowed-domains.txt 为真实域名
- [ ] staging smoke 通过
- [ ] 二次审批完成

### Security

- [ ] 所有容器 runAsNonRoot
- [ ] 所有容器 readOnlyRootFilesystem
- [ ] 所有容器 allowPrivilegeEscalation=false
- [ ] 所有容器 capabilities.drop=ALL
- [ ] Ingress TLS 配置
- [ ] CORS 配置正确
- [ ] 4 个 NetworkPolicy 全部应用

### Egress

- [ ] egress-proxy 部署正常
- [ ] Squid 允许域名配置正确
- [ ] HTTP_PROXY/HTTPS_PROXY/NO_PROXY 正确
- [ ] sandbox-worker 无代理环境变量
- [ ] Smoke test 通过

### Render

- [ ] 渲染作业 CRUD 正常
- [ ] 状态流转正确
- [ ] 远程 Worker 注册/心跳正常
- [ ] 产物预览正常

### Audit

- [ ] 审计记录分类正确
- [ ] CHECK 约束强制执行
- [ ] 拒绝 burst 检测正常
- [ ] 敏感字段脱敏正常

### Frontend

- [ ] 所有页面正常加载
- [ ] 管理员功能正常
- [ ] 审计日志查看器正常
- [ ] CSV 导出正常

---

## 14. 差异与风险

### 文档与代码冲突汇总

| 项目 | 文档值 | 代码值 | 严重度 |
|------|--------|--------|--------|
| 模块数量 | 30 | 34 | 🟡 中 |
| Flyway 迁移数 | 17 | 3 (V1-V3) | 🔴 高 |
| 数据库表数 | 28+ | 70+ | 🔴 高 |
| 后端测试文件 | 54+ | 366 | 🔴 高 |
| 渲染提供商 | 6 | 13+ | 🟡 中 |
| 生产 namespace | media-platform | media-platform-production | 🟡 中 |

### 文档有但代码未实现

| 项目 | 风险 |
|------|------|
| Novu 集成 | 🔴 高 — 文档描述的能力不存在 |
| SMTP/SendGrid/SES 邮件 | 🔴 高 — 邮件发送不存在 |
| Twilio SMS | 🔴 高 — SMS 不存在 |
| KillBill/Medusa/FederatedQueryGateway | 🟡 中 — 文档引用不存在的类名 |
| PopcornFX | 🔴 高 — 渲染管道文档描述不存在的能力 |

### 代码有但文档缺失

| 项目 | 风险 |
|------|------|
| MCP 媒体工具完整协议 | 🟡 中 |
| 客户端导出 | 🟡 中 |
| LiteLLM 租户虚拟密钥 | 🟡 中 |
| 渲染缓存详细配置 | 🟢 低 |
| AAF 转换器 | 🟢 低 |
| 大量配置属性未文档化 | 🟡 中 |

### 生产配置 Placeholder

| 项目 | 说明 |
|------|------|
| allowed-domains.txt | 包含 example.com placeholder |
| Ingress host | 使用 example.com |
| ArgoCD repoURL | 需替换 |
| Stripe URL | example.com |
| 镜像仓库 | 需替换 |

### 需要环境验证

| 项目 | 说明 |
|------|------|
| S3 存储切换 | 配置存在但默认关闭 |
| OIDC 认证 | 依赖外部 Authentik |
| Temporal 工作流 | Profile 存在但默认关闭 |
| LiteLLM | Profile 存在但未端到端验证 |
| 出口代理 | 默认关闭，需 staging 验证 |
| GraphQL | 默认关闭 |
| NLQ | 依赖真实 AI 模型 |

---

## 15. 未来方向

详见独立文档：[未来方向：智能渲染与自然语言编辑](future-roadmap-otio-llm.md)

### 简要概述

#### 产品愿景

用户上传素材 + 输入提示词 → 系统调用 LLM 和工具 → 生成多个预览 → 用户用自然语言持续修改 → 最终导出成片。

#### 推荐架构

```
Prompt → Intent Parser → Edit Plan → OTIO Timeline → Render Plan → Preview Render
    ↑                                                                      ↓
    ←←←←←←←←←← User Feedback → LLM Patch → Timeline Patch ←←←←←←←←←←←←←←←←←
```

#### 技术选型建议

| 维度 | Phase 1 | Phase 2 | Phase 3 |
|------|---------|---------|---------|
| 时间线格式 | Custom Render Plan | OTIO import/export | OTIO 内部交换 |
| 渲染器 | FFmpeg | MLT / 内部图 | 复杂 VFX |
| Agent 层 | 后端确定性工具 | OTIO 实用 CLI | MCP Server |
| LLM 输出 | Schema-validated JSON | OTIO + Patch | 完整 OTIO |

#### 安全要点

- Schema validation
- Operation allowlist
- Asset permission check
- Tenant isolation
- 无 arbitrary shell
- 无 arbitrary external URL
- Audit logging

#### 建议任务拆分

- P4-LLM-1：定义 render-plan.json schema
- P4-LLM-2：render-worker 支持 render-plan 本地预览
- P4-LLM-3：素材分析 pipeline
- P4-LLM-4：LLM prompt → render-plan
- P4-LLM-5：多 variant 预览
- P4-LLM-6：自然语言 patch
- P4-LLM-7：timeline versioning / diff / rollback
- P4-OTIO-1：OTIO validator / inspector
- P4-OTIO-2：OTIO → render-plan
- P4-OTIO-3：render-plan → OTIO export
- P4-OTIO-4：OTIO ChatGPT Skill
- P4-OTIO-5：OTIO MCP Server
