> **Status:** Archived (2026-06-22)
> **Reason:** Superseded by `docs/architecture/` series and `docs/modules/` series.
> **Superseded By:** `docs/architecture/02-backend-architecture.md`, `docs/modules/01-core-modules.md`
> **Do not use as current reference.**

---

# 功能分层与开源组件说明

> 全站文档索引见 [docs/README.md](./README.md)。对外 API 版本演进见 [api-versioning.md](./api-versioning.md)。

本文档说明本仓库 **模块内部分层约定**、**聚合模块（platform-app）引入的运行时组件**，以及 **文档/实现上建议补齐的部分**。可与 `README.md`、`architecture-notes.md` 对照阅读。

---

## 1. 模块内部分层（每个业务 Gradle module）

各业务模块在包名上统一采用下列切片（与现有代码目录一致）：

| 包后缀 | 职责 | 典型内容 |
|--------|------|----------|
| `*.api` | 对外边界 | `*Controller`、请求/响应 DTO、Web 层校验注解 |
| `*.app` | 应用服务 | 用例编排、事务边界、调用领域与基础设施 |
| `*.domain` | 领域模型 | 实体、值对象、领域事件、与持久化无关的不变式 |
| `*.spi` | 端口（可插拔） | 接口由 domain/app 依赖，实现在 `infrastructure` |
| `*.infrastructure` | 适配器实现 | 外部系统、Noop/假实现、SDK 封装 |

### 设计意图

- **api** 只负责 HTTP/OpenAPI 与入参出参形状，避免把厂商 DTO 泄漏到 domain。
- **app** 承担「编排」：跨聚合的步骤、幂等、与 Outbox/审计 的衔接（骨架阶段可逐步落地）。
- **domain** 保持可测试、不依赖 Spring Web 或具体数据库 API。
- **spi / infrastructure** 把「换 Stripe / 换对象存储」变成换适配器，而不是改核心模型。

### 与 Spring Modulith 的关系

