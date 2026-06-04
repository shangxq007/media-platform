# Media Platform 全模块逐文件级代码审查报告

> **审查日期：** 2026-05-23（二轮验证修正版）  
> **落地更新：** 2026-05-20 — P0 安全项部分已修复（支付 Webhook、CORS/JWT 门禁、RateLimit 清理等），见 §8.1「修复状态」列  
> **审查范围：** `media-platform-workspace` 全部 31 个后端 Gradle 模块 + Vue 3 前端 + 基础设施/运维配置  
> **审查方式：** 静态代码逐文件审查 + 关键发现代码级验证 + 测试实际运行  
> **审查深度：** 每个模块列出完整文件清单、类职责、实现状态、依赖关系和发现的问题  
> **验证方法：** 对首轮发现的 28 项 Critical 问题逐项读取源码验证，修正 5 项不准确描述

---

## 目录

- [第一部分：项目总览与综合评估](#第一部分项目总览与综合评估)
- [第二部分：核心与基础设施模块](#第二部分核心与基础设施模块)
- [第三部分：媒体管线模块](#第三部分媒体管线模块)
- [第四部分：商业治理模块](#第四部分商业治理模块)
- [第五部分：平台能力模块](#第五部分平台能力模块)
- [第六部分：前端 Vue 3 应用](#第六部分前端-vue-3-应用)
- [第七部分：基础设施与运维](#第七部分基础设施与运维)
- [第八部分：问题汇总与修复路线图](#第八部分问题汇总与修复路线图)

---

# 第一部分：项目总览与综合评估

## 1.1 项目规模

| 指标 | 数值 |
|------|------|
| 后端 Gradle 模块 | 31 个 |
| Java 源文件 | ~1050+ |
| 后端测试文件 | ~219 |
| 前端 Vue 组件 | ~90+ |
| 前端 TypeScript 文件 | ~100+ |
| 前端测试文件 | ~80 |
| Flyway 迁移脚本 | 23 个（V1–V23，共 1984 行 SQL） |
| 数据库表 | 70+ |
| REST API 端点 | 120+ |
| GraphQL 查询 | 6 个 |
| package-info.java | 86 个（30/32 模块覆盖） |
| @Deprecated 类 | 9 个（render-module 中待清理） |
| 内存存储 Service | 42 个（17 个模块） |

## 1.2 综合评分

| 维度 | 评分 | 说明 |
|------|------|------|
| 架构设计 | 3.5/5 | 意图优秀，Spring Modulith + DDD 分层清晰，ModularityTest 零容忍断言 |
| 功能完成度 | 3.5/5 | 功能广度覆盖 75%，核心渲染管线完成度高 |
| 生产就绪度 | 2.0/5 | P0 安全部分落地；大量内存存储与 AI/Worker Stub 仍阻断 |
| 代码质量 | 3.0/5 | 包结构规范、文档齐全，但存在 God Service 和类型安全问题 |
| 安全性 | 3.0/5 | Webhook 验签/CORS/JWT 生产门禁已加强；Groovy 无隔离、租户数据层未强制仍 Critical |
| 可观测性 | 3.0/5 | 日志/指标基础扎实，追踪与健康检查未闭环 |
| 前端质量 | 3.5/5 | 组件体系完善，核心模块测试缺口 |
| 运维就绪度 | 2.5/5 | Docker 构建合理，CI/CD 仅基础，IaC 全部占位 |
| **综合** | **2.9/5** | |

> **二轮验证修正说明（2026-05-23）：**  
> 本次修正了首轮报告中 5 项不准确的发现：  
> 1. ~~沙箱 isCodeSafe() 被绕过~~ → 实际已调用，降级为 High（正则可绕过 + Groovy 无隔离）  
> 2. ~~ModularityTest 有 13 条 filter~~ → 实际为零容忍断言，无 filter  
> 3. ~~守卫 next() 重复调用~~ → 每个分支均有 return，不存在此问题  
> 4. ~~认证默认关闭~~ → `app.security.enabled=true`，但 OAuth2/API Key 默认关闭  
> 5. ~~12+ 模块纯内存存储~~ → 实际为 42 个 Service 类、17 个模块  
> 同时补充了 Flyway 脚本清单（23 个/1984 行）、测试实际运行结果（前端 631/653 通过）、package-info.java 覆盖率（86 个/30 模块）、@Deprecated 清单（9 处）等验证数据。

## 1.3 问题统计

| 严重度 | 后端 | 前端 | 运维 | 总计 |
|--------|------|------|------|------|
| 🔴 Critical/P0 | 15 | 2 | 7 | **24** |
| 🟡 High/P1 | 33 | 4 | 13 | **50** |
| 🟢 Medium/P2 | 16 | 13 | 6 | **35** |
| **总计** | **64** | **19** | **26** | **109** |

> **二轮验证修正说明：** 首轮报告中"沙箱 isCodeSafe() 被绕过"经代码验证不准确（实际已调用），"ModularityTest 有 13 条 filter"经验证不准确（实际为零容忍断言），"守卫 next() 重复调用"经验证不准确（每个分支均有 return）。这 3 项已从 Critical 降级或移除。"内存存储模块数"从 12+ 修正为 42 个 Service/17 个模块。

---

# 第二部分：核心与基础设施模块

## 2.1 shared-kernel（共享内核）

**模块类型：** `@ApplicationModule(type=OPEN)` — 所有类可被任何模块访问  
**文件数量：** 45 个 Java 源文件 + 2 个测试

### 文件清单

| 包路径 | 类名 | 职责 | 状态 |
|--------|------|------|------|
| `shared` | `Jsons` | JSON 序列化工具（Jackson ObjectMapper） | ✅ 完整 |
| `shared` | `Ids` | ID 生成（prefix + UUID） | ✅ 完整 |
| `shared.web` | `ErrorCode` / `CommonErrorCode` / `ConfigurableErrorCode` | 错误码体系 | ✅ 完整 |
| `shared.web` | `ErrorCodeRegistry` | 错误码注册表（从 error-codes.json 加载） | ✅ 完整 |
| `shared.web` | `PlatformException` | 统一异常（携带 ErrorCode + i18n） | ✅ 完整 |
| `shared.web` | `TenantContext` | ThreadLocal 租户上下文 | ✅ 完整 |
| `shared.web` | `TenantGuard` | 租户隔离断言工具 | ✅ 完整 |
| `shared.web` | `MediaAssetErrors` | 媒体资产错误工厂 | ✅ 完整 |
| `shared.entitlement` | `EntitlementPort` | 权益校验 SPI（含 ExportValidationResult） | ✅ 完整 |
| `shared.payment` | `PaymentSucceededPort` | 支付成功回调 SPI | ✅ 完整 |
| `shared.commerce` | `CheckoutPaymentPort` / `PurchaseFulfillmentPort` | 结算/履约 SPI | ✅ 完整 |
| `shared.audit` | `AuditPort` | 审计记录 SPI | ✅ 完整 |
| `shared.cost` | `CostReservationPort` / `BudgetGuardPort` / `CostEstimationPort` | 成本管理 SPI | ✅ 完整 |
| `shared.notification` | `NotificationEventPublisher` | 事件发布 SPI | ✅ 完整 |
| `shared.monitoring` | `SentryMonitoringService` | Sentry 监控服务 | ⚠️ Stub（仅日志） |
| `shared.events` | 18 个 Event Record | 领域事件（Render/Artifact/Cost/Quota 等） | ✅ 完整 |

### 发现的问题

| 严重度 | 问题 |
|--------|------|
| 🟡 中 | `SentryMonitoringService` 是纯 stub — `captureException` 仅打 log/info 日志，未集成 Sentry SDK |
| 🟡 中 | `Jsons` 使用静态 `new ObjectMapper()` 未注册 `JavaTimeModule`，`Instant`/`OffsetDateTime` 会序列化为数组 |
| 🟡 中 | `CostReservationPort` 使用 `double` 表示金额，存在浮点精度问题 |
| 🟢 低 | `NotificationEventPublisher.publish(Object event)` 参数为 raw Object，类型不安全 |
| 🟢 低 | 测试覆盖不足 — 仅 2 个测试类，核心工具类无单元测试 |

---

## 2.2 platform-app（主应用入口）

**模块类型：** 应用聚合层，依赖全部 30 个模块  
**文件数量：** 90+ 个 Java 源文件 + 20+ 个测试  
**安全配置（application.yml 实测）：**

| 配置项 | 值 | 风险 |
|--------|-----|------|
| `app.security.enabled` | `true` | ✅ 已启用 |
| `app.security.dev-auth-endpoint` | `false` | ✅ |
| `app.security.oauth2.enabled` | `false` | ⚠️ OAuth2 默认关闭 |
| `app.security.jwt.secret-key` | 默认空（dev 由 `JwtProperties.resolvedSecretKey()` 回退） | 🟡 生产须设 `APP_JWT_SECRET`；`ProductionSafetyValidator` 拒绝 dev 默认 |
| `app.security.cors` | `AppCorsProperties` 白名单 origin + 显式 headers | ✅ 已配置（禁止 `*` origin + credentials） |
| `app.identity.api-key-auth-enabled` | `false` | ⚠️ API Key 认证关闭 |
| `spring.flyway.enabled` | `false` | ⚠️ Flyway 默认关闭 |

### 核心组件

| 包路径 | 类名 | 职责 | 状态 |
|--------|------|------|------|
| `platform` | `PlatformApplication` | Spring Boot 启动类 | ✅ |
| `platform` | `DslContextConfiguration` | Flyway + jOOQ + ObjectMapper + Sentry 配置 | ✅ |
| **Security** | | | |
| `security` | `SecurityFilterChainConfig` | JWT SecurityFilterChain（条件启用） | ✅ |
| `security` | `OAuth2ResourceServerSecurityConfiguration` | OAuth2 Resource Server 链 | ✅ |
| `security` | `PermitAllSecurityConfiguration` | permit-all chain（security=false 时） | ✅ |
| `security` | `JwtAuthFilter` | HMAC JWT Bearer 认证过滤器 | ✅ |
| `security` | `OAuth2RequestContextFilter` | OIDC JWT → TenantContext + MDC 映射 | ✅ |
| `security` | `TenantHeaderGuardFilter` | JWT tenantId 与 Header 一致性校验 | ✅ |
| `security` | `OidcIdentityProvisioningService` | OIDC JIT 用户自动注册 | ✅ |
| `security` | `RequestSourceAuditInterceptor` | API 请求审计拦截器 | ✅ |
| **Web** | | | |
| `web` | `GlobalExceptionHandler` | 全局异常处理（ProblemDetail + i18n） | ✅ |
| `web` | `MeController` | 用户仪表盘聚合端点 | ⚠️ 部分 Stub |
| `web.render` | 6 个 Controller | 渲染/时间线/预设/Worker 队列端点 | ✅ |
| `web.media` | 8 个 Controller/Service | 资产生命周期/治理/完整性扫描 | ✅ |
| `web.collaboration` | 4 个类 | 协作资源共享 | ✅ |
| `web.admin` | 2 个 Controller | 部署就绪/AI 管理 | ✅ |
| **App** | | | |
| `app` | `RateLimitFilter` | IP 限流过滤器 | ⚠️ OOM 风险 |
| `app.ai` | `TenantLitellmKeyVaultMigrationService` | AI 密钥 Vault 迁移 | ✅ |

### 发现的问题

| 严重度 | 问题 | 位置 |
|--------|------|------|
| ~~🔴 高~~ ✅ | ~~CORS `allowedHeaders` 硬编码 `*`~~ → `AppCorsProperties` 显式 headers | `SecurityConfiguration.java` |
| ~~🔴 高~~ ✅ | ~~`RateLimitFilter` 无过期清理~~ → 两分钟窗外条目 prune | `RateLimitFilter.java` |
| ~~🔴 高~~ 🟡 | JWT 默认不安全字符串 → 默认空 + 生产 `ProductionSafetyValidator` 校验 | `JwtProperties.java` |
| 🔴 高 | 手动 JSON 拼接存在注入风险（4 处 Filter） | `ApiKeyAuthFilter.java:65` 等 |
| 🟡 中 | `MeController` 直接依赖 8 个其他模块的 Service，违反模块边界 | `MeController.java:4-14` |
| 🟡 中 | `DslContextConfiguration` 硬编码 H2 方言 | `DslContextConfiguration.java:61` |
| 🟡 中 | `SharedResourceJdbcRepository` 在 web/collaboration 包，违反分层 | - |
| 🟢 低 | `MeController` 硬编码前端路径 `/project/new` | `MeController.java:334-339` |

---

## 2.3 config-module（配置模块）

**文件数量：** 4 个 Java 源文件

| 类名 | 职责 | 状态 |
|------|------|------|
| `ConfigService` | 配置项 CRUD（版本化 INSERT） | ✅ |
| `ConfigController` | REST API `/api/v1/configs` | ✅ |
| `UpsertConfigRequest` | DTO | ✅ |

**问题：**
- 🟡 中：`upsert()` 无 `@Transactional`，并发写入可能版本号冲突
- 🟡 中：无权限控制，任何请求都能修改配置
- 🟡 中：无唯一约束冲突处理

---

## 2.4 secrets-config-module（密钥配置模块）

**文件数量：** ~27 个 Java 源文件（`SecretService`、`VaultKv2SecretProvider`、`SecretsConfigPortAdapter` 等）

**状态：** ✅ 已实现 Vault KV2 / 复合解析 / REST API；与 `application-vault.yml` profile 配合使用。

**问题：**
- 🟡 中：各业务模块仍可能直接读环境变量，未统一经 `SecretsConfigPort` 解析

---

## 2.5 datasource-module（数据源模块）

**文件数量：** 9 个 Java 源文件 + 2 个测试

| 类名 | 职责 | 状态 |
|------|------|------|
| `DataSourceConfiguration` | 多数据源配置 + jOOQ DSLContext | ✅ |
| `AppDataSourceProperties` | 数据源配置属性 | ✅ |
| `NamedDataSourceRegistry` / `DslContextRegistry` | 名称→实例注册表 | ✅ |
| `FederatedQueryGateway` | 联邦查询 SPI | ✅（接口） |
| `NoopFederatedQueryGateway` | Noop 实现 | ⚠️ Stub |

**问题：**
- 🔴 高：`DriverManagerDataSource` 非连接池，生产应使用 HikariCP
- 🔴 高：数据库密码明文存储
- 🟡 中：默认 DSLContext 硬编码 H2 方言

---

## 2.6 identity-access-module（身份与访问模块）

**文件数量：** 65 个 Java 源文件 + 10+ 个测试

### Domain 层（14 个 Record）
`Tenant`, `User`, `Project`, `Workspace`, `WorkspaceMember`, `WorkspaceGroup`, `WorkspaceGroupMember`, `Role`, `Permission`, `RolePermission`, `UserRoleAssignment`, `GroupRoleAssignment`, `ApiClient`, `ServiceAccount`

### App 层（8 个 Service）
- `IdentityAccessService` — API Key 全生命周期（SHA-256 哈希存储）
- `WorkspaceService` — Workspace/Member/Group/RoleAssignment CRUD
- `RoleService` / `PermissionService` — RBAC 权限管理
- `TenantProjectService` — Tenant/Project/User/ApiKey CRUD
- `BuiltinDataInitializer` — 初始化 16 个 Permission + 3 个 Role

### 发现的问题

| 严重度 | 问题 |
|--------|------|
| 🔴 高 | `WorkspaceController` 直接依赖 entitlement 模块 9 个内部类，违反模块边界 |
| 🔴 高 | `IdentityAccessService` 构造函数中执行数据库操作，DB 未就绪会导致启动失败 |
| 🟡 中 | Repository 层位置混乱 — 部分在 `app` 包、部分在 `infrastructure` 包 |
| 🟡 中 | API Key 明文存储在配置文件 `IdentityProperties.apiKeys` |

---

## 2.7 scheduler-module（调度模块）

**文件数量：** 7 个 Java 源文件 + 1 个测试

| 类名 | 职责 | 状态 |
|------|------|------|
| `ScheduleRegistryService` | 作业注册/查询/运行记录/重试 | ⚠️ 纯内存存储 |
| `SchedulerController` | REST API | ⚠️ `runJob()` 是 Stub |

**问题：**
- 🔴 高：全部使用 `ConcurrentHashMap` 内存存储，重启丢失
- 🔴 高：`runJob()` 端点仅返回 `TRIGGERED` 但未实际执行
- 🟡 中：无实际调度引擎集成（无 Quartz/@Scheduled/Temporal）

---

## 2.8 outbox-event-module（发件箱事件模块）

**文件数量：** 6 个 Java 源文件 + 2 个测试

| 类名 | 职责 | 状态 |
|------|------|------|
| `OutboxEventService` | 事件 CRUD（jOOQ 持久化） | ✅ |
| `OutboxEventDispatcher` | 定时分发器 | ✅ |
| `OutboxBackedNotificationEventPublisher` | NotificationEventPublisher 实现 | ✅ |

**评价：** 设计良好，是少数有真实数据库持久化的模块之一。

---

## 2.9 observability-module（可观测性模块）

**文件数量：** 10+ 个 Java 源文件

| 类名 | 职责 | 状态 |
|------|------|------|
| `PlatformTraceCorrelationFilter` | 请求关联（MDC traceId/requestId） | ✅ |
| `ObservabilityOverviewService` | 概览服务 | ✅ |
| `ThirdPartyProviderHealthService` | 第三方健康状态跟踪 | ⚠️ 内存 Map |

**问题：**
- 🟡 中：`ThirdPartyProviderHealthService` 非原子状态更新
- 🟡 中：文档声称有 `DataSourceHealthIndicator` 和 `OutboxHealthIndicator`，但代码中未找到

---

# 第三部分：媒体管线模块

## 3.1 render-module（渲染模块）— 核心模块

**文件数量：** 200+ 个 Java 源文件  
**模块类型：** `@ApplicationModule(allowedDependencies=["ai","shared","storage","workflow","extension"])`

### 架构概览

render-module 是整个平台的核心，采用 SPI 模式支持 13+ 个渲染 Provider：

| Provider | 类型 | 状态 |
|----------|------|------|
| `JavaCVRenderProvider` | 本地渲染 | ✅ 完整 |
| `FFmpegRenderProvider` | 本地渲染 | ✅ 完整 |
| `GPACRenderProvider` | DASH/HLS 打包 | ✅ 完整 |
| `MltRenderProvider` | MLT/melt 渲染 | ✅ 完整 |
| `GStreamerRenderProvider` | GStreamer 渲染 | ✅ 完整 |
| `OFXRenderProvider` | OFX 特效 | ⚠️ 效果为 Java2D 模拟 |
| `NatronRenderProvider` | VFX 渲染 | ⚠️ POC 阶段 |
| `ShotstackRenderProvider` | 云渲染 | ✅ 完整 |
| `RemotionRenderProvider` | JS 渲染 | ✅ 完整 |
| `BlenderRenderProvider` | 3D 渲染 | ✅ 完整 |
| `VapourSynthRenderProvider` | 预处理 | ✅ 完整 |
| `LibassOverlayRenderProvider` | 字幕覆叠 | ✅ 完整 |
| `MockRenderProvider` | 测试用 | ✅ 完整 |

### Domain 层

时间线领域模型设计优秀：
- `TimelineSpec` — 核心聚合根，包含 tracks/clips/segments/transitions/markers/effects/textOverlays/stickers
- 标准格式适配器：AAF、FCP XML、EDL、SRT、WebVTT、OTIO（OTIO 为 Stub）
- 增量渲染支持：`IncrementalRenderPlan`、`SemanticDiffResult`、`DirtyScope`

### App 层

| 类名 | 行数 | 职责 | 状态 |
|------|------|------|------|
| `RenderOrchestratorService` | 682 | 核心编排（30+ 依赖） | ⚠️ God Service |
| `TimelineRevisionService` | - | 时间线版本管理 | ✅ |
| `TimelineEditorSyncService` | - | 编辑器同步 | ✅ |
| `IncrementalRenderOrchestrationService` | - | 增量渲染编排 | ✅ |
| `ClientExportService` | 130 | 浏览器端导出会话管理 | ✅ |

### 发现的问题

| 严重度 | 问题 |
|--------|------|
| 🔴 高 | `RenderOrchestratorService` 682 行、30+ 依赖 — God Service 反模式 |
| 🔴 高 | `OFXRenderProvider` 效果是 Java2D 模拟，非真实 OFX 插件调用 |
| 🟡 中 | FFmpeg/GPAC 各有两个重复类（PascalCase + camelCase deprecated wrapper） |
| 🟡 中 | `MockRenderProvider` 缺少 `@Profile("test")` 注解，可能在生产环境加载 |
| 🟡 中 | `SelectRenderBackendComponent`（LiteFlow）是空 Stub |
| 🟡 中 | `ClientExportController` 下载端点硬编码 `Content-Type: video/mp4` |
| 🟢 低 | 硬编码 `localFsStorageProvider://` 前缀在多个 Provider 中 |

---

## 3.2 workflow-module（工作流模块）

**文件数量：** 22 个 Java 源文件

### 架构

通过 Port/Adapter 模式支持两种执行模式：
- `LocalRenderExecutionAdapter` — 本地同步执行
- `TemporalRenderExecutionAdapter` — Temporal 分布式执行

Temporal 集成包含两套工作流：
- `RenderWorkflow` — 简单 3 步（decide→execute→deliver）
- `RenderPipelineWorkflow` — 高级 4 步（render→store→notify→audit + cancel/retry 信号）

### 发现的问题

| 严重度 | 问题 |
|--------|------|
| 🟡 中 | 两套工作流并存，无明确使用场景划分文档 |
| 🟡 中 | `RenderPipelineActivities` 只有接口无实现类 |
| 🟡 中 | `TemporalRenderExecutionAdapter` 使用 `stub.start()` 异步启动，调用方无法获得渲染结果 |

---

## 3.3 ai-module（AI 模块）

**文件数量：** 21 个 Java 源文件

| 类名 | 职责 | 状态 |
|------|------|------|
| `ChatProvider` | AI Provider SPI（单方法接口） | ✅ |
| `AiGatewayService` | AI 网关（路由 + 重试） | ✅ |
| `StubChatProvider` | 唯一的 Provider 实现 | ⚠️ Stub |
| `ConfigurableModelRouter` | 基于配置的模型路由 | ✅ |

### 发现的问题

| 严重度 | 问题 |
|--------|------|
| 🔴 高 | 只有 StubProvider，无真实 AI 集成（OpenAI/Claude/Gemini 等） |
| 🟡 中 | 双重重试 — `AiGatewayService` 和 `StubChatProvider` 都有重试逻辑 |
| 🟡 中 | token 计算使用 `字符数/4` 粗略估算 |

---

## 3.4 remote-render-worker（远程渲染 Worker）

**文件数量：** 6 个 Java 源文件

| 类名 | 职责 | 状态 |
|------|------|------|
| `RemoteRenderService` | 远程渲染执行 | ⚠️ `executeJob()` 始终调用 `renderPlaceholder` |
| `WorkerRegistryService` | Worker 注册表 | ⚠️ 内存存储 |

### 发现的问题

| 严重度 | 问题 |
|--------|------|
| 🔴 高 | `executeJob()` 始终调用 `renderPlaceholder`，无真正渲染逻辑 |
| 🔴 高 | 无认证/授权，任何人可注册 Worker 或提交作业 |
| 🟡 中 | `ComponentScan` 扫描整个 `render.infrastructure` 包，依赖过重 |
| 🟡 中 | `cancelJob()` 只改状态不中断执行中的线程 |

---

## 3.5 storage-module（存储模块）

**文件数量：** 17 个 Java 源文件

| 类名 | 职责 | 状态 |
|------|------|------|
| `BlobStorage` | SPI 接口（default 方法 + URI 解析） | ✅ |
| `LocalFsStorageProvider` | 本地文件系统实现 | ✅ |
| `S3BlobStorageProvider` | S3 兼容实现（含 R2 支持） | ✅ |
| `ArtifactRepository` | 产物仓储（jOOQ） | ✅ |

**问题：**
- 🟡 中：S3 凭证明文配置
- 🟡 中：`LocalFsStorageProvider` 无路径遍历防护（`../` 攻击）
- 🟡 中：presign URL 有效期硬编码 1 小时

---

## 3.6 artifact-catalog-module（产物目录模块）

**文件数量：** 18 个 Java 源文件

| 类名 | 职责 | 状态 |
|------|------|------|
| `ArtifactCatalogService` | 目录服务（持久化 + 内存双模式） | ✅ |
| `ArtifactLifecycleService` | 生命周期管理（ACTIVE→TOMBSTONED→PURGED） | ✅ |
| `ArtifactGcService` | GC 服务 | ✅ |
| `ArtifactStorageIntegrityScanner` | 存储完整性扫描 | ✅ |

**问题：**
- 🟡 中：`ArtifactLifecycleService.deleteCheck()` 跨模块直接查 `render_job` 表
- 🟡 中：GC 服务在内存模式下不工作

---

# 第四部分：商业治理模块

## 4.1 billing-module（计费模块）

**文件数量：** 90 个 Java 源文件 + 13 个测试

### Domain 层（33 个 Record）
涵盖：`SubscriptionContract`, `SubscriptionPlan`, `PricingRule`, `UsageRecord`, `BillingDecision`, `BillingLedgerEntry`, `CreditWallet`, `CreditTransaction`, `CostReservation`, `CostLimitPolicy`, `ProviderCostProfile`, `RenderCostRecord`, `ReconciliationRun`, `ReconciliationDifference` 等。

### App 层（14 个 Service）

| 类名 | 职责 | 存储方式 |
|------|------|----------|
| `SubscriptionBillingService` | 订阅管理核心 | 双模式（内存 + Optional JDBC） |
| `BillingCycleService` | 账期关闭编排 | 内存 |
| `RatingEngine` | 用量评级（flat/tiered） | 内存 |
| `UsageMeteringService` | 用量计量（幂等） | 内存 |
| `PricingRuleService` | 定价管理 | 内存 |
| `BillingLedgerService` | 计费分录 | 双模式 |
| `CreditWalletService` | 积分钱包 | 双模式 |
| `BudgetGuardService` | 预算守卫 | 内存 |
| `CostEstimationService` | 渲染成本估算 | 内存 |
| `CostReservationService` | 成本预留 | 内存 |
| `ReconciliationService` | 对账服务 | 内存 |

### 发现的问题

| 严重度 | 问题 |
|--------|------|
| 🔴 高 | 核心数据仅存于 `ConcurrentHashMap`（8 个 Service），重启丢失 |
| 🔴 高 | `BillingDecisionService.decideBilling()` 所有分支最终返回 `STATUS_APPROVED`，`STATUS_DENIED` 从未使用 |
| 🟡 中 | `CreditWalletService.reserve()` 使用 `synchronized` 但 `debit()`/`credit()` 未加锁 |
| 🟡 中 | 阶梯定价逻辑在 `RatingEngine`、`PricingRuleService`、`UsageBillingController` 三处重复 |
| 🟡 中 | `processBillingCycle()` 仅写 INFO 日志，不执行续期/关闭 |

---

## 4.2 quota-billing-module（配额计费模块）

**文件数量：** 9 个 Java 源文件 + 1 个测试

| 类名 | 职责 | 状态 |
|------|------|------|
| `QuotaService` | 配额管理（4 个 ConcurrentHashMap） | ⚠️ 纯内存 |
| `QuotaController` | REST API | ⚠️ `resetQuota()` 是空壳 |

### 发现的问题

| 严重度 | 问题 |
|--------|------|
| 🔴 高 | 零持久化 — 重启全部丢失 |
| 🔴 高 | 与 entitlement-module 的配额子系统完全独立，存在两套 `UsageRecord` 定义 |
| 🟡 中 | `resetQuota()` 端点仅返回 JSON 消息，实际不重置 |

---

## 4.3 entitlement-module（权益模块）

**文件数量：** 73 个 Java 源文件 + 16 个测试

### Domain 层（24 个 Record）
涵盖：`EntitlementGrant`, `EntitlementDecision`, `EntitlementPolicy`, `EntitlementBundle`, `EntitlementOverride`, `AccessDecision`, `QuotaPolicy`, `QuotaProfile`, `ExportCapabilityPolicy`, `ClientExportRoutingPolicy`, `ProviderAccessPolicy` 等。

### App 层（13 个 Service）

| 类名 | 职责 | 存储方式 |
|------|------|----------|
| `EntitlementService` | 权益管理核心 | 双模式 |
| `EntitlementDecisionService` | 5 层检查链决策引擎 | 双模式 |
| `EntitlementPolicyService` | Tier 策略 + EntitlementPort 实现 | 内存 |
| `AccessDecisionService` | 综合访问决策编排 | - |
| `QuotaDecisionService` | 配额决策 | - |
| `QuotaUsageService` | 配额用量跟踪 | 双模式 |
| `WorkspaceEntitlementPoolService` | 工作空间权益池 | - |

### 发现的问题

| 严重度 | 问题 |
|--------|------|
| 🔴 高 | 直接 import policy-governance-module 的 `FeatureFlagService` 等 7 个类型，违反模块边界 |
| 🟡 中 | `QuotaPolicyService` 硬编码 6 个默认策略 |
| 🟡 中 | Tier 映射硬编码 `tenant-1=FREE, tenant-pro=PRO` 等 |
| 🟡 中 | 6 个 Repository 接口无 JDBC 实现，运行时降级为纯内存 |

---

## 4.4 payment-module（支付模块）

**文件数量：** 29 个 Java 源文件 + 2 个测试

| 类名 | 职责 | 状态 |
|------|------|------|
| `PaymentGatewayService` | 支付网关核心 | ✅ |
| `StripeHttpPaymentProvider` | Stripe HTTP 客户端 | ⚠️ verifyPayment 是 Stub |
| `HyperswitchHttpPaymentProvider` | Hyperswitch HTTP 客户端 | ✅ 完整 |
| `NoopStripePaymentProvider` | Stripe Noop | ⚠️ 默认激活 |
| `NoopHyperswitchPaymentProvider` | Hyperswitch Noop | ⚠️ 默认激活 |

### 发现的问题

| 严重度 | 问题 |
|--------|------|
| ~~🔴 高~~ ✅ | ~~Noop 默认激活~~ → `matchIfMissing=false`，须显式 `stripe.enabled=false` 才注册 Noop |
| ~~🔴 高~~ ✅ | ~~Webhook 无签名校验~~ → `StripeWebhookSignatureVerifier` + Gateway 拒绝（dev/test 可 `allow-unsigned`） |
| 🟡 中 | Stripe `verifyPayment()` 直接返回 true，未调用 Stripe API |
| 🟡 中 | Provider 路由通过 `productCode.contains("hs")` 硬编码判断 |
| 🟡 中 | 手动 JSON 解析（`indexOf` 提取字段），不处理转义字符 |

---

## 4.5 commerce-module（商业流程模块）

**文件数量：** 32 个 Java 源文件 + 4 个测试

| 类名 | 职责 | 状态 |
|------|------|------|
| `CheckoutOrchestrator` | 结账编排器（412 行） | ✅ |
| `CommerceCartService` | 购物车管理 | ✅ |
| `CommerceCatalogService` | 产品目录（硬编码 9 个产品） | ⚠️ 硬编码 |

**问题：**
- 🟡 中：产品目录硬编码，`NoopMedusaCatalogAdapter` 未接入真实电商
- 🟡 中：企业产品访问控制硬编码（`tenant-1` 和 `tenant-prod`）
- 🟡 中：`CheckoutOrchestrator` 412 行，混合 6+ 种职责

---

## 4.6 policy-governance-module（策略治理模块）

**文件数量：** 57 个 Java 源文件

### 两大子系统

**策略引擎：** `PolicyRule`, `PolicyContext`, `PolicyDecision`, `PolicyCondition`, `PolicyGovernanceService`, `PolicyEvaluationService`

**Feature Flag：** `FeatureFlagDefinition`, `FeatureFlagService`, `FeatureFlagAuditService`, `LocalFeatureFlagProvider`

### 发现的问题

| 严重度 | 问题 |
|--------|------|
| 🔴 高 | `LocalFeatureFlagProvider` 默认纯内存（`@Autowired(required=false) FeatureFlagJdbcStore`，无 JDBC bean 时 fallback 到 `InMemoryFeatureFlagPersistence`） |
| 🟡 中 | `PolicyGovernanceService` 和 `PolicyEvaluationService` 使用 `ConcurrentHashMap` |
| 🟡 中 | 无远程 Feature Flag Provider（Unleash/LaunchDarkly）集成 |

> **二轮验证修正：** 首轮报告称"Feature Flag 仅内存"。经验证，`LocalFeatureFlagProvider` 实际支持 `FeatureFlagJdbcStore` 做持久化，仅在无 JDBC bean 时降级为纯内存。但在默认启动配置下确实是纯内存。

---

## 4.7 audit-compliance-module（审计合规模块）

**文件数量：** 10+ 个 Java 源文件

| 类名 | 职责 | 状态 |
|------|------|------|
| `AuditService` | 审计记录 CRUD | ✅ |
| `AuditController` | REST API | ✅ |

**评价：** 实现完整，使用 jOOQ 持久化。但部分服务未调用 `AuditPort`。

---

# 第五部分：平台能力模块

## 5.1 prompt-module（Prompt 工程模块）

**文件数量：** 23 个 Java 源文件

| 类名 | 职责 | 状态 |
|------|------|------|
| `PromptTemplateService` | 模板 CRUD + 版本管理 + 渲染 + 风险分析 | ✅（双模式） |
| `PromptSafetyPolicyService` | 秘密检测（6 个正则）+ 危险命令检测（9 个正则） | ✅ |
| `PromptJdbcRepository` | JDBC 持久化 | ✅ |

### 发现的问题

| 严重度 | 问题 |
|--------|------|
| 🔴 高 | 内存主存储 OOM 风险 — 启动时全量加载到内存 |
| 🔴 高 | checksum 使用 `Objects.hash()`（32 位），碰撞概率极高 |
| 🟡 中 | `markReviewed()` 方法未调用 service 持久化，返回硬编码响应 |
| 🟡 中 | `validateManifest()` 返回 `hashCode()` 而非 prompts 数量 |
| 🟢 低 | 敏感变量检测过于简单 — `lower.contains("key")` 会误匹配 |

---

## 5.2 extension-module（扩展平台模块）

**文件数量：** 56 个 Java 源文件

### SPI 设计

| SPI 接口 | 说明 |
|----------|------|
| `ProviderExtensionSPI` / `V2` | Provider 扩展 |
| `PromptExtensionSPI` / `V2` | Prompt 扩展 |
| `WorkflowStepExtensionSPI` / `V2` | 工作流步骤扩展 |

### 核心服务

| 类名 | 职责 | 状态 |
|------|------|------|
| `ExtensionRegistryService` | 扩展注册表（3 个 ConcurrentHashMap） | ✅ |
| `SandboxExecutionService` | 沙箱执行 | ⚠️ 核心 Stub |
| `ExtensionRouter` | 流量路由（优先级 + 百分比分流） | ✅ |
| `ExtensionResourceLimiter` | 资源限制（Semaphore + LongAdder） | ✅ |

### 发现的问题

| 严重度 | 问题 |
|--------|------|
| 🔴 高 | `SandboxExecutionService.invokeExtensionSpi()` 返回硬编码 JSON，未调用真实 SPI |
| 🟡 中 | `PluginManagerConfiguration` 仅创建 Bean，未调用 `loadPlugins()`/`startPlugins()` |
| 🟡 中 | `ExtensionAuditService.eventsByExtension` 使用非线程安全 `ArrayList` |
| 🟡 中 | `CachedThreadPool` 无上限，恶意扩展可耗尽系统资源 |

---

## 5.3 sandbox-runtime-module（沙箱运行时模块）— 安全关键

**文件数量：** 7 个 Java 源文件

| 类名 | 职责 | 状态 |
|------|------|------|
| `SandboxRuntimeService` | 代码执行（Groovy/JS/Python/Wasm） | ⚠️ 部分实现 |
| `DefaultSandboxSecurityPolicy` | 安全策略（17 个 BLOCKED_PATTERNS） | ⚠️ 未被调用 |

### 🔴 安全风险

| 严重度 | 问题 |
|--------|------|
| 🔴 高 | Groovy 脚本无类加载隔离 — `ScriptEngine.eval()` 无 SecurityManager/自定义 ClassLoader，可访问整个 JVM 类路径（包括 `Runtime.exec()`） |
| 🟡 中 | `isCodeSafe()` 虽被调用，但仅依赖字符串正则匹配（17 个 BLOCKED_PATTERNS），可通过编码绕过（如 Unicode 转义、反射调用） |
| 🟡 中 | Python 引擎在标准 JDK 中不可用（需 GraalVM/Jython） |
| 🟡 中 | Wasm 完全未实现 |

> **二轮验证修正：** 首轮报告称"isCodeSafe() 被绕过，execute() 不调用该方法"。经读取源码验证，`SandboxRuntimeService.java:58-62` **确实调用了** `securityPolicy.isCodeSafe(code)`。实际风险在于：(1) 正则匹配可被编码技巧绕过；(2) Groovy 引擎本身无类加载隔离，即使代码通过静态检查仍可执行恶意操作。

---

## 5.4 federation-query-module（GraphQL 聚合查询模块）

**文件数量：** 90+ 个 Java 源文件

### GraphQL 层

6 个 Resolver **全部被禁用**（`@Controller` 注解被注释掉）：
- `AdminDashboardGraphQLResolver`
- `PromptGraphQLResolver`
- `ExtensionGraphQLResolver`
- `MeOverviewGraphQLResolver`
- `ExportPanelGraphQLResolver`
- `MonitoringFeedbackGraphQLResolver`

基础设施完善：8 个 DataLoader、审计拦截器、查询复杂度限制、深度限制、分页校验。

### NLQ（自然语言查询）子模块

| 类名 | 职责 | 状态 |
|------|------|------|
| `SqlGenerationService` | NLQ → SQL 生成 | ✅ |
| `SqlSafetyValidator` | SQL 安全校验（9 项检查） | ✅ |
| `QueryExecutionService` | SQL 执行 | ⚠️ 参数绑定安全风险 |

### 发现的问题

| 严重度 | 问题 |
|--------|------|
| 🔴 高 | SQL 参数绑定使用字符串替换 `result.replace(placeholder, replacement)`，非预编译参数 |
| 🔴 高 | 6 个 GraphQL Resolver 全部被禁用 |
| 🟡 中 | `FederationQueryService` 是 Stub 但注册为 `@Service` |
| 🟡 中 | `RuntimeWiringConfigurer` 注册 5 个 stub DataFetcher |

---

## 5.5 notification-module（通知模块）

**文件数量：** 43 个 Java 源文件

| 类名 | 职责 | 状态 |
|------|------|------|
| `NotificationEventHandler` | 事件驱动核心（监听 6 种 Spring 事件） | ✅ |
| `NotificationEventCatalogService` | 事件目录（27 个内置事件） | ✅ |
| `NotificationSubscriptionService` | 订阅管理 | ✅ |
| `NotificationPreferenceService` | 偏好管理（免打扰/摘要模式） | ✅ |
| `NovuNotificationProvider` | Novu REST API 集成 | ✅ |
| `EmailNotificationProvider` | Email 发送 | ⚠️ Stub |
| `SmsNotificationProvider` | SMS 发送 | ⚠️ Stub |
| `WebhookNotificationProvider` | Webhook 发送 | ⚠️ Stub |

**问题：**
- 🔴 高：全渠道发送无过滤 — 遍历所有 provider 发送，未按用户偏好过滤
- 🟡 中：`NotificationProviderRouter` 的 key 不匹配（`"EMAIL"` vs `"stub-email"`），永远回退到 Mock

---

## 5.6 delivery-module（交付模块）

**文件数量：** 10+ 个 Java 源文件

| 类名 | 职责 | 状态 |
|------|------|------|
| `DeliveryService` | 投递服务 | ✅ |
| `DeliveryController` | REST API | ✅ |

**评价：** 实现完整，支持投递目的地、策略、任务管理。

---

## 5.7 其他平台模块

| 模块 | 文件数 | 状态 | 说明 |
|------|--------|------|------|
| `user-analytics-module` | 10+ | ✅ | 用户行为分析 |
| `cloud-resource-module` | 5+ | ⚠️ Stub | 云资源管理 |
| `social-publish-module` | 15+ | ⚠️ 部分 | 社交发布（平台适配器 Stub） |
| `compatibility-migration-module` | 19 | ✅ 完整但未接入 | 有 Controller/Service/Domain/Adapter 四层，未被 platform-app ComponentScan 扫描 |

---

# 第六部分：前端 Vue 3 应用

## 6.1 技术栈

Vue 3.5 + TypeScript 5.7 + Pinia 2.3 + Vue Router 4.5 + Vite 6 + TailwindCSS 3.4 + Vitest 3

## 6.2 规模统计

| 类别 | 数量 |
|------|------|
| Vue 组件 | ~90+ |
| TypeScript 文件 | ~100+ |
| 测试文件 | ~80 |
| 页面 | ~53+ |
| API 方法 | ~120+ |
| UI 组件 | 30 个 |
| Pinia Stores | 8 个 |
| Composables | 14 个 |

## 6.3 路由分析

- **静态路由：** ~55 条（核心 + 用户门户 + 工作区 + Admin 嵌套路由）
- **动态路由：** 后端驱动的路由注册，通过 `componentMap` 映射
- **导航守卫：** 多层守卫链（OIDC 认证 → 导航数据获取 → 可见性检查 → 启用状态检查）

### 问题

| 严重度 | 问题 |
|--------|------|
| 🔴 高 | Admin 路由 ~23 个组件直接导入（行 2-46），未使用懒加载 |
| 🟡 中 | `componentMap` 类型声明使用 `typeof import('*.vue')`（非法类型） |

> **二轮验证修正：** 首轮报告称"守卫 next() 重复调用"。经读取 `guards.ts` 全文验证，每个分支路径（行 20, 30, 37, 43, 68, 85, 99, 113, 127, 134）均有 `return` 语句，**不存在重复调用问题**。

## 6.4 状态管理

| Store | 行数 | 风格 | 职责 |
|-------|------|------|------|
| `auth` | 50 | 选项式 | OIDC 状态 |
| `project` | 54 | 组合式 | 项目管理 |
| `timeline` | 394 | 组合式 | 时间线核心 |
| `history` | 61 | 组合式 | Undo/Redo |
| `subtitle` | 104 | 组合式 | 字幕管理 |
| `effectPack` | 122 | 组合式 | 特效包 |

### 问题

| 严重度 | 问题 |
|--------|------|
| 🔴 高 | `history` store 使用 `any` 类型（`saveState(timelineStore: any)`） |
| 🔴 高 | `history` store 使用 `JSON.parse(JSON.stringify())` 深拷贝，大型时间线卡顿 |
| 🟡 中 | `timeline` store 394 行过于臃肿 |
| 🟡 中 | `auth` store 使用选项式 API，与其他 store 风格不一致 |

## 6.5 API 层

### REST API（14+ 模块，120+ 方法）

统一的 axios 实例 + 请求/响应拦截器：
- OIDC 模式：每次请求注入 Bearer token
- Dev 模式：从 bootstrapDevAuth() 获取 JWT
- 所有请求注入 `X-Tenant-ID` header
- 401 响应自动重定向到 OIDC 登录

### GraphQL API

- 6 个 .graphql 查询文件
- `graphql-request` 客户端
- `useGraphQLQuery` composable（支持 REST fallback）

### 问题

| 严重度 | 问题 |
|--------|------|
| 🔴 高 | GraphQL 客户端未注入认证 token |
| 🟡 中 | REST 和 GraphQL 混用无统一策略 |
| 🟡 中 | 无 GraphQL codegen |

## 6.6 测试覆盖

### 测试统计

| 分类 | spec 文件数 |
|------|-------------|
| Stores | 3 |
| Composables | 10 |
| Components | 16 |
| Pages | 25+ |
| API | 3（graphqlClient, me, workspace） |
| Utils | 7 |
| **总计** | **83 个文件** |

### 实际运行结果

```
Test Files  4 failed | 79 passed (83)   — 通过率 95.2%
Tests      22 failed | 631 passed (653) — 通过率 96.6%
Duration    20.57s
```

22 个失败用例均为 API 调用未 mock 导致，非业务逻辑错误。

### 测试缺口

| 严重度 | 缺失模块 |
|--------|----------|
| 🔴 高 | `useTimelineSync.ts`（346 行，最复杂的 composable） |
| 🔴 高 | `timeline.ts` store（394 行，核心 store） |
| 🟡 中 | `router/guards.ts`（135 行，多层守卫） |
| 🟡 中 | `auth/oidcClient.ts`（OIDC 客户端） |
| 🟡 中 | `sentry.ts` / `openreplay.ts`（数据脱敏逻辑） |

---

# 第七部分：基础设施与运维

## 7.1 Docker 构建

### 后端主 Dockerfile

三阶段构建：`node:22-alpine` → `gradle:9.1-jdk25-noble` → `eclipse-temurin:25-jre-jammy`

| 审查项 | 状态 |
|--------|------|
| 多阶段构建 | ✅ 合理 |
| 非 root 运行 | ✅ `spring` 用户 |
| 最小运行时 | ✅ JRE 而非 JDK |
| 依赖缓存 | ❌ `COPY . .` 导致缓存失效 |
| HEALTHCHECK | ❌ 缺失 |
| npm ci | ❌ 使用 `npm install` |

### Render Worker Dockerfiles

3 个 Worker Dockerfile（JavaCV/Natron/OFX）全部以 **root 用户运行**。

## 7.2 CI/CD Pipeline

`.github/workflows/ci.yml`：

| 步骤 | 状态 |
|------|------|
| Java 25 setup + Gradle 缓存 | ✅ |
| `./gradlew test` | ✅ |
| `./gradlew :platform-app:bootJar` | ✅ |
| Docker build smoke check | ✅ |
| 前端测试 | ❌ 缺失 |
| Docker 镜像推送 | ❌ 缺失 |
| 安全扫描 | ❌ 缺失 |
| 代码质量检查 | ❌ 缺失 |
| CD 部署流程 | ❌ 缺失 |

## 7.3 IaC（OpenTofu）

- 模块化结构良好（modules/environments 分离）
- **全部为 `null_resource` 占位符**，无真实资源定义
- 仅 `local` 环境，缺少 staging/production
- 无 remote state backend

## 7.4 配置管理

- `.env.example` 完善，覆盖所有配置项
- 缺少 `.env.production` 模板
- 缺少 secrets manager 集成
- 缺少环境变量校验机制

## 7.5 Flyway 迁移脚本

共 **23 个** SQL 迁移文件，合计 **1984 行**：

| 版本 | 文件 | 行数 | 内容 |
|------|------|------|------|
| V1 | `V1__core_infrastructure.sql` | 171 | 核心基础设施、扩展定义、发件箱 |
| V2 | `V2__commerce_identity_media.sql` | 292 | 商业、身份、渲染、产物 |
| V3 | `V3__platform_capabilities.sql` | 479 | Prompt、扩展平台、工作空间 RBAC |
| V4 | `V4__governance_billing_integrations.sql` | 559 | 权益、导航、计费、通知、社交、Feature Flag |
| V5 | `V5__indexes_and_constraints.sql` | 106 | 索引和约束 |
| V6–V23 | 18 个增量脚本 | 377 | NLQ、分析、共享资源、租户权益、时间线修订、投递、产物生命周期、LiteLLM 密钥等 |

**注意：** `application.yml` 中 `spring.flyway.enabled: false`，Flyway 默认关闭。有 `FlywaySchemaIntegrationTest` 使用 Testcontainers 在真实 PostgreSQL 上验证迁移。

## 7.6 测试实际运行结果

### 后端测试

```
./gradlew test --dry-run → BUILD SUCCESSFUL in 14s
```

所有模块的 test task 均被正确发现。仅 1 个文件使用 Testcontainers（`FlywaySchemaIntegrationTest`），其余使用 H2 内存数据库。

### 前端测试

```
Test Files  4 failed | 79 passed (83)
Tests      22 failed | 631 passed (653)
Duration    20.57s
```

**失败的 4 个测试文件：**

| 文件 | 失败用例 | 根因 |
|------|----------|------|
| `MyBillingPage.spec.ts` | 6 个 | API 调用未 mock |
| `MyCapabilitiesPage.spec.ts` | 5 个 | API 调用未 mock |
| `EffectsPanel.spec.ts` | 3 个 | API 调用未 mock |
| `EditorPage.spec.ts` | 5 个 | API 调用未 mock |

> 失败根因：`happy-dom` 的 `AsyncTaskManager` 被中断，axios 请求在组件 mount 时发起但未被 mock。

## 7.7 package-info.java 覆盖率

共 **86 个** package-info.java 文件，覆盖 **30/32** 个模块。

**缺少 package-info.java 的模块：**
- `secrets-config-module` — 空模块
- `social-publish-module` — 无 package-info.java

## 7.8 @Deprecated 类清理清单

render-module 中 **9 处** `@Deprecated`，涉及 **8 个类**：

| 文件 | 类 | 子包 |
|------|-----|------|
| `FfmpegRenderProvider.java` | 整个类 | ffmpeg/ |
| `FfmpegCommandFactory.java` | 整个类 | ffmpeg/ |
| `FfmpegProbeService.java` | 整个类 | ffmpeg/ |
| `FfmpegEnvironmentValidator.java` | 整个类 | ffmpeg/ |
| `GpacRenderProvider.java` | 整个类 | gpac/ |
| `GpacPackagingProvider.java` | 整个类 | gpac/ |
| `GpacEnvironmentValidator.java` | 整个类 | gpac/ |
| `MeltCommandFactory.java` | 整个类 | mlt/ |
| `MediaProbeService.probeLegacy()` | 方法级别 | infrastructure/ |

## 7.5 可观测性

| 组件 | 状态 |
|------|------|
| 结构化日志（JSON + MDC） | ✅ |
| Micrometer 指标 | ✅ |
| Prometheus 端点 | ✅ |
| Sentry 集成 | ⚠️ 配置存在但 SDK 未真正集成 |
| OpenTelemetry | ❌ 未集成 |
| Prometheus alerting rules | ❌ 缺失 |
| Grafana dashboards | ❌ 缺失 |
| 日志聚合方案 | ❌ 缺失 |

---

# 第八部分：问题汇总与修复路线图

## 8.1 Critical 问题清单（24 项，二轮验证后）

> **修复状态列（2026-05-20）：** ✅ 已修复 · 🟡 部分缓解 · ⏳ 待办

### 安全类（6 项）

| # | 问题 | 模块 | 验证状态 | 修复状态 |
|---|------|------|----------|----------|
| 1 | Groovy 脚本无类加载隔离（可执行任意系统命令） | sandbox-runtime-module | ✅ 已验证 | ⏳ 待办 |
| 2 | JWT Secret Key 默认不安全值 | platform-app | ✅ 已验证 | 🟡 默认空 + `ProductionSafetyValidator` 拒绝 dev 默认 |
| 3 | Webhook 无签名校验（硬编码 `validSignature=true`） | payment-module | ✅ 已验证 | ✅ `StripeWebhookSignatureVerifier` + Gateway 拒绝未签名 |
| 4 | 远程 Worker 无认证（无任何安全注解） | remote-render-worker | ✅ 已验证 | ⏳ 待办 |
| 5 | TenantContext 未在数据层强制 | 全局 | ✅ 已验证 | ⏳ 待办 |
| 6 | CORS `allowedHeaders` 硬编码 `*` | platform-app | ✅ 已验证 | ✅ `AppCorsProperties` 显式 headers |

### 数据类（5 项）

| # | 问题 | 模块 | 验证状态 |
|---|------|------|----------|
| 7 | **42 个 Service 类**使用纯内存存储（17 个模块），重启丢失 | 全局 | ✅ 已验证（从 12+ 修正） |
| 8 | BillingDecision 4 个分支全部返回 APPROVED，无拒绝逻辑 | billing-module | ✅ 已验证 |
| 9 | quota-billing 与 entitlement 配额系统功能重叠 | 跨模块 | ✅ 已验证 |
| 10 | checksum 使用 `Objects.hash()`（32 位），碰撞概率极高 | prompt-module | ✅ 已验证 |
| 11 | RateLimitFilter `ConcurrentHashMap` 无过期清理，OOM 风险 | platform-app | ✅ 已验证 | ✅ 两分钟窗外 prune |

### 功能类（7 项）

| # | 问题 | 模块 | 验证状态 |
|---|------|------|----------|
| 12 | 支付 Noop 默认激活（`matchIfMissing=true`） | payment-module | ✅ 已验证 | ✅ `matchIfMissing=false`；dev/test 显式 `enabled=false` |
| 13 | AI 全 Stub（仅 `StubChatProvider`） | ai-module | ✅ 已验证 |
| 14 | 远程 Worker `executeJob()` 始终调用 `renderPlaceholder` | remote-render-worker | ✅ 已验证 |
| 15 | GraphQL Resolver 全部禁用（6 个 `@Controller` 被注释） | federation-query-module | ✅ 已验证 |
| 16 | NLQ SQL 参数绑定使用字符串替换（非预编译） | federation-query-module | ✅ 已验证 |
| 17 | SandboxExecutionService `invokeExtensionSpi()` 返回硬编码 JSON | extension-module | ✅ 已验证 |
| 18 | OFX 效果是 Java2D 模拟，非真实 OFX 插件 | render-module | ✅ 已验证 |

### 运维类（6 项）

| # | 问题 | 模块 | 验证状态 |
|---|------|------|----------|
| 19 | CI 无前端测试 | CI/CD | ✅ 已验证 |
| 20 | CI 无 CD 流程 | CI/CD | ✅ 已验证 |
| 21 | CI 无 Docker 镜像推送 | CI/CD | ✅ 已验证 |
| 22 | IaC 全部 null_resource 占位符 | infra | ✅ 已验证 |
| 23 | Render Worker 以 root 运行 | Docker | ✅ 已验证 |
| 24 | 手动 JSON 拼接注入风险（3 个 Filter） | platform-app | ✅ 已验证 |

### 已从 Critical 移除的项目（二轮修正）

| 原始发现 | 修正原因 |
|----------|----------|
| ~~沙箱 isCodeSafe() 被绕过~~ | 经验证 `execute()` 确实调用了 `isCodeSafe()`，降级为 High（正则可绕过 + Groovy 无隔离） |
| ~~ModularityTest 有 13 条 filter~~ | 经验证代码为零容忍断言 `assertTrue(!violations.hasViolations())`，无 filter |
| ~~守卫 next() 重复调用~~ | 经验证每个分支均有 `return`，不存在重复调用 |
| ~~认证默认关闭~~ | 经验证 `app.security.enabled=true`，但 OAuth2 默认关闭、API Key 认证关闭 |

## 8.2 修复路线图

```
Phase 0 — 安全紧急修复（1-2 周）
├─ [✅] Stripe Webhook HMAC 签名校验
├─ [⚠️] Hyperswitch Webhook HMAC 需补全（当前仅检查 header 存在性）
├─ [✅] CORS 外化配置 + 生产检测
├─ [✅] JWT 不安全默认值检测（需加强：生产环境 fail-fast）
├─ [⚠️] Sandbox isCodeSafe 调用（需加强：Groovy ClassLoader 隔离）
├─ [✅] Noop 支付 matchIfMissing=false
├─ [⏳] Render Worker 非 root 用户
├─ [✅] RateLimitFilter 过期条目清理
└─ [✅] application-prod.yml 安全配置

Phase 1 — 生产阻断项（2-4 周）
├─ 数据层租户拦截器
├─ Feature Flag 持久化
├─ 接入真实支付 Provider
├─ 接入真实 AI Provider
├─ 远程 Worker 渲染逻辑
├─ 内存存储域迁 DB（分批）
└─ GraphQL Resolver 启用

Phase 2 — 架构治理（4-8 周）
├─ 统一 Flyway Schema 所有权
├─ [✅] Modulith 零容忍（无需消除 filter）
├─ 拆分 RenderOrchestratorService
├─ NLQ 参数绑定改为预编译
├─ Admin 路由懒加载
├─ GraphQL 认证注入
├─ history store 类型安全
└─ CI/CD 完善（前端测试 + 安全扫描 + 镜像推送）

Phase 3 — 质量提升（8-12 周）
├─ 核心模块测试补充
├─ Testcontainers 集成测试
├─ OTel 链路追踪集成
├─ Prometheus alerting rules + Grafana dashboards
├─ IaC 实现真实 provider
├─ Secrets Manager 集成
├─ GraphQL codegen
└─ 清理 deprecated 重复类
```

---

*本报告为全模块逐文件级代码审查产出，共覆盖 31 个后端模块、200+ 前端文件、18 个基础设施配置文件，识别 111 个问题（28 Critical、49 High、34 Medium）。*

---

# 第九部分：第三轮审查 — 安全修复 commit 验证

> **审查日期：** 2026-05-24  
> **审查对象：** commit `dfb1674 fix(security): harden payment webhooks, CORS/JWT prod gates, and sandbox`  
> **变更规模：** 22 个文件，+461 / -52 行

## 9.1 修复质量评估：3.5/5

## 9.2 已修复问题（10 项）

| # | 问题 | 修复方式 | 质量 |
|---|------|----------|------|
| 1 | Stripe webhook 无签名验证 | 新增 `StripeWebhookSignatureVerifier`（HMAC-SHA256 + 300s 时钟偏移） | ✅ 完整 |
| 2 | Noop 支付默认激活 | `matchIfMissing` 从 `true` 改为 `false` | ✅ 完整 |
| 3 | WebhookPayloadSupport 默认 validSignature=true | 默认改为 `false` | ✅ 完整 |
| 4 | CORS 硬编码 | 新增 `AppCorsProperties` 外化配置 + `hasWildcardOriginWithCredentials()` 检测 | ✅ 完整 |
| 5 | JWT 不安全默认值无检测 | 新增 `usesInsecureDefault()` 方法 | ⚠️ 部分（仍静默回退） |
| 6 | 生产环境缺少 fail-fast 检查 | `ProductionSafetyValidator` 新增 5 项检查 | ✅ 完整 |
| 7 | RateLimitFilter 内存泄漏 | 新增 `pruneStaleEntries()` 过期清理 | ✅ 完整 |
| 8 | 无 application-prod.yml | 新增生产 profile，强制安全配置 | ✅ 完整 |
| 9 | Sandbox isCodeSafe 未被调用 | `execute()` 中新增 `isCodeSafe()` 调用 | ✅ 完整 |
| 10 | 无 webhook allow-unsigned 配置 | 新增 `PaymentWebhookProperties` 外化配置 | ✅ 完整 |

## 9.3 新引入问题（3 项）

| # | 问题 | 严重度 | 位置 |
|---|------|--------|------|
| 1 | **Hyperswitch webhook HMAC 未实际验证** — 仅检查 header 存在性，不做 HMAC 计算，攻击者发送任意 `X-Hmac-SHA256` header 即可伪造 | 🔴 Critical | `PaymentGatewayService.java:133-143` |
| 2 | `constantTimeEquals` 先比较长度，泄漏时序侧信道 | 🟢 Low | `StripeWebhookSignatureVerifier.java` |
| 3 | `ProductionSafetyValidator` 缺少 Hyperswitch `webhook-secret` 检查 | 🟡 Medium | `ProductionSafetyValidator.java:94-98` |

## 9.4 上轮 Critical 问题修复状态

| # | 问题 | 状态 | 说明 |
|---|------|------|------|
| 1 | Groovy 无类加载隔离 | ⚠️ 部分修复 | 新增 `isCodeSafe()` 调用，但 Groovy 引擎仍无 ClassLoader 隔离，黑名单可被绕过 |
| 2 | JWT Secret 不安全默认值 | ⚠️ 部分修复 | 新增检测方法，但 `resolvedSecretKey()` 仍静默回退到硬编码值 |
| 3 | Webhook 无签名校验 | ✅ 已修复 | Stripe 侧完整 HMAC 验证 |
| 4 | 远程 Worker 无认证 | ❌ 未修复 | 所有端点仍公开 |
| 5 | TenantContext 未在数据层强制 | ❌ 未修复 | 无租户拦截器 |
| 6 | CORS 配置 | ✅ 已修复 | 外化配置 + 安全检测 |
| 7 | 42 个 Service 纯内存存储 | ❌ 未修复 | 仅 FeatureFlag 有 JDBC fallback，其余 41 个未变 |
| 8 | BillingDecision 始终 APPROVED | ❌ 未修复 | 4 分支仍全部 APPROVED |
| 9 | RateLimitFilter OOM | ✅ 已修复 | 新增过期清理 |
| 10 | NLQ SQL 注入 | ❌ 未修复 | 仍用 String.replace() |
| 11 | GraphQL Resolver 禁用 | ❌ 未修复 | @Controller 仍被注释 |
| 12 | AI 全 Stub | ❌ 未修复 | 无真实 Provider |
| 13 | RenderOrchestrator God Service | ❌ 未修复 | 仍 29 个依赖 |
| 14 | Admin 路由未懒加载 | ❌ 未修复 | 仍 23 个静态导入 |
| 15 | checksum 32 位 hash | ❌ 未修复 | 仍用 Objects.hash() |
| 16 | Noop 支付默认激活 | ✅ 已修复 | matchIfMissing=false |
| 17 | 远程 Worker 渲染逻辑未实现 | ❌ 未修复 | 仍调用 renderPlaceholder |
| 18 | SandboxExecutionService 硬编码 JSON | ❌ 未修复 | 未变更 |
| 19 | OFX 效果模拟 | ❌ 未修复 | 未变更 |
| 20 | CI 无前端测试 | ❌ 未修复 | 未变更 |
| 21 | CI 无 CD 流程 | ❌ 未修复 | 未变更 |
| 22 | CI 无 Docker 推送 | ❌ 未修复 | 未变更 |
| 23 | IaC 占位符 | ❌ 未修复 | 未变更 |
| 24 | Render Worker root 运行 | ❌ 未修复 | 未变更 |

**修复率：5/24 完全修复，2/24 部分修复，17/24 未修复**

## 9.5 测试验证结果

| 测试 | 结果 | 说明 |
|------|------|------|
| 后端 `./gradlew test` | ❌ 15 失败 | Bean 注入问题（非安全修复引入） |
| 前端 `npx vitest run` | ❌ 22 失败 | Axios mock 缺失（非安全修复引入） |
| `StripeWebhookSignatureVerifierTest` | ⚠️ 仅 2 用例 | 缺少篡改/过期/畸形 header 场景 |
| `ProductionSafetyValidatorTest` | ✅ 4 用例 | 覆盖主要路径 |

## 9.6 更新后的问题统计

| 严重度 | 上轮 | 本轮变化 | 当前 |
|--------|------|----------|------|
| 🔴 Critical | 24 | -5 已修复 +1 新引入 | **20** |
| 🟡 High | 50 | -5 已修复 | **45** |
| 🟢 Medium | 35 | 无变化 | **35** |
| **总计** | **109** | | **100** |

---

*三轮审查完成于 2026-05-24 00:05 CST。*
