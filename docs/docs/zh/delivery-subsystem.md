# 渲染产物交付子系统（Delivery）

> **最后更新:** 2026-05-20  
> **状态:** M3 已落地（SMB/HTTPS_PUT、Admin 运维、Temporal Activity、目的地/策略 CRUD）  
> **相关:** [架构说明](architecture.md)、[增量渲染](incremental-rendering.md)、[动态扩展](dynamic-extension.md)

---

## 1. 目标与非目标

### 目标

- 在**渲染作业已成功完成**后，将平台内工件（成片、可选侧车文件）**异步拷贝/上传**到用户或租户配置的**外部目的地**。
- 与平台内热路径存储（`BlobStorage`、render cache、增量复用）**解耦**：交付失败不推翻 `render_job=COMPLETED`。
- 支持可插拔协议适配器（MVP 起分阶段），统一审计、重试、通知。

### 非目标（本阶段不做）

- 不把 FTP/SFTP/SMB 等实现为 `BlobStorage` 的后端（不用于增量 cache / presign / hash 校验）。
- 不替代 `social-publish-module`（社媒发帖、OAuth 平台 API）。
- 不要求交付路径参与 Internal Timeline 1.0 或 `baseJobId` reuse（reuse 只读平台内 URI）。

---

## 2. 与现有模块的边界

```text
┌──────────────────────────────────────────────────────────────────┐
│ 热路径：render-module + storage-module                            │
│  RenderOrchestrator → 平台 artifact_uri / render-cache (S3/Local) │
│  IncrementalRenderPlan → segmentCacheIndex（仅平台内）             │
└───────────────────────────────┬──────────────────────────────────┘
                                │ RenderJobCompletedEvent
                                │ ArtifactCreatedEvent（可选）
                                ▼
┌──────────────────────────────────────────────────────────────────┐
│ 冷路径：delivery-module（新建）                                    │
│  DeliveryJobService → DeliveryAdapter（SFTP / WebDAV / …）         │
│  重试、凭证、审计、render.delivery.* 通知                          │
└──────────────────────────────────────────────────────────────────┘
```

| 模块 | 职责 |
|------|------|
| `storage-module` | 平台统一对象存储（LocalFs、S3 兼容） |
| `render-module` | 渲染、增量 DAG、平台内 cache |
| `delivery-module` | **成品出站**到用户指定系统 |
| `social-publish-module` | 社交平台发布（文案、排期、平台 API） |
| `notification-module` | 站内信 / Email / Webhook（含 `render.delivery.*`） |
| `workflow-module` | 可选：将交付编排进 Temporal（`app.temporal.enabled=true` 时） |

---

## 3. 核心领域模型

### 3.1 DeliveryDestination（目的地配置）

租户或用户级**可复用配置**（凭证加密存储，不在事件里明文传播）。

| 字段 | 说明 |
|------|------|
| `id` | `dst_*` |
| `tenantId` / `userId` | 归属 |
| `name` | 展示名 |
| `protocol` | `SFTP` \| `WEBDAV` \| `SMB` \| `S3_MIRROR` \| `HTTPS_PUT` \| … |
| `configJson` | 协议相关非密字段（host、port、basePath、bucket） |
| `credentialRef` | 指向密钥库 / 加密列，不直接存密码 |
| `enabled` | 开关 |
| `verifiedAt` | 上次连通性探测 |

### 3.2 DeliveryPolicy（绑定策略）

| 字段 | 说明 |
|------|------|
| `id` | `dlp_*` |
| `projectId` / `profile` | 可选过滤 |
| `destinationId` | 引用目的地 |
| `artifactSelector` | `FINAL_ONLY` \| `FINAL_AND_SIDECAR` \| `ALL_STAGE_ARTIFACTS` |
| `pathTemplate` | 如 `{tenantId}/{projectId}/{jobId}/{filename}` |
| `onCompletion` | `AUTO` \| `MANUAL` |

### 3.3 DeliveryJob（一次交付执行）

