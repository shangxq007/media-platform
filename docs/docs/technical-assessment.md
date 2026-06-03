# 技术评估报告：架构、可维护性、扩展性与代码质量

> **评估日期**：2026-05-28
> **项目版本**：0.2.0-SNAPSHOT
> **评估范围**：全仓库（后端 34 模块 + 前端 Vue 3 + 基础设施）

---

## 总评

| 维度 | 评分 | 说明 |
|------|------|------|
| 架构设计 | 7/10 | 模块化思路清晰，但 shared-kernel 膨胀、federation 耦合严重 |
| 可维护性 | 6.5/10 | 代码结构规范，但类偏大、文档过时、dead dependency 多 |
| 未来扩展性 | 6/10 | 插件 SPI 设计好，但模块边界模糊，拆分微服务成本高 |
| 代码实现质量 | 6.5/10 | 整体规范，但存在重复代码、不一致的错误处理、测试覆盖不均衡 |
| 技术选型 | 7.5/10 | 主流且合理，但部分选型偏新/偏实验性 |

**综合评分：6.7/10** — 一个有野心的平台，架构方向正确，但处于"从原型到生产"的过渡期，需要系统性治理。

---

## 1. 架构设计评估

### 1.1 做得好的地方

**模块化思路清晰**
- 34 个 Gradle 模块按域划分（render/billing/entitlement/audit 等），职责边界大体合理
- 使用 Spring Modulith 进行模块边界 enforcement，有 `package-info.java` 声明
- 共享内核模式：`shared-kernel` 作为唯一 OPEN 模块，其他模块 CLOSED

**端口-适配器模式**
- `shared-kernel` 定义了 `CheckoutPaymentPort`、`PurchaseFulfillmentPort`、`EntitlementPort` 等 SPI 接口
- `commerce-module` 通过 port 接口调用 billing/payment，不直接依赖实现 — 这是正确的依赖倒置

**事件驱动解耦**
- 18 个领域事件 + Outbox 模式实现跨模块异步通信
- `outbox-event-module` 提供事务性 Outbox（`dispatch-interval: 3s`, `retry-interval: 30s`）

**双路径认证**
- JWT (OIDC/Authentik) + API Key 并存，支持人机两种场景
- Legacy HMAC JWT 兼容迁移路径

**渲染提供商抽象**
- 13 个渲染提供商通过统一接口接入，DAG 管道编排
- 支持本地执行和 Temporal 分布式执行两种模式

**安全架构**
- 零信任 NetworkPolicy（4 个策略覆盖所有工作负载）
- 出口代理（Squid）+ 域名白名单 + SSRF 防护
- sandbox-worker 完全隔离（egress deny-all）

### 1.2 架构问题

#### 问题 1：shared-kernel 严重膨胀（🔴 严重）

`shared-kernel` 包含 **18 个领域事件** 和 **11 个子包的端口接口**，跨越 8+ 个有界上下文：

| 子包 | 属于哪个域 | 问题 |
|------|-----------|------|
| `events/RenderJob*Event` (×6) | Render | 渲染域事件不应在共享内核 |
| `events/RenderDelivery*Event` (×2) | Delivery | 交付域事件不应在共享内核 |
| `events/Artifact*Event` (×2) | Artifact | 产物域事件不应在共享内核 |
| `events/CostReservation*Event` (×2) | Billing | 计费域事件不应在共享内核 |
| `events/Quota*Event` (×2) | Quota | 配额域事件不应在共享内核 |
| `commerce/CheckoutPaymentPort` | Commerce | 商务端口不应在共享内核 |
| `payment/` | Payment | 支付端口不应在共享内核 |
| `entitlement/EntitlementPort` | Entitlement | 权益端口不应在共享内核 |
| `cost/CostReservationPort` | Billing | 计费端口不应在共享内核 |
| `asset/StorageUriReferenceContributor` | Storage | 存储端口不应在共享内核 |
| `monitoring/SentryMonitoringService` | Observability | 监控端口不应在共享内核 |

