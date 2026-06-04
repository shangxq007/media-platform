# 代码反推版：当前系统真实实现说明

> **生成方式**：仅基于代码、配置、脚本、测试、K8s manifests、CI workflow 反向推导，不依赖已有 docs 目录内容。
> **生成日期**：2026-05-28
> **项目版本**：0.2.0-SNAPSHOT

---

## 1. 项目概览

### 项目定位

AI 视频生产与渲染编排平台。提供从素材上传、时间线编辑、多提供商渲染、交付分发的完整视频处理链路，同时集成 Prompt 工程、成本管控、权限治理、审计合规等企业级能力。

### 当前系统解决的问题

- 多后端渲染提供商（FFmpeg/JavaCV/GStreamer/MLT/Natron/Remotion/Blender/Vapoursynth/Shaka/Skia/Bento4/GPAC）的统一编排
- 时间线版本控制、增量渲染、多 variant 预览
- 基于 Entitlement/Quota/Feature Flag 的多租户访问控制
- 审计日志全链路追踪与安全告警
- 插件化扩展平台（PF4J）+ 沙箱代码执行
- 订阅/信用钱包/发票/支付（Stripe/Hyperswitch）商业化链路

### 主要用户角色

| 角色 | 描述 | 代码证据 |
|------|------|----------|
| 普通用户 | 上传素材、编辑时间线、提交渲染、查看导出 | `MeController`, `/me/*` 路由 |
| 工作区成员 | 共享资源、角色权限管理 | `WorkspaceMember`, `/workspace/*` 路由 |
| 管理员 | 租户管理、渲染监控、配置管理、审计查看 | `admin/*` 路由, `AdminController` 系列 |
| 外部系统 | API Key 认证、远程 Worker 注册 | `RemoteWorkerController`, `ApiKey` 实体 |
| 服务账户 | 自动化任务、Worker 回调 | `ServiceAccount`, `ApiClient` 实体 |

### 主要业务对象

| 业务对象 | 代码证据 | 状态 |
|----------|----------|------|
| Tenant | `tenant` 表, `Tenant` 实体 | ✅ 已实现 |
| Project | `project` 表, `Project` 实体 | ✅ 已实现 |
| User | `user` 表, `User` 实体 | ✅ 已实现 |
| Workspace | `workspace` 表, `WorkspaceMember` | ✅ 已实现 |
| RenderJob | `render_job` 表, `RenderController` | ✅ 已实现 |
| Timeline | `timeline_snapshot`, `timeline_revision` 表 | ✅ 已实现 |
| Asset/Media | `storage_object`, `media_asset_metadata` 表 | ✅ 已实现 |
| Artifact | `artifact`, `artifact_relation` 表 | ✅ 已实现 |
| DeliveryJob | `delivery_job`, `delivery_destination` 表 | ✅ 已实现 |
| PromptTemplate | `prompt_template`, `prompt_template_version` 表 | ✅ 已实现 |
| EffectPack | `effect_pack`, `effect_pack_effect` 表 | ✅ 已实现 |
| FeatureFlag | `feature_flag_definition`, frontend Route | ✅ 已实现 |
| Entitlement | `entitlement_grant`, `entitlement_override` 表 | ✅ 已实现 |
| Billing/Invoice | `billing_invoice`, `billing_ledger_entry`, `credit_wallet` 表 | ✅ 域模型完整 |
| Subscription | `subscription_contract`, `subscription_plan` 表 | ✅ 已实现 |
| AuditRecord | `audit_records` 表, `AuditCategory` enum | ✅ 已实现 |
| Notification | `notification_event_definition`, `notification_delivery_log` 表 | ✅ 已实现 |
| Extension | `extension_definition`, `extension_invocation` 表 | ✅ 已实现 |
| SandboxJob | `sandbox_execution_job` 表 | ✅ 已实现 |
| NLQ Report | `nlq_report_definition`, `nlq_query_history` 表 | ✅ 已实现 |
| Commerce | `commerce_product`, `checkout_session`, `commerce_cart` 表 | ✅ 已实现 |
| SocialPost | `social_connected_platform`, `social_post` 表 | ✅ 已实现 |

### 当前实现状态总览

| 维度 | 状态 | 说明 |
|------|------|------|
| 后端模块 | 34 个 Gradle 模块 | `settings.gradle.kts` 中 include 34 个 |
| Spring Boot 应用 | 3 个独立进程 | platform-app, sandbox-worker, remote-render-worker |
| REST API | 25+ 个 Controller | 涵盖所有业务域 |
| 前端页面 | 44+ 管理员页面, 20+ 用户页面 | Vue 3 + Vue Router |
| 数据库表 | 70+ 张表 | V1 迁移脚本包含 10 个域 |
| Flyway 迁移 | 3 个逻辑版本 (V1-V3) | V1=初始 schema, V2=审计分类回填, V3=约束强制 |
| 后端测试 | 366 个测试类 | JUnit 5 + Spring Modulith Test |
| 前端测试 | 88 个 spec 文件 | Vitest + happy-dom |
| K8s 资源 | 20+ 种资源类型 | base + overlays + gitops |
| Docker 镜像 | 8 个 Dockerfile | 主应用、前端、render-worker、sandbox-worker、Natron POC 等 |

### 明确推断出的能力

1. **多提供商渲染管道** — DAG 管道 + 13 个渲染提供商（javacv/ofx/ffmpeg/gstreamer/gpac/mlt/natron/bento4/shotstack/remotion/blender/vapoursynth/shaka/skia）
2. **内部时间线 Schema 1.0** — 平台原生时间线格式，支持轨道/剪辑/效果/标记
3. **时间线版本控制** — 快照、修订历史、diff 比较、patch 应用、回滚
4. **增量渲染** — 基于变更范围的局部重渲染，支持缓存
5. **OTIO 导入/导出** — MCP 工具控制器提供 OTIO/EDL/FCPXML/AAF 格式互转
6. **MCP 媒体工具** — `/api/v1/mcp/media/tools` 和 `/api/v1/mcp/render/jobs`
7. **双路径认证** — JWT (OIDC/Authentik) + API Key
8. **审计告警** — 拒绝 burst 检测、Webhook/SLF4J/Noop 适配器
9. **沙箱执行** — disabled/external/in-process 三种模式
10. **GitOps 部署** — ArgoCD + Kustomize overlays + CI 自动 PR
11. **出口代理** — Squid 代理 + NetworkPolicy 零信任网络
12. **前端视频编辑器** — 时间线编辑、效果面板、字幕系统、导出面板
13. **NLQ 分析助手** — 自然语言查询转 SQL
14. **社交发布** — 多平台连接、帖子调度、分析
15. **客户端导出** — 浏览器端合成能力

### 明确未在代码中找到的能力

