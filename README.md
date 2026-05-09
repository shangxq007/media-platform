# Media Platform Skeleton v5

这是一个面向 **AI 视频生产、渲染编排、通知、多数据源、插件扩展、策略治理、长期平台化演进** 的 Spring Boot / Spring Modulith 工程骨架。

这版把三批模块都纳入了目录与代码骨架，并补上了：
- OpenAPI 分组与版本策略骨架
- `ProblemDetail` 风格的统一异常处理骨架
- JSON 结构化日志配置骨架
- PostgreSQL DDL 草案
- 未来优化路线（Wasm、联邦查询、Crossplane、更多 serverless）

---

## 1. 设计目标

- 语言与框架基线：**Java 25**、**Spring Boot 4.0.4**、**Spring Modulith 2.0.4**、**Gradle 9.1.0**（模块化校验见 `ModularityTest`）
- 先用 **模块化单体** 稳住边界，再按需要拆服务
- 主流程用 **Temporal**，局部策略用 **LiteFlow**
- 数据访问优先 **jOOQ + 多命名数据源**
- 对外接口通过 **springdoc-openapi** 自动生成文档
- AI、通知、存储、扩展、云资源、脚本运行都通过 **SPI / 抽象层** 解耦
- 从第一天就具备 **可观测性、审计、Outbox 事件、错误码、结构化日志**
- **特性开关**：**OpenFeature** + 可选 **Unleash**（`app.features.unleash`）；**Temporal Activity** 通过 `policy.api.FeatureFlagEvaluator` 做灰度，不在 Workflow 内直连开关实现

---

## 2. 模块目录与优先级

### 第一批：强制优先实现（必要）

| 模块 | 作用 | 优先级 | 注意事项 |
|---|---|---:|---|
| `observability-module` | logs / metrics / traces / 告警接入点 | P0 | 推荐接 OpenTelemetry + Grafana LGTM |
| `outbox-event-module` | 领域事件、Outbox、重试、幂等 | P0 | 不要让模块直接互相强耦合调用 |
| `audit-compliance-module` | 审计、配置变更、手工操作留痕 | P0 | 审计日志与普通技术日志要分流 |

### 第二批：强烈建议（平台治理）

| 模块 | 作用 | 优先级 | 注意事项 |
|---|---|---:|---|
| `identity-access-module` | 租户、用户、服务账号、API Key、权限 | P1 | 对外 OpenAPI、Webhook、n8n 接入都依赖它 |
| `scheduler-module` | 周期任务、清理任务、补偿扫描 | P1 | 不是所有周期逻辑都适合直接放 Temporal |
| `quota-billing-module` | 配额、用量、阈值、未来计费 | P1 | 即使暂时不计费，也建议先做配额模型 |


### `commerce-module`
- 平台自己的 canonical product / order / checkout / purchase 模型
- 不直接把 Stripe、Apple、Google、Medusa 的对象当成内部核心模型
- 后续若接更多支付方式，优先扩 adapter，不改内部产品与权益模型

### `payment-module`
- 负责 payment provider SPI、支付意图、支付尝试、Webhook 接入、校验与重试
- 后续可按需集成 Hyperswitch 做支付编排层
- App 内购（Apple / Google）仍建议保留独立 adapter

### `billing-module`
- 负责订阅、账单、发票、支付状态投影与账务解释
- 后续可按需集成 Kill Bill 做更复杂的 recurring billing 能力
- 账务状态变化通过事件驱动 entitlement 重算

### `entitlement-module`
- 负责最终功能授权、配额档位、宽限期、override、解释能力
- 不直接依赖任一支付平台对象
- 统一 web / iOS / Android / future provider 的权益判定


### 第三批：先写接口和文档（长期预留）

| 模块 | 作用 | 优先级 | 注意事项 |
|---|---|---:|---|
| `policy-governance-module` | 策略版本、灰度、解释、冲突检测 | P2 | LiteFlow、模型路由、通知路由会逐步用到 |
| `artifact-catalog-module` | 统一登记视频/字幕/封面/包等产物 | P2 | 和 `asset` / `render` 分工要写清楚 |
| `sandbox-runtime-module` | Wasm / 不可信脚本 / 沙箱运行时预留 | P2 | 当前只做接口位，不默认启用 |
| `federation-query-module` | Calcite/Trino 等联邦查询接入位 | P2 | 主业务多数据源先不要用 Calcite 复杂化 |

