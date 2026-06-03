# 模块详细说明

> **最后更新:** 2026-05-14

---

## 核心基础设施模块

### shared-kernel ✅ 完成

**用途:** 共享工具库，被所有模块依赖。

**功能:**
- `shared.events` - 事件定义（RenderJobCreatedEvent、PromptExecutionCompletedEvent 等）
- `shared.web` - PlatformException、ConfigurableErrorCode、ErrorCodeRegistry
- `shared.logging` - TraceKeys、TenantContext
- `shared.audit` - AuditPort 接口
- `shared.cost` - CostEstimationPort、BudgetGuardPort、CostReservationPort（Prompt 44）
- `shared.entitlement` - EntitlementPort（Prompt 45）
- `shared.monitoring` - SentryMonitoringService（Prompt 48）

**依赖:** 无（被所有模块依赖）

**测试:** 2 个测试类

---

### platform-app ✅ 完成

**用途:** Spring Boot 应用入口，组装所有模块。

**功能:**
- OpenApiConfiguration - 7 个 API 组、安全方案
- SecurityConfiguration - CORS、API 密钥过滤器注册
- RateLimitFilter - 基于 IP 的速率限制
- GlobalSentryExceptionHandler - 全局异常处理 → Sentry
- SentryMonitoringSpringBean - 条件性 Sentry 服务

**依赖:** 所有 30 个模块

**测试:** 11 个测试类（含 ModularityTest、RenderFlowIntegrationTest）

---

### config-module ✅ 完成

**用途:** 应用配置管理，支持版本控制。

**功能:** 配置的 CRUD 操作、版本历史

**接口:** REST API `/api/v1/config/**`

**测试:** 无独立测试（通过集成测试覆盖）

---

### secrets-config-module ✅ 完成

**用途:** 密钥引用管理，不存储真实密钥。

**功能:** 密钥引用 CRUD、密钥轮换标记

**安全:** 真实密钥仅通过环境变量注入

---

### identity-access-module ✅ 完成

**用途:** 身份认证与访问控制。

**功能:**
- API 密钥管理（创建、验证、吊销）
- 用户管理（创建、角色分配）
- 租户管理（创建、层级）
- 项目管理（创建、成员管理）
- ApiKeyAuthFilter - API 密钥认证过滤器

**接口:**
- `POST /api/v1/identity/api-keys` - 创建 API 密钥
- `GET /api/v1/identity/tenants/{id}` - 获取租户信息
- `GET /api/v1/identity/users/{id}` - 获取用户信息

**⚠️ 部分实现:** API 密钥认证默认关闭（`app.identity.api-key-auth-enabled=false`）

**🔴 阻塞项:** 无用户认证层（JWT/OAuth2 未实现）

---

### scheduler-module ✅ 完成

**用途:** 定时任务调度。

**功能:**
- Cron 任务定义与注册
- 手动触发（`POST /api/v1/internal/scheduler/run/{jobKey}`）
- 失败重试与死信队列
- Outbox 事件重试（OutboxRetryPort SPI）

**接口:**
- `POST /api/v1/internal/scheduler/run/{jobKey}` - 手动触发
- `GET /api/v1/scheduler/overview` - 概览

**测试:** 1 个测试类

---

### outbox-event-module ✅ 完成

**用途:** 事务发件箱模式，确保事件可靠发布。

**功能:**
- 事件存储与发布
- 重试机制（retry_count、next_attempt_at）
- 幂等性键（idempotency_key）
- 锁定机制（locked_at、locked_by）

**依赖:** shared-kernel

---

### datasource-module ✅ 完成

**用途:** 多数据源联邦查询。

**功能:**
- DSL 上下文注册表
- 联邦查询网关（NoopFederatedQueryGateway - 存根）

**⚠️ 部分实现:** 联邦查询网关为存根，实际查询路由待实现

---

### artifact-catalog-module ✅ 完成

**用途:** 渲染工件元数据跟踪。

**功能:** 工件记录、存储 URI、格式/编解码器元数据

---

### storage-module ✅ 完成

**用途:** 多提供者存储目录管理。

**功能:** 存储对象 CRUD、多提供者支持

---

### cloud-resource-module ✅ 完成

**用途:** 云资源管理。

**功能:** 存储桶管理（CloudResourceProvider SPI）

**⚠️ 部分实现:** 云资源提供者为存根