1. **OTIO 运行时库依赖** — `build.gradle.kts` 中未发现 `opentimelineio` 依赖；OTIO 支持通过 MCP 控制器和前端工具函数实现，但非原生 Java 库集成
2. **LLM 多预览生成** — 前端有 `AiProposalsPanel`、`AiTimelineEditPanel` 组件，API 有 `AiTimelineAPI`，但后端仅 `StubChatProvider`，无真实 LLM 多 variant 生成逻辑
3. **自然语言连续修改** — 前端有 `TimelinePatchStepsDialog`、`TimelineHighlightNavigator` 组件，但 LLM 驱动的 patch 生成未实现
4. **KillBill 集成** — 文档提到 `NoopKillBillBillingEngine`，但代码中未找到该 class（仅有 Stripe/Hyperswitch Noop）
5. **Novu 集成** — `docs/deployment/external-resources.md` 提到 Novu，但代码中未找到 `NovuNotificationProvider`
6. **Medusa 集成** — 文档提到 `NoopMedusaCatalogAdapter`，代码中未找到
7. **Temporal 实际部署** — 配置和 profile 存在，但默认关闭（`app.temporal.enabled=false`）
8. **Vault 集成** — 配置存在但默认关闭（`app.secrets.vault.enabled=false`）
9. **GPU 渲染** — Dockerfile 和配置提及 GPU 相关工具，但无实际 GPU 调度逻辑
10. **PopcornFX** — 文档提及，代码中未找到集成

---

## 2. 架构总览

### 后端模块划分

```
platform/
├── platform-app/            # Spring Boot 主应用（聚合所有模块）
├── shared-kernel/           # 共享内核（事件、错误码、基础类型）
├── identity-access-module/  # 身份认证（租户/用户/API Key/Workspace）
├── entitlement-module/      # 权益管理（5 层策略/授权/覆盖）
├── policy-governance-module/# 策略治理（Feature Flag/ABAC）
├── billing-module/          # 计费（计量/预算/对账）
├── quota-billing-module/    # 配额计费（配额桶/阈值事件）
├── payment-module/          # 支付（Stripe/Hyperswitch Noop）
├── commerce-module/         # 商务（产品/购物车/结账）
├── render-module/           # 渲染编排（管道/提供商/配额）
├── workflow-module/         # 工作流（Temporal+LiteFlow）
├── ai-module/               # AI（ChatProvider SPI, StubChatProvider）
├── prompt-module/           # Prompt 工程（模板/版本/安全）
├── storage-module/          # 存储抽象（S3/MinIO/本地）
├── delivery-module/         # 交付（SFTP/SMB/策略）
├── artifact-catalog-module/ # 目录（产物元数据）
├── remote-render-worker/    # 远程渲染 Worker
├── sandbox-runtime-module/  # 沙箱运行时
├── sandbox-worker/          # 沙箱执行 Worker（Python）
├── extension-module/        # 插件平台（PF4J）
├── federation-query-module/ # GraphQL 聚合 + NLQ
├── notification-module/     # 通知（多渠道/模板）
├── audit-compliance-module/ # 审计合规
├── observability-module/    # 可观测性（健康检查/熔断/SLA）
├── config-module/           # 配置管理（版本化 KV）
├── secrets-config-module/   # 密钥引用管理
├── datasource-module/       # 多数据源 + jOOQ DSLContext
├── scheduler-module/        # 调度器（Cron/手动/死信）
├── outbox-event-module/     # 事务 Outbox（重试/分发）
├── cloud-resource-module/   # 云资源目录
├── user-analytics-module/   # 用户分析（行为/画像/分群）
├── social-publish-module/   # 社交发布
├── compatibility-migration-module/ # 兼容迁移（9 个 schema 族）
├── frontend/                # Vue 3 前端（构建到 platform-app static）
└── platform-app/src/main/resources/db/migration/ # Flyway 迁移
```

### 前端结构

```
frontend/src/
├── router/           # 路由定义 + 导航守卫（OIDC 认证 + 权限检查）
├── pages/            # 页面组件（Editor/Admin/User/Workspace/Entitlement/Analytics）
├── components/       # 可复用组件（Editor/Timeline/Export/Effects/Admin/UI）
├── api/              # API 客户端（Axios 实例 + 各域 client）
├── api/admin/        # 管理员 API 客户端
├── stores/           # Pinia 状态管理（auth/project/timeline/history/effectPack/subtitle）
├── composables/      # 组合式函数（navigation/featureFlag/timelineSync/renderJob 等）
├── auth/             # OIDC 认证客户端
├── types/            # TypeScript 类型定义
├── utils/            # 工具函数（OTIO/timelineImport/subtitleParser 等）
└── clientExport/     # 客户端导出（浏览器端合成）
```

### Worker / Render / Sandbox 组件

```
                    ┌──────────────────┐
                    │   platform-api    │
                    │   (port 8080)     │
                    └────────┬─────────┘
                             │
              ┌──────────────┼──────────────┐
              │              │              │
    ┌─────────▼───────┐  ┌──▼───────────┐  ┌──▼──────────────┐
    │ remote-render-  │  │ sandbox-     │  │   egress-proxy  │
    │ worker          │  │ worker       │  │   (Squid 3128)  │
    │ (port 8090)     │  │ (port 8091)  │  │                  │
    └─────────┬───────┘  └──────────────┘  └──────────────────┘
              │
    ┌─────────▼───────────────────────────────────────┐
    │  Render Providers:                               │
    │  FFmpeg, JavaCV, GStreamer, MLT, Bento4, GPAC,  │
    │  Natron, Remotion, Blender, Vapoursynth, Shaka,  │
    │  Skia, OFX, Shotstack                           │
    └─────────────────────────────────────────────────┘
```

### 组件关系图

```
flowchart TD
    User["用户/浏览器"] --> Frontend["Vue 3 前端 (port 3000)"]
    Frontend -->|"REST /api/v1/*"| API["platform-api (port 8080)"]
    Frontend -->|"OIDC"| Authentik["Authentik/OIDC Provider"]
    API -->|"JWT 验证"| Authentik
    API -->|"读写"| Postgres["PostgreSQL"]
    API -->|"渲染任务"| RenderWorker["remote-render-worker (port 8090)"]
    API -->|"代码执行"| SandboxWorker["sandbox-worker (port 8091)"]
    API -->|"外部请求"| EgressProxy["egress-proxy (Squid, port 3128)"]
    RenderWorker -->|"读取/写入"| Storage["S3/MinIO/本地存储"]
    RenderWorker -->|"状态回调"| API
    API -->|"审计记录"| AuditDB["audit_records 表"]
    API -->|"Outbox 事件"| OutboxDB["outbox_events 表"]
    OutboxDB -->|"分发"| NotificationModule["notification-module"]
    OutboxDB -->|"分发"| BillingModule["billing-module"]
    Frontend -->|"GraphQL"| GraphQL["federation-query-module (默认关闭)"]
    API -->|"监控"| Prometheus["Prometheus / OTLP"]
    API -->|"错误追踪"| Sentry["Sentry (默认关闭)"]
    API -->|"会话回放"| OpenReplay["OpenReplay (默认关闭)"]
```

---

## 3. 技术栈

