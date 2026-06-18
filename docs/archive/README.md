# 文档索引

> **Important:** 存档文档是历史记录，不是当前真相。请使用 [architecture/current](../architecture/current/) 获取当前状态。

按阅读顺序与主题整理的入口；实现细节以 **Gradle 依赖与源码** 为准。

| 文档 | 说明 |
|------|------|
| [architecture-notes.md](./architecture-notes.md) | Spring Boot / Modulith 版本策略、架构约定 |
| [layering-and-open-source.md](./layering-and-open-source.md) | 模块内分层（api/app/domain…）、platform-app 开源栈、**多数据源**、**OpenFeature + Unleash + Temporal**、**配置驱动 CLI** |
| [spring-boot-4-upgrade-notes.md](./spring-boot-4-upgrade-notes.md) | Boot 4 / Modulith 2.x 基线与周边依赖 |
| [api-versioning.md](./api-versioning.md) | **对外 HTTP API 版本管理**：路径/Header/多文档等可选方案与推荐组合 |
| [commerce-payment-billing-entitlement.md](./commerce-payment-billing-entitlement.md) | 商业域：订单 / 支付 / 账单 / 权益 |
| [external-billing-integrations.md](./external-billing-integrations.md) | Kill Bill、Hyperswitch、Medusa 等外部集成思路 |
| [notification-integrations.md](./notification-integrations.md) | **通知**：Novu 是否可用、与 `NotificationProvider` 的关系、备选（Knock、Courier、FCM、直连 ESP 等） |
| [infrastructure-as-code.md](./infrastructure-as-code.md) | **IaC**：放在本仓还是独立仓、能否抹平云差异、与 `cloud-resource-module` / Crossplane 的关系 |
| [docker-external-config.md](./docker-external-config.md) | **Docker / prod**：`SPRING_PROFILES_ACTIVE`、环境变量、`spring.config.import` 挂载、存储卷 |
| [skeleton-gap-priorities.md](./skeleton-gap-priorities.md) | **缺口清单**：相对设计目标的 P0–P3 优先级排序与验收提示 |
| [runbook-five-capabilities.md](./runbook-five-capabilities.md) | **运行与验收**：CI / Outbox / 可观测 / 审计 / API Key（含 curl 与推荐配置） |
| [asdf-vm.md](./asdf-vm.md) | **asdf-vm**：本地 JDK 与 `.tool-versions` 约定、与 Gradle/CI 的关系 |
| [database-schema.md](./database-schema.md) | **数据库以 Flyway 为准**；`ddl-postgresql.sql` 仅为非权威参考 |
| [ddl-postgresql.sql](./ddl-postgresql.sql) | PostgreSQL 多 schema **草案**（历史/目标参考，**不替代** Flyway） |

## Entitlement & Billing

| 文档 | 说明 |
|------|------|
| [entitlement-policy.md](./entitlement-policy.md) | Tier system, decision priority chain, grant lifecycle, API reference |
| [rbac-abac-access-control.md](./rbac-abac-access-control.md) | RBAC model, ABAC model, decision service architecture |
| [workspace-entitlement-pool.md](./workspace-entitlement-pool.md) | Workspace pool allocation, group grants, quota tracking |
| [quota-policy.md](./quota-policy.md) | QuotaPolicy, QuotaProfile, runtime checks, integration points |
| [export-validation.md](./export-validation.md) | Export validation flow, presets, upgrade options |
| [flexible-billing-models.md](./flexible-billing-models.md) | 7 pricing models, meters, rating engine, ledger |
| [subscription-billing.md](./subscription-billing.md) | Plan lifecycle, quota, trials, cancellation, payment stub |
| [custom-pricing.md](./custom-pricing.md) | Tenant/workspace overrides, discount policies, pricing preview |
| [credit-wallet.md](./credit-wallet.md) | Wallet lifecycle, transaction types, admin management |
| [configurable-navigation.md](./configurable-navigation.md) | Route definitions, decision service, navigation policies |
| [frontend-entitlement-management.md](./frontend-entitlement-management.md) | User/workspace/admin pages, UI integration |
| [production-blockers.md](./production-blockers.md) | Production blockers (8 total, 3 critical) |

**根目录 [README.md](../README.md)**：项目总览、模块清单、本地运行与 Docker。