---

## 媒体处理模块

### render-module ✅ 完成

**用途:** 渲染编排，管理 6 个渲染提供者。

**功能:**
- RenderJob 生命周期管理（QUEUED → PROCESSING → COMPLETED/FAILED）
- RenderJobStateMachine - 状态转换验证
- RenderJobStatusHistoryRepository - 状态历史记录
- StaleRenderJobCompactionService - 卡住任务补偿（默认 30 分钟超时）
- RenderQualityCheckService - 输出质量检查
- RenderJobValidationService - 提交前验证（权益、成本、预算）

**提供者:**
| 提供者 | 类型 | 状态 |
|--------|------|------|
| JavaCV | 渲染 | ✅ 完整 |
| OFX | 渲染 | ✅ 完整 |
| GPAC | 打包 | ✅ 完整 |
| MLT | 渲染 | ✅ 完整 |
| GStreamer | 渲染 | ✅ 完整 |
| FFMPEG | 渲染 | ✅ 完整 |

**GPU 预设:**
| 预设 | 编码器 | 层级 |
|------|--------|------|
| GPU_H264 | NVENC H.264 | 团队+ |
| GPU_H265 | NVENC HEVC | 团队+ |
| GPU_VP9 | VAAPI VP9 | 团队+ |

**接口:**
- `POST /api/v1/render/jobs` - 创建渲染任务
- `GET /api/v1/render/jobs/{jobId}` - 获取任务状态
- `POST /api/v1/render/jobs/{jobId}/cancel` - 取消任务
- `POST /api/v1/render/jobs/{jobId}/retry` - 重试任务
- `GET /api/v1/render/presets` - 列出可用预设
- `POST /api/v1/render/export/validate` - 导出前验证

**测试:** 5 个测试类

---

### workflow-module ✅ 完成

**用途:** 工作流编排（Temporal + LiteFlow）。

**功能:**
- RenderWorkflow / RenderPipelineWorkflow - 工作流定义
- RenderActivities / RenderPipelineActivities - 活动接口
- LocalRenderExecutionAdapter / TemporalRenderExecutionAdapter - 执行适配器
- FeatureFlagEvaluator - 功能标志评估

**依赖:** render-module、shared-kernel

**测试:** 4 个测试类

---

### remote-render-worker ✅ 完成

**用途:** 远程渲染工作者（支持 GPU）。

**功能:**
- WorkerRegistryService - 工作者注册与健康检查
- RemoteRenderService - 任务分发与执行
- GPU 预设支持（GPU_H264、GPU_H265、GPU_VP9）

**接口:**
- `GET /api/v1/remote-worker/workers` - 列出工作者
- `POST /api/v1/remote-worker/drain` - 排空工作者

**测试:** 1 个测试类

---

### ai-module ⚠️ 部分实现

**用途:** AI 模型集成。

**功能:**
- ChatProvider SPI - AI 聊天提供者接口
- OpenAiChatProvider - OpenAI 集成（⚠️ 需要真实 API 密钥）
- StubChatProvider - 存根提供者（默认）
- SimpleModelRouter - 模型路由
- AiGatewayService - AI 网关服务

**🔴 阻塞项:** 使用 StubChatProvider，无真实 AI 模型集成

**接口:**
- `POST /api/v1/ai/chat` - AI 聊天
- `GET /api/v1/ai/models` - 列出可用模型

**测试:** 2 个测试类

---

## 业务逻辑模块

### billing-module ✅ 完成

**用途:** 成本计量与预算控制。

**功能:**
- CostEstimationService - 成本估算（基于提供者配置和预设乘数）
- BudgetGuardService - 预算守卫（软限制 80%、硬限制 100%）
- CostReservationService - 成本预留（创建/完成/释放）
- CostUsageAccumulator - 使用量聚合
- ReconciliationService - 自动对账（内部记录 vs 外部发票）

**接口:**
- `POST /api/v1/billing/cost/estimate` - 估算成本
- `GET /api/v1/billing/tenants/{tenantId}/budget` - 预算状态
- `POST /api/v1/billing/reconciliation/run` - 运行对账

**测试:** 3 个测试类

---

### entitlement-module ✅ 完成

**用途:** 基于层级的访问控制。