| 层 | 技术 | 用途 | 代码证据 | 风险 / 备注 |
|---|---|---|---|---|
| 语言 | Java 25 | 后端运行时 | `build.gradle.kts:22` — `JavaLanguageVersion.of(25)` | 非常新的 JDK 版本 |
| 框架 | Spring Boot 4.0.4 | 核心框架 | `build.gradle.kts:7` — `org.springframework.boot:4.0.4` | Boot 4 为较新版本 |
| 模块化 | Spring Modulith 2.0.4 | 模块边界 enforcement | `build.gradle.kts:36` + `package-info.java` | — |
| AI | Spring AI 2.0.0-M3 | AI 客户端抽象 | `build.gradle.kts:30` — Milestone 版本 | **Milestone 版本，非 GA** |
| 工作流 | Temporal SDK | 持久化工作流 | `application-temporal.yml` | 默认关闭 |
| 规则引擎 | LiteFlow 2.15.3.2 | 本地规则链 | `render-module/build.gradle.kts` | — |
| ORM | jOOQ 3.19.18 | 类型安全 SQL | `build.gradle.kts:8` | — |
| DB 迁移 | Flyway | Schema 迁移 | `application.yml:28-29` (dev 关闭, prod 启用) | — |
| Dev DB | H2 | 开发/测试内存库 | `application.yml:22` | — |
| Prod DB | PostgreSQL 16 | 生产数据库 | `application-prod.yml:17-19` | — |
| API 文档 | springdoc OpenAPI 3 | Swagger UI | `application.yml:30-40` | — |
| 插件系统 | PF4J | 运行时插件加载 | `extension-module/build.gradle.kts` | — |
| 前端框架 | Vue 3.5.13 | UI 框架 | `frontend/package.json` | — |
| 前端构建 | Vite 6 | 构建工具 | `frontend/vite.config.ts` | — |
| 前端语言 | TypeScript 5.7 | 类型安全 | `frontend/tsconfig.json` | — |
| 前端路由 | Vue Router 4.5 | SPA 路由 | `frontend/src/router/index.ts` | — |
| 前端状态 | Pinia 2.3 | 状态管理 | `frontend/src/stores/*.ts` | — |
| 前端 HTTP | Axios 1.7 | REST 客户端 | `frontend/src/api/index.ts` | — |
| OIDC | oidc-client-ts 3.1 | 认证 | `frontend/src/auth/oidcClient.ts` | — |
| 前端测试 | Vitest 3 + happy-dom | 单元/组件测试 | `frontend/package.json` | — |
| 后端测试 | JUnit 5 + Testcontainers | 集成测试 | `build.gradle.kts:39` | — |
| 容器 | Docker + BuildKit | 镜像构建 | `Dockerfile` (多阶段) | — |
| 编排 | Kubernetes | 容器编排 | `k8s/` | — |
| GitOps | ArgoCD | CD | `gitops/argocd/` | — |
| K8s 定制 | Kustomize | 环境差异化 | `k8s/base/` + `k8s/overlays/` | — |
| CI/CD | GitHub Actions | CI | `platform/.github/workflows/ci.yml` | — |
| 容器仓库 | GHCR | 镜像存储 | `ci.yml:29` | — |
| 监控 | Prometheus + OTLP | 指标采集 | `application.yml:42-77` | — |
| 错误追踪 | Sentry 10.53 | 前端+后端错误追踪 | `sentry.*` 配置, `@sentry/vue` | 默认关闭 |
| 会话回放 | OpenReplay | 用户行为回放 | `openreplay.*` 配置 | 默认关闭 |
| 出口代理 | Squid 6.6 | 出口流量控制 | `k8s/base/deployment-egress-proxy.yaml` | — |
| 网络策略 | NetworkPolicy | 零信任网络隔离 | `k8s/base/networkpolicy-*.yaml` | 需要 CNI 支持 |
| 渲染工具 | FFmpeg | 视频处理 | `application.yml:157` — `MEDIA_FFMPEG_PATH` | — |
| 渲染工具 | JavaCV | Java 视觉处理 | `render-module/build.gradle.kts` | — |
| 渲染工具 | GStreamer | 多媒体管道 | `application.yml:160` | — |
| 渲染工具 | MLT | 非线性编辑 | `application.yml` | — |
| 渲染工具 | Blender | 3D 渲染 | `application.yml:321` | — |
| OTIO | 前端工具函数 | 时间线互转 | `frontend/src/utils/otio.ts` | **无 Java OTIO 库依赖** |
| GraphQL | GraphQL (默认关闭) | 查询聚合 | `application.yml:15-16` — `enabled: false` | 默认关闭 |

---

## 4. 后端功能说明

### 4.1 Identity / User / Tenant / Workspace

**模块**: `identity-access-module`

**已实现功能**:
- 租户 CRUD（`tenant` 表）
- 用户管理（`user` 表, OIDC JIT 预配）
- 项目 CRUD（`project` 表）
- 工作区 + 成员 + 组（`workspace`, `workspace_member`, `workspace_group`）
- 角色 + 权限（`role`, `permission`, `role_permission`, `user_role_assignment`, `group_role_assignment`）
- API Key 认证（`api_key` 表, 默认关闭 `app.identity.api-key-auth-enabled=false`）
- 服务账户 + API 客户端（`service_account`, `api_client`）

**Endpoint**: `MeController` (`/api/v1/me`), `DevAuthController` (`/api/v1/dev/auth`, 默认关闭)

**安全控制**: 
- OIDC Resource Server（`application-oidc.yml`）
- 双解码器：Nimbus OIDC + 可选 Legacy HMAC JWT
- JIT 用户预配（`jit-provisioning-enabled: true`）
- API Key 过滤器（`/api/v1/*`）

**测试**: 测试类存在，覆盖身份认证流程

**未完成**: API Key 认证默认关闭；外部 IdP 需配置

### 4.2 Entitlement

**模块**: `entitlement-module`

**已实现功能**:
- 5 层策略体系（FREE/PRO/TEAM/ENTERPRISE/EXPERIMENTAL）
- Feature 定义 + Bundle（`feature_definition`, `feature_bundle`）
- 授权 Grant（`entitlement_grant`）
- 覆盖 Override（`entitlement_override`）
- Bundle 管理（`entitlement_bundle`）
- 工作区权益池（`workspace_entitlement_pool`）
- 工作区成员授权（`workspace_member_entitlement_grant`）
- 工作区配额分配（`workspace_quota_allocation`）
- 租户权益层级（`tenant_entitlement_tier`）

**前端**: 完整的用户端能力查看（`MyCapabilitiesPage`）+ 管理端 Entitlement 管理（`EntitlementBundleList`, `EntitlementManagementPage` 等）

### 4.3 Billing

**模块**: `billing-module`, `quota-billing-module`, `payment-module`, `commerce-module`

**已实现功能**:
- 计量（`usage_meter`, `usage_record`, `rated_usage_record`）
- 预算（`billing_module` domain）
- 信用钱包（`credit_wallet`, `credit_transaction`）
- 发票（`billing_invoice`, `invoice_line_item`）
- 订阅合同（`subscription_contract`, `subscription_plan`）
- 定价规则（`pricing_rule`, `custom_pricing_rule`, `discount_policy`）
- 商务产品/价格（`commerce_product`, `commerce_price`, `provider_product_mapping`）
- 购物车（`commerce_cart`, `commerce_cart_line`）
- 结账会话（`checkout_session`）
- 采购订单（`purchase_order`）
- 支付尝试（`payment_attempt`）
- 配额定义 + 使用（`quota_definitions`, `quota_usage`）
- 配额配置文件（`quota_profile`）

**默认关闭/Noop**:
- Stripe: `STRIPE_ENABLED=false`
- Hyperswitch: `HYPERSWITCH_ENABLED=false`
- 支付 Webhook 签名验证: `PAYMENT_WEBHOOK_ALLOW_UNSIGNED=false`

**前端**: 完整计费中心（`MyBillingPage`, `MyCreditsPage`, `BillingHistoryPage`）+ 管理端（`BillingPlanManagementPage`, `PricingRuleEditor`, `InvoicePreviewPage` 等）

