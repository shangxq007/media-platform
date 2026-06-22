> **Status:** Archived (2026-06-22)
> **Reason:** Point-in-time analysis from 2026-05-28. Superseded by comprehensive audit.
> **Superseded By:** `docs/system-audit/platform-architecture-audit-2026-06-13.md`
> **Do not use as current reference.**

---

# 已有文档与代码实现对照报告

> **生成方式**：对比代码反推结果与已有 docs 目录内容
> **生成日期**：2026-05-28
> **代码版本**：0.2.0-SNAPSHOT

---

## 1. 已有文档覆盖了哪些内容

| 主题 | 已有文档路径 | 覆盖程度 |
|------|-------------|----------|
| 项目概览 | `docs/overview/01-project-overview.md` | 全面 |
| 项目状态 | `docs/overview/02-project-status.md` | 全面 |
| 系统架构 | `docs/architecture/01-system-architecture.md` | 全面 |
| 后端架构 | `docs/architecture/02-backend-architecture.md` | 全面 |
| 模块架构 | `docs/architecture/03-module-architecture.md` | 全面 |
| 前端架构 | `docs/architecture/04-frontend-architecture.md` | 全面 |
| 请求流 | `docs/architecture/05-request-flows.md` | 全面 |
| 数据架构 | `docs/architecture/06-data-architecture.md` | 全面 |
| 架构决策 | `docs/architecture/07-architecture-decisions.md` | 全面 |
| 部署架构 | `docs/architecture/08-deployment-architecture.md` | 全面 |
| 模块参考 | `docs/modules/01-04-*.md` | 全面 |
| 渲染管道 | `docs/media-rendering/01-13-*.md` | 全面 |
| 前端文档 | `docs/frontend/01-09-*.md` | 全面 |
| 权限/计费 | `docs/billing-access/01-12-*.md` | 全面 |
| API 策略 | `docs/api/01-03-*.md` | 全面 |
| Prompt/AI/NLQ | `docs/prompt-ai/01-03-*.md` | 全面 |
| 扩展平台 | `docs/extensions/01-02-*.md` | 全面 |
| 可观测性 | `docs/observability/01-05-*.md` | 全面 |
| 部署运维 | `docs/deployment/01-04-*.md` | 全面 |
| 开发规范 | `docs/development/01-04-*.md` | 全面 |
| 评审/阻断 | `docs/review/01-06-*.md` | 全面 |
| 生产就绪 | `platform/docs/production-readiness.md` | 全面 |
| 环境指南 | `platform/docs/environments.md` | 全面 |
| GitOps | `platform/docs/gitops.md` | 全面 |
| 出口代理 | `platform/docs/egress-smoke-rollout.md` | 全面 |
| 沙箱安全 | `platform/docs/sandbox-security.md` | 全面 |
| K8s 部署 | `platform/docs/k8s-deployment.md` | 全面 |
| 安全告警 | `platform/docs/security-alerts.md` | 全面 |
| 外部资源 | `docs/deployment/external-resources.md` | 部分（Novu/SMTP 文档但代码未实现） |
| 中文文档 | `docs/zh/` + `docs/archive/docs-cn/` | 全面（翻译版） |
| 归档文档 | `docs/archive/` | 大量历史文档 |

---

## 2. 代码有但文档缺失

### 2.1 代码已实现但未在文档中描述

