# 密钥管理（SecretResolver + Vault）

> **最后更新:** 2026-05-20  
> **模块:** `secrets-config-module`  
> **相关:** [Vault、RustFS 与 Temporal 部署配置](vault-and-rustfs-setup.md)、[交付子系统](delivery-subsystem.md)、[部署](deployment.md)

---

## 1. 架构

```text
delivery-module / 其他业务
        │
        ▼
 CredentialBundleResolver ──► SecretResolver (端口)
        │                           │
        │                    CompositeSecretResolver
        │                           │
        │              ┌────────────┴────────────┐
        │              ▼                         ▼
        │      EnvSecretProvider        VaultKv2SecretProvider
        │      (始终可用)                (app.secrets.vault.enabled=true)
        ▼
 delivery_destination.credential_ref  (+ 兼容 credential_json)
 secret_ref 表（审计注册）
```

业务模块**只依赖** `SecretResolver` / `CredentialBundleResolver`，不直接依赖 Vault SDK。

---

## 2. 引用格式（SecretRef）

| 格式 | 示例 |
|------|------|
| 环境变量 | `env:STORAGE_S3_SECRET_KEY` 或 `${env:VAR:default}` |
| Vault KV v2 | `vault:media-platform/delivery/tenants/t1/destinations/dst_abc` |
| Vault 单字段 | `vault:path/to/secret#password` |

Vault 路径下存 **JSON 对象**（如 `username`、`password`、`privateKey`），交付适配器按 Map 使用。

---

## 3. 配置

### 3.1 自托管 Vault（HTTP，Token）

```yaml
app:
  secrets:
    vault:
      enabled: true
      uri: http://vault.example.com:8200
      auth-method: token
      token: ${VAULT_TOKEN}
      kv-mount: secret
      path-prefix: media-platform
    inline-credentials-enabled: false
```

```bash
export VAULT_ENABLED=true
export VAULT_ADDR=http://vault.example.com:8200
export VAULT_TOKEN=<app-token>
export SECRETS_INLINE_CREDENTIALS_ENABLED=false
```

启动：`--spring.profiles.active=dev,vault`（见 `application-vault.yml`）。

### 3.2 自托管 Vault（AppRole，生产推荐）

```yaml
app:
  secrets:
    vault:
      enabled: true
      uri: http://vault.internal:8200
      auth-method: approle
      role-id: ${VAULT_ROLE_ID}
      secret-id: ${VAULT_SECRET_ID}
      kv-mount: secret
      path-prefix: media-platform
```

```bash
vault write auth/approle/role/media-platform-delivery \
  token_policies="delivery-secrets" \
  token_ttl=1h token_max_ttl=4h
vault read auth/approle/role/media-platform-delivery/role-id
vault write -f auth/approle/role/media-platform-delivery/secret-id
```

### 3.3 HashiCorp Cloud（HCP Vault）预留

迁移到 HCP 时通常只需改 **URI / namespace / AppRole**，应用侧不变。默认命名见 [Vault、RustFS 与 Temporal 部署配置](vault-and-rustfs-setup.md)（组织 slug `bluepulse`，namespace `admin/bluepulse/media-platform-<env>`）：

```yaml
app:
  secrets:
    vault:
      enabled: true
      uri: https://<cluster>.vault.<region>.hashicorp.cloud:8200
      auth-method: approle
      namespace: admin/bluepulse/media-platform-prod
      role-id: ${VAULT_ROLE_ID}
      secret-id: ${VAULT_SECRET_ID}
      kv-mount: secret
```

平台通过 `VaultEndpoint.setNamespace()` 发送 `X-Vault-Namespace` 头，与开源版 API 兼容。

### 3.4 健康检查

Vault 启用后：

`GET /api/v1/secrets/vault/health` → `{ "healthy": true, "uri": "...", "authMethod": "..." }`

本地开发（无 Vault）：`vault.enabled=false`，`inline-credentials-enabled=true`，仍可用 `credential_json`（**仅 dev**）。

---

## 4. 交付目的地凭证流

**创建/更新**（`POST/PATCH .../delivery/destinations`）：

1. 若请求带 `credentialRef` → 直接存库，不写明文。
2. 若带 `credentials` Map 且 **Vault 启用** → 写入  
   `media-platform/delivery/tenants/{tenantId}/destinations/{destinationId}`，存 `credential_ref=vault:...`，`credential_json=null`。
3. 若 Vault 未启用且允许 inline → 仅存 `credential_json`（兼容）。

**执行交付**（`DeliveryJobService` / Adapter）：

- `CredentialBundleResolver.resolve(credential_ref, credential_json)`
- 优先 `credential_ref` → Vault/env；失败且无 ref 时回退 legacy JSON。

**API 响应**：返回 `credentialRef`（非敏感）与 `credentialsConfigured`，**永不返回**密码明文。

---

## 5. secret_ref 注册表

写入 Vault 时同步登记：

| 列 | 说明 |
|----|------|
| `namespace_key` | `delivery` |
| `secret_key` | `destinationId` |
| `backend_type` | `vault` |
| `backend_ref` | `vault:media-platform/delivery/...` |

供审计与后续管理面查询；解析以 `delivery_destination.credential_ref` 为准。

---

## 6. 迁移与生命周期

- Flyway：`V14__delivery_credential_ref.sql` 增加 `credential_ref` 列。
- **管理端迁移 API**（需 Vault 已启用）：

  `POST /api/v1/admin/delivery/credentials/migrate?tenantId=tenant-1&dryRun=false`

  将仍含 `credential_json` 且无 `credential_ref` 的目的地写入 Vault 并清空 JSON。

- **删除目的地**：`DeliveryJobService.deleteDestination` 会 `revoke` Vault KV 元数据（best-effort）。
- **更新凭证**：`PATCH` 目的地并提交新 `credentials` 会覆盖同路径 KV 版本。

---

## 7. Vault 初始化示例

```bash
vault secrets enable -path=secret kv-v2
vault kv put secret/media-platform/delivery/tenants/tenant-1/destinations/dst_demo \
  username=uploader password='***'
```

平台创建目的地时也会自动 `put` 到上述路径前缀。

---

## 8. 模块与类

| 类 | 职责 |
|----|------|
| `VaultClientFactory` | Token / AppRole 认证、namespace、超时 |
| `VaultKv2SecretProvider` | KV v2 读/写/删、probe |
| `CompositeSecretResolver` | 统一 `store` / `deleteByRef` |
| `DeliveryDestinationCredentialService` | 交付目的地存证/吊销 |
| `DeliveryCredentialMigrationService` | 批量 JSON → Vault |

## 9. 后续扩展

- Kubernetes Auth（集群内 Pod）
- 凭证轮换 Webhook / Vault dynamic secrets
- `app_datasource.secret_ref`、平台 S3 AK 复用 `SecretResolver`