- 根应用使用 `@Modulith`（见 `PlatformApplication`），按 `com.example.platform.<segment>` 识别逻辑模块。
- 各模块根包上的 `@ApplicationModule(displayName = "...")` 用于文档化与校验；**Shared Kernel** 使用 `type = OPEN`，表示可被各模块直接使用的公共类型，且不强调隐藏内部包（详见 [ApplicationModule.Type](https://docs.spring.io/spring-modulith/docs/current/api/org/springframework/modulith/ApplicationModule.Type.html)）。
- 后续若要加强边界，可在 `api` 包上使用 `@NamedInterface("API")`，并在 `@ApplicationModule` 上声明允许的依赖方向（`allowedDependencies`）。

---

## 2. platform-app 聚合的开源运行时（实际依赖）

以下依赖声明在 `platform-app/build.gradle.kts`，代表 **可运行的单体** 选用栈；各子模块默认 **不** 重复声明同一运行时，避免 classpath 与版本漂移。

| 组件 | 版本（以构建脚本为准） | 角色 |
|------|------------------------|------|
| Java | **25**（Gradle [Java toolchain](https://docs.gradle.org/current/userguide/toolchains.html)，Foojay 自动解析） | 编译/测试/运行目标版本 |
| Gradle Wrapper | **9.1** | 满足「在 JVM 25 上执行 Gradle」的 [官方要求](https://docs.gradle.org/current/userguide/compatibility.html) |
| Spring Boot | 4.0.4（BOM） | 核心运行时、Web、Actuator、Validation、JDBC、jOOQ（[4.0 系统要求](https://docs.spring.io/spring-boot/4.0/system-requirements.html)，含 Java 25）；版本策略见 [architecture-notes.md](./architecture-notes.md#spring-boot-pinned-version-vs-latest-releases) |
| Spring AI BOM | **2.0.0-M3**（Milestone）+ `spring-ai-starter-model-openai` | 与 Boot 4 对齐；**尚无面向 Boot 4 的 Spring AI GA BOM**，生产环境请自行评估里程碑风险 |
| Spring Modulith | 2.0.4（GA，`starter-core` 与 Boot 4.0.4 对齐；含 starter-test / insight） | 模块化单体边界与可选 runtime insight |
| Temporal | `temporal-spring-boot-starter` 1.33.0 | 长事务、编排类工作流 |
| springdoc OpenAPI | 3.0.2 | OpenAPI 3 / Swagger UI（Boot 4 需 3.x） |
| LiteFlow | 2.15.3.2 | 本地规则链、路由与轻量策略 |
| PF4J | 3.15.0 | JVM 插件扩展（与 `extension-module` 方向一致） |
| Flyway | BOM 管理 | 架构迁移 |
| H2 | runtimeOnly | 本地默认内存库（`application.yml` 中 PostgreSQL 兼容模式） |

### 合理性简评

- **Temporal + LiteFlow 分工**：长寿命、需持久与补偿的流程交给 Temporal；短分支、可本地执行的规则交给 LiteFlow——与 `architecture-notes.md` 一致，避免 LiteFlow 承载重型编排。
- **jOOQ + 多数据源**：适合复杂 SQL 与显式 SQL 审查；代价是代码生成与构建链路要纳入团队规范（见下文「待补充」）。
- **Spring AI**：作为统一 AI 客户端抽象合理；多厂商时建议在 `ai-module` 内保持自有 SPI，Spring AI 作为 adapter 之一而非唯一抽象。
- **PF4J**：与「插件治理」目标一致；需另文约定插件隔离、签名与类加载安全（README 已提示 GraalVM/独立进程等分级策略）。

### 特性开关（OpenFeature + Unleash）与 Temporal

- **`policy-governance-module`**：`dev.openfeature:sdk` + `dev.openfeature.contrib.providers:unleash`（**alpha** 构件，升级前请看 Release Notes）。配置前缀 **`app.features.unleash`**；`enabled: false` 时使用 **InMemoryProvider**（仅默认值）。
- **模块 API**：`com.example.platform.policy.api` 包标记为 **`@NamedInterface("feature-flags")`**，对外类型如 **`FeatureFlagEvaluator`**；其它模块（如 **`workflow-module`**）只依赖该接口，满足 Modulith 边界。
- **Temporal**：在 **Activity** 中调用 `FeatureFlagEvaluator`，**不要**在 Workflow 实现里直接访问 OpenFeature/Unleash。启用 Worker 需配置 **`spring.temporal.connection.target`** 等（见 `application.yml` 末尾注释示例）。

### `extension-module`：配置驱动 CLI（推荐）

`app.cli-tools` 下用 **`executables`（路径白名单）** 与 **`tools`（executable-key + 参数模板 `{占位符}` + 超时）** 描述外部命令；运行时通过 `POST /api/v1/extensions/cli-tools/{toolKey}/run` 只传参、不传任意路径。配置中心或数据库可提供 **与 YAML 同结构的配置快照**，由自定义 `PropertySource` 或定时刷新加载，无需改 Commons Exec 调用代码。

### 全仓库 `compileOnly`：spring-modulith-api

根 `build.gradle.kts` 为所有子工程增加 `compileOnly("org.springframework.modulith:spring-modulith-api")`，使各模块 `package-info.java` 可声明 `@ApplicationModule`，而 **不** 把 Modulith 运行时强绑到每个库模块。

---

## 3. 文档或 README 中提到、但未作为默认依赖引入的组件

下列技术在 README / 架构笔记中作为 **演进选项** 出现，**当前 `build.gradle.kts` 未默认引入**。在对外说明或对内 onboarding 时应明确「规划项 vs 已落地」：

| 技术 | 说明 |
|------|------|
| OpenTelemetry / LGTM | 建议在 `observability-module` 中封装导出与业务字段规范；需额外依赖与 Collector 配置。 |
| Apache Calcite / Trino | 仅 `federation-query-module` 占位；默认业务仍应「一数据源一 Repository 包」。 |
| Kill Bill / Hyperswitch / Medusa | 商业化扩展路径，见 `docs/external-billing-integrations.md`；代码中为 Noop/占位适配器。 |
| Novu / Knock / Courier 等通知编排 | 可选外部工作流与多通道；与 `NotificationProvider` SPI 对齐方式见 [notification-integrations.md](./notification-integrations.md)。 |
| Crossplane、Wasm 运行时 | 路线图与 `sandbox-runtime-module` 预留，未默认启用。 |
| Terraform / Pulumi / Helm 等 IaC | 非 Gradle 依赖；存放策略与多云边界见 [infrastructure-as-code.md](./infrastructure-as-code.md)。 |

---

## 4. 可改进的设计方案（可选演进）

1. **错误与领域结果**  
   统一在 `shared-kernel` 中定义 `ProblemDetail` / 错误码与「应用结果」类型，避免各模块重复定义异常策略。

2. **跨模块调用**  
   优先 **领域事件 + Outbox**（`outbox-event-module`）替代模块间直接 Bean 调用；若必须同步查询，通过对方模块暴露的 **稳定应用服务接口** 或只读端口，并纳入 Modulith 依赖规则。

3. **commerce / payment / billing / entitlement**  
   已与外部系统解耦方向正确；补充 **幂等键、Webhook 原始报文存储、对账任务** 的 DDL 与流程说明，并在文档中固定「支付成功 ≠ 授权生效」的时序图。

4. **jOOQ 工程化**  
   在 CI 中固定代码生成任务、目标包（如 `*.infrastructure.jooq`），并与 Flyway 版本对齐，避免「手写 DSL 与数据库漂移」。

5. **CI 与本地 JDK**  
   若 CI 未预装 JDK 25，依赖 `settings.gradle.kts` 中的 Foojay Toolchains 插件在首次构建时拉取发行版；也可在流水线镜像中固定 `JAVA_HOME` 指向 Temurin 25。

---

## 5. 与现有文档的索引

| 文档 | 内容 |
|------|------|
| `architecture-notes.md` | Temporal / LiteFlow / Outbox / API 版本等核心规则 |
| `external-billing-integrations.md` | Kill Bill、Hyperswitch、Medusa 的定位与接入 |
| `notification-integrations.md` | Novu 及通知类备选方案、与 SPI 的集成方式 |
| `infrastructure-as-code.md` | IaC 存放位置、多云差异、Crossplane 与 `cloud-resource-module` |
| `skeleton-gap-priorities.md` | 相对设计目标的 P0–P3 缺口排序与验收提示 |
| `runbook-five-capabilities.md` | CI / Outbox / 可观测 / 审计 / API Key 的运行与 curl 验收 |
| `asdf-vm.md` | 可选：asdf 统一本机 JDK 与 `.tool-versions` |
| `commerce-payment-billing-entitlement.md` | 四模块边界与事件流 |

---

## 6. 建议的后续实现清单（工程化）

**按优先级展开的缺口表与验收提示**见 [skeleton-gap-priorities.md](./skeleton-gap-priorities.md)（P0–P3）。下列勾选可与该文档对照使用：

- [ ] 为 Modulith `verify()` 配置允许的例外或逐步收紧依赖规则（随代码从骨架变为生产）。
- [ ] 在 `observability-module` 增加 OTel 可选 starter 与日志 trace 字段对齐 `shared-kernel` 中的 trace keys。
- [ ] 在 `outbox-event-module` 落库表 + 发布器线程，变更以 **`platform-app/.../db/migration/` Flyway** 为准（见 [database-schema.md](./database-schema.md)）。
- [ ] 恢复可用 `gradlew`，并在 CI 中执行 `test`（含 `ModularityTest`）。