| 项目 | 代码证据 | 文档状态 |
|------|----------|----------|
| **MCP 媒体工具** | `McpMediaToolsController` (`/api/v1/mcp/*`) — probe/validate/import/export/render-plan/canonicalize/diff/impact/patch/packaging | 文档仅在 `platform/docs/` 中部分提及，无完整 MCP 协议文档 |
| **时间线 Schema 1.0 详细规范** | 前端 `types/index.ts` (854 行) + MCP 控制器支持 | `docs/media-rendering/13-internal-timeline-schema-v1.md` 存在但可能不完整 |
| **客户端导出** | `clientExport/` 目录（compositor/timelineParser/resolveExportPath/capabilities） | 无专门文档 |
| **社交发布** | `social-publish-module`, `SocialPublishPage.vue` | 无专门文档 |
| **LiteLLM 租户虚拟密钥** | `TenantAiAdminController`, `application-litellm.yml` | 无专门文档 |
| **渲染缓存** | `render.cache.*` 配置（remote/content-hash/cleanup/webhook） | 无专门文档 |
| **AAF 转换器** | `aaf.*` 配置（converter/worker/poll/queue） | 无专门文档 |
| **存储孤立扫描/清除** | `platform.storage.orphan-scan/purge` | 无专门文档 |
| **支付 Webhook 安全** | `PAYMENT_WEBHOOK_ALLOW_UNSIGNED=false` | 无专门文档 |
| **前端路由动态注册** | `syncDynamicRoutes()` + `componentMap` (27 entries) | 无专门文档 |
| **前端 OIDC 认证流程** | `oidcClient.ts` + `oidcConfig.ts` + `OauthCallbackPage.vue` | 无专门前端认证文档 |
| **前端 Sentry/OpenReplay 集成** | `sentry.ts` + `openreplay.ts` | 无专门文档 |
| **前端 Undo/Redo** | `useHistoryStore` (max 50 states) | 无专门文档 |
| **前端时间线同步** | `useTimelineSync.ts` + `TimelineSyncMetaStore` + `timelineSyncHash.ts` | 无专门文档 |
| **前端字幕系统** | `SubtitleStore` + `subtitleParser.ts` (SRT/ASS/VTT) + 4 个组件 | `docs/frontend/` 提及但不够详细 |
| **前端 Beta 功能面板** | `BetaFeaturesPanel.vue` | 无文档 |
| **前端通知中心** | `NotificationDropdown` + `NotificationBell` + `NotificationListItem` | 无专门文档 |
| **兼容性迁移模块** | `compatibility-migration-module` (9 schema families) | 无专门文档 |
| **云资源模块** | `cloud-resource-module` | 无专门文档 |
| **Outbox 事件** | `outbox-event-module` + `dispatch-interval/retry-interval` | `docs/archive/outbox-reliability.md` 存在但新 docs 目录未引用 |
| **沙箱 Worker 独立进程** | `SandboxWorkerApplication.java` + `sandbox-worker/Dockerfile` | `sandbox-security.md` 描述架构但无独立进程运维文档 |
| **远程渲染 Worker 独立进程** | `RemoteRenderWorkerApplication.java` + `remote-render-worker/Dockerfile` | 无独立进程运维文档 |
| **Docker Compose Authentik** | `docker-compose.authentik.yml` | 无文档 |
| **Natron POC** | `infra/docker/Dockerfile.render-worker-natron` + `application-natron-worker.yml` | `docs/media-rendering/07-natron-worker-poc.md` 存在 |
| **jOOQ 代码生成** | `scripts/generate-jooq.sh` | 无文档 |
| **文档验证脚本** | `scripts/docs-validate.sh` | 无文档 |

### 2.2 配置属性未文档化

| 配置 | 代码位置 | 文档状态 |
|------|----------|----------|
| `app.ai.routing.*.fallback` | `application-litellm.yml` | 无文档 |
| `render.aaf.*` | `application.yml:276-281` | 无文档 |
| `render.subtitle.libass.*` | `application.yml:335-337` | 无文档 |
| `platform.artifact.gc.*` | `application.yml:394-399` | 无文档 |
| `platform.timeline.asset.gc.*` | `application.yml:403-408` | 无文档 |
| `platform.storage.orphan-purge.*` | `application.yml:414-417` | 无文档 |
| `app.filter.rate-limit.enabled` | `application.yml:438-439` | 无文档 |
| `render.cache.webhook-*` | `application.yml:349-351` | 无文档 |
| `render.providers.shotstack.*` | `application.yml:307-310` | 无文档 |
| `render.providers.remotion.*` | `application.yml:314-318` | 无文档 |
| `render.providers.blender.*` | `application.yml:319-322` | 无文档 |
| `render.providers.vapoursynth.*` | `application.yml:323-327` | 无文档 |
| `render.providers.shaka.*` | `application.yml:328-331` | 无文档 |
| `render.providers.skia.*` | `application.yml:332-334` | 无文档 |
| `render.timeline.mlt-multitrack-min-tracks` | `application.yml:339` | 无文档 |

### 2.3 测试未文档化