**功能:**
- EntitlementPolicy - 5 层策略定义
- ExportCapabilityPolicy - 导出格式/预设白名单
- ProviderAccessPolicy - 提供者访问白名单
- QuotaPolicy - 使用配额
- EntitlementPolicyService - 策略评估（通过 EntitlementPort）

**层级对比:**

| 层级 | 分辨率 | 月度时长 | GPU | 并发任务 |
|------|--------|----------|-----|----------|
| 免费 | 1280x720 | 60 分钟 | ❌ | 1 |
| 专业 | 1920x1080 | 300 分钟 | ❌ | 3 |
| 团队 | 3840x2160 | 1200 分钟 | ✅ | 10 |
| 企业 | 3840x2160 | 6000 分钟 | ✅ | 50 |
| 实验 | 3840x2160 | 无限 | ✅ | 100 |

**测试:** 1 个测试类

---

### audit-compliance-module ✅ 完成

**用途:** 审计跟踪、异常检测、用户体验保护、问题数据处理。

**功能:**
- AuditService - 审计记录 CRUD
- AuditEventHandler - 事件监听（RenderJobCreated、StatusChanged 等）
- UsageAnomalyDetectionService - 8 条异常检测规则
- UserExperienceGuard - 用户体验保护（永不取消运行中任务）
- ProblematicDataDetectionService - 问题数据检测（12 条规则）
- ProblematicDataAutoFixService - 自动修复与隔离

**接口:**
- `GET /api/v1/audit/compliance/overview` - 审计概览
- `POST /api/v1/audit/compliance/records` - 创建审计记录
- `GET /api/v1/audit/compliance/records` - 查询审计记录

**测试:** 3 个测试类

---

### notification-module ✅ 完成

**用途:** 多渠道通知。

**功能:**
- NotificationProvider SPI - 通知提供者接口
- 模板管理
- 多渠道支持（邮件、短信、Webhook - 存根）

**测试:** 4 个测试类

---

### observability-module ✅ 完成

**用途:** 第三方服务健康监控。

**功能:**
- ThirdPartyProviderHealthService - 14 个服务监控
- ProviderSlaMetric - SLA 指标跟踪
- ProviderCircuitBreakerState - 断路器（CLOSED/OPEN/HALF_OPEN）
- ProviderIncidentRecord - 事件管理

**接口:**
- `GET /api/v1/observability/health` - 健康概览
- `POST /api/v1/observability/incidents/report` - 报告事件

**测试:** 2 个测试类

---

### user-analytics-module ✅ 完成

**用途:** 用户行为分析。

**功能:**
- UserBehaviorEvent - 行为事件跟踪
- UserProfile / UserSegment - 用户画像与分群
- Analytics API

**测试:** 5 个测试类

---

### commerce-module ✅ 完成

**用途:** 商务流程。

**功能:** 结账、收入跟踪、采购订单

**⚠️ 部分实现:** 支付流程为存根

**测试:** 2 个测试类

---

### payment-module ⚠️ 部分实现

**用途:** 支付网关集成。

**功能:**
- PaymentProvider SPI
- NoopStripePaymentProvider / NoopHyperswitchPaymentProvider - 存根
- Webhook 处理

**🔴 阻塞项:** 所有支付提供者为存根，无法处理真实支付

**测试:** 1 个测试类

---

### compatibility-migration-module ✅ 完成

**用途:** 模式迁移，支持 9 个模式族。

**功能:**
- SchemaFamily.PROMPT_TEMPLATE - 提示词模板迁移
- JsonPatchMigrationAdapter - v1→v2 JSON Patch 迁移
- 13 个迁移测试样本

**测试:** 1 个测试类（13 个测试方法）

---

### policy-governance-module ✅ 完成

**用途:** 策略治理与功能标志。

**功能:**
- FeatureFlagEvaluator - 功能标志评估
- PolicyEvaluationService - 策略评估
- QuotaEvalService - 配额评估

**测试:** 2 个测试类

---

## 提示词工程平台

### prompt-module ✅ 完成

**用途:** 提示词模板全生命周期管理。

**功能:**
- PromptTemplateService - 模板 CRUD、版本管理、渲染、风险评估
- PromptSafetyPolicyService - 安全扫描（密钥检测、破坏性命令分类）
- 变量 Schema 支持（8 种类型）
- 执行记录与评估
- 文件扫描/导入（Frontmatter 解析）
- MANIFEST 验证

