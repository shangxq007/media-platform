# Media Platform 架构评估报告

> **评估日期：** 2026-05-20  
> **评估范围：** `media-platform-workspace`（主应用 `media-platform/` + 文档 `docs/`）  
> **评估方式：** 静态代码与文档审查（未修改代码、未运行全量测试）  
> **关联文档：** [生产阻断项](./01-production-blockers.md)、[技术债](./02-technical-debt.md)、[项目状态](../00-overview/02-project-status.md)

---

## 1. 执行摘要

Media Platform 是一个以 **Spring Modulith 模块化单体** 为核心的 AI 视频生产与编排平台：31 个 Gradle 后端模块、Vue 3 前端、Flyway 管理的数据库 Schema、REST + GraphQL 双协议 API。项目在 **功能广度** 上已达到 Prompt 66 宣称的「开发完成」水平，前端路由与后端模块覆盖面很广；但在 **生产就绪度** 上存在 5 项 Critical 阻断（认证、租户隔离、支付/AI Stub、Feature Flag 持久化），且架构约束（模块边界、Schema 所有权、内存存储）在若干关键路径上被放宽。

| 维度 | 评分（1–5） | 一句话结论 |
|------|-------------|------------|
| **可扩展性** | 3.5 | SPI/事件/outbox 设计良好，但模块耦合与 Schema 分散限制纵向拆分 |
| **可维护性** | 3.0 | 包结构规范、文档齐全，但边界测试被过滤、实现与文档不一致 |
| **可观测性** | 3.0 | 日志/指标基础扎实，分布式追踪与健康检查未闭环 |
| **完成度（功能）** | ~75% | 渲染/编排/前端编辑器较完整；商业闭环与安全层为 Stub |
| **完成度（生产）** | ~35% | 不可直接上生产，需先解决 Critical 5 项 + 持久化一致性 |

---

## 2. 项目形态与功能划分

### 2.1 工作区结构

```
media-platform-workspace/
├── media-platform/          # 主应用（Gradle 多模块 + Vue 前端）
├── docs/                    # 架构/模块/API 文档（Prompt 66 重建）
├── prompts/                 # 开发执行清单
├── autonomous-plan/         # 自动化开发模板
└── scripts/                 # 工作区脚本
```

主应用 **不是** npm/pnpm monorepo；根目录 `package.json` 仅用于从工作区根运行 Vitest。真正的构建入口在 `media-platform/`。

### 2.2 技术栈

| 层 | 选型 |
|----|------|
| 后端 | Java 25, Spring Boot 4.0.4, Spring Modulith 2.0.4 |
| 数据 | PostgreSQL 16（生产）/ H2（开发默认）, Flyway, jOOQ, JdbcTemplate |
| 工作流 | Temporal 1.33, LiteFlow 2.15 |
| 扩展 | PF4J 3.15 |
| 前端 | Vue 3.5, Vite 6, Pinia, GraphQL |
| 部署 | Docker Compose（简化版 + `infra/docker` 完整版）, 独立 `remote-render-worker` |

### 2.3 模块分层（31 个 Gradle 模块）

按 **业务能力** 可归纳为六组：

| 分组 | 模块 | 职责 |
|------|------|------|
| **内核与入口** | `shared-kernel`, `platform-app` | 共享类型、事件、错误码；应用启动与横切配置 |
| **基础设施** | `config`, `secrets-config`, `datasource`, `identity-access`, `scheduler`, `outbox-event` | 配置、密钥引用、多数据源、身份、调度、事务发件箱 |
| **媒体管线** | `render`, `workflow`, `ai`, `remote-render-worker`, `artifact-catalog`, `storage` | 渲染 Provider、编排、AI、远端 Worker、产物与存储 |
| **商业与治理** | `billing`, `quota-billing`, `entitlement`, `payment`, `commerce`, `policy-governance`, `audit-compliance` | 计量、配额、权益、支付、结账、策略/Feature Flag、审计 |
| **平台能力** | `prompt`, `extension`, `sandbox-runtime`, `federation-query`, `notification`, `observability`, `user-analytics`, `cloud-resource`, `social-publish` | Prompt 工程、插件、沙箱、GraphQL 聚合、通知、可观测、分析、社交发布 |
| **未接入主应用** | `compatibility-migration-module` | 在 `settings.gradle.kts` 注册，**未**列入 `platform-app` 依赖 |

### 2.4 包设计（模块内）