| 字段 | 说明 |
|------|------|
| `id` | `dlv_*` |
| `renderJobId` | 源作业 |
| `destinationId` | 目标 |
| `status` | `QUEUED` → `RUNNING` → `COMPLETED` \| `FAILED` \| `CANCELLED` |
| `sourceUri` | 平台内 `artifact_uri` 或解析后的 storage URI |
| `remotePath` / `remoteUri` | 交付结果 |
| `bytesTransferred` | 统计 |
| `attemptCount` | 重试次数 |
| `errorCode` / `errorMessage` | 失败原因 |

### 3.4 DeliveryAttempt（单次尝试，可选表）

便于审计与指数退避重试。

---

## 4. 触发与流程

### 4.1 默认：事件驱动（进程内）

```text
RenderJobCompletedEvent / ArtifactCreatedEvent
    → DeliveryJobScheduler.onRenderCompleted()
        → 解析 project/tenant 的 DeliveryPolicy（0..N 条）
        → 为每个 destination 创建 DeliveryJob（QUEUED）
    → @Scheduled 或虚拟线程 worker 消费队列
        → DeliveryAdapter.deliver(context)
        → 更新状态 + 审计 + 通知
```

**原则：** `RenderOrchestratorService` 在标记 `COMPLETED` 后**只发事件**，不阻塞等待 SFTP 上传完成。

### 4.2 可选：Temporal

当 `app.temporal.enabled=true` 时，可增加 `DeliverArtifactActivity`，由 `RenderPipelineWorkflow` 在 storage 步骤之后调用；本地模式仍用 `@Scheduled` 轮询 `delivery_job`。

### 4.3 手动补交

`POST /api/v1/tenants/{tenantId}/projects/{projectId}/render-jobs/{jobId}/deliver`  
body: `{ "destinationId": "dst_..." }` — 用于策略为 `MANUAL` 或失败后重试。

---

## 5. DeliveryAdapter SPI

```java
public interface DeliveryAdapter {
    /** 与 DeliveryDestination.protocol 对应，如 "SFTP". */
    String protocol();

    /** 连通性探测（配置保存时调用）。 */
    ProbeResult probe(DeliveryDestination dest, DecryptedCredentials cred);

    /** 流式上传；从大文件友好，避免整文件进堆。 */
    DeliveryResult deliver(DeliveryRequest request);
}

public record DeliveryRequest(
    String deliveryJobId,
    String tenantId,
    InputStream source,          // 或 Path tempFile
    long contentLength,
    String contentType,
    String sourceFileName,
    String remotePath,           // 由 pathTemplate 渲染
    DeliveryDestination dest,
    DecryptedCredentials cred
) {}
```

**内置适配器优先级（建议 MVP 顺序）：**

| 阶段 | 协议 | 典型场景 |
|------|------|----------|
| P0 | `S3_MIRROR` | 拷贝到客户自有桶（与现有 `BlobStorage` 复用 SDK） |
| P1 | `SFTP` | 企业出站 |
| P1 | `WEBDAV` | NAS/网盘 WebDAV 网关 |
| P2 | `SMB` | Windows 共享（需挂载或 SMBJ） |
| P3 | `HTTPS_PUT` | 用户提供的签名上传 URL |
| 扩展 | 网盘 OAuth | 独立 `CloudDriveAdapter`，不走 FTP 语义 |

通过 `extension-module` 注册额外 `DeliveryAdapter`（`providerType=DELIVERY`），与 [动态扩展](dynamic-extension.md) 一致。

---

## 6. 源文件解析

交付只读**平台内** URI：

1. `render_job.artifact_uri`
2. 或 `pipeline_execution_json` 中 `mezzanineCacheIndex.remoteUri`（需 tenant 前缀校验，可复用 `RenderCacheTenantGuard` 思路）

读取方式：

- `BlobStorage.get(bucket, key)`（S3/Local）
- 本地路径：`TimelineScriptParser.resolveLocalPath` 同类逻辑

**禁止**把用户 SMB 路径当作增量 reuse 输入。

---

## 7. 安全