**影响**：
- shared-kernel 不再是"内核"，而是"共享一切"
- 任何模块拆分微服务时，shared-kernel 成为最大障碍
- 18 个事件中，render/delivery/artifact/billing/commerce 域的事件占了 12 个

**建议**：
- 将域特定事件移到各自模块的 `domain/events/` 包
- shared-kernel 仅保留真正的跨域基础类型（`Ids`, `Jsons`, `TenantContext`, `ErrorCode`）
- 端口接口移到各自模块的 `spi/` 包，通过 Spring 配置注入

#### 问题 2：federation-query-module 成为上帝模块（🔴 严重）

`federation-query-module` 依赖 **12 个模块**（占平台 60%）：

```
shared-kernel, entitlement, identity, policy-governance, billing,
extension, render, prompt, user-analytics, audit-compliance, ai, datasource
```

**影响**：
- 无法独立部署 — 牵一发动全身
- 任何依赖模块的变更都可能影响 federation
- 测试复杂度极高（需要 mock 12 个模块）

**建议**：
- 将 GraphQL 查询按域拆分为多个 schema 模块
- 使用 GraphQL Federation（Apollo Federation 或 Spring GraphQL Federation）替代单体聚合
- 或者将 federation 定位为"只读查询层"，明确其不持有业务逻辑

#### 问题 3：api vs implementation 滥用（🟡 中等）

多个模块对所有项目依赖使用 `api` 而非 `implementation`，导致传递依赖膨胀：

| 模块 | 问题 |
|------|------|
| `render-module` | 5 个 project dep 全部 `api`，JavaCV (500MB+) 和 LiteFlow 泄漏给所有消费者 |
| `federation-query-module` | 12 个 project dep 全部 `api` |
| `workflow-module` | render + policy-governance 使用 `api`，间接泄漏 render 的 5 个依赖 |
| `delivery-module` | storage 使用 `api`（secrets 正确用 `implementation`） |
| `artifact-catalog-module` | storage 使用 `api` |

**影响**：
- `workflow-module` 依赖 `render-module`，间接获得 ai/storage/extension/entitlement 的传递依赖
- JavaCV (500MB+ 原生库) 通过 `api` 传递到 workflow/federation/remote-render-worker
- 编译时间增长，classpath 污染

**建议**：
- 所有项目间依赖默认使用 `implementation`
- 仅当消费者需要直接使用被依赖模块的 API 时才用 `api`
- JavaCV 和 LiteFlow 必须改为 `implementation`

#### 问题 4：platform-app 有 11 个死依赖（🟡 中等）

`platform-app/build.gradle.kts` 声明了 31 个模块依赖，但以下模块在 `platform-app/src/main/` 中 **零导入**：

```
notification-module, cloud-resource-module, quota-billing-module,
social-publish-module, compatibility-migration-module, federation-query-module,
user-analytics-module, observability-module, config-module, scheduler-module
```

**影响**：
- 增加构建时间和 classpath 大小
- 误导开发者以为这些模块在 platform-app 中有实际使用
- Spring Boot 自动配置可能意外激活这些模块的 Bean

**建议**：
- 清理死依赖
- 或者确认这些模块是否仅通过 Spring 自动配置生效（如 `@AutoConfiguration`）

#### 问题 5：package-info.java 与 build.gradle.kts 不一致（🟡 中等）

多处 `@ApplicationModule(allowedDependencies)` 与实际 Gradle 依赖不匹配：

| 模块 | 问题 |
|------|------|
| `render-module` | package-info 声明 `workflow` 依赖，但 build.gradle.kts 中没有 |
| `user-analytics-module` | package-info 声明 5 个 allowed dep，实际只用 1 个 |
| `commerce-module` | package-info 声明 6 个 allowed dep，实际只用 shared-kernel |
| `delivery-module` | allowedDependencies 比 Gradle dep 更窄（声称 `secrets::API` 但依赖完整模块） |
| `platform-app/app` | allowedDependencies 仅列 4 个，实际导入更多 |

