# 03 · 仓库与模块

> [← 分卷索引](README.md) | 上一卷：[02-技术栈与依赖](02-dependencies.md) | 下一卷：[04-核心实现](04-implementation.md)

---

## 工作区路径

```
media-platform-workspace/
├── docs/                    # 工作区文档（含本分卷）
├── docs/media-rendering/    # Internal Timeline 等英文规范
└── platform/                # Gradle 单体仓库
    ├── platform-app/
    ├── *-module/
    └── frontend/
```

---

## `platform/` 目录树（摘要）

```
platform/
├── platform-app/          # 组合根：REST、Security、Flyway、静态前端、Spring AI Bean
├── shared-kernel/         # 错误码、租户上下文、共享事件
├── render-module/         # 渲染编排、Internal Timeline、增量、AI 时间线编辑
├── workflow-module/       # Temporal 适配、LiteFlow
├── ai-module/             # ChatProvider SPI、ConfigurableModelRouter
├── storage-module/        # S3/R2/本地 FS
├── delivery-module/       # 出站交付
├── secrets-config-module/ # Vault
├── prompt-module/
├── entitlement-module/
├── billing-module/
├── artifact-catalog-module/
├── extension-module/      # PF4J
├── federation-query-module/
├── notification-module/
├── identity-access-module/
├── remote-render-worker/
├── frontend/
├── docker-compose.yml
└── docs/                  # 平台内英文 runbook
```

---

## Gradle 子项目（31 个）

见 `platform/settings.gradle.kts`。模块职责表见 [module-reference.md](../module-reference.md)。

### 按领域分组

| 分组 | 模块 |
|------|------|
| 组合与内核 | `platform-app`、`shared-kernel` |
| 渲染与媒体 | `render-module`、`workflow-module`、`remote-render-worker`、`artifact-catalog-module` |
| AI 与提示词 | `ai-module`、`prompt-module` |
| 商业与权限 | `entitlement-module`、`billing-module`、`quota-billing-module`、`commerce-module`、`payment-module` |
| 平台能力 | `storage-module`、`delivery-module`、`secrets-config-module`、`notification-module`、`scheduler-module`、`outbox-event-module` |
| 治理与扩展 | `policy-governance-module`、`extension-module`、`sandbox-runtime-module`、`audit-compliance-module` |
| 数据与查询 | `datasource-module`、`federation-query-module`、`compatibility-migration-module`、`user-analytics-module` |
| 其他 | `config-module`、`cloud-resource-module`、`observability-module`、`social-publish-module` |

---

## 模块边界规则（摘要）

- `shared-kernel` 不依赖业务模块。
- 业务模块通过 **Port** 通信，禁止随意跨模块直接依赖实现类。
- `platform-app` 为唯一组合根，依赖全部所需模块。

完整规范：[development-guidelines.md](../development-guidelines.md)。
