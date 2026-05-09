# 骨架缺口清单（按优先级排序）

> **文档导航**：[docs/README.md](./README.md)。数据库变更约定见 [database-schema.md](./database-schema.md)；分层与已有「工程化清单」见 [layering-and-open-source.md](./layering-and-open-source.md) 第 4、6 节。

本文档把当前仓库相对 **设计目标**（见根目录 `README.md` 第 1 节）的缺口 **按优先级排序**，便于排期与验收。优先级含义：

| 级别 | 含义 |
|------|------|
| **P0** | 不补齐则架构承诺（Outbox、可观测、审计）与「可安全演进」难以成立 |
| **P1** | 平台治理与主业务闭环（身份、调度、商业域落地）所需 |
| **P2** | 工程质量、运维与规模化（测试、CI、jOOQ、多云/IaC） |
| **P3** | 路线图与可选能力（README 中已标注的预留项） |

---

## P0 — 必须先补的底座

| 序号 | 缺口 | 说明 / 验收建议 |
|:----:|------|-----------------|
| P0-1 | **`outbox-event-module` 仅有 stub** | 需：**Outbox 表结构**（Flyway 新脚本，权威路径见 [database-schema.md](./database-schema.md)）、**写入与发布器**（轮询或事务消息）、**重试/死信策略**骨架。与 `architecture-notes` 中「Outbox 为默认跨模块机制」对齐。 |
| P0-2 | **`observability-module` 仍为 stub** | 设计目标要求「从第一天具备可观测性」。建议：**OpenTelemetry 可选 starter**、日志字段与 `shared-kernel` trace 键对齐；至少先统一 **traceId 贯通** 与文档化的指标命名约定。 |
| P0-3 | **`audit-compliance-module` 仍为 stub** | 关键操作审计与业务日志分流未落地。需：**审计模型 + 持久化或专用 appender 路径** 的最小闭环（哪怕先只覆盖一类事件）。 |
| P0-4 | **自动化测试极少** | 当前除 `ModularityTest` 与少量单元测试外，**缺少模块级与集成测试**。P0 阶段至少：**CI 固定执行 `./gradlew test`**（含 Modulith 校验），并对 Outbox/审计各加 **最小 happy path 测试**。 |

---

## P1 — 平台治理与核心业务闭环

| 序号 | 缺口 | 说明 / 验收建议 |
|:----:|------|-----------------|
| P1-1 | **`identity-access-module` stub** | 租户、用户、服务账号、API Key、权限模型未实现；对外 API 与 Webhook 鉴权难以统一。 |
| P1-2 | **`scheduler-module` stub** | 周期清理、补偿扫描、Outbox 积压巡检等缺少统一登记点；与 Temporal 分工需在代码与文档中可执行。 |
| P1-3 | **商业域：幂等、Webhook 报文、对账** | `commerce` / `payment` / `billing` / `entitlement` 已有方向与 Noop 适配器，但 **幂等键、Webhook 原始报文存储、对账任务** 的 DDL 与流程仍待补齐（见 [layering-and-open-source.md](./layering-and-open-source.md) §4.3、[commerce-payment-billing-entitlement.md](./commerce-payment-billing-entitlement.md)）。 |
| P1-4 | **`quota-billing-module` stub** | README 标为 P1；即使暂不计费，**配额/用量模型**影响渲染与 AI 网关策略。 |
| P1-5 | **跨模块集成方式未统一** | 在 Outbox 落地前，模块间易退回直接 Bean 调用。应在 **关键路径** 上选定 1～2 条 **事件驱动** 用例打通（例如：订单状态 → 通知），作为模板。 |
| P1-6 | **错误与结果模型统一（可选但强烈建议）** | 各模块异常策略若继续分裂，会增加 API 与排障成本。建议在 `shared-kernel` 收敛 **ProblemDetail / 错误码**（见 [layering-and-open-source.md](./layering-and-open-source.md) §4.1）。 |

---

## P2 — 工程化、质量与运维

| 序号 | 缺口 | 说明 / 验收建议 |
|:----:|------|-----------------|
| P2-1 | **jOOQ 与 Flyway 的工程化** | 以手写 `DSL` 为主时，易与库表漂移。建议在 CI 中固定 **代码生成** 或与迁移 **版本对齐** 的检查（见 [layering-and-open-source.md](./layering-and-open-source.md) §4.4）。 |
| P2-2 | **Modulith 规则随代码收紧** | 为 `ApplicationModules.verify()` 配置 **允许的例外** 或分阶段收紧依赖，避免边界随时间腐化（见 [layering-and-open-source.md](./layering-and-open-source.md) §6）。 |
| P2-3 | **JDK / Gradle 在 CI 中的一致性** | 本地 Java 25 + Foojay Toolchains 已配置；CI 镜像应 **固定 JDK 25** 或等价 toolchain 行为（见 [layering-and-open-source.md](./layering-and-open-source.md) §4.5）。 |
| P2-4 | **通知 / 云资源仍为 stub 实现** | `Email`/`Sms`/`CloudResource` 等多为占位；按业务里程碑替换为真实 **SPI 实现** 或 Novu 等编排（见 [notification-integrations.md](./notification-integrations.md)）。 |
| P2-5 | **IaC 与运行环境文档落地** | 原则见 [infrastructure-as-code.md](./infrastructure-as-code.md)；仓库内可增加 **`infra/` 示例** 或指向独立平台仓，避免「仅有原则无产物」。 |
| P2-6 | **`workflow-module` / Temporal 与业务编排** | 骨架存在；需 **可重复的 Worker 配置、示例 Workflow 与集成测试**，并与特性开关在 Activity 中的用法一致（见 `layering-and-open-source` 中 Temporal 说明）。 |

---

## P3 — 路线图与可选能力（README 已预留）

| 序号 | 缺口 | 说明 |
|:----:|------|------|
| P3-1 | **Wasm / `sandbox-runtime-module`** | 默认不启用；待插件治理成熟。 |
| P3-2 | **联邦查询 / Calcite、Trino** | `federation-query-module` 与 `NoopFederatedQueryGateway`；主业务仍应避免默认引入复杂度。 |
| P3-3 | **Crossplane 与 `cloud-resource-module` 深度集成** | 云资源种类激增时再评估；见 [infrastructure-as-code.md](./infrastructure-as-code.md)。 |
| P3-4 | **Kill Bill / Hyperswitch / Medusa 真实适配器** | 文档完备；实现为可选，见 [external-billing-integrations.md](./external-billing-integrations.md)。 |
| P3-5 | **AI 真实厂商路由与治理** | 当前 `StubChatProvider`；需配额、审计、密钥与模型路由策略与 `ai-module` 文档化。 |

---

## 维护方式

- 完成某项后，可在本表 **「说明」列末尾** 增加简短「✓ 已满足：PR/版本」或改为链接到具体 ADR；避免与 [layering-and-open-source.md](./layering-and-open-source.md) §6 重复维护时，以 **本节为排序视图**、以 layering §6 为 **勾选列表** 亦可。
- 若组织内使用工单系统，建议 **P0 项一一对应 epic/issue**，P1 按域拆分。

---

## 相关索引

| 文档 | 用途 |
|------|------|
| [architecture-notes.md](./architecture-notes.md) | Temporal、LiteFlow、Outbox、API 版本等核心规则 |
| [layering-and-open-source.md](./layering-and-open-source.md) | 分层、开源栈、特性开关、后续工程化条目 |
| [database-schema.md](./database-schema.md) | Flyway 为库结构唯一真相 |
