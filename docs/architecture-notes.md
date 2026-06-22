> **Status:** Archived (2026-06-22)
> **Reason:** Superseded by `docs/architecture/` series and `docs/review/project-intelligence-report.md`.
> **Superseded By:** `docs/architecture/01-system-architecture.md`, `docs/review/project-intelligence-report.md`
> **Do not use as current reference.**

---

# Architecture Notes

> **文档导航**：[docs/README.md](./README.md)（全文档索引）。**HTTP API 版本策略**：[api-versioning.md](./api-versioning.md)。**数据库结构以 Flyway 为准**：[database-schema.md](./database-schema.md)。

> 中文补充：**模块内分层（api / app / domain / spi / infrastructure）**、**platform-app 实际依赖的开源组件**、以及与 README 中「规划但未引入」技术的对照，见 [layering-and-open-source.md](./layering-and-open-source.md)。**骨架缺口优先级（P0–P3）**见 [skeleton-gap-priorities.md](./skeleton-gap-priorities.md)。

---

## Spring Boot: pinned version vs latest releases

**本仓库**在根 `build.gradle.kts` 中固定 **Spring Boot `4.0.4`**（与 **Java 25**、其余依赖一并验证过）。

**上游「最新正式版」如何理解**（Spring 并行维护多条发布线，且 Central 元数据易误导）：

| 含义 | 说明（请以升级当日 [Spring Boot Releases](https://github.com/spring-projects/spring-boot/releases) 为准） |
|------|------------------------------------------------------------------------------------------------------------|
| **4.x 线当前 GA** | **4.0.4** 起为 Spring Boot **4** 的稳定发布系列；其后的 **4.1.x** 在 Central 上常见为 **Milestone**，不算正式版。 |
| **3.5.x 线当前 GA** | **3.5.12** 等为 **3.5** 维护线；本仓库已迁至 **4.0.x**，不再钉在 3.5。 |
| **3.4.x 线** | 仍有 **3.4.13** 等补丁线，最高支持 Java 版本见各版本的 [System Requirements](https://docs.spring.io/spring-boot/system-requirements.html)。 |

**注意**：Maven Central 里 `spring-boot-dependencies` 的 `maven-metadata.xml` 中 `<latest>` / `<release>` 有时会指向 **`-M` / `-RC`** 构件，**不等于「推荐用于生产的最新正式版」**。升级前请对照官方 Release Notes 与 **System Requirements**（Java 版本、Spring Framework 版本等）。

若计划从 **3.5 → 4.0**：属于**大版本迁移**，需单独评估 **Spring Framework 7**、依赖生态（springdoc、Temporal、LiteFlow、Spring AI 等）与测试全量回归，**不要仅改 BOM 版本号**。  
**详细清单与 Modulith 在 Boot 4 下的版本线说明**见 [spring-boot-4-upgrade-notes.md](./spring-boot-4-upgrade-notes.md)。

---

## Spring Modulith: rationale and constraints

**为何在本项目中使用 Modulith**

- 与 **模块化单体** 目标一致：在统一进程内用 **包结构** 表达业务边界，并通过校验避免「启动模块随意依赖各模块内部实现」。
- **`ApplicationModules.verify()`**（见 `platform-app` 的 `ModularityTest`）在 CI 中可自动发现非法跨模块依赖；已促使将仅与某模块相关的启动期逻辑（如通知模板种子数据）收拢到对应模块内。
- 与 **后续可能拆服务** 的路径兼容：边界清晰后，事件与对外 API 更容易稳定下来。

**本仓库当前选型**

- 使用 **Spring Modulith `2.0.4`（GA）**，与 **Spring Boot `4.0.4`** 配套：`spring-modulith-starter-core:2.0.4` 在 Maven Central 上显式依赖 **`spring-boot-starter:4.0.4`**，与本仓库 BOM 一致。
- **Modulith 2.1.x** 面向 Boot 4 的演进线（参考文档 2.1）在 Central 上可能仍有 **Milestone**；若需 2.1 GA 再评估升级路径。
- **历史说明**：在仅使用 **Boot 3.5** 时，部分团队曾报告 **Modulith 2.0.x** 与 3.5 的 `ConfigDataEnvironmentPostProcessor` **二进制不兼容**；当前组合为 **Boot 4 + Modulith 2.0.4**，不再受此约束。
- 各逻辑模块根包使用 `@ApplicationModule`；跨模块共享类型放在 **`shared-kernel`** 并标注为 **`ApplicationModule.Type.OPEN`**，避免与「仅暴露 `api` 包」的默认规则冲突。

**实践约定**

- 优先通过 **`*.api` 包**（或明确的 named interface）暴露给其它模块；启动类所在包避免直接依赖其它模块的 `app` / `domain` 实现类型。
- 修改模块边界后应执行 **`./gradlew test`**，确保 `ModularityTest` 通过。

---

## Core rules
1. Temporal handles durable orchestration.
2. LiteFlow handles local policy/routing decisions.
3. Outbox is the default cross-module integration mechanism.
4. Public APIs use `/api/v1/**`; internal evolution should remain backward-compatible whenever possible.
5. Every long-running job should emit audit records and carry trace correlation identifiers.
6. Multi-datasource is managed via named DataSources and named jOOQ `DSLContext`s.

## Why no Calcite by default
Calcite is reserved for federated read-heavy queries. Mainline business services should stay simpler with one JDBC source per repository package.

## Why Wasm is deferred
Wasm is useful for sandboxing untrusted custom logic, but the current starter only reserves the module and interfaces. It is intentionally not enabled until script/plugin governance matures.


## External commerce/billing/payment integrations
- `commerce-module` owns canonical product, order, and purchase semantics.
- `payment-module` may optionally integrate Hyperswitch as a payment orchestration backend.
- `billing-module` may optionally integrate Kill Bill as a recurring billing/invoice backend.
- `commerce-module` may optionally integrate Medusa for advanced catalog/checkout/promotions.
- `entitlement-module` remains the internal source of truth for access decisions.
- All external systems must be integrated through adapters + local projections + reconciliation.


## Event flow: commerce -> payment -> billing -> entitlement
- `commerce-module` owns canonical products, orders, checkout intents, and provider SKU mapping.
- `payment-module` owns checkout provider orchestration, webhook parsing, transaction verification, and payment attempts.
- `billing-module` owns recurring contracts, invoice projection, and billing state transitions.
- `entitlement-module` consumes billing state and produces final feature/quota snapshots.
- `notification-module` should react to canonical state changes, not raw provider webhooks.

## Notification: Novu and other providers

**可以使用 Novu**（或其它通知编排 SaaS / 自托管方案）作为 **`notification-module` 背后的可选适配器**：通过 HTTP/SDK 触发外部工作流，平台内仍保留事件、投递记录与幂等语义。与直连 SendGrid/Twilio/FCM 等 **不互斥**，可按通道拆分。

**技术选型与备选方案清单**（Knock、Courier、OneSignal、AWS Pinpoint、纯 SPI 直连等）见 [notification-integrations.md](./notification-integrations.md)。

## Infrastructure as Code (IaC) placement and multi-cloud

**IaC 不应写在业务 Java 包内**；宜放在 **同仓库独立目录**（如 `infra/`）或 **独立平台仓库**，与 Gradle 应用模块边界分离。**无法完全抹平云差异**；应在 **应用层 SPI**（如 `cloud-resource-module`）与 **IaC 模块化**（Terraform/Pulumi/Crossplane 等）两层分别收敛，详见 [infrastructure-as-code.md](./infrastructure-as-code.md)。