### 4.4 Audit

**模块**: `audit-compliance-module`

**已实现功能**:
- 审计记录（`audit_records` 表）
- 18 种审计分类（`AuditCategory` enum）：ADMIN_AUDIT, RENDER, DATA_GOVERNANCE, FEATURE_FLAG, ENTITLEMENT, GRAPHQL_OPERATION, NLQ, PROVIDER_HEALTH, IDENTITY, API_REQUEST, EXTENSION, EXTENSION_ROUTING, EXTENSION_RESOURCE, PERMISSION, CONFIG, PROMPT, POLICY, SANDBOX, UNKNOWN
- V2 迁移：基于 action 前缀和 resource_type 的分类回填
- V3 迁移：CHECK 约束 + NOT NULL 约束
- 审计告警（`AuditAlerts`）：拒绝 burst 检测
- 安全告警发布器（`SecurityAlertPort`）：Slf4j / Noop / Webhook 适配器
- Webhook SSRF 防护（私有 IP/元数据 IP/回环地址 阻断）
- 敏感字段脱敏（authorization/cookie/token/apiKey/secret/password 等）

**Endpoint**: `AuditCompliance` 前端页面 + `AuditLogPage`（筛选/分页/CSV 导出/详情查看）

### 4.5 Admin Audit

**前端**: `pages/admin/AuditLogPage.vue` — 审计日志查看器，支持按分类/操作/演员/结果/租户/日期范围筛选，分页，详情查看（敏感数据脱敏），CSV 导出

### 4.6 Alert

**已实现功能**:
- 拒绝 burst 检测（`denied-burst`）：阈值 5 次/600 秒，冷却 1800 秒，最大 10000 个演员
- 安全告警 JSON Schema（rule/severity/category/action/actor/resource/result/attributes）
- 发布器类型：`slf4j`（默认）/ `noop` / `webhook`
- Webhook SSRF 防护（`allow-private-network=false`，`allowed-hosts`，`allowed-domain-suffixes`）
- 告警属性中敏感字段自动脱敏

**配置**: `app.audit.alerts.*`

### 4.7 Sandbox Runtime

**模块**: `sandbox-runtime-module`, `sandbox-worker`

**已实现功能**:
- 三种执行模式：`disabled` / `external` / `in-process`
- 外部 Worker HTTP 适配器（`SandboxWorkerPort`）
- 默认语言：Python（dev profile）
- 执行限制：最长 5 秒，最大输出 1MB，最大代码 64KB
- 审计日志（仅记录 codeHash，不记录代码内容）
- K8s NetworkPolicy：sandbox-worker 拒绝所有出口流量

**默认行为**:
- 默认 profile：`enabled=false`, `execution-mode=disabled`
- Dev profile：`enabled=true`, `execution-mode=external`, `allowed-languages=python`
- Prod profile：`enabled=false`, `execution-mode=disabled`

**沙箱 Worker 独立进程**: `SandboxWorkerApplication`（Spring Boot, port 8091）

**安全**: 生产环境禁止 `in-process` 模式；sandbox-worker 无出口网络；不记录代码内容

### 4.8 Storage

**模块**: `storage-module`

**已实现功能**:
- 存储对象元数据（`storage_object` 表）
- S3 兼容存储（AWS S3 / Cloudflare R2 / MinIO）
- 存储配置：endpoint, region, access-key, secret-key, path-style-access
- R2 兼容模式（禁用 chunked encoding）
- 存储孤立扫描（`orphan-scan`）：artifacts, render-cache buckets
- 存储孤立清除（`orphan-purge`）：默认关闭，需 approval-token

**默认状态**: `STORAGE_S3_ENABLED=false`（默认使用本地存储）

**配置**: `storage.s3.*`

### 4.9 Render / Delivery

**模块**: `render-module`, `remote-render-worker`, `delivery-module`, `artifact-catalog-module`

**已实现功能**:
- 渲染作业 CRUD（`render_job`, `render_job_status_history`）
- DAG 管道（`render.pipeline.dag.enabled=true`）
- 13 个渲染提供商（javacv/ofx/ffmpeg/gstreamer/gpac/mlt/natron/bento4/shotstack/remotion/blender/vapoursynth/shaka/skia/libass）
- 本地执行模式（`render.execution.mode=local`）
- Temporal 执行模式（需 `application-temporal.yml`）
- 陈旧补偿器（`stale-compensator`）：30 分钟阈值，5 分钟间隔
- 远程 Worker 注册/心跳/作业回调（`RemoteWorkerController`）
- 渲染预设 + 配置文件（`RenderPresetController`）
- 效果包（`effect_pack`, `effect_pack_effect`）+ 前端 CRUD
- 字幕烧录（`subtitle.libass.enabled=true`）
- 时间线多轨道（`timeline.mlt-multitrack-min-tracks: 2`）
- AAF 转换器（`aaf.converter-enabled: true`）
- 渲染缓存（远程/上传/内容哈希/清理，默认全部关闭）

**交付**:
- 交付目的地（`delivery_destination`）：SFTP/SMB 支持（JSch/SMBJ 依赖）
- 交付策略（`delivery_policy`）
- 交付作业（`delivery_job`）
- Worker 模式（`delivery.worker-enabled: true`）
- Temporal Activity 模式（默认关闭）

**远程渲染 Worker 独立进程**: `RemoteRenderWorkerApplication`（port 8090，H2 文件数据库，安装 ffmpeg）

### 4.10 Report / Federation / GraphQL

**模块**: `federation-query-module`

**已实现功能**:
- GraphQL 聚合查询（默认关闭 `spring.graphql.enabled=false`）
- NLQ（自然语言查询）：`nlq_report_definition`, `nlq_query_history`, `nlq_report_execution`
- NLQ 数据集目录 + 查询审计（前端 `DatasetCatalogPage`, `QueryAuditPage`）
- 分析报告（`MyReportsPage`）

### 4.11 Settings

**模块**: `config-module`, `secrets-config-module`

**已实现功能**:
- 版本化配置 CRUD（`config_item` 表）
- 密钥引用管理（`secret_ref` 表）
- Vault 集成（默认关闭 `VAULT_ENABLED=false`）
- 前端用户设置页面（`MySettingsPage`）：通知偏好（API）、外观（localStorage）、Profile 保存（**禁用**）、账户删除（**禁用**）

### 4.12 Sharing

**已实现功能**:
- 共享资源授权（`shared_resource_grant` 表）
- 协作 API（`CollaborationController` — `/api/v1/me/shared-resources`）
- 前端共享资源页面（`MySharedResourcesPage`, `SharedGrantsAdminPage`）

### 4.13 Provider Health

**模块**: `observability-module`

**已实现功能**:
- 健康检查（`HealthController`：`/healthz`, `/readyz`, `/metrics/summary`）
- 就绪检查包含 DB/存储/Outbox 状态
- 指标摘要（Prometheus 格式）
- OTLP 指标/追踪导出（默认 localhost:4318）
- Prometheus 指标导出（`management.prometheus.metrics.export.enabled: true`）

### 4.14 Feature Flags

**模块**: `policy-governance-module`