### 现有核心业务与平台模块

```text
media-platform/
├─ platform-app/                     # Spring Boot 启动模块，聚合配置、OpenAPI、异常、日志
├─ shared-kernel/                    # 公共工具、错误码、日志上下文字段、共享值对象
├─ render-module/                    # 渲染任务与策略入口
├─ notification-module/              # 通知事件、模板、provider SPI、webhook 签名
├─ ai-module/                        # AI gateway 与 provider SPI
├─ config-module/                    # 非敏感配置与动态配置查询
├─ workflow-module/                  # Temporal workflow 骨架
├─ storage-module/                   # 统一本地/远程/对象存储抽象
├─ prompt-module/                    # Prompt 模板、版本、渲染与执行日志
├─ cloud-resource-module/            # 多云资源抽象（bucket/queue/function/cdn 等）
├─ secrets-config-module/            # Secret 解析与敏感配置访问
├─ extension-module/                 # 插件治理、脚本执行、命令执行、PF4J 集成位
├─ datasource-module/                # 多数据源注册、DSLContext 注册、联邦查询抽象
├─ observability-module/             # 可观测性模型与入口
├─ outbox-event-module/              # 领域事件与 Outbox
├─ audit-compliance-module/          # 审计与合规
├─ scheduler-module/                 # 统一调度登记与周期作业
├─ identity-access-module/           # 身份、权限、API Key、服务账号
├─ quota-billing-module/             # 配额、计量、阈值
├─ commerce-module/                  # canonical product / order / checkout / purchase
├─ payment-module/                   # provider adapter / webhook / payment attempt
├─ billing-module/                   # subscription / invoice / recurring billing projection
├─ entitlement-module/               # 最终功能授权、quota profile、override
├─ policy-governance-module/         # 策略治理与 explain
├─ artifact-catalog-module/          # 产物目录与依赖关系
├─ sandbox-runtime-module/           # Wasm / 沙箱运行时预留
├─ federation-query-module/          # 联邦查询适配位
└─ docs/
   ├─ README.md                           # 文档索引（优先阅读）
   ├─ architecture-notes.md               # 架构说明与演进笔记
   ├─ layering-and-open-source.md         # 分层、开源栈、特性开关、CLI、多数据源
   ├─ api-versioning.md                   # HTTP API 版本管理可选方案与推荐组合
   ├─ spring-boot-4-upgrade-notes.md    # Boot 4 / Modulith 2.x / 周边依赖
   ├─ commerce-payment-billing-entitlement.md
   ├─ external-billing-integrations.md
   ├─ notification-integrations.md         # 通知：Novu / 备选方案与 SPI 集成
   ├─ infrastructure-as-code.md           # IaC 放哪、多云差异、Crossplane
   ├─ skeleton-gap-priorities.md          # 骨架缺口 P0–P3 优先级清单
   ├─ docker-external-config.md           # Docker / prod profile / 外部配置与卷
   ├─ runbook-five-capabilities.md        # 五项横切能力：运行与验收（curl / 配置）
   ├─ asdf-vm.md                          # 可选：asdf 与本机 JDK / .tool-versions
   ├─ database-schema.md                # 库结构：以 Flyway 为准（约定说明）
   └─ ddl-postgresql.sql                  # PostgreSQL 草案（非权威，见 database-schema.md）
```

---

## 2.1 文档导航（补充）

