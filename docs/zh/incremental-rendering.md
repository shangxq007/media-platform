# 增量渲染与 Internal Timeline 1.0

> **最后更新:** 2026-05-20  
> **规范全文:** [docs/media-rendering/13-internal-timeline-schema-v1.md](../media-rendering/13-internal-timeline-schema-v1.md)

---

## 概述

平台以 **Internal Timeline Schema 1.0** 为真源（`schemaVersion: "1.0"`），通过语义 Diff + DAG 增量计划复用未变更的渲染产物。对外交换格式（OTIO / EDL / FCPXML / AAF / SRT）导入后统一输出 1.0 JSON。

**不提供** v2/v3 多版本迁移；`revision` 仅为文档修订号，与 `schemaVersion` 无关。

---

## 增量渲染流程

```
新 1.0 JSON + baseJobId
    → 加载基准作业 Internal Timeline / 执行状态
    → TimelineSemanticDiffService（语义 Diff）
    → RenderImpactAnalyzer（失效范围）
    → IncrementalRenderPlanService（reuse / execute 标注）
    → PipelineDagExecutorService.executeWithPlan()
```

### 提交参数

| 字段 | 说明 |
|------|------|
| `baseJobId` | 已完成基准作业 ID（`render_job.base_job_id`） |
| `targetSegmentIds` | 可选，仅重跑指定 `seg_*`（局部段渲染） |
| `timelineSnapshotId` | 快照 ID，或 inline `prompt` 为 1.0 JSON |

### 执行状态字段（`pipeline_execution_json`）

| 字段 | 说明 |
|------|------|
| `segmentArtifacts` | `seg_*` → 本地/远程 URI |
| `segmentCacheIndex` | `cacheKey` → `{uri, remoteUri, contentHash, segmentId}` |
| `mezzanineCacheIndex` | `final_compose` 终稿/拼接产物索引 |
| `pipelineStageArtifacts` | 各阶段产物 URI |

---

## 段级缓存（segmentPolicy）

在 1.0 JSON 的 `renderGraph.segmentPolicy` 中启用：

```json
"segmentPolicy": {
  "enabled": true,
  "segmentDuration": { "frame": 120 },
  "overlapFrames": 2,
  "cacheScope": "SEGMENT"
}
```

- Planner 插入 `SEGMENT_RENDER`（`seg_0`、`seg_1`…）
- 脏段重跑，净段 `reuse` + `skipExecution`
- `final_compose` 按 `finalComposer` 用 FFmpeg concat 或 MLT 拼接段 MP4

---

## 远程缓存与 S3

### 配置（`application.yml`）

```yaml
render:
  cache:
    remote-enabled: true
    remote-uri-prefix: s3://tenant-cache/render
    upload-enabled: true
    content-hash-enabled: true
    invalidate-on-hash-mismatch: true

storage:
  s3:
    enabled: true
    region: us-east-1
    endpoint: http://minio:9000   # MinIO 等 S3 兼容端点
    access-key: ${STORAGE_S3_ACCESS_KEY}
    secret-key: ${STORAGE_S3_SECRET_KEY}
    path-style-access: true
    default-bucket: render-cache
```

| 环境变量 | 说明 |
|----------|------|
| `STORAGE_S3_ENABLED` | 启用 `S3BlobStorageProvider`（`@Primary` BlobStorage） |
| `RENDER_CACHE_UPLOAD_ENABLED` | 段/终稿产物上传至 BlobStorage |
| `RENDER_CACHE_CONTENT_HASH_ENABLED` | 记录并校验 `sha256:` 内容哈希 |
| `RENDER_CACHE_INVALIDATE_ON_HASH_MISMATCH` | 哈希不匹配时强制 `execute`（非仅丢弃 URI） |

### 行为说明