**已实现功能**:
- Feature Flag 定义 + 目标规则（`feature_flag_definition`, `feature_flag_targeting_rule`）
- Unleash 集成（默认关闭 `app.features.unleash.enabled=false`）
- 前端路由管理（`frontend_route_definition`）+ 导航策略（`navigation_policy`）
- 前端 Feature Flag 管理 UI（`FeatureFlags`, `FeatureFlagManagementPage`, `FeatureFlagEditor` 等）
- 策略管理（`PolicyManagementPage`, `PolicyRuleEditor`, `PolicySimulationPanel`）

**默认状态**: Unleash 关闭，使用内存 Provider（仅返回默认值）

### 4.15 Prompt Template

**模块**: `prompt-module`

**已实现功能**:
- Prompt 模板 CRUD（`prompt_template`, `prompt_template_version`）
- Prompt 渲染 + 安全验证（`PromptController`, `/api/v1/prompts`）
- Prompt 执行运行（`prompt_execution_run`）
- Prompt 评估结果（`prompt_evaluation_result`）
- 前端 Prompt 管理页面（`PromptManagementPage`）+ MCP 工具（`/api/v1/mcp/prompts`）

### 4.16 AI Module

**模块**: `ai-module`

**已实现功能**:
- ChatProvider SPI（`stubChatProvider` 默认）
- Spring AI 集成（OpenAI / LiteLLM）
- 模型路由（script-generation / nlq-sql-generation / nlq-result-summary / timeline-edit）
- LiteLLM 代理（`application-litellm.yml`）：统一 AI 网关，租户虚拟密钥
- 直接 OpenAI（`application-ai.yml`）：gpt-4o-mini 默认

**默认状态**: `default-provider: stubChatProvider`，OpenAI 关闭

### 4.17 MCP (Model Context Protocol)

**已实现**: `McpMediaToolsController` (`/api/v1/mcp/media/tools`, `/api/v1/mcp/render/jobs`, `/api/v1/mcp/prompts`)
- 媒体探测（probe）
- 验证（validate）
- 导入/导出：OTIO/EDL/FCPXML/AAF/SRT/WebVTT
- 渲染计划（render plan）
- 规范化（canonicalize）
- 差异比较（diff）
- 影响分析（impact analysis）
- 补丁（patch）
- 打包（DASH）

### 4.18 Timeline

**已实现功能**:
- 时间线快照（`timeline_snapshot`）
- 时间线修订（`timeline_revision`）
- 时间线同步（`TimelineEditorSyncController` — Editor v2 <-> Internal Timeline 1.0 双向同步）
- 时间线版本控制器（`TimelineRevisionController` — 历史/diff/patch preview/restore）
- 前端时间线编辑器（`TimelineEditor.vue`）+ 修订历史面板 + 冲突解决对话框

### 4.19 Social Publish

**模块**: `social-publish-module`

**已实现功能**:
- 社交平台连接（`social_connected_platform`）
- 帖子管理（`social_post`）
- 帖子分析（`social_post_analytics`）
- 前端发布页面（`SocialPublishPage`）+ 调度器（`PostSchedulerPage`）+ 历史（`PublishHistoryPage`）

### 4.20 Extension Platform

**模块**: `extension-module`

**已实现功能**:
- Extension 定义（`extension_definition`）
- Extension 调用（`extension_invocation`）
- 路由规则（`extension_routing_rule`）
- 资源限制（`extension_resource_limit`）
- 回滚点（`extension_rollback_point`）
- 审计事件（`extension_audit_event`）
- 前端管理 UI（`ExtensionManagement`, `ExtensionQuotaInfo`）

### 4.21 Notification

**模块**: `notification-module`

**已实现功能**:
- 事件定义（`notification_event_definition`）
- 模板（`notification_template`）
- 投递日志（`notification_delivery_log`）
- 多渠道支持（前端通知中心 + 管理端投递日志查看）
- 通知设置（`NotificationSettingsPage`）：渠道绑定、事件订阅、偏好设置

### 4.22 User Analytics

**模块**: `user-analytics-module`

**已实现功能**:
- 行为事件（`user_behavior_event`）
- 用户画像（`user_profile`）
- 用户分群（`user_segment`）
- 用户习惯（`user_habits`）
- 调度器（profiles-cron, segments-cron）

### 4.23 Client Export

**前端模块**: `clientExport/`

**已实现功能**:
- 客户端合成器（`clientCompositor.ts`）
- 时间线解析器（`timelineParser.ts`）
- 导出路径解析（`resolveExportPath.ts`）
- 能力检测（`clientExportCapabilities.ts`）
- 客户端导出会话（`client_export_session` 表）

---

## 5. 前端功能说明

### 页面列表（路由）

**编辑器**: `/` (EditorPage), `/project/:id` (EditorPage)
**Prompt 管理**: `/prompts`, `/prompts/:templateId`
**特效包**: `/effect-packs`
**用户门户**: `/me`, `/me/projects`, `/me/capabilities`, `/me/usage`, `/me/billing`, `/me/credits`, `/me/feedback`, `/me/settings`, `/me/exports`, `/me/delivery-destinations`, `/me/notifications`, `/me/shared-resources`, `/me/notification-settings`, `/me/publish`, `/me/scheduler`, `/me/publish-history`, `/me/analytics`, `/me/reports`
**工作区**: `/workspace/:workspaceId/members`, `/workspace/:workspaceId/roles`, `/workspace/:workspaceId/entitlements/*`
**系统页**: `/oauth/callback`, `/forbidden`, `/route-disabled`, `/upgrade-required`, `/me/plan`, `/me/upgrades`
**管理台 (30+ 路由)**: `/admin` — dashboard, tenants, render-jobs, delivery, extensions, quota-billing, analytics, notifications (4 子页面), audit, config, feature-flags (3 子页面), policies (2 子页面), routes, monitoring, audit-log, feedback, analytics/datasets, analytics/query-audit, entitlements (5 子页面), billing (6 子页面)

### Admin 页面（44 个 .vue 文件）

涵盖：租户管理、渲染作业监控、交付管理、插件管理、配额计费、用户分析、通知管理（事件定义+投递日志）、审计合规、配置管理、Feature Flag CRUD + 评估预览 + 评估日志、ABAC 策略管理 + 规则编辑器 + 策略模拟、路由管理、监控反馈、审计日志查看器、用户反馈管理、NLQ 数据集目录、NLQ 查询审计、Entitlement Bundle/Override/Grant/Shared/Quota 管理、Billing Plan/Pricing/Usage/Credit/Quote/Invoice 管理

### Audit Log 页面

- `pages/admin/AuditLogPage.vue` — 审计日志查看器
- 支持筛选：分类、操作、演员、结果、租户、日期范围
- 分页、详情查看（敏感数据脱敏）、CSV 导出
- API: `AuditAPI.listAuditRecords`, `getAuditRecord`, `exportAuditRecords`

### Settings 页面

- `pages/user/MySettingsPage.vue`
  - Profile 保存：**禁用**（前端注释 `// Profile -- no backend API yet (button is disabled)`）
  - 通知偏好：**已实现**（API 驱动）
  - 外观设置：**localStorage only**
  - 账户删除：**禁用**（`Account deletion is not yet implemented`）

### API Client

- 核心 Axios 实例 + 认证拦截器
- 14 个域 API client 文件（`src/api/`）
- 15 个管理员 API client 文件（`src/api/admin/`）
- GraphQL 客户端（`graphqlClient.ts`）

### Disabled / Coming Soon 功能