- **索引入口**：[`docs/README.md`](docs/README.md)（所有 Markdown / DDL 一览表与说明）。
- **分层与运行时依赖**（含 OpenFeature、Temporal、配置驱动 CLI）：[`docs/layering-and-open-source.md`](docs/layering-and-open-source.md)。
- **Boot / Modulith 版本**：[`docs/architecture-notes.md`](docs/architecture-notes.md) 与 [`docs/spring-boot-4-upgrade-notes.md`](docs/spring-boot-4-upgrade-notes.md)。
- **对外 API 版本策略**（路径 / Header / springdoc 多分组等）：[`docs/api-versioning.md`](docs/api-versioning.md)。
- **数据库结构**：以 Flyway 迁移为准，约定见 [`docs/database-schema.md`](docs/database-schema.md)。
- **通知技术选型**（Novu、Knock、Courier、直连 ESP/FCM 等）：[`docs/notification-integrations.md`](docs/notification-integrations.md)。
- **基础设施即代码**（同仓 vs 独立仓、能否抹平云差异）：[`docs/infrastructure-as-code.md`](docs/infrastructure-as-code.md)。
- **骨架缺口优先级**（P0–P3 排期与验收）：[`docs/skeleton-gap-priorities.md`](docs/skeleton-gap-priorities.md)。
- **Docker 与外部配置**（`prod`、环境变量、`spring.config.import`、存储卷）：[`docs/docker-external-config.md`](docs/docker-external-config.md)。
- **五项能力运行与验收手册**（CI、Outbox、可观测、审计、身份）：[`docs/runbook-five-capabilities.md`](docs/runbook-five-capabilities.md)。
- **asdf-vm 与本机 JDK**（`.tool-versions`、与 Gradle/CI 关系）：[`docs/asdf-vm.md`](docs/asdf-vm.md)。
- 与本文「规划中的技术」（Calcite、OTel、Kill Bill 等）对照时，请以 **Gradle 依赖与 `docs/`** 为准，避免将路线图误认为已默认启用。

---

## 3. 各模块功能说明与实现注意事项

### `datasource-module`
- 负责多个命名数据源的注册和管理
- 每个数据源应有独立 `DataSource`、独立 `DSLContext`、独立事务边界
- 主业务默认不引入 Calcite
- 只有出现明显的跨异构只读联邦查询需求时，才考虑 `federation-query-module`

### `extension-module`
- 统一管理 JVM 插件、脚本扩展、外部命令、外进程插件
- 外部进程：`Apache Commons Exec`（`CommonsExecToolRunner` / `CommonsExecProcessExecutor`）
- **推荐**：配置驱动 CLI（`app.cli-tools`：`executables` 白名单路径 + `tools` 参数模板 `{占位符}`）→ `GET /api/v1/extensions/cli-tools`、`POST /api/v1/extensions/cli-tools/{toolKey}/run`；配置中心 / DB 只需提供同源数据即可，不必改执行代码
- **慎用**：`POST /api/v1/extensions/tool-run` 直接执行请求体中的可执行路径（须鉴权与白名单）
- 建议：正式 Java 扩展优先 PF4J，小脚本优先 GraalVM，重依赖插件走独立进程/RPC
- 不要在业务模块里直接到处 `ProcessBuilder`

### `storage-module`
- 统一本地文件、对象存储、远端存储抽象
- 后续可支持冷热分层、预签名 URL、缓存、生命周期策略
- 业务代码不应直接依赖某个云厂商 SDK

### `prompt-module`
- 负责 Prompt 模板、版本、变量绑定、执行日志
- Prompt 不能只是一段字符串，要可审计、可回放、可 patch

### `notification-module`
- 负责事件转通知、模板渲染、渠道路由、Webhook/邮件/短信等投递
- 对外 webhook 建议统一签名和重试策略
- 可选接入 **Novu** 等外部编排：见 [`docs/notification-integrations.md`](docs/notification-integrations.md)

### `observability-module`
- 负责统一日志字段、埋点规范、未来 OTel/Grafana 接入点
- 推荐 stack：OpenTelemetry + Loki + Tempo + Prometheus/Mimir + Grafana

### `outbox-event-module`
- 所有关键跨模块事件走 Outbox
- 避免 render/publish/notification/ai 直接强依赖

### `audit-compliance-module`
- 关键变更必须审计：配置、prompt、策略、插件启停、人工重试、权限修改
- 审计日志不要混在普通业务日志里

### `sandbox-runtime-module`
- 当前不启用 Wasm，只保留接口和目录
- 未来用于不可信脚本、小型策略、租户自定义逻辑

---

## 4. OpenAPI 与 API 版本策略

### OpenAPI
- 默认通过 springdoc 自动生成，不手写整份规范
- `public-v1` 作为对外分组
- 通过控制器注解、DTO 注解和统一错误模型补充文档质量

### 版本策略
- Public API：路径大版本，例如 `/api/v1/...`
- Internal API：默认向后兼容演进，不强制把版本写进路径
- 异步事件 / Webhook：用 `eventType + eventVersion`
- 真正 breaking change 再开 `v2`

---

## 5. 异常与错误码