- 上传后 URI 形如 `s3StorageProvider://render-cache/{tenant}/{cacheKey}.mp4`
- `RenderCacheArtifactFetcher` 在本地文件缺失时从 BlobStorage 拉取字节做哈希校验
- **哈希不匹配**：对应任务进入 `execute`，元数据记录 `hashInvalidatedTaskIds`

---

## REST API（OpenAPI）

租户路径下正式端点（Swagger UI：`/swagger-ui.html`）：

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/v1/tenants/{tenantId}/projects/{projectId}/render-jobs/incremental/plan` | 预览增量计划（`GenerateIncrementalPlanRequest`） |
| `POST` | `/api/v1/tenants/{tenantId}/projects/{projectId}/render-jobs/incremental/submit` | 提交增量作业（`SubmitRenderJobRequest`，含 `baseJobId` / `targetSegmentIds`） |
| `GET` | `/api/v1/tenants/{tenantId}/projects/{projectId}/render-jobs/{jobId}/cache/presign` | 预签名下载 URL；`?cacheKey=`（URL 编码）可只取单条 |

### 租户隔离

- `RenderCacheTenantGuard`：校验作业 `tenant_id` / `project_id`，拒绝跨租户 `baseJobId` 复用
- 远程 cache 对象键须为 `{tenantId}/...`（与 `SegmentArtifactUploadService.objectKeyFor` 一致）
- 提交增量作业时 orchestrator 在写入 `base_job_id` 前执行 `requireBaseJobAccess`

### 内容哈希失效事件 / Webhook

当增量计划检测到 `contentHash` 不匹配且 `invalidate-on-hash-mismatch=true` 时：

1. 发布 Spring 事件 `RenderCacheHashInvalidatedEvent`
2. 通知模块投递 `render.cache.hash_invalidated`（可订阅 IN_APP / EMAIL / **WEBHOOK** 渠道）
3. 可选：配置 `render.cache.webhook-url` 直接 POST JSON（请求头 `X-Render-Cache-Signature` 为 `webhook-secret`）

```yaml
render:
  cache:
    webhook-enabled: true
    webhook-url: https://hooks.example.com/render-cache
    webhook-secret: ${RENDER_CACHE_WEBHOOK_SECRET}
```

### Cache TTL 清理

```yaml
render:
  cache:
    cleanup-enabled: true
    retention-days: 30
    cleanup-interval: PT24H
```

- 定时任务扫描 `COMPLETED` 且超过 `retention-days` 的作业，删除 `segmentCacheIndex` / `mezzanineCacheIndex` 中的远程对象
- 手动触发：`POST /api/v1/tenants/{tenantId}/projects/{projectId}/render/cache/cleanup`

### 前端 SDK

`platform/frontend/src/api/render-incremental.ts` 导出 `IncrementalRenderAPI`：

- `previewPlan` / `submitJob` / `presignCache` / `cleanupExpiredCache`
- 从 `@/api` 或 `@/api/index` 再导出类型与客户端

---

## MCP 工具

| 工具 | 说明 |
|------|------|
| `render_timeline` | 校验 1.0 → 提交作业（可选 `baseJobId`） |
| `render_segment` | 局部段预览/提交（`segmentIds`） |
| `generate_incremental_render_plan` | 增量 DAG 预览 |
| `diff_timelines` / `analyze_render_impact` | Diff 与失效分析 |

路径：`POST /api/v1/mcp/media/tools/{tool}`

---

## Writer 与 layers 同步

- `InternalTimelineWriter` 根据 `composition.subtitleTracks` 补全 `renderGraph.layers`（`layer_{trackId}`）
- `InternalTimelineAdapter` 读回 STICKER 层至 `metadata.platform.stickers`
- 外渲染 `templates` 与 `externalRenderNodes` 由 `TimelineExtensionsReader` / `RenderPlannerService` 对齐

---

## 相关文档

- [架构说明](architecture.md) — 模块与数据流
- [部署清单](deployment.md) — S3/MinIO 与缓存环境变量
- [常见问题](faq.md) — 增量渲染 FAQ