1. **Profile 保存** — 按钮禁用，提示 "Profile saving requires a server API (coming soon)"
2. **账户删除** — 按钮禁用，提示 "Account deletion is not yet implemented"
3. **Feature Flag Unleash** — 关闭时显示 "Using InMemoryProvider -- flags return default values only"
4. **导出 Feature Flag** — `FEATURE_FLAG_DISABLED` 错误码检查
5. **路由禁用** — 后端可通过 `NAV-403-DISABLED/FEAT/TIER` 和 `NAV-404-HIDDEN` 禁用路由
6. **关键通知** — "Critical notifications cannot be disabled"

### 测试覆盖

- 88 个 spec 文件
- API 测试：10 个
- Store 测试：3 个
- Composable 测试：12 个
- 组件测试：17 个
- 页面测试：46 个
- 工具测试：7 个
- 客户端导出测试：2 个

---

## 6. 审计与安全

### AuditService

- 通过 `audit_records` 表记录所有审计事件
- 支持 18 种分类（`AuditCategory` enum）
- V2/V3 迁移确保分类约束

### AuditPortAdapter

- `Slf4jSecurityAlertAdapter`（默认）
- `NoopSecurityAlertAdapter`
- `WebhookSecurityAlertAdapter`（SSRF 防护）

### AuditCategory

18 种：ADMIN_AUDIT, RENDER, DATA_GOVERNANCE, FEATURE_FLAG, ENTITLEMENT, GRAPHQL_OPERATION, NLQ, PROVIDER_HEALTH, IDENTITY, API_REQUEST, EXTENSION, EXTENSION_ROUTING, EXTENSION_RESOURCE, PERMISSION, CONFIG, PROMPT, POLICY, SANDBOX, UNKNOWN

### audit_records 约束

- NOT NULL：分类字段不可为空
- CHECK：仅允许 AuditCategory 枚举值
- V3 迁移回填 NULL 为 'UNKNOWN'

### Admin Audit

- 前端 `AuditLogPage.vue` 提供筛选、分页、详情、CSV 导出
- `AuditCompliance.vue` 提供 Outbox 监控

### Audit Export

- `AuditAPI.exportAuditRecords` — CSV 导出
- 敏感字段自动脱敏（`[REDACTED]`）
- CSV 注入防护（需验证实现）

### Audit Alerts

- 拒绝 burst 检测：5 次/600 秒窗口，冷却 1800 秒
- 告警 JSON Schema 包含 rule/severity/category/action/actor/resource/result/attributes
- 发布失败不阻塞 `AuditService.record()`

### Denied Burst Detection

- `AUDIT_ALERTS_DENIED_BURST_ENABLED=true`（默认）
- 阈值/窗口/冷却时间可配置
- 最大追踪演员数：10000

### Webhook SSRF 防护

- 始终阻断：localhost, 127.0.0.0/8, ::1, 0.0.0.0, 169.254.169.254, 169.254.0.0/16, 私有 IPv4（除非 `allow-private-network=true`）
- 允许列表：`allowed-hosts`（精确主机名）+ `allowed-domain-suffixes`（域名后缀）
- 元数据 IP 始终阻断

### 敏感字段脱敏

authorization, cookie, token, apiKey, secret, password, signedUrl, virtualKey, litellmKey, bearer 等

### 直接调用 / 间接调用路径

- 直接：Controller → AuditService → DB
- 间接：Domain Event → Outbox → EventListener → AuditService

---

## 7. Sandbox

### execution-mode

| 模式 | 描述 | 生产可用 |
|------|------|----------|
| `disabled` | 拒绝所有执行 | ✅ 最安全默认 |
| `external` | HTTP 调用外部 Worker | ✅ 需 Worker 部署 |
| `in-process` | JVM ScriptEngine.eval | ❌ 永不在生产启用 |

### Profile 默认行为

- 默认：`enabled=false`, `execution-mode=disabled`
- Dev：`enabled=true`, `execution-mode=external`, `allowed-languages=python`
- Prod：`enabled=false`, `execution-mode=disabled`

### External Worker Port

- `sandbox.worker.base-url`（dev: `http://sandbox-worker:8091`）
- 连接超时 1s，读取超时 5s
- HTTP API: `POST /v1/sandbox/execute`

### In-process 风险控制

- `allow-in-process-eval=false`（默认）
- 生产配置显式禁用
- 就绪验证脚本检查无 `in-process` 模式

### Audit Logging

- 仅记录 codeHash（SHA-256 截断至 16 位十六进制）和 codeLength
- 不记录代码内容
- 不记录 Authorization/token/secret

### NetworkPolicy

- sandbox-worker：入口仅 platform-api 8091，出口 **deny-all**（`egress: []`）
- 无 DNS 访问

### 未完成项

- 实际代码执行逻辑（Worker 端）为 Python 脚本，功能有限
- 无 WASM/container 隔离（文档提到 Wasm/container placeholder）

---

## 8. Render / Worker / Storage

### Render Job 模型

- `render_job` 表 + `render_job_status_history` 表
- 状态流转通过陈旧补偿器管理
- 支持本地执行和 Temporal 执行两种模式
- 支持 DAG 管道（`dag.enabled: true`）

### Worker 架构

- `remote-render-worker`：独立 Spring Boot 应用（port 8090）
- 注册/心跳/作业回调（`RemoteWorkerController`）
- H2 文件数据库（`/tmp/platform-remote-worker/db`）
- 安装 ffmpeg + curl
- 所有 13+ 渲染提供商在 Worker 端均启用

### Artifact 输出

- `artifact` 表 + `artifact_relation` 表
- 存储 URI 引用
- 前端 `ArtifactPreviewModal` + `ArtifactResult` 组件

### Storage 抽象

- `storage-module` 提供 S3 兼容存储
- 支持 AWS S3 / Cloudflare R2 / MinIO
- 默认本地存储（`STORAGE_S3_ENABLED=false`）
- 孤立扫描：artifacts + render-cache buckets

### Delivery Job

- `delivery_job` 表 + `delivery_destination` + `delivery_policy`
- 支持 SFTP（JSch）和 SMB（SMBJ）
- Worker 模式（`delivery.worker-enabled: true`）
- 重试：最大 3 次（`DELIVERY_MAX_ATTEMPTS: 3`）
- 轮询间隔：5 秒

### Retry / Migration / Destinations

- 交付重试：最大 3 次
- Flyway 迁移：V1-V3（逻辑版本）
- 交付目的地：SFTP/SMB/策略路由

### 本地测试方式

```bash
docker compose -f docker-compose.dev.yml up -d
# 包含 db + app + render-worker + sandbox-worker
```

### 缺失项

- GPU 渲染调度未实现
- 实际渲染执行逻辑依赖外部工具（ffmpeg 等）安装
- PopcornFX 集成文档提及但代码未找到

---

## 9. 部署与运维

### Docker Images

| Dockerfile | 用途 | 基础镜像 | 端口 |
|---|---|---|---|
| `platform/Dockerfile` | 主应用（前端+后端 JAR） | eclipse-temurin:25-jre-jammy | 8080 |
| `platform/sandbox-worker/Dockerfile` | 沙箱 Worker | eclipse-temurin:25-jre-jammy | 8091 |
| `platform/remote-render-worker/Dockerfile` | 远程渲染 Worker（+ffmpeg） | eclipse-temurin:25-jre-jammy | 8090 |
| `platform/frontend/Dockerfile` | 前端开发服务器 | node:22-alpine | 3000 |
| `platform/infra/docker/Dockerfile.render-worker-ofx` | OFX 渲染 Worker（draft） | — | — |
| `platform/infra/docker/Dockerfile.backend` | 备选后端镜像 | — | 8080 |
| `platform/infra/docker/Dockerfile.render-worker-natron` | Natron Worker POC | — | — |
| `platform/infra/docker/Dockerfile.render-worker-javacv` | JavaCV Worker（draft） | — | — |