**影响**：
- Spring Modulith 的边界检查不准确
- 开发者无法通过 package-info 了解真实依赖关系
- 模块拆分时产生误导

### 1.3 架构评分细则

| 子项 | 评分 | 说明 |
|------|------|------|
| 模块划分合理性 | 7/10 | 域划分清晰，但 shared-kernel 膨胀 |
| 依赖方向正确性 | 6/10 | 存在 api/implementation 滥用，传递依赖失控 |
| 边界 enforcement | 6.5/10 | Spring Modulith 有声明但与实际不一致 |
| 事件驱动设计 | 8/10 | Outbox 模式实现正确，事件定义合理 |
| 安全架构 | 8.5/10 | 零信任网络 + 出口代理 + 沙箱隔离，设计完善 |
| 部署架构 | 8/10 | K8s + GitOps + ArgoCD，现代化 |

---

## 2. 可维护性评估

### 2.1 做得好的地方

**统一的错误处理**
- `GlobalExceptionHandler` 返回 RFC 7807 `ProblemDetail`
- `ErrorCode` 枚举 + `ErrorCodeRegistry` 统一管理错误码
- 60+ 错误码覆盖各模块

**一致的代码风格**
- 模块内部分层清晰（api/app/domain/spi/infrastructure）
- Controller → Service → Repository 标准三层
- DTO 与 Entity 分离

**配置管理集中**
- `application.yml` 主配置 + profile 覆盖（dev/prod/oidc/litellm/temporal/r2）
- `@ConfigurationProperties` 类型安全配置
- K8s ConfigMap 外部化配置

**测试基础设施完善**
- 366 个后端测试类 + 88 个前端 spec 文件
- Spring Modulith 集成测试支持
- Testcontainers 支持
- Vitest + happy-dom 前端测试

### 2.2 可维护性问题

#### 问题 1：文档严重过时（🔴 严重）

| 指标 | 文档值 | 实际值 | 偏差 |
|------|--------|--------|------|
| 模块数 | 30 | 34 | +13% |
| Flyway 迁移数 | 17 | 3 (V1-V3) | -82% |
| 数据库表数 | 28+ | 70+ | +150% |
| 后端测试文件 | 54+ | 366 | +578% |
| 渲染提供商 | 6 | 13+ | +117% |

**影响**：
- 新开发者依赖文档会产生错误认知
- 架构决策记录与实际不符
- 运维手册中的步骤可能失效

#### 问题 2：文档描述的能力不存在（🔴 严重）

| 文档描述 | 代码状态 |
|----------|----------|
| Novu 通知集成 | ❌ 无 `NovuNotificationProvider` |
| SMTP/SendGrid/SES 邮件 | ❌ 无 `EmailNotificationProvider` |
| Twilio SMS | ❌ 无 `SmsNotificationProvider` |
| KillBill 计费引擎 | ❌ 无 `NoopKillBillBillingEngine` |
| Medusa 目录适配器 | ❌ 无 `NoopMedusaCatalogAdapter` |
| PopcornFX 渲染 | ❌ 无 PopcornFX 代码 |

**影响**：开发者按文档操作会发现功能不存在，浪费时间。

#### 问题 3：代码重复（🟡 中等）

- 多个模块的 Controller 有相似的 CRUD 模式（创建/查询/更新/删除），但无统一基类或泛型抽象
- 前端多个 admin 页面有相似的 DataTable + FilterBar + Pagination 模式，但复制代码较多
- `docker-compose*.yml` 文件之间有大量重复的服务定义

#### 问题 4：类偏大（🟡 中等）

- 部分 Service 类超过 500 行（如渲染编排、计费计算）
- 部分 Controller 类超过 300 行（如管理台聚合接口）
- 前端 `EditorPage.vue` 和 `types/index.ts` (854 行) 过大

#### 问题 5：魔法数字和硬编码（🟢 轻微）