| 项 | 做法 |
|----|------|
| 凭证 | **已落地** `credential_ref` + `SecretResolver`（Vault KV / env）；API 返回 `credentialsConfigured`，不落明文 |
| 出站 SSRF | `WEBDAV`/`HTTPS_PUT` URL 校验（可复用 notification `WebhookUrlValidator` 策略） |
| 租户隔离 | `destination.tenant_id` 与 `render_job.tenant_id` 一致才允许交付 |
| 路径注入 | `pathTemplate` 白名单变量；拒绝 `..` |
| 审计 | `audit` 记录 `DELIVERY_STARTED` / `DELIVERY_COMPLETED` / `DELIVERY_FAILED` |

---

## 8. 通知与可观测性

新增事件（`notification_event_catalog`）：

| eventKey | 说明 |
|----------|------|
| `render.delivery.completed` | 交付成功 |
| `render.delivery.failed` | 交付失败（可重试） |

载荷含：`deliveryJobId`、`renderJobId`、`destinationId`、`protocol`、`remotePath`。

站内信：订阅 `IN_APP` + 现有 inbox API。

指标（Prometheus 建议）：

- `delivery_job_duration_seconds{protocol,status}`
- `delivery_bytes_total{protocol}`

---

## 9. REST API（草案）

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `.../delivery/destinations` | 创建目的地 |
| `PATCH` | `.../delivery/destinations/{id}` | 更新名称/启用/配置 |
| `DELETE` | `.../delivery/destinations/{id}` | 删除（无策略引用时） |
| `POST` | `.../delivery/destinations/{id}/probe` | 探测连通性 |
| `GET` | `.../delivery/destinations` | 列表 |
| `GET` | `.../projects/{projectId}/delivery/policies` | 项目交付策略列表 |
| `POST` | `.../projects/{projectId}/delivery/policies` | 绑定项目交付策略 |
| `PATCH` | `.../projects/{projectId}/delivery/policies/{id}` | 启用/禁用策略 |
| `DELETE` | `.../projects/{projectId}/delivery/policies/{id}` | 删除策略 |
| `GET` | `.../render-jobs/{jobId}/deliveries` | 某作业的交付记录 |
| `POST` | `.../render-jobs/{jobId}/deliver` | 手动触发 |
| `POST` | `.../render-jobs/{jobId}/deliveries/{id}/retry` | 重试失败交付 |

OpenAPI 分组：`Delivery`（与 `Render Jobs` 分开）。

### 9.1 配置示例（`config` / `credentials`）

**SFTP**

```json
{
  "name": "企业 SFTP",
  "protocol": "SFTP",
  "config": { "host": "sftp.example.com", "port": 22, "basePath": "/uploads" },
  "credentials": { "username": "uploader", "password": "***" }
}
```

可选 `privateKey`（PEM 文本）替代密码；`strictHostKeyChecking` 默认 `no`（生产建议改为 `yes` 并配置 known_hosts）。

**WebDAV**

```json
{
  "name": "NAS WebDAV",
  "protocol": "WEBDAV",
  "config": { "baseUrl": "https://nas.example.com/dav", "basePath": "renders" },
  "credentials": { "username": "user", "password": "***" }
}
```

**S3_MIRROR**（拷贝到客户桶，使用平台 `BlobStorage` SDK）

```json
{
  "name": "客户桶",
  "protocol": "S3_MIRROR",
  "config": { "bucket": "customer-bucket", "keyPrefix": "exports" },
  "credentials": { "accessKeyId": "...", "secretAccessKey": "..." }
}
```

`delivery.max-attempts`（默认 3）控制失败自动重试；`POST .../probe` 成功会更新 `verified_at`。

**SMB**

```json
{
  "protocol": "SMB",
  "config": { "host": "fileserver.local", "share": "exports", "basePath": "renders" },
  "credentials": { "username": "svc", "password": "***", "domain": "CORP" }
}
```

**HTTPS_PUT**（预签名或客户 PUT URL，支持 `{remotePath}`、`{fileName}`、`{jobId}` 占位符）

```json
{
  "protocol": "HTTPS_PUT",
  "config": { "uploadUrl": "https://upload.example.com/bucket/{remotePath}" },
  "credentials": { "authorization": "Bearer ***", "apiKey": "...", "header.X-Custom": "v" }
}
```

**Temporal**：`delivery.temporal.activity-enabled=true`（`application-temporal.yml`）时，`RenderWorkflow` 在 `executeRenderJob` 后调用 `deliverArtifacts` Activity，入队 AUTO 策略并同步处理队列。