### Image Tag 规则

- **永不在生产使用 `:latest` 或 `:dev`**
- CI 生成 `git-<12-char-sha>`
- 所有三个核心镜像使用相同 tag
- 就绪验证脚本强制执行

### K8s Base / Staging / Production

**Base** (`k8s/base/`): 20 个资源文件
- Namespace, ConfigMap, Secret, PVC
- 4 个 Deployment（api, render-worker, sandbox-worker, egress-proxy）
- 4 个 Service
- Ingress, HPA (x2), NetworkPolicy (x4)

**Staging** (`k8s/overlays/staging/`):
- Namespace: `media-platform-staging`
- API replicas: 1, 资源减半
- Ingress: `staging.api.example.com`
- SPRING_PROFILES_ACTIVE: `staging,oidc`
- LOG_LEVEL: DEBUG

**Production** (`k8s/overlays/production/`):
- Namespace: `media-platform`
- API replicas: 2
- Ingress: `api.example.com`
- SPRING_PROFILES_ACTIVE: `prod,oidc`
- LOG_LEVEL: INFO

### GitOps

- `gitops/staging/` — 预渲染的 staging manifests
- `gitops/production/` — 预渲染的 production manifests
- `gitops/argocd/` — ArgoCD Application 定义
- Staging: 自动同步（prune + selfHeal）
- Production: **手动同步**（无自动化）

### ArgoCD

- `application-staging.yaml` — auto-sync on main
- `application-production.yaml` — manual sync only
- repoURL 需要替换为实际 Git 仓库 URL

### CI Workflow

- **backend**: `./gradlew test` → `bootJar` → `docker build`
- **frontend**: lint → vitest → vite build
- **images** (main push only): build/push 3 images → update gitops staging → validate → create staging PR
- **promote-production** (manual dispatch): update gitops production → strict validate → create production PR
- **deploy-staging** (manual dispatch): update gitops staging → create staging PR

### Production Readiness

`validate-production-readiness.sh` 检查：
1. Image Safety（无 :latest/:dev，三镜像同 tag）
2. Production Config Safety（无 in-process/dev-auth/oidc-bootstrap）
3. SecurityContext（nonRoot, readOnlyFS, noPrivDrop, capDrop ALL）
4. Resource Limits
5. Health Probes
6. Network Isolation（4 个 NetworkPolicy）
6e. Egress Proxy（deployment/service/configmap/networkpolicy）
6f. Allowed Domains
6g. Proxy Environment Variables
7. Secret Safety
8. Namespace
9. Ingress TLS

### Egress NetworkPolicy

- sandbox-worker: **egress deny-all**
- api: 仅允许 DNS(53), sandbox-worker(8091), postgresql(5432), minio(9000/443), egress-proxy(3128)
- render-worker: 仅允许 DNS(53), api(8080), postgresql(5432), minio(9000/443), egress-proxy(3128)
- egress-proxy: 入口允许 api+render-worker(3128)，出口 0.0.0.0/0（排除私有/元数据）

### Egress Proxy (Squid)

- 允许域名：`.oidc.example.com`, `.s3.example.com`, `.alerts.example.com`, `.litellm.example.com`, `.stripe.com`, `.sentry.io`, `.openreplay.com`
- **⚠️ 这些是 placeholder，生产必须替换为真实域名**
- 阻断：169.254.169.254, link-local, loopback, RFC1918

### Smoke Test

- `EgressProxySmokeService` — 使用 `java.net.http.HttpClient` 验证代理
- 默认关闭（`EGRESS_PROXY_SMOKE_ENABLED=false`）
- JVM 代理属性默认关闭（`EGRESS_PROXY_JVM_ENABLED=false`）

### CI Gate

- Staging: `verify-egress-smoke-config.sh`（正常模式，FAIL 阻止 PR）
- Production: `verify-egress-smoke-config.sh --strict`（严格模式，FAIL 阻止 PR）

### Secrets / ConfigMap 策略

- K8s Secret 仅包含引用，不包含实际值
- 生产通过 `kubectl create secret` 或外部密钥管理
- `SPRING_DATASOURCE_*` / `APP_JWT_SECRET` / `STRIPE_WEBHOOK_SECRET` 等通过环境变量注入

### Rollback

- ArgoCD: revert PR → auto-sync (staging) / manual-sync (production)
- kubectl: `rollout undo deployment/api`
- 紧急: `kubectl set image` 指定旧 tag

---

## 10. 开发注意事项

### 本地开发

```bash
cd platform
docker compose -f docker-compose.dev.yml up -d  # db + app + render-worker + sandbox-worker
./gradlew :platform-app:bootRun                   # dev profile, 免认证
cd frontend && npm install && npm run dev          # port 3000, 代理到 8088
```

### 测试命令

```bash
./gradlew test                    # 后端全部测试
./gradlew :platform-app:test      # 仅 platform-app 测试
cd frontend && npx vitest run     # 前端测试
./scripts/smoke-local.sh          # 本地 smoke test
./scripts/local-test.sh           # 完整本地集成测试
```

### 添加 API 的规则

1. Controller 放在 `platform-app` 模块
2. 使用 `/api/v1/*` 前缀
3. 返回 `ProblemDetail` 错误格式（通过 `GlobalExceptionHandler`）
4. 添加审计记录（`AuditService.record()`）
5. 添加对应的 OpenAPI 注解
6. 前端 API client 在 `frontend/src/api/` 中添加

### 添加 Audit 的规则

1. 使用 `AuditCategory` 枚举中的分类
2. 记录 actor/action/resource/result
3. 敏感字段自动脱敏
4. 通过 Outbox 事件分发（如需异步）

### 添加 Admin Endpoint 的规则

1. 路径前缀 `/api/v1/admin/*`
2. 前端 admin API client 在 `frontend/src/api/admin/` 中添加
3. 添加对应的 admin 前端页面
4. 确保路由管理（`RouteManagementController`）中可配置

### 添加 External HTTP Call 的规则

1. 确保目标域名在 Squid `allowed-domains.txt` 中
2. 确保 K8s NetworkPolicy 允许到 egress-proxy(3128)
3. Java 客户端可能需要 JVM 代理属性（`EGRESS_PROXY_JVM_ENABLED=true`）
4. Webhook URL 需通过 SSRF 验证
5. 添加审计记录

### 添加 Config Property 的规则

1. 使用 `@ConfigurationProperties` 注解
2. 在 `application.yml` 中提供默认值
3. 使用 `app.*` 命名空间
4. 文档化在本文档配置说明章节
5. K8s ConfigMap 中添加对应条目

### 添加 Migration 的规则

1. 文件放在 `platform-app/src/main/resources/db/migration/`
2. 命名规则：`V{n}__description.sql`
3. Greenfield 环境：V1 包含完整 schema
4. 添加向后兼容的变更（避免破坏性修改）
5. 在 staging 验证后再应用到 production

### 添加 K8s Env 的规则

