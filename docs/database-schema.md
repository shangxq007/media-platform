# 数据库结构维护约定

## 唯一真相：Flyway

**权威库结构**以 **`platform-app/src/main/resources/db/migration/`** 下的 **Flyway 迁移脚本**为准。

当前仅有单一初始迁移 `V1__initial_schema.sql`（67KB），包含全部表结构定义。

- 新增表、改列、索引、约束：**只通过新的版本化迁移**（如 `V2__xxx.sql`）完成，并与代码同一 MR/发版。
- 本地默认 **H2**、生产可用 **PostgreSQL**；大文本字段使用 **`text`**。若方言差异，在迁移中写兼容两种库的 SQL。

## 迁移历史

开发阶段曾有 V1–V25 共 25 个增量迁移脚本，在上线前合并为单一 `V1__initial_schema.sql`。

合并规则：
- 所有 `ALTER TABLE ADD COLUMN` 内联到对应 `CREATE TABLE`
- 移除 `IF NOT EXISTS`（全新 Schema）
- 按领域分组（10 个域）

## 当前 Schema 统计

| 域 | 表数 | 说明 |
|----|------|------|
| 核心基础设施 | 15 | render_job, outbox_events, audit_records, config_item, storage_object 等 |
| 身份访问 | 14 | tenant, project, user, api_key, workspace, RBAC 表 |
| 媒体渲染 | 10 | artifact, timeline_snapshot, timeline_revision, effect_pack, client_export_session, media_asset_metadata |
| 商业计费 | 18 | commerce_product, subscription_contract, credit_wallet, billing_ledger_entry 等 |
| 权益配额 | 12 | entitlement_grant, quota_profile, quota_usage, workspace_entitlement_pool 等 |
| 平台能力 | 17 | prompt_template, extension_definition, sandbox_execution_job, nlq_report 等 |
| 治理合规 | 8 | feature_flag_definition, notification_event_definition 等 |
| 交付发布 | 6 | delivery_destination, social_connected_platform, social_post 等 |
| 分析用户 | 5 | user_behavior_event, user_profile, shared_resource_grant 等 |
| AI/LiteLLM | 1 | tenant_litellm_virtual_key |
| **总计** | **~106** | |

## 索引规范

索引命名约定：`ix_<table>_<column>[_<column>]`

- **外键列**：始终创建索引
- **状态 + 时间戳列**：创建复合索引
- **唯一约束**：使用 `uq_` 前缀
- 总计约 178 个索引

## 命名约定

- 表名：小写 + 下划线（`render_job`, `outbox_events`）
- 列名：小写 + 下划线（`created_at`, `template_code`）
- 主键：`id`，类型 `varchar(64)`
- 时间戳：`created_at timestamp not null`
- 状态列：`status varchar(32) not null`
- 外键引用列：`<entity>_id varchar(64)`

## `docs/ddl-postgresql.sql` 的定位

该文件为 **历史参考草案**，**不随部署自动执行**，**不作为**与线上库对齐的依据。