| 测试 | 代码证据 | 文档状态 |
|------|----------|----------|
| 前端 OTIO 工具测试 | `utils/otio.spec.ts` | 无文档 |
| 前端时间线导入测试 | `utils/timelineImport.spec.ts` | 无文档 |
| 前端时间线 patch 高亮测试 | `utils/timelinePatchHighlight.spec.ts` | 无文档 |
| 前端时间线冲突合并测试 | `utils/timelineConflictMerge.spec.ts` | 无文档 |
| 客户端导出时间线解析测试 | `clientExport/timelineParser.spec.ts` | 无文档 |
| 前端 no-dynamic-execution 测试 | `no-dynamic-execution.spec.ts` | 无文档 |
| E2E 渲染流测试脚本 | `scripts/smoke/e2e-render-flow.sh` | 无文档 |

---

## 3. 文档有但代码未实现

| 文档内容 | 文档路径 | 代码状态 | 风险等级 |
|----------|----------|----------|----------|
| **Novu 通知集成** | `docs/deployment/external-resources.md` | 无 `NovuNotificationProvider` 类 | 🔴 高 — 文档描述的能力不存在 |
| **SMTP/SendGrid/AWS SES 邮件** | `docs/deployment/external-resources.md` | 无 `EmailNotificationProvider` 实现 | 🔴 高 — 邮件发送能力不存在 |
| **Twilio SMS** | `docs/deployment/external-resources.md` | 无 `SmsNotificationProvider` 实现 | 🔴 高 — SMS 能力不存在 |
| **KillBill 计费引擎** | `docs/overview/02-project-status.md` | 无 `NoopKillBillBillingEngine` 类 | 🟡 中 — 文档引用不存在的类名 |
| **Medusa 目录适配器** | `docs/overview/02-project-status.md` | 无 `NoopMedusaCatalogAdapter` 类 | 🟡 中 — 文档引用不存在的类名 |
| **FederatedQueryGateway** | `docs/overview/02-project-status.md` | 无 `NoopFederatedQueryGateway` 类 | 🟡 中 — 文档引用不存在的类名 |
| **Wasm/Container 沙箱** | `docs/modules/01-core-modules.md` | `sandbox-runtime-module` 仅有基础结构，无 Wasm/container 实现 | 🟡 中 — 文档过度描述 |
| **多数据源配置** | `docs/architecture/02-backend-architecture.md` | `datasource-module` 存在但仅 PostgreSQL 单数据源配置 | 🟢 低 — 架构支持但未配置 |
| **OpenTelemetry 集成** | `docs/overview/01-project-overview.md` (Future Work) | OTLP 配置存在（`management.otlp.*`）但默认 localhost | 🟢 低 — 配置存在但未完整部署 |
| **PopcornFX 集成** | `docs/media-rendering/08-09-*.md` | 无 PopcornFX 代码 | 🔴 高 — 渲染管道文档描述不存在的能力 |
| **GPU 渲染** | `docs/archive/gpu-rendering.md` | Dockerfile 提及 GPU 工具但无调度逻辑 | 🟡 中 — 规划阶段 |

---

## 4. 文档与代码冲突

### 4.1 数据不一致

| 项目 | 文档值 | 代码值 | 来源 |
|------|--------|--------|------|
| **Gradle 版本** | 文档: 9.1 | 代码: 9.1 | 一致 ✅ |
| **Flyway 迁移数量** | 文档: 17 | 代码: 3 (V1-V3) | ❌ **冲突** — 文档说 17，代码 Flyway 目录只有 3 个文件 |
| **Java 源文件数** | 文档: ~350+ / ~1050+ | 代码: 实际 Java 文件数需统计 | ⚠️ 需验证 |
| **数据库表数量** | 文档: 28+ | 代码: V1 迁移定义 70+ 张表 | ❌ **冲突** — 代码远多于文档 |
| **模块数量** | 文档: 30 | 代码: 34 (settings.gradle.kts include 34) | ❌ **冲突** — 代码比文档多 4 个 |
| **后端测试文件** | 文档: 54+ | 代码: 366 个 `*Test*.java` | ❌ **冲突** — 代码远多于文档 |
| **前端测试文件** | 文档: 78+ | 代码: 88 个 `*.spec.ts` | ❌ **冲突** — 代码多于文档 |
| **Temporal SDK 版本** | 文档: 1.33.0 | 代码: 依赖由 BOM 管理，未显式指定 | ⚠️ 需验证 |
| **Render 提供商数量** | 文档: 6 | 代码: 13+ | ❌ **冲突** — 代码远多于文档 |
| **Error Codes** | 文档: 60+ | 代码: 需统计 | ⚠️ 需验证 |