1. 在 `k8s/base/configmap.yaml` 中添加
2. 在 overlay 中按需覆盖
3. 在 `application.yml` 中提供默认值
4. 在就绪验证脚本中添加检查（如需）

### 不要做什么

1. **不要** 在生产开启 `sandbox.execution-mode=in-process`
2. **不要** 在生产开启 `app.security.dev-auth-endpoint=true`
3. **不要** 使用 `:latest` 或 `:dev` 镜像 tag
4. **不要** 在 Git 中提交真实 secret 值
5. **不要** 允许 `0.0.0.0/0` 在应用 NetworkPolicy 中
6. **不要** 允许 `169.254.169.254` 在任何 NetworkPolicy 中
7. **不要** 让 sandbox-worker 访问出口网络
8. **不要** 让 egress-proxy 接受 sandbox-worker 入口
9. **不要** 在审计日志中记录代码内容
10. **不要** 允许 LLM 输出 raw shell 命令

---

## 11. 验收标准

### 后端

- [ ] `./gradlew test` 全部通过
- [ ] `./gradlew :platform-app:bootJar` 构建成功
- [ ] Spring Modulith 边界验证通过
- [ ] 所有 Controller 有对应的测试
- [ ] 审计记录覆盖所有关键操作
- [ ] 错误码统一使用 `ProblemDetail` 格式

### 前端

- [ ] `npm run lint` 无错误
- [ ] `npx vitest run` 全部通过
- [ ] `npm run build` 构建成功
- [ ] 所有 API client 有对应的测试

### 审计

- [ ] 审计记录分类正确（18 种 AuditCategory）
- [ ] NULL 分类已回填为 UNKNOWN
- [ ] CHECK 约束强制执行
- [ ] 拒绝 burst 检测正常工作
- [ ] Webhook SSRF 防护验证通过
- [ ] 敏感字段脱敏验证通过

### Sandbox

- [ ] `disabled` 模式拒绝所有请求
- [ ] `external` 模式调用 Worker
- [ ] `in-process` + `allow-in-process-eval=false` 拒绝 eval
- [ ] Worker 不可用返回明确错误
- [ ] 审计日志仅包含 codeHash
- [ ] K8s NetworkPolicy deny-all 出口

### Render

- [ ] 渲染作业 CRUD 正常
- [ ] 状态流转正确
- [ ] 陈旧补偿器正常工作
- [ ] 远程 Worker 注册/心跳正常
- [ ] 产物预览正常

### Storage

- [ ] 文件上传/下载正常
- [ ] 存储孤立扫描正常
- [ ] S3 切换正常（需 staging 验证）

### Deployment

- [ ] Docker 镜像构建成功
- [ ] K8s 资源渲染正确
- [ ] 就绪验证脚本通过
- [ ] ArgoCD 同步正常
- [ ] 健康检查端点返回 UP

### Egress

- [ ] 4 个 NetworkPolicy 全部应用
- [ ] egress-proxy 部署正常
- [ ] Squid 允许域名配置正确
- [ ] HTTP_PROXY/HTTPS_PROXY/NO_PROXY 环境变量正确
- [ ] sandbox-worker 无代理环境变量
- [ ] Smoke test 通过（staging）

### CI

- [ ] backend job 通过
- [ ] frontend job 通过
- [ ] images job 通过（main push）
- [ ] staging PR 自动创建
- [ ] production PR 需要手动触发

### Security

- [ ] 所有容器 runAsNonRoot
- [ ] 所有容器 readOnlyRootFilesystem
- [ ] 所有容器 allowPrivilegeEscalation=false
- [ ] 所有容器 capabilities.drop=ALL
- [ ] Ingress TLS 配置
- [ ] CORS 配置正确

### Production Promotion

- [ ] 镜像 tag 为不可变 SHA
- [ ] 无 :latest/:dev tag
- [ ] 无 dev-auth-endpoint
- [ ] 无 in-process sandbox
- [ ] allowed-domains.txt 为真实域名
- [ ] staging smoke 通过
- [ ] staging S3/OIDC/webhook 验证通过
- [ ] 就绪验证严格模式通过
- [ ] 二次审批

---

## 12. 当前缺口与风险

### 代码已实现但未环境验证

1. **S3 存储切换** — 配置存在但默认关闭，需 staging 验证
2. **OIDC 认证** — 配置完整但依赖外部 Authentik 实例
3. **Webhook SSRF 防护** — 代码实现但需实际 SSRF 攻击测试
4. **Temporal 工作流** — profile 存在但默认关闭
5. **LiteLLM 代理** — profile 存在但未验证端到端
6. **GraphQL** — 默认关闭，前端有 GraphQL 客户端但功能未验证
7. **NLQ** — 前端页面 + API 存在，但依赖真实 AI 模型
8. **社交发布** — 前端 + API 存在，但无实际社交平台集成
9. **客户端导出** — 前端合成能力存在但需浏览器兼容性验证

### 测试缺失

1. **渲染执行端到端测试** — 依赖外部工具（ffmpeg 等）
2. **沙箱代码执行测试** — 仅单元测试，无真实执行验证
3. **K8s NetworkPolicy 测试** — 无集群内网络隔离验证
4. **出口代理 smoke 测试** — 默认关闭
5. **支付流程端到端测试** — 全部 Noop
6. **LLM 多预览测试** — 未实现

### 生产配置 Placeholder

1. **allowed-domains.txt** — 包含 `.example.com` 等 placeholder
2. **ingress host** — staging/production 使用 `example.com`
3. **ArgoCD repoURL** — `https://github.com/your-org/platform.git` 需替换
4. **Stripe URL** — `https://example.com/billing/success`
5. **OpenReplay ingest-point** — `https://openrelay.yourdomain.com`
6. **Vault** — 默认关闭，使用内联凭证
7. **镜像仓库** — `ghcr.io/<owner>` / `registry.example.com` 需替换

### Smoke Disabled

- `EGRESS_PROXY_SMOKE_ENABLED=false`（默认）
- `EGRESS_PROXY_JVM_ENABLED=false`（默认）
- 生产推广严格模式要求启用

### Strict Gate Expected Failure

- 当前 gitops/production 中的 `allowed-domains.txt` 包含 example.com placeholder
- `verify-egress-smoke-config.sh --strict` 会标记 WARN/FAIL
- 在替换为真实域名前，production promotion CI gate 会失败

### Egress Proxy 真实域名未配置

- `.oidc.example.com` → 需替换为真实 OIDC issuer
- `.s3.example.com` → 需替换为真实 S3 endpoint
- `.alerts.example.com` → 需替换为真实 webhook endpoint
- `.litellm.example.com` → 需替换为真实 LiteLLM 地址

### S3/OIDC/Webhook/Provider 需 Staging 验证

- 所有外部依赖需在 staging 通过出口代理验证
- 参见 `egress-smoke-rollout.md` 验收清单

### OTIO / LLM 多预览尚未实现

- **OTIO**：前端有 `otio.ts` 工具函数，MCP 控制器提供 OTIO 导入/导出，但无 Java OTIO 原生库依赖
- **LLM 多预览**：前端有 AI proposal 组件，但后端仅 StubChatProvider
- **自然语言连续修改**：前端有 patch 对话框组件，但 LLM 驱动未实现
- **素材分析**：MCP 控制器提供 probe/validate，但无自动分析 pipeline
