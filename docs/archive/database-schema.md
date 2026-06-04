# 数据库结构维护约定

## 唯一真相：Flyway

**权威库结构**以 **`platform-app/src/main/resources/db/migration/`** 下的 **Flyway 迁移脚本**为准（`V1__*.sql`、`V2__*.sql`…）。

若你曾在本地对**同一版本号**的旧脚本执行过迁移，后又拉取了修改过的脚本，Flyway 校验可能失败；开发环境可 **`flyway repair`** 或重建库，**不要**在线上改写已发布的迁移版本号内容（应新增更高版本脚本修复）。

- 新增表、改列、索引、约束：**只通过新的版本化迁移**（如 `V6__*.sql`）完成，并与代码（jOOQ 等）同一 MR/发版。
- 本地默认 **H2**、生产可用 **PostgreSQL**；大文本字段建议使用 **`text`**（与 H2 / PostgreSQL 均兼容）。若方言差异，在迁移中写 **兼容两种库的 SQL**，或拆 **可重复迁移** / 环境 profile（按团队规范选择一种并写进本文下方「实践」）。

## `docs/ddl-postgresql.sql` 的定位

该文件为 **历史 / 目标形态参考草案**（含多 schema 命名），**不随部署自动执行**，也**不作为**与线上库对齐的依据。**标记为参考-only，非权威。**

- 若其中设计仍要落地：应 **翻译为新的 Flyway 脚本**，而不是只改 `ddl-postgresql.sql`。
- 可选：在重大里程碑后，用 **`pg_dump --schema-only`** 从「由 Flyway 建好的库」导出一份只读快照，附在文档或 CI 产物中（仍注明以迁移为准）。

## 当前迁移清单

| 版本 | 文件 | 说明 |
|------|------|------|
| V1 | `V1__init.sql` | 核心表：render_job, notification_event, notification_template, notification_delivery, config_item |
| V2 | `V2__platform_v2.sql` | 扩展表：storage_object, prompt_template, prompt_execution_log, cloud_resource_definition, secret_ref, extension_definition, extension_invocation, app_datasource |
| V3 | `V3__platform_v3.sql` | 运营表：outbox_events, audit_records, schedules, quota_definitions |
| V4 | `V4__commerce_billing_entitlement.sql` | 商务/计费/权益表：commerce_product, commerce_price, provider_product_mapping, checkout_session, purchase_order, payment_attempt, provider_webhook_event, subscription_contract, billing_invoice, feature_definition, feature_bundle, feature_bundle_item, entitlement_grant, entitlement_override |
| V5 | `V5__outbox_audit_enhancements.sql` | outbox_events 增强（retry_count, next_attempt_at, idempotency_key）；audit_records 增强（category） |
| V6 | `V6__indexes_and_constraints.sql` | 性能索引基线：外键索引、状态+时间复合索引、查询模式索引 |
| V7 | `V7__identity_render_artifact.sql` | 身份与工件表：tenant, project, user, api_key, artifact, notification_record；render_job 增强列 |
| V8 | `V8__quota_usage_and_render_history.sql` | 配额用量表：quota_usage（租户+功能码维度累计用量） |

## 索引规范

索引命名约定：`ix_<table>_<column>[_<column>]`

- **外键列**：始终创建索引（JOIN / 级联性能）
- **状态 + 时间戳列**：创建复合索引（调度 / 轮询查询模式）
- **唯一约束**：数据库自动创建隐式索引，不重复添加
- 所有索引使用 `IF NOT EXISTS` 保证幂等性

## 命名约定

- 表名：小写 + 下划线（`render_job`, `outbox_events`）
- 列名：小写 + 下划线（`created_at`, `template_code`）
- 主键：`id`，类型 `varchar(64)`
- 时间戳：`created_at timestamp not null`
- 状态列：`status varchar(32) not null` 或 `varchar(50)`
- 外键引用列：`<entity>_id varchar(64) not null`

## V7 新增表结构

### tenant
| 列 | 类型 | 约束 |
|----|------|------|
| id | varchar(64) | PK |
| name | varchar(255) | NOT NULL |
| status | varchar(32) | NOT NULL, DEFAULT 'ACTIVE' |
| created_at | timestamp | NOT NULL |

### project
| 列 | 类型 | 约束 |
|----|------|------|
| id | varchar(64) | PK |
| tenant_id | varchar(64) | NOT NULL, FK → tenant |
| name | varchar(255) | NOT NULL |
| description | text | |
| status | varchar(32) | NOT NULL, DEFAULT 'ACTIVE' |
| created_at | timestamp | NOT NULL |

索引：`ix_project_tenant_id`

### user
| 列 | 类型 | 约束 |
|----|------|------|
| id | varchar(64) | PK |
| tenant_id | varchar(64) | NOT NULL, FK → tenant |
| username | varchar(128) | NOT NULL |
| email | varchar(255) | NOT NULL |
| role | varchar(32) | NOT NULL, DEFAULT 'MEMBER' |
| status | varchar(32) | NOT NULL, DEFAULT 'ACTIVE' |
| created_at | timestamp | NOT NULL |

索引：`ix_user_tenant_id`

### api_key
| 列 | 类型 | 约束 |
|----|------|------|
| id | varchar(64) | PK |
| tenant_id | varchar(64) | |
| fingerprint | varchar(32) | NOT NULL |
| hashed_key | varchar(128) | NOT NULL, UNIQUE |
| principal | varchar(255) | NOT NULL |
| created_at | timestamp | NOT NULL |
| last_used_at | timestamp | |
| revoked_at | timestamp | |

索引：`ix_api_key_fingerprint`

> **安全说明**：只存储 API Key 的 SHA-256 哈希值和指纹。明文 Key 仅在创建时返回一次，绝不持久化。

### artifact
| 列 | 类型 | 约束 |
|----|------|------|
| id | varchar(64) | PK |
| render_job_id | varchar(64) | NOT NULL |
| project_id | varchar(64) | NOT NULL |
| storage_uri | text | NOT NULL |
| format | varchar(32) | |
| resolution | varchar(32) | |
| duration | bigint | |
| created_at | timestamp | NOT NULL |

索引：`ix_artifact_render_job_id`, `ix_artifact_project_id`

### notification_record
| 列 | 类型 | 约束 |
|----|------|------|
| id | varchar(64) | PK |
| event_id | varchar(64) | NOT NULL |
| channel | varchar(32) | NOT NULL |
| provider_code | varchar(64) | NOT NULL |
| status | varchar(32) | NOT NULL |
| subject | varchar(512) | |
| body | text | |
| metadata_json | text | |
| attempt_count | int | NOT NULL, DEFAULT 1 |
| created_at | timestamp | NOT NULL |

索引：`ix_notification_record_event_id`, `ix_notification_record_status`

## V8 新增表结构

### quota_usage
| 列 | 类型 | 约束 |
|----|------|------|
| id | varchar(64) | PK |
| tenant_id | varchar(64) | NOT NULL |
| feature_code | varchar(80) | NOT NULL |
| usage_value | int | NOT NULL, DEFAULT 0 |
| created_at | timestamp | NOT NULL |
| updated_at | timestamp | NOT NULL |

索引：`ix_quota_usage_tenant_id`, `ix_quota_usage_tenant_feature`

## 与 `docs/README.md` 的关系

表清单、域划分说明可写在 **本文件** 或 **各域设计文档**；避免在多处手写「当前有哪些表」导致漂移。
