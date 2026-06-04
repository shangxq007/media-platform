# Vault、RustFS 与 Temporal 部署配置指南

> **最后更新:** 2026-05-20  
> **适用:** 自托管 HashiCorp Vault + 自部署 [RustFS](https://docs.rustfs.com/)（S3 兼容）+ 自部署 [Temporal](https://docs.temporal.io/) Server  
> **相关:** [密钥管理](secrets-management.md)、[部署清单](deployment.md)、[交付子系统](delivery-subsystem.md)

本文给出平台**默认采用的命名**（组织 slug、Vault namespace、KV 路径、RustFS 桶、Temporal namespace / Task Queue）。你按本文完成基础设施侧配置后，将环境变量或 Spring profile（`vault` / `temporal`）填入即可与代码对齐。

---

## 1. 命名约定总览

| 项 | 值 | 说明 |
|----|-----|------|
| **组织 slug** | `bluepulse` | HCP 组织标识；自托管 Vault 仅作逻辑分区名 |
| **产品前缀** | `media-platform` | 与 `app.secrets.vault.path-prefix` 默认一致 |
| **KV 引擎挂载** | `secret` | KV v2，与 `VAULT_KV_MOUNT` 一致 |
| **RustFS S3 端口** | `9000` | API；控制台常见为 `9001` |
| **Cloudflare R2** | `storage.s3.compatibility=r2` | 见 §3.7；endpoint 或 `account-id` |
| **默认 Region** | `us-east-1`（RustFS）/ `auto`（R2） | 由 `STORAGE_S3_COMPATIBILITY` 决定 |
| **Temporal namespace** | `media-platform-<env>` | 与 Vault/RustFS 环境后缀一致；见 §4 |
| **Temporal Task Queue** | `media-platform-tasks` | 代码常量，不可随意改名 |
| **Temporal gRPC** | `7233` | SDK ↔ Server；UI 常见 `8080` 或 `8233` |

### 1.1 环境维度

| 环境 | Vault namespace（仅 HCP） | Temporal namespace | RustFS 桶后缀 |
|------|---------------------------|--------------------|---------------|
| 开发 | `admin/bluepulse/media-platform-dev` | `media-platform-dev` | `-dev` |
| 预发 | `admin/bluepulse/media-platform-staging` | `media-platform-staging` | `-staging` |
| 生产 | `admin/bluepulse/media-platform-prod` | `media-platform-prod` | `-prod` |

**自托管开源 Vault：** `VAULT_NAMESPACE` 留空即可；路径仍使用下表 KV 前缀，通过不同 `VAULT_TOKEN` / AppRole 区分环境。

---

## 2. Vault 配置

### 2.1 KV 路径树（逻辑命名空间）

所有密钥写在 KV v2 挂载 `secret` 下，**数据路径**（API/CLI 用 `secret/data/...`）：

```text
secret/data/media-platform/
├── delivery/
│   └── tenants/{tenantId}/destinations/{destinationId}   # 交付目的地凭证 JSON
├── platform/                                              # 预留：平台级
│   ├── s3          # 可选：STORAGE_S3_* 迁入 Vault 时用
│   └── datasource  # 可选：DB 密码
└── integrations/   # 预留：第三方 API Key
```

**SecretRef 写法（应用内）：**

| 用途 | `credential_ref` / SecretRef 示例 |
|------|-----------------------------------|
| 交付 SFTP/WebDAV 等 | `vault:media-platform/delivery/tenants/tenant-1/destinations/dst_abc` |
| 单字段 | `vault:media-platform/delivery/.../dst_abc#password` |
| 环境变量（RustFS AK 等） | `env:STORAGE_S3_SECRET_KEY` |

代码写入路径 = `{path-prefix}/delivery/tenants/...`，默认 `path-prefix=media-platform`（见 `application.yml`）。

### 2.2 自托管 Vault 初始化

```bash
export VAULT_ADDR=http://127.0.0.1:8200
export VAULT_TOKEN=<root-or-bootstrap-token>

# KV v2（若尚未启用）
vault secrets enable -path=secret kv-v2

# 示例：手动写入一条交付凭证（与平台自动 put 的 JSON 字段一致）
vault kv put secret/media-platform/delivery/tenants/tenant-1/destinations/dst_demo \
  username=uploader password='change-me' host=sftp.example.com port=22
```

### 2.3 AppRole 与 Policy（生产推荐）

**Policy 名称：** `media-platform-secrets`  
**AppRole 名称：** `media-platform-app`

```hcl
# media-platform-secrets.hcl
path "secret/data/media-platform/*" {
  capabilities = ["create", "read", "update", "delete", "list"]
}
path "secret/metadata/media-platform/*" {
  capabilities = ["list", "read", "delete"]
}
path "secret/data/media-platform/delivery/*" {
  capabilities = ["create", "read", "update", "delete", "list"]
}
```

```bash
vault policy write media-platform-secrets media-platform-secrets.hcl

vault write auth/approle/role/media-platform-app \
  token_policies="media-platform-secrets" \
  token_ttl=1h \
  token_max_ttl=4h \
  secret_id_ttl=0 \
  secret_id_num_uses=0

vault read auth/approle/role/media-platform-app/role-id
# 生成 secret-id（妥善保管，仅下发给应用运行时）
vault write -f auth/approle/role/media-platform-app/secret-id
```

### 2.4 HashiCorp Cloud（HCP）namespace

在 HCP 控制台为组织 `bluepulse` 创建命名空间（与上表一致），应用配置：

```yaml
app:
  secrets:
    vault:
      enabled: true
      uri: https://<cluster-id>.vault.<region>.hashicorp.cloud:8200
      auth-method: approle
      namespace: admin/bluepulse/media-platform-prod   # 按环境切换
      role-id: ${VAULT_ROLE_ID}
      secret-id: ${VAULT_SECRET_ID}
      kv-mount: secret
      path-prefix: media-platform
    inline-credentials-enabled: false
```

```bash
export VAULT_NAMESPACE=admin/bluepulse/media-platform-prod
export VAULT_AUTH_METHOD=approle
export VAULT_ROLE_ID=<from-hcp>
export VAULT_SECRET_ID=<from-hcp>
export VAULT_ENABLED=true
export SECRETS_INLINE_CREDENTIALS_ENABLED=false
```

健康检查：`GET /api/v1/secrets/vault/health`（需 Vault 已启用）。

### 2.5 应用侧环境变量（Vault）

| 变量 | 开发示例 | 生产 |
|------|----------|------|
| `VAULT_ENABLED` | `true` | `true` |
| `VAULT_ADDR` | `http://vault:8200` | `https://vault.internal:8200` |
| `VAULT_AUTH_METHOD` | `token` | `approle` |
| `VAULT_TOKEN` | 开发用短期 token | （生产不用） |
| `VAULT_ROLE_ID` / `VAULT_SECRET_ID` | — | AppRole |
| `VAULT_NAMESPACE` | 空 | `admin/bluepulse/media-platform-prod` |
| `VAULT_KV_MOUNT` | `secret` | `secret` |
| `VAULT_PATH_PREFIX` | `media-platform` | `media-platform` |
| `SECRETS_INLINE_CREDENTIALS_ENABLED` | `false`（联调 Vault 时） | `false` |

启动 profile：`--spring.profiles.active=dev,vault`（见 `platform/platform-app/src/main/resources/application-vault.yml`）。

### 2.6 交付凭证迁移

Vault 启用后，将历史 `credential_json` 迁入 KV：

```http
POST /api/v1/admin/delivery/credentials/migrate?tenantId=tenant-1&dryRun=false
```

删除目的地时平台会 best-effort 删除对应 KV 元数据。

---

## 3. RustFS（S3 兼容对象存储）

平台通过 AWS SDK v2 访问 S3 兼容端（`S3BlobStorageProvider`），**必须**对自托管 RustFS 开启 **Path-Style**（默认已为 `true`）。

### 3.1 RustFS 服务部署（示例）

```bash
# 参考 https://docs.rustfs.com/installation/docker
docker run -d --name rustfs \
  -p 9000:9000 -p 9001:9001 \
  -v /var/lib/rustfs/data:/data \
  -e RUSTFS_ACCESS_KEY=mp-platform \
  -e RUSTFS_SECRET_KEY='请替换为强随机密钥' \
  rustfs/rustfs:latest /data
```

- **S3 API：** `http://<rustfs-host>:9000`
- **控制台：** 通常为 `http://<rustfs-host>:9001`（以 RustFS 版本文档为准）
- 认证：Signature V4；与 MinIO 类客户端配置方式相同

### 3.2 建议桶与对象前缀

| 桶名（生产） | 用途 | 平台配置 |
|--------------|------|----------|
| `mp-render-cache-prod` | 渲染段/终稿缓存、`BlobStorage` 默认桶 | `STORAGE_S3_DEFAULT_BUCKET` |
| `mp-delivery-out-prod` | 可选：仅 `S3_MIRROR` 交付镜像专用 | 目的地 `config.bucket` |

开发/预发将后缀改为 `-dev` / `-staging`，避免混用数据。

**对象键前缀（渲染缓存）：**

```text
mp-render-cache-prod/
  render/{tenantId}/segment:... 
  render/{tenantId}/final:...
```

与 `RENDER_CACHE_REMOTE_PREFIX` 对齐，例如：

```bash
RENDER_CACHE_REMOTE_PREFIX=s3://mp-render-cache-prod/render
```

`RenderCacheUriResolver` 会在此前缀下追加 `/{tenantId}/...`。

### 3.3 在 RustFS 创建桶

使用 AWS CLI（或 RustFS 控制台）：

```bash
export AWS_ACCESS_KEY_ID=mp-platform
export AWS_SECRET_ACCESS_KEY='<与 RUSTFS_SECRET_KEY 一致>'
export AWS_DEFAULT_REGION=us-east-1

aws --endpoint-url http://127.0.0.1:9000 s3 mb s3://mp-render-cache-dev
aws --endpoint-url http://127.0.0.1:9000 s3 mb s3://mp-delivery-out-dev
```

**生命周期（可选）：** 对 `render/` 前缀配置过期删除，与 `RENDER_CACHE_RETENTION_DAYS=30` 策略一致；平台 `RenderCacheCleanupService` 也会按保留天数删除对象。

**CORS：** 若浏览器直传预签名 URL，需对桶配置 CORS；仅服务端上传可暂不配。

### 3.4 平台环境变量（RustFS）

```bash
# 启用 S3 存储后端（替代本地 LocalFsStorageProvider）
STORAGE_S3_ENABLED=true
STORAGE_S3_ENDPOINT=http://rustfs:9000
STORAGE_S3_REGION=us-east-1
STORAGE_S3_ACCESS_KEY=mp-platform
STORAGE_S3_SECRET_KEY=<强密钥>
STORAGE_S3_PATH_STYLE=true
STORAGE_S3_DEFAULT_BUCKET=mp-render-cache-dev

# 渲染缓存上传到 RustFS
RENDER_CACHE_UPLOAD_ENABLED=true
RENDER_CACHE_REMOTE_ENABLED=true
RENDER_CACHE_REMOTE_PREFIX=s3://mp-render-cache-dev/render
RENDER_CACHE_CONTENT_HASH_ENABLED=true
RENDER_CACHE_CLEANUP_ENABLED=true
RENDER_CACHE_RETENTION_DAYS=30
```

`application.yml` 中对应项：

```yaml
storage:
  s3:
    enabled: true
    endpoint: http://rustfs:9000
    region: us-east-1
    access-key: mp-platform
    secret-key: ${STORAGE_S3_SECRET_KEY}
    path-style-access: true
    default-bucket: mp-render-cache-dev

render:
  cache:
    upload-enabled: true
    remote-enabled: true
    remote-uri-prefix: s3://mp-render-cache-dev/render
```

### 3.5 交付 `S3_MIRROR` 与 RustFS

当前 `S3_MIRROR` 适配器使用**平台全局** `BlobStorage`（即上述 `STORAGE_S3_*`），将成片写入目的地配置中的 `bucket` / `keyPrefix`：

```json
{
  "protocol": "S3_MIRROR",
  "config": {
    "bucket": "mp-delivery-out-dev",
    "keyPrefix": "tenants/tenant-1/out/"
  }
}
```

- 桶必须在**同一 RustFS 实例**上已创建，且平台 AK/SK 对该桶有读写权限。
- 若未来需「客户自有 S3 端点 + 独立 AK」，需扩展适配器；当前版本不支持 per-destination S3 endpoint。

### 3.6 密钥存放建议

| 密钥 | 推荐存放 | 说明 |
|------|----------|------|
| RustFS `STORAGE_S3_SECRET_KEY` | 部署环境变量 / K8s Secret | 平台启动必需；可后续改为 `env:` 或 Vault `platform/s3` |
| 交付 SFTP/WebDAV 密码 | Vault `delivery/tenants/...` | 创建目的地时由 API 自动写入 |
| `VAULT_SECRET_ID` | K8s Secret / CI 密文 | 不写进 Git |

### 3.7 Cloudflare R2（S3 兼容）

平台**已支持** R2：仍使用 `S3BlobStorageProvider`，通过 `storage.s3.compatibility=r2` 应用 [Cloudflare 要求的客户端设置](https://developers.cloudflare.com/r2/examples/aws/aws-sdk-java/)（`region=auto`、path-style、**关闭 chunked encoding**，否则 `putObject` 易 403）。

#### 前置（Cloudflare 控制台）

1. 创建 R2 桶（如 `mp-render-cache-prod`）。
2. 创建 **R2 API Token**（权限：对该桶 Object Read & Write；可按环境分 token）。
3. 记录 **Account ID**、Access Key ID、Secret Access Key。

#### 平台环境变量

```bash
STORAGE_S3_ENABLED=true
STORAGE_S3_COMPATIBILITY=r2
STORAGE_S3_ACCOUNT_ID=<Cloudflare_Account_ID>
# 或直接写完整 endpoint（二选一）：
# STORAGE_S3_ENDPOINT=https://<ACCOUNT_ID>.r2.cloudflarestorage.com
STORAGE_S3_ACCESS_KEY=<R2_Access_Key_ID>
STORAGE_S3_SECRET_KEY=<R2_Secret_Access_Key>
STORAGE_S3_REGION=auto
STORAGE_S3_PATH_STYLE=true
STORAGE_S3_CHUNKED_ENCODING=false
STORAGE_S3_DEFAULT_BUCKET=mp-render-cache-prod

RENDER_CACHE_UPLOAD_ENABLED=true
RENDER_CACHE_REMOTE_ENABLED=true
RENDER_CACHE_REMOTE_PREFIX=s3://mp-render-cache-prod/render
```

**Spring profile：** `application-r2.yml`，启动示例：

```bash
--spring.profiles.active=dev,r2
```

#### 与 RustFS 的差异

| 项 | RustFS（§3） | Cloudflare R2（本节） |
|----|--------------|------------------------|
| `compatibility` | `generic`（默认） | `r2` |
| Endpoint | `http://host:9000` | `https://<accountId>.r2.cloudflarestorage.com` |
| Region | `us-east-1` 占位 | **`auto`**（SDK 必填，R2 不使用） |
| Chunked encoding | 默认开启 | **必须关闭**（`r2` 模式自动关闭） |
| 公网访问 | 自建内网 | 建议仅服务端 AK；对外用 **R2 自定义域** 或 presigned URL |

#### 预签名与 CDN

`S3BlobStorageProvider.presign()` 生成的 GET URL 指向 R2 S3 端点。若需浏览器直连或 CDN 缓存，在 Cloudflare 为桶绑定**自定义域名**或 Workers；平台侧 URI 仍以 `s3://bucket/key` 存库，对外展示层可再映射。

#### 验证

```bash
export AWS_ACCESS_KEY_ID=<R2_Access_Key_ID>
export AWS_SECRET_ACCESS_KEY=<R2_Secret>
aws s3 ls --endpoint-url https://<ACCOUNT_ID>.r2.cloudflarestorage.com s3://mp-render-cache-dev/
```

提交渲染作业后，R2 控制台应出现 `render/<tenantId>/...` 对象。

---

## 4. Temporal（自托管）

平台在 `render.execution.mode=temporal` 时通过 **Temporal Spring Boot Starter 1.33** 连接 Server；同一进程内注册 **Worker** 并轮询 Task Queue（`application-temporal.yml`）。

### 4.1 版本与端口

| 组件 | 建议版本 | 说明 |
|------|----------|------|
| Temporal Server | **1.24+**（与 `auto-setup` 镜像标签一致） | 低于 1.22 勿用于生产 |
| Java SDK | **1.33.0**（`platform-app` 已锁定） | 与 Server 大版本兼容即可 |
| Temporal UI | 2.26+ | 可选，排障与查看 Workflow |

| 端口 | 协议 | 用途 |
|------|------|------|
| **7233** | gRPC | 应用 `TEMPORAL_TARGET` / `spring.temporal.connection.target` |
| **8080** 或 **8233** | HTTP | Web UI（取决于 UI 镜像/compose 映射） |

### 4.2 你需要在 Temporal 侧创建的资源

#### Namespace（按环境各一个）

与 RustFS/Vault 环境后缀对齐，**不要**在生产长期使用内置 `default`：

| 环境 | Namespace 名称 | 建议 retention |
|------|----------------|----------------|
| 开发 | `media-platform-dev` | 3–7 天 |
| 预发 | `media-platform-staging` | 14 天 |
| 生产 | `media-platform-prod` | 30 天 |

**创建示例**（Temporal CLI，Server 已启动且 `TEMPORAL_ADDRESS` 指向 7233）：

```bash
export TEMPORAL_ADDRESS=127.0.0.1:7233

temporal operator namespace create media-platform-dev \
  --retention 7d \
  --description "Media platform development"

temporal operator namespace create media-platform-staging --retention 14d
temporal operator namespace create media-platform-prod --retention 30d
```

> **本地快速试跑：** 若仅用 `temporalio/auto-setup` 且尚未建 namespace，可暂时设 `TEMPORAL_NAMESPACE=default`；与生产命名不一致，联调通过后再切到 `media-platform-dev`。

#### Task Queue（无需在 Server 预创建）

| 名称 | 代码位置 | 说明 |
|------|----------|------|
| `media-platform-tasks` | `RenderTaskQueue.NAME` | Worker 与 Workflow 启动均使用此队列 |

首次有 Worker 连接时 Temporal 自动识别该队列；**无需**在控制台手工建队列。

#### Workflow 类型（自动注册）

| 类型 | 实现类 | Workflow ID 规则 |
|------|--------|------------------|
| `RenderWorkflow` | `RenderWorkflowImpl` | **= `renderJobId`**（幂等：同一作业重复提交复用同一 ID） |

Activity 链（`RenderWorkflowImpl`）：

```text
decideRenderPipeline → executeRenderJob → deliverArtifacts（可选）
```

| Activity | 超时（代码默认） | 依赖 |
|----------|------------------|------|
| 全部 Activity | `startToClose` **2 小时** | `ActivityOptions` in `RenderWorkflowImpl` |
| Workflow 整体 | `workflowExecutionTimeout` **2 小时** | `TemporalRenderExecutionAdapter` |
| 单次 Run | `workflowRunTimeout` **30 分钟** | 同上 |

长片源渲染若常超 30 分钟，需在改代码前评估是否调大 `workflowRunTimeout`。

### 4.3 Temporal Server 部署（PostgreSQL 持久化）

**建议：** Temporal 使用**独立 PostgreSQL**（或与平台库同实例不同 database），不要用平台业务 H2。

`docker-compose` 最小示例（单节点开发）：

```yaml
# docker-compose.temporal.yml
services:
  temporal-postgresql:
    image: postgres:16
    environment:
      POSTGRES_USER: temporal
      POSTGRES_PASSWORD: temporal
    volumes:
      - temporal-pg:/var/lib/postgresql/data

  temporal:
    image: temporalio/auto-setup:1.24.2
    depends_on:
      - temporal-postgresql
    environment:
      DB: postgresql
      DB_PORT: 5432
      POSTGRES_USER: temporal
      POSTGRES_PWD: temporal
      POSTGRES_SEEDS: temporal-postgresql
      DYNAMIC_CONFIG_FILE_PATH: config/dynamicconfig/development-sql.yaml
    ports:
      - "7233:7233"

  temporal-ui:
    image: temporalio/ui:2.26.2
    depends_on:
      - temporal
    environment:
      TEMPORAL_ADDRESS: temporal:7233
      TEMPORAL_CORS_ORIGINS: http://localhost:3000
    ports:
      - "8088:8080"   # 浏览器访问 http://localhost:8088

volumes:
  temporal-pg:
```

启动后创建 namespace（§4.2），确认 UI 中可见 `media-platform-dev`。

**生产：** 参考 [Temporal 生产部署](https://docs.temporal.io/self-hosted-guide)（多副本 Frontend/History/Matching + 独立 Visibility 库或 Elasticsearch）。

### 4.4 平台应用配置

启用 Temporal 需同时满足：

1. **取消** `application.yml` 中对 Temporal 自动配置的 exclude（由 profile 完成）
2. `app.temporal.enabled=true`
3. `render.execution.mode=temporal`
4. gRPC 可达 `TEMPORAL_TARGET`

**Profile：** `application-temporal.yml`

```bash
export TEMPORAL_ENABLED=true
export TEMPORAL_TARGET=127.0.0.1:7233
export TEMPORAL_NAMESPACE=media-platform-dev

# 启动（与 vault 可叠加）
java -jar platform-app.jar --spring.profiles.active=dev,temporal
```

| 环境变量 | 说明 | 开发示例 |
|----------|------|----------|
| `TEMPORAL_ENABLED` | → `app.temporal.enabled` | `true` |
| `TEMPORAL_TARGET` | gRPC 地址 | `127.0.0.1:7233` 或 `temporal.internal:7233` |
| `TEMPORAL_NAMESPACE` | Server namespace | `media-platform-dev` |
| `DELIVERY_TEMPORAL_ACTIVITY_ENABLED` | 渲染后走 Activity 交付 | `true`（profile 已默认） |

`application-temporal.yml` 关键片段（与仓库一致）：

```yaml
app:
  temporal:
    enabled: true
render:
  execution:
    mode: temporal
delivery:
  temporal:
    activity-enabled: true
spring.temporal:
  connection:
    target: ${TEMPORAL_TARGET:127.0.0.1:7233}
  namespace: ${TEMPORAL_NAMESPACE:default}
  workers:
    - task-queue: media-platform-tasks
      name: render-worker
```

**未启用 Temporal 时（默认）：** `app.temporal.enabled=false`，且 `spring.autoconfigure.exclude` 包含 Temporal 自动配置类 → 不连 Server、不启 Worker；渲染走 `LocalRenderExecutionAdapter`（进程内同步）。

### 4.5 与交付、存储的协作

```text
POST render-job
  → TemporalRenderSubmitContinuation
  → TemporalRenderExecutionAdapter.start(workflowId = renderJobId)
  → Worker: executeRenderJob → RenderOrchestratorPort（写 RustFS 缓存等）
  → Worker: deliverArtifacts（若 delivery.temporal.activity-enabled=true）
       → DeliveryAfterRenderPort.finalizeDeliveriesForRenderJob
```

| 模式 | 渲染执行 | 交付触发 |
|------|----------|----------|
| `local`（默认） | 同步 `LocalRenderExecutionAdapter` | `@Scheduled` 轮询 `delivery_job` + 渲染完成事件 |
| `temporal` | 异步 Workflow | 优先 `deliverArtifacts` Activity；`DELIVERY_WORKER_ENABLED` 仍可保留作补偿 |

启用 Temporal 后仍建议：`DELIVERY_ENABLED=true`；Activity 失败时可依赖 DB 队列重试。

### 4.6 拓扑建议

**开发（单机）：** 一个 `platform-app`（`dev,temporal`）+ 一个 `auto-setup` Temporal + RustFS + Vault。

**生产（可扩展）：**

```text
                    ┌─────────────────┐
                    │ Temporal Server │
                    │   :7233 gRPC    │
                    └────────┬────────┘
                             │
        ┌────────────────────┼────────────────────┐
        ▼                    ▼                    ▼
  platform-app (API)   platform-app (Worker)   platform-app (Worker)
  可选 start-workers:false   start-workers:true   同左
  mode=temporal              mode=temporal
        │                    │
        └──────── 同一 Task Queue: media-platform-tasks ────────┘
```

- 多副本 Worker 共用 `media-platform-tasks` 即可水平扩展。
- 可选：API 实例设 `spring.temporal.start-workers=false`，仅 Worker 实例轮询队列（需 Spring 配置，当前仓库默认**同进程启 Worker**）。

### 4.7 网络与依赖检查

| 检查项 | 要求 |
|--------|------|
| 平台 → Temporal | TCP **7233** 畅通 |
| Worker → PostgreSQL | 平台业务库可读写（Activity 内渲染状态） |
| Worker → RustFS | `STORAGE_S3_*` 与 §3 一致（缓存上传） |
| Worker → Vault | 若交付读 `credential_ref`，与 §2 一致 |
| 防火墙 | 不要将 7233 暴露公网；仅内网或 mesh |

**验证：**

1. Temporal UI 中 namespace = `media-platform-dev`，提交渲染后可见 `RenderWorkflow`，Workflow ID = 作业 ID。
2. 应用日志：`Temporal workflow started successfully: workflowId=..., taskQueue=media-platform-tasks`。
3. Activity 完成后交付记录进入终态（或 `delivery_job` 被处理）。

### 4.8 常见问题

| 现象 | 可能原因 |
|------|----------|
| 提交渲染立刻报错 | `TEMPORAL_TARGET` 不可达或未启 `temporal` profile |
| Workflow 一直 Scheduled | 无 Worker 轮询 `media-platform-tasks`（`app.temporal.enabled=false` 或队列名不一致） |
| `executeRenderJob` 失败 | Worker 进程缺少 `RenderOrchestratorPort` Bean（应使用完整 `platform-app`，勿裁模块） |
| 渲染完成但未交付 | `delivery.temporal.activity-enabled=false` 或 `DeliveryAfterRenderPort` 未注入 |
| 重复提交同一 jobId | 设计如此：Workflow ID = jobId，Temporal 去重/复用执行 |

---

## 5. 端到端联调清单

### Vault

- [ ] `secret` KV v2 已启用
- [ ] Policy `media-platform-secrets` 与 AppRole `media-platform-app` 已创建（或开发 Token 具备同等路径权限）
- [ ] HCP：`VAULT_NAMESPACE=admin/bluepulse/media-platform-<env>`
- [ ] `VAULT_ENABLED=true`，`SECRETS_INLINE_CREDENTIALS_ENABLED=false`
- [ ] `GET /api/v1/secrets/vault/health` 返回 `healthy: true`
- [ ] 创建交付目的地后，`vault kv get secret/media-platform/delivery/tenants/...` 可见 JSON

### RustFS / R2

- [ ] RustFS：`9000` 可达；或 R2：`STORAGE_S3_COMPATIBILITY=r2` + endpoint/account-id
- [ ] 桶 `mp-render-cache-<env>`（及可选 `mp-delivery-out-<env>`）已创建
- [ ] R2：`chunked-encoding` 已关闭（profile `r2` 自动处理）
- [ ] `STORAGE_S3_ENABLED=true`，渲染任务后对象出现在 `render/{tenantId}/` 下
- [ ] `S3_MIRROR` 交付任务写入目的地 `bucket` + `keyPrefix`

### Temporal

- [ ] Server 1.24+ 已部署，PostgreSQL 持久化正常
- [ ] 已创建 namespace `media-platform-<env>`（或开发暂用 `default`）
- [ ] `TEMPORAL_TARGET` 可达，`--spring.profiles.active=...,temporal`
- [ ] `app.temporal.enabled=true`，`render.execution.mode=temporal`
- [ ] `TEMPORAL_NAMESPACE` 与 Server 一致
- [ ] UI 可见 Task Queue `media-platform-tasks` 有 Worker 轮询
- [ ] 提交渲染作业后 Workflow ID = `renderJobId` 且 Run 完成
- [ ] （可选）`delivery.temporal.activity-enabled=true` 且交付记录更新

---

## 6. 与代码模块对应关系

| 能力 | 模块 / 配置键 |
|------|----------------|
| Vault 客户端、namespace、AppRole | `secrets-config-module` → `VaultClientFactory` |
| KV 读写、`credential_ref` | `VaultKv2SecretProvider`、`DeliveryDestinationCredentialService` |
| S3 / R2 上传/预签名 | `storage-module` → `S3BlobStorageProvider`、`S3ClientSettingsResolver` |
| 渲染缓存 URI | `render-module` → `RenderCacheProperties.remoteUriPrefix` |
| 交付镜像 | `delivery-module` → `S3MirrorDeliveryAdapter` |
| 启动 Workflow | `workflow-module` → `TemporalRenderExecutionAdapter` |
| Workflow / Activity | `RenderWorkflowImpl`、`RenderActivitiesImpl` |
| Task Queue 常量 | `RenderTaskQueue.NAME` = `media-platform-tasks` |
| Profile 开关 | `application-temporal.yml`、`app.temporal.enabled` |

更细的 API 与迁移说明见 [密钥管理](secrets-management.md)；交付与 Temporal Activity 见 [交付子系统](delivery-subsystem.md)。

---

## 7. 变更记录

| 日期 | 说明 |
|------|------|
| 2026-05-20 | 初版：组织 slug `bluepulse`、HCP namespace、KV 路径、RustFS 桶与环境变量 |
| 2026-05-20 | 增补 §4 Temporal；§3.7 Cloudflare R2（`compatibility=r2`、profile `r2`） |