文档约定且多数模块遵循的标准布局（见 `docs/11-development/02-module-structure.md`）：

```
api/            → REST 控制器、DTO（@NamedInterface("API")）
app/            → 应用服务、编排
domain/         → Record、值对象、领域事件
spi/            → 端口接口
infrastructure/ → Repository、外部适配器
package-info.java → @ApplicationModule 元数据
```

**评价：** 包设计 **意图清晰**，有利于新人按层定位代码；与 DDD 分层、Spring Modulith 的「显式模块 API」一致。已知不一致项（Repository 落在 `app/`、部分模块缺少根 `package-info.java`）已记录在 `02-technical-debt.md`，属 **中低优先级** 整洁度问题，不阻断演进。

### 2.5 API 与功能边界

| 协议 | 路径/入口 | 职责划分 |
|------|-----------|----------|
| REST | `/api/v1/*` | 写操作、模块 CRUD、用户门户 `/api/v1/me/*` |
| GraphQL | `/graphql` | 只读聚合（`federation-query-module`） |
| OpenAPI | `/swagger-ui.html` | 契约文档 |

**横切编排层：** `platform-app` 的 `web/`、`app/` 包中存在跨模块聚合控制器（如 `MeController`、`NavigationController`），这在模块化单体中合理，但若直接依赖其他模块的 **非暴露类型**，会破坏 Modulith 边界（见第 4 节）。

**前端功能划分（完成度较高）：** 编辑器、用户门户、工作空间协作、管理控制台、社交发布、NLQ/分析等路由已在 `frontend/src/router/index.ts` 中落地；与后端模块映射基本可对应。

---

## 3. 表设计评估

### 3.1 迁移与所有权

- **主迁移路径：** `platform-app/src/main/resources/db/migration/`，当前 **V1–V18** 共 18 个文件。
- **访问方式：** 无 JPA Entity；以 **Flyway DDL + jOOQ/JdbcTemplate + Java Record** 为主，利于不可变领域模型与 SQL 可控性。
- **参考 DDL：** `media-platform/docs/ddl-postgresql.sql`（70+ 表，按域分组）。

### 3.2 表域划分（按迁移演进）

| 版本域 | 主要业务表 | 设计评价 |
|--------|------------|----------|
| V1–V3 | 渲染作业、存储、发件箱、审计、调度、配额定义 | 核心编排基础合理 |
| V4 | 商业/计费/权益 | 商业化域集中，利于事务边界 |
| V7–V10 | 租户/项目/用户/API Key/产物、配额用量、渲染历史 | 多租户字段开始出现 |
| V11–V14 | Prompt 工程、问题数据隔离、扩展平台 v2、工作空间 RBAC | 域扩展有序 |
| V15–V18 | 权益升级、导航、计费模型、通知升级 | Post-66 持续演进 |

整体 Schema **按业务域纵向分表**，外键与索引在 V6 等迁移中补强，**可扩展性在数据模型层面中等偏上**。

### 3.3 不合理或高风险的设计实践

#### 问题 A：Schema 所有权分散（严重）

存在 **三套** 表定义来源：

1. **中央 Flyway**（`platform-app/db/migration`）— 唯一会被 Flyway 自动执行的路径；
2. **模块内 Flyway SQL**（如 `notification-module`、`social-publish-module` 下的 `db/migration/`）— **不会** 被主应用 Flyway 扫描；
3. **运行时 Bootstrap DDL**（`SocialPublishDataBootstrap` 使用 `CREATE TABLE IF NOT EXISTS`）。

**影响：**

- 不同环境（本地 H2、Docker PostgreSQL、CI）表结构可能不一致；
- 模块内 `V1__notification_tables.sql` 与 `V18__notification_tables.sql` **内容重叠**，维护双份真相；
- 社交发布域依赖 Bootstrap 而非迁移，**不可复现、不可回滚**，违反 Flyway 单一所有权原则。

**建议方向（文档级，暂不改代码）：** 所有 DDL 收敛到 `platform-app/db/migration`；删除模块内迁移与 Bootstrap 建表；模块仅保留 Repository。

#### 问题 B：文档与实测数据不一致（中等）

| 文档声称 | 实测（2026-05-20） |
|----------|-------------------|
| 30 个 Gradle 模块 | 31 个（含 `social-publish-module`） |
| 17 个 Flyway 迁移 | 18 个（含 V18） |
| 28+ 表 | DDL 文档 70+ 表 |
| ~350 Java 源文件 | ~1057 个 `.java` 文件 |
| 54+ 测试文件 | ~219 个测试 `.java` |