- 前端 `useHistoryStore` 中 `max: 50`（undo 栈大小）硬编码
- 多个 Controller 中的分页默认值不一致（10/20/25）
- 部分超时值硬编码在代码中而非配置

### 2.3 可维护性评分细则

| 子项 | 评分 | 说明 |
|------|------|------|
| 代码结构清晰度 | 7.5/10 | 分层清晰，包结构合理 |
| 文档准确性 | 4/10 | 严重过时，数据偏差大 |
| 错误处理一致性 | 7.5/10 | ProblemDetail 统一，但部分模块处理不一致 |
| 代码重复度 | 6/10 | CRUD 模式重复，缺少泛型抽象 |
| 配置管理 | 8/10 | 集中化 + 类型安全 + 外部化 |
| 测试可维护性 | 6.5/10 | 数量充足但部分测试脆弱 |

---

## 3. 未来扩展性评估

### 3.1 做得好的地方

**插件系统（PF4J）**
- `extension-module` 提供运行时插件加载
- 支持插件版本管理、路由规则、资源限制、回滚点
- 沙箱执行隔离用户代码

**AI 提供商 SPI**
- `ChatProvider` 接口 + `StubChatProvider` 默认实现
- 支持 OpenAI / LiteLLM 路由
- 按场景路由（script-generation / nlq / timeline-edit）

**渲染提供商扩展**
- 13 个提供商通过统一接口接入
- 新增提供商只需实现接口 + 配置开关
- DAG 管道支持自定义编排

**前端动态路由**
- `syncDynamicRoutes()` 支持运行时路由注册
- `componentMap` 27 个懒加载组件
- Feature Flag 控制路由可见性

**多环境配置**
- Kustomize overlays（base/staging/production）
- Spring profiles（dev/prod/oidc/litellm/temporal/r2）
- 环境差异明确（replicas/resources/log-level）

### 3.2 扩展性限制

#### 限制 1：微服务拆分成本高（🔴 严重）

如果未来需要从单体拆分为微服务，以下模块拆分成本极高：

| 模块 | 拆分难度 | 原因 |
|------|----------|------|
| `federation-query-module` | 🔴 极高 | 依赖 12 个模块 |
| `shared-kernel` | 🔴 极高 | 35/36 个模块依赖它 |
| `render-module` | 🟡 中等 | 5 个依赖 + 传递依赖失控 |
| `platform-app` | 🟡 中等 | 31 个依赖，11 个死的 |
| `compatibility-migration-module` | 🟡 中等 | 6 个跨域依赖 |

**根本原因**：模块间耦合度高，shared-kernel 过于庞大，api/implementation 滥用导致传递依赖失控。

#### 限制 2：数据库耦合（🟡 中等）

- 所有模块共享同一个 PostgreSQL 实例
- 70+ 张表在一个 schema 中
- 无明确的数据库边界（每个模块应有自己的 schema 或数据库）
- Flyway 迁移是单体式的（V1 包含全部 schema）

**影响**：
- 无法独立扩展某个模块的数据库
- 表名冲突风险
- 迁移脚本难以按模块管理

#### 限制 3：前端单体（🟡 中等）

- 所有前端代码在一个 Vue 3 SPA 中
- 44 个 admin 页面 + 20+ 用户页面在一个 bundle 中
- 虽然有懒加载，但路由和 store 是集中式的
- 无微前端架构（Module Federation / iframe / Web Components）

#### 限制 4：GraphQL 默认关闭（🟢 轻微）

- `spring.graphql.enabled: false`（默认）
- 前端有 GraphQL 客户端但功能未验证
- 如果未来需要 GraphQL，需要额外验证和测试

### 3.3 扩展性评分细则

