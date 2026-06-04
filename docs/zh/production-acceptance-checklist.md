# 生产验收清单

> **最后更新:** 2026-05-20  
> **相关:** [platform-guide/10-roadmap.md](platform-guide/10-roadmap.md)、[graceful-shutdown-and-data-consistency.md](graceful-shutdown-and-data-consistency.md)

发布前按模块勾选。代码侧可先调用 `GET /api/v1/admin/platform/readiness` 获取配置快照。

---

## 1. Temporal

| 项 | 验收步骤 | 通过 |
|----|----------|------|
| Namespace | 集群存在 `media-platform-{env}`（见 [temporal-production-namespace.md](temporal-production-namespace.md)） | ☐ |
| 脚本 | `./scripts/ops/temporal-acceptance.sh` 通过 | ☐ |
| Worker | `SPRING_PROFILES_ACTIVE=prod,temporal` 启动无 `TemporalWorkerStartupVerifier` 失败 | ☐ |
| Health | Actuator `temporalWorker` health 为 UP | ☐ |
| 优雅停机 | 滚动发布时 in-flight workflow 可完成或按策略取消（见 graceful-shutdown 文档） | ☐ |

---

## 2. 对象存储（R2 / RustFS）

| 项 | 验收步骤 | 通过 |
|----|----------|------|
| Profile | `SPRING_PROFILES_ACTIVE=prod,r2`，`STORAGE_S3_ENABLED=true` | ☐ |
| 桶 | 默认桶存在；CORS/生命周期策略符合租户隔离设计 | ☐ |
| 就绪 API | `readiness.storage.s3Enabled=true`，`compatibility=R2` | ☐ |
| 段缓存 | `renderCache.remoteEnabled` + upload；抽样 `POST /asset-governance/segment-cache/cleanup` | ☐ |

---

## 3. LiteLLM + 租户密钥

| 项 | 验收步骤 | 通过 |
|----|----------|------|
| 集群 | LiteLLM 部署可达；`LITELLM_BASE_URL` 从 platform-app 可访问 | ☐ |
| Virtual keys | `LITELLM_TENANT_VIRTUAL_KEYS=true`；Admin API 可 PUT key | ☐ |
| Vault 模式 | `LITELLM_TENANT_KEYS_VAULT=true` + `app.secrets.vault.enabled=true` | ☐ |
| 存量迁移 | `POST /admin/platform/migrate/litellm-keys-to-vault?dryRun=true` 无 failed → `dryRun=false` | ☐ |
| 就绪 | `readiness.ai.tenantKeysVaultBacked=true`，`vaultAvailableForLitellmKeys=true` | ☐ |

---

## 4. 编辑器时间线同步

| 项 | 验收步骤 | 通过 |
|----|----------|------|
| 打开项目 | 编辑器加载 `GET /render/timeline-sync/latest?projectId=` 无 500 | ☐ |
| 离线草稿 | 编辑后刷新页面，未保存内容从 `localStorage` 恢复 | ☐ |
| 冲突合并 | 两端同时改时间线 → 弹出冲突对话框；三种策略均可完成 | ☐ |
| 快进到服务端 | 仅服务端变更、本地未改 clip → 自动应用服务端（无对话框） | ☐ |
| 保存 | 工具栏 Save → `POST /render/timeline-sync/sync` 返回 snapshotId | ☐ |
| 渲染 | 导出提交快照 `ensureInternal: true`；渲染作业使用 internal-1.0 | ☐ |

---

## 5. 支付（Stripe / Hyperswitch）

| 项 | 验收步骤 | 通过 |
|----|----------|------|
| Stripe | `platform.payment.stripe.enabled=true`；测试结账 session 可创建 | ☐ |
| Hyperswitch | `hyperswitch` profile + `HYPERSWITCH_API_KEY`；Payment Link URL 可打开 | ☐ |
| Webhook | 支付成功 webhook → 履约 `CheckoutPaymentPort` 链路通 | ☐ |

---

## 6. Authentik OIDC

| 项 | 验收步骤 | 通过 |
|----|----------|------|
| 实例 | 生产 Authentik 与 Property Mapping 按 [authentik-property-mapping-and-migration.md](authentik-property-mapping-and-migration.md) | ☐ |
| 脚本 | `./scripts/ops/authentik-acceptance.sh`（设置 `API_BASE`、`AUTHENTIK_URL`、`TOKEN`） | ☐ |
| 租户 | `X-Tenant-ID` + JWT claim 与 `trust-jwt-tenant-only` 防提权抽测 | ☐ |
| 开通 | 新租户 JIT 用户 + RBAC 同步冒烟 | ☐ |

---

## 7. 资产治理与停机

| 项 | 验收步骤 | 通过 |
|----|----------|------|
| 删除检查 | 资产/制品 delete-check 返回正确 `ASSET`/`ARTIFACT` 码 | ☐ |
| 孤儿 purge | 审批令牌 + purge API 仅在维护窗口执行 | ☐ |
| 优雅停机 | `server.shutdown=graceful`；协调器日志无异常风暴 | ☐ |

---

## 快速命令

```bash
# 就绪快照
curl -s -H "Authorization: Bearer $TOKEN" \
  https://api.example.com/api/v1/admin/platform/readiness | jq .

# LiteLLM 密钥迁移预检
curl -s -X POST -H "Authorization: Bearer $TOKEN" \
  "https://api.example.com/api/v1/admin/platform/migrate/litellm-keys-to-vault?dryRun=true" | jq .
```