**影响：** 新成员与评审者会对项目规模与完成度产生 **系统性误判**。

#### 问题 C：测试 Schema 滞后（中等）

`platform-app/src/test/resources/schema.sql` 注释仍为 V1–V8 范围，未覆盖 V14–V18（工作空间 RBAC、导航、计费模型、通知升级等）。

**影响：** 单元测试在 H2 上 **无法验证** 新表相关 Repository 行为，回归风险集中在集成环境。

#### 问题 D：租户字段与隔离策略脱节（严重）

多数业务表含 `tenant_id` 列，部分 Repository（如 `WorkspaceRepository`、`QuotaUsageRepository`）在查询中 **显式** 使用 `tenant_id` 条件；但平台级 **未** 在数据访问层统一强制租户过滤（见 `01-production-blockers.md` #2），`TenantContext` 仅存在于请求上下文。

**影响：** 可扩展的多租户模型在表结构上已预留，但 **安全隔离未闭环**，任意可伪造 Header 的调用方可能跨租户读写的风险仍在。

#### 问题 E：内存存储与 DB Schema 并行（严重）

Flyway 已为 prompt、entitlement、billing、policy 等域建表，但多处实现仍使用 `ConcurrentHashMap` 作为权威存储，例如：

- `LocalFeatureFlagProvider`、`FeatureFlagService`
- `PolicyGovernanceService`、`PolicyEvaluationService`
- NLQ 相关 `ReportDefinitionService`、`QueryHistoryService` 等
- 文档已列出的 `prompt-module`、`entitlement-module`、`billing-module` 信用钱包

**影响：** 重启丢状态、与 Schema **双轨运行**，增加迁移到 DB 时的行为差异与测试矩阵复杂度。

---

## 4. 模块设计与可扩展性

### 4.1 架构优势

1. **Port/Adapter + SPI：** 渲染 Provider、支付、通知、云资源、AI 等均可插拔，符合 **开闭原则**。
2. **Transactional Outbox：** `outbox-event-module` 支持跨模块最终一致性，为将来拆分为独立服务预留通道。
3. **GraphQL 聚合层：** 读路径与写路径分离，前端可减少 REST 往返，读模型可独立演进。
4. **Spring Modulith 元数据：** `@ApplicationModule`、`@NamedInterface` 明确公开 API，理论上支持 Modulith 文档与违规检测。
5. **独立渲染 Worker：** `remote-render-worker` 可单独容器化，媒体重负载与 API 层解耦。

### 4.2 模块边界被削弱（严重）

`ModularityTest` 对 **13 类** 违规进行了 filter，而非修复依赖方向，例如：

- `render` → `extension` / `billing` / `entitlement` / `audit`
- `web` → `render` / `prompt` / `identity` 的非暴露类型
- `app` / `security` → `identity` 非暴露类型

```java
// platform-app/src/test/java/com/example/platform/ModularityTest.java（节选）
.filter(it -> !it.toString().contains("Module 'render' depends on module 'extension'"))
// ... 共 13 条 filter ...
```

**影响：**

- **可扩展性：** 难以按模块边界拆分为微服务或独立部署单元；
- **可维护性：** 新功能倾向于继续「穿透」调用内部包，技术债复利；
- **可测试性：** 模块替换/mock 边界模糊。

**建议方向：** 将 filter 视为 **临时豁免清单** 并逐项消除；`platform-app` 聚合层只依赖各模块的 `api`/`spi` 包。

### 4.3 孤立模块

`compatibility-migration-module` 未加入 `platform-app/build.gradle.kts`，运行时 **不可用**。若其职责是 Schema 族迁移工具，应明确是 CLI 工具模块还是应接入主应用；当前状态增加 **认知负担**。

### 4.4 功能完成度矩阵

| 域 | 完成度 | 说明 |
|----|--------|------|
| 渲染管线（6 Provider、配额、历史） | **高 (~85%)** | 核心路径与测试较多 |
| 前端视频编辑器 / 时间线 / 导出 | **高 (~80%)** | 路由与组件齐全，Vitest 覆盖广 |
| GraphQL 聚合 / NLQ | **中高 (~70%)** | Resolver 与测试丰富，部分存储内存化 |
| 权益 / 配额 / Commerce API | **中 (~60%)** | API 与模型完整，持久化与调度未闭环 |
| 认证 / 多租户隔离 | **低 (~25%)** | JWT/Security 已实现但默认 `app.security.enabled: false` |
| 支付 / AI | **低 (~15%)** | 全 Stub/Noop |
| Feature Flag / Policy | **中 (~50%)** | 逻辑完整，持久化与远程 Provider 未接 |
| 社交发布 | **中 (~55%)** | 前后端有实现，平台适配器 Stub，Schema 管理混乱 |
| 沙箱运行时 | **低 (~20%)** | 占位实现 |
| 兼容性迁移工具 | **未接入 (0%)** | 模块存在但未装配 |