| 子项 | 评分 | 说明 |
|------|------|------|
| 水平扩展能力 | 7/10 | K8s HPA + 多副本，但共享数据库是瓶颈 |
| 功能扩展能力 | 7.5/10 | 插件 SPI + 渲染提供商扩展 + 动态路由 |
| 微服务拆分准备度 | 5/10 | 模块耦合度高，shared-kernel 过大 |
| 数据库扩展能力 | 5/10 | 单体数据库，无 schema 隔离 |
| 前端扩展能力 | 6/10 | SPA 单体，有懒加载但无微前端 |
| 多租户扩展 | 7/10 | TenantContext 存在但隔离未完全 enforcement |

---

## 4. 代码实现质量评估

### 4.1 做得好的地方

**安全实现质量高**
- SSRF 防护完整（Webhook URL 验证 + 出口代理 + NetworkPolicy）
- 沙箱执行审计（不记录代码内容，仅记录 codeHash）
- 敏感字段自动脱敏
- SecurityContext 配置完善（nonRoot/readOnlyFS/noPrivEsc/capDrop ALL）

**事务 Outbox 实现正确**
- 事务性消息发布（Outbox 表 + 定时分发）
- 重试机制（3s 间隔 + 30s 重试间隔）
- 优雅关闭时 Outbox 排空

**CI/CD 流程完善**
- 4 个 CI job（backend/frontend/images/promote-production）
- 不可变镜像 tag（git-SHA）
- 就绪验证脚本（816 行，覆盖 13 个检查类别）
- 出口代理 smoke 验证

### 4.2 代码质量问题

#### 问题 1：测试覆盖不均衡（🟡 中等）

**后端测试**（366 个测试类）：
- ✅ 领域模型测试覆盖较好
- ✅ Controller 层有集成测试
- ⚠️ 渲染执行端到端测试缺失（依赖外部工具）
- ⚠️ 支付流程测试全部 Noop
- ⚠️ GraphQL 测试几乎为空（功能默认关闭）
- ❌ K8s NetworkPolicy 无集群内验证
- ❌ 出口代理 smoke 测试默认关闭

**前端测试**（88 个 spec 文件）：
- ✅ 组件测试覆盖较好
- ✅ Store/Composable 测试充分
- ⚠️ E2E 测试仅有 Playwright MCP 配置，无实际测试
- ⚠️ 编辑器核心交互（拖拽/缩放）测试不足
- ❌ 渲染预览播放器无测试

#### 问题 2：错误处理不一致（🟡 中等）

- 大部分 Controller 使用 `ProblemDetail` + `GlobalExceptionHandler`
- 但部分模块直接返回 `ResponseEntity` 自定义格式
- 部分 Service 层抛出 `PlatformException`，部分抛出标准 `RuntimeException`
- 前端 API 错误处理有 `apiError.ts` 工具，但各 API client 使用不一致

#### 问题 3：Stub/Noop 实现过多（🟡 中等）

以下核心功能仅有 Stub 实现：

| 功能 | Stub 类 | 影响 |
|------|---------|------|
| AI 聊天 | `StubChatProvider` | 所有 AI 功能不可用 |
| Stripe 支付 | `NoopStripePaymentProvider` | 支付流程不可用 |
| Hyperswitch 支付 | `NoopHyperswitchPaymentProvider` | 支付流程不可用 |
| Feature Flag | `LocalFeatureFlagProvider` (内存) | Flag 不持久化 |
| 邮件通知 | 文档描述但代码不存在 | 邮件不可用 |
| SMS 通知 | 文档描述但代码不存在 | SMS 不可用 |

**影响**：系统看起来功能完整，但核心商业化链路（支付/AI/通知）全部是断的。

#### 问题 4：前端类型定义过于集中（🟢 轻微）

- `frontend/src/types/index.ts` — 854 行，包含所有类型定义
- 建议按模块拆分（`types/render.ts`, `types/billing.ts` 等）

#### 问题 5：TODO 注释残留（🟢 轻微）

- `api/admin/feature-flags.ts:102` — `TODO: Implement admin cross-tenant capability query`
- `utils/tenant.ts:10` — `TODO: Replace with selectedWorkspaceId for UI state only`
- 前端 `MySettingsPage.vue` — 2 处功能禁用注释