**Admin**：`GET /api/v1/admin/delivery/jobs`、`/destinations`，`POST .../jobs/{id}/retry`；前端 `/admin/delivery`。

---

## 10. 数据库（草案）

```sql
create table delivery_destination (
  id varchar(64) primary key,
  tenant_id varchar(64) not null,
  user_id varchar(64),
  name varchar(255) not null,
  protocol varchar(32) not null,
  config_json clob,
  credential_encrypted clob,
  enabled boolean default true,
  verified_at timestamp,
  created_at timestamp not null
);

create table delivery_policy (
  id varchar(64) primary key,
  tenant_id varchar(64) not null,
  project_id varchar(64),
  destination_id varchar(64) not null,
  artifact_selector varchar(32) not null,
  path_template varchar(512) not null,
  trigger_mode varchar(16) not null, -- AUTO | MANUAL
  enabled boolean default true
);

create table delivery_job (
  id varchar(64) primary key,
  tenant_id varchar(64) not null,
  render_job_id varchar(64) not null,
  destination_id varchar(64) not null,
  status varchar(32) not null,
  source_uri varchar(1024) not null,
  remote_path varchar(1024),
  remote_uri varchar(1024),
  bytes_transferred bigint,
  attempt_count int default 0,
  error_code varchar(64),
  error_message varchar(2048),
  created_at timestamp not null,
  completed_at timestamp
);
```

---

## 11. 模块结构（建议）

```text
platform/delivery-module/
  api/DeliveryController.java
  api/port/DeliveryPort.java          // 供 render 或 workflow 调用
  app/DeliveryJobService.java
  app/DeliveryScheduler.java
  app/DeliveryPolicyResolver.java
  app/DeliverySourceResolver.java     // 从 render_job 取源流
  domain/DeliveryDestination.java
  domain/DeliveryProtocol.java
  infrastructure/
    adapter/SftpDeliveryAdapter.java
    adapter/WebDavDeliveryAdapter.java
    adapter/S3MirrorDeliveryAdapter.java
    credential/CredentialCipher.java
  spi/DeliveryAdapter.java            // 扩展点
```

`platform-app` 依赖 `delivery-module`；`render-module` **仅依赖** `DeliveryPort` 或纯事件（推荐事件，避免循环依赖）。

---

## 12. 实施阶段

| 阶段 | 交付物 |
|------|--------|
| **M0** | 本文档 + `architecture.md` 链接；`RenderJobCompletedEvent` 监听器 stub |
| **M1** | 表 + `DeliveryJob` 状态机 + `S3_MIRROR` + `AUTO` 策略 + 通知 |
| **M2** | ✅ SFTP + WebDAV + probe + 手动 deliver + 重试 |
| **M3** | ✅ SMB + HTTPS_PUT + Admin `/admin/delivery` + `deliverArtifacts` Activity |
| **M4** | ✅ `/me/delivery-destinations` + `DeliveryStatusPanel` + PATCH/DELETE |

---

## 13. 与「多协议」结论的关系

- **平台内**：继续单一 S3 兼容对象模型（已实现）。
- **平台外**：本交付子系统按协议加 **Adapter**，不污染 `BlobStorage`。
- 用户感知的「渲完自动进 NAS/网盘」= **DeliveryPolicy + DeliveryJob**，不是改渲染 DAG。

---

## 14. 待决问题（实现前确认）

1. 交付粒度：仅终稿 MP4，还是含字幕 SRT、缩略图、manifest？
2. 默认策略：按 **项目** 绑定还是 **租户** 全局默认？
3. C 端 vs B 端：C 端是否优先 **网盘 OAuth** 而非 SFTP？
4. 大文件：超过阈值是否强制先落临时盘再上传（worker 磁盘配额）？

前端 API：`platform/frontend/src/api/delivery.ts`（`DeliveryAPI`）。Temporal 渲染：`--spring.profiles.active=dev,temporal` + `render.execution.mode=temporal`，提交作业后由 `RenderJobSubmitContinuation` 启动 workflow，不再同步阻塞 `executeExistingRenderJob`。