**综合功能完成度：** 约 **75%**（按「用户可见功能 + API 存在」计）；**生产可用完成度：** 约 **35%**（按 Critical 阻断 + 持久化 + 安全计）。

---

## 5. 可维护性评估

### 5.1 优势

| 方面 | 表现 |
|------|------|
| 文档体系 | `docs/` 覆盖架构、模块、API、部署、review，Prompt 66 重建质量高 |
| 错误处理 | `PlatformException` + `ProblemDetail` + `ErrorCodeRegistry` + i18n，统一且可观测 |
| 测试规模 | ~219 后端测试类、前端 78+ spec；`federation-query`、`policy-governance` 测试密度高 |
| 质量门禁 | `gradlew test`、`bootJar`、`infra-validate.sh`、前端 build/test 有历史记录 |
| 代码生成 | jOOQ 与 Flyway 对齐，减少手写 SQL 错误（在已迁移域） |

### 5.2 风险

| 风险 | 严重度 | 说明 |
|------|--------|------|
| ModularityTest 过滤 | 高 | 边界约束名存实亡 |
| Schema 三轨制 | 高 | Flyway / 模块 SQL / Bootstrap |
| 内存与 DB 双轨 | 高 | 行为与运维不可预测 |
| 无 Testcontainers 集成测试 | 中 | 仅 H2 单元测试，PostgreSQL 方言与约束未验证 |
| `platform-app` 胖应用层 | 中 | 聚合控制器增多，与模块 API 重复风险 |
| 文档统计过时 | 中 | `02-project-status.md` 与实测偏差大 |
| 包结构不一致 | 低 | 见 technical-debt 清单 |

### 5.3 可维护性评分说明（3.0/5）

团队 **能** 在现有文档与模块命名下定位功能，但 **不能** 仅依赖 ModularityTest 保证依赖健康；Schema 与存储一致性问题会在每次新域（如 social-publish）时放大维护成本。

---

## 6. 可观测性评估

### 6.1 已实现能力

| 能力 | 实现 | 评价 |
|------|------|------|
| 结构化日志 | `logback-spring.xml` JSON + MDC（traceId, requestId, tenantId, projectId） | 良好 |
| 请求关联 | `PlatformTraceCorrelationFilter`、`PlatformRequestContextFilter` | 良好 |
| 指标导出 | Actuator `metrics`/`prometheus`，业务指标如 `render.jobs.*`、`outbox.*` | 良好 |
| 错误上报 | Sentry（后端配置 + 前端 `@sentry/vue`） | 良好 |
| 会话回放 | OpenReplay 配置项 | 依赖部署配置 |
| 业务概览 API | `GET /api/v1/observability/overview` | 便于运营面板 |

### 6.2 缺口与文档不一致

| 项目 | 文档描述 | 代码现状 | 影响 |
|------|----------|----------|------|
| OpenTelemetry | `09-observability-quality/01-observability.md` 标注 OTel 依赖「未添加」 | `application.yml` 已配置 OTLP endpoint | 配置存在但链路可能未真正导出 |
| 自定义 HealthIndicator | 文档列出 `DataSourceHealthIndicator`、`OutboxHealthIndicator` | 代码库 **无** 对应 Java 类 | K8s readiness 无法反映 DB/发件箱积压 |
| 第三方健康 | `ThirdPartyProviderHealthService` | 内存 Map 跟踪 SLA，非真实 probe | 熔断决策可能脱离实际 |
| 分布式追踪 | MDC 级 traceId | 无完整 OTel SDK 集成 | 跨服务（render-worker）关联困难 |
| 审计覆盖 | 部分服务未调用 `AuditPort` | 合规追溯不完整 | 中 |

### 6.3 可观测性评分说明（3.0/5）

**日志与指标** 已达到可运维基线；**追踪与健康检查** 未形成闭环，文档超前于实现，SRE 按文档搭建监控会遇到 **预期落差**。

---

