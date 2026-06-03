# 优雅停机与数据一致性

> 与 [asset-lifecycle-governance.md](asset-lifecycle-governance.md)、[persistence-restart-semantics.md](../platform/docs/persistence-restart-semantics.md) 互补。

## 优雅停机能力（当前）

| 机制 | 状态 | 说明 |
|------|------|------|
| `server.shutdown=graceful` | ✅ | Tomcat 停止接受新连接，等待在途 HTTP 请求（默认最长 `spring.lifecycle.timeout-per-shutdown-phase`，当前 30s） |
| `PlatformGracefulShutdownCoordinator` | ✅ | `ContextClosedEvent` 时：可选 outbox 批量 drain、关闭 extension/sandbox 线程池 |
| `TemporalWorkerGracefulShutdown` | ✅ | profile `temporal` 下 `WorkerFactory.shutdown()` + `awaitTermination`（`app.temporal.shutdown-await-seconds`） |
| `StaleRenderJobStartupListener` | ✅ | 本地模式启动时将 `AI_PROCESSING`/`RENDERING` 标为 `FAILED`；`execution.mode=temporal` 时默认跳过 |
| Outbox 事务写入 | ✅ | 业务与 `outbox_events` 同事务；停机前 drain 一批降低「已提交未派发」窗口 |
| 制品/时间线 GC | ⚠️ | 定时任务可能在停机中途被中断；已 tombstone 状态在 DB，下次调度可继续 |
| 渲染 DAG 本地执行 | ⚠️ | 非正常停机仍可能半完成；启动补偿 + 定时 `StaleRenderJobCompensator` 收敛 |

配置示例（`application.yml`）：

```yaml
server:
  shutdown: graceful
spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s
platform:
  lifecycle:
    shutdown:
      outbox-drain-enabled: true
      outbox-drain-batch: 50
```

## 部署 / 非正常停机风险矩阵

| 场景 | 可能后果 | 严重度 | 缓解 |
|------|----------|--------|------|
| **滚动发布 SIGTERM（graceful）** | 在途请求完成；outbox 可能剩少量 PENDING | 低 | drain + 定时 `OutboxEventDispatcher` 重试 |
| **kill -9 / OOM / 节点宕机** | 内存态丢失；DB 事务已提交部分可见；渲染/job 半完成 | 中 | Outbox 重试；`render_job` 状态机；AST 扫描修复 |
| **GC 中途被杀** | blob 已删但 catalog 仍 TOMBSTONED（或相反） | 中 | GC 先 delete-check；AST-002/004；手动 scan-global |
| **孤儿 purge 中途被杀** | 部分对象已删 | 低 | purge 按对象独立；再次 scan 可发现残留 |
| **时间线 tombstone 与 blob 不一致** | 元数据 tombstone、blob 仍在 | 低 | AST-002、制品 GC |
| **Billing/Checkout 内存态** | 重启后会话丢失 | 中（商业） | 见 persistence-restart-semantics 已知缺口 |
| **双写非原子（blob + DB）** | 先写 blob 后写 DB 失败 → 桶孤儿 | 中 | AST-005 + 受控 purge |
| **segmentCacheIndex 与 render_job** | 清理一半 execution_state 不完整 | 低 | `RenderCacheCleanupService` 可重跑；与制品 GC 分轨 |

结论：**支持「基础级」优雅停机**（HTTP + 可选 outbox drain + 沙箱线程池），**不能**保证所有后台任务在停机窗口内原子完成；**非正常停机**可能产生可修复的不一致，需依赖 **outbox 重试、任务状态机、AST 完整性扫描与 GC** 收敛。

## 推荐运维做法

1. 发布前：`POST /api/v1/asset-governance/integrity/scan-global`（可选）
2. 使用 K8s `preStop` + `terminationGracePeriodSeconds` ≥ 35s，先发 SIGTERM
3. 避免在 GC / purge 高峰强制 `kill -9`
4. 重启后检查：`outbox_events` PENDING 数量、`render_job` RUNNING 滞留、Prometheus `asset.integrity.*`

## 资产治理相关 API（停机后收敛）

```text
POST /api/v1/asset-governance/integrity/scan-global
POST /api/v1/asset-governance/gc/run-all
POST /api/v1/asset-governance/storage-orphans/scan
POST /api/v1/asset-governance/storage-orphans/purge?approvalToken=&dryRun=true
POST /api/v1/asset-governance/segment-cache/cleanup
```

## R2 / 段缓存 profile

`application-r2.yml`（`SPRING_PROFILES_ACTIVE=prod,r2`）默认开启 S3 R2 兼容、段 cache 上传与 `render.cache.cleanup-enabled`；与制品 catalog GC 分轨，见 `POST .../segment-cache/cleanup`。

## Temporal namespace 与 Worker

- 未设置 `TEMPORAL_NAMESPACE` 时：`spring.temporal.namespace` → `media-platform-${PLATFORM_ENV}`（`TemporalNamespaceEnvironmentPostProcessor`）
- profile `temporal`：`app.temporal.enabled=true`，Worker 轮询 `media-platform-tasks`
- 生产建议：`SPRING_PROFILES_ACTIVE=prod,temporal`，`TEMPORAL_FAIL_ON_MISSING_WORKER=true`
- 就绪检查：`GET /api/v1/admin/platform/readiness`；Actuator：`/actuator/health` 含 `temporalWorker`（profile `temporal` 且 Worker 在跑时）