- HTTP 层统一用 `ProblemDetail`
- 错误码单独维护，例如：
  - `COMMON-400-001`
  - `AUTH-401-001`
  - `RENDER-409-012`
  - `AI-502-003`
- 异常按层分：业务、校验、集成、基础设施、可重试、致命错误
- 异步任务错误不要只返 HTTP，要写入任务状态与审计

---

## 6. 日志与系统行为追踪

### 日志字段规范
建议所有服务至少输出：
- `traceId`
- `requestId`
- `tenantId`
- `projectId`
- `jobId`
- `workflowId`
- `eventId`
- `errorCode`

### 推荐可观测性栈
- OpenTelemetry Collector
- Loki（日志）
- Tempo（Trace）
- Prometheus / Mimir（指标）
- Grafana（统一查询和仪表盘）

### 查询思路
- 按 `jobId` 查渲染链路
- 按 `eventId` 查通知与 webhook 投递
- 按 `pluginId` 查脚本/插件行为
- 按 `provider` 查 AI fallback 和延迟

---

## 7. 数据库建议

### PostgreSQL vs Supabase
- 核心事务库优先标准 PostgreSQL
- 如果明确要 Auth / Storage / Realtime，再视场景接入 Supabase
- 不要一开始把主数据库设计绑死在某个 BaaS 上

### 单库多 schema vs 多数据库
当前建议：
- **单 PostgreSQL 集群 + 单应用数据库 + 多 schema**
- 模块分 schema，例如：`core / render / notify / ai / ops / ext / billing`
- 真有必要再拆分析库、归档库、向量库

---

## 8. 未来优化点 / 路线图

### 已预留但默认不启用
- Wasm 插件 / `sandbox-runtime-module`
- Calcite / Trino 联邦查询
- Crossplane 多云资源控制平面
- 更完整的 serverless / edge runtime 抽象
- Prompt A/B 实验、策略 explain、更细粒度计量

### 何时值得启用
- 租户自定义脚本开始变多 → 上 Wasm / 更强沙箱
- 跨异构数据源只读分析需求明显增加 → 引入联邦查询层
- 云资源种类和数量持续增长 → 强化 `cloud-resource-module` 并接 Crossplane（IaC 与多云边界见 [`docs/infrastructure-as-code.md`](docs/infrastructure-as-code.md)）

---


## 9. 外部商业化能力集成：Kill Bill / Hyperswitch / Medusa

### 为什么引入它们
这三个都不是平台内部核心域模型的替代品，而是可选的外部能力层：
- **Kill Bill**：更像复杂 recurring billing / invoice 核心
- **Hyperswitch**：更像支付编排与路由层
- **Medusa**：更像 headless commerce / catalog / checkout 层

### 推荐放置位置
```text
commerce-module
  -> medusa-adapter          # 目录/价格/促销/订单增强（可选）
payment-module
  -> hyperswitch-adapter     # 多支付通道编排（可选）
billing-module
  -> killbill-adapter        # 复杂账务与订阅引擎（可选）
entitlement-module
  -> always internal source of truth
```

### 核心规则
- 平台必须保留自己的 canonical product / purchase / subscription / entitlement 模型
- 不要把外部平台对象直接暴露成 public API
- 不要让外部平台直接成为 entitlement source of truth
- 通过本地 projection、mapping、reconciliation 来集成，而不是强耦合引用

### 文档位置
详细说明见：`docs/external-billing-integrations.md`


## 10. 相关官方文档（建议保留）

- Spring Modulith: https://docs.spring.io/spring-modulith/reference/
- Spring Boot Logging: https://docs.spring.io/spring-boot/reference/features/logging.html
- Springdoc OpenAPI: https://springdoc.org/
- OpenFeature: https://openfeature.dev/
- Unleash: https://docs.getunleash.io/
- Spring AI: https://docs.spring.io/spring-ai/reference/
- Temporal Java + Spring Boot: https://docs.temporal.io/develop/java/spring-boot-integration
- LiteFlow: https://liteflow.cc/
- PF4J: https://pf4j.org/
- OpenTelemetry: https://opentelemetry.io/docs/
- Calcite: https://calcite.apache.org/
- Trino: https://trino.io/
- Crossplane: https://docs.crossplane.io/latest/
- Kill Bill: https://killbill.io/
- Hyperswitch: https://hyperswitch.io/
- Medusa: https://medusajs.com/

---

## 11. 本地运行