## 7. 不合理设计汇总与优先级

### 7.1 必须纠正（P0 — 生产阻断）

与 `01-production-blockers.md` 一致，不再赘述细节：

1. 默认关闭认证（`app.security.enabled: false`）
2. 租户隔离未在数据层强制
3. 支付全 Noop
4. AI 全 Stub
5. Feature Flag 仅内存

### 7.2 架构级不合理（P1 — 上线前应规划）

| # | 问题 | 建议 |
|---|------|------|
| 1 | Flyway 所有权分散 + Bootstrap 建表 | 单一迁移目录；禁止运行时 DDL |
| 2 | ModularityTest 长期 filter | 建立「豁免清单」工单，逐条消除 |
| 3 | 内存存储与 Flyway 表并行 | 选定权威存储，分批迁移至 Repository |
| 4 | `compatibility-migration-module` 未接入 | 明确产品化路径或从 settings 移除 |
| 5 | 文档指标与代码规模严重偏离 | 更新 `02-project-status.md` 与 README 统计 |

### 7.3 工程实践不合理（P2 — 持续改进）

| # | 问题 | 建议 |
|---|------|------|
| 6 | 无 PostgreSQL 集成测试 | 引入 Testcontainers + 与 V18 同步的 fixture |
| 7 | H2 test schema 滞后 | 生成脚本从 Flyway 导出或 CI 跑迁移 |
| 8 | 可观测性文档与实现不一致 | 删除或实现 HealthIndicator；明确 OTel 路线图 |
| 9 | `social-publish` 平台适配器 Stub | 与支付/AI 同级标注为 Partial |
| 10 | 速率限制与 Remote Worker 注册使用内存 Map | 多实例部署时不一致 |

---

## 8. 改进路线图（建议，非实施承诺）

```mermaid
graph LR
    P0[P0 安全与商业 Stub] --> P1[P1 Schema 与模块边界]
    P1 --> P2[P2 测试与可观测闭环]
    P2 --> P3[P3 拆分准备]
```

| 阶段 | 目标 | 关键动作 |
|------|------|----------|
| **P0** | 可安全试运行 | 启用 Security + JWT；Repository 层租户拦截器；Feature Flag 持久化 |
| **P1** | 数据与边界可信 | 统一 Flyway；消除 ModularityTest filter；内存域迁 DB |
| **P2** | 质量可证明 | Testcontainers；同步 test schema；实现 HealthIndicator + OTel |
| **P3** | 可拆分演进 | 强化模块 API；明确 worker 部署拓扑；评估 social/notification 独立服务 |

---

## 9. 结论

Media Platform 在 **表设计域划分**、**模块包结构约定**、**SPI 扩展点** 和 **文档化** 上体现了较好的架构意图，适合作为「模块化单体 → 可拆分服务」的演进起点。当前主要不合理之处集中在 **执行层与意图层的落差**：模块边界测试被豁免、Schema 与存储多轨、安全与多租户未闭环、可观测性文档超前于代码。

**若目标为生产环境：** 应先完成 P0（5 项 Critical），再推进 P1 的统一迁移与边界收紧；不宜仅依据「Prompt 66 开发完成」或 README 中的模块/迁移数量判断就绪度。

**若目标为继续功能迭代：** 建议新功能 **禁止** 新增 Bootstrap DDL 与模块内 Flyway；新域表一律进入 `platform-app/db/migration`，并通过 `spi` 暴露模块能力，避免加剧 `web` 包对内部类型的依赖。

---

## 10. 附录：关键证据路径

| 主题 | 路径 |
|------|------|
| 模块列表 | `media-platform/settings.gradle.kts` |
| 主应用依赖（无 compatibility-migration） | `media-platform/platform-app/build.gradle.kts` |
| ModularityTest 过滤 | `media-platform/platform-app/src/test/java/com/example/platform/ModularityTest.java` |
| Flyway 迁移 | `media-platform/platform-app/src/main/resources/db/migration/` |
| 安全配置 | `media-platform/platform-app/src/main/resources/application.yml` |
| 社交 Bootstrap DDL | `media-platform/social-publish-module/.../SocialPublishDataBootstrap.java` |
| 生产阻断清单 | `docs/12-review/01-production-blockers.md` |
| 可观测性文档 | `docs/09-observability-quality/01-observability.md` |

---

*本报告为架构评审产出，不代表对代码库的修改承诺。后续若需按优先级拆分为实施任务单，可基于第 7–8 节逐项展开。*
