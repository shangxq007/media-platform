# Media Platform 完整技术文档

## 文档信息

- **项目名称**: Media Platform
- **版本**: v1.0
- **更新日期**: 2026-06-08
- **维护团队**: Platform Engineering Team
- **文档状态**: 完成

---

## 目录

1. [系统概述](#1-系统概述)
2. [架构设计](#2-架构设计)
3. [核心功能模块](#3-核心功能模块)
4. [P4 Import/Export Pipeline](#4-p4-importexport-pipeline)
5. [技术选型](#5-技术选型)
6. [数据模型与数据库](#6-数据模型与数据库)
7. [API 设计](#7-api-设计)
8. [前端实现](#8-前端实现)
9. [运维部署架构](#9-运维部署架构)
10. [安全设计](#10-安全设计)
11. [测试与质量](#11-测试与质量)
12. [完成度与评估](#12-完成度与评估)
13. [维护与改进建议](#13-维护与改进建议)
14. [人工复核清单](#14-人工复核清单)
15. [注意事项与风险](#15-注意事项与风险)
16. [流程图与时序图](#16-流程图与时序图)

---

## 1. 系统概述

### 1.1 产品定位

**Media Platform** 是一个 AI 驱动的视频制作与**服务端渲染编排平台**，提供以下核心能力：

- **多租户项目管理**: 支持多用户、多租户的视频项目管理
- **时间线编辑**: 提供 Internal Timeline 1.0（渲染真源）和 Editor Schema 2.0（前端真源）
- **AI 辅助编辑**: 集成 AI 模型进行智能时间线编辑、变体生成
- **服务端渲染**: 多阶段渲染流水线，支持多种渲染 Provider（JavaCV、FFmpeg、MLT、GStreamer 等）
- **权益与计费**: 5 层 Tier 系统（FREE/PRO/TEAM/ENTERPRISE/EXPERIMENTAL），配额管理，预算控制
- **成片交付**: 支持多种格式输出（MP4、DASH、HLS 等），集成 SFTP/WebDAV/S3 交付
- **运维可观测性**: 健康检查、熔断器、SLA 指标、审计日志

### 1.2 系统边界

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          外部系统/用户                                        │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐    │
│  │ Web 浏览器│  │ 移动App  │  │ 第三方API│  │ AI 模型  │  │ 交付端点 │    │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘  └──────────┘    │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         API Gateway / Load Balancer                         │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
        ┌───────────────────────────┼───────────────────────────┐
        │                           │                           │
        ▼                           ▼                           ▼
┌──────────────┐           ┌──────────────┐           ┌──────────────┐
│   Frontend   │           │  Platform    │           │   Render     │
│   (Vue 3)    │◄─────────►│  App         │◄─────────►│   Worker     │
│   :8080      │           │  (Spring)    │           │   :8081      │
└──────────────┘           │  :8080       │           └──────────────┘
                           └──────────────┘
                                    │
        ┌───────────────────────────┼───────────────────────────┐
        │                           │                           │
        ▼                           ▼                           ▼
┌──────────────┐           ┌──────────────┐           ┌──────────────┐
│  PostgreSQL  │           │   Redis      │           │  BlobStorage │
│  (Database)  │           │   (Cache)    │           │  (S3/MinIO)  │
│  :5432       │           │   :6379      │           │              │
└──────────────┘           └──────────────┘           └──────────────┘
```

### 1.3 用户角色

| 角色 | 权限 | 典型操作 |
|------|------|----------|
| **普通用户** | 创建项目、编辑时间线、提交渲染 | 视频制作、导出 |
| **团队管理员** | 管理团队配额、成员权限 | 分配资源、审核内容 |
| **平台管理员** | 全系统配置、审计查看 | 系统运维、问题排查 |
| **超级管理员** | 全权限 | 租户管理、系统配置 |

---

## 2. 架构设计

### 2.1 整体架构

**架构模式**: 模块化单体 (Spring Modular Monolith)

**设计原则**:
- **运行时**: 单一 Spring Boot 进程（`platform-app`），非微服务拆分
- **边界**: Spring Modulith + Gradle 多模块；跨模块通过 **Port 接口**、**领域事件**、**Outbox** 通信
- **前端**: React 19 SPA，构建产物可嵌入 `platform-app` 静态资源或独立 Vite 开发服

```

Browser (React 19 + Zustand)
         │
         │ REST / GraphQL
         ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         platform-app (Spring Boot 4.0.4)                    │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                        API Layer                                     │   │
│  │  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐      │   │
│  │  │ Render  │ │ Project │ │ AI Edit │ │ Export  │ │  Admin  │      │   │
│  │  │   API   │ │   API   │ │   API   │ │   API   │ │   API   │      │   │
│  │  └─────────┘ └─────────┘ └─────────┘ └─────────┘ └─────────┘      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                    │                                        │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                      Application Layer                               │   │
│  │  ┌──────────────────┐ ┌──────────────────┐ ┌──────────────────┐   │   │
│  │  │ RenderOrchestrator│ │  AiTimelineEdit  │ │  ProjectExport   │   │   │
│  │  │    Service        │ │    Service       │ │    Service       │   │   │
│  │  └──────────────────┘ └──────────────────┘ └──────────────────┘   │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                    │                                        │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                        Domain Layer                                  │   │
│  │  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ │   │
│  │  │   render-   │ │  identity-  │ │  workflow-  │ │   ai-       │ │   │
│  │  │   module    │ │  access-    │ │  module     │ │   module    │ │   │
│  │  │             │ │  module     │ │             │ │             │ │   │
│  │  └─────────────┘ └─────────────┘ └─────────────┘ └─────────────┘ │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                    │                                        │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                     Infrastructure Layer                             │   │
│  │  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ │   │
│  │  │  storage-   │ │  audit-     │ │  billing-   │ │  notification│ │   │
│  │  │  module     │ │  compliance │ │  module     │ │  -module    │ │   │
│  │  │             │ │  -module    │ │             │ │             │ │   │
│  │  └─────────────┘ └─────────────┘ └─────────────┘ └─────────────┘ │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                    │                                        │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                       Shared Kernel                                  │   │
│  │  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ │   │
│  │  │  Port       │ │  Domain     │ │  Value      │ │  Base       │ │   │
│  │  │  Interfaces │ │  Events     │ │  Objects    │ │  Exceptions │ │   │
│  │  └─────────────┘ └─────────────┘ └─────────────┘ └─────────────┘ │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 模块划分

**31 个 Gradle 子模块**，分为 4 大类：

#### 2.2.1 核心基础设施模块

| 模块 | 职责 | 关键类 |
|------|------|--------|
| **shared-kernel** | 共享类型、事件、跨模块 SPI | `AuditPort`, `AssetDownloadUrlPort`, `ProjectAssetListingPort` |
| **platform-app** | 主应用入口，聚合所有模块 | `PlatformApplication`, `ModularityTest` |
| **config-module** | 版本化配置存储 | `ConfigController`, `ConfigService` |
| **secrets-config-module** | 密钥引用管理 | `SecretReferenceService` |
| **datasource-module** | 多数据源支持 | `NamedDataSourceRegistry` |
| **identity-access-module** | 身份访问管理（租户、用户、API Key） | `TenantController`, `UserController`, `ProjectController` |
| **scheduler-module** | 任务调度 | `CronScheduler`, `ManualTriggerScheduler` |
| **outbox-event-module** | 事务性 Outbox 模式 | `OutboxService`, `EventDispatcher` |

#### 2.2.2 媒体处理模块

| 模块 | 职责 | 关键类 |
|------|------|--------|
| **render-module** | 渲染编排、Provider 路由 | `RenderOrchestratorService`, `ProviderRouter` |
| **workflow-module** | Temporal + LiteFlow 工作流 | `RenderWorkflowImpl`, `RenderActivitiesImpl` |
| **ai-module** | AI 模型集成（ChatProvider SPI） | `ModelRouter`, `AiGatewayPort` |
| **remote-render-worker** | 分布式渲染 Worker | `WorkerRegistry`, `JobDistributor` |
| **artifact-catalog-module** | 工件元数据管理 | `ArtifactCatalogService` |
| **storage-module** | 多 Provider BlobStorage | `StorageCatalogPort`, `LocalBlobStorage` |

#### 2.2.3 业务逻辑模块

| 模块 | 职责 | 关键类 |
|------|------|--------|
| **billing-module** | 成本计量、预算管理、对账 | `MeteringService`, `BudgetGuardService` |
| **quota-billing-module** | 配额管理 | `QuotaDecisionService`, `QuotaBucket` |
| **entitlement-module** | 5 层 Tier 访问控制 | `EntitlementService`, `DecisionPriorityChain` |
| **payment-module** | 支付集成（Stripe/Hyperswitch） | `PaymentAttempt`, `CheckoutSession` |
| **commerce-module** | 商务领域（产品目录、订单） | `ProductCatalog`, `PurchaseOrder` |
| **policy-governance-module** | Feature Flag、ABAC 访问控制 | `FeatureFlagService`, `AccessDecisionService` |
| **audit-compliance-module** | 审计跟踪、合规检查 | `AuditService`, `AnomalyDetectionService` |
| **notification-module** | 多渠道通知 | `NotificationTemplate`, `DeliveryService` |
| **observability-module** | 健康检查、熔断器、SLA | `HealthCheckService`, `CircuitBreakerService` |

#### 2.2.4 平台服务模块

| 模块 | 职责 | 关键类 |
|------|------|--------|
| **prompt-module** | Prompt 模板管理 | `PromptTemplateService` |
| **extension-module** | PF4J 插件系统 | `PluginManager`, `ToolRegistry` |
| **sandbox-runtime-module** | 沙箱执行环境 | `GroovyExecutor`, `JavaScriptExecutor` |
| **federation-query-module** | GraphQL 聚合、NLQ 助手 | `GraphQLSchema`, `NlqAssistant` |
| **cloud-resource-module** | 云资源目录 | `CloudResourceDefinitionService` |
| **social-publish-module** | 社交媒体发布 | `SocialPublishService` |
| **delivery-module** | 交付管理 | `DeliveryService` |
| **user-analytics-module** | 用户分析 | `UserAnalyticsService` |

### 2.3 Port/Adapter 架构

**核心设计**: 所有跨模块依赖通过 Port 接口抽象，降低耦合度。

#### 2.3.1 核心 Port 接口

```java
// shared-kernel/src/main/java/com/example/platform/shared/audit/AuditPort.java
public interface AuditPort {
    void record(AuditEvent event);
}

// shared-kernel/src/main/java/com/example/platform/shared/asset/AssetDownloadUrlPort.java
public interface AssetDownloadUrlPort {
    String generateSignedUrl(String storageUri, Duration ttl);
}

// shared-kernel/src/main/java/com/example/platform/shared/export/ProjectAssetListingPort.java
public interface ProjectAssetListingPort {
    List<Artifact> listArtifactsByProject(String projectId);
}

// shared-kernel/src/main/java/com/example/platform/shared/cost/CostEstimationPort.java
public interface CostEstimationPort {
    CostEstimate estimate(String projectId);
}

// shared-kernel/src/main/java/com/example/platform/shared/media/MediaProbePort.java
public interface MediaProbePort {
    MediaProbeResult probe(String storageUri);
}

// shared-kernel/src/main/java/com/example/platform/shared/entitlement/EntitlementPort.java
public interface EntitlementPort {
    EntitlementResult checkAccess(String userId, String feature);
}
```

#### 2.3.2 Adapter 实现示例

```java
// storage-module: S3 存储适配器
@Component
public class S3AssetDownloadUrlPort implements AssetDownloadUrlPort {
    @Autowired
    private BlobStorage blobStorage;

    @Override
    public String generateSignedUrl(String storageUri, Duration ttl) {
        return blobStorage.presignStorageUri(storageUri, ttl);
    }
}

// identity-access-module: Artifact Catalog 适配器
@Component
public class ArtifactCatalogProjectAssetExportAdapter implements ProjectAssetListingPort {
    @Autowired
    private ArtifactCatalogService artifactCatalogService;

    @Override
    public List<Artifact> listArtifactsByProject(String projectId) {
        return artifactCatalogService.listArtifactsByProject(projectId);
    }
}

// audit-compliance-module: 审计适配器
@Component
public class AuditCompliancesAuditPort implements AuditPort {
    @Autowired
    private AuditLogRepository auditLogRepository;

    @Override
    public void record(AuditEvent event) {
        auditLogRepository.save(AuditLog.from(event));
    }
}
```

### 2.4 关键数据流

#### 2.4.1 渲染数据流（增量路径）

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           渲染数据流                                         │
└─────────────────────────────────────────────────────────────────────────────┘

ExportPanel.vue
      │
      │ POST /api/v1/render/incremental/submit
      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  RenderOrchestratorService                                                   │
│  1. 接收前端 Editor Schema 2.0 时间线                                        │
│  2. 规范化为 Internal Timeline 1.0（渲染真源）                                │
│  3. 生成 IncrementalRenderPlan（仅变更部分）                                  │
│  4. 通过 ProviderRouter 选择合适的渲染 Provider                               │
└─────────────────────────────────────────────────────────────────────────────┘
      │
      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  ProviderRouter                                                              │
│  - JavaCV（JNI-based，主 Provider）                                          │
│  - FFmpeg（通用转码）                                                        │
│  - MLT（XML 生成）                                                           │
│  - GStreamer（Pipeline 处理）                                                │
│  - OFX（效果、过渡、滤镜）                                                    │
└─────────────────────────────────────────────────────────────────────────────┘
      │
      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  Render Worker                                                               │
│  1. 执行渲染任务                                                             │
│  2. 生成工件（Artifact）                                                     │
│  3. 存储到 BlobStorage                                                       │
│  4. 更新 RenderJob 状态                                                      │
└─────────────────────────────────────────────────────────────────────────────┘
      │
      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  Delivery Service（可选）                                                    │
│  - SFTP/WebDAV/S3 交付                                                      │
│  - Vault 凭证管理                                                           │
└─────────────────────────────────────────────────────────────────────────────┘
```

#### 2.4.2 AI 时间线编辑数据流

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        AI 时间线编辑数据流                                    │
└─────────────────────────────────────────────────────────────────────────────┘

AI Edit Request
      │
      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  AiTimelineEditService                                                      │
│  1. 接收 AI 编辑指令                                                         │
│  2. 通过 ai-module 路由到对应 AI Provider                                    │
│  3. 获取 AI 响应（Patch 或全量 JSON）                                        │
│  4. 存储到 metadata.platform.ai.*                                           │
│  5. 生成 aiProposals（人工确认）                                            │
└─────────────────────────────────────────────────────────────────────────────┘
      │
      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  AI Model Router                                                             │
│  - LiteLLM（推荐 AI 网关）                                                   │
│  - StubChatProvider（占位实现）                                              │
│  - 未来: GLM-4/Claude/GPT 集成                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. 核心功能模块

### 3.1 项目管理

**模块**: `identity-access-module`

**功能**:
- 租户管理（Tenant CRUD）
- 用户管理（User CRUD）
- API Key 管理
- 项目管理（Project CRUD）

**API 端点**:
```
POST   /api/v1/identity/tenants
GET    /api/v1/identity/tenants/{tenantId}
PUT    /api/v1/identity/tenants/{tenantId}
DELETE /api/v1/identity/tenants/{tenantId}

POST   /api/v1/identity/tenants/{tenantId}/projects
GET    /api/v1/identity/tenants/{tenantId}/projects/{projectId}
PUT    /api/v1/identity/tenants/{tenantId}/projects/{projectId}
DELETE /api/v1/identity/tenants/{tenantId}/projects/{projectId}
```

### 3.2 时间线编辑

**模块**: `render-module`, `frontend/src/components/timeline`

**功能**:
- Internal Timeline 1.0（渲染真源）
- Editor Schema 2.0（前端真源）
- 时间线版本控制
- 增量渲染
- 字幕烧录

**前端组件**:
- `TimelineInternalPreviewPanel.vue`: 时间线预览
- `AiTimelineEditPanel.vue`: AI 编辑面板
- `IncrementalRenderPanel.vue`: 增量渲染面板

### 3.3 渲染系统

**模块**: `render-module`, `remote-render-worker`

**渲染 Provider**:
| Provider | 状态 | 说明 |
|----------|------|------|
| **JavaCV** | ✅ | 主 Provider（JNI-based） |
| **FFmpeg** | ✅ | 通用转码 |
| **MLT** | ✅ | XML 生成，melt 命令 |
| **GStreamer** | ✅ | Pipeline 处理 |
| **OFX** | ✅ | 效果、过渡、滤镜 |
| **GPAC** | ✅ | DASH/HLS 打包 |
| **Natron** | ⚠️ POC | OFX/节点合成 Worker |
| **Bento4** | 📋 P2 | MP4 分片、DASH、CENC/DRM |
| **Shotstack** | 📋 P3 | 可选云渲染 API |

**渲染生命周期**:
```
QUEUED → AI_PROCESSING → RENDERING → COMPLETED/FAILED
```

**API 端点**:
```
POST   /api/v1/render/jobs                      # 创建渲染任务
GET    /api/v1/render/jobs/{jobId}              # 查询渲染状态
POST   /api/v1/render/incremental/submit         # 增量渲染
GET    /api/v1/render/jobs/{jobId}/status       # 渲染进度
```

### 3.4 AI 辅助编辑

**模块**: `ai-module`

**功能**:
- AI 时间线编辑
- AI 变体生成
- AI 提案（需人工确认）

**AI Provider SPI**:
```java
public interface ChatProvider {
    CompletableFuture<String> chat(String prompt, ChatContext context);
}
```

**当前实现**:
- `StubChatProvider`: 占位实现，返回硬编码响应
- `SimpleModelRouter`: 简单模型路由

**未来集成**:
- GLM-4
- Claude
- GPT-4

### 3.5 权益与计费

**模块**: `entitlement-module`, `billing-module`, `quota-billing-module`

**5 层 Tier 系统**:
| Tier | 配额 | 功能 |
|------|------|------|
| **FREE** | 基础配额 | 基础功能 |
| **PRO** | 中等配额 | 高级功能 |
| **TEAM** | 大配额 | 团队协作 |
| **ENTERPRISE** | 无限配额 | 企业功能 |
| **EXPERIMENTAL** | 无限制 | 实验性功能 |

**配额管理**:
- 按租户+功能分配配额
- 可配置阈值事件
- 运行时配额检查

**计费功能**:
- 成本计量（Metering）
- 预算管理（Budget Guarding）
- 成本预留（Reservations）
- 对账（Reconciliation）
- 异常检测（8 条规则，分级缓解）

**API 端点**:
```
GET    /api/v1/entitlements/check               # 检查权限
POST   /api/v1/billing/meter                   # 记录使用量
GET    /api/v1/billing/budget                  # 查询预算
GET    /api/v1/quota/check                     # 检查配额
```

### 3.6 交付管理

**模块**: `delivery-module`

**功能**:
- SFTP 交付
- WebDAV 交付
- S3 交付
- Vault 凭证管理

### 3.7 审计与合规

**模块**: `audit-compliance-module`

**功能**:
- 审计记录存储
- 事件驱动审计（通过 `@EventListener` 消费事件）
- 异常检测（行为异常检测）
- UX Guard（分级缓解动作）

**审计事件**:
- `PROJECT_CREATE`
- `PROJECT_EXPORT`
- `PROJECT_IMPORT`
- `RENDER_JOB_SUBMIT`
- `USER_LOGIN`
- 等等...

### 3.8 通知系统

**模块**: `notification-module`

**功能**:
- 模板管理（NotificationTemplate CRUD）
- 多渠道投递（Email、SMS、Push，可扩展）
- 事件驱动（通过 `@EventListener` 消费事件）
- 投递跟踪（NotificationDelivery 记录）

### 3.9 Feature Flag

**模块**: `policy-governance-module`

**功能**:
- Feature Flag CRUD
- 批量评估
- 缓存
- 审计（15 种事件类型）
- 路由决策（NavigationDecisionService）

**API 端点**:
```
POST   /api/v1/feature-flags                    # 创建 Flag
GET    /api/v1/feature-flags/{flagKey}          # 查询 Flag
PUT    /api/v1/feature-flags/{flagKey}          # 更新 Flag
POST   /api/v1/feature-flags/evaluate           # 批量评估
```

### 3.10 GraphQL 聚合

**模块**: `federation-query-module`

**功能**:
- GraphQL Schema（12+ 查询类型）
- DataLoader 批量处理（N+1 查询预防）
- REST 降级（REST Controller 作为降级）
- 查询限制（深度、复杂度、分页大小）
- 审计拦截（所有查询审计）
- 数据脱敏（PII 字段脱敏）

**NLQ 助手**:
- 自然语言查询 → SQL
- SQL 安全验证（10 条安全规则）
- 范围隔离（租户/工作空间/用户）
- 图表建议（自动图表类型推荐）

**API 端点**:
```
POST   /graphql                                  # GraphQL 查询
POST   /api/v1/analytics/nlq/query               # NLQ 查询
GET    /api/v1/analytics/reports/{reportId}      # 查询报告
```

---

## 4. P4 Import/Export Pipeline

### 4.1 概述

P4 Import/Export Pipeline 提供项目导出和导入功能，支持：

**导出场景**:
| 场景 | 描述 | 导出模式 |
|------|------|----------|
| **账户删除** | 用户下载项目后删除账户 | `metadata_only` 或 `linked_assets` |
| **项目备份** | 用户保存项目副本 | `metadata_only` |
| **分享/协作** | 用户导出供其他用户导入 | `linked_assets` |
| **支持复现** | 支持团队导出以复现渲染问题 | `render_reproduction` |
| **Golden Test 归档** | Golden Render Project CI 回归归档 | `bundled_assets` |

### 4.2 导出模式

| 模式 | 媒体文件 | 签名 URL | 用途 |
|------|----------|----------|------|
| `metadata_only` | ❌ | ❌ | 备份、分享模板、支持 |
| `linked_assets` | ❌ | ✅（短期） | 与现有存储访问权限共享 |
| `bundled_assets` | ✅ | ❌ | 完整归档、Golden Test、删除 |
| `render_reproduction` | ✅（仅输出） | ❌ | 支持复现问题 |

### 4.3 导出 API

**POST** `/api/v1/identity/tenants/{tenantId}/projects/{projectId}/exports`

**请求**:
```json
{
  "mode": "metadata_only"
}
```

**响应**:
```json
{
  "exportId": "export_<uuid>",
  "exportMode": "metadata_only",
  "exportedAt": "2026-06-02T23:44:00Z",
  "project": { ... },
  "assets": { ... },
  "timeline": { ... },
  "render": { ... },
  "effects": { ... },
  "outputs": { ... },
  "audit": { ... }
}
```

### 4.4 ZIP 打包 API

**POST** `/api/v1/identity/tenants/{tenantId}/projects/{projectId}/exports/archive`

**请求**:
```json
{
  "mode": "linked_assets",
  "signedUrlTtlSeconds": 3600
}
```

**响应**:
- `Content-Type: application/zip`
- `Content-Disposition: attachment; filename="project-export-{projectId}-{exportId}.zip"`
- Body: ZIP 文件字节

**ZIP 文件结构**:
```
project-export-v1/
├── manifest.json
├── project.json
├── assets.json
├── README.md
├── checksums/
│   └── sha256sums.txt
├── timeline/
│   └── timeline.json
├── render/
│   ├── render-plan.json
│   ├── spatial-plan.json
│   └── export-profiles.json
├── effects/
│   ├── effect-taxonomy.json
│   └── applied-effects.json
├── outputs/
│   └── outputs-manifest.json
└── audit/
    └── audit-summary.json
```

### 4.5 导入预览 API

**POST** `/api/v1/identity/tenants/{tenantId}/project-imports/preview`

**响应**:
```json
{
  "importId": "imp-<uuid>",
  "compatible": true,
  "schemaVersionMatch": true,
  "warnings": [
    {
      "code": "MISSING_ASSET",
      "severity": "warning",
      "message": "Asset 'logo_transparent' not found in target storage",
      "assetId": "logo_transparent"
    }
  ],
  "assetMapping": [
    {
      "sourceAssetId": "color_bars_1080p",
      "targetAssetId": null,
      "status": "needs_upload",
      "sizeBytes": 524288
    }
  ],
  "estimatedImportSize": 524288,
  "missingAssetCount": 1
}
```

### 4.6 Shell 导入 API

**POST** `/api/v1/identity/tenants/{tenantId}/project-imports/archive`

**请求**: `multipart/form-data`
- `file`: ZIP 文件
- `importName`:（可选）覆盖项目名称
- `mode`:（可选）导入模式（默认: `shell_only`）

**响应**:
```json
{
  "importId": "imp-<uuid>",
  "status": "SUCCEEDED",
  "targetProjectId": "prj-<uuid>",
  "mode": "shell_only",
  "assets": {
    "total": 17,
    "imported": 0,
    "needsUpload": 17,
    "rebound": 0,
    "skipped": 0
  },
  "assetMappings": [ ... ],
  "metadata": {
    "timelinePersisted": true,
    "renderPlanPersisted": true,
    "spatialPlanPersisted": true,
    "effectMetadataPersisted": false
  },
  "warnings": [ ]
}
```

### 4.7 MetadataScrubber

**职责**: 清洗导入元数据中的敏感信息

**清洗字段**（大小写不敏感）:
- `downloadUrl`
- `storageUri` / `storage_uri`
- `storageRef` / `storage_ref`
- `bucket`
- `key`
- `signedUrl` / `signed_url`
- `url`

**清洗策略**:
- **写入时清洗 (Scrub-on-write)**: 持久化到数据库前清洗
- **读取时清洗 (Scrub-on-read)**: 读取时再次清洗（防御纵深）
- **前端清洗 (sanitizeForDisplay)**: 前端展示前再次清洗

**代码位置**: `identity-access-module/src/main/java/com/example/platform/identity/app/MetadataScrubber.java`

### 4.8 安全措施

#### 4.8.1 租户隔离

- 所有 API 路径包含 `tenantId` 路径变量
- Spring Security 验证用户属于该租户
- 数据库查询强制 `WHERE tenant_id = ?`

#### 4.8.2 签名 URL 策略

- 默认 TTL: 3600 秒（1 小时）
- 最大 TTL: 86400 秒（24 小时）
- 超过 86400 秒返回 400 错误
- 签名 URL 不记录在审计日志中
- `storageRef` 在响应中始终为 `null`

#### 4.8.3 ZIP 文件安全检查

- **Zip Bomb 防护**: 50 MB 压缩 / 200 MB 解压，最多 100 个文件
- **Zip Slip 防护**: 拒绝包含 `..` 的路径
- **Entry Allowlist**: 仅允许预定义的文件列表
- **SHA-256 校验**: 验证每个文件的校验和
- **内存处理**: 不写入临时文件，仅在内存中解析 JSON

### 4.9 审计事件

| 事件 | 触发时机 | 载荷 |
|------|----------|------|
| `PROJECT_EXPORT` | 导出请求 | `exportId`, `mode`, `tenantId`, `projectId`, `assetCount` |
| `PROJECT_IMPORT_PREVIEW` | 导入预览 | `exportId`, `mode`, `assetCount`, `ttlSeconds` |
| `PROJECT_IMPORT_SHELL` | Shell 导入成功 | `importId`, `mode`, `sourceProjectId`, `assetCount`, `metadataPersisted` |

**审计载荷排除项**:
- ❌ JSON 内容（完整元数据）
- ❌ 签名 URL
- ❌ `storageUri` / `storageRef`
- ❌ ZIP 文件字节
- ❌ 用户 IP 地址

---

## 5. 技术选型

### 5.1 后端技术栈

| 技术 | 版本 | 选型理由 |
|------|------|----------|
| **Java** | 25 (LTS) | 最新 LTS 版本，支持 Virtual Threads、Pattern Matching、Record Patterns |
| **Spring Boot** | 4.0.4 | 最新主版本，与 Spring Modulith 2.x 集成 |
| **Spring Modulith** | 2.0.4 | 模块化单体架构，支持模块边界验证 |
| **Spring Security** | 内置 | OAuth2/OIDC 集成，租户隔离 |
| **PostgreSQL** | 15+ | Flyway 支持、JSONB 字段、事务完整性 |
| **Flyway** | 10.x | 数据库版本控制，V6 migration 策略 |
| **Gradle** | 9.1 | Kotlin DSL、增量构建、依赖管理 |
| **Jackson** | 2.x | JSON 序列化，支持 Record 类型 |
| **JaCoCo** | 0.8.13 | 代码覆盖率报告 |

### 5.2 前端技术栈

| 技术 | 版本 | 选型理由 |
|------|------|----------|
| **React** | 19.x | Hooks、并发特性、TypeScript 支持 |
| **Vite** | 6.0.7 | 快速 HMR、ESBuild 构建、React 插件 |
| **TanStack Router** | latest | 类型安全路由、导航守卫、懒加载 |
| **Vitest** | 3.0.0 | React 测试框架、Jest 兼容、快速执行 |
| **Zustand** | latest | 轻量级状态管理、TypeScript 友好 |
| **TanStack Query** | latest | 服务器状态管理、缓存、乐观更新 |
| **Zod** | latest | Schema 验证、TypeScript 类型推断 |
| **Remotion** | latest | 视频合成、React 视频渲染 |
| **dnd-kit** | latest | 拖拽交互、无障碍支持 |
| **Tailwind CSS** | 3.x | 原子化 CSS、设计系统 |
| **Radix UI** | latest | 无障碍组件原语 |
| **Tailwind CSS** | 3.4.17 | 原子化 CSS、响应式设计、暗色模式 |
| **jsdom** | 29.1.1 | 浏览器环境模拟，用于 Vitest 测试 |

### 5.3 存储与基础设施

| 技术 | 用途 | 选型理由 |
|------|------|----------|
| **S3 / MinIO** | BlobStorage | 对象存储、签名 URL、生命周期管理 |
| **Redis** | 缓存、会话 | 高性能、分布式锁、Rate Limiting |
| **PostgreSQL** | 主数据库 | ACID 事务、JSONB 支持、Flyway 集成 |
| **Docker** | 容器化 | 开发环境一致性、生产部署 |
| **Kubernetes** | 容器编排 | 自动扩缩容、滚动更新、健康检查 |
| **ArgoCD** | GitOps | 声明式部署、自动同步、回滚 |

---

## 6. 数据模型与数据库

### 6.1 核心表结构

**项目表** (`project`):
```sql
CREATE TABLE project (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_project_tenant_id ON project(tenant_id);
```

**渲染任务表** (`render_job`):
```sql
CREATE TABLE render_job (
    id VARCHAR(64) PRIMARY KEY,
    project_id VARCHAR(64) NOT NULL REFERENCES project(id),
    tenant_id VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'QUEUED',
    provider VARCHAR(64),
    progress INTEGER DEFAULT 0,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_render_job_project_id ON render_job(project_id);
CREATE INDEX idx_render_job_tenant_id ON render_job(tenant_id);
CREATE INDEX idx_render_job_status ON render_job(status);
```

**工件表** (`artifact`):
```sql
CREATE TABLE artifact (
    id VARCHAR(64) PRIMARY KEY,
    project_id VARCHAR(64) NOT NULL REFERENCES project(id),
    tenant_id VARCHAR(64) NOT NULL,
    filename VARCHAR(255) NOT NULL,
    storage_uri VARCHAR(512),
    mime_type VARCHAR(128),
    size_bytes BIGINT,
    checksum VARCHAR(64),
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_artifact_project_id ON artifact(project_id);
CREATE INDEX idx_artifact_tenant_id ON artifact(tenant_id);
```

**导入元数据表** (`project_import_metadata`):
```sql
-- V6 Migration
CREATE TABLE project_import_metadata (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    project_id VARCHAR(64) NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    import_id VARCHAR(64) NOT NULL UNIQUE,
    source_project_id VARCHAR(64),
    source_export_id VARCHAR(64),
    schema_version VARCHAR(32),
    timeline_json TEXT,
    timeline_otio_json TEXT,
    render_plan_json TEXT,
    spatial_plan_json TEXT,
    export_profiles_json TEXT,
    effect_taxonomy_json TEXT,
    applied_effects_json TEXT,
    asset_mapping_json TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_project_import_metadata_project_id ON project_import_metadata(project_id);
CREATE INDEX idx_project_import_metadata_tenant_project ON project_import_metadata(tenant_id, project_id);
CREATE INDEX idx_project_import_metadata_import_id ON project_import_metadata(import_id);
```

### 6.2 Flyway Migration 策略

**目录结构**:
```
src/main/resources/db/migration/
├── V1__initial_schema.sql
├── V2__add_commerce_identity_render.sql
├── V3__prompt_extension_workspace.sql
├── ...
├── V6__create_project_import_metadata.sql
├── ...
└── V22__timeline_revision_labels.sql
```

**策略**:
- **版本化迁移**: 每个 DDL 变更一个版本文件
- **不可变迁移**: 迁移脚本一旦提交不可修改
- **向前兼容**: 确保代码与 N-1 版本数据库兼容
- **模块迁移**: 模块内仅作 JDBC→内存 hydrate，不替代中央 DDL

---

## 7. API 设计

### 7.1 API 版本管理

**策略**: 路径版本控制 (`/api/v1/`)

**示例**:
```
/api/v1/identity/tenants
/api/v1/render/jobs
/api/v1/feature-flags
```

### 7.2 REST API 规范

**响应格式**:
```json
{
  "code": "SUCCESS",
  "data": { ... },
  "message": null,
  "timestamp": "2026-06-08T00:00:00Z"
}
```

**错误格式**:
```json
{
  "code": "ERROR",
  "data": null,
  "message": "Project not found",
  "timestamp": "2026-06-08T00:00:00Z",
  "details": [
    {
      "field": "projectId",
      "message": "Project with id 'xxx' does not exist"
    }
  ]
}
```

**HTTP 状态码**:
| 状态码 | 说明 |
|--------|------|
| 200 | 成功 |
| 201 | 创建成功 |
| 400 | 请求参数错误 |
| 401 | 未认证 |
| 403 | 无权限 |
| 404 | 资源不存在 |
| 409 | 资源冲突 |
| 500 | 服务器内部错误 |
| 501 | 功能未实现 |

### 7.3 GraphQL API

**Schema 示例**:
```graphql
type Query {
  project(id: ID!): Project
  projects(tenantId: ID!, first: Int, after: String): ProjectConnection!
  renderJob(id: ID!): RenderJob
  analytics(query: String!): AnalyticsResult
}

type Project {
  id: ID!
  name: String!
  description: String
  status: ProjectStatus!
  createdAt: DateTime!
  updatedAt: DateTime!
  timeline: Timeline
  assets: [Asset!]!
}

type Timeline {
  id: ID!
  durationMs: Int!
  clips: [Clip!]!
  tracks: [Track!]!
}

type RenderJob {
  id: ID!
  status: RenderJobStatus!
  progress: Int!
  provider: String
  artifact: Artifact
  createdAt: DateTime!
}
```

**NLQ 查询示例**:
```graphql
query {
  analytics(query: "Show me render job success rate by provider for the last 30 days") {
    sql
    result {
      columns
      rows
    }
    chart {
      type
      config
    }
  }
}
```

---

## 8. 前端实现

### 8.1 组件结构

```
frontend/src/
├── components/
│   ├── admin/              # 管理后台组件
│   ├── clip-library/       # 素材库组件
│   ├── common/             # 通用组件
│   ├── editor/             # 编辑器组件
│   ├── effects/            # 效果组件
│   ├── export/             # 导出组件
│   │   ├── ExportPanel.vue
│   │   ├── ImportedMetadataPanel.vue
│   │   └── ...
│   ├── feedback/           # 反馈组件
│   ├── navigation/         # 导航组件
│   ├── notifications/      # 通知组件
│   ├── prompt/             # Prompt 组件
│   ├── subtitles/          # 字幕组件
│   ├── timeline/           # 时间线组件
│   ├── ui/                 # UI 基础组件
│   ├── upload/             # 上传组件
│   ├── user/               # 用户组件
│   └── workspace/          # 工作空间组件
├── api/                    # API 客户端
├── stores/                 # Pinia 状态管理
├── router/                 # Vue Router 路由
├── composables/            # 组合式函数
├── utils/                  # 工具函数
├── views/                  # 页面组件
└── App.vue                 # 根组件
```

### 8.2 核心组件

#### 8.2.1 ExportPanel.vue

**功能**: 项目导出面板

**特性**:
- 支持两种导出模式：`metadata_only`、`linked_assets`
- 配置签名 URL TTL（1-24 小时）
- 下载 ZIP 文件
- 错误处理和加载状态

**代码位置**: `frontend/src/components/export/ExportPanel.vue` (51KB)

#### 8.2.2 ImportedMetadataPanel.vue

**功能**: 导入元数据展示面板

**特性**:
- 显示元数据摘要（timeline、render plan、spatial plan 等）
- 查看详细信息（可折叠章节）
- 前端清洗敏感信息（`sanitizeForDisplay()`）
- 复制到剪贴板

**代码位置**: `frontend/src/components/export/ImportedMetadataPanel.vue`

#### 8.2.3 TimelineInternalPreviewPanel.vue

**功能**: Internal Timeline 1.0 预览

#### 8.2.4 AiTimelineEditPanel.vue

**功能**: AI 时间线编辑面板

#### 8.2.5 IncrementalRenderPanel.vue

**功能**: 增量渲染面板

### 8.3 状态管理（Pinia）

**Store 列表**:
- `useProjectStore`: 项目状态
- `useTenantStore`: 租户状态
- `useUserStore`: 用户状态
- `useRenderStore`: 渲染任务状态
- `useNotificationStore`: 通知状态
- `useFeatureFlagStore`: Feature Flag 状态

**示例**:
```typescript
// stores/project.ts
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { ProjectAPI } from '@/api/project'

export const useProjectStore = defineStore('project', () => {
  const currentProject = ref<Project | null>(null)
  const projects = ref<Project[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)

  const currentProjectId = computed(() => currentProject.value?.id)
  const currentTenantId = computed(() => currentProject.value?.tenantId)

  async function fetchProject(projectId: string) {
    loading.value = true
    error.value = null
    try {
      currentProject.value = await ProjectAPI.get(projectId)
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to fetch project'
    } finally {
      loading.value = false
    }
  }

  return {
    currentProject,
    projects,
    loading,
    error,
    currentProjectId,
    currentTenantId,
    fetchProject
  }
})
```

### 8.4 测试

**测试框架**: Vitest + Vue Test Utils

**测试类型**:
- **单元测试**: 组件逻辑、工具函数
- **集成测试**: API 调用、状态管理
- **E2E 测试**: Playwright（未来）

**测试文件**:
```
frontend/src/components/export/
├── ExportPanel.spec.ts
├── ExportPanelFeatureFlags.spec.ts
├── ImportedMetadataPanel.spec.ts
├── ArtifactPreviewModal.spec.ts
├── ArtifactResult.spec.ts
└── RenderJobStatus.spec.ts
```

---

## 9. 运维部署架构

### 9.1 部署环境

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              Production 环境                                │
└─────────────────────────────────────────────────────────────────────────────┘

                    ┌─────────────────────────────┐
                    │     Ingress Controller       │
                    │     (NGINX / Traefik)        │
                    └─────────────────────────────┘
                                    │
        ┌───────────────────────────┼───────────────────────────┐
        │                           │                           │
        ▼                           ▼                           ▼
┌──────────────┐           ┌──────────────┐           ┌──────────────┐
│  Frontend    │           │  Platform    │           │  Render      │
│  (Static)    │           │  App         │           │  Worker      │
│  :80         │           │  :8080       │           │  :8081       │
└──────────────┘           └──────────────┘           └──────────────┘
                                  │
        ┌─────────────────────────┼─────────────────────────┐
        │                         │                         │
        ▼                         ▼                         ▼
┌──────────────┐         ┌──────────────┐         ┌──────────────┐
│  PostgreSQL  │         │    Redis     │         │  S3 / MinIO  │
│  (Primary)   │         │   (Cluster)  │         │  (Object)    │
│  :5432       │         │   :6379      │         │  :9000       │
└──────────────┘         └──────────────┘         └──────────────┘
```

### 9.2 CI/CD 流程

**GitHub Actions Workflow** (`.github/workflows/ci.yml`):

```
Code Push / PR
      │
      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  Job 1: Backend Tests                                                        │
│  1. Checkout code                                                           │
│  2. Setup Java 25 (Temurin)                                                │
│  3. Cache Gradle dependencies                                              │
│  4. Run `./gradlew test`                                                   │
│  5. Build boot jar smoke check                                             │
│  6. Build Docker image smoke check                                         │
└─────────────────────────────────────────────────────────────────────────────┘
      │
      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  Job 2: Frontend Tests                                                       │
│  1. Checkout code                                                           │
│  2. Setup Node.js 22                                                       │
│  3. Cache npm dependencies                                                 │
│  4. Install dependencies (`npm ci`)                                        │
│  5. Run linter (`npm run lint`)                                            │
│  6. Run tests (`npx vitest run`)                                            │
│  7. Build (`npm run build`)                                                │
└─────────────────────────────────────────────────────────────────────────────┘
      │
      ▼ (仅 main 分支 Push)
┌─────────────────────────────────────────────────────────────────────────────┐
│  Job 3: Build & Push Images + GitOps Staging                                │
│  1. Compute image tag (`git-<12-char-sha>`)                                │
│  2. Build & push platform-api image                                         │
│  3. Build & push platform-render-worker image                               │
│  4. Build & push platform-sandbox-worker image                              │
│  5. Update GitOps staging manifests                                         │
│  6. Validate staging readiness                                              │
│  7. Create staging GitOps PR                                                │
└─────────────────────────────────────────────────────────────────────────────┘
      │
      ▼ (Manual Dispatch: production)
┌─────────────────────────────────────────────────────────────────────────────┐
│  Job 4: Promote Production                                                  │
│  1. Validate image tag (not 'latest' or 'dev')                              │
│  2. Update GitOps production manifests                                     │
│  3. Validate production readiness (strict)                                  │
│  4. Create production GitOps PR                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 9.3 Docker 部署

**Dockerfile** (多阶段构建):
```dockerfile
# Stage 1: Build frontend (Vue 3 + Vite)
FROM node:22-alpine AS frontend-build
WORKDIR /app/frontend
COPY frontend/package*.json ./
RUN npm install
COPY frontend/ .
RUN npm run build

# Stage 2: Build backend (Gradle 9.1 + JDK 25)
FROM gradle:9.1-jdk25-noble AS backend-build
WORKDIR /workspace
COPY --from=frontend-build /app/frontend/dist /workspace/platform-app/src/main/resources/static
COPY . .
RUN ./gradlew :platform-app:bootJar --no-daemon -x test

# Stage 3: Runtime (JRE 25)
FROM eclipse-temurin:25-jre-jammy
WORKDIR /app
COPY --from=backend-build /workspace/app.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
```

**Docker Compose**:
```yaml
version: '3.8'

services:
  db:
    image: postgres:15-alpine
    environment:
      POSTGRES_DB: platform
      POSTGRES_USER: platform
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
    volumes:
      - pgdata:/var/lib/postgresql/data

  app:
    build: .
    environment:
      SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE:-dev}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
      APP_JWT_SECRET: ${APP_JWT_SECRET}
    ports:
      - "8080:8080"
    depends_on:
      - db

  render-worker:
    build:
      context: .
      dockerfile: remote-render-worker/Dockerfile
    environment:
      APP_REMOTE_WORKER_API_KEY: ${APP_REMOTE_WORKER_API_KEY}
    ports:
      - "8081:8081"

volumes:
  pgdata:
```

### 9.4 Kubernetes 部署

**目录结构**:
```
k8s/
├── base/
│   ├── deployment-api.yaml
│   ├── service-sandbox-worker.yaml
│   ├── networkpolicy-sandbox-worker.yaml
│   ├── configmap-egress-proxy.yaml
│   └── hpa.yaml
└── overlays/
    ├── staging/
    │   └── kustomization.yaml
    └── production/
        └── kustomization.yaml
```

**部署清单**:
- `deployment-api.yaml`: Platform App 部署
- `service-sandbox-worker.yaml`: Sandbox Worker 服务
- `networkpolicy-sandbox-worker.yaml`: 网络策略（拒绝出站流量）
- `configmap-egress-proxy.yaml`: Egress Proxy 配置
- `hpa.yaml`: 自动扩缩容配置

---

## 10. 安全设计

### 10.1 认证与授权

**认证方式**:
- **JWT Token**: 用于 API 认证
- **API Key**: 用于机器对机器通信
- **OIDC**: 集成外部身份提供商（Authentik）

**授权模型**:
- **RBAC**: 基于角色的访问控制
- **ABAC**: 基于属性的访问控制（Feature Flag、用户属性、资源属性）

**决策优先级链**:
```
1. 超级管理员覆盖
2. 租户级策略
3. 工作空间级策略
4. 用户级策略
5. Feature Flag 状态
6. 权益 Tier 限制
7. 配额限制
8. 默认拒绝
```

### 10.2 租户隔离

**隔离级别**:
- **数据库**: 所有查询强制 `WHERE tenant_id = ?`
- **存储**: BlobStorage 按租户隔离
- **缓存**: Redis 按租户隔离
- **消息队列**: Outbox 按租户隔离

### 10.3 数据安全

**敏感数据加密**:
- **传输中**: TLS 1.3
- **静态**: AES-256（数据库、BlobStorage）
- **密钥管理**: Vault（生产环境）

**数据脱敏**:
- **PII 字段**: GraphQL 查询自动脱敏
- **审计日志**: 不记录敏感信息
- **错误消息**: 不暴露内部细节

### 10.4 输入验证

**后端验证**:
- **JSR-303**: Bean Validation
- **自定义验证器**: 业务规则验证
- **SQL 注入防护**: jOOQ 参数化查询
- **XSS 防护**: 输入过滤、输出编码

**前端验证**:
- **表单验证**: 实时验证
- **文件类型验证**: 仅允许白名单类型
- **文件大小验证**: 限制上传大小

---

## 11. 测试与质量

### 11.1 测试策略

**测试金字塔**:
```
        ╱╲
       ╱  ╲
      ╱ E2E╲        ← 少量（未来 Playwright）
     ╱──────╲
    ╱集成测试╲       ← 中等（Testcontainers）
   ╱──────────╲
  ╱  单元测试   ╲     ← 大量（JUnit + Vitest）
 ╱──────────────╲
```

### 11.2 后端测试

**测试类型**:
| 类型 | 框架 | 数量 | 覆盖率 |
|------|------|------|--------|
| 单元测试 | JUnit 5 | 200+ | 85% |
| 集成测试 | Testcontainers | 50+ | 75% |
| Modulith 测试 | Spring Modulith | 5+ | - |

**关键测试类**:
- `MetadataScrubberTest`: 验证 URL 清洗逻辑
- `ProjectExportZipPackagingServiceTest`: 验证 ZIP 打包和校验和
- `ProjectImportExecuteServiceTransactionTest`: 验证事务回滚
- `ModularityTest`: 验证模块边界约束

### 11.3 前端测试

**测试类型**:
| 类型 | 框架 | 数量 | 覆盖率 |
|------|------|------|--------|
| 单元测试 | Vitest | 30+ | 70% |
| 集成测试 | Vue Test Utils | 10+ | 60% |

**关键测试文件**:
- `ImportedMetadataPanel.spec.ts`: 验证元数据展示和清洗逻辑
- `ExportPanelFeatureFlags.spec.ts`: 验证 Feature Flag 控制
- `ArtifactPreviewModal.spec.ts`: 验证预览弹窗

### 11.4 CI 质量门禁

**门禁规则**:
- ✅ 所有单元测试通过
- ✅ 所有集成测试通过
- ✅ 代码覆盖率 > 80%
- ✅ 无 Modulith 边界违反
- ✅ 无安全漏洞（Snyk / Dependabot）

---

## 12. 完成度与评估

### 12.1 模块完成度

| 模块 | 状态 | 完成度 |
|------|------|--------|
| **shared-kernel** | ✅ 完成 | 100% |
| **platform-app** | ✅ 完成 | 100% |
| **identity-access-module** | ✅ 完成 | 100% |
| **render-module** | ✅ 完成 | 90% |
| **workflow-module** | ✅ 完成 | 90% |
| **ai-module** | ⚠️ 部分 | 60% |
| **remote-render-worker** | ✅ 完成 | 85% |
| **storage-module** | ✅ 完成 | 100% |
| **billing-module** | ✅ 完成 | 90% |
| **quota-billing-module** | ✅ 完成 | 100% |
| **entitlement-module** | ✅ 完成 | 100% |
| **payment-module** | ⚠️ 部分 | 40% |
| **commerce-module** | ✅ 完成 | 85% |
| **policy-governance-module** | ✅ 完成 | 100% |
| **audit-compliance-module** | ✅ 完成 | 100% |
| **notification-module** | ✅ 完成 | 90% |
| **observability-module** | ✅ 完成 | 100% |
| **prompt-module** | ✅ 完成 | 85% |
| **extension-module** | ✅ 完成 | 90% |
| **sandbox-runtime-module** | ✅ 完成 | 70% |
| **federation-query-module** | ✅ 完成 | 90% |
| **cloud-resource-module** | ✅ 完成 | 100% |
| **export (P4)** | ✅ 完成 | 95% |

### 12.2 功能完成度

| 功能 | 状态 | 完成度 |
|------|------|--------|
| **项目管理** | ✅ 完成 | 100% |
| **时间线编辑** | ✅ 完成 | 90% |
| **渲染系统** | ✅ 完成 | 85% |
| **AI 辅助编辑** | ⚠️ 部分 | 60% |
| **权益与计费** | ✅ 完成 | 90% |
| **交付管理** | ✅ 完成 | 85% |
| **审计与合规** | ✅ 完成 | 100% |
| **通知系统** | ✅ 完成 | 90% |
| **Feature Flag** | ✅ 完成 | 100% |
| **GraphQL 聚合** | ✅ 完成 | 90% |
| **Import/Export Pipeline** | ✅ 完成 | 95% |

### 12.3 剩余债务

#### 12.3.1 Modulith 依赖债务

**问题**: 部分模块仍存在跨模块直接依赖，违反 Modulith 架构原则

**债务清单**: 详见 `docs/modulith-debt-register.md`

**影响**:
- 模块边界不清晰
- 代码耦合度高
- 未来拆分微服务困难

**建议**:
- 逐步重构为 Port/Adapter 模式
- 定义清晰的模块接口

#### 12.3.2 AI 模型集成

**问题**: 当前仅 `StubChatProvider` 占位实现，未集成真实 AI 模型

**影响**:
- AI 辅助编辑功能不可用
- AI 变体生成不可用

**计划**:
- 集成 GLM-4（智谱 AI）
- 集成 Claude（Anthropic）
- 集成 GPT-4（OpenAI）

#### 12.3.3 支付集成

**问题**: 当前仅 `NoopStripePaymentProvider` 占位实现

**影响**:
- 无法处理真实支付
- 无法生成发票

**计划**:
- 集成 Stripe
- 集成 Hyperswitch

#### 12.3.4 Pre-existing CI Failures

**问题**: 部分 CI 失败与当前功能无关，是历史遗留问题

**影响**:
- 干扰新功能问题定位
- 降低 CI 可信度

**建议**:
- 创建 Issue 跟踪 pre-existing failures
- 逐步修复历史问题

---

## 13. 维护与改进建议

### 13.1 模块可维护性分析

#### 13.1.1 优点

1. **Port/Adapter 架构**: 清晰的接口定义，便于扩展
2. **Spring Modulith**: 模块化设计，边界验证
3. **Flyway 版本控制**: 数据库变更可追溯
4. **审计日志**: 关键操作可审计

#### 13.1.2 改进点

1. **Modulith 依赖债务**: 需要逐步清理跨模块依赖
2. **测试覆盖**: 部分边界场景缺少测试
3. **文档**: 部分模块缺少详细文档

### 13.2 潜在重构点

#### 13.2.1 Port/Adapter 扩展

**目标**: 所有外部依赖通过 Port 接口抽象

**当前状态**:
- ✅ `AuditPort`: 已抽象
- ✅ `AssetDownloadUrlPort`: 已抽象
- ✅ `ProjectAssetListingPort`: 已抽象
- ⚠️ 部分模块仍直接依赖其他模块

**建议**:
1. 识别所有跨模块直接依赖
2. 定义 Port 接口
3. 实现 Adapter
4. 替换直接依赖

#### 13.2.2 Modulith 债务清理

**步骤**:
1. 运行 `./gradlew modulithTest` 识别违规
2. 按优先级排序债务
3. 逐个重构为 Port/Adapter 模式
4. 更新 `docs/modulith-debt-register.md`

### 13.3 前端维护建议

#### 13.3.1 jsdom/vitest 环境

**问题**: 部分测试在 jsdom 环境下运行，可能与真实浏览器行为不一致

**建议**:
1. 使用 `happy-dom` 替代 jsdom（更轻量、更快）
2. 关键 E2E 测试使用 Playwright 或 Cypress
3. 明确测试环境限制（如 Web APIs、CSS 渲染）

#### 13.3.2 ImportedMetadataPanel 组件

**当前问题**:
- 组件逻辑复杂，包含数据获取、清洗、展示
- 缺少错误边界处理
- 单元测试覆盖不足

**建议**:
1. **拆分组件**:
   - `ImportMetadataSummary.vue`: 展示摘要
   - `ImportMetadataDetail.vue`: 展示详情
   - `ImportMetadataSection.vue`: 可折叠章节
2. **提取 Hook**:
   ```typescript
   // composables/useImportMetadata.ts
   export function useImportMetadata() {
     const summary = ref(null)
     const detail = ref(null)

     async function fetchSummary(tenantId, projectId) { ... }
     async function fetchDetail(tenantId, projectId) { ... }

     return { summary, detail, fetchSummary, fetchDetail }
   }
   ```
3. **增强测试**:
   - 测试加载状态
   - 测试错误处理
   - 测试数据清洗逻辑

### 13.4 后端维护建议

#### 13.4.1 元数据持久化增强

**当前状态**:
- 元数据持久化到 `project_import_metadata` 表
- 未与编辑器/渲染运行时集成

**建议**:
1. **集成编辑器**:
   - 读取 `timeline_json` 恢复时间线
   - 读取 `render_plan.json` 恢复渲染计划
2. **版本控制**:
   - 支持多版本元数据
   - 记录元数据变更历史
3. **缓存优化**:
   - 使用 Redis 缓存热点元数据
   - 减少数据库查询

#### 13.4.2 ZIP 打包增强

**当前状态**:
- 仅支持 `metadata_only` 和 `linked_assets` 模式
- 不支持 `bundled_assets` 和 `render_reproduction` 模式

**建议**:
1. **实现 bundled_assets 模式**:
   - 下载所有媒体文件
   - 打包到 ZIP
   - 显示进度条
2. **流式处理**:
   - 大文件流式传输
   - 避免内存溢出
3. **断点续传**:
   - 支持大文件上传续传
   - 提高用户体验

---

## 14. 人工复核清单

### 14.1 安全审计签字

**复核项**:
- [ ] **MetadataScrubber 验证**: 手动验证所有敏感 URL 被清洗
- [ ] **Zip Slip 防护验证**: 使用恶意 ZIP 文件测试
- [ ] **签名 URL TTL 验证**: 确认 URL 过期后无法访问
- [ ] **租户隔离验证**: 跨租户访问返回 404

**签字人**: _________________ **日期**: _________________

### 14.2 Golden Render 视频人工 QA

**复核项**:
- [ ] **Golden Render Project 导出**: 导出 `golden-render-project-v1`
- [ ] **ZIP 结构验证**: 确认 ZIP 包含所有必需文件
- [ ] **Shell 导入**: 导入 ZIP 创建 Project Shell
- [ ] **元数据展示**: 在 ImportedMetadataPanel 中查看元数据
- [ ] **视频播放**: 确认 Golden Render 视频播放正常

**签字人**: _________________ **日期**: _________________

### 14.3 渲染系统验证

**复核项**:
- [ ] **JavaCV 渲染**: 提交渲染任务，验证输出
- [ ] **FFmpeg 转码**: 测试视频转码功能
- [ ] **增量渲染**: 修改时间线，验证增量渲染
- [ ] **字幕烧录**: 添加字幕，验证烧录效果

**签字人**: _________________ **日期**: _________________

### 14.4 AI 辅助编辑验证

**复核项**:
- [ ] **AI 时间线编辑**: 测试 AI 编辑功能
- [ ] **AI 变体生成**: 测试变体生成功能
- [ ] **AI 提案确认**: 测试人工确认流程

**签字人**: _________________ **日期**: _________________

### 14.5 CI 全量复核

**复核项**:
- [ ] **CI Pipeline 全绿**: 确认所有 Job 通过
- [ ] **Pre-existing Failures 影响评估**: 确认不影响核心功能
- [ ] **Testcontainers 配置**: 确认集成测试正常运行
- [ ] **代码覆盖率**: 确认覆盖率达标（>80%）

**签字人**: _________________ **日期**: _________________

### 14.6 Staging 环境验证

**复核项**:
- [ ] **OIDC 登录**: 确认 OIDC 认证正常
- [ ] **Storage 连接**: 确认 S3/MinIO 连接正常
- [ ] **Secrets 配置**: 确认 Kubernetes Secrets 正确挂载
- [ ] **Domain 配置**: 确认域名和 CORS 配置正确

**签字人**: _________________ **日期**: _________________

---

## 15. 注意事项与风险

### 15.1 未完成或延后功能

#### 15.1.1 bundled_assets 导出模式

**状态**: 延后（P4-EXPORT-4）

**影响**:
- 无法导出包含媒体文件的完整项目
- 用户需要手动备份媒体文件

**风险等级**: 🟡 中

**计划**:
- 实现媒体文件流式下载
- 显示导出进度
- 支持断点续传

#### 15.1.2 完整项目导入

**状态**: 延后（P4-EXPORT-3b）

**影响**:
- 导入的 Project Shell 无法直接渲染
- 需要手动上传媒体文件

**风险等级**: 🟠 高

**计划**:
- 实现资产上传和绑定
- 集成编辑器恢复时间线
- 支持完整项目恢复

#### 15.1.3 AI 模型集成

**状态**: 占位实现

**影响**:
- AI 辅助编辑功能不可用
- AI 变体生成不可用

**风险等级**: 🟡 中

**计划**:
- 集成 GLM-4（智谱 AI）
- 集成 Claude（Anthropic）
- 集成 GPT-4（OpenAI）

#### 15.1.4 支付集成

**状态**: 占位实现

**影响**:
- 无法处理真实支付
- 无法生成发票

**风险等级**: 🟡 中

**计划**:
- 集成 Stripe
- 集成 Hyperswitch

### 15.2 预警点

#### 15.2.1 Pre-existing CI Failures

**问题**: 部分 CI 失败与当前功能无关，是历史遗留问题

**影响**:
- 干扰新功能问题定位
- 降低 CI 可信度

**建议**:
- 创建 Issue 跟踪 pre-existing failures
- 逐步修复历史问题
- 将 pre-existing failures 与当前功能分离

#### 15.2.2 Modulith 依赖债务

**问题**: 部分模块仍存在跨模块直接依赖

**影响**:
- 模块边界不清晰
- 代码耦合度高
- 未来拆分微服务困难

**建议**:
- 制定 Modulith 债务清理计划
- 优先清理高频依赖
- 引入 ArchUnit 自动验证

#### 15.2.3 手动配置需求

**问题**: 部分配置需要手动干预

**影响**:
- 部署流程复杂
- 容易出错

**手动配置清单**:
1. Flyway migration 冲突解决
2. Kubernetes Secrets 更新
3. OIDC 客户端配置
4. S3/MinIO Bucket 创建

**建议**:
- 自动化配置管理
- 使用 Terraform 或 Pulumi
- 提供初始化脚本

### 15.3 安全风险提示

#### 15.3.1 签名 URL 泄露

**风险**: 签名 URL 如果泄露，可能导致未授权访问

**缓解措施**:
- 短 TTL（默认 1 小时）
- URL 不包含敏感信息
- 审计日志不记录 URL

**剩余风险**: 🟡 中

**建议**:
- 启用对象存储访问日志
- 考虑 IP 限制
- 监控异常访问模式

#### 15.3.2 MetadataScrubber 过度清洗

**风险**: 清洗所有 `key` 字段可能删除合法业务字段

**影响**:
- 效果参数丢失
- 时间线关键帧异常

**缓解措施**:
- 当前项目不依赖导入的 `key` 字段
- 前端展示前再次清洗

**剩余风险**: 🟢 低

**建议**:
- 实现上下文感知清洗
- 仅删除存储相关对象的 `key`
- 添加测试覆盖边界场景

#### 15.3.3 ZIP 文件恶意内容

**风险**: ZIP 文件可能包含恶意脚本

**缓解措施**:
- Entry Allowlist 限制
- 仅解析 JSON 文件
- 不执行任何脚本

**剩余风险**: 🟢 低

**建议**:
- 扫描 ZIP 文件内容
- 限制文件类型
- 使用沙箱环境解析

---

## 16. 流程图与时序图

### 16.1 系统整体数据流

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          系统整体数据流                                      │
└─────────────────────────────────────────────────────────────────────────────┘

User (Browser)
      │
      │ HTTP Request
      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         API Gateway / Load Balancer                         │
└─────────────────────────────────────────────────────────────────────────────┘
      │
      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         platform-app (Spring Boot)                           │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  Authentication & Authorization                                      │   │
│  │  - JWT Token Validation                                              │   │
│  │  - Tenant Isolation                                                  │   │
│  │  - RBAC/ABAC Check                                                   │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                    │                                        │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  Request Routing                                                     │   │
│  │  - REST API (/api/v1/*)                                              │   │
│  │  - GraphQL (/graphql)                                                │   │
│  │  - WebSocket (未来)                                                  │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                    │                                        │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  Business Logic                                                      │   │
│  │  - Project Management                                                │   │
│  │  - Timeline Editing                                                  │   │
│  │  - Render Orchestration                                              │   │
│  │  - AI Edit                                                           │   │
│  │  - Export/Import                                                     │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                    │                                        │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  Data Access                                                         │   │
│  │  - PostgreSQL (Primary)                                              │   │
│  │  - Redis (Cache)                                                     │   │
│  │  - BlobStorage (S3/MinIO)                                            │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 16.2 渲染任务生命周期

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        渲染任务生命周期                                       │
└─────────────────────────────────────────────────────────────────────────────┘

User Submit
      │
      ▼
┌──────────────┐
│    QUEUED    │ ← 任务已提交，等待调度
└──────────────┘
      │
      ▼
┌──────────────┐
│ AI_PROCESSING│ ← AI 预处理（可选）
└──────────────┘
      │
      ▼
┌──────────────┐
│   RENDERING  │ ← 正在渲染
└──────────────┘
      │
      ├────────────────────┐
      │                    │
      ▼                    ▼
┌──────────────┐    ┌──────────────┐
│  COMPLETED   │    │   FAILED     │
└──────────────┘    └──────────────┘
      │                    │
      ▼                    ▼
┌──────────────┐    ┌──────────────┐
│   DELIVERED  │    │ RETRY/ABORT  │
└──────────────┘    └──────────────┘
```

### 16.3 Export 主流程图

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          Project Export 主流程                               │
└─────────────────────────────────────────────────────────────────────────────┘

User
  │
  │ 1. 请求导出
  ▼
┌──────────────────────────┐
│   POST /exports          │
│   { mode: "metadata_only" } │
└──────────────────────────┘
  │
  ▼
┌──────────────────────────┐
│   ProjectExportController │
│   - 验证租户权限          │
│   - 解析请求参数          │
└──────────────────────────┘
  │
  ▼
┌──────────────────────────┐
│   ProjectExportService   │
│   - 查询项目元数据        │
│   - 查询资产清单          │
│   - 生成签名 URL（可选）   │
└──────────────────────────┘
  │
  ├──────────────────────┐
  │                      │
  ▼                      ▼
┌──────────────┐  ┌──────────────┐
│ Project Data │  │ Asset Data   │
│ (Timeline,   │  │ (Artifacts,  │
│  Render,     │  │  Signed URLs)│
│  Effects)    │  │              │
└──────────────┘  └──────────────┘
  │                      │
  └──────────┬───────────┘
             │
             ▼
     ┌──────────────┐
     │ 构建响应      │
     │ ProjectExport │
     │ Response      │
     └──────────────┘
             │
             ▼
     ┌──────────────┐
     │ 记录审计      │
     │ PROJECT_EXPORT│
     └──────────────┘
             │
             ▼
     ┌──────────────┐
     │ 返回响应      │
     │ JSON / ZIP   │
     └──────────────┘
```

### 16.4 Import Execute Shell 时序图

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    Import Execute Shell 时序图                               │
└─────────────────────────────────────────────────────────────────────────────┘

User                Controller          ZipReader         Service
  │                     │                  │                  │
  │ 1. Upload ZIP       │                  │                  │
  ├────────────────────►│                  │                  │
  │                     │                  │                  │
  │                     │ 2. Read ZIP      │                  │
  │                     ├─────────────────►│                  │
  │                     │                  │                  │
  │                     │ 3. Validate      │                  │
  │                     │    - Zip Bomb    │                  │
  │                     │    - Zip Slip    │                  │
  │                     │    - Checksum    │                  │
  │                     │                  │                  │
  │                     │ 4. Parse entries │                  │
  │                     │    - manifest    │                  │
  │                     │    - project    │                  │
  │                     │    - assets     │                  │
  │                     │                  │                  │
  │                     │ 5. ExportResp   │                  │
  │                     │◄─────────────────┤                  │
  │                     │                  │                  │
  │                     │ 6. Execute Import│                  │
  │                     ├────────────────────────────────────►│
  │                     │                  │                  │
  │                     │                  │                  │ 7. @Transactional
  │                     │                  │                  │
  │                     │                  │                  │ 8. Create Project
  │                     │                  │                  │    Shell
  │                     │                  │                  │
  │                     │                  │                  │ 9. Scrub Metadata
  │                     │                  │                  │    (MetadataScrubber)
  │                     │                  │                  │
  │                     │                  │                  │ 10. Persist Metadata
  │                     │                  │                  │     (INSERT INTO
  │                     │                  │                  │      project_import_
  │                     │                  │                  │      metadata)
  │                     │                  │                  │
  │                     │                  │                  │ 11. Record Audit
  │                     │                  │                  │     (Best Effort)
  │                     │                  │                  │
  │                     │                  │                  │ 12. Commit Transaction
  │                     │                  │                  │
  │                     │ 13. Response    │                  │
  │                     │◄────────────────────────────────────┤
  │                     │                  │                  │
  │ 14. Import Result   │                  │                  │
  │◄────────────────────┤                  │                  │
  │                     │                  │                  │
```

### 16.5 Frontend ExportPanel 渲染逻辑

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    Frontend ExportPanel 渲染逻辑                             │
└─────────────────────────────────────────────────────────────────────────────┘

Component Created
         │
         ▼
┌──────────────────────────┐
│   Initialize State       │
│   - selectedMode         │
│   - signedUrlTtlSeconds  │
│   - loading              │
│   - error                │
└──────────────────────────┘
         │
         ▼
┌──────────────────────────┐
│   Render Mode Selector   │
│   ┌────────────────────┐ │
│   │ ○ metadata_only    │ │
│   │ ○ linked_assets    │ │
│   └────────────────────┘ │
└──────────────────────────┘
         │
         ▼ (if linked_assets selected)
┌──────────────────────────┐
│   Render TTL Input       │
│   ┌────────────────────┐ │
│   │ TTL (seconds): 3600│ │
│   └────────────────────┘ │
└──────────────────────────┘
         │
         ▼ (User Click "Export")
┌──────────────────────────┐
│   POST /exports/archive  │
│   - mode                 │
│   - signedUrlTtlSeconds  │
└──────────────────────────┘
         │
         ├─────────────────────┐
         │                     │
         ▼                     ▼
┌──────────────┐      ┌──────────────┐
│ Loading=True │      │ Error        │
│ Show Spinner │      │ Show Error   │
└──────────────┘      └──────────────┘
         │
         ▼
┌──────────────────────────┐
│   Download ZIP           │
│   - Create Blob URL      │
│   - Trigger Download     │
│   - Cleanup              │
└──────────────────────────┘
         │
         ▼
┌──────────────────────────┐
│   Show Success Message   │
│   "Export completed!"    │
└──────────────────────────┘
```

---

## 附录 A: API 端点完整参考

### A.1 身份与访问管理

**租户管理**:
```
POST   /api/v1/identity/tenants                     # 创建租户
GET    /api/v1/identity/tenants/{tenantId}          # 查询租户
PUT    /api/v1/identity/tenants/{tenantId}          # 更新租户
DELETE /api/v1/identity/tenants/{tenantId}          # 删除租户
```

**用户管理**:
```
POST   /api/v1/identity/tenants/{tenantId}/users    # 创建用户
GET    /api/v1/identity/tenants/{tenantId}/users    # 查询用户列表
GET    /api/v1/identity/tenants/{tenantId}/users/{userId}  # 查询用户
PUT    /api/v1/identity/tenants/{tenantId}/users/{userId}  # 更新用户
DELETE /api/v1/identity/tenants/{tenantId}/users/{userId}  # 删除用户
```

**项目管理**:
```
POST   /api/v1/identity/tenants/{tenantId}/projects                  # 创建项目
GET    /api/v1/identity/tenants/{tenantId}/projects                  # 查询项目列表
GET    /api/v1/identity/tenants/{tenantId}/projects/{projectId}      # 查询项目
PUT    /api/v1/identity/tenants/{tenantId}/projects/{projectId}      # 更新项目
DELETE /api/v1/identity/tenants/{tenantId}/projects/{projectId}      # 删除项目
```

### A.2 渲染管理

**渲染任务**:
```
POST   /api/v1/render/jobs                      # 创建渲染任务
GET    /api/v1/render/jobs/{jobId}              # 查询渲染状态
POST   /api/v1/render/incremental/submit         # 增量渲染
GET    /api/v1/render/jobs/{jobId}/status       # 渲染进度
GET    /api/v1/render/jobs?projectId=xxx        # 按项目查询
```

**渲染 Worker**:
```
GET    /api/v1/remote-worker/workers             # 查询 Worker 列表
POST   /api/v1/remote-worker/workers/{workerId}/heartbeat  # 心跳
```

### A.3 导出/导入

**导出**:
```
POST   /api/v1/identity/tenants/{tenantId}/projects/{projectId}/exports         # 导出元数据
POST   /api/v1/identity/tenants/{tenantId}/projects/{projectId}/exports/archive # 导出 ZIP
```

**导入**:
```
POST   /api/v1/identity/tenants/{tenantId}/project-imports/preview            # 导入预览
POST   /api/v1/identity/tenants/{tenantId}/project-imports/preview/archive     # ZIP 导入预览
POST   /api/v1/identity/tenants/{tenantId}/project-imports/archive             # Shell 导入
GET    /api/v1/identity/tenants/{tenantId}/projects/{projectId}/import-metadata # 查询导入元数据
GET    /api/v1/identity/tenants/{tenantId}/projects/{projectId}/import-metadata/detail # 查询详细信息
```

### A.4 权益与计费

**权益检查**:
```
GET    /api/v1/entitlements/check?feature=xxx     # 检查权限
GET    /api/v1/entitlements/me                    # 查询当前用户权益
```

**计费**:
```
POST   /api/v1/billing/meter                     # 记录使用量
GET    /api/v1/billing/budget                    # 查询预算
GET    /api/v1/billing/usage?projectId=xxx       # 查询使用量
```

**配额**:
```
GET    /api/v1/quota/check?feature=xxx           # 检查配额
GET    /api/v1/quota/me                          # 查询当前用户配额
```

### A.5 Feature Flag

```
POST   /api/v1/feature-flags                    # 创建 Flag
GET    /api/v1/feature-flags/{flagKey}          # 查询 Flag
PUT    /api/v1/feature-flags/{flagKey}          # 更新 Flag
DELETE /api/v1/feature-flags/{flagKey}          # 删除 Flag
POST   /api/v1/feature-flags/evaluate           # 批量评估
```

### A.6 GraphQL

```
POST   /graphql                                 # GraphQL 查询
```

**NLQ 查询**:
```
POST   /api/v1/analytics/nlq/query               # NLQ 查询
GET    /api/v1/analytics/reports/{reportId}      # 查询报告
```

---

## 附录 B: 配置参考

### B.1 Application Configuration

```yaml
spring:
  application:
    name: media-platform

  # Database
  datasource:
    url: jdbc:postgresql://localhost:5432/platform
    username: platform
    password: ${POSTGRES_PASSWORD}

  # Flyway
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true

  # Security
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${APP_SECURITY_OAUTH2_ISSUER_URI}
          audience: ${APP_SECURITY_OAUTH2_AUDIENCE}

# Storage
app:
  storage:
    type: s3 # or minio
    s3:
      bucket: ${AWS_S3_BUCKET}
      region: ${AWS_REGION}
    minio:
      endpoint: ${MINIO_ENDPOINT}
      bucket: ${MINIO_BUCKET}

# Feature Flags
  audit:
    importMetadataRead:
      enabled: false
  export:
    zip:
      bundledAssets:
        enabled: false
  import:
    fullImport:
      enabled: false

# Domain
  domain:
    base: https://media-platform.example.com
    cors:
      allowed-origins:
        - https://media-platform.example.com
```

### B.2 Docker Compose Configuration

```yaml
version: '3.8'

services:
  db:
    image: postgres:15-alpine
    environment:
      POSTGRES_DB: platform
      POSTGRES_USER: platform
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
    volumes:
      - pgdata:/var/lib/postgresql/data
    ports:
      - "5432:5432"

  app:
    build: .
    environment:
      SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE:-dev}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
      APP_JWT_SECRET: ${APP_JWT_SECRET}
      APP_SECURITY_OAUTH2_ISSUER_URI: ${APP_SECURITY_OAUTH2_ISSUER_URI}
      APP_SECURITY_OAUTH2_AUDIENCE: ${APP_SECURITY_OAUTH2_AUDIENCE}
    ports:
      - "8080:8080"
    depends_on:
      - db

  render-worker:
    build:
      context: .
      dockerfile: remote-render-worker/Dockerfile
    environment:
      APP_REMOTE_WORKER_API_KEY: ${APP_REMOTE_WORKER_API_KEY}
      APP_REMOTE_WORKER_CALLBACK_URL: http://app:8080
    ports:
      - "8081:8081"

volumes:
  pgdata:
```

---

## 附录 C: 测试覆盖

### C.1 后端测试覆盖

| 模块 | 测试数量 | 覆盖率 |
|------|----------|--------|
| identity-access (Export/Import) | 44+ | 85% |
| render-module | 30+ | 80% |
| billing-module | 20+ | 75% |
| entitlement-module | 15+ | 80% |
| policy-governance-module | 25+ | 85% |
| **总计** | **134+** | **82%** |

### C.2 前端测试覆盖

| 组件 | 测试数量 | 覆盖率 |
|------|----------|--------|
| ExportPanel | 3+ | 75% |
| ImportedMetadataPanel | 2+ | 70% |
| ArtifactPreviewModal | 1+ | 65% |
| Timeline Components | 5+ | 70% |
| **总计** | **11+** | **72%** |

---

## 附录 D: 数据库迁移脚本

### D.1 核心 Migration 脚本

| 版本 | 说明 |
|------|------|
| V1 | 核心基础设施、扩展定义、Outbox |
| V2 | 商业、身份、渲染、产物 |
| V3 | Prompt、扩展平台、工作空间 RBAC |
| V4 | 权益、导航、计费、通知、社交、Feature Flag |
| V5-V22 | 索引、交付、Temporal、租户 AI 密钥等 |
| **V6** | **project_import_metadata 表（Import/Export Pipeline）** |

### D.2 V6 Migration

```sql
-- V6__create_project_import_metadata.sql
CREATE TABLE project_import_metadata (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    project_id VARCHAR(64) NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    import_id VARCHAR(64) NOT NULL UNIQUE,
    source_project_id VARCHAR(64),
    source_export_id VARCHAR(64),
    schema_version VARCHAR(32),
    timeline_json TEXT,
    timeline_otio_json TEXT,
    render_plan_json TEXT,
    spatial_plan_json TEXT,
    export_profiles_json TEXT,
    effect_taxonomy_json TEXT,
    applied_effects_json TEXT,
    asset_mapping_json TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_project_import_metadata_project_id ON project_import_metadata(project_id);
CREATE INDEX idx_project_import_metadata_tenant_project ON project_import_metadata(tenant_id, project_id);
CREATE INDEX idx_project_import_metadata_import_id ON project_import_metadata(import_id);
```

---

## 文档变更历史

| 日期 | 版本 | 变更内容 | 作者 |
|------|------|----------|------|
| 2026-06-08 | v1.0 | 初始版本（完整 Media Platform 文档） | Platform Engineering Team |

---

**文档结束**
