# 资产生命周期治理（P0）

> 对应路线图「R2/RustFS 段缓存 + 生命周期」的前置能力：受控 tombstone、删除前引用检查、结构化错误码。

## 能力概览

| 层级 | API | 行为 |
|------|-----|------|
| 时间线 `assetRegistry` | `GET /api/v1/media/assets/{assetId}/delete-check?projectId=` | 扫描项目下快照中的 clip/layer 引用 |
| 时间线 tombstone | `POST /api/v1/media/assets/{assetId}/tombstone?projectId=&snapshotId=` | 无引用时写入 `status=TOMBSTONED` 并生成新快照 |
| 制品目录 | `GET /api/v1/artifacts/{artifactId}/delete-check` | 检查 relation、render_job 输出 URI |
| 制品 tombstone | `POST /api/v1/artifacts/{artifactId}/tombstone` | 更新 `artifact.status`，**不**删除 blob |

## 错误码

| 错误码 | 场景 |
|--------|------|
| `ASSET-404-001` | registry 无条目或 URI 为占位符 |
| `ASSET-409-001` | 仍有 clip 引用，禁止 tombstone |
| `ASSET-410-001` | 资产已 tombstone，渲染解析失败 |
| `STORAGE-404-001` | 存储对象不存在（预留，供 fetch 层使用） |
| `ARTIFACT-404-001` | 制品不存在 |
| `ARTIFACT-409-001` | 制品仍被引用 |
| `ARTIFACT-410-001` | 制品已 tombstone |

## 渲染解析

`InternalTimelineAdapter` 经 `TimelineAssetUriResolver` 解析 clip URI：**不再**回退 `asset://` 占位符，缺失或 tombstone 时抛出 `PlatformException`。

## P1（已落地）

### 制品关系持久化

- 表 `artifact_relation`（迁移 `V16__artifact_relation.sql`）
- `ArtifactRelationRepository` 落库；`relateArtifacts` 写入 DB

### 后台 GC

- `ArtifactGcService`：对超过 `platform.artifact.gc.retention-days` 的 `TOMBSTONED` 制品删除 blob 并标记 `PURGED`
- 定时任务 `ArtifactGcScheduler`（默认每 6h）
- 手动触发：`POST /api/v1/artifacts/gc/run`

配置示例：

```yaml
platform:
  artifact:
    gc:
      enabled: true
      retention-days: 7
      schedule-interval: PT6H
      batch-size: 50
```

### 存储错误码接线

- `RenderCacheArtifactFetcher`：远程 URI（`s3://` 等）对象不存在时抛出 `STORAGE-404-001`
- `requireBytes(storageUri)` 严格读取

### 问题数据规则（AST-*）

| 规则 | 场景 |
|------|------|
| AST-001 | clip 引用缺失或已 tombstone 的 assetId |
| AST-002 | 已 tombstone 制品但 blob 仍在 |
| AST-003 | registry 无有效 storage URI |
| AST-004 | ACTIVE 制品但 blob 不存在 |

扫描 API：`POST /api/v1/media/assets/integrity/scan?projectId=` → 写入 `ProblematicDataDetectionService` 审计记录

## P2（已落地）

### 跨项目扫描与 Prometheus 指标

- `POST /api/v1/media/assets/integrity/scan-global` 或 `POST /api/v1/asset-governance/integrity/scan-global`
- 指标（Gauge）：
  - `asset.integrity.orphan_blobs`（AST-002）
  - `asset.integrity.missing_blobs`（AST-004）
  - `asset.integrity.dangling_timeline_refs`（AST-001）
  - `asset.integrity.unresolved_registry_uris`（AST-003）
  - `asset.integrity.projects_scanned`

### `artifact_relation` 外键

- 迁移 `V17__artifact_relation_fk.sql`：`ON DELETE RESTRICT` 防止误删仍被引用的制品

### 时间线 GC + 制品联动

- `TimelineAssetGcService`：清理超过保留期、无 clip 引用的 `TOMBSTONED` registry 项 → `PURGED`，可选删 blob
- 定时：`TimelineAssetGcScheduler`（`platform.timeline.asset.gc.*`）
- `POST /api/v1/media/assets/gc/run?projectId=`、`POST /api/v1/asset-governance/gc/run-all`
- 制品 tombstone 时发布 `ArtifactTombstonedEvent` → 同步 tombstone 同 URI 的时间线 registry 项

```yaml
platform:
  timeline:
    asset:
      gc:
        enabled: true
        retention-days: 7
        delete-blob-on-purge: true
```

## P3（已落地）

### 桶级孤儿对象扫描（AST-005）

- `BlobStorage.listObjects`：LocalFS / S3 列举
- `KnownStorageUriIndexService` + `StorageBucketOrphanScanner`
- `POST /api/v1/asset-governance/storage-orphans/scan`（亦包含在全局 scan-global）
- 指标：`asset.integrity.bucket_orphan_objects`

```yaml
platform:
  storage:
    orphan-scan:
      buckets: artifacts,render-cache
      max-objects-per-bucket: 500
```

### Delivery 联合引用检查

- `DeliveryStorageUriReferenceContributor` 接入制品/时间线 `delete-check`

## P4（已落地）

### 孤儿对象受控清理

- `StorageOrphanPurgeService`：扫描后按 AST-005 列表删除；**必须** `approval-token`（`STORAGE-403-001`）
- `POST /api/v1/asset-governance/storage-orphans/purge?approvalToken=&dryRun=true&storageUri=`
- 删除前二次校验已知 URI 索引，避免误删新引用对象

```yaml
platform:
  storage:
    orphan-purge:
      enabled: false
      approval-token: ${STORAGE_ORPHAN_PURGE_TOKEN:}
      max-deletes-per-run: 20
```

### Delivery 远端 URI 反向索引

- `DeliveryRemoteUriIndexService`
- `GET /api/v1/admin/delivery/by-storage-uri?storageUri=&projectId=&limit=`

### 段缓存 GC（与制品 GC 分轨）

- `RenderCacheCleanupService` / `render.cache.cleanup-enabled`（默认关闭）
- 按已完成 `render_job` + `segmentCacheIndex` 远程 URI 清理，不经过制品 catalog GC

## P5（已落地）

### 停机与任务恢复

- `TemporalWorkerGracefulShutdown`（profile `temporal`）
- `StaleRenderJobStartupListener`：本地执行模式启动时回收中断中的 render_job
- 见 [graceful-shutdown-and-data-consistency.md](graceful-shutdown-and-data-consistency.md)

### 段缓存 GC API（分轨）

- `POST /api/v1/asset-governance/segment-cache/cleanup?tenantId=&projectId=`
- profile `r2`：`application-r2.yml` 开启远程 cache + cleanup

### Delivery 目的地 URI 索引

- `DeliveryDestinationUriIndexService` 纳入 `KnownStorageUriIndexService`
- `GET /api/v1/admin/delivery/destination-uri-prefixes`

## 后续

- Temporal namespace 多环境隔离验收
- 集群级 `terminationGracePeriodSeconds` 与发布 Runbook 固化

## 停机与一致性

见 [graceful-shutdown-and-data-consistency.md](graceful-shutdown-and-data-consistency.md)。