### 4.3 代码质量评分细则

| 子项 | 评分 | 说明 |
|------|------|------|
| 代码规范一致性 | 7/10 | 大体一致，但存在风格差异 |
| 错误处理 | 6.5/10 | 框架统一但执行不一致 |
| 测试覆盖 | 6.5/10 | 数量充足但不均衡，关键路径缺失 |
| 安全实现 | 8.5/10 | SSRF/沙箱/脱敏/SecurityContext 完善 |
| 代码复杂度 | 6/10 | 部分类偏大，缺少泛型抽象 |
| 注释/TODO 管理 | 6.5/10 | 有 TODO 残留，部分复杂逻辑缺注释 |

---

## 5. 技术选型评估

### 5.1 选型合理的项

| 技术 | 版本 | 评估 |
|------|------|------|
| **Spring Boot** | 4.0.4 | ✅ 主流框架，生态丰富 |
| **Spring Modulith** | 2.0.4 | ✅ 模块化 enforcement，适合单体→微服务演进 |
| **jOOQ** | 3.19.18 | ✅ 类型安全 SQL，比 JPA 更适合复杂查询 |
| **Flyway** | BOM-managed | ✅ 行业标准 DB 迁移工具 |
| **PostgreSQL** | 16 | ✅ 生产级关系型数据库 |
| **Vue 3 + Vite** | 3.5.13 + 6 | ✅ 现代前端框架，开发体验好 |
| **Pinia** | 2.3 | ✅ Vue 3 官方推荐状态管理 |
| **Kubernetes** | — | ✅ 容器编排标准 |
| **ArgoCD** | — | ✅ GitOps CD 标准 |
| **Squid** | 6.6 | ✅ 成熟代理，适合出口控制 |
| **PF4J** | 3.15.0 | ✅ JVM 插件系统成熟方案 |
| **Temporal** | SDK | ✅ 持久化工作流，适合长时间渲染任务 |
| **Sentry** | 10.53 | ✅ 错误追踪标准 |
| **MinIO/S3** | — | ✅ 对象存储标准接口 |

### 5.2 选型有风险的项

| 技术 | 版本 | 风险 | 建议 |
|------|------|------|------|
| **Java** | 25 | ⚠️ 非常新的 JDK，LTS 版本是 21；部分库可能不兼容 | 考虑降级到 LTS 版本（21）或确认所有依赖支持 25 |
| **Spring AI** | 2.0.0-M3 | ⚠️ **Milestone 版本**，非 GA；API 可能变更 | 关注 GA 发布，准备适配；或考虑直接使用 OpenAI SDK |
| **LiteFlow** | 2.15.3.2 | ⚠️ 国产规则引擎，社区较小，文档有限 | 评估是否可用 Spring Expression Language 或 Drools 替代 |
| **JavaCV** | 1.5.9 | ⚠️ 500MB+ 原生依赖，通过 `api` 传递 | 改为 `implementation`；考虑仅在 Worker 中使用 |
| **oidc-client-ts** | 3.1.0 | ⚠️ 前端 OIDC 库，需确认与 Authentik 兼容性 | 验证 PKCE 流程 |
| **GraphQL** | Spring GraphQL | ⚠️ 默认关闭，前端有客户端但未验证 | 如不需要可移除以减少复杂度 |

### 5.3 选型缺失的项

| 领域 | 缺失 | 建议 |
|------|------|------|
| **API 网关** | 无独立网关（直接用 K8s Ingress） | 考虑 Kong/Spring Cloud Gateway 用于限流/路由 |
| **消息队列** | 无 MQ（使用 Outbox 模式） | 对于高吞吐场景考虑 Kafka/RabbitMQ |
| **缓存** | 无分布式缓存（Redis） | 考虑添加 Redis 用于会话/缓存 |
| **搜索引擎** | 无全文搜索 | 考虑 Elasticsearch 用于素材/项目搜索 |
| **对象存储客户端** | 无 MinIO 客户端配置 | 开发环境建议用 MinIO 替代本地存储 |
| **分布式追踪** | OTLP 配置存在但默认 localhost | 生产需配置 Jaeger/Tempo |
| **配置中心** | 无（使用 K8s ConfigMap） | 考虑 Spring Cloud Config / Consul |
| **密钥管理** | Vault 配置存在但默认关闭 | 生产必须启用 Vault 或云 KMS |