### 4.2 模块状态不一致

| 模块 | 文档状态 | 代码状态 |
|------|----------|----------|
| `user-analytics-module` | 未在文档模块列表中 | ✅ 存在于 `settings.gradle.kts` |
| `social-publish-module` | 未在文档模块列表中 | ✅ 存在于 `settings.gradle.kts` |
| `compatibility-migration-module` | 文档列出 | ✅ 代码存在 |
| `cloud-resource-module` | 文档列出 | ✅ 代码存在 |

### 4.3 默认值不一致

| 配置 | 文档描述 | 代码默认值 | 状态 |
|------|----------|------------|------|
| `spring.graphql.enabled` | 文档未明确 | `false` | ✅ 一致（关闭） |
| `app.security.enabled` | 文档说生产需启用 | dev: `false`, prod: `true` | ✅ 一致 |
| `sandbox.enabled` | 文档说默认关闭 | `false` | ✅ 一致 |
| `render.execution.mode` | 文档说默认 local | `local` | ✅ 一致 |
| `storage.s3.enabled` | 文档未明确 | `false` | ✅ 一致 |
| `app.temporal.enabled` | 文档说可选 | `false` | ✅ 一致 |

### 4.4 Profile 不一致

| Profile | 文档描述 | 代码配置 | 状态 |
|---------|----------|----------|------|
| dev | 文档说安全关闭 | `app.security.enabled: false` | ✅ 一致 |
| prod | 文档说安全启用 | `app.security.enabled: true`, `oauth2.enabled: true` | ✅ 一致 |
| oidc | 文档说 Authentik | `application-oidc.yml` | ✅ 一致 |
| litellm | 文档未详细描述 | `application-litellm.yml` 完整配置 | ⚠️ 文档缺失 |
| temporal | 文档说可选 | `application-temporal.yml` | ✅ 一致 |
| r2 | 文档未提及 | `application-r2.yml` | ⚠️ 文档缺失 |

### 4.5 镜像名不一致

| 文档 | 代码 (CI workflow) | 状态 |
|------|-------------------|------|
| 文档: `media-platform` | CI: `platform-api`, `platform-render-worker`, `platform-sandbox-worker` | ❌ **冲突** |
| gitops: `platform-{api,render-worker,sandbox-worker}` | CI: 同上 | ✅ 代码一致 |
| 文档 (旧): `media-platform:dev/latest` | 代码: 不可变 tag | ⚠️ 文档有旧引用 |

### 4.6 Namespace 不一致

| 文档 | 代码 (gitops) | 状态 |
|------|--------------|------|
| `docs/environments.md`: production → `media-platform` | `gitops/production/`: `media-platform-production` | ❌ **冲突** |
| `docs/environments.md`: staging → `media-platform-staging` | `gitops/staging/`: `media-platform-staging` | ✅ 一致 |

### 4.7 Old Example 命名残留

| 位置 | 类型 | 状态 |
|------|------|------|
| `build.gradle.kts:11` — `group = "com.example.platform"` | Gradle group | ⚠️ example 残留 |
| `allowed-domains.txt` — `.example.com` 等 | 配置 placeholder | ✅ 有意 placeholder |
| `ingress` — `api.example.com` / `staging.api.example.com` | K8s 主机名 | ✅ 有意 placeholder |
| `Stripe URL` — `https://example.com/billing/*` | 回调 URL | ✅ 有意 placeholder |
| `ArgoCD repoURL` — `https://github.com/your-org/platform.git` | Git URL | ✅ 有意 placeholder |
| Java package — `com.example.platform.*` | 包名 | ⚠️ example 残留 |
| `application.yml` — `example.com` OTLP/监控地址 | 服务地址 | ✅ 有意 placeholder |

---

## 5. 文档过时内容

### 5.1 需要删除或更新的内容