**接口:**
- `POST /api/v1/prompts/templates` - 创建模板
- `GET /api/v1/prompts/templates` - 列出模板
- `POST /api/v1/prompts/templates/{id}/versions` - 创建版本
- `POST /api/v1/prompts/templates/{id}/render` - 渲染预览
- `POST /api/v1/prompts/templates/{id}/validate` - 验证
- `POST /api/v1/prompts/risk/analyze` - 风险分析
- `POST /api/v1/prompts/executions` - 开始执行
- `POST /api/v1/prompts/executions/{id}/evaluate` - 评估执行

**测试:** 2 个测试类

---

## 扩展与安全模块

### extension-module ✅ 完成

**用途:** 动态扩展与插件热加载。

**功能:**
- ProviderExtensionSPI - 提供者扩展接口
- PromptExtensionSPI - 提示词扩展接口
- WorkflowStepExtensionSPI - 工作流步骤扩展接口
- ExtensionRegistryService - 注册、卸载、回滚、查询
- SandboxExecutionService - 沙箱执行（超时 30s、输出限制 4MB）
- ToolRegistry - 工具注册表（可执行文件白名单）
- ProcessToolRunner - 进程工具运行器（Apache Commons Exec）
- PluginManagerConfiguration - PF4J 插件管理器
- ExtensionCatalogService - 扩展目录

**接口:**
- `POST /api/v1/extensions` - 注册扩展
- `GET /api/v1/extensions` - 列出扩展
- `POST /api/v1/extensions/{key}/unload` - 卸载扩展
- `POST /api/v1/extensions/{key}/execute` - 执行扩展

**测试:** 5 个测试类

---

### sandbox-runtime-module ⚠️ 部分实现

**用途:** 沙箱运行时（Wasm/容器）。

**功能:**
- SandboxExecutor SPI - 沙箱执行接口
- SandboxSecurityPolicy SPI - 安全策略接口
- SandboxRuntimeService - 占位符服务（返回 status: "disabled"）

**📋 未来:** Wasmtime/Wasmer 运行时集成

**测试:** 1 个测试类

---

## 前端组件

### 视频编辑器 (✅ 完成)

| 组件 | 用途 | 关键特性 |
|------|------|----------|
| TimelineEditor.vue | 时间线编辑 | 轨道管理、片段编辑、OTIO 导出 |
| ExportPanel.vue | 导出面板 | 预设选择、预算状态、异常警告、GPU/远程工作者状态 |
| EffectsPanel.vue | 特效面板 | 特效包管理、滤镜应用 |
| SubtitleTimeline.vue | 字幕时间线 | 字幕提示编辑、多语言支持 |
| PromptManagementPage.vue | 提示词管理 | 侧边栏导航、标签切换 |
| FeedbackButton.vue | 用户反馈 | 反馈弹窗、OpenReplay 集成 |
| MonitoringStatus.vue | 监控状态 | Sentry/OpenReplay 状态指示器 |

---

## 错误码参考

| 模块 | 错误码范围 | 数量 |
|------|-----------|------|
| 通用 | COMMON-400-001 ~ COMMON-502-001 | 7 |
| 渲染 | RENDER-400-001 ~ RENDER-503-001 | 5 |
| 字幕 | SUBTITLE-400-001 ~ SUBTITLE-422-001 | 4 |
| 特效 | EFFECT-400-001 ~ EFFECT-403-001 | 3 |
| 时间线 | TIMELINE-400-001 ~ TIMELINE-422-001 | 2 |
| 迁移 | MIGRATION-400-001 ~ MIGRATION-422-001 | 3 |
| 权益 | ENTITLEMENT-403-001 ~ ENTITLEMENT-403-005 | 5 |
| 成本 | COST-402-001 ~ COST-402-003 | 3 |
| 用量 | USAGE-429-001 ~ USAGE-429-003 | 3 |
| 对账 | RECON-409-001 | 1 |
| 提供者 | PROVIDER-503-001 | 1 |
| 提示词 | PROMPT-400-001 ~ PROMPT-500-001 | 10 |
| 监控 | MONITORING-500-001 ~ MONITORING-503-001 | 2 |
| 安全 | SECURITY-401-001 ~ SECURITY-429-001 | 3 |
| 反馈 | FEEDBACK-400-001 ~ FEEDBACK-500-001 | 2 |
| **总计** | | **51** |

所有错误码支持中/英双语翻译。