```bash
./gradlew :platform-app:bootRun
```

### 常用地址
- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI: http://localhost:8080/v3/api-docs
- Health: http://localhost:8080/actuator/health

### 说明
这是一份 **结构完整、接口明确、便于继续迭代** 的 starter 骨架。
它优先强调边界、接口、文档、DDL、日志、异常与演进路线，而不是把所有模块都实现成生产级功能。

### 构建说明
- **Java 25** + **Gradle Wrapper 9.1.0**（见根目录 `gradlew`、`gradle/wrapper/`）。未安装 JDK 25 时，首次构建可通过 `settings.gradle.kts` 中的 **Foojay Toolchains** 约定插件（**1.0.0**）自动解析 JDK。
- 本地命令：`./gradlew test`、`./gradlew :platform-app:bootRun`。
- **Git**：根目录 [`.gitignore`](.gitignore) 忽略 `build/`、`.gradle/`、IDE 与本地密钥等；提交前勿将构建产物或 `.env` 纳入版本库。
- **可选本机 JDK 对齐**：根目录 [`.tool-versions`](.tool-versions) + [`docs/asdf-vm.md`](docs/asdf-vm.md)（asdf-vm 与 Gradle/CI 关系）。

### Docker 镜像
在项目根目录（含 `Dockerfile`、`.dockerignore`）执行：

```bash
docker build -t media-platform:local .
docker run --rm -p 8080:8080 media-platform:local
```

**生产 / 集成环境**建议激活 **`prod` profile**（关闭 H2 控制台、可选加载挂载配置、默认 INFO 日志），并 **必须** 用真实数据源覆盖默认 H2：

```bash
docker run --rm -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e SPRING_DATASOURCE_URL='jdbc:postgresql://db:5432/platform' \
  -e SPRING_DATASOURCE_USERNAME=platform \
  -e SPRING_DATASOURCE_PASSWORD=secret \
  -e APP_STORAGE_LOCAL_ROOT=/data/storage \
  -v my-app-storage:/data/storage \
  media-platform:local
```

- **`APP_STORAGE_LOCAL_ROOT`**：与 [`LocalFsStorageProvider`](storage-module/src/main/java/com/example/platform/storage/infrastructure/LocalFsStorageProvider.java) 的 `app.storage.local-root` 对应；使用本地文件存储时请 **挂载卷**，避免数据只写在容器可写层。
- **`spring.config.import`**：在 `prod` 下可选加载 `/etc/media-platform/application.yaml`（文件可不存在）。详见 [`docs/docker-external-config.md`](docs/docker-external-config.md)。

**Compose 示例**（PostgreSQL + 应用 + 存储卷）：根目录 [`docker-compose.yml`](docker-compose.yml)。

```bash
docker compose up --build
```


## 12. Commerce / Payment / Billing / Entitlement

### `commerce-module`
- 统一产品目录、价格语义、订单与购买意图。
- 对外部系统（Stripe / Apple / Google / Medusa）的商品模型做 canonical mapping。
- 注意：不要把 provider 的 priceId / productId 直接当内部产品主键。

### `payment-module`
- 统一 `PaymentProvider` SPI，处理 checkout、校验、webhook、交易投影。
- 可接 Stripe、Apple、Google、PayPal、Alipay、WeChat Pay，也可把 Hyperswitch 放在此层之下。
- 注意：支付结果只说明“钱的状态”，不直接等于最终权益。

### `billing-module`
- 负责订阅、账单、发票、周期投影和 recurring billing 状态。
- 可接 Kill Bill 等复杂 billing engine，但内部仍保留统一 `BillingState`。
- 注意：账单系统和支付系统要解耦，退款、宽限、试用期都应先落到 billing state。

### `entitlement-module`
- 平台最终授权真相源，面向 feature access / quota profile / override。
- Web、iOS、Android 或其他支付渠道都应统一汇总到这里。
- 注意：角色、支付、账单状态都不应直接替代 entitlement 判定。

### 事件流（简化）
```text
commerce.checkout.requested
  -> payment.checkout.created
  -> payment.succeeded / payment.failed
  -> billing.contract.activated / billing.invoice.updated
  -> entitlement.changed
  -> notification.event.published
```

### 参考文档
- `docs/external-billing-integrations.md`
- `docs/commerce-payment-billing-entitlement.md`
# media-platform