| 文档 | 过时内容 | 建议 |
|------|----------|------|
| `docs/overview/02-project-status.md` | Flyway 迁移 17 个 | 更新为 3 个逻辑版本 (V1-V3) |
| `docs/overview/02-project-status.md` | 模块数 30 | 更新为 34 |
| `docs/overview/02-project-status.md` | Java 源文件 ~350+ | 更新为实际数量 |
| `docs/overview/02-project-status.md` | 后端测试 54+ | 更新为 366 |
| `docs/overview/02-project-status.md` | 数据库表 28+ | 更新为 70+ |
| `docs/overview/02-project-status.md` | Render 提供商 6 个 | 更新为 13+ |
| `docs/overview/02-project-status.md` | `NoopKillBillBillingEngine` | 删除（不存在） |
| `docs/overview/02-project-status.md` | `NoopMedusaCatalogAdapter` | 删除（不存在） |
| `docs/overview/02-project-status.md` | `NoopFederatedQueryGateway` | 删除（不存在） |
| `docs/deployment/01-deployment.md` | Flyway 迁移 V1-V17 | 更新为 V1-V3 |
| `docs/deployment/01-deployment.md` | 服务列表仅 db+app | 补充 render-worker/sandbox-worker |
| `docs/deployment/02-deployment-checklist.md` | "No Authentication" 作为 blocker | 代码已有 OAuth2+JWT 配置，更新为 "需要配置外部 OIDC Provider" |
| `docs/architecture/08-deployment-architecture.md` | 部署拓扑无 render-worker/sandbox-worker/egress-proxy | 补充完整 4 组件拓扑 |
| `docs/architecture/08-deployment-architecture.md` | 健康检查仅 actuator | 补充自定义 `/healthz`, `/readyz`, `/metrics/summary` |
| `docs/README.md` | 统计数据（模块 30、测试 54+ 等） | 更新为准确数字 |
| `docs/README.md` | "No Authentication" 阻断 | 更新为准确描述 |
| `platform/docs/k8s-deployment.md` | 镜像名 `platform-api` / `platform-render-worker` | 与 gitops 一致 ✅ |
| `platform/docs/k8s-deployment.md` | 仅提及 api + render-worker | 补充 sandbox-worker |

### 5.2 归档文档应移入 archive

大量 `docs/archive/` 中的文档是历史产物，已在 `docs/` 新版中有对应版本。建议确认归档策略。

---

## 6. 文档应保留的 Placeholder

以下 `example.com` 引用是**有意的设计 placeholder**，不应误认为项目旧名：

| 位置 | 内容 | 说明 |
|------|------|------|
| `allowed-domains.txt` | `.oidc.example.com`, `.s3.example.com`, `.alerts.example.com`, `.litellm.example.com` | 出口代理域名白名单模板 |
| K8s Ingress | `api.example.com`, `staging.api.example.com` | 入口主机名模板 |
| Stripe 回调 URL | `https://example.com/billing/success`, `https://example.com/billing/cancel` | 支付回调 URL 模板 |
| ArgoCD repoURL | `https://github.com/your-org/platform.git` | Git 仓库 URL 模板 |
| OpenTelemetry | `http://localhost:4318` | 本地 OTLP 收集器地址 |
| OpenReplay | `https://openrelay.yourdomain.com` | 会话回放服务地址 |
| `.env.oidc.example` | `auth.example.com` | OIDC 提供者地址模板 |

---

## 7. 需要改名的 Example 残留

### must_replace（生产部署前必须替换）

| 位置 | 当前值 | 建议 |
|------|--------|------|
| `build.gradle.kts:11` | `group = "com.example.platform"` | 替换为实际组织 group |
| Java 包名 | `com.example.platform.*` | 替换为实际组织包名（影响所有 import） |
| `gitops/argocd/*.yaml` repoURL | `https://github.com/your-org/platform.git` | 替换为实际 Git 仓库 URL |

### keep_as_placeholder（保留为模板）

| 位置 | 说明 |
|------|------|
| `allowed-domains.txt` 中的 `example.com` | 部署时替换 |
| K8s Ingress 中的 `example.com` | 部署时替换 |
| Stripe URL 中的 `example.com` | 部署时替换 |
| `.env.oidc.example` 中的 `example.com` | 开发模板 |

### review（需要审查）

| 位置 | 说明 |
|------|------|
| `project-naming-audit.md` | 已有命名审计文档，需确认更新状态 |
| `docs/archive/` 中的旧文档 | 可能包含旧的 example 引用 |
| `platform/docs/` 中的旧文档 | 可能包含旧的 example 引用 |