### 5.4 技术选型评分细则

| 子项 | 评分 | 说明 |
|------|------|------|
| 主流程度 | 8/10 | 大部分为主流选择 |
| 版本稳定性 | 6.5/10 | Java 25 和 Spring AI Milestone 有风险 |
| 生态兼容性 | 7/10 | 整体兼容，但 JavaCV 传递依赖有问题 |
| 运维友好度 | 7.5/10 | K8s + GitOps 现代化，但缺少监控/追踪/缓存 |
| 团队学习成本 | 7/10 | 技术栈广泛但不深奥 |

---

## 6. 关键建议（按优先级）

### P0：生产阻断（必须修复）

1. **清理 shared-kernel** — 将域特定事件和端口接口移到各自模块
2. **修复 api/implementation** — 所有项目间依赖默认 `implementation`，JavaCV 和 LiteFlow 必须改
3. **清理 platform-app 死依赖** — 移除 11 个未使用的模块依赖
4. **同步 package-info 与 build.gradle.kts** — 确保 Spring Modulith 边界声明准确
5. **更新文档** — 修正所有过时数据，删除不存在的功能描述

### P1：架构改进（计划迭代）

6. **拆分 federation-query-module** — 按域拆分 GraphQL schema 或采用 Apollo Federation
7. **数据库 schema 隔离** — 每个模块使用独立 schema，为微服务拆分做准备
8. **统一错误处理** — 所有模块使用 `GlobalExceptionHandler` + `ProblemDetail`
9. **添加分布式缓存** — Redis 用于会话和热点数据
10. **完善测试** — 补充渲染 E2E 测试、NetworkPolicy 验证、支付流程 Mock 测试

### P2：技术债务（持续偿还）

11. **Java 版本评估** — 评估是否降级到 LTS 21
12. **Spring AI GA 迁移** — 等待 GA 版本发布后迁移
13. **前端类型拆分** — `types/index.ts` 按模块拆分
14. **代码重复消除** — 提取泛型 CRUD 基类、前端 DataTable 抽象
15. **类拆分** — 超过 500 行的 Service 类按职责拆分

### P3：未来探索（按需推进）

16. **微前端** — 评估 Module Federation 用于前端模块化
17. **消息队列** — 评估 Kafka 用于高吞吐事件
18. **搜索引擎** — 评估 Elasticsearch 用于素材搜索
19. **GPU 渲染** — 评估 GPU 调度方案
20. **OTIO 原生集成** — 评估 Java OTIO 库（如存在）或继续通过 MCP 工具实现

---

## 7. 总结

这是一个**有清晰架构愿景的平台项目**，在以下方面表现出色：

- **安全架构**：零信任网络 + 出口代理 + 沙箱隔离，设计完善
- **模块化思路**：34 个模块按域划分，Spring Modulith enforcement
- **CI/CD**：完整的 GitOps 流程 + 就绪验证 + 不可变镜像
- **渲染编排**：13 个提供商 + DAG 管道 + 双执行模式

但在以下方面需要系统性治理：

- **模块耦合**：shared-kernel 膨胀 + federation 上帝模块 + api/implementation 滥用
- **文档质量**：严重过时，数据偏差大，存在虚构功能
- **生产准备度**：核心商业化链路（支付/AI/通知）全部 Stub
- **技术选型风险**：Java 25 + Spring AI Milestone

**建议**：在进入下一阶段功能开发前，先进行一轮架构治理（P0 + P1），清理技术债务，同步文档，为未来的微服务演进打好基础。
